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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.StoreSettingsEntity
import com.example.service.BluetoothPrinterService
import com.example.service.DiscoveredBluetoothDevice
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.components.PinLockDialog
import com.example.ui.components.openAppNotificationAndPermissionSettings
import com.example.util.SecurityAndFormatUtils

@Composable
fun SettingsScreen(
    settings: StoreSettingsEntity,
    users: List<CashierUserEntity>,
    isAdminUnlocked: Boolean,
    onVerifyAdminPin: (String) -> Boolean,
    onSaveSettings: (StoreSettingsEntity) -> Unit,
    onSaveCashierUser: (CashierUserEntity, String?) -> Unit,
    onDeleteCashierUser: (CashierUserEntity) -> Unit,
    onSelectDefaultPrinter: (String, String, Int) -> Unit,
    onTestPrinter: (String, String) -> Unit,
    onBackupToUri: (Uri) -> Unit,
    onRestoreFromUri: (Uri) -> Unit,
    onExportProductsCsv: (Uri) -> Unit,
    onImportProductsCsv: (Uri) -> Unit,
    onLockAppNow: () -> Unit
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableIntStateOf(0) }

    // Require Admin PIN if protectAdminSettings is enabled
    if (settings.protectAdminSettings && !isAdminUnlocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            PinLockDialog(
                title = "Akses Menu Administrasi",
                subtitle = "Masukkan PIN Admin untuk membuka Pengaturan (Default: 1234)",
                canDismiss = false,
                onVerifyPin = onVerifyAdminPin
            )
        }
        return
    }

    // Editable state synced with current settings
    var draft by remember(settings) { mutableStateOf(settings) }
    var taxPercentText by remember(settings.defaultTaxPercent) {
        mutableStateOf(settings.defaultTaxPercent.toString())
    }
    var serviceFeeText by remember(settings.defaultServiceFee) {
        mutableStateOf(settings.defaultServiceFee.toLong().toString())
    }
    var newAdminPin by remember { mutableStateOf("") }

    // Cashier Dialog States
    var editingUser by remember { mutableStateOf<CashierUserEntity?>(null) }
    var isAddingUser by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<CashierUserEntity?>(null) }

    // Bluetooth Printer States
    var pairedDevices by remember { mutableStateOf<List<DiscoveredBluetoothDevice>>(emptyList()) }
    var btStatusMessage by remember { mutableStateOf<String?>(null) }
    var showBtPermissionRationale by remember { mutableStateOf(false) }

    // Restore Confirmation State
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    // SAF Launchers
    val logoPickerLauncher = rememberLauncherForActivityResult(
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
            draft = draft.copy(storeLogoUri = uri.toString())
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onBackupToUri(uri)
    }

    val openRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) onExportProductsCsv(uri)
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportProductsCsv(uri)
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        BluetoothPrinterService.getPairedDevices(context)
            .onSuccess {
                pairedDevices = it
                btStatusMessage = "Ditemukan ${it.size} perangkat Bluetooth terpasang."
            }
            .onFailure { btStatusMessage = it.message }
    }

    val btPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        if (map.values.all { it }) {
            if (!BluetoothPrinterService.isBluetoothEnabled(context)) {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                BluetoothPrinterService.getPairedDevices(context)
                    .onSuccess {
                        pairedDevices = it
                        btStatusMessage = "Ditemukan ${it.size} perangkat Bluetooth terpasang."
                    }
                    .onFailure { btStatusMessage = it.message }
            }
        } else {
            showBtPermissionRationale = true
        }
    }

    if (showBtPermissionRationale) {
        PermissionRationaleDialog(
            title = "Izin Bluetooth Diperlukan",
            rationaleMessage = "Izin Bluetooth diperlukan untuk menampilkan daftar printer thermal Bluetooth yang terpasang dan melakukan tes cetak.",
            onDismiss = { showBtPermissionRationale = false },
            onOpenSettings = { openAppNotificationAndPermissionSettings(context) }
        )
    }

    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Konfirmasi Restore Database", fontWeight = FontWeight.Bold) },
            text = {
                Text("Proses Restore akan memvalidasi file backup terlebih dahulu, lalu mengganti data saat ini dengan data dari file backup. Apakah Anda yakin ingin melanjutkan?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pendingRestoreUri!!
                        pendingRestoreUri = null
                        onRestoreFromUri(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ya, Lanjutkan Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text("Batal")
                }
            }
        )
    }

    if (isAddingUser || editingUser != null) {
        CashierUserFormDialog(
            initialUser = editingUser ?: CashierUserEntity(name = "", role = "KASIR"),
            onDismiss = {
                isAddingUser = false
                editingUser = null
            },
            onSave = { user, rawPin ->
                onSaveCashierUser(user, rawPin)
                isAddingUser = false
                editingUser = null
            }
        )
    }

    if (userToDelete != null) {
        val target = userToDelete!!
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Hapus Kasir?", fontWeight = FontWeight.Bold) },
            text = { Text("Hapus akun kasir '${target.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCashierUser(target)
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("Batal") }
            }
        )
    }

    val sectionTitles = listOf(
        "Info Toko",
        "Kasir & Akses",
        "Transaksi & Stok",
        "Printer Bluetooth",
        "Tampilan",
        "Backup & Export",
        "Keamanan"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedSection,
            edgePadding = 12.dp
        ) {
            sectionTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSection == index,
                    onClick = { selectedSection = index },
                    text = { Text(title, fontWeight = FontWeight.Bold) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedSection) {
                0 -> {
                    // INFORMASI TOKO
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Informasi Profil Toko", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (draft.storeLogoUri.isNotBlank()) {
                                        AsyncImage(
                                            model = Uri.parse(draft.storeLogoUri),
                                            contentDescription = "Logo Toko",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Image, contentDescription = null)
                                        }
                                    }
                                    Column {
                                        OutlinedButton(
                                            onClick = {
                                                logoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            }
                                        ) {
                                            Text("Pilih Logo Toko")
                                        }
                                        if (draft.storeLogoUri.isNotBlank()) {
                                            TextButton(onClick = { draft = draft.copy(storeLogoUri = "") }) {
                                                Text("Hapus Logo")
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = draft.storeName,
                                    onValueChange = { draft = draft.copy(storeName = it) },
                                    label = { Text("Nama Toko *") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = draft.storeAddress,
                                    onValueChange = { draft = draft.copy(storeAddress = it) },
                                    label = { Text("Alamat Toko") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = draft.storePhone,
                                    onValueChange = { draft = draft.copy(storePhone = it) },
                                    label = { Text("Nomor HP / Telepon") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = draft.storeEmail,
                                    onValueChange = { draft = draft.copy(storeEmail = it) },
                                    label = { Text("Email Toko") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = draft.storeNpwp,
                                    onValueChange = { draft = draft.copy(storeNpwp = it) },
                                    label = { Text("NPWP (Opsional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = draft.receiptFooter,
                                    onValueChange = { draft = draft.copy(receiptFooter = it) },
                                    label = { Text("Footer Struk") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = { onSaveSettings(draft) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Simpan Informasi Toko")
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // PENGATURAN KASIR & HAK AKSES
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Daftar Kasir & Hak Akses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Button(onClick = { isAddingUser = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tambah Kasir")
                            }
                        }
                    }

                    items(users.size) { idx ->
                        val u = users[idx]
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${u.name} (${u.role})", fontWeight = FontWeight.Bold)
                                    val perms = buildList {
                                        if (u.canGiveDiscount) add("Diskon")
                                        if (u.canManageStock) add("Stok")
                                        if (u.canVoidTransaction) add("Refund")
                                        if (u.canViewReports) add("Laporan")
                                        if (u.canManageSettings) add("Pengaturan")
                                    }
                                    Text(
                                        text = "Akses: ${perms.joinToString(", ").ifEmpty { "Kasir Dasar" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(onClick = { editingUser = u }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Kasir", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    if (users.size > 1) {
                                        IconButton(onClick = { userToDelete = u }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus Kasir", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // PENGATURAN TRANSAKSI & STOK
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Pengaturan Transaksi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                SettingSwitchRow(
                                    title = "Aktifkan Fitur Diskon",
                                    subtitle = "Izinkan input potongan diskon di halaman kasir",
                                    checked = draft.enableDiscount,
                                    onCheckedChange = { draft = draft.copy(enableDiscount = it) }
                                )

                                SettingSwitchRow(
                                    title = "Aktifkan Pajak Otomatis (PPN)",
                                    subtitle = "Tambahkan pajak otomatis pada setiap transaksi",
                                    checked = draft.enableTax,
                                    onCheckedChange = { draft = draft.copy(enableTax = it) }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = taxPercentText,
                                        onValueChange = {
                                            taxPercentText = it
                                            draft = draft.copy(defaultTaxPercent = it.toDoubleOrNull() ?: 0.0)
                                        },
                                        label = { Text("Persentase Pajak (%)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = serviceFeeText,
                                        onValueChange = {
                                            serviceFeeText = it.filter { ch -> ch.isDigit() }
                                            draft = draft.copy(defaultServiceFee = serviceFeeText.toDoubleOrNull() ?: 0.0)
                                        },
                                        label = { Text("Biaya Layanan Default (Rp)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                SettingSwitchRow(
                                    title = "Pembulatan Harga Otomatis",
                                    subtitle = "Bulatkan total tagihan ke ratusan Rupiah terdekat",
                                    checked = draft.enableRounding,
                                    onCheckedChange = { draft = draft.copy(enableRounding = it) }
                                )

                                SettingSwitchRow(
                                    title = "Nomor Transaksi Otomatis",
                                    subtitle = "Buat nomor struk berurutan secara otomatis",
                                    checked = draft.autoInvoiceNumber,
                                    onCheckedChange = { draft = draft.copy(autoInvoiceNumber = it) }
                                )

                                OutlinedTextField(
                                    value = draft.invoicePrefix,
                                    onValueChange = { draft = draft.copy(invoicePrefix = it) },
                                    label = { Text("Format / Prefix Nomor Transaksi (Contoh: INV)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                HorizontalDivider()

                                Text("Pengaturan Stok", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                SettingSwitchRow(
                                    title = "Pengurangan Stok Otomatis",
                                    subtitle = "Kurangi stok produk secara otomatis saat pembayaran berhasil",
                                    checked = draft.autoReduceStock,
                                    onCheckedChange = { draft = draft.copy(autoReduceStock = it) }
                                )

                                SettingSwitchRow(
                                    title = "Peringatan Stok Minimum",
                                    subtitle = "Tampilkan indikator stok menipis pada produk & beranda",
                                    checked = draft.enableLowStockAlert,
                                    onCheckedChange = { draft = draft.copy(enableLowStockAlert = it) }
                                )

                                SettingSwitchRow(
                                    title = "Izinkan Stok Minus (Negatif)",
                                    subtitle = "Jika aktif, kasir tetap bisa menjual produk meski stok tercatat 0",
                                    checked = draft.allowNegativeStock,
                                    onCheckedChange = { draft = draft.copy(allowNegativeStock = it) }
                                )

                                Button(
                                    onClick = { onSaveSettings(draft) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Simpan Pengaturan Transaksi & Stok")
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // PENGATURAN PRINTER THERMAL BLUETOOTH
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Pengaturan Printer Thermal Bluetooth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Printer Default Saat Ini:", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            text = if (draft.defaultPrinterAddress.isNotBlank()) {
                                                "${draft.defaultPrinterName} (${draft.defaultPrinterAddress})"
                                            } else {
                                                "Belum ada printer dipilih"
                                            },
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                Text("Ukuran Kertas Struk Thermal", fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FilterChip(
                                        selected = draft.paperSizeMm == 58,
                                        onClick = { draft = draft.copy(paperSizeMm = 58) },
                                        label = { Text("58mm (32 Karakter)") }
                                    )
                                    FilterChip(
                                        selected = draft.paperSizeMm == 80,
                                        onClick = { draft = draft.copy(paperSizeMm = 80) },
                                        label = { Text("80mm (48 Karakter)") }
                                    )
                                }

                                Text("Jumlah Salinan Cetak: ${draft.printCopies}x", fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1, 2, 3).forEach { count ->
                                        FilterChip(
                                            selected = draft.printCopies == count,
                                            onClick = { draft = draft.copy(printCopies = count) },
                                            label = { Text("$count Salinan") }
                                        )
                                    }
                                }

                                SettingSwitchRow(
                                    title = "Cetak Otomatis Setelah Pembayaran",
                                    subtitle = "Langsung cetak struk ke printer default begitu pembayaran selesai",
                                    checked = draft.autoPrintReceipt,
                                    onCheckedChange = { draft = draft.copy(autoPrintReceipt = it) }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (!BluetoothPrinterService.hasBluetoothPermissions(context)) {
                                                btPermLauncher.launch(BluetoothPrinterService.getRequiredBluetoothPermissions())
                                            } else if (!BluetoothPrinterService.isBluetoothEnabled(context)) {
                                                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                            } else {
                                                BluetoothPrinterService.getPairedDevices(context)
                                                    .onSuccess {
                                                        pairedDevices = it
                                                        btStatusMessage = "Ditemukan ${it.size} perangkat Bluetooth terpasang."
                                                    }
                                                    .onFailure { btStatusMessage = it.message }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Cari Printer")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onSaveSettings(draft)
                                            onTestPrinter(draft.defaultPrinterName.ifBlank { "Printer Default" }, draft.defaultPrinterAddress)
                                        },
                                        enabled = draft.defaultPrinterAddress.isNotBlank(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tes Printer")
                                    }
                                }

                                if (btStatusMessage != null) {
                                    Text(
                                        text = btStatusMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                pairedDevices.forEach { dev ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (draft.defaultPrinterAddress == dev.address) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                draft = draft.copy(
                                                    defaultPrinterName = dev.name,
                                                    defaultPrinterAddress = dev.address
                                                )
                                                onSelectDefaultPrinter(dev.name, dev.address, draft.paperSizeMm)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(dev.name, fontWeight = FontWeight.Bold)
                                                Text(dev.address, style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text(
                                                if (draft.defaultPrinterAddress == dev.address) "Terpilih" else "Pilih Default",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = { onSaveSettings(draft) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Simpan Pengaturan Printer")
                                }
                            }
                        }
                    }
                }

                4 -> {
                    // PENGATURAN TAMPILAN
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Pengaturan Tampilan & Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                Text("Mode Tema Aplikasi", fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("LIGHT" to "Terang", "DARK" to "Gelap", "SYSTEM" to "Sistem").forEach { (key, label) ->
                                        FilterChip(
                                            selected = draft.themeMode == key,
                                            onClick = {
                                                draft = draft.copy(themeMode = key)
                                                onSaveSettings(draft)
                                            },
                                            label = { Text(label) }
                                        )
                                    }
                                }

                                Text("Ukuran Teks", fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0.9f to "Ringkas", 1.0f to "Normal", 1.15f to "Besar").forEach { (scale, label) ->
                                        FilterChip(
                                            selected = draft.textScale == scale,
                                            onClick = {
                                                draft = draft.copy(textScale = scale)
                                                onSaveSettings(draft)
                                            },
                                            label = { Text(label) }
                                        )
                                    }
                                }

                                Text("Tampilan Produk di Halaman Kasir", fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = draft.productViewMode == "GRID",
                                        onClick = {
                                            draft = draft.copy(productViewMode = "GRID")
                                            onSaveSettings(draft)
                                        },
                                        label = { Text("Grid (Kotak)") }
                                    )
                                    FilterChip(
                                        selected = draft.productViewMode == "LIST",
                                        onClick = {
                                            draft = draft.copy(productViewMode = "LIST")
                                            onSaveSettings(draft)
                                        },
                                        label = { Text("List (Baris)") }
                                    )
                                }

                                if (draft.productViewMode == "GRID") {
                                    Text("Jumlah Produk per Baris (Grid)", fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(2, 3).forEach { cols ->
                                            FilterChip(
                                                selected = draft.gridColumns == cols,
                                                onClick = {
                                                    draft = draft.copy(gridColumns = cols)
                                                    onSaveSettings(draft)
                                                },
                                                label = { Text("$cols Kolom") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                5 -> {
                    // BACKUP, RESTORE, EXPORT, IMPORT
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Backup & Restore Database Lengkap", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Simpan seluruh database (produk, transaksi, stok, pelanggan, pengaturan) ke penyimpanan HP Anda secara aman menggunakan Storage Access Framework.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        val fileName = "KasirKu_Backup_${SecurityAndFormatUtils.formatInvoiceDate(System.currentTimeMillis())}.json"
                                        createBackupLauncher.launch(fileName)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_backup_database")
                                ) {
                                    Icon(Icons.Default.Backup, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Backup Database Sekarang (JSON)")
                                }

                                OutlinedButton(
                                    onClick = {
                                        openRestoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_restore_database")
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Restore Database dari File Backup")
                                }

                                HorizontalDivider()

                                Text("Export & Import Katalog Produk (CSV)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Ekspor daftar produk ke Excel/CSV atau impor produk massal dari file CSV.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            exportCsvLauncher.launch("Katalog_Produk_KasirKu.csv")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Export CSV")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            importCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Import CSV")
                                    }
                                }
                            }
                        }
                    }
                }

                6 -> {
                    // KEAMANAN & KUNCI APLIKASI
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Pengaturan Keamanan & PIN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                SettingSwitchRow(
                                    title = "Lindungi Menu Pengaturan dengan PIN Admin",
                                    subtitle = "Wajib memasukkan PIN Admin sebelum mengubah pengaturan toko",
                                    checked = draft.protectAdminSettings,
                                    onCheckedChange = { draft = draft.copy(protectAdminSettings = it) }
                                )

                                SettingSwitchRow(
                                    title = "Kunci Aplikasi Saat Baru Dibuka",
                                    subtitle = "Wajib memasukkan PIN Kasir/Admin ketika membuka aplikasi",
                                    checked = draft.requirePinOnStartup,
                                    onCheckedChange = { draft = draft.copy(requirePinOnStartup = it) }
                                )

                                OutlinedTextField(
                                    value = newAdminPin,
                                    onValueChange = { newAdminPin = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Ubah PIN Admin Baru (Kosongkan jika tidak diubah)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        val updated = if (newAdminPin.length >= 4) {
                                            draft.copy(adminPinHash = SecurityAndFormatUtils.hashPin(newAdminPin))
                                        } else {
                                            draft
                                        }
                                        onSaveSettings(updated)
                                        newAdminPin = ""
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Simpan Pengaturan Keamanan")
                                }

                                HorizontalDivider()

                                OutlinedButton(
                                    onClick = onLockAppNow,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Kunci Aplikasi / Logout Sekarang")
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
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CashierUserFormDialog(
    initialUser: CashierUserEntity,
    onDismiss: () -> Unit,
    onSave: (CashierUserEntity, String?) -> Unit
) {
    var name by remember { mutableStateOf(initialUser.name) }
    var role by remember { mutableStateOf(initialUser.role) }
    var rawPin by remember { mutableStateOf("") }
    var canGiveDiscount by remember { mutableStateOf(initialUser.canGiveDiscount) }
    var canManageStock by remember { mutableStateOf(initialUser.canManageStock) }
    var canVoidTransaction by remember { mutableStateOf(initialUser.canVoidTransaction) }
    var canViewReports by remember { mutableStateOf(initialUser.canViewReports) }
    var canManageSettings by remember { mutableStateOf(initialUser.canManageSettings) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialUser.id == 0L) "Tambah Kasir Baru" else "Edit Kasir",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text("Nama Kasir *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == "KASIR",
                        onClick = { role = "KASIR" },
                        label = { Text("KASIR") }
                    )
                    FilterChip(
                        selected = role == "ADMIN",
                        onClick = { role = "ADMIN" },
                        label = { Text("ADMIN") }
                    )
                }
                OutlinedTextField(
                    value = rawPin,
                    onValueChange = { rawPin = it.filter { ch -> ch.isDigit() } },
                    label = {
                        Text(if (initialUser.id == 0L) "PIN Kasir (Min 4 angka) *" else "PIN Baru (Kosongkan jika tetap)")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Hak Akses Kasir:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                PermissionCheckbox("Beri Diskon Transaksi", canGiveDiscount) { canGiveDiscount = it }
                PermissionCheckbox("Kelola Stok Barang", canManageStock) { canManageStock = it }
                PermissionCheckbox("Batalkan / Refund Transaksi", canVoidTransaction) { canVoidTransaction = it }
                PermissionCheckbox("Lihat Laporan Keuangan", canViewReports) { canViewReports = it }
                PermissionCheckbox("Ubah Pengaturan Toko", canManageSettings) { canManageSettings = it }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        errorMsg = "Nama kasir wajib diisi!"
                        return@Button
                    }
                    if (initialUser.id == 0L && rawPin.length < 4) {
                        errorMsg = "PIN minimal 4 digit!"
                        return@Button
                    }
                    onSave(
                        initialUser.copy(
                            name = name.trim(),
                            role = role,
                            canGiveDiscount = canGiveDiscount,
                            canManageStock = canManageStock,
                            canVoidTransaction = canVoidTransaction,
                            canViewReports = canViewReports,
                            canManageSettings = canManageSettings
                        ),
                        rawPin.ifBlank { null }
                    )
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun PermissionCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
