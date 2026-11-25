package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryTrackerStats
import com.example.data.model.DailyTrendPoint
import com.example.data.model.ProductReportRow
import com.example.data.model.ReportPeriod
import com.example.data.model.ReportSummary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LossRed
import com.example.ui.theme.LossRedContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.viewmodel.ReportFilterState
import com.example.util.Formatters

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    filterState: ReportFilterState,
    summary: ReportSummary,
    onPeriodSelected: (ReportPeriod) -> Unit,
    onShiftPeriod: (Int) -> Unit,
    onAnchorDateChanged: (Long) -> Unit,
    onCustomRangeChanged: (Long, Long) -> Unit,
    onDownloadPdf: () -> Unit,
    onDownloadCsv: () -> Unit,
    onSharePdf: () -> Unit,
    onShareCsv: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }

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
            // Period Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Accounting Reports", style = MaterialTheme.typography.headlineSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReportPeriod.entries.forEach { period ->
                            FilterChip(
                                selected = filterState.period == period,
                                onClick = {
                                    if (period == ReportPeriod.CUSTOM) {
                                        showRangePicker = true
                                    } else {
                                        onPeriodSelected(period)
                                    }
                                },
                                label = { Text(period.label) },
                                modifier = Modifier.testTag("period_chip_${period.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Date Navigator Bar
            item {
                val rangeText = when (filterState.period) {
                    ReportPeriod.DAILY -> Formatters.date(summary.rangeStartMillis)
                    ReportPeriod.WEEKLY -> "${Formatters.shortDate(summary.rangeStartMillis)} – ${Formatters.date(summary.rangeEndMillis)}"
                    ReportPeriod.MONTHLY -> Formatters.monthYear(summary.rangeStartMillis)
                    ReportPeriod.YEARLY -> Formatters.year(summary.rangeStartMillis)
                    ReportPeriod.CUSTOM -> "${Formatters.shortDate(summary.rangeStartMillis)} – ${Formatters.date(summary.rangeEndMillis)}"
                }

                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { onShiftPeriod(-1) },
                            enabled = filterState.period != ReportPeriod.CUSTOM
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
                        }

                        TextButton(
                            onClick = {
                                if (filterState.period == ReportPeriod.CUSTOM) showRangePicker = true
                                else showDatePicker = true
                            }
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(rangeText, style = MaterialTheme.typography.titleSmall)
                        }

                        IconButton(
                            onClick = { onShiftPeriod(1) },
                            enabled = filterState.period != ReportPeriod.CUSTOM
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period")
                        }
                    }
                }
            }

            // Export PDF & Excel CSV Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Download & Share Reports", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onDownloadPdf,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("download_pdf_button"),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save PDF")
                            }

                            OutlinedButton(
                                onClick = onDownloadCsv,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("download_csv_button"),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Excel")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                onClick = onSharePdf,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share PDF")
                            }
                            TextButton(
                                onClick = onShareCsv,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share Excel CSV")
                            }
                        }
                    }
                }
            }

            // Financial Summary KPI Grid
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Period Totals", style = MaterialTheme.typography.titleMedium)
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${summary.billCount} Bills • ${Formatters.qty(summary.totalQuantity)} Qty",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SummaryMetricBox(
                                label = "Total Sales",
                                value = Formatters.inr(summary.totalSellingAmount),
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricBox(
                                label = "Purchase Cost",
                                value = Formatters.inr(summary.totalPurchaseCost),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val profitPositive = summary.totalProfit >= 0.0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SummaryMetricBox(
                                label = if (profitPositive) "Net Profit" else "Net Loss",
                                value = Formatters.inr(summary.totalProfit),
                                containerColor = if (profitPositive) ProfitGreenContainer else LossRedContainer,
                                valueColor = if (profitPositive) ProfitGreen else LossRed,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricBox(
                                label = "Profit Margin",
                                value = "${Formatters.qty(summary.profitMarginPercent)}%",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                valueColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Sales vs Cost Chart
            if (summary.dailySeries.isNotEmpty()) {
                item {
                    SalesVsCostChartCard(points = summary.dailySeries)
                }
            }

            // Dedicated Key Trackers: Masoor Full, Masoor Half, Roti
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Key Item Trackers", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Masoor Full, Masoor Half, and Roti are tracked separately across all bills.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TrackerDetailRow(summary.masoorFull)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TrackerDetailRow(summary.masoorHalf)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TrackerDetailRow(summary.roti)
                    }
                }
            }

            // Product-Wise Breakdown Header
            item {
                Text(
                    text = "Product-Wise Report (${summary.productBreakdown.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (summary.productBreakdown.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No transactions in this ${filterState.period.label.lowercase()} period",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Switch dates or save bills from the Ledger or New Paper tab.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(summary.productBreakdown, key = { it.key }) { row ->
                    ProductReportCard(row)
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = filterState.anchorDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            onAnchorDateChanged(Formatters.utcPickerMillisToLocalNoon(it))
                        }
                        showDatePicker = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showRangePicker) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = filterState.customStartMillis ?: summary.rangeStartMillis,
            initialSelectedEndDateMillis = filterState.customEndMillis ?: summary.rangeEndMillis
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = rangeState.selectedStartDateMillis
                        val e = rangeState.selectedEndDateMillis ?: s
                        if (s != null && e != null) {
                            onCustomRangeChanged(
                                Formatters.utcPickerMillisToLocalNoon(s),
                                Formatters.utcPickerMillisToLocalNoon(e)
                            )
                        }
                        showRangePicker = false
                    }
                ) { Text("Apply Range") }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.height(420.dp))
        }
    }
}

