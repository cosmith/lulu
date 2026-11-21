package com.cosmith.lulu.core.content

import java.text.Normalizer
import java.util.Locale

/** Families of sounds. Each one gets its own colour in the app. */
enum class SoundFamily {
    /** Level 1: a, é, i, o, u, eu, ou. */
    Vowel,

    /** Level 2: consonants you can hold: f, ch, j, v, s, l, r. */
    Continuant,

    /** Level 3: on, an, in, un. */
    NasalVowel,

    /** Level 4: short consonants and nasal consonants: p, b, t, d, qu, m, n, gn. */
    StopOrNasal,
}

/**
 * One grapheme-phoneme correspondence, like "ou" → /u/.
 *
 * @param id stable identifier, stored in profiles; never rename one.
 * @param grapheme how it is written, in lower case.
 * @param ipa the phoneme, for the recognizer and for parents.
 * @param ttsText what to give a French text-to-speech engine when no recording exists.
 * It is only a fallback: consonants cannot be said alone by a TTS engine, so for them
 * it gives the sound followed by a short vowel.
 * @param lookalikes ids of sounds that are easy to confuse with this one, used as
 * decoys once the child knows the sound well.
 */
data class Sound(
    val id: String,
    val grapheme: String,
    val ipa: String,
    val family: SoundFamily,
    val level: Int,
    val ttsText: String,
    val lookalikes: List<String> = emptyList(),
) {
    /** Sounds are shown in capitals first. */
    val capitals: String get() = grapheme.uppercase(Locale.FRENCH)
}

/**
 * Levels 1 to 4 of the progression in the spec, which follows the CSEN note of
 * September 2025: the most regular and most frequent correspondences first, and
 * two-letter sounds like "ou" and "ch" early. Syllables (level 6) and words
 * (level 7) come in a later milestone.
 */
object Curriculum {
    val sounds: List<Sound> = listOf(
        // 1. Voyelles
        Sound("a", "a", "a", SoundFamily.Vowel, 1, "a"),
        // A lone "é" is spelled out by TTS engines ("e accent aigu"); the word "et" says /e/.
        Sound("e_acute", "é", "e", SoundFamily.Vowel, 1, "et"),
        Sound("i", "i", "i", SoundFamily.Vowel, 1, "i"),
        Sound("o", "o", "o", SoundFamily.Vowel, 1, "o", lookalikes = listOf("ou")),
        Sound("u", "u", "y", SoundFamily.Vowel, 1, "u", lookalikes = listOf("ou")),
        Sound("eu", "eu", "ø", SoundFamily.Vowel, 1, "eux", lookalikes = listOf("ou", "u")),
        Sound("ou", "ou", "u", SoundFamily.Vowel, 1, "ou", lookalikes = listOf("o", "u", "on")),
        // 2. Consonnes qu'on peut tenir
        Sound("f", "f", "f", SoundFamily.Continuant, 2, "feu", lookalikes = listOf("v")),
        Sound("ch", "ch", "ʃ", SoundFamily.Continuant, 2, "cheu", lookalikes = listOf("j", "s")),
        Sound("j", "j", "ʒ", SoundFamily.Continuant, 2, "jeu", lookalikes = listOf("ch", "i")),
        Sound("v", "v", "v", SoundFamily.Continuant, 2, "veu", lookalikes = listOf("f", "u")),
        Sound("s", "s", "s", SoundFamily.Continuant, 2, "ceux", lookalikes = listOf("ch")),
        Sound("l", "l", "l", SoundFamily.Continuant, 2, "leu", lookalikes = listOf("i")),
        Sound("r", "r", "ʁ", SoundFamily.Continuant, 2, "reu"),
        // 3. Voyelles nasales ("en" waits: it reads "an" or "in")
        Sound("on", "on", "ɔ̃", SoundFamily.NasalVowel, 3, "on", lookalikes = listOf("an", "ou")),
        Sound("an", "an", "ɑ̃", SoundFamily.NasalVowel, 3, "an", lookalikes = listOf("on", "in")),
        Sound("in", "in", "ɛ̃", SoundFamily.NasalVowel, 3, "hein", lookalikes = listOf("un", "an")),
        Sound("un", "un", "œ̃", SoundFamily.NasalVowel, 3, "un", lookalikes = listOf("in", "on")),
        // 4. Consonnes courtes et nasales
        Sound("p", "p", "p", SoundFamily.StopOrNasal, 4, "peu", lookalikes = listOf("b", "t")),
        Sound("b", "b", "b", SoundFamily.StopOrNasal, 4, "beu", lookalikes = listOf("p", "d")),
        Sound("t", "t", "t", SoundFamily.StopOrNasal, 4, "teu", lookalikes = listOf("d", "p")),
        Sound("d", "d", "d", SoundFamily.StopOrNasal, 4, "deux", lookalikes = listOf("t", "b")),
        Sound("qu", "qu", "k", SoundFamily.StopOrNasal, 4, "queue", lookalikes = listOf("ou")),
        Sound("m", "m", "m", SoundFamily.StopOrNasal, 4, "meu", lookalikes = listOf("n")),
        Sound("n", "n", "n", SoundFamily.StopOrNasal, 4, "neu", lookalikes = listOf("m", "gn")),
        Sound("gn", "gn", "ɲ", SoundFamily.StopOrNasal, 4, "gneu", lookalikes = listOf("n")),
    )

    private val byId = sounds.associateBy { it.id }

    operator fun get(id: String): Sound = byId.getValue(id)

    fun find(id: String): Sound? = byId[id]

    /**
     * The order in which a child meets the sounds. The sound of the first letter of
     * their first name, when it is one of ours, moves up to third place: the spec
     * places the initial early, without expecting a miracle from it.
     */
    fun orderFor(firstName: String?): List<Sound> {
        val initial = initialSoundOf(firstName) ?: return sounds
        val index = sounds.indexOf(initial)
        if (index <= INITIAL_SLOT) return sounds
        return sounds.toMutableList().apply {
            removeAt(index)
            add(INITIAL_SLOT, initial)
        }
    }

    /**
     * "Élodie" → é, "Charlotte" → ch, "Quentin" → qu, "Oumar" → ou, "Antoine" → an, "Lucas" → l.
     * Letters that do not make the card's sound fall through: "Anna" and "Inès" start
     * with a, i (the n belongs to the next syllable), "Chloé" starts with a /k/ we do not teach.
     */
    fun initialSoundOf(firstName: String?): Sound? {
        val name = firstName?.trim()?.lowercase(Locale.FRENCH)?.takeIf { it.isNotEmpty() } ?: return null
        val plain = stripAccents(name)
        val twoLetters = plain.take(2)
        val third = plain.getOrNull(2)
        val digraph = sounds.firstOrNull { it.grapheme.length == 2 && it.grapheme == twoLetters }
        val digraphHolds = when (digraph?.family) {
            null -> false
            SoundFamily.NasalVowel -> third == null || third !in "aeiouyhnm"
            else -> !(digraph.id == "ch" && third != null && third in "rl")
        }
        if (digraph != null && digraphHolds) return digraph
        // é keeps its accent; other accented first letters (È, Ê) have no card of their own.
        if (name.first() == 'é') return get("e_acute")
        return sounds.firstOrNull { it.grapheme.length == 1 && it.grapheme == name.take(1) }
    }

    private fun stripAccents(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    private const val INITIAL_SLOT = 2
}
