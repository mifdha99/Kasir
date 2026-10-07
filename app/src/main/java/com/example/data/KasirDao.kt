package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.util.SecurityAndFormatUtils
import kotlinx.coroutines.flow.Flow

@Dao
interface KasirDao {

    // --- CATEGORIES ---
    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllCategoriesSnapshot(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("UPDATE products SET categoryName = :newName WHERE categoryId = :categoryId")
    suspend fun updateProductsCategoryName(categoryId: Long, newName: String)

    @Query("UPDATE products SET categoryId = :toCategoryId, categoryName = :toCategoryName WHERE categoryId = :fromCategoryId")
    suspend fun moveProductsToCategory(fromCategoryId: Long, toCategoryId: Long, toCategoryName: String)

    @Query("SELECT COUNT(*) FROM products WHERE categoryId = :categoryId")
    suspend fun countProductsInCategory(categoryId: Long): Int

    // --- PRODUCTS ---
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsSnapshot(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

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

    // --- TRANSACTIONS / BILLINGS ---
    @Transaction
    @Query("SELECT * FROM transactions ORDER BY billingNumber DESC, timestamp DESC")
    fun getAllTransactionsWithItems(): Flow<List<TransactionWithItems>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE status = 'UNPAID' ORDER BY billingNumber ASC")
    fun getUnpaidBillingsWithItems(): Flow<List<TransactionWithItems>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionWithItemsById(transactionId: Long): TransactionWithItems?

    @Query("SELECT * FROM transactions ORDER BY billingNumber DESC, timestamp DESC")
    suspend fun getAllTransactionsSnapshot(): List<TransactionEntity>

    @Query("SELECT * FROM transaction_items ORDER BY id ASC")
    suspend fun getAllTransactionItemsSnapshot(): List<TransactionItemEntity>

    @Query("SELECT COALESCE(MAX(billingNumber), 0) FROM transactions")
    suspend fun getMaxBillingNumber(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItems(items: List<TransactionItemEntity>)

    @Query("DELETE FROM transaction_items WHERE transactionId = :transactionId")
    suspend fun deleteItemsForTransaction(transactionId: Long)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    // --- ATOMIC: KIRIM PESANAN KE DAPUR (SAVE / UPDATE UNPAID BILLING) ---
    @Transaction
    suspend fun saveOrderToKitchenAtomic(
        existingTransactionId: Long?,
        billingNumber: Int,
        cashier: CashierUserEntity,
        customer: CustomerEntity?,
        cartItems: List<CartItem>,
        notes: String
    ): Long {
        if (cartItems.isEmpty()) {
            throw IllegalArgumentException("Pilih minimal 1 menu makanan/minuman terlebih dahulu.")
        }

        val subtotal = cartItems.sumOf { it.subtotal }
        val totalCost = cartItems.sumOf { it.totalCost }
        val billingLabel = SecurityAndFormatUtils.formatBillingLabel(billingNumber)
        val now = System.currentTimeMillis()

        val txId = if (existingTransactionId != null && existingTransactionId > 0L) {
            val existing = getTransactionWithItemsById(existingTransactionId)?.transaction
            if (existing != null) {
                updateTransaction(
                    existing.copy(
                        billingNumber = billingNumber,
                        invoiceNumber = billingLabel,
                        cashierId = cashier.id,
                        cashierName = cashier.name,
                        customerId = customer?.id ?: existing.customerId,
                        customerName = customer?.name ?: existing.customerName,
                        subtotal = subtotal,
                        totalAmount = (subtotal - existing.discountAmount + existing.taxAmount + existing.serviceFee + existing.roundingAmount).coerceAtLeast(0.0),
                        totalCost = totalCost,
                        notes = notes.trim(),
                        isSentToKitchen = true,
                        status = "UNPAID"
                    )
                )
                deleteItemsForTransaction(existing.id)
                existing.id
            } else {
                insertTransaction(
                    TransactionEntity(
                        billingNumber = billingNumber,
                        invoiceNumber = billingLabel,
                        timestamp = now,
                        cashierId = cashier.id,
                        cashierName = cashier.name,
                        customerId = customer?.id,
                        customerName = customer?.name ?: "Pelanggan Umum",
                        subtotal = subtotal,
                        totalAmount = subtotal,
                        totalCost = totalCost,
                        notes = notes.trim(),
                        isSentToKitchen = true,
                        status = "UNPAID"
                    )
                )
            }
        } else {
            insertTransaction(
                TransactionEntity(
                    billingNumber = billingNumber,
                    invoiceNumber = billingLabel,
                    timestamp = now,
                    cashierId = cashier.id,
                    cashierName = cashier.name,
                    customerId = customer?.id,
                    customerName = customer?.name ?: "Pelanggan Umum",
                    subtotal = subtotal,
                    totalAmount = subtotal,
                    totalCost = totalCost,
                    notes = notes.trim(),
                    isSentToKitchen = true,
                    status = "UNPAID"
                )
            )
        }

        val txItems = cartItems.map { cartItem ->
            val normalizedNotes = cartItem.normalizedPortionNotes
            TransactionItemEntity(
                transactionId = txId,
                productId = cartItem.product.id,
                productName = cartItem.product.name,
                categoryName = cartItem.product.categoryName,
                buyPrice = cartItem.product.buyPrice,
                sellPrice = cartItem.product.sellPrice,
                quantity = cartItem.quantity,
                unit = cartItem.product.unit,
                itemNote = SecurityAndFormatUtils.summarizePortionNotes(normalizedNotes),
                portionNotesJson = SecurityAndFormatUtils.encodePortionNotes(normalizedNotes),
                subtotal = cartItem.subtotal
            )
        }
        insertTransactionItems(txItems)
        return txId
    }

    // --- ATOMIC: BAYAR & SELESAIKAN BILLING ---
    @Transaction
    suspend fun completeBillingPaymentAtomic(
        transactionId: Long,
        cashier: CashierUserEntity,
        discountAmount: Double,
        taxPercentage: Double,
        taxAmount: Double,
        serviceFee: Double,
        roundingAmount: Double,
        totalAmount: Double,
        paymentMethod: String,
        amountPaid: Double,
        changeAmount: Double
    ): Long {
        val txWithItems = getTransactionWithItemsById(transactionId)
            ?: throw IllegalStateException("Data Billing tidak ditemukan.")
        val existing = txWithItems.transaction
        if (existing.status == "PAID" || existing.status == "COMPLETED") {
            throw IllegalStateException("${existing.billingDisplay} sudah dibayar sebelumnya.")
        }

        val now = System.currentTimeMillis()
        updateTransaction(
            existing.copy(
                timestamp = now,
                cashierId = cashier.id,
                cashierName = cashier.name,
                discountAmount = discountAmount,
                taxPercentage = taxPercentage,
                taxAmount = taxAmount,
                serviceFee = serviceFee,
                roundingAmount = roundingAmount,
                totalAmount = totalAmount,
                paymentMethod = paymentMethod,
                amountPaid = amountPaid,
                changeAmount = changeAmount,
                status = "PAID"
            )
        )

        val custId = existing.customerId
        if (custId != null && custId > 0L) {
            val customer = getCustomerById(custId)
            if (customer != null) {
                updateCustomer(
                    customer.copy(
                        totalTransactions = customer.totalTransactions + 1,
                        totalPurchase = customer.totalPurchase + totalAmount
                    )
                )
            }
        }

        return existing.id
    }

    // --- ATOMIC: CANCEL / REFUND TRANSACTION ---
    @Transaction
    suspend fun cancelTransactionAtomic(
        transactionId: Long,
        cancelReason: String
    ) {
        val txWithItems = getTransactionWithItemsById(transactionId)
            ?: throw IllegalStateException("Transaksi tidak ditemukan.")
        val tx = txWithItems.transaction
        if (tx.status == "CANCELLED") {
            throw IllegalStateException("${tx.billingDisplay} sudah dibatalkan sebelumnya.")
        }

        val wasPaid = (tx.status == "PAID" || tx.status == "COMPLETED")
        val now = System.currentTimeMillis()
        updateTransaction(
            tx.copy(
                status = "CANCELLED",
                cancelReason = cancelReason.ifBlank { "Dibatalkan oleh kasir" },
                cancelledAt = now
            )
        )

        if (wasPaid) {
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
    }

    // --- CLEAR & RESTORE FOR BACKUP ---
    @Query("DELETE FROM transaction_items")
    suspend fun clearAllTransactionItems()

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

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
        transactionItems: List<TransactionItemEntity>
    ) {
        clearAllTransactionItems()
        clearAllTransactions()
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
    }
}
