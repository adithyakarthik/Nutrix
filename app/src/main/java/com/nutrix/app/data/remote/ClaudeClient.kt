package com.nutrix.app.data.remote

import com.nutrix.app.model.SourceRef
import com.nutrix.app.util.NutrixJson
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Where the app should send Claude requests, and with what credential. */
data class ClaudeConfig(
    val apiKey: String? = null,
    /**
     * Base URL of a backend that forwards to the Messages API and holds the real key.
     * When set it wins over [apiKey] — see the README on why that is the right shape for a
     * published build.
     */
    val proxyBaseUrl: String? = null,
    /** Set when the user has switched AI features off; nothing is sent anywhere. */
    val disabled: Boolean = false,
) {
    val isConfigured: Boolean
        get() = !disabled && (!apiKey.isNullOrBlank() || !proxyBaseUrl.isNullOrBlank())

    val usesProxy: Boolean get() = !proxyBaseUrl.isNullOrBlank()
}

class ClaudeException(
    val statusCode: Int?,
    val errorType: String?,
    override val message: String,
) : Exception(message) {

    /** A message worth showing a user who is standing in a kitchen holding a plate. */
    val userMessage: String
        get() = when {
            statusCode == 401 || errorType == "authentication_error" ->
                "That API key was rejected. Check it in Settings."
            statusCode == 403 -> "This key is not allowed to use the Claude API."
            statusCode == 429 -> "Rate limited. Give it a few seconds and try again."
            statusCode == 400 && errorType == "invalid_request_error" ->
                "Claude could not process that request: $message"
            statusCode != null && statusCode >= 500 ->
                "Claude is busy right now. Try again in a moment."
            else -> message
        }
}

/** A finished (non-streaming) response, reduced to the parts Nutrix cares about. */
data class ClaudeResponse(
    val text: String,
    val toolUses: List<ToolUse>,
    val sources: List<SourceRef>,
    val stopReason: String?,
    val inputTokens: Int,
    val outputTokens: Int,
) {
    fun toolInput(name: String): JsonObject? = toolUses.firstOrNull { it.name == name }?.input
}

data class ToolUse(val id: String, val name: String, val input: JsonObject)

sealed interface ClaudeStreamEvent {
    data class TextDelta(val text: String) : ClaudeStreamEvent
    data class Completed(val sources: List<SourceRef>) : ClaudeStreamEvent
    data class Failed(val error: ClaudeException) : ClaudeStreamEvent
}

/**
 * A small, purpose-built client for the Anthropic Messages API.
 *
 * Anthropic publishes a Java SDK, but it targets server JVMs — it is heavy for an APK and
 * assumes a key sits on the machine making the call, which is exactly what a mobile client
 * must not do. Talking to the documented HTTP surface directly keeps the dependency footprint
 * to OkHttp and lets the same code point at either the API or the user's own proxy.
 */
