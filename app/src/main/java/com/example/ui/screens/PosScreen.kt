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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.CartItem
import com.example.data.CashierUserEntity
import com.example.data.CategoryEntity
import com.example.data.CustomerEntity
import com.example.data.ProductEntity
import com.example.data.StoreSettingsEntity
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.CategoryVisuals
import com.example.util.SecurityAndFormatUtils
import com.example.viewmodel.CartCalculation

@Composable
fun PosScreen(
    products: List<ProductEntity>,
    categories: List<CategoryEntity>,
    customers: List<CustomerEntity>,
    settings: StoreSettingsEntity,
    activeCashier: CashierUserEntity?,
    cartItems: List<CartItem>,
    selectedCustomer: CustomerEntity?,
    discountInput: Double,
    serviceFeeInput: Double,
    transactionNote: String,
    paymentMethod: String,
    amountPaidInput: String,
    cartCalculation: CartCalculation,
    onAddToCart: (ProductEntity) -> Unit,
    onUpdateQuantity: (Long, Int) -> Unit,
    onUpdateItemNote: (Long, String) -> Unit,
    onRemoveFromCart: (Long) -> Unit,
    onClearCart: () -> Unit,
    onSelectCustomer: (CustomerEntity?) -> Unit,
    onSetDiscount: (Double) -> Unit,
    onSetServiceFee: (Double) -> Unit,
    onSetTransactionNote: (String) -> Unit,
    onSetPaymentMethod: (String) -> Unit,
    onSetAmountPaidInput: (String) -> Unit,
    onScanBarcode: (String) -> Unit,
    onSubmitCheckout: () -> Unit,
    onToggleViewMode: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var itemForQuantityOrNoteEdit by remember { mutableStateOf<CartItem?>(null) }

    val activeProducts = remember(products, searchQuery, selectedCategoryId) {
        products.filter { product ->
            product.isActive &&
                (selectedCategoryId == null || product.categoryId == selectedCategoryId) &&
                (searchQuery.isBlank() ||
                    product.name.contains(searchQuery, ignoreCase = true) ||
                    product.sku.contains(searchQuery, ignoreCase = true) ||
                    product.barcode.contains(searchQuery, ignoreCase = true))
        }
    }

    val cartMap = remember(cartItems) {
        cartItems.associateBy { it.product.id }
    }
    val totalCartItemsCount = remember(cartItems) {
        cartItems.sumOf { it.quantity }
    }

    if (showScannerDialog) {
        BarcodeScannerDialog(
            title = "Scan Barcode ke Keranjang",
            onDismiss = { showScannerDialog = false },
            onBarcodeScanned = { code ->
                onScanBarcode(code)
            }
        )
    }

    if (itemForQuantityOrNoteEdit != null) {
        val currentItem = itemForQuantityOrNoteEdit!!
        CartItemEditDialog(
            cartItem = currentItem,
            onDismiss = { itemForQuantityOrNoteEdit = null },
            onSave = { newQty, newNote ->
                onUpdateQuantity(currentItem.product.id, newQty)
                onUpdateItemNote(currentItem.product.id, newNote)
                itemForQuantityOrNoteEdit = null
            },
            onDelete = {
                onRemoveFromCart(currentItem.product.id)
                itemForQuantityOrNoteEdit = null
            }
        )
    }

    if (showCheckoutDialog) {
        CheckoutPaymentDialog(
            cartItems = cartItems,
            customers = customers,
            selectedCustomer = selectedCustomer,
            settings = settings,
            canGiveDiscount = activeCashier?.canGiveDiscount ?: true,
            discountInput = discountInput,
            serviceFeeInput = serviceFeeInput,
            transactionNote = transactionNote,
            paymentMethod = paymentMethod,
            amountPaidInput = amountPaidInput,
            cartCalculation = cartCalculation,
            onUpdateQuantity = onUpdateQuantity,
            onEditCartItem = { itemForQuantityOrNoteEdit = it },
            onRemoveFromCart = onRemoveFromCart,
            onClearCart = {
                onClearCart()
                showCheckoutDialog = false
            },
            onSelectCustomer = onSelectCustomer,
            onSetDiscount = onSetDiscount,
            onSetServiceFee = onSetServiceFee,
            onSetTransactionNote = onSetTransactionNote,
            onSetPaymentMethod = onSetPaymentMethod,
            onSetAmountPaidInput = onSetAmountPaidInput,
            onDismiss = { showCheckoutDialog = false },
            onConfirmPay = {
                showCheckoutDialog = false
                onSubmitCheckout()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Search + Barcode Scanner + Grid/List Toggle Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari nama, SKU, atau barcode...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus pencarian")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pos_search_input")
                    )

                    Button(
                        onClick = { showScannerDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        modifier = Modifier.testTag("btn_pos_scan_barcode")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
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

                Spacer(modifier = Modifier.height(8.dp))

                // Category Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("Semua Kategori") }
                        )
                    }
                    items(categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = {
                                selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
                            },
                            label = { Text(category.name) },
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
            }
        }

        // Products Area
        Box(modifier = Modifier.weight(1f)) {
            if (activeProducts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Produk tidak ditemukan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Coba kata kunci lain atau pilih kategori Semua.",
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
                        PosProductGridCard(
                            product = product,
                            cartItem = cartItem,
                            allowNegativeStock = settings.allowNegativeStock || !settings.autoReduceStock,
                            onAdd = { onAddToCart(product) },
                            onIncrement = {
                                val qty = (cartItem?.quantity ?: 0) + 1
                                onUpdateQuantity(product.id, qty)
                            },
                            onDecrement = {
                                val qty = (cartItem?.quantity ?: 0) - 1
                                onUpdateQuantity(product.id, qty)
                            },
                            onEditItem = {
                                if (cartItem != null) {
                                    itemForQuantityOrNoteEdit = cartItem
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
                        PosProductListRow(
                            product = product,
                            cartItem = cartItem,
                            allowNegativeStock = settings.allowNegativeStock || !settings.autoReduceStock,
                            onAdd = { onAddToCart(product) },
                            onIncrement = {
                                val qty = (cartItem?.quantity ?: 0) + 1
                                onUpdateQuantity(product.id, qty)
                            },
                            onDecrement = {
                                val qty = (cartItem?.quantity ?: 0) - 1
                                onUpdateQuantity(product.id, qty)
                            },
                            onEditItem = {
                                if (cartItem != null) {
                                    itemForQuantityOrNoteEdit = cartItem
                                }
                            }
                        )
                    }
                }
            }
        }

        // Bottom Sticky Cart & Quick Checkout Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = cartItems.isNotEmpty()) {
                            showCheckoutDialog = true
                        }
                ) {
                    BadgedBox(
                        badge = {
                            if (totalCartItemsCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ) {
                                    Text(totalCartItemsCount.toString(), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Keranjang",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (cartItems.isEmpty()) "Keranjang Kosong" else "$totalCartItemsCount Barang (${cartItems.size} Produk)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = SecurityAndFormatUtils.formatRupiah(cartCalculation.finalTotal),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Button(
                    onClick = { showCheckoutDialog = true },
                    enabled = cartItems.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                    modifier = Modifier.testTag("btn_open_checkout_sheet")
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BAYAR",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PosProductGridCard(
    product: ProductEntity,
    cartItem: CartItem?,
    allowNegativeStock: Boolean,
    onAdd: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEditItem: () -> Unit
) {
    val isOutOfStock = !allowNegativeStock && product.stock <= 0
    val isLowStock = product.stock <= product.minStock

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isOutOfStock) {
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
            // Product Thumbnail or Category Icon + Stock Badge
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
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
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

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        product.stock <= 0 -> MaterialTheme.colorScheme.errorContainer
                        isLowStock -> Color(0xFFFEF3C7)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = if (product.stock <= 0) "Habis" else "Stok: ${product.stock}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            product.stock <= 0 -> MaterialTheme.colorScheme.onErrorContainer
                            isLowStock -> Color(0xFF92400E)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
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
                    enabled = !isOutOfStock,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isOutOfStock) "Stok Habis" else "Tambah", style = MaterialTheme.typography.labelLarge)
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
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Kurangi", modifier = Modifier.size(18.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onEditItem() }
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "${cartItem.quantity}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Ubah Jumlah/Catatan",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onIncrement,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Tambah",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PosProductListRow(
    product: ProductEntity,
    cartItem: CartItem?,
    allowNegativeStock: Boolean,
    onAdd: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEditItem: () -> Unit
) {
    val isOutOfStock = !allowNegativeStock && product.stock <= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isOutOfStock) {
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
                        "${product.categoryName} • Stok: ${product.stock} ${product.unit}",
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
                    enabled = !isOutOfStock,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrement) {
                        Icon(Icons.Default.Remove, contentDescription = "Kurangi")
                    }
                    Text(
                        text = "${cartItem.quantity}",
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clickable { onEditItem() }
                            .padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = onIncrement) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah")
                    }
                }
            }
        }
    }
}

