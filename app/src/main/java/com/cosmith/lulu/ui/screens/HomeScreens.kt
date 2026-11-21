package com.cosmith.lulu.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import com.cosmith.lulu.AppContainer
import com.cosmith.lulu.R
import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.session.SessionPlanner
import com.cosmith.lulu.core.store.Profile
import com.cosmith.lulu.ui.components.Glyph
import com.cosmith.lulu.ui.components.Hill
import com.cosmith.lulu.ui.components.LuluIcon
import com.cosmith.lulu.ui.components.Mascot
import com.cosmith.lulu.ui.components.Mood
import com.cosmith.lulu.ui.components.RoundButton
import com.cosmith.lulu.ui.components.bouncyClick
import com.cosmith.lulu.ui.components.rememberShake
import com.cosmith.lulu.ui.theme.Lulu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

fun mascotColor(profile: Profile) = Lulu.mascots[profile.avatar % Lulu.mascots.size]

@Composable
fun HomeScreen(
    c: AppContainer,
    profile: Profile,
    canSwitch: Boolean,
    onPlay: () -> Unit,
    onSwitch: () -> Unit,
    onParent: () -> Unit,
) {
    // Is there anything to do right now? Otherwise Lulu sleeps and the child comes back later.
    var hasWork by remember { mutableStateOf(true) }
    LaunchedEffect(profile) {
        hasWork = withContext(Dispatchers.IO) {
            SessionPlanner().plan(profile, c.store.progress(profile.id), c.now()).isNotEmpty()
        }
    }
    val pulse by rememberInfiniteTransition(label = "play").animateFloat(
        initialValue = 1f, targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(Modifier.fillMaxSize().background(Lulu.Cream)) {
        Hill(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(130.dp))
        Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canSwitch) {
                    Box(
                        Modifier.size(56.dp).bouncyClick(onClick = onSwitch).clip(CircleShape).background(Lulu.Sand),
                        contentAlignment = Alignment.Center,
                    ) {
                        Mascot(mascotColor(profile), Mood.Idle, Modifier.size(42.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                RoundButton(Glyph.Gear, onParent, tint = Lulu.Navy.copy(alpha = 0.75f))
            }
            Image(
                painter = painterResource(R.drawable.lulu_logo),
                contentDescription = "Lulu",
                modifier = Modifier.padding(top = 8.dp).size(250.dp),
            )
            Spacer(Modifier.weight(1f))
            if (hasWork) {
                Box(
                    Modifier
                        .size(150.dp)
                        .graphicsLayer { scaleX = pulse; scaleY = pulse }
                        .bouncyClick(onClick = onPlay)
                        .clip(CircleShape)
                        .background(Lulu.Terracotta),
                    contentAlignment = Alignment.Center,
                ) {
                    LuluIcon(Glyph.Play, Lulu.Cream, Modifier.size(78.dp))
                }
            } else {
                Text("À plus tard !", color = Lulu.Muted, fontSize = 18.sp)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, bottom = 16.dp)) {
                Mascot(mascotColor(profile), if (hasWork) Mood.Idle else Mood.Sleepy, Modifier.size(120.dp))
            }
        }
    }
}

/** "Qui joue ?": each child finds their own mascot by its colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(profiles: List<Profile>, onPick: (Profile) -> Unit, onParent: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Lulu.Cream)) {
        Hill(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp))
        Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Spacer(Modifier.weight(1f))
                RoundButton(Glyph.Gear, onParent, tint = Lulu.Navy.copy(alpha = 0.75f))
            }
            Image(painterResource(R.drawable.lulu_logo), "Lulu", Modifier.size(170.dp))
            Spacer(Modifier.weight(1f))
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                profiles.forEach { p ->
                    Column(
                        Modifier.width(140.dp).bouncyClick { onPick(p) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Mascot(mascotColor(p), Mood.Idle, Modifier.size(130.dp))
                        if (p.firstName.isNotBlank()) {
                            Text(p.firstName, color = Lulu.Navy, fontSize = 20.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1.4f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    existing: Profile?,
    firstRun: Boolean,
    onSave: (firstName: String, avatar: Int) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(existing?.firstName.orEmpty()) }
    var avatar by remember { mutableIntStateOf(existing?.avatar ?: 0) }
    var jump by remember { mutableIntStateOf(0) }
    val initial = Curriculum.initialSoundOf(name)

    Column(
        Modifier
            .fillMaxSize()
            .background(Lulu.Cream)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onCancel != null) {
            Row(Modifier.fillMaxWidth()) { RoundButton(Glyph.Back, onCancel, tint = Lulu.Terracotta) }
        }
        if (firstRun) {
            Image(painterResource(R.drawable.lulu_logo), "Lulu", Modifier.size(190.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                "Bienvenue ! Lulu apprend aux enfants le son des lettres, en jouant quelques minutes par jour. " +
                    "Commençons par le profil de votre enfant.",
                style = MaterialTheme.typography.bodyLarge,
                color = Lulu.Ink,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                if (existing == null) "Nouveau profil" else "Modifier le profil",
                style = MaterialTheme.typography.headlineSmall,
                color = Lulu.Navy,
            )
        }
        Spacer(Modifier.height(20.dp))
        Mascot(Lulu.mascots[avatar], Mood.Happy, Modifier.size(150.dp), jumpKey = jump)
        Spacer(Modifier.height(12.dp))
        Text("Sa couleur, pour qu'il ou elle se reconnaisse :", color = Lulu.Muted)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Lulu.mascots.forEachIndexed { i, color ->
                Box(
                    Modifier
                        .size(48.dp)
                        .bouncyClick { avatar = i; jump++ }
                        .border(if (i == avatar) 4.dp else 0.dp, Lulu.Navy, CircleShape)
                        .padding(7.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Prénom (facultatif)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when {
                name.isBlank() -> "Le prénom reste sur ce téléphone. Il sert à placer tôt le son de son initiale."
                initial != null -> "Le son « ${initial.grapheme} » de son initiale viendra tôt dans le parcours."
                else -> "Son initiale ne correspond pas à un son du début du parcours : l'ordre ne change pas."
            },
            color = Lulu.Muted,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { onSave(name, avatar) },
            colors = ButtonDefaults.buttonColors(containerColor = Lulu.Terracotta),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (firstRun) "C'est parti" else "Enregistrer", fontSize = 18.sp)
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel) { Text("Annuler", color = Lulu.Muted) }
        }
    }
}

/**
 * A small lock a 3-year-old cannot open alone: a multiplication.
 * Three wrong answers give a new question.
 */
@Composable
fun ParentGateScreen(onUnlock: () -> Unit, onCancel: () -> Unit) {
    var question by remember { mutableStateOf(newQuestion()) }
    var typed by remember { mutableStateOf("") }
    var shakeKey by remember { mutableIntStateOf(0) }
    val shake = rememberShake(shakeKey)
    val density = LocalDensity.current

    Column(
        Modifier.fillMaxSize().background(Lulu.Cream).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) { RoundButton(Glyph.Back, onCancel, tint = Lulu.Terracotta) }
        Spacer(Modifier.height(24.dp))
        Text("Espace parent", style = MaterialTheme.typography.headlineSmall, color = Lulu.Navy)
        Spacer(Modifier.height(8.dp))
        Text("Pour entrer, combien font ${question.first} × ${question.second} ?", color = Lulu.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .offset { IntOffset((shake.value * density.density).roundToInt(), 0) }
                .width(160.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Lulu.Sand),
            contentAlignment = Alignment.Center,
        ) {
            Text(typed.ifEmpty { " " }, fontSize = 34.sp, color = Lulu.Navy)
        }
        Spacer(Modifier.weight(1f))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        Box(
                            Modifier
                                .size(width = 88.dp, height = 64.dp)
                                .bouncyClick {
                                    when (key) {
                                        "⌫" -> typed = typed.dropLast(1)
                                        "OK" -> {
                                            if (typed.toIntOrNull() == question.first * question.second) {
                                                onUnlock()
                                            } else {
                                                shakeKey++
                                                typed = ""
                                                question = newQuestion()
                                            }
                                        }
                                        else -> if (typed.length < 3) typed += key
                                    }
                                }
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (key == "OK") Lulu.Terracotta else Lulu.Sand),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(key, fontSize = 24.sp, color = if (key == "OK") Lulu.Cream else Lulu.Navy)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun newQuestion() = Pair(Random.nextInt(3, 10), Random.nextInt(6, 10))
