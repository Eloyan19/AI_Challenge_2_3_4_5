package com.example.petapp.data

import android.util.Log
import com.example.petapp.domain.model.Tool
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class McpServerManager @Inject constructor(
    @Named("base") private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "McpServerManager"
    }

    private val servers = mapOf(
        "market" to "https://jorchik.com/mcp/market",
        "notify" to "https://jorchik.com/mcp/notify"
    )

    private val clients: Map<String, McpClient> = servers.mapValues { (_, url) ->
        McpClient(httpClient, gson, url)
    }

    // @Volatile ensures readers always see the fully-constructed map,
    // never a partially-written one from a concurrent allTools() call
    @Volatile private var toolToServer: Map<String, String> = emptyMap()

    suspend fun allTools(): List<Tool> {
        val fresh = mutableMapOf<String, String>()
        val tools = clients.flatMap { (serverId, client) ->
            client.listTools().also { list ->
                list.forEach { fresh[it.function.name] = serverId }
            }
        }
        toolToServer = fresh  // atomic reference swap — no partial state visible to readers
        Log.d(TAG, "allTools: discovered ${tools.size} tools across ${servers.size} servers: ${fresh.keys.toList()}")
        return tools
    }

    suspend fun callTool(name: String, args: JsonObject): String {
        var serverId = toolToServer[name]
        if (serverId == null) {
            Log.w(TAG, "callTool('$name') — toolToServer empty, triggering allTools() refresh")
            allTools()
            serverId = toolToServer[name]
        }
        if (serverId == null) {
            return "Error: unknown MCP tool '$name'"
        }
        val client = clients[serverId]
            ?: return "Error: internal — no client for server '$serverId'"
        Log.d(TAG, "callTool → routing '$name' to server '$serverId'")
        return client.callTool(name, args)
    }

    fun ownsToolName(name: String): Boolean = name in toolToServer
}
