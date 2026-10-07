package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.util.SecurityAndFormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        CustomerEntity::class,
        CashierUserEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        StoreSettingsEntity::class,
        PrinterDeviceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class KasirDatabase : RoomDatabase() {

    abstract fun kasirDao(): KasirDao

    companion object {
        @Volatile
        private var INSTANCE: KasirDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Migrate products (preserve all existing products, drop barcode/sku/stock columns)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `products_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `buyPrice` REAL NOT NULL,
                        `sellPrice` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `imageUri` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `products_new` (
                        `id`, `name`, `buyPrice`, `sellPrice`, `unit`,
                        `categoryId`, `categoryName`, `description`, `imageUri`,
                        `isActive`, `createdAt`, `updatedAt`
                    )
                    SELECT
                        `id`, `name`, `buyPrice`, `sellPrice`,
                        CASE WHEN `unit` = '' THEN 'Porsi' ELSE `unit` END,
                        `categoryId`,
                        CASE WHEN `categoryName` = '' THEN 'Makanan' ELSE `categoryName` END,
                        `description`, `imageUri`, `isActive`, `createdAt`, `updatedAt`
                    FROM `products`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `products`")
                db.execSQL("ALTER TABLE `products_new` RENAME TO `products`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_categoryId` ON `products` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)")

                // 2. Migrate users (remove canManageStock)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `users_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `pinHash` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `canGiveDiscount` INTEGER NOT NULL,
                        `canVoidTransaction` INTEGER NOT NULL,
                        `canViewReports` INTEGER NOT NULL,
                        `canManageSettings` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `users_new` (
                        `id`, `name`, `pinHash`, `role`, `canGiveDiscount`,
                        `canVoidTransaction`, `canViewReports`, `canManageSettings`,
                        `isActive`, `createdAt`
                    )
                    SELECT
                        `id`, `name`, `pinHash`, `role`, `canGiveDiscount`,
                        `canVoidTransaction`, `canViewReports`, `canManageSettings`,
                        `isActive`, `createdAt`
                    FROM `users`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `users`")
                db.execSQL("ALTER TABLE `users_new` RENAME TO `users`")

                // 3. Migrate transactions (add billingNumber & isSentToKitchen, convert COMPLETED -> PAID)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transactions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `billingNumber` INTEGER NOT NULL,
                        `invoiceNumber` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `cashierId` INTEGER NOT NULL,
                        `cashierName` TEXT NOT NULL,
                        `customerId` INTEGER,
                        `customerName` TEXT NOT NULL,
                        `subtotal` REAL NOT NULL,
                        `discountAmount` REAL NOT NULL,
                        `taxPercentage` REAL NOT NULL,
                        `taxAmount` REAL NOT NULL,
                        `serviceFee` REAL NOT NULL,
                        `roundingAmount` REAL NOT NULL,
                        `totalAmount` REAL NOT NULL,
                        `totalCost` REAL NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `amountPaid` REAL NOT NULL,
                        `changeAmount` REAL NOT NULL,
                        `notes` TEXT NOT NULL,
                        `isSentToKitchen` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `cancelReason` TEXT NOT NULL,
                        `cancelledAt` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `transactions_new` (
                        `id`, `billingNumber`, `invoiceNumber`, `timestamp`,
                        `cashierId`, `cashierName`, `customerId`, `customerName`,
                        `subtotal`, `discountAmount`, `taxPercentage`, `taxAmount`,
                        `serviceFee`, `roundingAmount`, `totalAmount`, `totalCost`,
                        `paymentMethod`, `amountPaid`, `changeAmount`, `notes`,
                        `isSentToKitchen`, `status`, `cancelReason`, `cancelledAt`
                    )
                    SELECT
                        `id`,
                        `id`,
                        'Billing ' || `id`,
                        `timestamp`,
                        `cashierId`, `cashierName`, `customerId`, `customerName`,
                        `subtotal`, `discountAmount`, `taxPercentage`, `taxAmount`,
                        `serviceFee`, `roundingAmount`, `totalAmount`, `totalCost`,
                        `paymentMethod`, `amountPaid`, `changeAmount`, `notes`,
                        1,
                        CASE WHEN `status` = 'COMPLETED' THEN 'PAID' ELSE `status` END,
                        `cancelReason`, `cancelledAt`
                    FROM `transactions`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_billingNumber` ON `transactions` (`billingNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_customerId` ON `transactions` (`customerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_status` ON `transactions` (`status`)")

                // 4. Migrate transaction_items (remove sku, add portionNotesJson)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `productId` INTEGER NOT NULL,
                        `productName` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `buyPrice` REAL NOT NULL,
                        `sellPrice` REAL NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `unit` TEXT NOT NULL,
                        `itemNote` TEXT NOT NULL,
                        `portionNotesJson` TEXT NOT NULL,
                        `subtotal` REAL NOT NULL,
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `transaction_items_new` (
                        `id`, `transactionId`, `productId`, `productName`,
                        `categoryName`, `buyPrice`, `sellPrice`, `quantity`,
                        `unit`, `itemNote`, `portionNotesJson`, `subtotal`
                    )
                    SELECT
                        `id`, `transactionId`, `productId`, `productName`,
                        `categoryName`, `buyPrice`, `sellPrice`, `quantity`,
                        `unit`, `itemNote`, '[]', `subtotal`
                    FROM `transaction_items`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `transaction_items`")
                db.execSQL("ALTER TABLE `transaction_items_new` RENAME TO `transaction_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_transactionId` ON `transaction_items` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_productId` ON `transaction_items` (`productId`)")

                // 5. Migrate settings (remove stock & invoice prefix columns, add autoPrintKitchenTicket)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `settings_new` (
                        `id` INTEGER NOT NULL,
                        `storeName` TEXT NOT NULL,
                        `storeLogoUri` TEXT NOT NULL,
                        `storeAddress` TEXT NOT NULL,
                        `storePhone` TEXT NOT NULL,
                        `storeEmail` TEXT NOT NULL,
                        `storeNpwp` TEXT NOT NULL,
                        `receiptFooter` TEXT NOT NULL,
                        `enableDiscount` INTEGER NOT NULL,
                        `enableTax` INTEGER NOT NULL,
                        `defaultTaxPercent` REAL NOT NULL,
                        `defaultServiceFee` REAL NOT NULL,
                        `enableRounding` INTEGER NOT NULL,
                        `defaultPrinterName` TEXT NOT NULL,
                        `defaultPrinterAddress` TEXT NOT NULL,
                        `paperSizeMm` INTEGER NOT NULL,
                        `printCopies` INTEGER NOT NULL,
                        `autoPrintReceipt` INTEGER NOT NULL,
                        `autoPrintKitchenTicket` INTEGER NOT NULL,
                        `themeMode` TEXT NOT NULL,
                        `textScale` REAL NOT NULL,
                        `productViewMode` TEXT NOT NULL,
                        `gridColumns` INTEGER NOT NULL,
                        `requirePinOnStartup` INTEGER NOT NULL,
                        `protectAdminSettings` INTEGER NOT NULL,
                        `adminPinHash` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `settings_new` (
                        `id`, `storeName`, `storeLogoUri`, `storeAddress`, `storePhone`,
                        `storeEmail`, `storeNpwp`, `receiptFooter`, `enableDiscount`,
                        `enableTax`, `defaultTaxPercent`, `defaultServiceFee`, `enableRounding`,
                        `defaultPrinterName`, `defaultPrinterAddress`, `paperSizeMm`,
                        `printCopies`, `autoPrintReceipt`, `autoPrintKitchenTicket`,
                        `themeMode`, `textScale`, `productViewMode`, `gridColumns`,
                        `requirePinOnStartup`, `protectAdminSettings`, `adminPinHash`
                    )
                    SELECT
                        `id`, `storeName`, `storeLogoUri`, `storeAddress`, `storePhone`,
                        `storeEmail`, `storeNpwp`, `receiptFooter`, `enableDiscount`,
                        `enableTax`, `defaultTaxPercent`, `defaultServiceFee`, `enableRounding`,
                        `defaultPrinterName`, `defaultPrinterAddress`, `paperSizeMm`,
                        `printCopies`, `autoPrintReceipt`, 0,
                        `themeMode`, `textScale`, `productViewMode`, `gridColumns`,
                        `requirePinOnStartup`, `protectAdminSettings`, `adminPinHash`
                    FROM `settings`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `settings`")
                db.execSQL("ALTER TABLE `settings_new` RENAME TO `settings`")

                // 6. Drop obsolete stock_movements table
                db.execSQL("DROP TABLE IF EXISTS `stock_movements`")
            }
        }

        fun getInstance(context: Context): KasirDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KasirDatabase::class.java,
                    "kasirku_offline_pos.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                CoroutineScope(Dispatchers.IO).launch {
                    seedInitialDataIfNeeded(instance.kasirDao())
                }
                instance
            }
        }

        suspend fun seedInitialDataIfNeeded(dao: KasirDao) {
            val existingSettings = dao.getStoreSettingsSnapshot()
            if (existingSettings == null) {
                dao.saveStoreSettings(StoreSettingsEntity(id = 1))
            }

            val existingUsers = dao.getAllUsersSnapshot()
            if (existingUsers.isEmpty()) {
                dao.insertUser(
                    CashierUserEntity(
                        name = "Admin Resto",
                        pinHash = SecurityAndFormatUtils.hashPin("1234"),
                        role = "ADMIN",
                        canGiveDiscount = true,
                        canVoidTransaction = true,
                        canViewReports = true,
                        canManageSettings = true
                    )
                )
                dao.insertUser(
                    CashierUserEntity(
                        name = "Kasir Shift 1",
                        pinHash = SecurityAndFormatUtils.hashPin("0000"),
                        role = "KASIR",
                        canGiveDiscount = true,
                        canVoidTransaction = false,
                        canViewReports = true,
                        canManageSettings = false
                    )
                )
            }

            val existingCategories = dao.getAllCategoriesSnapshot()
            if (existingCategories.isEmpty()) {
                val catMakananId = dao.insertCategory(
                    CategoryEntity(name = "Makanan", iconName = "Restaurant", colorHex = "#D97706")
                )
                val catMinumanId = dao.insertCategory(
                    CategoryEntity(name = "Minuman", iconName = "LocalCafe", colorHex = "#0F766E")
                )
                val catSnackId = dao.insertCategory(
                    CategoryEntity(name = "Snack", iconName = "Fastfood", colorHex = "#7C3AED")
                )
                val catKopiId = dao.insertCategory(
                    CategoryEntity(name = "Kopi", iconName = "LocalCafe", colorHex = "#4F46E5")
                )
                val catDessertId = dao.insertCategory(
                    CategoryEntity(name = "Dessert", iconName = "BakeryDining", colorHex = "#DB2777")
                )
                val catPaketId = dao.insertCategory(
                    CategoryEntity(name = "Paket", iconName = "ShoppingBag", colorHex = "#059669")
                )

                val existingProducts = dao.getAllProductsSnapshot()
                if (existingProducts.isEmpty()) {
                    val sampleProducts = listOf(
                        ProductEntity(
                            name = "Mie Goreng",
                            buyPrice = 8000.0,
                            sellPrice = 15000.0,
                            unit = "Porsi",
                            categoryId = catMakananId,
                            categoryName = "Makanan",
                            description = "Mie goreng spesial dengan telur dan sayuran segar"
                        ),
                        ProductEntity(
                            name = "Nasi Goreng Spesial",
                            buyPrice = 10000.0,
                            sellPrice = 20000.0,
                            unit = "Porsi",
                            categoryId = catMakananId,
                            categoryName = "Makanan",
                            description = "Nasi goreng kampung dengan suwiran ayam dan telur mata sapi"
                        ),
                        ProductEntity(
                            name = "Ayam Bakar Madu",
                            buyPrice = 13000.0,
                            sellPrice = 25000.0,
                            unit = "Porsi",
                            categoryId = catMakananId,
                            categoryName = "Makanan",
                            description = "Ayam bakar bumbu madu lengkap dengan lalapan dan sambal"
                        ),
                        ProductEntity(
                            name = "Soto Ayam Lamongan",
                            buyPrice = 9000.0,
                            sellPrice = 18000.0,
                            unit = "Porsi",
                            categoryId = catMakananId,
                            categoryName = "Makanan",
                            description = "Soto ayam kuah hangat dengan koya gurih"
                        ),
                        ProductEntity(
                            name = "Es Teh",
                            buyPrice = 1500.0,
                            sellPrice = 5000.0,
                            unit = "Gelas",
                            categoryId = catMinumanId,
                            categoryName = "Minuman",
                            description = "Es teh manis segar"
                        ),
                        ProductEntity(
                            name = "Teh Manis Hangat",
                            buyPrice = 1500.0,
                            sellPrice = 5000.0,
                            unit = "Gelas",
                            categoryId = catMinumanId,
                            categoryName = "Minuman",
                            description = "Teh melati manis hangat"
                        ),
                        ProductEntity(
                            name = "Es Jeruk Peras",
                            buyPrice = 4000.0,
                            sellPrice = 10000.0,
                            unit = "Gelas",
                            categoryId = catMinumanId,
                            categoryName = "Minuman",
                            description = "Perasan jeruk asli segar dengan es batu"
                        ),
                        ProductEntity(
                            name = "Kopi Susu Gula Aren",
                            buyPrice = 8000.0,
                            sellPrice = 18000.0,
                            unit = "Cup",
                            categoryId = catKopiId,
                            categoryName = "Kopi",
                            description = "Es kopi susu espresso dengan gula aren asli"
                        ),
                        ProductEntity(
                            name = "Kentang Goreng Crispy",
                            buyPrice = 6000.0,
                            sellPrice = 12000.0,
                            unit = "Porsi",
                            categoryId = catSnackId,
                            categoryName = "Snack",
                            description = "Kentang goreng renyah dengan saus sambal & tomat"
                        ),
                        ProductEntity(
                            name = "Pisang Bakar Coklat Keju",
                            buyPrice = 7000.0,
                            sellPrice = 15000.0,
                            unit = "Porsi",
                            categoryId = catDessertId,
                            categoryName = "Dessert",
                            description = "Pisang bakar manis dengan taburan coklat dan keju"
                        ),
                        ProductEntity(
                            name = "Paket Ayam Bakar + Nasi + Es Teh",
                            buyPrice = 15000.0,
                            sellPrice = 28000.0,
                            unit = "Paket",
                            categoryId = catPaketId,
                            categoryName = "Paket",
                            description = "Paket kenyang hemat ayam bakar, nasi putih, dan es teh"
                        )
                    )

                    for (prod in sampleProducts) {
                        dao.insertProduct(prod)
                    }
                }
            } else {
                // Ensure Makanan and Minuman categories exist and any legacy unmapped products are mapped cleanly
                val currentCats = dao.getAllCategoriesSnapshot().toMutableList()
                var makananCat = currentCats.find { it.name.equals("Makanan", ignoreCase = true) }
                if (makananCat == null) {
                    val id = dao.insertCategory(CategoryEntity(name = "Makanan", iconName = "Restaurant", colorHex = "#D97706"))
                    makananCat = CategoryEntity(id = id, name = "Makanan", iconName = "Restaurant", colorHex = "#D97706")
                    currentCats.add(makananCat)
                }
                var minumanCat = currentCats.find { it.name.equals("Minuman", ignoreCase = true) }
                if (minumanCat == null) {
                    val id = dao.insertCategory(CategoryEntity(name = "Minuman", iconName = "LocalCafe", colorHex = "#0F766E"))
                    minumanCat = CategoryEntity(id = id, name = "Minuman", iconName = "LocalCafe", colorHex = "#0F766E")
                    currentCats.add(minumanCat)
                }

                val validCatIds = currentCats.map { it.id }.toSet()
                val allProds = dao.getAllProductsSnapshot()
                for (p in allProds) {
                    if (p.categoryId !in validCatIds) {
                        val matchedByName = currentCats.find { it.name.equals(p.categoryName, ignoreCase = true) }
                            ?: makananCat
                        dao.updateProduct(
                            p.copy(
                                categoryId = matchedByName.id,
                                categoryName = matchedByName.name
                            )
                        )
                    }
                }
            }

            val existingCustomers = dao.getAllCustomersSnapshot()
            if (existingCustomers.isEmpty()) {
                dao.insertCustomer(
                    CustomerEntity(
                        name = "Budi Santoso",
                        phone = "081298765432",
                        address = "Meja VIP / Pelanggan Tetap",
                        notes = "Suka pedas"
                    )
                )
                dao.insertCustomer(
                    CustomerEntity(
                        name = "Siti Aminah",
                        phone = "081345678901",
                        address = "Langganan Katering",
                        notes = "Tanpa MSG"
                    )
                )
            }
        }
    }
}
