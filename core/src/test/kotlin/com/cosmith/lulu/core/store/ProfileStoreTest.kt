package com.cosmith.lulu.core.store

import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.fsrs.Rating
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
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
        store.saveProfile(lucie.copy(settings = LearningSettings(cardsPerSession = 6)))

        val key = CardKey("a", CardKind.ListenAndChoose)
        val progress = Progress()
            .introduce(Curriculum["a"], t0)
            .record(key, Rating.Good, t0.plusSeconds(10), Judge.Tap, luluScheduler())
        store.saveProgress("p-1", progress)

        val reopened = ProfileStore(dir)
        assertEquals(listOf("Lucie", "Tom"), reopened.profiles().map { it.firstName })
        assertEquals(6, reopened.profiles().first().settings.cardsPerSession)
        assertEquals(progress, reopened.progress("p-1"))
        assertEquals(Progress(), reopened.progress("p-2"))

        reopened.deleteProfile("p-1")
        assertEquals(listOf("Tom"), reopened.profiles().map { it.firstName })
        assertTrue(dir.listFiles()!!.none { it.name.contains("p-1") })
    }
}
