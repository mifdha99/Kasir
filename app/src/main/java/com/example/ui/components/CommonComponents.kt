package com.example.ui.components

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.StoreSettingsEntity
import com.example.data.TransactionWithItems
import com.example.service.BarcodeScannerService
import com.example.service.BluetoothPrinterService
import com.example.service.DiscoveredBluetoothDevice
import com.example.util.SecurityAndFormatUtils
import java.util.concurrent.Executors

object CategoryVisuals {
    val iconOptions: List<Pair<String, ImageVector>> = listOf(
        "ShoppingBag" to Icons.Default.ShoppingBag,
        "LocalCafe" to Icons.Default.LocalCafe,
        "Restaurant" to Icons.Default.Restaurant,
        "Inventory2" to Icons.Default.Inventory2,
        "BakeryDining" to Icons.Default.BakeryDining,
        "Fastfood" to Icons.Default.Fastfood,
        "LocalGroceryStore" to Icons.Default.LocalGroceryStore,
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
            ?: Icons.Default.ShoppingBag
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
fun BarcodeScannerDialog(
    title: String = "Scan Barcode Produk",
    onDismiss: () -> Unit,
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showDeniedRationale by remember { mutableStateOf(false) }
    var manualCode by remember { mutableStateOf("") }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            showDeniedRationale = true
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showDeniedRationale) {
        PermissionRationaleDialog(
            title = "Izin Kamera Diperlukan",
            rationaleMessage = "Izin Kamera diperlukan untuk memindai barcode produk secara langsung menggunakan kamera HP Anda. Anda juga tetap bisa memasukkan kode barcode secara manual.",
            onDismiss = { showDeniedRationale = false },
            onOpenSettings = { openAppNotificationAndPermissionSettings(context) }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup Scanner")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (hasCameraPermission && cameraError == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                        DisposableEffect(Unit) {
                            onDispose {
                                cameraExecutor.shutdown()
                            }
                        }

                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val analyzer = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also { analysis ->
                                                analysis.setAnalyzer(
                                                    cameraExecutor,
                                                    BarcodeScannerService.ZxingBarcodeAnalyzer { code ->
                                                        previewView.post {
                                                            onBarcodeDetectedSafe(code, onBarcodeScanned, onDismiss)
                                                        }
                                                    }
                                                )
                                            }

                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            analyzer
                                        )
                                    } catch (e: Exception) {
                                        cameraError = "Kamera tidak dapat diakses pada perangkat ini. Gunakan input kode manual di bawah."
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            }
                        )

                        // Target frame overlay
                        Box(
                            modifier = Modifier
                                .size(width = 220.dp, height = 110.dp)
                                .border(2.dp, Color(0xFF2DD4BF), RoundedCornerShape(12.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Arahkan kamera ke barcode produk (EAN / Code128 / QR)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = cameraError
                                    ?: "Izin kamera belum aktif. Aktifkan izin kamera untuk scan langsung atau ketik barcode di bawah.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            if (!hasCameraPermission) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                                    ) {
                                        Text("Minta Izin Kamera")
                                    }
                                    Button(
                                        onClick = { openAppNotificationAndPermissionSettings(context) }
                                    ) {
                                        Text("Buka Pengaturan")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Atau Masukkan Kode Barcode / SKU Manual:",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        placeholder = { Text("Contoh: 899100100001") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_barcode_input")
                    )
                    Button(
                        onClick = {
                            if (manualCode.isNotBlank()) {
                                onBarcodeScanned(manualCode.trim())
                                onDismiss()
                            }
                        },
                        enabled = manualCode.isNotBlank(),
                        modifier = Modifier.testTag("btn_submit_manual_barcode")
                    ) {
                        Text("Cari")
                    }
                }
            }
        }
    }
}

private fun onBarcodeDetectedSafe(
    code: String,
    onBarcodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    onBarcodeScanned(code)
    onDismiss()
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
                        text = "Pembayaran Berhasil!",
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
                            text = "Detail & Cetak Struk",
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
                            val fileName = "Struk_${txWithItems.transaction.invoiceNumber}.pdf"
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
                        text = if (isNewCheckout) "Transaksi Baru" else "Selesai",
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