@Composable
private fun CartItemEditDialog(
    cartItem: CartItem,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit,
    onDelete: () -> Unit
) {
    var qtyText by remember { mutableStateOf(cartItem.quantity.toString()) }
    var noteText by remember { mutableStateOf(cartItem.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(cartItem.product.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Harga Satuan: ${SecurityAndFormatUtils.formatRupiah(cartItem.product.sellPrice)} / ${cartItem.product.unit}",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Jumlah (${cartItem.product.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Catatan Khusus Item (Opsional)") },
                    placeholder = { Text("Contoh: Tanpa es, pedas sedang, bungkus") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = qtyText.toIntOrNull() ?: 1
                    onSave(q, noteText)
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hapus Item")
                }
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckoutPaymentDialog(
    cartItems: List<CartItem>,
    customers: List<CustomerEntity>,
    selectedCustomer: CustomerEntity?,
    settings: StoreSettingsEntity,
    canGiveDiscount: Boolean,
    discountInput: Double,
    serviceFeeInput: Double,
    transactionNote: String,
    paymentMethod: String,
    amountPaidInput: String,
    cartCalculation: CartCalculation,
    onUpdateQuantity: (Long, Int) -> Unit,
    onEditCartItem: (CartItem) -> Unit,
    onRemoveFromCart: (Long) -> Unit,
    onClearCart: () -> Unit,
    onSelectCustomer: (CustomerEntity?) -> Unit,
    onSetDiscount: (Double) -> Unit,
    onSetServiceFee: (Double) -> Unit,
    onSetTransactionNote: (String) -> Unit,
    onSetPaymentMethod: (String) -> Unit,
    onSetAmountPaidInput: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirmPay: () -> Unit
) {
    val paymentMethods = listOf("Tunai", "QRIS", "Transfer", "Debit", "Kredit", "Lainnya")
    var discountText by remember(discountInput) {
        mutableStateOf(if (discountInput == 0.0) "" else discountInput.toLong().toString())
    }
    var serviceFeeText by remember(serviceFeeInput) {
        mutableStateOf(if (serviceFeeInput == 0.0) "" else serviceFeeInput.toLong().toString())
    }
    var showCustomerDialog by remember { mutableStateOf(false) }

    if (showCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showCustomerDialog = false },
            title = { Text("Pilih Pelanggan", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedCustomer == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectCustomer(null)
                                showCustomerDialog = false
                            }
                    ) {
                        Text(
                            text = "Pelanggan Umum (Tanpa Member)",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    customers.forEach { cust ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedCustomer?.id == cust.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectCustomer(cust)
                                    showCustomerDialog = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(cust.name, fontWeight = FontWeight.Bold)
                                if (cust.phone.isNotBlank()) {
                                    Text(cust.phone, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerDialog = false }) {
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
                .fillMaxSize()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Keranjang & Pembayaran",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${cartItems.sumOf { it.quantity }} item dalam pesanan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        TextButton(
                            onClick = onClearCart,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kosongkan")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                HorizontalDivider()

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Customer Selector
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCustomerDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Pelanggan", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        selectedCustomer?.name ?: "Pelanggan Umum",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                "Ubah",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Cart Items List
                    Text("Daftar Barang", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    cartItems.forEach { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.product.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${SecurityAndFormatUtils.formatRupiah(item.product.sellPrice)} x ${item.quantity} ${item.product.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (item.note.isNotBlank()) {
                                            Text(
                                                "Catatan: ${item.note}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Text(
                                        text = SecurityAndFormatUtils.formatRupiah(item.subtotal),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { onEditCartItem(item) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Catatan / Ubah Jml", style = MaterialTheme.typography.labelMedium)
                                        }
                                        TextButton(
                                            onClick = { onRemoveFromCart(item.product.id) },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Hapus", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onUpdateQuantity(item.product.id, item.quantity - 1) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Kurangi")
                                        }
                                        Text(
                                            text = "${item.quantity}",
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        )
                                        IconButton(
                                            onClick = { onUpdateQuantity(item.product.id, item.quantity + 1) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Tambah")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Discount, Additional Fee, Transaction Note
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (settings.enableDiscount && canGiveDiscount) {
                            OutlinedTextField(
                                value = discountText,
                                onValueChange = {
                                    discountText = it.filter { ch -> ch.isDigit() }
                                    onSetDiscount(discountText.toDoubleOrNull() ?: 0.0)
                                },
                                label = { Text("Diskon (Rp)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = serviceFeeText,
                            onValueChange = {
                                serviceFeeText = it.filter { ch -> ch.isDigit() }
                                onSetServiceFee(serviceFeeText.toDoubleOrNull() ?: 0.0)
                            },
                            label = { Text("Biaya Tambahan (Rp)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = transactionNote,
                        onValueChange = onSetTransactionNote,
                        label = { Text("Catatan Transaksi (Opsional)") },
                        placeholder = { Text("Contoh: Meja 4 / Pesanan ambil jam 5") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Price Breakdown Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SummaryLine("Subtotal", SecurityAndFormatUtils.formatRupiah(cartCalculation.subtotal))
                            if (cartCalculation.discountAmount > 0) {
                                SummaryLine(
                                    "Diskon",
                                    "-${SecurityAndFormatUtils.formatRupiah(cartCalculation.discountAmount)}",
                                    valueColor = Color(0xFF059669)
                                )
                            }
                            if (settings.enableTax && cartCalculation.taxAmount > 0) {
                                SummaryLine(
                                    "Pajak (${SecurityAndFormatUtils.formatNumber(cartCalculation.taxPercent)}%)",
                                    SecurityAndFormatUtils.formatRupiah(cartCalculation.taxAmount)
                                )
                            }
                            if (cartCalculation.serviceFee > 0) {
                                SummaryLine(
                                    "Biaya Tambahan",
                                    SecurityAndFormatUtils.formatRupiah(cartCalculation.serviceFee)
                                )
                            }
                            if (cartCalculation.roundingAmount != 0.0) {
                                SummaryLine(
                                    "Pembulatan",
                                    SecurityAndFormatUtils.formatRupiah(cartCalculation.roundingAmount)
                                )
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "TOTAL HARUS DIBAYAR",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    SecurityAndFormatUtils.formatRupiah(cartCalculation.finalTotal),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Payment Method Selection
                    Text("Metode Pembayaran", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        paymentMethods.forEach { method ->
                            FilterChip(
                                selected = paymentMethod.equals(method, ignoreCase = true),
                                onClick = {
                                    onSetPaymentMethod(method)
                                    if (!method.equals("Tunai", ignoreCase = true)) {
                                        onSetAmountPaidInput(cartCalculation.finalTotal.toLong().toString())
                                    }
                                },
                                label = { Text(method, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    // Cash Input & Automatic Change Calculation
                    if (paymentMethod.equals("Tunai", ignoreCase = true)) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = amountPaidInput,
                                    onValueChange = { raw ->
                                        onSetAmountPaidInput(raw.filter { it.isDigit() })
                                    },
                                    label = { Text("Input Uang Diterima (Rp)") },
                                    placeholder = { Text(cartCalculation.finalTotal.toLong().toString()) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_cash_received")
                                )

                                // Quick Cash Presets
                                val exactTotal = cartCalculation.finalTotal.toLong()
                                val presets = listOf(
                                    "Uang Pas" to exactTotal,
                                    "20.000" to 20000L,
                                    "50.000" to 50000L,
                                    "100.000" to 100000L,
                                    "200.000" to 200000L
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(presets) { (label, amount) ->
                                        OutlinedButton(
                                            onClick = { onSetAmountPaidInput(amount.toString()) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(label, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                HorizontalDivider()

                                SummaryLine(
                                    "Nominal Harus Dibayar",
                                    SecurityAndFormatUtils.formatRupiah(cartCalculation.finalTotal)
                                )
                                SummaryLine(
                                    "Uang Diterima",
                                    SecurityAndFormatUtils.formatRupiah(cartCalculation.amountPaid)
                                )

                                val isEnough = cartCalculation.amountPaid >= cartCalculation.finalTotal
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEnough) "Kembalian" else "Kurang Bayar",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isEnough) {
                                            SecurityAndFormatUtils.formatRupiah(cartCalculation.changeAmount)
                                        } else {
                                            SecurityAndFormatUtils.formatRupiah(cartCalculation.finalTotal - cartCalculation.amountPaid)
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isEnough) Color(0xFF059669) else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Big "BAYAR" Button
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isCashValid = !paymentMethod.equals("Tunai", ignoreCase = true) ||
                        cartCalculation.amountPaid >= cartCalculation.finalTotal
                    Button(
                        onClick = onConfirmPay,
                        enabled = cartItems.isNotEmpty() && isCashValid,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(54.dp)
                            .testTag("btn_pay_checkout")
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BAYAR • ${SecurityAndFormatUtils.formatRupiah(cartCalculation.finalTotal)}",
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
private fun SummaryLine(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
