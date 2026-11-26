@file:UseSerializers(InstantMillisSerializer::class)

package com.cosmith.lulu.core.store

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Everything Lulu knows about every child, in one document the parent can keep or
 * share: profiles, settings, cards, every answer with its time, and when each sound,
 * syllable, word and lower case was met. Times are milliseconds since the epoch;
 * [timeZone] tells which day each one fell on. Voice recordings are not included,
 * only which sounds have one.
 */
@Serializable
data class DataExport(
    val format: Int = FORMAT,
    val exportedAt: Instant,
    val appVersion: String,
    val timeZone: String,
    val profiles: List<Profile>,
    /** Progress by profile id. */
    val progress: Map<String, Progress>,
    /** Sound ids the parent has recorded. */
    val recordings: List<String> = emptyList(),
) {
    companion object {
        const val FORMAT = 1

        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = true
            prettyPrintIndent = " "
        }

        fun encode(export: DataExport): String = json.encodeToString(serializer(), export)

        fun decode(text: String): DataExport = json.decodeFromString(serializer(), text)
    }
}

/** Gathers every profile and its progress into one export. */
fun ProfileStore.exportAll(exportedAt: Instant, appVersion: String, timeZone: String, recordings: List<String>): DataExport {
    val profiles = profiles()
    return DataExport(
        exportedAt = exportedAt,
        appVersion = appVersion,
        timeZone = timeZone,
        profiles = profiles,
        progress = profiles.associate { it.id to progress(it.id) },
        recordings = recordings.sorted(),
    )
}
