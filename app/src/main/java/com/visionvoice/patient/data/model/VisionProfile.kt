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
enum class FunctionalDominance(val label: String, val workingDistance: String) {
    NEAR_DOMINANT("Near Dominant", "30 - 45 cm"),
    INTERMEDIATE_DOMINANT("Intermediate Dominant", "60 - 100 cm"),
    DISTANCE_DOMINANT("Distance Dominant", "> 200 cm"),
    BALANCED_MULTIFOCAL("Balanced Multifocal", "Varied daily demands")
}

@Serializable
data class OccupationalDemand(
    val occupation: String = "Not specified",
    val primaryDistance: String = "Near & Intermediate",
    val visualDemandsDescription: String = "General daily activities"
)

@Serializable
data class DigitalAndLighting(
    val dailyScreenHours: String = "< 2 hours",
    val lightingCondition: String = "Standard indoor lighting"
)

@Serializable
data class SystemicHistory(
    val hasDiabetes: Boolean = false,
    val hasHypertension: Boolean = false,
    val relevantNotes: String = "No chronic systemic conditions declared"
)

@Serializable
data class VisionProfile(
    val id: String = UUID.randomUUID().toString().take(8).uppercase(),
    val backupCode: String = "VV-" + (1000..9999).random(),
    val selectedLanguage: String = "Hindi",
    val audioGuidanceEnabled: Boolean = true,

    // Core 6 Clinical Dimensions
    val occupationalDemand: OccupationalDemand = OccupationalDemand(),
    val environmentalHazards: List<String> = emptyList(),
    val digitalAndLighting: DigitalAndLighting = DigitalAndLighting(),
    val refractiveSymptoms: List<String> = listOf("Blurry or hazy vision"),
    val systemicHistory: SystemicHistory = SystemicHistory(),
    val pathologyIndicators: List<String> = emptyList(),

    // Extra spontaneous patient symptoms
    val additionalInformation: List<String> = emptyList(),

    // General Clinical Attributes
    val symptoms: List<String> = listOf("Blurry or hazy vision"),
    val duration: String = "2-4 weeks",
    val affectedEye: String = "Both eyes",
    val painLevel: String = "Mild discomfort",
    val photophobia: Boolean = false,
    val riskFactors: List<String> = emptyList(),

    // Functional & Clinical Synthesis
    val functionalDominance: FunctionalDominance = FunctionalDominance.BALANCED_MULTIFOCAL,
    val aiTriageAssessment: String = "Preliminary analysis indicates progressive refractive blur. Recommended for clinical refraction and slit-lamp ophthalmic evaluation.",
    val urgencyLevel: UrgencyLevel = UrgencyLevel.ROUTINE,
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
                occ = "${occupationalDemand.occupation} (${occupationalDemand.primaryDistance})",
                env = environmentalHazards,
                refract = refractiveSymptoms,
                pathology = pathologyIndicators,
                sys = systemicHistory.relevantNotes,
                addInfo = additionalInformation,
                funcDom = functionalDominance.label,
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
    val occ: String,
    val env: List<String>,
    val refract: List<String>,
    val pathology: List<String>,
    val sys: String,
    val addInfo: List<String>,
    val funcDom: String,
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
