package com.cosmith.lulu.ui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosmith.lulu.AppContainer
import com.cosmith.lulu.core.content.Item
import com.cosmith.lulu.core.content.Sound
import com.cosmith.lulu.core.content.Syllable
import com.cosmith.lulu.core.session.Step
import com.cosmith.lulu.core.store.Profile
import com.cosmith.lulu.ui.components.Confetti
import com.cosmith.lulu.ui.components.Glyph
import com.cosmith.lulu.ui.components.Grapheme
import com.cosmith.lulu.ui.components.Hill
import com.cosmith.lulu.ui.components.LuluIcon
import com.cosmith.lulu.ui.components.Mascot
import com.cosmith.lulu.ui.components.MascotLook
import com.cosmith.lulu.ui.components.Mood
import com.cosmith.lulu.ui.components.PebbleShape
import com.cosmith.lulu.ui.components.RoundButton
import com.cosmith.lulu.ui.components.bouncyClick
import com.cosmith.lulu.ui.components.itemText
import com.cosmith.lulu.ui.components.rememberShake
import com.cosmith.lulu.ui.theme.Fredoka
import com.cosmith.lulu.ui.theme.Lulu
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SessionScreen(c: AppContainer, profile: Profile, runKey: Long, onExit: () -> Unit) {
    val vm: SessionViewModel = viewModel(key = "session-$runKey") { SessionViewModel(c, profile) }
    SessionContent(
        state = vm,
        mascotColor = mascotColor(profile),
        look = mascotLook(profile),
        onExit = onExit,
    )
}

@Composable
internal fun SessionContent(state: SessionViewModel, mascotColor: Color, look: MascotLook, onExit: () -> Unit) {
    val view = LocalView.current
    LaunchedEffect(state.confettiKey) {
        if (state.confettiKey > 0) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP,
            )
        }
    }
    // Session time only runs while the app is on screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> state.onPause()
                Lifecycle.Event.ON_RESUME -> state.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var center by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Lulu.Cream)
            .onSizeChanged { center = Offset(it.width / 2f, it.height * 0.42f) },
    ) {
        val step = state.step
        if (step !is Step.ListenAndChoose && step !is Step.MatchCase) {
            Hill(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp))
        }

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Glyph.Back, onExit, tint = Lulu.Terracotta)
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    state.finished -> Finished(state.cardsDone > 0, mascotColor, look, state.jumpKey, onExit)
                    step is Step.Meet && step.item is Syllable -> Blending(step.item as Syllable, step.lowercase, state)
                    step is Step.Meet -> BigItem(step.item, step.lowercase, state, onTap = state::onSpeaker, withCapital = step.lowercase)
                    step is Step.SeeAndSay -> BigItem(step.item, step.lowercase, state, onTap = state::onSpeaker)
                    step is Step.ListenAndChoose -> Choices(step.item, step.choices, step.lowercase, state) {
                        val picture = state.picture
                        if (picture != null) Picture(picture, Modifier.padding(vertical = 8.dp).size(150.dp))
                        else BigSpeaker(state.playing, state.soundKey, onClick = state::onSpeaker)
                    }
                    step is Step.MatchCase -> Choices(step.item, step.choices, lowercase = true, state) {
                        CapitalToMatch(step.item, state)
                    }
                }
            }

            if (!state.finished) {
                BottomBar(step, state, mascotColor, look)
            }
        }

        Confetti(state.confettiKey, center, Modifier.fillMaxSize())
    }
}

@Composable
private fun BottomBar(step: Step?, state: SessionViewModel, mascotColor: Color, look: MascotLook) {
    Row(
        Modifier.fillMaxWidth().height(150.dp).padding(start = 12.dp, end = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Mascot(mascotColor, state.mood, Modifier.size(130.dp), look = look, jumpKey = state.jumpKey)
        Box(Modifier.weight(1f).height(110.dp), contentAlignment = Alignment.BottomCenter) {
            when (step) {
                is Step.SeeAndSay -> ParentVerdict(
                    label = if (step.item is Sound) "Son juste ?" else "Bien lu ?",
                    enabled = !state.busy,
                    onVerdict = state::onVerdict,
                )
                is Step.Meet -> {
                    val pop = remember(step) { Animatable(0f) }
                    LaunchedEffect(pop) {
                        kotlinx.coroutines.delay(700)
                        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
                    }
                    RoundButton(
                        Glyph.Next, state::onMeetDone,
                        modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                        size = 84.dp, fill = Lulu.Mustard, tint = Lulu.Navy,
                    )
                }
                else -> Unit
            }
        }
        if (step is Step.SeeAndSay || step is Step.Meet) {
            SpeakerButton(state.playing, onClick = state::onSpeaker)
        } else {
            Spacer(Modifier.width(96.dp))
        }
    }
}

/**
 * The verdict for "voir et dire" comes from the parent sitting next to the child, until
 * the on-device recognizer is in. Small and quiet on purpose: the letter is the star.
 */
@Composable
private fun ParentVerdict(label: String, enabled: Boolean, onVerdict: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = Lulu.Navy.copy(alpha = 0.8f),
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoundButton(Glyph.Cross, { onVerdict(false) }, size = 50.dp, fill = Color.White.copy(alpha = 0.85f), tint = Lulu.Terracotta, enabled = enabled)
            RoundButton(Glyph.Check, { onVerdict(true) }, size = 50.dp, fill = Color.White.copy(alpha = 0.85f), tint = Lulu.Sage, enabled = enabled)
        }
    }
}

