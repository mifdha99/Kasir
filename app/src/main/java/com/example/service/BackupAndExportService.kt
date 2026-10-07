package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.CashierUserEntity
import com.example.data.CategoryEntity
import com.example.data.CustomerEntity
import com.example.data.KasirDao
import com.example.data.PrinterDeviceEntity
import com.example.data.ProductEntity
import com.example.data.StoreSettingsEntity
import com.example.data.TransactionEntity
import com.example.data.TransactionItemEntity
import com.example.data.TransactionWithItems
import com.example.util.SecurityAndFormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object BackupAndExportService {

    private const val BACKUP_MAGIC_V1 = "KasirKu_POS_Backup_v1"
    private const val BACKUP_MAGIC_V2 = "KasirKu_Resto_Backup_v2"

    // --- FULL DATABASE JSON BACKUP ---
    suspend fun createBackupJsonString(dao: KasirDao): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject()
            root.put("backupFormat", BACKUP_MAGIC_V2)
            root.put("createdAt", System.currentTimeMillis())

            // Settings
            val settings = dao.getStoreSettingsSnapshot() ?: StoreSettingsEntity()
            val settingsObj = JSONObject().apply {
                put("storeName", settings.storeName)
                put("storeLogoUri", settings.storeLogoUri)
                put("storeAddress", settings.storeAddress)
                put("storePhone", settings.storePhone)
                put("storeEmail", settings.storeEmail)
                put("storeNpwp", settings.storeNpwp)
                put("receiptFooter", settings.receiptFooter)
                put("enableDiscount", settings.enableDiscount)
                put("enableTax", settings.enableTax)
                put("defaultTaxPercent", settings.defaultTaxPercent)
                put("defaultServiceFee", settings.defaultServiceFee)
                put("enableRounding", settings.enableRounding)
                put("defaultPrinterName", settings.defaultPrinterName)
                put("defaultPrinterAddress", settings.defaultPrinterAddress)
                put("paperSizeMm", settings.paperSizeMm)
                put("printCopies", settings.printCopies)
                put("autoPrintReceipt", settings.autoPrintReceipt)
                put("autoPrintKitchenTicket", settings.autoPrintKitchenTicket)
                put("themeMode", settings.themeMode)
                put("textScale", settings.textScale.toDouble())
                put("productViewMode", settings.productViewMode)
                put("gridColumns", settings.gridColumns)
                put("requirePinOnStartup", settings.requirePinOnStartup)
                put("protectAdminSettings", settings.protectAdminSettings)
                put("adminPinHash", settings.adminPinHash)
            }
            root.put("settings", settingsObj)

            // Categories
            val catArray = JSONArray()
            dao.getAllCategoriesSnapshot().forEach { c ->
                catArray.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("iconName", c.iconName)
                        put("colorHex", c.colorHex)
                        put("createdAt", c.createdAt)
                    }
                )
            }
            root.put("categories", catArray)

            // Products
            val prodArray = JSONArray()
            dao.getAllProductsSnapshot().forEach { p ->
                prodArray.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("buyPrice", p.buyPrice)
                        put("sellPrice", p.sellPrice)
                        put("unit", p.unit)
                        put("categoryId", p.categoryId)
                        put("categoryName", p.categoryName)
                        put("description", p.description)
                        put("imageUri", p.imageUri)
                        put("isActive", p.isActive)
                        put("createdAt", p.createdAt)
                        put("updatedAt", p.updatedAt)
                    }
                )
            }
            root.put("products", prodArray)

            // Customers
            val custArray = JSONArray()
            dao.getAllCustomersSnapshot().forEach { c ->
                custArray.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("phone", c.phone)
                        put("address", c.address)
                        put("notes", c.notes)
                        put("totalTransactions", c.totalTransactions)
                        put("totalPurchase", c.totalPurchase)
                        put("createdAt", c.createdAt)
                    }
                )
            }
            root.put("customers", custArray)

            // Users
            val userArray = JSONArray()
            dao.getAllUsersSnapshot().forEach { u ->
                userArray.put(
                    JSONObject().apply {
                        put("id", u.id)
                        put("name", u.name)
                        put("pinHash", u.pinHash)
                        put("role", u.role)
                        put("canGiveDiscount", u.canGiveDiscount)
                        put("canVoidTransaction", u.canVoidTransaction)
                        put("canViewReports", u.canViewReports)
                        put("canManageSettings", u.canManageSettings)
                        put("isActive", u.isActive)
                        put("createdAt", u.createdAt)
                    }
                )
            }
            root.put("users", userArray)

            // Printers
            val printerArray = JSONArray()
            dao.getAllPrintersSnapshot().forEach { pr ->
                printerArray.put(
                    JSONObject().apply {
                        put("address", pr.address)
                        put("name", pr.name)
                        put("paperSizeMm", pr.paperSizeMm)
                        put("isDefault", pr.isDefault)
                        put("lastConnectedAt", pr.lastConnectedAt)
                    }
                )
            }
            root.put("printers", printerArray)

            // Transactions
            val txArray = JSONArray()
            dao.getAllTransactionsSnapshot().forEach { t ->
                txArray.put(
                    JSONObject().apply {
                        put("id", t.id)
                        put("billingNumber", t.billingNumber)
                        put("invoiceNumber", t.invoiceNumber)
                        put("timestamp", t.timestamp)
                        put("cashierId", t.cashierId)
                        put("cashierName", t.cashierName)
                        if (t.customerId != null) put("customerId", t.customerId)
                        put("customerName", t.customerName)
                        put("subtotal", t.subtotal)
                        put("discountAmount", t.discountAmount)
                        put("taxPercentage", t.taxPercentage)
                        put("taxAmount", t.taxAmount)
                        put("serviceFee", t.serviceFee)
                        put("roundingAmount", t.roundingAmount)
                        put("totalAmount", t.totalAmount)
                        put("totalCost", t.totalCost)
                        put("paymentMethod", t.paymentMethod)
                        put("amountPaid", t.amountPaid)
                        put("changeAmount", t.changeAmount)
                        put("notes", t.notes)
                        put("isSentToKitchen", t.isSentToKitchen)
                        put("status", t.status)
                        put("cancelReason", t.cancelReason)
                        if (t.cancelledAt != null) put("cancelledAt", t.cancelledAt)
                    }
                )
            }
            root.put("transactions", txArray)

            // Transaction Items
            val itemsArray = JSONArray()
            dao.getAllTransactionItemsSnapshot().forEach { item ->
                itemsArray.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("transactionId", item.transactionId)
                        put("productId", item.productId)
                        put("productName", item.productName)
                        put("categoryName", item.categoryName)
                        put("buyPrice", item.buyPrice)
                        put("sellPrice", item.sellPrice)
                        put("quantity", item.quantity)
                        put("unit", item.unit)
                        put("itemNote", item.itemNote)
                        put("portionNotesJson", item.portionNotesJson)
                        put("subtotal", item.subtotal)
                    }
                )
            }
            root.put("transactionItems", itemsArray)

            root.toString(2)
        }
    }

    suspend fun writeBackupToUri(context: Context, uri: Uri, dao: KasirDao): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val jsonString = createBackupJsonString(dao).getOrThrow()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonString.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("Gagal membuka lokasi penyimpanan file backup.")
                "Backup database berhasil disimpan!"
            }
        }

    // --- VALIDATED ATOMIC RESTORE (Supports both v1 and v2 backup files) ---
    suspend fun restoreFromUri(context: Context, uri: Uri, dao: KasirDao): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rawContent = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalStateException("File backup tidak dapat dibaca.")

                if (rawContent.isBlank()) {
                    throw IllegalArgumentException("File backup kosong atau rusak.")
                }

                val root = try {
                    JSONObject(rawContent)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Format file backup bukan JSON yang valid.")
                }

                val magic = root.optString("backupFormat", "")
                if (magic != BACKUP_MAGIC_V1 && magic != BACKUP_MAGIC_V2) {
                    throw IllegalArgumentException("File ini bukan file backup resmi KasirKu.")
                }

                val categories = mutableListOf<CategoryEntity>()
                val catArray = root.optJSONArray("categories") ?: JSONArray()
                for (i in 0 until catArray.length()) {
                    val obj = catArray.getJSONObject(i)
                    val name = obj.optString("name", "").trim()
                    if (name.isEmpty()) throw IllegalArgumentException("Data kategori pada backup tidak valid.")
                    categories.add(
                        CategoryEntity(
                            id = obj.getLong("id"),
                            name = name,
                            iconName = obj.optString("iconName", "Restaurant"),
                            colorHex = obj.optString("colorHex", "#0F766E"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }

                val products = mutableListOf<ProductEntity>()
                val prodArray = root.optJSONArray("products") ?: JSONArray()
                for (i in 0 until prodArray.length()) {
                    val obj = prodArray.getJSONObject(i)
                    val name = obj.optString("name", "").trim()
                    if (name.isEmpty()) throw IllegalArgumentException("Data produk pada backup tidak valid.")
                    products.add(
                        ProductEntity(
                            id = obj.getLong("id"),
                            name = name,
                            buyPrice = obj.optDouble("buyPrice", 0.0).coerceAtLeast(0.0),
                            sellPrice = obj.optDouble("sellPrice", 0.0).coerceAtLeast(0.0),
                            unit = obj.optString("unit", "Porsi"),
                            categoryId = obj.optLong("categoryId", 0L),
                            categoryName = obj.optString("categoryName", "Makanan"),
                            description = obj.optString("description", ""),
                            imageUri = obj.optString("imageUri", ""),
                            isActive = obj.optBoolean("isActive", true),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }

                val customers = mutableListOf<CustomerEntity>()
                val custArray = root.optJSONArray("customers") ?: JSONArray()
                for (i in 0 until custArray.length()) {
                    val obj = custArray.getJSONObject(i)
                    customers.add(
                        CustomerEntity(
                            id = obj.getLong("id"),
                            name = obj.optString("name", "Pelanggan"),
                            phone = obj.optString("phone", ""),
                            address = obj.optString("address", ""),
                            notes = obj.optString("notes", ""),
                            totalTransactions = obj.optInt("totalTransactions", 0),
                            totalPurchase = obj.optDouble("totalPurchase", 0.0),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }

                val users = mutableListOf<CashierUserEntity>()
                val userArray = root.optJSONArray("users") ?: JSONArray()
                for (i in 0 until userArray.length()) {
                    val obj = userArray.getJSONObject(i)
                    users.add(
                        CashierUserEntity(
                            id = obj.getLong("id"),
                            name = obj.optString("name", "Admin"),
                            pinHash = obj.optString("pinHash", SecurityAndFormatUtils.hashPin("1234")),
                            role = obj.optString("role", "ADMIN"),
                            canGiveDiscount = obj.optBoolean("canGiveDiscount", true),
                            canVoidTransaction = obj.optBoolean("canVoidTransaction", true),
                            canViewReports = obj.optBoolean("canViewReports", true),
                            canManageSettings = obj.optBoolean("canManageSettings", true),
                            isActive = obj.optBoolean("isActive", true),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (users.isEmpty()) {
                    users.add(CashierUserEntity(id = 1, name = "Admin Utama"))
                }

                val settingsObj = root.optJSONObject("settings")
                val restoredSettings = if (settingsObj != null) {
                    StoreSettingsEntity(
                        id = 1,
                        storeName = settingsObj.optString("storeName", "Resto & Warung KasirKu"),
                        storeLogoUri = settingsObj.optString("storeLogoUri", ""),
                        storeAddress = settingsObj.optString("storeAddress", ""),
                        storePhone = settingsObj.optString("storePhone", ""),
                        storeEmail = settingsObj.optString("storeEmail", ""),
                        storeNpwp = settingsObj.optString("storeNpwp", ""),
                        receiptFooter = settingsObj.optString("receiptFooter", "TERIMA KASIH"),
                        enableDiscount = settingsObj.optBoolean("enableDiscount", true),
                        enableTax = settingsObj.optBoolean("enableTax", false),
                        defaultTaxPercent = settingsObj.optDouble("defaultTaxPercent", 10.0),
                        defaultServiceFee = settingsObj.optDouble("defaultServiceFee", 0.0),
                        enableRounding = settingsObj.optBoolean("enableRounding", false),
                        defaultPrinterName = settingsObj.optString("defaultPrinterName", ""),
                        defaultPrinterAddress = settingsObj.optString("defaultPrinterAddress", ""),
                        paperSizeMm = settingsObj.optInt("paperSizeMm", 58),
                        printCopies = settingsObj.optInt("printCopies", 1),
                        autoPrintReceipt = settingsObj.optBoolean("autoPrintReceipt", false),
                        autoPrintKitchenTicket = settingsObj.optBoolean("autoPrintKitchenTicket", false),
                        themeMode = settingsObj.optString("themeMode", "LIGHT"),
                        textScale = settingsObj.optDouble("textScale", 1.0).toFloat(),
                        productViewMode = settingsObj.optString("productViewMode", "GRID"),
                        gridColumns = settingsObj.optInt("gridColumns", 2),
                        requirePinOnStartup = settingsObj.optBoolean("requirePinOnStartup", false),
                        protectAdminSettings = settingsObj.optBoolean("protectAdminSettings", true),
                        adminPinHash = settingsObj.optString("adminPinHash", SecurityAndFormatUtils.hashPin("1234"))
                    )
                } else null

                val printers = mutableListOf<PrinterDeviceEntity>()
                val prArray = root.optJSONArray("printers") ?: JSONArray()
                for (i in 0 until prArray.length()) {
                    val obj = prArray.getJSONObject(i)
                    val addr = obj.optString("address", "")
                    if (addr.isNotBlank()) {
                        printers.add(
                            PrinterDeviceEntity(
                                address = addr,
                                name = obj.optString("name", "Printer"),
                                paperSizeMm = obj.optInt("paperSizeMm", 58),
                                isDefault = obj.optBoolean("isDefault", false),
                                lastConnectedAt = obj.optLong("lastConnectedAt", System.currentTimeMillis())
                            )
                        )
                    }
                }

                val transactions = mutableListOf<TransactionEntity>()
                val txArray = root.optJSONArray("transactions") ?: JSONArray()
                for (i in 0 until txArray.length()) {
                    val obj = txArray.getJSONObject(i)
                    val id = obj.getLong("id")
                    val billingNum = obj.optInt("billingNumber", id.toInt().coerceAtLeast(1))
                    val rawStatus = obj.optString("status", "PAID")
                    val normalizedStatus = if (rawStatus == "COMPLETED") "PAID" else rawStatus
                    transactions.add(
                        TransactionEntity(
                            id = id,
                            billingNumber = billingNum,
                            invoiceNumber = "Billing $billingNum",
                            timestamp = obj.getLong("timestamp"),
                            cashierId = obj.optLong("cashierId", 1L),
                            cashierName = obj.optString("cashierName", "Kasir"),
                            customerId = if (obj.has("customerId") && !obj.isNull("customerId")) obj.getLong("customerId") else null,
                            customerName = obj.optString("customerName", "Pelanggan Umum"),
                            subtotal = obj.optDouble("subtotal", 0.0),
                            discountAmount = obj.optDouble("discountAmount", 0.0),
                            taxPercentage = obj.optDouble("taxPercentage", 0.0),
                            taxAmount = obj.optDouble("taxAmount", 0.0),
                            serviceFee = obj.optDouble("serviceFee", 0.0),
                            roundingAmount = obj.optDouble("roundingAmount", 0.0),
                            totalAmount = obj.optDouble("totalAmount", 0.0),
                            totalCost = obj.optDouble("totalCost", 0.0),
                            paymentMethod = obj.optString("paymentMethod", "Tunai"),
                            amountPaid = obj.optDouble("amountPaid", 0.0),
                            changeAmount = obj.optDouble("changeAmount", 0.0),
                            notes = obj.optString("notes", ""),
                            isSentToKitchen = obj.optBoolean("isSentToKitchen", true),
                            status = normalizedStatus,
                            cancelReason = obj.optString("cancelReason", ""),
                            cancelledAt = if (obj.has("cancelledAt") && !obj.isNull("cancelledAt")) obj.getLong("cancelledAt") else null
                        )
                    )
                }

                val txItems = mutableListOf<TransactionItemEntity>()
                val itemArray = root.optJSONArray("transactionItems") ?: JSONArray()
                for (i in 0 until itemArray.length()) {
                    val obj = itemArray.getJSONObject(i)
                    txItems.add(
                        TransactionItemEntity(
                            id = obj.getLong("id"),
                            transactionId = obj.getLong("transactionId"),
                            productId = obj.getLong("productId"),
                            productName = obj.getString("productName"),
                            categoryName = obj.optString("categoryName", "Makanan"),
                            buyPrice = obj.optDouble("buyPrice", 0.0),
                            sellPrice = obj.optDouble("sellPrice", 0.0),
                            quantity = obj.optInt("quantity", 1),
                            unit = obj.optString("unit", "Porsi"),
                            itemNote = obj.optString("itemNote", ""),
                            portionNotesJson = obj.optString("portionNotesJson", "[]"),
                            subtotal = obj.optDouble("subtotal", 0.0)
                        )
                    )
                }

                dao.restoreFullDatabaseAtomic(
                    categories = categories,
                    products = products,
                    customers = customers,
                    users = users,
                    settings = restoredSettings,
                    printers = printers,
                    transactions = transactions,
                    transactionItems = txItems
                )

                "Restore berhasil! (${products.size} menu, ${transactions.size} billing dipulihkan)."
            }
        }

    // --- PRODUCT CSV EXPORT & IMPORT (WITHOUT BARCODE OR STOCK) ---
    suspend fun exportProductsCsvToUri(context: Context, uri: Uri, dao: KasirDao): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val products = dao.getAllProductsSnapshot()
                val sb = StringBuilder()
                sb.appendLine("Nama Menu,Kategori,Harga Beli,Harga Jual,Satuan,Status Aktif,Deskripsi")
                for (p in products) {
                    sb.appendLine(
                        listOf(
                            escapeCsv(p.name),
                            escapeCsv(p.categoryName),
                            p.buyPrice.toLong().toString(),
                            p.sellPrice.toLong().toString(),
                            escapeCsv(p.unit),
                            if (p.isActive) "AKTIF" else "NONAKTIF",
                            escapeCsv(p.description)
                        ).joinToString(",")
                    )
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(sb.toString().toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("Gagal menulis file CSV menu.")
                "Berhasil mengekspor ${products.size} menu ke file CSV."
            }
        }

    suspend fun importProductsCsvFromUri(context: Context, uri: Uri, dao: KasirDao): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val lines = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readLines()
                } ?: throw IllegalStateException("File CSV tidak dapat dibaca.")

                if (lines.size <= 1) {
                    throw IllegalArgumentException("File CSV kosong atau tidak memiliki baris data menu.")
                }

                val existingCategories = dao.getAllCategoriesSnapshot().associateBy { it.name.lowercase() }.toMutableMap()
                var importedCount = 0

                for (i in 1 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue
                    val cols = parseCsvLine(line)
                    if (cols.isEmpty()) continue
                    val name = cols.getOrNull(0)?.trim().orEmpty()
                    if (name.isEmpty()) continue

                    val catName = cols.getOrNull(1)?.trim()?.ifEmpty { "Makanan" } ?: "Makanan"
                    val buyPrice = cols.getOrNull(2)?.trim()?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val sellPrice = cols.getOrNull(3)?.trim()?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val unit = cols.getOrNull(4)?.trim()?.ifEmpty { "Porsi" } ?: "Porsi"
                    val isActive = cols.getOrNull(5)?.trim()?.uppercase() != "NONAKTIF"
                    val desc = cols.getOrNull(6)?.trim().orEmpty()

                    val category = existingCategories[catName.lowercase()] ?: run {
                        val newCatId = dao.insertCategory(CategoryEntity(name = catName))
                        val created = CategoryEntity(id = newCatId, name = catName)
                        existingCategories[catName.lowercase()] = created
                        created
                    }

                    dao.insertProduct(
                        ProductEntity(
                            name = name,
                            buyPrice = buyPrice,
                            sellPrice = sellPrice,
                            unit = unit,
                            categoryId = category.id,
                            categoryName = category.name,
                            description = desc,
                            isActive = isActive
                        )
                    )
                    importedCount++
                }

                if (importedCount == 0) {
                    throw IllegalArgumentException("Tidak ada menu valid yang ditemukan dalam file CSV.")
                }
                "Berhasil mengimpor $importedCount menu dari CSV!"
            }
        }

    // --- REPORT CSV EXPORT ---
    fun buildReportCsv(
        reportTitle: String,
        periodLabel: String,
        transactions: List<TransactionWithItems>
    ): String {
        val completed = transactions.filter { it.transaction.status == "PAID" || it.transaction.status == "COMPLETED" }
        val totalSales = completed.sumOf { it.transaction.totalAmount }
        val totalCost = completed.sumOf { it.transaction.totalCost }
        val totalDiscount = completed.sumOf { it.transaction.discountAmount }
        val totalTax = completed.sumOf { it.transaction.taxAmount }
        val estimatedProfit = totalSales - totalCost - totalTax

        val sb = StringBuilder()
        sb.appendLine("LAPORAN KASIRKU RESTO - $reportTitle")
        sb.appendLine("Periode,${escapeCsv(periodLabel)}")
        sb.appendLine("Tanggal Cetak,${escapeCsv(SecurityAndFormatUtils.formatDateTime(System.currentTimeMillis()))}")
        sb.appendLine()
        sb.appendLine("RINGKASAN KEUANGAN")
        sb.appendLine("Jumlah Billing Lunas,${completed.size}")
        sb.appendLine("Total Penjualan (Omzet),${totalSales.toLong()}")
        sb.appendLine("Total Modal (HPP),${totalCost.toLong()}")
        sb.appendLine("Total Diskon,${totalDiscount.toLong()}")
        sb.appendLine("Total Pajak,${totalTax.toLong()}")
        sb.appendLine("Estimasi Keuntungan Bersih,${estimatedProfit.toLong()}")
        sb.appendLine()
        sb.appendLine("DAFTAR BILLING & TRANSAKSI")
        sb.appendLine("Nomor Billing,Tanggal,Jam,Kasir,Pelanggan,Metode Bayar,Subtotal,Diskon,Pajak,Total Akhir,Status")
        for (tw in transactions) {
            val t = tw.transaction
            val statusIndo = when (t.status) {
                "PAID", "COMPLETED" -> "LUNAS"
                "UNPAID" -> "BELUM BAYAR"
                else -> "DIBATALKAN"
            }
            sb.appendLine(
                listOf(
                    escapeCsv(t.billingDisplay),
                    escapeCsv(SecurityAndFormatUtils.formatDate(t.timestamp)),
                    escapeCsv(SecurityAndFormatUtils.formatTime(t.timestamp)),
                    escapeCsv(t.cashierName),
                    escapeCsv(t.customerName),
                    escapeCsv(t.paymentMethod),
                    t.subtotal.toLong().toString(),
                    t.discountAmount.toLong().toString(),
                    t.taxAmount.toLong().toString(),
                    t.totalAmount.toLong().toString(),
                    statusIndo
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    // --- RECEIPT PDF GENERATION & SHARE ---
    suspend fun generateReceiptPdfFile(
        context: Context,
        txWithItems: TransactionWithItems,
        settings: StoreSettingsEntity
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val receiptLines = BluetoothPrinterService.formatReceiptText(
                txWithItems = txWithItems,
                settings = settings,
                paperSizeMm = settings.paperSizeMm
            ).lines()

            val pageWidth = if (settings.paperSizeMm >= 80) 360 else 260
            val lineHeight = 15
            val pageHeight = (receiptLines.size * lineHeight + 60).coerceAtLeast(320)

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 9.5f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }

            var y = 28f
            for (line in receiptLines) {
                canvas.drawText(line, 14f, y, paint)
                y += lineHeight
            }

            pdfDocument.finishPage(page)

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeBilling = txWithItems.transaction.billingDisplay.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val outFile = File(exportDir, "Struk_$safeBilling.pdf")
            FileOutputStream(outFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            outFile
        }
    }

    // --- REPORT PDF GENERATION ---
    suspend fun generateReportPdfFile(
        context: Context,
        storeName: String,
        periodLabel: String,
        transactions: List<TransactionWithItems>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val completed = transactions.filter { it.transaction.status == "PAID" || it.transaction.status == "COMPLETED" }
            val totalSales = completed.sumOf { it.transaction.totalAmount }
            val totalCost = completed.sumOf { it.transaction.totalCost }
            val totalDiscount = completed.sumOf { it.transaction.discountAmount }
            val totalTax = completed.sumOf { it.transaction.taxAmount }
            val estimatedProfit = totalSales - totalCost - totalTax

            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 118, 110)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 10f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }

            var pageNumber = 1
            var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            var y = 45f

            fun checkNewPage() {
                if (y > pageHeight - 50) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    y = 45f
                }
            }

            canvas.drawText("LAPORAN PENJUALAN RESTORAN - $storeName", 36f, y, titlePaint)
            y += 20f
            canvas.drawText("Periode: $periodLabel", 36f, y, headerPaint)
            y += 16f
            canvas.drawText("Dicetak: ${SecurityAndFormatUtils.formatDateTime(System.currentTimeMillis())}", 36f, y, bodyPaint)
            y += 24f

            canvas.drawText("RINGKASAN UTAMA", 36f, y, headerPaint)
            y += 16f
            val summaryLines = listOf(
                "Total Billing Lunas       : ${completed.size} Billing",
                "Total Omzet Penjualan     : ${SecurityAndFormatUtils.formatRupiah(totalSales)}",
                "Total Modal Pokok (HPP)   : ${SecurityAndFormatUtils.formatRupiah(totalCost)}",
                "Total Diskon Diberikan    : ${SecurityAndFormatUtils.formatRupiah(totalDiscount)}",
                "Total Pajak Terkumpul     : ${SecurityAndFormatUtils.formatRupiah(totalTax)}",
                "Estimasi Laba Bersih      : ${SecurityAndFormatUtils.formatRupiah(estimatedProfit)}"
            )
            for (s in summaryLines) {
                canvas.drawText(s, 36f, y, bodyPaint)
                y += 15f
            }

            y += 14f
            canvas.drawText("METODE PEMBAYARAN", 36f, y, headerPaint)
            y += 16f
            val byPayment = completed.groupBy { it.transaction.paymentMethod }
            if (byPayment.isEmpty()) {
                canvas.drawText("- Belum ada billing lunas pada periode ini.", 36f, y, bodyPaint)
                y += 15f
            } else {
                for ((method, list) in byPayment) {
                    val sum = list.sumOf { it.transaction.totalAmount }
                    canvas.drawText(
                        "- ${method.padEnd(12)} : ${list.size} billing | ${SecurityAndFormatUtils.formatRupiah(sum)}",
                        36f,
                        y,
                        bodyPaint
                    )
                    y += 15f
                }
            }

            y += 14f
            checkNewPage()
            canvas.drawText("DAFTAR BILLING (${transactions.size})", 36f, y, headerPaint)
            y += 16f

            for (tw in transactions.take(150)) {
                checkNewPage()
                val t = tw.transaction
                val statusLabel = when (t.status) {
                    "PAID", "COMPLETED" -> "LUNAS"
                    "UNPAID" -> "BELUM BAYAR"
                    else -> "BATAL"
                }
                val line = "${t.billingDisplay.padEnd(12)} | ${SecurityAndFormatUtils.formatDateTime(t.timestamp)} | ${t.paymentMethod.padEnd(8)} | ${SecurityAndFormatUtils.formatRupiah(t.totalAmount).padStart(12)} | $statusLabel"
                canvas.drawText(line.take(90), 36f, y, bodyPaint)
                y += 14f
            }

            pdfDocument.finishPage(page)

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outFile = File(exportDir, "Laporan_KasirKu_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            outFile
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun sharePlainText(context: Context, text: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\"", "\"\"").replace("\n", " ")
        return if (clean.contains(",") || clean.contains("\"")) "\"$clean\"" else clean
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
