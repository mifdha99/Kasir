package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.KasirDatabase
import com.example.data.KasirRepository
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
import com.example.viewmodel.KasirViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = KasirDatabase.getInstance(applicationContext)
        val repository = KasirRepository(database)
        val factory = KasirViewModelFactory(application, repository)

        setContent {
            val viewModel: KasirViewModel = viewModel(factory = factory)
            val settings by viewModel.storeSettings.collectAsStateWithLifecycle()

            KasirKuTheme(themeMode = if (settings.isDarkMode) "DARK" else "LIGHT") {
                KasirKuMainApp(viewModel = viewModel)
            }
        }
    }
}

private data class DrawerNavDestination(
    val screen: AppScreen,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasirKuMainApp(viewModel: KasirViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val settings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val paymentMethods by viewModel.paymentMethods.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val unpaidBillings by viewModel.unpaidBillings.collectAsStateWithLifecycle()
    val cashierUsers by viewModel.cashierUsers.collectAsStateWithLifecycle()
    val activeCashier by viewModel.activeCashier.collectAsStateWithLifecycle()
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()

    // Ordering (PROSES PESAN) states
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val activeBillingNumber by viewModel.activeBillingNumber.collectAsStateWithLifecycle()
    val editingTransactionId by viewModel.editingTransactionId.collectAsStateWithLifecycle()
    val lastSentKitchenOrder by viewModel.lastSentKitchenOrder.collectAsStateWithLifecycle()

    // Payment (PROSES BAYAR) states
    val selectedBillingForPayment by viewModel.selectedBillingForPayment.collectAsStateWithLifecycle()
    val lastCompletedTransaction by viewModel.lastCompletedTransaction.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BackHandler(enabled = drawerState.isOpen || selectedBillingForPayment != null || currentScreen != AppScreen.DASHBOARD) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (currentScreen == AppScreen.PAYMENT && selectedBillingForPayment != null) {
            viewModel.selectBillingForPayment(null)
        } else {
            viewModel.navigateBack()
        }
    }

    if (isAppLocked) {
        PinLockDialog(
            title = "Kunci Keamanan ${settings.storeName}",
            subtitle = "Masukkan PIN Kasir / Admin (Default Admin: 1234)",
            canDismiss = false,
            onVerifyPin = { pin -> viewModel.unlockAppWithPin(pin) }
        )
        return
    }

    // Kitchen Ticket Dialog (Shown after "KIRIM PESANAN")
    if (lastSentKitchenOrder != null) {
        val kitchenOrder = lastSentKitchenOrder!!
        KitchenTicketDialog(
            txWithItems = kitchenOrder,
            settings = settings,
            onDismiss = { viewModel.dismissKitchenOrderModal() },
            onPrintKitchenBluetooth = { addr ->
                viewModel.printKitchenTicketViaBluetooth(context, kitchenOrder, addr)
            },
            onShareKitchenText = {
                viewModel.shareKitchenTicketAsText(context, kitchenOrder)
            },
            onSelectDefaultPrinter = { name, address, paperSize ->
                viewModel.updateStoreSettings(
                    settings.copy(
                        defaultPrinterName = name,
                        defaultPrinterAddress = address,
                        paperSizeMm = paperSize
                    )
                )
            }
        )
    }

    // Payment Receipt Dialog (Shown after "BAYAR & SELESAIKAN" or from History)
    if (lastCompletedTransaction != null) {
        val tx = lastCompletedTransaction!!
        ReceiptPreviewAndActionDialog(
            txWithItems = tx,
            settings = settings,
            isNewCheckout = currentScreen == AppScreen.PAYMENT,
            onDismiss = { viewModel.dismissCompletedTransactionModal() },
            onPrintBluetooth = { addr ->
                viewModel.printReceiptViaBluetooth(context, tx, addr)
            },
            onShareText = {
                viewModel.shareReceiptAsText(context, tx)
            },
            onSharePdf = {
                viewModel.shareReceiptAsPdf(context, tx)
            },
            onSavePdfToUri = { uri ->
                viewModel.saveReceiptPdfToUri(context, uri, tx)
            },
            onSelectDefaultPrinter = { name, address, paperSize ->
                viewModel.updateStoreSettings(
                    settings.copy(
                        defaultPrinterName = name,
                        defaultPrinterAddress = address,
                        paperSizeMm = paperSize
                    )
                )
            }
        )
    }

    val allDrawerDestinations = remember {
        listOf(
            DrawerNavDestination(AppScreen.DASHBOARD, "Beranda Utama", Icons.Default.Home, "drawer_dashboard"),
            DrawerNavDestination(AppScreen.POS, "Pesan Menu (Dapur)", Icons.Default.RestaurantMenu, "drawer_pos"),
            DrawerNavDestination(AppScreen.PAYMENT, "Pembayaran Billing", Icons.Default.Payments, "drawer_payment"),
            DrawerNavDestination(AppScreen.PRODUCTS, "Edit Menu dan Harga", Icons.Default.Restaurant, "drawer_products"),
            DrawerNavDestination(AppScreen.CATEGORIES, "Kategori Menu", Icons.Default.Category, "drawer_categories"),
            DrawerNavDestination(AppScreen.HISTORY, "Riwayat Transaksi", Icons.Default.History, "drawer_history"),
            DrawerNavDestination(AppScreen.REPORTS, "Laporan Penjualan (Rekap)", Icons.Default.Assessment, "drawer_reports"),
            DrawerNavDestination(AppScreen.CUSTOMERS, "Pelanggan", Icons.Default.People, "drawer_customers"),
            DrawerNavDestination(AppScreen.SETTINGS, "Pengaturan", Icons.Default.Settings, "drawer_settings")
        )
    }

    val bottomDestinations = remember {
        listOf(
            DrawerNavDestination(AppScreen.DASHBOARD, "Beranda", Icons.Default.Home, "bottom_dashboard"),
            DrawerNavDestination(AppScreen.POS, "Pesan", Icons.Default.RestaurantMenu, "bottom_pos"),
            DrawerNavDestination(AppScreen.PAYMENT, "Bayar", Icons.Default.Payments, "bottom_payment"),
            DrawerNavDestination(AppScreen.PRODUCTS, "Edit Menu", Icons.Default.Restaurant, "bottom_products"),
            DrawerNavDestination(AppScreen.REPORTS, "Rekap", Icons.Default.Assessment, "bottom_reports")
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = settings.storeName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Kasir: ${activeCashier?.name ?: "Admin"} (${activeCashier?.role ?: "ADMIN"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    allDrawerDestinations.forEach { dest ->
                        NavigationDrawerItem(
                            label = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dest.label,
                                        fontWeight = if (currentScreen == dest.screen) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (dest.screen == AppScreen.PAYMENT && unpaidBillings.isNotEmpty()) {
                                        Badge {
                                            Text("${unpaidBillings.size}")
                                        }
                                    }
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            selected = currentScreen == dest.screen,
                            onClick = {
                                viewModel.navigateTo(dest.screen)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                .testTag(dest.tag)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (currentScreen == AppScreen.DASHBOARD) settings.storeName else currentScreen.title,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (currentScreen == AppScreen.DASHBOARD) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("btn_open_drawer")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Buka Menu Navigasi")
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (currentScreen == AppScreen.PAYMENT && selectedBillingForPayment != null) {
                                        viewModel.selectBillingForPayment(null)
                                    } else {
                                        viewModel.navigateBack()
                                    }
                                },
                                modifier = Modifier.testTag("btn_back")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("btn_top_drawer")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu Lainnya")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    bottomDestinations.forEach { item ->
                        val selected = currentScreen == item.screen
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.navigateTo(item.screen) },
                            icon = {
                                if (item.screen == AppScreen.POS && cartItems.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text("${cartItems.sumOf { it.quantity }}")
                                            }
                                        }
                                    ) {
                                        Icon(item.icon, contentDescription = item.label)
                                    }
                                } else if (item.screen == AppScreen.PAYMENT && unpaidBillings.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text("${unpaidBillings.size}")
                                            }
                                        }
                                    ) {
                                        Icon(item.icon, contentDescription = item.label)
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = item.label)
                                }
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.testTag(item.tag)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    AppScreen.DASHBOARD -> DashboardScreen(
                        settings = settings,
                        activeCashier = activeCashier,
                        allUsers = cashierUsers,
                        products = products,
                        transactions = allTransactions,
                        unpaidBillings = unpaidBillings,
                        onNavigate = { viewModel.navigateTo(it) },
                        onSwitchCashier = { user, pin -> viewModel.switchCashier(user, pin) },
                        onLockApp = { viewModel.lockApp() },
                        onSelectTransaction = { viewModel.showTransactionReceiptModal(it) },
                        onSelectBillingToPay = { viewModel.openBillingInPaymentScreen(it) }
                    )

                    AppScreen.POS -> PosScreen(
                        products = products,
                        categories = categories,
                        settings = settings,
                        activeBillingNumber = activeBillingNumber,
                        editingTransactionId = editingTransactionId,
                        unpaidBillings = unpaidBillings,
                        cartItems = cartItems,
                        onAddToCart = { viewModel.addToCart(it) },
                        onUpdateQuantity = { id, qty -> viewModel.updateCartItemQuantity(id, qty) },
                        onUpdatePortionNotes = { id, notes -> viewModel.updateCartItemPortionNotes(id, notes) },
                        onRemoveFromCart = { viewModel.removeFromCart(it) },
                        onClearCart = { viewModel.clearOrderDraft() },
                        onStartNewBilling = { viewModel.startNewBilling() },
                        onLoadUnpaidBilling = { viewModel.loadUnpaidBillingToEdit(it) },
                        onSendOrderToKitchen = { viewModel.sendOrderToKitchen(context) },
                        onToggleViewMode = { mode ->
                            viewModel.updateStoreSettings(settings.copy(productViewMode = mode))
                        }
                    )

                    AppScreen.PAYMENT -> PaymentScreen(
                        unpaidBillings = unpaidBillings,
                        selectedBilling = selectedBillingForPayment,
                        paymentMethods = paymentMethods,
                        settings = settings,
                        activeCashier = activeCashier,
                        onSelectBilling = { viewModel.selectBillingForPayment(it) },
                        onEditBillingItems = { viewModel.loadUnpaidBillingToEdit(it) },
                        onPrintKitchenTicket = { viewModel.showKitchenTicketModal(it) },
                        onCancelBilling = { tx, reason -> viewModel.cancelTransaction(tx, reason) },
                        onAddPaymentMethod = { name, isCash -> viewModel.addPaymentMethod(name, isCash) },
                        onUpdatePaymentMethod = { method, newName, isCash ->
                            viewModel.updatePaymentMethod(method, newName, isCash)
                        },
                        onDeletePaymentMethod = { method -> viewModel.deletePaymentMethod(method) },
                        onCompletePayment = { tx, method, methodId, paid, discType, discIn, taxOn, taxPct, srvFee, note ->
                            viewModel.payBilling(
                                context = context,
                                txWithItems = tx,
                                paymentMethod = method,
                                paymentMethodId = methodId,
                                amountPaidInput = paid,
                                discountType = discType,
                                discountInput = discIn,
                                enableTax = taxOn,
                                taxPercent = taxPct,
                                serviceFee = srvFee,
                                notes = note
                            )
                        },
                        onOpenPosScreen = { viewModel.navigateTo(AppScreen.POS) }
                    )

                    AppScreen.PRODUCTS -> ProductScreen(
                        products = products,
                        categories = categories,
                        canEditPrice = activeCashier?.canEditPrice ?: true,
                        onSaveProduct = { id, name, sell, unit, catId, catName, desc, img, active ->
                            viewModel.saveProduct(
                                id = id,
                                name = name,
                                sellPrice = sell,
                                unit = unit,
                                categoryId = catId,
                                categoryName = catName,
                                description = desc,
                                imageUri = img,
                                isActive = active
                            )
                        },
                        onDeleteProduct = { viewModel.deleteProduct(it) }
                    )

                    AppScreen.CATEGORIES -> CategoryScreen(
                        categories = categories,
                        products = products,
                        onSaveCategory = { cat ->
                            viewModel.saveCategory(cat.id, cat.name, cat.description, cat.colorHex, cat.iconName)
                        },
                        onMoveCategoryProducts = { fromCat, toCat ->
                            viewModel.moveCategoryProducts(fromCat, toCat)
                        },
                        onDeleteCategory = { cat, reassignTarget ->
                            viewModel.deleteCategory(cat, reassignTarget)
                        }
                    )

                    AppScreen.HISTORY -> HistoryScreen(
                        transactions = allTransactions,
                        activeCashier = activeCashier,
                        onOpenReceiptModal = { viewModel.showTransactionReceiptModal(it) },
                        onOpenKitchenModal = { viewModel.showKitchenTicketModal(it) },
                        onPayUnpaidBilling = { viewModel.openBillingInPaymentScreen(it) },
                        onShareReceiptText = { viewModel.shareReceiptAsText(context, it) },
                        onCancelTransaction = { txId, reason ->
                            val target = allTransactions.find { it.transaction.id == txId }
                            if (target != null) {
                                viewModel.cancelTransaction(target, reason)
                            }
                        }
                    )

                    AppScreen.REPORTS -> ReportScreen(
                        transactions = allTransactions,
                        paymentMethods = paymentMethods,
                        products = products,
                        categories = categories,
                        customers = customers,
                        storeName = settings.storeName,
                        onExportCsv = { uri, list, isExcel ->
                            viewModel.exportReportCsvOrExcel(context, uri, list, isExcel)
                        },
                        onExportPdf = { uri, period, list ->
                            viewModel.exportReportPdf(context, uri, period, list)
                        }
                    )

                    AppScreen.CUSTOMERS -> CustomerScreen(
                        customers = customers,
                        onSaveCustomer = { cust ->
                            viewModel.saveCustomer(
                                id = cust.id,
                                name = cust.name,
                                phone = cust.phone,
                                address = cust.address,
                                notes = cust.notes,
                                existingDebt = cust.totalDebt
                            )
                        },
                        onDeleteCustomer = { viewModel.deleteCustomer(it) }
                    )

                    AppScreen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        paymentMethods = paymentMethods,
                        cashierUsers = cashierUsers,
                        onSaveSettings = { viewModel.updateStoreSettings(it) },
                        onAddPaymentMethod = { name, isCash -> viewModel.addPaymentMethod(name, isCash) },
                        onUpdatePaymentMethod = { method, newName, isCash ->
                            viewModel.updatePaymentMethod(method, newName, isCash)
                        },
                        onDeletePaymentMethod = { method -> viewModel.deletePaymentMethod(method) },
                        onSaveCashierUser = { id, name, role, rawPin, hash, editPrice, disc, cancel, rep ->
                            viewModel.saveCashierUser(id, name, role, rawPin, hash, editPrice, disc, cancel, rep)
                        },
                        onDeleteCashierUser = { viewModel.deleteCashierUser(it) },
                        onBackupDatabase = { viewModel.exportDatabaseBackup(context, it) },
                        onRestoreDatabase = { viewModel.restoreDatabaseBackup(context, it) },
                        onResetTransactionsOnly = { viewModel.resetTransactionsHistoryOnly() },
                        onResetFactory = { viewModel.resetAllFactoryData() },
                        onEmitMessage = { viewModel.emitMessage(it) }
                    )
                }
            }
        }
    }
}
