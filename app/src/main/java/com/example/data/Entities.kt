package com.example.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.util.SecurityAndFormatUtils

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = false)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String = "ShoppingBag",
    val colorHex: String = "#0F766E",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["barcode"]),
        Index(value = ["sku"]),
        Index(value = ["name"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sku: String = "",
    val barcode: String = "",
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val stock: Int = 0,
    val minStock: Int = 5,
    val unit: String = "Pcs",
    val categoryId: Long = 0,
    val categoryName: String = "Umum",
    val description: String = "",
    val imageUri: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "customers",
    indices = [Index(value = ["name"]), Index(value = ["phone"])]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val totalTransactions: Int = 0,
    val totalPurchase: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class CashierUserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pinHash: String = SecurityAndFormatUtils.hashPin("1234"),
    val role: String = "ADMIN", // "ADMIN" or "KASIR"
    val canGiveDiscount: Boolean = true,
    val canManageStock: Boolean = true,
    val canVoidTransaction: Boolean = true,
    val canViewReports: Boolean = true,
    val canManageSettings: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["invoiceNumber"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["customerId"]),
        Index(value = ["status"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val cashierId: Long = 1,
    val cashierName: String = "Admin Kasir",
    val customerId: Long? = null,
    val customerName: String = "Pelanggan Umum",
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxPercentage: Double = 0.0,
    val taxAmount: Double = 0.0,
    val serviceFee: Double = 0.0,
    val roundingAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val totalCost: Double = 0.0,
    val paymentMethod: String = "Tunai", // Tunai, QRIS, Transfer, Debit, Kredit, Lainnya
    val amountPaid: Double = 0.0,
    val changeAmount: Double = 0.0,
    val notes: String = "",
    val status: String = "COMPLETED", // COMPLETED, CANCELLED
    val cancelReason: String = "",
    val cancelledAt: Long? = null
)

@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transactionId"]), Index(value = ["productId"])]
)
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long = 0,
    val productId: Long,
    val productName: String,
    val sku: String = "",
    val categoryName: String = "Umum",
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val quantity: Int = 1,
    val unit: String = "Pcs",
    val itemNote: String = "",
    val subtotal: Double = 0.0
)

data class TransactionWithItems(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    )
    val items: List<TransactionItemEntity>
)

@Entity(
    tableName = "stock_movements",
    indices = [Index(value = ["productId"]), Index(value = ["timestamp"])]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val type: String, // "IN" (Stok Masuk), "OUT" (Stok Keluar), "ADJUSTMENT" (Penyesuaian), "SALE" (Penjualan), "REFUND" (Pembatalan)
    val quantityChange: Int,
    val previousStock: Int,
    val newStock: Int,
    val note: String = "",
    val referenceInvoice: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val userName: String = "Admin"
)

@Entity(tableName = "settings")
data class StoreSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val storeName: String = "Toko KasirKu Nusantara",
    val storeLogoUri: String = "",
    val storeAddress: String = "Jl. Merdeka Raya No. 88, Jakarta",
    val storePhone: String = "0812-3456-7890",
    val storeEmail: String = "halo@kasirku.id",
    val storeNpwp: String = "",
    val receiptFooter: String = "Terima kasih telah berbelanja!\nBarang yang sudah dibeli tidak dapat ditukar/dikembalikan.",
    // Transaction settings
    val enableDiscount: Boolean = true,
    val enableTax: Boolean = false,
    val defaultTaxPercent: Double = 11.0,
    val defaultServiceFee: Double = 0.0,
    val enableRounding: Boolean = false,
    val autoInvoiceNumber: Boolean = true,
    val invoicePrefix: String = "INV",
    // Printer settings
    val defaultPrinterName: String = "",
    val defaultPrinterAddress: String = "",
    val paperSizeMm: Int = 58, // 58 or 80
    val printCopies: Int = 1,
    val autoPrintReceipt: Boolean = false,
    // Appearance settings
    val themeMode: String = "LIGHT", // "LIGHT", "DARK", "SYSTEM"
    val textScale: Float = 1.0f, // 0.9f, 1.0f, 1.15f
    val productViewMode: String = "GRID", // "GRID" or "LIST"
    val gridColumns: Int = 2, // 2 or 3
    // Stock settings
    val enableLowStockAlert: Boolean = true,
    val allowNegativeStock: Boolean = false,
    val autoReduceStock: Boolean = true,
    // Security settings
    val requirePinOnStartup: Boolean = false,
    val protectAdminSettings: Boolean = true,
    val adminPinHash: String = SecurityAndFormatUtils.hashPin("1234")
)

@Entity(tableName = "printers")
data class PrinterDeviceEntity(
    @PrimaryKey val address: String,
    val name: String,
    val paperSizeMm: Int = 58,
    val isDefault: Boolean = false,
    val lastConnectedAt: Long = System.currentTimeMillis()
)

data class CartItem(
    val product: ProductEntity,
    val quantity: Int = 1,
    val note: String = ""
) {
    val subtotal: Double
        get() = product.sellPrice * quantity
    val totalCost: Double
        get() = product.buyPrice * quantity
}