@Composable
private fun SummaryMetricBox(
    label: String,
    value: String,
    containerColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TrackerDetailRow(stats: CategoryTrackerStats) {
    val positive = stats.profit >= 0.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.1f)) {
            Text(stats.label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Qty: ${Formatters.qty(stats.quantity)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Text("Sales ${Formatters.inr(stats.sellingAmount)}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily))
            Text("Cost ${Formatters.inr(stats.purchaseCost)}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = if (positive) ProfitGreenContainer else LossRedContainer,
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = Formatters.inr(stats.profit),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = if (positive) ProfitGreen else LossRed,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SalesVsCostChartCard(points: List<DailyTrendPoint>) {
    val visiblePoints = remember(points) { points.takeLast(10) }
    val maxVal = remember(visiblePoints) {
        visiblePoints.maxOfOrNull { maxOf(it.sellingAmount, it.purchaseCost, 1.0) } ?: 1.0
    }
    val salesColor = MaterialTheme.colorScheme.primary
    val costColor = MaterialTheme.colorScheme.secondary

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sales vs Purchase Cost", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot("Sales", salesColor)
                    LegendDot("Cost", costColor)
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val count = visiblePoints.size.coerceAtLeast(1)
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.28f).coerceIn(8f, 28f)
                val gap = 4f
                val chartHeight = size.height - 8f

                visiblePoints.forEachIndexed { idx, pt ->
                    val centerX = slotWidth * idx + slotWidth / 2f
                    val saleH = ((pt.sellingAmount / maxVal) * chartHeight).toFloat().coerceAtLeast(3f)
                    val costH = ((pt.purchaseCost / maxVal) * chartHeight).toFloat().coerceAtLeast(3f)

                    drawRoundRect(
                        color = salesColor,
                        topLeft = Offset(centerX - barWidth - gap / 2f, size.height - saleH),
                        size = Size(barWidth, saleH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = costColor,
                        topLeft = Offset(centerX + gap / 2f, size.height - costH),
                        size = Size(barWidth, costH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                visiblePoints.forEach { pt ->
                    Text(
                        text = pt.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ProductReportCard(row: ProductReportRow) {
    val profitPositive = row.profit >= 0.0
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
            Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.productName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${Formatters.qty(row.quantity)} ${row.unitType.label} • ${row.billCount} bills",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sales ${Formatters.inr(row.sellingAmount)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                )
                Text(
                    text = "Cost ${Formatters.inr(row.purchaseCost)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = if (profitPositive) ProfitGreenContainer else LossRedContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = Formatters.inr(row.profit),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = if (profitPositive) ProfitGreen else LossRed,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