class ClaudeClient(
    private val configProvider: suspend () -> ClaudeConfig,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {

    suspend fun isConfigured(): Boolean = configProvider().isConfigured

    /**
     * One non-streaming turn.
     *
     * [enableWebSearch] hands Claude the server-side web search tool, which is how Nutrix
     * grounds nutrition numbers in published food composition data and research rather than
     * in the model's recollection.
     */
    suspend fun send(
        system: String,
        messages: List<JsonObject>,
        tools: JsonArray? = null,
        maxTokens: Int = 16_000,
        effort: String = "medium",
        enableWebSearch: Boolean = false,
        maxWebSearches: Int = 4,
    ): ClaudeResponse = withContext(Dispatchers.IO) {
        val config = requireConfig()
        var conversation = messages
        val accumulatedSources = mutableListOf<SourceRef>()

        // A server-side tool can pause the turn while it works; the documented continuation is
        // to send the assistant's partial content straight back. Bounded so a misbehaving turn
        // cannot loop forever on the user's data plan.
        repeat(MAX_PAUSE_CONTINUATIONS) {
            val body = requestBody(
                system = system,
                messages = conversation,
                tools = tools,
                maxTokens = maxTokens,
                effort = effort,
                enableWebSearch = enableWebSearch,
                maxWebSearches = maxWebSearches,
                stream = false,
            )
            val json = execute(config, body)
            val parsed = parseResponse(json)
            accumulatedSources += parsed.sources

            if (parsed.stopReason != "pause_turn") {
                return@withContext parsed.copy(sources = accumulatedSources.distinctBy { it.url + it.title })
            }
            val assistantContent = json["content"]?.jsonArray ?: buildJsonArray { }
            conversation = conversation + buildJsonObject {
                put("role", "assistant")
                put("content", assistantContent)
            }
        }
        throw ClaudeException(null, null, "Claude kept pausing without finishing. Try again.")
    }

    /** Streaming turn, for the chat screen where waiting in silence feels broken. */
    fun stream(
        system: String,
        messages: List<JsonObject>,
        maxTokens: Int = 32_000,
        effort: String = "medium",
        enableWebSearch: Boolean = false,
    ): Flow<ClaudeStreamEvent> = flow {
        val config = requireConfig()
        val body = requestBody(
            system = system,
            messages = messages,
            tools = null,
            maxTokens = maxTokens,
            effort = effort,
            enableWebSearch = enableWebSearch,
            maxWebSearches = 4,
            stream = true,
        )
        val request = buildRequest(config, body)
        val sources = mutableListOf<SourceRef>()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw parseError(response.code, response.body?.string())
            }
            val source = response.body?.source() ?: throw ClaudeException(null, null, "Empty response from Claude.")
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") continue
                val event = runCatching { NutrixJson.parseObject(payload) }.getOrNull() ?: continue
                when (event["type"]?.jsonPrimitive?.contentOrNull) {
                    "content_block_delta" -> {
                        val delta = event["delta"]?.jsonObject ?: continue
                        // Thinking deltas arrive on the same channel; only text reaches the user.
                        if (delta["type"]?.jsonPrimitive?.contentOrNull == "text_delta") {
                            delta["text"]?.jsonPrimitive?.contentOrNull?.let { emit(ClaudeStreamEvent.TextDelta(it)) }
                        }
                    }
                    "content_block_start" -> {
                        val block = event["content_block"]?.jsonObject ?: continue
                        sources += extractSources(block)
                    }
                    "error" -> {
                        val error = event["error"]?.jsonObject
                        throw ClaudeException(
                            null,
                            error?.get("type")?.jsonPrimitive?.contentOrNull,
                            error?.get("message")?.jsonPrimitive?.contentOrNull ?: "Claude reported an error.",
                        )
                    }
                }
            }
        }
        emit(ClaudeStreamEvent.Completed(sources.distinctBy { it.url + it.title }))
    }.flowOn(Dispatchers.IO)

    private suspend fun requireConfig(): ClaudeConfig {
        val config = configProvider()
        if (config.disabled) {
            throw ClaudeException(
                null,
                "ai_disabled",
                "AI features are switched off. Turn them back on in Settings to scan photos or ask questions.",
            )
        }
        if (!config.isConfigured) {
            throw ClaudeException(
                null,
                "not_configured",
                "Nutrix needs a Claude API key or a proxy URL before it can read photos or answer questions. Add one in Settings.",
            )
        }
        return config
    }

    private fun execute(config: ClaudeConfig, body: JsonObject): JsonObject {
        val request = buildRequest(config, body)
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw ClaudeException(null, "network_error", "No connection to Claude: ${e.message ?: "network unavailable"}")
        }
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw parseError(it.code, raw)
            return runCatching { NutrixJson.parseObject(raw) }
                .getOrElse { throw ClaudeException(null, null, "Claude returned something unreadable.") }
        }
    }

    private fun buildRequest(config: ClaudeConfig, body: JsonObject): Request {
        val url = if (config.usesProxy) {
            config.proxyBaseUrl!!.trimEnd('/') + "/v1/messages"
        } else {
            "$API_BASE/v1/messages"
        }
        return Request.Builder()
            .url(url)
            .post(NutrixJson.encode(body).toRequestBody(JSON_MEDIA_TYPE))
            .header("content-type", "application/json")
            .header("anthropic-version", ANTHROPIC_VERSION)
            .apply {
                // A proxy holds its own credential; the app must not also ship one.
                if (!config.usesProxy) header("x-api-key", config.apiKey!!)
            }
            .build()
    }

    private fun requestBody(
        system: String,
        messages: List<JsonObject>,
        tools: JsonArray?,
        maxTokens: Int,
        effort: String,
        enableWebSearch: Boolean,
        maxWebSearches: Int,
        stream: Boolean,
    ): JsonObject = buildJsonObject {
        put("model", MODEL)
        put("max_tokens", maxTokens)
        if (stream) put("stream", true)
        putJsonArray("system") {
            // The system prompt is the stable prefix; caching it keeps repeat scans cheap.
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", system)
                    putJsonObject("cache_control") { put("type", "ephemeral") }
                },
            )
        }
        putJsonObject("output_config") { put("effort", effort) }

        val allTools = buildJsonArray {
            if (enableWebSearch) {
                add(
                    buildJsonObject {
                        put("type", WEB_SEARCH_TOOL)
                        put("name", "web_search")
                        put("max_uses", maxWebSearches)
                    },
                )
            }
            tools?.forEach { add(it) }
        }
        if (allTools.isNotEmpty()) put("tools", allTools)

        putJsonArray("messages") { messages.forEach { add(it) } }
    }

    private fun parseResponse(json: JsonObject): ClaudeResponse {
        val content = json["content"]?.jsonArray ?: buildJsonArray { }
        val text = StringBuilder()
        val toolUses = mutableListOf<ToolUse>()
        val sources = mutableListOf<SourceRef>()

        for (element in content) {
            val block = element.jsonObject
            when (block["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> text.append(block["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                "tool_use" -> toolUses += ToolUse(
                    id = block["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    name = block["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    input = block["input"]?.jsonObject ?: buildJsonObject { },
                )
            }
            sources += extractSources(block)
        }

        val usage = json["usage"]?.jsonObject
        return ClaudeResponse(
            text = text.toString().trim(),
            toolUses = toolUses,
            sources = sources.distinctBy { it.url + it.title },
            stopReason = json["stop_reason"]?.jsonPrimitive?.contentOrNull,
            inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.int ?: 0,
            outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.int ?: 0,
        )
    }

    /**
     * Pulls citations out of a content block so the app can show the user where a number
     * came from. Web search errors arrive as a 200 with an error object in place of the
     * result list, so the type is checked before indexing.
     */
    private fun extractSources(block: JsonObject): List<SourceRef> {
        val results = when (block["type"]?.jsonPrimitive?.contentOrNull) {
            "web_search_tool_result" -> block["content"] as? JsonArray ?: return emptyList()
            "text" -> block["citations"] as? JsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return results.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val url = item["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = item["title"]?.jsonPrimitive?.contentOrNull
                ?: item["document_title"]?.jsonPrimitive?.contentOrNull
                ?: url
            SourceRef(title = title, url = url)
        }
    }

    private fun parseError(code: Int, raw: String?): ClaudeException {
        val error = raw?.let { runCatching { NutrixJson.parseObject(it) }.getOrNull() }?.get("error")?.jsonObject
        return ClaudeException(
            statusCode = code,
            errorType = error?.get("type")?.jsonPrimitive?.contentOrNull,
            message = error?.get("message")?.jsonPrimitive?.contentOrNull
                ?: "Claude request failed (HTTP $code).",
        )
    }

    companion object {
        const val MODEL = "claude-opus-5"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val API_BASE = "https://api.anthropic.com"

        /** Dynamic-filtering web search, supported on the model above. */
        const val WEB_SEARCH_TOOL = "web_search_20260209"

        private const val MAX_PAUSE_CONTINUATIONS = 4
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // Grounded analysis with web search genuinely takes a while; a short read timeout
            // here shows up to the user as "scanning failed" on a request that was fine.
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
