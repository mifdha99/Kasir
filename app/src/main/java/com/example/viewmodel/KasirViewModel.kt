package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CartItem
import com.example.data.CashierUserEntity
import com.example.data.CategoryEntity
import com.example.data.CustomerEntity
import com.example.data.KasirDatabase
import com.example.data.KasirRepository
import com.example.data.PrinterDeviceEntity
import com.example.data.ProductEntity
import com.example.data.StockMovementEntity
import com.example.data.StoreSettingsEntity
import com.example.data.TransactionWithItems
import com.example.service.BackupAndExportService
import com.example.service.BluetoothPrinterService
import com.example.util.SecurityAndFormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreen(val title: String) {
    DASHBOARD("Beranda KasirKu"),
    POS("Kasir / Transaksi"),
    PRODUCTS("Daftar Produk"),
    CATEGORIES("Kategori Produk"),
    STOCK("Manajemen Stok"),
    CUSTOMERS("Data Pelanggan"),
    HISTORY("Riwayat Transaksi"),
    REPORTS("Laporan & Analisis"),
    SETTINGS("Pengaturan Toko")
}

enum class ProductSortOption(val label: String) {
    NAME("Nama (A-Z)"),
    PRICE_ASC("Harga Terendah"),
    PRICE_DESC("Harga Tertinggi"),
    STOCK_ASC("Stok Terendah"),
    NEWEST("Terbaru")
}

enum class ReportFilterPeriod(val label: String) {
    TODAY("Hari Ini"),
    YESTERDAY("Kemarin"),
    LAST_7_DAYS("7 Hari Terakhir"),
    THIS_MONTH("Bulan Ini"),
    LAST_MONTH("Bulan Lalu"),
    CUSTOM("Custom Tanggal")
}

class KasirViewModel(application: Application) : AndroidViewModel(application) {

    private val database = KasirDatabase.getInstance(application)
    private val repository = KasirRepository(database.kasirDao())

    val categories: StateFlow<List<CategoryEntity>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = repository.products
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repository.customers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val users: StateFlow<List<CashierUserEntity>> = repository.users
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<StoreSettingsEntity> = repository.settings
        .map { it ?: StoreSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreSettingsEntity())

    val printers: StateFlow<List<PrinterDeviceEntity>> = repository.printers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockMovements: StateFlow<List<StockMovementEntity>> = repository.stockMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionsWithItems: StateFlow<List<TransactionWithItems>> = repository.transactionsWithItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- NAVIGATION & SESSION STATE ---
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _activeCashier = MutableStateFlow<CashierUserEntity?>(null)
    val activeCashier: StateFlow<CashierUserEntity?> = _activeCashier.asStateFlow()

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _isAdminUnlocked = MutableStateFlow(false)
    val isAdminUnlocked: StateFlow<Boolean> = _isAdminUnlocked.asStateFlow()

    // --- SNACKBAR / STATUS FEEDBACK ---
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // --- CART & POS STATE ---
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomer.asStateFlow()

    private val _discountInput = MutableStateFlow(0.0)
    val discountInput: StateFlow<Double> = _discountInput.asStateFlow()

    private val _serviceFeeInput = MutableStateFlow(0.0)
    val serviceFeeInput: StateFlow<Double> = _serviceFeeInput.asStateFlow()

    private val _transactionNote = MutableStateFlow("")
    val transactionNote: StateFlow<String> = _transactionNote.asStateFlow()

    private val _paymentMethod = MutableStateFlow("Tunai")
    val paymentMethod: StateFlow<String> = _paymentMethod.asStateFlow()

    private val _amountPaidInput = MutableStateFlow("")
    val amountPaidInput: StateFlow<String> = _amountPaidInput.asStateFlow()

    private val _lastCompletedTransaction = MutableStateFlow<TransactionWithItems?>(null)
    val lastCompletedTransaction: StateFlow<TransactionWithItems?> = _lastCompletedTransaction.asStateFlow()

    init {
        viewModelScope.launch {
            users.collect { list ->
                if (_activeCashier.value == null && list.isNotEmpty()) {
                    _activeCashier.value = list.first()
                } else if (_activeCashier.value != null) {
                    val updated = list.find { it.id == _activeCashier.value?.id }
                    if (updated != null) _activeCashier.value = updated
                }
            }
        }
        viewModelScope.launch {
            settings.collect { s ->
                _serviceFeeInput.value = s.defaultServiceFee
            }
        }
    }

