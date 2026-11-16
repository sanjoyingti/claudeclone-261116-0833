package com.aibuilt.claudeclone.network

import com.aibuilt.claudeclone.model.SendResult
import com.aibuilt.claudeclone.model.StreamEvent
import com.aibuilt.claudeclone.model.TestOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate

/** What came back from a "which models may this key use" call. */
data class ModelsResult(
    val url: String,
    val status: Int,
    val models: List<String>,
    val error: String
)

class HttpEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: OkHttpClient = run {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun newCall(prepared: PreparedRequest): Call = client.newCall(toRequest(prepared))

    private fun toRequest(prepared: PreparedRequest): Request {
        val builder = Request.Builder().url(prepared.url)
        prepared.headers.forEach { (name, value) -> runCatching { builder.addHeader(name, value) } }
        val body = prepared.body.ifBlank { "{}" }
        builder.post(body.toRequestBody("application/json".toMediaType()))
        return builder.build()
    }

    /** GET a model list and pick the names out of whatever shape comes back. */
    suspend fun listModels(url: String, headers: List<Pair<String, String>>): ModelsResult =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).get()
            headers.forEach { (name, value) -> runCatching { builder.addHeader(name, value) } }
            try {
                client.newCall(builder.build()).execute().use { response ->
                    val bodyText = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    if (!response.isSuccessful) {
                        return@use ModelsResult(url, response.code, emptyList(), describeError(bodyText, response.code))
                    }
                    ModelsResult(url, response.code, parseModels(bodyText), "")
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                ModelsResult(url, 0, emptyList(), error.javaClass.simpleName + ": " + (error.message ?: "no message"))
            }
        }

    fun stream(call: Call, url: String, responsePath: String): Flow<StreamEvent> = flow {
        try {
            call.execute().use { response ->
                val source = response.body?.source()
                if (!response.isSuccessful || source == null) {
                    val errorBody = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    emit(StreamEvent.Failed(url, "HttpStatus${response.code}", describeError(errorBody, response.code)))
                    emit(StreamEvent.Completed)
                    return@use
                }
                val collected = StringBuilder()
                val raw = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) continue
                    val payload = when {
                        line.startsWith("data:") -> line.removePrefix("data:").trim()
                        line.startsWith("{") || line.startsWith("[") -> line.trim()
                        else -> {
                            raw.append(line).append('\n')
                            continue
                        }
                    }
                    if (payload == "[DONE]") break
                    raw.append(payload).append('\n')
                    val piece = extractFromChunk(payload, responsePath)
                    if (!piece.isNullOrEmpty()) {
                        collected.append(piece)
                        emit(StreamEvent.Delta(piece))
                    }
                }
                if (collected.isEmpty()) {
                    val fallback = rawText(raw.toString(), responsePath)
                    if (fallback.isNotBlank()) emit(StreamEvent.Delta(fallback))
                }
                emit(StreamEvent.Completed)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            runCatching {
                emit(StreamEvent.Failed(url, error.javaClass.name, error.message ?: "no message"))
                emit(StreamEvent.Completed)
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun complete(prepared: PreparedRequest, responsePath: String): SendResult =
        withContext(Dispatchers.IO) {
            val call = newCall(prepared)
            try {
                call.execute().use { response ->
                    val bodyText = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    if (!response.isSuccessful) {
                        return@use SendResult.Failure(
                            prepared.url,
                            "HttpStatus${response.code}",
                            describeError(bodyText, response.code)
                        )
                    }
                    SendResult.Success(extractResponse(bodyText, responsePath))
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                SendResult.Failure(prepared.url, error.javaClass.name, error.message ?: "no message")
            }
        }

    suspend fun test(prepared: PreparedRequest, responsePath: String): TestOutput =
        withContext(Dispatchers.IO) {
            try {
                newCall(prepared).execute().use { response ->
                    val bodyText = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    TestOutput(
                        url = prepared.url,
                        status = "${response.code} ${response.message}".trim(),
                        exceptionClass = "",
                        bodyHead = if (response.isSuccessful) {
                            extractResponse(bodyText, responsePath).take(200)
                        } else {
                            describeError(bodyText, response.code).take(200)
                        },
                        ran = true
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                TestOutput(
                    url = prepared.url,
                    status = "no response",
                    exceptionClass = error.javaClass.name + ": " + (error.message ?: "no message"),
                    bodyHead = "",
                    ran = true
                )
            }
        }

    /** Turns a JSON or HTML error body into a short readable "403: message" line. */
    private fun describeError(bodyText: String, code: Int): String {
        val trimmed = bodyText.trim()
        if (trimmed.isEmpty()) return "HTTP $code"
        val parsed = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
        val message = parsed?.let { firstMessage(it) }
        if (!message.isNullOrBlank()) return "$code: $message"
        if (trimmed.startsWith("<")) return "$code: ${trimmed.take(200)}"
        return "$code: ${trimmed.take(300)}"
    }

    private fun firstMessage(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonArray -> element.firstNotNullOfOrNull { firstMessage(it) }
        is JsonObject -> {
            val direct = listOf("message", "error_description", "detail", "msg", "reason", "title")
                .firstNotNullOfOrNull { key ->
                    (element[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                }
            direct ?: listOf("error", "errors", "detail", "data")
                .firstNotNullOfOrNull { key -> element[key]?.let { firstMessage(it) } }
        }

        else -> null
    }

    /** Reads every "id"/"name"/"model" string out of a model listing. */
    private fun parseModels(bodyText: String): List<String> {
        val root = runCatching { json.parseToJsonElement(bodyText) }.getOrNull() ?: return emptyList()
        val arrays = mutableListOf<JsonArray>()
        when (root) {
            is JsonArray -> arrays.add(root)
            is JsonObject -> listOf("data", "models", "result", "items", "available_models", "availableModels")
                .forEach { key -> (root[key] as? JsonArray)?.let { arrays.add(it) } }

            else -> Unit
        }
        val out = mutableListOf<String>()
        arrays.forEach { array ->
            array.forEach { element ->
                val raw = when (element) {
                    is JsonPrimitive -> element.contentOrNull
                    is JsonObject -> listOf("id", "name", "model", "slug", "model_id")
                        .firstNotNullOfOrNull { key -> (element[key] as? JsonPrimitive)?.contentOrNull }
                    else -> null
                }
                if (!raw.isNullOrBlank()) {
                    out.add(raw.removePrefix("models/").removePrefix("tunedModels/").trim())
                }
            }
        }
        return out.filter { it.isNotBlank() }.distinct().sorted()
    }

    private fun extractResponse(bodyText: String, responsePath: String): String {
        if (bodyText.isBlank()) return "(empty body)"
        val parsed = runCatching { json.parseToJsonElement(bodyText) }.getOrNull() ?: return bodyText.trim()
        val selected = if (responsePath.isBlank()) null else JsonPath.select(parsed, responsePath)
        return selected?.takeIf { it.isNotBlank() } ?: bodyText.trim()
    }

    private fun extractFromChunk(payload: String, responsePath: String): String? {
        val parsed: JsonElement = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: return null
        val candidates = buildList {
            if (responsePath.isNotBlank()) add(responsePath)
            add("delta.text")
            add("choices[0].delta.content")
            add("choices[0].message.content")
            add("candidates[0].content.parts[0].text")
            add("content[0].text")
            add("text")
        }
        return JsonPath.selectAny(parsed, candidates)
    }

    private fun rawText(raw: String, responsePath: String): String {
        val parsed = runCatching { json.parseToJsonElement(raw.trim()) }.getOrNull()
        if (parsed != null && responsePath.isNotBlank()) {
            JsonPath.select(parsed, responsePath)?.let { if (it.isNotBlank()) return it }
        }
        val pieces = raw.lineSequence()
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotEmpty() && it != "[DONE]" }
            .toList()
        if (pieces.isEmpty()) return raw.trim()
        val joined = StringBuilder()
        for (piece in pieces) {
            val element = runCatching { json.parseToJsonElement(piece) }.getOrNull()
            if (element == null) {
                joined.append(piece)
            } else {
                val found = JsonPath.selectAny(
                    element,
                    listOfNotNull(responsePath.takeIf { it.isNotBlank() }, "delta.text", "choices[0].delta.content", "text")
                )
                if (found != null) joined.append(found) else joined.append(piece)
            }
        }
        return joined.toString().trim()
    }
}
