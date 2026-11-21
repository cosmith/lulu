package com.cosmith.lulu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosmith.lulu.AppContainer
import com.cosmith.lulu.core.store.LearningSettings
import com.cosmith.lulu.core.store.Profile
import com.cosmith.lulu.ui.screens.EditProfileScreen
import com.cosmith.lulu.ui.screens.HomeScreen
import com.cosmith.lulu.ui.screens.ParentGateScreen
import com.cosmith.lulu.ui.screens.ParentScreen
import com.cosmith.lulu.ui.screens.ProfilesScreen
import com.cosmith.lulu.ui.screens.SessionScreen
import com.cosmith.lulu.ui.theme.Lulu
import java.util.UUID

sealed interface Screen {
    data object Profiles : Screen
    data class Home(val profileId: String) : Screen
    data class Session(val profileId: String, val run: Long = System.nanoTime()) : Screen

    /** The parent lock, then [then]. */
    data class Gate(val then: Screen) : Screen
    data class Parent(val profileId: String?) : Screen

    /** Create ([profileId] null) or edit a profile. [firstRun] shows the welcome. */
    data class EditProfile(val profileId: String?, val firstRun: Boolean = false) : Screen
}

class AppViewModel(private val c: AppContainer) : ViewModel() {
    var profiles by mutableStateOf(c.store.profiles())
        private set
    var screen by mutableStateOf(startScreen())
        private set
    private val backStack = ArrayDeque<Screen>()
    var canGoBack by mutableStateOf(false)
        private set

    private fun startScreen(): Screen = when (profiles.size) {
        0 -> Screen.EditProfile(null, firstRun = true)
        1 -> Screen.Home(profiles.single().id)
        else -> Screen.Profiles
    }

    fun go(next: Screen) {
        backStack.addLast(screen)
        canGoBack = true
        screen = next
    }

    /** Leaves the current screen without keeping it in the history (the gate, a finished session). */
    fun replace(next: Screen) {
        screen = next
    }

    fun back(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        canGoBack = backStack.isNotEmpty()
        screen = previous
        return true
    }

    fun resetTo(next: Screen) {
        backStack.clear()
        canGoBack = false
        screen = next
    }

    fun profile(id: String): Profile? = profiles.firstOrNull { it.id == id }

    /** Each screen gets its own ViewModels, kept while it is shown or in the history. */
    private val stores = HashMap<Screen, ViewModelStore>()

    fun ownerFor(s: Screen): ViewModelStoreOwner {
        val store = stores.getOrPut(s) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
    }

    /** Called when a screen leaves the composition: drops its ViewModels if it cannot come back. */
    fun releaseIfGone(s: Screen) {
        if (s != screen && s !in backStack) stores.remove(s)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
    }

    fun saveProfile(id: String?, firstName: String, avatar: Int, mascotSeed: Long): Profile {
        val existing = id?.let(::profile)
        val profile = existing?.copy(firstName = firstName.trim(), avatar = avatar, mascotSeed = mascotSeed)
            ?: Profile(UUID.randomUUID().toString(), firstName.trim(), avatar, c.now(), mascotSeed = mascotSeed)
        c.store.saveProfile(profile)
        refresh()
        return profile
    }

    fun saveSettings(id: String, settings: LearningSettings) {
        val p = profile(id) ?: return
        c.store.saveProfile(p.copy(settings = settings))
        refresh()
    }

    fun deleteProfile(id: String) {
        c.store.deleteProfile(id)
        refresh()
        resetTo(if (profiles.isEmpty()) Screen.EditProfile(null, firstRun = true) else startScreen())
    }

    fun refresh() {
        profiles = c.store.profiles()
    }
}

@Composable
fun LuluRoot(c: AppContainer) {
    val vm: AppViewModel = viewModel { AppViewModel(c) }
    val screen = vm.screen
    val homeWithSiblings = screen is Screen.Home && vm.profiles.size > 1
    BackHandler(enabled = vm.canGoBack || homeWithSiblings) {
        if (!vm.back() && homeWithSiblings) vm.resetTo(Screen.Profiles)
    }
    Box(Modifier.fillMaxSize().background(Lulu.Cream)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { s ->
            ScreenScope(vm, s) { ScreenContent(c, vm, s) }
        }
    }
}

@Composable
private fun ScreenScope(vm: AppViewModel, s: Screen, content: @Composable () -> Unit) {
    val owner = remember(s) { vm.ownerFor(s) }
    DisposableEffect(owner) { onDispose { vm.releaseIfGone(s) } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

@Composable
private fun ScreenContent(c: AppContainer, vm: AppViewModel, s: Screen) {
            when (s) {
                Screen.Profiles -> ProfilesScreen(
                    profiles = vm.profiles,
                    onPick = { vm.go(Screen.Home(it.id)) },
                    onParent = { vm.go(Screen.Gate(Screen.Parent(null))) },
                )

                is Screen.Home -> {
                    val profile = vm.profile(s.profileId)
                    if (profile != null) {
                        HomeScreen(
                            c = c,
                            profile = profile,
                            canSwitch = vm.profiles.size > 1,
                            onPlay = { vm.go(Screen.Session(profile.id)) },
                            onSwitch = { vm.resetTo(Screen.Profiles) },
                            onParent = { vm.go(Screen.Gate(Screen.Parent(profile.id))) },
                        )
                    }
                }

                is Screen.Session -> {
                    val profile = vm.profile(s.profileId)
                    if (profile != null) {
                        SessionScreen(c = c, profile = profile, runKey = s.run, onExit = { vm.back() })
                    }
                }

                is Screen.Gate -> ParentGateScreen(onUnlock = { vm.replace(s.then) }, onCancel = { vm.back() })

                is Screen.Parent -> ParentScreen(
                    c = c,
                    profiles = vm.profiles,
                    initialProfileId = s.profileId,
                    onBack = { vm.back() },
                    onSaveSettings = vm::saveSettings,
                    onEditProfile = { vm.go(Screen.EditProfile(it)) },
                    onAddProfile = { vm.go(Screen.EditProfile(null)) },
                    onDeleteProfile = vm::deleteProfile,
                )

                is Screen.EditProfile -> EditProfileScreen(
                    existing = s.profileId?.let(vm::profile),
                    firstRun = s.firstRun,
                    onSave = { name, avatar, seed ->
                        val saved = vm.saveProfile(s.profileId, name, avatar, seed)
                        if (s.firstRun) vm.resetTo(Screen.Home(saved.id)) else vm.back()
                    },
                    onCancel = if (s.firstRun) null else ({ vm.back() }),
                )
            }
}