    fun showMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun lockApp() {
        _isAppLocked.value = true
        _isAdminUnlocked.value = false
    }

    fun unlockAppWithPin(pin: String, user: CashierUserEntity? = null): Boolean {
        val targetUser = user ?: _activeCashier.value ?: users.value.firstOrNull()
        val s = settings.value
        val matchedUser = if (targetUser != null && SecurityAndFormatUtils.verifyPin(pin, targetUser.pinHash)) {
            targetUser
        } else {
            users.value.firstOrNull { SecurityAndFormatUtils.verifyPin(pin, it.pinHash) }
        }

        return if (matchedUser != null || SecurityAndFormatUtils.verifyPin(pin, s.adminPinHash)) {
            if (matchedUser != null) {
                _activeCashier.value = matchedUser
            }
            _isAppLocked.value = false
            true
        } else {
            false
        }
    }

    fun verifyAdminPin(pin: String): Boolean {
        val s = settings.value
        val adminMatch = SecurityAndFormatUtils.verifyPin(pin, s.adminPinHash) ||
            users.value.any { it.role == "ADMIN" && SecurityAndFormatUtils.verifyPin(pin, it.pinHash) }
        if (adminMatch) {
            _isAdminUnlocked.value = true
        }
        return adminMatch
    }

    fun switchCashier(user: CashierUserEntity, pin: String): Boolean {
        return if (SecurityAndFormatUtils.verifyPin(pin, user.pinHash)) {
            _activeCashier.value = user
            _isAdminUnlocked.value = (user.role == "ADMIN")
            showMessage("Aktif sebagai kasir: ${user.name}")
            true
        } else {
            showMessage("PIN Kasir salah!")
            false
        }
    }

    // --- CART OPERATIONS ---
    fun addProductToCart(product: ProductEntity) {
        if (!product.isActive) {
            showMessage("Produk '${product.name}' sedang dinonaktifkan.")
            return
        }
        val s = settings.value
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == product.id }
        val currentQty = if (index >= 0) currentList[index].quantity else 0
        val nextQty = currentQty + 1

        if (s.autoReduceStock && !s.allowNegativeStock && nextQty > product.stock) {
            showMessage("Stok '${product.name}' tidak mencukupi (Tersisa: ${product.stock} ${product.unit}).")
            return
        }

