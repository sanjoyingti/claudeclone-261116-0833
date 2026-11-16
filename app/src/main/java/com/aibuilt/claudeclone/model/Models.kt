package com.aibuilt.claudeclone.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** The request/response shape used to talk to the endpoint. */
@Serializable
enum class Dialect { OPENAI, ANTHROPIC, GOOGLE, CUSTOM }

@Serializable
enum class Role { USER, ASSISTANT }

/** One extra header row in the settings table. */
@Serializable
data class HeaderPair(val key: String = "", val value: String = "")

/** A file the user attached: kept as base64 plus its mime type. */
@Serializable
data class Attachment(
    val name: String = "file",
    val mime: String = "application/octet-stream",
    val base64: String = ""
) {
    val isImage: Boolean get() = mime.startsWith("image/")
    val isPdf: Boolean get() = mime == "application/pdf"
}

/**
 * A fully user-editable connection profile. Nothing here is whitelisted:
 * every field is free text and every request is assembled from this object.
 */
@Serializable
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New profile",
    val baseUrl: String = "",
    val authHeaderName: String = "",
    val authValuePrefix: String = "",
    val apiKey: String = "",
    val extraHeaders: List<HeaderPair> = emptyList(),
    val model: String = "",
    val pathTemplate: String = "{base}",
    val dialect: Dialect = Dialect.ANTHROPIC,
    val responsePath: String = "content[0].text",
    val stream: Boolean = true,
    val customBody: String = ""
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val attachment: Attachment? = null,
    val streaming: Boolean = false,
    val error: Boolean = false,
    val errorDetail: String = ""
)

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val createdAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
)

/** Result of one send attempt. */
sealed interface SendResult {
    data class Success(val text: String) : SendResult
    data class Failure(val url: String, val exceptionClass: String, val message: String) : SendResult
}

/** Events coming out of a streaming reply. */
sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Failed(val url: String, val exceptionClass: String, val message: String) : StreamEvent
    data object Completed : StreamEvent
}

/** Result of the Test connection button. */
data class TestOutput(
    val url: String = "",
    val status: String = "",
    val exceptionClass: String = "",
    val bodyHead: String = "",
    val ran: Boolean = false
)
