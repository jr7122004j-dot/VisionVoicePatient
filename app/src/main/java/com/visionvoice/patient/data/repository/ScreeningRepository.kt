package com.visionvoice.patient.data.repository

import android.content.Context
import com.visionvoice.patient.data.model.VisionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * ensuring complete offline resilience before cloud handoff.
 */
class ScreeningRepository(private val context: Context) {

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
        try {
            val file = File(screeningsDir, "profile_${profile.id}.json")
            val serialized = json.encodeToString(profile)
            file.writeText(serialized)

            // Also keep a pointer to latest profile
            val latestFile = File(screeningsDir, "latest_profile.json")
            latestFile.writeText(serialized)

            _syncStatus.value = SyncStatus.LOCAL_SAVED
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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
