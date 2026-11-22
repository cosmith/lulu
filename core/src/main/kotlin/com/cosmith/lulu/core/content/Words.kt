package com.cosmith.lulu.core.content

/** How hard a word is to decode, which decides when it can come. */
enum class WordShape {
    /** Only open syllables (consonant-vowel), silent letters aside: vélo, chou, lapin. */
    Open,

    /** A syllable closed by a consonant: jour, lune, tomate. */
    Closed,

    /** Two consonants in a row in a syllable: trou, fleur, chèvre. */
    Cluster,
}

/**
 * A word to read, cut into the sounds the child knows. Silent letters are shown in
 * grey, as in French first readers. Its [picture] comes only after the child has
 * read it, never before, so that the child decodes instead of guessing.
 */
data class Word(val text: String, override val parts: List<Part>, val picture: String) : Item {
    override val id: String get() = Words.PREFIX + text
    override val spoken: String get() = text

    val shape: WordShape
        get() {
            val consonant = sounds.map { !it.family.isVowel }
            var shape = WordShape.Open
            for (i in consonant.indices) {
                if (!consonant[i]) continue
                val next = consonant.getOrNull(i + 1)
                when {
                    next == null -> shape = maxOf(shape, WordShape.Closed)
                    next && Words.isCluster(sounds[i], sounds[i + 1]) -> return WordShape.Cluster
                    next -> shape = maxOf(shape, WordShape.Closed)
                }
            }
            return shape
        }
}

/**
 * Words a 3-year-old knows, decodable with the sounds of the progression, each with
 * a picture. Only words whose spelling follows rules the child has met: no s read
 * /z/ between vowels, no double consonants, no ill or ph.
 */
object Words {
    const val PREFIX = "word:"

    private val clusterPairs = setOf(
        "pl", "bl", "fl", "cl", "gl", "pr", "br", "tr", "dr", "fr", "vr", "cr", "gr",
    )

    fun isCluster(a: Sound, b: Sound): Boolean = (a.grapheme + b.grapheme) in clusterPairs && b.id in setOf("l", "r")

    /** Parts are sound ids; letters in parentheses are silent. */
    private fun w(text: String, picture: String, vararg parts: String): Word {
        val built = parts.map { p ->
            if (p.startsWith("(")) Part(null, p.removeSurrounding("(", ")")) else Part(Curriculum[p])
        }
        val word = Word(text, built, picture)
        require(word.written(lowercase = true) == text) { "$text is spelled ${word.written(true)}" }
        return word
    }

