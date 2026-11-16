package com.aibuilt.claudeclone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibuilt.claudeclone.data.AppContainer
import com.aibuilt.claudeclone.model.ChatMessage
import com.aibuilt.claudeclone.model.Dialect
import com.aibuilt.claudeclone.model.HeaderPair
import com.aibuilt.claudeclone.model.Presets
import com.aibuilt.claudeclone.model.Profile
import com.aibuilt.claudeclone.model.Role
import com.aibuilt.claudeclone.model.TestOutput
import com.aibuilt.claudeclone.network.RequestBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val profiles: List<Profile> = emptyList(),
    val defaultId: String? = null,
    val draft: Profile = Presets.templateFor(Dialect.ANTHROPIC),
    val isNew: Boolean = false,
    val errors: List<String> = emptyList(),
    val testing: Boolean = false,
    val test: TestOutput = TestOutput(),
    val savedFlash: Boolean = false
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val store = container.profileStore
    private val engine = container.httpEngine

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.raw.collect { snap ->
                _state.update { current ->
                    val preferred = snap.activeId ?: snap.defaultId
                    val selected = snap.profiles.firstOrNull { it.id == preferred }
                    if (selected != null && (current.isNew || current.draft.id != selected.id)) {
                        current.copy(
                            profiles = snap.profiles,
                            defaultId = snap.defaultId,
                            draft = selected,
                            isNew = false
                        )
                    } else {
                        current.copy(profiles = snap.profiles, defaultId = snap.defaultId)
                    }
                }
            }
        }
    }

    fun selectProfile(id: String) {
        val profile = store.profileById(id) ?: return
        store.setActive(id)
        _state.update {
            it.copy(draft = profile, isNew = false, errors = emptyList(), test = TestOutput())
        }
    }

    fun newProfile() {
        val seed = Presets.templateFor(Dialect.ANTHROPIC)
        _state.update {
            it.copy(
                draft = seed.copy(name = "Profile ${it.profiles.size + 1}"),
                isNew = true,
                errors = emptyList(),
                test = TestOutput()
            )
        }
    }

    fun edit(block: (Profile) -> Profile) {
        _state.update { it.copy(draft = block(it.draft), errors = emptyList(), savedFlash = false) }
    }

    fun setDialect(dialect: Dialect) {
        _state.update { it.copy(draft = Presets.applyDefaults(it.draft, dialect), errors = emptyList()) }
    }

    fun setAuthHeader(value: String) = edit { it.copy(authHeaderName = value) }

    fun setAuthPrefix(value: String) = edit { it.copy(authValuePrefix = value) }

    fun setResponsePath(value: String) = edit { it.copy(responsePath = value) }

    fun addHeader() = edit { it.copy(extraHeaders = it.extraHeaders + HeaderPair()) }

    fun updateHeader(index: Int, key: String? = null, value: String? = null) = edit { profile ->
        profile.copy(extraHeaders = profile.extraHeaders.mapIndexed { position, pair ->
            if (position != index) pair
            else pair.copy(key = key ?: pair.key, value = value ?: pair.value)
        })
    }

    fun removeHeader(index: Int) = edit { profile ->
        profile.copy(extraHeaders = profile.extraHeaders.filterIndexed { position, _ -> position != index })
    }

    fun save(): Boolean {
        val draft = _state.value.draft
        val repaired = RequestBuilder.repairBase(draft.baseUrl)
        val errors = buildList {
            if (repaired.isBlank()) add("Base URL is empty. Enter the server address, for example https://api.example.com/v1")
            if (draft.model.isBlank()) add("Model field is empty. Enter the model name your server expects.")
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return false
        }
        val cleaned = draft.copy(
            name = draft.name.trim().ifBlank { "Profile" },
            baseUrl = repaired,
            model = draft.model.trim(),
            authHeaderName = draft.authHeaderName.trim(),
            authValuePrefix = draft.authValuePrefix.trim(),
            apiKey = draft.apiKey.trim(),
            pathTemplate = draft.pathTemplate.trim().ifBlank { "{base}" },
            responsePath = draft.responsePath.trim(),
            extraHeaders = draft.extraHeaders.filter { it.key.isNotBlank() }
        )
        store.save(cleaned)
        _state.update { it.copy(draft = cleaned, isNew = false, errors = emptyList(), savedFlash = true) }
        return true
    }

    fun delete(id: String) {
        store.delete(id)
        _state.update { it.copy(errors = emptyList(), test = TestOutput()) }
    }

    fun setDefault(id: String) = store.setDefault(id)

    fun testConnection() {
        val draft = _state.value.draft
        val repaired = RequestBuilder.repairBase(draft.baseUrl)
        val errors = buildList {
            if (repaired.isBlank()) add("Base URL is empty, nothing to test.")
            if (draft.model.isBlank()) add("Model field is empty, nothing to test.")
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        val probe = draft.copy(baseUrl = repaired, model = draft.model.trim(), stream = false)
        _state.update { it.copy(errors = emptyList(), testing = true, test = TestOutput()) }
        viewModelScope.launch {
            val prepared = runCatching {
                RequestBuilder.prepare(probe, listOf(ChatMessage(role = Role.USER, text = "ping")), repaired)
            }.getOrNull()
            if (prepared == null) {
                _state.update {
                    it.copy(
                        testing = false,
                        test = TestOutput(url = repaired, status = "could not build URL", ran = true)
                    )
                }
                return@launch
            }
            val output = engine.test(prepared, probe.responsePath)
            _state.update { it.copy(testing = false, test = output) }
        }
    }

    fun clearTest() = _state.update { it.copy(test = TestOutput()) }
}
