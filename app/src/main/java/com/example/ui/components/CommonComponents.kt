package com.example.ui.components

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.StoreSettingsEntity
import com.example.data.TransactionWithItems
import com.example.service.BluetoothPrinterService
import com.example.service.DiscoveredBluetoothDevice
import com.example.util.SecurityAndFormatUtils

object CategoryVisuals {
    val iconOptions: List<Pair<String, ImageVector>> = listOf(
        "Restaurant" to Icons.Default.Restaurant,
        "LocalCafe" to Icons.Default.LocalCafe,
        "Fastfood" to Icons.Default.Fastfood,
        "BakeryDining" to Icons.Default.BakeryDining,
        "ShoppingBag" to Icons.Default.ShoppingBag,
        "LocalGroceryStore" to Icons.Default.LocalGroceryStore,
        "Inventory2" to Icons.Default.Inventory2,
        "Home" to Icons.Default.Home,
        "Checkroom" to Icons.Default.Checkroom,
        "MedicalServices" to Icons.Default.MedicalServices,
        "Build" to Icons.Default.Build,
        "Pets" to Icons.Default.Pets
    )

    val colorOptions: List<String> = listOf(
        "#0F766E", // Teal
        "#D97706", // Amber
        "#2563EB", // Blue
        "#7C3AED", // Purple
        "#DC2626", // Red
        "#059669", // Emerald
        "#DB2777", // Pink
        "#4F46E5"  // Indigo
    )

    fun getIcon(name: String): ImageVector {
        return iconOptions.find { it.first.equals(name, ignoreCase = true) }?.second
            ?: Icons.Default.Restaurant
    }

    fun parseColor(hex: String): Color {
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            Color(0xFF0F766E)
        }
    }
}

fun openAppNotificationAndPermissionSettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

