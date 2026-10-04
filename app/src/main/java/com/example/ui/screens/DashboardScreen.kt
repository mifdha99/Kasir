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
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
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
    onNavigate: (AppScreen) -> Unit,
    onSwitchCashier: (CashierUserEntity, String) -> Boolean,
    onLockApp: () -> Unit,
    onSelectTransaction: (TransactionWithItems) -> Unit
) {
    val startOfToday = remember { SecurityAndFormatUtils.getStartOfDay() }
    val endOfToday = remember { SecurityAndFormatUtils.getEndOfDay() }

    val completedToday = remember(transactions, startOfToday, endOfToday) {
        transactions.filter {
            it.transaction.status == "COMPLETED" &&
                it.transaction.timestamp in startOfToday..endOfToday
        }
    }

    val omzetHariIni = remember(completedToday) {
        completedToday.sumOf { it.transaction.totalAmount }
    }
    val jumlahTransaksiHariIni = completedToday.size

    val lowStockProducts = remember(products) {
        products.filter { it.isActive && it.stock <= it.minStock }
    }

    val bestSellingProduct = remember(transactions) {
        val completedAll = transactions.filter { it.transaction.status == "COMPLETED" }
        val mapQty = mutableMapOf<String, Int>()
        for (tw in completedAll) {
            for (item in tw.items) {
                mapQty[item.productName] = (mapQty[item.productName] ?: 0) + item.quantity
            }
        }
        mapQty.maxByOrNull { it.value }
    }

    val recentTransactions = remember(transactions) {
        transactions.take(5)
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

    val menuItems = remember {
        listOf(
            MenuGridItem("Transaksi", "Kasir Cepat", Icons.Default.PointOfSale, AppScreen.POS, Color(0xFF0F766E), "menu_pos"),
            MenuGridItem("Daftar Produk", "Kelola Barang", Icons.Default.ShoppingBag, AppScreen.PRODUCTS, Color(0xFF2563EB), "menu_products"),
            MenuGridItem("Kategori", "Grup Produk", Icons.Default.Category, AppScreen.CATEGORIES, Color(0xFF7C3AED), "menu_categories"),
            MenuGridItem("Riwayat Transaksi", "Struk & Refund", Icons.Default.History, AppScreen.HISTORY, Color(0xFFD97706), "menu_history"),
            MenuGridItem("Laporan", "Omzet & Laba", Icons.Default.Assessment, AppScreen.REPORTS, Color(0xFF059669), "menu_reports"),
            MenuGridItem("Stok", "Masuk & Opname", Icons.Default.Inventory, AppScreen.STOCK, Color(0xFFDC2626), "menu_stock"),
            MenuGridItem("Pelanggan", "Database Member", Icons.Default.People, AppScreen.CUSTOMERS, Color(0xFF0284C7), "menu_customers"),
            MenuGridItem("Pengaturan", "Toko & Printer", Icons.Default.Settings, AppScreen.SETTINGS, Color(0xFF475569), "menu_settings")
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
                        text = settings.storeAddress.ifBlank { "Siap melayani transaksi offline & cepat" },
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

        // Hero POS Banner Card with Quick CTA
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(AppScreen.POS) }
                    .testTag("hero_pos_card"),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_pos_banner),
                        contentDescription = "KasirKu Point of Sale",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xE6042F2E),
                                        Color(0xB30F766E),
                                        Color(0x330F766E)
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
                                text = "KASIR CEPAT & OFFLINE",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF99F6E4),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Mulai Transaksi Baru",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Scan barcode, hitung kembalian & cetak struk",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        Button(
                            onClick = { onNavigate(AppScreen.POS) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF59E0B),
                                contentColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_hero_open_pos")
                        ) {
                            Icon(Icons.Default.PointOfSale, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BUKA MESIN KASIR", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // Dashboard Summary Metrics (2x2 Grid)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardMetricCard(
                        title = "Omzet Hari Ini",
                        value = SecurityAndFormatUtils.formatRupiah(omzetHariIni),
                        subtitle = "$jumlahTransaksiHariIni Transaksi Selesai",
                        icon = Icons.Default.TrendingUp,
                        accentColor = Color(0xFF0F766E),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(AppScreen.REPORTS) }
                    )
                    DashboardMetricCard(
                        title = "Produk Terlaris",
                        value = bestSellingProduct?.key ?: "Belum Ada",
                        subtitle = if (bestSellingProduct != null) "${bestSellingProduct.value} terjual" else "Siap transaksi",
                        icon = Icons.Default.Star,
                        accentColor = Color(0xFFD97706),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(AppScreen.REPORTS) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardMetricCard(
                        title = "Stok Hampir Habis",
                        value = "${lowStockProducts.size} Produk",
                        subtitle = if (lowStockProducts.isNotEmpty()) {
                            lowStockProducts.take(2).joinToString(", ") { it.name }
                        } else {
                            "Semua stok aman"
                        },
                        icon = Icons.Default.WarningAmber,
                        accentColor = if (lowStockProducts.isNotEmpty()) Color(0xFFDC2626) else Color(0xFF059669),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(AppScreen.STOCK) }
                    )
                    DashboardMetricCard(
                        title = "Total Produk Aktif",
                        value = "${products.count { it.isActive }} Item",
                        subtitle = "Terdaftar di katalog",
                        icon = Icons.Default.ShoppingBag,
                        accentColor = Color(0xFF2563EB),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(AppScreen.PRODUCTS) }
                    )
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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Transactions Section
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

            if (recentTransactions.isEmpty()) {
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
                            text = "Belum ada transaksi tercatat.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tekan tombol Transaksi untuk melayani pelanggan pertama.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentTransactions.forEach { tw ->
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
                                        text = tx.invoiceNumber,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${SecurityAndFormatUtils.formatDateTime(tx.timestamp)} • ${tx.paymentMethod}",
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
                                        text = if (isCancelled) "DIBATALKAN" else "SELESAI",
                                        style = MaterialTheme.typography.labelSmall,
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
