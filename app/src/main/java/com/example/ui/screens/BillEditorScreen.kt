package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.BillLineItem
import com.example.data.model.CustomProduct
import com.example.data.model.OcrConfidence
import com.example.data.model.ProductCategory
import com.example.data.model.UnitType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LossRed
import com.example.ui.theme.LossRedContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.ui.viewmodel.BillEditorState
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillEditorScreen(
    editorState: BillEditorState,
    catalogProducts: List<CustomProduct>,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onVendorChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onNotesChange: (String) -> Unit,
    onPickPhoto: (runOcr: Boolean) -> Unit,
    onRetryOcr: () -> Unit,
    onRemovePhoto: () -> Unit,
    onAddQuickPreset: (ProductCategory) -> Unit,
    onAddCatalogProduct: (CustomProduct) -> Unit,
    onAddBlankRow: () -> Unit,
    onUpdateLine: (
        lineId: String,
        productName: String?,
        unitType: UnitType?,
        category: ProductCategory?,
        quantity: Double?,
        purchasePrice: Double?,
        sellingPrice: Double?
    ) -> Unit,
    onRemoveLine: (String) -> Unit,
    onSave: () -> Unit
) {
    BackHandler(onBack = onBack)

    var showDatePicker by remember { mutableStateOf(false) }
    var showCatalogDialog by remember { mutableStateOf(false) }
    var showPhotoZoomDialog by remember { mutableStateOf(false) }
    var showReplacePhotoChoice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Editor Header Bar
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = if (editorState.isNewBill) {
                                if (editorState.photoBitmap != null) "Scan & Edit Bill" else "New Paper"
                            } else {
                                "Edit Bill #${editorState.existingBill?.billNumber ?: ""}"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (editorState.isNewBill) "Creates a brand-new ledger record" else "Updates saved bill & reports",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = { onPickPhoto(true) },
                    enabled = !editorState.isScanningOcr && !editorState.isSaving
                ) {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan Photo")
                }
            }
        }

        // Scrollable Body
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 760.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // OCR Progress Banner
            if (editorState.isScanningOcr) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                            Column {
                                Text("Scanning Bill with Gemini Vision...", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Reading Marathi, Hindi & English items, units, and prices.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // OCR Clarity Warning / Error Banner
            if (editorState.ocrWarning.isNotBlank() || editorState.ocrError != null) {
                item {
                    val isError = editorState.ocrError != null || editorState.ocrConfidence == OcrConfidence.LOW
                    Surface(
                        color = if (isError) LossRedContainer else WarningAmberContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = if (isError) LossRed else WarningAmber
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (isError) "Manual Review Required" else "Verify Extracted Items",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isError) LossRed else WarningAmber
                                )
                                Text(
                                    text = editorState.ocrError ?: editorState.ocrWarning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Attached Photo Preview & Controls
            item {
                PhotoAttachmentCard(
                    bitmap = editorState.photoBitmap,
                    hasStoredImage = editorState.existingBill?.hasImage == true && !editorState.removeExistingImage,
                    isScanning = editorState.isScanningOcr,
                    onZoom = { showPhotoZoomDialog = true },
                    onReplace = { showReplacePhotoChoice = true },
                    onRetryOcr = onRetryOcr,
                    onRemove = onRemovePhoto
                )
            }

            // Bill Header Metadata
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Bill Details", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = editorState.title,
                            onValueChange = onTitleChange,
                            label = { Text("Bill Title / Counter Label *") },
                            placeholder = { Text("e.g. Morning Counter or Supplier Bill") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bill_title_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = editorState.vendorOrParty,
                                onValueChange = onVendorChange,
                                label = { Text("Party / Vendor") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier
                                    .height(56.dp)
                                    .padding(top = 6.dp),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(Formatters.date(editorState.billDateMillis))
                            }
                        }
                        OutlinedTextField(
                            value = editorState.notes,
                            onValueChange = onNotesChange,
                            label = { Text("Notes (Optional)") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Quick-Add Product Presets + Catalog Picker
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Products (${editorState.items.size} rows)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { onAddQuickPreset(ProductCategory.MASOOR_FULL) },
                            label = { Text("+ Masoor Full") },
                            modifier = Modifier.testTag("quick_add_masoor_full")
                        )
                        AssistChip(
                            onClick = { onAddQuickPreset(ProductCategory.MASOOR_HALF) },
                            label = { Text("+ Masoor Half") },
                            modifier = Modifier.testTag("quick_add_masoor_half")
                        )
                        AssistChip(
                            onClick = { onAddQuickPreset(ProductCategory.ROTI) },
                            label = { Text("+ Roti") },
                            modifier = Modifier.testTag("quick_add_roti")
                        )
                        AssistChip(
                            onClick = { showCatalogDialog = true },
                            label = { Text("Catalog (${catalogProducts.size})") },
                            leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        AssistChip(
                            onClick = onAddBlankRow,
                            label = { Text("+ Custom Row") },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("add_custom_row_button")
                        )
                    }
                }
            }

            // Editable Product Line Items
            itemsIndexed(editorState.items, key = { _, item -> item.lineId }) { index, item ->
                EditableLineItemCard(
                    index = index,
                    item = item,
                    onUpdate = { name, unit, cat, qty, pPrice, sPrice ->
                        onUpdateLine(item.lineId, name, unit, cat, qty, pPrice, sPrice)
                    },
                    onRemove = { onRemoveLine(item.lineId) }
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        // Sticky Bottom Summary & Save Footer
        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (editorState.saveError != null) {
                    Surface(
                        color = LossRedContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = editorState.saveError,
                            style = MaterialTheme.typography.bodySmall,
                            color = LossRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Qty ${Formatters.qty(editorState.totals.totalQuantity)} • Cost ${Formatters.inr(editorState.totals.totalPurchaseCost)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Sales ${Formatters.inr(editorState.totals.totalSellingAmount)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                fontWeight = FontWeight.Bold
                            )
                            val pPositive = editorState.totals.totalProfit >= 0.0
                            Text(
                                text = "${if (pPositive) "+" else ""}${Formatters.inr(editorState.totals.totalProfit)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = if (pPositive) ProfitGreen else LossRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onSave,
                        enabled = !editorState.isSaving && !editorState.isScanningOcr,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("save_bill_button"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (editorState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...")
                        } else {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (editorState.isNewBill) "Save Bill" else "Update Bill")
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = editorState.billDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            onDateChange(Formatters.utcPickerMillisToLocalNoon(it))
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showCatalogDialog) {
        AlertDialog(
            onDismissRequest = { showCatalogDialog = false },
            title = { Text("Pick from Saved Products") },
            text = {
                if (catalogProducts.isEmpty()) {
                    Text("No saved products yet. Add custom items in the Products tab or directly in rows.")
                } else {
                    LazyColumn(
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(catalogProducts) { _, prod ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddCatalogProduct(prod)
                                        showCatalogDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(prod.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${prod.unitEnum.label} • Cost ${Formatters.inr(prod.defaultPurchasePrice)} • Sale ${Formatters.inr(prod.defaultSellingPrice)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCatalogDialog = false }) { Text("Close") }
            }
        )
    }

    if (showReplacePhotoChoice) {
        AlertDialog(
            onDismissRequest = { showReplacePhotoChoice = false },
            title = { Text("Upload / Replace Bill Photo") },
            text = { Text("Would you like to run AI OCR on the new photo, or only attach/replace the stored bill image?") },
            confirmButton = {
                Button(
                    onClick = {
                        showReplacePhotoChoice = false
                        onPickPhoto(true)
                    }
                ) {
                    Text("Photo + AI Scan")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showReplacePhotoChoice = false
                        onPickPhoto(false)
                    }
                ) {
                    Text("Attach Photo Only")
                }
            }
        )
    }

    if (showPhotoZoomDialog && editorState.photoBitmap != null) {
        Dialog(onDismissRequest = { showPhotoZoomDialog = false }) {
            Card(shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Original Bill Photo", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { showPhotoZoomDialog = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close preview")
                        }
                    }
                    Image(
                        bitmap = editorState.photoBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_bill_photo),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoAttachmentCard(
    bitmap: Bitmap?,
    hasStoredImage: Boolean,
    isScanning: Boolean,
    onZoom: () -> Unit,
    onReplace: () -> Unit,
    onRetryOcr: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (bitmap == null && !hasStoredImage) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isScanning, onClick = onReplace)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Column {
                        Text("Attach Bill Photo (Optional)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Upload from gallery for Marathi, Hindi or English AI OCR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (bitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(onClick = onZoom)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_bill_photo),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Tap to Zoom", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    Text("Loading stored bill photo...", style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReplace,
                        enabled = !isScanning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Replace")
                    }
                    if (bitmap != null) {
                        OutlinedButton(
                            onClick = onRetryOcr,
                            enabled = !isScanning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Re-Scan")
                        }
                    }
                    IconButton(onClick = onRemove, enabled = !isScanning) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove bill photo", tint = LossRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableLineItemCard(
    index: Int,
    item: BillLineItem,
    onUpdate: (
        productName: String?,
        unitType: UnitType?,
        category: ProductCategory?,
        quantity: Double?,
        purchasePrice: Double?,
        sellingPrice: Double?
    ) -> Unit,
    onRemove: () -> Unit
) {
    var qtyText by remember(item.lineId) { mutableStateOf(Formatters.qty(item.quantity)) }
    var purchaseText by remember(item.lineId) {
        mutableStateOf(if (item.purchasePrice == 0.0) "" else Formatters.qty(item.purchasePrice))
    }
    var sellingText by remember(item.lineId) {
        mutableStateOf(if (item.sellingPrice == 0.0) "" else Formatters.qty(item.sellingPrice))
    }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    // Keep text synchronized when OCR or quick-add populates fields externally
    LaunchedEffect(item.quantity, item.purchasePrice, item.sellingPrice) {
        val currentQty = qtyText.toDoubleOrNull()
        if (!(qtyText.isEmpty() && item.quantity == 0.0) &&
            (currentQty == null || kotlin.math.abs(currentQty - item.quantity) > 0.005)
        ) {
            qtyText = Formatters.qty(item.quantity)
        }
        val currentP = purchaseText.toDoubleOrNull() ?: 0.0
        if (!(purchaseText.isEmpty() && item.purchasePrice == 0.0) &&
            kotlin.math.abs(currentP - item.purchasePrice) > 0.005
        ) {
            purchaseText = if (item.purchasePrice == 0.0) "" else Formatters.qty(item.purchasePrice)
        }
        val currentS = sellingText.toDoubleOrNull() ?: 0.0
        if (!(sellingText.isEmpty() && item.sellingPrice == 0.0) &&
            kotlin.math.abs(currentS - item.sellingPrice) > 0.005
        ) {
            sellingText = if (item.sellingPrice == 0.0) "" else Formatters.qty(item.sellingPrice)
        }
    }

    val borderColor = if (item.needsReview) WarningAmber else MaterialTheme.colorScheme.outlineVariant

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, MaterialTheme.shapes.large)
            .testTag("line_item_card_$index")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Item #${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.categoryEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (item.needsReview) {
                        Text(
                            text = "Check price",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("remove_line_${index}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_item),
                        tint = LossRed
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = item.productName,
                    onValueChange = { newName ->
                        onUpdate(newName, null, null, null, null, null)
                    },
                    label = { Text("Product Name") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("product_name_input_$index")
                )

                Box {
                    OutlinedButton(
                        onClick = { unitMenuExpanded = true },
                        modifier = Modifier
                            .height(56.dp)
                            .padding(top = 6.dp)
                            .testTag("unit_selector_$index"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(item.unitEnum.label)
                    }
                    DropdownMenu(
                        expanded = unitMenuExpanded,
                        onDismissRequest = { unitMenuExpanded = false }
                    ) {
                        UnitType.entries.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.label) },
                                leadingIcon = {
                                    if (unit == item.unitEnum) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    unitMenuExpanded = false
                                    onUpdate(null, unit, null, null, null, null)
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { raw ->
                        qtyText = raw
                        val parsed = raw.toDoubleOrNull()
                        if (parsed != null && parsed >= 0.0) {
                            onUpdate(null, null, null, parsed, null, null)
                        } else if (raw.isBlank()) {
                            onUpdate(null, null, null, 0.0, null, null)
                        }
                    },
                    label = { Text("Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(0.85f)
                        .testTag("qty_input_$index")
                )

                OutlinedTextField(
                    value = purchaseText,
                    onValueChange = { raw ->
                        purchaseText = raw
                        val parsed = raw.toDoubleOrNull()
                        if (parsed != null && parsed >= 0.0) {
                            onUpdate(null, null, null, null, parsed, null)
                        } else if (raw.isBlank()) {
                            onUpdate(null, null, null, null, 0.0, null)
                        }
                    },
                    label = { Text("Buy ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("purchase_price_input_$index")
                )

                OutlinedTextField(
                    value = sellingText,
                    onValueChange = { raw ->
                        sellingText = raw
                        val parsed = raw.toDoubleOrNull()
                        if (parsed != null && parsed >= 0.0) {
                            onUpdate(null, null, null, null, null, parsed)
                        } else if (raw.isBlank()) {
                            onUpdate(null, null, null, null, null, 0.0)
                        }
                    },
                    label = { Text("Sell ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("selling_price_input_$index")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val rowPositive = item.profit >= 0.0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Cost ${Formatters.inr(item.purchaseTotal)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Sales ${Formatters.inr(item.sellingTotal)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = if (rowPositive) ProfitGreenContainer else LossRedContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Profit ${Formatters.inr(item.profit)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = if (rowPositive) ProfitGreen else LossRed,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
