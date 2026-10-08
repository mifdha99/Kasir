package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        PaymentMethodEntity::class,
        CustomerEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        StockMutationEntity::class,
        DebtPaymentEntity::class,
        CashierUserEntity::class,
        StoreSettingsEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class KasirDatabase : RoomDatabase() {
    abstract fun kasirDao(): KasirDao

    companion object {
        @Volatile
        private var INSTANCE: KasirDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                runCatching {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN billingNumber INTEGER NOT NULL DEFAULT 1")
                }
                runCatching {
                    db.execSQL("ALTER TABLE store_settings ADD COLUMN autoPrintKitchenTicket INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payment_methods` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isCashType` INTEGER NOT NULL DEFAULT 0,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                runCatching {
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `paymentMethodId` INTEGER DEFAULT NULL")
                }
                runCatching {
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `billingDate` TEXT NOT NULL DEFAULT ''")
                }
                runCatching {
                    db.execSQL("ALTER TABLE `store_settings` ADD COLUMN `lastBillingDate` TEXT NOT NULL DEFAULT ''")
                }
                runCatching {
                    db.execSQL("ALTER TABLE `store_settings` ADD COLUMN `lastBillingSequence` INTEGER NOT NULL DEFAULT 0")
                }

                // Seed default payment methods if table is empty
                val cursor = db.query("SELECT COUNT(*) FROM `payment_methods`")
                var count = 0
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0)
                }
                cursor.close()
                if (count == 0) {
                    val now = System.currentTimeMillis()
                    db.execSQL("INSERT INTO `payment_methods` (`name`, `isCashType`, `sortOrder`, `createdAt`) VALUES ('TUNAI', 1, 1, $now)")
                    db.execSQL("INSERT INTO `payment_methods` (`name`, `isCashType`, `sortOrder`, `createdAt`) VALUES ('TRANSFER', 0, 2, $now)")
                    db.execSQL("INSERT INTO `payment_methods` (`name`, `isCashType`, `sortOrder`, `createdAt`) VALUES ('QRIS', 0, 3, $now)")
                    db.execSQL("INSERT INTO `payment_methods` (`name`, `isCashType`, `sortOrder`, `createdAt`) VALUES ('GOJEK', 0, 4, $now)")
                    db.execSQL("INSERT INTO `payment_methods` (`name`, `isCashType`, `sortOrder`, `createdAt`) VALUES ('SHOPEE', 0, 5, $now)")
                    db.execSQL("INSERT INTO `payment_methods` (`name`, `isCashType`, `sortOrder`, `createdAt`) VALUES ('GRAB', 0, 6, $now)")
                }
            }
        }

        fun getInstance(context: Context): KasirDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KasirDatabase::class.java,
                    "kasirku_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
