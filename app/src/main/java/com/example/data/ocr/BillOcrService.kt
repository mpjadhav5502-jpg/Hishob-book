package com.example.data.ocr

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AccountingCalculator
import com.example.data.model.BillLineItem
import com.example.data.model.CustomProduct
import com.example.data.model.OcrConfidence
import com.example.data.model.ProductCategory
import com.example.data.model.UnitType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

data class OcrExtractionResult(
    val suggestedTitle: String,
    val vendorOrParty: String,
    val detectedDateMillis: Long?,
    val confidence: OcrConfidence,
    val warningMessage: String,
    val items: List<BillLineItem>
)

class BillOcrService {

    private val billOcrSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "suggestedTitle" to Schema.string(description = "Short descriptive title of the bill"),
                "vendorOrParty" to Schema.string(description = "Shop, customer, or vendor name if visible, else empty string"),
                "billDateIso" to Schema.string(description = "Date on the bill in yyyy-MM-dd format if visible, else empty string"),
                "clarity" to Schema.enumeration(
                    values = listOf("HIGH", "MEDIUM", "LOW"),
                    description = "HIGH if clearly readable, MEDIUM if some values are inferred/missing, LOW if blurry/illegible/unclear"
                ),
                "warningMessage" to Schema.string(description = "Clear explanation if image is blurry, handwritten parts are ambiguous, or purchase/selling prices are missing"),
                "items" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "productName" to Schema.string(description = "Clean product name (use 'Masoor Full', 'Masoor Half', 'Roti' when applicable, or exact product name)"),
                            "unitType" to Schema.enumeration(
                                values = listOf("FULL", "HALF", "PIECE", "KG", "UNIT"),
                                description = "Unit of measurement"
                            ),
                            "category" to Schema.enumeration(
                                values = listOf("MASOOR_FULL", "MASOOR_HALF", "ROTI", "CUSTOM"),
                                description = "MASOOR_FULL for Masoor Full, MASOOR_HALF for Masoor Half, ROTI for Roti/Chapati, CUSTOM for all other products"
                            ),
                            "quantity" to Schema.double(description = "Numeric quantity, default 1.0 if unclear"),
                            "purchasePricePerUnit" to Schema.double(description = "Purchase cost per unit in INR; 0.0 if not shown on bill"),
                            "sellingPricePerUnit" to Schema.double(description = "Selling price per unit in INR; derive from line total / quantity if only total is written; 0.0 if unreadable"),
                            "needsReview" to Schema.boolean(description = "True if handwriting or price was ambiguous or missing")
                        )
                    ),
                    description = "Extracted product rows from the bill"
                )
            )
        )
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = billOcrSchema
            }
        )
    }

    suspend fun analyzeBillImage(
        bitmap: Bitmap,
        catalogProducts: List<CustomProduct>
    ): Result<OcrExtractionResult> {
        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(buildPrompt())
                    }
                )
            }
            val jsonText = response.text
            if (jsonText.isNullOrBlank()) {
                return Result.failure(IllegalStateException("OCR service returned an empty response. Please verify manually."))
            }
            val parsed = parseOcrJson(jsonText, catalogProducts)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Bill OCR timed out", e)
            Result.failure(IllegalStateException("OCR scan timed out after 60 seconds. Check your internet connection and try again, or enter items manually."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Bill OCR failed", e)
            val msg = e.localizedMessage ?: "OCR scan failed."
            val actionable = if (msg.contains("403") || msg.contains("AppCheck", ignoreCase = true)) {
                "Firebase AI Logic App Check attestation failed (HTTP 403). Ensure Firebase AI Logic is provisioned and App Check debug token is active."
            } else {
                "Could not scan bill image: $msg"
            }
            Result.failure(IllegalStateException(actionable, e))
        }
    }

    internal fun parseOcrJson(
        jsonString: String,
        catalogProducts: List<CustomProduct>
    ): OcrExtractionResult {
        val root = SimpleJsonParser.parseObject(jsonString)
        val rawTitle = (root["suggestedTitle"] as? String).orEmpty().trim()
        val vendor = (root["vendorOrParty"] as? String).orEmpty().trim()
        val dateIso = (root["billDateIso"] as? String).orEmpty().trim()
        val clarityRaw = (root["clarity"] as? String).orEmpty().ifBlank { "MEDIUM" }
        val warning = (root["warningMessage"] as? String).orEmpty().trim()
        val itemsList = (root["items"] as? List<*>) ?: emptyList<Any?>()

        val extractedItems = mutableListOf<BillLineItem>()
        for (rawEntry in itemsList) {
            @Suppress("UNCHECKED_CAST")
            val obj = (rawEntry as? Map<String, Any?>) ?: continue
            val rawName = (obj["productName"] as? String).orEmpty().trim()
            if (rawName.isBlank()) continue

            val unitType = UnitType.fromRaw(obj["unitType"] as? String)
            val rawCat = ProductCategory.fromRaw(obj["category"] as? String)
            val category = if (rawCat == ProductCategory.CUSTOM) {
                ProductCategory.inferFrom(rawName, unitType)
            } else {
                rawCat
            }
            val finalName = when (category) {
                ProductCategory.MASOOR_FULL -> "Masoor Full"
                ProductCategory.MASOOR_HALF -> "Masoor Half"
                ProductCategory.ROTI -> "Roti"
                ProductCategory.CUSTOM -> rawName
            }
            val finalUnit = when (category) {
                ProductCategory.MASOOR_FULL -> UnitType.FULL
                ProductCategory.MASOOR_HALF -> UnitType.HALF
                ProductCategory.ROTI -> if (unitType == UnitType.UNIT) UnitType.PIECE else unitType
                ProductCategory.CUSTOM -> unitType
            }

            val qty = (obj["quantity"] as? Number)?.toDouble()?.let { if (it.isNaN() || it <= 0.0) 1.0 else it } ?: 1.0
            var pPrice = (obj["purchasePricePerUnit"] as? Number)?.toDouble()?.let { if (it.isNaN() || it < 0.0) 0.0 else it } ?: 0.0
            var sPrice = (obj["sellingPricePerUnit"] as? Number)?.toDouble()?.let { if (it.isNaN() || it < 0.0) 0.0 else it } ?: 0.0
            var needsReview = (obj["needsReview"] as? Boolean) ?: false

            // Fill missing prices from user's saved catalog if exact product/category matches
            val matchedCatalog = catalogProducts.firstOrNull { catProd ->
                (category != ProductCategory.CUSTOM && catProd.categoryEnum == category) ||
                    catProd.name.equals(finalName, ignoreCase = true)
            }
            if (matchedCatalog != null) {
                if (pPrice == 0.0 && matchedCatalog.defaultPurchasePrice > 0.0) {
                    pPrice = matchedCatalog.defaultPurchasePrice
                }
                if (sPrice == 0.0 && matchedCatalog.defaultSellingPrice > 0.0) {
                    sPrice = matchedCatalog.defaultSellingPrice
                    needsReview = true
                }
            }
            if (sPrice == 0.0) needsReview = true

            extractedItems.add(
                AccountingCalculator.recalculateItem(
                    BillLineItem(
                        lineId = UUID.randomUUID().toString(),
                        productName = finalName,
                        unitType = finalUnit.name,
                        category = category.name,
                        quantity = qty,
                        purchasePrice = pPrice,
                        sellingPrice = sPrice,
                        needsReview = needsReview
                    )
                )
            )
        }

        val confidence = when {
            extractedItems.isEmpty() -> OcrConfidence.LOW
            clarityRaw.equals("LOW", ignoreCase = true) -> OcrConfidence.LOW
            clarityRaw.equals("MEDIUM", ignoreCase = true) || extractedItems.any { it.needsReview } -> OcrConfidence.MEDIUM
            else -> OcrConfidence.HIGH
        }

        val finalWarning = when {
            extractedItems.isEmpty() ->
                warning.ifBlank { "Image was unclear or no bill items could be read. Please add and edit items manually." }
            confidence == OcrConfidence.LOW ->
                warning.ifBlank { "Some handwriting or prices are unclear. Please review and correct the highlighted rows before saving." }
            confidence == OcrConfidence.MEDIUM && warning.isBlank() ->
                "Please verify extracted quantities, purchase prices, and selling prices before saving."
            else -> warning
        }

        val parsedDate = parseIsoDate(dateIso)

        return OcrExtractionResult(
            suggestedTitle = rawTitle.ifBlank { if (vendor.isNotBlank()) "$vendor Bill" else "Scanned Bill" },
            vendorOrParty = vendor,
            detectedDateMillis = parsedDate,
            confidence = confidence,
            warningMessage = finalWarning,
            items = extractedItems
        )
    }

    private fun parseIsoDate(raw: String): Long? {
        if (raw.isBlank()) return null
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            fmt.parse(raw)?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPrompt(): String = """
        You are an expert Indian retail & restaurant bill OCR extractor for HisabPro.
        Carefully read this bill/receipt photo. It may be handwritten or printed, and written in Marathi (मराठी), Hindi (हिंदी), or English (or a mix).

        CRITICAL EXTRACTION RULES:
        1. Extract every product line item visible on the bill.
        2. Track Masoor Full and Masoor Half STRICTLY SEPARATELY:
           - If the item says Masoor Full / मसूर फूल / Masoor (F) / Full Masoor -> productName="Masoor Full", unitType="FULL", category="MASOOR_FULL".
           - If the item says Masoor Half / मसूर हाफ / अर्धा मसूर / Masoor (H) / Half Masoor -> productName="Masoor Half", unitType="HALF", category="MASOOR_HALF".
           - If the item says Roti / Chapati / पोळी / चपाती / रोटी -> productName="Roti", unitType="PIECE", category="ROTI".
           - For all other items, set category="CUSTOM" and preserve a clear readable productName (in English/transliterated or clean Devanagari as written).
        3. Units: map Full -> FULL, Half -> HALF, Piece/Nos/Plate -> PIECE, Kg/Kilo -> KG, otherwise UNIT.
        4. Prices: extract purchasePricePerUnit and sellingPricePerUnit.
           - Most customer/counter bills only show selling price or line total. If only a line total is written (e.g. "2 x 120 = 240" or "Roti 10 - 150"), compute sellingPricePerUnit = total / quantity.
           - If purchase price is not printed on the bill, set purchasePricePerUnit = 0.0.
        5. Clarity check:
           - If the photo is blurry, dark, cropped, or handwriting is unreadable, set clarity="LOW" and explain clearly in warningMessage which part needs manual correction.
           - Do NOT invent fake items. If unreadable, return an empty items array and clarity="LOW".
    """.trimIndent()

    companion object {
        private const val TAG = "BillOcrService"
    }
}
