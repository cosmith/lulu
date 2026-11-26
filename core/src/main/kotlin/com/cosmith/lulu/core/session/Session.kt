package com.cosmith.lulu.core.session

import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.content.Item
import com.cosmith.lulu.core.content.Sound
import com.cosmith.lulu.core.content.Syllable
import com.cosmith.lulu.core.content.SyllableShape
import com.cosmith.lulu.core.content.Syllables
import com.cosmith.lulu.core.content.Word
import com.cosmith.lulu.core.content.WordShape
import com.cosmith.lulu.core.content.Words
import com.cosmith.lulu.core.fsrs.CardState
import com.cosmith.lulu.core.fsrs.Rating
import com.cosmith.lulu.core.store.CardKey
import com.cosmith.lulu.core.store.CardKind
import com.cosmith.lulu.core.store.CardProgress
import com.cosmith.lulu.core.store.Profile
import com.cosmith.lulu.core.store.Progress
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** One screen of a session, about an item: a sound, a syllable or a word. */
sealed interface Step {
    val item: Item

    /**
     * First meeting: Lulu shows the item and says it. Not graded. For a syllable the
     * sounds come one by one, then merge. With [lowercase], the meeting is with the
     * lower-case letter of a sound already known.
     */
    data class Meet(override val item: Item, val lowercase: Boolean = false) : Step

    /** A step that is an FSRS card and gets a rating. */
    sealed interface Card : Step {
        val key: CardKey
        val lowercase: Boolean
    }

    /** "Voir et dire": read the item aloud. */
    data class SeeAndSay(override val item: Item, override val lowercase: Boolean = false) : Card {
        override val key get() = CardKey(item.id, if (item is Sound && lowercase) CardKind.SeeAndSayLower else CardKind.SeeAndSay)
    }

    /** "Écouter et choisir": Lulu says the item, the child taps it among [choices]. */
    data class ListenAndChoose(override val item: Item, val choices: List<Item>, override val lowercase: Boolean = false) : Card {
        override val key get() = CardKey(item.id, if (item is Sound && lowercase) CardKind.ListenAndChooseLower else CardKind.ListenAndChoose)
    }

    /** The capital is shown; the child taps its lower-case letter among [choices]. */
    data class MatchCase(override val item: Sound, val choices: List<Sound>) : Card {
        override val key get() = CardKey(item.id, CardKind.MatchCase)
        override val lowercase get() = true
    }
}

/** The kinds of new things a session can bring. */
private enum class Novelty { Sound, Syllable, Word, Lowercase }

/**
 * Chooses the cards of one session, one at a time, from the child's progress and the
 * clock, until the session's time is up. The parent only sets how long a session
 * lasts; everything else is decided here:
 *
 * 1. something new, after a warm-up of [WARM_UP] cards and at least
 *    [NEW_SOUND_SPACING] cards after the previous new thing, at most [NEW_THINGS_PER_DAY]
 *    a day, and only once what is there holds (few cards waiting, few recent misses;
 *    see [readyForSomethingNew]). The kinds take turns among those allowed:
 *    - a sound, up to [NEW_SOUNDS_PER_DAY] a day while fewer than
 *      [MAX_SOUNDS_IN_LEARNING] are being learned;
 *    - a syllable made of known sounds, up to [SYLLABLES_PER_DAY] a day: CV first, then
 *      inverse pairs (VC), CVC and CCV as the earlier shapes settle;
 *    - a word made of known sounds, up to [WORDS_PER_DAY] a day, once enough
 *      syllables of its shape are known;
 *    - the lower case of a sound whose capitals are solid, up to [LOWERCASE_PER_DAY] a day;
 * 2. cards that are due, the most overdue first;
 * 3. practice on items still being learned, the one seen longest ago first;
 * 4. early review of cards that are not due yet, once each;
 * 5. when all of that is used up, whatever was seen longest ago, so the session
 *    lasts its full time.
 *
 * A card never seen waits for [NEW_CARD_GAP] other cards: an item just met is not
 * asked about right away, when it would only be an echo. The same item never comes
 * twice in a row when something else can come. A missed card is due again a few
 * minutes later (FSRS learning steps), so it comes back by itself.
 * [next] returns null only when there is no card at all and nothing new to meet.
 */
