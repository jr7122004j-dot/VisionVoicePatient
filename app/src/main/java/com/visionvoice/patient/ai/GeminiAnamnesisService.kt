package com.visionvoice.patient.ai

import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.visionvoice.patient.BuildConfig
import com.visionvoice.patient.data.model.ChatMessage
import com.visionvoice.patient.data.model.DigitalAndLighting
import com.visionvoice.patient.data.model.FunctionalDominance
import com.visionvoice.patient.data.model.MessageSender
import com.visionvoice.patient.data.model.OccupationalDemand
import com.visionvoice.patient.data.model.SystemicHistory
import com.visionvoice.patient.data.model.UrgencyLevel
import com.visionvoice.patient.data.model.VisionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Service orchestrating the adaptive 10-turn AI Anamnesis using Google Gemini API.
 * Includes strict non-diagnostic clinical guardrails, structured JSON extraction,
 * and resilient offline rule-based fallback when no API key or network is present.
 */
class GeminiAnamnesisService(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY
) {
    private val isLiveApiConfigured: Boolean = apiKey.isNotBlank() && apiKey != "\"\""

    private var generativeModel: GenerativeModel? = null
    private var activeChat: Chat? = null
    private var currentLanguage: String = "Hindi"

    val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // 10-turn clinical progression titles
    val turnTitles = listOf(
        "Occupation & Primary Needs",
        "Working Distance Details",
        "Environmental Hazards",
        "Digital Strain & Lighting",
        "Refractive Symptoms",
        "Eye Strain & Headaches",
        "Systemic Health (Diabetes & BP)",
        "Pathology Red Flags",
        "Additional Information",
        "Summary & Completion"
    )

    fun initializeSession(language: String) {
        currentLanguage = language
        if (isLiveApiConfigured) {
            try {
                generativeModel = GenerativeModel(
                    modelName = "gemini-flash-lite-latest",
                    apiKey = apiKey,
                    generationConfig = generationConfig {
                        temperature = 0.4f
                        topK = 40
                        topP = 0.9f
                    },
                    systemInstruction = content {
                        text(buildSystemInstruction(language))
                    }
                )
                activeChat = generativeModel?.startChat()
                android.util.Log.i("GeminiAnamnesis", "✨ Initialized live Gemini session with model: gemini-flash-lite-latest for language: $language")
            } catch (e: Exception) {
                android.util.Log.e("GeminiAnamnesis", "Failed to initialize Gemini session", e)
                activeChat = null
            }
        } else {
            android.util.Log.w("GeminiAnamnesis", "Live Gemini API not configured or key invalid. Running in offline rule-based mode.")
        }
    }

    private fun buildSystemInstruction(language: String): String {
        return """
            You are VisionVoice, an empathetic, female pre-clinical ophthalmic screening assistant (क्लिनिकल सहायिका) in rural India.
            Your purpose is to conduct a gentle, 10-turn adaptive voice anamnesis in $language.
            
            PERSONA & VOICE GENDER RULES:
            - You are female. When conversing in Hindi or Indian languages, always use female first-person grammatical agreements (e.g., 'मैं आपकी सहायिका हूँ', 'मैं समझ सकती हूँ', 'मैं आपकी बात दर्ज कर रही हूँ').
            
            CLINICAL RULES:
            1. NEVER diagnose the patient or prescribe medication. (Do NOT say "You have cataract" or "You have glaucoma". Instead say: "I have recorded this for the doctor to examine.")
            2. BREVITY IS CRITICAL: Each reply MUST be strictly 1 to 2 spoken sentences (maximum 25-30 words) because low-vision patients are listening via text-to-speech.
            3. Always ask only ONE clear, accessible question per turn.
            4. Speak strictly in $language.
            
            PACING OVER 10 TURNS:
            - Turns 1-2: Patient's daily work/occupation and near vs distance visual demands.
            - Turns 3-4: Environmental hazards (biomass smoke, UV, dust) and digital screens/lighting.
            - Turns 5-6: Refractive symptoms (distance vs near blur, reading distance, squinting, headaches).
            - Turns 7-8: Diabetes duration, hypertension, and pathology red flags (cloudiness, halos, flashes, pain).
            - Turn 9 (CRITICAL OPEN INQUIRY): Ask if there are any other specific symptoms, past eye injuries, or concerns the patient wants the doctor to know.
            - Turn 10: Warmly conclude the anamnesis and state the pre-clinical report is ready.
        """.trimIndent()
    }

    suspend fun generateInitialGreeting(language: String): String = withContext(Dispatchers.IO) {
        if (activeChat == null) {
            initializeSession(language)
        }

        if (!isLiveApiConfigured || activeChat == null) {
            throw IllegalStateException("Gemini live API is not configured or active chat is null")
        }

        android.util.Log.d("GeminiAnamnesis", "Generating initial greeting from Gemini in $language")
        val greetingPrompt = """
            Start the clinical screening anamnesis session.
            The patient has chosen $language.
            Greet the patient warmly as their female VisionVoice clinical screening assistant (क्लिनिकल सहायिका).
            Introduce yourself, and ask the first turn question:
            Ask what daily work or occupation they do, and what primary vision or eye difficulty they are experiencing.
            Remember: Brevity is critical (1 to 2 spoken sentences, max 25-30 words).
            Respond strictly in $language.
        """.trimIndent()

        val reply = kotlinx.coroutines.withTimeoutOrNull(7000L) {
            val response = activeChat?.sendMessage(
                content {
                    text(greetingPrompt)
                }
            )
            response?.text?.trim()
        }

        if (!reply.isNullOrBlank()) {
            android.util.Log.i("GeminiAnamnesis", "⚡ Received LIVE initial greeting from Gemini: $reply")
            return@withContext reply
        } else {
            throw java.io.IOException("Gemini response timed out or returned empty for initial greeting")
        }
    }

    suspend fun sendPatientMessage(
        turnIndex: Int,
        patientText: String
    ): String = withContext(Dispatchers.IO) {
        if (!isLiveApiConfigured) {
            throw IllegalStateException("Gemini API is not configured")
        }

        if (activeChat == null) {
            initializeSession(currentLanguage)
        }

        val chat = activeChat ?: throw java.io.IOException("Active Gemini chat session is unavailable")

        android.util.Log.d("GeminiAnamnesis", "Sending message to Gemini (turn ${turnIndex + 1}): $patientText")
        val reply = kotlinx.coroutines.withTimeoutOrNull(7000L) {
            val response = chat.sendMessage(
                content {
                    text("Patient response at turn ${turnIndex + 1}: $patientText")
                }
            )
            response.text?.trim()
        }

        if (!reply.isNullOrBlank()) {
            android.util.Log.i("GeminiAnamnesis", "⚡ Received LIVE reply from Gemini: $reply")
            reply
        } else {
            throw java.io.IOException("Gemini response timed out or returned empty")
        }
    }

    suspend fun extractStructuredProfile(
        chatHistory: List<ChatMessage>,
        baseProfile: VisionProfile
    ): VisionProfile = withContext(Dispatchers.IO) {
        if (isLiveApiConfigured && generativeModel != null && chatHistory.isNotEmpty()) {
            try {
                val transcriptBuilder = StringBuilder()
                chatHistory.forEach { msg ->
                    val role = if (msg.sender == MessageSender.PATIENT) "Patient" else "Assistant"
                    transcriptBuilder.append("$role: ${msg.text}\n")
                }

                val extractionPrompt = """
                    Analyze this 10-turn ophthalmic screening conversation between a rural patient and the assistant:
                    
                    $transcriptBuilder
                    
                    Extract the clinical parameters into a raw JSON object (without markdown blocks) with this exact schema:
                    {
                      "occupation": "string (e.g. Tailor, Farmer, Student)",
                      "primaryDistance": "string (e.g. 35cm near, Intermediate, Outdoor distance)",
                      "environmentalHazards": ["smoke", "sunlight", ...],
                      "dailyScreenHours": "string",
                      "lightingCondition": "string",
                      "refractiveSymptoms": ["near blur", "headache", ...],
                      "hasDiabetes": boolean,
                      "hasHypertension": boolean,
                      "systemicNotes": "string",
                      "pathologyIndicators": ["cloudy lens", "glare halos", ...],
                      "additionalInformation": ["any other patient mentioned symptoms or past injuries not in other fields"],
                      "functionalDominance": "NEAR_DOMINANT or INTERMEDIATE_DOMINANT or DISTANCE_DOMINANT or BALANCED_MULTIFOCAL",
                      "urgencyLevel": "ROUTINE or PRIORITY or URGENT",
                      "aiTriageAssessment": "2-3 sentence clinical triage summary for the optometrist"
                    }
                """.trimIndent()

                val result = kotlinx.coroutines.withTimeoutOrNull(9000L) {
                    generativeModel?.generateContent(content { text(extractionPrompt) })
                }
                val rawJson = result?.text?.replace("```json", "")?.replace("```", "")?.trim()

                if (!rawJson.isNullOrBlank()) {
                    val root = jsonParser.parseToJsonElement(rawJson).jsonObject

                    val occupation = root["occupation"]?.jsonPrimitive?.content ?: "Agricultural / Rural"
                    val primaryDist = root["primaryDistance"]?.jsonPrimitive?.content ?: "Varied distance"
                    val hazards = root["environmentalHazards"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val screenHours = root["dailyScreenHours"]?.jsonPrimitive?.content ?: "Under 2 hours"
                    val lighting = root["lightingCondition"]?.jsonPrimitive?.content ?: "Natural daylight"
                    val refract = root["refractiveSymptoms"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val hasDiabetes = root["hasDiabetes"]?.jsonPrimitive?.booleanOrNull ?: false
                    val hasBp = root["hasHypertension"]?.jsonPrimitive?.booleanOrNull ?: false
                    val sysNotes = root["systemicNotes"]?.jsonPrimitive?.content ?: "No systemic notes"
                    val pathology = root["pathologyIndicators"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    val addInfo = root["additionalInformation"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

                    val funcDomStr = root["functionalDominance"]?.jsonPrimitive?.content ?: "BALANCED_MULTIFOCAL"
                    val funcDom = try {
                        FunctionalDominance.valueOf(funcDomStr)
                    } catch (e: Exception) {
                        FunctionalDominance.BALANCED_MULTIFOCAL
                    }

                    val urgencyStr = root["urgencyLevel"]?.jsonPrimitive?.content ?: "ROUTINE"
                    val urgency = try {
                        UrgencyLevel.valueOf(urgencyStr)
                    } catch (e: Exception) {
                        UrgencyLevel.ROUTINE
                    }

                    val assessment = root["aiTriageAssessment"]?.jsonPrimitive?.content
                        ?: "Pre-clinical screening completed. Recommended for doctor evaluation."

                    return@withContext baseProfile.copy(
                        occupationalDemand = OccupationalDemand(
                            occupation = occupation,
                            primaryDistance = primaryDist,
                            visualDemandsDescription = "Visual priority: $primaryDist"
                        ),
                        environmentalHazards = hazards,
                        digitalAndLighting = DigitalAndLighting(
                            dailyScreenHours = screenHours,
                            lightingCondition = lighting
                        ),
                        refractiveSymptoms = refract,
                        systemicHistory = SystemicHistory(
                            hasDiabetes = hasDiabetes,
                            hasHypertension = hasBp,
                            relevantNotes = sysNotes
                        ),
                        pathologyIndicators = pathology,
                        additionalInformation = addInfo,
                        functionalDominance = funcDom,
                        urgencyLevel = urgency,
                        aiTriageAssessment = assessment,
                        symptoms = (refract + pathology).ifEmpty { listOf("Refractive blur") }
                    )
                }
            } catch (e: Exception) {
                // Fall back to rule-based synthesis
            }
        }

        // Resilient Offline Clinical Heuristic Extractor
        val fullText = chatHistory.filter { it.sender == MessageSender.PATIENT }.joinToString(" ") { it.text }

        val hasTailoring = fullText.contains("सिलाई", true) || fullText.contains("tailor", true) || fullText.contains("बुनाई", true)
        val hasFarming = fullText.contains("खेत", true) || fullText.contains("farm", true) || fullText.contains("धूल", true)
        val hasSmoke = fullText.contains("धुआँ", true) || fullText.contains("smoke", true) || fullText.contains("चूल्हा", true)
        val hasDiabetes = fullText.contains("शुगर", true) || fullText.contains("diabetes", true)
        val hasBp = fullText.contains("प्रेशर", true) || fullText.contains("bp", true)
        val hasCataract = fullText.contains("मोतिया", true) || fullText.contains("cloudy", true) || fullText.contains("जाला", true)

        val funcDom = when {
            hasTailoring -> FunctionalDominance.NEAR_DOMINANT
            hasFarming -> FunctionalDominance.DISTANCE_DOMINANT
            else -> FunctionalDominance.BALANCED_MULTIFOCAL
        }

        val urgency = when {
            hasCataract || (hasDiabetes && fullText.contains("धुंधला", true)) -> UrgencyLevel.URGENT
            hasSmoke || hasDiabetes || hasBp -> UrgencyLevel.PRIORITY
            else -> UrgencyLevel.ROUTINE
        }

        val collectedAdditional = mutableListOf<String>()
        if (fullText.contains("रेत", true) || fullText.contains("चुभन", true) || fullText.contains("sand", true)) {
            collectedAdditional.add("Foreign body / gritty sensation reported")
        }
        if (fullText.contains("चोट", true) || fullText.contains("injury", true)) {
            collectedAdditional.add("Historical ocular trauma mentioned")
        }

        val offlineAssessment = "Patient identified with ${funcDom.label} demands. " +
                "Reported risk factors: ${if (hasSmoke) "Biomass smoke exposure. " else ""}" +
                "${if (hasDiabetes) "Diabetic history. " else ""}${if (hasCataract) "Cataract-like cloudy haze reported. " else ""}" +
                "Clinical referral priority: ${urgency.title}."

        return@withContext baseProfile.copy(
            occupationalDemand = OccupationalDemand(
                occupation = if (hasTailoring) "Tailoring / Craft" else if (hasFarming) "Agriculture / Field" else "General Worker",
                primaryDistance = funcDom.workingDistance
            ),
            environmentalHazards = if (hasSmoke) listOf("Biomass cooking smoke", "UV Sunlight") else listOf("Ambient dust"),
            systemicHistory = SystemicHistory(
                hasDiabetes = hasDiabetes,
                hasHypertension = hasBp,
                relevantNotes = if (hasDiabetes || hasBp) "Chronic conditions noted" else "None declared"
            ),
            pathologyIndicators = if (hasCataract) listOf("Cloudy or hazy lens opacity") else emptyList(),
            additionalInformation = collectedAdditional,
            functionalDominance = funcDom,
            urgencyLevel = urgency,
            aiTriageAssessment = offlineAssessment
        )
    }
}
