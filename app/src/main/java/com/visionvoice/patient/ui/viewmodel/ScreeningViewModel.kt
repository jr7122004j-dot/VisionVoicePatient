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

    val isListening: StateFlow<Boolean> = voiceManager.isListening
    val isSpeaking: StateFlow<Boolean> = voiceManager.isSpeaking
    val voiceTranscript: StateFlow<String> = voiceManager.liveTranscript
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
        val initialQ = tenTurnQuestions[0]
        val initialText = if (language.equals("English", ignoreCase = true)) {
            initialQ.promptEnglish
        } else {
            initialQ.promptHindi
        }

        _profile.update { current ->
            current.copy(
                selectedLanguage = language,
                chatHistory = listOf(
                    ChatMessage(
                        sender = MessageSender.AI_ASSISTANT,
                        text = initialText
                    )
                )
            )
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

    fun submitVoiceResponse(text: String) {
        if (_isAiThinking.value) return

        val turn = _currentTurnIndex.value

        // 1. Record Patient's Voice/Response
        _profile.update { current ->
            current.copy(
                chatHistory = current.chatHistory + ChatMessage(
                    sender = MessageSender.PATIENT,
                    text = text
                )
            )
        }

        // 2. Query Gemini Adaptive Anamnesis Engine
        _isAiThinking.value = true
        viewModelScope.launch {
            try {
                val aiReply = geminiService.sendPatientMessage(turn, text)

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
            } catch (e: Exception) {
                // Safe recovery
            } finally {
                _isAiThinking.value = false
            }
        }
    }

    fun selectHintResponse(hint: String) {
        submitVoiceResponse(hint)
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
                ?: getCurrentTurnPrompt()
            speakPrompt(lastAssistantMsg)
        }
    }

    fun seekAudio(progress: Float) {
        _audioProgress.value = progress.coerceIn(0f, 1f)
    }

    fun replayAudio() {
        val lastAssistantMsg = _profile.value.chatHistory
            .lastOrNull { it.sender == MessageSender.AI_ASSISTANT }?.text
            ?: getCurrentTurnPrompt()
        speakPrompt(lastAssistantMsg)
    }

    fun getCurrentTurnPrompt(): String {
        val q = tenTurnQuestions[_currentTurnIndex.value]
        return if (_profile.value.selectedLanguage.equals("English", ignoreCase = true)) {
            q.promptEnglish
        } else {
            q.promptHindi
        }
    }

    fun getCurrentTurnTitle(): String {
        return tenTurnQuestions[_currentTurnIndex.value].title
    }

    fun getCurrentTurnHints(): List<String> {
        return tenTurnQuestions[_currentTurnIndex.value].hints
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
