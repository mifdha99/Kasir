package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.CategoryEntity
import com.example.data.ProductEntity
import com.example.util.SecurityAndFormatUtils
import com.example.viewmodel.ProductSortOption

@Composable
fun ProductScreen(
    products: List<ProductEntity>,
    categories: List<CategoryEntity>,
    onSaveProduct: (ProductEntity) -> Unit,
    onMoveProductCategory: (ProductEntity, CategoryEntity) -> Unit,
    onDeleteProduct: (ProductEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var sortOption by remember { mutableStateOf(ProductSortOption.NAME) }
    var showSortMenu by remember { mutableStateOf(false) }

    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var productToMoveCategory by remember { mutableStateOf<ProductEntity?>(null) }

    val filteredAndSorted = remember(products, searchQuery, selectedCategoryId, sortOption) {
        val filtered = products.filter { p ->
            (selectedCategoryId == null || p.categoryId == selectedCategoryId) &&
                (searchQuery.isBlank() ||
                    p.name.contains(searchQuery, ignoreCase = true) ||
                    p.categoryName.contains(searchQuery, ignoreCase = true) ||
                    p.description.contains(searchQuery, ignoreCase = true))
        }
        when (sortOption) {
            ProductSortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
            ProductSortOption.PRICE_ASC -> filtered.sortedBy { it.sellPrice }
            ProductSortOption.PRICE_DESC -> filtered.sortedByDescending { it.sellPrice }
            ProductSortOption.NEWEST -> filtered.sortedByDescending { it.createdAt }
        }
    }

    if (isAddingNew || editingProduct != null) {
        val defaultCat = categories.firstOrNull()
        ProductFormDialog(
            initialProduct = editingProduct ?: ProductEntity(
                name = "",
                categoryId = defaultCat?.id ?: 0L,
                categoryName = defaultCat?.name ?: "Makanan"
            ),
            categories = categories,
            onDismiss = {
                isAddingNew = false
                editingProduct = null
            },
            onSave = { saved ->
                onSaveProduct(saved)
                isAddingNew = false
                editingProduct = null
            }
        )
    }

    if (productToMoveCategory != null) {
        val targetProduct = productToMoveCategory!!
        AlertDialog(
            onDismissRequest = { productToMoveCategory = null },
            title = { Text("Pindahkan Kategori Menu", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Pilih kategori tujuan untuk '${targetProduct.name}' (Saat ini: ${targetProduct.categoryName}):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    categories.forEach { cat ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (cat.id == targetProduct.categoryId) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveProductCategory(targetProduct, cat)
                                    productToMoveCategory = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cat.name, fontWeight = FontWeight.Bold)
                                if (cat.id == targetProduct.categoryId) {
                                    Text("Saat Ini", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { productToMoveCategory = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    if (productToDelete != null) {
        val target = productToDelete!!
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Hapus Menu?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Apakah Anda yakin ingin menghapus '${target.name}' dari daftar menu?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(target)
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ya, Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddingNew = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_product")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Menu")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Menu", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search & Sort Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari nama makanan atau minuman...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Hapus")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("product_search_input")
                        )

                        Box {
                            OutlinedButton(
                                onClick = { showSortMenu = true },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                            ) {
                                Icon(Icons.Default.Sort, contentDescription = "Urutkan")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(sortOption.label, style = MaterialTheme.typography.labelMedium)
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                ProductSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            sortOption = option
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategoryId == null,
                                onClick = { selectedCategoryId = null },
                                label = { Text("Semua (${products.size})") }
                            )
                        }
                        items(categories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = {
                                    selectedCategoryId = if (selectedCategoryId == cat.id) null else cat.id
                                },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredAndSorted, key = { it.id }) { product ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingProduct = product },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (product.imageUri.isNotBlank()) {
                                AsyncImage(
                                    model = Uri.parse(product.imageUri),
                                    contentDescription = product.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = product.name.take(2).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = product.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!product.isActive) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                "NONAKTIF",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "Kategori: ${product.categoryName} • Satuan: ${product.unit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = SecurityAndFormatUtils.formatRupiah(product.sellPrice),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                if (product.description.isNotBlank()) {
                                    Text(
                                        text = product.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column {
                                IconButton(onClick = { editingProduct = product }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Menu", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { productToMoveCategory = product }) {
                                    Icon(Icons.Default.DriveFileMove, contentDescription = "Pindah Kategori", tint = MaterialTheme.colorScheme.secondary)
                                }
                                IconButton(onClick = { productToDelete = product }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus Menu", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductFormDialog(
    initialProduct: ProductEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (ProductEntity) -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(initialProduct.name) }
    var sellPriceText by remember {
        mutableStateOf(if (initialProduct.sellPrice == 0.0) "" else initialProduct.sellPrice.toLong().toString())
    }
    var buyPriceText by remember {
        mutableStateOf(if (initialProduct.buyPrice == 0.0) "" else initialProduct.buyPrice.toLong().toString())
    }
    var unit by remember { mutableStateOf(initialProduct.unit.ifBlank { "Porsi" }) }
    var selectedCategoryId by remember { mutableStateOf(initialProduct.categoryId) }
    var selectedCategoryName by remember { mutableStateOf(initialProduct.categoryName) }
    var description by remember { mutableStateOf(initialProduct.description) }
    var imageUri by remember { mutableStateOf(initialProduct.imageUri) }
    var isActive by remember { mutableStateOf(initialProduct.isActive) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            imageUri = uri.toString()
        }
    }

    val unitOptions = listOf("Porsi", "Gelas", "Cup", "Paket", "Piring", "Mangkok", "Pcs", "Bungkus")

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialProduct.id == 0L) "Tambah Menu Baru" else "Edit Menu & Harga",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (errorMsg != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMsg!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Photo Picker Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (imageUri.isNotBlank()) {
                            AsyncImage(
                                model = Uri.parse(imageUri),
                                contentDescription = "Foto Menu",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                            }
                        }
                        Column {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Text("Pilih Foto Menu")
                            }
                            if (imageUri.isNotBlank()) {
                                TextButton(onClick = { imageUri = "" }) {
                                    Text("Hapus Foto")
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMsg = null },
                        label = { Text("Nama Makanan / Minuman *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_product_name")
                    )

                    // Category Selection
                    Text("Kategori Menu", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id || selectedCategoryName.equals(cat.name, ignoreCase = true),
                                onClick = {
                                    selectedCategoryId = cat.id
                                    selectedCategoryName = cat.name
                                },
                                label = { Text(cat.name, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    // Sell Price & Optional Buy Price
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sellPriceText,
                            onValueChange = { sellPriceText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Harga Jual (Rp) *") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_product_sell_price")
                        )
                        OutlinedTextField(
                            value = buyPriceText,
                            onValueChange = { buyPriceText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Harga Modal (Opsional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Unit
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Satuan (Porsi, Gelas, Cup, Paket)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(unitOptions) { u ->
                            FilterChip(
                                selected = unit.equals(u, ignoreCase = true),
                                onClick = { unit = u },
                                label = { Text(u) }
                            )
                        }
                    }

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Keterangan Menu (Opsional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Status Active Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Status Menu Tersedia", fontWeight = FontWeight.Bold)
                            Text(
                                "Jika nonaktif, menu tidak tampil di layar pemesanan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Surface(
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (name.trim().isEmpty()) {
                                errorMsg = "Nama menu wajib diisi!"
                                return@Button
                            }
                            val sell = sellPriceText.toDoubleOrNull() ?: 0.0
                            val buy = buyPriceText.toDoubleOrNull() ?: 0.0

                            onSave(
                                initialProduct.copy(
                                    name = name.trim(),
                                    buyPrice = buy,
                                    sellPrice = sell,
                                    unit = unit.trim().ifEmpty { "Porsi" },
                                    categoryId = selectedCategoryId,
                                    categoryName = selectedCategoryName.ifBlank { "Makanan" },
                                    description = description.trim(),
                                    imageUri = imageUri,
                                    isActive = isActive
                                )
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(50.dp)
                            .testTag("btn_save_product")
                    ) {
                        Text("Simpan Menu", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
