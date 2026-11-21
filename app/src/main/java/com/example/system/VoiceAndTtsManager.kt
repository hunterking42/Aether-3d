package com.example.system

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
import android.util.Log
import com.example.ai.AssistantPersona
import com.example.ai.SpeechLocaleOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private const val TAG = "VoiceAndTtsManager"

class VoiceAndTtsManager(
    context: Context,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onPartialSpeech: (String) -> Unit,
    private val onFinalSpeech: (String) -> Unit,
    private val onSpeechError: (String) -> Unit,
    private val onSpeakingStateChanged: (Boolean) -> Unit,
    private val onSpeakingFinished: () -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speakingPulseJob: Job? = null
    private var speakingSafetyJob: Job? = null
    private var retriedWithFallbackLocale = false

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            onSpeakingStateChanged(true)
                            startSpeakingCadencePulse()
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            speakingSafetyJob?.cancel()
                            stopSpeakingCadencePulse()
                            onSpeakingStateChanged(false)
                            onSpeakingFinished()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            speakingSafetyJob?.cancel()
                            stopSpeakingCadencePulse()
                            onSpeakingStateChanged(false)
                        }
                    }
                })
            } else {
                Log.w(TAG, "TTS initialization failed with status=$status")
            }
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    fun startListening(localeOption: SpeechLocaleOption, isLocaleFallbackRetry: Boolean = false) {
        mainHandler.post {
            stopSpeaking()
            if (!isLocaleFallbackRetry) {
                retriedWithFallbackLocale = false
            }
            if (!isSpeechRecognitionAvailable()) {
                onSpeechError("Speech recognition service unavailable on this device. Use the command bar below.")
                return@post
            }

            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            onPartialSpeech("Listening...")
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                            onAmplitudeChanged(normalized)
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            onAmplitudeChanged(0.1f)
                        }

                        override fun onError(error: Int) {
                            onAmplitudeChanged(0f)
                            if ((error == 12 || error == 13 || error == SpeechRecognizer.ERROR_CLIENT) && !retriedWithFallbackLocale) {
                                retriedWithFallbackLocale = true
                                Log.w(TAG, "SpeechRecognizer error=$error for ${localeOption.languageTag}, retrying with en-US")
                                startListening(SpeechLocaleOption.EN_US, isLocaleFallbackRetry = true)
                                return
                            }
                            val msg = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                                SpeechRecognizer.ERROR_CLIENT -> "Voice recognizer unavailable. Type your command below."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                    "Network unavailable for speech recognition."
                                SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch any speech. Tap mic or type below."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy, please retry."
                                SpeechRecognizer.ERROR_SERVER -> "Speech service temporarily unreachable."
                                12, 13 -> "Selected voice language unavailable on this device."
                                else -> "Speech recognition error ($error)."
                            }
                            onSpeechError(msg)
                        }

                        override fun onResults(results: Bundle?) {
                            onAmplitudeChanged(0f)
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val best = matches?.firstOrNull()?.trim().orEmpty()
                            if (best.isNotBlank()) {
                                onFinalSpeech(best)
                            } else {
                                onSpeechError("No speech recognized. Try again.")
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            if (partial.isNotBlank()) {
                                onPartialSpeech(partial)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeOption.languageTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                onSpeechError(e.localizedMessage ?: "Could not start voice listener.")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            onAmplitudeChanged(0f)
        }
    }

    fun speak(
        text: String,
        persona: AssistantPersona,
        localeOption: SpeechLocaleOption
    ) {
        val clean = text.trim()
        if (clean.isBlank() || !ttsReady) {
            mainHandler.post {
                stopSpeakingCadencePulse()
                onSpeakingStateChanged(false)
                onSpeakingFinished()
            }
            return
        }
        mainHandler.post {
            try {
                val hasBanglaChars = clean.any { it in '\u0980'..'\u09FF' }
                val effectiveTag = if (hasBanglaChars) "bn-BD" else localeOption.languageTag
                val targetLocale = Locale.forLanguageTag(effectiveTag)

                val res = tts?.setLanguage(targetLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val altLocale = if (hasBanglaChars || effectiveTag.startsWith("bn")) {
                        Locale.forLanguageTag("bn-IN")
                    } else {
                        Locale.US
                    }
                    val altRes = tts?.setLanguage(altLocale)
                    if (altRes == TextToSpeech.LANG_MISSING_DATA || altRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.US)
                    }
                }

                // Pick highest-quality installed neural/HD voice matching locale & persona timbre
                selectBestNaturalVoice(targetLocale, persona)?.let { bestVoice ->
                    tts?.voice = bestVoice
                }

                tts?.setPitch(persona.ttsPitch)
                tts?.setSpeechRate(persona.ttsRate)

                // Split long replies into sentences with subtle cadence pauses for natural delivery
                val sentences = clean.split(Regex("(?<=[.!?।])\\s+"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .ifEmpty { listOf(clean) }

                var finalResult = TextToSpeech.SUCCESS
                sentences.forEachIndexed { index, sentence ->
                    val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    val utteranceId = if (index == sentences.lastIndex) {
                        UUID.randomUUID().toString()
                    } else {
                        "part_$index"
                    }
                    val result = tts?.speak(sentence, queueMode, null, utteranceId) ?: TextToSpeech.ERROR
                    if (result != TextToSpeech.SUCCESS) {
                        finalResult = result
                    } else if (index < sentences.lastIndex) {
                        tts?.playSilentUtterance(110L, TextToSpeech.QUEUE_ADD, "pause_$index")
                    }
                }

                if (finalResult != TextToSpeech.SUCCESS) {
                    stopSpeakingCadencePulse()
                    onSpeakingStateChanged(false)
                    onSpeakingFinished()
                } else {
                    speakingSafetyJob?.cancel()
                    val estimatedMs = (clean.length * 90L).coerceIn(5_000L, 45_000L)
                    speakingSafetyJob = scope.launch {
                        delay(estimatedMs)
                        stopSpeakingCadencePulse()
                        onSpeakingStateChanged(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak error", e)
                stopSpeakingCadencePulse()
                onSpeakingStateChanged(false)
                onSpeakingFinished()
            }
        }
    }

    private fun selectBestNaturalVoice(
        desiredLocale: Locale,
        persona: AssistantPersona
    ): android.speech.tts.Voice? {
        val voices = runCatching { tts?.voices }.getOrNull().orEmpty()
        if (voices.isEmpty()) return null
        val targetLang = desiredLocale.language.lowercase(Locale.US)
        val candidates = voices.filter { v ->
            v.locale?.language?.lowercase(Locale.US) == targetLang &&
                !v.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }
        if (candidates.isEmpty()) return null

        val prefersFemale = persona == AssistantPersona.MAYRA
        return candidates.maxByOrNull { voice ->
            var score = 0
            val name = voice.name.lowercase(Locale.US)
            score += when {
                voice.quality >= android.speech.tts.Voice.QUALITY_VERY_HIGH -> 100
                voice.quality >= android.speech.tts.Voice.QUALITY_HIGH -> 75
                voice.quality >= android.speech.tts.Voice.QUALITY_NORMAL -> 40
                else -> 10
            }
            if (name.contains("neural") || name.contains("wavenet") || name.contains("-x-")) {
                score += 35
            }
            if (voice.locale?.country.equals(desiredLocale.country, ignoreCase = true)) {
                score += 20
            }
            // Subtle persona gender match using common Google/Android voice code hints
            val soundsFemale = name.contains("female") || name.contains("-sfg-") ||
                name.contains("-tpf-") || name.contains("-bnf-") || name.contains("-ban-")
            val soundsMale = name.contains("male") || name.contains("-iom-") ||
                name.contains("-tpd-") || name.contains("-bnm-")
            if (prefersFemale && soundsFemale) score += 18
            if (!prefersFemale && soundsMale) score += 18
            if (!voice.isNetworkConnectionRequired) score += 10
            score
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            speakingSafetyJob?.cancel()
            stopSpeakingCadencePulse()
            try {
                tts?.stop()
            } catch (_: Exception) {
            }
            onSpeakingStateChanged(false)
        }
    }

    private fun startSpeakingCadencePulse() {
        speakingPulseJob?.cancel()
        speakingPulseJob = scope.launch {
            var phase = 0f
            while (isActive) {
                phase += 0.45f
                val amp = (0.38f + 0.35f * kotlin.math.sin(phase) + 0.18f * kotlin.math.cos(phase * 2.3f))
                    .coerceIn(0.15f, 0.92f)
                onAmplitudeChanged(amp)
                delay(50L)
            }
        }
    }

    private fun stopSpeakingCadencePulse() {
        speakingPulseJob?.cancel()
        speakingPulseJob = null
        onAmplitudeChanged(0f)
    }

    fun release() {
        speakingSafetyJob?.cancel()
        stopSpeakingCadencePulse()
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                tts?.stop()
                tts?.shutdown()
                tts = null
            } catch (_: Exception) {
            }
        }
    }
}
