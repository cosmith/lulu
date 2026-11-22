package com.cosmith.lulu.core.content

import java.text.Normalizer
import java.util.Locale

/** Families of sounds. Each one gets its own colour in the app. */
enum class SoundFamily {
    /** Oral vowels: a, é, i, o, u, eu, ou (level 1), then e, è, oi, au, eau, ai. */
    Vowel,

    /** Consonants you can hold: f, ch, j, v, s, l, r (level 2), then z. */
    Continuant,

    /** Nasal vowels: on, an, in, un (level 3), then en, ain. */
    NasalVowel,

    /** Short consonants and nasal consonants: p, b, t, d, qu, m, n, gn (level 4), then c, g. */
    StopOrNasal;

    val isVowel: Boolean get() = this == Vowel || this == NasalVowel
}

/**
 * Something a child reads: a sound, a syllable or a word. Its id is stored in
 * progress files: sounds use their own id ("ou"), syllables "syl:…", words "word:…".
 */
sealed interface Item {
    val id: String

    /** What is written, in order: sounds, and silent letters shown in grey. */
    val parts: List<Part>

    /** What text-to-speech says for the whole item. */
    val spoken: String

    fun written(lowercase: Boolean): String = parts.joinToString("") { it.written(lowercase) }

    /** The sounds read aloud, without the silent letters. */
    val sounds: List<Sound> get() = parts.mapNotNull { it.sound }
}

/** One part of an item: a sound, or letters that are not pronounced (the t of "lit"). */
data class Part(val sound: Sound?, val silent: String? = null) {
    init {
        require((sound == null) != (silent == null)) { "a part is a sound or silent letters" }
    }

