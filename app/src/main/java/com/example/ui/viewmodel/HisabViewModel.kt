package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.export.ReportExporter
import com.example.data.firebase.friendlyFirestoreMessage
import com.example.data.model.AccountingCalculator
import com.example.data.model.Bill
import com.example.data.model.BillLineItem
import com.example.data.model.BillSourceType
import com.example.data.model.BillTotals
import com.example.data.model.CustomProduct
import com.example.data.model.OcrConfidence
import com.example.data.model.ProductCategory
import com.example.data.model.ReportPeriod
import com.example.data.model.ReportSummary
import com.example.data.model.UnitType
import com.example.data.ocr.BillOcrService
import com.example.data.repository.BillDraftPayload
import com.example.data.repository.BillImageLocalStore
import com.example.data.repository.CompressedBillImage
import com.example.data.repository.HisabRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class BillEditorState(
    val billId: String = UUID.randomUUID().toString(),
    val isNewBill: Boolean = true,
    val existingBill: Bill? = null,
    val title: String = "",
    val vendorOrParty: String = "",
    val billDateMillis: Long = System.currentTimeMillis(),
    val sourceType: BillSourceType = BillSourceType.PAPER,
    val ocrConfidence: OcrConfidence = OcrConfidence.MANUAL,
    val ocrWarning: String = "",
    val notes: String = "",
    val items: List<BillLineItem> = listOf(defaultEmptyLine()),
    val totals: BillTotals = BillTotals(),
    val photoBitmap: Bitmap? = null,
    val localImagePath: String = "",
    val pendingCloudImage: CompressedBillImage? = null,
    val removeExistingImage: Boolean = false,
    val isScanningOcr: Boolean = false,
    val ocrError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null
) {
    companion object {
        fun defaultEmptyLine(): BillLineItem = BillLineItem(
            lineId = UUID.randomUUID().toString(),
            productName = "",
            unitType = UnitType.UNIT.name,
            category = ProductCategory.CUSTOM.name,
            quantity = 1.0,
            purchasePrice = 0.0,
            sellingPrice = 0.0
        )
    }
}

data class ReportFilterState(
    val period: ReportPeriod = ReportPeriod.DAILY,
    val anchorDateMillis: Long = System.currentTimeMillis(),
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null
)

