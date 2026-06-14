package com.example.petapp.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class ToolExecutor(
    private val yandexUser: String,
    private val yandexKey: String
) {

    // ── Retrofit interfaces ───────────────────────────────────────────────────

    private interface CurrencyApi {
        @GET("latest")
        suspend fun convert(
            @Query("amount") amount: Double,
            @Query("from")   from:   String,
            @Query("to")     to:     String
        ): CurrencyResponse
    }

    // ── Response models ───────────────────────────────────────────────────────

    private data class CurrencyResponse(
        val date: String,
        val rates: Map<String, Double>
    )

    // ── Service instances ─────────────────────────────────────────────────────

    private val currencyApi = buildClient<CurrencyApi>(
        "https://api.frankfurter.app/",
        CurrencyApi::class.java
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun execute(toolCall: ToolCall): String {
        Log.d("ToolExecutor", "Executing: ${toolCall.function.name}(${toolCall.function.arguments})")
        return try {
            val args = Gson().fromJson(toolCall.function.arguments, JsonObject::class.java)
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

    private suspend fun getWeather(args: JsonObject): String {
        val city = args.get("city").asString
        val encodedCity = java.net.URLEncoder.encode(city, "UTF-8")
        // Компактный формат: ~80 байт вместо сотен KB от format=j1
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

    private suspend fun convertCurrency(args: JsonObject): String {
        val amount = args.get("amount").asDouble
        val from   = args.get("from").asString.uppercase()
        val to     = args.get("to").asString.uppercase()
        val resp   = currencyApi.convert(amount, from, to)
        val result = resp.rates[to] ?: return "Не удалось получить курс $from → $to"
        return "$amount $from = ${"%.2f".format(result)} $to (курс на ${resp.date})"
    }

    private suspend fun webSearch(args: JsonObject): String {
        if (yandexUser.isBlank() || yandexKey.isBlank()) {
            return "Поиск недоступен: добавь YANDEX_SEARCH_USER=<логин> и " +
                   "YANDEX_SEARCH_KEY=<ключ> в local.properties. " +
                   "Ключ получить на https://xml.yandex.com/"
        }
        val query = args.get("query").asString

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun <T> buildClient(
        baseUrl: String,
        clazz: Class<T>,
        headers: Map<String, String> = emptyMap()
    ): T {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (headers.isNotEmpty()) {
                    addInterceptor { chain ->
                        val req = chain.request().newBuilder()
                        headers.forEach { (k, v) -> req.addHeader(k, v) }
                        chain.proceed(req.build())
                    }
                }
            }.build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(clazz)
    }
}
