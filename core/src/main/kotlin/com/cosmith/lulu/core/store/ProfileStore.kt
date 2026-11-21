package com.cosmith.lulu.core.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Keeps profiles and progress as JSON files in one folder on the phone.
 * Nothing leaves the device. Each write goes to a temporary file first and then
 * replaces the old one, so a crash never leaves half a file behind.
 */
class ProfileStore(private val dir: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()

    init {
        dir.mkdirs()
    }

    fun profiles(): List<Profile> = synchronized(lock) {
        read(profilesFile, ListSerializer(Profile.serializer())) ?: emptyList()
    }

    fun saveProfile(profile: Profile) = synchronized(lock) {
        val all = profiles().filterNot { it.id == profile.id } + profile
        write(profilesFile, ListSerializer(Profile.serializer()), all.sortedBy { it.createdAt })
    }

    fun deleteProfile(id: String) = synchronized(lock) {
        write(profilesFile, ListSerializer(Profile.serializer()), profiles().filterNot { it.id == id })
        progressFile(id).delete()
    }

    fun progress(profileId: String): Progress = synchronized(lock) {
        read(progressFile(profileId), Progress.serializer()) ?: Progress()
    }

    fun saveProgress(profileId: String, progress: Progress) = synchronized(lock) {
        write(progressFile(profileId), Progress.serializer(), progress)
    }

    private val profilesFile get() = File(dir, "profiles.json")
    private fun progressFile(id: String) = File(dir, "progress-${id.filter { it.isLetterOrDigit() || it == '-' }}.json")

    private fun <T> read(file: File, serializer: KSerializer<T>): T? {
        if (!file.exists()) return null
        return json.decodeFromString(serializer, file.readText())
    }

    private fun <T> write(file: File, serializer: KSerializer<T>, value: T) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(serializer, value))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("could not replace $file")
        }
    }
}
