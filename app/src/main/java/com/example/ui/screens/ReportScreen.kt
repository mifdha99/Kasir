package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ProductEntity
import com.example.data.TransactionWithItems
import com.example.util.SecurityAndFormatUtils
import com.example.viewmodel.ReportFilterPeriod

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    transactions: List<TransactionWithItems>,
    products: List<ProductEntity>,
    onExportCsv: (Uri, String, String, List<TransactionWithItems>) -> Unit,
    onExportPdf: (String, List<TransactionWithItems>) -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(ReportFilterPeriod.TODAY) }
    var customStartMs by remember { mutableLongStateOf(SecurityAndFormatUtils.getStartOfDaysAgo(7)) }
    var customEndMs by remember { mutableLongStateOf(SecurityAndFormatUtils.getEndOfDay()) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var selectedReportTab by remember { mutableIntStateOf(0) }

    val now = System.currentTimeMillis()

    // Quick Dashboard Benchmarks (always visible at top)
    val completedAll = remember(transactions) {
        transactions.filter { it.transaction.status == "COMPLETED" }
    }
    val salesToday = remember(completedAll) {
        val s = SecurityAndFormatUtils.getStartOfDay(now)
        val e = SecurityAndFormatUtils.getEndOfDay(now)
        completedAll.filter { it.transaction.timestamp in s..e }.sumOf { it.transaction.totalAmount }
    }
    val salesYesterday = remember(completedAll) {
        val s = SecurityAndFormatUtils.getStartOfYesterday()
        val e = SecurityAndFormatUtils.getEndOfYesterday()
        completedAll.filter { it.transaction.timestamp in s..e }.sumOf { it.transaction.totalAmount }
    }
    val salesThisWeek = remember(completedAll) {
        val s = SecurityAndFormatUtils.getStartOfCurrentWeek()
        completedAll.filter { it.transaction.timestamp >= s }.sumOf { it.transaction.totalAmount }
    }
    val salesThisMonth = remember(completedAll) {
        val s = SecurityAndFormatUtils.getStartOfCurrentMonth()
        completedAll.filter { it.transaction.timestamp >= s }.sumOf { it.transaction.totalAmount }
    }

    val (startMs, endMs) = remember(selectedPeriod, customStartMs, customEndMs) {
        when (selectedPeriod) {
            ReportFilterPeriod.TODAY -> SecurityAndFormatUtils.getStartOfDay() to SecurityAndFormatUtils.getEndOfDay()
            ReportFilterPeriod.YESTERDAY -> SecurityAndFormatUtils.getStartOfYesterday() to SecurityAndFormatUtils.getEndOfYesterday()
            ReportFilterPeriod.LAST_7_DAYS -> SecurityAndFormatUtils.getStartOfDaysAgo(6) to SecurityAndFormatUtils.getEndOfDay()
            ReportFilterPeriod.THIS_MONTH -> SecurityAndFormatUtils.getStartOfCurrentMonth() to SecurityAndFormatUtils.getEndOfDay()
            ReportFilterPeriod.LAST_MONTH -> SecurityAndFormatUtils.getStartOfLastMonth() to SecurityAndFormatUtils.getEndOfLastMonth()
            ReportFilterPeriod.CUSTOM -> SecurityAndFormatUtils.getStartOfDay(customStartMs) to SecurityAndFormatUtils.getEndOfDay(customEndMs)
        }
    }

    val periodLabel = remember(selectedPeriod, startMs, endMs) {
        "${selectedPeriod.label} (${SecurityAndFormatUtils.formatDate(startMs)} - ${SecurityAndFormatUtils.formatDate(endMs)})"
    }

    val filteredTransactions = remember(transactions, startMs, endMs) {
        transactions.filter { it.transaction.timestamp in startMs..endMs }
    }
    val filteredCompleted = remember(filteredTransactions) {
        filteredTransactions.filter { it.transaction.status == "COMPLETED" }
    }

    val periodTotalSales = remember(filteredCompleted) {
        filteredCompleted.sumOf { it.transaction.totalAmount }
    }
    val periodTotalCost = remember(filteredCompleted) {
        filteredCompleted.sumOf { it.transaction.totalCost }
    }
    val periodTotalDiscount = remember(filteredCompleted) {
        filteredCompleted.sumOf { it.transaction.discountAmount }
    }
    val periodTotalTax = remember(filteredCompleted) {
        filteredCompleted.sumOf { it.transaction.taxAmount }
    }
    val periodEstimatedProfit = remember(periodTotalSales, periodTotalCost, periodTotalTax) {
        periodTotalSales - periodTotalCost - periodTotalTax
    }
    val totalInventoryValue = remember(products) {
        products.sumOf { it.buyPrice * it.stock.coerceAtLeast(0) }
    }

    // Best selling products in filtered period
    val topProducts = remember(filteredCompleted) {
        data class ProdAgg(val name: String, val qty: Int, val revenue: Double, val profit: Double)
        val map = mutableMapOf<String, ProdAgg>()
        for (tw in filteredCompleted) {
            for (item in tw.items) {
                val cur = map[item.productName]
                val itemProfit = (item.sellPrice - item.buyPrice) * item.quantity
                if (cur == null) {
                    map[item.productName] = ProdAgg(item.productName, item.quantity, item.subtotal, itemProfit)
                } else {
                    map[item.productName] = cur.copy(
                        qty = cur.qty + item.quantity,
                        revenue = cur.revenue + item.subtotal,
                        profit = cur.profit + itemProfit
                    )
                }
            }
        }
        map.values.sortedByDescending { it.qty }
    }

    // Best selling categories in filtered period
    val topCategories = remember(filteredCompleted) {
        data class CatAgg(val categoryName: String, val qty: Int, val revenue: Double)
        val map = mutableMapOf<String, CatAgg>()
        for (tw in filteredCompleted) {
            for (item in tw.items) {
                val cat = item.categoryName.ifBlank { "Umum" }
                val cur = map[cat]
                if (cur == null) {
                    map[cat] = CatAgg(cat, item.quantity, item.subtotal)
                } else {
                    map[cat] = cur.copy(
                        qty = cur.qty + item.quantity,
                        revenue = cur.revenue + item.subtotal
                    )
                }
            }
        }
        map.values.sortedByDescending { it.revenue }
    }

    // Payment method breakdown
    val paymentBreakdown = remember(filteredCompleted) {
        filteredCompleted.groupBy { it.transaction.paymentMethod }
            .map { (method, list) ->
                Triple(method, list.size, list.sumOf { it.transaction.totalAmount })
            }
            .sortedByDescending { it.third }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            onExportCsv(uri, "Laporan Lengkap", periodLabel, filteredTransactions)
        }
    }

    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = customStartMs)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        pickerState.selectedDateMillis?.let { customStartMs = it }
                        showStartDatePicker = false
                    }
                ) {
                    Text("Pilih")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEndDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = customEndMs)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        pickerState.selectedDateMillis?.let { customEndMs = it }
                        showEndDatePicker = false
                    }
                ) {
                    Text("Pilih")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    val reportTabs = listOf(
        "Laporan Penjualan",
        "Laporan Keuntungan",
        "Produk & Kategori Terlaris",
        "Metode Pembayaran",
        "Laporan Stok"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Overview: Hari ini, Kemarin, Minggu ini, Bulan ini
        item {
            Text(
                text = "Ringkasan Penjualan Cepat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickStatCard("Hari Ini", SecurityAndFormatUtils.formatRupiah(salesToday), Modifier.weight(1f))
                QuickStatCard("Kemarin", SecurityAndFormatUtils.formatRupiah(salesYesterday), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickStatCard("Minggu Ini", SecurityAndFormatUtils.formatRupiah(salesThisWeek), Modifier.weight(1f))
                QuickStatCard("Bulan Ini", SecurityAndFormatUtils.formatRupiah(salesThisMonth), Modifier.weight(1f))
            }
        }

        // Period Filter & Export Buttons
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Filter Periode Laporan", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ReportFilterPeriod.entries.forEach { period ->
                            FilterChip(
                                selected = selectedPeriod == period,
                                onClick = { selectedPeriod = period },
                                label = { Text(period.label) }
                            )
                        }
                    }

                    if (selectedPeriod == ReportFilterPeriod.CUSTOM) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showStartDatePicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dari: ${SecurityAndFormatUtils.formatDate(customStartMs)}")
                            }
                            OutlinedButton(
                                onClick = { showEndDatePicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sampai: ${SecurityAndFormatUtils.formatDate(customEndMs)}")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Export CSV & PDF Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                exportCsvLauncher.launch("Laporan_KasirKu_${System.currentTimeMillis()}.csv")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_export_report_csv")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV")
                        }

                        OutlinedButton(
                            onClick = {
                                onExportPdf(periodLabel, filteredTransactions)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_export_report_pdf")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export PDF")
                        }
                    }
                }
            }
        }

        // Core Financial Summary for Selected Period
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()
                    ReportRow("Jumlah Transaksi Selesai", "${filteredCompleted.size} Transaksi")
                    ReportRow("Total Omzet Penjualan", SecurityAndFormatUtils.formatRupiah(periodTotalSales))
                    ReportRow("Total Diskon Diberikan", SecurityAndFormatUtils.formatRupiah(periodTotalDiscount))
                    ReportRow("Total Pajak Terkumpul", SecurityAndFormatUtils.formatRupiah(periodTotalTax))
                    ReportRow("Estimasi Keuntungan Bersih", SecurityAndFormatUtils.formatRupiah(periodEstimatedProfit), isHighlight = true)
                    ReportRow("Total Nilai Stok Persediaan", SecurityAndFormatUtils.formatRupiah(totalInventoryValue))
                }
            }
        }

        // Sub-report Tabs
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedReportTab,
                edgePadding = 0.dp
            ) {
                reportTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedReportTab == index,
                        onClick = { selectedReportTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        // Tab Content
        when (selectedReportTab) {
            0 -> {
                // Laporan Penjualan
                if (filteredCompleted.isEmpty()) {
                    item {
                        EmptyReportCard("Belum ada transaksi penjualan selesai pada periode ini.")
                    }
                } else {
                    items(filteredCompleted, key = { it.transaction.id }) { tw ->
                        val t = tw.transaction
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(t.invoiceNumber, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${SecurityAndFormatUtils.formatDateTime(t.timestamp)} • ${t.paymentMethod}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    SecurityAndFormatUtils.formatRupiah(t.totalAmount),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                // Laporan Keuntungan
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Analisis Laba Rugi Penjualan", fontWeight = FontWeight.Bold)
                            ReportRow("Penjualan Kotor (Subtotal)", SecurityAndFormatUtils.formatRupiah(filteredCompleted.sumOf { it.transaction.subtotal }))
                            ReportRow("Potongan Diskon", "-${SecurityAndFormatUtils.formatRupiah(periodTotalDiscount)}")
                            ReportRow("Harga Pokok Penjualan (Modal)", "-${SecurityAndFormatUtils.formatRupiah(periodTotalCost)}")
                            HorizontalDivider()
                            ReportRow("Estimasi Laba Bersih", SecurityAndFormatUtils.formatRupiah(periodEstimatedProfit), isHighlight = true)
                        }
                    }
                }
                items(topProducts) { prodAgg ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(prodAgg.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "Terjual: ${prodAgg.qty} • Omzet: ${SecurityAndFormatUtils.formatRupiah(prodAgg.revenue)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Laba", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    SecurityAndFormatUtils.formatRupiah(prodAgg.profit),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF059669)
                                )
                            }
                        }
                    }
                }
            }

            2 -> {
                // Produk Terlaris & Kategori Terlaris
                item {
                    Text("Kategori Terlaris", fontWeight = FontWeight.Bold)
                }
                if (topCategories.isEmpty()) {
                    item { EmptyReportCard("Belum ada data kategori terlaris.") }
                } else {
                    items(topCategories) { cat ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${cat.categoryName} (${cat.qty} item)", fontWeight = FontWeight.Bold)
                                Text(
                                    SecurityAndFormatUtils.formatRupiah(cat.revenue),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Produk Terlaris", fontWeight = FontWeight.Bold)
                }
                if (topProducts.isEmpty()) {
                    item { EmptyReportCard("Belum ada data produk terlaris.") }
                } else {
                    items(topProducts) { prod ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(prod.name, fontWeight = FontWeight.Bold)
                                    Text("Terjual: ${prod.qty} unit", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    SecurityAndFormatUtils.formatRupiah(prod.revenue),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            3 -> {
                // Laporan Metode Pembayaran
                if (paymentBreakdown.isEmpty()) {
                    item { EmptyReportCard("Belum ada transaksi untuk metode pembayaran.") }
                } else {
                    items(paymentBreakdown) { (method, count, amount) ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(method, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("$count Transaksi", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    SecurityAndFormatUtils.formatRupiah(amount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            4 -> {
                // Laporan Stok
                items(products, key = { it.id }) { p ->
                    val valModal = p.buyPrice * p.stock.coerceAtLeast(0)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "Stok: ${p.stock} ${p.unit} • Harga Beli: ${SecurityAndFormatUtils.formatRupiah(p.buyPrice)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Nilai Stok", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    SecurityAndFormatUtils.formatRupiah(valModal),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ReportRow(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontWeight = if (isHighlight) FontWeight.ExtraBold else FontWeight.Normal
        )
        Text(
            text = value,
            fontWeight = FontWeight.ExtraBold,
            color = if (isHighlight) Color(0xFF059669) else Color.Unspecified
        )
    }
}

@Composable
private fun EmptyReportCard(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
