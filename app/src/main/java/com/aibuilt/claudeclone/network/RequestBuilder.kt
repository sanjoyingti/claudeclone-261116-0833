package com.aibuilt.claudeclone.network

import com.aibuilt.claudeclone.model.ChatMessage
import com.aibuilt.claudeclone.model.Dialect
import com.aibuilt.claudeclone.model.Presets
import com.aibuilt.claudeclone.model.Profile
import com.aibuilt.claudeclone.model.Role
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

data class PreparedRequest(
    val url: String,
    val headers: List<Pair<String, String>>,
    val body: String,
    val stream: Boolean
)

object RequestBuilder {

    private val knownEndpoints = listOf("/v1/messages", "/chat/completions")
    private val doubleSlash = Regex("(?<!:)/{2,}")

    private fun tidy(url: String): String {
        var value = url.replace(doubleSlash, "/")
        while (value.contains("/v1/v1/")) value = value.replace("/v1/v1/", "/v1/")
        while (value.contains("/v1beta/v1beta/")) value = value.replace("/v1beta/v1beta/", "/v1beta/")
        return value
    }

    /** Trims, collapses doubled slashes, strips a trailing endpoint and a trailing slash. */
    fun repairBase(raw: String): String {
        var value = raw.trim()
        if (value.isEmpty()) return ""
        value = value.replace(doubleSlash, "/")
        for (endpoint in knownEndpoints) {
            if (value.endsWith(endpoint)) value = value.removeSuffix(endpoint)
        }
        value = value.trimEnd('/')
        return value
    }

    fun resolveUrl(profile: Profile, repairedBase: String): String {
        val encodedModel = URLEncoder.encode(profile.model.trim(), "UTF-8")
        val template = profile.pathTemplate.trim().ifEmpty { "{base}" }
        val filled = template.replace("{model}", encodedModel)
        var url = when {
            filled.contains("{base}") -> filled.replace("{base}", repairedBase)
            filled.startsWith("http://") || filled.startsWith("https://") -> filled
            else -> if (repairedBase.isEmpty()) filled else "$repairedBase/${filled.trimStart('/')}"
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        url = tidy(url)
        if (profile.dialect == Dialect.GOOGLE && profile.apiKey.isNotBlank()) {
            val separator = if (url.contains("?")) "&" else "?"
            url = url + separator + "key=" + URLEncoder.encode(profile.apiKey.trim(), "UTF-8")
        }
        return url
    }

    /**
     * Candidate addresses for "which models may this key use". Tried in order until
     * one of them answers with a list.
     */
    fun modelListUrls(profile: Profile, repairedBase: String): List<String> {
        if (repairedBase.isBlank()) return emptyList()
        val candidates = listOf(
            "$repairedBase/models",
            "$repairedBase/v1/models",
            "$repairedBase/v1beta/models"
        ).map { tidy(it) }.distinct()
        if (profile.dialect == Dialect.GOOGLE && profile.apiKey.isNotBlank()) {
            val key = URLEncoder.encode(profile.apiKey.trim(), "UTF-8")
            return candidates.map { "$it?key=$key" }
        }
        return candidates
    }

    fun prepare(profile: Profile, history: List<ChatMessage>, repairedBase: String): PreparedRequest =
        PreparedRequest(
            url = resolveUrl(profile, repairedBase),
            headers = headersFor(profile),
            body = buildBody(profile, history),
            stream = profile.stream
        )

    fun headersFor(profile: Profile): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        list.add("Content-Type" to "application/json")
        list.add("Accept" to if (profile.stream) "text/event-stream" else "application/json")
        val name = profile.authHeaderName.trim()
        val key = profile.apiKey.trim()
        if (name.isNotEmpty() && key.isNotEmpty()) {
            val prefix = profile.authValuePrefix.trim()
            val value = if (prefix.isEmpty() || prefix.equals("raw", ignoreCase = true) || prefix.equals("none", ignoreCase = true)) {
                key
            } else {
                "$prefix $key"
            }
            list.add(name to value)
        }
        profile.extraHeaders.forEach { pair ->
            if (pair.key.isNotBlank()) list.add(pair.key.trim() to pair.value)
        }
        return list
    }

