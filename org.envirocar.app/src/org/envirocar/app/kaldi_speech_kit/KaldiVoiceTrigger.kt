package org.envirocar.app.kaldi_speech_kit

import com.justai.aimybox.components.L
import com.justai.aimybox.extensions.cancelChildrenAndJoin
import com.justai.aimybox.voicetrigger.VoiceTrigger
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.concurrent.atomic.AtomicBoolean

class KaldiVoiceTrigger(
    assets: KaldiAssets,
    _phrases: Collection<String>
) : VoiceTrigger, CoroutineScope {

    var phrases = _phrases
        private set

    private val job = Job()
    override val coroutineContext = Dispatchers.IO + job

    private val initialization = CompletableDeferred<Model>()

    private var recognizer: SpeechService? = null
    private var isListening = AtomicBoolean(false)

    init {
        launch {
            initialization.complete(Model(assets.directory))
        }
    }

    override suspend fun startDetection(
        onTriggered: (phrase: String?) -> Unit,
        onException: (e: Throwable) -> Unit
    ) {
        if (isListening.compareAndSet(false, true)) {
            val phrasesString = phrases
                .joinToString(separator = " ", prefix = "[\"", postfix = "\"]")
            val recognizer =
                Recognizer(initialization.await(), 16000f, phrasesString)

            // directly calling SpeechService method will block the current thread, catching any exceptions within it.
            // a good doc on this: https://hossainkhan.medium.com/kotlin-coroutines-error-handling-strategy-runcatching-and-result-class-14e1467ced6
            kotlin.runCatching{
                    this@KaldiVoiceTrigger.recognizer = SpeechService(recognizer, 16000f).apply {
                        startListening(RecognizerListener(phrases, onTriggered, onException))
                    }
                }
            L.i("Kaldi voice trigger was started")
        }
    }

    override suspend fun stopDetection() {
        recognizer?.run {
            cancel()
            shutdown()
            job.cancelChildrenAndJoin()
            isListening.set(false)
            L.i("Kaldi voice trigger was stopped")
        }
    }

    override fun destroy() {
        recognizer?.run {
            cancel()
            shutdown()
            isListening.set(false)
            L.i("Kaldi voice trigger was stopped")
        }
        super.destroy()
    }

    fun updateTriggers(triggers: Collection<String>) {
        phrases = triggers
    }

    internal class RecognizerListener(
        private val phrases: Collection<String>,
        private val onTriggered: (phrase: String?) -> Unit,
        private val onException: (e: Throwable) -> Unit
    ) : RecognitionListener {
        private var triggered = false

        override fun onResult(result: String?) {}

        override fun onFinalResult(result: String?) {
            if (!triggered) {
                result?.parsePartial()?.findAnyOf(phrases)?.let {
                    triggered = true
                    L.i("Detected phrase ${it.second}")
                    onTriggered(it.second)
                }
            }
        }

        override fun onPartialResult(result: String?) {
            if (!triggered) {
                result?.parsePartial()?.findAnyOf(phrases)?.let {
                    triggered = true
                    L.i("Detected phrase ${it.second}")
                    onTriggered(it.second)
                }
            }
        }

        override fun onTimeout() {}

        override fun onError(e: Exception?) {
            L.e(e)
            e?.let { onException(e) }
        }

    }
}