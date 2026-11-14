package com.visionvoice.patient.voice

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Native Voice Engine for VisionVoice Patient App.
 * Orchestrates:
 * 1. Native MediaRecorder for capturing patient speech into a local audio file
 * 2. MediaPlayer for instant patient audio playback ("Play My Audio")
 * 3. SpeechRecognizer for live on-device speech-to-text preview
 * 4. TextToSpeech for female clinical assistant voice guidance
 */
class VoiceManager(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    // Native audio recorder & player
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _hasRecordedAudio = MutableStateFlow(false)
    val hasRecordedAudio: StateFlow<Boolean> = _hasRecordedAudio.asStateFlow()

    private val _isPatientAudioPlaying = MutableStateFlow(false)
    val isPatientAudioPlaying: StateFlow<Boolean> = _isPatientAudioPlaying.asStateFlow()

    private val _recordedAudioDurationMs = MutableStateFlow(0)
    val recordedAudioDurationMs: StateFlow<Int> = _recordedAudioDurationMs.asStateFlow()

    val recordedAudioFile: File?
        get() = currentRecordingFile

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

    private val amplitudePollRunnable = object : Runnable {
        override fun run() {
            if (_isListening.value && mediaRecorder != null) {
                try {
                    val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                    if (maxAmp > 0) {
                        val db = (maxAmp.toFloat() / 32767f) * 30f
                        _rmsDb.value = db
                    }
                } catch (e: Exception) {
                    // Ignore amplitude polling errors
                }
                mainHandler.postDelayed(this, 90)
            }
        }
    }

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
            Log.w("VoiceManager", "Speech recognition service is not available on this device")
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
            textToSpeech?.setLanguage(Locale.ENGLISH)
        }

        try {
            val femaleVoice = textToSpeech?.voices?.firstOrNull { voice ->
                voice.locale.language.equals(locale.language, ignoreCase = true) &&
                    (voice.name.contains("female", ignoreCase = true) ||
                     voice.name.contains("-f-", ignoreCase = true) ||
                     voice.features?.contains("female") == true)
            }
            if (femaleVoice != null) {
                textToSpeech?.voice = femaleVoice
            }
        } catch (e: Exception) {
            // Ignore and use default voice
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
        stopAudioPlayback()
        onSpeechResultCallback = onResult
        _voiceError.value = null
        _liveTranscript.value = ""
        _hasRecordedAudio.value = false

        mainHandler.post {
            // 1. Start native MediaRecorder audio capture
            try {
                val audioFile = File(context.cacheDir, "patient_recording_${System.currentTimeMillis()}.m4a")
                currentRecordingFile = audioFile

                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    setOutputFile(audioFile.absolutePath)
                    prepare()
                    start()
                }
                _isListening.value = true
                mainHandler.post(amplitudePollRunnable)
            } catch (e: Exception) {
                Log.w("VoiceManager", "MediaRecorder initialization notice: ${e.message}")
            }

            // 2. Also start SpeechRecognizer if available
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
                Log.w("VoiceManager", "SpeechRecognizer start notice: ${e.message}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            mainHandler.removeCallbacks(amplitudePollRunnable)
            _isListening.value = false
            _rmsDb.value = 0f

            // Stop MediaRecorder and finalize file
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                Log.w("VoiceManager", "MediaRecorder stop note: ${e.message}")
            } finally {
                try {
                    mediaRecorder?.release()
                } catch (e: Exception) {}
                mediaRecorder = null
            }

            val file = currentRecordingFile
            Log.i("VoiceManager", "stopListening: file=${file?.absolutePath}, exists=${file?.exists()}, length=${file?.length()}")
            if (file != null && file.exists() && file.length() > 50) {
                _hasRecordedAudio.value = true
                try {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(file.absolutePath)
                    val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    _recordedAudioDurationMs.value = durStr?.toIntOrNull() ?: 0
                    mmr.release()
                } catch (e: Exception) {
                    _recordedAudioDurationMs.value = 0
                }
            }

            // Stop SpeechRecognizer safely
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                // Ignore safe teardown
            }
        }
    }

    /**
     * Plays back the patient's recorded audio via MediaPlayer.
     */
    fun playRecordedAudio(onComplete: (() -> Unit)? = null) {
        val file = currentRecordingFile
        if (file == null || !file.exists() || file.length() == 0L) {
            return
        }

        stopAudioPlayback()
        stopSpeaking()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isPatientAudioPlaying.value = false
                    onComplete?.invoke()
                }
                start()
            }
            _isPatientAudioPlaying.value = true
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error playing patient audio: ${e.message}", e)
            _isPatientAudioPlaying.value = false
        }
    }

    /**
     * Stops playback of the patient's recorded audio.
     */
    fun stopAudioPlayback() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (e: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
        _isPatientAudioPlaying.value = false
    }

    /**
     * Discards the currently recorded audio and resets state.
     */
    fun discardRecordedAudio() {
        stopAudioPlayback()
        try {
            currentRecordingFile?.delete()
        } catch (e: Exception) {}
        currentRecordingFile = null
        _hasRecordedAudio.value = false
        _recordedAudioDurationMs.value = 0
        _liveTranscript.value = ""
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        stopListening()
        stopAudioPlayback()
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
        if (_rmsDb.value == 0f) {
            _rmsDb.value = rmsdB
        }
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        // Do not prematurely stop listening; user controls tap-to-stop
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording notice"
            SpeechRecognizer.ERROR_CLIENT -> "Client-side recognition notice"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network connection required for speech service"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match detected"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Recognition server notice"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
            else -> "Speech recognition notice ($error)"
        }

        Log.w("VoiceManager", "SpeechRecognizer notice: $error ($message)")

        // If mediaRecorder is active, keep recording session alive for patient!
        if (mediaRecorder != null) {
            return
        }

        _isListening.value = false
        if (!_hasRecordedAudio.value &&
            error != SpeechRecognizer.ERROR_CLIENT &&
            error != SpeechRecognizer.ERROR_NO_MATCH &&
            error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            _voiceError.value = message
        }
    }

    override fun onResults(results: Bundle?) {
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
        stopAudioPlayback()
        discardRecordedAudio()
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