class HisabViewModel(
    private val repository: HisabRepository,
    private val imageStore: BillImageLocalStore,
    private val ocrService: BillOcrService,
    private val exporter: ReportExporter,
    private val currentUserId: String
) : ViewModel() {

    val billsState: StateFlow<UiState<List<Bill>>> = repository.observeBills(currentUserId)
        .map<List<Bill>, UiState<List<Bill>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing bills", rootError)
            emit(UiState.Error(friendlyFirestoreMessage(rootError)))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val productsState: StateFlow<UiState<List<CustomProduct>>> = repository.observeProducts(currentUserId)
        .map<List<CustomProduct>, UiState<List<CustomProduct>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing products", rootError)
            emit(UiState.Error(friendlyFirestoreMessage(rootError)))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _reportFilter = MutableStateFlow(ReportFilterState())
    val reportFilter: StateFlow<ReportFilterState> = _reportFilter.asStateFlow()

    val reportSummary: StateFlow<ReportSummary> = combine(billsState, _reportFilter) { billsUi, filter ->
        val bills = (billsUi as? UiState.Success)?.data ?: emptyList()
        AccountingCalculator.buildReportSummary(
            bills = bills,
            period = filter.period,
            anchorMillis = filter.anchorDateMillis,
            customStartMillis = filter.customStartMillis,
            customEndMillis = filter.customEndMillis
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AccountingCalculator.buildReportSummary(
            bills = emptyList(),
            period = ReportPeriod.DAILY,
            anchorMillis = System.currentTimeMillis()
        )
    )

    val todaySummary: StateFlow<ReportSummary> = billsState.map { billsUi ->
        val bills = (billsUi as? UiState.Success)?.data ?: emptyList()
        AccountingCalculator.buildReportSummary(
            bills = bills,
            period = ReportPeriod.DAILY,
            anchorMillis = System.currentTimeMillis()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AccountingCalculator.buildReportSummary(
            bills = emptyList(),
            period = ReportPeriod.DAILY,
            anchorMillis = System.currentTimeMillis()
        )
    )

    private val _editorState = MutableStateFlow(BillEditorState())
    val editorState: StateFlow<BillEditorState> = _editorState.asStateFlow()

    private val _bannerMessage = MutableStateFlow<Pair<Boolean, String>?>(null)
    val bannerMessage: StateFlow<Pair<Boolean, String>?> = _bannerMessage.asStateFlow()

    private val _isDeletingBill = MutableStateFlow(false)
    val isDeletingBill: StateFlow<Boolean> = _isDeletingBill.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultProducts(currentUserId)
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun showBanner(isError: Boolean, message: String) {
        _bannerMessage.value = isError to message
    }

    // --- NEW PAPER & EDITOR LIFECYCLE ---

    fun startBlankNewPaper() {
        // Always generate a genuinely new unique billId so an old bill is never overwritten
        val blankLine = BillEditorState.defaultEmptyLine()
        _editorState.value = BillEditorState(
            billId = UUID.randomUUID().toString(),
            isNewBill = true,
            existingBill = null,
            title = "",
            vendorOrParty = "",
            billDateMillis = System.currentTimeMillis(),
            sourceType = BillSourceType.PAPER,
            ocrConfidence = OcrConfidence.MANUAL,
            ocrWarning = "",
            notes = "",
            items = listOf(blankLine),
            totals = AccountingCalculator.computeBillTotals(listOf(blankLine))
        )
    }

    fun startPhotoScanFlow(uri: Uri) {
        val newBillId = UUID.randomUUID().toString()
        _editorState.value = BillEditorState(
            billId = newBillId,
            isNewBill = true,
            existingBill = null,
            title = "Scanned Bill",
            billDateMillis = System.currentTimeMillis(),
            sourceType = BillSourceType.PHOTO,
            ocrConfidence = OcrConfidence.MEDIUM,
            items = emptyList(),
            isScanningOcr = true,
            ocrError = null
        )

        viewModelScope.launch {
            val importRes = imageStore.importFromUri(uri, newBillId)
            if (importRes.isFailure) {
                val msg = importRes.exceptionOrNull()?.localizedMessage ?: "Failed to read selected photo."
                _editorState.update {
                    it.copy(
                        isScanningOcr = false,
                        ocrError = msg,
                        ocrConfidence = OcrConfidence.LOW,
                        ocrWarning = msg,
                        items = listOf(BillEditorState.defaultEmptyLine())
                    )
                }
                return@launch
            }

            val loaded = importRes.getOrThrow()
            _editorState.update {
                it.copy(
                    photoBitmap = loaded.bitmap,
                    localImagePath = loaded.localPath,
                    pendingCloudImage = loaded.compressedPayload
                )
            }

            runOcrOnBitmap(loaded.bitmap)
        }
    }

    fun replacePhotoOnCurrentBill(uri: Uri, runOcrAgain: Boolean) {
        val current = _editorState.value
        val targetId = current.billId
        viewModelScope.launch {
            _editorState.update { it.copy(isScanningOcr = runOcrAgain, ocrError = null) }
            val importRes = imageStore.importFromUri(uri, targetId)
            if (importRes.isFailure) {
                val msg = importRes.exceptionOrNull()?.localizedMessage ?: "Failed to load replacement photo."
                _editorState.update { it.copy(isScanningOcr = false, ocrError = msg) }
                return@launch
            }
            val loaded = importRes.getOrThrow()
            _editorState.update {
                it.copy(
                    photoBitmap = loaded.bitmap,
                    localImagePath = loaded.localPath,
                    pendingCloudImage = loaded.compressedPayload,
                    removeExistingImage = false,
                    sourceType = BillSourceType.PHOTO
                )
            }
            if (runOcrAgain) {
                runOcrOnBitmap(loaded.bitmap)
            }
        }
    }

    fun retryOcrOnCurrentPhoto() {
        val bitmap = _editorState.value.photoBitmap ?: return
        viewModelScope.launch {
            _editorState.update { it.copy(isScanningOcr = true, ocrError = null) }
            runOcrOnBitmap(bitmap)
        }
    }

    private suspend fun runOcrOnBitmap(bitmap: Bitmap) {
        val catalog = (productsState.value as? UiState.Success)?.data ?: emptyList()
        val ocrBitmap = imageStore.scaleForOcr(bitmap)
        val ocrResult = ocrService.analyzeBillImage(ocrBitmap, catalog)
        if (ocrResult.isSuccess) {
            val extracted = ocrResult.getOrThrow()
            val itemsToShow = if (extracted.items.isNotEmpty()) {
                extracted.items
            } else {
                listOf(BillEditorState.defaultEmptyLine())
            }
            _editorState.update { state ->
                val updatedTitle = if (state.isNewBill || state.title.isBlank()) {
                    extracted.suggestedTitle
                } else {
                    state.title
                }
                val updatedVendor = if (state.vendorOrParty.isBlank()) extracted.vendorOrParty else state.vendorOrParty
                state.copy(
                    title = updatedTitle,
                    vendorOrParty = updatedVendor,
                    billDateMillis = extracted.detectedDateMillis ?: state.billDateMillis,
                    ocrConfidence = extracted.confidence,
                    ocrWarning = extracted.warningMessage,
                    items = itemsToShow,
                    totals = AccountingCalculator.computeBillTotals(itemsToShow),
                    isScanningOcr = false,
                    ocrError = null
                )
            }
        } else {
            val errMsg = ocrResult.exceptionOrNull()?.localizedMessage
                ?: "Bill image could not be scanned automatically. Please enter items manually."
            _editorState.update { state ->
                val fallbackItems = state.items.ifEmpty { listOf(BillEditorState.defaultEmptyLine()) }
                state.copy(
                    isScanningOcr = false,
                    ocrConfidence = OcrConfidence.LOW,
                    ocrWarning = errMsg,
                    ocrError = errMsg,
                    items = fallbackItems,
                    totals = AccountingCalculator.computeBillTotals(fallbackItems)
                )
            }
        }
    }

    fun openBillForEdit(bill: Bill) {
        val parsed = bill.parsedItems.ifEmpty { listOf(BillEditorState.defaultEmptyLine()) }
        _editorState.value = BillEditorState(
            billId = bill.id,
            isNewBill = false,
            existingBill = bill,
            title = bill.title,
            vendorOrParty = bill.vendorOrParty,
            billDateMillis = bill.billDateMillis,
            sourceType = bill.sourceEnum,
            ocrConfidence = bill.confidenceEnum,
            ocrWarning = bill.ocrWarning,
            notes = bill.notes,
            items = parsed,
            totals = AccountingCalculator.computeBillTotals(parsed),
            localImagePath = bill.localImagePath,
            photoBitmap = null,
            pendingCloudImage = null,
            removeExistingImage = false
        )

        if (bill.hasImage || bill.localImagePath.isNotBlank()) {
            viewModelScope.launch {
                val localBmp = imageStore.loadBitmapFromLocalOrBase64(bill.localImagePath, null)
                if (localBmp != null) {
                    _editorState.update { state ->
                        if (state.billId == bill.id) state.copy(photoBitmap = localBmp) else state
                    }
                } else {
                    val cloudBase64 = repository.loadBillImageBase64(currentUserId, bill.id).getOrNull()
                    val cloudBmp = imageStore.loadBitmapFromLocalOrBase64(null, cloudBase64)
                    if (cloudBmp != null) {
                        _editorState.update { state ->
                            if (state.billId == bill.id) state.copy(photoBitmap = cloudBmp) else state
                        }
                    }
                }
            }
        }
    }

    fun removePhotoFromEditor() {
        _editorState.update {
            it.copy(
                photoBitmap = null,
                localImagePath = "",
                pendingCloudImage = null,
                removeExistingImage = !it.isNewBill
            )
        }
    }

    fun updateBillTitle(value: String) {
        _editorState.update { it.copy(title = value.take(120), saveError = null) }
    }

    fun updateVendorOrParty(value: String) {
        _editorState.update { it.copy(vendorOrParty = value.take(120), saveError = null) }
    }

    fun updateBillDate(millis: Long) {
        _editorState.update { it.copy(billDateMillis = millis, saveError = null) }
    }

    fun updateNotes(value: String) {
        _editorState.update { it.copy(notes = value.take(500), saveError = null) }
    }

    fun addLineItem(preset: CustomProduct? = null) {
        val newItem = if (preset != null) {
            AccountingCalculator.recalculateItem(
                BillLineItem(
                    lineId = UUID.randomUUID().toString(),
                    productName = preset.name,
                    unitType = preset.unitType,
                    category = preset.category,
                    quantity = 1.0,
                    purchasePrice = preset.defaultPurchasePrice,
                    sellingPrice = preset.defaultSellingPrice
                )
            )
        } else {
            BillEditorState.defaultEmptyLine()
        }
        _editorState.update { state ->
            val updated = state.items + newItem
            state.copy(
                items = updated,
                totals = AccountingCalculator.computeBillTotals(updated),
                saveError = null
            )
        }
    }

    fun addQuickPreset(category: ProductCategory) {
        val catalog = (productsState.value as? UiState.Success)?.data ?: emptyList()
        val matched = catalog.firstOrNull { it.categoryEnum == category }
        val item = if (matched != null) {
            BillLineItem(
                lineId = UUID.randomUUID().toString(),
                productName = matched.name,
                unitType = matched.unitType,
                category = matched.category,
                quantity = 1.0,
                purchasePrice = matched.defaultPurchasePrice,
                sellingPrice = matched.defaultSellingPrice
            )
        } else {
            when (category) {
                ProductCategory.MASOOR_FULL -> BillLineItem(
                    productName = "Masoor Full",
                    unitType = UnitType.FULL.name,
                    category = ProductCategory.MASOOR_FULL.name,
                    quantity = 1.0,
                    purchasePrice = 80.0,
                    sellingPrice = 120.0
                )
                ProductCategory.MASOOR_HALF -> BillLineItem(
                    productName = "Masoor Half",
                    unitType = UnitType.HALF.name,
                    category = ProductCategory.MASOOR_HALF.name,
                    quantity = 1.0,
                    purchasePrice = 45.0,
                    sellingPrice = 70.0
                )
                ProductCategory.ROTI -> BillLineItem(
                    productName = "Roti",
                    unitType = UnitType.PIECE.name,
                    category = ProductCategory.ROTI.name,
                    quantity = 1.0,
                    purchasePrice = 8.0,
                    sellingPrice = 15.0
                )
                ProductCategory.CUSTOM -> BillEditorState.defaultEmptyLine()
            }
        }
        val recalculated = AccountingCalculator.recalculateItem(item)
        _editorState.update { state ->
            // Replace first blank placeholder row if user hasn't typed into it yet
            val baseList = if (
                state.items.size == 1 &&
                state.items.first().productName.isBlank() &&
                state.items.first().sellingPrice == 0.0
            ) {
                emptyList()
            } else {
                state.items
            }
            val updated = baseList + recalculated
            state.copy(
                items = updated,
                totals = AccountingCalculator.computeBillTotals(updated),
                saveError = null
            )
        }
    }

    fun updateLineItem(
        lineId: String,
        productName: String? = null,
        unitType: UnitType? = null,
        category: ProductCategory? = null,
        quantity: Double? = null,
        purchasePrice: Double? = null,
        sellingPrice: Double? = null
    ) {
        _editorState.update { state ->
            val updated = state.items.map { item ->
                if (item.lineId != lineId) {
                    item
                } else {
                    val newName = productName ?: item.productName
                    val newUnit = unitType ?: item.unitEnum
                    val newCat = category ?: ProductCategory.inferFrom(newName, newUnit)
                    AccountingCalculator.recalculateItem(
                        item.copy(
                            productName = newName.take(80),
                            unitType = newUnit.name,
                            category = newCat.name,
                            quantity = quantity ?: item.quantity,
                            purchasePrice = purchasePrice ?: item.purchasePrice,
                            sellingPrice = sellingPrice ?: item.sellingPrice,
                            needsReview = false
                        )
                    )
                }
            }
            state.copy(
                items = updated,
                totals = AccountingCalculator.computeBillTotals(updated),
                saveError = null
            )
        }
    }

    fun removeLineItem(lineId: String) {
        _editorState.update { state ->
            val filtered = state.items.filterNot { it.lineId == lineId }
            val nextItems = filtered.ifEmpty { listOf(BillEditorState.defaultEmptyLine()) }
            state.copy(
                items = nextItems,
                totals = AccountingCalculator.computeBillTotals(nextItems),
                saveError = null
            )
        }
    }

    fun saveEditorBill(onSavedSuccess: () -> Unit) {
        val state = _editorState.value
        if (state.isSaving) return // Prevent duplicate double-tap submissions

        val validItems = state.items.filter { it.productName.isNotBlank() }
        if (validItems.isEmpty()) {
            _editorState.update { it.copy(saveError = "Add at least one product with a name before saving.") }
            return
        }
        val effectiveTitle = state.title.trim().ifBlank {
            val firstProd = validItems.first().productName.trim()
            if (validItems.size > 1) "$firstProd + ${validItems.size - 1} items" else firstProd
        }

        val draft = BillDraftPayload(
            billId = state.billId,
            title = effectiveTitle,
            vendorOrParty = state.vendorOrParty.trim(),
            billDateMillis = state.billDateMillis,
            sourceType = state.sourceType,
            ocrConfidence = state.ocrConfidence,
            ocrWarning = state.ocrWarning,
            hasImage = (state.photoBitmap != null && !state.removeExistingImage),
            localImagePath = if (state.removeExistingImage) "" else state.localImagePath,
            notes = state.notes.trim(),
            items = validItems
        )

        viewModelScope.launch {
            _editorState.update { it.copy(isSaving = true, saveError = null) }

            val result = if (state.isNewBill) {
                repository.saveNewBill(
                    userId = currentUserId,
                    draft = draft,
                    imagePayload = state.pendingCloudImage
                ).map { assignedNum -> "Saved Bill #$assignedNum" }
            } else {
                val existing = state.existingBill
                if (existing == null) {
                    Result.failure(IllegalStateException("Original bill reference is missing."))
                } else {
                    repository.updateExistingBill(
                        userId = currentUserId,
                        existingBill = existing,
                        draft = draft,
                        newImagePayload = state.pendingCloudImage,
                        removeExistingImage = state.removeExistingImage
                    ).map { "Updated Bill #${existing.billNumber}" }
                }
            }

            if (result.isSuccess) {
                // Also save any custom products so user builds an unlimited reusable catalog
                val existingProducts = (productsState.value as? UiState.Success)?.data ?: emptyList()
                val existingKeys = existingProducts.map { it.normalizedName }.toSet()
                validItems.forEach { line ->
                    val norm = CustomProduct.normalizeProductName(line.productName, line.unitEnum)
                    if (norm.isNotBlank() && !existingKeys.contains(norm) && line.categoryEnum == ProductCategory.CUSTOM) {
                        repository.saveCustomProduct(
                            currentUserId,
                            CustomProduct(
                                name = line.productName.trim(),
                                normalizedName = norm,
                                unitType = line.unitType,
                                category = line.category,
                                defaultPurchasePrice = line.purchasePrice,
                                defaultSellingPrice = line.sellingPrice
                            )
                        )
                    }
                }

                _editorState.update { it.copy(isSaving = false, saveError = null) }
                _bannerMessage.value = false to result.getOrThrow()
                onSavedSuccess()
            } else {
                val errMsg = result.exceptionOrNull()?.let { friendlyFirestoreMessage(it) }
                    ?: "Failed to save bill."
                _editorState.update { it.copy(isSaving = false, saveError = errMsg) }
            }
        }
    }

    fun deleteBill(bill: Bill, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isDeletingBill.value = true
            val res = repository.deleteBill(currentUserId, bill)
            _isDeletingBill.value = false
            if (res.isSuccess) {
                _bannerMessage.value = false to "Deleted Bill #${bill.billNumber}"
                onDeleted()
            } else {
                val msg = res.exceptionOrNull()?.let { friendlyFirestoreMessage(it) } ?: "Failed to delete bill."
                _bannerMessage.value = true to msg
            }
        }
    }

    // --- CUSTOM PRODUCTS ---

    fun saveProduct(
        existingId: String,
        name: String,
        unitType: UnitType,
        category: ProductCategory,
        purchasePrice: Double,
        sellingPrice: Double
    ) {
        if (name.isBlank()) {
            _bannerMessage.value = true to "Product name cannot be empty."
            return
        }
        viewModelScope.launch {
            val product = CustomProduct(
                id = existingId,
                name = name.trim(),
                normalizedName = CustomProduct.normalizeProductName(name, unitType),
                unitType = unitType.name,
                category = category.name,
                defaultPurchasePrice = purchasePrice.coerceAtLeast(0.0),
                defaultSellingPrice = sellingPrice.coerceAtLeast(0.0)
            )
            val res = repository.saveCustomProduct(currentUserId, product)
            if (res.isSuccess) {
                _bannerMessage.value = false to "Saved product \"${product.name}\""
            } else {
                val msg = res.exceptionOrNull()?.let { friendlyFirestoreMessage(it) } ?: "Failed to save product."
                _bannerMessage.value = true to msg
            }
        }
    }

    fun deleteProduct(product: CustomProduct) {
        viewModelScope.launch {
            val res = repository.deleteCustomProduct(currentUserId, product.id)
            if (res.isSuccess) {
                _bannerMessage.value = false to "Removed \"${product.name}\""
            } else {
                val msg = res.exceptionOrNull()?.let { friendlyFirestoreMessage(it) } ?: "Failed to delete product."
                _bannerMessage.value = true to msg
            }
        }
    }

    // --- REPORT FILTERS & EXPORTS ---

    fun setReportPeriod(period: ReportPeriod) {
        _reportFilter.update { it.copy(period = period) }
    }

    fun setReportAnchorDate(millis: Long) {
        _reportFilter.update { it.copy(anchorDateMillis = millis) }
    }

    fun setCustomDateRange(startMillis: Long, endMillis: Long) {
        _reportFilter.update {
            it.copy(
                period = ReportPeriod.CUSTOM,
                customStartMillis = minOf(startMillis, endMillis),
                customEndMillis = maxOf(startMillis, endMillis)
            )
        }
    }

    fun shiftReportPeriod(direction: Int) {
        _reportFilter.update { filter ->
            val cal = Calendar.getInstance().apply { timeInMillis = filter.anchorDateMillis }
            when (filter.period) {
                ReportPeriod.DAILY -> cal.add(Calendar.DAY_OF_YEAR, direction)
                ReportPeriod.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, direction)
                ReportPeriod.MONTHLY -> cal.add(Calendar.MONTH, direction)
                ReportPeriod.YEARLY -> cal.add(Calendar.YEAR, direction)
                ReportPeriod.CUSTOM -> cal.add(Calendar.DAY_OF_YEAR, direction)
            }
            filter.copy(anchorDateMillis = cal.timeInMillis)
        }
    }

    fun exportPdfToUri(context: Context, uri: Uri) {
        val summary = reportSummary.value
        viewModelScope.launch {
            try {
                val outStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Unable to open destination file for PDF.")
                val res = outStream.use { exporter.writePdfReport(summary, it) }
                if (res.isSuccess) {
                    _bannerMessage.value = false to "PDF report downloaded successfully."
                } else {
                    _bannerMessage.value = true to (res.exceptionOrNull()?.localizedMessage ?: "PDF export failed.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = true to (e.localizedMessage ?: "PDF export failed.")
            }
        }
    }

    fun exportCsvToUri(context: Context, uri: Uri) {
        val summary = reportSummary.value
        viewModelScope.launch {
            try {
                val outStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Unable to open destination file for CSV.")
                val res = outStream.use { exporter.writeCsvReport(summary, it) }
                if (res.isSuccess) {
                    _bannerMessage.value = false to "Excel CSV report downloaded successfully."
                } else {
                    _bannerMessage.value = true to (res.exceptionOrNull()?.localizedMessage ?: "CSV export failed.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = true to (e.localizedMessage ?: "CSV export failed.")
            }
        }
    }

    fun createShareFile(context: Context, isPdf: Boolean, onReady: (File, String) -> Unit) {
        val summary = reportSummary.value
        val periodSlug = summary.period.name.lowercase()
        viewModelScope.launch {
            val res = if (isPdf) {
                exporter.writePdfToCacheFile(context, summary, "HisabPro_${periodSlug}_report.pdf")
            } else {
                exporter.writeCsvToCacheFile(context, summary, "HisabPro_${periodSlug}_report.csv")
            }
            if (res.isSuccess) {
                val file = res.getOrThrow()
                val mime = if (isPdf) "application/pdf" else "text/csv"
                onReady(file, mime)
            } else {
                _bannerMessage.value = true to (res.exceptionOrNull()?.localizedMessage ?: "Failed to prepare report file.")
            }
        }
    }

    companion object {
        private const val TAG = "HisabVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
