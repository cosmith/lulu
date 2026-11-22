package com.cosmith.lulu.core.session

import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.content.Sound
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

/** One screen of a session. */
sealed interface Step {
    val sound: Sound

    /** First meeting with a sound: Lulu shows it big and says it. Not graded. */
    data class Meet(override val sound: Sound) : Step

    data class SeeAndSay(override val sound: Sound) : Step {
        val key get() = CardKey(sound.id, CardKind.SeeAndSay)
    }

    data class ListenAndChoose(override val sound: Sound, val choices: List<Sound>) : Step {
        val key get() = CardKey(sound.id, CardKind.ListenAndChoose)
    }
}

/**
 * Chooses the cards of one session, one at a time, from the child's progress and the
 * clock, until the session's time is up. The parent only sets how long a session
 * lasts; everything else is decided here:
 *
 * 1. cards that are due, the most overdue first;
 * 2. a new sound, shown and said by Lulu: after a warm-up of [WARM_UP] cards, at
 *    least [NEW_SOUND_SPACING] cards after the previous one, at most
 *    [NEW_SOUNDS_PER_DAY] a day whatever the number of sessions, while fewer than
 *    [MAX_SOUNDS_IN_LEARNING] sounds are being learned, not when the child just
 *    missed several cards, and not in the last [NO_NEW_SOUND_AT_THE_END] of the session;
 * 3. practice on sounds still being learned, the one seen longest ago first;
 * 4. early review of cards that are not due yet, once each;
 * 5. when all of that is used up, whatever was seen longest ago, so the session
 *    lasts its full time.
 *
 * A card never seen waits for [NEW_CARD_GAP] other cards: a sound just met is not
 * asked about right away, when it would only be an echo. The same sound never
 * comes twice in a row when something else can come. A missed card is due again a
 * few minutes later (FSRS learning steps), so it comes back by itself.
 * [next] returns null only when there is no card at all and no new sound to meet.
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
    private var lastSound: String? = null
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
        lastSound = step.sound.id
        when (step) {
            is Step.Meet -> lastMeetAt = cardsShown
            is Step.SeeAndSay -> count(step.key)
            is Step.ListenAndChoose -> count(step.key)
        }
        return step
    }

    private fun count(key: CardKey) {
        shown.merge(key.id, 1, Int::plus)
        lastShown[key.id] = cardsShown
        cardsShown++
    }

    private fun choose(progress: Progress, now: Instant): Step? {
        val all = progress.cards.values
        val held = all.filter { (heldUntil[it.key.id] ?: 0) > cardsShown }.map { it.key.id }.toSet()
        val open = all.filter { it.key.id !in held && (shown[it.key.id] ?: 0) < MAX_SHOWS_PER_CARD }
        val seenLongestAgo = compareBy<CardProgress>({ lastShown[it.key.id] ?: -1 }, { it.memory.due })
        val due = open.filter { !it.memory.due.isAfter(now) }.sortedBy { it.memory.due }
        val practice = open.filter { it.memory.state != CardState.Review && it.memory.due.isAfter(now) }
            .sortedWith(seenLongestAgo)
        val early = open.filter { it.memory.state == CardState.Review && it.memory.due.isAfter(now) && it.key.id !in shown }
            .sortedBy { it.memory.due }
        val newSound = newSoundAllowed(progress, now)

        fun List<CardProgress>.other() = firstOrNull { it.key.soundId != lastSound }

        due.other()?.let { return stepFor(it, progress) }
        if (newSound != null && cardsShown >= WARM_UP && cardsShown - lastMeetAt >= NEW_SOUND_SPACING) {
            return Step.Meet(newSound)
        }
        (practice.other() ?: early.other())?.let { return stepFor(it, progress) }
        // Nothing else to show yet (the very first screens): meet a sound, but not more
        // than two in a row before asking about them.
        if (newSound != null && held.size < 2) return Step.Meet(newSound)
        // Cards still waiting after a first meeting come last.
        val any = all.sortedWith(compareBy<CardProgress> { it.key.id in held }.then(seenLongestAgo))
        return (any.other() ?: any.firstOrNull())?.let { stepFor(it, progress) }
    }

    private fun newSoundAllowed(progress: Progress, now: Instant): Sound? {
        val next = order.firstOrNull { it.id !in progress.introduced } ?: return null
        // Too late in the session to meet a sound and then be asked about it.
        if (timeLeft?.let { it < NO_NEW_SOUND_AT_THE_END } == true) return null
        val startOfDay = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val today = progress.introduced.count { id -> progress.introducedAt(id)?.isBefore(startOfDay) == false }
        if (today >= NEW_SOUNDS_PER_DAY) return null
        if (progress.soundsInLearning().size >= MAX_SOUNDS_IN_LEARNING) return null
        val recent = progress.reviews.filter { r -> startedAt?.let { !r.at.isBefore(it) } == true }.takeLast(4)
        if (recent.count { it.rating == Rating.Again } >= 2) return null
        return next
    }

    private fun stepFor(card: CardProgress, progress: Progress): Step {
        val sound = Curriculum[card.key.soundId]
        return when (card.key.kind) {
            CardKind.SeeAndSay -> Step.SeeAndSay(sound)
            CardKind.ListenAndChoose -> Step.ListenAndChoose(sound, choicesFor(sound, card, progress.introducedSounds()))
        }
    }

    /**
     * Two choices while a card is new, three while it is settling, four once it is solid.
     * Decoys are sounds the child has met; a solid card gets look-alikes first.
     */
    fun choicesFor(target: Sound, card: CardProgress?, known: List<Sound>): List<Sound> {
        val stability = card?.memory?.stability ?: 0.0
        val solid = card != null && card.memory.state == CardState.Review && stability >= SOLID_DAYS
        val count = when {
            card == null || card.reviews < 2 -> 2
            !solid -> 3
            else -> 4
        }
        val others = known.filter { it.id != target.id }.distinctBy { it.id }
        val lookalikes = if (solid) others.filter { it.id in target.lookalikes }.shuffled(random) else emptyList()
        val rest = (others - lookalikes.toSet()).shuffled(random)
        // At the very start there may not be enough known sounds: borrow the next ones to come.
        val upcoming = order.filter { s -> s.id != target.id && others.none { it.id == s.id } }
        val decoys = (lookalikes + rest + upcoming).take(count - 1)
        return (decoys + target).shuffled(random)
    }

    companion object {
        /** A card this stable (in days) is solid: four choices, look-alike decoys. */
        const val SOLID_DAYS = 7.0
        const val MAX_SOUNDS_IN_LEARNING = 3
        const val MAX_SHOWS_PER_CARD = 3
        const val NEW_SOUNDS_PER_DAY = 3
        const val NEW_SOUND_SPACING = 4
        const val NEW_CARD_GAP = 3
        const val WARM_UP = 3
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

    /** "Écouter et choisir": right → Bien; wrong → À revoir. */
    fun listenAndChoose(correct: Boolean): Rating = if (correct) Rating.Good else Rating.Again
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
        if (rating == Rating.Again && step !is Step.Meet) {
            val id = when (step) {
                is Step.SeeAndSay -> step.key.id
                is Step.ListenAndChoose -> step.key.id
                is Step.Meet -> error("unreachable")
            }
            if (requeued.add(id)) {
                val again = if (step is Step.ListenAndChoose) step.copy(choices = step.choices.shuffled()) else step
                steps.add(minOf(index + 1 + REQUEUE_GAP, steps.size), again)
            }
        }
        index++
    }

    companion object {
        const val REQUEUE_GAP = 2
    }
}
