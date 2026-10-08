package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CartItem
import com.example.data.CashierUserEntity
import com.example.data.CategoryEntity
import com.example.data.CustomerEntity
import com.example.data.DebtPaymentEntity
import com.example.data.KasirDatabase
import com.example.data.KasirRepository
import com.example.data.PaymentMethodEntity
import com.example.data.ProductEntity
import com.example.data.StoreSettingsEntity
import com.example.data.TransactionEntity
import com.example.data.TransactionItemEntity
import com.example.data.TransactionWithItems
import com.example.service.BackupAndExportService
import com.example.service.BluetoothPrinterService
import com.example.util.SecurityAndFormatUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen(val title: String) {
    DASHBOARD("KasirKu"),
    POS("Pesan Menu"),
    PAYMENT("Pembayaran Billing"),
    PRODUCTS("Edit Menu dan Harga"),
    CATEGORIES("Kategori Menu"),
    HISTORY("Riwayat Transaksi"),
    REPORTS("Laporan Penjualan"),
    CUSTOMERS("Pelanggan"),
    SETTINGS("Pengaturan")
}

class KasirViewModel(
    application: Application,
    private val repository: KasirRepository
) : AndroidViewModel(application) {

    // --- Navigation State ---
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val screenBackStack = ArrayDeque<AppScreen>()

    fun navigateTo(screen: AppScreen) {
        if (_currentScreen.value != screen) {
            screenBackStack.addLast(_currentScreen.value)
            _currentScreen.value = screen
            if (screen == AppScreen.POS && _editingTransactionId.value == null) {
                viewModelScope.launch {
                    refreshNextBillingNumber()
                }
            }
        }
    }

    fun navigateBack(): Boolean {
        return if (screenBackStack.isNotEmpty()) {
            _currentScreen.value = screenBackStack.removeLast()
            if (_currentScreen.value == AppScreen.POS && _editingTransactionId.value == null) {
                viewModelScope.launch {
                    refreshNextBillingNumber()
                }
            }
            true
        } else if (_currentScreen.value != AppScreen.DASHBOARD) {
            _currentScreen.value = AppScreen.DASHBOARD
            true
        } else {
            false
        }
    }

    // --- UI Feedback Events (Snackbar / Toast) ---
    private val _uiMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    fun emitMessage(msg: String) {
        _uiMessage.tryEmit(msg)
    }

    // --- Database Streams ---
    val categories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paymentMethods: StateFlow<List<PaymentMethodEntity>> = repository.allPaymentMethods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<TransactionWithItems>> = repository.allTransactionsWithItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unpaidBillings: StateFlow<List<TransactionWithItems>> = repository.unpaidTransactionsWithItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debtPayments: StateFlow<List<DebtPaymentEntity>> = repository.allDebtPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashierUsers: StateFlow<List<CashierUserEntity>> = repository.allCashierUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val storeSettings: StateFlow<StoreSettingsEntity> = repository.storeSettings
        .combine(MutableStateFlow(StoreSettingsEntity())) { dbSettings, fallback ->
            dbSettings ?: fallback
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreSettingsEntity())

    // --- Active Cashier & PIN Lock ---
    private val _activeCashier = MutableStateFlow<CashierUserEntity?>(null)
    val activeCashier: StateFlow<CashierUserEntity?> = _activeCashier.asStateFlow()

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private var lockEvaluatedOnStartup = false

    // --- Active Ordering Billing State (PROSES PESAN) ---
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _activeBillingNumber = MutableStateFlow(1)
    val activeBillingNumber: StateFlow<Int> = _activeBillingNumber.asStateFlow()

    // If cashier selects an existing UNPAID billing to edit/add items before sending to kitchen again
    private val _editingTransactionId = MutableStateFlow<Long?>(null)
    val editingTransactionId: StateFlow<Long?> = _editingTransactionId.asStateFlow()

    private val _selectedOrderCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedOrderCustomer: StateFlow<CustomerEntity?> = _selectedOrderCustomer.asStateFlow()

    private val _orderNotes = MutableStateFlow("")
    val orderNotes: StateFlow<String> = _orderNotes.asStateFlow()

    // Kitchen ticket modal shown right after "KIRIM PESANAN"
    private val _lastSentKitchenOrder = MutableStateFlow<TransactionWithItems?>(null)
    val lastSentKitchenOrder: StateFlow<TransactionWithItems?> = _lastSentKitchenOrder.asStateFlow()

    // --- Active Payment State (PROSES BAYAR) ---
    private val _selectedBillingForPayment = MutableStateFlow<TransactionWithItems?>(null)
    val selectedBillingForPayment: StateFlow<TransactionWithItems?> = _selectedBillingForPayment.asStateFlow()

    // Receipt modal shown right after "BAYAR & SELESAIKAN" or from History
    private val _lastCompletedTransaction = MutableStateFlow<TransactionWithItems?>(null)
    val lastCompletedTransaction: StateFlow<TransactionWithItems?> = _lastCompletedTransaction.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureInitialData()
            refreshNextBillingNumber()
        }
        viewModelScope.launch {
            cashierUsers.collect { users ->
                if (_activeCashier.value == null && users.isNotEmpty()) {
                    _activeCashier.value = users.firstOrNull { it.isActive } ?: users.first()
                } else if (_activeCashier.value != null) {
                    val refreshed = users.find { it.id == _activeCashier.value?.id }
                    if (refreshed != null) _activeCashier.value = refreshed
                }
            }
        }
        viewModelScope.launch {
            storeSettings.collect { settings ->
                if (!lockEvaluatedOnStartup && settings.storeName.isNotEmpty()) {
                    lockEvaluatedOnStartup = true
                    if (settings.requirePinOnStartup) {
                        _isAppLocked.value = true
                    }
                }
                if (_editingTransactionId.value == null) {
                    val nextNum = repository.getNextBillingNumberForToday()
                    if (nextNum > _activeBillingNumber.value || _activeBillingNumber.value < 1) {
                        _activeBillingNumber.value = nextNum
                    }
                }
            }
        }
        viewModelScope.launch {
            allTransactions.collect {
                if (_editingTransactionId.value == null) {
                    val nextNum = repository.getNextBillingNumberForToday()
                    _activeBillingNumber.value = nextNum
                }
            }
        }
        viewModelScope.launch {
            unpaidBillings.collect { list ->
                val currentSel = _selectedBillingForPayment.value
                if (currentSel != null) {
                    val updated = list.find { it.transaction.id == currentSel.transaction.id }
                    _selectedBillingForPayment.value = updated
                }
            }
        }
    }

    suspend fun refreshNextBillingNumber() {
        val nextNum = repository.getNextBillingNumberForToday()
        if (_editingTransactionId.value == null) {
            _activeBillingNumber.value = nextNum
        }
    }

    // --- PIN & Cashier Switching ---
    fun unlockAppWithPin(pin: String): Boolean {
        val hash = SecurityAndFormatUtils.hashPin(pin)
        val matchedUser = cashierUsers.value.firstOrNull { it.isActive && it.pinHash == hash }
        return if (matchedUser != null) {
            _activeCashier.value = matchedUser
            _isAppLocked.value = false
            emitMessage("Selamat bertugas, ${matchedUser.name}")
            true
        } else {
            emitMessage("PIN salah! Coba lagi (Default Admin: 1234)")
            false
        }
    }

    fun switchCashier(user: CashierUserEntity, pin: String): Boolean {
        val hash = SecurityAndFormatUtils.hashPin(pin)
        return if (user.pinHash == hash) {
            _activeCashier.value = user
            emitMessage("Kasir aktif: ${user.name} (${user.role})")
            true
        } else {
            emitMessage("PIN untuk ${user.name} tidak sesuai")
            false
        }
    }

    fun lockApp() {
        _isAppLocked.value = true
    }

    // --- PAYMENT METHODS MANAGEMENT (NO ON/OFF SWITCHES; MANUAL ADD/EDIT/DELETE) ---
    fun addPaymentMethod(name: String, isCashType: Boolean = false) {
        val cleanName = name.trim().uppercase()
        if (cleanName.isBlank()) {
            emitMessage("Nama metode pembayaran tidak boleh kosong")
            return
        }
        if (paymentMethods.value.any { it.name.equals(cleanName, ignoreCase = true) }) {
            emitMessage("Metode pembayaran \"$cleanName\" sudah ada")
            return
        }
        viewModelScope.launch {
            repository.addPaymentMethod(cleanName, isCashType)
            emitMessage("Metode pembayaran \"$cleanName\" ditambahkan")
        }
    }

    fun updatePaymentMethod(method: PaymentMethodEntity, newName: String, isCashType: Boolean = method.isCashType) {
        val cleanName = newName.trim().uppercase()
        if (cleanName.isBlank()) {
            emitMessage("Nama metode pembayaran tidak boleh kosong")
            return
        }
        if (paymentMethods.value.any { it.id != method.id && it.name.equals(cleanName, ignoreCase = true) }) {
            emitMessage("Metode pembayaran \"$cleanName\" sudah digunakan")
            return
        }
        viewModelScope.launch {
            repository.updatePaymentMethod(method, cleanName, isCashType)
            emitMessage("Metode pembayaran diubah menjadi \"$cleanName\"")
        }
    }

    fun deletePaymentMethod(method: PaymentMethodEntity) {
        if (paymentMethods.value.size <= 1) {
            emitMessage("Minimal harus ada 1 metode pembayaran yang tersimpan")
            return
        }
        viewModelScope.launch {
            repository.deletePaymentMethod(method)
            emitMessage("Metode \"${method.name}\" dihapus (riwayat transaksi lama tetap aman)")
        }
    }

    // --- PROSES PESAN (CART & BILLING CREATION) ---
    fun addToCart(product: ProductEntity) {
        if (!product.isActive) {
            emitMessage("Menu ${product.name} sedang nonaktif")
            return
        }
        val currentList = _cartItems.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.product.id == product.id }

        if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            val newQty = existing.quantity + 1
            val updatedNotes = SecurityAndFormatUtils.normalizePortionNotes(existing.portionNotes, newQty)
            currentList[existingIndex] = existing.copy(
                quantity = newQty,
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
            removeFromCart(productId)
            return
        }
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            val item = currentList[index]
            val updatedNotes = SecurityAndFormatUtils.normalizePortionNotes(item.portionNotes, newQuantity)
            currentList[index] = item.copy(
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
            val normalized = SecurityAndFormatUtils.normalizePortionNotes(portionNotes, item.quantity)
            currentList[index] = item.copy(portionNotes = normalized)
            _cartItems.value = currentList
        }
    }

    fun updateCartItemSinglePortionNote(productId: Long, portionIndex: Int, note: String) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            val item = currentList[index]
            val mutableNotes = SecurityAndFormatUtils.normalizePortionNotes(item.portionNotes, item.quantity).toMutableList()
            if (portionIndex in mutableNotes.indices) {
                mutableNotes[portionIndex] = note.trim()
                currentList[index] = item.copy(portionNotes = mutableNotes)
                _cartItems.value = currentList
            }
        }
    }

    fun removeFromCart(productId: Long) {
        _cartItems.value = _cartItems.value.filterNot { it.product.id == productId }
    }

    fun clearOrderDraft() {
        _cartItems.value = emptyList()
        _editingTransactionId.value = null
        _selectedOrderCustomer.value = null
        _orderNotes.value = ""
        viewModelScope.launch {
            refreshNextBillingNumber()
        }
    }

    fun startNewBilling() {
        _cartItems.value = emptyList()
        _editingTransactionId.value = null
        _selectedOrderCustomer.value = null
        _orderNotes.value = ""
        viewModelScope.launch {
            val nextNum = repository.getNextBillingNumberForToday()
            _activeBillingNumber.value = nextNum
            emitMessage("Siap membuat Billing $nextNum")
        }
    }

    fun loadUnpaidBillingToEdit(txWithItems: TransactionWithItems) {
        val tx = txWithItems.transaction
        val allProds = products.value
        val reconstructedCart = txWithItems.items.map { item ->
            val matchedProd = allProds.find { it.id == item.productId } ?: ProductEntity(
                id = item.productId,
                name = item.productName,
                sellPrice = item.sellPrice,
                unit = item.unit,
                categoryId = 1,
                categoryName = "Menu"
            )
            CartItem(
                product = matchedProd,
                quantity = item.quantity,
                portionNotes = item.portionNotes
            )
        }
        _editingTransactionId.value = tx.id
        _activeBillingNumber.value = tx.billingNumber
        _cartItems.value = reconstructedCart
        _orderNotes.value = tx.notes
        _selectedOrderCustomer.value = customers.value.find { it.id == tx.customerId }
        _currentScreen.value = AppScreen.POS
        emitMessage("Mengubah ${tx.billingDisplay}")
    }

    fun setOrderCustomer(customer: CustomerEntity?) {
        _selectedOrderCustomer.value = customer
    }

    fun setOrderNotes(notes: String) {
        _orderNotes.value = notes
    }

    // --- KIRIM PESANAN KE DAPUR (SAVES AS UNPAID, DOES NOT OPEN PAYMENT) ---
    fun sendOrderToKitchen(context: Context) {
        val items = _cartItems.value
        if (items.isEmpty()) {
            emitMessage("Pilih makanan atau minuman terlebih dahulu")
            return
        }

        viewModelScope.launch {
            val settings = storeSettings.value
            val cashier = _activeCashier.value
            val customer = _selectedOrderCustomer.value
            val now = System.currentTimeMillis()
            val todayKey = SecurityAndFormatUtils.formatDateKey(now)
            val subtotal = items.sumOf { it.subtotal }

            val editingId = _editingTransactionId.value
            val existingUnpaid = if (editingId != null) {
                unpaidBillings.value.find { it.transaction.id == editingId }?.transaction
            } else null

            val requestedBillingNum = if (existingUnpaid != null) {
                existingUnpaid.billingNumber
            } else {
                maxOf(_activeBillingNumber.value, repository.getNextBillingNumberForToday(now))
            }

            val invoiceNumber = existingUnpaid?.invoiceNumber
                ?: SecurityAndFormatUtils.generateInvoiceNumber(settings.invoicePrefix, requestedBillingNum, now)

            val txEntities = items.map { cartItem ->
                TransactionItemEntity(
                    transactionId = editingId ?: 0L,
                    productId = cartItem.product.id,
                    productName = cartItem.product.name,
                    sku = cartItem.product.sku,
                    unit = cartItem.product.unit,
                    costPrice = 0.0,
                    sellPrice = cartItem.product.sellPrice,
                    quantity = cartItem.quantity,
                    itemNote = cartItem.encodedItemNote,
                    subtotal = cartItem.subtotal
                )
            }

            val resultTxWithItems = if (existingUnpaid != null) {
                val updatedTx = existingUnpaid.copy(
                    customerId = customer?.id ?: existingUnpaid.customerId,
                    customerName = customer?.name ?: existingUnpaid.customerName,
                    subtotal = subtotal,
                    totalAmount = (subtotal - existingUnpaid.discountAmount + existingUnpaid.taxAmount + existingUnpaid.serviceFee).coerceAtLeast(0.0),
                    notes = _orderNotes.value.trim(),
                    status = "UNPAID"
                )
                repository.updateKitchenOrderBilling(updatedTx, txEntities)
            } else {
                val newTx = TransactionEntity(
                    invoiceNumber = invoiceNumber,
                    billingNumber = requestedBillingNum,
                    billingDate = todayKey,
                    timestamp = now,
                    cashierId = cashier?.id ?: 1L,
                    cashierName = cashier?.name ?: "Admin",
                    customerId = customer?.id,
                    customerName = customer?.name ?: "Pelanggan Umum",
                    subtotal = subtotal,
                    totalAmount = subtotal,
                    paymentMethodId = null,
                    paymentMethod = "BELUM BAYAR",
                    amountPaid = 0.0,
                    changeAmount = 0.0,
                    notes = _orderNotes.value.trim(),
                    status = "UNPAID"
                )
                repository.createKitchenOrderBilling(newTx, txEntities)
            }

            _lastSentKitchenOrder.value = resultTxWithItems

            // Reset order draft and advance strictly to next daily sequential billing number
            _cartItems.value = emptyList()
            _editingTransactionId.value = null
            _selectedOrderCustomer.value = null
            _orderNotes.value = ""

            val nextNumAfterSend = maxOf(
                resultTxWithItems.transaction.billingNumber + 1,
                repository.getNextBillingNumberForToday()
            )
            _activeBillingNumber.value = nextNumAfterSend

            emitMessage("${resultTxWithItems.transaction.billingDisplay} dikirim ke dapur (BELUM BAYAR)")

            if (settings.autoPrintKitchenTicket && settings.defaultPrinterAddress.isNotBlank()) {
                BluetoothPrinterService.printKitchenTicketToBluetooth(
                    context = context,
                    txWithItems = resultTxWithItems,
                    settings = settings
                ).onSuccess { msg ->
                    emitMessage(msg)
                }.onFailure { err ->
                    emitMessage("Auto-print dapur gagal: ${err.message}")
                }
            }
        }
    }

    fun dismissKitchenOrderModal() {
        _lastSentKitchenOrder.value = null
    }

    // --- PROSES BAYAR (SELECT BILLING & COMPLETE PAYMENT) ---
    fun selectBillingForPayment(txWithItems: TransactionWithItems?) {
        _selectedBillingForPayment.value = txWithItems
    }

    fun openBillingInPaymentScreen(txWithItems: TransactionWithItems) {
        _selectedBillingForPayment.value = txWithItems
        navigateTo(AppScreen.PAYMENT)
    }

    fun payBilling(
        context: Context,
        txWithItems: TransactionWithItems,
        paymentMethod: String,
        paymentMethodId: Long? = null,
        amountPaidInput: Double,
        discountType: String = "NOMINAL",
        discountInput: Double = 0.0,
        enableTax: Boolean = false,
        taxPercent: Double = 0.0,
        serviceFee: Double = 0.0,
        notes: String = ""
    ) {
        val tx = txWithItems.transaction
        val subtotal = txWithItems.items.sumOf { it.subtotal }
        val discountAmount = if (discountType == "PERCENT") {
            subtotal * (discountInput.coerceIn(0.0, 100.0) / 100.0)
        } else {
            discountInput.coerceIn(0.0, subtotal)
        }
        val afterDiscount = (subtotal - discountAmount).coerceAtLeast(0.0)
        val effectiveTaxPct = if (enableTax) taxPercent.coerceAtLeast(0.0) else 0.0
        val taxAmount = afterDiscount * (effectiveTaxPct / 100.0)
        val rawTotal = afterDiscount + taxAmount + serviceFee.coerceAtLeast(0.0)

        val settings = storeSettings.value
        val finalTotal = if (settings.enableRounding && settings.roundingMultiple > 1) {
            SecurityAndFormatUtils.roundToNearestMultiple(rawTotal, settings.roundingMultiple)
        } else {
            rawTotal
        }
        val roundingDiff = finalTotal - rawTotal

        val cleanMethodName = paymentMethod.trim().uppercase().ifBlank { "TUNAI" }
        val matchedMethodEntity = paymentMethods.value.find {
            (paymentMethodId != null && it.id == paymentMethodId) ||
                it.name.equals(cleanMethodName, ignoreCase = true)
        }
        val isCashMethod = matchedMethodEntity?.requiresCashInput
            ?: (cleanMethodName == "TUNAI" || cleanMethodName == "CASH")

        val actualPaid = if (isCashMethod) amountPaidInput else finalTotal
        if (isCashMethod && actualPaid < finalTotal) {
            emitMessage("Uang pelanggan kurang dari total bayar (${SecurityAndFormatUtils.formatRupiah(finalTotal)})")
            return
        }
        val change = (actualPaid - finalTotal).coerceAtLeast(0.0)

        viewModelScope.launch {
            val cashier = _activeCashier.value
            val preservedBillingDate = tx.billingDate.ifBlank {
                SecurityAndFormatUtils.formatDateKey(tx.timestamp)
            }
            val updatedTx = tx.copy(
                subtotal = subtotal,
                discountAmount = discountAmount,
                discountType = discountType,
                discountInput = discountInput,
                taxPercentage = effectiveTaxPct,
                taxAmount = taxAmount,
                serviceFee = serviceFee,
                roundingAmount = roundingDiff,
                totalAmount = finalTotal,
                paymentMethodId = matchedMethodEntity?.id ?: paymentMethodId,
                paymentMethod = cleanMethodName,
                amountPaid = actualPaid,
                changeAmount = change,
                notes = notes.trim().ifBlank { tx.notes },
                status = "PAID",
                billingDate = preservedBillingDate,
                cashierId = cashier?.id ?: tx.cashierId,
                cashierName = cashier?.name ?: tx.cashierName,
                timestamp = System.currentTimeMillis()
            )

            val paidTxWithItems = repository.completeBillingPayment(updatedTx)
            _selectedBillingForPayment.value = null
            _lastCompletedTransaction.value = paidTxWithItems

            if (_editingTransactionId.value == tx.id) {
                _editingTransactionId.value = null
                _cartItems.value = emptyList()
            }
            // Refresh next billing number so it stays sequential and never resets to Billing 1 on the same day
            refreshNextBillingNumber()

            emitMessage("${tx.billingDisplay} LUNAS (${cleanMethodName}: ${SecurityAndFormatUtils.formatRupiah(finalTotal)})")

            if (settings.autoPrintReceipt && settings.defaultPrinterAddress.isNotBlank()) {
                BluetoothPrinterService.printReceiptToBluetooth(
                    context = context,
                    txWithItems = paidTxWithItems,
                    settings = settings
                ).onSuccess { msg ->
                    emitMessage(msg)
                }.onFailure { err ->
                    emitMessage("Auto-print struk gagal: ${err.message}")
                }
            }
        }
    }

    fun dismissCompletedTransactionModal() {
        _lastCompletedTransaction.value = null
    }

    fun showTransactionReceiptModal(txWithItems: TransactionWithItems) {
        _lastCompletedTransaction.value = txWithItems
    }

    fun showKitchenTicketModal(txWithItems: TransactionWithItems) {
        _lastSentKitchenOrder.value = txWithItems
    }

    // --- Printing & PDF Sharing Actions ---
    fun printReceiptViaBluetooth(
        context: Context,
        txWithItems: TransactionWithItems,
        printerAddressOverride: String? = null
    ) {
        viewModelScope.launch {
            val result = BluetoothPrinterService.printReceiptToBluetooth(
                context = context,
                txWithItems = txWithItems,
                settings = storeSettings.value,
                printerAddressOverride = printerAddressOverride
            )
            result.onSuccess { emitMessage(it) }
                .onFailure { emitMessage("Gagal cetak Bluetooth: ${it.message}") }
        }
    }

    fun printKitchenTicketViaBluetooth(
        context: Context,
        txWithItems: TransactionWithItems,
        printerAddressOverride: String? = null
    ) {
        viewModelScope.launch {
            val result = BluetoothPrinterService.printKitchenTicketToBluetooth(
                context = context,
                txWithItems = txWithItems,
                settings = storeSettings.value,
                printerAddressOverride = printerAddressOverride
            )
            result.onSuccess { emitMessage(it) }
                .onFailure { emitMessage("Gagal cetak tiket dapur: ${it.message}") }
        }
    }

    fun shareReceiptAsText(context: Context, txWithItems: TransactionWithItems) {
        val text = BluetoothPrinterService.formatReceiptText(txWithItems, storeSettings.value)
        BackupAndExportService.shareReceiptText(
            context = context,
            title = "Struk ${txWithItems.transaction.billingDisplay}",
            receiptText = text
        )
    }

    fun shareKitchenTicketAsText(context: Context, txWithItems: TransactionWithItems) {
        val text = BluetoothPrinterService.formatKitchenTicketText(
            txWithItems,
            storeSettings.value.paperSizeMm
        )
        BackupAndExportService.shareReceiptText(
            context = context,
            title = "Pesanan Dapur ${txWithItems.transaction.billingDisplay}",
            receiptText = text
        )
    }

    fun shareReceiptAsPdf(context: Context, txWithItems: TransactionWithItems) {
        viewModelScope.launch {
            runCatching {
                val pdfFile = BackupAndExportService.generateReceiptPdfFile(
                    context = context,
                    txWithItems = txWithItems,
                    settings = storeSettings.value
                )
                BackupAndExportService.shareFile(
                    context = context,
                    file = pdfFile,
                    mimeType = "application/pdf",
                    chooserTitle = "Bagikan Struk PDF"
                )
            }.onFailure {
                emitMessage("Gagal membuat PDF struk: ${it.message}")
            }
        }
    }

    fun saveReceiptPdfToUri(context: Context, uri: Uri, txWithItems: TransactionWithItems) {
        viewModelScope.launch {
            val res = BackupAndExportService.saveReceiptPdfToUri(
                context = context,
                uri = uri,
                txWithItems = txWithItems,
                settings = storeSettings.value
            )
            res.onSuccess { emitMessage(it) }
                .onFailure { emitMessage("Gagal simpan PDF: ${it.message}") }
        }
    }

    // --- Cancel Billing / Transaction ---
    fun cancelTransaction(txWithItems: TransactionWithItems, reason: String) {
        val cashier = _activeCashier.value
        if (cashier != null && !cashier.canCancelTransaction && cashier.role != "ADMIN") {
            emitMessage("Kasir ${cashier.name} tidak memiliki izin membatalkan transaksi")
            return
        }
        viewModelScope.launch {
            repository.cancelTransaction(
                txWithItems = txWithItems,
                reason = reason.ifBlank { "Dibatalkan oleh kasir" }
            )
            if (_selectedBillingForPayment.value?.transaction?.id == txWithItems.transaction.id) {
                _selectedBillingForPayment.value = null
            }
            refreshNextBillingNumber()
            emitMessage("${txWithItems.transaction.billingDisplay} berhasil dibatalkan")
        }
    }

    // --- Products (Menu Management - NO COST PRICE) ---
    fun saveProduct(
        id: Long = 0L,
        name: String,
        sellPrice: Double,
        unit: String,
        categoryId: Long,
        categoryName: String,
        description: String,
        imageUri: String,
        isActive: Boolean
    ) {
        if (name.isBlank()) {
            emitMessage("Nama menu wajib diisi")
            return
        }
        viewModelScope.launch {
            val product = ProductEntity(
                id = id,
                name = name.trim(),
                costPrice = 0.0,
                sellPrice = sellPrice.coerceAtLeast(0.0),
                unit = unit.trim().ifBlank { "Porsi" },
                categoryId = categoryId,
                categoryName = categoryName.ifBlank { "Makanan" },
                description = description.trim(),
                imageUri = imageUri,
                isActive = isActive
            )
            repository.saveProduct(product)
            emitMessage(if (id == 0L) "Menu ditambahkan" else "Menu diperbarui")
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            _cartItems.value = _cartItems.value.filterNot { it.product.id == product.id }
            emitMessage("Menu ${product.name} dihapus")
        }
    }

    // --- Categories (With Menu Count Check & Reassignment) ---
    fun saveCategory(
        id: Long = 0L,
        name: String,
        description: String,
        colorHex: String,
        iconName: String
    ) {
        if (name.isBlank()) {
            emitMessage("Nama kategori tidak boleh kosong")
            return
        }
        viewModelScope.launch {
            repository.saveCategory(
                CategoryEntity(
                    id = id,
                    name = name.trim(),
                    description = description.trim(),
                    colorHex = colorHex,
                    iconName = iconName
                )
            )
            emitMessage(if (id == 0L) "Kategori ditambahkan" else "Kategori diperbarui")
        }
    }

    fun deleteCategory(
        category: CategoryEntity,
        reassignToCategory: CategoryEntity? = null
    ) {
        viewModelScope.launch {
            repository.deleteCategoryWithReassignment(category, reassignToCategory)
            if (reassignToCategory != null) {
                emitMessage("Kategori ${category.name} dihapus & menu dipindahkan ke ${reassignToCategory.name}")
            } else {
                emitMessage("Kategori ${category.name} dihapus")
            }
        }
    }

    fun moveCategoryProducts(fromCategory: CategoryEntity, toCategory: CategoryEntity) {
        viewModelScope.launch {
            repository.reassignProductsCategory(fromCategory.id, toCategory.id, toCategory.name)
            emitMessage("Semua menu dari '${fromCategory.name}' dipindahkan ke '${toCategory.name}'")
        }
    }

    // --- Customers & Debt ---
    fun saveCustomer(
        id: Long = 0L,
        name: String,
        phone: String,
        address: String,
        notes: String,
        existingDebt: Double = 0.0
    ) {
        if (name.isBlank()) {
            emitMessage("Nama pelanggan wajib diisi")
            return
        }
        viewModelScope.launch {
            repository.saveCustomer(
                CustomerEntity(
                    id = id,
                    name = name.trim(),
                    phone = phone.trim(),
                    address = address.trim(),
                    notes = notes.trim(),
                    totalDebt = existingDebt
                )
            )
            emitMessage(if (id == 0L) "Pelanggan ditambahkan" else "Data pelanggan diperbarui")
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
            if (_selectedOrderCustomer.value?.id == customer.id) {
                _selectedOrderCustomer.value = null
            }
            emitMessage("Pelanggan ${customer.name} dihapus")
        }
    }

    fun payCustomerDebt(
        customer: CustomerEntity,
        amount: Double,
        paymentMethod: String,
        notes: String
    ) {
        if (amount <= 0) {
            emitMessage("Nominal pembayaran harus lebih dari 0")
            return
        }
        viewModelScope.launch {
            val cashierName = _activeCashier.value?.name ?: "Admin"
            repository.recordDebtPayment(
                customer = customer,
                amount = amount.coerceAtMost(customer.totalDebt),
                paymentMethod = paymentMethod,
                notes = notes.trim(),
                cashierName = cashierName
            )
            emitMessage("Pembayaran piutang ${SecurityAndFormatUtils.formatRupiah(amount)} berhasil dicatat")
        }
    }

    // --- Settings & Users ---
    fun updateStoreSettings(updated: StoreSettingsEntity) {
        viewModelScope.launch {
            repository.saveStoreSettings(updated)
            emitMessage("Pengaturan berhasil disimpan")
        }
    }

    fun saveCashierUser(
        id: Long = 0L,
        name: String,
        role: String,
        rawPin: String,
        existingPinHash: String = "",
        canEditPrice: Boolean = true,
        canGiveDiscount: Boolean = true,
        canCancelTransaction: Boolean = true,
        canViewReports: Boolean = true
    ) {
        if (name.isBlank()) {
            emitMessage("Nama pengguna wajib diisi")
            return
        }
        val finalHash = if (rawPin.isNotBlank()) {
            SecurityAndFormatUtils.hashPin(rawPin)
        } else if (existingPinHash.isNotBlank()) {
            existingPinHash
        } else {
            SecurityAndFormatUtils.hashPin("1234")
        }

        viewModelScope.launch {
            repository.saveCashierUser(
                CashierUserEntity(
                    id = id,
                    name = name.trim(),
                    role = role,
                    pinHash = finalHash,
                    isActive = true,
                    canEditPrice = canEditPrice,
                    canGiveDiscount = canGiveDiscount,
                    canCancelTransaction = canCancelTransaction,
                    canViewReports = canViewReports,
                    canManageStock = false
                )
            )
            emitMessage("Data kasir/admin disimpan")
        }
    }

    fun deleteCashierUser(user: CashierUserEntity) {
        if (cashierUsers.value.size <= 1) {
            emitMessage("Tidak dapat menghapus satu-satunya akun pengguna")
            return
        }
        viewModelScope.launch {
            repository.deleteCashierUser(user)
            emitMessage("Pengguna ${user.name} dihapus")
        }
    }

    // --- Backup, Restore & Export ---
    fun exportDatabaseBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            val snapshot = repository.getDatabaseSnapshot()
            val res = BackupAndExportService.writeBackupToUri(context, uri, snapshot)
            res.onSuccess { emitMessage(it) }
                .onFailure { emitMessage("Gagal backup: ${it.message}") }
        }
    }

    fun restoreDatabaseBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            val res = BackupAndExportService.readBackupFromUri(context, uri)
            res.onSuccess { snapshot ->
                repository.restoreDatabaseSnapshot(snapshot)
                refreshNextBillingNumber()
                emitMessage("Database berhasil dipulihkan (${snapshot.products.size} menu, ${snapshot.transactions.size} transaksi)")
            }.onFailure {
                emitMessage("Gagal restore database: ${it.message}")
            }
        }
    }

    fun exportReportCsvOrExcel(
        context: Context,
        uri: Uri,
        transactions: List<TransactionWithItems>,
        isExcel: Boolean
    ) {
        viewModelScope.launch {
            val res = BackupAndExportService.exportTransactionsToCsvUri(
                context = context,
                uri = uri,
                transactions = transactions,
                isExcelFormat = isExcel
            )
            res.onSuccess { emitMessage(it) }
                .onFailure { emitMessage("Gagal ekspor laporan: ${it.message}") }
        }
    }

    fun exportReportPdf(
        context: Context,
        uri: Uri,
        periodTitle: String,
        transactions: List<TransactionWithItems>
    ) {
        viewModelScope.launch {
            val res = BackupAndExportService.exportSalesReportPdfToUri(
                context = context,
                uri = uri,
                periodTitle = periodTitle,
                storeName = storeSettings.value.storeName,
                transactions = transactions
            )
            res.onSuccess { emitMessage(it) }
                .onFailure { emitMessage("Gagal ekspor PDF: ${it.message}") }
        }
    }

    fun resetTransactionsHistoryOnly() {
        viewModelScope.launch {
            repository.resetTransactionsHistoryOnly()
            _activeBillingNumber.value = 1
            emitMessage("Seluruh riwayat transaksi berhasil direset")
        }
    }

    fun resetAllFactoryData() {
        viewModelScope.launch {
            repository.resetAllFactoryData()
            _cartItems.value = emptyList()
            _editingTransactionId.value = null
            _selectedBillingForPayment.value = null
            _activeBillingNumber.value = 1
            emitMessage("Aplikasi dikembalikan ke pengaturan awal pabrik")
        }
    }
}

class KasirViewModelFactory(
    private val application: Application,
    private val repository: KasirRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KasirViewModel::class.java)) {
            return KasirViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
