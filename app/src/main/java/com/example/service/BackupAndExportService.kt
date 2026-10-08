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
import com.example.data.DatabaseBackupSnapshot
import com.example.data.DebtPaymentEntity
import com.example.data.PaymentMethodEntity
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

    // --- SHARE RECEIPT OR KITCHEN TICKET AS PLAIN TEXT ---
    fun shareReceiptText(context: Context, title: String, receiptText: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, receiptText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "Bagikan via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    // --- GENERATE & SAVE/SHARE RECEIPT AS PDF ---
    suspend fun generateReceiptPdfFile(
        context: Context,
        txWithItems: TransactionWithItems,
        settings: StoreSettingsEntity
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeInvoice = txWithItems.transaction.invoiceNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val pdfFile = File(exportDir, "Struk_${safeInvoice}.pdf")

        val pdfDocument = PdfDocument()
        val pageWidth = if (settings.paperSizeMm >= 80) 280 else 210
        val estimatedHeight = (360 + txWithItems.items.size * 52).coerceAtLeast(420)
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, estimatedHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        renderReceiptCanvas(page.canvas, pageWidth, txWithItems, settings)
        pdfDocument.finishPage(page)

        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        pdfFile
    }

    suspend fun saveReceiptPdfToUri(
        context: Context,
        uri: Uri,
        txWithItems: TransactionWithItems,
        settings: StoreSettingsEntity
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val pdfDocument = PdfDocument()
            val pageWidth = if (settings.paperSizeMm >= 80) 280 else 210
            val estimatedHeight = (360 + txWithItems.items.size * 52).coerceAtLeast(420)
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, estimatedHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            renderReceiptCanvas(page.canvas, pageWidth, txWithItems, settings)
            pdfDocument.finishPage(page)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                pdfDocument.writeTo(out)
            } ?: throw IllegalStateException("Gagal menulis file PDF ke lokasi tujuan.")
            pdfDocument.close()
            "Struk PDF berhasil disimpan."
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun renderReceiptCanvas(
        canvas: Canvas,
        pageWidth: Int,
        txWithItems: TransactionWithItems,
        settings: StoreSettingsEntity
    ) {
        val tx = txWithItems.transaction
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 8.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        val leftBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val rightBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 0.8f
        }

        val margin = 12f
        val centerX = pageWidth / 2f
        val rightX = pageWidth - margin
        var y = 22f

        canvas.drawText(settings.storeName.uppercase(), centerX, y, titlePaint)
        y += 13f
        if (settings.storeAddress.isNotBlank()) {
            canvas.drawText(settings.storeAddress.take(36), centerX, y, centerPaint)
            y += 11f
        }
        if (settings.storePhone.isNotBlank()) {
            canvas.drawText("Telp: ${settings.storePhone}", centerX, y, centerPaint)
            y += 12f
        }

        canvas.drawLine(margin, y, rightX, y, linePaint)
        y += 12f

        canvas.drawText(tx.billingDisplay.uppercase(), margin, y, leftBoldPaint)
        y += 11f
        canvas.drawText("Waktu: ${SecurityAndFormatUtils.formatReceiptDateTime(tx.timestamp)}", margin, y, leftPaint)
        y += 11f
        if (tx.customerName.isNotBlank() && tx.customerName != "Pelanggan Umum") {
            canvas.drawText("Pelanggan: ${tx.customerName}", margin, y, leftPaint)
            y += 12f
        }

        canvas.drawLine(margin, y, rightX, y, linePaint)
        y += 13f

        for (item in txWithItems.items) {
            canvas.drawText(item.productName.take(30), margin, y, leftBoldPaint)
            y += 11f
            val qtyStr = "${item.quantity} x ${SecurityAndFormatUtils.formatRupiah(item.sellPrice)}"
            val subStr = SecurityAndFormatUtils.formatRupiah(item.subtotal)
            canvas.drawText(qtyStr, margin, y, leftPaint)
            canvas.drawText(subStr, rightX, y, rightPaint)
            y += 11f

            val portionNotes = item.portionNotes
            portionNotes.forEachIndexed { idx, note ->
                if (note.isNotBlank()) {
                    val label = if (portionNotes.size == 1) "Catatan: $note" else "- Porsi ${idx + 1}: $note"
                    canvas.drawText(label.take(34), margin + 4f, y, leftPaint)
                    y += 10f
                }
            }
            y += 2f
        }

        canvas.drawLine(margin, y, rightX, y, linePaint)
        y += 13f

        if (tx.discountAmount > 0) {
            canvas.drawText("SUBTOTAL", margin, y, leftPaint)
            canvas.drawText(SecurityAndFormatUtils.formatRupiah(tx.subtotal), rightX, y, rightPaint)
            y += 11f
            canvas.drawText("DISKON", margin, y, leftPaint)
            canvas.drawText("-${SecurityAndFormatUtils.formatRupiah(tx.discountAmount)}", rightX, y, rightPaint)
            y += 11f
        }

        canvas.drawText("TOTAL", margin, y, leftBoldPaint)
        canvas.drawText(SecurityAndFormatUtils.formatRupiah(tx.totalAmount), rightX, y, rightBoldPaint)
        y += 13f

        if (tx.status == "PAID" || tx.status == "COMPLETED") {
            val methodUpper = tx.paymentMethod.uppercase()
            canvas.drawText("METODE BAYAR", margin, y, leftPaint)
            canvas.drawText(methodUpper, rightX, y, rightBoldPaint)
            y += 11f
            canvas.drawText(methodUpper, margin, y, leftPaint)
            canvas.drawText(SecurityAndFormatUtils.formatRupiah(tx.amountPaid), rightX, y, rightPaint)
            y += 11f
            canvas.drawText("KEMBALI", margin, y, leftPaint)
            canvas.drawText(SecurityAndFormatUtils.formatRupiah(tx.changeAmount), rightX, y, rightPaint)
            y += 16f
        } else {
            canvas.drawText("STATUS", margin, y, leftPaint)
            canvas.drawText(if (tx.status == "UNPAID") "BELUM BAYAR" else "DIBATALKAN", rightX, y, rightBoldPaint)
            y += 16f
        }

        canvas.drawLine(margin, y, rightX, y, linePaint)
        y += 14f
        canvas.drawText(settings.receiptFooter.ifBlank { "TERIMA KASIH" }.take(38), centerX, y, centerPaint)
    }

    // --- EXPORT REPORTS TO CSV OR EXCEL-COMPATIBLE TSV/CSV ---
    suspend fun exportTransactionsToCsvUri(
        context: Context,
        uri: Uri,
        transactions: List<TransactionWithItems>,
        isExcelFormat: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val separator = if (isExcelFormat) "\t" else ","
            val sb = StringBuilder()
            if (isExcelFormat) {
                sb.append('\uFEFF')
            }
            sb.appendLine(
                listOf(
                    "Nomor Billing",
                    "No Invoice",
                    "Waktu",
                    "Kasir",
                    "Pelanggan",
                    "Status",
                    "Metode Bayar",
                    "Subtotal",
                    "Diskon",
                    "Pajak",
                    "Biaya Tambahan",
                    "Total Akhir",
                    "Daftar Pesanan & Catatan Porsi"
                ).joinToString(separator)
            )

            for (tw in transactions) {
                val t = tw.transaction
                val itemDetails = tw.items.joinToString(" | ") { item ->
                    val notesSummary = SecurityAndFormatUtils.formatPortionNotesSummary(item.portionNotes)
                    if (notesSummary.isNotEmpty()) {
                        "${item.productName} x${item.quantity} (${notesSummary.joinToString("; ")})"
                    } else {
                        "${item.productName} x${item.quantity}"
                    }
                }
                val statusLabel = when (t.status) {
                    "PAID", "COMPLETED" -> "LUNAS"
                    "UNPAID" -> "BELUM BAYAR"
                    else -> "DIBATALKAN"
                }

                val row = listOf(
                    escapeCsv(t.billingDisplay, separator),
                    escapeCsv(t.invoiceNumber, separator),
                    escapeCsv(SecurityAndFormatUtils.formatDateTime(t.timestamp), separator),
                    escapeCsv(t.cashierName, separator),
                    escapeCsv(t.customerName, separator),
                    escapeCsv(statusLabel, separator),
                    escapeCsv(t.paymentMethod, separator),
                    t.subtotal.toLong().toString(),
                    t.discountAmount.toLong().toString(),
                    t.taxAmount.toLong().toString(),
                    t.serviceFee.toLong().toString(),
                    t.totalAmount.toLong().toString(),
                    escapeCsv(itemDetails, separator)
                )
                sb.appendLine(row.joinToString(separator))
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Gagal membuka file tujuan.")

            if (isExcelFormat) "Laporan Excel (.xls/.csv) berhasil disimpan."
            else "Laporan CSV berhasil disimpan."
        }
    }

    private fun escapeCsv(value: String, separator: String): String {
        val clean = value.replace("\n", " ").replace("\r", " ")
        return if (clean.contains(separator) || clean.contains("\"")) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }

    // --- EXPORT REPORT AS PDF ---
    suspend fun exportSalesReportPdfToUri(
        context: Context,
        uri: Uri,
        periodTitle: String,
        storeName: String,
        transactions: List<TransactionWithItems>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val paid = transactions.filter {
                it.transaction.status == "PAID" || it.transaction.status == "COMPLETED"
            }
            val totalOmzet = paid.sumOf { it.transaction.totalAmount }
            val byPaymentMethod = paid.groupBy { it.transaction.paymentMethod.uppercase() }
                .mapValues { entry -> entry.value.sumOf { it.transaction.totalAmount } }

            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = (500 + byPaymentMethod.size * 20 + paid.take(35).size * 18).coerceAtLeast(842)
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawColor(Color.WHITE)
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 11f
            }
            val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 9.5f
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            var y = 40f
            canvas.drawText("LAPORAN & REKAP PENJUALAN - ${storeName.uppercase()}", 36f, y, headerPaint)
            y += 20f
            canvas.drawText("Periode: $periodTitle", 36f, y, subPaint)
            y += 16f
            canvas.drawText(
                "Dicetak pada: ${SecurityAndFormatUtils.formatDateTime(System.currentTimeMillis())}",
                36f,
                y,
                subPaint
            )
            y += 20f
            canvas.drawLine(36f, y, 559f, y, linePaint)
            y += 22f

            canvas.drawText("REKAP METODE PEMBAYARAN (TRANSAKSI LUNAS)", 36f, y, boldPaint)
            y += 18f
            byPaymentMethod.forEach { (methodName, nominal) ->
                canvas.drawText("$methodName : ${SecurityAndFormatUtils.formatRupiah(nominal)}", 36f, y, rowPaint)
                y += 16f
            }
            y += 4f
            canvas.drawText("TOTAL PENJUALAN: ${SecurityAndFormatUtils.formatRupiah(totalOmzet)}", 36f, y, boldPaint)
            y += 16f
            canvas.drawText("Jumlah Billing Lunas: ${paid.size} Transaksi", 36f, y, subPaint)
            y += 22f

            canvas.drawLine(36f, y, 559f, y, linePaint)
            y += 20f

            canvas.drawText("DAFTAR TRANSAKSI LUNAS", 36f, y, boldPaint)
            y += 18f

            for (tw in paid.take(35)) {
                val t = tw.transaction
                val lineText =
                    "${t.billingDisplay} (${t.invoiceNumber}) | ${SecurityAndFormatUtils.formatDateTime(t.timestamp)} | ${t.paymentMethod} | ${SecurityAndFormatUtils.formatRupiah(t.totalAmount)}"
                canvas.drawText(lineText.take(95), 36f, y, rowPaint)
                y += 16f
            }

            pdfDocument.finishPage(page)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                pdfDocument.writeTo(out)
            } ?: throw IllegalStateException("Gagal menyimpan file PDF laporan.")
            pdfDocument.close()
            "Laporan PDF berhasil disimpan."
        }
    }

    // --- BACKUP & RESTORE JSON SERIALIZATION ---
    suspend fun writeBackupToUri(
        context: Context,
        uri: Uri,
        snapshot: DatabaseBackupSnapshot
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val json = serializeSnapshotToJson(snapshot)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Tidak dapat menulis ke lokasi file backup.")
            "Backup database berhasil disimpan."
        }
    }

    suspend fun readBackupFromUri(
        context: Context,
        uri: Uri
    ): Result<DatabaseBackupSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: throw IllegalStateException("File backup tidak dapat dibaca.")
            deserializeSnapshotFromJson(raw)
        }
    }

    fun serializeSnapshotToJson(snapshot: DatabaseBackupSnapshot): String {
        val root = JSONObject()
        root.put("version", snapshot.version)
        root.put("exportedAt", snapshot.exportedAt)

        // Categories
        val catArr = JSONArray()
        snapshot.categories.forEach { c ->
            catArr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("description", c.description)
                    put("colorHex", c.colorHex)
                    put("iconName", c.iconName)
                    put("createdAt", c.createdAt)
                }
            )
        }
        root.put("categories", catArr)

        // Payment Methods
        val pmArr = JSONArray()
        snapshot.paymentMethods.forEach { pm ->
            pmArr.put(
                JSONObject().apply {
                    put("id", pm.id)
                    put("name", pm.name)
                    put("isCashType", pm.isCashType)
                    put("sortOrder", pm.sortOrder)
                    put("createdAt", pm.createdAt)
                }
            )
        }
        root.put("paymentMethods", pmArr)

        // Products
        val prodArr = JSONArray()
        snapshot.products.forEach { p ->
            prodArr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
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
        root.put("products", prodArr)

        // Customers
        val custArr = JSONArray()
        snapshot.customers.forEach { c ->
            custArr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("phone", c.phone)
                    put("address", c.address)
                    put("notes", c.notes)
                    put("totalDebt", c.totalDebt)
                    put("createdAt", c.createdAt)
                }
            )
        }
        root.put("customers", custArr)

        // Transactions
        val txArr = JSONArray()
        snapshot.transactions.forEach { t ->
            txArr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("invoiceNumber", t.invoiceNumber)
                    put("billingNumber", t.billingNumber)
                    put("billingDate", t.billingDate)
                    put("timestamp", t.timestamp)
                    put("cashierId", t.cashierId)
                    put("cashierName", t.cashierName)
                    if (t.customerId != null) put("customerId", t.customerId)
                    put("customerName", t.customerName)
                    put("subtotal", t.subtotal)
                    put("discountAmount", t.discountAmount)
                    put("discountType", t.discountType)
                    put("discountInput", t.discountInput)
                    put("taxPercentage", t.taxPercentage)
                    put("taxAmount", t.taxAmount)
                    put("serviceFee", t.serviceFee)
                    put("roundingAmount", t.roundingAmount)
                    put("totalAmount", t.totalAmount)
                    if (t.paymentMethodId != null) put("paymentMethodId", t.paymentMethodId)
                    put("paymentMethod", t.paymentMethod)
                    put("amountPaid", t.amountPaid)
                    put("changeAmount", t.changeAmount)
                    put("notes", t.notes)
                    put("status", t.status)
                    put("cancelReason", t.cancelReason)
                    put("isDebt", t.isDebt)
                    put("debtPaidAmount", t.debtPaidAmount)
                }
            )
        }
        root.put("transactions", txArr)

        // Transaction Items
        val itemArr = JSONArray()
        snapshot.transactionItems.forEach { i ->
            itemArr.put(
                JSONObject().apply {
                    put("id", i.id)
                    put("transactionId", i.transactionId)
                    put("productId", i.productId)
                    put("productName", i.productName)
                    put("unit", i.unit)
                    put("sellPrice", i.sellPrice)
                    put("quantity", i.quantity)
                    put("itemNote", i.itemNote)
                    put("subtotal", i.subtotal)
                }
            )
        }
        root.put("transactionItems", itemArr)

        // Debt Payments
        val dpArr = JSONArray()
        snapshot.debtPayments.forEach { d ->
            dpArr.put(
                JSONObject().apply {
                    put("id", d.id)
                    put("customerId", d.customerId)
                    put("customerName", d.customerName)
                    if (d.transactionId != null) put("transactionId", d.transactionId)
                    put("amount", d.amount)
                    put("paymentMethod", d.paymentMethod)
                    put("notes", d.notes)
                    put("timestamp", d.timestamp)
                    put("cashierName", d.cashierName)
                }
            )
        }
        root.put("debtPayments", dpArr)

        // Users
        val userArr = JSONArray()
        snapshot.cashierUsers.forEach { u ->
            userArr.put(
                JSONObject().apply {
                    put("id", u.id)
                    put("name", u.name)
                    put("role", u.role)
                    put("pinHash", u.pinHash)
                    put("isActive", u.isActive)
                    put("canEditPrice", u.canEditPrice)
                    put("canGiveDiscount", u.canGiveDiscount)
                    put("canCancelTransaction", u.canCancelTransaction)
                    put("canViewReports", u.canViewReports)
                }
            )
        }
        root.put("cashierUsers", userArr)

        // Settings
        val s = snapshot.storeSettings
        val settingsObj = JSONObject().apply {
            put("storeName", s.storeName)
            put("storeAddress", s.storeAddress)
            put("storePhone", s.storePhone)
            put("storeEmail", s.storeEmail)
            put("storeLogoUri", s.storeLogoUri)
            put("receiptHeader", s.receiptHeader)
            put("receiptFooter", s.receiptFooter)
            put("currencySymbol", s.currencySymbol)
            put("defaultTaxPercentage", s.defaultTaxPercentage)
            put("defaultServiceFee", s.defaultServiceFee)
            put("enableTaxByDefault", s.enableTaxByDefault)
            put("enableRounding", s.enableRounding)
            put("roundingMultiple", s.roundingMultiple)
            put("autoPrintReceipt", s.autoPrintReceipt)
            put("autoPrintKitchenTicket", s.autoPrintKitchenTicket)
            put("printCopies", s.printCopies)
            put("paperSizeMm", s.paperSizeMm)
            put("defaultPrinterName", s.defaultPrinterName)
            put("defaultPrinterAddress", s.defaultPrinterAddress)
            put("requirePinOnStartup", s.requirePinOnStartup)
            put("isDarkMode", s.isDarkMode)
            put("productViewMode", s.productViewMode)
            put("gridColumns", s.gridColumns)
            put("invoicePrefix", s.invoicePrefix)
            put("lastBillingDate", s.lastBillingDate)
            put("lastBillingSequence", s.lastBillingSequence)
        }
        root.put("storeSettings", settingsObj)

        return root.toString(2)
    }

    fun deserializeSnapshotFromJson(jsonString: String): DatabaseBackupSnapshot {
        val root = JSONObject(jsonString)

        val categories = mutableListOf<CategoryEntity>()
        root.optJSONArray("categories")?.let { arr ->
            for (idx in 0 until arr.length()) {
                val o = arr.getJSONObject(idx)
                categories.add(
                    CategoryEntity(
                        id = o.optLong("id", 0),
                        name = o.optString("name", "Umum"),
                        description = o.optString("description", ""),
                        colorHex = o.optString("colorHex", "#0F766E"),
                        iconName = o.optString("iconName", "Category"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val paymentMethods = mutableListOf<PaymentMethodEntity>()
        root.optJSONArray("paymentMethods")?.let { arr ->
            for (idx in 0 until arr.length()) {
                val o = arr.getJSONObject(idx)
                paymentMethods.add(
                    PaymentMethodEntity(
                        id = o.optLong("id", 0),
                        name = o.optString("name", "TUNAI"),
                        isCashType = o.optBoolean("isCashType", false),
                        sortOrder = o.optInt("sortOrder", idx + 1),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val products = mutableListOf<ProductEntity>()
        root.optJSONArray("products")?.let { arr ->
            for (idx in 0 until arr.length()) {
                val o = arr.getJSONObject(idx)
                products.add(
                    ProductEntity(
                        id = o.optLong("id", 0),
                        name = o.optString("name", ""),
                        sellPrice = o.optDouble("sellPrice", 0.0),
                        unit = o.optString("unit", "Porsi"),
                        categoryId = o.optLong("categoryId", 1),
                        categoryName = o.optString("categoryName", "Makanan"),
                        description = o.optString("description", ""),
                        imageUri = o.optString("imageUri", ""),
                        isActive = o.optBoolean("isActive", true),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val customers = mutableListOf<CustomerEntity>()
        root.optJSONArray("customers")?.let { arr ->
            for (idx in 0 until arr.length()) {
                val o = arr.getJSONObject(idx)
                customers.add(
                    CustomerEntity(
                        id = o.optLong("id", 0),
                        name = o.optString("name", ""),
                        phone = o.optString("phone", ""),
                        address = o.optString("address", ""),
                        notes = o.optString("notes", ""),
                        totalDebt = o.optDouble("totalDebt", 0.0),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val transactions = mutableListOf<TransactionEntity>()
        root.optJSONArray("transactions")?.let { arr ->
            for (idx in 0 until arr.length()) {
                val o = arr.getJSONObject(idx)
                transactions.add(
                    TransactionEntity(
                        id = o.optLong("id", 0),
                        invoiceNumber = o.optString("invoiceNumber", ""),
                        billingNumber = o.optInt("billingNumber", 1),
                        billingDate = o.optString("billingDate", ""),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        cashierId = o.optLong("cashierId", 1),
                        cashierName = o.optString("cashierName", "Admin"),
                        customerId = if (o.has("customerId") && !o.isNull("customerId")) o.getLong("customerId") else null,
                        customerName = o.optString("customerName", "Pelanggan Umum"),
                        subtotal = o.optDouble("subtotal", 0.0),
                        discountAmount = o.optDouble("discountAmount", 0.0),
                        discountType = o.optString("discountType", "NOMINAL"),
                        discountInput = o.optDouble("discountInput", 0.0),
                        taxPercentage = o.optDouble("taxPercentage", 0.0),
                        taxAmount = o.optDouble("taxAmount", 0.0),
                        serviceFee = o.optDouble("serviceFee", 0.0),
                        roundingAmount = o.optDouble("roundingAmount", 0.0),
                        totalAmount = o.optDouble("totalAmount", 0.0),
                        paymentMethodId = if (o.has("paymentMethodId") && !o.isNull("paymentMethodId")) o.getLong("paymentMethodId") else null,
                        paymentMethod = o.optString("paymentMethod", "TUNAI"),
                        amountPaid = o.optDouble("amountPaid", 0.0),
                        changeAmount = o.optDouble("changeAmount", 0.0),
                        notes = o.optString("notes", ""),
                        status = o.optString("status", "PAID"),
                        cancelReason = o.optString("cancelReason", ""),
                        isDebt = o.optBoolean("isDebt", false),
                        debtPaidAmount = o.optDouble("debtPaidAmount", 0.0)
                    )
                )
            }
        }

        val transactionItems = mutableListOf<TransactionItemEntity>()
        root.optJSONArray("transactionItems")?.let { arr ->
            for (idx in 0 until arr.length()) {
                val o = arr.getJSONObject(idx)
                transactionItems.add(
                    TransactionItemEntity(
                        id = o.optLong("id", 0),
                        transactionId = o.optLong("transactionId", 0),
                        productId = o.optLong("productId", 0),
                        productName = o.optString("productName", ""),
                        unit = o.optString("unit", "Porsi"),
                        sellPrice = o.optDouble("sellPrice", 0.0),
                        quantity = o.optInt("quantity", 1),
                        itemNote = o.optString("itemNote", ""),
                        subtotal = o.optDouble("subtotal", 0.0)
                    )
                )
            }
        }

        val debtPayments = mutableListOf<DebtPaymentEntity>()
        root.optJSONArray("debtPayments")?.let { arr ->
            for (idx in 0 until arr.length()) {
                val o = arr.getJSONObject(idx)
                debtPayments.add(
                    DebtPaymentEntity(
                        id = o.optLong("id", 0),
                        customerId = o.optLong("customerId", 0),
                        customerName = o.optString("customerName", ""),
                        transactionId = if (o.has("transactionId") && !o.isNull("transactionId")) o.getLong("transactionId") else null,
                        amount = o.optDouble("amount", 0.0),
                        paymentMethod = o.optString("paymentMethod", "TUNAI"),
                        notes = o.optString("notes", ""),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        cashierName = o.optString("cashierName", "Admin")
                    )
                )
            }
        }

        val users = mutableListOf<CashierUserEntity>()
        root.optJSONArray("cashierUsers")?.let { arr ->
            for (idx in 0 until arr.length()) {
                val o = arr.getJSONObject(idx)
                users.add(
                    CashierUserEntity(
                        id = o.optLong("id", 0),
                        name = o.optString("name", "Admin"),
                        role = o.optString("role", "ADMIN"),
                        pinHash = o.optString("pinHash", SecurityAndFormatUtils.hashPin("1234")),
                        isActive = o.optBoolean("isActive", true),
                        canEditPrice = o.optBoolean("canEditPrice", true),
                        canGiveDiscount = o.optBoolean("canGiveDiscount", true),
                        canCancelTransaction = o.optBoolean("canCancelTransaction", true),
                        canViewReports = o.optBoolean("canViewReports", true)
                    )
                )
            }
        }

        val sObj = root.optJSONObject("storeSettings")
        val settings = if (sObj != null) {
            StoreSettingsEntity(
                id = 1,
                storeName = sObj.optString("storeName", "KasirKu Warung & Resto"),
                storeAddress = sObj.optString("storeAddress", ""),
                storePhone = sObj.optString("storePhone", ""),
                storeEmail = sObj.optString("storeEmail", ""),
                storeLogoUri = sObj.optString("storeLogoUri", ""),
                receiptHeader = sObj.optString("receiptHeader", "Selamat Datang"),
                receiptFooter = sObj.optString("receiptFooter", "Terima Kasih"),
                currencySymbol = sObj.optString("currencySymbol", "Rp"),
                defaultTaxPercentage = sObj.optDouble("defaultTaxPercentage", 0.0),
                defaultServiceFee = sObj.optDouble("defaultServiceFee", 0.0),
                enableTaxByDefault = sObj.optBoolean("enableTaxByDefault", false),
                enableRounding = sObj.optBoolean("enableRounding", false),
                roundingMultiple = sObj.optInt("roundingMultiple", 100),
                autoPrintReceipt = sObj.optBoolean("autoPrintReceipt", false),
                autoPrintKitchenTicket = sObj.optBoolean("autoPrintKitchenTicket", false),
                printCopies = sObj.optInt("printCopies", 1),
                paperSizeMm = sObj.optInt("paperSizeMm", 58),
                defaultPrinterName = sObj.optString("defaultPrinterName", ""),
                defaultPrinterAddress = sObj.optString("defaultPrinterAddress", ""),
                requirePinOnStartup = sObj.optBoolean("requirePinOnStartup", false),
                isDarkMode = sObj.optBoolean("isDarkMode", false),
                productViewMode = sObj.optString("productViewMode", "GRID"),
                gridColumns = sObj.optInt("gridColumns", 2),
                invoicePrefix = sObj.optString("invoicePrefix", "BIL"),
                lastBillingDate = sObj.optString("lastBillingDate", ""),
                lastBillingSequence = sObj.optInt("lastBillingSequence", 0)
            )
        } else {
            StoreSettingsEntity()
        }

        return DatabaseBackupSnapshot(
            version = root.optInt("version", 3),
            exportedAt = root.optLong("exportedAt", System.currentTimeMillis()),
            categories = categories,
            paymentMethods = paymentMethods,
            products = products,
            customers = customers,
            transactions = transactions,
            transactionItems = transactionItems,
            debtPayments = debtPayments,
            cashierUsers = users,
            storeSettings = settings
        )
    }
}
