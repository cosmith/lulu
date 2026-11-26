@file:UseSerializers(InstantMillisSerializer::class)

package com.cosmith.lulu.core.store

import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.content.Item
import com.cosmith.lulu.core.content.Word
import com.cosmith.lulu.core.content.Words
import com.cosmith.lulu.core.content.Sound
import com.cosmith.lulu.core.fsrs.CardState
import com.cosmith.lulu.core.fsrs.FsrsScheduler
import com.cosmith.lulu.core.fsrs.Memory
import com.cosmith.lulu.core.fsrs.Rating
import kotlinx.serialization.SerialName
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

/**
 * One card: an item (a sound, a syllable or a word) and a way of asking about it.
 * Syllables and words use only [CardKind.SeeAndSay] and [CardKind.ListenAndChoose].
 */
@Serializable
data class CardKey(
    // Stored as "soundId": progress saved when only sounds existed keeps loading.
    @SerialName("soundId") val itemId: String,
    val kind: CardKind,
) {
    val id: String get() = "$itemId:${kind.name}"
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
    /** When each syllable and word was met. */
    val itemsOn: Map<String, Instant> = emptyMap(),
    val cards: Map<String, CardProgress> = emptyMap(),
    val reviews: List<ReviewEntry> = emptyList(),
) {
    fun card(key: CardKey): CardProgress? = cards[key.id]

    fun introducedSounds(): List<Sound> = introduced.mapNotNull { Curriculum.find(it) }

    fun introducedAt(soundId: String): Instant? =
        introducedOn[soundId] ?: reviews.firstOrNull { it.key.itemId == soundId }?.at

    /**
     * A card has graduated once it has left its first learning steps. A card missed later
     * goes to Relearning but stays graduated: the child knew it and is reviewing it.
     */
    fun graduated(key: CardKey): Boolean = card(key)?.memory?.state.let { it != null && it != CardState.Learning }

    /** A sound is being learned until both of its capital cards exist and have graduated. */
    fun soundsInLearning(): List<String> = introduced.filter { id ->
        CardKind.capitals.any { kind -> !graduated(CardKey(id, kind)) }
    }

    /** Both capital cards are in Review and will hold for at least [days]: time for the lower case. */
    fun capitalsSolid(soundId: String, days: Double): Boolean =
        solid(CardKey(soundId, CardKind.SeeAndSay), days) && graduated(CardKey(soundId, CardKind.ListenAndChoose))

    private fun solid(key: CardKey, days: Double): Boolean =
        card(key)?.memory?.let { it.state == CardState.Review && (it.stability ?: 0.0) >= days } == true

    /**
     * "Écouter et choisir" and "majuscule → minuscule" are stepping stones towards reading
     * aloud. Once the item's "voir et dire" card is solid, they are not asked any more,
     * which keeps the daily reviews within a short session. If "voir et dire" is
     * forgotten later, they come back as support.
     */
    fun retired(key: CardKey): Boolean {
        val main = when (key.kind) {
            CardKind.ListenAndChoose -> CardKind.SeeAndSay
            CardKind.ListenAndChooseLower, CardKind.MatchCase -> CardKind.SeeAndSayLower
            else -> return false
        }
        return solid(CardKey(key.itemId, main), SCAFFOLD_RETIRES_AFTER_DAYS)
    }

    /** The child knows a sound once both of its capital cards have graduated. */
    fun knows(soundId: String): Boolean {
        Curriculum.prerequisites[soundId]?.let { needs -> return needs.all { knows(it) } }
        return soundId in introduced && known(soundId)
    }

    /**
     * Both cards of an item have graduated, or its "voir et dire" has and its stepping
     * stone was retired before it could (a word read so well that its listening card
     * was never needed).
     */
    fun known(itemId: String): Boolean {
        val listen = CardKey(itemId, CardKind.ListenAndChoose)
        return graduated(CardKey(itemId, CardKind.SeeAndSay)) && (graduated(listen) || retired(listen))
    }

    /** Syllables or words (by id prefix) met but not yet known. */
    fun itemsInLearning(prefix: String): Int = itemsOn.keys.count { it.startsWith(prefix) && !known(it) }

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

    /**
     * Meets a syllable or a word. A syllable starts like a sound, picked out by ear;
     * a word starts with reading it, so that the child decodes it before hearing it.
     */
    fun meet(item: Item, at: Instant): Progress {
        if (item is Sound) return introduce(item, at)
        if (item.id in itemsOn) return this
        val key = CardKey(item.id, if (item is Word) CardKind.SeeAndSay else CardKind.ListenAndChoose)
        return copy(
            itemsOn = itemsOn + (item.id to at),
            cards = cards + (key.id to CardProgress(key, Memory(due = at))),
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
        // A card that does not exist yet starts fresh rather than failing in the middle of a session.
        val current = card(key) ?: CardProgress(key, Memory(due = at))
        val updated = current.copy(
            memory = scheduler.review(current.memory, rating, at, fuzzFor(key, at)),
            reviews = current.reviews + 1,
            lapses = current.lapses + if (rating == Rating.Again && current.memory.state == CardState.Review) 1 else 0,
        )
        var next = copy(
            cards = cards + (key.id to updated),
            reviews = reviews + ReviewEntry(key, at, rating, judge),
        )
        // The second card opens once the first has left its learning steps (two successes):
        // "voir et dire" once the child picks a sound or syllable out by ear reliably
        // (capitals and lower case), "écouter et choisir" once a word is read reliably.
        // Recognising comes before saying.
        val isWord = key.itemId.startsWith(Words.PREFIX)
        val opens = when (key.kind) {
            CardKind.ListenAndChoose -> if (isWord) null else CardKey(key.itemId, CardKind.SeeAndSay)
            CardKind.SeeAndSay -> if (isWord) CardKey(key.itemId, CardKind.ListenAndChoose) else null
            CardKind.ListenAndChooseLower -> CardKey(key.itemId, CardKind.SeeAndSayLower)
            else -> null
        }
        if (opens != null && updated.memory.state != CardState.Learning && next.card(opens) == null) {
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
 * 10 minutes to relearn), aiming at 95 % recall rather than Anki's 90 %. The default
 * parameters come from adults; the first weeks of a 3-year-old recalled less than
 * they predicted (79 % against 87 %), so cards come back sooner. A failed card is
 * due again within the hour, so it comes back in the next session.
 */
fun luluScheduler(): FsrsScheduler = FsrsScheduler(desiredRetention = DESIRED_RETENTION)

const val DESIRED_RETENTION = 0.95

/** Stability (days) of "voir et dire" from which its stepping-stone cards retire. */
const val SCAFFOLD_RETIRES_AFTER_DAYS = 7.0
