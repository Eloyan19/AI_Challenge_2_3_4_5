package com.example.petapp.data

import android.util.Log
import com.example.petapp.domain.model.Tool
import com.example.petapp.domain.model.ToolFunction
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

class McpClient(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val mcpUrl: String
) {
    companion object {
        private const val TAG = "McpClient"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    @Volatile private var sessionId: String? = null
    private val initMutex = Mutex()

    @Volatile private var cachedTools: List<Tool> = emptyList()
    @Volatile private var toolsCachedAt: Long = 0
    @Volatile private var mcpToolNames: Set<String> = emptySet()
    private val requestIdCounter = java.util.concurrent.atomic.AtomicLong(0)

    fun ownsToolName(name: String): Boolean = name in mcpToolNames

    suspend fun listTools(): List<Tool> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedTools.isNotEmpty() && now - toolsCachedAt < CACHE_TTL_MS) {
            Log.d(TAG, "tools/list — cache hit (${cachedTools.size} tools, age=${(now - toolsCachedAt) / 1000}s)")
            return@withContext cachedTools
        }
        try {
            ensureSession()
            Log.d(TAG, "tools/list → fetching from server...")
            val raw = postMcp(buildJsonRpc("tools/list", requestIdCounter.incrementAndGet().toInt(), JsonObject()))
            val tools = parseSseData(raw)
                .getAsJsonObject("result")
                ?.getAsJsonArray("tools")
                ?.mapNotNull { convertMcpTool(it.asJsonObject) }
                ?: emptyList()
            cachedTools = tools
            toolsCachedAt = now
            mcpToolNames = tools.map { it.function.name }.toSet()
            Log.d(TAG, "tools/list ← ${tools.size} tools: ${mcpToolNames.toList()}")
            tools
        } catch (e: Exception) {
            Log.w(TAG, "tools/list failed — degrading to cached (${cachedTools.size} tools): ${e.message}")
            cachedTools
        }
    }

    suspend fun callTool(name: String, args: JsonObject): String = withContext(Dispatchers.IO) {
        ensureSession()
        Log.d(TAG, "tools/call → $name args=$args")
        val start = System.currentTimeMillis()
        try {
            val result = doToolCall(name, args)
            Log.d(TAG, "tools/call ← $name (${System.currentTimeMillis() - start}ms): ${result.take(200)}")
            result
        } catch (e: SessionExpiredException) {
            Log.w(TAG, "tools/call — session expired, reinitializing and retrying $name")
            sessionId = null
            ensureSession()
            val result = doToolCall(name, args)
            Log.d(TAG, "tools/call ← $name retry ok (${System.currentTimeMillis() - start}ms): ${result.take(200)}")
            result
        }
    }

    private suspend fun ensureSession() {
        if (sessionId != null) return
        initMutex.withLock {
            if (sessionId != null) return
            Log.d(TAG, "initialize → opening new MCP session")
            sessionId = initialize()
            Log.d(TAG, "initialize ← session=$sessionId")
        }
    }

    private fun initialize(): String {
        val params = JsonObject().apply {
            addProperty("protocolVersion", "2024-11-05")
            add("capabilities", JsonObject())
            add("clientInfo", JsonObject().apply {
                addProperty("name", "android-app")
                addProperty("version", "1.0")
            })
        }
        val request = buildRequest(buildJsonRpc("initialize", requestIdCounter.incrementAndGet().toInt(), params), sessionId = null)
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("MCP initialize failed: ${response.code}")
            response.header("mcp-session-id")
                ?: throw RuntimeException("No mcp-session-id in initialize response")
        }
    }

    private fun doToolCall(name: String, args: JsonObject): String {
        val params = JsonObject().apply {
            addProperty("name", name)
            add("arguments", args)
        }
        val raw = postMcp(buildJsonRpc("tools/call", requestIdCounter.incrementAndGet().toInt(), params))
        return parseSseData(raw)
            .getAsJsonObject("result")
            ?.getAsJsonArray("content")
            ?.asSequence()
            ?.map { it.asJsonObject }
            ?.filter { it.get("type")?.asString == "text" }
            ?.mapNotNull { it.get("text")?.asString }
            ?.joinToString("\n")
            ?.ifEmpty { "Пустой ответ от MCP-инструмента" }
            ?: "Пустой ответ от MCP-инструмента"
    }

    private fun postMcp(payload: JsonObject): String {
        val sid = sessionId ?: throw RuntimeException("No active MCP session")
        return httpClient.newCall(buildRequest(payload, sid)).execute().use { response ->
            if (response.code == 400) throw SessionExpiredException()
            if (!response.isSuccessful) throw RuntimeException("MCP request failed: ${response.code}")
            response.body?.string() ?: throw RuntimeException("Empty MCP response body")
        }
    }

    private fun buildRequest(payload: JsonObject, sessionId: String?) =
        Request.Builder()
            .url(mcpUrl)
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Accept", "application/json, text/event-stream")
            .addHeader("Authorization", "Bearer ${com.example.petapp.BuildConfig.MCP_API_KEY}")
            .apply { sessionId?.let { addHeader("mcp-session-id", it) } }
            .build()

    private fun buildJsonRpc(method: String, id: Int, params: JsonObject) = JsonObject().apply {
        addProperty("jsonrpc", "2.0")
        addProperty("method", method)
        addProperty("id", id)
        add("params", params)
    }

    private fun parseSseData(raw: String): JsonObject {
        val data = raw.lines().firstOrNull { it.startsWith("data: ") }
            ?: throw RuntimeException("No SSE data line in response")
        return gson.fromJson(data.removePrefix("data: "), JsonObject::class.java)
    }

    private fun convertMcpTool(json: JsonObject): Tool? {
        val name = json.get("name")?.asString ?: return null
        val description = json.get("description")?.asString ?: ""
        val parameters = json.getAsJsonObject("inputSchema") ?: JsonObject().apply {
            addProperty("type", "object")
        }
        return Tool(function = ToolFunction(name = name, description = description, parameters = parameters))
    }

    private class SessionExpiredException : Exception()
}
