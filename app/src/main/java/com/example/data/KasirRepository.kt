package com.example.data

import com.example.util.SecurityAndFormatUtils
import kotlinx.coroutines.flow.Flow

class KasirRepository(private val dao: KasirDao) {

    val categories: Flow<List<CategoryEntity>> = dao.getAllCategories()
    val products: Flow<List<ProductEntity>> = dao.getAllProducts()
    val customers: Flow<List<CustomerEntity>> = dao.getAllCustomers()
    val users: Flow<List<CashierUserEntity>> = dao.getAllUsers()
    val settings: Flow<StoreSettingsEntity?> = dao.getStoreSettings()
    val printers: Flow<List<PrinterDeviceEntity>> = dao.getAllPrinters()
    val stockMovements: Flow<List<StockMovementEntity>> = dao.getAllStockMovements()
    val transactionsWithItems: Flow<List<TransactionWithItems>> = dao.getAllTransactionsWithItems()

    // --- CATEGORY CRUD ---
    suspend fun saveCategory(category: CategoryEntity): Result<Long> = runCatching {
        val cleanName = category.name.trim()
        if (cleanName.isEmpty()) {
            throw IllegalArgumentException("Nama kategori tidak boleh kosong.")
        }
        val toSave = category.copy(name = cleanName)
        if (toSave.id == 0L) {
            dao.insertCategory(toSave)
        } else {
            dao.updateCategory(toSave)
            dao.updateProductsCategoryName(toSave.id, cleanName)
            toSave.id
        }
    }

    suspend fun deleteCategory(category: CategoryEntity): Result<Unit> = runCatching {
        dao.deleteCategory(category)
    }

    // --- PRODUCT CRUD ---
    suspend fun saveProduct(product: ProductEntity, userName: String): Result<Long> = runCatching {
        val cleanName = product.name.trim()
        if (cleanName.isEmpty()) {
            throw IllegalArgumentException("Nama produk tidak boleh kosong.")
        }
        if (product.buyPrice < 0.0 || product.sellPrice < 0.0) {
            throw IllegalArgumentException("Harga beli dan harga jual tidak boleh bernilai negatif.")
        }
        if (product.minStock < 0) {
            throw IllegalArgumentException("Stok minimum tidak boleh bernilai negatif.")
        }
        val settingsSnap = dao.getStoreSettingsSnapshot() ?: StoreSettingsEntity()
        if (!settingsSnap.allowNegativeStock && product.stock < 0) {
            throw IllegalArgumentException("Stok tidak boleh bernilai negatif.")
        }

        val now = System.currentTimeMillis()
        val toSave = product.copy(
            name = cleanName,
            sku = product.sku.trim(),
            barcode = product.barcode.trim(),
            unit = product.unit.trim().ifEmpty { "Pcs" },
            description = product.description.trim(),
            updatedAt = now
        )

        if (toSave.id == 0L) {
            val newId = dao.insertProduct(toSave.copy(createdAt = now))
            if (toSave.stock != 0) {
                dao.insertStockMovement(
                    StockMovementEntity(
                        productId = newId,
                        productName = toSave.name,
                        type = "IN",
                        quantityChange = toSave.stock,
                        previousStock = 0,
                        newStock = toSave.stock,
                        note = "Stok awal produk baru",
                        timestamp = now,
                        userName = userName
                    )
                )
            }
            newId
        } else {
            val existing = dao.getProductById(toSave.id)
            dao.updateProduct(toSave)
            if (existing != null && existing.stock != toSave.stock) {
                val diff = toSave.stock - existing.stock
                dao.insertStockMovement(
                    StockMovementEntity(
                        productId = toSave.id,
                        productName = toSave.name,
                        type = "ADJUSTMENT",
                        quantityChange = diff,
                        previousStock = existing.stock,
                        newStock = toSave.stock,
                        note = "Perubahan stok melalui Edit Produk",
                        timestamp = now,
                        userName = userName
                    )
                )
            }
            toSave.id
        }
    }

    suspend fun deleteProduct(product: ProductEntity): Result<Unit> = runCatching {
        dao.deleteProduct(product)
    }

    suspend fun findProductByBarcodeOrSku(code: String): ProductEntity? {
        val clean = code.trim()
        if (clean.isEmpty()) return null
        return dao.getProductByBarcode(clean) ?: dao.getProductBySku(clean)
    }

