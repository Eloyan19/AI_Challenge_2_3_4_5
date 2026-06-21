package com.example.petapp.data

import com.example.petapp.domain.model.Tool
import com.example.petapp.domain.model.ToolFunction
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Catalogue of tool definitions sent to the DeepSeek API.
 *
 * Each tool is described by a name, a natural-language description (used by the model to decide
 * when to call it), and a JSON-Schema object that specifies the expected argument types.
 * Tools are disabled automatically when thinking mode is active (the API does not support
 * function calling and extended reasoning simultaneously).
 *
 * Tool implementations live in [ToolExecutor].
 */
object ToolDefinitions {

    /** Fetches current weather conditions for a given city via wttr.in. */
    val weatherTool = Tool(
        function = ToolFunction(
            name = "get_weather",
            description = "Получает текущую погоду для указанного города. " +
                    "Используй когда пользователь спрашивает о погоде.",
            parameters = params(
                props = mapOf(
                    "city" to prop("string", "Название города, например 'Москва' или 'Paris'")
                ),
                required = listOf("city")
            )
        )
    )

    /** Converts an amount between two ISO 4217 currencies via api.frankfurter.app. */
    val currencyTool = Tool(
        function = ToolFunction(
            name = "convert_currency",
            description = "Конвертирует сумму из одной валюты в другую по актуальному курсу. " +
                    "Используй при вопросах о курсах валют или конвертации.",
            parameters = params(
                props = mapOf(
                    "amount" to prop("number", "Сумма для конвертации"),
                    "from"   to prop("string", "Исходная валюта, код ISO 4217: USD, EUR, RUB и др."),
                    "to"     to prop("string", "Целевая валюта, код ISO 4217: USD, EUR, RUB и др.")
                ),
                required = listOf("amount", "from", "to")
            )
        )
    )

    /** Searches the web via the Yandex XML Search API. Requires credentials in `local.properties`. */
    val searchTool = Tool(
        function = ToolFunction(
            name = "web_search",
            description = "Выполняет поиск актуальной информации в интернете. " +
                    "Используй для поиска новостей, фактов, последних событий.",
            parameters = params(
                props = mapOf(
                    "query" to prop("string", "Поисковый запрос")
                ),
                required = listOf("query")
            )
        )
    )

    /** All available tools passed to the API in a single request. */
    val allTools = listOf(weatherTool, currencyTool, searchTool)

    private fun prop(type: String, description: String) = JsonObject().apply {
        addProperty("type", type)
        addProperty("description", description)
    }

    private fun params(props: Map<String, JsonObject>, required: List<String>) = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            props.forEach { (name, schema) -> add(name, schema) }
        })
        add("required", JsonArray().apply { required.forEach(::add) })
    }
}