@Composable
private fun SpeakerButton(playing: Boolean, onClick: () -> Unit) {
    val pulse by animateFloatAsState(
        targetValue = if (playing) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "speaker",
    )
    Box(
        Modifier
            .size(96.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .bouncyClick(onClick = onClick)
            .clip(PebbleShape(11))
            .background(Lulu.Navy.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        LuluIcon(Glyph.Speaker, Lulu.Cream, Modifier.size(48.dp))
    }
}

/**
 * The item alone on screen: pops in, wiggles when its sound plays, shakes softly on a
 * miss. [withCapital] shows the capital, smaller and paler, next to a lower-case letter
 * met for the first time. Once a word is read, its picture appears above it.
 */
@Composable
private fun BigItem(item: Item, lowercase: Boolean, state: SessionViewModel, onTap: () -> Unit, withCapital: Boolean = false) {
    val appear = remember(item.id, state.stepIndex) { Animatable(0.4f) }
    LaunchedEffect(appear) { appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(state.soundKey) {
        if (state.soundKey == 0) return@LaunchedEffect
        wiggle.animateTo(1f, tween(120))
        wiggle.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 250f))
    }
    val shake = rememberShake(state.shakeKey)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        val picture = state.picture
        if (picture != null) Picture(picture, Modifier.weight(1f).fillMaxWidth())
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .offset { IntOffset((shake.value * density).roundToInt(), 0) }
                .graphicsLayer {
                    val s = appear.value * (1f + 0.09f * wiggle.value)
                    scaleX = s
                    scaleY = s
                    rotationZ = -3f * wiggle.value
                }
                .bouncyClick(pressedScale = 0.92f, onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            if (withCapital && item is Sound) {
                val ink = Lulu.ink(item.family)
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Grapheme(item.capitals, ink.copy(alpha = 0.4f), Modifier.weight(0.8f).fillMaxHeight(0.6f))
                    Grapheme(item.lowercase, ink, Modifier.weight(1.2f).fillMaxHeight())
                }
            } else {
                Grapheme(itemText(item, lowercase), Modifier.fillMaxSize())
            }
        }
    }
}

/** A word's picture, popping in once the word has been read. */
@Composable
private fun Picture(picture: String, modifier: Modifier = Modifier) {
    val pop = remember(picture) { Animatable(0f) }
    LaunchedEffect(pop) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f)) }
    Box(modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }, contentAlignment = Alignment.Center) {
        Grapheme(picture, Color.Black, Modifier.fillMaxSize(), fill = 0.8f)
    }
}

/**
 * Meeting a syllable: its sounds stand apart and light up one by one as Lulu says
 * them, then slide together while Lulu says the syllable (« L… A… LA »).
 */
@Composable
private fun Blending(syllable: Syllable, lowercase: Boolean, state: SessionViewModel) {
    val gap by animateFloatAsState(
        targetValue = if (state.blended) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 180f),
        label = "gap",
    )
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        Modifier.fillMaxSize().padding(horizontal = 12.dp).bouncyClick(pressedScale = 0.95f, onClick = state::onSpeaker),
        contentAlignment = Alignment.Center,
    ) {
        val style = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 100.sp, lineHeight = 100.sp)
        val whole = remember(syllable, lowercase) { measurer.measure(syllable.written(lowercase), style).size }
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        // Sized so that the sounds still fit while apart.
        val scale = min(wPx * 0.62f / whole.width, hPx * 0.7f / whole.height)
        val size = 100.sp * scale
        Row(horizontalArrangement = Arrangement.spacedBy((36 * gap).dp), verticalAlignment = Alignment.CenterVertically) {
            syllable.sounds.forEachIndexed { i, sound ->
                val lit = state.blendPart == i
                val grow by animateFloatAsState(if (lit) 1.18f else 1f, spring(dampingRatio = 0.4f), label = "lit")
                val faded = !state.blended && state.blendPart != null && !lit
                Text(
                    sound.written(lowercase),
                    color = Lulu.ink(sound.family).copy(alpha = if (faded) 0.35f else 1f),
                    softWrap = false,
                    style = style.copy(fontSize = size, lineHeight = size),
                    modifier = Modifier.graphicsLayer { scaleX = grow; scaleY = grow },
                )
            }
        }
    }
}

