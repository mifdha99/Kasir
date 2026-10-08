package com.example.data

import androidx.room.withTransaction
import com.example.util.SecurityAndFormatUtils
import kotlinx.coroutines.flow.Flow

class KasirRepository(private val database: KasirDatabase) {
    private val dao = database.kasirDao()

    val allCategories: Flow<List<CategoryEntity>> = dao.getAllCategories()
    val allPaymentMethods: Flow<List<PaymentMethodEntity>> = dao.getAllPaymentMethods()
    val allProducts: Flow<List<ProductEntity>> = dao.getAllProducts()
    val allCustomers: Flow<List<CustomerEntity>> = dao.getAllCustomers()
    val allTransactionsWithItems: Flow<List<TransactionWithItems>> = dao.getAllTransactionsWithItems()
    val unpaidTransactionsWithItems: Flow<List<TransactionWithItems>> = dao.getUnpaidTransactionsWithItems()
    val allDebtPayments: Flow<List<DebtPaymentEntity>> = dao.getAllDebtPayments()
    val allCashierUsers: Flow<List<CashierUserEntity>> = dao.getAllCashierUsers()
    val storeSettings: Flow<StoreSettingsEntity?> = dao.getStoreSettings()

    suspend fun ensureInitialData() {
        val currentSettings = dao.getStoreSettingsOnce()
        if (currentSettings == null) {
            dao.saveStoreSettings(StoreSettingsEntity())
        }

        if (dao.getPaymentMethodCount() == 0) {
            val now = System.currentTimeMillis()
            val defaultPaymentMethods = listOf(
                PaymentMethodEntity(name = "TUNAI", isCashType = true, sortOrder = 1, createdAt = now),
                PaymentMethodEntity(name = "TRANSFER", isCashType = false, sortOrder = 2, createdAt = now),
                PaymentMethodEntity(name = "QRIS", isCashType = false, sortOrder = 3, createdAt = now),
                PaymentMethodEntity(name = "GOJEK", isCashType = false, sortOrder = 4, createdAt = now),
                PaymentMethodEntity(name = "SHOPEE", isCashType = false, sortOrder = 5, createdAt = now),
                PaymentMethodEntity(name = "GRAB", isCashType = false, sortOrder = 6, createdAt = now)
            )
            dao.insertPaymentMethods(defaultPaymentMethods)
        }

        if (dao.getCashierUserCount() == 0) {
            dao.insertCashierUser(
                CashierUserEntity(
                    name = "Admin Utama",
                    role = "ADMIN",
                    pinHash = SecurityAndFormatUtils.hashPin("1234"),
                    isActive = true,
                    canEditPrice = true,
                    canGiveDiscount = true,
                    canCancelTransaction = true,
                    canViewReports = true,
                    canManageStock = true
                )
            )
            dao.insertCashierUser(
                CashierUserEntity(
                    name = "Kasir Shift 1",
                    role = "KASIR",
                    pinHash = SecurityAndFormatUtils.hashPin("0000"),
                    isActive = true,
                    canEditPrice = false,
                    canGiveDiscount = true,
                    canCancelTransaction = false,
                    canViewReports = false,
                    canManageStock = false
                )
            )
        }

        if (dao.getCategoryCount() == 0 && dao.getProductCount() == 0) {
            val catMakananId = dao.insertCategory(
                CategoryEntity(
                    name = "Makanan",
                    description = "Menu makanan utama & hidangan",
                    colorHex = "#0F766E",
                    iconName = "Restaurant"
                )
            )
            val catMinumanId = dao.insertCategory(
                CategoryEntity(
                    name = "Minuman",
                    description = "Aneka minuman dingin & hangat",
                    colorHex = "#0284C7",
                    iconName = "LocalCafe"
                )
            )
            val catSnackId = dao.insertCategory(
                CategoryEntity(
                    name = "Snack",
                    description = "Cemilan, gorengan & lauk tambahan",
                    colorHex = "#D97706",
                    iconName = "Fastfood"
                )
            )

            val starterMenus = listOf(
                ProductEntity(
                    name = "Nasi Goreng Spesial",
                    sellPrice = 18000.0,
                    unit = "Porsi",
                    categoryId = catMakananId,
                    categoryName = "Makanan",
                    description = "Nasi goreng telur + ayam suwir + kerupuk"
                ),
                ProductEntity(
                    name = "Mie Goreng Jawa",
                    sellPrice = 15000.0,
                    unit = "Porsi",
                    categoryId = catMakananId,
                    categoryName = "Makanan",
                    description = "Mie goreng bumbu rempah dengan sayuran"
                ),
                ProductEntity(
                    name = "Ayam Bakar Madu + Nasi",
                    sellPrice = 22000.0,
                    unit = "Porsi",
                    categoryId = catMakananId,
                    categoryName = "Makanan",
                    description = "Ayam bakar madu lengkap lalapan & sambal"
                ),
                ProductEntity(
                    name = "Soto Ayam Lamongan",
                    sellPrice = 15000.0,
                    unit = "Porsi",
                    categoryId = catMakananId,
                    categoryName = "Makanan",
                    description = "Soto ayam kuah kuning koya gurih"
                ),
                ProductEntity(
                    name = "Es Teh Manis",
                    sellPrice = 5000.0,
                    unit = "Gelas",
                    categoryId = catMinumanId,
                    categoryName = "Minuman",
                    description = "Es teh manis segar"
                ),
                ProductEntity(
                    name = "Es Jeruk Peras",
                    sellPrice = 7000.0,
                    unit = "Gelas",
                    categoryId = catMinumanId,
                    categoryName = "Minuman",
                    description = "Jeruk peras asli dingin / hangat"
                ),
                ProductEntity(
                    name = "Kopi Susu Gula Aren",
                    sellPrice = 12000.0,
                    unit = "Cup",
                    categoryId = catMinumanId,
                    categoryName = "Minuman",
                    description = "Es kopi susu creamy gula aren"
                ),
                ProductEntity(
                    name = "Tempe Mendoan Hangat",
                    sellPrice = 8000.0,
                    unit = "Porsi",
                    categoryId = catSnackId,
                    categoryName = "Snack",
                    description = "Isi 4 potong + sambal kecap rawit"
                ),
                ProductEntity(
                    name = "Kentang Goreng Crispy",
                    sellPrice = 12000.0,
                    unit = "Porsi",
                    categoryId = catSnackId,
                    categoryName = "Snack",
                    description = "Kentang goreng saus sambal & tomat"
                )
            )
            dao.insertProducts(starterMenus)
        }
    }

