package com.visionvoice.patient.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class MessageSender {
    PATIENT,
    AI_ASSISTANT
}

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class UrgencyLevel(val title: String, val badgeColorHex: Long) {
    ROUTINE("Routine Evaluation", 0xFF059669), // Success Emerald
    PRIORITY("Priority Review", 0xFFD97706),    // Warm Amber
    URGENT("Immediate Attention", 0xFFBA1A1A)  // Clinical Error Red
}

@Serializable
data class VisionProfile(
    val id: String = UUID.randomUUID().toString().take(8).uppercase(),
    val backupCode: String = "VV-" + (1000..9999).random(),
    val selectedLanguage: String = "English",
    val audioGuidanceEnabled: Boolean = true,
    val symptoms: List<String> = listOf("Blurry or hazy vision", "Difficulty with night vision"),
    val duration: String = "2-4 weeks",
    val affectedEye: String = "Both eyes",
    val painLevel: String = "Mild strain / discomfort",
    val photophobia: Boolean = true,
    val riskFactors: List<String> = listOf("Screen usage > 6 hrs", "Family history of glaucoma"),
    val aiTriageAssessment: String = "Preliminary analysis indicates progressive bilateral refractive blur accompanied by mild photophobia. No sudden acute loss reported. Recommended for clinical refraction and slit-lamp ophthalmic evaluation.",
    val urgencyLevel: UrgencyLevel = UrgencyLevel.PRIORITY,
    val timestamp: Long = System.currentTimeMillis(),
    val chatHistory: List<ChatMessage> = emptyList()
) {
    /**
     * Compact JSON payload designed for high-density, offline QR code scanning
     * by ophthalmologists and triage workers.
     */
    fun toHandoffPayload(): String {
        return Json.encodeToString(
            HandoffSummary(
                refId = id,
                code = backupCode,
                lang = selectedLanguage,
                sym = symptoms,
                dur = duration,
                eye = affectedEye,
                pain = painLevel,
                risk = riskFactors,
                urgency = urgencyLevel.title,
                triage = aiTriageAssessment,
                ts = timestamp
            )
        )
    }
}

@Serializable
data class HandoffSummary(
    val refId: String,
    val code: String,
    val lang: String,
    val sym: List<String>,
    val dur: String,
    val eye: String,
    val pain: String,
    val risk: List<String>,
    val urgency: String,
    val triage: String,
    val ts: Long
)

data class AnamnesisQuestion(
    val index: Int,
    val totalCount: Int,
    val title: String,
    val spokenPrompt: String,
    val hints: List<String>
)