    fun written(lowercase: Boolean): String =
        sound?.written(lowercase) ?: silent!!.let { if (lowercase) it else it.uppercase(Locale.FRENCH) }
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
 * @param lowerLookalikes ids of sounds whose lower-case letters look like this one's
 * (b and d, n and u…), used as decoys for solid lower-case cards.
 * @param voice the sound whose recording to play when this one has none of its own
 * ("au" and "eau" sound like "o"): the parent records each sound only once.
 * @param alone false for the contextual sounds (soft c and g), which only appear in
 * syllables and words, never on a card of their own.
 */
data class Sound(
    override val id: String,
    val grapheme: String,
    val ipa: String,
    val family: SoundFamily,
    val level: Int,
    val ttsText: String,
    val lookalikes: List<String> = emptyList(),
    val lowerLookalikes: List<String> = emptyList(),
    val voice: String? = null,
    val alone: Boolean = true,
) : Item {
    override val parts: List<Part> get() = listOf(Part(this))
    override val spoken: String get() = ttsText

    /** Sounds are shown in capitals first. */
    val capitals: String get() = grapheme.uppercase(Locale.FRENCH)

    /** The lower-case letters, which come once the capitals are solid. */
    val lowercase: String get() = grapheme

    override fun written(lowercase: Boolean) = if (lowercase) this.lowercase else capitals
}

/**
 * The sounds of the progression in the spec. Levels 1 to 4 follow the CSEN note of
 * September 2025: the most regular and most frequent correspondences first, and
 * two-letter sounds like "ou" and "ch" early. Level 5 follows the rest of the
 * Sprenger-Charolles and Dehaene progression (Éduscol, 2017): e, z, hard c and g,
 * oi, au and eau, è, ai, en, ain; soft c and g come through syllables and words.
 * Syllables (level 6) and words (level 7) are in [Syllables] and [Words].
 */
object Curriculum {
    val sounds: List<Sound> = listOf(
        // 1. Voyelles
        Sound("a", "a", "a", SoundFamily.Vowel, 1, "a"),
        // A lone "é" is spelled out by TTS engines ("e accent aigu"); the word "et" says /e/.
        Sound("e_acute", "é", "e", SoundFamily.Vowel, 1, "et"),
        Sound("i", "i", "i", SoundFamily.Vowel, 1, "i", lowerLookalikes = listOf("j", "l")),
        Sound("o", "o", "o", SoundFamily.Vowel, 1, "o", lookalikes = listOf("ou")),
        Sound("u", "u", "y", SoundFamily.Vowel, 1, "u", lookalikes = listOf("ou"), lowerLookalikes = listOf("n")),
        Sound("eu", "eu", "ø", SoundFamily.Vowel, 1, "eux", lookalikes = listOf("ou", "u")),
        Sound("ou", "ou", "u", SoundFamily.Vowel, 1, "ou", lookalikes = listOf("o", "u", "on")),
        // 2. Consonnes qu'on peut tenir
        Sound("f", "f", "f", SoundFamily.Continuant, 2, "feu", lookalikes = listOf("v"), lowerLookalikes = listOf("t", "l")),
        Sound("ch", "ch", "ʃ", SoundFamily.Continuant, 2, "cheu", lookalikes = listOf("j", "s")),
        Sound("j", "j", "ʒ", SoundFamily.Continuant, 2, "jeu", lookalikes = listOf("ch", "i"), lowerLookalikes = listOf("i")),
        Sound("v", "v", "v", SoundFamily.Continuant, 2, "veu", lookalikes = listOf("f", "u")),
        Sound("s", "s", "s", SoundFamily.Continuant, 2, "ceux", lookalikes = listOf("ch")),
        Sound("l", "l", "l", SoundFamily.Continuant, 2, "leu", lookalikes = listOf("i"), lowerLookalikes = listOf("i", "t")),
        Sound("r", "r", "ʁ", SoundFamily.Continuant, 2, "reu", lowerLookalikes = listOf("n")),
        // 3. Voyelles nasales ("en" waits: it reads "an" or "in")
        Sound("on", "on", "ɔ̃", SoundFamily.NasalVowel, 3, "on", lookalikes = listOf("an", "ou"), lowerLookalikes = listOf("an", "un")),
        Sound("an", "an", "ɑ̃", SoundFamily.NasalVowel, 3, "an", lookalikes = listOf("on", "in"), lowerLookalikes = listOf("on", "un")),
        Sound("in", "in", "ɛ̃", SoundFamily.NasalVowel, 3, "hein", lookalikes = listOf("un", "an")),
        Sound("un", "un", "œ̃", SoundFamily.NasalVowel, 3, "un", lookalikes = listOf("in", "on"), lowerLookalikes = listOf("an", "on")),
        // 4. Consonnes courtes et nasales
        Sound("p", "p", "p", SoundFamily.StopOrNasal, 4, "peu", lookalikes = listOf("b", "t"), lowerLookalikes = listOf("b", "d", "qu")),
        Sound("b", "b", "b", SoundFamily.StopOrNasal, 4, "beu", lookalikes = listOf("p", "d"), lowerLookalikes = listOf("d", "p")),
        Sound("t", "t", "t", SoundFamily.StopOrNasal, 4, "teu", lookalikes = listOf("d", "p"), lowerLookalikes = listOf("f", "l")),
        Sound("d", "d", "d", SoundFamily.StopOrNasal, 4, "deux", lookalikes = listOf("t", "b"), lowerLookalikes = listOf("b", "p")),
        Sound("qu", "qu", "k", SoundFamily.StopOrNasal, 4, "queue", lookalikes = listOf("ou"), lowerLookalikes = listOf("p", "gn")),
        Sound("m", "m", "m", SoundFamily.StopOrNasal, 4, "meu", lookalikes = listOf("n"), lowerLookalikes = listOf("n")),
        Sound("n", "n", "n", SoundFamily.StopOrNasal, 4, "neu", lookalikes = listOf("m", "gn"), lowerLookalikes = listOf("u", "m", "r")),
        Sound("gn", "gn", "ɲ", SoundFamily.StopOrNasal, 4, "gneu", lookalikes = listOf("n"), lowerLookalikes = listOf("qu")),
        // 5. Suite de la progression (Sprenger-Charolles et Dehaene)
        Sound("e", "e", "ə", SoundFamily.Vowel, 5, "e", lookalikes = listOf("eu", "e_acute")),
        Sound("z", "z", "z", SoundFamily.Continuant, 5, "zeu", lookalikes = listOf("s", "j")),
        Sound("c", "c", "k", SoundFamily.StopOrNasal, 5, "queue", lookalikes = listOf("qu", "g"), voice = "qu"),
        Sound("g", "g", "g", SoundFamily.StopOrNasal, 5, "gueu", lookalikes = listOf("c", "qu"), lowerLookalikes = listOf("qu")),
        Sound("oi", "oi", "wa", SoundFamily.Vowel, 5, "oie", lookalikes = listOf("ou", "o")),
        Sound("au", "au", "o", SoundFamily.Vowel, 5, "au", lookalikes = listOf("eau", "ou", "o"), voice = "o"),
        Sound("eau", "eau", "o", SoundFamily.Vowel, 5, "eau", lookalikes = listOf("au", "eu"), voice = "o"),
        // "est" says /ɛ/; a lone "è" would be spelled out like "é".
        Sound("e_grave", "è", "ɛ", SoundFamily.Vowel, 5, "est", lookalikes = listOf("e_acute", "e", "ai")),
        Sound("ai", "ai", "ɛ", SoundFamily.Vowel, 5, "est", lookalikes = listOf("e_grave", "oi"), voice = "e_grave"),
        Sound("en", "en", "ɑ̃", SoundFamily.NasalVowel, 5, "en", lookalikes = listOf("an", "on", "un"), lowerLookalikes = listOf("an", "on"), voice = "an"),
        Sound("ain", "ain", "ɛ̃", SoundFamily.NasalVowel, 5, "hein", lookalikes = listOf("in", "an", "ai"), voice = "in"),
    )

