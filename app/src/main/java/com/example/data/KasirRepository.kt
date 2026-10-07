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
    val transactionsWithItems: Flow<List<TransactionWithItems>> = dao.getAllTransactionsWithItems()
    val unpaidBillingsWithItems: Flow<List<TransactionWithItems>> = dao.getUnpaidBillingsWithItems()

    // --- CATEGORY CRUD & PRODUCT MIGRATION ---
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

    suspend fun getProductCountInCategory(categoryId: Long): Int {
        return dao.countProductsInCategory(categoryId)
    }

    suspend fun moveProductsBetweenCategories(
        fromCategoryId: Long,
        targetCategory: CategoryEntity
    ): Result<Unit> = runCatching {
        if (fromCategoryId == targetCategory.id) {
            throw IllegalArgumentException("Kategori tujuan harus berbeda dengan kategori asal.")
        }
        dao.moveProductsToCategory(
            fromCategoryId = fromCategoryId,
            toCategoryId = targetCategory.id,
            toCategoryName = targetCategory.name
        )
    }

    suspend fun deleteCategoryWithOptionalMove(
        categoryToDelete: CategoryEntity,
        moveToCategory: CategoryEntity? = null
    ): Result<Unit> = runCatching {
        val count = dao.countProductsInCategory(categoryToDelete.id)
        if (count > 0) {
            if (moveToCategory == null) {
                throw IllegalStateException(
                    "Kategori '${categoryToDelete.name}' masih memiliki $count menu. Pindahkan menu ke kategori lain terlebih dahulu."
                )
            }
            if (moveToCategory.id == categoryToDelete.id) {
                throw IllegalArgumentException("Kategori tujuan pemindahan tidak boleh sama dengan kategori yang dihapus.")
            }
            dao.moveProductsToCategory(
                fromCategoryId = categoryToDelete.id,
                toCategoryId = moveToCategory.id,
                toCategoryName = moveToCategory.name
            )
        }
        dao.deleteCategory(categoryToDelete)
    }

    // --- PRODUCT CRUD ---
    suspend fun saveProduct(product: ProductEntity): Result<Long> = runCatching {
        val cleanName = product.name.trim()
        if (cleanName.isEmpty()) {
            throw IllegalArgumentException("Nama menu/produk tidak boleh kosong.")
        }
        if (product.buyPrice < 0.0 || product.sellPrice < 0.0) {
            throw IllegalArgumentException("Harga tidak boleh bernilai negatif.")
        }

        val now = System.currentTimeMillis()
        val toSave = product.copy(
            name = cleanName,
            unit = product.unit.trim().ifEmpty { "Porsi" },
            description = product.description.trim(),
            updatedAt = now
        )

        if (toSave.id == 0L) {
            dao.insertProduct(toSave.copy(createdAt = now))
        } else {
            dao.updateProduct(toSave)
            toSave.id
        }
    }

    suspend fun moveSingleProductToCategory(product: ProductEntity, targetCategory: CategoryEntity): Result<Unit> = runCatching {
        dao.updateProduct(
            product.copy(
                categoryId = targetCategory.id,
                categoryName = targetCategory.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteProduct(product: ProductEntity): Result<Unit> = runCatching {
        dao.deleteProduct(product)
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
            throw IllegalArgumentException("Nama restoran/toko tidak boleh kosong.")
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

    // --- BILLING NUMBER & ORDERING ("PROSES PESAN") ---
    suspend fun getNextBillingNumber(): Int {
        return dao.getMaxBillingNumber() + 1
    }

    suspend fun sendOrderToKitchen(
        existingTransactionId: Long?,
        billingNumber: Int?,
        cartItems: List<CartItem>,
        cashier: CashierUserEntity,
        customer: CustomerEntity?,
        notes: String
    ): Result<TransactionWithItems> = runCatching {
        if (cartItems.isEmpty()) {
            throw IllegalArgumentException("Pilih menu terlebih dahulu sebelum mengirim pesanan.")
        }
        val effectiveBillingNumber = if (billingNumber != null && billingNumber > 0) {
            billingNumber
        } else {
            getNextBillingNumber()
        }

        val savedTxId = dao.saveOrderToKitchenAtomic(
            existingTransactionId = existingTransactionId,
            billingNumber = effectiveBillingNumber,
            cashier = cashier,
            customer = customer,
            cartItems = cartItems,
            notes = notes
        )

        dao.getTransactionWithItemsById(savedTxId)
            ?: throw IllegalStateException("Gagal memuat data pesanan setelah disimpan.")
    }

    // --- PAYMENT ("PROSES BAYAR") ---
    suspend fun processBillingPayment(
        transactionId: Long,
        cashier: CashierUserEntity,
        discountAmount: Double,
        serviceFee: Double,
        paymentMethod: String,
        amountPaid: Double
    ): Result<TransactionWithItems> = runCatching {
        val existingTxWithItems = dao.getTransactionWithItemsById(transactionId)
            ?: throw IllegalStateException("Billing tidak ditemukan.")
        if (existingTxWithItems.items.isEmpty()) {
            throw IllegalStateException("Billing ini tidak memiliki item pesanan.")
        }
        if (discountAmount < 0.0) {
            throw IllegalArgumentException("Diskon tidak boleh bernilai negatif.")
        }
        if (serviceFee < 0.0) {
            throw IllegalArgumentException("Biaya tambahan tidak boleh bernilai negatif.")
        }

        val settingsSnap = dao.getStoreSettingsSnapshot() ?: StoreSettingsEntity()
        val subtotal = existingTxWithItems.items.sumOf { it.subtotal }
        val safeDiscount = if (settingsSnap.enableDiscount) discountAmount.coerceIn(0.0, subtotal) else 0.0
        val afterDiscount = (subtotal - safeDiscount).coerceAtLeast(0.0)
        val effectiveTaxPercent = if (settingsSnap.enableTax) settingsSnap.defaultTaxPercent.coerceAtLeast(0.0) else 0.0
        val taxAmount = afterDiscount * (effectiveTaxPercent / 100.0)
        val rawTotal = afterDiscount + taxAmount + serviceFee

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
            val shortage = finalTotal - amountPaid
            throw IllegalArgumentException(
                "Uang tunai kurang ${SecurityAndFormatUtils.formatRupiah(shortage)} (Total: ${SecurityAndFormatUtils.formatRupiah(finalTotal)}, Tunai: ${SecurityAndFormatUtils.formatRupiah(amountPaid)})."
            )
        }

        val effectivePaid = if (paymentMethod.equals("Tunai", ignoreCase = true)) amountPaid else finalTotal
        val changeAmount = (effectivePaid - finalTotal).coerceAtLeast(0.0)

        dao.completeBillingPaymentAtomic(
            transactionId = transactionId,
            cashier = cashier,
            discountAmount = safeDiscount,
            taxPercentage = effectiveTaxPercent,
            taxAmount = taxAmount,
            serviceFee = serviceFee,
            roundingAmount = roundingAmount,
            totalAmount = finalTotal,
            paymentMethod = paymentMethod,
            amountPaid = effectivePaid,
            changeAmount = changeAmount
        )

        dao.getTransactionWithItemsById(transactionId)
            ?: throw IllegalStateException("Gagal memuat kembali transaksi setelah pembayaran.")
    }

    suspend fun cancelTransaction(
        transactionId: Long,
        cancelReason: String
    ): Result<Unit> = runCatching {
        if (cancelReason.trim().isEmpty()) {
            throw IllegalArgumentException("Harap isi alasan pembatalan billing/transaksi.")
        }
        dao.cancelTransactionAtomic(
            transactionId = transactionId,
            cancelReason = cancelReason.trim()
        )
    }

    suspend fun getDao(): KasirDao = dao
}