class SessionPlanner(
    private val profile: Profile,
    private val random: Random = Random.Default,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val order = Curriculum.orderFor(profile.firstName)
    private val shown = mutableMapOf<String, Int>()
    private val lastShown = mutableMapOf<String, Int>()
    private val heldUntil = mutableMapOf<String, Int>()
    private var cardsShown = 0
    private var lastMeetAt = Int.MIN_VALUE / 2
    private var lastItem: String? = null
    private var startedAt: Instant? = null
    private var timeLeft: Duration? = null

    /** @param timeLeft how much of the session remains, when known. */
    fun next(progress: Progress, now: Instant, timeLeft: Duration? = null): Step? {
        if (startedAt == null) startedAt = now
        this.timeLeft = timeLeft
        // Cards never seen wait a little before their first question.
        for (card in progress.cards.values) {
            if (card.reviews == 0 && card.key.id !in heldUntil) heldUntil[card.key.id] = cardsShown + NEW_CARD_GAP
        }
        val step = choose(progress, now) ?: return null
        lastItem = step.item.id
        // A word is met by reading it: its first card is new too.
        if (step is Step.Meet || (step is Step.Card && progress.card(step.key) == null)) lastMeetAt = cardsShown
        if (step is Step.Card) count(step.key)
        return step
    }

    private fun count(key: CardKey) {
        shown.merge(key.id, 1, Int::plus)
        lastShown[key.id] = cardsShown
        cardsShown++
    }

    private fun choose(progress: Progress, now: Instant): Step? {
        // Cards of items this version does not know (none today) are left aside.
        val all = progress.cards.values.filter { Curriculum.item(it.key.itemId) != null }
        val held = all.filter { (heldUntil[it.key.id] ?: 0) > cardsShown }.map { it.key.id }.toSet()
        val open = all.filter { it.key.id !in held && (shown[it.key.id] ?: 0) < MAX_SHOWS_PER_CARD }
        val seenLongestAgo = compareBy<CardProgress>({ lastShown[it.key.id] ?: -1 }, { it.memory.due })
        val due = open.filter { !it.memory.due.isAfter(now) }.sortedBy { it.memory.due }
        val practice = open.filter { it.memory.state != CardState.Review && it.memory.due.isAfter(now) }
            .sortedWith(seenLongestAgo)
        val early = open.filter { it.memory.state == CardState.Review && it.memory.due.isAfter(now) && it.key.id !in shown }
            .sortedBy { it.memory.due }
        val newThing = somethingNew(progress, now, dueNow = due.size)

        fun List<CardProgress>.other() = firstOrNull { it.key.itemId != lastItem }

        // Something new gets its turn even when reviews are waiting, as new cards do in Anki;
        // the daily limits keep the pace.
        if (newThing != null && cardsShown >= WARM_UP && cardsShown - lastMeetAt >= NEW_SOUND_SPACING) {
            return newThing
        }
        due.other()?.let { return stepFor(it, progress) }
        (practice.other() ?: early.other())?.let { return stepFor(it, progress) }
        // Nothing else to show yet (the very first screens): meet something, but not more
        // than two in a row before asking about them.
        if (newThing != null && held.size < 2) return newThing
        // Cards still waiting after a first meeting come last.
        val any = all.sortedWith(compareBy<CardProgress> { it.key.id in held }.then(seenLongestAgo))
        return (any.other() ?: any.firstOrNull())?.let { stepFor(it, progress) }
    }

    /**
     * The next new thing. The kinds take turns: among those allowed now, the one whose
     * last new item is the oldest goes first, so sounds, syllables, words and lower case
     * all move forward (sounds first on a tie).
     */
    private fun somethingNew(progress: Progress, now: Instant, dueNow: Int): Step? {
        if (!readyForSomethingNew(progress, dueNow)) return null
        val today = startOfDay(now)
        fun Map<String, Instant>.countToday(prefix: String = "") = count { (id, at) -> id.startsWith(prefix) && !at.isBefore(today) }
        fun Map<String, Instant>.last(prefix: String = "") = filterKeys { it.startsWith(prefix) }.values.maxOrNull() ?: Instant.MIN
        val newToday = progress.introducedOn.countToday() + progress.itemsOn.countToday() + progress.lowercaseOn.countToday()
        if (newToday >= NEW_THINGS_PER_DAY) return null
        val lastSound = progress.introduced.mapNotNull { progress.introducedAt(it) }.maxOrNull() ?: Instant.MIN
        val options = listOfNotNull(
            newSound(progress, today)?.let { Triple(Novelty.Sound, lastSound, Step.Meet(it)) },
            newSyllable(progress)?.takeIf { progress.itemsOn.countToday(Syllables.PREFIX) < SYLLABLES_PER_DAY }
                ?.let { Triple(Novelty.Syllable, progress.itemsOn.last(Syllables.PREFIX), Step.Meet(it, lowercase(it, progress))) },
            newWord(progress)?.takeIf { progress.itemsOn.countToday(Words.PREFIX) < WORDS_PER_DAY }
                ?.let { Triple(Novelty.Word, progress.itemsOn.last(Words.PREFIX), Step.SeeAndSay(it, lowercase(it, progress))) },
            newLowercase(progress)?.takeIf { progress.lowercaseOn.countToday() < LOWERCASE_PER_DAY }
                ?.let { Triple(Novelty.Lowercase, progress.lowercaseOn.last(), Step.Meet(it, lowercase = true)) },
        )
        return options.minWithOrNull(compareBy({ it.second }, { it.first.ordinal }))?.third
    }

    private fun newSound(progress: Progress, today: Instant): Sound? {
        val next = order.firstOrNull { it.id !in progress.introduced } ?: return null
        val metToday = progress.introduced.count { id -> progress.introducedAt(id)?.isBefore(today) == false }
        if (metToday >= NEW_SOUNDS_PER_DAY) return null
        if (progress.soundsInLearning().size >= MAX_SOUNDS_IN_LEARNING) return null
        return next
    }

    private fun newLowercase(progress: Progress): Sound? = progress.introducedSounds().firstOrNull {
        it.id !in progress.lowercaseOn && progress.capitalsSolid(it.id, SOLID_DAYS)
    }

    /** Syllables of each shape that the child knows well. */
    private fun syllablesInReview(progress: Progress): Map<SyllableShape, Int> =
        progress.itemsOn.keys.filter { it.startsWith(Syllables.PREFIX) && progress.known(it) }
            .mapNotNull { Syllables.find(it)?.shape }.groupingBy { it }.eachCount()

    private fun unlockedShapes(progress: Progress): Set<SyllableShape> {
        val known = syllablesInReview(progress)
        val cv = known[SyllableShape.CV] ?: 0
        return buildSet {
            add(SyllableShape.CV)
            if (cv >= VC_AFTER_CV) add(SyllableShape.VC)
            if (cv >= CVC_AFTER_CV) add(SyllableShape.CVC)
            if ((known[SyllableShape.CVC] ?: 0) >= CCV_AFTER_CVC) add(SyllableShape.CCV)
        }
    }

    /**
     * The next syllable: made of known sounds, of an unlocked shape, taking the shapes in
     * turn (the one met longest ago first), and within a shape the syllable whose first
     * and last sounds have been used the least. Vowel-consonant syllables come as the
     * inverse of a consonant-vowel one already met: OUF after FOU.
     */
    private fun newSyllable(progress: Progress): Syllable? {
        if (progress.itemsInLearning(Syllables.PREFIX) >= MAX_SYLLABLES_IN_LEARNING) return null
        val met = progress.itemsOn.filterKeys { it.startsWith(Syllables.PREFIX) }
        val metSyllables = met.keys.mapNotNull { Syllables.find(it) }
        val shapes = unlockedShapes(progress)
        val candidates = Syllables.all.filter { s ->
            s.id !in met && s.shape in shapes && s.sounds.all { progress.knows(it.id) } &&
                (s.shape != SyllableShape.VC || s.inverse?.id in met)
        }
        if (candidates.isEmpty()) return null
        val lastByShape = metSyllables.associate { it.shape to met.getValue(it.id) }
        val shape = candidates.map { it.shape }.distinct().minBy { lastByShape[it] ?: Instant.MIN }
        val firstUse = metSyllables.groupingBy { it.sounds.first().id }.eachCount()
        val lastUse = metSyllables.groupingBy { it.sounds.last().id }.eachCount()
        return candidates.filter { it.shape == shape }
            .minBy { (firstUse[it.sounds.first().id] ?: 0) + (lastUse[it.sounds.last().id] ?: 0) }
    }

    /** The next word: made of known sounds, open syllables first, short ones first. */
    private fun newWord(progress: Progress): Word? {
        if (progress.itemsInLearning(Words.PREFIX) >= MAX_WORDS_IN_LEARNING) return null
        val known = syllablesInReview(progress)
        fun allowed(shape: WordShape) = when (shape) {
            WordShape.Open -> (known[SyllableShape.CV] ?: 0) >= WORDS_AFTER_CV
            WordShape.Closed -> (known[SyllableShape.CVC] ?: 0) >= WORDS_AFTER_SHAPE
            WordShape.Cluster -> (known[SyllableShape.CCV] ?: 0) >= WORDS_AFTER_SHAPE
        }
        return Words.all.filter { w ->
            w.id !in progress.itemsOn && allowed(w.shape) && w.sounds.all { progress.knows(it.id) }
        }.minWithOrNull(compareBy({ it.shape.ordinal }, { it.sounds.size }))
    }

    /**
     * Something new only once what is already there holds: not while [MAX_DUE_FOR_NEW]
     * cards or more wait to be reviewed, not when more than [MAX_RECENT_MISSES] of the
     * last [RECENT_ANSWERS] answers (across sessions) were missed, not right after two
     * misses in this session, and not too late in the session to be asked about it.
     */
    private fun readyForSomethingNew(progress: Progress, dueNow: Int): Boolean {
        if (timeLeft?.let { it < NO_NEW_SOUND_AT_THE_END } == true) return false
        if (dueNow >= MAX_DUE_FOR_NEW) return false
        val recent = progress.reviews.takeLast(RECENT_ANSWERS)
        if (recent.count { it.rating == Rating.Again } > MAX_RECENT_MISSES) return false
        val thisSession = progress.reviews.filter { r -> startedAt?.let { !r.at.isBefore(it) } == true }.takeLast(4)
        return thisSession.count { it.rating == Rating.Again } < 2
    }

    private fun startOfDay(now: Instant): Instant = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

    /** Syllables and words switch to lower case once the child has met every letter of them in lower case. */
    private fun lowercase(item: Item, progress: Progress): Boolean =
        item.sounds.all { (Curriculum.prerequisites[it.id]?.first() ?: it.id) in progress.lowercaseOn }

    private fun stepFor(card: CardProgress, progress: Progress): Step {
        val item = Curriculum.item(card.key.itemId) ?: error("unknown item ${card.key.itemId}")
        if (item !is Sound) {
            val lower = lowercase(item, progress)
            return when (card.key.kind) {
                CardKind.ListenAndChoose -> Step.ListenAndChoose(item, itemChoices(item, card, progress), lower)
                else -> Step.SeeAndSay(item, lower)
            }
        }
        return when (card.key.kind) {
            CardKind.SeeAndSay -> Step.SeeAndSay(item)
            CardKind.SeeAndSayLower -> Step.SeeAndSay(item, lowercase = true)
            CardKind.ListenAndChoose -> Step.ListenAndChoose(item, choicesFor(item, card, progress.introducedSounds()))
            CardKind.ListenAndChooseLower -> Step.ListenAndChoose(item, lowerChoices(item, card, progress, listening = true), lowercase = true)
            CardKind.MatchCase -> Step.MatchCase(item, lowerChoices(item, card, progress, listening = false))
        }
    }

    /** Lower-case decoys: letters whose lower case the child has met come first. */
    private fun lowerChoices(sound: Sound, card: CardProgress, progress: Progress, listening: Boolean) =
        choicesFor(sound, card, progress.introducedSounds(), lowercase = true, preferred = progress.lowercaseOn.keys, listening = listening)

    private fun choiceCount(card: CardProgress?): Int {
        val solid = card != null && card.memory.state == CardState.Review && (card.memory.stability ?: 0.0) >= SOLID_DAYS
        return when {
            card == null || card.reviews < 2 -> 2
            !solid -> 3
            else -> 4
        }
    }

    /**
     * Two choices while a card is new, three while it is settling, four once it is solid.
     * Decoys are sounds the child has met, [preferred] ones first; a solid card gets
     * look-alikes first (by sound for capitals, by shape too for lower case). When the
     * child chooses by ear, no decoy sounds the same as the target (AU and O, C and QU).
     */
    fun choicesFor(
        target: Sound,
        card: CardProgress?,
        known: List<Sound>,
        lowercase: Boolean = false,
        preferred: Set<String> = emptySet(),
        listening: Boolean = true,
    ): List<Sound> {
        val count = choiceCount(card)
        val solid = count == 4
        fun ok(s: Sound) = s.id != target.id && s.alone && !(listening && s.ipa == target.ipa)
        val others = known.filter(::ok).distinctBy { it.id }
        val alike = if (lowercase) target.lowerLookalikes + target.lookalikes else target.lookalikes
        val lookalikes = if (solid) others.filter { it.id in alike }.shuffled(random) else emptyList()
        val (first, then) = (others - lookalikes.toSet()).partition { it.id in preferred }
        val rest = first.shuffled(random) + then.shuffled(random)
        // At the very start there may not be enough known sounds: borrow the next ones to come.
        val upcoming = order.filter { s -> ok(s) && others.none { it.id == s.id } }
        val decoys = (lookalikes + rest + upcoming).distinctBy { it.ipa }.take(count - 1)
        return (decoys + target).shuffled(random)
    }

    /**
     * Decoys for a syllable or a word, read the same way as the target is shown. For a
     * syllable: its inverse first (OUF for FOU), then syllables sharing its first or
     * last sound. For a word: words sharing a sound with it. Never one that sounds the
     * same, and three choices at most for words, which are long to read.
     */
    private fun itemChoices(target: Item, card: CardProgress, progress: Progress): List<Item> {
        val ipa = { i: Item -> i.sounds.joinToString("") { it.ipa } }
        fun decodable(i: Item) = i.id != target.id && ipa(i) != ipa(target) && i.sounds.all { progress.knows(it.id) }
        val decoys: List<Item> = when (target) {
            is Syllable -> {
                val pool = Syllables.all.filter { it.shape == target.shape && decodable(it) }
                val inverse = target.inverse?.takeIf { decodable(it) }
                val close = pool.filter { it.sounds.first() == target.sounds.first() || it.sounds.last() == target.sounds.last() }
                listOfNotNull(inverse) + close.shuffled(random) + pool.shuffled(random)
            }
            is Word -> {
                val pool = Words.all.filter(::decodable)
                val close = pool.filter { w -> w.sounds.first() == target.sounds.first() || w.sounds.last() == target.sounds.last() }
                close.shuffled(random) + pool.shuffled(random)
            }
            is Sound -> emptyList()
        }
        val count = if (target is Word) minOf(3, choiceCount(card)) else choiceCount(card)
        return (decoys.distinctBy(ipa).take(count - 1) + target).shuffled(random)
    }

    companion object {
        /** A card this stable (in days) is solid: four choices, look-alike decoys. */
        const val SOLID_DAYS = 7.0
        const val MAX_SOUNDS_IN_LEARNING = 3
        const val MAX_SYLLABLES_IN_LEARNING = 4
        const val MAX_WORDS_IN_LEARNING = 2
        const val MAX_SHOWS_PER_CARD = 3
        const val NEW_THINGS_PER_DAY = 3
        const val NEW_SOUNDS_PER_DAY = 2
        const val SYLLABLES_PER_DAY = 2
        const val WORDS_PER_DAY = 2
        const val LOWERCASE_PER_DAY = 1
        const val MAX_DUE_FOR_NEW = 8
        const val RECENT_ANSWERS = 20
        const val MAX_RECENT_MISSES = 4
        const val NEW_SOUND_SPACING = 4
        const val NEW_CARD_GAP = 3
        const val WARM_UP = 3

        /** Syllables of the earlier shape the child must know before the next shape opens. */
        const val VC_AFTER_CV = 4
        const val CVC_AFTER_CV = 6
        const val CCV_AFTER_CVC = 4

        /** Known syllables needed before words: open words after CV, others after their own shape. */
        const val WORDS_AFTER_CV = 6
        const val WORDS_AFTER_SHAPE = 3
        val NO_NEW_SOUND_AT_THE_END: Duration = Duration.ofMinutes(1)
    }
}