/** The capital to match with its lower-case letter. Tapping it plays the sound. */
@Composable
private fun CapitalToMatch(sound: Sound, state: SessionViewModel) {
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(state.soundKey) {
        if (state.soundKey == 0) return@LaunchedEffect
        wiggle.animateTo(1f, tween(120))
        wiggle.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 250f))
    }
    Box(
        Modifier
            .padding(top = 4.dp, bottom = 8.dp)
            .size(width = 220.dp, height = 160.dp)
            .graphicsLayer {
                val s = 1f + 0.1f * wiggle.value
                scaleX = s
                scaleY = s
            }
            .bouncyClick(onClick = state::onSpeaker),
        contentAlignment = Alignment.Center,
    ) {
        Grapheme(sound.capitals, Lulu.ink(sound.family), Modifier.fillMaxSize())
    }
}

/** Pebbles to tap, under a [header]: the speaker, or the capital to match. */
@Composable
private fun Choices(target: Item, choices: List<Item>, lowercase: Boolean, state: SessionViewModel, header: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        header()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            val n = choices.size
            val rows = if (n <= 2) 1 else 2
            val side = min(maxWidth.value * 0.47f, maxHeight.value / rows * 0.92f).dp
            val firstRow = choices.take(2)
            val secondRow = choices.drop(2)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-side.value * 0.06f).dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(side * 0.02f)) {
                    firstRow.forEachIndexed { i, s -> Pebble(s, target, choices, lowercase, state, i, side) }
                }
                if (secondRow.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(side * 0.02f)) {
                        secondRow.forEachIndexed { i, s -> Pebble(s, target, choices, lowercase, state, i + 2, side) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigSpeaker(playing: Boolean, soundKey: Int, onClick: () -> Unit) {
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(soundKey) {
        if (soundKey == 0) return@LaunchedEffect
        wiggle.animateTo(1f, tween(120))
        wiggle.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 250f))
    }
    val alpha by animateFloatAsState(if (playing) 1f else 0.9f, label = "speakerAlpha")
    Box(
        Modifier
            .padding(top = 8.dp, bottom = 8.dp)
            .size(150.dp)
            .graphicsLayer {
                val s = 1f + 0.12f * wiggle.value
                scaleX = s
                scaleY = s
                this.alpha = alpha
            }
            .bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LuluIcon(Glyph.Speaker, Lulu.Terracotta, Modifier.size(130.dp))
    }
}

@Composable
private fun Pebble(sound: Item, target: Item, choices: List<Item>, lowercase: Boolean, state: SessionViewModel, index: Int, side: Dp) {
    val wrong = sound.id in state.wrongChoices
    val right = state.rightChoice == sound.id
    val dimmed = wrong || (state.rightChoice != null && !right)
    val alpha by animateFloatAsState(if (dimmed) 0.32f else 1f, tween(250), label = "pebbleAlpha")
    val grow by animateFloatAsState(
        targetValue = if (right) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 300f),
        label = "pebbleGrow",
    )
    val shake = rememberShake(if (state.shakingChoice == sound.id) state.shakeKey else 0)
    val appear = remember(target, choices, state.stepIndex) { Animatable(0f) }
    LaunchedEffect(appear) {
        kotlinx.coroutines.delay(80L * index)
        appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
    }
    val fill = Lulu.pebbles[(index + target.id.length) % Lulu.pebbles.size]
    Box(
        Modifier
            .size(side)
            .offset { IntOffset((shake.value * density).roundToInt(), 0) }
            .graphicsLayer {
                val s = appear.value * grow
                scaleX = s
                scaleY = s
                this.alpha = alpha
            }
            .bouncyClick(enabled = !state.busy && !wrong) { state.onChoice(sound) }
            .clip(PebbleShape(sound.id.hashCode() + index))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Grapheme(itemText(sound, lowercase, plain = Lulu.Ink), Modifier.fillMaxSize().padding(side * 0.16f), fill = 0.95f)
    }
}

@Composable
private fun Finished(didSomething: Boolean, mascotColor: Color, look: MascotLook, jumpKey: Int, onExit: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Mascot(
            mascotColor,
            if (didSomething) Mood.Happy else Mood.Sleepy,
            Modifier.size(240.dp),
            look = look,
            jumpKey = jumpKey,
        )
        Spacer(Modifier.height(32.dp))
        RoundButton(Glyph.Home, onExit, size = 96.dp, fill = Lulu.Terracotta, tint = Lulu.Cream)
    }
}
