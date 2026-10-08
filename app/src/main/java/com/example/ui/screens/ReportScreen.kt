package com.example.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CategoryEntity
import com.example.data.CustomerEntity
import com.example.data.PaymentMethodEntity
import com.example.data.ProductEntity
import com.example.data.TransactionWithItems
import com.example.service.BackupAndExportService
import com.example.util.SecurityAndFormatUtils
import java.util.Calendar

data class DailyPaymentMethodRecapRow(
    val methodName: String,
    val transactionCount: Int,
    val totalNominal: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    transactions: List<TransactionWithItems>,
    paymentMethods: List<PaymentMethodEntity>,
    products: List<ProductEntity>,
    categories: List<CategoryEntity>,
    customers: List<CustomerEntity>,
    storeName: String,
    onExportCsv: (Uri, List<TransactionWithItems>, Boolean) -> Unit,
    onExportPdf: (Uri, String, List<TransactionWithItems>) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("REKAP", "Rincian", "Per Menu", "Per Kategori", "Per Kasir")

    // Date state for Daily Rekap & Period Filtering
    var selectedRecapDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var periodFilter by remember { mutableStateOf("HARI_INI") } // HARI_INI, KEMARIN, PILIH_TANGGAL, MINGGU_INI, BULAN_INI, SEMUA

    val now = System.currentTimeMillis()
    val (startMillis, endMillis, periodLabel) = remember(periodFilter, selectedRecapDateMillis, now) {
        when (periodFilter) {
            "HARI_INI" -> Triple(
                SecurityAndFormatUtils.getStartOfDay(now),
                SecurityAndFormatUtils.getEndOfDay(now),
                SecurityAndFormatUtils.formatDateOnly(now)
            )
            "KEMARIN" -> {
                val yesterday = now - 24L * 60 * 60 * 1000
                Triple(
                    SecurityAndFormatUtils.getStartOfDay(yesterday),
                    SecurityAndFormatUtils.getEndOfDay(yesterday),
                    SecurityAndFormatUtils.formatDateOnly(yesterday)
                )
            }
            "PILIH_TANGGAL" -> Triple(
                SecurityAndFormatUtils.getStartOfDay(selectedRecapDateMillis),
                SecurityAndFormatUtils.getEndOfDay(selectedRecapDateMillis),
                SecurityAndFormatUtils.formatDateOnly(selectedRecapDateMillis)
            )
            "MINGGU_INI" -> Triple(
                SecurityAndFormatUtils.getStartOfDay(now - 6L * 24 * 60 * 60 * 1000),
                SecurityAndFormatUtils.getEndOfDay(now),
                "7 Hari Terakhir"
            )
            "BULAN_INI" -> Triple(
                SecurityAndFormatUtils.getStartOfMonth(now),
                SecurityAndFormatUtils.getEndOfDay(now),
                "Bulan Ini"
            )
            else -> Triple(0L, Long.MAX_VALUE, "Semua Periode")
        }
    }

    // ONLY PAID / COMPLETED (LUNAS) transactions are counted in Rekap & Sales Reports!
    // Unpaid billings (BELUM BAYAR) and Cancelled billings are strictly excluded from sales calculations.
    val paidTransactions = remember(transactions, startMillis, endMillis) {
        transactions.filter {
            (it.transaction.status == "PAID" || it.transaction.status == "COMPLETED") &&
                it.transaction.timestamp in startMillis..endMillis
        }
    }

    val unpaidCountInPeriod = remember(transactions, startMillis, endMillis) {
        transactions.count {
            it.transaction.status == "UNPAID" &&
                it.transaction.timestamp in startMillis..endMillis
        }
    }

    val totalOmzet = remember(paidTransactions) { paidTransactions.sumOf { it.transaction.totalAmount } }
    val totalTxCount = paidTransactions.size

    // Build Payment Method Recap Rows:
    // 1. Automatically includes all saved payment methods in their sortOrder
    // 2. Also includes any historical payment method names present in paidTransactions so renamed/deleted methods in historical transactions are still accurately shown
    val paymentMethodRecapRows = remember(paidTransactions, paymentMethods) {
        val rows = mutableListOf<DailyPaymentMethodRecapRow>()
        val accountedMethodNames = mutableSetOf<String>()

        val effectiveMethods = paymentMethods.ifEmpty {
            listOf(
                PaymentMethodEntity(id = 1, name = "TUNAI", isCashType = true, sortOrder = 1),
                PaymentMethodEntity(id = 2, name = "TRANSFER", isCashType = false, sortOrder = 2),
                PaymentMethodEntity(id = 3, name = "QRIS", isCashType = false, sortOrder = 3),
                PaymentMethodEntity(id = 4, name = "GOJEK", isCashType = false, sortOrder = 4),
                PaymentMethodEntity(id = 5, name = "SHOPEE", isCashType = false, sortOrder = 5),
                PaymentMethodEntity(id = 6, name = "GRAB", isCashType = false, sortOrder = 6)
            )
        }

        for (method in effectiveMethods) {
            val cleanName = method.name.trim().uppercase()
            if (cleanName.isNotBlank() && accountedMethodNames.add(cleanName)) {
                val matchingTxs = paidTransactions.filter {
                    it.transaction.paymentMethod.trim().equals(cleanName, ignoreCase = true)
                }
                rows.add(
                    DailyPaymentMethodRecapRow(
                        methodName = cleanName,
                        transactionCount = matchingTxs.size,
                        totalNominal = matchingTxs.sumOf { it.transaction.totalAmount }
                    )
                )
            }
        }

        // Also include any historical payment methods used in paidTransactions that were later renamed or deleted
        val historicalGroups = paidTransactions.groupBy { it.transaction.paymentMethod.trim().uppercase() }
        for ((histName, txList) in historicalGroups) {
            val safeName = histName.ifBlank { "LAINNYA" }
            if (accountedMethodNames.add(safeName)) {
                rows.add(
                    DailyPaymentMethodRecapRow(
                        methodName = safeName,
                        transactionCount = txList.size,
                        totalNominal = txList.sumOf { it.transaction.totalAmount }
                    )
                )
            }
        }

        rows
    }

    val openDatePicker = {
        val cal = Calendar.getInstance().apply {
            timeInMillis = when (periodFilter) {
                "KEMARIN" -> System.currentTimeMillis() - 24L * 60 * 60 * 1000
                "PILIH_TANGGAL" -> selectedRecapDateMillis
                else -> System.currentTimeMillis()
            }
        }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val pickedCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                selectedRecapDateMillis = pickedCal.timeInMillis
                periodFilter = "PILIH_TANGGAL"
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val shiftSelectedDay: (Int) -> Unit = { dayDelta ->
        val baseMillis = when (periodFilter) {
            "KEMARIN" -> System.currentTimeMillis() - 24L * 60 * 60 * 1000
            "PILIH_TANGGAL" -> selectedRecapDateMillis
            else -> System.currentTimeMillis()
        }
        val shifted = baseMillis + dayDelta * 24L * 60 * 60 * 1000
        selectedRecapDateMillis = shifted
        val todayKey = SecurityAndFormatUtils.formatDateKey(System.currentTimeMillis())
        val shiftedKey = SecurityAndFormatUtils.formatDateKey(shifted)
        periodFilter = if (shiftedKey == todayKey) "HARI_INI" else "PILIH_TANGGAL"
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            onExportCsv(uri, paidTransactions, false)
        }
    }

    val excelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/tab-separated-values")
    ) { uri ->
        if (uri != null) {
            onExportCsv(uri, paidTransactions, true)
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            onExportPdf(uri, periodLabel, paidTransactions)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("report_tab_$index")
                )
            }
        }

        // Period Filter Bar
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val periods = listOf(
                "HARI_INI" to "Hari Ini",
                "KEMARIN" to "Kemarin",
                "PILIH_TANGGAL" to "Pilih Tanggal",
                "MINGGU_INI" to "7 Hari",
                "BULAN_INI" to "Bulan Ini",
                "SEMUA" to "Semua"
            )
            items(periods) { (key, label) ->
                FilterChip(
                    selected = periodFilter == key,
                    onClick = {
                        if (key == "PILIH_TANGGAL") {
                            openDatePicker()
                        } else {
                            periodFilter = key
                        }
                    },
                    label = {
                        Text(
                            text = if (key == "PILIH_TANGGAL" && periodFilter == "PILIH_TANGGAL") {
                                "Tgl: $periodLabel"
                            } else {
                                label
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    modifier = Modifier.testTag("chip_period_$key")
                )
            }
        }

        // Export Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { csvLauncher.launch("Laporan_KasirKu_${periodFilter}.csv") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_export_csv"),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CSV", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { excelLauncher.launch("Laporan_KasirKu_${periodFilter}.xls") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_export_excel"),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Excel", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = { pdfLauncher.launch("Laporan_KasirKu_${periodFilter}.pdf") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_export_pdf"),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PDF", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> DailyRecapReportTab(
                storeName = storeName,
                dateLabel = periodLabel,
                recapRows = paymentMethodRecapRows,
                totalSales = totalOmzet,
                totalPaidCount = totalTxCount,
                unpaidCountInPeriod = unpaidCountInPeriod,
                onPreviousDay = { shiftSelectedDay(-1) },
                onNextDay = { shiftSelectedDay(1) },
                onPickDate = openDatePicker,
                onShareRecap = {
                    val recapText = buildString {
                        appendLine("================================")
                        appendLine("REKAP PENJUALAN - ${storeName.uppercase()}")
                        appendLine("Tanggal: $periodLabel")
                        appendLine("================================")
                        appendLine()
                        paymentMethodRecapRows.forEach { row ->
                            appendLine("${row.methodName} : ${SecurityAndFormatUtils.formatRupiah(row.totalNominal)}")
                        }
                        appendLine()
                        appendLine("--------------------------------")
                        appendLine("TOTAL PENJUALAN : ${SecurityAndFormatUtils.formatRupiah(totalOmzet)}")
                        appendLine("Jumlah Billing Lunas : $totalTxCount Transaksi")
                        appendLine("================================")
                    }
                    BackupAndExportService.shareReceiptText(
                        context = context,
                        title = "Rekap Penjualan $periodLabel",
                        receiptText = recapText
                    )
                }
            )
            1 -> SalesTransactionsDetailTab(
                paidTransactions = paidTransactions,
                totalOmzet = totalOmzet,
                totalTxCount = totalTxCount
            )
            2 -> ProductPerformanceReportTab(paidTransactions = paidTransactions)
            3 -> CategoryPerformanceReportTab(paidTransactions = paidTransactions, products = products)
            4 -> CashierPerformanceReportTab(paidTransactions = paidTransactions)
        }
    }
}

