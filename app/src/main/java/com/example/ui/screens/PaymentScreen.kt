package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.CashierUserEntity
import com.example.data.PaymentMethodEntity
import com.example.data.StoreSettingsEntity
import com.example.data.TransactionWithItems
import com.example.util.SecurityAndFormatUtils

@Composable
fun PaymentScreen(
    unpaidBillings: List<TransactionWithItems>,
    selectedBilling: TransactionWithItems?,
    paymentMethods: List<PaymentMethodEntity>,
    settings: StoreSettingsEntity,
    activeCashier: CashierUserEntity?,
    onSelectBilling: (TransactionWithItems?) -> Unit,
    onEditBillingItems: (TransactionWithItems) -> Unit,
    onPrintKitchenTicket: (TransactionWithItems) -> Unit,
    onCancelBilling: (TransactionWithItems, String) -> Unit,
    onAddPaymentMethod: (String, Boolean) -> Unit,
    onUpdatePaymentMethod: (PaymentMethodEntity, String, Boolean) -> Unit,
    onDeletePaymentMethod: (PaymentMethodEntity) -> Unit,
    onCompletePayment: (
        txWithItems: TransactionWithItems,
        paymentMethod: String,
        paymentMethodId: Long?,
        amountPaidInput: Double,
        discountType: String,
        discountInput: Double,
        enableTax: Boolean,
        taxPercent: Double,
        serviceFee: Double,
        notes: String
    ) -> Unit,
    onOpenPosScreen: () -> Unit
) {
    var billingToCancel by remember { mutableStateOf<TransactionWithItems?>(null) }
    var cancelReason by remember { mutableStateOf("") }
    var showManagePaymentMethodsDialog by remember { mutableStateOf(false) }

    if (showManagePaymentMethodsDialog) {
        ManagePaymentMethodsDialog(
            paymentMethods = paymentMethods,
            onDismiss = { showManagePaymentMethodsDialog = false },
            onAddMethod = onAddPaymentMethod,
            onUpdateMethod = onUpdatePaymentMethod,
            onDeleteMethod = onDeletePaymentMethod
        )
    }

    if (billingToCancel != null) {
        AlertDialog(
            onDismissRequest = { billingToCancel = null },
            title = { Text("Batalkan ${billingToCancel?.transaction?.billingDisplay}?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Billing yang dibatalkan tidak akan masuk ke daftar pembayaran aktif.")
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Alasan Pembatalan") },
                        placeholder = { Text("Contoh: Pelanggan batal pesan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = billingToCancel
                        if (target != null) {
                            onCancelBilling(target, cancelReason.ifBlank { "Dibatalkan sebelum bayar" })
                        }
                        billingToCancel = null
                        cancelReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ya, Batalkan Billing")
                }
            },
            dismissButton = {
                TextButton(onClick = { billingToCancel = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    if (selectedBilling != null) {
        BillingCheckoutDetailView(
            txWithItems = selectedBilling,
            paymentMethods = paymentMethods,
            settings = settings,
            activeCashier = activeCashier,
            onBackToList = { onSelectBilling(null) },
            onEditItems = { onEditBillingItems(selectedBilling) },
            onPrintKitchen = { onPrintKitchenTicket(selectedBilling) },
            onOpenManagePaymentMethods = { showManagePaymentMethodsDialog = true },
            onPayAndFinish = { method, methodId, paidAmount, discType, discIn, taxOn, taxPct, srvFee, note ->
                onCompletePayment(
                    selectedBilling,
                    method,
                    methodId,
                    paidAmount,
                    discType,
                    discIn,
                    taxOn,
                    taxPct,
                    srvFee,
                    note
                )
            }
        )
    } else {
        UnpaidBillingListView(
            unpaidBillings = unpaidBillings,
            onSelectBilling = { onSelectBilling(it) },
            onEditBillingItems = onEditBillingItems,
            onPrintKitchenTicket = onPrintKitchenTicket,
            onCancelBilling = {
                billingToCancel = it
                cancelReason = ""
            },
            onOpenManagePaymentMethods = { showManagePaymentMethodsDialog = true },
            onOpenPosScreen = onOpenPosScreen
        )
    }
}

@Composable
private fun UnpaidBillingListView(
    unpaidBillings: List<TransactionWithItems>,
    onSelectBilling: (TransactionWithItems) -> Unit,
    onEditBillingItems: (TransactionWithItems) -> Unit,
    onPrintKitchenTicket: (TransactionWithItems) -> Unit,
    onCancelBilling: (TransactionWithItems) -> Unit,
    onOpenManagePaymentMethods: () -> Unit,
    onOpenPosScreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daftar Billing Belum Bayar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${unpaidBillings.size} Billing aktif menunggu pembayaran",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenManagePaymentMethods,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_manage_payment_methods_top")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Metode Bayar", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = onOpenPosScreen,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_new_order_from_payment")
                ) {
                    Icon(Icons.Default.RestaurantMenu, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pesan Baru")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (unpaidBillings.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Semua Billing Sudah Lunas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Belum ada pesanan dapur yang menunggu pembayaran. Silakan buat pesanan baru di menu Pesan Menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onOpenPosScreen) {
                        Icon(Icons.Default.RestaurantMenu, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buka Halaman Pesan Menu")
                    }
                }
            }
        } else {
            LazyColumn(
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
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = tx.billingDisplay,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = SecurityAndFormatUtils.formatDateTime(tx.timestamp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (tx.customerName != "Pelanggan Umum") {
                                            Text(
                                                text = "Pelanggan: ${tx.customerName}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = "Status: BELUM BAYAR",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFB45309),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            // List of ordered items + per-portion notes
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
                                val notesSummary = SecurityAndFormatUtils.formatPortionNotesSummary(item.portionNotes)
                                notesSummary.forEach { noteLine ->
                                    Text(
                                        text = "  • $noteLine",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Total Tagihan",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Total ${SecurityAndFormatUtils.formatRupiah(tx.totalAmount)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = { onPrintKitchenTicket(tw) },
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(
                                            Icons.Default.Print,
                                            contentDescription = "Lihat/Cetak Struk Dapur",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { onEditBillingItems(tw) },
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Tambah/Ubah Pesanan",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    IconButton(
                                        onClick = { onCancelBilling(tw) },
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                    ) {
                                        Icon(
                                            Icons.Default.Cancel,
                                            contentDescription = "Batalkan Billing",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    Button(
                                        onClick = { onSelectBilling(tw) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("btn_select_pay_billing_${tx.billingNumber}")
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("PROSES BAYAR", fontWeight = FontWeight.ExtraBold)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillingCheckoutDetailView(
    txWithItems: TransactionWithItems,
    paymentMethods: List<PaymentMethodEntity>,
    settings: StoreSettingsEntity,
    activeCashier: CashierUserEntity?,
    onBackToList: () -> Unit,
    onEditItems: () -> Unit,
    onPrintKitchen: () -> Unit,
    onOpenManagePaymentMethods: () -> Unit,
    onPayAndFinish: (
        paymentMethod: String,
        paymentMethodId: Long?,
        amountPaidInput: Double,
        discountType: String,
        discountInput: Double,
        enableTax: Boolean,
        taxPercent: Double,
        serviceFee: Double,
        notes: String
    ) -> Unit
) {
    val tx = txWithItems.transaction
    val subtotal = remember(txWithItems.items) { txWithItems.items.sumOf { it.subtotal } }

    var showDiscountSection by remember { mutableStateOf(tx.discountAmount > 0 || tx.taxAmount > 0 || tx.serviceFee > 0) }
    var discountType by remember { mutableStateOf(tx.discountType.ifBlank { "NOMINAL" }) }
    var discountInputStr by remember {
        mutableStateOf(if (tx.discountInput > 0) tx.discountInput.toLong().toString() else "")
    }
    var enableTax by remember { mutableStateOf(tx.taxAmount > 0 || settings.enableTaxByDefault) }
    var taxPercentStr by remember {
        mutableStateOf(
            if (tx.taxPercentage > 0) tx.taxPercentage.toString()
            else if (settings.defaultTaxPercentage > 0) settings.defaultTaxPercentage.toString()
            else "10"
        )
    }
    var serviceFeeStr by remember {
        mutableStateOf(
            if (tx.serviceFee > 0) tx.serviceFee.toLong().toString()
            else if (settings.defaultServiceFee > 0) settings.defaultServiceFee.toLong().toString()
            else ""
        )
    }

    val effectivePaymentMethods = remember(paymentMethods) {
        paymentMethods.ifEmpty {
            listOf(
                PaymentMethodEntity(id = 1, name = "TUNAI", isCashType = true, sortOrder = 1),
                PaymentMethodEntity(id = 2, name = "TRANSFER", isCashType = false, sortOrder = 2),
                PaymentMethodEntity(id = 3, name = "QRIS", isCashType = false, sortOrder = 3),
                PaymentMethodEntity(id = 4, name = "GOJEK", isCashType = false, sortOrder = 4),
                PaymentMethodEntity(id = 5, name = "SHOPEE", isCashType = false, sortOrder = 5),
                PaymentMethodEntity(id = 6, name = "GRAB", isCashType = false, sortOrder = 6)
            )
        }
    }

    var selectedMethodId by remember {
        mutableStateOf(effectivePaymentMethods.firstOrNull()?.id)
    }

    LaunchedEffect(effectivePaymentMethods) {
        if (effectivePaymentMethods.none { it.id == selectedMethodId }) {
            selectedMethodId = effectivePaymentMethods.firstOrNull()?.id
        }
    }

    val selectedMethodEntity = remember(effectivePaymentMethods, selectedMethodId) {
        effectivePaymentMethods.find { it.id == selectedMethodId } ?: effectivePaymentMethods.first()
    }

    var cashReceivedInput by remember { mutableStateOf("") }
    var paymentNotes by remember { mutableStateOf(tx.notes) }

    val discountInputVal = SecurityAndFormatUtils.parseDoubleInput(discountInputStr)
    val discountNominal = if (discountType == "PERCENT") {
        subtotal * (discountInputVal.coerceIn(0.0, 100.0) / 100.0)
    } else {
        discountInputVal.coerceIn(0.0, subtotal)
    }
    val afterDiscount = (subtotal - discountNominal).coerceAtLeast(0.0)
    val taxPctVal = if (enableTax) SecurityAndFormatUtils.parseDoubleInput(taxPercentStr).coerceAtLeast(0.0) else 0.0
    val taxNominal = afterDiscount * (taxPctVal / 100.0)
    val serviceFeeVal = SecurityAndFormatUtils.parseDoubleInput(serviceFeeStr).coerceAtLeast(0.0)
    val rawTotal = afterDiscount + taxNominal + serviceFeeVal
    val finalTotal = if (settings.enableRounding && settings.roundingMultiple > 1) {
        SecurityAndFormatUtils.roundToNearestMultiple(rawTotal, settings.roundingMultiple)
    } else {
        rawTotal
    }

    val isCashPayment = selectedMethodEntity.requiresCashInput
    val cashReceivedVal = if (isCashPayment) {
        if (cashReceivedInput.isBlank()) 0.0 else SecurityAndFormatUtils.parseDoubleInput(cashReceivedInput)
    } else {
        finalTotal
    }
    val changeAmount = (cashReceivedVal - finalTotal).coerceAtLeast(0.0)
    val isCashEnough = !isCashPayment || cashReceivedVal >= finalTotal

    val canGiveDiscount = activeCashier?.canGiveDiscount ?: true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header of Payment Checkout
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
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
                    IconButton(onClick = onBackToList) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke Daftar Billing")
                    }
                    Column {
                        Text(
                            text = "Pembayaran ${tx.billingDisplay}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Invoice: ${tx.invoiceNumber} • BELUM BAYAR",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onPrintKitchen,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Struk Dapur", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onEditItems,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ubah Menu", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Ordered Items Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rincian Pesanan (${tx.billingDisplay})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${txWithItems.items.sumOf { it.quantity }} Porsi",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    txWithItems.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
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
                                val portionSummary = SecurityAndFormatUtils.formatPortionNotesSummary(item.portionNotes)
                                portionSummary.forEach { noteLine ->
                                    Text(
                                        text = "• $noteLine",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD97706),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Text(
                                text = SecurityAndFormatUtils.formatRupiah(item.subtotal),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Subtotal", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            SecurityAndFormatUtils.formatRupiah(subtotal),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (discountNominal > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Diskon", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Text(
                                "-${SecurityAndFormatUtils.formatRupiah(discountNominal)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (taxNominal > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pajak (${SecurityAndFormatUtils.formatNumber(taxPctVal)}%)", style = MaterialTheme.typography.bodyMedium)
                            Text(SecurityAndFormatUtils.formatRupiah(taxNominal), style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (serviceFeeVal > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Biaya Tambahan", style = MaterialTheme.typography.bodyMedium)
                            Text(SecurityAndFormatUtils.formatRupiah(serviceFeeVal), style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL AKHIR",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = SecurityAndFormatUtils.formatRupiah(finalTotal),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (canGiveDiscount) {
                        TextButton(
                            onClick = { showDiscountSection = !showDiscountSection },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                if (showDiscountSection) "Sembunyikan Opsi Diskon / Pajak"
                                else "+ Tambah Diskon / Pajak (Opsional)"
                            )
                        }
                    }

                    if (showDiscountSection && canGiveDiscount) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = discountType == "NOMINAL",
                                    onClick = { discountType = "NOMINAL" },
                                    label = { Text("Diskon Rp") }
                                )
                                FilterChip(
                                    selected = discountType == "PERCENT",
                                    onClick = { discountType = "PERCENT" },
                                    label = { Text("Diskon %") }
                                )
                                FilterChip(
                                    selected = enableTax,
                                    onClick = { enableTax = !enableTax },
                                    label = { Text("Pajak") }
                                )
                            }
                            OutlinedTextField(
                                value = discountInputStr,
                                onValueChange = { discountInputStr = it },
                                label = {
                                    Text(if (discountType == "PERCENT") "Persentase Diskon (%)" else "Nominal Diskon (Rp)")
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 2. Payment Method Selection (Dynamic from Database + Kelola Metode Button)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pilih Metode Pembayaran",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Aktif: ${selectedMethodEntity.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenManagePaymentMethods,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_manage_payment_methods")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kelola Metode", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        effectivePaymentMethods.forEach { method ->
                            val isSelected = selectedMethodEntity.id == method.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedMethodId = method.id },
                                label = {
                                    Text(
                                        text = method.name,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                },
                                modifier = Modifier.testTag("chip_payment_method_${method.name}")
                            )
                        }
                    }

                    if (isCashPayment) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = cashReceivedInput,
                            onValueChange = { cashReceivedInput = it },
                            label = { Text("Masukkan Uang Pelanggan (Rp)") },
                            placeholder = { Text(finalTotal.toLong().toString()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_cash_received")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Cash Buttons
                        val quickNominals = remember(finalTotal) {
                            listOf(
                                "UANG PAS" to finalTotal,
                                "Rp 20.000" to 20000.0,
                                "Rp 50.000" to 50000.0,
                                "Rp 100.000" to 100000.0
                            ).distinctBy { it.second }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickNominals.forEach { (label, value) ->
                                if (value >= finalTotal || label == "UANG PAS") {
                                    OutlinedButton(
                                        onClick = { cashReceivedInput = value.toLong().toString() },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("btn_quick_cash_${label.replace(" ", "_")}")
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Automatic Change Display Box
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = if (isCashEnough) Color(0xFF059669) else MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            color = if (isCashEnough) Color(0xFFECFDF5) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isCashEnough) "KEMBALIAN OTOMATIS" else "UANG PELANGGAN KURANG",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isCashEnough) Color(0xFF065F46) else MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Dibayar: ${SecurityAndFormatUtils.formatRupiah(cashReceivedVal)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF334155)
                                    )
                                }
                                Text(
                                    text = if (isCashEnough) {
                                        SecurityAndFormatUtils.formatRupiah(changeAmount)
                                    } else {
                                        "-${SecurityAndFormatUtils.formatRupiah(finalTotal - cashReceivedVal)}"
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isCashEnough) Color(0xFF047857) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag("text_change_amount")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Bar: [ BAYAR & SELESAIKAN ]
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        val effectivePaid = if (isCashPayment) cashReceivedVal else finalTotal
                        onPayAndFinish(
                            selectedMethodEntity.name,
                            selectedMethodEntity.id,
                            effectivePaid,
                            discountType,
                            discountInputVal,
                            enableTax,
                            taxPctVal,
                            serviceFeeVal,
                            paymentNotes
                        )
                    },
                    enabled = isCashEnough,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_pay_and_finish")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "BAYAR & SELESAIKAN (${SecurityAndFormatUtils.formatRupiah(finalTotal)})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
fun ManagePaymentMethodsDialog(
    paymentMethods: List<PaymentMethodEntity>,
    onDismiss: () -> Unit,
    onAddMethod: (String, Boolean) -> Unit,
    onUpdateMethod: (PaymentMethodEntity, String, Boolean) -> Unit,
    onDeleteMethod: (PaymentMethodEntity) -> Unit
) {
    var newMethodName by remember { mutableStateOf("") }
    var newMethodIsCash by remember { mutableStateOf(false) }
    var editingMethod by remember { mutableStateOf<PaymentMethodEntity?>(null) }
    var editingName by remember { mutableStateOf("") }
    var editingIsCash by remember { mutableStateOf(false) }

    if (editingMethod != null) {
        AlertDialog(
            onDismissRequest = { editingMethod = null },
            title = { Text("Edit Metode Pembayaran", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Ubah nama metode (contoh: TUNAI → CASH, QRIS → QRIS BCA, TRANSFER → BANK BCA). Transaksi lama tetap aman.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        label = { Text("Nama Metode Pembayaran") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_payment_method_name")
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingIsCash = !editingIsCash }
                    ) {
                        Checkbox(
                            checked = editingIsCash,
                            onCheckedChange = { editingIsCash = it }
                        )
                        Text(
                            text = "Metode Tunai (Hitung uang diterima & kembalian)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = editingMethod
                        if (target != null && editingName.isNotBlank()) {
                            onUpdateMethod(target, editingName, editingIsCash)
                            editingMethod = null
                        }
                    },
                    modifier = Modifier.testTag("btn_save_edit_payment_method")
                ) {
                    Text("Simpan Perubahan")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMethod = null }) {
                    Text("Batal")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Kelola Metode Pembayaran", fontWeight = FontWeight.ExtraBold)
                Text(
                    "Tambah, ubah nama, atau hapus metode pembayaran",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Add new method form
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Tambah Metode Pembayaran Baru",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newMethodName,
                                onValueChange = { newMethodName = it },
                                placeholder = { Text("Contoh: DEBIT, EDC, DANA, OVO...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_new_payment_method")
                            )
                            Button(
                                onClick = {
                                    if (newMethodName.isNotBlank()) {
                                        onAddMethod(newMethodName, newMethodIsCash)
                                        newMethodName = ""
                                        newMethodIsCash = false
                                    }
                                },
                                enabled = newMethodName.isNotBlank(),
                                modifier = Modifier.testTag("btn_add_payment_method")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tambah")
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { newMethodIsCash = !newMethodIsCash }
                        ) {
                            Checkbox(
                                checked = newMethodIsCash,
                                onCheckedChange = { newMethodIsCash = it }
                            )
                            Text(
                                text = "Hitung kembalian otomatis (Tunai)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Daftar Metode Tersimpan (${paymentMethods.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                paymentMethods.forEach { method ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = method.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (method.requiresCashInput) {
                                    Text(
                                        text = "Input uang pelanggan & hitung kembalian",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        editingMethod = method
                                        editingName = method.name
                                        editingIsCash = method.isCashType
                                    },
                                    modifier = Modifier.testTag("btn_edit_method_${method.name}")
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit ${method.name}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteMethod(method) },
                                    enabled = paymentMethods.size > 1,
                                    modifier = Modifier.testTag("btn_delete_method_${method.name}")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus ${method.name}",
                                        tint = if (paymentMethods.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Selesai")
            }
        }
    )
}
