package com.aibuilt.claudeclone.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibuilt.claudeclone.data.AppContainer
import com.aibuilt.claudeclone.model.Attachment
import com.aibuilt.claudeclone.model.ChatMessage
import com.aibuilt.claudeclone.model.Conversation
import com.aibuilt.claudeclone.model.Presets
import com.aibuilt.claudeclone.model.Profile
import com.aibuilt.claudeclone.model.Role
import com.aibuilt.claudeclone.model.SendResult
import com.aibuilt.claudeclone.model.StreamEvent
import com.aibuilt.claudeclone.network.RequestBuilder
import com.aibuilt.claudeclone.util.Attachments
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.Call

data class ChatError(val text: String, val url: String = "", val exceptionClass: String = "")

class ChatViewModel(private val container: AppContainer) : ViewModel() {

    private val store = container.conversationStore
    private val profileStore = container.profileStore
    private val engine = container.httpEngine

    val conversations: StateFlow<List<Conversation>> = store.conversations
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeProfile = profileStore.activeProfile

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> =
        combine(store.conversations, _activeId) { list, id ->
            list.firstOrNull { it.id == id }?.messages.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _input = MutableStateFlow("")
    val input = _input.asStateFlow()

    private val _pending = MutableStateFlow<Attachment?>(null)
    val pending = _pending.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming = _streaming.asStateFlow()

    private val _error = MutableStateFlow<ChatError?>(null)
    val error = _error.asStateFlow()

    private val _attaching = MutableStateFlow(false)
    val attaching = _attaching.asStateFlow()

    private var job: Job? = null
    private var call: Call? = null

    init {
        viewModelScope.launch {
            store.conversations.collect { list ->
                if (_activeId.value == null || list.none { it.id == _activeId.value }) {
                    _activeId.value = list.firstOrNull()?.id ?: store.create()
                }
            }
        }
    }

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun newChat() {
        stop()
        _error.value = null
        _pending.value = null
        _input.value = ""
        _activeId.value = store.create()
    }

    fun openConversation(id: String) {
        stop()
        _error.value = null
        _activeId.value = id
    }

    fun rename(id: String, title: String) = store.rename(id, title)

    fun delete(id: String) {
        store.delete(id)
        if (_activeId.value == id) {
            _activeId.value = store.snapshot().firstOrNull()?.id ?: store.create()
        }
    }

    fun attach(uri: Uri) {
        viewModelScope.launch {
            _attaching.value = true
            val loaded = Attachments.load(container.appContext, uri)
            _attaching.value = false
            if (loaded == null) {
                _error.value = ChatError("Could not read that file.")
            } else {
                _pending.value = loaded
            }
        }
    }

    fun clearAttachment() {
        _pending.value = null
    }

    fun dismissError() {
        _error.value = null
    }

    fun send() {
        if (_streaming.value) return
        val text = _input.value.trim()
        val attachment = _pending.value
        if (text.isEmpty() && attachment == null) return
        val conversationId = _activeId.value ?: store.create().also { _activeId.value = it }
        store.addMessage(
            conversationId,
            ChatMessage(role = Role.USER, text = text, attachment = attachment)
        )
        _input.value = ""
        _pending.value = null
        _error.value = null
        startCompletion(conversationId)
    }

    fun retry() {
        val conversationId = _activeId.value ?: return
        store.dropTrailingAssistants(conversationId)
        startCompletion(conversationId)
    }

    fun regenerate() {
        val conversationId = _activeId.value ?: return
        if (_streaming.value) return
        store.dropTrailingAssistants(conversationId)
        if (store.messages(conversationId).isEmpty()) return
        _error.value = null
        startCompletion(conversationId)
    }

    fun stop() {
        call?.cancel()
        call = null
        job?.cancel()
        job = null
        _streaming.value = false
        val conversationId = _activeId.value ?: return
        val last = store.messages(conversationId).lastOrNull()
        if (last != null && last.streaming) {
            store.updateMessage(conversationId, last.id) { it.copy(streaming = false) }
        }
    }

    private fun startCompletion(conversationId: String) {
        val stored = profileStore.current()
        if (stored == null) {
            _error.value = ChatError("No connection yet. Open Settings and paste your base URL and key.")
            return
        }
        val base = RequestBuilder.repairBase(stored.baseUrl)
        if (base.isBlank()) {
            _error.value = ChatError("Base URL is empty. Open Settings and paste your server address.")
            return
        }
        val profile = stored.copy(
            baseUrl = base,
            model = stored.model.trim().ifBlank { Presets.suggestedModel(stored.dialect) }
        )

        val history = store.messages(conversationId)
        if (history.isEmpty()) return

        val assistantId = store.addMessage(
            conversationId,
            ChatMessage(role = Role.ASSISTANT, text = "", streaming = true)
        )
        _streaming.value = true
        _error.value = null

        job = viewModelScope.launch {
            val prepared = runCatching { RequestBuilder.prepare(profile, history, base) }.getOrNull()
            if (prepared == null) {
                store.updateMessage(conversationId, assistantId) { it.copy(streaming = false) }
                _streaming.value = false
                _error.value = ChatError("Could not build the request URL. Check the path template.")
                return@launch
            }
            if (profile.stream) {
                val activeCall = engine.newCall(prepared)
                call = activeCall
                engine.stream(activeCall, prepared.url, profile.responsePath).collect { event ->
                    when (event) {
                        is StreamEvent.Delta -> store.appendToMessage(conversationId, assistantId, event.text)
                        is StreamEvent.Failed -> fail(conversationId, assistantId, event.url, event.exceptionClass, event.message)
                        StreamEvent.Completed -> Unit
                    }
                }
            } else {
                when (val result = engine.complete(prepared, profile.responsePath)) {
                    is SendResult.Success ->
                        store.appendToMessage(conversationId, assistantId, result.text)

                    is SendResult.Failure ->
                        fail(conversationId, assistantId, result.url, result.exceptionClass, result.message)
                }
            }
            call = null
            job = null
            _streaming.value = false
            store.updateMessage(conversationId, assistantId) { it.copy(streaming = false) }
        }
    }

    private fun fail(conversationId: String, assistantId: String, url: String, exceptionClass: String, message: String) {
        store.updateMessage(conversationId, assistantId) {
            it.copy(
                streaming = false,
                error = true,
                errorDetail = "$url\n$exceptionClass: $message"
            )
        }
        _error.value = ChatError(message, url, exceptionClass)
    }

    fun profileSummary(): Profile? = profileStore.current()
}
