package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Bill
import com.example.data.model.BillSourceType
import com.example.data.model.OcrConfidence
import com.example.data.model.ReportSummary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LossRed
import com.example.ui.theme.LossRedContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.ui.viewmodel.UiState
import com.example.util.Formatters

@Composable
fun DashboardScreen(
    billsState: UiState<List<Bill>>,
    todaySummary: ReportSummary,
    isOnline: Boolean,
    isDeletingBill: Boolean,
    onScanBillPhoto: () -> Unit,
    onNewPaper: () -> Unit,
    onEditBill: (Bill) -> Unit,
    onDeleteBill: (Bill) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf<BillSourceType?>(null) }
    var billPendingDelete by remember { mutableStateOf<Bill?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!isOnline) {
                item {
                    Surface(
                        color = WarningAmberContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = WarningAmber)
                            Text(
                                text = stringResource(R.string.error_no_internet),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Today's Summary Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Today's Hisab (${Formatters.shortDate(System.currentTimeMillis())})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = "${todaySummary.billCount} Bills",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                KpiColumn("Sales", Formatters.inr(todaySummary.totalSellingAmount), MaterialTheme.colorScheme.onPrimary)
                                KpiColumn("Cost", Formatters.inr(todaySummary.totalPurchaseCost), MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f))
                                KpiColumn("Profit", Formatters.inr(todaySummary.totalProfit), Color(0xFFFFD68A))
                            }

                            Surface(
                                color = Color.Black.copy(alpha = 0.16f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TrackerMiniMetric("Masoor Full", Formatters.qty(todaySummary.masoorFull.quantity), Formatters.inr(todaySummary.masoorFull.sellingAmount))
                                    TrackerMiniMetric("Masoor Half", Formatters.qty(todaySummary.masoorHalf.quantity), Formatters.inr(todaySummary.masoorHalf.sellingAmount))
                                    TrackerMiniMetric("Roti", Formatters.qty(todaySummary.roti.quantity), Formatters.inr(todaySummary.roti.sellingAmount))
                                }
                            }
                        }
                    }
                }
            }

            // Primary Action Cards: Scan Photo & New Paper
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onScanBillPhoto,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("scan_bill_photo_button"),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Bill Photo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = onNewPaper,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("new_paper_button"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Paper", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Search and Source Filter
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search bills by title, party, or product...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bill_search_input"),
                        shape = MaterialTheme.shapes.medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sourceFilter == null,
                            onClick = { sourceFilter = null },
                            label = { Text("All Bills") }
                        )
                        FilterChip(
                            selected = sourceFilter == BillSourceType.PHOTO,
                            onClick = {
                                sourceFilter = if (sourceFilter == BillSourceType.PHOTO) null else BillSourceType.PHOTO
                            },
                            label = { Text("Photo Scans") }
                        )
                        FilterChip(
                            selected = sourceFilter == BillSourceType.PAPER,
                            onClick = {
                                sourceFilter = if (sourceFilter == BillSourceType.PAPER) null else BillSourceType.PAPER
                            },
                            label = { Text("New Papers") }
                        )
                    }
                }
            }

            when (billsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
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
                                text = billsState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    val filtered = billsState.data.filter { bill ->
                        val matchesSource = sourceFilter == null || bill.sourceEnum == sourceFilter
                        val q = searchQuery.trim().lowercase()
                        val matchesQuery = q.isBlank() ||
                            bill.title.lowercase().contains(q) ||
                            bill.vendorOrParty.lowercase().contains(q) ||
                            "#${bill.billNumber}".contains(q) ||
                            bill.parsedItems.any { it.productName.lowercase().contains(q) }
                        matchesSource && matchesQuery
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = if (billsState.data.isEmpty()) "No bills saved yet" else "No matching bills",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Upload a Marathi, Hindi, or English bill photo for AI extraction, or tap New Paper to create a blank billing sheet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(onClick = onNewPaper) {
                                        Icon(Icons.Filled.AddCircle, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create First Paper")
                                    }
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { bill ->
                            BillLedgerCard(
                                bill = bill,
                                onEdit = { onEditBill(bill) },
                                onDelete = { billPendingDelete = bill }
                            )
                        }
                    }
                }
            }
        }
    }

    if (billPendingDelete != null) {
        val target = billPendingDelete!!
        AlertDialog(
            onDismissRequest = { if (!isDeletingBill) billPendingDelete = null },
            title = { Text("Delete Bill #${target.billNumber}?") },
            text = {
                Text("Are you sure you want to permanently delete \"${target.title}\"? All reports will recalculate immediately.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBill(target)
                        billPendingDelete = null
                    },
                    enabled = !isDeletingBill,
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { billPendingDelete = null },
                    enabled = !isDeletingBill
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun KpiColumn(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.85f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TrackerMiniMetric(name: String, qty: String, sales: String) {
    Column {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
        Text(
            text = "$qty • $sales",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BillLedgerCard(
    bill: Bill,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val profitPositive = bill.totalProfit >= 0.0
    val items = remember(bill.items) { bill.parsedItems }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onEdit)
            .testTag("bill_card_${bill.billNumber}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#${bill.billNumber}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = Formatters.date(bill.billDateMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (bill.hasImage) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = stringResource(R.string.cd_bill_photo),
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = bill.title.ifBlank { "Untitled Bill" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (bill.vendorOrParty.isNotBlank()) {
                        Text(
                            text = bill.vendorOrParty,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_bill_${bill.billNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.cd_edit_bill),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_bill_${bill.billNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete_bill),
                            tint = LossRed
                        )
                    }
                }
            }

            // Item preview chips
            val previewText = items.take(3).joinToString("  •  ") {
                "${it.productName} (${Formatters.qty(it.quantity)} ${it.unitEnum.shortLabel})"
            } + if (items.size > 3) "  +${items.size - 3} more" else ""
            if (previewText.isNotBlank()) {
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (bill.confidenceEnum == OcrConfidence.LOW) {
                Surface(
                    color = WarningAmberContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                        Text(
                            text = "OCR marked unclear — tap to review",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Formatters.inr(bill.totalPurchaseCost),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                }
                Column {
                    Text("Sales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Formatters.inr(bill.totalSellingAmount),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                }
                Surface(
                    color = if (profitPositive) ProfitGreenContainer else LossRedContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(
                            text = if (profitPositive) "Profit" else "Loss",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (profitPositive) ProfitGreen else LossRed
                        )
                        Text(
                            text = Formatters.inr(bill.totalProfit),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = if (profitPositive) ProfitGreen else LossRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
