package com.cosmith.lulu

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.content.Syllables
import com.cosmith.lulu.core.content.Words
import com.cosmith.lulu.core.fsrs.Rating
import com.cosmith.lulu.core.session.Step
import com.cosmith.lulu.core.store.CardKey
import com.cosmith.lulu.core.store.CardKind
import com.cosmith.lulu.core.store.Judge
import com.cosmith.lulu.core.store.Profile
import com.cosmith.lulu.core.store.Progress
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cosmith.lulu.ui.components.Mascot
import com.cosmith.lulu.ui.components.MascotLook
import com.cosmith.lulu.ui.components.Mood
import com.cosmith.lulu.ui.screens.EditProfileScreen
import com.cosmith.lulu.ui.screens.HomeScreen
import com.cosmith.lulu.ui.screens.ParentGateScreen
import com.cosmith.lulu.ui.screens.ParentScreen
import com.cosmith.lulu.ui.screens.ProfilesScreen
import com.cosmith.lulu.ui.screens.SessionContent
import com.cosmith.lulu.ui.screens.SessionViewModel
import com.cosmith.lulu.ui.theme.Lulu
import com.cosmith.lulu.ui.theme.LuluTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant

/**
 * Renders the main screens to PNG files under build/screenshots, to check the art
 * direction without a device. Run with: ./gradlew :app:recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-normal-long-notround-any-420dpi-keyshidden-nonav")
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val c get() = RuntimeEnvironment.getApplication().container
    private val t0 = Instant.parse("2026-11-21T17:00:00Z")
    private val lucie = Profile("p1", "Lucie", avatar = 4, createdAt = t0)
    private val tom = Profile("p2", "Tom", avatar = 1, createdAt = t0)

    private fun shot(name: String) = rule.onRoot().captureRoboImage("build/screenshots/$name.png")

    private fun session(steps: List<Step>) = SessionViewModel(c, lucie, planned = steps)

    @Test
    fun seeAndSay() {
        val vm = session(listOf(Step.SeeAndSay(Curriculum["ou"]), Step.SeeAndSay(Curriculum["a"])))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Terracotta, MascotLook.from(7), onExit = {}) } }
        shot("1-voir-et-dire")
    }

    @Test
    fun listenAndChoose() {
        val ou = Curriculum["ou"]
        val vm = session(listOf(Step.ListenAndChoose(ou, listOf(Curriculum["a"], ou, Curriculum["ch"])), Step.SeeAndSay(ou)))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Sky, MascotLook.from(7), onExit = {}) } }
        shot("2-ecouter-et-choisir")
    }

    @Test
    fun listenAndChooseFour() {
        val s = Curriculum["s"]
        val vm = session(listOf(Step.ListenAndChoose(s, listOf(Curriculum["ch"], s, Curriculum["f"], Curriculum["on"]))))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Mustard, MascotLook.from(7), onExit = {}) } }
        shot("3-ecouter-et-choisir-4")
    }

    @Test
    fun meet() {
        val vm = session(listOf(Step.Meet(Curriculum["ch"]), Step.ListenAndChoose(Curriculum["ch"], listOf(Curriculum["ch"], Curriculum["a"]))))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Sage, MascotLook.from(7), onExit = {}) } }
        rule.mainClock.advanceTimeBy(1500)
        shot("4-nouveau-son")
    }

    @Test
    fun meetLowercase() {
        val ch = Curriculum["ch"]
        val vm = session(listOf(Step.Meet(ch, lowercase = true), Step.MatchCase(ch, listOf(ch, Curriculum["a"]))))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Sage, MascotLook.from(5), onExit = {}) } }
        rule.mainClock.advanceTimeBy(1500)
        shot("13-minuscule-nouvelle")
    }

    @Test
    fun matchCase() {
        val b = Curriculum["b"]
        val vm = session(listOf(Step.MatchCase(b, listOf(Curriculum["d"], b, Curriculum["p"], Curriculum["n"]))))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Terracotta, MascotLook.from(11), onExit = {}) } }
        shot("14-majuscule-minuscule")
    }

    @Test
    fun listenAndChooseLowercase() {
        val ou = Curriculum["ou"]
        val vm = session(listOf(Step.ListenAndChoose(ou, listOf(Curriculum["on"], ou, Curriculum["an"]), lowercase = true)))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Sky, MascotLook.from(3), onExit = {}) } }
        shot("15-ecouter-minuscules")
    }

    @Test
    fun meetSyllable() {
        val fou = Syllables.find("syl:f-ou")!!
        val vm = session(listOf(Step.Meet(fou), Step.SeeAndSay(fou)))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Mustard, MascotLook.from(8), onExit = {}) } }
        rule.mainClock.advanceTimeBy(500)
        shot("16-syllabe-fusion")
    }

    @Test
    fun readSyllable() {
        val poul = Syllables.find("syl:p-ou-l")!!
        val vm = session(listOf(Step.SeeAndSay(poul)))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Sky, MascotLook.from(2), onExit = {}) } }
        shot("17-syllabe-lire")
    }

    @Test
    fun readWord() {
        val loup = Words.find("word:loup")!!
        val vm = session(listOf(Step.SeeAndSay(loup), Step.SeeAndSay(loup)))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Terracotta, MascotLook.from(4), onExit = {}) } }
        shot("18-mot-lire")
        vm.onVerdict(true)
        rule.mainClock.advanceTimeBy(600)
        shot("19-mot-lu-image")
    }

    @Test
    fun listenWord() {
        val chat = Words.find("word:chat")!!
        val vm = session(listOf(Step.ListenAndChoose(chat, listOf(Words.find("word:chou")!!, chat, Words.find("word:rat")!!), lowercase = true)))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Sage, MascotLook.from(6), onExit = {}) } }
        shot("20-mot-ecouter")
    }

    @Test
    fun finished() {
        val vm = session(emptyList())
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Terracotta, MascotLook.from(7), onExit = {}) } }
        shot("5-fin-sans-carte")
    }

    @Test
    fun home() {
        rule.setContent { LuluTheme { HomeScreen(c, lucie, canSwitch = true, onPlay = {}, onSwitch = {}, onParent = {}) } }
        shot("6-accueil")
    }

    @Test
    fun profiles() {
        rule.setContent { LuluTheme { ProfilesScreen(listOf(lucie, tom, Profile("p3", "", 2, t0)), onPick = {}, onParent = {}) } }
        shot("7-qui-joue")
    }

    @Test
    fun welcome() {
        rule.setContent { LuluTheme { EditProfileScreen(null, firstRun = true, onSave = { _, _, _ -> }, onCancel = null) } }
        shot("8-bienvenue")
    }

    @Test
    fun personalities() {
        rule.setContent {
            LuluTheme {
                Column(Modifier.fillMaxSize().background(Lulu.Cream).padding(8.dp)) {
                    for (row in 0 until 5) {
                        Row {
                            for (col in 0 until 3) {
                                val i = row * 3 + col
                                val mood = listOf(Mood.Idle, Mood.Listening, Mood.Happy, Mood.Encourage, Mood.Idle)[row]
                                Mascot(Lulu.mascots[i % Lulu.mascots.size], mood, Modifier.size(130.dp), look = MascotLook.from(i * 7919L + 3))
                            }
                        }
                    }
                }
            }
        }
        shot("12-personnalites")
    }

    @Test
    fun gate() {
        rule.setContent { LuluTheme { ParentGateScreen(onUnlock = {}, onCancel = {}) } }
        shot("9-verrou-parent")
    }

    @Test
    fun parent() {
        var p = Progress()
        var t = Instant.now().minus(Duration.ofDays(20))
        for (id in listOf("a", "e_acute", "l", "i")) {
            p = p.introduce(Curriculum[id], t)
            p = p.record(CardKey(id, CardKind.ListenAndChoose), Rating.Good, t, Judge.Tap, c.scheduler)
            p = p.record(CardKey(id, CardKind.SeeAndSay), if (id == "i") Rating.Again else Rating.Good, t.plusSeconds(40), Judge.Parent, c.scheduler)
            t = t.plus(Duration.ofDays(3))
        }
        c.store.saveProfile(lucie)
        c.store.saveProgress(lucie.id, p)
        rule.setContent {
            LuluTheme {
                ParentScreen(
                    c, listOf(lucie, tom), lucie.id,
                    onBack = {}, onSaveSettings = { _, _ -> }, onEditProfile = {}, onAddProfile = {}, onDeleteProfile = {},
                )
            }
        }
        shot("10-espace-parent")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Durée"))
        shot("11-espace-parent-seance")
    }
}
