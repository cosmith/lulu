package com.cosmith.lulu.core.session

import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.fsrs.CardState
import com.cosmith.lulu.core.fsrs.Rating
import com.cosmith.lulu.core.store.CardKey
import com.cosmith.lulu.core.store.CardKind
import com.cosmith.lulu.core.store.Judge
import com.cosmith.lulu.core.store.LearningSettings
import com.cosmith.lulu.core.store.Profile
import com.cosmith.lulu.core.store.Progress
import com.cosmith.lulu.core.store.luluScheduler
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTest {
    private val t0 = Instant.parse("2026-11-21T17:00:00Z")
    private val profile = Profile("p1", "", avatar = 0, createdAt = t0)
    private val scheduler = luluScheduler()

    @Test
    fun firstSessionMeetsTwoSoundsWithListenCards() {
        val steps = SessionPlanner(Random(1)).plan(profile, Progress(), t0)
        assertEquals(listOf("a", "a", "e_acute", "e_acute"), steps.map { it.sound.id })
        assertIs<Step.Meet>(steps[0])
        val listen = assertIs<Step.ListenAndChoose>(steps[1])
        assertEquals(2, listen.choices.size)
        assertTrue(listen.choices.any { it.id == "a" })
    }

    @Test
    fun seeAndSayOpensAfterFirstSuccessByEar() {
        val a = Curriculum["a"]
        var p = Progress().introduce(a, t0)
        val listen = CardKey("a", CardKind.ListenAndChoose)
        val see = CardKey("a", CardKind.SeeAndSay)
        p = p.record(listen, Rating.Again, t0, Judge.Tap, scheduler)
        assertNull(p.card(see))
        p = p.record(listen, Rating.Good, t0.plusSeconds(60), Judge.Tap, scheduler)
        assertNotNull(p.card(see))
        assertEquals(2, p.reviews.size)
        assertEquals(2, p.card(listen)!!.reviews)
    }

    @Test
    fun dueCardsComeFirstAndNoNewSoundWhenBusy() {
        var p = Progress()
        for (id in listOf("a", "e_acute", "i")) {
            p = p.introduce(Curriculum[id], t0)
            p = p.record(CardKey(id, CardKind.ListenAndChoose), Rating.Good, t0, Judge.Tap, scheduler)
        }
        // Three sounds in learning: no fourth one yet.
        val later = t0.plus(Duration.ofHours(2))
        val steps = SessionPlanner(Random(2)).plan(profile, p, later)
        assertTrue(steps.none { it is Step.Meet })
        assertEquals(6, steps.size) // 3 listen + 3 see-and-say cards, all due
        for (seed in 0 until 50) {
            val ids = SessionPlanner(Random(seed)).plan(profile, p, later).map { it.sound.id }
            for (i in 1 until ids.size) assertTrue(ids[i] != ids[i - 1], "same sound twice in a row: $ids")
        }
    }

    @Test
    fun newSoundArrivesOnceOthersAreSettled() {
        var p = Progress()
        var t = t0
        for (id in listOf("a", "e_acute")) p = p.introduce(Curriculum[id], t)
        // Graduate every card to Review with "Bien" until nothing is in learning.
        repeat(6) {
            for (card in p.cards.values.toList()) {
                if (!card.memory.due.isAfter(t)) p = p.record(card.key, Rating.Good, t, Judge.Tap, scheduler)
            }
            t = t.plus(Duration.ofMinutes(15))
        }
        assertTrue(p.cards.values.all { it.memory.state == CardState.Review }, p.cards.values.toString())
        val steps = SessionPlanner(Random(3)).plan(profile, p, t.plus(Duration.ofDays(30)))
        assertEquals(listOf("i"), steps.filterIsInstance<Step.Meet>().map { it.sound.id })
    }

    @Test
    fun sessionIsCappedAtSettingsSize() {
        var p = Progress()
        for (s in Curriculum.sounds.take(10)) {
            p = p.introduce(s, t0)
            p = p.record(CardKey(s.id, CardKind.ListenAndChoose), Rating.Good, t0, Judge.Tap, scheduler)
        }
        val small = profile.copy(settings = LearningSettings(cardsPerSession = 5))
        val steps = SessionPlanner(Random(4)).plan(small, p, t0.plus(Duration.ofDays(1)))
        assertEquals(5, steps.size)
    }

    @Test
    fun solidCardsGetFourChoicesAndLookalikes() {
        val ou = Curriculum["ou"]
        var p = Progress()
        for (s in Curriculum.sounds.take(7)) p = p.introduce(s, t0)
        val key = CardKey("ou", CardKind.ListenAndChoose)
        var t = t0
        repeat(5) {
            p = p.record(key, Rating.Good, t, Judge.Tap, scheduler)
            t = p.card(key)!!.memory.due
        }
        val card = p.card(key)!!
        assertTrue(card.memory.stability!! >= SessionPlanner.SOLID_DAYS)
        val choices = SessionPlanner(Random(5)).choicesFor(ou, card, p.introducedSounds(), Curriculum.sounds)
        assertEquals(4, choices.size)
        // "on" is a look-alike too but has not been met yet; o and u have.
        assertTrue(choices.map { it.id }.containsAll(listOf("ou", "o", "u")), choices.map { it.id }.toString())
    }

    @Test
    fun seeAndSayRatings() {
        assertEquals(Outcome.Done(Rating.Good), SeeAndSayFlow().onVerdict(true))
        SeeAndSayFlow().apply {
            assertEquals(Outcome.TryAgain, onVerdict(false))
            assertEquals(Outcome.Done(Rating.Hard), onVerdict(true))
        }
        SeeAndSayFlow().apply {
            assertEquals(Outcome.TryAgain, onVerdict(false))
            assertEquals(Outcome.Done(Rating.Again), onVerdict(false))
        }
        SeeAndSayFlow().apply {
            onSoundPlayed()
            assertEquals(Outcome.Done(Rating.Hard), onVerdict(true))
        }
    }

    @Test
    fun listenAndChooseRatings() {
        assertEquals(Outcome.Done(Rating.Good), ListenAndChooseFlow().onChoice(true))
        ListenAndChooseFlow().apply {
            assertEquals(Outcome.TryAgain, onChoice(false))
            assertEquals(Outcome.Done(Rating.Again), onChoice(true))
        }
        ListenAndChooseFlow().apply {
            onChoice(false)
            assertEquals(Outcome.Done(Rating.Again, revealAnswer = true), onChoice(false))
        }
    }

    @Test
    fun failedCardComesBackOnce() {
        val a = Curriculum["a"]
        val i = Curriculum["i"]
        val runner = SessionRunner(listOf(Step.SeeAndSay(a), Step.SeeAndSay(i), Step.SeeAndSay(i), Step.SeeAndSay(i)))
        runner.next(Rating.Again)
        assertEquals(5, runner.total)
        runner.next(Rating.Good); runner.next(Rating.Good)
        assertEquals("a", runner.current!!.sound.id)
        runner.next(Rating.Again) // second failure: not requeued again
        assertEquals(5, runner.total)
        runner.next(Rating.Good)
        assertTrue(runner.finished)
    }

    @Test
    fun parentCorrectionReplaysTheCard() {
        val a = Curriculum["a"]
        val key = CardKey("a", CardKind.SeeAndSay)
        var p = Progress().introduce(a, t0)
        p = p.record(CardKey("a", CardKind.ListenAndChoose), Rating.Good, t0, Judge.Tap, scheduler)
        p = p.record(key, Rating.Again, t0.plusSeconds(30), Judge.Parent, scheduler)
        p = p.record(key, Rating.Good, t0.plusSeconds(120), Judge.Parent, scheduler)
        val wrong = p.reviews.indexOfFirst { it.key == key }

        val corrected = p.correct(wrong, Rating.Good, scheduler)
        assertTrue(corrected.reviews[wrong].corrected)
        // Same result as if the parent had said "right" both times.
        var direct = Progress().introduce(a, t0)
        direct = direct.record(CardKey("a", CardKind.ListenAndChoose), Rating.Good, t0, Judge.Tap, scheduler)
        direct = direct.record(key, Rating.Good, t0.plusSeconds(30), Judge.Parent, scheduler)
        direct = direct.record(key, Rating.Good, t0.plusSeconds(120), Judge.Parent, scheduler)
        assertEquals(direct.card(key), corrected.card(key))
        assertFalse(p.card(key) == corrected.card(key))
    }

    @Test
    fun noTtsTextIsALoneAccentedLetter() {
        // Engines spell a lone accented letter out ("e accent aigu") instead of saying its sound.
        val spelled = Curriculum.sounds.filter { it.ttsText.length == 1 && it.ttsText.first() !in 'a'..'z' }
        assertTrue(spelled.isEmpty(), spelled.map { it.ttsText }.toString())
    }

    @Test
    fun initialOfFirstNameMovesUp() {
        assertEquals("l", Curriculum.initialSoundOf("Lucie")?.id)
        assertEquals("ch", Curriculum.initialSoundOf("Charlotte")?.id)
        assertNull(Curriculum.initialSoundOf("Chloé"))
        assertEquals("an", Curriculum.initialSoundOf("Antoine")?.id)
        assertEquals("a", Curriculum.initialSoundOf("Anna")?.id)
        assertEquals("i", Curriculum.initialSoundOf("Inès")?.id)
        assertEquals("e_acute", Curriculum.initialSoundOf("Élodie")?.id)
        assertEquals("ou", Curriculum.initialSoundOf("Oumar")?.id)
        assertEquals("qu", Curriculum.initialSoundOf("Quentin")?.id)
        assertNull(Curriculum.initialSoundOf("Emma"))
        assertNull(Curriculum.initialSoundOf(""))

        val order = Curriculum.orderFor("Lucie").map { it.id }
        assertEquals(listOf("a", "e_acute", "l", "i"), order.take(4))
        assertEquals(Curriculum.sounds.size, order.size)
        assertEquals(Curriculum.sounds.map { it.id }, Curriculum.orderFor("Aya").map { it.id })
    }
}
