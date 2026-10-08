package com.example.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.CashierUserEntity
import com.example.data.PaymentMethodEntity
import com.example.data.StoreSettingsEntity
import com.example.service.BluetoothPrinterService
import com.example.service.DiscoveredBluetoothDevice
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.components.openAppNotificationAndPermissionSettings
import com.example.util.SecurityAndFormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: StoreSettingsEntity,
    paymentMethods: List<PaymentMethodEntity>,
    cashierUsers: List<CashierUserEntity>,
    onSaveSettings: (StoreSettingsEntity) -> Unit,
    onAddPaymentMethod: (String, Boolean) -> Unit,
    onUpdatePaymentMethod: (PaymentMethodEntity, String, Boolean) -> Unit,
    onDeletePaymentMethod: (PaymentMethodEntity) -> Unit,
    onSaveCashierUser: (
        id: Long,
        name: String,
        role: String,
        rawPin: String,
        existingPinHash: String,
        canEditPrice: Boolean,
        canGiveDiscount: Boolean,
        canCancelTransaction: Boolean,
        canViewReports: Boolean
    ) -> Unit,
    onDeleteCashierUser: (CashierUserEntity) -> Unit,
    onBackupDatabase: (Uri) -> Unit,
    onRestoreDatabase: (Uri) -> Unit,
    onResetTransactionsOnly: () -> Unit,
    onResetFactory: () -> Unit,
    onEmitMessage: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Profil Resto & Struk",
        "Metode Bayar & Billing",
        "Printer Bluetooth",
        "Kasir & PIN",
        "Backup & Reset"
    )

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
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("settings_tab_$index")
                )
            }
        }

        when (selectedTab) {
            0 -> StoreProfileAndReceiptSettingsTab(settings = settings, onSave = onSaveSettings)
            1 -> TransactionAndPaymentMethodsSettingsTab(
                settings = settings,
                paymentMethods = paymentMethods,
                onSaveSettings = onSaveSettings,
                onAddPaymentMethod = onAddPaymentMethod,
                onUpdatePaymentMethod = onUpdatePaymentMethod,
                onDeletePaymentMethod = onDeletePaymentMethod
            )
            2 -> BluetoothPrinterSettingsTab(
                settings = settings,
                onSave = onSaveSettings,
                onEmitMessage = onEmitMessage
            )
            3 -> CashierUsersAndPinSettingsTab(
                settings = settings,
                users = cashierUsers,
                onSaveSettings = onSaveSettings,
                onSaveUser = onSaveCashierUser,
                onDeleteUser = onDeleteCashierUser
            )
            4 -> BackupAndResetSettingsTab(
                onBackup = onBackupDatabase,
                onRestore = onRestoreDatabase,
                onResetTransactionsOnly = onResetTransactionsOnly,
                onResetFactory = onResetFactory
            )
        }
    }
}

