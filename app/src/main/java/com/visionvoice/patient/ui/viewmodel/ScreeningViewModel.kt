package com.visionvoice.patient.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.visionvoice.patient.ai.GeminiAnamnesisService
import com.visionvoice.patient.ai.TurnQuestion
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

    val voiceManager = VoiceManager(application)
    val geminiService = GeminiAnamnesisService()
    val repository = ScreeningRepository(application)

    private val _profile = MutableStateFlow(VisionProfile())
    val profile: StateFlow<VisionProfile> = _profile.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus

    val tenTurnQuestions: List<TurnQuestion> = geminiService.fallbackTenTurnQuestions

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

    private val _audioProgress = MutableStateFlow(0f)
    val audioProgress: StateFlow<Float> = _audioProgress.asStateFlow()

    init {
        initializeSession("Hindi")
    }

    private fun initializeSession(language: String) {
        geminiService.initializeSession(language)
        _profile.update { current ->
            current.copy(
                selectedLanguage = language,
                chatHistory = emptyList()
            )
        }
        loadInitialGreeting(language)
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
                    if (geminiService.offlineFallbackEnabled) {
                        break
                    }
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
            } else if (!geminiService.offlineFallbackEnabled) {
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
                    if (geminiService.offlineFallbackEnabled) {
                        break
                    }

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

                if (turn < tenTurnQuestions.lastIndex) {
                    _currentTurnIndex.value = turn + 1
                } else {
                    finalizeTriage()
                }
            } else if (geminiService.offlineFallbackEnabled) {
                // Offline fallback mode enabled via local.properties flag
                val q = tenTurnQuestions[turn.coerceAtMost(tenTurnQuestions.lastIndex)]
                val fallbackPrompt = if (_profile.value.selectedLanguage.equals("English", ignoreCase = true)) {
                    q.promptEnglish
                } else {
                    q.promptHindi
                }
                _profile.update { current ->
                    current.copy(
                        chatHistory = current.chatHistory + ChatMessage(
                            sender = MessageSender.AI_ASSISTANT,
                            text = fallbackPrompt
                        )
                    )
                }
                triggerAudioGuidance(fallbackPrompt)
                if (turn < tenTurnQuestions.lastIndex) {
                    _currentTurnIndex.value = turn + 1
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
            loadInitialGreeting()
        } else {
            val pendingText = _lastPendingPatientResponse.value
            if (!pendingText.isNullOrBlank()) {
                submitVoiceResponse(pendingText)
            }
        }
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

    fun toggleAudioPlayback() {
        if (voiceManager.isSpeaking.value) {
            voiceManager.stopSpeaking()
            _isPlayingAudio.value = false
        } else {
            val lastAssistantMsg = _profile.value.chatHistory
                .lastOrNull { it.sender == MessageSender.AI_ASSISTANT }?.text
            if (!lastAssistantMsg.isNullOrBlank()) {
                speakPrompt(lastAssistantMsg)
            }
        }
    }

    fun seekAudio(progress: Float) {
        _audioProgress.value = progress.coerceIn(0f, 1f)
    }

    fun replayAudio() {
        val lastAssistantMsg = _profile.value.chatHistory
            .lastOrNull { it.sender == MessageSender.AI_ASSISTANT }?.text
        if (!lastAssistantMsg.isNullOrBlank()) {
            speakPrompt(lastAssistantMsg)
        }
    }

    fun getCurrentTurnTitle(): String {
        return tenTurnQuestions[_currentTurnIndex.value].title
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
