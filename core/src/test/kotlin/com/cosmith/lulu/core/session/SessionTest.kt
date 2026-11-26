package com.cosmith.lulu.core.session

import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.store.CardProgress
import com.cosmith.lulu.core.fsrs.Memory
import com.cosmith.lulu.core.content.Word
import com.cosmith.lulu.core.content.Words
import com.cosmith.lulu.core.content.SyllableShape
import com.cosmith.lulu.core.content.Syllables
import com.cosmith.lulu.core.content.Sound
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

    /** Plays a session like the app does: meets introduce, answers are recorded, 10 s per screen, 5 minutes. */
    private class Played(val steps: List<Step>, val progress: Progress, val end: Instant)

    private fun play(
        progress: Progress,
        start: Instant,
        seed: Int = 1,
        maxSteps: Int = 30,
        answer: (Step) -> Rating = { Rating.Good },
    ): Played {
        val planner = SessionPlanner(profile, Random(seed), ZoneOffset.UTC)
        var p = progress
        var t = start
        val steps = mutableListOf<Step>()
        while (steps.size < maxSteps) {
            val step = planner.next(p, t, timeLeft = Duration.ofSeconds(10L * (maxSteps - steps.size))) ?: break
            steps += step
            p = when (step) {
                is Step.Meet -> (step.item as? Sound)?.takeIf { step.lowercase }?.let { p.meetLowercase(it, t) } ?: p.meet(step.item, t)
                is Step.SeeAndSay -> (if (p.card(step.key) == null) p.meet(step.item, t) else p)
                    .record(step.key, answer(step), t, Judge.Parent, scheduler)
                is Step.Card -> p.record(step.key, answer(step), t, Judge.Tap, scheduler)
            }
            t = t.plusSeconds(10)
        }
        return Played(steps, p, t)
    }

    private fun Step.label() = when (this) {
        is Step.Meet -> if (lowercase) "meet-lower " else "meet "
        is Step.SeeAndSay -> if (lowercase) "see-lower " else "see "
        is Step.ListenAndChoose -> if (lowercase) "listen-lower " else "listen "
        is Step.MatchCase -> "match "
    } + item.id

    /** Plays one 5-minute session a day for [days] days, from [from]. */
    private fun playDays(days: Int, from: Progress = Progress()): List<Played> {
        var p = from
        return (0 until days).map { day ->
            play(p, t0.plus(Duration.ofDays(day.toLong())), seed = day).also { p = it.progress }
        }
    }

    @Test
    fun lowerCaseComesOnceTheCapitalsAreSolid() {
        val week = playDays(25)
        val firstLower = week.indexOfFirst { day -> day.steps.any { it is Step.Meet && it.lowercase && it.item is Sound } }
        assertTrue(firstLower >= 3, "lower case on day ${firstLower + 1}")
        val p = week.last().progress
        assertTrue(p.lowercaseOn.isNotEmpty())
        for (id in p.lowercaseOn.keys) {
            assertNotNull(p.card(CardKey(id, CardKind.MatchCase)))
            assertNotNull(p.card(CardKey(id, CardKind.ListenAndChooseLower)))
        }
        // Lower-case "voir et dire" opens after the first success by ear, like the capitals.
        assertTrue(p.lowercaseOn.keys.any { p.card(CardKey(it, CardKind.SeeAndSayLower)) != null })
        // Every sound met in lower case had solid capitals then; at most two a day.
        val perDay = p.lowercaseOn.values.groupingBy { it.atZone(ZoneOffset.UTC).toLocalDate() }.eachCount()
        assertTrue(perDay.values.all { it <= SessionPlanner.LOWERCASE_PER_DAY }, perDay.toString())
        // Lower-case cards are asked in the sessions.
        assertTrue(week.flatMap { it.steps }.any { it is Step.MatchCase })
        assertTrue(week.flatMap { it.steps }.any { it is Step.ListenAndChoose && it.lowercase })
    }

    @Test
    fun syllablesAndWordsUseOnlySoundsAlreadyMet() {
        val p = playDays(24).last().progress
        val syllables = p.itemsOn.keys.mapNotNull { Syllables.find(it) }
        val words = p.itemsOn.keys.mapNotNull { Words.find(it) }
        assertTrue(syllables.size >= 8, "${syllables.size} syllables")
        assertTrue(words.isNotEmpty(), "no words after 24 days")
        for (item in syllables + words) {
            val metAt = p.itemsOn.getValue(item.id)
            for (sound in item.sounds) {
                val base = Curriculum.prerequisites[sound.id] ?: listOf(sound.id)
                for (id in base) assertTrue(p.introducedAt(id)!!.isBefore(metAt), "${item.id} before $id")
            }
        }
        // Words only once six consonant-vowel syllables were known: they come well after the first syllable.
        val firstWord = words.minOf { p.itemsOn.getValue(it.id) }
        val cvBefore = syllables.count { it.shape == SyllableShape.CV && p.itemsOn.getValue(it.id).isBefore(firstWord) }
        assertTrue(cvBefore >= SessionPlanner.WORDS_AFTER_CV, "$cvBefore CV syllables before the first word")
    }

    @Test
    fun syllableChoicesOfferTheInversePair() {
        var p = Progress()
        for (id in listOf("a", "i", "o", "ou", "f", "l", "r")) {
            p = p.introduce(Curriculum[id], t0)
            for (kind in listOf(CardKind.ListenAndChoose, CardKind.SeeAndSay)) {
                val key = CardKey(id, kind)
                if (p.card(key) == null) p = p.copy(cards = p.cards + (key.id to CardProgress(key, Memory(due = t0))))
                p = p.record(key, Rating.Easy, t0, Judge.Tap, scheduler)
            }
        }
        val fou = Syllables.find("syl:f-ou")!!
        p = p.meet(fou, t0).record(CardKey(fou.id, CardKind.ListenAndChoose), Rating.Good, t0, Judge.Tap, scheduler)
        p = p.record(CardKey(fou.id, CardKind.ListenAndChoose), Rating.Good, t0.plusSeconds(700), Judge.Tap, scheduler)
        val planner = SessionPlanner(profile, Random(1), ZoneOffset.UTC)
        var listen: Step.ListenAndChoose? = null
        repeat(40) {
            val step = planner.next(p, t0.plus(Duration.ofDays(3)))
            if (step is Step.ListenAndChoose && step.item.id == fou.id) listen = step
        }
        assertNotNull(listen)
        assertTrue(listen!!.choices.any { it.id == "syl:ou-f" }, listen!!.choices.map { it.id }.toString())
    }

    @Test
    fun listeningChoicesNeverSoundTheSame() {
        val known = listOf("o", "au", "eau", "ou", "eu", "oi", "a").map { Curriculum[it] }
        var p = Progress()
        for (s in known) p = p.introduce(s, t0)
        val key = CardKey("au", CardKind.ListenAndChoose)
        var t = t0
        repeat(5) {
            p = p.record(key, Rating.Good, t, Judge.Tap, scheduler)
            t = p.card(key)!!.memory.due
        }
        repeat(20) { seed ->
            val choices = SessionPlanner(profile, Random(seed)).choicesFor(Curriculum["au"], p.card(key), known)
            val o = choices.filter { it.ipa == "o" }
            assertEquals(listOf("au"), o.map { it.id }, choices.map { it.id }.toString())
        }
    }

    @Test
    fun solidLowerCaseCardsGetLookalikeLetters() {
        var p = Progress()
        for (id in listOf("a", "i", "u", "p", "b", "d", "t", "n")) p = p.introduce(Curriculum[id], t0)
        val key = CardKey("b", CardKind.MatchCase)
        p = p.meetLowercase(Curriculum["b"], t0)
        var t = t0
        repeat(5) {
            p = p.record(key, Rating.Good, t, Judge.Tap, scheduler)
            t = p.card(key)!!.memory.due
        }
        val choices = SessionPlanner(profile, Random(2)).choicesFor(
            Curriculum["b"], p.card(key), p.introducedSounds(), lowercase = true,
        )
        assertEquals(4, choices.size)
        assertTrue(choices.map { it.id }.containsAll(listOf("b", "d", "p")), choices.map { it.id }.toString())
    }

    @Test
    fun firstSessionMeetsTwoSounds() {
        val played = play(Progress(), t0)
        assertEquals(listOf("meet a", "meet e_acute"), played.steps.take(2).map { it.label() })
        assertEquals(listOf("a", "e_acute"), played.progress.introduced)
        val firstListen = played.steps.filterIsInstance<Step.ListenAndChoose>().first()
        assertEquals(2, firstListen.choices.size)
    }

    @Test
    fun sessionLastsItsFullTime() {
        // Day 1 and day 5: there is always something to show for 5 minutes.
        var played = play(Progress(), t0)
        assertEquals(30, played.steps.size)
        for (day in 1..4) played = play(played.progress, t0.plus(Duration.ofDays(day.toLong())))
        assertEquals(30, played.steps.size)
    }

    @Test
    fun noNewSoundInTheLastMinute() {
        val day1 = play(Progress(), t0)
        for (day in 2..6) {
            val played = play(day1.progress, t0.plus(Duration.ofDays(day.toLong())), seed = day)
            // 10 s per screen: the last 6 screens are the last minute.
            assertTrue(played.steps.takeLast(6).none { it is Step.Meet }, played.steps.map { it.label() }.toString())
        }
    }

    @Test
    fun aSoundJustMetIsNotAskedRightAway() {
        val day1 = play(Progress(), t0)
        val day2 = play(day1.progress, Instant.parse("2026-11-22T08:00:00Z"))
        val steps = day2.steps
        val meets = steps.withIndex().filter { it.value is Step.Meet }
        assertTrue(meets.isNotEmpty())
        for ((i, meet) in meets) {
            val firstQuestion = steps.withIndex().first { (j, s) -> j > i && s !is Step.Meet && s.item.id == meet.item.id }.index
            val cardsBetween = steps.subList(i + 1, firstQuestion).count { it !is Step.Meet }
            assertTrue(cardsBetween >= SessionPlanner.NEW_CARD_GAP, steps.map { it.label() }.toString())
        }
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
                assertTrue(steps[i].item.id != steps[i - 1].item.id, "same sound twice in a row: ${steps.map { it.label() }}")
            }
        }
    }

    @Test
    fun seeAndSayOpensOnceTheSoundIsPickedOutReliably() {
        val a = Curriculum["a"]
        var p = Progress().introduce(a, t0)
        val listen = CardKey("a", CardKind.ListenAndChoose)
        val see = CardKey("a", CardKind.SeeAndSay)
        p = p.record(listen, Rating.Again, t0, Judge.Tap, scheduler)
        assertNull(p.card(see))
        p = p.record(listen, Rating.Good, t0.plusSeconds(60), Judge.Tap, scheduler)
        assertNull(p.card(see)) // one success is not enough: recognising comes before saying
        p = p.record(listen, Rating.Good, t0.plusSeconds(700), Judge.Tap, scheduler)
        assertNotNull(p.card(see))
        assertEquals(3, p.card(listen)!!.reviews)
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
    fun newThingsAreCappedPerDay() {
        val day1 = play(Progress(), t0)
        assertEquals(SessionPlanner.NEW_SOUNDS_PER_DAY, day1.progress.introduced.size)
        // Two sessions on day 2, morning and evening: the daily limits hold across sessions.
        val morning = play(day1.progress, Instant.parse("2026-11-22T08:00:00Z"))
        val evening = play(morning.progress, Instant.parse("2026-11-22T18:00:00Z"))
        val newSounds = evening.progress.introduced.size - day1.progress.introduced.size
        val newThings = (morning.steps + evening.steps).count { it is Step.Meet || (it is Step.SeeAndSay && it.item is Word && it !in day1.steps) }
        assertTrue(newSounds in 1..SessionPlanner.NEW_SOUNDS_PER_DAY, evening.progress.introduced.toString())
        assertTrue(newThings <= SessionPlanner.NEW_THINGS_PER_DAY, "$newThings new things")
    }

    @Test
    fun nothingNewWhileManyCardsWaitOrAfterManyMisses() {
        // A week away: many cards are due, so the first session only reviews.
        var p = playDays(6).last().progress
        val back = play(p, t0.plus(Duration.ofDays(14)), maxSteps = 8)
        assertTrue(back.steps.none { it is Step.Meet }, back.steps.map { it.label() }.toString())
        // Many recent misses: nothing new either.
        p = playDays(3).last().progress
        val struggling = play(p, t0.plus(Duration.ofDays(3)).plusSeconds(3600)) { Rating.Again }
        assertTrue(struggling.steps.none { it is Step.Meet })
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
            if (id != "a") {
                p = p.record(CardKey(id, CardKind.ListenAndChoose), Rating.Good, t0, Judge.Tap, scheduler)
                p = p.record(CardKey(id, CardKind.ListenAndChoose), Rating.Good, t0, Judge.Tap, scheduler)
            }
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
    fun everythingKnownStillGivesAFullSession() {
        var p = Progress()
        var t = t0
        for (sound in Curriculum.sounds) p = p.introduce(sound, t)
        repeat(8) {
            for (card in p.cards.values.toList()) p = p.record(card.key, Rating.Good, t, Judge.Tap, scheduler)
            t = t.plus(Duration.ofDays(1))
        }
        val played = play(p, t)
        assertEquals(30, played.steps.size)
        // No sound left to meet in capitals; lower case and syllables come instead.
        val meets = played.steps.filterIsInstance<Step.Meet>()
        assertTrue(meets.none { it.item is Sound && !it.lowercase }, meets.toString())
        assertTrue(meets.count { it.lowercase && it.item is Sound } <= SessionPlanner.LOWERCASE_PER_DAY, meets.toString())
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
        assertEquals(Rating.Good, Ratings.choice(true))
        assertEquals(Rating.Again, Ratings.choice(false))
    }

    @Test
    fun failedCardComesBackOnce() {
        val a = Curriculum["a"]
        val i = Curriculum["i"]
        val runner = SessionRunner(listOf(Step.SeeAndSay(a), Step.SeeAndSay(i), Step.SeeAndSay(i), Step.SeeAndSay(i)))
        runner.next(Rating.Again)
        assertEquals(5, runner.total)
        runner.next(Rating.Good); runner.next(Rating.Good)
        assertEquals("a", runner.current!!.item.id)
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
        repeat(2) { p = p.record(CardKey("a", CardKind.ListenAndChoose), Rating.Good, t0, Judge.Tap, scheduler) }
        p = p.record(key, Rating.Again, t0.plusSeconds(30), Judge.Parent, scheduler)
        p = p.record(key, Rating.Good, t0.plusSeconds(120), Judge.Parent, scheduler)
        val wrong = p.reviews.indexOfFirst { it.key == key }

        val corrected = p.correct(wrong, Rating.Good, scheduler)
        assertTrue(corrected.reviews[wrong].corrected)
        // Same result as if the parent had said "right" both times.
        var direct = Progress().introduce(a, t0)
        repeat(2) { direct = direct.record(CardKey("a", CardKind.ListenAndChoose), Rating.Good, t0, Judge.Tap, scheduler) }
        direct = direct.record(key, Rating.Good, t0.plusSeconds(30), Judge.Parent, scheduler)
        direct = direct.record(key, Rating.Good, t0.plusSeconds(120), Judge.Parent, scheduler)
        assertEquals(direct.card(key), corrected.card(key))
        assertFalse(p.card(key) == corrected.card(key))
    }

    @Test
    fun answeringACardThatDoesNotExistYetCreatesIt() {
        val key = CardKey("ou", CardKind.SeeAndSay)
        val p = Progress().record(key, Rating.Good, t0, Judge.Parent, scheduler)
        assertEquals(1, p.card(key)!!.reviews)
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
        assertEquals("c", Curriculum.initialSoundOf("Chloé")?.id) // a hard c, from level 5
        assertNull(Curriculum.initialSoundOf("Céline")) // a soft c has no card of its own
        assertEquals("z", Curriculum.initialSoundOf("Zoé")?.id)
        assertEquals("au", Curriculum.initialSoundOf("Aurélie")?.id)
        assertEquals("a", Curriculum.initialSoundOf("Aïcha")?.id)
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