    private fun escape(raw: String): String = raw
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")

    private fun buildBody(profile: Profile, history: List<ChatMessage>): String {
        val model = profile.model.trim()
        val system = Presets.SYSTEM_PROMPT
        if (profile.dialect == Dialect.CUSTOM && profile.customBody.isNotBlank()) {
            val lastUser = history.lastOrNull { it.role == Role.USER }
            var text = lastUser?.text ?: ""
            val attachment = lastUser?.attachment
            if (attachment != null) {
                text = text + "\n\n[attachment ${attachment.name} (${attachment.mime})]\n" + attachment.base64
            }
            return profile.customBody
                .replace("{model}", escape(model))
                .replace("{text}", escape(text))
                .replace("{system}", escape(system))
        }

        val usable = history.filter { it.text.isNotBlank() || it.attachment != null }
        return when (profile.dialect) {
            Dialect.ANTHROPIC -> buildAnthropic(model, system, usable, profile.stream)
            Dialect.GOOGLE -> buildGoogle(model, system, usable, profile.stream)
            else -> buildOpenAi(model, system, usable, profile.stream)
        }.toString()
    }

    private fun buildOpenAi(model: String, system: String, history: List<ChatMessage>, stream: Boolean): JsonObject =
        buildJsonObject {
            put("model", model)
            put("stream", stream)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", system)
                })
                history.forEach { message ->
                    add(buildJsonObject {
                        put("role", if (message.role == Role.USER) "user" else "assistant")
                        val attachment = message.attachment
                        if (attachment == null) {
                            put("content", message.text)
                        } else {
                            put("content", buildJsonArray {
                                if (message.text.isNotBlank()) {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", message.text)
                                    })
                                }
                                if (attachment.isImage) {
                                    add(buildJsonObject {
                                        put("type", "image_url")
                                        put("image_url", buildJsonObject {
                                            put("url", "data:${attachment.mime};base64,${attachment.base64}")
                                        })
                                    })
                                } else {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", "[file ${attachment.name}]\n${attachment.base64}")
                                    })
                                }
                            })
                        }
                    })
                }
            })
        }

    private fun buildAnthropic(model: String, system: String, history: List<ChatMessage>, stream: Boolean): JsonObject =
        buildJsonObject {
            put("model", model)
            put("max_tokens", 2048)
            put("stream", stream)
            put("system", system)
            put("messages", buildJsonArray {
                history.forEach { message ->
                    add(buildJsonObject {
                        put("role", if (message.role == Role.USER) "user" else "assistant")
                        val attachment = message.attachment
                        if (attachment == null) {
                            put("content", message.text)
                        } else {
                            put("content", buildJsonArray {
                                if (message.text.isNotBlank()) {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", message.text)
                                    })
                                }
                                val blockType = if (attachment.isImage) "image" else "document"
                                add(buildJsonObject {
                                    put("type", blockType)
                                    put("source", buildJsonObject {
                                        put("type", "base64")
                                        put("media_type", attachment.mime)
                                        put("data", attachment.base64)
                                    })
                                    if (!attachment.isImage) put("title", attachment.name)
                                })
                            })
                        }
                    })
                }
            })
        }

    private fun buildGoogle(model: String, system: String, history: List<ChatMessage>, stream: Boolean): JsonObject =
        buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            })
            put("contents", JsonArray(history.map { message ->
                buildJsonObject {
                    put("role", if (message.role == Role.USER) "user" else "model")
                    put("parts", buildJsonArray {
                        if (message.text.isNotBlank()) add(buildJsonObject { put("text", message.text) })
                        message.attachment?.let { attachment ->
                            add(buildJsonObject {
                                put("inline_data", buildJsonObject {
                                    put("mime_type", attachment.mime)
                                    put("data", attachment.base64)
                                })
                            })
                        }
                    })
                }
            }))
            if (stream) put("streamGenerations", true)
        }
}
