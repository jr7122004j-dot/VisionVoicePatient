package com.visionvoice.patient.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Native Voice Engine for VisionVoice Patient App.
 * Orchestrates on-device SpeechRecognizer (STT) and TextToSpeech (TTS)
 * with dynamic multilingual locale switching and synchronized state flows.
 */
class VoiceManager(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private var currentLocale: Locale = Locale.ENGLISH
    private var pendingSpeechText: String? = null
    private var onSpeechResultCallback: ((String) -> Unit)? = null
    private var onTtsDoneCallback: (() -> Unit)? = null

    init {
        mainHandler.post {
            initSpeechRecognizer()
            textToSpeech = TextToSpeech(context.applicationContext, this)
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceManager)
            }
        } else {
            _voiceError.value = "Speech recognition is not available on this device"
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            applyLocaleToTts(currentLocale)
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    mainHandler.post {
                        onTtsDoneCallback?.invoke()
                        onTtsDoneCallback = null
                    }
                }

                @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _voiceError.value = "Text-to-speech playback error"
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    _voiceError.value = "Text-to-speech error: code $errorCode"
                }
            })

            pendingSpeechText?.let { text ->
                pendingSpeechText = null
                speak(text)
            }
        } else {
            isTtsInitialized = false
            _voiceError.value = "Failed to initialize Text-to-Speech engine"
        }
    }

    fun setLanguage(languageName: String) {
        val locale = mapLanguageNameToLocale(languageName)
        currentLocale = locale
        if (isTtsInitialized) {
            applyLocaleToTts(locale)
        }
    }

    fun clearError() {
        _voiceError.value = null
    }

    private fun applyLocaleToTts(locale: Locale) {
        val result = textToSpeech?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to English if exact dialect is not available
            textToSpeech?.setLanguage(Locale.ENGLISH)
        }
    }

    private fun mapLanguageNameToLocale(name: String): Locale {
        return when (name.lowercase()) {
            "hindi", "हिन्दी" -> Locale.forLanguageTag("hi-IN")
            "bengali", "বাংলা" -> Locale.forLanguageTag("bn-IN")
            "telugu", "తెలుగు" -> Locale.forLanguageTag("te-IN")
            "tamil", "தமிழ்" -> Locale.forLanguageTag("ta-IN")
            "marathi", "मराठी" -> Locale.forLanguageTag("mr-IN")
            "rajasthani", "राजस्थानी", "marwari" -> {
                val rajLocale = Locale.forLanguageTag("raj-IN")
                val availability = textToSpeech?.isLanguageAvailable(rajLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (availability >= TextToSpeech.LANG_AVAILABLE) {
                    rajLocale
                } else {
                    Locale.forLanguageTag("hi-IN")
                }
            }
            "english" -> Locale.forLanguageTag("en-IN")
            else -> Locale.forLanguageTag("en-IN")
        }
    }

    fun startListening(onResult: (String) -> Unit) {
        stopSpeaking()
        onSpeechResultCallback = onResult
        _voiceError.value = null
        _liveTranscript.value = ""

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initSpeechRecognizer()
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLocale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                speechRecognizer?.startListening(intent)
                _isListening.value = true
            } catch (e: Exception) {
                _isListening.value = false
                _voiceError.value = "Error starting voice recognition: ${e.message}"
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                // Ignore safe teardown
            } finally {
                _isListening.value = false
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        stopListening()
        onTtsDoneCallback = onDone

        if (!isTtsInitialized) {
            pendingSpeechText = text
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _isSpeaking.value = false
    }

    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        _isListening.value = true
        _voiceError.value = null
    }

    override fun onBeginningOfSpeech() {
        _isListening.value = true
    }

    override fun onRmsChanged(rmsdB: Float) {
        _rmsDb.value = rmsdB
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _isListening.value = false
    }

    override fun onError(error: Int) {
        _isListening.value = false
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client-side recognition error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network connection required for speech service"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match detected. Please try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
            else -> "Speech recognition error ($error)"
        }
        // Don't flag NO_MATCH or SPEECH_TIMEOUT as a fatal error, just inform quietly
        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            _voiceError.value = message
        }
    }

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val bestResult = matches?.firstOrNull()?.trim() ?: ""
        if (bestResult.isNotEmpty()) {
            _liveTranscript.value = bestResult
            onSpeechResultCallback?.invoke(bestResult)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val partialText = matches?.firstOrNull()?.trim() ?: ""
        if (partialText.isNotEmpty()) {
            _liveTranscript.value = partialText
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                textToSpeech?.stop()
                textToSpeech?.shutdown()
                textToSpeech = null
            } catch (e: Exception) {
                // Ignore teardown exception
            }
        }
    }
}
