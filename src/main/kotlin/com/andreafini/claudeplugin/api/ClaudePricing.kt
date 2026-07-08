package com.andreafini.claudeplugin.api

import java.util.Locale

/** Stima del costo di una chiamata in base al modello e ai token usati. */
object ClaudePricing {

    // Prezzi in USD per 1 milione di token: (input, output).
    private val prices: Map<String, Pair<Double, Double>> = mapOf(
        "claude-opus-4-8" to (5.0 to 25.0),
        "claude-sonnet-5" to (3.0 to 15.0),
        "claude-haiku-4-5" to (1.0 to 5.0),
    )

    /** Costo stimato in USD, o null se il modello non è nel listino. */
    fun costUsd(model: String, inputTokens: Int, outputTokens: Int): Double? {
        val p = prices[model] ?: return null
        return inputTokens / 1_000_000.0 * p.first + outputTokens / 1_000_000.0 * p.second
    }

    /** Riga informativa pronta da mostrare: token + costo stimato. */
    fun infoLine(model: String, inputTokens: Int, outputTokens: Int): String {
        val cost = costUsd(model, inputTokens, outputTokens)
        val costText = if (cost != null) "~ " + String.format(Locale.US, "$%.4f", cost) else "n/d"
        return "Token: $inputTokens in / $outputTokens out · Costo: $costText · $model"
    }
}