    // --- STOCK ADJUSTMENT ---
    suspend fun adjustStock(
        productId: Long,
        type: String,
        quantityInput: Int,
        note: String,
        userName: String
    ): Result<Unit> = runCatching {
        val settingsSnap = dao.getStoreSettingsSnapshot() ?: StoreSettingsEntity()
        dao.adjustStockAtomic(
            productId = productId,
            type = type,
            quantityInput = quantityInput,
            note = note.trim(),
            userName = userName,
            allowNegativeStock = settingsSnap.allowNegativeStock
        )
    }

    // --- CUSTOMER CRUD ---
    suspend fun saveCustomer(customer: CustomerEntity): Result<Long> = runCatching {
        val cleanName = customer.name.trim()
        if (cleanName.isEmpty()) {
            throw IllegalArgumentException("Nama pelanggan tidak boleh kosong.")
        }
        val toSave = customer.copy(
            name = cleanName,
            phone = customer.phone.trim(),
            address = customer.address.trim(),
            notes = customer.notes.trim()
        )
        if (toSave.id == 0L) {
            dao.insertCustomer(toSave)
        } else {
            dao.updateCustomer(toSave)
            toSave.id
        }
    }

    suspend fun deleteCustomer(customer: CustomerEntity): Result<Unit> = runCatching {
        dao.deleteCustomer(customer)
    }

    // --- CASHIER USER CRUD ---
    suspend fun saveUser(user: CashierUserEntity, rawPinIfChanged: String?): Result<Long> = runCatching {
        val cleanName = user.name.trim()
        if (cleanName.isEmpty()) {
            throw IllegalArgumentException("Nama kasir/pengguna tidak boleh kosong.")
        }
        val pinHash = if (!rawPinIfChanged.isNullOrBlank()) {
            if (rawPinIfChanged.trim().length < 4) {
                throw IllegalArgumentException("PIN minimal terdiri dari 4 digit/karakter.")
            }
            SecurityAndFormatUtils.hashPin(rawPinIfChanged.trim())
        } else {
            user.pinHash
        }
        val toSave = user.copy(name = cleanName, pinHash = pinHash)
        if (toSave.id == 0L) {
            dao.insertUser(toSave)
        } else {
            dao.updateUser(toSave)
            toSave.id
        }
    }

    suspend fun deleteUser(user: CashierUserEntity): Result<Unit> = runCatching {
        val allUsers = dao.getAllUsersSnapshot()
        if (allUsers.size <= 1) {
            throw IllegalStateException("Tidak dapat menghapus satu-satunya pengguna kasir yang tersisa.")
        }
        dao.deleteUser(user)
    }

    // --- SETTINGS ---
    suspend fun saveSettings(settings: StoreSettingsEntity): Result<Unit> = runCatching {
        if (settings.storeName.trim().isEmpty()) {
            throw IllegalArgumentException("Nama toko tidak boleh kosong.")
        }
        if (settings.defaultTaxPercent < 0.0 || settings.defaultTaxPercent > 100.0) {
            throw IllegalArgumentException("Persentase pajak harus antara 0% hingga 100%.")
        }
        if (settings.defaultServiceFee < 0.0) {
            throw IllegalArgumentException("Biaya layanan tidak boleh negatif.")
        }
        dao.saveStoreSettings(settings.copy(id = 1, storeName = settings.storeName.trim()))
    }

    // --- PRINTERS ---
    suspend fun setDefaultPrinter(name: String, address: String, paperSizeMm: Int): Result<Unit> = runCatching {
        dao.clearDefaultPrinters()
        dao.savePrinter(
            PrinterDeviceEntity(
                address = address,
                name = name,
                paperSizeMm = paperSizeMm,
                isDefault = true,
                lastConnectedAt = System.currentTimeMillis()
            )
        )
        val currentSettings = dao.getStoreSettingsSnapshot() ?: StoreSettingsEntity()
        dao.saveStoreSettings(
            currentSettings.copy(
                defaultPrinterName = name,
                defaultPrinterAddress = address,
                paperSizeMm = paperSizeMm
            )
        )
    }

    // --- CHECKOUT & TRANSACTIONS ---
    suspend fun generateNextInvoiceNumber(): String {
        val settingsSnap = dao.getStoreSettingsSnapshot() ?: StoreSettingsEntity()
        val prefix = settingsSnap.invoicePrefix.trim().ifEmpty { "INV" }
        val now = System.currentTimeMillis()
        val dateStr = SecurityAndFormatUtils.formatInvoiceDate(now)
        val countToday = dao.getTransactionCountBetween(
            SecurityAndFormatUtils.getStartOfDay(now),
            SecurityAndFormatUtils.getEndOfDay(now)
        ) + 1
        val seq = countToday.toString().padStart(4, '0')
        val randomSuffix = (10..99).random()
        return "$prefix-$dateStr-$seq$randomSuffix"
    }