    // --- Categories ---
    suspend fun saveCategory(category: CategoryEntity): Long {
        return database.withTransaction {
            if (category.id == 0L) {
                dao.insertCategory(category)
            } else {
                dao.updateCategory(category)
                dao.updateProductsCategoryName(category.id, category.name)
                category.id
            }
        }
    }

    suspend fun getProductCountByCategory(categoryId: Long): Int {
        return dao.getProductCountByCategory(categoryId)
    }

    suspend fun reassignProductsCategory(
        oldCategoryId: Long,
        newCategoryId: Long,
        newCategoryName: String
    ) {
        dao.reassignProductsCategory(oldCategoryId, newCategoryId, newCategoryName)
    }

    suspend fun deleteCategoryWithReassignment(
        categoryToDelete: CategoryEntity,
        targetCategory: CategoryEntity?
    ) {
        database.withTransaction {
            if (targetCategory != null) {
                dao.reassignProductsCategory(
                    oldCategoryId = categoryToDelete.id,
                    newCategoryId = targetCategory.id,
                    newCategoryName = targetCategory.name
                )
            }
            dao.deleteCategory(categoryToDelete)
        }
    }

    // --- Payment Methods ---
    suspend fun getAllPaymentMethodsOnce(): List<PaymentMethodEntity> {
        return dao.getAllPaymentMethodsOnce()
    }

    suspend fun addPaymentMethod(name: String, isCashType: Boolean): Long {
        val cleanName = name.trim().uppercase()
        val existing = dao.getAllPaymentMethodsOnce()
        val nextSortOrder = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1
        return dao.insertPaymentMethod(
            PaymentMethodEntity(
                name = cleanName,
                isCashType = isCashType || cleanName == "TUNAI" || cleanName == "CASH",
                sortOrder = nextSortOrder
            )
        )
    }

