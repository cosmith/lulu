package com.cosmith.lulu

import android.os.Looper
import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.fsrs.Rating
import com.cosmith.lulu.core.session.Step
import com.cosmith.lulu.core.store.CardKey
import com.cosmith.lulu.core.store.CardKind
import com.cosmith.lulu.core.store.Judge
import com.cosmith.lulu.core.store.LearningSettings
import com.cosmith.lulu.core.store.Profile
import com.cosmith.lulu.core.store.Progress
import com.cosmith.lulu.ui.screens.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionViewModelTest {
    private val c get() = RuntimeEnvironment.getApplication().container

    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))

    @Test
    fun oneAnswerPerCardAndFailedCardsComeBack() {
        val profile = Profile("vm-test", "", 0, Instant.now())
        val ou = Curriculum["ou"]
        val a = Curriculum["a"]
        val listenKey = CardKey("ou", CardKind.ListenAndChoose)
        val seeKey = CardKey("ou", CardKind.SeeAndSay)
        val start = Instant.now().minus(Duration.ofDays(2))
        c.store.saveProgress(
            profile.id,
            Progress().introduce(ou, start).record(listenKey, Rating.Good, start, Judge.Tap, c.scheduler),
        )

        val vm = SessionViewModel(c, profile, planned = listOf(Step.ListenAndChoose(ou, listOf(a, ou)), Step.SeeAndSay(ou)))
        settle()

        vm.onChoice(a) // wrong: "À revoir", the right answer is shown, and the next card comes
        assertTrue(vm.busy)
        vm.onChoice(ou) // ignored while the feedback plays
        settle()
        assertTrue(vm.step is Step.SeeAndSay)
        assertFalse(vm.busy)

        vm.onVerdict(true) // the parent says the sound was right
        settle()
        assertTrue(vm.step is Step.ListenAndChoose)

        vm.onChoice(ou)
        settle()
        assertTrue(vm.finished)

        Thread.sleep(300) // saves run on a background thread
        val saved = c.store.progress(profile.id)
        assertEquals(
            listOf(Rating.Good, Rating.Again, Rating.Good, Rating.Good),
            saved.reviews.map { it.rating },
        )
        assertEquals(listOf(listenKey, listenKey, seeKey, listenKey), saved.reviews.map { it.key })
    }

    @Test
    fun meetIntroducesTheSound() {
        val profile = Profile("vm-meet", "", 0, Instant.now())
        val ch = Curriculum["ch"]
        val vm = SessionViewModel(c, profile, planned = listOf(Step.Meet(ch), Step.ListenAndChoose(ch, listOf(ch, Curriculum["a"]))))
        settle()
        vm.onMeetDone()
        settle()
        vm.onChoice(ch)
        settle()
        assertTrue(vm.finished)
        Thread.sleep(300)
        val saved = c.store.progress(profile.id)
        assertEquals(listOf("ch"), saved.introduced)
        // First success by ear opens "voir et dire" for this sound.
        assertTrue(saved.card(CardKey("ch", CardKind.SeeAndSay)) != null)
    }

    @Test
    fun sessionStopsAfterTheCardInProgressWhenTimeIsUp() {
        val profile = Profile("vm-time", "", 0, Instant.now(), LearningSettings(sessionMinutes = 2))
        val ou = Curriculum["ou"]
        val start = Instant.now().minus(Duration.ofDays(2))
        c.store.saveProgress(
            profile.id,
            Progress().introduce(ou, start)
                .record(CardKey("ou", CardKind.ListenAndChoose), Rating.Good, start, Judge.Tap, c.scheduler),
        )
        val many = List(30) { Step.SeeAndSay(ou) }
        val vm = SessionViewModel(c, profile, planned = many)
        settle()
        assertEquals(3, vm.dots) // short sessions still show three dots
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))
        assertFalse(vm.finished) // the child finishes the card on screen
        vm.onVerdict(true)
        settle()
        assertTrue(vm.finished)
        assertEquals(1, vm.cardsDone)
        assertEquals(vm.dots, vm.dotsDone)
    }

    @Test
    fun timeDoesNotRunWhileTheAppIsInTheBackground() {
        val profile = Profile("vm-pause", "", 0, Instant.now(), LearningSettings(sessionMinutes = 2))
        val ou = Curriculum["ou"]
        val start = Instant.now().minus(Duration.ofDays(2))
        c.store.saveProgress(
            profile.id,
            Progress().introduce(ou, start)
                .record(CardKey("ou", CardKind.ListenAndChoose), Rating.Good, start, Judge.Tap, c.scheduler),
        )
        val vm = SessionViewModel(c, profile, planned = List(30) { Step.SeeAndSay(ou) })
        vm.onPause()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10))
        vm.onResume()
        vm.onVerdict(true)
        settle()
        assertFalse(vm.finished)
        assertTrue(vm.step is Step.SeeAndSay)
    }
}
