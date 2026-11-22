package com.cosmith.lulu.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cosmith.lulu.AppContainer
import com.cosmith.lulu.audio.SoundRecorder
import com.cosmith.lulu.audio.SoundSource
import com.cosmith.lulu.core.content.Curriculum
import com.cosmith.lulu.core.content.Sound
import com.cosmith.lulu.core.fsrs.CardState
import com.cosmith.lulu.core.fsrs.Rating
import com.cosmith.lulu.core.session.SessionPlanner
import com.cosmith.lulu.core.store.CardKey
import com.cosmith.lulu.core.store.CardKind
import com.cosmith.lulu.core.store.CardProgress
import com.cosmith.lulu.core.store.LearningSettings
import com.cosmith.lulu.core.store.Profile
import com.cosmith.lulu.core.store.Progress
import com.cosmith.lulu.ui.components.Glyph
import com.cosmith.lulu.ui.components.Mascot
import com.cosmith.lulu.ui.components.Mood
import com.cosmith.lulu.ui.components.RoundButton
import com.cosmith.lulu.ui.components.bouncyClick
import com.cosmith.lulu.ui.theme.Lulu
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

@Composable
fun ParentScreen(
    c: AppContainer,
    profiles: List<Profile>,
    initialProfileId: String?,
    onBack: () -> Unit,
    onSaveSettings: (String, LearningSettings) -> Unit,
    onEditProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onDeleteProfile: (String) -> Unit,
) {
    var selectedId by remember { mutableStateOf(initialProfileId ?: profiles.firstOrNull()?.id) }
    val profile = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    var progress by remember(profile?.id) { mutableStateOf(profile?.let { c.store.progress(it.id) } ?: Progress()) }
    var toDelete by remember { mutableStateOf<Profile?>(null) }
    val now = c.now()

    LazyColumn(
        Modifier.fillMaxSize().background(Lulu.Cream).systemBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Glyph.Back, onBack, tint = Lulu.Terracotta)
                Spacer(Modifier.width(16.dp))
                Text("Espace parent", style = MaterialTheme.typography.headlineSmall, color = Lulu.Navy)
            }
        }
        if (profiles.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(profiles, key = { it.id }) { p ->
                        Pill(
                            text = p.firstName.ifBlank { "Profil ${profiles.indexOf(p) + 1}" },
                            selected = p.id == profile?.id,
                            color = mascotColor(p),
                            onClick = { selectedId = p.id },
                        )
                    }
                }
            }
        }

        if (profile != null) {
            progressSection(profile, progress, now)
            answersSection(progress, now) { index, rating ->
                progress = progress.correct(index, rating, c.scheduler)
                c.saveProgress(profile.id, progress)
            }
            item { SettingsCard(profile.settings) { onSaveSettings(profile.id, it) } }
        }

        item { SectionTitle("Sons de référence") }
        item {
            Text(
                "Enregistrez votre voix pour chaque son : elle remplace la voix de synthèse, approximative pour les consonnes. " +
                    "Dites le son seul, bien tenu (« fff », « ou »), pas le nom de la lettre.",
                color = Lulu.Muted, fontSize = 14.sp,
            )
        }
        items(Curriculum.sounds, key = { "rec-" + it.id }) { sound -> RecordingRow(c, sound) }

        item { SectionTitle("Profils") }
        items(profiles, key = { "profile-" + it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.6f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mascot(mascotColor(p), Mood.Idle, Modifier.size(44.dp), look = mascotLook(p))
                Spacer(Modifier.width(12.dp))
                Text(p.firstName.ifBlank { "Sans prénom" }, Modifier.weight(1f), color = Lulu.Ink, fontSize = 17.sp)
                TextButton(onClick = { onEditProfile(p.id) }) { Text("Modifier", color = Lulu.Navy) }
                TextButton(onClick = { toDelete = p }) { Text("Supprimer", color = Lulu.Brick) }
            }
        }
        item {
            TextButton(onClick = onAddProfile) { Text("+ Ajouter un enfant", color = Lulu.Terracotta, fontSize = 17.sp) }
        }
        item {
            Text(
                "Tout reste sur ce téléphone : pas de compte, pas de serveur. Lulu n'a pas accès à internet.",
                color = Lulu.Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer ce profil ?") },
            text = { Text("Les progrès de ${p.firstName.ifBlank { "cet enfant" }} seront effacés. On ne peut pas revenir en arrière.") },
            confirmButton = {
                TextButton(onClick = { toDelete = null; onDeleteProfile(p.id) }) { Text("Supprimer", color = Lulu.Brick) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}

private fun LazyListScope.progressSection(profile: Profile, progress: Progress, now: Instant) {
    item { SectionTitle("Progrès" + if (profile.firstName.isNotBlank()) " de ${profile.firstName}" else "") }
    val sounds = progress.introducedSounds()
    item {
        val solid = sounds.count { s -> CardKind.entries.all { k -> status(progress.card(CardKey(s.id, k))) == Status.Solid } }
        val nextNew = Curriculum.orderFor(profile.firstName).firstOrNull { it.id !in progress.introduced }
        Text(
            buildString {
                append("${sounds.size} sons rencontrés sur ${Curriculum.sounds.size}, dont $solid solides. ")
                append("${progress.reviews.size} réponses en tout.")
                if (nextNew != null) append(" Prochain son : « ${nextNew.grapheme} ».")
            },
            color = Lulu.Ink,
        )
    }
    if (sounds.isEmpty()) {
        item { Text("Aucun son pour l'instant : lancez une première partie.", color = Lulu.Muted) }
    }
    items(sounds, key = { "progress-" + it.id }) { sound ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GraphemeChip(sound)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                CardLine("Écouter", progress.card(CardKey(sound.id, CardKind.ListenAndChoose)), now)
                CardLine("Dire", progress.card(CardKey(sound.id, CardKind.SeeAndSay)), now)
                // Lower case: how far along its least advanced card is.
                val lower = CardKind.lowercase.mapNotNull { progress.card(CardKey(sound.id, it)) }
                if (lower.isNotEmpty()) {
                    val weakest = lower.minBy { status(it).ordinal }
                    CardLine("Minuscules « ${sound.lowercase} »", weakest, now)
                }
            }
        }
    }
}

private fun LazyListScope.answersSection(progress: Progress, now: Instant, onCorrect: (Int, Rating) -> Unit) {
    item { SectionTitle("Dernières réponses") }
    item {
        Text(
            "Lulu s'est trompée ? Corrigez la réponse : la carte est replanifiée comme si elle avait été notée juste.",
            color = Lulu.Muted, fontSize = 14.sp,
        )
    }
    val recent = progress.reviews.withIndex().reversed().take(15)
    if (recent.isEmpty()) item { Text("Pas encore de réponse.", color = Lulu.Muted) }
    items(recent, key = { "review-" + it.index }) { (index, r) ->
        val sound = Curriculum.find(r.key.soundId) ?: return@items
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GraphemeChip(sound, lowercase = r.key.kind.lowercase && r.key.kind != CardKind.MatchCase)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    kindLabel(r.key.kind) + " · " + ratingLabel(r.rating) +
                        if (r.corrected) " (corrigé)" else "",
                    color = if (r.rating == Rating.Again) Lulu.Brick else Lulu.Ink,
                    fontSize = 15.sp,
                )
                Text(ago(r.at, now), color = Lulu.Muted, fontSize = 13.sp)
            }
            TextButton(onClick = { onCorrect(index, if (r.rating == Rating.Again) Rating.Good else Rating.Again) }) {
                Text(if (r.rating == Rating.Again) "C'était juste" else "C'était faux", color = Lulu.Navy, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SettingsCard(settings: LearningSettings, onChange: (LearningSettings) -> Unit) {
    var s by remember(settings) { mutableStateOf(settings) }
    val range = LearningSettings.MINUTES
    Column(Modifier.padding(top = 8.dp)) {
        SectionTitle("Séance")
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Durée", Modifier.weight(1f), color = Lulu.Ink)
            RoundButton(Glyph.Minus, { s = s.copy(sessionMinutes = s.sessionMinutes - 1); onChange(s) }, size = 40.dp, enabled = s.sessionMinutes > range.first)
            Text(
                "${s.sessionMinutes} min",
                Modifier.widthIn(min = 76.dp).padding(horizontal = 8.dp),
                color = Lulu.Navy, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
            RoundButton(Glyph.Plus, { s = s.copy(sessionMinutes = s.sessionMinutes + 1); onChange(s) }, size = 40.dp, enabled = s.sessionMinutes < range.last)
        }
        Text(
            "Lulu choisit seule les cartes, les nouveaux sons et le nombre de choix d'après les réponses. " +
                "Quand le temps est écoulé, la séance se termine après la carte en cours.",
            color = Lulu.Muted, fontSize = 14.sp,
        )
    }
}

@Composable
private fun RecordingRow(c: AppContainer, sound: Sound) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { SoundRecorder(context) }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    var source by remember { mutableStateOf(c.sounds.source(sound)) }
    var recording by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            recorder.start(c.sounds.recordingFile(sound))
            recording = true
        }
    }

    fun stopAndPlay() {
        recording = false
        if (recorder.stop()) {
            source = c.sounds.source(sound)
            scope.launch { c.sounds.play(sound) }
        }
    }

    // Stop by itself after 4 seconds: a sound is short.
    LaunchedEffect(recording, version) {
        if (recording) {
            delay(4_000)
            if (recording) stopAndPlay()
        }
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GraphemeChip(sound)
        Spacer(Modifier.width(12.dp))
        Text(
            when {
                recording -> "Enregistrement…"
                source == SoundSource.Recording -> "Votre voix"
                source == SoundSource.Bundled -> "Fourni avec l'appli"
                else -> "Voix de synthèse"
            },
            Modifier.weight(1f),
            color = if (recording) Lulu.Brick else Lulu.Muted,
            fontSize = 14.sp,
        )
        RoundButton(Glyph.Speaker, { scope.launch { c.sounds.play(sound) } }, size = 40.dp, enabled = !recording)
        Spacer(Modifier.width(8.dp))
        RoundButton(
            if (recording) Glyph.Stop else Glyph.Mic,
            {
                if (recording) {
                    stopAndPlay()
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    c.sounds.stop()
                    recorder.start(c.sounds.recordingFile(sound))
                    recording = true
                    version++
                } else {
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            size = 40.dp,
            fill = if (recording) Lulu.Terracotta else Lulu.Sand,
            tint = if (recording) Lulu.Cream else Lulu.Navy,
        )
        if (source == SoundSource.Recording && !recording) {
            Spacer(Modifier.width(8.dp))
            RoundButton(Glyph.Trash, { c.sounds.deleteRecording(sound); source = c.sounds.source(sound) }, size = 40.dp, tint = Lulu.Brick)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Lulu.Navy, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun GraphemeChip(sound: Sound, lowercase: Boolean = false) {
    Box(
        Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(14.dp)).background(Lulu.Sand),
        contentAlignment = Alignment.Center,
    ) {
        Text(sound.written(lowercase), color = Lulu.ink(sound.family), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun kindLabel(kind: CardKind) = when (kind) {
    CardKind.SeeAndSay -> "Dire"
    CardKind.ListenAndChoose -> "Écouter"
    CardKind.MatchCase -> "Majuscule → minuscule"
    CardKind.ListenAndChooseLower -> "Écouter (minuscules)"
    CardKind.SeeAndSayLower -> "Dire (minuscules)"
}

@Composable
private fun Pill(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .bouncyClick(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Lulu.Navy else Lulu.Sand)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (selected) Lulu.Cream else Lulu.Navy)
    }
}

@Composable
private fun CardLine(label: String, card: CardProgress?, now: Instant) {
    val st = status(card)
    Text(
        buildString {
            append(label).append(" : ").append(st.label)
            if (card != null && card.reviews > 0) append(" · ").append(dueLabel(card.memory.due, now))
        },
        color = if (st == Status.Solid) Lulu.Sage else Lulu.Ink,
        fontSize = 14.sp,
    )
}

private enum class Status(val label: String) {
    Locked("pas encore"),
    New("nouveau"),
    Learning("en cours"),
    Settling("à consolider"),
    Solid("solide"),
}

private fun status(card: CardProgress?): Status = when {
    card == null -> Status.Locked
    card.reviews == 0 -> Status.New
    card.memory.state != CardState.Review -> Status.Learning
    (card.memory.stability ?: 0.0) < SessionPlanner.SOLID_DAYS -> Status.Settling
    else -> Status.Solid
}

private fun ratingLabel(r: Rating) = when (r) {
    Rating.Again -> "À revoir"
    Rating.Hard -> "Difficile"
    Rating.Good -> "Bien"
    Rating.Easy -> "Facile"
}

private fun dueLabel(due: Instant, now: Instant): String {
    val d = Duration.between(now, due)
    return when {
        d.isNegative || d.toMinutes() < 1 -> "à revoir maintenant"
        d.toHours() < 1 -> "revient dans ${d.toMinutes()} min"
        d.toDays() < 1 -> "revient dans ${d.toHours()} h"
        else -> "revient dans ${d.toDays()} j"
    }
}

private fun ago(at: Instant, now: Instant): String {
    val d = Duration.between(at, now)
    return when {
        d.toMinutes() < 1 -> "à l'instant"
        d.toHours() < 1 -> "il y a ${d.toMinutes()} min"
        d.toDays() < 1 -> "il y a ${d.toHours()} h"
        else -> "il y a ${d.toDays()} j"
    }
}
