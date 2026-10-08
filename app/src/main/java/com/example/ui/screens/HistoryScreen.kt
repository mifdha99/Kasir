package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CashierUserEntity
import com.example.data.TransactionWithItems
import com.example.util.SecurityAndFormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    transactions: List<TransactionWithItems>,
    activeCashier: CashierUserEntity?,
    onOpenReceiptModal: (TransactionWithItems) -> Unit,
    onOpenKitchenModal: (TransactionWithItems) -> Unit,
    onPayUnpaidBilling: (TransactionWithItems) -> Unit,
    onShareReceiptText: (TransactionWithItems) -> Unit,
    onCancelTransaction: (Long, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("PAID") } // "PAID", "UNPAID", "CANCELLED", "ALL"
    var transactionToCancel by remember { mutableStateOf<TransactionWithItems?>(null) }

    val filteredTransactions = remember(transactions, searchQuery, statusFilter) {
        transactions.filter { tw ->
            val tx = tw.transaction
            val statusMatch = statusFilter == "ALL" || tx.status == statusFilter
            val queryMatch = searchQuery.isBlank() ||
                tx.billingDisplay.contains(searchQuery, ignoreCase = true) ||
                tx.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                tx.customerName.contains(searchQuery, ignoreCase = true) ||
                tx.cashierName.contains(searchQuery, ignoreCase = true) ||
                tx.paymentMethod.contains(searchQuery, ignoreCase = true) ||
                tw.items.any { it.productName.contains(searchQuery, ignoreCase = true) }
            statusMatch && queryMatch
        }
    }

    if (transactionToCancel != null) {
        val target = transactionToCancel!!
        CancelTransactionConfirmDialog(
            txWithItems = target,
            onDismiss = { transactionToCancel = null },
            onConfirmCancel = { reason ->
                onCancelTransaction(target.transaction.id, reason)
                transactionToCancel = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari Billing, menu, pelanggan, kasir...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == "PAID",
                        onClick = { statusFilter = "PAID" },
                        label = { Text("Lunas (${transactions.count { it.transaction.status == "PAID" }})") }
                    )
                    FilterChip(
                        selected = statusFilter == "UNPAID",
                        onClick = { statusFilter = "UNPAID" },
                        label = { Text("Belum Bayar (${transactions.count { it.transaction.status == "UNPAID" }})") }
                    )
                    FilterChip(
                        selected = statusFilter == "CANCELLED",
                        onClick = { statusFilter = "CANCELLED" },
                        label = { Text("Batal (${transactions.count { it.transaction.status == "CANCELLED" }})") }
                    )
                    FilterChip(
                        selected = statusFilter == "ALL",
                        onClick = { statusFilter = "ALL" },
                        label = { Text("Semua (${transactions.size})") }
                    )
                }
            }
        }

        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tidak ada riwayat transaksi pada filter ini.", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredTransactions, key = { it.transaction.id }) { tw ->
                    val tx = tw.transaction
                    val isCancelled = tx.status == "CANCELLED"
                    val isUnpaid = tx.status == "UNPAID"
                    val canVoid = activeCashier?.canCancelTransaction ?: true

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isUnpaid) onOpenKitchenModal(tw) else onOpenReceiptModal(tw)
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = tx.billingDisplay,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "${SecurityAndFormatUtils.formatDateTime(tx.timestamp)} • Kasir: ${tx.cashierName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (isUnpaid) {
                                            "Pelanggan: ${tx.customerName} • Menunggu Pembayaran"
                                        } else {
                                            "Pelanggan: ${tx.customerName} • Metode: ${tx.paymentMethod}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val badgeBg = when {
                                    isCancelled -> MaterialTheme.colorScheme.errorContainer
                                    isUnpaid -> Color(0xFFFEF3C7)
                                    else -> Color(0xFFD1FAE5)
                                }
                                val badgeTextColor = when {
                                    isCancelled -> MaterialTheme.colorScheme.onErrorContainer
                                    isUnpaid -> Color(0xFF92400E)
                                    else -> Color(0xFF065F46)
                                }
                                val badgeLabel = when {
                                    isCancelled -> "DIBATALKAN"
                                    isUnpaid -> "BELUM BAYAR"
                                    else -> "LUNAS"
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = badgeBg
                                ) {
                                    Text(
                                        text = badgeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeTextColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            // Item preview with per-portion notes
                            tw.items.forEach { item ->
                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${item.quantity}x ${item.productName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = SecurityAndFormatUtils.formatRupiah(item.subtotal),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    val notes = item.portionNotes
                                    notes.forEachIndexed { idx, pNote ->
                                        if (pNote.isNotBlank()) {
                                            val label = if (item.quantity > 1) "Porsi ${idx + 1}: $pNote" else "Catatan: $pNote"
                                            Text(
                                                text = "  - $label",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFD97706)
                                            )
                                        }
                                    }
                                }
                            }

                            if (isCancelled && tx.cancelReason.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Catatan Pembatalan: ${tx.cancelReason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Akhir",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = SecurityAndFormatUtils.formatRupiah(tx.totalAmount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isUnpaid) {
                                    Button(
                                        onClick = { onPayUnpaidBilling(tw) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Bayar Billing", style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = { onOpenKitchenModal(tw) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.RestaurantMenu, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tiket Dapur", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onOpenReceiptModal(tw) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Detail & Cetak", style = MaterialTheme.typography.labelMedium)
                                    }

                                    OutlinedButton(
                                        onClick = { onShareReceiptText(tw) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Bagikan", style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                if (!isCancelled && canVoid) {
                                    OutlinedButton(
                                        onClick = { transactionToCancel = tw },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .testTag("btn_void_tx_${tx.id}"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Batal", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CancelTransactionConfirmDialog(
    txWithItems: TransactionWithItems,
    onDismiss: () -> Unit,
    onConfirmCancel: (String) -> Unit
) {
    var cancelReason by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Batalkan ${txWithItems.transaction.billingDisplay}?",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${txWithItems.transaction.billingDisplay} senilai ${SecurityAndFormatUtils.formatRupiah(txWithItems.transaction.totalAmount)} akan ditandai sebagai DIBATALKAN.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = cancelReason,
                    onValueChange = {
                        cancelReason = it
                        errorMsg = null
                    },
                    label = { Text("Catatan / Alasan Pembatalan *") },
                    placeholder = { Text("Contoh: Salah input pesanan / Pembatalan pelanggan") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_cancel_reason")
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (cancelReason.trim().isEmpty()) {
                        errorMsg = "Harap masukkan alasan pembatalan!"
                        return@Button
                    }
                    onConfirmCancel(cancelReason.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("btn_confirm_cancel_tx")
            ) {
                Text("Ya, Batalkan Billing")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}
