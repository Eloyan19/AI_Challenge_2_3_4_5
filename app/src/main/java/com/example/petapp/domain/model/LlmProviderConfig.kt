package com.example.petapp.domain.model

/**
 * Per-model pricing used for cost estimation.
 *
 * All rates are in USD per million tokens.
 */
data class ModelPricing(
    val model: String,
    val costPerMInputCached: Double,
    val costPerMInputUncached: Double,
    val costPerMOutput: Double
)

/**
 * Describes a specific LLM provider's capabilities, available models, and pricing.
 *
 * Injected via DI so the provider can be swapped in [com.example.petapp.di.AppModule]
 * without touching agent, strategy, or UI code.
 *
 * @property providerName Human-readable provider label (e.g. "DeepSeek").
 * @property availableModels Models shown in the UI picker, ordered by capability.
 * @property defaultModel Initial model selected on first launch.
 * @property backgroundModel Cheap model used for summarization / fact extraction / working memory.
 * @property contextLimit Token context window size; used for the context usage progress bar.
 * @property supportsThinking Whether the provider has extended reasoning (thinking) mode.
 * @property supportsTools Whether the provider supports function / tool calling.
 * @property modelPricing Per-model cost rates; used by [SimpleAgent] for cost estimation.
 */
data class LlmProviderConfig(
    val providerName: String,
    val availableModels: List<String>,
    val defaultModel: String,
    val backgroundModel: String,
    val contextLimit: Int,
    val supportsThinking: Boolean,
    val supportsTools: Boolean,
    val modelPricing: List<ModelPricing>
) {
    /** Returns pricing for [model], or null if no pricing data is available for it. */
    fun pricingFor(model: String): ModelPricing? = modelPricing.find { it.model == model }
}
