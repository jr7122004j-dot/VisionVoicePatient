package com.visionvoice.patient.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.visionvoice.patient.data.model.AnamnesisQuestion
import com.visionvoice.patient.data.model.ChatMessage
import com.visionvoice.patient.data.model.MessageSender
import com.visionvoice.patient.data.model.UrgencyLevel
import com.visionvoice.patient.data.model.VisionProfile
import com.visionvoice.patient.voice.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScreeningViewModel(application: Application) : AndroidViewModel(application) {

    val voiceManager = VoiceManager(application)

    private val _profile = MutableStateFlow(VisionProfile())
    val profile: StateFlow<VisionProfile> = _profile.asStateFlow()

    val questions: List<AnamnesisQuestion> = listOf(
        AnamnesisQuestion(
            index = 1,
            totalCount = 4,
            title = "Visual Symptoms",
            spokenPrompt = "What primary changes in your vision or eye discomfort have you noticed recently?",
            hints = listOf("Blurry or hazy vision", "Trouble focusing up close", "Difficulty with night vision", "Occasional double vision")
        ),
        AnamnesisQuestion(
            index = 2,
            totalCount = 4,
            title = "Duration & Affected Eye",
            spokenPrompt = "How long has this been occurring, and is it affecting one eye or both?",
            hints = listOf("Under 1 week", "2-4 weeks", "Over 2 months", "Both eyes", "Right eye predominantly", "Left eye only")
        ),
        AnamnesisQuestion(
            index = 3,
            totalCount = 4,
            title = "Pain & Light Sensitivity",
            spokenPrompt = "Do you feel pain, pressure, or noticeable sensitivity when stepping into bright sunlight?",
            hints = listOf("Mild eye fatigue", "Moderate throbbing", "Sensitive to glare (Photophobia)", "Zero pain or headache")
        ),
        AnamnesisQuestion(
            index = 4,
            totalCount = 4,
            title = "Medical History & Risks",
            spokenPrompt = "Do you have diabetes, high blood pressure, or a family history of glaucoma or cataracts?",
            hints = listOf("Type 2 Diabetes", "Hypertension", "Glaucoma family history", "High daily screen exposure", "No known conditions")
        )
    )

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    val isListening: StateFlow<Boolean> = voiceManager.isListening
    val isSpeaking: StateFlow<Boolean> = voiceManager.isSpeaking
    val voiceTranscript: StateFlow<String> = voiceManager.liveTranscript
    val voiceError: StateFlow<String?> = voiceManager.voiceError

    private val _isPlayingAudio = MutableStateFlow(false)
    val isPlayingAudio: StateFlow<Boolean> = _isPlayingAudio.asStateFlow()

    private val _audioProgress = MutableStateFlow(0f)
    val audioProgress: StateFlow<Float> = _audioProgress.asStateFlow()

    init {
        val initialQuestion = questions[0]
        _profile.update { current ->
            current.copy(
                chatHistory = listOf(
                    ChatMessage(
                        sender = MessageSender.AI_ASSISTANT,
                        text = "Hello! I am your VisionVoice clinical assistant. Let's understand your vision symptoms together. ${initialQuestion.spokenPrompt}"
                    )
                )
            )
        }
    }

    fun setLanguage(language: String) {
        _profile.update { it.copy(selectedLanguage = language) }
        voiceManager.setLanguage(language)
    }

    fun setAudioGuidance(enabled: Boolean) {
        _profile.update { it.copy(audioGuidanceEnabled = enabled) }
        if (!enabled) {
            voiceManager.stopSpeaking()
        }
    }

    fun toggleListening() {
        if (voiceManager.isListening.value) {
            voiceManager.stopListening()
        } else {
            voiceManager.startListening { resultText ->
                if (resultText.isNotBlank()) {
                    submitVoiceResponse(resultText)
                }
            }
        }
    }

    fun stopListening() {
        voiceManager.stopListening()
    }

    fun submitVoiceResponse(text: String) {
        _profile.update { current ->
            val updatedChat = current.chatHistory + ChatMessage(
                sender = MessageSender.PATIENT,
                text = text
            )

            when (_currentQuestionIndex.value) {
                0 -> current.copy(
                    symptoms = (current.symptoms + text).distinct(),
                    chatHistory = updatedChat
                )
                1 -> current.copy(
                    duration = if (text.contains("week") || text.contains("month")) text else current.duration,
                    affectedEye = if (text.contains("Both", ignoreCase = true)) "Both eyes" else text,
                    chatHistory = updatedChat
                )
                2 -> current.copy(
                    painLevel = text,
                    photophobia = text.contains("glare", ignoreCase = true) || text.contains("light", ignoreCase = true),
                    chatHistory = updatedChat
                )
                3 -> current.copy(
                    riskFactors = (current.riskFactors + text).distinct(),
                    chatHistory = updatedChat
                )
                else -> current.copy(chatHistory = updatedChat)
            }
        }

        if (_currentQuestionIndex.value < questions.lastIndex) {
            val nextIdx = _currentQuestionIndex.value + 1
            _currentQuestionIndex.value = nextIdx
            val nextQ = questions[nextIdx]
            _profile.update { current ->
                current.copy(
                    chatHistory = current.chatHistory + ChatMessage(
                        sender = MessageSender.AI_ASSISTANT,
                        text = nextQ.spokenPrompt
                    )
                )
            }
            triggerAudioGuidance(nextQ.spokenPrompt)
        } else {
            finalizeTriage()
        }
    }

    fun selectHintResponse(hint: String) {
        submitVoiceResponse(hint)
    }

    private fun finalizeTriage() {
        val p = _profile.value
        val hasHighRisk = p.riskFactors.any { it.contains("Diabetes", ignoreCase = true) || it.contains("Glaucoma", ignoreCase = true) }
        val hasSevereSymptoms = p.painLevel.contains("Sharp", ignoreCase = true) || p.photophobia

        val urgency = when {
            hasSevereSymptoms -> UrgencyLevel.URGENT
            hasHighRisk -> UrgencyLevel.PRIORITY
            else -> UrgencyLevel.ROUTINE
        }

        val assessment = "Patient reports ${p.symptoms.joinToString(", ")} ongoing for ${p.duration}. " +
                "Reported discomfort: ${p.painLevel}. Light sensitivity present: ${if (p.photophobia) "Yes" else "No"}. " +
                "Underlying risk factors identified: ${p.riskFactors.joinToString(", ")}. " +
                "Clinical referral classification: ${urgency.title}."

        val summaryVoice = "Thank you. Your pre-clinical screening assessment is complete. I have generated your clinical summary."

        _profile.update { current ->
            current.copy(
                urgencyLevel = urgency,
                aiTriageAssessment = assessment,
                chatHistory = current.chatHistory + ChatMessage(
                    sender = MessageSender.AI_ASSISTANT,
                    text = summaryVoice
                )
            )
        }

        triggerAudioGuidance(summaryVoice)
    }

    fun toggleAudioPlayback() {
        if (voiceManager.isSpeaking.value) {
            voiceManager.stopSpeaking()
            _isPlayingAudio.value = false
        } else {
            val currentQ = questions[_currentQuestionIndex.value]
            speakPrompt(currentQ.spokenPrompt)
        }
    }

    fun seekAudio(progress: Float) {
        _audioProgress.value = progress.coerceIn(0f, 1f)
    }

    fun replayAudio() {
        val currentQ = questions[_currentQuestionIndex.value]
        speakPrompt(currentQ.spokenPrompt)
    }

    private fun speakPrompt(prompt: String) {
        _isPlayingAudio.value = true
        voiceManager.speak(prompt) {
            _isPlayingAudio.value = false
            _audioProgress.value = 1f
        }
    }

    fun triggerAudioGuidance(prompt: String) {
        if (_profile.value.audioGuidanceEnabled) {
            speakPrompt(prompt)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
    }
}
