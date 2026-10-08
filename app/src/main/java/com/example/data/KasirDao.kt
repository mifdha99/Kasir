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
    suspend fun getAllCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    // --- PAYMENT METHODS ---
    @Query("SELECT * FROM payment_methods ORDER BY sortOrder ASC, id ASC")
    fun getAllPaymentMethods(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllPaymentMethodsOnce(): List<PaymentMethodEntity>

    @Query("SELECT COUNT(*) FROM payment_methods")
    suspend fun getPaymentMethodCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentMethod(method: PaymentMethodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentMethods(methods: List<PaymentMethodEntity>)

    @Update
    suspend fun updatePaymentMethod(method: PaymentMethodEntity)

    @Delete
    suspend fun deletePaymentMethod(method: PaymentMethodEntity)

    @Query("DELETE FROM payment_methods")
    suspend fun clearAllPaymentMethods()

    // --- PRODUCTS (MENUS) ---
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsOnce(): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Query("SELECT COUNT(*) FROM products WHERE categoryId = :categoryId")
    suspend fun getProductCountByCategory(categoryId: Long): Int

    @Query("UPDATE products SET categoryId = :newCategoryId, categoryName = :newCategoryName, updatedAt = :updatedAt WHERE categoryId = :oldCategoryId")
    suspend fun reassignProductsCategory(
        oldCategoryId: Long,
        newCategoryId: Long,
        newCategoryName: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE products SET categoryName = :newCategoryName, updatedAt = :updatedAt WHERE categoryId = :categoryId")
    suspend fun updateProductsCategoryName(
        categoryId: Long,
        newCategoryName: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    // --- CUSTOMERS ---
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY name ASC")
    suspend fun getAllCustomersOnce(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    // --- TRANSACTIONS & BILLINGS ---
    @Transaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsWithItems(): Flow<List<TransactionWithItems>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE status = 'UNPAID' ORDER BY billingNumber ASC, timestamp ASC")
    fun getUnpaidTransactionsWithItems(): Flow<List<TransactionWithItems>>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsWithItemsOnce(): List<TransactionWithItems>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionWithItemsById(transactionId: Long): TransactionWithItems?

    @Query(
        "SELECT COALESCE(MAX(billingNumber), 0) FROM transactions " +
            "WHERE billingDate = :todayDateKey OR (billingDate = '' AND timestamp BETWEEN :startOfDay AND :endOfDay)"
    )
    suspend fun getMaxBillingNumberForToday(
        todayDateKey: String,
        startOfDay: Long,
        endOfDay: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItems(items: List<TransactionItemEntity>)

    @Query("DELETE FROM transaction_items WHERE transactionId = :transactionId")
    suspend fun deleteTransactionItemsForTransaction(transactionId: Long)

    @Query("SELECT * FROM transaction_items")
    suspend fun getAllTransactionItemsOnce(): List<TransactionItemEntity>

    // --- DEBT PAYMENTS ---
    @Query("SELECT * FROM debt_payments ORDER BY timestamp DESC")
    fun getAllDebtPayments(): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debt_payments ORDER BY timestamp DESC")
    suspend fun getAllDebtPaymentsOnce(): List<DebtPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPayment(payment: DebtPaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPayments(payments: List<DebtPaymentEntity>)

    // --- CASHIER USERS ---
    @Query("SELECT * FROM cashier_users ORDER BY id ASC")
    fun getAllCashierUsers(): Flow<List<CashierUserEntity>>

    @Query("SELECT * FROM cashier_users ORDER BY id ASC")
    suspend fun getAllCashierUsersOnce(): List<CashierUserEntity>

    @Query("SELECT COUNT(*) FROM cashier_users")
    suspend fun getCashierUserCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashierUser(user: CashierUserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashierUsers(users: List<CashierUserEntity>)

    @Update
    suspend fun updateCashierUser(user: CashierUserEntity)

    @Delete
    suspend fun deleteCashierUser(user: CashierUserEntity)

    // --- STORE SETTINGS ---
    @Query("SELECT * FROM store_settings WHERE id = 1 LIMIT 1")
    fun getStoreSettings(): Flow<StoreSettingsEntity?>

    @Query("SELECT * FROM store_settings WHERE id = 1 LIMIT 1")
    suspend fun getStoreSettingsOnce(): StoreSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStoreSettings(settings: StoreSettingsEntity)

    // --- CLEAR DATA FOR RESTORE / RESET ---
    @Query("DELETE FROM transaction_items")
    suspend fun clearAllTransactionItems()

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    @Query("DELETE FROM debt_payments")
    suspend fun clearAllDebtPayments()

    @Query("DELETE FROM products")
    suspend fun clearAllProducts()

    @Query("DELETE FROM categories")
    suspend fun clearAllCategories()

    @Query("DELETE FROM customers")
    suspend fun clearAllCustomers()

    @Query("DELETE FROM cashier_users")
    suspend fun clearAllCashierUsers()
}