@Composable
fun PermissionRationaleDialog(
    title: String,
    rationaleMessage: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Izin Diperlukan",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = rationaleMessage)
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onOpenSettings()
                },
                modifier = Modifier.testTag("btn_open_app_settings")
            ) {
                Text("Buka Pengaturan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun KitchenTicketDialog(
    txWithItems: TransactionWithItems,
    settings: StoreSettingsEntity,
    onDismiss: () -> Unit,
    onPrintKitchenBluetooth: (String?) -> Unit,
    onShareKitchenText: () -> Unit,
    onSelectDefaultPrinter: (String, String, Int) -> Unit
) {
    val context = LocalContext.current
    val kitchenText = remember(txWithItems, settings) {
        BluetoothPrinterService.formatKitchenTicketText(txWithItems, settings.paperSizeMm)
    }

    var showPrinterPicker by remember { mutableStateOf(false) }
    var showBtPermissionRationale by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf<List<DiscoveredBluetoothDevice>>(emptyList()) }
    var btErrorMessage by remember { mutableStateOf<String?>(null) }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        BluetoothPrinterService.getPairedDevices(context)
            .onSuccess {
                pairedDevices = it
                btErrorMessage = null
            }
            .onFailure { btErrorMessage = it.message }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        if (allGranted) {
            if (!BluetoothPrinterService.isBluetoothEnabled(context)) {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else if (settings.defaultPrinterAddress.isNotBlank()) {
                onPrintKitchenBluetooth(null)
            } else {
                BluetoothPrinterService.getPairedDevices(context)
                    .onSuccess {
                        pairedDevices = it
                        showPrinterPicker = true
                    }
                    .onFailure { btErrorMessage = it.message; showPrinterPicker = true }
            }
        } else {
            showBtPermissionRationale = true
        }
    }

    if (showBtPermissionRationale) {
        PermissionRationaleDialog(
            title = "Izin Bluetooth Diperlukan",
            rationaleMessage = "KasirKu memerlukan izin Bluetooth untuk mencetak tiket pesanan dapur ke printer thermal Bluetooth.",
            onDismiss = { showBtPermissionRationale = false },
            onOpenSettings = { openAppNotificationAndPermissionSettings(context) }
        )
    }

    if (showPrinterPicker) {
        AlertDialog(
            onDismissRequest = { showPrinterPicker = false },
            title = { Text("Pilih Printer Dapur Bluetooth", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (btErrorMessage != null) {
                        Text(
                            text = btErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (pairedDevices.isEmpty()) {
                        Text(
                            "Belum ada printer Bluetooth yang dipasangkan (paired) di HP Anda.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        pairedDevices.forEach { dev ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onSelectDefaultPrinter(dev.name, dev.address, settings.paperSizeMm)
                                        showPrinterPicker = false
                                        onPrintKitchenBluetooth(dev.address)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(dev.name, fontWeight = FontWeight.SemiBold)
                                        Text(dev.address, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrinterPicker = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(12.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RestaurantMenu,
                        contentDescription = "Pesanan Dapur",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pesanan Dikirim ke Dapur!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${txWithItems.transaction.billingDisplay} • Status: BELUM BAYAR",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    color = Color(0xFFFFFDF9),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = kitchenText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFF1E293B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .testTag("kitchen_ticket_preview")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (!BluetoothPrinterService.hasBluetoothPermissions(context)) {
                                btPermissionLauncher.launch(BluetoothPrinterService.getRequiredBluetoothPermissions())
                            } else if (!BluetoothPrinterService.isBluetoothEnabled(context)) {
                                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            } else if (settings.defaultPrinterAddress.isNotBlank()) {
                                onPrintKitchenBluetooth(null)
                            } else {
                                BluetoothPrinterService.getPairedDevices(context)
                                    .onSuccess {
                                        pairedDevices = it
                                        showPrinterPicker = true
                                    }
                                    .onFailure { btErrorMessage = it.message; showPrinterPicker = true }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_print_kitchen_ticket")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cetak Dapur")
                    }

                    OutlinedButton(
                        onClick = onShareKitchenText,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_share_kitchen_ticket")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bagikan Dapur")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_close_kitchen_ticket")
                ) {
                    Text(
                        text = "Selesai & Buat Pesanan Berikutnya",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
fun ReceiptPreviewAndActionDialog(
    txWithItems: TransactionWithItems,
    settings: StoreSettingsEntity,
    isNewCheckout: Boolean = false,
    onDismiss: () -> Unit,
    onPrintBluetooth: (String?) -> Unit,
    onShareText: () -> Unit,
    onSharePdf: () -> Unit,
    onSavePdfToUri: (Uri) -> Unit,
    onSelectDefaultPrinter: (String, String, Int) -> Unit
) {
    val context = LocalContext.current
    val receiptText = remember(txWithItems, settings) {
        BluetoothPrinterService.formatReceiptText(txWithItems, settings)
    }

    var showPrinterPicker by remember { mutableStateOf(false) }
    var showBtPermissionRationale by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf<List<DiscoveredBluetoothDevice>>(emptyList()) }
    var btErrorMessage by remember { mutableStateOf<String?>(null) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            onSavePdfToUri(uri)
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        BluetoothPrinterService.getPairedDevices(context)
            .onSuccess {
                pairedDevices = it
                btErrorMessage = null
            }
            .onFailure { btErrorMessage = it.message }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        if (allGranted) {
            if (!BluetoothPrinterService.isBluetoothEnabled(context)) {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else if (settings.defaultPrinterAddress.isNotBlank()) {
                onPrintBluetooth(null)
            } else {
                BluetoothPrinterService.getPairedDevices(context)
                    .onSuccess {
                        pairedDevices = it
                        showPrinterPicker = true
                    }
                    .onFailure { btErrorMessage = it.message }
            }
        } else {
            showBtPermissionRationale = true
        }
    }

    if (showBtPermissionRationale) {
        PermissionRationaleDialog(
            title = "Izin Bluetooth Diperlukan",
            rationaleMessage = "KasirKu memerlukan izin Bluetooth untuk mencari dan mencetak struk ke printer thermal Bluetooth Anda.",
            onDismiss = { showBtPermissionRationale = false },
            onOpenSettings = { openAppNotificationAndPermissionSettings(context) }
        )
    }

    if (showPrinterPicker) {
        AlertDialog(
            onDismissRequest = { showPrinterPicker = false },
            title = { Text("Pilih Printer Bluetooth", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (btErrorMessage != null) {
                        Text(
                            text = btErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (pairedDevices.isEmpty()) {
                        Text(
                            "Belum ada printer Bluetooth yang dipasangkan (paired) di pengaturan Bluetooth HP Anda. Pastikan printer menyala dan sudah di-pair.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        pairedDevices.forEach { dev ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onSelectDefaultPrinter(dev.name, dev.address, settings.paperSizeMm)
                                        showPrinterPicker = false
                                        onPrintBluetooth(dev.address)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(dev.name, fontWeight = FontWeight.SemiBold)
                                        Text(dev.address, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrinterPicker = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(12.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isNewCheckout) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Pembayaran Berhasil",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${txWithItems.transaction.billingDisplay} LUNAS!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Kembalian: ${SecurityAndFormatUtils.formatRupiah(txWithItems.transaction.changeAmount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Detail Struk (${txWithItems.transaction.billingDisplay})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                // Thermal Receipt Paper Simulation
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    color = Color(0xFFFFFDF9),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = receiptText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        color = Color(0xFF1E293B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Row 1: Print Bluetooth + Pilih Printer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (!BluetoothPrinterService.hasBluetoothPermissions(context)) {
                                btPermissionLauncher.launch(BluetoothPrinterService.getRequiredBluetoothPermissions())
                            } else if (!BluetoothPrinterService.isBluetoothEnabled(context)) {
                                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            } else if (settings.defaultPrinterAddress.isNotBlank()) {
                                onPrintBluetooth(null)
                            } else {
                                BluetoothPrinterService.getPairedDevices(context)
                                    .onSuccess {
                                        pairedDevices = it
                                        showPrinterPicker = true
                                    }
                                    .onFailure { btErrorMessage = it.message; showPrinterPicker = true }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_print_receipt_bluetooth")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cetak Struk")
                    }

                    OutlinedButton(
                        onClick = {
                            if (!BluetoothPrinterService.hasBluetoothPermissions(context)) {
                                btPermissionLauncher.launch(BluetoothPrinterService.getRequiredBluetoothPermissions())
                            } else if (!BluetoothPrinterService.isBluetoothEnabled(context)) {
                                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            } else {
                                BluetoothPrinterService.getPairedDevices(context)
                                    .onSuccess {
                                        pairedDevices = it
                                        btErrorMessage = null
                                        showPrinterPicker = true
                                    }
                                    .onFailure {
                                        btErrorMessage = it.message
                                        showPrinterPicker = true
                                    }
                            }
                        },
                        modifier = Modifier.testTag("btn_choose_printer")
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Printer")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons Row 2: Bagikan Struk, Simpan PDF, Bagikan PDF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onShareText,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_share_receipt_text")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bagikan Teks")
                    }

                    OutlinedButton(
                        onClick = {
                            val safeName = txWithItems.transaction.billingDisplay.replace(" ", "_")
                            val fileName = "Struk_$safeName.pdf"
                            savePdfLauncher.launch(fileName)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_save_receipt_pdf")
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simpan PDF")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onSharePdf,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bagikan File Struk PDF (WhatsApp / Email)")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_new_transaction")
                ) {
                    Text(
                        text = if (isNewCheckout) "Selesai" else "Tutup",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PinLockDialog(
    title: String = "Masukkan PIN Keamanan",
    subtitle: String = "PIN default Admin adalah 1234",
    canDismiss: Boolean = true,
    onDismiss: () -> Unit = {},
    onVerifyPin: (String) -> Boolean
) {
    var pinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        errorText = null
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_input_field")
                )
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ok = onVerifyPin(pinInput)
                    if (!ok) {
                        errorText = "PIN yang Anda masukkan salah!"
                    }
                },
                modifier = Modifier.testTag("btn_verify_pin")
            ) {
                Text("Buka")
            }
        },
        dismissButton = {
            if (canDismiss) {
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
            }
        }
    )
}
