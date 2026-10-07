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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RestaurantMenu
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.StoreSettingsEntity
import com.example.data.TransactionWithItems
import com.example.util.SecurityAndFormatUtils
import com.example.viewmodel.PaymentCalculation

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentScreen(
    unpaidBillings: List<TransactionWithItems>,
    selectedBilling: TransactionWithItems?,
    settings: StoreSettingsEntity,
    canGiveDiscount: Boolean,
    discountInput: Double,
    paymentMethod: String,
    amountPaidInput: String,
    paymentCalculation: PaymentCalculation,
    onSelectBilling: (TransactionWithItems?) -> Unit,
    onAddMoreItemsToBilling: (TransactionWithItems) -> Unit,
    onViewKitchenTicket: (TransactionWithItems) -> Unit,
    onSetDiscount: (Double) -> Unit,
    onSetPaymentMethod: (String) -> Unit,
    onSetAmountPaidInput: (String) -> Unit,
    onCompletePayment: () -> Unit,
    onGoToNewOrder: () -> Unit
) {
    if (selectedBilling == null) {
        // LIST OF UNPAID BILLINGS
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Daftar Billing Belum Dibayar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${unpaidBillings.size} Billing menunggu pembayaran",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onGoToNewOrder,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pesan Baru")
                    }
                }
            }

            if (unpaidBillings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Semua Billing Sudah Lunas!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Belum ada antrean billing yang menunggu pembayaran.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onGoToNewOrder) {
                            Icon(Icons.Default.RestaurantMenu, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buat Pesanan Baru", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(unpaidBillings, key = { it.transaction.id }) { tw ->
                        val tx = tw.transaction
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBilling(tw) }
                                .testTag("unpaid_billing_card_${tx.billingNumber}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = tx.billingDisplay,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = SecurityAndFormatUtils.formatDateTime(tx.timestamp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFFEF3C7)
                                    ) {
                                        Text(
                                            text = "Status: BELUM BAYAR",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFB45309),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(10.dp))

                                // List of items in this unpaid billing
                                tw.items.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${item.productName} x${item.quantity}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = SecurityAndFormatUtils.formatRupiah(item.subtotal),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    val pNotes = item.portionNotes
                                    pNotes.forEachIndexed { idx, note ->
                                        if (note.isNotBlank()) {
                                            Text(
                                                text = if (pNotes.size == 1) "  Catatan: $note" else "  - Porsi ${idx + 1}: $note",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFD97706)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Total ${SecurityAndFormatUtils.formatRupiah(tx.totalAmount)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onViewKitchenTicket(tw) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Dapur", style = MaterialTheme.typography.labelMedium)
                                        }

                                        OutlinedButton(
                                            onClick = { onAddMoreItemsToBilling(tw) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Tambah Menu", style = MaterialTheme.typography.labelMedium)
                                        }

                                        Button(
                                            onClick = { onSelectBilling(tw) },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("btn_select_billing_${tx.billingNumber}")
                                        ) {
                                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Bayar", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // PAYMENT DETAIL SCREEN FOR SELECTED BILLING
        val tx = selectedBilling.transaction
        val paymentMethods = listOf("Tunai", "QRIS", "Transfer", "Debit", "Kredit", "Lainnya")
        var discountText by remember(discountInput) {
            mutableStateOf(if (discountInput == 0.0) "" else discountInput.toLong().toString())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header with Billing Number and Back to Unpaid List
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onSelectBilling(null) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali ke Daftar Billing"
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = tx.billingDisplay.uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Proses Pembayaran Pesanan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onAddMoreItemsToBilling(selectedBilling) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tambah Menu")
                    }
                }
            }

            // Scrollable Billing Details & Payment Input
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Ordered Items Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Rincian Pesanan (${tx.billingDisplay})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        HorizontalDivider()

                        selectedBilling.items.forEach { item ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.productName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${item.quantity} x ${SecurityAndFormatUtils.formatRupiah(item.sellPrice)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = SecurityAndFormatUtils.formatRupiah(item.subtotal),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                val pNotes = item.portionNotes
                                pNotes.forEachIndexed { idx, note ->
                                    if (note.isNotBlank()) {
                                        Text(
                                            text = if (pNotes.size == 1) "Catatan: $note" else "- Porsi ${idx + 1}: $note",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFD97706)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal", fontWeight = FontWeight.SemiBold)
                            Text(
                                SecurityAndFormatUtils.formatRupiah(paymentCalculation.subtotal),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (settings.enableDiscount && canGiveDiscount) {
                            OutlinedTextField(
                                value = discountText,
                                onValueChange = {
                                    discountText = it.filter { ch -> ch.isDigit() }
                                    onSetDiscount(discountText.toDoubleOrNull() ?: 0.0)
                                },
                                label = { Text("Diskon (Rp) - Opsional") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (paymentCalculation.discountAmount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Potongan Diskon", color = Color(0xFF059669))
                                Text(
                                    "-${SecurityAndFormatUtils.formatRupiah(paymentCalculation.discountAmount)}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                            }
                        }

                        if (settings.enableTax && paymentCalculation.taxAmount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pajak (${SecurityAndFormatUtils.formatNumber(paymentCalculation.taxPercent)}%)")
                                Text(
                                    SecurityAndFormatUtils.formatRupiah(paymentCalculation.taxAmount),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Prominent Total Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = SecurityAndFormatUtils.formatRupiah(paymentCalculation.finalTotal),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Payment Method Selection
                Text(
                    text = "Metode Pembayaran",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    paymentMethods.forEach { method ->
                        FilterChip(
                            selected = paymentMethod.equals(method, ignoreCase = true),
                            onClick = {
                                onSetPaymentMethod(method)
                                if (!method.equals("Tunai", ignoreCase = true)) {
                                    onSetAmountPaidInput(paymentCalculation.finalTotal.toLong().toString())
                                }
                            },
                            label = { Text(method, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                // Tunai Input & Automatic Kembalian / Kekurangan Display
                if (paymentMethod.equals("Tunai", ignoreCase = true)) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = amountPaidInput,
                                onValueChange = { raw ->
                                    onSetAmountPaidInput(raw.filter { it.isDigit() })
                                },
                                label = { Text("Tunai (Uang Pelanggan Rp)") },
                                placeholder = { Text("Masukkan nominal uang tunai...") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_cash_received")
                            )

                            // Quick Cash Nominal Buttons
                            val exactTotal = paymentCalculation.finalTotal.toLong()
                            val presets = listOf(
                                "Uang Pas" to exactTotal,
                                "20.000" to 20000L,
                                "50.000" to 50000L,
                                "100.000" to 100000L,
                                "200.000" to 200000L
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(presets) { (label, amount) ->
                                    OutlinedButton(
                                        onClick = { onSetAmountPaidInput(amount.toString()) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(label, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    SecurityAndFormatUtils.formatRupiah(paymentCalculation.finalTotal),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tunai", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    SecurityAndFormatUtils.formatRupiah(paymentCalculation.amountPaid),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (paymentCalculation.isSufficient) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFD1FAE5),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Kembalian:",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF065F46)
                                        )
                                        Text(
                                            text = SecurityAndFormatUtils.formatRupiah(paymentCalculation.changeAmount),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF065F46),
                                            modifier = Modifier.testTag("text_change_amount")
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Kurang:",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "Kurang ${SecurityAndFormatUtils.formatRupiah(paymentCalculation.shortageAmount)}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.testTag("text_shortage_amount")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Sticky [ BAYAR & SELESAIKAN ] Button
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onCompletePayment,
                    enabled = paymentCalculation.isSufficient,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp)
                        .testTag("btn_pay_and_finish")
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BAYAR & SELESAIKAN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
