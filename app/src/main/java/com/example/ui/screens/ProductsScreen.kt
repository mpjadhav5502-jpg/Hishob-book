package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CustomProduct
import com.example.data.model.ProductCategory
import com.example.data.model.UnitType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LossRed
import com.example.ui.theme.ProfitGreen
import com.example.ui.viewmodel.UiState
import com.example.util.Formatters

@Composable
fun ProductsScreen(
    productsState: UiState<List<CustomProduct>>,
    onSaveProduct: (
        existingId: String,
        name: String,
        unitType: UnitType,
        category: ProductCategory,
        purchasePrice: Double,
        sellingPrice: Double
    ) -> Unit,
    onDeleteProduct: (CustomProduct) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<CustomProduct?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CustomProduct?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Products Catalog", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Unlimited custom items + Masoor Full, Masoor Half & Roti presets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            editingProduct = null
                            showEditDialog = true
                        },
                        modifier = Modifier.testTag("add_product_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search products...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when (productsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = productsState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    val filtered = productsState.data.filter {
                        search.isBlank() || it.name.contains(search.trim(), ignoreCase = true)
                    }
                    items(filtered, key = { it.id }) { prod ->
                        val margin = prod.defaultSellingPrice - prod.defaultPurchasePrice
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(prod.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${prod.unitEnum.label} • ${prod.categoryEnum.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Buy ${Formatters.inr(prod.defaultPurchasePrice)}  |  Sell ${Formatters.inr(prod.defaultSellingPrice)}  |  Profit ${Formatters.inr(margin)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = if (margin >= 0.0) ProfitGreen else LossRed
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            editingProduct = prod
                                            showEditDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit product", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { pendingDelete = prod }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete product", tint = LossRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        ProductEditorDialog(
            initial = editingProduct,
            onDismiss = { showEditDialog = false },
            onSave = { name, unit, cat, buy, sell ->
                onSaveProduct(editingProduct?.id.orEmpty(), name, unit, cat, buy, sell)
                showEditDialog = false
            }
        )
    }

    if (pendingDelete != null) {
        val target = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("Removing this preset from the catalog will not alter existing saved bills.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(target)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductEditorDialog(
    initial: CustomProduct?,
    onDismiss: () -> Unit,
    onSave: (String, UnitType, ProductCategory, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var unit by remember { mutableStateOf(initial?.unitEnum ?: UnitType.UNIT) }
    var category by remember { mutableStateOf(initial?.categoryEnum ?: ProductCategory.CUSTOM) }
    var buyText by remember { mutableStateOf(if ((initial?.defaultPurchasePrice ?: 0.0) == 0.0) "" else Formatters.qty(initial!!.defaultPurchasePrice)) }
    var sellText by remember { mutableStateOf(if ((initial?.defaultSellingPrice ?: 0.0) == 0.0) "" else Formatters.qty(initial!!.defaultSellingPrice)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Custom Product" else "Edit Product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(80)
                        if (initial == null) {
                            category = ProductCategory.inferFrom(it, unit)
                        }
                    },
                    label = { Text("Product Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Unit", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UnitType.entries.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = {
                                unit = u
                                if (initial == null) {
                                    category = ProductCategory.inferFrom(name, u)
                                }
                            },
                            label = { Text(u.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = buyText,
                        onValueChange = { buyText = it },
                        label = { Text("Default Buy ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sellText,
                        onValueChange = { sellText = it },
                        label = { Text("Default Sell ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = buyText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val s = sellText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    onSave(name.trim(), unit, category, p, s)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
