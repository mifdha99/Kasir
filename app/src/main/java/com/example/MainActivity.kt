package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.TransactionWithItems
import com.example.ui.components.KitchenTicketDialog
import com.example.ui.components.PinLockDialog
import com.example.ui.components.ReceiptPreviewAndActionDialog
import com.example.ui.screens.CategoryScreen
import com.example.ui.screens.CustomerScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PaymentScreen
import com.example.ui.screens.PosScreen
import com.example.ui.screens.ProductScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.KasirKuTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.KasirViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val kasirViewModel: KasirViewModel = viewModel()
            val settings by kasirViewModel.settings.collectAsStateWithLifecycle()

            KasirKuTheme(
                themeMode = settings.themeMode,
                textScale = settings.textScale
            ) {
                KasirKuApp(viewModel = kasirViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasirKuApp(viewModel: KasirViewModel) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val transactions by viewModel.transactionsWithItems.collectAsStateWithLifecycle()
    val unpaidBillings by viewModel.unpaidBillings.collectAsStateWithLifecycle()
    val activeCashier by viewModel.activeCashier.collectAsStateWithLifecycle()
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val isAdminUnlocked by viewModel.isAdminUnlocked.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    // Proses Pesan (Active Order Billing) states
    val activeBillingNumber by viewModel.activeBillingNumber.collectAsStateWithLifecycle()
    val editingTransactionId by viewModel.editingTransactionId.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val lastSentKitchenOrder by viewModel.lastSentKitchenOrder.collectAsStateWithLifecycle()

    // Proses Bayar (Payment of Unpaid Billing) states
    val selectedBillingForPayment by viewModel.selectedBillingForPayment.collectAsStateWithLifecycle()
    val discountInput by viewModel.discountInput.collectAsStateWithLifecycle()
    val serviceFeeInput by viewModel.serviceFeeInput.collectAsStateWithLifecycle()
    val paymentMethod by viewModel.paymentMethod.collectAsStateWithLifecycle()
    val amountPaidInput by viewModel.amountPaidInput.collectAsStateWithLifecycle()
    val lastCompletedTx by viewModel.lastCompletedTransaction.collectAsStateWithLifecycle()

    var inspectedTransaction by remember { mutableStateOf<TransactionWithItems?>(null) }
    var inspectedKitchenOrder by remember { mutableStateOf<TransactionWithItems?>(null) }
    var startupLockChecked by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(settings.requirePinOnStartup) {
        if (!startupLockChecked && settings.requirePinOnStartup) {
            viewModel.lockApp()
            startupLockChecked = true
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    // Handle system Back button on sub-screens
    BackHandler(enabled = currentScreen != AppScreen.DASHBOARD) {
        if (currentScreen == AppScreen.PAYMENT && selectedBillingForPayment != null) {
            viewModel.selectBillingForPayment(null)
        } else {
            viewModel.navigateTo(AppScreen.DASHBOARD)
        }
    }

    if (isAppLocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            PinLockDialog(
                title = "KasirKu Terkunci",
                subtitle = "Masukkan PIN Kasir atau PIN Admin (Default: 1234)",
                canDismiss = false,
                onVerifyPin = { pin -> viewModel.unlockAppWithPin(pin) }
            )
        }
        return
    }

    // Show Kitchen Ticket Dialog when an order is sent to kitchen or inspected
    val activeKitchenTx = lastSentKitchenOrder ?: inspectedKitchenOrder
    if (activeKitchenTx != null) {
        KitchenTicketDialog(
            txWithItems = activeKitchenTx,
            settings = settings,
            onDismiss = {
                viewModel.dismissLastSentKitchenOrder()
                inspectedKitchenOrder = null
            },
            onPrintKitchenBluetooth = { overrideAddr ->
                viewModel.printKitchenTicketBluetooth(context, activeKitchenTx, overrideAddr)
            },
            onShareKitchenText = {
                viewModel.shareKitchenTicketText(context, activeKitchenTx)
            },
            onSelectDefaultPrinter = { name, addr, paper ->
                viewModel.selectDefaultPrinter(name, addr, paper)
            }
        )
    }

    // Show Receipt Dialog after payment or when viewing paid transaction detail
    val activeReceiptTx = lastCompletedTx ?: inspectedTransaction
    if (activeReceiptTx != null) {
        ReceiptPreviewAndActionDialog(
            txWithItems = activeReceiptTx,
            settings = settings,
            isNewCheckout = (lastCompletedTx != null),
            onDismiss = {
                viewModel.dismissLastCompletedTransaction()
                inspectedTransaction = null
            },
            onPrintBluetooth = { overrideAddr ->
                viewModel.printReceiptBluetooth(context, activeReceiptTx, overrideAddr)
            },
            onShareText = {
                viewModel.shareReceiptText(context, activeReceiptTx)
            },
            onSharePdf = {
                viewModel.shareOrSaveReceiptPdf(context, activeReceiptTx)
            },
            onSavePdfToUri = { uri ->
                viewModel.saveReceiptPdfToUri(context, activeReceiptTx, uri)
            },
            onSelectDefaultPrinter = { name, addr, paper ->
                viewModel.selectDefaultPrinter(name, addr, paper)
            }
        )
    }

    val paymentCalculation = remember(
        selectedBillingForPayment,
        discountInput,
        serviceFeeInput,
        paymentMethod,
        amountPaidInput,
        settings
    ) {
        viewModel.calculateBillingPaymentTotals(selectedBillingForPayment)
    }

    val bottomNavItems = remember {
        listOf(
            Triple(AppScreen.DASHBOARD, "Beranda", Icons.Default.Home),
            Triple(AppScreen.POS, "Pesan", Icons.Default.Restaurant),
            Triple(AppScreen.PAYMENT, "Bayar", Icons.Default.Payments),
            Triple(AppScreen.PRODUCTS, "Menu", Icons.Default.RestaurantMenu),
            Triple(AppScreen.HISTORY, "Riwayat", Icons.Default.History)
        )
    }

    Scaffold(
        topBar = {
            if (currentScreen != AppScreen.DASHBOARD) {
                TopAppBar(
                    title = {
                        Text(
                            text = currentScreen.title,
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (currentScreen == AppScreen.PAYMENT && selectedBillingForPayment != null) {
                                    viewModel.selectBillingForPayment(null)
                                } else {
                                    viewModel.navigateTo(AppScreen.DASHBOARD)
                                }
                            },
                            modifier = Modifier.testTag("btn_back_to_dashboard")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali"
                            )
                        }
                    },
                    actions = {
                        if (currentScreen != AppScreen.POS) {
                            IconButton(onClick = { viewModel.navigateTo(AppScreen.POS) }) {
                                Icon(
                                    imageVector = Icons.Default.Restaurant,
                                    contentDescription = "Pesan Menu"
                                )
                            }
                        }
                        if (currentScreen != AppScreen.PAYMENT) {
                            IconButton(onClick = { viewModel.navigateTo(AppScreen.PAYMENT) }) {
                                BadgedBox(
                                    badge = {
                                        if (unpaidBillings.isNotEmpty()) {
                                            Badge { Text("${unpaidBillings.size}") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = "Pembayaran Billing"
                                    )
                                }
                            }
                        }
                        if (currentScreen != AppScreen.SETTINGS) {
                            IconButton(onClick = { viewModel.navigateTo(AppScreen.SETTINGS) }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Pengaturan"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                bottomNavItems.forEach { (screen, label, icon) ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { viewModel.navigateTo(screen) },
                        icon = {
                            if (screen == AppScreen.PAYMENT && unpaidBillings.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text("${unpaidBillings.size}") }
                                    }
                                ) {
                                    Icon(icon, contentDescription = label)
                                }
                            } else {
                                Icon(icon, contentDescription = label)
                            }
                        },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("bottom_nav_${screen.name.lowercase()}")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.DASHBOARD -> {
                    DashboardScreen(
                        settings = settings,
                        activeCashier = activeCashier,
                        allUsers = users,
                        products = products,
                        transactions = transactions,
                        unpaidBillings = unpaidBillings,
                        onNavigate = { viewModel.navigateTo(it) },
                        onSwitchCashier = { user, pin -> viewModel.switchCashier(user, pin) },
                        onLockApp = { viewModel.lockApp() },
                        onSelectTransaction = { inspectedTransaction = it },
                        onSelectBillingToPay = { billing ->
                            viewModel.selectBillingForPayment(billing)
                            viewModel.navigateTo(AppScreen.PAYMENT)
                        }
                    )
                }

                AppScreen.POS -> {
                    PosScreen(
                        products = products,
                        categories = categories,
                        settings = settings,
                        activeBillingNumber = activeBillingNumber,
                        editingTransactionId = editingTransactionId,
                        unpaidBillings = unpaidBillings,
                        cartItems = cartItems,
                        onAddToCart = { viewModel.addProductToCart(it) },
                        onUpdateQuantity = { id, q -> viewModel.updateCartItemQuantity(id, q) },
                        onUpdatePortionNotes = { id, notes -> viewModel.updateCartItemPortionNotes(id, notes) },
                        onRemoveFromCart = { viewModel.removeCartItem(it) },
                        onClearCart = { viewModel.clearCart() },
                        onStartNewBilling = { viewModel.startNewOrderBilling() },
                        onLoadUnpaidBilling = { viewModel.loadUnpaidBillingIntoPos(it) },
                        onSendOrderToKitchen = { viewModel.sendOrderToKitchen(context) },
                        onToggleViewMode = { mode ->
                            viewModel.saveStoreSettings(settings.copy(productViewMode = mode))
                        }
                    )
                }

                AppScreen.PAYMENT -> {
                    PaymentScreen(
                        unpaidBillings = unpaidBillings,
                        selectedBilling = selectedBillingForPayment,
                        settings = settings,
                        canGiveDiscount = activeCashier?.canGiveDiscount ?: true,
                        discountInput = discountInput,
                        paymentMethod = paymentMethod,
                        amountPaidInput = amountPaidInput,
                        paymentCalculation = paymentCalculation,
                        onSelectBilling = { viewModel.selectBillingForPayment(it) },
                        onAddMoreItemsToBilling = { viewModel.loadUnpaidBillingIntoPos(it) },
                        onViewKitchenTicket = { inspectedKitchenOrder = it },
                        onSetDiscount = { viewModel.setDiscount(it) },
                        onSetPaymentMethod = { viewModel.setPaymentMethod(it) },
                        onSetAmountPaidInput = { viewModel.setAmountPaidInput(it) },
                        onCompletePayment = { viewModel.completeSelectedBillingPayment(context) },
                        onGoToNewOrder = {
                            viewModel.startNewOrderBilling()
                            viewModel.navigateTo(AppScreen.POS)
                        }
                    )
                }

                AppScreen.PRODUCTS -> {
                    ProductScreen(
                        products = products,
                        categories = categories,
                        onSaveProduct = { viewModel.saveProduct(it) },
                        onMoveProductCategory = { prod, cat ->
                            viewModel.moveSingleProductCategory(prod, cat)
                        },
                        onDeleteProduct = { viewModel.deleteProduct(it) }
                    )
                }

                AppScreen.CATEGORIES -> {
                    CategoryScreen(
                        categories = categories,
                        products = products,
                        onSaveCategory = { viewModel.saveCategory(it) },
                        onMoveCategoryProducts = { fromCat, toCat ->
                            viewModel.moveProductsBetweenCategories(fromCat, toCat)
                        },
                        onDeleteCategory = { targetCat, moveTarget ->
                            viewModel.deleteCategory(targetCat, moveTarget)
                        }
                    )
                }

                AppScreen.CUSTOMERS -> {
                    CustomerScreen(
                        customers = customers,
                        onSaveCustomer = { viewModel.saveCustomer(it) },
                        onDeleteCustomer = { viewModel.deleteCustomer(it) }
                    )
                }

                AppScreen.HISTORY -> {
                    HistoryScreen(
                        transactions = transactions,
                        activeCashier = activeCashier,
                        onOpenReceiptModal = { inspectedTransaction = it },
                        onOpenKitchenModal = { inspectedKitchenOrder = it },
                        onPayUnpaidBilling = { billing ->
                            viewModel.selectBillingForPayment(billing)
                            viewModel.navigateTo(AppScreen.PAYMENT)
                        },
                        onShareReceiptText = { viewModel.shareReceiptText(context, it) },
                        onCancelTransaction = { txId, reason ->
                            viewModel.cancelTransaction(txId, reason)
                        }
                    )
                }

                AppScreen.REPORTS -> {
                    ReportScreen(
                        transactions = transactions,
                        onExportCsv = { uri, title, periodLabel, list ->
                            viewModel.exportReportCsvToUri(context, uri, title, periodLabel, list)
                        },
                        onExportPdf = { periodLabel, list ->
                            viewModel.exportAndShareReportPdf(context, periodLabel, list)
                        }
                    )
                }

                AppScreen.SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        users = users,
                        isAdminUnlocked = isAdminUnlocked,
                        onVerifyAdminPin = { viewModel.verifyAdminPin(it) },
                        onSaveSettings = { viewModel.saveStoreSettings(it) },
                        onSaveCashierUser = { u, pin -> viewModel.saveCashierUser(u, pin) },
                        onDeleteCashierUser = { viewModel.deleteCashierUser(it) },
                        onSelectDefaultPrinter = { name, addr, paper ->
                            viewModel.selectDefaultPrinter(name, addr, paper)
                        },
                        onTestPrinter = { name, addr ->
                            viewModel.testPrintBluetooth(context, name, addr)
                        },
                        onBackupToUri = { viewModel.performBackupToUri(context, it) },
                        onRestoreFromUri = { viewModel.performRestoreFromUri(context, it) },
                        onExportProductsCsv = { viewModel.exportProductsCsv(context, it) },
                        onImportProductsCsv = { viewModel.importProductsCsv(context, it) },
                        onLockAppNow = { viewModel.lockApp() }
                    )
                }
            }
        }
    }
}
