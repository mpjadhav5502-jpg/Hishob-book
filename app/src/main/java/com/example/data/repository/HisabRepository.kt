package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AccountingCalculator
import com.example.data.model.Bill
import com.example.data.model.BillLineItem
import com.example.data.model.BillSourceType
import com.example.data.model.CustomProduct
import com.example.data.model.OcrConfidence
import com.example.data.model.ProductCategory
import com.example.data.model.UnitType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class BillDraftPayload(
    val billId: String,
    val title: String,
    val vendorOrParty: String,
    val billDateMillis: Long,
    val sourceType: BillSourceType,
    val ocrConfidence: OcrConfidence,
    val ocrWarning: String,
    val hasImage: Boolean,
    val localImagePath: String,
    val notes: String,
    val items: List<BillLineItem>
)

class HisabRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val localImageStore: BillImageLocalStore? = null
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        localImageStore = BillImageLocalStore(context.applicationContext)
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    fun observeBills(userId: String): Flow<List<Bill>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/bills"
        emitAll(
            db.collection("users").document(userId).collection("bills")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Bill::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<Bill> { it.billDateMillis }
                            .thenByDescending { it.billNumber }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exToLog = firestoreError ?: (error as? Exception)
                    if (exToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProducts(userId: String): Flow<List<CustomProduct>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/products"
        emitAll(
            db.collection("users").document(userId).collection("products")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CustomProduct::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exToLog = firestoreError ?: (error as? Exception)
                    if (exToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureDefaultProducts(userId: String): Result<Unit> {
        val path = "users/$userId/products"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val productsRef = db.collection("users").document(uid).collection("products")
            val existing = productsRef.get().await()
            val existingIds = existing.documents.map { it.id }.toSet()

            val presets = listOf(
                "preset_masoor_full" to CustomProduct(
                    name = "Masoor Full",
                    normalizedName = "masoor full (full)",
                    unitType = UnitType.FULL.name,
                    category = ProductCategory.MASOOR_FULL.name,
                    defaultPurchasePrice = 80.0,
                    defaultSellingPrice = 120.0
                ),
                "preset_masoor_half" to CustomProduct(
                    name = "Masoor Half",
                    normalizedName = "masoor half (half)",
                    unitType = UnitType.HALF.name,
                    category = ProductCategory.MASOOR_HALF.name,
                    defaultPurchasePrice = 45.0,
                    defaultSellingPrice = 70.0
                ),
                "preset_roti" to CustomProduct(
                    name = "Roti",
                    normalizedName = "roti (piece)",
                    unitType = UnitType.PIECE.name,
                    category = ProductCategory.ROTI.name,
                    defaultPurchasePrice = 8.0,
                    defaultSellingPrice = 15.0
                )
            )

            for ((docId, preset) in presets) {
                if (!existingIds.contains(docId)) {
                    productsRef.document(docId).set(preset.toCreateMap(uid)).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveNewBill(
        userId: String,
        draft: BillDraftPayload,
        imagePayload: CompressedBillImage?
    ): Result<Long> {
        val docPath = "users/$userId/bills/${draft.billId}"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(draft.billId.isNotBlank()) { "Bill ID must not be blank." }
            require(draft.title.trim().isNotEmpty()) { "Bill title is required." }

            val cleanedItems = draft.items
                .filter { it.productName.isNotBlank() }
                .take(100)
                .map { AccountingCalculator.recalculateItem(it) }
            require(cleanedItems.isNotEmpty()) { "At least one product row is required to save a bill." }

            val totals = AccountingCalculator.computeBillTotals(cleanedItems)
            val userDoc = db.collection("users").document(uid)
            val summaryRef = userDoc.collection("meta").document("ledgerSummary")
            val billRef = userDoc.collection("bills").document(draft.billId)
            val imageRef = userDoc.collection("billImages").document(draft.billId)

            val assignedBillNumber = db.runTransaction { transaction ->
                // All reads MUST precede all writes in Firestore transactions
                val summarySnap = transaction.get(summaryRef)
                val billSnap = transaction.get(billRef)

                if (billSnap.exists()) {
                    throw IllegalStateException("Bill already exists. Duplicate creation prevented.")
                }

                val currentNext = (summarySnap.getLong("nextBillNumber") ?: 1L).coerceAtLeast(1L)
                val currentTotalBills = (summarySnap.getLong("totalBills") ?: 0L).coerceAtLeast(0L)

                if (summarySnap.exists()) {
                    transaction.update(
                        summaryRef,
                        mapOf(
                            "nextBillNumber" to (currentNext + 1L),
                            "totalBills" to (currentTotalBills + 1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    transaction.set(
                        summaryRef,
                        mapOf(
                            "userId" to uid,
                            "nextBillNumber" to (currentNext + 1L),
                            "totalBills" to (currentTotalBills + 1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val billMap = buildCreateBillMap(
                    uid = uid,
                    billNumber = currentNext,
                    draft = draft,
                    cleanedItems = cleanedItems,
                    totals = totals
                )
                transaction.set(billRef, billMap)

                if (imagePayload != null) {
                    transaction.set(
                        imageRef,
                        mapOf(
                            "userId" to uid,
                            "imageBase64" to imagePayload.base64,
                            "mimeType" to imagePayload.mimeType,
                            "width" to imagePayload.width,
                            "height" to imagePayload.height,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                currentNext
            }.await()

            Result.success(assignedBillNumber)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateExistingBill(
        userId: String,
        existingBill: Bill,
        draft: BillDraftPayload,
        newImagePayload: CompressedBillImage?,
        removeExistingImage: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/bills/${existingBill.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(existingBill.id.isNotBlank()) { "Existing bill ID must not be blank." }
            require(draft.title.trim().isNotEmpty()) { "Bill title is required." }

            val cleanedItems = draft.items
                .filter { it.productName.isNotBlank() }
                .take(100)
                .map { AccountingCalculator.recalculateItem(it) }
            require(cleanedItems.isNotEmpty()) { "At least one product row is required." }

            val totals = AccountingCalculator.computeBillTotals(cleanedItems)
            val userDoc = db.collection("users").document(uid)
            val billRef = userDoc.collection("bills").document(existingBill.id)
            val imageRef = userDoc.collection("billImages").document(existingBill.id)

            db.runTransaction { transaction ->
                val billSnap = transaction.get(billRef)
                val imageSnap = transaction.get(imageRef)

                if (!billSnap.exists()) {
                    throw IllegalStateException("Bill no longer exists in the database.")
                }

                val updateMap = buildUpdateBillMap(
                    draft = draft,
                    cleanedItems = cleanedItems,
                    totals = totals
                )
                transaction.update(billRef, updateMap)

                if (removeExistingImage) {
                    if (imageSnap.exists()) {
                        transaction.delete(imageRef)
                    }
                } else if (newImagePayload != null) {
                    if (imageSnap.exists()) {
                        transaction.update(
                            imageRef,
                            mapOf(
                                "imageBase64" to newImagePayload.base64,
                                "mimeType" to newImagePayload.mimeType,
                                "width" to newImagePayload.width,
                                "height" to newImagePayload.height,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        transaction.set(
                            imageRef,
                            mapOf(
                                "userId" to uid,
                                "imageBase64" to newImagePayload.base64,
                                "mimeType" to newImagePayload.mimeType,
                                "width" to newImagePayload.width,
                                "height" to newImagePayload.height,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }.await()

            if (removeExistingImage) {
                localImageStore?.deleteLocalImage(existingBill.id, existingBill.localImagePath)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBill(userId: String, bill: Bill): Result<Unit> {
        val docPath = "users/$userId/bills/${bill.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user delete." }
            require(bill.id.isNotBlank()) { "Bill ID must not be blank." }

            val userDoc = db.collection("users").document(uid)
            val summaryRef = userDoc.collection("meta").document("ledgerSummary")
            val billRef = userDoc.collection("bills").document(bill.id)
            val imageRef = userDoc.collection("billImages").document(bill.id)

            db.runTransaction { transaction ->
                val summarySnap = transaction.get(summaryRef)
                val billSnap = transaction.get(billRef)
                val imageSnap = transaction.get(imageRef)

                if (billSnap.exists()) {
                    transaction.delete(billRef)
                }
                if (imageSnap.exists()) {
                    transaction.delete(imageRef)
                }
                if (summarySnap.exists()) {
                    val currentTotal = (summarySnap.getLong("totalBills") ?: 0L).coerceAtLeast(0L)
                    transaction.update(
                        summaryRef,
                        mapOf(
                            "totalBills" to (currentTotal - 1L).coerceAtLeast(0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            localImageStore?.deleteLocalImage(bill.id, bill.localImagePath)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun loadBillImageBase64(userId: String, billId: String): Result<String?> {
        val docPath = "users/$userId/billImages/$billId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val snap = db.collection("users").document(uid)
                .collection("billImages").document(billId)
                .get()
                .await()
            Result.success(snap.getString("imageBase64"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCustomProduct(userId: String, product: CustomProduct): Result<Unit> {
        val docId = product.id.ifBlank {
            CustomProduct.docIdFor(product.name, product.unitEnum)
        }
        val docPath = "users/$userId/products/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(product.name.trim().isNotEmpty()) { "Product name cannot be blank." }
            val ref = db.collection("users").document(uid).collection("products").document(docId)
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(product.toUpdateMap()).await()
            } else {
                ref.set(product.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomProduct(userId: String, productId: String): Result<Unit> {
        val docPath = "users/$userId/products/$productId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(productId.isNotBlank()) { "Product ID cannot be blank." }
            db.collection("users").document(uid)
                .collection("products").document(productId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun buildCreateBillMap(
        uid: String,
        billNumber: Long,
        draft: BillDraftPayload,
        cleanedItems: List<BillLineItem>,
        totals: com.example.data.model.BillTotals
    ): Map<String, Any> = mapOf(
        "userId" to uid,
        "billNumber" to billNumber,
        "title" to draft.title.trim().take(120),
        "vendorOrParty" to draft.vendorOrParty.trim().take(120),
        "billDateMillis" to draft.billDateMillis.coerceAtLeast(1L),
        "sourceType" to draft.sourceType.name,
        "ocrConfidence" to draft.ocrConfidence.name,
        "ocrWarning" to draft.ocrWarning.trim().take(400),
        "hasImage" to draft.hasImage,
        "localImagePath" to draft.localImagePath.take(500),
        "notes" to draft.notes.trim().take(500),
        "items" to cleanedItems.map { it.toFirestoreMap() },
        "totalQuantity" to totals.totalQuantity,
        "totalPurchaseCost" to totals.totalPurchaseCost,
        "totalSellingAmount" to totals.totalSellingAmount,
        "totalProfit" to totals.totalProfit,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun buildUpdateBillMap(
        draft: BillDraftPayload,
        cleanedItems: List<BillLineItem>,
        totals: com.example.data.model.BillTotals
    ): Map<String, Any> = mapOf(
        "title" to draft.title.trim().take(120),
        "vendorOrParty" to draft.vendorOrParty.trim().take(120),
        "billDateMillis" to draft.billDateMillis.coerceAtLeast(1L),
        "sourceType" to draft.sourceType.name,
        "ocrConfidence" to draft.ocrConfidence.name,
        "ocrWarning" to draft.ocrWarning.trim().take(400),
        "hasImage" to draft.hasImage,
        "localImagePath" to draft.localImagePath.take(500),
        "notes" to draft.notes.trim().take(500),
        "items" to cleanedItems.map { it.toFirestoreMap() },
        "totalQuantity" to totals.totalQuantity,
        "totalPurchaseCost" to totals.totalPurchaseCost,
        "totalSellingAmount" to totals.totalSellingAmount,
        "totalProfit" to totals.totalProfit,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
