package com.example.petapp.data

import android.app.Application
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads behavioral guardrails from [assets/guardrails.json] and formats them into a
 * system-prompt string for injection into every LLM request.
 *
 * Edit [assets/guardrails.json] to add, remove, or disable individual rules without
 * changing any Kotlin code — only a rebuild is required.
 */
@Singleton
class GuardrailsLoader @Inject constructor(
    private val application: Application,
    private val gson: Gson
) {

    private data class GuardrailsConfig(
        @SerializedName("enabled") val enabled: Boolean = true,
        @SerializedName("system_prefix") val systemPrefix: String = "",
        @SerializedName("rules") val rules: List<Rule> = emptyList()
    )

    private data class Rule(
        @SerializedName("id") val id: String,
        @SerializedName("enabled") val enabled: Boolean = true,
        @SerializedName("instruction") val instruction: String
    )

    /**
     * Reads and parses [assets/guardrails.json].
     *
     * @return Formatted system-prompt string, or `null` if guardrails are globally disabled
     *         or no rules are active. Returns `null` on any parse/IO error to avoid blocking
     *         the app from starting.
     */
    fun load(): String? = runCatching {
        val json = application.assets.open("guardrails.json").bufferedReader().readText()
        val config = gson.fromJson(json, GuardrailsConfig::class.java)

        if (!config.enabled) return null

        val activeRules = config.rules.filter { it.enabled }
        if (activeRules.isEmpty()) return null

        val rulesText = activeRules.joinToString("\n") { "- ${it.instruction}" }
        config.systemPrefix + rulesText
    }.getOrElse { e ->
        Log.e("GuardrailsLoader", "Failed to load guardrails.json", e)
        null
    }
}
