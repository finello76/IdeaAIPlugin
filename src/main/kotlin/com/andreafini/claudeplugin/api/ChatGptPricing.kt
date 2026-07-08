package com.andreafini.claudeplugin.api

import java.util.Locale

/** Stima del costo di una chiamata ChatGPT in base al modello e ai token usati. */
object ChatGptPricing {

    // Prezzi in USD per 1 milione di token: (input, output).
    private val prices: Map<String, Pair<Double, Double>> = mapOf(
        "gpt-5" to (1.25 to 10.0),
        "gpt-5-mini" to (0.25 to 2.0),
        "gpt-4.1" to (2.0 to 8.0),
        "gpt-4o" to (2.5 to 10.0),
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
