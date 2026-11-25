package com.example

import com.example.data.export.ReportExporter
import com.example.data.model.AccountingCalculator
import com.example.data.model.Bill
import com.example.data.model.BillLineItem
import com.example.data.model.CustomProduct
import com.example.data.model.OcrConfidence
import com.example.data.model.ProductCategory
import com.example.data.model.ReportPeriod
import com.example.data.model.UnitType
import com.example.data.ocr.BillOcrService
import com.example.util.Formatters
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.TimeZone

class HisabAccountingAndOcrTest {

    private fun dateMillis(year: Int, monthZeroBased: Int, day: Int, hour: Int = 12): Long {
        return Calendar.getInstance().apply {
            set(year, monthZeroBased, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `recalculateItem and computeBillTotals compute exact purchase, selling, and profit`() {
        val masoorFull = AccountingCalculator.recalculateItem(
            BillLineItem(
                productName = "Masoor Full",
                unitType = UnitType.FULL.name,
                category = ProductCategory.MASOOR_FULL.name,
                quantity = 3.0,
                purchasePrice = 80.0,
                sellingPrice = 120.0
            )
        )
        val masoorHalf = AccountingCalculator.recalculateItem(
            BillLineItem(
                productName = "Masoor Half",
                unitType = UnitType.HALF.name,
                category = ProductCategory.MASOOR_HALF.name,
                quantity = 2.0,
                purchasePrice = 45.0,
                sellingPrice = 70.0
            )
        )
        val roti = AccountingCalculator.recalculateItem(
            BillLineItem(
                productName = "Roti",
                unitType = UnitType.PIECE.name,
                category = ProductCategory.ROTI.name,
                quantity = 12.0,
                purchasePrice = 8.0,
                sellingPrice = 15.0
            )
        )
        val custom = AccountingCalculator.recalculateItem(
            BillLineItem(
                productName = "Masala Papad",
                unitType = UnitType.PIECE.name,
                category = ProductCategory.CUSTOM.name,
                quantity = 4.0,
                purchasePrice = 12.5,
                sellingPrice = 25.0
            )
        )

        assertEquals(240.0, masoorFull.purchaseTotal, 0.01)
        assertEquals(360.0, masoorFull.sellingTotal, 0.01)
        assertEquals(120.0, masoorFull.profit, 0.01)

        assertEquals(90.0, masoorHalf.purchaseTotal, 0.01)
        assertEquals(140.0, masoorHalf.sellingTotal, 0.01)
        assertEquals(50.0, masoorHalf.profit, 0.01)

        assertEquals(96.0, roti.purchaseTotal, 0.01)
        assertEquals(180.0, roti.sellingTotal, 0.01)
        assertEquals(84.0, roti.profit, 0.01)

        val totals = AccountingCalculator.computeBillTotals(listOf(masoorFull, masoorHalf, roti, custom))
        assertEquals(21.0, totals.totalQuantity, 0.01)
        assertEquals(476.0, totals.totalPurchaseCost, 0.01)
        assertEquals(780.0, totals.totalSellingAmount, 0.01)
        assertEquals(304.0, totals.totalProfit, 0.01)
    }

    @Test
    fun `BillOcrService parses multilingual Marathi Hindi English JSON and separates Masoor Full Half and Roti`() {
        val service = BillOcrService()
        val catalog = listOf(
            CustomProduct(
                name = "Masoor Full",
                unitType = UnitType.FULL.name,
                category = ProductCategory.MASOOR_FULL.name,
                defaultPurchasePrice = 80.0,
                defaultSellingPrice = 120.0
            ),
            CustomProduct(
                name = "Roti",
                unitType = UnitType.PIECE.name,
                category = ProductCategory.ROTI.name,
                defaultPurchasePrice = 8.0,
                defaultSellingPrice = 15.0
            )
        )

        val rawJson = """
            {
              "suggestedTitle": "Pune Dhaba Counter",
              "vendorOrParty": "Shri Ganesh Hotel",
              "billDateIso": "2026-11-24",
              "clarity": "MEDIUM",
              "warningMessage": "Purchase price missing on handwritten receipt; catalog defaults applied where matched.",
              "items": [
                {
                  "productName": "मसूर फूल",
                  "unitType": "FULL",
                  "category": "MASOOR_FULL",
                  "quantity": 2,
                  "purchasePricePerUnit": 0,
                  "sellingPricePerUnit": 120,
                  "needsReview": false
                },
                {
                  "productName": "मसूर हाफ",
                  "unitType": "HALF",
                  "category": "MASOOR_HALF",
                  "quantity": 3,
                  "purchasePricePerUnit": 45,
                  "sellingPricePerUnit": 70,
                  "needsReview": false
                },
                {
                  "productName": "रोटी",
                  "unitType": "PIECE",
                  "category": "ROTI",
                  "quantity": 10,
                  "purchasePricePerUnit": 0,
                  "sellingPricePerUnit": 15,
                  "needsReview": true
                },
                {
                  "productName": "Jeera Rice",
                  "unitType": "FULL",
                  "category": "CUSTOM",
                  "quantity": 1,
                  "purchasePricePerUnit": 55,
                  "sellingPricePerUnit": 110,
                  "needsReview": false
                }
              ]
            }
        """.trimIndent()

        val result = service.parseOcrJson(rawJson, catalog)
        assertEquals("Pune Dhaba Counter", result.suggestedTitle)
        assertEquals("Shri Ganesh Hotel", result.vendorOrParty)
        assertEquals(OcrConfidence.MEDIUM, result.confidence)
        assertEquals(4, result.items.size)

        val mf = result.items[0]
        val mh = result.items[1]
        val roti = result.items[2]
        val custom = result.items[3]

        assertEquals("Masoor Full", mf.productName)
        assertEquals(UnitType.FULL, mf.unitEnum)
        assertEquals(80.0, mf.purchasePrice, 0.01) // filled from catalog
        assertEquals(240.0, mf.sellingTotal, 0.01)

        assertEquals("Masoor Half", mh.productName)
        assertEquals(UnitType.HALF, mh.unitEnum)
        assertEquals(210.0, mh.sellingTotal, 0.01)

        assertEquals("Roti", roti.productName)
        assertEquals(UnitType.PIECE, roti.unitEnum)
        assertEquals(8.0, roti.purchasePrice, 0.01)
        assertEquals(150.0, roti.sellingTotal, 0.01)

        assertEquals("Jeera Rice", custom.productName)
        assertEquals(ProductCategory.CUSTOM, custom.categoryEnum)
    }

    @Test
    fun `BillOcrService flags unclear LOW image with manual correction guidance`() {
        val service = BillOcrService()
        val unclearJson = """
            {
              "suggestedTitle": "",
              "vendorOrParty": "",
              "billDateIso": "",
              "clarity": "LOW",
              "warningMessage": "Handwriting smudged and prices cut off on right edge.",
              "items": []
            }
        """.trimIndent()

        val result = service.parseOcrJson(unclearJson, emptyList())
        assertEquals(OcrConfidence.LOW, result.confidence)
        assertTrue(result.items.isEmpty())
        assertTrue(result.warningMessage.contains("smudged"))
    }

    @Test
    fun `Reports accurately filter Daily Weekly Monthly Yearly Custom and recalculate on Edit and Delete`() {
        val billDay1 = Bill(
            id = "b1",
            billNumber = 1,
            title = "Day 1",
            billDateMillis = dateMillis(2026, Calendar.NOVEMBER, 25, 10),
            items = listOf(
                BillLineItem(
                    productName = "Masoor Full",
                    unitType = UnitType.FULL.name,
                    category = ProductCategory.MASOOR_FULL.name,
                    quantity = 2.0,
                    purchasePrice = 80.0,
                    sellingPrice = 120.0
                ).toFirestoreMap(),
                BillLineItem(
                    productName = "Masoor Half",
                    unitType = UnitType.HALF.name,
                    category = ProductCategory.MASOOR_HALF.name,
                    quantity = 1.0,
                    purchasePrice = 45.0,
                    sellingPrice = 70.0
                ).toFirestoreMap()
            )
        )

        val billSameWeek = Bill(
            id = "b2",
            billNumber = 2,
            title = "Same Week",
            billDateMillis = dateMillis(2026, Calendar.NOVEMBER, 23, 14),
            items = listOf(
                BillLineItem(
                    productName = "Roti",
                    unitType = UnitType.PIECE.name,
                    category = ProductCategory.ROTI.name,
                    quantity = 10.0,
                    purchasePrice = 8.0,
                    sellingPrice = 15.0
                ).toFirestoreMap()
            )
        )

        val billSameMonth = Bill(
            id = "b3",
            billNumber = 3,
            title = "Earlier in Month",
            billDateMillis = dateMillis(2026, Calendar.NOVEMBER, 5, 11),
            items = listOf(
                BillLineItem(
                    productName = "Chaas",
                    unitType = UnitType.UNIT.name,
                    category = ProductCategory.CUSTOM.name,
                    quantity = 5.0,
                    purchasePrice = 10.0,
                    sellingPrice = 25.0
                ).toFirestoreMap()
            )
        )

        val billEarlierYear = Bill(
            id = "b4",
            billNumber = 4,
            title = "October Bill",
            billDateMillis = dateMillis(2026, Calendar.OCTOBER, 12, 16),
            items = listOf(
                BillLineItem(
                    productName = "Masoor Full",
                    unitType = UnitType.FULL.name,
                    category = ProductCategory.MASOOR_FULL.name,
                    quantity = 1.0,
                    purchasePrice = 80.0,
                    sellingPrice = 120.0
                ).toFirestoreMap()
            )
        )

        val allBills = listOf(billDay1, billSameWeek, billSameMonth, billEarlierYear)
        val anchor = dateMillis(2026, Calendar.NOVEMBER, 25, 15)

        val daily = AccountingCalculator.buildReportSummary(allBills, ReportPeriod.DAILY, anchor)
        assertEquals(1, daily.billCount)
        assertEquals(310.0, daily.totalSellingAmount, 0.01)
        assertEquals(2.0, daily.masoorFull.quantity, 0.01)
        assertEquals(1.0, daily.masoorHalf.quantity, 0.01)
        assertEquals(0.0, daily.roti.quantity, 0.01)

        val weekly = AccountingCalculator.buildReportSummary(allBills, ReportPeriod.WEEKLY, anchor)
        assertEquals(2, weekly.billCount)
        assertEquals(460.0, weekly.totalSellingAmount, 0.01)
        assertEquals(10.0, weekly.roti.quantity, 0.01)

        val monthly = AccountingCalculator.buildReportSummary(allBills, ReportPeriod.MONTHLY, anchor)
        assertEquals(3, monthly.billCount)
        assertEquals(585.0, monthly.totalSellingAmount, 0.01)

        val yearly = AccountingCalculator.buildReportSummary(allBills, ReportPeriod.YEARLY, anchor)
        assertEquals(4, yearly.billCount)
        assertEquals(705.0, yearly.totalSellingAmount, 0.01)
        assertEquals(3.0, yearly.masoorFull.quantity, 0.01)
        assertEquals(1.0, yearly.masoorHalf.quantity, 0.01)

        val customRange = AccountingCalculator.buildReportSummary(
            bills = allBills,
            period = ReportPeriod.CUSTOM,
            anchorMillis = anchor,
            customStartMillis = dateMillis(2026, Calendar.NOVEMBER, 1, 0),
            customEndMillis = dateMillis(2026, Calendar.NOVEMBER, 24, 23)
        )
        assertEquals(2, customRange.billCount)
        assertEquals(275.0, customRange.totalSellingAmount, 0.01)

        // Simulate editing billDay1 Masoor Full qty to 4 and removing Masoor Half, plus deleting billEarlierYear
        val editedBill1 = billDay1.copy(
            items = listOf(
                BillLineItem(
                    productName = "Masoor Full",
                    unitType = UnitType.FULL.name,
                    category = ProductCategory.MASOOR_FULL.name,
                    quantity = 4.0,
                    purchasePrice = 80.0,
                    sellingPrice = 120.0
                ).toFirestoreMap()
            )
        )
        val afterEditAndDelete = listOf(editedBill1, billSameWeek, billSameMonth)
        val updatedYearly = AccountingCalculator.buildReportSummary(afterEditAndDelete, ReportPeriod.YEARLY, anchor)
        assertEquals(3, updatedYearly.billCount)
        assertEquals(4.0, updatedYearly.masoorFull.quantity, 0.01)
        assertEquals(0.0, updatedYearly.masoorHalf.quantity, 0.01)
        assertEquals(755.0, updatedYearly.totalSellingAmount, 0.01)
    }

    @Test
    fun `ReportExporter writes valid UTF-8 BOM Excel CSV preserving Marathi Hindi text and escaped commas`() = runBlocking {
        val exporter = ReportExporter()
        val now = dateMillis(2026, Calendar.NOVEMBER, 25, 11)
        val bills = listOf(
            Bill(
                id = "b_csv_1",
                billNumber = 42,
                title = "Counter मराठी, Bill",
                vendorOrParty = "Patil \"Shop\"",
                billDateMillis = now,
                items = listOf(
                    BillLineItem(
                        productName = "Masoor Full",
                        unitType = UnitType.FULL.name,
                        category = ProductCategory.MASOOR_FULL.name,
                        quantity = 3.0,
                        purchasePrice = 80.0,
                        sellingPrice = 120.0
                    ).toFirestoreMap(),
                    BillLineItem(
                        productName = "Masoor Half",
                        unitType = UnitType.HALF.name,
                        category = ProductCategory.MASOOR_HALF.name,
                        quantity = 2.0,
                        purchasePrice = 45.0,
                        sellingPrice = 70.0
                    ).toFirestoreMap(),
                    BillLineItem(
                        productName = "Roti",
                        unitType = UnitType.PIECE.name,
                        category = ProductCategory.ROTI.name,
                        quantity = 8.0,
                        purchasePrice = 8.0,
                        sellingPrice = 15.0
                    ).toFirestoreMap()
                )
            )
        )
        val summary = AccountingCalculator.buildReportSummary(bills, ReportPeriod.DAILY, now)
        val baos = ByteArrayOutputStream()
        val res = exporter.writeCsvReport(summary, baos)
        assertTrue(res.isSuccess)

        val bytes = baos.toByteArray()
        assertTrue(bytes.size > 100)
        // UTF-8 BOM header check (0xEF, 0xBB, 0xBF)
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            bytes.copyOfRange(0, 3)
        )

        val text = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        assertTrue(text.contains("Masoor Full"))
        assertTrue(text.contains("Masoor Half"))
        assertTrue(text.contains("Roti"))
        assertTrue(text.contains("\"Counter मराठी, Bill\""))
        assertTrue(text.contains("\"Patil \"\"Shop\"\"\""))
        assertTrue(text.contains("620.00")) // 360 + 140 + 120 total sales
    }

    @Test
    fun `BillLineItem Firestore map roundtrip and CustomProduct normalization preserve accounting fields`() {
        val original = AccountingCalculator.recalculateItem(
            BillLineItem(
                lineId = "line-99",
                productName = "Paneer Masala",
                unitType = UnitType.FULL.name,
                category = ProductCategory.CUSTOM.name,
                quantity = 2.5,
                purchasePrice = 90.0,
                sellingPrice = 160.0
            )
        )
        val map = original.toFirestoreMap()
        val restored = BillLineItem.fromMap(map)

        assertEquals(original.lineId, restored.lineId)
        assertEquals("Paneer Masala", restored.productName)
        assertEquals(UnitType.FULL, restored.unitEnum)
        assertEquals(225.0, restored.purchaseTotal, 0.01)
        assertEquals(400.0, restored.sellingTotal, 0.01)
        assertEquals(175.0, restored.profit, 0.01)

        // Ensure Masoor Full and Masoor Half produce distinct catalog IDs & normalized names
        val mfId = CustomProduct.docIdFor("Masoor", UnitType.FULL)
        val mhId = CustomProduct.docIdFor("Masoor", UnitType.HALF)
        assertNotEquals(mfId, mhId)
    }

    @Test
    fun `utcPickerMillisToLocalNoon preserves selected calendar day across timezones`() {
        val utcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.NOVEMBER, 25, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val localNoon = Formatters.utcPickerMillisToLocalNoon(utcMidnight)
        val localCal = Calendar.getInstance().apply { timeInMillis = localNoon }
        assertEquals(2026, localCal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, localCal.get(Calendar.MONTH))
        assertEquals(25, localCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(12, localCal.get(Calendar.HOUR_OF_DAY))
    }
}
