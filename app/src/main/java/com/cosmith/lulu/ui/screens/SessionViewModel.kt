package com.cosmith.lulu.ui.screens

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmith.lulu.AppContainer
import com.cosmith.lulu.core.content.Item
import com.cosmith.lulu.core.content.Sound
import com.cosmith.lulu.core.content.Syllable
import com.cosmith.lulu.core.content.Word
import com.cosmith.lulu.core.fsrs.Rating
import com.cosmith.lulu.core.session.Ratings
import com.cosmith.lulu.core.session.SessionPlanner
import com.cosmith.lulu.core.session.SessionRunner
import com.cosmith.lulu.core.session.Step
import com.cosmith.lulu.core.store.CardKey
import com.cosmith.lulu.core.store.Judge
import com.cosmith.lulu.core.store.LearningSettings
import com.cosmith.lulu.core.store.Profile
import com.cosmith.lulu.core.store.Progress
import com.cosmith.lulu.ui.components.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Runs one session. Every rating is saved right away, so leaving in the middle
 * loses nothing.
 */
class SessionViewModel(
    private val c: AppContainer,
    private val profile: Profile,
    /** Fixed steps instead of a planned session, for screenshots and tests. */
    planned: List<Step>? = null,
) : ViewModel() {
    private var progress: Progress = c.store.progress(profile.id)
    private val planner = SessionPlanner(profile)
    private val fixed = planned?.let { SessionRunner(it) }
    private var fixedStarted = false

    var step by mutableStateOf<Step?>(null)
        private set

    /** Counts screens, so a card shown twice still pops in again. */
    var stepIndex by mutableIntStateOf(0)
        private set
    var cardsDone by mutableIntStateOf(0)
        private set
    var finished by mutableStateOf(false)
        private set

    private val durationMs = profile.settings.sessionMinutes.coerceIn(LearningSettings.MINUTES) * 60_000L

    /** Time counts only while the app is on screen. */
    private var activeMs = 0L
    private var resumedAt: Long? = SystemClock.elapsedRealtime()

    var mood by mutableStateOf(Mood.Listening)
        private set
    var jumpKey by mutableIntStateOf(0)
        private set
    var shakeKey by mutableIntStateOf(0)
        private set
    var confettiKey by mutableIntStateOf(0)
        private set

    /** Bumped each time a reference sound starts, so the matching letter can move with it. */
    var soundKey by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set

    /** True while feedback plays: taps are ignored. */
    var busy by mutableStateOf(false)
        private set

    /** "Écouter et choisir": the pebble tapped by mistake, and the right one once found or shown. */
    var wrongChoices by mutableStateOf(emptySet<String>())
        private set
    var rightChoice by mutableStateOf<String?>(null)
        private set
    var shakingChoice by mutableStateOf<String?>(null)
        private set

    /** Meeting a syllable: the sound being said on its own, then true once the sounds have merged. */
    var blendPart by mutableStateOf<Int?>(null)
        private set
    var blended by mutableStateOf(true)
        private set

    /** A word's picture, shown only once the child has read the word. */
    var picture by mutableStateOf<String?>(null)
        private set

    private var streak = 0

    /** "Voir et dire": the child asked to hear the sound before answering. */
    private var heardSoundFirst = false
    private var soundJob: Job? = null

    /** Intro playback and feedback of the current step; cancelled when the step changes. */
    private var stepJob: Job? = null

    private fun launchStep(block: suspend CoroutineScope.() -> Unit) {
        stepJob = viewModelScope.launch(block = block)
    }

    init {
        enter(pick(null))
    }

    fun onPause() {
        resumedAt?.let { activeMs += SystemClock.elapsedRealtime() - it }
        resumedAt = null
    }

    fun onResume() {
        if (resumedAt == null) resumedAt = SystemClock.elapsedRealtime()
    }

    private fun elapsedMs() = activeMs + (resumedAt?.let { SystemClock.elapsedRealtime() - it } ?: 0L)

    private fun pick(rating: Rating?): Step? {
        val runner = fixed ?: return planner.next(progress, c.now(), Duration.ofMillis(durationMs - elapsedMs()))
        if (fixedStarted) runner.next(rating) else fixedStarted = true
        return runner.current
    }

    private fun enter(s: Step?) {
        step = s
        stepIndex++
        wrongChoices = emptySet()
        rightChoice = null
        shakingChoice = null
        heardSoundFirst = false
        busy = false
        picture = null
        blendPart = null
        blended = true
        when (s) {
            null -> finish()
            is Step.Meet -> {
                mood = Mood.Happy
                val item = s.item
                progress = if (item is Sound && s.lowercase) progress.meetLowercase(item, c.now()) else progress.meet(item, c.now())
                save()
                blended = item !is Syllable
                launchStep {
                    delay(450)
                    if (item is Syllable) {
                        // Each sound on its own, then together: « L… A… LA ».
                        for ((i, part) in item.sounds.withIndex()) {
                            blendPart = i
                            playSound(part)
                            delay(250)
                        }
                        blendPart = null
                        blended = true
                        delay(300)
                    }
                    playSound(item)
                    delay(500)
                    playSound(item)
                }
            }
            is Step.SeeAndSay -> {
                // A word is met by reading it: its first card is created now.
                if (progress.card(s.key) == null) {
                    progress = progress.meet(s.item, c.now())
                    save()
                }
                mood = Mood.Listening
            }
            is Step.MatchCase -> mood = Mood.Listening
            is Step.ListenAndChoose -> {
                mood = Mood.Listening
                launchStep {
                    delay(450)
                    playSound(s.item)
                }
            }
        }
    }

    /** The speaker button, or a tap on the big letter. */
    fun onSpeaker() {
        val s = step ?: return
        if (busy) return
        if (s is Step.SeeAndSay) heardSoundFirst = true
        viewModelScope.launch { playSound(s.item) }
    }

    /**
     * The parent's ✓ / ✗ on "voir et dire", until the recognizer takes over. One answer
     * per card: on a miss, Lulu says the right sound while the letter moves with it,
     * then the next card comes.
     */
    fun onVerdict(correct: Boolean) {
        val s = step as? Step.SeeAndSay ?: return
        if (busy) return
        busy = true
        val rating = Ratings.seeAndSay(correct, heardSoundFirst)
        val word = s.item as? Word
        launchStep {
            if (correct) {
                successFeedback()
                if (word != null) {
                    // Read it: now the picture, and Lulu says the word.
                    picture = word.picture
                    playSound(word)
                    delay(700)
                } else {
                    delay(950)
                }
            } else {
                missFeedback()
                delay(400)
                picture = word?.picture
                playSound(s.item)
                delay(word?.let { 900 } ?: 500)
            }
            finishCard(s.key, rating, Judge.Parent)
        }
    }

    /**
     * One tap per card. On a miss the tapped pebble shakes and fades, the right one
     * lights up while Lulu plays the sound again, then the next card comes.
     */
    fun onChoice(choice: Item) {
        val s = step as? Step.Card ?: return
        if (s is Step.SeeAndSay) return
        if (busy) return
        busy = true
        val correct = choice.id == s.item.id
        val word = s.item as? Word
        launchStep {
            if (correct) {
                rightChoice = choice.id
                successFeedback()
                if (word != null) {
                    picture = word.picture
                    playSound(word)
                    delay(600)
                } else {
                    delay(950)
                }
            } else {
                wrongChoices = setOf(choice.id)
                shakingChoice = choice.id
                missFeedback()
                delay(350)
                rightChoice = s.item.id
                picture = word?.picture
                playSound(s.item)
                delay(700)
            }
            finishCard(s.key, Ratings.choice(correct), Judge.Tap)
        }
    }

    fun onMeetDone() {
        if (step !is Step.Meet || busy) return
        c.tones.tap()
        next(null)
    }

    private fun finishCard(key: CardKey, rating: Rating, judge: Judge) {
        progress = progress.record(key, rating, c.now(), judge, c.scheduler)
        save()
        next(rating)
    }

    /** When time is up, the card that just ended was the last one (a sound just met still gets its card). */
    private fun next(rating: Rating?) {
        stepJob?.cancel()
        soundJob?.cancel()
        c.sounds.stop()
        val wasMeet = step is Step.Meet
        if (!wasMeet) cardsDone++
        enter(if (elapsedMs() >= durationMs && !wasMeet) null else pick(rating))
    }

    private fun successFeedback() {
        c.tones.success(streak)
        streak++
        confettiKey++
        jumpKey++
        mood = Mood.Happy
    }

    private fun missFeedback() {
        streak = 0
        c.tones.soft()
        shakeKey++
        mood = Mood.Encourage
    }

    private suspend fun playSound(sound: Item) {
        soundJob?.cancel()
        val job = viewModelScope.launch {
            playing = true
            soundKey++
            try {
                c.sounds.play(sound)
            } finally {
                playing = false
            }
        }
        soundJob = job
        job.join()
    }

    private fun finish() {
        if (finished) return
        finished = true
        mood = Mood.Happy
        if (cardsDone > 0) {
            c.tones.fanfare()
            confettiKey++
            jumpKey++
        }
    }

    private fun save() = c.saveProgress(profile.id, progress)

    override fun onCleared() {
        c.sounds.stop()
    }
}
