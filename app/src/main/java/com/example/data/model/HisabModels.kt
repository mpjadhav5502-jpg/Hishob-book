package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

enum class UnitType(val label: String, val shortLabel: String) {
    FULL("Full", "Full"),
    HALF("Half", "Half"),
    PIECE("Piece", "Pc"),
    KG("Kg", "Kg"),
    UNIT("Unit", "Unit");

    companion object {
        fun fromRaw(raw: String?): UnitType {
            val normalized = raw?.trim()?.uppercase(Locale.ROOT)
            return entries.firstOrNull { it.name == normalized } ?: UNIT
        }
    }
}

enum class ProductCategory(val label: String) {
    MASOOR_FULL("Masoor Full"),
    MASOOR_HALF("Masoor Half"),
    ROTI("Roti"),
    CUSTOM("Custom");

    companion object {
        fun fromRaw(raw: String?): ProductCategory {
            val normalized = raw?.trim()?.uppercase(Locale.ROOT)
            return entries.firstOrNull { it.name == normalized } ?: CUSTOM
        }

        fun inferFrom(name: String, unitType: UnitType): ProductCategory {
            val lower = name.trim().lowercase(Locale.ROOT)
            val isMasoor = lower.contains("masoor") || lower.contains("masur") || lower.contains("मसूर")
            if (isMasoor) {
                return when {
                    unitType == UnitType.HALF || lower.contains("half") || lower.contains("हाफ") || lower.contains("अर्धा") -> MASOOR_HALF
                    else -> MASOOR_FULL
                }
            }
            if (lower.contains("roti") || lower.contains("chapati") || lower.contains("रोटी") || lower.contains("चपाती") || lower.contains("पोळी")) {
                return ROTI
            }
            return CUSTOM
        }
    }
}

enum class BillSourceType(val label: String) {
    PHOTO("Photo Scan"),
    PAPER("New Paper");

    companion object {
        fun fromRaw(raw: String?): BillSourceType {
            val normalized = raw?.trim()?.uppercase(Locale.ROOT)
            return entries.firstOrNull { it.name == normalized } ?: PAPER
        }
    }
}

enum class OcrConfidence(val label: String) {
    HIGH("High Clarity"),
    MEDIUM("Verify Prices"),
    LOW("Unclear - Manual Edit Needed"),
    MANUAL("Manual Entry");

    companion object {
        fun fromRaw(raw: String?): OcrConfidence {
            val normalized = raw?.trim()?.uppercase(Locale.ROOT)
            return entries.firstOrNull { it.name == normalized } ?: MANUAL
        }
    }
}

enum class ReportPeriod(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    CUSTOM("Custom")
}

