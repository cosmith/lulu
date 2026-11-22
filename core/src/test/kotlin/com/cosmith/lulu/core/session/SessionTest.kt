package com.cosmith.lulu.core.session

import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.fsrs.CardState
import com.cosmith.lulu.core.fsrs.Rating
import com.cosmith.lulu.core.store.CardKey
import com.cosmith.lulu.core.store.CardKind
import com.cosmith.lulu.core.store.Judge
import com.cosmith.lulu.core.store.Profile
import com.cosmith.lulu.core.store.Progress
import com.cosmith.lulu.core.store.luluScheduler
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
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

    /** Plays a session like the app does: meets introduce, answers are recorded, 10 s per screen. */
    private class Played(val steps: List<Step>, val progress: Progress, val end: Instant)

    private fun play(
        progress: Progress,
        start: Instant,
        seed: Int = 1,
        maxSteps: Int = 200,
        answer: (Step) -> Rating = { Rating.Good },
    ): Played {
        val planner = SessionPlanner(profile, Random(seed), ZoneOffset.UTC)
        var p = progress
        var t = start
        val steps = mutableListOf<Step>()
        while (steps.size < maxSteps) {
            val step = planner.next(p, t) ?: break
            steps += step
            p = when (step) {
                is Step.Meet -> p.introduce(step.sound, t)
                is Step.SeeAndSay -> p.record(step.key, answer(step), t, Judge.Parent, scheduler)
                is Step.ListenAndChoose -> p.record(step.key, answer(step), t, Judge.Tap, scheduler)
            }
            t = t.plusSeconds(10)
        }
        return Played(steps, p, t)
    }

    private fun Step.label() = (if (this is Step.Meet) "meet " else if (this is Step.SeeAndSay) "see " else "listen ") + sound.id

    @Test
    fun firstSessionMeetsTwoSoundsAndInterleavesThem() {
        val played = play(Progress(), t0)
        assertEquals(
            listOf("meet a", "listen a", "meet e_acute", "listen e_acute", "see a", "see e_acute"),
            played.steps.take(6).map { it.label() },
        )
        val firstListen = assertIs<Step.ListenAndChoose>(played.steps[1])
        assertEquals(2, firstListen.choices.size)
        assertEquals(2, played.steps.count { it is Step.Meet })
    }

    @Test
    fun sessionEndsWhenNothingUsefulIsLeft() {
        val played = play(Progress(), t0)
        assertTrue(played.steps.size < 200, "session never ends")
        val perCard = played.steps.filterNot { it is Step.Meet }.groupingBy { it.label() }.eachCount()
        assertTrue(perCard.values.all { it <= SessionPlanner.MAX_SHOWS_PER_CARD }, perCard.toString())
    }

    @Test
    fun sameSoundNeverTwiceInARowWhenThereIsAChoice() {
        var p = Progress()
        for (id in listOf("a", "e_acute", "i")) {
            p = p.introduce(Curriculum[id], t0)
            p = p.record(CardKey(id, CardKind.ListenAndChoose), Rating.Good, t0, Judge.Tap, scheduler)
        }
        for (seed in 0 until 30) {
            val rnd = Random(seed)
            // The first screens, while several sounds still have cards to show. At the very
            // end of a session the last cards left may all be of one sound, and then it repeats.
            val played = play(p, t0.plus(Duration.ofHours(3)), seed = seed, maxSteps = 9) {
                if (rnd.nextInt(4) == 0) Rating.Again else Rating.Good
            }
            val steps = played.steps
            for (i in 1 until steps.size) {
                // A sound just met is picked out by ear right after: that pair is on purpose.
                if (steps[i - 1] is Step.Meet) continue
                assertTrue(steps[i].sound.id != steps[i - 1].sound.id, "same sound twice in a row: ${steps.map { it.label() }}")
            }
        }
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
    fun noNewSoundWhileThreeAreBeingLearned() {
        var p = Progress()
        for (id in listOf("a", "e_acute", "i")) {
            p = p.introduce(Curriculum[id], t0)
            p = p.record(CardKey(id, CardKind.ListenAndChoose), Rating.Good, t0, Judge.Tap, scheduler)
        }
        val played = play(p, t0.plus(Duration.ofHours(2))) { Rating.Again }
        assertTrue(played.steps.none { it is Step.Meet })
        assertTrue(played.steps.isNotEmpty())
    }

    @Test
    fun newSoundArrivesOnceOthersAreSettled() {
        // Two days of play with good answers: a and é settle, and new sounds keep coming.
        var played = play(Progress(), t0)
        played = play(played.progress, t0.plus(Duration.ofDays(1)))
        played = play(played.progress, t0.plus(Duration.ofDays(2)))
        assertTrue(played.progress.introduced.size >= 4, played.progress.introduced.toString())
        assertEquals(Curriculum.sounds.take(played.progress.introduced.size).map { it.id }, played.progress.introduced)
    }

    @Test
    fun oneNewSoundPerDayAfterTheFirst() {
        val day1 = play(Progress(), t0)
        assertEquals(2, day1.progress.introduced.size)
        // Two sessions on day 2, morning and evening: one new sound in all.
        val morning = play(day1.progress, Instant.parse("2026-11-22T08:00:00Z"))
        val evening = play(morning.progress, Instant.parse("2026-11-22T18:00:00Z"))
        assertEquals(3, evening.progress.introduced.size)
        assertEquals(1, morning.steps.count { it is Step.Meet } + evening.steps.count { it is Step.Meet })
        // And another one the day after.
        val day3 = play(evening.progress, Instant.parse("2026-11-23T08:00:00Z"))
        assertEquals(4, day3.progress.introduced.size)
    }

    @Test
    fun newSoundComesAfterAWarmUp() {
        val day1 = play(Progress(), t0)
        val day2 = play(day1.progress, Instant.parse("2026-11-22T08:00:00Z"))
        val meet = day2.steps.indexOfFirst { it is Step.Meet }
        assertTrue(meet >= SessionPlanner.WARM_UP, day2.steps.map { it.label() }.toString())
        // Before the new sound, yesterday's cards were reviewed (early: they are due tomorrow).
        assertTrue(day2.steps.take(meet).all { it !is Step.Meet })
    }

    @Test
    fun noNewSoundWhenTheChildJustMissedSeveralCards() {
        val day1 = play(Progress(), t0)
        val day2 = play(day1.progress, Instant.parse("2026-11-22T08:00:00Z")) { Rating.Again }
        assertTrue(day2.steps.none { it is Step.Meet }, day2.steps.map { it.label() }.toString())
    }

    @Test
    fun missedCardComesBackAfterAMinute() {
        var p = Progress()
        for (id in listOf("a", "e_acute", "i")) {
            p = p.introduce(Curriculum[id], t0)
            if (id != "a") p = p.record(CardKey(id, CardKind.ListenAndChoose), Rating.Good, t0, Judge.Tap, scheduler)
        }
        val key = CardKey("a", CardKind.ListenAndChoose)
        p = p.record(key, Rating.Again, t0, Judge.Tap, scheduler)
        assertEquals(t0.plusSeconds(60), p.card(key)!!.memory.due)
        // é and i are next due in 10 minutes, but their "voir et dire" cards just opened.
        p = p.record(CardKey("e_acute", CardKind.SeeAndSay), Rating.Good, t0, Judge.Parent, scheduler)
        p = p.record(CardKey("i", CardKind.SeeAndSay), Rating.Good, t0, Judge.Parent, scheduler)
        // A minute later the missed card is the only one due, so it comes first.
        assertEquals(key, (SessionPlanner(profile, zone = ZoneOffset.UTC).next(p, t0.plusSeconds(61)) as Step.ListenAndChoose).key)
    }

    @Test
    fun nothingToDoWhenEverythingIsSolidAndNotDue() {
        var p = Progress()
        var t = t0
        for (s in Curriculum.sounds) p = p.introduce(s, t)
        repeat(8) {
            for (card in p.cards.values.toList()) p = p.record(card.key, Rating.Good, t, Judge.Tap, scheduler)
            t = t.plus(Duration.ofDays(1))
        }
        for (card in p.cards.values.toList()) p = p.record(card.key, Rating.Good, t, Judge.Tap, scheduler)
        assertTrue(p.cards.values.all { it.memory.state == CardState.Review })
        assertNull(SessionPlanner(profile, zone = ZoneOffset.UTC).next(p, t.plusSeconds(60)))
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
        val choices = SessionPlanner(profile, Random(5)).choicesFor(ou, card, p.introducedSounds())
        assertEquals(4, choices.size)
        // "on" is a look-alike too but has not been met yet; o and u have.
        assertTrue(choices.map { it.id }.containsAll(listOf("ou", "o", "u")), choices.map { it.id }.toString())
    }

    @Test
    fun oneAnswerPerCard() {
        assertEquals(Rating.Good, Ratings.seeAndSay(correct = true, heardSoundFirst = false))
        assertEquals(Rating.Hard, Ratings.seeAndSay(correct = true, heardSoundFirst = true))
        assertEquals(Rating.Again, Ratings.seeAndSay(correct = false, heardSoundFirst = false))
        assertEquals(Rating.Again, Ratings.seeAndSay(correct = false, heardSoundFirst = true))
        assertEquals(Rating.Good, Ratings.listenAndChoose(true))
        assertEquals(Rating.Again, Ratings.listenAndChoose(false))
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