    suspend fun updatePaymentMethod(method: PaymentMethodEntity, newName: String, isCashType: Boolean) {
        val cleanName = newName.trim().uppercase()
        dao.updatePaymentMethod(
            method.copy(
                name = cleanName,
                isCashType = isCashType || cleanName == "TUNAI" || cleanName == "CASH"
            )
        )
    }

    suspend fun deletePaymentMethod(method: PaymentMethodEntity) {
        dao.deletePaymentMethod(method)
    }

    // --- Products (Menus) ---
    suspend fun saveProduct(product: ProductEntity): Long {
        return if (product.id == 0L) {
            dao.insertProduct(product)
        } else {
            dao.updateProduct(product.copy(updatedAt = System.currentTimeMillis()))
            product.id
        }
    }

    suspend fun deleteProduct(product: ProductEntity) {
        dao.deleteProduct(product)
    }

    // --- Customers ---
    suspend fun saveCustomer(customer: CustomerEntity): Long {
        return if (customer.id == 0L) {
            dao.insertCustomer(customer)
        } else {
            dao.updateCustomer(customer)
            customer.id
        }
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        dao.deleteCustomer(customer)
    }

    // --- Daily Billing Counter & Orders ---
    suspend fun getNextBillingNumberForToday(nowMillis: Long = System.currentTimeMillis()): Int {
        val todayKey = SecurityAndFormatUtils.formatDateKey(nowMillis)
        val startOfDay = SecurityAndFormatUtils.getStartOfDay(nowMillis)
        val endOfDay = SecurityAndFormatUtils.getEndOfDay(nowMillis)
        val maxInTransactions = dao.getMaxBillingNumberForToday(todayKey, startOfDay, endOfDay)
        val settings = dao.getStoreSettingsOnce()
        val maxInSettings = if (settings != null && settings.lastBillingDate == todayKey) {
            settings.lastBillingSequence
        } else {
            0
        }
        return maxOf(maxInTransactions, maxInSettings) + 1
    }

    suspend fun createKitchenOrderBilling(
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>
    ): TransactionWithItems {
        return database.withTransaction {
            val now = transaction.timestamp
            val todayKey = SecurityAndFormatUtils.formatDateKey(now)
            val startOfDay = SecurityAndFormatUtils.getStartOfDay(now)
            val endOfDay = SecurityAndFormatUtils.getEndOfDay(now)

            val maxInTx = dao.getMaxBillingNumberForToday(todayKey, startOfDay, endOfDay)
            val settings = dao.getStoreSettingsOnce() ?: StoreSettingsEntity()
            val maxInSettings = if (settings.lastBillingDate == todayKey) settings.lastBillingSequence else 0
            val nextSequentialBilling = maxOf(maxInTx, maxInSettings) + 1

            // Ensure the new billing number is strictly >= nextSequentialBilling so it NEVER resets to Billing 1 on the same day
            val finalBillingNumber = maxOf(transaction.billingNumber, nextSequentialBilling)
            val finalInvoiceNumber = SecurityAndFormatUtils.generateInvoiceNumber(
                prefix = settings.invoicePrefix,
                billingNumber = finalBillingNumber,
                timestamp = now
            )

            val txToInsert = transaction.copy(
                billingNumber = finalBillingNumber,
                billingDate = todayKey,
                invoiceNumber = finalInvoiceNumber
            )

            val txId = dao.insertTransaction(txToInsert)
            val itemsWithTxId = items.map { it.copy(transactionId = txId) }
            dao.insertTransactionItems(itemsWithTxId)

            dao.saveStoreSettings(
                settings.copy(
                    lastBillingDate = todayKey,
                    lastBillingSequence = finalBillingNumber
                )
            )

            val savedTx = txToInsert.copy(id = txId)
            TransactionWithItems(savedTx, itemsWithTxId)
        }
    }

