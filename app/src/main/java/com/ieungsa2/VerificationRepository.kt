package com.ieungsa2

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/**
 * Handles Firestore data synchronization for user verification and impersonation prevention.
 */
class VerificationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val requestsCollection = db.collection("verification_requests")
    private val usersCollection = db.collection("users")

    private fun normalizePhone(phone: String): String {
        return phone.replace("-", "").replace(" ", "").trim()
    }

    /**
     * Updates the user's FCM token in Firestore associated with their phone number.
     */
    fun updateMyToken(phone: String, token: String) {
        val normalizedPhone = normalizePhone(phone)
        val userData = hashMapOf(
            "phone" to normalizedPhone,
            "fcmToken" to token,
            "lastUpdated" to System.currentTimeMillis()
        )
        usersCollection.document(normalizedPhone).set(userData, SetOptions.merge())
            .addOnSuccessListener { Log.d("VerificationRepo", "User token registered: $normalizedPhone") }
            .addOnFailureListener { e -> Log.e("VerificationRepo", "Failed to register token", e) }
    }

    /**
     * Retrieves the target user's FCM token from Firestore.
     */
    fun getTargetToken(phone: String, callback: (String?) -> Unit) {
        val normalizedPhone = normalizePhone(phone)
        usersCollection.document(normalizedPhone).get()
            .addOnSuccessListener { doc ->
                callback(doc.getString("fcmToken"))
            }
            .addOnFailureListener { callback(null) }
    }

    /**
     * Creates a new verification request in Firestore.
     */
    fun sendVerificationRequest(requesterPhone: String, targetPhone: String, callback: (String?) -> Unit) {
        val requestId = UUID.randomUUID().toString()
        val requestData = hashMapOf(
            "requestId" to requestId,
            "requesterPhone" to normalizePhone(requesterPhone),
            "targetPhone" to normalizePhone(targetPhone),
            "status" to "PENDING",
            "timestamp" to System.currentTimeMillis()
        )

        requestsCollection.document(requestId).set(requestData)
            .addOnSuccessListener { callback(requestId) }
            .addOnFailureListener { callback(null) }
    }

    /**
     * Listens for status updates on a specific verification request.
     */
    fun listenForVerificationResult(requestId: String): Flow<String> = callbackFlow {
        val registration = requestsCollection.document(requestId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.getString("status") ?: "PENDING")
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Listens for new incoming verification requests for the current user.
     */
    fun listenForIncomingRequests(myPhone: String): Flow<Map<String, Any>> = callbackFlow {
        val normalized = normalizePhone(myPhone)
        val registration = requestsCollection
            .whereEqualTo("targetPhone", normalized)
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                for (doc in snapshots?.documentChanges ?: emptyList()) {
                    if (doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        trySend(doc.document.data)
                    }
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Updates the status of a verification request (Approved or Rejected).
     */
    fun approveRequest(requestId: String, isApproved: Boolean) {
        val newStatus = if (isApproved) "APPROVED" else "REJECTED"
        requestsCollection.document(requestId).update("status", newStatus)
    }
}