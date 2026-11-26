package com.cosmith.lulu.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.cosmith.lulu.AppContainer
import com.cosmith.lulu.core.store.DataExport
import com.cosmith.lulu.core.store.exportAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Writes the full data export (see [DataExport]) for the parent: shared through the
 * Android share sheet, or saved to a file they pick. The app itself has no internet
 * access; the parent chooses where the file goes.
 */
object Export {
    fun fileName(c: AppContainer): String =
        "lulu-donnees-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(ZoneId.systemDefault()).format(c.now()) + ".json"

    private suspend fun text(c: AppContainer, context: Context): String = withContext(Dispatchers.IO) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        DataExport.encode(
            c.store.exportAll(c.now(), version ?: "?", ZoneId.systemDefault().id, c.sounds.recordedIds()),
        )
    }

    /** Opens the share sheet with the export as a JSON file. */
    suspend fun share(c: AppContainer, context: Context) {
        val text = text(c, context)
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // only the latest export stays
            File(dir, fileName(c)).apply { writeText(text) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Données Lulu")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Exporter les données de Lulu"))
    }

    /** Writes the export to a file the parent picked. */
    suspend fun save(c: AppContainer, context: Context, uri: Uri) {
        val text = text(c, context)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }
    }
}
