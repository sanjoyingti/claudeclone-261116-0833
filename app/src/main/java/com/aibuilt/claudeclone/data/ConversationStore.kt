package com.aibuilt.claudeclone.data

import android.content.Context
import com.aibuilt.claudeclone.model.ChatMessage
import com.aibuilt.claudeclone.model.Conversation
import com.aibuilt.claudeclone.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ConversationFile(val conversations: List<Conversation> = emptyList())

class ConversationStore(context: Context) {

    private val file = File(context.filesDir, "claude_conversations.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow(ConversationFile())

    val conversations: Flow<List<Conversation>> = state.map { it.conversations }

    init {
        val stored = runCatching {
            if (file.exists()) json.decodeFromString<ConversationFile>(file.readText()) else null
        }.getOrNull()
        if (stored != null) state.value = stored
    }

    fun snapshot(): List<Conversation> = state.value.conversations

    fun messages(id: String): List<ChatMessage> =
        state.value.conversations.firstOrNull { it.id == id }?.messages.orEmpty()

    fun create(): String {
        val conv = Conversation()
        mutate { it.copy(conversations = listOf(conv) + it.conversations) }
        return conv.id
    }

    fun rename(id: String, title: String) {
        mutate { snap ->
            snap.copy(conversations = snap.conversations.map {
                if (it.id == id) it.copy(title = title.trim().ifBlank { "New chat" }) else it
            })
        }
    }

    fun delete(id: String) {
        mutate { snap -> snap.copy(conversations = snap.conversations.filterNot { it.id == id }) }
    }

    fun addMessage(conversationId: String, message: ChatMessage): String {
        mutate { snap ->
            snap.copy(conversations = snap.conversations.map { conv ->
                if (conv.id != conversationId) conv
                else conv.copy(
                    title = if (conv.messages.none { it.role == Role.USER } && message.role == Role.USER)
                        message.text.trim().ifBlank { message.attachment?.name ?: "New chat" }.take(60)
                    else conv.title,
                    messages = conv.messages + message
                )
            })
        }
        return message.id
    }

    fun updateMessage(conversationId: String, messageId: String, block: (ChatMessage) -> ChatMessage) {
        mutate { snap ->
            snap.copy(conversations = snap.conversations.map { conv ->
                if (conv.id != conversationId) conv
                else conv.copy(messages = conv.messages.map { if (it.id == messageId) block(it) else it })
            })
        }
    }

    fun appendToMessage(conversationId: String, messageId: String, extra: String) {
        updateMessage(conversationId, messageId) { it.copy(text = it.text + extra) }
    }

    fun dropTrailingAssistants(conversationId: String) {
        mutate { snap ->
            snap.copy(conversations = snap.conversations.map { conv ->
                if (conv.id != conversationId) conv
                else {
                    var list = conv.messages
                    while (list.isNotEmpty() && list.last().role == Role.ASSISTANT) {
                        list = list.dropLast(1)
                    }
                    conv.copy(messages = list)
                }
            })
        }
    }

    private fun mutate(block: (ConversationFile) -> ConversationFile) {
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
