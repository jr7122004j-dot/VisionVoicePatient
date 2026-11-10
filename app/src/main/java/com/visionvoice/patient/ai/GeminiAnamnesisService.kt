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

    // 10-turn clinical prompt guidance (for offline fallback & quick reply suggestions)
    val fallbackTenTurnQuestions = listOf(
        TurnQuestion(
            turn = 1,
            title = "Occupation & Primary Needs",
            promptHindi = "नमस्ते! मैं आपका विज़न-वॉयस सहायक हूँ। आप रोज़ाना क्या काम करते हैं और आपको देखने में मुख्य परेशानी क्या हो रही है?",
            promptEnglish = "Hello! I am your VisionVoice assistant. What daily work or occupation do you do, and what is your main visual difficulty?",
            hints = listOf("खेत में काम / Farming", "सिलाई व बुनाई / Tailoring", "दुकान / Office Desk", "ड्राइविंग / Driving")
        ),
        TurnQuestion(
            turn = 2,
            title = "Working Distance Details",
            promptHindi = "काम करते समय आपको सुई-धागा जैसा बारीक पास का काम ज़्यादा करना पड़ता है या दूर का देखना होता है?",
            promptEnglish = "While working, do you need to focus on fine near work like threading needles, or distant viewing?",
            hints = listOf("बारीक पास का काम / Fine near work", "1-2 मीटर की दूरी / Arm's length", "दूर का काम / Distant viewing")
        ),
        TurnQuestion(
            turn = 3,
            title = "Environmental Hazards",
            promptHindi = "क्या आपकी आँखों में चूल्हे का धुआँ, तेज़ धूप, धूल-मिट्टी या कोई रासायनिक पदार्थ जाता है?",
            promptEnglish = "Are your eyes exposed to cooking fire smoke, bright sunlight/UV, dust, or chemicals daily?",
            hints = listOf("चूल्हे का धुआँ / Biomass smoke", "तेज़ धूप व धूल / Bright sun & dust", "सामान्य वातावरण / Normal environment")
        ),
        TurnQuestion(
            turn = 4,
            title = "Digital Strain & Lighting",
            promptHindi = "आप मोबाइल या टीवी कितने घंटे देखते हैं, और आपके कमरे या काम करने की जगह में रोशनी कैसी रहती है?",
            promptEnglish = "How many hours do you use mobile screens or TV, and what is the lighting like where you work?",
            hints = listOf("2 घंटे से कम / < 2 hours", "4-6 घंटे स्क्रीन / 4-6 hours screen", "कम रोशनी में काम / Dim lighting")
        ),
        TurnQuestion(
            turn = 5,
            title = "Refractive Symptoms (Distance vs Near)",
            promptHindi = "क्या आपको अखबार पढ़ने के लिए हाथ आगे फैलाना पड़ता है, या दूर का बोर्ड व चेहरे धुंधले दिखते हैं?",
            promptEnglish = "Do you have to stretch your arm to read newspapers, or do distant signs and faces appear blurry?",
            hints = listOf("पास का धुंधला / Near blur", "दूर का धुंधला / Distance blur", "दोनों धुंधले / Both near & distant blur")
        ),
        TurnQuestion(
            turn = 6,
            title = "Eye Strain & Headaches",
            promptHindi = "क्या शाम होते-होते आपके माथे में दर्द होता है या आँखें सिकोड़कर देखना पड़ता है?",
            promptEnglish = "Do you experience forehead headaches towards the evening, or do you have to squint to see clearly?",
            hints = listOf("शाम को सिरदर्द / Evening headaches", "आँखों में खिंचाव / Eye strain", "कोई दर्द नहीं / No headaches")
        ),
        TurnQuestion(
            turn = 7,
            title = "Systemic Health (Diabetes & BP)",
            promptHindi = "क्या आपको शुगर (डायबिटीज़) या ब्लड प्रेशर की शिकायत है, और आप कोई नियमित दवा लेते हैं?",
            promptEnglish = "Do you have Diabetes or high Blood Pressure, and do you take regular medications for them?",
            hints = listOf("डायबिटीज़ है / Yes, Diabetes", "हाई ब्लड प्रेशर / High BP", "दोनों हैं / Both Diabetes & BP", "कोई बीमारी नहीं / None")
        ),
        TurnQuestion(
            turn = 8,
            title = "Pathology Red Flags",
            promptHindi = "क्या आपको आँखों के आगे जाला, बादल जैसा धुंधलापन, बिजली जैसी चमक या आँखों में तेज़ दर्द महसूस होता है?",
            promptEnglish = "Do you see cloudy haze like a cataract, flashing lights, black floaters, or severe eye pain?",
            hints = listOf("मोतियाबिंद जैसा धुंधला / Cloudy lens haze", "रात में रोशनी के छल्ले / Halos around lights", "सामान्य धुंधलापन / Mild blur only")
        ),
        TurnQuestion(
            turn = 9,
            title = "Additional Information (Open Inquiry)",
            promptHindi = "क्या आँखों, पुरानी चोट या सेहत से जुड़ी कोई ऐसी बात है जो हमने नहीं पूछी पर आप डॉक्टर को बताना चाहते हैं?",
            promptEnglish = "Is there any other eye symptom, past injury, or health concern not covered yet that you want the doctor to know?",
            hints = listOf("आँखों में रेत जैसा चुभना / Gritty sensation", "पुरानी चोट लगी थी / Old eye injury", "कुछ और नहीं / Nothing else")
        ),
        TurnQuestion(
            turn = 10,
            title = "Summary & Completion",
            promptHindi = "धन्यवाद। आपकी सभी जानकारियाँ सुरक्षित रूप से दर्ज कर ली गई हैं। आपकी क्लिनिकल रिपोर्ट तैयार है।",
            promptEnglish = "Thank you. All your details have been recorded. Your pre-clinical screening report is ready for the doctor.",
            hints = listOf("रिपोर्ट देखें / View Summary")
        )
    )

    fun initializeSession(language: String) {
        currentLanguage = language
        if (isLiveApiConfigured) {
            try {
                generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
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
            } catch (e: Exception) {
                activeChat = null
            }
        }
    }

    private fun buildSystemInstruction(language: String): String {
        return """
            You are VisionVoice, an empathetic, pre-clinical ophthalmic screening assistant in rural India.
            Your purpose is to conduct a gentle, 10-turn adaptive voice anamnesis in $language.
            
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

    suspend fun sendPatientMessage(
        turnIndex: Int,
        patientText: String
    ): String = withContext(Dispatchers.IO) {
        if (isLiveApiConfigured && activeChat != null) {
            try {
                val response = activeChat?.sendMessage(
                    content {
                        text("Patient response at turn ${turnIndex + 1}: $patientText")
                    }
                )
                val reply = response?.text?.trim()
                if (!reply.isNullOrBlank()) {
                    return@withContext reply
                }
            } catch (e: Exception) {
                // Fall back to rule-based response
            }
        }

        // Offline / Fallback question progression
        val nextTurnIdx = (turnIndex + 1).coerceAtMost(fallbackTenTurnQuestions.lastIndex)
        val q = fallbackTenTurnQuestions[nextTurnIdx]
        return@withContext if (currentLanguage.equals("English", ignoreCase = true)) {
            q.promptEnglish
        } else {
            q.promptHindi
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

                val result = generativeModel?.generateContent(content { text(extractionPrompt) })
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

data class TurnQuestion(
    val turn: Int,
    val title: String,
    val promptHindi: String,
    val promptEnglish: String,
    val hints: List<String>
)
