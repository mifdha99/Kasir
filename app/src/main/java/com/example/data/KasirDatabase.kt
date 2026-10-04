package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
        StockMovementEntity::class,
        StoreSettingsEntity::class,
        PrinterDeviceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KasirDatabase : RoomDatabase() {

    abstract fun kasirDao(): KasirDao

    companion object {
        @Volatile
        private var INSTANCE: KasirDatabase? = null

        fun getInstance(context: Context): KasirDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KasirDatabase::class.java,
                    "kasirku_offline_pos.db"
                )
                    .fallbackToDestructiveMigration()
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
                        name = "Admin Utama",
                        pinHash = SecurityAndFormatUtils.hashPin("1234"),
                        role = "ADMIN",
                        canGiveDiscount = true,
                        canManageStock = true,
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
                        canManageStock = false,
                        canVoidTransaction = false,
                        canViewReports = true,
                        canManageSettings = false
                    )
                )
            }

            val existingCategories = dao.getAllCategoriesSnapshot()
            if (existingCategories.isEmpty()) {
                val catMinumanId = dao.insertCategory(
                    CategoryEntity(name = "Minuman", iconName = "LocalCafe", colorHex = "#0F766E")
                )
                val catMakananId = dao.insertCategory(
                    CategoryEntity(name = "Makanan", iconName = "Restaurant", colorHex = "#D97706")
                )
                val catSembakoId = dao.insertCategory(
                    CategoryEntity(name = "Sembako", iconName = "Inventory2", colorHex = "#2563EB")
                )
                val catSnackId = dao.insertCategory(
                    CategoryEntity(name = "Snack & Roti", iconName = "BakeryDining", colorHex = "#7C3AED")
                )
                val catRumahId = dao.insertCategory(
                    CategoryEntity(name = "Kebutuhan Rumah", iconName = "Home", colorHex = "#DC2626")
                )

                val existingProducts = dao.getAllProductsSnapshot()
                if (existingProducts.isEmpty()) {
                    val sampleProducts = listOf(
                        ProductEntity(
                            name = "Kopi Susu Gula Aren",
                            sku = "MN-001",
                            barcode = "899100100001",
                            buyPrice = 9000.0,
                            sellPrice = 18000.0,
                            stock = 45,
                            minStock = 10,
                            unit = "Cup",
                            categoryId = catMinumanId,
                            categoryName = "Minuman",
                            description = "Es kopi susu segar dengan gula aren asli"
                        ),
                        ProductEntity(
                            name = "Teh Melati Dingin",
                            sku = "MN-002",
                            barcode = "899100100002",
                            buyPrice = 3500.0,
                            sellPrice = 8000.0,
                            stock = 60,
                            minStock = 10,
                            unit = "Cup",
                            categoryId = catMinumanId,
                            categoryName = "Minuman",
                            description = "Es teh melati segar ukuran besar"
                        ),
                        ProductEntity(
                            name = "Air Mineral 600ml",
                            sku = "MN-003",
                            barcode = "899100100003",
                            buyPrice = 2200.0,
                            sellPrice = 4000.0,
                            stock = 8,
                            minStock = 12,
                            unit = "Botol",
                            categoryId = catMinumanId,
                            categoryName = "Minuman",
                            description = "Air mineral kemasan botol 600ml"
                        ),
                        ProductEntity(
                            name = "Nasi Goreng Spesial",
                            sku = "MK-001",
                            barcode = "899100200001",
                            buyPrice = 14000.0,
                            sellPrice = 25000.0,
                            stock = 30,
                            minStock = 5,
                            unit = "Porsi",
                            categoryId = catMakananId,
                            categoryName = "Makanan",
                            description = "Nasi goreng lengkap dengan telur dan ayam suwir"
                        ),
                        ProductEntity(
                            name = "Mie Goreng Jawa",
                            sku = "MK-002",
                            barcode = "899100200002",
                            buyPrice = 12000.0,
                            sellPrice = 22000.0,
                            stock = 25,
                            minStock = 5,
                            unit = "Porsi",
                            categoryId = catMakananId,
                            categoryName = "Makanan",
                            description = "Mie goreng bumbu tradisional"
                        ),
                        ProductEntity(
                            name = "Beras Pandan Wangi 5Kg",
                            sku = "SB-001",
                            barcode = "899100300001",
                            buyPrice = 64000.0,
                            sellPrice = 75000.0,
                            stock = 18,
                            minStock = 5,
                            unit = "Sak",
                            categoryId = catSembakoId,
                            categoryName = "Sembako",
                            description = "Beras pulen kualitas premium 5 kilogram"
                        ),
                        ProductEntity(
                            name = "Minyak Goreng Sawit 2L",
                            sku = "SB-002",
                            barcode = "899100300002",
                            buyPrice = 31000.0,
                            sellPrice = 36000.0,
                            stock = 4,
                            minStock = 6,
                            unit = "Pouch",
                            categoryId = catSembakoId,
                            categoryName = "Sembako",
                            description = "Minyak goreng kemasan pouch 2 liter"
                        ),
                        ProductEntity(
                            name = "Gula Pasir Kristal 1Kg",
                            sku = "SB-003",
                            barcode = "899100300003",
                            buyPrice = 14500.0,
                            sellPrice = 17500.0,
                            stock = 32,
                            minStock = 10,
                            unit = "Bungkus",
                            categoryId = catSembakoId,
                            categoryName = "Sembako",
                            description = "Gula pasir putih bersih 1 kg"
                        ),
                        ProductEntity(
                            name = "Roti Bakar Coklat Keju",
                            sku = "SN-001",
                            barcode = "899100400001",
                            buyPrice = 8000.0,
                            sellPrice = 15000.0,
                            stock = 20,
                            minStock = 5,
                            unit = "Porsi",
                            categoryId = catSnackId,
                            categoryName = "Snack & Roti",
                            description = "Roti bakar lembut tabur coklat dan keju parut"
                        ),
                        ProductEntity(
                            name = "Keripik Singkong Balado",
                            sku = "SN-002",
                            barcode = "899100400002",
                            buyPrice = 6000.0,
                            sellPrice = 10000.0,
                            stock = 0,
                            minStock = 5,
                            unit = "Pcs",
                            categoryId = catSnackId,
                            categoryName = "Snack & Roti",
                            description = "Keripik singkong renyah rasa balado pedas manis"
                        ),
                        ProductEntity(
                            name = "Sabun Cuci Piring 750ml",
                            sku = "RM-001",
                            barcode = "899100500001",
                            buyPrice = 11000.0,
                            sellPrice = 14500.0,
                            stock = 15,
                            minStock = 5,
                            unit = "Pouch",
                            categoryId = catRumahId,
                            categoryName = "Kebutuhan Rumah",
                            description = "Sabun cair pencuci piring ekstrak jeruk nipis"
                        )
                    )

                    for (prod in sampleProducts) {
                        val id = dao.insertProduct(prod)
                        if (prod.stock > 0) {
                            dao.insertStockMovement(
                                StockMovementEntity(
                                    productId = id,
                                    productName = prod.name,
                                    type = "IN",
                                    quantityChange = prod.stock,
                                    previousStock = 0,
                                    newStock = prod.stock,
                                    note = "Stok awal produk",
                                    userName = "Admin Utama"
                                )
                            )
                        }
                    }
                }
            }

            val existingCustomers = dao.getAllCustomersSnapshot()
            if (existingCustomers.isEmpty()) {
                dao.insertCustomer(
                    CustomerEntity(
                        name = "Budi Santoso",
                        phone = "081298765432",
                        address = "Jl. Melati Indah No. 12",
                        notes = "Pelanggan tetap warung"
                    )
                )
                dao.insertCustomer(
                    CustomerEntity(
                        name = "Siti Aminah",
                        phone = "081345678901",
                        address = "Komp. Griya Asri Blok C4",
                        notes = "Sering pesan sembako bulanan"
                    )
                )
            }
        }
    }
}
