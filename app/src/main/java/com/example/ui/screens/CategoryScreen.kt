package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CategoryEntity
import com.example.data.ProductEntity
import com.example.ui.components.CategoryVisuals

@Composable
fun CategoryScreen(
    categories: List<CategoryEntity>,
    products: List<ProductEntity>,
    onSaveCategory: (CategoryEntity) -> Unit,
    onMoveCategoryProducts: (CategoryEntity, CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity, CategoryEntity?) -> Unit
) {
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryToMoveFrom by remember { mutableStateOf<CategoryEntity?>(null) }

    val productCountByCategory = remember(products) {
        products.groupingBy { it.categoryId }.eachCount()
    }

    if (isAddingNew || editingCategory != null) {
        CategoryFormDialog(
            initialCategory = editingCategory ?: CategoryEntity(name = ""),
            onDismiss = {
                isAddingNew = false
                editingCategory = null
            },
            onSave = { cat ->
                onSaveCategory(cat)
                isAddingNew = false
                editingCategory = null
            }
        )
    }

    if (categoryToMoveFrom != null) {
        val fromCat = categoryToMoveFrom!!
        val otherCategories = categories.filter { it.id != fromCat.id }
        AlertDialog(
            onDismissRequest = { categoryToMoveFrom = null },
            title = { Text("Pindahkan Menu dari '${fromCat.name}'", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (otherCategories.isEmpty()) {
                        Text("Buat kategori lain terlebih dahulu untuk memindahkan menu.")
                    } else {
                        Text(
                            "Pilih kategori tujuan untuk memindahkan semua menu dari '${fromCat.name}':",
                            style = MaterialTheme.typography.bodySmall
                        )
                        otherCategories.forEach { targetCat ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onMoveCategoryProducts(fromCat, targetCat)
                                        categoryToMoveFrom = null
                                    }
                            ) {
                                Text(
                                    text = targetCat.name,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { categoryToMoveFrom = null }) {
                    Text("Batal")
                }
            }
        )
    }

    if (categoryToDelete != null) {
        val target = categoryToDelete!!
        val countInCat = productCountByCategory[target.id] ?: 0
        val otherCategories = categories.filter { it.id != target.id }
        var selectedMoveTarget by remember(target) {
            mutableStateOf(otherCategories.firstOrNull())
        }

        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            icon = {
                if (countInCat > 0) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFD97706)
                    )
                }
            },
            title = {
                Text(
                    text = if (countInCat > 0) "Peringatan: Kategori Masih Memiliki Menu" else "Hapus Kategori?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (countInCat == 0) {
                        Text("Apakah Anda yakin ingin menghapus kategori '${target.name}'?")
                    } else {
                        Text(
                            "Kategori '${target.name}' masih memiliki $countInCat produk/menu. Produk tidak akan dihapus. Pilih kategori tujuan untuk memindahkan $countInCat menu tersebut terlebih dahulu:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (otherCategories.isEmpty()) {
                            Text(
                                "Tidak ada kategori lain yang tersedia. Silakan tambah kategori baru terlebih dahulu sebelum menghapus kategori ini.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            otherCategories.forEach { dest ->
                                val isSelected = selectedMoveTarget?.id == dest.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedMoveTarget = dest }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(dest.name, fontWeight = FontWeight.Bold)
                                        if (isSelected) {
                                            Text("Tujuan Pindah", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCategory(target, selectedMoveTarget)
                        categoryToDelete = null
                    },
                    enabled = countInCat == 0 || selectedMoveTarget != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_delete_category")
                ) {
                    Text(if (countInCat > 0) "Pindahkan Menu & Hapus" else "Ya, Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
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
                modifier = Modifier.testTag("fab_add_category")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Kategori")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Kategori", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(categories, key = { it.id }) { cat ->
                val catColor = CategoryVisuals.parseColor(cat.colorHex)
                val count = productCountByCategory[cat.id] ?: 0

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingCategory = cat },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(catColor.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = CategoryVisuals.getIcon(cat.iconName),
                                    contentDescription = cat.name,
                                    tint = catColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$count Menu dalam kategori ini",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row {
                            if (count > 0 && categories.size > 1) {
                                IconButton(onClick = { categoryToMoveFrom = cat }) {
                                    Icon(
                                        Icons.Default.DriveFileMove,
                                        contentDescription = "Pindahkan Menu ke Kategori Lain",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            IconButton(onClick = { editingCategory = cat }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Kategori", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { categoryToDelete = cat }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus Kategori", tint = MaterialTheme.colorScheme.error)
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
private fun CategoryFormDialog(
    initialCategory: CategoryEntity,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit
) {
    var name by remember { mutableStateOf(initialCategory.name) }
    var selectedIcon by remember { mutableStateOf(initialCategory.iconName) }
    var selectedColor by remember { mutableStateOf(initialCategory.colorHex) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCategory.id == 0L) "Tambah Kategori Menu" else "Ubah Kategori Menu",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMsg = null
                    },
                    label = { Text("Nama Kategori *") },
                    placeholder = { Text("Contoh: Makanan, Minuman, Snack, Dessert") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_category_name")
                )

                Text("Pilih Ikon Kategori", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryVisuals.iconOptions.forEach { (iconKey, vector) ->
                        val isSelected = selectedIcon == iconKey
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedIcon = iconKey },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = iconKey,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text("Pilih Warna Kategori", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CategoryVisuals.colorOptions.forEach { hex ->
                        val color = CategoryVisuals.parseColor(hex)
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }

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
                    if (name.trim().isEmpty()) {
                        errorMsg = "Nama kategori tidak boleh kosong!"
                        return@Button
                    }
                    onSave(
                        initialCategory.copy(
                            name = name.trim(),
                            iconName = selectedIcon,
                            colorHex = selectedColor
                        )
                    )
                },
                modifier = Modifier.testTag("btn_save_category")
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
