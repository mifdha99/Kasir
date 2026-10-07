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
    POS("Pesan Menu"),
    PAYMENT("Pembayaran Billing"),
    PRODUCTS("Daftar Menu"),
    CATEGORIES("Kategori Menu"),
    CUSTOMERS("Data Pelanggan"),
    HISTORY("Riwayat Transaksi"),
    REPORTS("Laporan Penjualan"),
    SETTINGS("Pengaturan Resto")
}

enum class ProductSortOption(val label: String) {
    NAME("Nama (A-Z)"),
    PRICE_ASC("Harga Terendah"),
    PRICE_DESC("Harga Tertinggi"),
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

    val transactionsWithItems: StateFlow<List<TransactionWithItems>> = repository.transactionsWithItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unpaidBillings: StateFlow<List<TransactionWithItems>> = repository.unpaidBillingsWithItems
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

    // --- PROSES PESAN (ACTIVE ORDER BILLING STATE) ---
    private val _editingTransactionId = MutableStateFlow<Long?>(null)
    val editingTransactionId: StateFlow<Long?> = _editingTransactionId.asStateFlow()

    private val _activeBillingNumber = MutableStateFlow(1)
    val activeBillingNumber: StateFlow<Int> = _activeBillingNumber.asStateFlow()

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomer.asStateFlow()

    private val _transactionNote = MutableStateFlow("")
    val transactionNote: StateFlow<String> = _transactionNote.asStateFlow()

    private val _lastSentKitchenOrder = MutableStateFlow<TransactionWithItems?>(null)
    val lastSentKitchenOrder: StateFlow<TransactionWithItems?> = _lastSentKitchenOrder.asStateFlow()

    // --- PROSES BAYAR (SELECTED UNPAID BILLING STATE) ---
    private val _selectedBillingForPayment = MutableStateFlow<TransactionWithItems?>(null)
    val selectedBillingForPayment: StateFlow<TransactionWithItems?> = _selectedBillingForPayment.asStateFlow()

    private val _discountInput = MutableStateFlow(0.0)
    val discountInput: StateFlow<Double> = _discountInput.asStateFlow()

    private val _serviceFeeInput = MutableStateFlow(0.0)
    val serviceFeeInput: StateFlow<Double> = _serviceFeeInput.asStateFlow()

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
            transactionsWithItems.collect { allTx ->
                if (_editingTransactionId.value == null) {
                    val maxBilling = allTx.maxOfOrNull { it.transaction.billingNumber } ?: 0
                    _activeBillingNumber.value = maxBilling + 1
                } else {
                    val existing = allTx.find { it.transaction.id == _editingTransactionId.value }
                    if (existing != null && existing.transaction.status != "UNPAID") {
                        startNewOrderBilling()
                    }
                }
                val selectedPay = _selectedBillingForPayment.value
                if (selectedPay != null) {
                    val refreshed = allTx.find { it.transaction.id == selectedPay.transaction.id }
                    if (refreshed == null || refreshed.transaction.status != "UNPAID") {
                        _selectedBillingForPayment.value = null
                    } else {
                        _selectedBillingForPayment.value = refreshed
                    }
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

    // --- PROSES PESAN: ORDERING & ACTIVE BILLING OPERATIONS ---
    fun startNewOrderBilling() {
        val maxBilling = transactionsWithItems.value.maxOfOrNull { it.transaction.billingNumber } ?: 0
        _editingTransactionId.value = null
        _activeBillingNumber.value = maxBilling + 1
        _cartItems.value = emptyList()
        _selectedCustomer.value = null
        _transactionNote.value = ""
    }

    fun loadUnpaidBillingIntoPos(txWithItems: TransactionWithItems) {
        val tx = txWithItems.transaction
        val allProds = products.value.associateBy { it.id }
        val loadedCart = txWithItems.items.map { item ->
            val prod = allProds[item.productId] ?: ProductEntity(
                id = item.productId,
                name = item.productName,
                buyPrice = item.buyPrice,
                sellPrice = item.sellPrice,
                unit = item.unit,
                categoryName = item.categoryName
            )
            CartItem(
                product = prod,
                quantity = item.quantity,
                portionNotes = item.portionNotes
            )
        }
        _editingTransactionId.value = tx.id
        _activeBillingNumber.value = tx.billingNumber
        _cartItems.value = loadedCart
        _selectedCustomer.value = customers.value.find { it.id == tx.customerId }
        _transactionNote.value = tx.notes
        _currentScreen.value = AppScreen.POS
        showMessage("Memuat ${tx.billingDisplay} untuk tambah/ubah pesanan.")
    }

    fun addProductToCart(product: ProductEntity) {
        if (!product.isActive) {
            showMessage("Menu '${product.name}' sedang dinonaktifkan.")
            return
        }
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            val existing = currentList[index]
            val nextQty = existing.quantity + 1
            val updatedNotes = SecurityAndFormatUtils.normalizePortionNotes(existing.portionNotes, nextQty)
            currentList[index] = existing.copy(
                product = product,
                quantity = nextQty,
                portionNotes = updatedNotes
            )
        } else {
            currentList.add(
                CartItem(
                    product = product,
                    quantity = 1,
                    portionNotes = listOf("")
                )
            )
        }
        _cartItems.value = currentList
    }

    fun updateCartItemQuantity(productId: Long, newQuantity: Int) {
        if (newQuantity <= 0) {
            removeCartItem(productId)
            return
        }
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            val item = currentList[index]
            val latestProduct = products.value.find { it.id == productId } ?: item.product
            val updatedNotes = SecurityAndFormatUtils.normalizePortionNotes(item.portionNotes, newQuantity)
            currentList[index] = item.copy(
                product = latestProduct,
                quantity = newQuantity,
                portionNotes = updatedNotes
            )
            _cartItems.value = currentList
        }
    }

