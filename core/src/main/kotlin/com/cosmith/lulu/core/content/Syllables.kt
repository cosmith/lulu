package com.cosmith.lulu.core.content

/**
 * Syllable shapes, in the order of the progression: consonant-vowel (LA, BU),
 * vowel-consonant (AL, OUF: the inverse pairs, to show that order matters),
 * consonant-vowel-consonant (POUL, FOUR), then two consonants first (TRI, FLA).
 */
enum class SyllableShape { CV, VC, CVC, CCV }

/**
 * A syllable to read, made of sounds the child knows. Many are invented on purpose:
 * POUL is not a word, so the child has to decode it rather than recognise it.
 */
data class Syllable(override val sounds: List<Sound>, val shape: SyllableShape) : Item {
    override val id: String get() = Syllables.PREFIX + sounds.joinToString("-") { it.id }
    override val parts: List<Part> get() = sounds.map { Part(it) }

    /** Text-to-speech reads short syllables well when given them in lower case. */
    override val spoken: String get() = sounds.joinToString("") { it.grapheme }

    /** The same sounds the other way round: FOU and OUF. */
    val inverse: Syllable? get() = when (shape) {
        SyllableShape.CV -> Syllables.find(Syllables.PREFIX + sounds.reversed().joinToString("-") { it.id })
        SyllableShape.VC -> Syllables.find(Syllables.PREFIX + sounds.reversed().joinToString("-") { it.id })
        else -> null
    }
}

/**
 * Every syllable Lulu can ask, built from the sounds of the progression with the
 * spelling rules a beginner can rely on:
 * - hard c and g only before a, o, u, ou, on, an, oi, au (CA, GOU), and soft c and g
 *   only before e, é, è, i (CE, GI);
 * - qu not before u, ou, au;
 * - closing consonants are the ones that are always heard at the end: l, r, f;
 * - two-consonant starts are the French ones: pl, bl, fl, cl, gl, pr, br, tr, dr, fr, vr, cr, gr.
 */
object Syllables {
    const val PREFIX = "syl:"

    private val openVowels = listOf("a", "e_acute", "i", "o", "u", "eu", "ou", "on", "an", "in", "e", "e_grave", "oi", "au", "ai")
    private val closedVowels = listOf("a", "i", "o", "ou", "u", "eu")
    private val finals = listOf("l", "r", "f")
    private val clusterVowels = listOf("a", "e_acute", "i", "o", "u", "eu", "ou", "on", "an", "in", "oi")
    private val clusters = listOf(
        "p" to "l", "b" to "l", "f" to "l", "c" to "l", "g" to "l",
        "p" to "r", "b" to "r", "t" to "r", "d" to "r", "f" to "r", "v" to "r", "c" to "r", "g" to "r",
    )

    /** Consonants that can start a syllable: every consonant of the progression but gn, plus soft c and g. */
    private val onsets: List<Sound> =
        Curriculum.sounds.filter { !it.family.isVowel && it.id != "gn" } + Curriculum.contextual

    fun fits(consonant: String, vowel: String): Boolean = when (consonant) {
        "c", "g" -> vowel in setOf("a", "o", "u", "ou", "on", "an", "oi", "au")
        "c_soft", "g_soft" -> vowel in setOf("e", "e_acute", "e_grave", "i")
        "qu" -> vowel !in setOf("u", "ou", "au")
        else -> true
    }

    val all: List<Syllable> by lazy {
        val s = Curriculum::get
        val cv = onsets.flatMap { c -> openVowels.filter { fits(c.id, it) }.map { v -> Syllable(listOf(c, s(v)), SyllableShape.CV) } }
        val vc = closedVowels.flatMap { v -> finals.map { c -> Syllable(listOf(s(v), s(c)), SyllableShape.VC) } }
        val cvc = onsets.filter { it.alone }.flatMap { c ->
            // Not the same consonant on both sides (LAL, RAR): odd to read and to hear.
            closedVowels.filter { fits(c.id, it) }.flatMap { v ->
                finals.filter { it != c.id }.map { f -> Syllable(listOf(c, s(v), s(f)), SyllableShape.CVC) }
            }
        }
        val ccv = clusters.flatMap { (a, b) -> clusterVowels.map { v -> Syllable(listOf(s(a), s(b), s(v)), SyllableShape.CCV) } }
        cv + vc + cvc + ccv
    }

    private val byId: Map<String, Syllable> by lazy { all.associateBy { it.id } }

    fun find(id: String): Syllable? = byId[id]
}
