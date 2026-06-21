package com.example.petapp.data

import android.util.Log
import com.example.petapp.domain.model.ToolCall
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Named

/**
 * Executes tool calls dispatched by [SimpleAgent] and returns plain-text results.
 *
 * Supports three tools:
 * - `get_weather` — current conditions via [wttr.in](https://wttr.in).
 * - `convert_currency` — live exchange rates via [Frankfurter](https://www.frankfurter.app).
 * - `web_search` — top 3 results via the Yandex XML Search API.
 *
 * Both the currency Retrofit client and the raw HTTP client share the connection pool of
 * the injected [baseClient] (`@Named("base")`), avoiding the overhead of creating new pools.
 *
 * @param yandexUser Yandex search login from `BuildConfig.YANDEX_SEARCH_USER`.
 * @param yandexKey Yandex XML API key from `BuildConfig.YANDEX_SEARCH_KEY`.
 * @param baseClient Shared OkHttpClient; used as-is for raw HTTP and as the base for Retrofit.
 * @param gson Singleton [Gson] instance for parsing tool call argument JSON.
 */
class ToolExecutor @Inject constructor(
    @Named("yandexUser") private val yandexUser: String,
    @Named("yandexKey")  private val yandexKey: String,
    @Named("base")       baseClient: OkHttpClient,
    private val gson: Gson
) {

    // ── Retrofit interface ────────────────────────────────────────────────────

    private interface CurrencyApi {
        @GET("latest")
        suspend fun convert(
            @Query("amount") amount: Double,
            @Query("from")   from:   String,
            @Query("to")     to:     String
        ): CurrencyResponse
    }

    private data class CurrencyResponse(
        val date: String,
        val rates: Map<String, Double>
    )

    // ── Service instances — share baseClient's connection pool ────────────────

    private val currencyApi: CurrencyApi = Retrofit.Builder()
        .baseUrl("https://api.frankfurter.app/")
        .client(baseClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CurrencyApi::class.java)

    private val httpClient: OkHttpClient = baseClient

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Dispatches [toolCall] to the appropriate implementation and returns a human-readable result.
     *
     * Unknown tool names and argument parse errors return an error string rather than throwing,
     * so the agent can relay the message back to the model without crashing.
     */
    suspend fun execute(toolCall: ToolCall): String {
        Log.d("ToolExecutor", "Executing: ${toolCall.function.name}(${toolCall.function.arguments})")
        return try {
            val args = gson.fromJson(toolCall.function.arguments, JsonObject::class.java)
            when (toolCall.function.name) {
                "get_weather"      -> getWeather(args)
                "convert_currency" -> convertCurrency(args)
                "web_search"       -> webSearch(args)
                else               -> "Неизвестный инструмент: ${toolCall.function.name}"
            }
        } catch (e: Exception) {
            Log.e("ToolExecutor", "Tool error", e)
            "Ошибка инструмента: ${e.localizedMessage}"
        }
    }

    // ── Tool implementations ──────────────────────────────────────────────────

    /**
     * Fetches current weather for [args]`["city"]` from wttr.in using pipe-delimited format.
     * Returns a single formatted string with condition, temperature, feels-like, humidity, and wind.
     */
    private suspend fun getWeather(args: JsonObject): String {
        val city = args.get("city")?.asString
            ?: return "Ошибка: не указан город"
        val encodedCity = java.net.URLEncoder.encode(city, "UTF-8")
        val url = "https://wttr.in/$encodedCity?format=%C|%t|%f|%h|%w&lang=ru"

        val raw = withContext(Dispatchers.IO) {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()?.trim()
            }
        } ?: return "Данные о погоде для '$city' недоступны"

        val parts = raw.split("|")
        if (parts.size < 5) return "Данные о погоде для '$city' недоступны"
        val (condition, temp, feelsLike, humidity, wind) = parts
        return "Погода в $city: $condition, температура $temp (ощущается как $feelsLike), " +
               "влажность $humidity, ветер $wind"
    }

    /**
     * Converts [args]`["amount"]` from [args]`["from"]` to [args]`["to"]` currency
     * using live rates from api.frankfurter.app.
     */
    private suspend fun convertCurrency(args: JsonObject): String {
        val amount = args.get("amount")?.asDouble
            ?: return "Ошибка: не указана сумма"
        val from = args.get("from")?.asString?.uppercase()
            ?: return "Ошибка: не указана исходная валюта"
        val to = args.get("to")?.asString?.uppercase()
            ?: return "Ошибка: не указана целевая валюта"
        val resp   = currencyApi.convert(amount, from, to)
        val result = resp.rates[to] ?: return "Не удалось получить курс $from → $to"
        return "$amount $from = ${"%.2f".format(result)} $to (курс на ${resp.date})"
    }

    /**
     * Searches the web via the Yandex XML API for [args]`["query"]` and returns the top 3 results
     * (title, snippet, URL). Returns an instructional message if credentials are not configured.
     */
    private suspend fun webSearch(args: JsonObject): String {
        if (yandexUser.isBlank() || yandexKey.isBlank()) {
            return "Поиск недоступен: добавь YANDEX_SEARCH_USER=<логин> и " +
                   "YANDEX_SEARCH_KEY=<ключ> в local.properties. " +
                   "Ключ получить на https://xml.yandex.com/"
        }
        val query = args.get("query")?.asString
            ?: return "Ошибка: не указан поисковый запрос"

        val url = "https://yandex.com/search/xml".toHttpUrl().newBuilder()
            .addQueryParameter("user",    yandexUser)
            .addQueryParameter("key",     yandexKey)
            .addQueryParameter("query",   query)
            .addQueryParameter("l10n",    "ru")
            .addQueryParameter("sortby",  "rlv")
            .addQueryParameter("filter",  "none")
            .addQueryParameter("groupby", "attr=d.mode=flat.groups-on-page=5.docs-in-group=1")
            .build()

        val xml = withContext(Dispatchers.IO) {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } ?: return "Поиск не дал результатов по запросу: $query"

        return parseYandexXml(xml, query)
    }

    /**
     * Parses the Yandex XML Search response and returns the top 3 results as formatted text.
     * Strips HTML tags from titles and snippets using a simple regex.
     */
    private fun parseYandexXml(xml: String, query: String): String {
        val errorMatch = Regex("<error[^>]*>(.*?)</error>").find(xml)
        if (errorMatch != null) return "Ошибка Яндекс.Поиска: ${errorMatch.groupValues[1]}"

        fun stripTags(s: String) =
            s.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

        data class Result(val url: String, val title: String, val snippet: String)

        val results    = mutableListOf<Result>()
        val docRe      = Regex("<doc[^>]*>(.*?)</doc>",     RegexOption.DOT_MATCHES_ALL)
        val urlRe      = Regex("<url>(.*?)</url>",           RegexOption.DOT_MATCHES_ALL)
        val titleRe    = Regex("<title>(.*?)</title>",       RegexOption.DOT_MATCHES_ALL)
        val headlineRe = Regex("<headline>(.*?)</headline>", RegexOption.DOT_MATCHES_ALL)

        for (docMatch in docRe.findAll(xml)) {
            val body    = docMatch.groupValues[1]
            val url     = urlRe.find(body)?.groupValues?.get(1)?.trim() ?: continue
            val title   = stripTags(titleRe.find(body)?.groupValues?.get(1) ?: "")
            val snippet = stripTags(headlineRe.find(body)?.groupValues?.get(1) ?: "")
            if (url.isNotBlank()) results.add(Result(url, title, snippet))
        }

        if (results.isEmpty()) return "Ничего не найдено по запросу: $query"
        return results.take(3).joinToString("\n\n") { r -> "${r.title}\n${r.snippet}\n${r.url}" }
    }
}
