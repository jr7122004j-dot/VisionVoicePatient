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

class ScreeningViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "visionvoice_patient_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "pref_onboarding_completed"
        private const val KEY_SELECTED_LANGUAGE = "pref_selected_language"
        private const val KEY_AUDIO_GUIDANCE = "pref_audio_guidance"
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

    private val _isPlayingAudio = MutableStateFlow(false)
    val isPlayingAudio: StateFlow<Boolean> = _isPlayingAudio.asStateFlow()

    init {
        val savedLanguage = prefs.getString(KEY_SELECTED_LANGUAGE, "Hindi") ?: "Hindi"
        val savedAudioGuidance = prefs.getBoolean(KEY_AUDIO_GUIDANCE, true)
        _profile.update { current ->
            current.copy(
                selectedLanguage = savedLanguage,
                audioGuidanceEnabled = savedAudioGuidance
            )
        }
        voiceManager.setLanguage(savedLanguage)
        initializeSession(savedLanguage)
    }

    private fun initializeSession(language: String) {
        geminiService.initializeSession(language)
        _profile.update { current ->
            current.copy(
                selectedLanguage = language,
                chatHistory = emptyList()
            )
        }
    }

    fun completeOnboarding(language: String, audioGuidance: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .putString(KEY_SELECTED_LANGUAGE, language)
            .putBoolean(KEY_AUDIO_GUIDANCE, audioGuidance)
            .apply()

        setLanguage(language)
        setAudioGuidance(audioGuidance)
        _showOnboardingDialog.value = false

        loadInitialGreeting(language)
    }

    fun openLanguageSettingsDialog() {
        _showOnboardingDialog.value = true
    }

    fun dismissLanguageSettingsDialog() {
        if (isOnboardingCompleted) {
            _showOnboardingDialog.value = false
        }
    }

    fun updateLanguageSettings(language: String, audioGuidance: Boolean) {
        val languageChanged = _profile.value.selectedLanguage != language
        prefs.edit()
            .putString(KEY_SELECTED_LANGUAGE, language)
            .putBoolean(KEY_AUDIO_GUIDANCE, audioGuidance)
            .apply()

        setLanguage(language)
        setAudioGuidance(audioGuidance)
        _showOnboardingDialog.value = false

        if (languageChanged) {
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
        initializeSession(currentLang)
        _profile.update { it.copy(audioGuidanceEnabled = currentAudio) }
        loadInitialGreeting(currentLang)
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
            loadInitialGreeting(_profile.value.selectedLanguage)
        }
    }

    fun loadInitialGreeting(language: String = _profile.value.selectedLanguage) {
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
                    greeting = geminiService.generateInitialGreeting(language)
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
            voiceManager.startListening { resultText ->
                if (resultText.isNotBlank()) {
                    submitVoiceResponse(resultText)
                }
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
        _isPlayingAudio.value = false
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

    private fun speakPrompt(prompt: String) {
        _isPlayingAudio.value = true
        voiceManager.speak(prompt) {
            _isPlayingAudio.value = false
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
