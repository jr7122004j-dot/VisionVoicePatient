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
import java.io.File

/**
 * Service orchestrating the adaptive AI Anamnesis using Google Gemini API.
 * Conducts an empathetic, age-tailored, occupation-aware, and environment-conscious
 * pre-clinical ophthalmic investigation without gender bias.
 */
class GeminiAnamnesisService(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY
) {
    private val isLiveApiConfigured: Boolean = apiKey.isNotBlank() && apiKey != "\"\""

    private var generativeModel: GenerativeModel? = null
    private var activeChat: Chat? = null
    private var currentLanguage: String = "Hindi"
    private var currentPatientName: String = ""
    private var currentPatientAge: Int = 0
    private var currentPatientGender: String = "Male"

    val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Clinical stage progression titles
    val turnTitles = listOf(
        "Occupation & Primary Needs",
        "Working Distance Details",
        "Environmental Hazards (Smoke & UV)",
        "Digital Strain & Lighting",
        "Refractive Symptoms",
        "Eye Strain & Headaches",
        "Systemic Health (Diabetes & BP)",
        "Pathology Red Flags",
        "Additional Information",
        "Summary & Completion"
    )

    fun initializeSession(
        language: String,
        patientName: String = "",
        patientAge: Int = 0,
        patientGender: String = "Male"
    ) {
        currentLanguage = language
        currentPatientName = patientName
        currentPatientAge = patientAge
        currentPatientGender = patientGender

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
                        text(buildSystemInstruction(language, patientName, patientAge, patientGender))
                    }
                )
                activeChat = generativeModel?.startChat()
                android.util.Log.i("GeminiAnamnesis", "✨ Initialized Gemini session for $patientName ($patientAge y/o, $patientGender) in $language")
            } catch (e: Exception) {
                android.util.Log.e("GeminiAnamnesis", "Failed to initialize Gemini session", e)
                activeChat = null
            }
        } else {
            android.util.Log.w("GeminiAnamnesis", "Live Gemini API not configured or key invalid. Running in offline rule-based mode.")
        }
    }

    private fun buildSystemInstruction(
        language: String,
        patientName: String,
        patientAge: Int,
        patientGender: String
    ): String {
        val safeName = patientName.trim().ifEmpty { "Patient" }
        val ageContext = if (patientAge > 0) "$patientAge years old" else "Adult"
        val isMale = patientGender.equals("Male", ignoreCase = true)
        val isFemale = patientGender.equals("Female", ignoreCase = true)

        val genderInstruction = when {
            isMale -> """
                PATIENT GENDER IS MALE:
                - When speaking in Hindi or regional Indian languages, ALWAYS address the patient with respectful MASCULINE honorific verb forms (e.g., 'आप कैसे हैं?', 'आप क्या काम करते हैं?', 'क्या आपको देखने में परेशानी होती है?').
                - NEVER use feminine verb agreements for a male patient (DO NOT say 'करती हैं', 'कैसी हैं', 'आती हैं' when asking him).
                - Address him respectfully by name with honorifics: '$safeName जी'.
            """.trimIndent()
            isFemale -> """
                PATIENT GENDER IS FEMALE:
                - When speaking in Hindi or regional Indian languages, ALWAYS address the patient with respectful FEMININE honorific verb forms (e.g., 'आप कैसी हैं?', 'आप क्या काम करती हैं?', 'क्या आपको परेशानी होती है?').
                - Address her respectfully by name with honorifics: '$safeName जी'.
            """.trimIndent()
            else -> """
                PATIENT GENDER IS OTHER / NEUTRAL:
                - Use respectful, gender-neutral honorific address: '$safeName जी'.
            """.trimIndent()
        }

        val ageProbingGuidance = when {
            patientAge in 1..19 -> """
                AGE CONTEXT ($patientAge years - Pediatric / Student):
                - Tailor questions for young learners: classroom blackboard visibility, reading textbooks, mobile screen usage, squinting or frequent blinking, sitting too close to the TV.
            """.trimIndent()
            patientAge in 20..39 -> """
                AGE CONTEXT ($patientAge years - Working Age Adult):
                - Tailor questions for occupational demands: daily digital screens, working lighting, dry eyes, outdoor dust/glare, and distance vs near focus shifting.
            """.trimIndent()
            patientAge in 40..59 -> """
                AGE CONTEXT ($patientAge years - Presbyopia & Early Systemic Age):
                - High probability of presbyopia: specifically probe difficulty reading small print (e.g. mobile text, newspapers, bills), threading needles, holding things further away, eye strain during near tasks.
                - Probe for occupational smoke, dust, sunlight, and early signs of diabetes or hypertension.
            """.trimIndent()
            patientAge >= 60 -> """
                AGE CONTEXT ($patientAge years - Senior Adult):
                - High risk of cataracts and degenerative ocular conditions: specifically probe cloudy/foggy/yellowish vision, glare or halos around headlights/lamps at night, difficulty recognizing faces across the road, and history of diabetes/blood pressure.
            """.trimIndent()
            else -> """
                AGE CONTEXT (General Adult):
                - Inquire about both near vision (reading, close tasks) and distance vision (outdoors, road signs, TV).
            """.trimIndent()
        }

        return """
            You are VisionVoice, an empathetic, female pre-clinical ophthalmic screening assistant (क्लिनिकल सहायिका) in rural India.
            You are conducting a gentle, adaptive voice anamnesis in $language.
            
            PATIENT INFORMATION:
            - Name: $safeName
            - Age: $ageContext
            - Gender: $patientGender
            
            $genderInstruction
            
            ASSISTANT VOICE & PERSONA:
            - You are female. In Hindi/Indian languages, always use female first-person grammatical agreements (e.g., 'मैं आपकी सहायिका हूँ', 'मैं समझ सकती हूँ', 'मैं आपकी बात दर्ज कर रही हूँ').
            
            CLINICAL RULES:
            1. NEVER diagnose or prescribe medication. (Do NOT say "You have cataract" or "You have glaucoma". Instead say: "I have recorded this for the doctor to examine.")
            2. BREVITY IS CRITICAL: Each reply MUST be strictly 1 to 2 spoken sentences (maximum 25-30 words) because low-vision patients are listening via text-to-speech.
            3. Always ask only ONE clear, accessible question per turn.
            4. Speak strictly in $language.
            
            $ageProbingGuidance
            
            ENVIRONMENTAL & OCCUPATIONAL PROBING:
            - Regularly probe for local environmental hazards: chulha/biomass wood cooking smoke, intense agricultural sunlight/UV glare, heavy dust/sand, welding sparks, or long uninterrupted mobile screen hours.
            
            PACING OVER THE ANAMNESIS:
            - Turns 1-2: Daily work/occupation, working distance (near, intermediate, far), and primary eye complaint.
            - Turns 3-4: Environmental hazards (biomass smoke, dust, UV glare) and screen/lighting exposure.
            - Turns 5-6: Refractive symptoms (near vs distance blur, reading distance, squinting, headaches).
            - Turns 7-8: Systemic health (diabetes duration, blood pressure) and pathology red flags (cloudiness, halos, flashes, sudden vision loss, pain).
            - Turn 9 (CRITICAL OPEN INQUIRY): Ask if there are any other specific symptoms, past eye injuries, or concerns the patient wants the doctor to know.
            - Turn 10: Warmly conclude the anamnesis and state the pre-clinical report is ready.
        """.trimIndent()
    }

    suspend fun generateInitialGreeting(
        language: String,
        patientName: String = currentPatientName,
        patientAge: Int = currentPatientAge,
        patientGender: String = currentPatientGender
    ): String = withContext(Dispatchers.IO) {
        if (activeChat == null) {
            initializeSession(language, patientName, patientAge, patientGender)
        }

        if (!isLiveApiConfigured || activeChat == null) {
            throw IllegalStateException("Gemini live API is not configured or active chat is null")
        }

        val safeName = patientName.trim().ifEmpty { "Patient" }
        android.util.Log.d("GeminiAnamnesis", "Generating initial greeting for $safeName ($patientGender, $patientAge) in $language")

        val greetingPrompt = """
            Start the clinical screening anamnesis session.
            The patient is $safeName, age $patientAge, gender $patientGender.
            The selected language is $language.
            
            Instructions:
            1. Greet the patient warmly and respectfully by name (e.g. '$safeName जी' in Hindi) as their female VisionVoice clinical screening assistant (क्लिनिकल सहायिका).
            2. Briefly introduce yourself in 1 short sentence.
            3. Ask the first turn question: What daily work or occupation do they do, and what primary vision or eye problem are they experiencing today?
            4. CRITICAL GRAMMAR: Follow strict grammatical agreement for the patient's gender '$patientGender' (use masculine honorific forms if Male, feminine if Female).
            5. BREVITY: Strictly 1 to 2 spoken sentences (maximum 25-30 words).
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
            initializeSession(currentLanguage, currentPatientName, currentPatientAge, currentPatientGender)
        }

        val chat = activeChat ?: throw java.io.IOException("Active Gemini chat session is unavailable")

        android.util.Log.d("GeminiAnamnesis", "Sending message to Gemini (turn ${turnIndex + 1}): $patientText")
        val reply = kotlinx.coroutines.withTimeoutOrNull(7000L) {
            val response = chat.sendMessage(
                content {
                    text("Patient ($currentPatientGender, $currentPatientAge y/o) response at turn ${turnIndex + 1}: $patientText")
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

    suspend fun sendPatientAudio(
        turnIndex: Int,
        audioFile: File,
        language: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        if (!isLiveApiConfigured) {
            throw IllegalStateException("Gemini API is not configured")
        }

        if (activeChat == null) {
            initializeSession(currentLanguage, currentPatientName, currentPatientAge, currentPatientGender)
        }

        val chat = activeChat ?: throw java.io.IOException("Active Gemini chat session is unavailable")

        val audioBytes = audioFile.readBytes()
        val audioPrompt = """
            The patient ($currentPatientGender, $currentPatientAge y/o) has spoken their response in audio for turn ${turnIndex + 1} of the clinical screening.
            The selected language is $language.
            
            Task:
            1. Listen to the audio carefully and transcribe EXACTLY what the patient said.
               STRICT GUARDRAIL: If the audio is silent, inaudible, static noise, or no human speech is heard, DO NOT hallucinate or invent a response. Set PATIENT_TRANSCRIPT to "[No speech detected]" and set ASSISTANT_REPLY to gently ask the patient to speak again in 1 short sentence in $language.
            2. If human speech is heard, formulate your gentle, 1-2 sentence response as the female VisionVoice clinical screening assistant in $language, maintaining correct grammatical gender agreements for $currentPatientGender.
            
            Format your response EXACTLY like this:
            PATIENT_TRANSCRIPT: <the patient's words in $language, or [No speech detected]>
            ASSISTANT_REPLY: <your 1-2 sentence reply in $language>
        """.trimIndent()

        android.util.Log.d("GeminiAnamnesis", "Sending audio message to Gemini (${audioBytes.size} bytes)")
        val reply = kotlinx.coroutines.withTimeoutOrNull(15000L) {
            val response = chat.sendMessage(
                content {
                    blob("audio/mp4", audioBytes)
                    text(audioPrompt)
                }
            )
            response.text?.trim()
        }

        if (!reply.isNullOrBlank()) {
            android.util.Log.i("GeminiAnamnesis", "⚡ Received LIVE reply from Gemini for audio: $reply")
            var transcript = ""
            var assistantReply = ""

            val lines = reply.lines()
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("PATIENT_TRANSCRIPT:", ignoreCase = true) -> {
                        transcript = trimmed.substringAfter(":").trim()
                    }
                    trimmed.startsWith("ASSISTANT_REPLY:", ignoreCase = true) -> {
                        assistantReply = trimmed.substringAfter(":").trim()
                    }
                }
            }

            if (assistantReply.isBlank()) {
                assistantReply = reply
                    .replace(Regex("(?i)PATIENT_TRANSCRIPT:.*"), "")
                    .replace(Regex("(?i)ASSISTANT_REPLY:"), "")
                    .trim()
            }
            if (transcript.isBlank()) {
                transcript = "Patient voice response"
            }

            Pair(transcript, assistantReply)
        } else {
            throw java.io.IOException("Gemini response timed out or returned empty for audio")
        }
    }

    suspend fun extractStructuredProfile(
        chatHistory: List<ChatMessage>,
        baseProfile: VisionProfile
    ): VisionProfile = withContext(Dispatchers.IO) {
        if (isLiveApiConfigured && generativeModel != null && chatHistory.isNotEmpty()) {
            try {
                val transcriptBuilder = StringBuilder()
                transcriptBuilder.append("Patient: ${baseProfile.patientName}, Age: ${baseProfile.patientAge}, Gender: ${baseProfile.patientGender}\n")
                chatHistory.forEach { msg ->
                    val role = if (msg.sender == MessageSender.PATIENT) "Patient" else "Assistant"
                    transcriptBuilder.append("$role: ${msg.text}\n")
                }

                val extractionPrompt = """
                    Analyze this ophthalmic screening conversation for patient '${baseProfile.patientName}' (${baseProfile.patientAge} y/o, ${baseProfile.patientGender}):
                    
                    $transcriptBuilder
                    
                    Extract the clinical parameters into a raw JSON object (without markdown blocks) with this exact schema:
                    {
                      "occupation": "string (e.g. Tailor, Farmer, Student)",
                      "primaryDistance": "string (e.g. 35cm near, Intermediate, Outdoor distance)",
                      "environmentalHazards": ["biomass smoke", "sunlight UV", "dust", ...],
                      "dailyScreenHours": "string",
                      "lightingCondition": "string",
                      "refractiveSymptoms": ["near blur", "headache", "squinting", ...],
                      "hasDiabetes": boolean,
                      "hasHypertension": boolean,
                      "systemicNotes": "string",
                      "pathologyIndicators": ["cloudy lens", "glare halos", ...],
                      "additionalInformation": ["any other patient mentioned symptoms, ocular trauma, or concerns"],
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
                        ?: "Pre-clinical screening completed for ${baseProfile.patientName}. Recommended for doctor evaluation."

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

        val age = baseProfile.patientAge
        val isSenior = age >= 60
        val isMiddleAge = age in 40..59

        val urgency = when {
            hasCataract || (hasDiabetes && fullText.contains("धुंधला", true)) -> UrgencyLevel.URGENT
            hasSmoke || hasDiabetes || hasBp || isSenior -> UrgencyLevel.PRIORITY
            else -> UrgencyLevel.ROUTINE
        }

        val collectedAdditional = mutableListOf<String>()
        if (fullText.contains("रेत", true) || fullText.contains("चुभन", true) || fullText.contains("sand", true)) {
            collectedAdditional.add("Foreign body / gritty sensation reported")
        }
        if (fullText.contains("चोट", true) || fullText.contains("injury", true)) {
            collectedAdditional.add("Historical ocular trauma mentioned")
        }

        val presbyopicNote = if (isMiddleAge) "Probable presbyopic near-strain. " else ""
        val offlineAssessment = "Patient ${baseProfile.patientName.ifEmpty { "Patient" }} (Age: ${if (age > 0) age.toString() else "Adult"}, ${baseProfile.patientGender}) identified with ${funcDom.label} demands. " +
                "Reported risk factors: ${if (hasSmoke) "Biomass cooking smoke exposure. " else ""}" +
                presbyopicNote +
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