    val all: List<Word> = listOf(
        // Open syllables only
        w("fou", "🤪", "f", "ou"),
        w("chou", "🥬", "ch", "ou"),
        w("jeu", "🎮", "j", "eu"),
        w("feu", "🔥", "f", "eu"),
        w("dé", "🎲", "d", "e_acute"),
        w("lit", "🛏️", "l", "i", "(t)"),
        w("riz", "🍚", "r", "i", "(z)"),
        w("rat", "🐀", "r", "a", "(t)"),
        w("chat", "🐱", "ch", "a", "(t)"),
        w("loup", "🐺", "l", "ou", "(p)"),
        w("nid", "🪺", "n", "i", "(d)"),
        w("vélo", "🚲", "v", "e_acute", "l", "o"),
        w("moto", "🏍️", "m", "o", "t", "o"),
        w("lama", "🦙", "l", "a", "m", "a"),
        w("bébé", "👶", "b", "e_acute", "b", "e_acute"),
        w("papa", "👨", "p", "a", "p", "a"),
        w("maman", "👩", "m", "a", "m", "an"),
        w("dodo", "😴", "d", "o", "d", "o"),
        w("tutu", "🩰", "t", "u", "t", "u"),
        w("judo", "🥋", "j", "u", "d", "o"),
        w("radio", "📻", "r", "a", "d", "i", "o"),
        w("piano", "🎹", "p", "i", "a", "n", "o"),
        w("mouton", "🐑", "m", "ou", "t", "on"),
        w("bonbon", "🍬", "b", "on", "b", "on"),
        w("lapin", "🐰", "l", "a", "p", "in"),
        w("sapin", "🎄", "s", "a", "p", "in"),
        w("lion", "🦁", "l", "i", "on"),
        w("souris", "🐭", "s", "ou", "r", "i", "(s)"),
        w("dent", "🦷", "d", "en", "(t)"),
        w("melon", "🍈", "m", "e", "l", "on"),
        w("fée", "🧚", "f", "e_acute", "(e)"),
        w("épée", "🗡️", "e_acute", "p", "e_acute", "(e)"),
        w("café", "☕", "c", "a", "f", "e_acute"),
        w("cadeau", "🎁", "c", "a", "d", "eau"),
        w("bateau", "⛵", "b", "a", "t", "eau"),
        w("seau", "🪣", "s", "eau"),
        w("chameau", "🐫", "ch", "a", "m", "eau"),
        w("chapeau", "🎩", "ch", "a", "p", "eau"),
        w("taureau", "🐂", "t", "au", "r", "eau"),
        w("roi", "🤴", "r", "oi"),
        w("bois", "🪵", "b", "oi", "(s)"),
        w("lait", "🥛", "l", "ai", "(t)"),
        w("balai", "🧹", "b", "a", "l", "ai"),
        w("pain", "🍞", "p", "ain"),
        w("main", "✋", "m", "ain"),
        w("bain", "🛁", "b", "ain"),
        // A syllable closed by a consonant
        w("jour", "☀️", "j", "ou", "r"),
        w("tour", "🗼", "t", "ou", "r"),
        w("mur", "🧱", "m", "u", "r"),
        w("sac", "👜", "s", "a", "c"),
        w("lac", "🏞️", "l", "a", "c"),
        w("bus", "🚌", "b", "u", "s"),
        w("lune", "🌙", "l", "u", "n", "(e)"),
        w("pile", "🔋", "p", "i", "l", "(e)"),
        w("robe", "👗", "r", "o", "b", "(e)"),
        w("poule", "🐔", "p", "ou", "l", "(e)"),
        w("vache", "🐄", "v", "a", "ch", "(e)"),
        w("bouche", "👄", "b", "ou", "ch", "(e)"),
        w("douche", "🚿", "d", "ou", "ch", "(e)"),
        w("soupe", "🍲", "s", "ou", "p", "(e)"),
        w("route", "🛣️", "r", "ou", "t", "(e)"),
        w("loupe", "🔍", "l", "ou", "p", "(e)"),
        w("tomate", "🍅", "t", "o", "m", "a", "t", "(e)"),
        w("banane", "🍌", "b", "a", "n", "a", "n", "(e)"),
        w("salade", "🥗", "s", "a", "l", "a", "d", "(e)"),
        w("tulipe", "🌷", "t", "u", "l", "i", "p", "(e)"),
        w("olive", "🫒", "o", "l", "i", "v", "(e)"),
        w("pirate", "🏴‍☠️", "p", "i", "r", "a", "t", "(e)"),
        w("tortue", "🐢", "t", "o", "r", "t", "u", "(e)"),
        w("cheval", "🐴", "ch", "e", "v", "a", "l"),
        w("renard", "🦊", "r", "e", "n", "a", "r", "(d)"),
        w("canard", "🦆", "c", "a", "n", "a", "r", "(d)"),
        w("étoile", "⭐", "e_acute", "t", "oi", "l", "(e)"),
        w("voiture", "🚗", "v", "oi", "t", "u", "r", "(e)"),
        w("poire", "🍐", "p", "oi", "r", "(e)"),
        w("miroir", "🪞", "m", "i", "r", "oi", "r"),
        w("girafe", "🦒", "g_soft", "i", "r", "a", "f", "(e)"),
        // Two consonants in a row
        w("trou", "🕳️", "t", "r", "ou"),
        w("fleur", "🌸", "f", "l", "eu", "r"),
        w("train", "🚆", "t", "r", "ain"),
        w("flèche", "🏹", "f", "l", "e_grave", "ch", "(e)"),
        w("chèvre", "🐐", "ch", "e_grave", "v", "r", "(e)"),
        w("zèbre", "🦓", "z", "e_grave", "b", "r", "(e)"),
        w("citron", "🍋", "c_soft", "i", "t", "r", "on"),
        w("glace", "🍦", "g", "l", "a", "c_soft", "(e)"),
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String): Word? = byId[id]
}