    suspend fun processCheckout(
        cartItems: List<CartItem>,
        cashier: CashierUserEntity,
        customer: CustomerEntity?,
        discountAmount: Double,
        taxPercentage: Double,
        serviceFee: Double,
        paymentMethod: String,
        amountPaid: Double,
        notes: String,
        customInvoiceNumber: String? = null
    ): Result<TransactionWithItems> = runCatching {
        if (cartItems.isEmpty()) {
            throw IllegalArgumentException("Keranjang belanja masih kosong.")
        }
        if (discountAmount < 0.0) {
            throw IllegalArgumentException("Diskon tidak boleh bernilai negatif.")
        }
        if (serviceFee < 0.0) {
            throw IllegalArgumentException("Biaya tambahan tidak boleh bernilai negatif.")
        }

        val settingsSnap = dao.getStoreSettingsSnapshot() ?: StoreSettingsEntity()
        val subtotal = cartItems.sumOf { it.subtotal }
        val totalCost = cartItems.sumOf { it.totalCost }
        val safeDiscount = discountAmount.coerceAtMost(subtotal)
        val afterDiscount = (subtotal - safeDiscount).coerceAtLeast(0.0)
        val effectiveTaxPercent = if (settingsSnap.enableTax) taxPercentage.coerceAtLeast(0.0) else 0.0
        val taxAmount = afterDiscount * (effectiveTaxPercent / 100.0)
        val rawTotal = afterDiscount + taxAmount + serviceFee

        // Optional price rounding to nearest 100 Rupiah if enabled in settings
        val roundingAmount = if (settingsSnap.enableRounding) {
            val remainder = rawTotal % 100.0
            if (remainder == 0.0) 0.0
            else if (remainder >= 50.0) 100.0 - remainder
            else -remainder
        } else {
            0.0
        }

        val finalTotal = (rawTotal + roundingAmount).coerceAtLeast(0.0)

        if (paymentMethod.equals("Tunai", ignoreCase = true) && amountPaid < finalTotal) {
            throw IllegalArgumentException(
                "Uang diterima (${SecurityAndFormatUtils.formatRupiah(amountPaid)}) kurang dari total tagihan (${SecurityAndFormatUtils.formatRupiah(finalTotal)})."
            )
        }

        val effectivePaid = if (paymentMethod.equals("Tunai", ignoreCase = true)) amountPaid else finalTotal
        val changeAmount = (effectivePaid - finalTotal).coerceAtLeast(0.0)
        val invoiceNo = if (!customInvoiceNumber.isNullOrBlank()) {
            customInvoiceNumber.trim()
        } else {
            generateNextInvoiceNumber()
        }

        val txEntity = TransactionEntity(
            invoiceNumber = invoiceNo,
            timestamp = System.currentTimeMillis(),
            cashierId = cashier.id,
            cashierName = cashier.name,
            customerId = customer?.id,
            customerName = customer?.name ?: "Pelanggan Umum",
            subtotal = subtotal,
            discountAmount = safeDiscount,
            taxPercentage = effectiveTaxPercent,
            taxAmount = taxAmount,
            serviceFee = serviceFee,
            roundingAmount = roundingAmount,
            totalAmount = finalTotal,
            totalCost = totalCost,
            paymentMethod = paymentMethod,
            amountPaid = effectivePaid,
            changeAmount = changeAmount,
            notes = notes.trim(),
            status = "COMPLETED"
        )

        val insertedId = dao.executeCheckoutAtomic(
            transaction = txEntity,
            cartItems = cartItems,
            autoReduceStock = settingsSnap.autoReduceStock,
            allowNegativeStock = settingsSnap.allowNegativeStock
        )

        dao.getTransactionWithItemsById(insertedId)
            ?: throw IllegalStateException("Gagal memuat kembali transaksi setelah disimpan.")
    }

    suspend fun cancelTransaction(
        transactionId: Long,
        cancelReason: String,
        cancelledBy: String
    ): Result<Unit> = runCatching {
        if (cancelReason.trim().isEmpty()) {
            throw IllegalArgumentException("Harap isi alasan pembatalan transaksi.")
        }
        val settingsSnap = dao.getStoreSettingsSnapshot() ?: StoreSettingsEntity()
        dao.cancelTransactionAtomic(
            transactionId = transactionId,
            cancelReason = cancelReason.trim(),
            cancelledBy = cancelledBy,
            restoreStock = settingsSnap.autoReduceStock
        )
    }

    suspend fun getDao(): KasirDao = dao
}
