package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KasirDao {

    // --- CATEGORIES ---
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAllCategoriesSnapshot(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("UPDATE products SET categoryName = :newName WHERE categoryId = :categoryId")
    suspend fun updateProductsCategoryName(categoryId: Long, newName: String)

    // --- PRODUCTS ---
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsSnapshot(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode != '' LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE sku = :sku AND sku != '' LIMIT 1")
    suspend fun getProductBySku(sku: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    // --- CUSTOMERS ---
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY name ASC")
    suspend fun getAllCustomersSnapshot(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    // --- USERS (CASHIERS & ADMINS) ---
    @Query("SELECT * FROM users ORDER BY id ASC")
    fun getAllUsers(): Flow<List<CashierUserEntity>>

    @Query("SELECT * FROM users ORDER BY id ASC")
    suspend fun getAllUsersSnapshot(): List<CashierUserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: CashierUserEntity): Long

    @Update
    suspend fun updateUser(user: CashierUserEntity)

    @Delete
    suspend fun deleteUser(user: CashierUserEntity)

    // --- SETTINGS ---
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun getStoreSettings(): Flow<StoreSettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getStoreSettingsSnapshot(): StoreSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStoreSettings(settings: StoreSettingsEntity)

    // --- PRINTERS ---
    @Query("SELECT * FROM printers ORDER BY isDefault DESC, lastConnectedAt DESC")
    fun getAllPrinters(): Flow<List<PrinterDeviceEntity>>

    @Query("SELECT * FROM printers ORDER BY isDefault DESC, lastConnectedAt DESC")
    suspend fun getAllPrintersSnapshot(): List<PrinterDeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePrinter(printer: PrinterDeviceEntity)

    @Query("UPDATE printers SET isDefault = 0")
    suspend fun clearDefaultPrinters()

    @Delete
    suspend fun deletePrinter(printer: PrinterDeviceEntity)

    // --- STOCK MOVEMENTS ---
    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC")
    fun getAllStockMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC")
    suspend fun getAllStockMovementsSnapshot(): List<StockMovementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovement(movement: StockMovementEntity): Long

    // --- TRANSACTIONS ---
    @Transaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsWithItems(): Flow<List<TransactionWithItems>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionWithItemsById(transactionId: Long): TransactionWithItems?

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsSnapshot(): List<TransactionEntity>

    @Query("SELECT * FROM transaction_items ORDER BY id ASC")
    suspend fun getAllTransactionItemsSnapshot(): List<TransactionItemEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay")
    suspend fun getTransactionCountBetween(startOfDay: Long, endOfDay: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItems(items: List<TransactionItemEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    // --- ATOMIC CHECKOUT TRANSACTION ---
    @Transaction
    suspend fun executeCheckoutAtomic(
        transaction: TransactionEntity,
        cartItems: List<CartItem>,
        autoReduceStock: Boolean,
        allowNegativeStock: Boolean
    ): Long {
        if (cartItems.isEmpty()) {
            throw IllegalArgumentException("Keranjang belanja masih kosong.")
        }

        // Validate stock first before writing anything
        val productSnapshots = mutableMapOf<Long, ProductEntity>()
        for (cartItem in cartItems) {
            if (cartItem.quantity <= 0) {
                throw IllegalArgumentException("Jumlah produk ${cartItem.product.name} tidak valid.")
            }
            val currentProduct = getProductById(cartItem.product.id)
                ?: throw IllegalStateException("Produk '${cartItem.product.name}' tidak ditemukan di database.")
            if (autoReduceStock && !allowNegativeStock && currentProduct.stock < cartItem.quantity) {
                throw IllegalStateException(
                    "Stok '${currentProduct.name}' tidak mencukupi (Tersedia: ${currentProduct.stock} ${currentProduct.unit}, Diminta: ${cartItem.quantity} ${currentProduct.unit})."
                )
            }
            productSnapshots[currentProduct.id] = currentProduct
        }

        // Insert transaction header
        val txId = insertTransaction(transaction)

        // Build and insert transaction items
        val txItems = cartItems.map { cartItem ->
            TransactionItemEntity(
                transactionId = txId,
                productId = cartItem.product.id,
                productName = cartItem.product.name,
                sku = cartItem.product.sku,
                categoryName = cartItem.product.categoryName,
                buyPrice = cartItem.product.buyPrice,
                sellPrice = cartItem.product.sellPrice,
                quantity = cartItem.quantity,
                unit = cartItem.product.unit,
                itemNote = cartItem.note.trim(),
                subtotal = cartItem.subtotal
            )
        }
        insertTransactionItems(txItems)

        // Deduct stock and record stock movement if autoReduceStock is enabled
        if (autoReduceStock) {
            for (cartItem in cartItems) {
                val currentProduct = productSnapshots[cartItem.product.id]!!
                val previousStock = currentProduct.stock
                val newStock = previousStock - cartItem.quantity
                updateProduct(
                    currentProduct.copy(
                        stock = newStock,
                        updatedAt = transaction.timestamp
                    )
                )
                insertStockMovement(
                    StockMovementEntity(
                        productId = currentProduct.id,
                        productName = currentProduct.name,
                        type = "SALE",
                        quantityChange = -cartItem.quantity,
                        previousStock = previousStock,
                        newStock = newStock,
                        note = "Penjualan ${transaction.invoiceNumber}",
                        referenceInvoice = transaction.invoiceNumber,
                        timestamp = transaction.timestamp,
                        userName = transaction.cashierName
                    )
                )
            }
        }

        // Update customer stats if a registered customer is attached
        val custId = transaction.customerId
        if (custId != null && custId > 0L) {
            val customer = getCustomerById(custId)
            if (customer != null) {
                updateCustomer(
                    customer.copy(
                        totalTransactions = customer.totalTransactions + 1,
                        totalPurchase = customer.totalPurchase + transaction.totalAmount
                    )
                )
            }
        }

        return txId
    }

    // --- ATOMIC CANCEL / REFUND TRANSACTION ---
    @Transaction
    suspend fun cancelTransactionAtomic(
        transactionId: Long,
        cancelReason: String,
        cancelledBy: String,
        restoreStock: Boolean
    ) {
        val txWithItems = getTransactionWithItemsById(transactionId)
            ?: throw IllegalStateException("Transaksi tidak ditemukan.")
        val tx = txWithItems.transaction
        if (tx.status == "CANCELLED") {
            throw IllegalStateException("Transaksi ${tx.invoiceNumber} sudah dibatalkan sebelumnya.")
        }

        val now = System.currentTimeMillis()
        updateTransaction(
            tx.copy(
                status = "CANCELLED",
                cancelReason = cancelReason.ifBlank { "Dibatalkan oleh kasir" },
                cancelledAt = now
            )
        )

        if (restoreStock) {
            for (item in txWithItems.items) {
                val product = getProductById(item.productId)
                if (product != null) {
                    val prevStock = product.stock
                    val updatedStock = prevStock + item.quantity
                    updateProduct(
                        product.copy(
                            stock = updatedStock,
                            updatedAt = now
                        )
                    )
                    insertStockMovement(
                        StockMovementEntity(
                            productId = product.id,
                            productName = product.name,
                            type = "REFUND",
                            quantityChange = item.quantity,
                            previousStock = prevStock,
                            newStock = updatedStock,
                            note = "Pembatalan ${tx.invoiceNumber}: $cancelReason",
                            referenceInvoice = tx.invoiceNumber,
                            timestamp = now,
                            userName = cancelledBy
                        )
                    )
                }
            }
        }

        // Revert customer stats if applicable
        val custId = tx.customerId
        if (custId != null && custId > 0L) {
            val customer = getCustomerById(custId)
            if (customer != null) {
                updateCustomer(
                    customer.copy(
                        totalTransactions = (customer.totalTransactions - 1).coerceAtLeast(0),
                        totalPurchase = (customer.totalPurchase - tx.totalAmount).coerceAtLeast(0.0)
                    )
                )
            }
        }
    }

    // --- ATOMIC STOCK ADJUSTMENT ---
    @Transaction
    suspend fun adjustStockAtomic(
        productId: Long,
        type: String, // "IN", "OUT", "ADJUSTMENT"
        quantityInput: Int,
        note: String,
        userName: String,
        allowNegativeStock: Boolean
    ) {
        if (quantityInput < 0) {
            throw IllegalArgumentException("Jumlah stok tidak boleh bernilai negatif.")
        }
        val product = getProductById(productId)
            ?: throw IllegalStateException("Produk tidak ditemukan.")
        val prevStock = product.stock
        val newStock = when (type) {
            "IN" -> {
                if (quantityInput == 0) throw IllegalArgumentException("Jumlah stok masuk harus lebih dari 0.")
                prevStock + quantityInput
            }
            "OUT" -> {
                if (quantityInput == 0) throw IllegalArgumentException("Jumlah stok keluar harus lebih dari 0.")
                val target = prevStock - quantityInput
                if (!allowNegativeStock && target < 0) {
                    throw IllegalStateException("Stok tidak mencukupi untuk dikeluarkan (Stok saat ini: $prevStock).")
                }
                target
            }
            "ADJUSTMENT" -> {
                quantityInput
            }
            else -> throw IllegalArgumentException("Tipe perubahan stok tidak dikenal.")
        }

        val delta = newStock - prevStock
        val now = System.currentTimeMillis()
        updateProduct(
            product.copy(
                stock = newStock,
                updatedAt = now
            )
        )
        insertStockMovement(
            StockMovementEntity(
                productId = product.id,
                productName = product.name,
                type = type,
                quantityChange = delta,
                previousStock = prevStock,
                newStock = newStock,
                note = note.ifBlank {
                    when (type) {
                        "IN" -> "Stok masuk manual"
                        "OUT" -> "Stok keluar manual"
                        else -> "Penyesuaian stok (Stock Opname)"
                    }
                },
                referenceInvoice = "",
                timestamp = now,
                userName = userName
            )
        )
    }

    // --- CLEAR & RESTORE FOR BACKUP ---
    @Query("DELETE FROM transaction_items")
    suspend fun clearAllTransactionItems()

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    @Query("DELETE FROM stock_movements")
    suspend fun clearAllStockMovements()

    @Query("DELETE FROM products")
    suspend fun clearAllProducts()

    @Query("DELETE FROM categories")
    suspend fun clearAllCategories()

    @Query("DELETE FROM customers")
    suspend fun clearAllCustomers()

    @Query("DELETE FROM users")
    suspend fun clearAllUsers()

    @Query("DELETE FROM printers")
    suspend fun clearAllPrinters()

    @Transaction
    suspend fun restoreFullDatabaseAtomic(
        categories: List<CategoryEntity>,
        products: List<ProductEntity>,
        customers: List<CustomerEntity>,
        users: List<CashierUserEntity>,
        settings: StoreSettingsEntity?,
        printers: List<PrinterDeviceEntity>,
        transactions: List<TransactionEntity>,
        transactionItems: List<TransactionItemEntity>,
        stockMovements: List<StockMovementEntity>
    ) {
        clearAllTransactionItems()
        clearAllTransactions()
        clearAllStockMovements()
        clearAllProducts()
        clearAllCategories()
        clearAllCustomers()
        clearAllUsers()
        clearAllPrinters()

        categories.forEach { insertCategory(it) }
        products.forEach { insertProduct(it) }
        customers.forEach { insertCustomer(it) }
        users.forEach { insertUser(it) }
        if (settings != null) {
            saveStoreSettings(settings.copy(id = 1))
        }
        printers.forEach { savePrinter(it) }
        transactions.forEach { insertTransaction(it) }
        if (transactionItems.isNotEmpty()) {
            insertTransactionItems(transactionItems)
        }
        stockMovements.forEach { insertStockMovement(it) }
    }
}
