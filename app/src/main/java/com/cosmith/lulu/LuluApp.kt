package com.cosmith.lulu

import android.app.Application
import android.content.Context
import com.cosmith.lulu.audio.SoundBank
import com.cosmith.lulu.audio.Tones
import com.cosmith.lulu.core.fsrs.FsrsScheduler
import com.cosmith.lulu.core.store.ProfileStore
import com.cosmith.lulu.core.store.Progress
import com.cosmith.lulu.core.store.luluScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

class LuluApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** The app's few long-lived objects. Everything stays on the phone. */
class AppContainer(context: Context) {
    val store = ProfileStore(File(context.filesDir, "profiles"))
    val sounds = SoundBank(context.applicationContext)
    val tones = Tones()
    val scheduler: FsrsScheduler = luluScheduler()
    val now: () -> Instant = { Instant.now() }

    /** One writer at a time, in order, and not tied to any screen: a save outlives the session that asked for it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun saveProgress(profileId: String, progress: Progress) {
        writes.launch { store.saveProgress(profileId, progress) }
    }
}

val Context.container: AppContainer get() = (applicationContext as LuluApp).container
