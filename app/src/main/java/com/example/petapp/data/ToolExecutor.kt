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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class ToolExecutor(
    private val yandexUser: String,
    private val yandexKey: String,
    private val yandexWeatherKey: String
) {

    // ── Retrofit interfaces ───────────────────────────────────────────────────

    private interface NominatimApi {
        @GET("search")
        suspend fun search(
            @Query("q")      query:  String,
            @Query("format") format: String = "json",
            @Query("limit")  limit:  Int    = 1
        ): List<NominatimResult>
    }

    private interface YandexWeatherApi {
        @GET("v2/informers")
        suspend fun get(
            @Query("lat")  lat:  Double,
            @Query("lon")  lon:  Double,
            @Query("lang") lang: String = "ru_RU"
        ): YandexWeatherResponse
    }

    private interface CurrencyApi {
        @GET("latest")
        suspend fun convert(
            @Query("amount") amount: Double,
            @Query("from")   from:   String,
            @Query("to")     to:     String
        ): CurrencyResponse
    }

    // ── Response models ───────────────────────────────────────────────────────

    private data class NominatimResult(
        val lat: String,
        val lon: String,
        @SerializedName("display_name") val displayName: String
    )

    private data class YandexWeatherResponse(val fact: WeatherFact?)
    private data class WeatherFact(
        val temp: Int?,
        @SerializedName("feels_like")  val feelsLike:  Int?,
        val condition: String?,
        @SerializedName("wind_speed")  val windSpeed:  Double?,
        val humidity: Int?
    )

    private data class CurrencyResponse(
        val date: String,
        val rates: Map<String, Double>
    )

    // ── Service instances ─────────────────────────────────────────────────────

    private val nominatimApi = buildClient<NominatimApi>(
        "https://nominatim.openstreetmap.org/",
        NominatimApi::class.java,
        headers = mapOf("User-Agent" to "PetApp/1.0")
    )
    private val yandexWeatherApi = buildClient<YandexWeatherApi>(
        "https://api.weather.yandex.ru/",
        YandexWeatherApi::class.java,
        headers = mapOf("X-Yandex-Weather-Key" to yandexWeatherKey)
    )
    private val currencyApi = buildClient<CurrencyApi>(
        "https://api.frankfurter.app/",
        CurrencyApi::class.java
    )

    private val httpClient = OkHttpClient()

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
        if (yandexWeatherKey.isBlank()) {
            return "Погода недоступна: добавь YANDEX_WEATHER_KEY в local.properties. " +
                   "Ключ: https://developer.tech.yandex.ru/services/38"
        }
        val city = args.get("city").asString

        val geoResults = nominatimApi.search(city)
        val geo = geoResults.firstOrNull() ?: return "Город '$city' не найден"
        val lat = geo.lat.toDoubleOrNull() ?: return "Не удалось определить координаты '$city'"
        val lon = geo.lon.toDoubleOrNull() ?: return "Не удалось определить координаты '$city'"

        val weather = yandexWeatherApi.get(lat, lon)
        val fact = weather.fact ?: return "Данные о погоде недоступны"

        val cityName = geo.displayName.split(",").firstOrNull()?.trim() ?: city
        return "Погода в $cityName: ${conditionDesc(fact.condition)}, " +
               "температура ${fact.temp}°C (ощущается как ${fact.feelsLike}°C), " +
               "влажность ${fact.humidity}%, ветер ${fact.windSpeed} м/с"
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

    private fun conditionDesc(condition: String?) = when (condition) {
        "clear"                   -> "ясно"
        "partly-cloudy"           -> "переменная облачность"
        "cloudy"                  -> "облачно с прояснениями"
        "overcast"                -> "пасмурно"
        "drizzle"                 -> "морось"
        "light-rain"              -> "небольшой дождь"
        "rain"                    -> "дождь"
        "moderate-rain"           -> "умеренный дождь"
        "heavy-rain"              -> "сильный дождь"
        "continuous-heavy-rain"   -> "длительный сильный дождь"
        "showers"                 -> "ливень"
        "wet-snow"                -> "дождь со снегом"
        "light-snow"              -> "небольшой снег"
        "snow"                    -> "снег"
        "snow-showers"            -> "снегопад"
        "hail"                    -> "град"
        "thunderstorm"            -> "гроза"
        "thunderstorm-with-rain"  -> "дождь с грозой"
        "thunderstorm-with-hail"  -> "гроза с градом"
        else                      -> condition ?: "неизвестно"
    }

    private fun <T> buildClient(
        baseUrl: String,
        clazz: Class<T>,
        headers: Map<String, String> = emptyMap()
    ): T {
        val client = OkHttpClient.Builder().apply {
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
