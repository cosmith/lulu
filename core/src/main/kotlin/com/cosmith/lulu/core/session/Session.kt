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
import java.time.Instant
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
 * Builds a session: due cards first, then a new sound when the child is not already
 * busy learning several, then cards still in their short learning steps.
 */
class SessionPlanner(private val random: Random = Random.Default) {

    fun plan(profile: Profile, progress: Progress, now: Instant): List<Step> {
        val settings = profile.settings
        val size = settings.cardsPerSession.coerceAtLeast(1)
        val cards = progress.cards.values

        val due = cards.filter { !it.memory.due.isAfter(now) }.sortedBy { it.memory.due }
        val picked = due.take(size).toMutableList()

        val order = Curriculum.orderFor(profile.firstName)
        val learning = progress.soundsInLearning().size
        // The very first session meets two sounds, so that "écouter et choisir" has a real decoy.
        val wanted = settings.newSoundsPerSession + if (progress.introduced.isEmpty()) 1 else 0
        val room = minOf(wanted, settings.maxSoundsInLearning - learning, size - picked.size)
        val newSounds = if (room > 0) order.filter { it.id !in progress.introduced }.take(room) else emptyList()

        // Fill up with cards that are in their learning steps anyway: they are due within minutes.
        if (picked.size + 2 * newSounds.size < size) {
            val soon = cards
                .filter { it !in picked && it.memory.state != CardState.Review }
                .sortedBy { it.memory.due }
            picked += soon.take(size - picked.size - 2 * newSounds.size)
        }

        val known = progress.introducedSounds() + newSounds
        val reviewSteps = spreadOut(picked.shuffled(random)).map { card ->
            val sound = Curriculum[card.key.soundId]
            when (card.key.kind) {
                CardKind.SeeAndSay -> Step.SeeAndSay(sound)
                CardKind.ListenAndChoose -> Step.ListenAndChoose(sound, choicesFor(sound, card, known, order))
            }
        }

        // New sounds come after a short warm-up, each one met and then picked out by ear.
        val steps = reviewSteps.toMutableList()
        var insertAt = minOf(2, steps.size)
        for (sound in newSounds) {
            steps.add(insertAt, Step.Meet(sound))
            steps.add(insertAt + 1, Step.ListenAndChoose(sound, choicesFor(sound, null, known, order)))
            insertAt = minOf(insertAt + 4, steps.size)
        }
        return steps
    }

    /**
     * Two choices while a card is new, three while it is settling, four once it is solid.
     * Decoys are sounds the child has met; a solid card gets look-alikes first.
     */
    fun choicesFor(target: Sound, card: CardProgress?, known: List<Sound>, order: List<Sound>): List<Sound> {
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

    /** Avoids showing the same sound twice in a row when another order allows it. */
    private fun spreadOut(cards: List<CardProgress>): List<CardProgress> {
        val left = cards.toMutableList()
        val out = mutableListOf<CardProgress>()
        while (left.isNotEmpty()) {
            val previous = out.lastOrNull()?.key?.soundId
            val remaining = left.groupingBy { it.key.soundId }.eachCount()
            // Take the sound with the most cards left first, so none is stuck at the end.
            val next = left.filter { it.key.soundId != previous }
                .maxByOrNull { remaining.getValue(it.key.soundId) }
                ?: left.first()
            left.remove(next)
            out += next
        }
        return out
    }

    companion object {
        const val SOLID_DAYS = 7.0
    }
}

/** What the screen does after a verdict. */
sealed interface Outcome {
    /** Second chance: Lulu plays the sound, then the child tries again. */
    data object TryAgain : Outcome

    /** The card is done and gets this FSRS rating. [revealAnswer] asks the screen to show the right choice. */
    data class Done(val rating: Rating, val revealAnswer: Boolean = false) : Outcome
}

/**
 * "Voir et dire": first try right → Bien; right after hearing the sound → Difficile;
 * wrong twice → À revoir. Asking to hear the sound before answering counts as a hint.
 */
class SeeAndSayFlow {
    var attempts = 0
        private set
    private var heardSound = false

    fun onSoundPlayed() {
        heardSound = true
    }

    fun onVerdict(correct: Boolean): Outcome {
        attempts++
        return when {
            correct && attempts == 1 && !heardSound -> Outcome.Done(Rating.Good)
            correct -> Outcome.Done(Rating.Hard)
            attempts == 1 -> Outcome.TryAgain
            else -> Outcome.Done(Rating.Again)
        }
    }
}

/** "Écouter et choisir": right on the first tap → Bien; anything else → À revoir, with one more try. */
class ListenAndChooseFlow {
    var attempts = 0
        private set

    fun onChoice(correct: Boolean): Outcome {
        attempts++
        return when {
            correct && attempts == 1 -> Outcome.Done(Rating.Good)
            attempts == 1 -> Outcome.TryAgain
            else -> Outcome.Done(Rating.Again, revealAnswer = !correct)
        }
    }
}

/**
 * Walks through a planned session. A card rated "À revoir" comes back once,
 * a few screens later, so the child ends on a success with it.
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
