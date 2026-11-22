@file:UseSerializers(InstantMillisSerializer::class)

package com.cosmith.lulu.core.store

import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.content.Sound
import com.cosmith.lulu.core.fsrs.CardState
import com.cosmith.lulu.core.fsrs.FsrsScheduler
import com.cosmith.lulu.core.fsrs.Memory
import com.cosmith.lulu.core.fsrs.Rating
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import kotlin.random.Random

/**
 * The ways Lulu asks about a sound. Each one is its own FSRS card, like the sides of
 * an Anki note. Capitals come first; the lower-case cards open once the capitals are solid.
 */
enum class CardKind(val lowercase: Boolean) {
    /** "Voir et dire": a grapheme is shown, the child says its sound. */
    SeeAndSay(false),

    /** "Écouter et choisir": Lulu plays a sound, the child taps the matching grapheme. */
    ListenAndChoose(false),

    /** The capital is shown, the child taps its lower-case letter. */
    MatchCase(true),

    /** "Écouter et choisir" with lower-case letters. */
    ListenAndChooseLower(true),

    /** "Voir et dire" with the lower-case letter. */
    SeeAndSayLower(true);

    companion object {
        val capitals = entries.filter { !it.lowercase }
        val lowercase = entries.filter { it.lowercase }
    }
}

@Serializable
data class CardKey(val soundId: String, val kind: CardKind) {
    val id: String get() = "$soundId:${kind.name}"
}

/** Who decided whether the child was right. */
enum class Judge {
    /** The parent pressed ✓ or ✗ ("voir et dire" until the recognizer exists). */
    Parent,

    /** The child's own tap on a choice ("écouter et choisir"). */
    Tap,

    /** The on-device speech recognizer (next milestone). */
    Recognizer,
}

/**
 * The parent's only setting. Which cards, how many new sounds and how many choices
 * are decided by the session planner from the child's answers.
 */
@Serializable
data class LearningSettings(
    val sessionMinutes: Int = 5,
) {
    companion object {
        val MINUTES = 2..15
    }
}

/**
 * A child. They pick themselves by their mascot's colour, not by name, since they
 * cannot read yet; [firstName] is for the parent and for placing the initial early.
 */
@Serializable
data class Profile(
    val id: String,
    val firstName: String,
    val avatar: Int,
    val createdAt: Instant,
    val settings: LearningSettings = LearningSettings(),
    /** Draws the mascot's personality. Profiles from before it existed use their id instead. */
    val mascotSeed: Long? = null,
) {
    val mascot: Long get() = mascotSeed ?: id.hashCode().toLong()
}

@Serializable
data class CardProgress(
    val key: CardKey,
    val memory: Memory,
    val reviews: Int = 0,
    val lapses: Int = 0,
)

@Serializable
data class ReviewEntry(
    val key: CardKey,
    val at: Instant,
    val rating: Rating,
    val judge: Judge,
    /** True once the parent flipped the verdict in the parent area. */
    val corrected: Boolean = false,
)

