package com.cosmith.lulu.ui.screens

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmith.lulu.AppContainer
import com.cosmith.lulu.core.content.Sound
import com.cosmith.lulu.core.fsrs.Rating
import com.cosmith.lulu.core.session.ListenAndChooseFlow
import com.cosmith.lulu.core.session.Outcome
import com.cosmith.lulu.core.session.SeeAndSayFlow
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

    /** One progress dot per minute of session. */
    val dots = profile.settings.sessionMinutes.coerceIn(3, 10)
    var dotsDone by mutableIntStateOf(0)
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

    /** "Écouter et choisir": choices already tried, and the right one once found or shown. */
    var wrongChoices by mutableStateOf(emptySet<String>())
        private set
    var rightChoice by mutableStateOf<String?>(null)
        private set
    /** Which pebble shakes: the last wrong choice. */
    var shakingChoice by mutableStateOf<String?>(null)
        private set

    /** "Voir et dire": second attempt in progress. */
    var retrying by mutableStateOf(false)
        private set

    private var streak = 0
    private var seeAndSay = SeeAndSayFlow()
    private var listen = ListenAndChooseFlow()
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
        val runner = fixed ?: return planner.next(progress, c.now())
        if (fixedStarted) runner.next(rating) else fixedStarted = true
        return runner.current
    }

    private fun enter(s: Step?) {
        step = s
        stepIndex++
        dotsDone = minOf(dots, (elapsedMs() * dots / durationMs).toInt())
        wrongChoices = emptySet()
        rightChoice = null
        shakingChoice = null
        retrying = false
        busy = false
        when (s) {
            null -> finish()
            is Step.Meet -> {
                mood = Mood.Happy
                progress = progress.introduce(s.sound, c.now())
                save()
                launchStep {
                    delay(450)
                    playSound(s.sound)
                    delay(500)
                    playSound(s.sound)
                }
            }
            is Step.SeeAndSay -> {
                seeAndSay = SeeAndSayFlow()
                mood = Mood.Listening
            }
            is Step.ListenAndChoose -> {
                listen = ListenAndChooseFlow()
                mood = Mood.Listening
                launchStep {
                    delay(450)
                    playSound(s.sound)
                }
            }
        }
    }

    /** The speaker button, or a tap on the big letter. */
    fun onSpeaker() {
        val s = step ?: return
        if (busy) return
        if (s is Step.SeeAndSay) seeAndSay.onSoundPlayed()
        viewModelScope.launch { playSound(s.sound) }
    }

    /** The parent's ✓ / ✗ on "voir et dire", until the recognizer takes over. */
    fun onVerdict(correct: Boolean) {
        val s = step as? Step.SeeAndSay ?: return
        if (busy) return
        busy = true
        when (val outcome = seeAndSay.onVerdict(correct)) {
            Outcome.TryAgain -> launchStep {
                missFeedback()
                delay(450)
                playSound(s.sound)
                retrying = true
                mood = Mood.Listening
                busy = false
            }
            is Outcome.Done -> launchStep {
                if (outcome.rating == Rating.Again) {
                    missFeedback()
                    delay(400)
                    playSound(s.sound)
                    delay(400)
                } else {
                    successFeedback()
                    delay(950)
                }
                finishCard(s.key, outcome.rating, Judge.Parent)
            }
        }
    }

    fun onChoice(choice: Sound) {
        val s = step as? Step.ListenAndChoose ?: return
        if (busy || choice.id in wrongChoices) return
        busy = true
        val correct = choice.id == s.sound.id
        when (val outcome = listen.onChoice(correct)) {
            Outcome.TryAgain -> launchStep {
                wrongChoices = wrongChoices + choice.id
                shakingChoice = choice.id
                missFeedback()
                delay(450)
                playSound(s.sound)
                mood = Mood.Listening
                busy = false
            }
            is Outcome.Done -> launchStep {
                when {
                    outcome.rating == Rating.Good -> {
                        rightChoice = choice.id
                        successFeedback()
                        delay(950)
                    }
                    correct -> {
                        // Found on the second try: a smaller celebration, but still one.
                        rightChoice = choice.id
                        c.tones.success(0)
                        jumpKey++
                        mood = Mood.Happy
                        delay(900)
                    }
                    else -> {
                        wrongChoices = wrongChoices + choice.id
                        shakingChoice = choice.id
                        missFeedback()
                        delay(350)
                        rightChoice = s.sound.id
                        playSound(s.sound)
                        delay(700)
                    }
                }
                finishCard(s.key, outcome.rating, Judge.Tap)
            }
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

    private suspend fun playSound(sound: Sound) {
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
        dotsDone = dots
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