    suspend fun updateKitchenOrderBilling(
        existingTransaction: TransactionEntity,
        items: List<TransactionItemEntity>
    ): TransactionWithItems {
        return database.withTransaction {
            val effectiveBillingDate = existingTransaction.billingDate.ifBlank {
                SecurityAndFormatUtils.formatDateKey(existingTransaction.timestamp)
            }
            val updatedTx = existingTransaction.copy(billingDate = effectiveBillingDate)
            dao.updateTransaction(updatedTx)
            dao.deleteTransactionItemsForTransaction(updatedTx.id)
            val itemsWithTxId = items.map { it.copy(id = 0L, transactionId = updatedTx.id) }
            dao.insertTransactionItems(itemsWithTxId)
            TransactionWithItems(updatedTx, itemsWithTxId)
        }
    }

    suspend fun completeBillingPayment(
        updatedTransaction: TransactionEntity
    ): TransactionWithItems {
        return database.withTransaction {
            val effectiveBillingDate = updatedTransaction.billingDate.ifBlank {
                SecurityAndFormatUtils.formatDateKey(updatedTransaction.timestamp)
            }
            val finalTx = updatedTransaction.copy(billingDate = effectiveBillingDate)
            dao.updateTransaction(finalTx)
            if (finalTx.isDebt && finalTx.customerId != null) {
                val unpaidRemainder = (finalTx.totalAmount - finalTx.amountPaid).coerceAtLeast(0.0)
                if (unpaidRemainder > 0) {
                    val customer = dao.getCustomerById(finalTx.customerId)
                    if (customer != null) {
                        dao.updateCustomer(customer.copy(totalDebt = customer.totalDebt + unpaidRemainder))
                    }
                }
            }
            dao.getTransactionWithItemsById(finalTx.id)
                ?: TransactionWithItems(finalTx, emptyList())
        }
    }

    suspend fun cancelTransaction(
        txWithItems: TransactionWithItems,
        reason: String
    ) {
        database.withTransaction {
            val tx = txWithItems.transaction
            if (tx.status == "CANCELLED") return@withTransaction

            val updatedTx = tx.copy(
                status = "CANCELLED",
                cancelReason = reason
            )
            dao.updateTransaction(updatedTx)

            if (tx.isDebt && tx.customerId != null) {
                val unpaidDebt = (tx.totalAmount - tx.debtPaidAmount).coerceAtLeast(0.0)
                val customer = dao.getCustomerById(tx.customerId)
                if (customer != null && unpaidDebt > 0) {
                    dao.updateCustomer(
                        customer.copy(totalDebt = (customer.totalDebt - unpaidDebt).coerceAtLeast(0.0))
                    )
                }
            }
        }
    }

    // --- Debt Payment ---
    suspend fun recordDebtPayment(
        customer: CustomerEntity,
        amount: Double,
        paymentMethod: String,
        notes: String,
        cashierName: String
    ) {
        database.withTransaction {
            val payment = DebtPaymentEntity(
                customerId = customer.id,
                customerName = customer.name,
                amount = amount,
                paymentMethod = paymentMethod,
                notes = notes,
                cashierName = cashierName
            )
            dao.insertDebtPayment(payment)
            val newDebt = (customer.totalDebt - amount).coerceAtLeast(0.0)
            dao.updateCustomer(customer.copy(totalDebt = newDebt))
        }
    }

    // --- Cashier Users & Settings ---
    suspend fun saveCashierUser(user: CashierUserEntity): Long {
        return if (user.id == 0L) {
            dao.insertCashierUser(user)
        } else {
            dao.updateCashierUser(user)
            user.id
        }
    }

    suspend fun deleteCashierUser(user: CashierUserEntity) {
        dao.deleteCashierUser(user)
    }

    suspend fun saveStoreSettings(settings: StoreSettingsEntity) {
        val current = dao.getStoreSettingsOnce()
        val preservedDate = settings.lastBillingDate.ifBlank { current?.lastBillingDate.orEmpty() }
        val preservedSeq = if (settings.lastBillingSequence > 0) {
            settings.lastBillingSequence
        } else {
            current?.lastBillingSequence ?: 0
        }
        dao.saveStoreSettings(
            settings.copy(
                id = 1,
                lastBillingDate = preservedDate,
                lastBillingSequence = preservedSeq
            )
        )
    }