    fun updateCartItemPortionNotes(productId: Long, portionNotes: List<String>) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            val item = currentList[index]
            currentList[index] = item.copy(
                portionNotes = SecurityAndFormatUtils.normalizePortionNotes(portionNotes, item.quantity)
            )
            _cartItems.value = currentList
        }
    }

    fun removeCartItem(productId: Long) {
        _cartItems.value = _cartItems.value.filterNot { it.product.id == productId }
    }

    fun clearCart() {
        startNewOrderBilling()
    }

    fun setCustomer(customer: CustomerEntity?) {
        _selectedCustomer.value = customer
    }

    fun setTransactionNote(note: String) {
        _transactionNote.value = note
    }

    fun dismissLastSentKitchenOrder() {
        _lastSentKitchenOrder.value = null
    }

    fun sendOrderToKitchen(context: Context, onOrderSent: (TransactionWithItems) -> Unit = {}) {
        viewModelScope.launch {
            val cashier = _activeCashier.value ?: CashierUserEntity(id = 1, name = "Admin Resto")
            val result = repository.sendOrderToKitchen(
                existingTransactionId = _editingTransactionId.value,
                billingNumber = _activeBillingNumber.value,
                cartItems = _cartItems.value,
                cashier = cashier,
                customer = _selectedCustomer.value,
                notes = _transactionNote.value
            )

            result.onSuccess { savedOrder ->
                _lastSentKitchenOrder.value = savedOrder
                startNewOrderBilling()
                showMessage("${savedOrder.transaction.billingDisplay} dikirim ke dapur (Status: BELUM BAYAR).")
                onOrderSent(savedOrder)

                val s = settings.value
                if (s.autoPrintKitchenTicket && s.defaultPrinterAddress.isNotBlank()) {
                    printKitchenTicketBluetooth(context, savedOrder)
                }
            }.onFailure { err ->
                showMessage(err.message ?: "Gagal menyimpan pesanan.")
            }
        }
    }

    // --- PROSES BAYAR: PAYMENT OF UNPAID BILLINGS ---
    fun selectBillingForPayment(txWithItems: TransactionWithItems?) {
        _selectedBillingForPayment.value = txWithItems
        _discountInput.value = txWithItems?.transaction?.discountAmount ?: 0.0
        _serviceFeeInput.value = txWithItems?.transaction?.serviceFee ?: settings.value.defaultServiceFee
        _paymentMethod.value = "Tunai"
        _amountPaidInput.value = ""
    }

    fun setDiscount(discount: Double) {
        _discountInput.value = discount.coerceAtLeast(0.0)
    }

    fun setServiceFee(fee: Double) {
        _serviceFeeInput.value = fee.coerceAtLeast(0.0)
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

    fun calculateBillingPaymentTotals(billing: TransactionWithItems?): PaymentCalculation {
        val items = billing?.items ?: emptyList()
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

        val isTunai = _paymentMethod.value.equals("Tunai", ignoreCase = true)
        val paidVal = if (isTunai) {
            _amountPaidInput.value.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
        } else {
            finalTotal
        }
        val change = (paidVal - finalTotal).coerceAtLeast(0.0)
        val shortage = (finalTotal - paidVal).coerceAtLeast(0.0)

        return PaymentCalculation(
            subtotal = subtotal,
            discountAmount = discount,
            taxPercent = taxPercent,
            taxAmount = taxAmount,
            serviceFee = serviceFee,
            roundingAmount = rounding,
            finalTotal = finalTotal,
            amountPaid = paidVal,
            changeAmount = change,
            shortageAmount = shortage,
            isSufficient = !isTunai || paidVal >= finalTotal
        )
    }

    fun completeSelectedBillingPayment(context: Context, onSuccess: (TransactionWithItems) -> Unit = {}) {
        val targetBilling = _selectedBillingForPayment.value
        if (targetBilling == null) {
            showMessage("Pilih Billing yang ingin dibayar terlebih dahulu.")
            return
        }
        viewModelScope.launch {
            val calc = calculateBillingPaymentTotals(targetBilling)
            val cashier = _activeCashier.value ?: CashierUserEntity(id = 1, name = "Admin Resto")
            val result = repository.processBillingPayment(
                transactionId = targetBilling.transaction.id,
                cashier = cashier,
                discountAmount = calc.discountAmount,
                serviceFee = calc.serviceFee,
                paymentMethod = _paymentMethod.value,
                amountPaid = calc.amountPaid
            )

            result.onSuccess { paidTx ->
                _selectedBillingForPayment.value = null
                _lastCompletedTransaction.value = paidTx
                _amountPaidInput.value = ""
                _discountInput.value = 0.0
                showMessage("${paidTx.transaction.billingDisplay} LUNAS!")
                onSuccess(paidTx)

                val s = settings.value
                if (s.autoPrintReceipt && s.defaultPrinterAddress.isNotBlank()) {
                    printReceiptBluetooth(context, paidTx)
                }
            }.onFailure { err ->
                showMessage(err.message ?: "Gagal menyelesaikan pembayaran.")
            }
        }
    }

    // --- PRODUCT, CATEGORY, CUSTOMER, USER CRUD ---
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

    fun moveProductsBetweenCategories(
        fromCategory: CategoryEntity,
        toCategory: CategoryEntity,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.moveProductsBetweenCategories(fromCategory.id, toCategory)
                .onSuccess {
                    showMessage("Semua menu dari '${fromCategory.name}' dipindahkan ke '${toCategory.name}'.")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Gagal memindahkan menu.") }
        }
    }

    fun deleteCategory(
        category: CategoryEntity,
        moveToCategory: CategoryEntity? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.deleteCategoryWithOptionalMove(category, moveToCategory)
                .onSuccess {
                    showMessage("Kategori '${category.name}' berhasil dihapus.")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Gagal menghapus kategori.") }
        }
    }

    fun saveProduct(product: ProductEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveProduct(product)
                .onSuccess {
                    showMessage("Menu '${product.name}' berhasil disimpan.")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Gagal menyimpan menu.") }
        }
    }

    fun moveSingleProductCategory(product: ProductEntity, targetCategory: CategoryEntity) {
        viewModelScope.launch {
            repository.moveSingleProductToCategory(product, targetCategory)
                .onSuccess {
                    showMessage("'${product.name}' dipindahkan ke kategori '${targetCategory.name}'.")
                }
                .onFailure { showMessage(it.message ?: "Gagal memindahkan kategori menu.") }
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
                .onSuccess { showMessage("Menu '${product.name}' telah dihapus.") }
                .onFailure { showMessage(it.message ?: "Gagal menghapus menu.") }
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
                    showMessage("Pengaturan berhasil disimpan.")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Gagal menyimpan pengaturan.") }
        }
    }

    fun cancelTransaction(transactionId: Long, reason: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.cancelTransaction(transactionId, reason)
                .onSuccess {
                    showMessage("Billing berhasil dibatalkan.")
                    onSuccess()
                }
                .onFailure { showMessage(it.message ?: "Gagal membatalkan billing.") }
        }
    }

    // --- PRINTER & TICKET OPERATIONS ---
    fun selectDefaultPrinter(name: String, address: String, paperSizeMm: Int) {
        viewModelScope.launch {
            repository.setDefaultPrinter(name, address, paperSizeMm)
                .onSuccess { showMessage("Printer default diatur ke: $name ($paperSizeMm mm)") }
                .onFailure { showMessage(it.message ?: "Gagal menyimpan printer default.") }
        }
    }

    fun printKitchenTicketBluetooth(
        context: Context,
        txWithItems: TransactionWithItems,
        printerAddressOverride: String? = null
    ) {
        viewModelScope.launch {
            showMessage("Mencetak tiket dapur ${txWithItems.transaction.billingDisplay}...")
            BluetoothPrinterService.printKitchenTicketToBluetooth(
                context = context,
                txWithItems = txWithItems,
                settings = settings.value,
                printerAddressOverride = printerAddressOverride
            ).onSuccess { showMessage(it) }
                .onFailure { showMessage(it.message ?: "Gagal mencetak tiket dapur.") }
        }
    }

    fun shareKitchenTicketText(context: Context, txWithItems: TransactionWithItems) {
        val text = BluetoothPrinterService.formatKitchenTicketText(txWithItems, settings.value.paperSizeMm)
        BackupAndExportService.sharePlainText(
            context = context,
            text = text,
            subject = "Pesanan Dapur - ${txWithItems.transaction.billingDisplay}"
        )
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
            subject = "Struk ${settings.value.storeName} - ${txWithItems.transaction.billingDisplay}"
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
                .onFailure { showMessage(it.message ?: "Gagal export menu ke CSV.") }
        }
    }

    fun importProductsCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            val dao = repository.getDao()
            BackupAndExportService.importProductsCsvFromUri(context, uri, dao)
                .onSuccess { showMessage(it) }
                .onFailure { showMessage(it.message ?: "Gagal import menu dari CSV.") }
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
                    transactions = filteredTransactions
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
                transactions = filteredTransactions
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

data class PaymentCalculation(
    val subtotal: Double,
    val discountAmount: Double,
    val taxPercent: Double,
    val taxAmount: Double,
    val serviceFee: Double,
    val roundingAmount: Double,
    val finalTotal: Double,
    val amountPaid: Double,
    val changeAmount: Double,
    val shortageAmount: Double,
    val isSufficient: Boolean
)