@Composable
private fun DailyRecapReportTab(
    storeName: String,
    dateLabel: String,
    recapRows: List<DailyPaymentMethodRecapRow>,
    totalSales: Double,
    totalPaidCount: Int,
    unpaidCountInPeriod: Int,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onPickDate: () -> Unit,
    onShareRecap: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Date Selector Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousDay,
                        modifier = Modifier.testTag("btn_recap_prev_day")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Hari Sebelumnya"
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPickDate() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("btn_recap_pick_date")
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Tanggal: $dateLabel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Ketuk untuk pilih tanggal kalender",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onNextDay,
                        modifier = Modifier.testTag("btn_recap_next_day")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Hari Berikutnya"
                        )
                    }
                }
            }
        }

        // Main Daily Rekap Card (Formatted clearly per Payment Method)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_daily_rekap"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "REKAP PENJUALAN HARIAN",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Tanggal: $dateLabel",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onShareRecap,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_share_rekap")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan Rekap", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Separate breakdown row for each payment method
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        recapRows.forEach { row ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (row.totalNominal > 0) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rekap_row_${row.methodName}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Payments,
                                            contentDescription = null,
                                            tint = if (row.totalNominal > 0) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = row.methodName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "${row.transactionCount} Billing Lunas",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = SecurityAndFormatUtils.formatRupiah(row.totalNominal),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (row.totalNominal > 0) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    // Total Penjualan Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F766E),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rekap_total_penjualan_box")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL PENJUALAN",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF99F6E4)
                                )
                                Text(
                                    text = "$totalPaidCount Billing Lunas Dihitung",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            Text(
                                text = SecurityAndFormatUtils.formatRupiah(totalSales),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.testTag("text_rekap_total_penjualan")
                            )
                        }
                    }

                    if (unpaidCountInPeriod > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Catatan: Terdapat $unpaidCountInPeriod Billing berstatus BELUM BAYAR yang belum masuk ke Rekap Penjualan (hanya transaksi LUNAS yang dihitung).",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Plain Text Summary Box matching the exact receipt/recap style requested
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF9))
            ) {
                val formattedRecapSummary = remember(dateLabel, recapRows, totalSales) {
                    buildString {
                        appendLine("Tanggal: $dateLabel")
                        appendLine()
                        recapRows.forEach { row ->
                            appendLine("${row.methodName} : ${SecurityAndFormatUtils.formatRupiah(row.totalNominal)}")
                        }
                        appendLine()
                        append("TOTAL PENJUALAN : ${SecurityAndFormatUtils.formatRupiah(totalSales)}")
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Ringkasan Teks Rekap",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formattedRecapSummary,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.testTag("rekap_plain_text_summary")
                    )
                }
            }
        }
    }
}

