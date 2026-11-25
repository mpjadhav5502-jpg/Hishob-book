package com.example.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.Bill
import com.example.data.model.ReportSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportExporter {

    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val dateTimeFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    suspend fun writePdfReport(
        summary: ReportSummary,
        outputStream: OutputStream
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val document = buildPdfDocument(summary)
            try {
                document.writeTo(outputStream)
                outputStream.flush()
            } finally {
                document.close()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writePdfToCacheFile(
        context: Context,
        summary: ReportSummary,
        fileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { stream ->
                val result = writePdfReport(summary, stream)
                if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeCsvReport(
        summary: ReportSummary,
        outputStream: OutputStream
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val csvText = generateCsvString(summary)
            // UTF-8 BOM ensures Excel opens Devanagari (Marathi/Hindi) and Rupee symbol cleanly
            val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            outputStream.write(bom)
            outputStream.write(csvText.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeCsvToCacheFile(
        context: Context,
        summary: ReportSummary,
        fileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { stream ->
                val result = writeCsvReport(summary, stream)
                if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateCsvString(summary: ReportSummary): String {
        val sb = StringBuilder()
        val startStr = dateFmt.format(Date(summary.rangeStartMillis))
        val endStr = dateFmt.format(Date(summary.rangeEndMillis))

        sb.appendLine("HisabPro Accounting Report")
        sb.appendLine("Period,${escapeCsv(summary.period.label)},From,${escapeCsv(startStr)},To,${escapeCsv(endStr)}")
        sb.appendLine("Generated At,${escapeCsv(dateTimeFmt.format(Date()))}")
        sb.appendLine()

        sb.appendLine("SUMMARY TOTALS")
        sb.appendLine("Total Bills,Total Quantity,Total Purchase Cost (INR),Total Sales (INR),Net Profit (INR),Margin (%)")
        sb.appendLine(
            "${summary.billCount},${fmtNum(summary.totalQuantity)},${fmtNum(summary.totalPurchaseCost)}," +
                "${fmtNum(summary.totalSellingAmount)},${fmtNum(summary.totalProfit)},${fmtNum(summary.profitMarginPercent)}"
        )
        sb.appendLine()

        sb.appendLine("KEY TRACKERS (MASOOR FULL / MASOOR HALF / ROTI)")
        sb.appendLine("Tracker,Quantity,Purchase Cost (INR),Selling Amount (INR),Profit (INR)")
        listOf(summary.masoorFull, summary.masoorHalf, summary.roti).forEach { tracker ->
            sb.appendLine(
                "${escapeCsv(tracker.label)},${fmtNum(tracker.quantity)},${fmtNum(tracker.purchaseCost)}," +
                    "${fmtNum(tracker.sellingAmount)},${fmtNum(tracker.profit)}"
            )
        }
        sb.appendLine()

        sb.appendLine("PRODUCT-WISE REPORT")
        sb.appendLine("Product Name,Unit,Category,Bills,Quantity,Purchase Cost (INR),Selling Amount (INR),Profit (INR)")
        summary.productBreakdown.forEach { row ->
            sb.appendLine(
                "${escapeCsv(row.productName)},${row.unitType.label},${row.category.label},${row.billCount}," +
                    "${fmtNum(row.quantity)},${fmtNum(row.purchaseCost)},${fmtNum(row.sellingAmount)},${fmtNum(row.profit)}"
            )
        }
        sb.appendLine()

        sb.appendLine("DETAILED BILLS & LINE ITEMS")
        sb.appendLine("Bill #,Date,Title,Party/Vendor,Source,Product,Unit,Qty,Purchase Price,Selling Price,Purchase Total,Selling Total,Profit")
        summary.filteredBills.forEach { bill ->
            val bDate = dateFmt.format(Date(bill.billDateMillis))
            bill.parsedItems.forEach { item ->
                sb.appendLine(
                    "${bill.billNumber},${escapeCsv(bDate)},${escapeCsv(bill.title)},${escapeCsv(bill.vendorOrParty)}," +
                        "${escapeCsv(bill.sourceEnum.label)},${escapeCsv(item.productName)},${item.unitEnum.label}," +
                        "${fmtNum(item.quantity)},${fmtNum(item.purchasePrice)},${fmtNum(item.sellingPrice)}," +
                        "${fmtNum(item.purchaseTotal)},${fmtNum(item.sellingTotal)},${fmtNum(item.profit)}"
                )
            }
        }
        return sb.toString()
    }

    private fun buildPdfDocument(summary: ReportSummary): PdfDocument {
        val pdf = PdfDocument()
        val pageWidth = 595 // A4 width in PostScript points (72 dpi)
        val pageHeight = 842
        val margin = 36f
        val contentWidth = pageWidth - margin * 2

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(11, 61, 62)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(84, 98, 99)
            textSize = 10.5f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(11, 61, 62)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 33, 34)
            textSize = 9.5f
        }
        val boldRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 33, 34)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerBgPaint = Paint().apply { color = Color.rgb(11, 61, 62) }
        val cardBgPaint = Paint().apply { color = Color.rgb(243, 239, 231) }
        val altRowBgPaint = Paint().apply { color = Color.rgb(250, 247, 242) }
        val dividerPaint = Paint().apply {
            color = Color.rgb(214, 207, 196)
            strokeWidth = 0.8f
        }

        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin

        fun newPage() {
            pdf.finishPage(page)
            pageNum += 1
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            canvas = page.canvas
            y = margin
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - margin) {
                newPage()
            }
        }

        // Document Header
        canvas.drawText("HisabPro Accounting Report", margin, y + 18f, titlePaint)
        y += 28f
        val rangeLabel = "${summary.period.label} Report: ${dateFmt.format(Date(summary.rangeStartMillis))} to ${dateFmt.format(Date(summary.rangeEndMillis))}"
        canvas.drawText(rangeLabel, margin, y + 10f, subtitlePaint)
        canvas.drawText("Generated: ${dateTimeFmt.format(Date())}", pageWidth - margin - 180f, y + 10f, subtitlePaint)
        y += 22f
        canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
        y += 16f

        // Summary KPI Box
        canvas.drawRoundRect(margin, y, pageWidth - margin, y + 66f, 8f, 8f, cardBgPaint)
        val kpiCols = listOf(
            "Bills" to summary.billCount.toString(),
            "Total Qty" to fmtNum(summary.totalQuantity),
            "Purchase Cost" to "Rs ${fmtNum(summary.totalPurchaseCost)}",
            "Total Sales" to "Rs ${fmtNum(summary.totalSellingAmount)}",
            "Net Profit" to "Rs ${fmtNum(summary.totalProfit)}"
        )
        val colStep = contentWidth / kpiCols.size
        kpiCols.forEachIndexed { idx, (label, value) ->
            val x = margin + 10f + idx * colStep
            canvas.drawText(label, x, y + 22f, subtitlePaint)
            canvas.drawText(value, x, y + 44f, boldRowPaint)
        }
        y += 84f

        // Key Trackers (Masoor Full / Masoor Half / Roti)
        canvas.drawText("Key Trackers (Masoor Full / Masoor Half / Roti)", margin, y + 12f, sectionPaint)
        y += 20f
        canvas.drawRect(margin, y, pageWidth - margin, y + 20f, headerBgPaint)
        canvas.drawText("Item", margin + 8f, y + 14f, headerTextPaint)
        canvas.drawText("Qty", margin + 180f, y + 14f, headerTextPaint)
        canvas.drawText("Cost (Rs)", margin + 255f, y + 14f, headerTextPaint)
        canvas.drawText("Sales (Rs)", margin + 350f, y + 14f, headerTextPaint)
        canvas.drawText("Profit (Rs)", margin + 445f, y + 14f, headerTextPaint)
        y += 22f

        listOf(summary.masoorFull, summary.masoorHalf, summary.roti).forEachIndexed { idx, tracker ->
            if (idx % 2 == 1) canvas.drawRect(margin, y, pageWidth - margin, y + 18f, altRowBgPaint)
            canvas.drawText(tracker.label, margin + 8f, y + 13f, boldRowPaint)
            canvas.drawText(fmtNum(tracker.quantity), margin + 180f, y + 13f, rowTextPaint)
            canvas.drawText(fmtNum(tracker.purchaseCost), margin + 255f, y + 13f, rowTextPaint)
            canvas.drawText(fmtNum(tracker.sellingAmount), margin + 350f, y + 13f, rowTextPaint)
            canvas.drawText(fmtNum(tracker.profit), margin + 445f, y + 13f, boldRowPaint)
            y += 19f
        }
        y += 18f

        // Product-Wise Breakdown
        ensureSpace(70f)
        canvas.drawText("Product-Wise Breakdown (${summary.productBreakdown.size} products)", margin, y + 12f, sectionPaint)
        y += 20f

        fun drawProductHeader(c: Canvas, top: Float) {
            c.drawRect(margin, top, pageWidth - margin, top + 20f, headerBgPaint)
            c.drawText("Product (Unit)", margin + 8f, top + 14f, headerTextPaint)
            c.drawText("Qty", margin + 195f, top + 14f, headerTextPaint)
            c.drawText("Cost (Rs)", margin + 265f, top + 14f, headerTextPaint)
            c.drawText("Sales (Rs)", margin + 355f, top + 14f, headerTextPaint)
            c.drawText("Profit (Rs)", margin + 445f, top + 14f, headerTextPaint)
        }
        drawProductHeader(canvas, y)
        y += 22f

        if (summary.productBreakdown.isEmpty()) {
            canvas.drawText("No product transactions recorded in this period.", margin + 8f, y + 14f, subtitlePaint)
            y += 24f
        } else {
            summary.productBreakdown.forEachIndexed { idx, row ->
                if (y + 22f > pageHeight - margin) {
                    newPage()
                    drawProductHeader(canvas, y)
                    y += 22f
                }
                if (idx % 2 == 1) canvas.drawRect(margin, y, pageWidth - margin, y + 18f, altRowBgPaint)
                val nameUnit = "${row.productName.take(24)} (${row.unitType.shortLabel})"
                canvas.drawText(nameUnit, margin + 8f, y + 13f, rowTextPaint)
                canvas.drawText(fmtNum(row.quantity), margin + 195f, y + 13f, rowTextPaint)
                canvas.drawText(fmtNum(row.purchaseCost), margin + 265f, y + 13f, rowTextPaint)
                canvas.drawText(fmtNum(row.sellingAmount), margin + 355f, y + 13f, rowTextPaint)
                canvas.drawText(fmtNum(row.profit), margin + 445f, y + 13f, boldRowPaint)
                y += 19f
            }
        }
        y += 18f

        // Bill Ledger List
        ensureSpace(70f)
        canvas.drawText("Bills Included (${summary.filteredBills.size})", margin, y + 12f, sectionPaint)
        y += 20f

        fun drawBillsHeader(c: Canvas, top: Float) {
            c.drawRect(margin, top, pageWidth - margin, top + 20f, headerBgPaint)
            c.drawText("Bill #", margin + 8f, top + 14f, headerTextPaint)
            c.drawText("Date", margin + 56f, top + 14f, headerTextPaint)
            c.drawText("Title", margin + 135f, top + 14f, headerTextPaint)
            c.drawText("Cost (Rs)", margin + 305f, top + 14f, headerTextPaint)
            c.drawText("Sales (Rs)", margin + 385f, top + 14f, headerTextPaint)
            c.drawText("Profit (Rs)", margin + 460f, top + 14f, headerTextPaint)
        }
        drawBillsHeader(canvas, y)
        y += 22f

        summary.filteredBills.forEachIndexed { idx, bill: Bill ->
            if (y + 22f > pageHeight - margin) {
                newPage()
                drawBillsHeader(canvas, y)
                y += 22f
            }
            if (idx % 2 == 1) canvas.drawRect(margin, y, pageWidth - margin, y + 18f, altRowBgPaint)
            canvas.drawText("#${bill.billNumber}", margin + 8f, y + 13f, boldRowPaint)
            canvas.drawText(dateFmt.format(Date(bill.billDateMillis)), margin + 56f, y + 13f, rowTextPaint)
            canvas.drawText(bill.title.take(26), margin + 135f, y + 13f, rowTextPaint)
            canvas.drawText(fmtNum(bill.totalPurchaseCost), margin + 305f, y + 13f, rowTextPaint)
            canvas.drawText(fmtNum(bill.totalSellingAmount), margin + 385f, y + 13f, rowTextPaint)
            canvas.drawText(fmtNum(bill.totalProfit), margin + 460f, y + 13f, boldRowPaint)
            y += 19f
        }

        pdf.finishPage(page)
        return pdf
    }

    private fun fmtNum(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun escapeCsv(raw: String): String {
        val cleaned = raw.replace("\r", " ").replace("\n", " ")
        return if (cleaned.contains(",") || cleaned.contains("\"")) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }
}
