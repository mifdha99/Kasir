package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CashierUserEntity
import com.example.data.ProductEntity
import com.example.data.StoreSettingsEntity
import com.example.data.TransactionWithItems
import com.example.util.SecurityAndFormatUtils
import com.example.viewmodel.AppScreen

private data class MenuGridItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val screen: AppScreen,
    val color: Color,
    val testTag: String
)

@Composable
fun DashboardScreen(
    settings: StoreSettingsEntity,
    activeCashier: CashierUserEntity?,
    allUsers: List<CashierUserEntity>,
    products: List<ProductEntity>,
    transactions: List<TransactionWithItems>,
    unpaidBillings: List<TransactionWithItems>,
    onNavigate: (AppScreen) -> Unit,
    onSwitchCashier: (CashierUserEntity, String) -> Boolean,
    onLockApp: () -> Unit,
    onSelectTransaction: (TransactionWithItems) -> Unit,
    onSelectBillingToPay: (TransactionWithItems) -> Unit
) {
    val startOfToday = remember { SecurityAndFormatUtils.getStartOfDay() }
    val endOfToday = remember { SecurityAndFormatUtils.getEndOfDay() }

    val paidToday = remember(transactions, startOfToday, endOfToday) {
        transactions.filter {
            (it.transaction.status == "PAID" || it.transaction.status == "COMPLETED") &&
                it.transaction.timestamp in startOfToday..endOfToday
        }
    }

    val omzetHariIni = remember(paidToday) {
        paidToday.sumOf { it.transaction.totalAmount }
    }
    val jumlahLunasHariIni = paidToday.size

    val bestSellingProduct = remember(transactions) {
        val paidAll = transactions.filter { it.transaction.status == "PAID" || it.transaction.status == "COMPLETED" }
        val mapQty = mutableMapOf<String, Int>()
        for (tw in paidAll) {
            for (item in tw.items) {
                mapQty[item.productName] = (mapQty[item.productName] ?: 0) + item.quantity
            }
        }
        mapQty.maxByOrNull { it.value }
    }

    val recentPaidTransactions = remember(transactions) {
        transactions.filter { it.transaction.status != "UNPAID" }.take(5)
    }

    var showSwitchCashierDialog by remember { mutableStateOf(false) }

    if (showSwitchCashierDialog) {
        SwitchCashierDialog(
            users = allUsers,
            currentCashier = activeCashier,
            onDismiss = { showSwitchCashierDialog = false },
            onSelectAndVerify = { user, pin ->
                val ok = onSwitchCashier(user, pin)
                if (ok) showSwitchCashierDialog = false
                ok
            }
        )
    }

    val menuItems = remember(unpaidBillings.size) {
        listOf(
            MenuGridItem("Pesan Menu", "Buat Billing & Dapur", Icons.Default.RestaurantMenu, AppScreen.POS, Color(0xFF0F766E), "menu_pos"),
            MenuGridItem("Pembayaran", "${unpaidBillings.size} Billing Belum Bayar", Icons.Default.Payments, AppScreen.PAYMENT, Color(0xFFD97706), "menu_payment"),
            MenuGridItem("Edit Menu dan Harga", "Makanan & Minuman", Icons.Default.Restaurant, AppScreen.PRODUCTS, Color(0xFF2563EB), "menu_products"),
            MenuGridItem("Kategori", "Atur Kategori Menu", Icons.Default.Category, AppScreen.CATEGORIES, Color(0xFF7C3AED), "menu_categories"),
            MenuGridItem("Riwayat Transaksi", "Billing Lunas & Struk", Icons.Default.History, AppScreen.HISTORY, Color(0xFF059669), "menu_history"),
            MenuGridItem("Laporan Penjualan", "Rekap Harian & Omzet", Icons.Default.Assessment, AppScreen.REPORTS, Color(0xFF0284C7), "menu_reports"),
            MenuGridItem("Pelanggan", "Data Pelanggan", Icons.Default.People, AppScreen.CUSTOMERS, Color(0xFFDB2777), "menu_customers"),
            MenuGridItem("Pengaturan", "Metode Bayar & Printer", Icons.Default.Settings, AppScreen.SETTINGS, Color(0xFF475569), "menu_settings")
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Store Header & Active Cashier
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settings.storeName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = settings.storeAddress.ifBlank { "Kasir Restoran & Warung Offline Cepat" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clickable { showSwitchCashierDialog = true }
                            .testTag("btn_switch_cashier")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Kasir Aktif",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeCashier?.name ?: "Admin",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onLockApp,
                        modifier = Modifier.testTag("btn_lock_app")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Kunci Aplikasi",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Hero Banner Card with Two Fast Actions: PESAN MENU & PEMBAYARAN
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_pos_card"),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(185.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_pos_banner),
                        contentDescription = "KasirKu Restoran",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xEE042F2E),
                                        Color(0xCC0F766E),
                                        Color(0x660F766E)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "KASIR RESTORAN & WARUNG",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF99F6E4),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pesan Cepat & Bayar Mudah",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Pilih menu → Kirim ke dapur → Bayar Billing",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onNavigate(AppScreen.POS) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_hero_open_pos")
                            ) {
                                Icon(Icons.Default.RestaurantMenu, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PESAN MENU", fontWeight = FontWeight.ExtraBold)
                            }

                            Button(
                                onClick = { onNavigate(AppScreen.PAYMENT) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF0F766E)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_hero_open_payment")
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (unpaidBillings.isNotEmpty()) "BAYAR (${unpaidBillings.size})" else "PEMBAYARAN",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dashboard Summary Metrics (2x2 Grid)
        item {
            val totalUnpaidNominal = unpaidBillings.sumOf { it.transaction.totalAmount }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardMetricCard(
                        title = "Omzet Hari Ini",
                        value = SecurityAndFormatUtils.formatRupiah(omzetHariIni),
                        subtitle = "$jumlahLunasHariIni Billing Lunas",
                        icon = Icons.Default.TrendingUp,
                        accentColor = Color(0xFF0F766E),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(AppScreen.REPORTS) }
                    )
                    DashboardMetricCard(
                        title = "Billing Belum Bayar",
                        value = "${unpaidBillings.size} Billing",
                        subtitle = if (unpaidBillings.isNotEmpty()) {
                            "Total ${SecurityAndFormatUtils.formatRupiah(totalUnpaidNominal)}"
                        } else {
                            "Semua sudah lunas"
                        },
                        icon = Icons.Default.PendingActions,
                        accentColor = if (unpaidBillings.isNotEmpty()) Color(0xFFD97706) else Color(0xFF059669),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(AppScreen.PAYMENT) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardMetricCard(
                        title = "Menu Terlaris",
                        value = bestSellingProduct?.key ?: "Belum Ada",
                        subtitle = if (bestSellingProduct != null) "${bestSellingProduct.value} porsi terjual" else "Siap melayani",
                        icon = Icons.Default.Star,
                        accentColor = Color(0xFF7C3AED),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(AppScreen.REPORTS) }
                    )
                    DashboardMetricCard(
                        title = "Menu Tersedia",
                        value = "${products.count { it.isActive }} Menu",
                        subtitle = "Makanan & Minuman",
                        icon = Icons.Default.Restaurant,
                        accentColor = Color(0xFF2563EB),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(AppScreen.PRODUCTS) }
                    )
                }
            }
        }

        // Quick Unpaid Billings Banner if any exist
        if (unpaidBillings.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Billing Menunggu Pembayaran (${unpaidBillings.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { onNavigate(AppScreen.PAYMENT) }) {
                        Text("Buka Pembayaran")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    unpaidBillings.take(3).forEach { tw ->
                        val tx = tw.transaction
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBillingToPay(tw) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFEF3C7).copy(alpha = 0.65f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.billingDisplay,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF78350F)
                                    )
                                    Text(
                                        text = tw.items.joinToString(", ") { "${it.productName} x${it.quantity}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF92400E),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = SecurityAndFormatUtils.formatRupiah(tx.totalAmount),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF78350F)
                                    )
                                    Text(
                                        text = "BELUM BAYAR",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8 Main Navigation Buttons
        item {
            Text(
                text = "Menu Utama KasirKu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                menuItems.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { item ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onNavigate(item.screen) }
                                    .testTag(item.testTag),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(item.color.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.title,
                                            tint = item.color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = item.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Paid Transactions Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transaksi Terakhir",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onNavigate(AppScreen.HISTORY) }) {
                    Text("Lihat Semua")
                }
            }

            if (recentPaidTransactions.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Belum ada riwayat billing lunas.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tekan Pesan Menu untuk membuat Billing 1.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentPaidTransactions.forEach { tw ->
                        val tx = tw.transaction
                        val isCancelled = tx.status == "CANCELLED"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTransaction(tw) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.billingDisplay,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${SecurityAndFormatUtils.formatDateTime(tx.timestamp)} • ${tw.items.sumOf { it.quantity }} item • ${tx.paymentMethod}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = SecurityAndFormatUtils.formatRupiah(tx.totalAmount),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (isCancelled) "DIBATALKAN" else "LUNAS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCancelled) MaterialTheme.colorScheme.error else Color(0xFF059669)
                                    )
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
private fun DashboardMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SwitchCashierDialog(
    users: List<CashierUserEntity>,
    currentCashier: CashierUserEntity?,
    onDismiss: () -> Unit,
    onSelectAndVerify: (CashierUserEntity, String) -> Boolean
) {
    var selectedUser by remember { mutableStateOf(currentCashier ?: users.firstOrNull()) }
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Kasir Bertugas", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Pilih nama kasir dan masukkan PIN (Default Admin: 1234, Kasir Shift 1: 0000):",
                    style = MaterialTheme.typography.bodySmall
                )
                users.filter { it.isActive }.forEach { user ->
                    val isSelected = selectedUser?.id == user.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedUser = user
                                errorMsg = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(user.name, fontWeight = FontWeight.Bold)
                            Text(user.role, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        errorMsg = null
                    },
                    label = { Text("Masukkan PIN Kasir") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val u = selectedUser
                    if (u != null) {
                        val ok = onSelectAndVerify(u, pinInput)
                        if (!ok) errorMsg = "PIN Kasir tidak cocok!"
                    }
                }
            ) {
                Text("Ganti Kasir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