    // --- Snapshot for Backup / Restore ---
    suspend fun getDatabaseSnapshot(): DatabaseBackupSnapshot {
        val categories = dao.getAllCategoriesOnce()
        val paymentMethods = dao.getAllPaymentMethodsOnce()
        val products = dao.getAllProductsOnce()
        val customers = dao.getAllCustomersOnce()
        val transactionsWithItems = dao.getAllTransactionsWithItemsOnce()
        val transactions = transactionsWithItems.map { it.transaction }
        val transactionItems = dao.getAllTransactionItemsOnce()
        val debtPayments = dao.getAllDebtPaymentsOnce()
        val cashierUsers = dao.getAllCashierUsersOnce()
        val settings = dao.getStoreSettingsOnce() ?: StoreSettingsEntity()

        return DatabaseBackupSnapshot(
            categories = categories,
            paymentMethods = paymentMethods,
            products = products,
            customers = customers,
            transactions = transactions,
            transactionItems = transactionItems,
            debtPayments = debtPayments,
            cashierUsers = cashierUsers,
            storeSettings = settings
        )
    }

    suspend fun restoreDatabaseSnapshot(snapshot: DatabaseBackupSnapshot) {
        database.withTransaction {
            dao.clearAllTransactionItems()
            dao.clearAllTransactions()
            dao.clearAllDebtPayments()
            dao.clearAllProducts()
            dao.clearAllCategories()
            dao.clearAllCustomers()
            dao.clearAllCashierUsers()

            if (snapshot.categories.isNotEmpty()) dao.insertCategories(snapshot.categories)
            if (snapshot.paymentMethods.isNotEmpty()) {
                dao.clearAllPaymentMethods()
                dao.insertPaymentMethods(snapshot.paymentMethods)
            }
            if (snapshot.products.isNotEmpty()) dao.insertProducts(snapshot.products)
            if (snapshot.customers.isNotEmpty()) dao.insertCustomers(snapshot.customers)
            if (snapshot.transactions.isNotEmpty()) dao.insertTransactions(snapshot.transactions)
            if (snapshot.transactionItems.isNotEmpty()) dao.insertTransactionItems(snapshot.transactionItems)
            if (snapshot.debtPayments.isNotEmpty()) dao.insertDebtPayments(snapshot.debtPayments)
            if (snapshot.cashierUsers.isNotEmpty()) dao.insertCashierUsers(snapshot.cashierUsers)
            dao.saveStoreSettings(snapshot.storeSettings)
        }
        ensureInitialData()
    }

    suspend fun resetTransactionsHistoryOnly() {
        database.withTransaction {
            dao.clearAllTransactionItems()
            dao.clearAllTransactions()
            dao.clearAllDebtPayments()
            val customers = dao.getAllCustomersOnce()
            customers.forEach { c ->
                if (c.totalDebt != 0.0) {
                    dao.updateCustomer(c.copy(totalDebt = 0.0))
                }
            }
            val settings = dao.getStoreSettingsOnce()
            if (settings != null) {
                dao.saveStoreSettings(settings.copy(lastBillingDate = "", lastBillingSequence = 0))
            }
        }
    }

    suspend fun resetAllFactoryData() {
        database.withTransaction {
            dao.clearAllTransactionItems()
            dao.clearAllTransactions()
            dao.clearAllDebtPayments()
            dao.clearAllProducts()
            dao.clearAllCategories()
            dao.clearAllPaymentMethods()
            dao.clearAllCustomers()
            dao.clearAllCashierUsers()
            dao.saveStoreSettings(StoreSettingsEntity())
        }
        ensureInitialData()
    }
}

data class DatabaseBackupSnapshot(
    val version: Int = 3,
    val exportedAt: Long = System.currentTimeMillis(),
    val categories: List<CategoryEntity> = emptyList(),
    val paymentMethods: List<PaymentMethodEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val transactionItems: List<TransactionItemEntity> = emptyList(),
    val debtPayments: List<DebtPaymentEntity> = emptyList(),
    val cashierUsers: List<CashierUserEntity> = emptyList(),
    val storeSettings: StoreSettingsEntity = StoreSettingsEntity()
)
