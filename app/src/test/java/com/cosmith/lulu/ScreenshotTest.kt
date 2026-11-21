package com.cosmith.lulu

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.fsrs.Rating
import com.cosmith.lulu.core.session.Step
import com.cosmith.lulu.core.store.CardKey
import com.cosmith.lulu.core.store.CardKind
import com.cosmith.lulu.core.store.Judge
import com.cosmith.lulu.core.store.Profile
import com.cosmith.lulu.core.store.Progress
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
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Terracotta, onExit = {}) } }
        shot("1-voir-et-dire")
    }

    @Test
    fun listenAndChoose() {
        val ou = Curriculum["ou"]
        val vm = session(listOf(Step.ListenAndChoose(ou, listOf(Curriculum["a"], ou, Curriculum["ch"])), Step.SeeAndSay(ou)))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Sky, onExit = {}) } }
        shot("2-ecouter-et-choisir")
    }

    @Test
    fun listenAndChooseFour() {
        val s = Curriculum["s"]
        val vm = session(listOf(Step.ListenAndChoose(s, listOf(Curriculum["ch"], s, Curriculum["f"], Curriculum["on"]))))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Mustard, onExit = {}) } }
        shot("3-ecouter-et-choisir-4")
    }

    @Test
    fun meet() {
        val vm = session(listOf(Step.Meet(Curriculum["ch"]), Step.ListenAndChoose(Curriculum["ch"], listOf(Curriculum["ch"], Curriculum["a"]))))
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Sage, onExit = {}) } }
        rule.mainClock.advanceTimeBy(1500)
        shot("4-nouveau-son")
    }

    @Test
    fun finished() {
        val vm = session(emptyList())
        rule.setContent { LuluTheme { SessionContent(vm, Lulu.Terracotta, onExit = {}) } }
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
        rule.setContent { LuluTheme { EditProfileScreen(null, firstRun = true, onSave = { _, _ -> }, onCancel = null) } }
        shot("8-bienvenue")
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
