package com.example.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.util.SecurityAndFormatUtils

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val colorHex: String = "#0F766E",
    val iconName: String = "Category",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sku: String = "",
    val barcode: String = "",
    val costPrice: Double = 0.0,
    val sellPrice: Double,
    val stock: Int = 0,
    val minStock: Int = 0,
    val unit: String = "Porsi",
    val categoryId: Long,
    val categoryName: String,
    val description: String = "",
    val imageUri: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "payment_methods")
data class PaymentMethodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isCashType: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val requiresCashInput: Boolean
        get() = isCashType ||
            name.trim().equals("TUNAI", ignoreCase = true) ||
            name.trim().equals("CASH", ignoreCase = true)
}

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val address: String = "",
    val notes: String = "",
    val totalDebt: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val billingNumber: Int = 1,
    val billingDate: String = "", // yyyy-MM-dd of when the billing was created
    val timestamp: Long = System.currentTimeMillis(),
    val cashierId: Long = 1,
    val cashierName: String = "Admin",
    val customerId: Long? = null,
    val customerName: String = "Pelanggan Umum",
    val subtotal: Double,
    val discountAmount: Double = 0.0,
    val discountType: String = "NOMINAL", // NOMINAL or PERCENT
    val discountInput: Double = 0.0,
    val taxPercentage: Double = 0.0,
    val taxAmount: Double = 0.0,
    val serviceFee: Double = 0.0,
    val roundingAmount: Double = 0.0,
    val totalAmount: Double,
    val paymentMethodId: Long? = null,
    val paymentMethod: String, // Snapshot name at transaction time: TUNAI, QRIS, TRANSFER, etc.
    val amountPaid: Double,
    val changeAmount: Double,
    val notes: String = "",
    val status: String = "UNPAID", // UNPAID, PAID, COMPLETED (legacy paid), CANCELLED
    val cancelReason: String = "",
    val isDebt: Boolean = false,
    val debtPaidAmount: Double = 0.0
) {
    val isPaidStatus: Boolean
        get() = status == "PAID" || status == "COMPLETED"

    val billingDisplay: String
        get() = if (billingNumber > 0) "Billing $billingNumber" else invoiceNumber
}

@Entity(tableName = "transaction_items")
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val productId: Long,
    val productName: String,
    val sku: String = "",
    val unit: String = "Porsi",
    val costPrice: Double = 0.0,
    val sellPrice: Double,
    val quantity: Int,
    val itemNote: String = "",
    val subtotal: Double
) {
    val portionNotes: List<String>
        get() = SecurityAndFormatUtils.decodePortionNotes(itemNote, quantity)
}

data class TransactionWithItems(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    )
    val items: List<TransactionItemEntity>
)

@Entity(tableName = "stock_mutations")
data class StockMutationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val type: String,
    val quantityChange: Int,
    val previousStock: Int,
    val newStock: Int,
    val reason: String,
    val referenceInvoice: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val cashierName: String = "Admin"
)

@Entity(tableName = "debt_payments")
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val customerName: String,
    val transactionId: Long? = null,
    val amount: Double,
    val paymentMethod: String = "TUNAI",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val cashierName: String = "Admin"
)

@Entity(tableName = "cashier_users")
data class CashierUserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String, // ADMIN or KASIR
    val pinHash: String,
    val isActive: Boolean = true,
    val canEditPrice: Boolean = true,
    val canGiveDiscount: Boolean = true,
    val canCancelTransaction: Boolean = true,
    val canViewReports: Boolean = true,
    val canManageStock: Boolean = true
)

@Entity(tableName = "store_settings")
data class StoreSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val storeName: String = "KasirKu Warung & Resto",
    val storeAddress: String = "Jl. Merdeka No. 10, Indonesia",
    val storePhone: String = "0812-3456-7890",
    val storeEmail: String = "",
    val storeLogoUri: String = "",
    val receiptHeader: String = "Selamat Datang",
    val receiptFooter: String = "Terima Kasih Atas Kunjungan Anda",
    val currencySymbol: String = "Rp",
    val defaultTaxPercentage: Double = 0.0,
    val defaultServiceFee: Double = 0.0,
    val enableTaxByDefault: Boolean = false,
    val enableRounding: Boolean = false,
    val roundingMultiple: Int = 100,
    val autoPrintReceipt: Boolean = false,
    val autoPrintKitchenTicket: Boolean = false,
    val printCopies: Int = 1,
    val paperSizeMm: Int = 58, // 58 or 80
    val defaultPrinterName: String = "",
    val defaultPrinterAddress: String = "",
    val enableCash: Boolean = true,
    val enableQris: Boolean = true,
    val enableTransfer: Boolean = true,
    val enableDebit: Boolean = true,
    val enableCredit: Boolean = true,
    val enableOtherPayment: Boolean = true,
    val allowNegativeStock: Boolean = true,
    val showLowStockAlert: Boolean = false,
    val requirePinOnStartup: Boolean = false,
    val isDarkMode: Boolean = false,
    val productViewMode: String = "GRID", // GRID or LIST
    val gridColumns: Int = 2,
    val invoicePrefix: String = "BIL",
    val lastBillingDate: String = "",
    val lastBillingSequence: Int = 0
)

data class CartItem(
    val product: ProductEntity,
    val quantity: Int,
    val portionNotes: List<String> = emptyList()
) {
    val subtotal: Double
        get() = product.sellPrice * quantity

    val normalizedPortionNotes: List<String>
        get() = SecurityAndFormatUtils.normalizePortionNotes(portionNotes, quantity)

    val encodedItemNote: String
        get() = SecurityAndFormatUtils.encodePortionNotes(normalizedPortionNotes, quantity)
}
