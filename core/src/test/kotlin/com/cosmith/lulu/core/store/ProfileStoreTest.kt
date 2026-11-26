package com.cosmith.lulu.core.store

import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.fsrs.CardState
import com.cosmith.lulu.core.session.SessionPlanner
import com.cosmith.lulu.core.fsrs.Rating
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileStoreTest {
    @Test
    fun profilesAndProgressSurviveARestart() {
        val dir = Files.createTempDirectory("lulu-store").toFile()
        val t0 = Instant.parse("2026-11-21T17:00:00Z")
        val store = ProfileStore(dir)
        val lucie = Profile("p-1", "Lucie", avatar = 2, createdAt = t0)
        val tom = Profile("p-2", "Tom", avatar = 1, createdAt = t0.plusSeconds(5))
        store.saveProfile(lucie)
        store.saveProfile(tom)
        store.saveProfile(lucie.copy(settings = LearningSettings(sessionMinutes = 7)))

        val key = CardKey("a", CardKind.ListenAndChoose)
        val progress = Progress()
            .introduce(Curriculum["a"], t0)
            .record(key, Rating.Good, t0.plusSeconds(10), Judge.Tap, luluScheduler())
        store.saveProgress("p-1", progress)

        val reopened = ProfileStore(dir)
        assertEquals(listOf("Lucie", "Tom"), reopened.profiles().map { it.firstName })
        assertEquals(7, reopened.profiles().first().settings.sessionMinutes)
        assertEquals(progress, reopened.progress("p-1"))
        assertEquals(Progress(), reopened.progress("p-2"))

        reopened.deleteProfile("p-1")
        assertEquals(listOf("Tom"), reopened.profiles().map { it.firstName })
        assertTrue(dir.listFiles()!!.none { it.name.contains("p-1") })
    }

    @Test
    fun profilesSavedBeforeTheDurationSettingStillLoad() {
        val dir = Files.createTempDirectory("lulu-store-v1").toFile()
        // Written by the first version, which had three session settings.
        dir.resolve("profiles.json").writeText(
            """[{"id":"p-1","firstName":"Lucie","avatar":0,"createdAt":1763744400000,""" +
                """"settings":{"cardsPerSession":8,"newSoundsPerSession":1,"maxSoundsInLearning":3}}]""",
        )
        val profile = ProfileStore(dir).profiles().single()
        assertEquals("Lucie", profile.firstName)
        assertEquals(5, profile.settings.sessionMinutes)
        // No mascot seed yet: the personality comes from the id, so it stays the same.
        assertEquals(null, profile.mascotSeed)
        assertEquals("p-1".hashCode().toLong(), profile.mascot)
    }

    @Test
    fun progressSavedByThePreviousVersionStillLoadsAndPlays() {
        val dir = Files.createTempDirectory("lulu-store-v2").toFile()
        val t = 1_795_300_000_000L
        // As written before syllables and words: cards keyed by "soundId", no "itemsOn".
        dir.resolve("progress-p-1.json").writeText(
            """{"introduced":["a","e_acute"],"introducedOn":{"a":$t,"e_acute":$t},"lowercaseOn":{},
            "cards":{"a:ListenAndChoose":{"key":{"soundId":"a","kind":"ListenAndChoose"},
            "memory":{"due":${t + 172_800_000},"state":"Review","step":null,"stability":2.3065,"difficulty":2.1,"lastReview":$t},
            "reviews":1,"lapses":0}},
            "reviews":[{"key":{"soundId":"a","kind":"ListenAndChoose"},"at":$t,"rating":"Good","judge":"Tap","corrected":false}]}""",
        )
        val progress = ProfileStore(dir).progress("p-1")
        val key = CardKey("a", CardKind.ListenAndChoose)
        assertEquals(CardState.Review, progress.card(key)?.memory?.state)
        assertEquals(key, progress.reviews.single().key)
        assertTrue(progress.itemsOn.isEmpty())
        // And saving it again keeps the same field name.
        ProfileStore(dir).saveProgress("p-1", progress)
        assertTrue(dir.resolve("progress-p-1.json").readText().contains("\"soundId\":\"a\""))
        // A session can start from it.
        val step = SessionPlanner(Profile("p-1", "", 0, Instant.ofEpochMilli(t))).next(progress, Instant.ofEpochMilli(t + 86_400_000))
        assertNotNull(step)
    }

    @Test
    fun exportHoldsEveryProfileAndAllTheirAnswers() {
        val dir = Files.createTempDirectory("lulu-export").toFile()
        val t0 = Instant.parse("2026-11-21T17:00:00Z")
        val store = ProfileStore(dir)
        val lucie = Profile("p-1", "Lucie", avatar = 2, createdAt = t0, mascotSeed = 42)
        val tom = Profile("p-2", "Tom", avatar = 1, createdAt = t0)
        store.saveProfile(lucie)
        store.saveProfile(tom)
        val key = CardKey("a", CardKind.ListenAndChoose)
        val progress = Progress()
            .introduce(Curriculum["a"], t0)
            .record(key, Rating.Good, t0.plusSeconds(10), Judge.Tap, luluScheduler())
            .record(key, Rating.Again, t0.plusSeconds(700), Judge.Tap, luluScheduler())
        store.saveProgress("p-1", progress)

        val text = DataExport.encode(store.exportAll(t0.plusSeconds(3600), "0.6.0", "Europe/Paris", listOf("ou", "a")))
        val back = DataExport.decode(text)
        assertEquals(listOf(lucie, tom), back.profiles)
        assertEquals(progress, back.progress["p-1"])
        assertEquals(Progress(), back.progress["p-2"])
        assertEquals(listOf("a", "ou"), back.recordings)
        assertEquals("Europe/Paris", back.timeZone)
        assertEquals(DataExport.FORMAT, back.format)
        assertTrue(text.contains("\"rating\": \"Again\""))
    }
}
