package com.visionvoice.patient.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.visionvoice.patient.ai.GeminiAnamnesisService
import com.visionvoice.patient.data.model.ChatMessage
import com.visionvoice.patient.data.model.MessageSender
import com.visionvoice.patient.data.model.VisionProfile
import com.visionvoice.patient.data.repository.ScreeningRepository
import com.visionvoice.patient.data.repository.SyncStatus
import com.visionvoice.patient.voice.VoiceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class ScreeningViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "visionvoice_patient_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "pref_onboarding_completed"
        private const val KEY_SELECTED_LANGUAGE = "pref_selected_language"
        private const val KEY_AUDIO_GUIDANCE = "pref_audio_guidance"
        private const val KEY_PATIENT_NAME = "pref_patient_name"
        private const val KEY_PATIENT_AGE = "pref_patient_age"
        private const val KEY_PATIENT_GENDER = "pref_patient_gender"
    }

    enum class AnamnesisStage(
        val stageNumber: Int,
        val title: String,
        val subtitle: String
    ) {
        PROFILE_CONTEXT(1, "Profile & Context", "Demographics & language verified"),
        VISION_WORK(2, "Vision Needs & Work", "Occupational demands & lighting"),
        HEALTH_SYMPTOMS(3, "Health & Symptoms", "Refraction, smoke & systemic"),
        REVIEW_HANDOFF(4, "Review & Handoff", "Triage summary ready for doctor")
    }

    private val prefs: SharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    private val _showOnboardingDialog = MutableStateFlow(!isOnboardingCompleted)
    val showOnboardingDialog: StateFlow<Boolean> = _showOnboardingDialog.asStateFlow()

    val voiceManager = VoiceManager(application)
    val geminiService = GeminiAnamnesisService()
    val repository = ScreeningRepository(application)

    private val _profile = MutableStateFlow(VisionProfile())
    val profile: StateFlow<VisionProfile> = _profile.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus

    val turnTitles: List<String> = geminiService.turnTitles

    private val _currentTurnIndex = MutableStateFlow(0)
    val currentTurnIndex: StateFlow<Int> = _currentTurnIndex.asStateFlow()

    fun getCurrentStage(): AnamnesisStage {
        val turn = _currentTurnIndex.value
        return when {
            turn <= 2 -> AnamnesisStage.VISION_WORK
            turn in 3..6 -> AnamnesisStage.HEALTH_SYMPTOMS
            else -> AnamnesisStage.REVIEW_HANDOFF
        }
    }

    fun getStageProgressFraction(): Float {
        val turn = _currentTurnIndex.value
        return when {
            turn <= 2 -> 0.25f + (turn / 3f) * 0.25f
            turn in 3..6 -> 0.50f + ((turn - 3) / 4f) * 0.25f
            else -> 0.75f + ((turn - 7) / 3f).coerceAtMost(1f) * 0.25f
        }.coerceIn(0.25f, 1.0f)
    }

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _isConnectingToAi = MutableStateFlow(false)
    val isConnectingToAi: StateFlow<Boolean> = _isConnectingToAi.asStateFlow()

    private val _aiConnectionStatus = MutableStateFlow<String?>(null)
    val aiConnectionStatus: StateFlow<String?> = _aiConnectionStatus.asStateFlow()

    private val _aiConnectionError = MutableStateFlow<String?>(null)
    val aiConnectionError: StateFlow<String?> = _aiConnectionError.asStateFlow()

    private val _lastPendingPatientResponse = MutableStateFlow<String?>(null)
    val lastPendingPatientResponse: StateFlow<String?> = _lastPendingPatientResponse.asStateFlow()

    val isListening: StateFlow<Boolean> = voiceManager.isListening
    val isSpeaking: StateFlow<Boolean> = voiceManager.isSpeaking
    val voiceTranscript: StateFlow<String> = voiceManager.liveTranscript
    val voiceRmsDb: StateFlow<Float> = voiceManager.rmsDb
    val voiceError: StateFlow<String?> = voiceManager.voiceError

    val hasRecordedAudio: StateFlow<Boolean> = voiceManager.hasRecordedAudio
    val isPatientAudioPlaying: StateFlow<Boolean> = voiceManager.isPatientAudioPlaying
    val recordedAudioDurationMs: StateFlow<Int> = voiceManager.recordedAudioDurationMs

    private val _isPlayingAudio = MutableStateFlow(false)
    val isPlayingAudio: StateFlow<Boolean> = _isPlayingAudio.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    init {
        val savedName = prefs.getString(KEY_PATIENT_NAME, "") ?: ""
        val savedAge = prefs.getInt(KEY_PATIENT_AGE, 0)
        val savedGender = prefs.getString(KEY_PATIENT_GENDER, "Male") ?: "Male"
        val savedLanguage = prefs.getString(KEY_SELECTED_LANGUAGE, "Hindi") ?: "Hindi"
        val savedAudioGuidance = prefs.getBoolean(KEY_AUDIO_GUIDANCE, true)

        _profile.update { current ->
            current.copy(
                patientName = savedName,
                patientAge = savedAge,
                patientGender = savedGender,
                selectedLanguage = savedLanguage,
                audioGuidanceEnabled = savedAudioGuidance
            )
        }
        voiceManager.setLanguage(savedLanguage)
        initializeSession(savedLanguage, savedName, savedAge, savedGender)

        viewModelScope.launch {
            voiceManager.isSpeaking.collect { speaking ->
                if (!speaking && _isPlayingAudio.value) {
                    _playingMessageId.value = null
                    _isPlayingAudio.value = false
                }
            }
        }
    }

    private fun initializeSession(
        language: String,
        name: String = _profile.value.patientName,
        age: Int = _profile.value.patientAge,
        gender: String = _profile.value.patientGender
    ) {
        geminiService.initializeSession(language, name, age, gender)
        _profile.update { current ->
            current.copy(
                selectedLanguage = language,
                patientName = name,
                patientAge = age,
                patientGender = gender,
                chatHistory = emptyList()
            )
        }
    }

    fun completeOnboarding(
        name: String,
        age: Int,
        gender: String,
        language: String,
        audioGuidance: Boolean
    ) {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .putString(KEY_PATIENT_NAME, name)
            .putInt(KEY_PATIENT_AGE, age)
            .putString(KEY_PATIENT_GENDER, gender)
            .putString(KEY_SELECTED_LANGUAGE, language)
            .putBoolean(KEY_AUDIO_GUIDANCE, audioGuidance)
            .apply()

        _profile.update { current ->
            current.copy(
                patientName = name,
                patientAge = age,
                patientGender = gender,
                selectedLanguage = language,
                audioGuidanceEnabled = audioGuidance
            )
        }

        setLanguage(language)
        setAudioGuidance(audioGuidance)
        _showOnboardingDialog.value = false

        initializeSession(language, name, age, gender)
        loadInitialGreeting(language, name, age, gender)
    }

    fun openLanguageSettingsDialog() {
        _showOnboardingDialog.value = true
    }

    fun dismissLanguageSettingsDialog() {
        if (isOnboardingCompleted) {
            _showOnboardingDialog.value = false
        }
    }

    fun updateLanguageSettings(
        name: String,
        age: Int,
        gender: String,
        language: String,
        audioGuidance: Boolean
    ) {
        val languageChanged = _profile.value.selectedLanguage != language
        val detailsChanged = _profile.value.patientName != name ||
            _profile.value.patientAge != age ||
            _profile.value.patientGender != gender

        prefs.edit()
            .putString(KEY_PATIENT_NAME, name)
            .putInt(KEY_PATIENT_AGE, age)
            .putString(KEY_PATIENT_GENDER, gender)
            .putString(KEY_SELECTED_LANGUAGE, language)
            .putBoolean(KEY_AUDIO_GUIDANCE, audioGuidance)
            .apply()

        _profile.update { current ->
            current.copy(
                patientName = name,
                patientAge = age,
                patientGender = gender,
                selectedLanguage = language,
                audioGuidanceEnabled = audioGuidance
            )
        }

        setLanguage(language)
        setAudioGuidance(audioGuidance)
        _showOnboardingDialog.value = false

        if (languageChanged || detailsChanged) {
            resetScreening()
        }
    }

    fun resetScreening() {
        stopAudio()
        _currentTurnIndex.value = 0
        _aiConnectionError.value = null
        _aiConnectionStatus.value = null
        _isConnectingToAi.value = false
        _isAiThinking.value = false
        _lastPendingPatientResponse.value = null
        val currentLang = _profile.value.selectedLanguage
        val currentAudio = _profile.value.audioGuidanceEnabled
        val name = _profile.value.patientName
        val age = _profile.value.patientAge
        val gender = _profile.value.patientGender
        initializeSession(currentLang, name, age, gender)
        _profile.update { it.copy(audioGuidanceEnabled = currentAudio) }
        loadInitialGreeting(currentLang, name, age, gender)
    }

    fun startAnamnesis() {
        // If first-time onboarding dialog is currently being shown, defer anamnesis until user taps "Begin"
        if (_showOnboardingDialog.value && !isOnboardingCompleted) {
            return
        }

        val existingAssistantMsg = _profile.value.chatHistory
            .lastOrNull { it.sender == MessageSender.AI_ASSISTANT }?.text
        if (existingAssistantMsg != null) {
            triggerAudioGuidance(existingAssistantMsg)
        } else {
            loadInitialGreeting(
                _profile.value.selectedLanguage,
                _profile.value.patientName,
                _profile.value.patientAge,
                _profile.value.patientGender
            )
        }
    }

    fun loadInitialGreeting(
        language: String = _profile.value.selectedLanguage,
        name: String = _profile.value.patientName,
        age: Int = _profile.value.patientAge,
        gender: String = _profile.value.patientGender
    ) {
        if (_profile.value.chatHistory.any { it.sender == MessageSender.AI_ASSISTANT }) {
            return
        }

        _isAiThinking.value = true
        _aiConnectionError.value = null

        viewModelScope.launch {
            val maxRetryDurationMs = 30_000L
            val startTime = System.currentTimeMillis()
            var greeting: String? = null

            while (System.currentTimeMillis() - startTime < maxRetryDurationMs) {
                try {
                    greeting = geminiService.generateInitialGreeting(language, name, age, gender)
                    if (!greeting.isNullOrBlank()) {
                        break
                    }
                } catch (e: Exception) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val remainingSeconds = ((maxRetryDurationMs - elapsed) / 1000).coerceAtLeast(1)
                    _isConnectingToAi.value = true
                    _aiConnectionStatus.value = "Connecting to AI Assistant... Retrying (${remainingSeconds}s)"
                    delay(3000L)
                }
            }

            _isConnectingToAi.value = false
            _aiConnectionStatus.value = null
            _isAiThinking.value = false

            if (!greeting.isNullOrBlank()) {
                _profile.update { current ->
                    current.copy(
                        chatHistory = listOf(
                            ChatMessage(
                                sender = MessageSender.AI_ASSISTANT,
                                text = greeting
                            )
                        )
                    )
                }
                triggerAudioGuidance(greeting)
            } else {
                _aiConnectionError.value = "Internet connectivity is needed for VisionVoice AI. Please check your network connection and retry."
            }
        }
    }

    fun setLanguage(language: String) {
        _profile.update { it.copy(selectedLanguage = language) }
        voiceManager.setLanguage(language)
        initializeSession(language)
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
            voiceManager.discardRecordedAudio()
            voiceManager.startListening { resultText ->
                // On speech recognition result, stored in liveTranscript
            }
        }
    }

    fun toggleRecordedAudioPlayback() {
        if (voiceManager.isPatientAudioPlaying.value) {
            voiceManager.stopAudioPlayback()
        } else {
            voiceManager.playRecordedAudio()
        }
    }

    fun discardRecordedAudio() {
        voiceManager.discardRecordedAudio()
    }

    fun submitCurrentResponse() {
        if (_isAiThinking.value || _isConnectingToAi.value) return

        val transcriptText = voiceManager.liveTranscript.value.trim()
        val audioFile = voiceManager.recordedAudioFile
        val durationMs = voiceManager.recordedAudioDurationMs.value

        if (transcriptText.isNotBlank()) {
            voiceManager.discardRecordedAudio()
            submitVoiceResponse(transcriptText)
        } else if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
            submitAudioResponse(audioFile, durationMs)
        }
    }

    fun submitAudioResponse(audioFile: File, durationMs: Int) {
        if (_isAiThinking.value || _isConnectingToAi.value) return

        val turn = _currentTurnIndex.value
        _aiConnectionError.value = null
        _isAiThinking.value = true

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val maxRetryDurationMs = 30_000L
            var result: Pair<String, String>? = null
            var attempt = 0

            while (System.currentTimeMillis() - startTime < maxRetryDurationMs && result == null) {
                attempt++
                try {
                    if (attempt > 1) {
                        _isConnectingToAi.value = true
                        val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                        val remainingSec = (30 - elapsedSec).coerceAtLeast(0)
                        _aiConnectionStatus.value = "Connecting to AI Assistant... Retrying (${remainingSec}s)"
                    }

                    result = geminiService.sendPatientAudio(turn, audioFile, _profile.value.selectedLanguage)
                } catch (e: Exception) {
                    android.util.Log.w("ScreeningViewModel", "Attempt $attempt audio send failed: ${e.message}")
                    _isConnectingToAi.value = true
                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    val remainingSec = (30 - elapsedSec).coerceAtLeast(0)
                    _aiConnectionStatus.value = "Connecting to AI Assistant... Retrying (${remainingSec}s)"

                    if (System.currentTimeMillis() - startTime + 3000L < maxRetryDurationMs) {
                        delay(3000L)
                    } else {
                        break
                    }
                }
            }

            _isAiThinking.value = false
            _isConnectingToAi.value = false
            _aiConnectionStatus.value = null

            if (result != null) {
                val (transcript, aiReply) = result
                voiceManager.discardRecordedAudio()

                val isSilence = transcript.contains("[No speech detected]", ignoreCase = true)
                if (isSilence) {
                    _profile.update { current ->
                        current.copy(
                            chatHistory = current.chatHistory +
                                ChatMessage(sender = MessageSender.AI_ASSISTANT, text = aiReply)
                        )
                    }
                    triggerAudioGuidance(aiReply)
                } else {
                    val displayText = transcript.ifBlank { "Spoken response" }
                    _profile.update { current ->
                        current.copy(
                            chatHistory = current.chatHistory +
                                ChatMessage(sender = MessageSender.PATIENT, text = displayText) +
                                ChatMessage(sender = MessageSender.AI_ASSISTANT, text = aiReply)
                        )
                    }
                    triggerAudioGuidance(aiReply)

                    if (turn < turnTitles.lastIndex) {
                        _currentTurnIndex.value = turn + 1
                    } else {
                        finalizeTriage()
                    }
                }
            } else {
                _aiConnectionError.value = "Internet connectivity is needed for VisionVoice AI. Please check your network connection and retry."
            }
        }
    }

    fun clearVoiceError() {
        voiceManager.clearError()
    }

    fun submitVoiceResponse(text: String) {
        if (_isAiThinking.value || _isConnectingToAi.value) return

        val turn = _currentTurnIndex.value
        _aiConnectionError.value = null

        // 1. Record Patient's Voice/Response (avoid duplicate appending if retrying)
        val lastMsg = _profile.value.chatHistory.lastOrNull()
        if (lastMsg == null || lastMsg.sender != MessageSender.PATIENT || lastMsg.text != text) {
            _profile.update { current ->
                current.copy(
                    chatHistory = current.chatHistory + ChatMessage(
                        sender = MessageSender.PATIENT,
                        text = text
                    )
                )
            }
        }

        // 2. Query Gemini Adaptive Anamnesis Engine with 30-second retry loop
        _isAiThinking.value = true
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val maxRetryDurationMs = 30_000L
            var aiReply: String? = null
            var attempt = 0

            while (System.currentTimeMillis() - startTime < maxRetryDurationMs && aiReply == null) {
                attempt++
                try {
                    if (attempt > 1) {
                        _isConnectingToAi.value = true
                        val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                        val remainingSec = (30 - elapsedSec).coerceAtLeast(0)
                        _aiConnectionStatus.value = "Connecting to AI Assistant... Retrying (${remainingSec}s)"
                    }

                    aiReply = geminiService.sendPatientMessage(turn, text)
                } catch (e: Exception) {
                    android.util.Log.w("ScreeningViewModel", "Attempt $attempt failed: ${e.message}")
                    _isConnectingToAi.value = true
                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    val remainingSec = (30 - elapsedSec).coerceAtLeast(0)
                    _aiConnectionStatus.value = "Connecting to AI Assistant... Retrying (${remainingSec}s)"

                    if (System.currentTimeMillis() - startTime + 3000L < maxRetryDurationMs) {
                        delay(3000L)
                    } else {
                        break
                    }
                }
            }

            _isAiThinking.value = false
            _isConnectingToAi.value = false
            _aiConnectionStatus.value = null

            if (!aiReply.isNullOrBlank()) {
                _lastPendingPatientResponse.value = null
                _profile.update { current ->
                    current.copy(
                        chatHistory = current.chatHistory + ChatMessage(
                            sender = MessageSender.AI_ASSISTANT,
                            text = aiReply
                        )
                    )
                }

                // Spoken output for low-vision patients
                triggerAudioGuidance(aiReply)

                if (turn < turnTitles.lastIndex) {
                    _currentTurnIndex.value = turn + 1
                } else {
                    finalizeTriage()
                }
            } else {
                // 30 seconds expired without live Gemini connection
                _lastPendingPatientResponse.value = text
                _aiConnectionError.value = "Internet connectivity is needed for VisionVoice AI. Please check your network connection and retry."
            }
        }
    }

    fun retryConnection() {
        _aiConnectionError.value = null
        if (_profile.value.chatHistory.none { it.sender == MessageSender.AI_ASSISTANT }) {
            startAnamnesis()
        } else {
            val pendingText = _lastPendingPatientResponse.value
            if (!pendingText.isNullOrBlank()) {
                submitVoiceResponse(pendingText)
            }
        }
    }

    fun stopAudio() {
        voiceManager.stopSpeaking()
        voiceManager.stopAudioPlayback()
        _isPlayingAudio.value = false
        _playingMessageId.value = null
    }

    fun dismissAiConnectionError() {
        _aiConnectionError.value = null
    }

    private suspend fun finalizeTriage() {
        _isAiThinking.value = true
        try {
            val updatedProfile = geminiService.extractStructuredProfile(
                chatHistory = _profile.value.chatHistory,
                baseProfile = _profile.value
            )
            _profile.value = updatedProfile
            repository.saveProfile(updatedProfile)
        } catch (e: Exception) {
            // Keep existing base profile and persist
            repository.saveProfile(_profile.value)
        } finally {
            _isAiThinking.value = false
        }
    }

    fun persistCurrentProfile() {
        viewModelScope.launch {
            repository.saveProfile(_profile.value)
        }
    }

    fun toggleAudioPlayback(text: String? = null) {
        if (voiceManager.isSpeaking.value) {
            voiceManager.stopSpeaking()
            _isPlayingAudio.value = false
        } else {
            val toSpeak = text ?: _profile.value.chatHistory
                .lastOrNull { it.sender == MessageSender.AI_ASSISTANT }?.text
            if (!toSpeak.isNullOrBlank()) {
                speakPrompt(toSpeak)
            }
        }
    }

    fun replayAudio(text: String? = null) {
        val toSpeak = text ?: _profile.value.chatHistory
            .lastOrNull { it.sender == MessageSender.AI_ASSISTANT }?.text
        if (!toSpeak.isNullOrBlank()) {
            speakPrompt(toSpeak)
        }
    }

    fun getCurrentTurnTitle(): String {
        return turnTitles.getOrElse(_currentTurnIndex.value) { "Clinical Screening" }
    }

    fun toggleMessageAudio(message: ChatMessage) {
        if (_playingMessageId.value == message.id && (voiceManager.isSpeaking.value || _isPlayingAudio.value)) {
            stopAudio()
        } else {
            stopAudio()
            _playingMessageId.value = message.id
            _isPlayingAudio.value = true
            voiceManager.speak(message.text) {
                if (_playingMessageId.value == message.id) {
                    _playingMessageId.value = null
                    _isPlayingAudio.value = false
                }
            }
        }
    }

    private fun speakPrompt(prompt: String) {
        _isPlayingAudio.value = true
        voiceManager.speak(prompt) {
            _isPlayingAudio.value = false
        }
    }

    fun triggerAudioGuidance(prompt: String, messageId: String? = null) {
        if (_profile.value.audioGuidanceEnabled) {
            val targetId = messageId ?: _profile.value.chatHistory
                .lastOrNull { it.sender == MessageSender.AI_ASSISTANT }?.id
            _playingMessageId.value = targetId
            _isPlayingAudio.value = true
            voiceManager.speak(prompt) {
                if (_playingMessageId.value == targetId) {
                    _playingMessageId.value = null
                    _isPlayingAudio.value = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
    }
}
