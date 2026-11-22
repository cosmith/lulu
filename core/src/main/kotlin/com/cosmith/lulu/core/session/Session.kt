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
 * clock. The parent only sets how long a session lasts; everything else is decided here:
 *
 * 1. cards that are due, the most overdue first;
 * 2. a new sound, met then picked out by ear, but only after a warm-up of
 *    [WARM_UP] cards, at most [NEW_SOUNDS_PER_DAY] per day whatever the number of
 *    sessions (two on the very first day, so "écouter et choisir" has a real decoy),
 *    while fewer than [MAX_SOUNDS_IN_LEARNING] sounds are being learned, and not
 *    when the child just missed several cards;
 * 3. practice on sounds still being learned, even a little before they are due;
 * 4. early review of cards due within [EARLY_REVIEW_DAYS] days, once each.
 *
 * The same sound never comes twice in a row when something else can come, and a card
 * is shown at most [MAX_SHOWS_PER_CARD] times per session. A missed card is due again
 * a few minutes later (FSRS learning steps), so it comes back by itself.
 * [next] returns null when there is nothing useful left: the session ends early.
 */
class SessionPlanner(
    private val profile: Profile,
    private val random: Random = Random.Default,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val order = Curriculum.orderFor(profile.firstName)
    private val shown = mutableMapOf<String, Int>()
    private var cardsShown = 0
    private var lastSound: String? = null
    private var justMet: Sound? = null
    private var startedAt: Instant? = null

    fun next(progress: Progress, now: Instant): Step? {
        if (startedAt == null) startedAt = now
        val step = choose(progress, now) ?: return null
        lastSound = step.sound.id
        when (step) {
            is Step.Meet -> justMet = step.sound
            is Step.SeeAndSay -> count(step.key)
            is Step.ListenAndChoose -> count(step.key)
        }
        return step
    }

    private fun count(key: CardKey) {
        shown.merge(key.id, 1, Int::plus)
        cardsShown++
    }

    private fun choose(progress: Progress, now: Instant): Step? {
        // A sound just met is picked out by ear right away.
        justMet?.let { sound ->
            justMet = null
            val card = progress.card(CardKey(sound.id, CardKind.ListenAndChoose))
            return Step.ListenAndChoose(sound, choicesFor(sound, card, progress.introducedSounds() + sound))
        }
        val open = progress.cards.values.filter { (shown[it.key.id] ?: 0) < MAX_SHOWS_PER_CARD }
        val due = open.filter { !it.memory.due.isAfter(now) }.sortedBy { it.memory.due }
        val practice = open.filter { it.memory.state != CardState.Review && it.memory.due.isAfter(now) }
            .sortedBy { it.memory.due }
        val earlyLimit = now.plus(Duration.ofDays(EARLY_REVIEW_DAYS))
        val early = open.filter {
            it.memory.state == CardState.Review && it.memory.due.isAfter(now) &&
                it.memory.due.isBefore(earlyLimit) && it.key.id !in shown
        }.sortedBy { it.memory.due }
        val newSound = newSoundAllowed(progress, now)

        fun List<CardProgress>.other() = firstOrNull { it.key.soundId != lastSound }

        due.other()?.let { return stepFor(it, progress) }
        if (newSound != null && cardsShown >= WARM_UP) return Step.Meet(newSound)
        (practice.other() ?: early.other())?.let { return stepFor(it, progress) }
        // Nothing else to warm up with (the very first session): meet the sound now.
        if (newSound != null) return Step.Meet(newSound)
        return (due.firstOrNull() ?: practice.firstOrNull() ?: early.firstOrNull())?.let { stepFor(it, progress) }
    }

    private fun newSoundAllowed(progress: Progress, now: Instant): Sound? {
        val next = order.firstOrNull { it.id !in progress.introduced } ?: return null
        val startOfDay = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val today = progress.introduced.count { id -> progress.introducedAt(id)?.isBefore(startOfDay) == false }
        val everBefore = progress.introduced.size - today
        val perDay = if (everBefore == 0) FIRST_DAY_NEW_SOUNDS else NEW_SOUNDS_PER_DAY
        if (today >= perDay) return null
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
        const val NEW_SOUNDS_PER_DAY = 1
        const val FIRST_DAY_NEW_SOUNDS = 2
        const val WARM_UP = 3
        const val EARLY_REVIEW_DAYS = 2L
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