/**
 * One answer per card, as in Anki: the card gets its rating and the session moves on.
 * A card rated "À revoir" is due again a few minutes later (FSRS learning steps), so
 * it comes back later in the session instead of right away.
 */
object Ratings {
    /** "Voir et dire": right → Bien; right after asking to hear the sound → Difficile; wrong → À revoir. */
    fun seeAndSay(correct: Boolean, heardSoundFirst: Boolean): Rating = when {
        !correct -> Rating.Again
        heardSoundFirst -> Rating.Hard
        else -> Rating.Good
    }

    /** A tap among choices ("écouter et choisir", capital to lower case): right → Bien; wrong → À revoir. */
    fun choice(correct: Boolean): Rating = if (correct) Rating.Good else Rating.Again
}

/**
 * Walks through a fixed list of steps (screenshots and tests). A card rated
 * "À revoir" comes back once, a few screens later.
 */
class SessionRunner(planned: List<Step>) {
    private val steps = planned.toMutableList()
    private val requeued = mutableSetOf<String>()

    var index = 0
        private set

    val current: Step? get() = steps.getOrNull(index)
    val total: Int get() = steps.size
    val finished: Boolean get() = index >= steps.size

    fun next(rating: Rating? = null) {
        val step = current ?: return
        if (rating == Rating.Again && step is Step.Card) {
            if (requeued.add(step.key.id)) {
                val again = when (step) {
                    is Step.ListenAndChoose -> step.copy(choices = step.choices.shuffled())
                    is Step.MatchCase -> step.copy(choices = step.choices.shuffled())
                    is Step.SeeAndSay -> step
                }
                steps.add(minOf(index + 1 + REQUEUE_GAP, steps.size), again)
            }
        }
        index++
    }

    companion object {
        const val REQUEUE_GAP = 2
    }
}
