package com.aibuilt.claudeclone.data

import android.content.Context
import com.aibuilt.claudeclone.model.Presets
import com.aibuilt.claudeclone.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ProfileFile(
    val profiles: List<Profile> = emptyList(),
    val activeId: String? = null,
    val defaultId: String? = null
)

class ProfileStore(context: Context) {

    private val file = File(context.filesDir, "claude_profiles.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val state = MutableStateFlow(ProfileFile())

    val profiles: Flow<List<Profile>> = state.map { it.profiles }
    val raw: Flow<ProfileFile> = state

    val activeProfile: Flow<Profile?> = state.map { snap -> resolve(snap) }

    val defaultId: Flow<String?> = state.map { it.defaultId }

    init {
        val stored = runCatching {
            if (file.exists()) json.decodeFromString<ProfileFile>(file.readText()) else null
        }.getOrNull()
        if (stored == null || stored.profiles.isEmpty()) {
            val seed = Presets.anthropicDefault()
            state.value = ProfileFile(listOf(seed), seed.id, seed.id)
            persist()
        } else {
            state.value = stored
        }
    }

    private fun resolve(snap: ProfileFile): Profile? =
        snap.profiles.firstOrNull { it.id == snap.activeId }
            ?: snap.profiles.firstOrNull { it.id == snap.defaultId }
            ?: snap.profiles.firstOrNull()

    fun current(): Profile? = resolve(state.value)

    fun snapshot(): ProfileFile = state.value

    fun profileById(id: String?): Profile? = state.value.profiles.firstOrNull { it.id == id }

    fun add(profile: Profile): String {
        mutate { snap ->
            val first = snap.profiles.isEmpty()
            snap.copy(
                profiles = snap.profiles + profile,
                activeId = if (first) profile.id else snap.activeId,
                defaultId = if (first) profile.id else snap.defaultId
            )
        }
        return profile.id
    }

    fun save(profile: Profile) {
        mutate { snap ->
            val exists = snap.profiles.any { it.id == profile.id }
            val list = if (exists) snap.profiles.map { if (it.id == profile.id) profile else it } else snap.profiles + profile
            snap.copy(
                profiles = list,
                activeId = profile.id,
                defaultId = if (snap.defaultId == null) profile.id else snap.defaultId
            )
        }
    }

    fun delete(id: String) {
        mutate { snap ->
            val remaining = snap.profiles.filterNot { it.id == id }
            snap.copy(
                profiles = remaining,
                activeId = if (snap.activeId == id) remaining.firstOrNull()?.id else snap.activeId,
                defaultId = if (snap.defaultId == id) remaining.firstOrNull()?.id else snap.defaultId
            )
        }
    }

    fun setActive(id: String) {
        mutate { it.copy(activeId = id) }
    }

    fun setDefault(id: String) {
        mutate { it.copy(defaultId = id) }
    }

    private fun mutate(block: (ProfileFile) -> ProfileFile) {
        state.value = block(state.value)
        persist()
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(state.value))
        }
    }
}
