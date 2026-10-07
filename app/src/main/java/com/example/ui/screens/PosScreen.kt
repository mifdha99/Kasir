package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.CartItem
import com.example.data.CategoryEntity
import com.example.data.ProductEntity
import com.example.data.StoreSettingsEntity
import com.example.data.TransactionWithItems
import com.example.ui.components.CategoryVisuals
import com.example.util.SecurityAndFormatUtils

@Composable
fun PosScreen(
    products: List<ProductEntity>,
    categories: List<CategoryEntity>,
    settings: StoreSettingsEntity,
    activeBillingNumber: Int,
    editingTransactionId: Long?,
    unpaidBillings: List<TransactionWithItems>,
    cartItems: List<CartItem>,
    onAddToCart: (ProductEntity) -> Unit,
    onUpdateQuantity: (Long, Int) -> Unit,
    onUpdatePortionNotes: (Long, List<String>) -> Unit,
    onRemoveFromCart: (Long) -> Unit,
    onClearCart: () -> Unit,
    onStartNewBilling: () -> Unit,
    onLoadUnpaidBilling: (TransactionWithItems) -> Unit,
    onSendOrderToKitchen: () -> Unit,
    onToggleViewMode: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var isBillingPanelExpanded by remember { mutableStateOf(true) }
    var itemForPortionNotesEdit by remember { mutableStateOf<CartItem?>(null) }
    var showSwitchBillingDialog by remember { mutableStateOf(false) }

    val activeProducts = remember(products, searchQuery, selectedCategoryId) {
        products.filter { product ->
            product.isActive &&
                (selectedCategoryId == null || product.categoryId == selectedCategoryId) &&
                (searchQuery.isBlank() ||
                    product.name.contains(searchQuery, ignoreCase = true) ||
                    product.categoryName.contains(searchQuery, ignoreCase = true))
        }
    }

    val cartMap = remember(cartItems) {
        cartItems.associateBy { it.product.id }
    }
    val orderSubtotal = remember(cartItems) {
        cartItems.sumOf { it.subtotal }
    }
    val billingTitle = remember(activeBillingNumber) {
        "BILLING ${activeBillingNumber.coerceAtLeast(1)}"
    }

    if (itemForPortionNotesEdit != null) {
        val currentItem = cartMap[itemForPortionNotesEdit!!.product.id] ?: itemForPortionNotesEdit!!
        PortionNotesDialog(
            cartItem = currentItem,
            onDismiss = { itemForPortionNotesEdit = null },
            onSave = { newQty, updatedPortionNotes ->
                onUpdateQuantity(currentItem.product.id, newQty)
                onUpdatePortionNotes(currentItem.product.id, updatedPortionNotes)
                itemForPortionNotesEdit = null
            },
            onDelete = {
                onRemoveFromCart(currentItem.product.id)
                itemForPortionNotesEdit = null
            }
        )
    }

    if (showSwitchBillingDialog) {
        SwitchActiveBillingDialog(
            unpaidBillings = unpaidBillings,
            currentEditingId = editingTransactionId,
            onDismiss = { showSwitchBillingDialog = false },
            onNewBilling = {
                onStartNewBilling()
                showSwitchBillingDialog = false
            },
            onSelectExisting = { tw ->
                onLoadUnpaidBilling(tw)
                showSwitchBillingDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header: Active Billing Selector + Search + Category Chips
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Active Billing Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = billingTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (editingTransactionId != null) {
                            Text(
                                text = "(Tambah / Edit Pesanan)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFD97706),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { showSwitchBillingDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_switch_active_billing")
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (unpaidBillings.isNotEmpty()) "Ganti Billing (${unpaidBillings.size})" else "Billing Baru",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                val nextMode = if (settings.productViewMode == "GRID") "LIST" else "GRID"
                                onToggleViewMode(nextMode)
                            }
                        ) {
                            Icon(
                                imageVector = if (settings.productViewMode == "GRID") Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Ubah Tampilan"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Prominent Category Buttons: [ SEMUA ] [ MAKANAN ] [ MINUMAN ] [ SNACK ] ...
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = {
                                Text(
                                    text = "SEMUA",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        )
                    }
                    items(categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = {
                                selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
                            },
                            label = {
                                Text(
                                    text = category.name.uppercase(),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = CategoryVisuals.getIcon(category.iconName),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari makanan atau minuman...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus pencarian")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pos_search_input")
                )
            }
        }

        // Menu Grid / List Area
        Box(modifier = Modifier.weight(1f)) {
            if (activeProducts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RestaurantMenu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Menu tidak ditemukan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pilih kategori lain atau ubah kata kunci pencarian.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (settings.productViewMode == "GRID") {
                val cols = settings.gridColumns.coerceIn(2, 3)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(activeProducts, key = { it.id }) { product ->
                        val cartItem = cartMap[product.id]
                        MenuGridCard(
                            product = product,
                            cartItem = cartItem,
                            onAdd = { onAddToCart(product) },
                            onIncrement = {
                                val qty = (cartItem?.quantity ?: 0) + 1
                                onUpdateQuantity(product.id, qty)
                            },
                            onDecrement = {
                                val qty = (cartItem?.quantity ?: 0) - 1
                                onUpdateQuantity(product.id, qty)
                            },
                            onEditPortionNotes = {
                                if (cartItem != null) {
                                    itemForPortionNotesEdit = cartItem
                                }
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(activeProducts, key = { it.id }) { product ->
                        val cartItem = cartMap[product.id]
                        MenuListRow(
                            product = product,
                            cartItem = cartItem,
                            onAdd = { onAddToCart(product) },
                            onIncrement = {
                                val qty = (cartItem?.quantity ?: 0) + 1
                                onUpdateQuantity(product.id, qty)
                            },
                            onDecrement = {
                                val qty = (cartItem?.quantity ?: 0) - 1
                                onUpdateQuantity(product.id, qty)
                            },
                            onEditPortionNotes = {
                                if (cartItem != null) {
                                    itemForPortionNotesEdit = cartItem
                                }
                            }
                        )
                    }
                }
            }
        }

        // ACTIVE BILLING PANEL AT THE BOTTOM (Always shows BILLING X, items, portion notes, TOTAL, [KIRIM PESANAN])
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header Row of Active Billing
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isBillingPanelExpanded = !isBillingPanelExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = billingTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (cartItems.isEmpty()) {
                                "• Belum ada menu dipilih"
                            } else {
                                "• ${cartItems.sumOf { it.quantity }} Porsi (${cartItems.size} Menu)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (cartItems.isNotEmpty()) {
                            TextButton(
                                onClick = onClearCart,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Reset", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        IconButton(
                            onClick = { isBillingPanelExpanded = !isBillingPanelExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isBillingPanelExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = "Buka/Tutup Detail Billing"
                            )
                        }
                    }
                }

                // Active Billing Item List (when expanded and non-empty)
                if (isBillingPanelExpanded && cartItems.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 185.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cartItems, key = { it.product.id }) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.product.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${item.quantity} x ${SecurityAndFormatUtils.formatRupiah(item.product.sellPrice)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = SecurityAndFormatUtils.formatRupiah(item.subtotal),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Display per-portion notes if any exist
                                val portionNotes = item.normalizedPortionNotes
                                portionNotes.forEachIndexed { idx, note ->
                                    if (note.isNotBlank()) {
                                        Text(
                                            text = if (portionNotes.size == 1) {
                                                "Catatan: $note"
                                            } else {
                                                "Porsi ${idx + 1} - Catatan: $note"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFD97706),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Quick controls for item in billing: Catatan Per Porsi, Hapus, -, qty, +
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { itemForPortionNotesEdit = item },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("btn_portion_notes_${item.product.id}")
                                        ) {
                                            Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Catatan Porsi", style = MaterialTheme.typography.labelMedium)
                                        }
                                        IconButton(
                                            onClick = { onRemoveFromCart(item.product.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Hapus Item",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onUpdateQuantity(item.product.id, item.quantity - 1) },
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surface)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Kurangi", modifier = Modifier.size(16.dp))
                                        }
                                        Text(
                                            text = "${item.quantity}",
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        )
                                        IconButton(
                                            onClick = { onUpdateQuantity(item.product.id, item.quantity + 1) },
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Tambah",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // TOTAL & [ KIRIM PESANAN ] Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TOTAL",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = SecurityAndFormatUtils.formatRupiah(orderSubtotal),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = onSendOrderToKitchen,
                        enabled = cartItems.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                        modifier = Modifier.testTag("btn_send_order")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "KIRIM PESANAN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuGridCard(
    product: ProductEntity,
    cartItem: CartItem?,
    onAdd: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEditPortionNotes: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (cartItem == null) onAdd() else onIncrement()
            }
            .testTag("pos_product_card_${product.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (cartItem != null) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (product.imageUri.isNotBlank()) {
                    AsyncImage(
                        model = Uri.parse(product.imageUri),
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = product.name.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (cartItem != null) {
                    TextButton(
                        onClick = onEditPortionNotes,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Catatan", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.categoryName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(40.dp)
            )
            Text(
                text = SecurityAndFormatUtils.formatRupiah(product.sellPrice),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (cartItem == null) {
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDecrement,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Kurangi", modifier = Modifier.size(20.dp))
                    }

                    Text(
                        text = "${cartItem.quantity}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )

                    IconButton(
                        onClick = onIncrement,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Tambah",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuListRow(
    product: ProductEntity,
    cartItem: CartItem?,
    onAdd: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEditPortionNotes: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (cartItem == null) onAdd() else onIncrement()
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = product.name.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(product.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        product.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        SecurityAndFormatUtils.formatRupiah(product.sellPrice),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (cartItem == null) {
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEditPortionNotes) {
                        Icon(Icons.Default.EditNote, contentDescription = "Catatan Porsi", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDecrement) {
                        Icon(Icons.Default.Remove, contentDescription = "Kurangi")
                    }
                    Text(
                        text = "${cartItem.quantity}",
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    IconButton(onClick = onIncrement) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortionNotesDialog(
    cartItem: CartItem,
    onDismiss: () -> Unit,
    onSave: (Int, List<String>) -> Unit,
    onDelete: () -> Unit
) {
    var quantity by remember { mutableStateOf(cartItem.quantity.coerceAtLeast(1)) }
    var portionNotes by remember {
        mutableStateOf(SecurityAndFormatUtils.normalizePortionNotes(cartItem.portionNotes, quantity))
    }

    val quickPresetNotes = listOf("Pedas", "Tidak pedas", "Sedang", "Tanpa bawang", "Es sedikit", "Bungkus")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(cartItem.product.name, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Atur Jumlah & Catatan Per Porsi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quantity adjuster
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Jumlah Porsi:", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (quantity > 1) {
                                    quantity -= 1
                                    portionNotes = SecurityAndFormatUtils.normalizePortionNotes(portionNotes, quantity)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Kurangi Porsi")
                        }
                        Text(
                            text = "$quantity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = {
                                quantity += 1
                                portionNotes = SecurityAndFormatUtils.normalizePortionNotes(portionNotes, quantity)
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Tambah Porsi")
                        }
                    }
                }

                HorizontalDivider()

                // Per-Portion Note Inputs
                for (index in 0 until quantity) {
                    val currentVal = portionNotes.getOrNull(index) ?: ""
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Porsi ${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = currentVal,
                            onValueChange = { newText ->
                                val mutable = portionNotes.toMutableList()
                                if (index < mutable.size) {
                                    mutable[index] = newText
                                    portionNotes = mutable
                                }
                            },
                            label = { Text("Catatan Porsi ${index + 1}") },
                            placeholder = { Text("Contoh: Pedas / Tidak pedas") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            quickPresetNotes.forEach { preset ->
                                FilterChip(
                                    selected = currentVal.equals(preset, ignoreCase = true),
                                    onClick = {
                                        val mutable = portionNotes.toMutableList()
                                        if (index < mutable.size) {
                                            mutable[index] = if (currentVal == preset) "" else preset
                                            portionNotes = mutable
                                        }
                                    },
                                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(quantity, portionNotes)
                }
            ) {
                Text("Simpan Catatan")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus Item")
                }
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
            }
        }
    )
}

@Composable
private fun SwitchActiveBillingDialog(
    unpaidBillings: List<TransactionWithItems>,
    currentEditingId: Long?,
    onDismiss: () -> Unit,
    onNewBilling: () -> Unit,
    onSelectExisting: (TransactionWithItems) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih / Buat Billing Pesanan", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNewBilling,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buat Billing Baru")
                }

                if (unpaidBillings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Atau Tambah Menu ke Billing Belum Bayar:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    unpaidBillings.forEach { tw ->
                        val tx = tw.transaction
                        val isCurrent = currentEditingId == tx.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectExisting(tw) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.billingDisplay, fontWeight = FontWeight.ExtraBold)
                                    Text(
                                        tw.items.joinToString(", ") { "${it.productName} x${it.quantity}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    SecurityAndFormatUtils.formatRupiah(tx.totalAmount),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}
