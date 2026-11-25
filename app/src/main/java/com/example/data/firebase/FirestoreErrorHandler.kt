package com.example.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun friendlyFirestoreMessage(error: Throwable): String {
    val root = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull() ?: error

    if (root is FirebaseFirestoreException) {
        return when (root.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Access denied by database security rules. Please ensure you are signed in with Google and try again."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Network unavailable. Please check your internet connection and retry."
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "Request timed out while contacting the database. Please try again."
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                "Firebase Firestore daily quota exceeded. Please check your Firebase console quota."
            FirebaseFirestoreException.Code.ALREADY_EXISTS ->
                "A record with this ID already exists. Duplicate save prevented."
            FirebaseFirestoreException.Code.NOT_FOUND ->
                "Record not found. It may have been deleted."
            else -> root.localizedMessage ?: "Database error (${root.code.name})."
        }
    }
    return root.localizedMessage ?: "Unexpected database error occurred."
}
