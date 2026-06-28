package com.example.petapp.data

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
    private val servers = mapOf(
        "market" to "https://jorchik.com/mcp/market",
        "notify" to "https://jorchik.com/mcp/notify"
    )

    private val clients: Map<String, McpClient> = servers.mapValues { (_, url) ->
        McpClient(httpClient, gson, url)
    }

    private val toolToServer = mutableMapOf<String, String>()

    suspend fun allTools(): List<Tool> {
        toolToServer.clear()
        return clients.flatMap { (serverId, client) ->
            client.listTools().also { tools ->
                tools.forEach { toolToServer[it.function.name] = serverId }
            }
        }
    }

    suspend fun callTool(name: String, args: JsonObject): String {
        val serverId = toolToServer[name]
            ?: return "Error: unknown MCP tool '$name'"
        return clients[serverId]!!.callTool(name, args)
    }

    fun ownsToolName(name: String): Boolean = name in toolToServer
}