/** Everything one child has done. Card memories are always the replay of [reviews]. */
@Serializable
data class Progress(
    /** Sound ids, in the order the child met them. */
    val introduced: List<String> = emptyList(),
    /** When each sound was met. Progress saved before this field existed falls back on the first review. */
    val introducedOn: Map<String, Instant> = emptyMap(),
    /** When the lower case of each sound was met. */
    val lowercaseOn: Map<String, Instant> = emptyMap(),
    val cards: Map<String, CardProgress> = emptyMap(),
    val reviews: List<ReviewEntry> = emptyList(),
) {
    fun card(key: CardKey): CardProgress? = cards[key.id]

    fun introducedSounds(): List<Sound> = introduced.mapNotNull { Curriculum.find(it) }

    fun introducedAt(soundId: String): Instant? =
        introducedOn[soundId] ?: reviews.firstOrNull { it.key.soundId == soundId }?.at

    /** A sound is being learned until both of its capital cards exist and have reached the Review state. */
    fun soundsInLearning(): List<String> = introduced.filter { id ->
        CardKind.capitals.any { kind -> card(CardKey(id, kind))?.memory?.state != CardState.Review }
    }

    /** Both capital cards are in Review and will hold for at least [days]: time for the lower case. */
    fun capitalsSolid(soundId: String, days: Double): Boolean = CardKind.capitals.all { kind ->
        val m = card(CardKey(soundId, kind))?.memory
        m != null && m.state == CardState.Review && (m.stability ?: 0.0) >= days
    }

    /** Sounds whose lower case the child has met, in that order. */
    fun lowercaseSounds(): List<Sound> = lowercaseOn.entries.sortedBy { it.value }.mapNotNull { Curriculum.find(it.key) }

    /**
     * Meets the lower case of a known sound: it starts with matching the capital to its
     * lower-case letter, and picking the lower-case letter out by ear.
     */
    fun meetLowercase(sound: Sound, at: Instant): Progress {
        if (sound.id in lowercaseOn) return this
        val new = listOf(CardKind.MatchCase, CardKind.ListenAndChooseLower).map { CardKey(sound.id, it) }
        return copy(
            lowercaseOn = lowercaseOn + (sound.id to at),
            cards = cards + new.associate { it.id to CardProgress(it, Memory(due = at)) },
        )
    }

    /** Meets a new sound: it starts with its "écouter et choisir" card, the easier one. */
    fun introduce(sound: Sound, at: Instant): Progress {
        if (sound.id in introduced) return this
        val key = CardKey(sound.id, CardKind.ListenAndChoose)
        return copy(
            introduced = introduced + sound.id,
            introducedOn = introducedOn + (sound.id to at),
            cards = cards + (key.id to CardProgress(key, Memory(due = at))),
        )
    }

    fun record(key: CardKey, rating: Rating, at: Instant, judge: Judge, scheduler: FsrsScheduler): Progress {
        val current = requireNotNull(card(key)) { "unknown card ${key.id}" }
        val updated = current.copy(
            memory = scheduler.review(current.memory, rating, at, fuzzFor(key, at)),
            reviews = current.reviews + 1,
            lapses = current.lapses + if (rating == Rating.Again && current.memory.state == CardState.Review) 1 else 0,
        )
        var next = copy(
            cards = cards + (key.id to updated),
            reviews = reviews + ReviewEntry(key, at, rating, judge),
        )
        // "Voir et dire" opens once the child has picked the sound out by ear, in capitals and in lower case.
        val opens = when (key.kind) {
            CardKind.ListenAndChoose -> CardKey(key.soundId, CardKind.SeeAndSay)
            CardKind.ListenAndChooseLower -> CardKey(key.soundId, CardKind.SeeAndSayLower)
            else -> null
        }
        if (opens != null && rating != Rating.Again && next.card(opens) == null) {
            next = next.copy(cards = next.cards + (opens.id to CardProgress(opens, Memory(due = at))))
        }
        return next
    }

    /**
     * The parent changes the verdict of a past review. The card's memory is rebuilt by
     * replaying all of its reviews, as py-fsrs's `reschedule_card` does.
     */
    fun correct(reviewIndex: Int, rating: Rating, scheduler: FsrsScheduler): Progress {
        val entry = reviews[reviewIndex]
        if (entry.rating == rating) return this
        val newReviews = reviews.toMutableList().apply {
            set(reviewIndex, entry.copy(rating = rating, corrected = true))
        }
        val replayed = replay(entry.key, newReviews, scheduler)
        return copy(reviews = newReviews, cards = cards + (entry.key.id to replayed))
    }

    private fun replay(key: CardKey, log: List<ReviewEntry>, scheduler: FsrsScheduler): CardProgress {
        val own = log.filter { it.key == key }.sortedBy { it.at }
        var card = CardProgress(key, Memory(due = own.firstOrNull()?.at ?: Instant.EPOCH))
        for (r in own) {
            card = card.copy(
                memory = scheduler.review(card.memory, r.rating, r.at, fuzzFor(key, r.at)),
                reviews = card.reviews + 1,
                lapses = card.lapses + if (r.rating == Rating.Again && card.memory.state == CardState.Review) 1 else 0,
            )
        }
        return card
    }

    companion object {
        /** Seeded so that replaying a card's reviews gives the same intervals. */
        fun fuzzFor(key: CardKey, at: Instant): Double =
            Random(key.id.hashCode().toLong() * 1_000_003L + at.toEpochMilli()).nextDouble()
    }
}

/**
 * The scheduler Lulu uses: FSRS-6 default parameters and steps (1 and 10 minutes,
 * 10 minutes to relearn). A failed card is due again within the hour, so it comes
 * back in the next session.
 */
fun luluScheduler(): FsrsScheduler = FsrsScheduler()
