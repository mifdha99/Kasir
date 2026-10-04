package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.ProductEntity
import com.example.data.StockMovementEntity
import com.example.util.SecurityAndFormatUtils

@Composable
fun StockScreen(
    products: List<ProductEntity>,
    stockMovements: List<StockMovementEntity>,
    onAdjustStock: (Long, String, Int, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var stockFilter by remember { mutableStateOf("ALL") } // "ALL", "LOW", "OUT"
    var dialogProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var dialogType by remember { mutableStateOf("IN") } // "IN", "OUT", "ADJUSTMENT"

    val totalModalValue = remember(products) {
        products.sumOf { it.buyPrice * it.stock.coerceAtLeast(0) }
    }
    val totalSellValue = remember(products) {
        products.sumOf { it.sellPrice * it.stock.coerceAtLeast(0) }
    }
    val lowStockCount = remember(products) {
        products.count { it.stock in 1..it.minStock }
    }
    val outOfStockCount = remember(products) {
        products.count { it.stock <= 0 }
    }

    val filteredProducts = remember(products, stockFilter) {
        when (stockFilter) {
            "LOW" -> products.filter { it.stock in 1..it.minStock }
            "OUT" -> products.filter { it.stock <= 0 }
            else -> products
        }
    }

    if (dialogProduct != null) {
        val prod = dialogProduct!!
        StockAdjustmentDialog(
            product = prod,
            initialType = dialogType,
            onDismiss = { dialogProduct = null },
            onConfirm = { type, qty, note ->
                onAdjustStock(prod.id, type, qty, note)
                dialogProduct = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Inventory Valuation & Stock Alert Banner
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Nilai Persediaan (Modal)", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = SecurityAndFormatUtils.formatRupiah(totalModalValue),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Nilai Jual: ${SecurityAndFormatUtils.formatRupiah(totalSellValue)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (lowStockCount + outOfStockCount > 0) {
                                Color(0xFFFEF3C7)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Status Stok", style = MaterialTheme.typography.labelSmall, color = Color(0xFF92400E))
                            }
                            Text(
                                text = "$lowStockCount Menipis • $outOfStockCount Habis",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF78350F)
                            )
                            Text(
                                text = "Total ${products.size} jenis produk",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Stok Produk (${products.size})", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Inventory, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Riwayat Stok (${stockMovements.size})", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.History, contentDescription = null) }
            )
        }

        if (selectedTab == 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = stockFilter == "ALL",
                    onClick = { stockFilter = "ALL" },
                    label = { Text("Semua (${products.size})") }
                )
                FilterChip(
                    selected = stockFilter == "LOW",
                    onClick = { stockFilter = "LOW" },
                    label = { Text("Stok Menipis ($lowStockCount)") }
                )
                FilterChip(
                    selected = stockFilter == "OUT",
                    onClick = { stockFilter = "OUT" },
                    label = { Text("Habis ($outOfStockCount)") }
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredProducts, key = { it.id }) { prod ->
                    val isOut = prod.stock <= 0
                    val isLow = prod.stock in 1..prod.minStock

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${prod.categoryName} • Min. Stok: ${prod.minStock} ${prod.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Nilai Persediaan: ${SecurityAndFormatUtils.formatRupiah(prod.buyPrice * prod.stock.coerceAtLeast(0))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = when {
                                        isOut -> MaterialTheme.colorScheme.errorContainer
                                        isLow -> Color(0xFFFEF3C7)
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                ) {
                                    Text(
                                        text = "${prod.stock} ${prod.unit}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = when {
                                            isOut -> MaterialTheme.colorScheme.onErrorContainer
                                            isLow -> Color(0xFF92400E)
                                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        dialogProduct = prod
                                        dialogType = "IN"
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stok Masuk", style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = {
                                        dialogProduct = prod
                                        dialogType = "OUT"
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stok Keluar", style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = {
                                        dialogProduct = prod
                                        dialogType = "ADJUSTMENT"
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Penyesuaian", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Stock Movement History Tab
            if (stockMovements.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Belum ada riwayat perubahan stok.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(stockMovements, key = { it.id }) { sm ->
                        val isPositive = sm.quantityChange >= 0
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sm.productName, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${sm.type} • Oleh: ${sm.userName} • ${SecurityAndFormatUtils.formatDateTime(sm.timestamp)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (sm.note.isNotBlank()) {
                                        Text(
                                            text = sm.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (isPositive) "+${sm.quantityChange}" else "${sm.quantityChange}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isPositive) Color(0xFF059669) else MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "${sm.previousStock} -> ${sm.newStock}",
                                        style = MaterialTheme.typography.labelSmall,
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
}

@Composable
private fun StockAdjustmentDialog(
    product: ProductEntity,
    initialType: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String) -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var qtyText by remember(type) {
        mutableStateOf(if (type == "ADJUSTMENT") product.stock.toString() else "")
    }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val title = when (type) {
        "IN" -> "Tambah Stok Masuk"
        "OUT" -> "Catat Stok Keluar"
        else -> "Penyesuaian Stok (Opname)"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Produk: ${product.name}\nStok Saat Ini: ${product.stock} ${product.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = type == "IN",
                        onClick = { type = "IN" },
                        label = { Text("Masuk") }
                    )
                    FilterChip(
                        selected = type == "OUT",
                        onClick = { type = "OUT" },
                        label = { Text("Keluar") }
                    )
                    FilterChip(
                        selected = type == "ADJUSTMENT",
                        onClick = { type = "ADJUSTMENT" },
                        label = { Text("Opname") }
                    )
                }

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = {
                        qtyText = it.filter { ch -> ch.isDigit() }
                        errorMsg = null
                    },
                    label = {
                        Text(
                            when (type) {
                                "IN" -> "Jumlah Stok Masuk (${product.unit})"
                                "OUT" -> "Jumlah Stok Keluar (${product.unit})"
                                else -> "Stok Fisik Sebenarnya (${product.unit})"
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Keterangan / Catatan") },
                    placeholder = {
                        Text(
                            when (type) {
                                "IN" -> "Contoh: Belanja dari supplier"
                                "OUT" -> "Contoh: Barang rusak / kadaluarsa"
                                else -> "Contoh: Penyesuaian stok bulanan"
                            }
                        )
                    },
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
                    val q = qtyText.toIntOrNull()
                    if (q == null || q < 0) {
                        errorMsg = "Masukkan angka jumlah yang valid!"
                        return@Button
                    }
                    if ((type == "IN" || type == "OUT") && q == 0) {
                        errorMsg = "Jumlah harus lebih dari 0!"
                        return@Button
                    }
                    onConfirm(type, q, note)
                }
            ) {
                Text("Simpan Stok")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
