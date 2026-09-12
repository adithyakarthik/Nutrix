package com.nutrix.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which Claude model Nutrix sends photos and questions to.
 *
 * The three tiers differ in more than price, and the request shape has to follow: effort is
 * rejected outright by Haiku, and the dynamic-filtering web search tool only exists on the
 * larger models. Getting either wrong is an HTTP 400, not a quiet degradation — so the
 * capability flags here are what [com.nutrix.app.data.remote.ClaudeClient] builds each
 * request from.
 *
 * Prices are per million tokens, input/output, as published by Anthropic.
 */
@Serializable
enum class ClaudeModel(
    val id: String,
    val label: String,
    val priceNote: String,
    val blurb: String,
    /** `output_config.effort` is accepted. Haiku 4.5 returns a 400 for it. */
    val supportsEffort: Boolean,
    /** Server-side web search tool type this model accepts. */
    val webSearchToolType: String,
) {
    @SerialName("best")
    BEST(
        id = "claude-opus-5",
        label = "Most accurate",
        priceNote = "$5 / $25 per million tokens",
        blurb = "Best at reading a crowded plate and estimating portions. The default.",
        supportsEffort = true,
        webSearchToolType = "web_search_20260209",
    ),

    @SerialName("balanced")
    BALANCED(
        id = "claude-sonnet-5",
        label = "Balanced",
        priceNote = "$2 / $10 per million tokens",
        blurb = "Noticeably cheaper, still strong on food recognition. A good everyday setting.",
        supportsEffort = true,
        webSearchToolType = "web_search_20260209",
    ),

    @SerialName("cheapest")
    CHEAPEST(
        id = "claude-haiku-4-5",
        label = "Cheapest",
        priceNote = "$1 / $5 per million tokens",
        blurb = "Fastest and least expensive — best for testing. Expect looser portion estimates " +
            "on complex dishes.",
        supportsEffort = false,
        webSearchToolType = "web_search_20250305",
    ),
    ;

    companion object {
        val DEFAULT = BEST
    }
}