        if (index >= 0) {
            currentList[index] = currentList[index].copy(product = product, quantity = nextQty)
        } else {
            currentList.add(CartItem(product = product, quantity = 1))
        }
        _cartItems.value = currentList
    }

    fun updateCartItemQuantity(productId: Long, newQuantity: Int) {
        if (newQuantity <= 0) {
            removeCartItem(productId)
            return
        }
        val s = settings.value
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            val item = currentList[index]
            val latestProduct = products.value.find { it.id == productId } ?: item.product
            if (s.autoReduceStock && !s.allowNegativeStock && newQuantity > latestProduct.stock) {
                showMessage("Stok '${latestProduct.name}' hanya tersedia ${latestProduct.stock} ${latestProduct.unit}.")
                return
            }
            currentList[index] = item.copy(product = latestProduct, quantity = newQuantity)
            _cartItems.value = currentList
        }
    }

    fun updateCartItemNote(productId: Long, note: String) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            currentList[index] = currentList[index].copy(note = note)
            _cartItems.value = currentList
        }
    }

    fun removeCartItem(productId: Long) {
        _cartItems.value = _cartItems.value.filterNot { it.product.id == productId }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _selectedCustomer.value = null
        _discountInput.value = 0.0
        _serviceFeeInput.value = settings.value.defaultServiceFee
        _transactionNote.value = ""
        _paymentMethod.value = "Tunai"
        _amountPaidInput.value = ""
    }

    fun setCustomer(customer: CustomerEntity?) {
        _selectedCustomer.value = customer
    }

    fun setDiscount(discount: Double) {
        _discountInput.value = discount.coerceAtLeast(0.0)
    }

    fun setServiceFee(fee: Double) {
        _serviceFeeInput.value = fee.coerceAtLeast(0.0)
    }

    fun setTransactionNote(note: String) {
        _transactionNote.value = note
    }

    fun setPaymentMethod(method: String) {
        _paymentMethod.value = method
    }

    fun setAmountPaidInput(input: String) {
        _amountPaidInput.value = input
    }

    fun dismissLastCompletedTransaction() {
        _lastCompletedTransaction.value = null
    }

    fun scanAndAddProductByBarcode(code: String, onNotFound: (String) -> Unit = {}) {
        viewModelScope.launch {
            val product = repository.findProductByBarcodeOrSku(code)
            if (product != null) {
                addProductToCart(product)
                showMessage("Ditambahkan: ${product.name}")
            } else {
                showMessage("Produk dengan barcode/SKU '$code' tidak ditemukan.")
                onNotFound(code)
            }
        }
    }

    fun calculateCartTotals(): CartCalculation {
        val items = _cartItems.value
        val s = settings.value
        val subtotal = items.sumOf { it.subtotal }
        val discount = if (s.enableDiscount) _discountInput.value.coerceIn(0.0, subtotal) else 0.0
        val afterDiscount = (subtotal - discount).coerceAtLeast(0.0)
        val taxPercent = if (s.enableTax) s.defaultTaxPercent.coerceAtLeast(0.0) else 0.0
        val taxAmount = afterDiscount * (taxPercent / 100.0)
        val serviceFee = _serviceFeeInput.value.coerceAtLeast(0.0)
        val rawTotal = afterDiscount + taxAmount + serviceFee
        val rounding = if (s.enableRounding) {
            val rem = rawTotal % 100.0
            if (rem == 0.0) 0.0 else if (rem >= 50.0) 100.0 - rem else -rem
        } else 0.0
        val finalTotal = (rawTotal + rounding).coerceAtLeast(0.0)

        val paidVal = if (_paymentMethod.value.equals("Tunai", ignoreCase = true)) {
            _amountPaidInput.value.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
        } else {
            finalTotal
        }
        val change = (paidVal - finalTotal).coerceAtLeast(0.0)

        return CartCalculation(
            subtotal = subtotal,
            discountAmount = discount,
            taxPercent = taxPercent,
            taxAmount = taxAmount,
            serviceFee = serviceFee,
            roundingAmount = rounding,
            finalTotal = finalTotal,
            amountPaid = paidVal,
            changeAmount = change
        )
    }

    fun submitPayment(context: Context, onSuccess: (TransactionWithItems) -> Unit = {}) {
        viewModelScope.launch {
            val calc = calculateCartTotals()
            val cashier = _activeCashier.value ?: CashierUserEntity(id = 1, name = "Admin Utama")
            val result = repository.processCheckout(
                cartItems = _cartItems.value,
                cashier = cashier,
                customer = _selectedCustomer.value,
                discountAmount = calc.discountAmount,
                taxPercentage = calc.taxPercent,
                serviceFee = calc.serviceFee,
                paymentMethod = _paymentMethod.value,
                amountPaid = calc.amountPaid,
                notes = _transactionNote.value
            )

            result.onSuccess { txWithItems ->
                _lastCompletedTransaction.value = txWithItems
                clearCart()
                showMessage("Transaksi ${txWithItems.transaction.invoiceNumber} berhasil disimpan!")
                onSuccess(txWithItems)

                // Auto print if configured
                val s = settings.value
                if (s.autoPrintReceipt && s.defaultPrinterAddress.isNotBlank()) {
                    printReceiptBluetooth(context, txWithItems)
                }
            }.onFailure { err ->
                showMessage(err.message ?: "Gagal memproses transaksi pembayaran.")
            }
        }
    }

    // --- PRODUCT, CATEGORY, STOCK, CUSTOMER, USER CRUD ---
    fun saveCategory(category: CategoryEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveCategory(category)
                .onSuccess {
                    showMessage("Kategori '${category.name}' berhasil disimpan.")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Gagal menyimpan kategori.") }
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
                .onSuccess { showMessage("Kategori '${category.name}' dihapus.") }
                .onFailure { showMessage(it.message ?: "Gagal menghapus kategori.") }
        }
    }

    fun saveProduct(product: ProductEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val cashierName = _activeCashier.value?.name ?: "Admin"
            repository.saveProduct(product, cashierName)
                .onSuccess {
                    showMessage("Produk '${product.name}' berhasil disimpan.")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Gagal menyimpan produk.") }
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
                .onSuccess { showMessage("Produk '${product.name}' telah dihapus.") }
                .onFailure { showMessage(it.message ?: "Gagal menghapus produk.") }
        }
    }

    fun adjustStock(
        productId: Long,
        type: String,
        quantityInput: Int,
        note: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val userName = _activeCashier.value?.name ?: "Admin"
            repository.adjustStock(productId, type, quantityInput, note, userName)
                .onSuccess {
                    showMessage("Perubahan stok berhasil disimpan.")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Gagal mengubah stok.") }
        }
    }

    fun saveCustomer(customer: CustomerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveCustomer(customer)
                .onSuccess {
                    showMessage("Pelanggan '${customer.name}' berhasil disimpan.")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Gagal menyimpan pelanggan.") }
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
                .onSuccess { showMessage("Data pelanggan '${customer.name}' dihapus.") }
                .onFailure { showMessage(it.message ?: "Gagal menghapus pelanggan.") }
        }
    }

    fun saveCashierUser(user: CashierUserEntity, rawPin: String?, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveUser(user, rawPin)
                .onSuccess {
                    showMessage("Data kasir '${user.name}' berhasil disimpan.")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Gagal menyimpan data kasir.") }
        }
    }

    fun deleteCashierUser(user: CashierUserEntity) {
        viewModelScope.launch {
            repository.deleteUser(user)
                .onSuccess { showMessage("Kasir '${user.name}' dihapus.") }
                .onFailure { showMessage(it.message ?: "Gagal menghapus kasir.") }
        }
    }

    fun saveStoreSettings(newSettings: StoreSettingsEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveSettings(newSettings)
                .onSuccess {
                    showMessage("Pengaturan toko berhasil disimpan.")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Gagal menyimpan pengaturan.") }
        }
    }

    fun cancelTransaction(transactionId: Long, reason: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val cancelledBy = _activeCashier.value?.name ?: "Admin"
            repository.cancelTransaction(transactionId, reason, cancelledBy)
                .onSuccess {
                    showMessage("Transaksi berhasil dibatalkan & stok telah dikembalikan otomatis.")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Gagal membatalkan transaksi.") }
        }
    }

    // --- PRINTER OPERATIONS ---
    fun selectDefaultPrinter(name: String, address: String, paperSizeMm: Int) {
        viewModelScope.launch {
            repository.setDefaultPrinter(name, address, paperSizeMm)
                .onSuccess { showMessage("Printer default diatur ke: $name ($paperSizeMm mm)") }
                .onFailure { showMessage(it.message ?: "Gagal menyimpan printer default.") }
        }
    }

    fun printReceiptBluetooth(
        context: Context,
        txWithItems: TransactionWithItems,
        printerAddressOverride: String? = null
    ) {
        viewModelScope.launch {
            showMessage("Menghubungkan ke printer Bluetooth...")
            val result = BluetoothPrinterService.printReceiptToBluetooth(
                context = context,
                txWithItems = txWithItems,
                settings = settings.value,
                printerAddressOverride = printerAddressOverride
            )
            result.onSuccess { msg -> showMessage(msg) }
                .onFailure { err -> showMessage(err.message ?: "Gagal mencetak struk ke printer Bluetooth.") }
        }
    }

    fun testPrintBluetooth(context: Context, printerName: String, printerAddress: String) {
        viewModelScope.launch {
            showMessage("Mengirim tes cetak ke $printerName...")
            BluetoothPrinterService.testPrintBluetooth(
                context = context,
                settings = settings.value,
                printerAddress = printerAddress,
                printerName = printerName
            ).onSuccess { showMessage(it) }
                .onFailure { showMessage(it.message ?: "Tes cetak gagal.") }
        }
    }

    fun shareReceiptText(context: Context, txWithItems: TransactionWithItems) {
        val text = BluetoothPrinterService.formatReceiptText(txWithItems, settings.value)
        BackupAndExportService.sharePlainText(
            context = context,
            text = text,
            subject = "Struk ${settings.value.storeName} - ${txWithItems.transaction.invoiceNumber}"
        )
    }

    fun shareOrSaveReceiptPdf(
        context: Context,
        txWithItems: TransactionWithItems,
        onGeneratedFile: (File) -> Unit = {}
    ) {
        viewModelScope.launch {
            BackupAndExportService.generateReceiptPdfFile(context, txWithItems, settings.value)
                .onSuccess { pdfFile ->
                    onGeneratedFile(pdfFile)
                    BackupAndExportService.shareFile(
                        context = context,
                        file = pdfFile,
                        mimeType = "application/pdf",
                        chooserTitle = "Simpan / Bagikan Struk PDF"
                    )
                }
                .onFailure { showMessage(it.message ?: "Gagal membuat file PDF struk.") }
        }
    }

    fun saveReceiptPdfToUri(context: Context, txWithItems: TransactionWithItems, targetUri: Uri) {
        viewModelScope.launch {
            BackupAndExportService.generateReceiptPdfFile(context, txWithItems, settings.value)
                .onSuccess { pdfFile ->
                    runCatching {
                        context.contentResolver.openOutputStream(targetUri)?.use { out ->
                            pdfFile.inputStream().use { input -> input.copyTo(out) }
                        } ?: throw IllegalStateException("Gagal menulis ke lokasi file.")
                    }.onSuccess {
                        showMessage("Struk PDF berhasil disimpan ke penyimpanan HP!")
                    }.onFailure {
                        showMessage(it.message ?: "Gagal menyimpan file PDF struk.")
                    }
                }
                .onFailure { showMessage(it.message ?: "Gagal membuat PDF struk.") }
        }
    }

    // --- BACKUP, RESTORE, EXPORT, IMPORT ---
    fun performBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val dao = repository.getDao()
            BackupAndExportService.writeBackupToUri(context, uri, dao)
                .onSuccess { showMessage(it) }
                .onFailure { showMessage(it.message ?: "Gagal membuat backup database.") }
        }
    }

    fun performRestoreFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val dao = repository.getDao()
            BackupAndExportService.restoreFromUri(context, uri, dao)
                .onSuccess { showMessage(it) }
                .onFailure { showMessage(it.message ?: "Restore gagal. Data lama tetap aman.") }
        }
    }

    fun exportProductsCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            val dao = repository.getDao()
            BackupAndExportService.exportProductsCsvToUri(context, uri, dao)
                .onSuccess { showMessage(it) }
                .onFailure { showMessage(it.message ?: "Gagal export produk ke CSV.") }
        }
    }

    fun importProductsCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            val dao = repository.getDao()
            BackupAndExportService.importProductsCsvFromUri(context, uri, dao)
                .onSuccess { showMessage(it) }
                .onFailure { showMessage(it.message ?: "Gagal import produk dari CSV.") }
        }
    }

    fun exportReportCsvToUri(
        context: Context,
        uri: Uri,
        reportTitle: String,
        periodLabel: String,
        filteredTransactions: List<TransactionWithItems>
    ) {
        viewModelScope.launch {
            runCatching {
                val csv = BackupAndExportService.buildReportCsv(
                    reportTitle = reportTitle,
                    periodLabel = periodLabel,
                    transactions = filteredTransactions,
                    products = products.value
                )
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("Gagal menulis file CSV laporan.")
            }.onSuccess {
                showMessage("Laporan CSV berhasil disimpan!")
            }.onFailure {
                showMessage(it.message ?: "Gagal mengekspor laporan CSV.")
            }
        }
    }

    fun exportAndShareReportPdf(
        context: Context,
        periodLabel: String,
        filteredTransactions: List<TransactionWithItems>
    ) {
        viewModelScope.launch {
            BackupAndExportService.generateReportPdfFile(
                context = context,
                storeName = settings.value.storeName,
                periodLabel = periodLabel,
                transactions = filteredTransactions,
                products = products.value
            ).onSuccess { file ->
                BackupAndExportService.shareFile(
                    context = context,
                    file = file,
                    mimeType = "application/pdf",
                    chooserTitle = "Simpan / Bagikan Laporan PDF"
                )
            }.onFailure {
                showMessage(it.message ?: "Gagal membuat file PDF laporan.")
            }
        }
    }
}

data class CartCalculation(
    val subtotal: Double,
    val discountAmount: Double,
    val taxPercent: Double,
    val taxAmount: Double,
    val serviceFee: Double,
    val roundingAmount: Double,
    val finalTotal: Double,
    val amountPaid: Double,
    val changeAmount: Double
)
