package com.visionvoice.patient.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.visionvoice.patient.data.model.VisionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

enum class SyncStatus(val label: String) {
    LOCAL_SAVED("Saved Locally (Offline Ready)"),
    SYNCED_CLOUD("Synced with Clinic Cloud"),
    SYNC_PENDING("Queued for Clinic Sync")
}

/**
 * Offline-first repository for VisionVoice patient profiles.
 * Saves clinical profiles to private device storage immediately,
 * ensuring complete offline resilience, and asynchronously syncs to
 * Cloud Firestore under an anonymous authenticated session.
 */
class ScreeningRepository(private val context: Context) {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val screeningsDir: File by lazy {
        File(context.filesDir, "screenings").apply {
            if (!exists()) mkdirs()
        }
    }

    private val _syncStatus = MutableStateFlow(SyncStatus.LOCAL_SAVED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    suspend fun saveProfile(profile: VisionProfile): Boolean = withContext(Dispatchers.IO) {
        // 1. Immediately persist to local device storage (100% offline guarantee)
        val localSaved = try {
            val file = File(screeningsDir, "profile_${profile.id}.json")
            val serialized = json.encodeToString(profile)
            file.writeText(serialized)

            val latestFile = File(screeningsDir, "latest_profile.json")
            latestFile.writeText(serialized)

            _syncStatus.value = SyncStatus.LOCAL_SAVED
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

        // 2. Attempt silent anonymous auth + Cloud Firestore sync
        try {
            syncToFirestore(profile)
        } catch (e: Exception) {
            // Keep local saved status gracefully if offline/network fails
            _syncStatus.value = SyncStatus.LOCAL_SAVED
        }

        localSaved
    }

    private suspend fun syncToFirestore(profile: VisionProfile) = withContext(Dispatchers.IO) {
        try {
            _syncStatus.value = SyncStatus.SYNC_PENDING

            // Ensure anonymous authentication is active
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }

            val uid = auth.currentUser?.uid ?: "anonymous"

            val firestoreData = hashMapOf(
                "id" to profile.id,
                "backupCode" to profile.backupCode,
                "authorUid" to uid,
                "selectedLanguage" to profile.selectedLanguage,
                "occupation" to profile.occupationalDemand.occupation,
                "primaryDistance" to profile.occupationalDemand.primaryDistance,
                "occupationalDemands" to profile.occupationalDemand.visualDemandsDescription,
                "environmentalHazards" to profile.environmentalHazards,
                "dailyScreenHours" to profile.digitalAndLighting.dailyScreenHours,
                "lightingCondition" to profile.digitalAndLighting.lightingCondition,
                "refractiveSymptoms" to profile.refractiveSymptoms,
                "systemicHistory" to hashMapOf(
                    "hasDiabetes" to profile.systemicHistory.hasDiabetes,
                    "hasHypertension" to profile.systemicHistory.hasHypertension,
                    "relevantNotes" to profile.systemicHistory.relevantNotes
                ),
                "pathologyIndicators" to profile.pathologyIndicators,
                "additionalInformation" to profile.additionalInformation,
                "functionalDominance" to profile.functionalDominance.name,
                "functionalDominanceLabel" to profile.functionalDominance.label,
                "aiTriageAssessment" to profile.aiTriageAssessment,
                "urgencyLevel" to profile.urgencyLevel.name,
                "urgencyTitle" to profile.urgencyLevel.title,
                "timestamp" to profile.timestamp,
                "syncedAt" to System.currentTimeMillis()
            )

            firestore.collection("screening_profiles")
                .document(profile.id)
                .set(firestoreData, SetOptions.merge())
                .await()

            _syncStatus.value = SyncStatus.SYNCED_CLOUD
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.LOCAL_SAVED
        }
    }

    suspend fun loadLatestProfile(): VisionProfile? = withContext(Dispatchers.IO) {
        try {
            val latestFile = File(screeningsDir, "latest_profile.json")
            if (latestFile.exists()) {
                val content = latestFile.readText()
                json.decodeFromString<VisionProfile>(content)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getAllProfiles(): List<VisionProfile> = withContext(Dispatchers.IO) {
        try {
            val files = screeningsDir.listFiles { file -> file.name.startsWith("profile_") && file.name.endsWith(".json") }
            files?.mapNotNull { file ->
                try {
                    json.decodeFromString<VisionProfile>(file.readText())
                } catch (e: Exception) {
                    null
                }
            }?.sortedByDescending { it.timestamp } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
