package com.cosmith.lulu.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.cosmith.lulu.core.content.Sound
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/** Where a reference sound comes from. */
enum class SoundSource {
    /** Recorded by the parent in the parent area. */
    Recording,

    /** Shipped with the app (none yet: who records them is an open question). */
    Bundled,

    /** French text-to-speech. Fine for vowels, approximate for consonants. */
    Synthesis,
}

/**
 * Plays the reference sound of each grapheme: the parent's recording when there is
 * one, else a bundled file, else French text-to-speech.
 */
class SoundBank(private val context: Context) {
    private val recordings = File(context.filesDir, "recordings").apply { mkdirs() }
    private val bundled: Set<String> = runCatching { context.assets.list("sounds")?.toSet() }.getOrNull().orEmpty()

    private var player: MediaPlayer? = null
    private val ttsReady = CompletableDeferred<TextToSpeech?>()
    private val tts: TextToSpeech = TextToSpeech(context) { status -> onTtsInit(status) }

    private fun onTtsInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "text-to-speech unavailable ($status)")
            ttsReady.complete(null)
            return
        }
        val result = tts.setLanguage(Locale.FRANCE)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "no French voice for text-to-speech ($result)")
            ttsReady.complete(null)
            return
        }
        tts.setSpeechRate(0.8f)
        ttsReady.complete(tts)
    }

    fun recordingFile(sound: Sound) = File(recordings, "${sound.id}.m4a")

    fun source(sound: Sound): SoundSource = when {
        recordingFile(sound).let { it.exists() && it.length() > 0 } -> SoundSource.Recording
        "${sound.id}.ogg" in bundled -> SoundSource.Bundled
        else -> SoundSource.Synthesis
    }

    /** Plays the sound and returns when it is over (or after a few seconds at most). */
    suspend fun play(sound: Sound) {
        stop()
        withTimeoutOrNull(MAX_PLAY_MS) {
            when (source(sound)) {
                SoundSource.Recording -> playMedia { setDataSource(recordingFile(sound).path) }
                SoundSource.Bundled -> playMedia {
                    context.assets.openFd("sounds/${sound.id}.ogg").use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                }
                SoundSource.Synthesis -> speak(sound.ttsText)
            }
        }
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        tts.stop()
    }

    fun deleteRecording(sound: Sound) {
        recordingFile(sound).delete()
    }

    fun release() {
        stop()
        tts.shutdown()
    }

    private suspend fun playMedia(setSource: MediaPlayer.() -> Unit) = suspendCancellableCoroutine { cont ->
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setSource()
            mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
            mp.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            Log.w(TAG, "could not play recording", e)
            if (cont.isActive) cont.resume(Unit)
        }
        cont.invokeOnCancellation { runCatching { mp.stop() } }
    }

    private suspend fun speak(text: String) {
        val engine = ttsReady.await() ?: return
        val id = "lulu-${System.nanoTime()}"
        suspendCancellableCoroutine { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            cont.invokeOnCancellation { engine.stop() }
        }
    }

    private companion object {
        const val TAG = "SoundBank"
        const val MAX_PLAY_MS = 4_000L
    }
}

/** Records a reference sound to an AAC file. Used by the parent only. */
class SoundRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var temp: File? = null

    fun start(file: File) {
        cancel()
        val tmp = File(file.parentFile, file.name + ".rec")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44_100)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioChannels(1)
        r.setOutputFile(tmp.path)
        r.prepare()
        r.start()
        recorder = r
        target = file
        temp = tmp
    }

    /** Stops and keeps the recording. Returns false when it failed (too short, no mic…). */
    fun stop(): Boolean {
        val r = recorder ?: return false
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        recorder = null
        val tmp = temp
        val file = target
        temp = null
        target = null
        if (!ok || tmp == null || file == null) {
            tmp?.delete()
            return false
        }
        return tmp.renameTo(file)
    }

    fun cancel() {
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        temp?.delete()
        temp = null
        target = null
    }
}