@Composable
private fun StoreProfileAndReceiptSettingsTab(
    settings: StoreSettingsEntity,
    onSave: (StoreSettingsEntity) -> Unit
) {
    val context = LocalContext.current
    var storeName by remember(settings) { mutableStateOf(settings.storeName) }
    var storeAddress by remember(settings) { mutableStateOf(settings.storeAddress) }
    var storePhone by remember(settings) { mutableStateOf(settings.storePhone) }
    var storeEmail by remember(settings) { mutableStateOf(settings.storeEmail) }
    var storeLogoUri by remember(settings) { mutableStateOf(settings.storeLogoUri) }
    var receiptHeader by remember(settings) { mutableStateOf(settings.receiptHeader) }
    var receiptFooter by remember(settings) { mutableStateOf(settings.receiptFooter) }
    var invoicePrefix by remember(settings) { mutableStateOf(settings.invoicePrefix) }
    var isDarkMode by remember(settings) { mutableStateOf(settings.isDarkMode) }
    var productViewMode by remember(settings) { mutableStateOf(settings.productViewMode) }
    var gridColumns by remember(settings) { mutableIntStateOf(settings.gridColumns) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            storeLogoUri = uri.toString()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Identitas Restoran / Warung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (storeLogoUri.isNotBlank()) {
                            AsyncImage(
                                model = Uri.parse(storeLogoUri),
                                contentDescription = "Logo Resto",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Store,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column {
                            OutlinedButton(
                                onClick = {
                                    logoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pilih Logo Resto")
                            }
                            if (storeLogoUri.isNotBlank()) {
                                TextButton(onClick = { storeLogoUri = "" }) {
                                    Text("Hapus Logo", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text("Nama Restoran / Warung") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_store_name")
                    )
                    OutlinedTextField(
                        value = storeAddress,
                        onValueChange = { storeAddress = it },
                        label = { Text("Alamat Lengkap") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = storePhone,
                        onValueChange = { storePhone = it },
                        label = { Text("Nomor Telepon / WhatsApp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = storeEmail,
                        onValueChange = { storeEmail = it },
                        label = { Text("Email / Instagram (Opsional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Teks Struk & Tampilan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = invoicePrefix,
                        onValueChange = { invoicePrefix = it },
                        label = { Text("Prefix Kode Billing (Contoh: BIL)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = receiptHeader,
                        onValueChange = { receiptHeader = it },
                        label = { Text("Header Struk (Ucapan Atas)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = receiptFooter,
                        onValueChange = { receiptFooter = it },
                        label = { Text("Footer Struk (Ucapan Bawah)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tema Gelap (Dark Mode)")
                        Switch(checked = isDarkMode, onCheckedChange = { isDarkMode = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tampilan Menu Kasir")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = productViewMode == "GRID",
                                onClick = { productViewMode = "GRID" },
                                label = { Text("Grid") }
                            )
                            FilterChip(
                                selected = productViewMode == "LIST",
                                onClick = { productViewMode = "LIST" },
                                label = { Text("List") }
                            )
                        }
                    }

                    if (productViewMode == "GRID") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Jumlah Kolom Grid")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = gridColumns == 2,
                                    onClick = { gridColumns = 2 },
                                    label = { Text("2 Kolom") }
                                )
                                FilterChip(
                                    selected = gridColumns == 3,
                                    onClick = { gridColumns = 3 },
                                    label = { Text("3 Kolom") }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onSave(
                                settings.copy(
                                    storeName = storeName.trim().ifBlank { "KasirKu" },
                                    storeAddress = storeAddress.trim(),
                                    storePhone = storePhone.trim(),
                                    storeEmail = storeEmail.trim(),
                                    storeLogoUri = storeLogoUri,
                                    receiptHeader = receiptHeader.trim(),
                                    receiptFooter = receiptFooter.trim(),
                                    invoicePrefix = invoicePrefix.trim().ifBlank { "BIL" },
                                    isDarkMode = isDarkMode,
                                    productViewMode = productViewMode,
                                    gridColumns = gridColumns
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_store_profile")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan Profil & Struk", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionAndPaymentMethodsSettingsTab(
    settings: StoreSettingsEntity,
    paymentMethods: List<PaymentMethodEntity>,
    onSaveSettings: (StoreSettingsEntity) -> Unit,
    onAddPaymentMethod: (String, Boolean) -> Unit,
    onUpdatePaymentMethod: (PaymentMethodEntity, String, Boolean) -> Unit,
    onDeletePaymentMethod: (PaymentMethodEntity) -> Unit
) {
    var defaultTax by remember(settings) { mutableStateOf(settings.defaultTaxPercentage.toString()) }
    var defaultServiceFee by remember(settings) { mutableStateOf(settings.defaultServiceFee.toLong().toString()) }
    var enableTaxByDefault by remember(settings) { mutableStateOf(settings.enableTaxByDefault) }
    var enableRounding by remember(settings) { mutableStateOf(settings.enableRounding) }
    var roundingMultiple by remember(settings) { mutableIntStateOf(settings.roundingMultiple) }

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
                        "Contoh perubahan: TUNAI → CASH, QRIS → QRIS BCA, TRANSFER → BANK BCA. Transaksi lama di riwayat tetap aman.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        label = { Text("Nama Metode Pembayaran") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { editingIsCash = !editingIsCash }
                    ) {
                        Checkbox(checked = editingIsCash, onCheckedChange = { editingIsCash = it })
                        Text(
                            "Metode Tunai (Hitung uang diterima & kembalian)",
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
                            onUpdatePaymentMethod(target, editingName, editingIsCash)
                            editingMethod = null
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMethod = null }) {
                    Text("Batal")
                }
            }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Flexible Payment Methods Management (No ON/OFF Switches!)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Payments,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Kelola Metode Pembayaran",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Tambah manual, edit nama, atau hapus metode pembayaran",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Add new method box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
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
                                    placeholder = { Text("Misal: DEBIT, EDC, DANA, OVO...") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_input_new_payment_method")
                                )
                                Button(
                                    onClick = {
                                        if (newMethodName.isNotBlank()) {
                                            onAddPaymentMethod(newMethodName, newMethodIsCash)
                                            newMethodName = ""
                                            newMethodIsCash = false
                                        }
                                    },
                                    enabled = newMethodName.isNotBlank(),
                                    modifier = Modifier.testTag("settings_btn_add_payment_method")
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
                                Checkbox(checked = newMethodIsCash, onCheckedChange = { newMethodIsCash = it })
                                Text(
                                    text = "Hitung uang pelanggan & kembalian otomatis (Tunai)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Daftar Metode Pembayaran Tersimpan (${paymentMethods.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    paymentMethods.forEach { method ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
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
                                            text = "Metode Tunai (Input uang & kembalian)",
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
                                        modifier = Modifier.testTag("settings_edit_method_${method.name}")
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit ${method.name}",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeletePaymentMethod(method) },
                                        enabled = paymentMethods.size > 1,
                                        modifier = Modifier.testTag("settings_delete_method_${method.name}")
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
            }
        }

        // 2. Tax, Service Fee & Rounding Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Pajak, Biaya Layanan & Pembulatan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Aktifkan Pajak Otomatis Saat Pembayaran")
                        Switch(checked = enableTaxByDefault, onCheckedChange = { enableTaxByDefault = it })
                    }

                    OutlinedTextField(
                        value = defaultTax,
                        onValueChange = { defaultTax = it },
                        label = { Text("Persentase Pajak Default (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = defaultServiceFee,
                        onValueChange = { defaultServiceFee = it },
                        label = { Text("Biaya Layanan / Kemasan Default (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bulatkan Total Akhir Otomatis")
                        Switch(checked = enableRounding, onCheckedChange = { enableRounding = it })
                    }

                    if (enableRounding) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(100, 500, 1000).forEach { mult ->
                                FilterChip(
                                    selected = roundingMultiple == mult,
                                    onClick = { roundingMultiple = mult },
                                    label = { Text("Ke Rp $mult") }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onSaveSettings(
                                settings.copy(
                                    defaultTaxPercentage = SecurityAndFormatUtils.parseDoubleInput(defaultTax),
                                    defaultServiceFee = SecurityAndFormatUtils.parseDoubleInput(defaultServiceFee),
                                    enableTaxByDefault = enableTaxByDefault,
                                    enableRounding = enableRounding,
                                    roundingMultiple = roundingMultiple
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan Pengaturan Pajak & Pembulatan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothPrinterSettingsTab(
    settings: StoreSettingsEntity,
    onSave: (StoreSettingsEntity) -> Unit,
    onEmitMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var paperSizeMm by remember(settings) { mutableIntStateOf(settings.paperSizeMm) }
    var autoPrintKitchenTicket by remember(settings) { mutableStateOf(settings.autoPrintKitchenTicket) }
    var autoPrintReceipt by remember(settings) { mutableStateOf(settings.autoPrintReceipt) }
    var printCopies by remember(settings) { mutableIntStateOf(settings.printCopies) }
    var defaultPrinterName by remember(settings) { mutableStateOf(settings.defaultPrinterName) }
    var defaultPrinterAddress by remember(settings) { mutableStateOf(settings.defaultPrinterAddress) }

    var pairedDevices by remember { mutableStateOf<List<DiscoveredBluetoothDevice>>(emptyList()) }
    var showBtRationale by remember { mutableStateOf(false) }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        BluetoothPrinterService.getPairedDevices(context)
            .onSuccess { pairedDevices = it }
            .onFailure { onEmitMessage(it.message ?: "Gagal memuat printer") }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.all { it }
        if (granted) {
            if (!BluetoothPrinterService.isBluetoothEnabled(context)) {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                BluetoothPrinterService.getPairedDevices(context)
                    .onSuccess { pairedDevices = it }
                    .onFailure { onEmitMessage(it.message ?: "Gagal memuat printer") }
            }
        } else {
            showBtRationale = true
        }
    }

    if (showBtRationale) {
        PermissionRationaleDialog(
            title = "Izin Bluetooth Diperlukan",
            rationaleMessage = "KasirKu membutuhkan izin Bluetooth untuk memindai dan menghubungkan printer thermal kasir.",
            onDismiss = { showBtRationale = false },
            onOpenSettings = { openAppNotificationAndPermissionSettings(context) }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Konfigurasi Printer Thermal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ukuran Kertas Struk")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = paperSizeMm == 58,
                                onClick = { paperSizeMm = 58 },
                                label = { Text("58mm") }
                            )
                            FilterChip(
                                selected = paperSizeMm == 80,
                                onClick = { paperSizeMm = 80 },
                                label = { Text("80mm") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cetak Otomatis Tiket Dapur")
                            Text(
                                "Saat tombol KIRIM PESANAN ditekan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = autoPrintKitchenTicket, onCheckedChange = { autoPrintKitchenTicket = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cetak Otomatis Struk Pembayaran")
                            Text(
                                "Saat tombol BAYAR & SELESAIKAN ditekan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = autoPrintReceipt, onCheckedChange = { autoPrintReceipt = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Jumlah Rangkap Struk Bayar")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3).forEach { c ->
                                FilterChip(
                                    selected = printCopies == c,
                                    onClick = { printCopies = c },
                                    label = { Text("${c}x") }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = if (defaultPrinterAddress.isNotBlank()) {
                            "Printer Utama: $defaultPrinterName ($defaultPrinterAddress)"
                        } else {
                            "Belum ada printer utama dipilih"
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!BluetoothPrinterService.hasBluetoothPermissions(context)) {
                                    btPermissionLauncher.launch(BluetoothPrinterService.getRequiredBluetoothPermissions())
                                } else if (!BluetoothPrinterService.isBluetoothEnabled(context)) {
                                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                } else {
                                    BluetoothPrinterService.getPairedDevices(context)
                                        .onSuccess { pairedDevices = it }
                                        .onFailure { onEmitMessage(it.message ?: "Gagal memuat perangkat") }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cari Printer")
                        }

                        Button(
                            onClick = {
                                if (defaultPrinterAddress.isBlank()) {
                                    onEmitMessage("Pilih printer Bluetooth terlebih dahulu")
                                } else {
                                    scope.launch {
                                        BluetoothPrinterService.printTestPage(
                                            context = context,
                                            printerAddress = defaultPrinterAddress,
                                            storeName = settings.storeName,
                                            paperSizeMm = paperSizeMm
                                        ).onSuccess { onEmitMessage(it) }
                                            .onFailure { onEmitMessage("Tes cetak gagal: ${it.message}") }
                                    }
                                }
                            },
                            enabled = defaultPrinterAddress.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tes Cetak")
                        }
                    }

                    if (pairedDevices.isNotEmpty()) {
                        Text(
                            text = "Pilih dari Perangkat Bluetooth Tersambung:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        pairedDevices.forEach { dev ->
                            val isSelected = dev.address == defaultPrinterAddress
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        defaultPrinterName = dev.name
                                        defaultPrinterAddress = dev.address
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bluetooth, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(dev.name, fontWeight = FontWeight.Bold)
                                            Text(dev.address, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Terpilih",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onSave(
                                settings.copy(
                                    paperSizeMm = paperSizeMm,
                                    autoPrintKitchenTicket = autoPrintKitchenTicket,
                                    autoPrintReceipt = autoPrintReceipt,
                                    printCopies = printCopies,
                                    defaultPrinterName = defaultPrinterName,
                                    defaultPrinterAddress = defaultPrinterAddress
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan Pengaturan Printer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CashierUsersAndPinSettingsTab(
    settings: StoreSettingsEntity,
    users: List<CashierUserEntity>,
    onSaveSettings: (StoreSettingsEntity) -> Unit,
    onSaveUser: (
        id: Long,
        name: String,
        role: String,
        rawPin: String,
        existingPinHash: String,
        canEditPrice: Boolean,
        canGiveDiscount: Boolean,
        canCancelTransaction: Boolean,
        canViewReports: Boolean
    ) -> Unit,
    onDeleteUser: (CashierUserEntity) -> Unit
) {
    var requirePinOnStartup by remember(settings) { mutableStateOf(settings.requirePinOnStartup) }
    var showUserDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<CashierUserEntity?>(null) }

    if (showUserDialog) {
        var name by remember { mutableStateOf(editingUser?.name ?: "") }
        var role by remember { mutableStateOf(editingUser?.role ?: "KASIR") }
        var pinInput by remember { mutableStateOf("") }
        var canEditPrice by remember { mutableStateOf(editingUser?.canEditPrice ?: false) }
        var canGiveDiscount by remember { mutableStateOf(editingUser?.canGiveDiscount ?: true) }
        var canCancelTransaction by remember { mutableStateOf(editingUser?.canCancelTransaction ?: false) }
        var canViewReports by remember { mutableStateOf(editingUser?.canViewReports ?: false) }

        AlertDialog(
            onDismissRequest = {
                showUserDialog = false
                editingUser = null
            },
            title = {
                Text(
                    if (editingUser == null) "Tambah Kasir / Admin" else "Edit Kasir / Admin",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Kasir *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = role == "ADMIN",
                            onClick = {
                                role = "ADMIN"
                                canEditPrice = true
                                canGiveDiscount = true
                                canCancelTransaction = true
                                canViewReports = true
                            },
                            label = { Text("ADMIN") }
                        )
                        FilterChip(
                            selected = role == "KASIR",
                            onClick = { role = "KASIR" },
                            label = { Text("KASIR") }
                        )
                    }

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6) pinInput = it.filter { ch -> ch.isDigit() } },
                        label = {
                            Text(if (editingUser == null) "PIN (4-6 Angka) *" else "PIN Baru (Kosongkan jika tetap)")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Izin Ubah Harga Menu", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = canEditPrice, onCheckedChange = { canEditPrice = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Izin Beri Diskon", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = canGiveDiscount, onCheckedChange = { canGiveDiscount = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Izin Batalkan Billing/Transaksi", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = canCancelTransaction, onCheckedChange = { canCancelTransaction = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Izin Lihat Laporan", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = canViewReports, onCheckedChange = { canViewReports = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveUser(
                            editingUser?.id ?: 0L,
                            name,
                            role,
                            pinInput,
                            editingUser?.pinHash ?: "",
                            canEditPrice,
                            canGiveDiscount,
                            canCancelTransaction,
                            canViewReports
                        )
                        showUserDialog = false
                        editingUser = null
                    },
                    enabled = name.isNotBlank() && (editingUser != null || pinInput.length >= 4)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUserDialog = false; editingUser = null }) {
                    Text("Batal")
                }
            }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kunci PIN Saat Aplikasi Dibuka", fontWeight = FontWeight.Bold)
                            Text(
                                "Default PIN Admin: 1234 | Kasir: 0000",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = requirePinOnStartup,
                            onCheckedChange = {
                                requirePinOnStartup = it
                                onSaveSettings(settings.copy(requirePinOnStartup = it))
                            }
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Daftar Akun Kasir & Admin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        editingUser = null
                        showUserDialog = true
                    }
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Kasir")
                }
            }
        }

        items(users, key = { it.id }) { u ->
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
                        Text("${u.name} (${u.role})", fontWeight = FontWeight.Bold)
                        val perms = buildList {
                            if (u.canEditPrice) add("Ubah Harga")
                            if (u.canGiveDiscount) add("Diskon")
                            if (u.canCancelTransaction) add("Batal Billing")
                            if (u.canViewReports) add("Laporan")
                        }
                        Text(
                            text = "Akses: ${perms.joinToString(", ").ifBlank { "Transaksi Standar" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(
                            onClick = {
                                editingUser = u
                                showUserDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                        if (users.size > 1) {
                            IconButton(onClick = { onDeleteUser(u) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupAndResetSettingsTab(
    onBackup: (Uri) -> Unit,
    onRestore: (Uri) -> Unit,
    onResetTransactionsOnly: () -> Unit,
    onResetFactory: () -> Unit
) {
    var confirmResetTx by remember { mutableStateOf(false) }
    var confirmResetFactory by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onBackup(uri)
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onRestore(uri)
    }

    if (confirmResetTx) {
        AlertDialog(
            onDismissRequest = { confirmResetTx = false },
            title = { Text("Hapus Riwayat Transaksi?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Semua riwayat billing dan transaksi akan dihapus. Daftar menu dan kategori tetap tersimpan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetTransactionsOnly()
                        confirmResetTx = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ya, Hapus Transaksi")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetTx = false }) { Text("Batal") }
            }
        )
    }

    if (confirmResetFactory) {
        AlertDialog(
            onDismissRequest = { confirmResetFactory = false },
            title = { Text("Reset Data Awal Pabrik?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Seluruh transaksi, pelanggan, dan menu tambahan akan direset kembali ke menu contoh awal KasirKu.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetFactory()
                        confirmResetFactory = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ya, Reset Pabrik")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetFactory = false }) { Text("Batal") }
            }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Backup & Restore Database Offline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Simpan seluruh data menu, kategori, metode pembayaran, pelanggan, dan transaksi ke file JSON di HP Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            val ts = SecurityAndFormatUtils.formatDateOnly(System.currentTimeMillis()).replace(" ", "_")
                            backupLauncher.launch("Backup_KasirKu_$ts.json")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_backup_db")
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan File Backup (.json)", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_restore_db")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pulihkan (Restore) dari File Backup")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Reset Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )

                    OutlinedButton(
                        onClick = { confirmResetTx = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hapus Riwayat Transaksi Saja", color = MaterialTheme.colorScheme.error)
                    }

                    Button(
                        onClick = { confirmResetFactory = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Seluruh Data ke Awal Pabrik")
                    }
                }
            }
        }
    }
}