    /**
     * c and g before e, é, è, i: soft, like s and j. They have no card of their own;
     * the child meets them in syllables (CE, GI) and words (citron, girafe) once hard
     * c and g, and s and j, are known.
     */
    val contextual: List<Sound> = listOf(
        Sound("c_soft", "c", "s", SoundFamily.Continuant, 5, "ceux", voice = "s", alone = false),
        Sound("g_soft", "g", "ʒ", SoundFamily.Continuant, 5, "jeu", voice = "j", alone = false),
    )

    /** What a contextual sound needs the child to know first. */
    val prerequisites: Map<String, List<String>> = mapOf("c_soft" to listOf("c", "s"), "g_soft" to listOf("g", "j"))

    private val byId = (sounds + contextual).associateBy { it.id }

    operator fun get(id: String): Sound = byId.getValue(id)

    fun find(id: String): Sound? = byId[id]

    /** Any item by its id: a sound, a syllable or a word. */
    fun item(id: String): Item? = when {
        id.startsWith(Syllables.PREFIX) -> Syllables.find(id)
        id.startsWith(Words.PREFIX) -> Words.find(id)
        else -> find(id)
    }

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
        // "Aïcha": the diaeresis splits the letters.
        if (name.getOrNull(1) in setOf('ï', 'ë', 'ü')) return sounds.firstOrNull { it.grapheme == plain.take(1) && it.id != "e" }
        val digraph = sounds.firstOrNull { it.grapheme.length == 2 && it.grapheme == twoLetters }
        val digraphHolds = when (digraph?.family) {
            null -> false
            SoundFamily.NasalVowel -> third == null || third !in "aeiouyhnm"
            else -> !(digraph.id == "ch" && third != null && third in "rl")
        }
        if (digraph != null && digraphHolds) return digraph
        // é keeps its accent; other accented first letters (È, Ê) have no card of their own.
        if (name.first() == 'é') return get("e_acute")
        // "Céline", "Gilles": a soft c or g, which has no card of its own.
        if (plain.length > 1 && plain.first() in "cg" && plain[1] in "eiy") return null
        // A plain "e" at the start of a name is rarely the sound of « le » (Emma, Eva).
        return sounds.firstOrNull { it.grapheme.length == 1 && it.grapheme == name.take(1) && it.id != "e" }
    }

    private fun stripAccents(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    private const val INITIAL_SLOT = 2
}