@Keep
@IgnoreExtraProperties
data class BillLineItem(
    val lineId: String = UUID.randomUUID().toString(),
    val productName: String = "",
    val unitType: String = UnitType.UNIT.name,
    val category: String = ProductCategory.CUSTOM.name,
    val quantity: Double = 1.0,
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val purchaseTotal: Double = 0.0,
    val sellingTotal: Double = 0.0,
    val profit: Double = 0.0,
    // Client-side UI hint only (not stored in Firestore)
    val needsReview: Boolean = false
) {
    val unitEnum: UnitType get() = UnitType.fromRaw(unitType)
    val categoryEnum: ProductCategory get() = ProductCategory.fromRaw(category)

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "lineId" to lineId.ifBlank { UUID.randomUUID().toString() },
        "productName" to productName.trim().take(80),
        "unitType" to UnitType.fromRaw(unitType).name,
        "category" to ProductCategory.fromRaw(category).name,
        "quantity" to AccountingCalculator.round2(quantity.coerceAtLeast(0.0)),
        "purchasePrice" to AccountingCalculator.round2(purchasePrice.coerceAtLeast(0.0)),
        "sellingPrice" to AccountingCalculator.round2(sellingPrice.coerceAtLeast(0.0)),
        "purchaseTotal" to AccountingCalculator.round2(purchaseTotal.coerceAtLeast(0.0)),
        "sellingTotal" to AccountingCalculator.round2(sellingTotal.coerceAtLeast(0.0)),
        "profit" to AccountingCalculator.round2(profit)
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): BillLineItem {
            val lineId = (map["lineId"] as? String).orEmpty().ifBlank { UUID.randomUUID().toString() }
            val name = (map["productName"] as? String).orEmpty()
            val unit = UnitType.fromRaw(map["unitType"] as? String)
            val category = ProductCategory.fromRaw(map["category"] as? String)
            val qty = (map["quantity"] as? Number)?.toDouble() ?: 0.0
            val pPrice = (map["purchasePrice"] as? Number)?.toDouble() ?: 0.0
            val sPrice = (map["sellingPrice"] as? Number)?.toDouble() ?: 0.0
            return AccountingCalculator.recalculateItem(
                BillLineItem(
                    lineId = lineId,
                    productName = name,
                    unitType = unit.name,
                    category = category.name,
                    quantity = qty,
                    purchasePrice = pPrice,
                    sellingPrice = sPrice
                )
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class Bill(
    @DocumentId val id: String = "",
    val userId: String = "",
    val billNumber: Long = 1L,
    val title: String = "",
    val vendorOrParty: String = "",
    val billDateMillis: Long = System.currentTimeMillis(),
    val sourceType: String = BillSourceType.PAPER.name,
    val ocrConfidence: String = OcrConfidence.MANUAL.name,
    val ocrWarning: String = "",
    val hasImage: Boolean = false,
    val localImagePath: String = "",
    val notes: String = "",
    val items: List<Map<String, Any?>> = emptyList(),
    val totalQuantity: Double = 0.0,
    val totalPurchaseCost: Double = 0.0,
    val totalSellingAmount: Double = 0.0,
    val totalProfit: Double = 0.0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val parsedItems: List<BillLineItem>
        get() = items.map { BillLineItem.fromMap(it) }

    val sourceEnum: BillSourceType get() = BillSourceType.fromRaw(sourceType)
    val confidenceEnum: OcrConfidence get() = OcrConfidence.fromRaw(ocrConfidence)
}

@Keep
@IgnoreExtraProperties
data class CustomProduct(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val normalizedName: String = "",
    val unitType: String = UnitType.UNIT.name,
    val category: String = ProductCategory.CUSTOM.name,
    val defaultPurchasePrice: Double = 0.0,
    val defaultSellingPrice: Double = 0.0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val unitEnum: UnitType get() = UnitType.fromRaw(unitType)
    val categoryEnum: ProductCategory get() = ProductCategory.fromRaw(category)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(80),
        "normalizedName" to normalizeProductName(name, UnitType.fromRaw(unitType)).take(80),
        "unitType" to UnitType.fromRaw(unitType).name,
        "category" to ProductCategory.fromRaw(category).name,
        "defaultPurchasePrice" to AccountingCalculator.round2(defaultPurchasePrice.coerceAtLeast(0.0)),
        "defaultSellingPrice" to AccountingCalculator.round2(defaultSellingPrice.coerceAtLeast(0.0)),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "name" to name.trim().take(80),
        "normalizedName" to normalizeProductName(name, UnitType.fromRaw(unitType)).take(80),
        "unitType" to UnitType.fromRaw(unitType).name,
        "category" to ProductCategory.fromRaw(category).name,
        "defaultPurchasePrice" to AccountingCalculator.round2(defaultPurchasePrice.coerceAtLeast(0.0)),
        "defaultSellingPrice" to AccountingCalculator.round2(defaultSellingPrice.coerceAtLeast(0.0)),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun normalizeProductName(name: String, unitType: UnitType): String {
            val base = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
            return if (base.isBlank()) "" else "$base (${unitType.name.lowercase(Locale.ROOT)})"
        }

        fun docIdFor(name: String, unitType: UnitType): String {
            val raw = "${name.trim().lowercase(Locale.ROOT)}_${unitType.name.lowercase(Locale.ROOT)}"
            val sanitized = raw.replace(Regex("[^a-z0-9]+"), "_").trim('_')
            return if (sanitized.isBlank()) "prod_${UUID.randomUUID().toString().take(8)}" else sanitized.take(64)
        }
    }
}

data class BillTotals(
    val totalQuantity: Double = 0.0,
    val totalPurchaseCost: Double = 0.0,
    val totalSellingAmount: Double = 0.0,
    val totalProfit: Double = 0.0
)

data class CategoryTrackerStats(
    val label: String,
    val quantity: Double = 0.0,
    val purchaseCost: Double = 0.0,
    val sellingAmount: Double = 0.0,
    val profit: Double = 0.0
)

data class ProductReportRow(
    val key: String,
    val productName: String,
    val unitType: UnitType,
    val category: ProductCategory,
    val quantity: Double,
    val purchaseCost: Double,
    val sellingAmount: Double,
    val profit: Double,
    val billCount: Int
)

data class DailyTrendPoint(
    val label: String,
    val dateMillis: Long,
    val sellingAmount: Double,
    val purchaseCost: Double,
    val profit: Double
)

data class ReportSummary(
    val period: ReportPeriod,
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
    val billCount: Int = 0,
    val totalQuantity: Double = 0.0,
    val totalPurchaseCost: Double = 0.0,
    val totalSellingAmount: Double = 0.0,
    val totalProfit: Double = 0.0,
    val profitMarginPercent: Double = 0.0,
    val masoorFull: CategoryTrackerStats = CategoryTrackerStats("Masoor Full"),
    val masoorHalf: CategoryTrackerStats = CategoryTrackerStats("Masoor Half"),
    val roti: CategoryTrackerStats = CategoryTrackerStats("Roti"),
    val productBreakdown: List<ProductReportRow> = emptyList(),
    val dailySeries: List<DailyTrendPoint> = emptyList(),
    val filteredBills: List<Bill> = emptyList()
)

object AccountingCalculator {

    fun round2(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return (value * 100.0).roundToLong() / 100.0
    }

    fun recalculateItem(item: BillLineItem): BillLineItem {
        val qty = item.quantity.coerceAtLeast(0.0)
        val pPrice = item.purchasePrice.coerceAtLeast(0.0)
        val sPrice = item.sellingPrice.coerceAtLeast(0.0)
        val pTotal = round2(qty * pPrice)
        val sTotal = round2(qty * sPrice)
        val profit = round2(sTotal - pTotal)
        val inferredCategory = if (item.categoryEnum == ProductCategory.CUSTOM) {
            ProductCategory.inferFrom(item.productName, item.unitEnum)
        } else {
            item.categoryEnum
        }
        return item.copy(
            category = inferredCategory.name,
            quantity = round2(qty),
            purchasePrice = round2(pPrice),
            sellingPrice = round2(sPrice),
            purchaseTotal = pTotal,
            sellingTotal = sTotal,
            profit = profit
        )
    }

    fun computeBillTotals(items: List<BillLineItem>): BillTotals {
        var totalQty = 0.0
        var totalPurchase = 0.0
        var totalSelling = 0.0
        var totalProfit = 0.0
        for (raw in items) {
            val item = recalculateItem(raw)
            totalQty += item.quantity
            totalPurchase += item.purchaseTotal
            totalSelling += item.sellingTotal
            totalProfit += item.profit
        }
        return BillTotals(
            totalQuantity = round2(totalQty),
            totalPurchaseCost = round2(totalPurchase),
            totalSellingAmount = round2(totalSelling),
            totalProfit = round2(totalProfit)
        )
    }

    fun computePeriodBounds(
        period: ReportPeriod,
        anchorMillis: Long,
        customStartMillis: Long? = null,
        customEndMillis: Long? = null
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = anchorMillis }
        fun Calendar.startOfDay() {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        fun Calendar.endOfDay() {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        return when (period) {
            ReportPeriod.DAILY -> {
                val start = (cal.clone() as Calendar).apply { startOfDay() }.timeInMillis
                val end = (cal.clone() as Calendar).apply { endOfDay() }.timeInMillis
                start to end
            }
            ReportPeriod.WEEKLY -> {
                val startCal = (cal.clone() as Calendar).apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    if (timeInMillis > anchorMillis) add(Calendar.DAY_OF_YEAR, -7)
                    startOfDay()
                }
                val endCal = (startCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, 6)
                    endOfDay()
                }
                startCal.timeInMillis to endCal.timeInMillis
            }
            ReportPeriod.MONTHLY -> {
                val startCal = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    startOfDay()
                }
                val endCal = (startCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    endOfDay()
                }
                startCal.timeInMillis to endCal.timeInMillis
            }
            ReportPeriod.YEARLY -> {
                val startCal = (cal.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    startOfDay()
                }
                val endCal = (cal.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.DECEMBER)
                    set(Calendar.DAY_OF_MONTH, 31)
                    endOfDay()
                }
                startCal.timeInMillis to endCal.timeInMillis
            }
            ReportPeriod.CUSTOM -> {
                val rawStart = customStartMillis ?: anchorMillis
                val rawEnd = customEndMillis ?: anchorMillis
                val orderedStart = minOf(rawStart, rawEnd)
                val orderedEnd = maxOf(rawStart, rawEnd)
                val start = Calendar.getInstance().apply {
                    timeInMillis = orderedStart
                    startOfDay()
                }.timeInMillis
                val end = Calendar.getInstance().apply {
                    timeInMillis = orderedEnd
                    endOfDay()
                }.timeInMillis
                start to end
            }
        }
    }

    fun buildReportSummary(
        bills: List<Bill>,
        period: ReportPeriod,
        anchorMillis: Long,
        customStartMillis: Long? = null,
        customEndMillis: Long? = null
    ): ReportSummary {
        val (startMillis, endMillis) = computePeriodBounds(
            period = period,
            anchorMillis = anchorMillis,
            customStartMillis = customStartMillis,
            customEndMillis = customEndMillis
        )
        val filtered = bills
            .filter { it.billDateMillis in startMillis..endMillis }
            .sortedWith(compareByDescending<Bill> { it.billDateMillis }.thenByDescending { it.billNumber })

        var totalQty = 0.0
        var totalPurchase = 0.0
        var totalSelling = 0.0
        var totalProfit = 0.0

        var mfQty = 0.0; var mfCost = 0.0; var mfSale = 0.0; var mfProfit = 0.0
        var mhQty = 0.0; var mhCost = 0.0; var mhSale = 0.0; var mhProfit = 0.0
        var rotiQty = 0.0; var rotiCost = 0.0; var rotiSale = 0.0; var rotiProfit = 0.0

        data class MutableProdAgg(
            val name: String,
            val unitType: UnitType,
            val category: ProductCategory,
            var qty: Double = 0.0,
            var cost: Double = 0.0,
            var sale: Double = 0.0,
            var profit: Double = 0.0,
            val billIds: MutableSet<String> = mutableSetOf()
        )
        val prodMap = linkedMapOf<String, MutableProdAgg>()

        data class MutableDayAgg(
            val dayStart: Long,
            var sale: Double = 0.0,
            var cost: Double = 0.0,
            var profit: Double = 0.0
        )
        val dayMap = linkedMapOf<Long, MutableDayAgg>()

        for (bill in filtered) {
            val items = bill.parsedItems
            val billTotals = computeBillTotals(items)
            totalQty += billTotals.totalQuantity
            totalPurchase += billTotals.totalPurchaseCost
            totalSelling += billTotals.totalSellingAmount
            totalProfit += billTotals.totalProfit

            val dayCal = Calendar.getInstance().apply {
                timeInMillis = bill.billDateMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayKey = dayCal.timeInMillis
            val dayAgg = dayMap.getOrPut(dayKey) { MutableDayAgg(dayKey) }
            dayAgg.sale += billTotals.totalSellingAmount
            dayAgg.cost += billTotals.totalPurchaseCost
            dayAgg.profit += billTotals.totalProfit

            for (rawItem in items) {
                val item = recalculateItem(rawItem)
                val cat = item.categoryEnum
                when (cat) {
                    ProductCategory.MASOOR_FULL -> {
                        mfQty += item.quantity
                        mfCost += item.purchaseTotal
                        mfSale += item.sellingTotal
                        mfProfit += item.profit
                    }
                    ProductCategory.MASOOR_HALF -> {
                        mhQty += item.quantity
                        mhCost += item.purchaseTotal
                        mhSale += item.sellingTotal
                        mhProfit += item.profit
                    }
                    ProductCategory.ROTI -> {
                        rotiQty += item.quantity
                        rotiCost += item.purchaseTotal
                        rotiSale += item.sellingTotal
                        rotiProfit += item.profit
                    }
                    ProductCategory.CUSTOM -> Unit
                }

                // Keep Masoor Full and Masoor Half strictly separate even if named similarly
                val cleanName = item.productName.ifBlank { cat.label }
                val key = when (cat) {
                    ProductCategory.MASOOR_FULL -> "preset_masoor_full"
                    ProductCategory.MASOOR_HALF -> "preset_masoor_half"
                    ProductCategory.ROTI -> "preset_roti"
                    ProductCategory.CUSTOM -> "${cleanName.trim().lowercase(Locale.ROOT)}__${item.unitEnum.name}"
                }
                val agg = prodMap.getOrPut(key) {
                    MutableProdAgg(
                        name = when (cat) {
                            ProductCategory.MASOOR_FULL -> "Masoor Full"
                            ProductCategory.MASOOR_HALF -> "Masoor Half"
                            ProductCategory.ROTI -> "Roti"
                            ProductCategory.CUSTOM -> cleanName.trim()
                        },
                        unitType = item.unitEnum,
                        category = cat
                    )
                }
                agg.qty += item.quantity
                agg.cost += item.purchaseTotal
                agg.sale += item.sellingTotal
                agg.profit += item.profit
                agg.billIds.add(bill.id.ifBlank { bill.billNumber.toString() })
            }
        }

        val breakdown = prodMap.entries.map { (key, agg) ->
            ProductReportRow(
                key = key,
                productName = agg.name,
                unitType = agg.unitType,
                category = agg.category,
                quantity = round2(agg.qty),
                purchaseCost = round2(agg.cost),
                sellingAmount = round2(agg.sale),
                profit = round2(agg.profit),
                billCount = agg.billIds.size
            )
        }.sortedByDescending { it.sellingAmount }

        val dateFormat = java.text.SimpleDateFormat("dd MMM", Locale.getDefault())
        val series = dayMap.values.sortedBy { it.dayStart }.map {
            DailyTrendPoint(
                label = dateFormat.format(java.util.Date(it.dayStart)),
                dateMillis = it.dayStart,
                sellingAmount = round2(it.sale),
                purchaseCost = round2(it.cost),
                profit = round2(it.profit)
            )
        }

        val margin = if (totalSelling > 0.0) round2((totalProfit / totalSelling) * 100.0) else 0.0

        return ReportSummary(
            period = period,
            rangeStartMillis = startMillis,
            rangeEndMillis = endMillis,
            billCount = filtered.size,
            totalQuantity = round2(totalQty),
            totalPurchaseCost = round2(totalPurchase),
            totalSellingAmount = round2(totalSelling),
            totalProfit = round2(totalProfit),
            profitMarginPercent = margin,
            masoorFull = CategoryTrackerStats(
                label = "Masoor Full",
                quantity = round2(mfQty),
                purchaseCost = round2(mfCost),
                sellingAmount = round2(mfSale),
                profit = round2(mfProfit)
            ),
            masoorHalf = CategoryTrackerStats(
                label = "Masoor Half",
                quantity = round2(mhQty),
                purchaseCost = round2(mhCost),
                sellingAmount = round2(mhSale),
                profit = round2(mhProfit)
            ),
            roti = CategoryTrackerStats(
                label = "Roti",
                quantity = round2(rotiQty),
                purchaseCost = round2(rotiCost),
                sellingAmount = round2(rotiSale),
                profit = round2(rotiProfit)
            ),
            productBreakdown = breakdown,
            dailySeries = series,
            filteredBills = filtered
        )
    }
}
