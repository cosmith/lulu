package com.cosmith.lulu.core.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentTest {
    @Test
    fun everyItemIsFoundByItsId() {
        for (item in Curriculum.sounds + Curriculum.contextual + Syllables.all + Words.all) {
            assertEquals(item, Curriculum.item(item.id), item.id)
        }
        assertEquals(Curriculum.sounds.size + Curriculum.contextual.size, (Curriculum.sounds + Curriculum.contextual).map { it.id }.toSet().size)
    }

    @Test
    fun wordsAreSpelledFromTheirParts() {
        assertTrue(Words.all.size >= 80)
        assertEquals(Words.all.size, Words.all.map { it.text }.toSet().size, "duplicate words")
        for (w in Words.all) {
            assertEquals(w.text, w.written(lowercase = true))
            assertEquals(w.text.uppercase(), w.written(lowercase = false))
            assertTrue(w.picture.isNotBlank(), w.text)
        }
        assertEquals("LOUP", Words.find("word:loup")!!.written(false))
        assertEquals(listOf("l", "ou"), Words.find("word:loup")!!.sounds.map { it.id }) // the p is silent
    }

    @Test
    fun wordShapes() {
        fun shape(text: String) = Words.find(Words.PREFIX + text)!!.shape
        assertEquals(WordShape.Open, shape("vélo"))
        assertEquals(WordShape.Open, shape("lit"))
        assertEquals(WordShape.Open, shape("lapin"))
        assertEquals(WordShape.Closed, shape("jour"))
        assertEquals(WordShape.Closed, shape("lune"))
        assertEquals(WordShape.Closed, shape("tortue"))
        assertEquals(WordShape.Cluster, shape("trou"))
        assertEquals(WordShape.Cluster, shape("chèvre"))
        assertEquals(WordShape.Cluster, shape("glace"))
    }

    @Test
    fun syllablesFollowTheSpellingRules() {
        val ids = Syllables.all.map { it.written(false) to it.shape }
        assertTrue(("LA" to SyllableShape.CV) in ids)
        assertTrue(("OUF" to SyllableShape.VC) in ids)
        assertTrue(("POUL" to SyllableShape.CVC) in ids)
        assertTrue(("TRI" to SyllableShape.CCV) in ids)
        assertTrue(("FLA" to SyllableShape.CCV) in ids)
        // Hard c and g only before a, o, u…; soft c and g only before e, é, è, i.
        for (s in Syllables.all.filter { it.shape == SyllableShape.CV }) {
            val (c, v) = s.sounds
            if (c.id in setOf("c", "g")) assertTrue(v.id !in setOf("e", "e_acute", "e_grave", "i", "ai"), s.id)
            if (c.id in setOf("c_soft", "g_soft")) assertTrue(v.id in setOf("e", "e_acute", "e_grave", "i"), s.id)
        }
        assertNull(Syllables.find("syl:qu-u"))
        assertNotNull(Syllables.find("syl:c_soft-i"))
        assertEquals("ci", Syllables.find("syl:c_soft-i")!!.spoken)
        // Closing consonants are always heard: l, r, f.
        assertTrue(Syllables.all.filter { it.shape == SyllableShape.CVC }.all { it.sounds.last().id in setOf("l", "r", "f") })
    }

    @Test
    fun inversePairs() {
        val fou = Syllables.find("syl:f-ou")!!
        assertEquals("syl:ou-f", fou.inverse?.id)
        assertEquals(fou, fou.inverse?.inverse)
        assertNull(Syllables.find("syl:p-ou")!!.inverse) // OUP: a final p is often silent
    }

    @Test
    fun level5SoundsBorrowAVoice() {
        assertEquals("o", Curriculum["au"].voice)
        assertEquals("o", Curriculum["eau"].voice)
        assertEquals("s", Curriculum["c_soft"].voice)
        assertTrue(Curriculum.contextual.none { it.alone })
        assertTrue(Curriculum.sounds.all { it.alone })
    }
}