@Composable
private fun SalesTransactionsDetailTab(
    paidTransactions: List<TransactionWithItems>,
    totalOmzet: Double,
    totalTxCount: Int
) {
    val avgPerTx = if (totalTxCount > 0) totalOmzet / totalTxCount else 0.0

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReportMetricCard(
                    title = "Total Penjualan Lunas",
                    value = SecurityAndFormatUtils.formatRupiah(totalOmzet),
                    subtitle = "$totalTxCount Billing Lunas",
                    color = Color(0xFF0F766E),
                    modifier = Modifier.weight(1f)
                )
                ReportMetricCard(
                    title = "Rata-rata per Billing",
                    value = SecurityAndFormatUtils.formatRupiah(avgPerTx),
                    subtitle = "Transaksi selesai",
                    color = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                text = "Daftar Transaksi Lunas (${paidTransactions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (paidTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "Belum ada transaksi lunas pada periode ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(paidTransactions, key = { it.transaction.id }) { tw ->
                val t = tw.transaction
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${t.billingDisplay} (${t.invoiceNumber})",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${SecurityAndFormatUtils.formatDateTime(t.timestamp)} • ${t.paymentMethod}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = tw.items.joinToString(", ") { "${it.productName} x${it.quantity}" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = SecurityAndFormatUtils.formatRupiah(t.totalAmount),
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "LUNAS",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductPerformanceReportTab(
    paidTransactions: List<TransactionWithItems>
) {
    data class ProductStat(
        val name: String,
        val qtySold: Int,
        val revenue: Double
    )

    val productStats = remember(paidTransactions) {
        val map = mutableMapOf<String, ProductStat>()
        for (tw in paidTransactions) {
            for (item in tw.items) {
                val cur = map[item.productName]
                val rev = item.subtotal
                if (cur == null) {
                    map[item.productName] = ProductStat(item.productName, item.quantity, rev)
                } else {
                    map[item.productName] = cur.copy(
                        qtySold = cur.qtySold + item.quantity,
                        revenue = cur.revenue + rev
                    )
                }
            }
        }
        map.values.sortedByDescending { it.qtySold }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Penjualan per Menu Makanan & Minuman",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (productStats.isEmpty()) {
            item {
                Text("Belum ada data penjualan menu pada periode ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(productStats) { stat ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stat.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Terjual: ${stat.qtySold} porsi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = SecurityAndFormatUtils.formatRupiah(stat.revenue),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPerformanceReportTab(
    paidTransactions: List<TransactionWithItems>,
    products: List<ProductEntity>
) {
    val productCategoryMap = remember(products) {
        products.associate { it.id to it.categoryName }
    }

    val categoryBreakdown = remember(paidTransactions, productCategoryMap) {
        val map = mutableMapOf<String, Pair<Int, Double>>()
        for (tw in paidTransactions) {
            for (item in tw.items) {
                val catName = productCategoryMap[item.productId] ?: "Menu"
                val cur = map[catName] ?: (0 to 0.0)
                map[catName] = (cur.first + item.quantity) to (cur.second + item.subtotal)
            }
        }
        map.entries.sortedByDescending { it.value.second }
    }

    val grandTotal = categoryBreakdown.sumOf { it.value.second }.coerceAtLeast(1.0)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Penjualan per Kategori Menu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (categoryBreakdown.isEmpty()) {
            item {
                Text("Belum ada data penjualan kategori pada periode ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(categoryBreakdown) { entry ->
                val catName = entry.key
                val (qty, nominal) = entry.value
                val share = (nominal / grandTotal).toFloat().coerceIn(0f, 1f)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(catName, fontWeight = FontWeight.Bold)
                            Text(
                                SecurityAndFormatUtils.formatRupiah(nominal),
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "$qty porsi terjual (${(share * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { share },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CashierPerformanceReportTab(
    paidTransactions: List<TransactionWithItems>
) {
    val byCashier = remember(paidTransactions) {
        paidTransactions.groupBy { it.transaction.cashierName }
            .mapValues { (_, list) ->
                list.size to list.sumOf { it.transaction.totalAmount }
            }
            .entries.sortedByDescending { it.value.second }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Rekap Penjualan per Kasir",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (byCashier.isEmpty()) {
            item {
                Text("Belum ada transaksi kasir pada periode ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(byCashier) { entry ->
                val cashierName = entry.key
                val (count, totalOmzet) = entry.value
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(cashierName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "$count Billing diselesaikan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = SecurityAndFormatUtils.formatRupiah(totalOmzet),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportMetricCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
