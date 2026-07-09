package com.andreafini.claudeplugin.api

import java.util.Locale

/**
 * Stima del costo di una chiamata OpenRouter. OpenRouter espone centinaia di modelli con prezzi
 * variabili, impossibili da elencare tutti: qui i modelli gratuiti (suffisso `:free`) valgono 0,
 * mentre per gli altri il costo resta "n/d" (il consuntivo reale è sul cruscotto di OpenRouter).
 */
object OpenRouterPricing {

    /** Vero se il modello è gratuito (id che termina con `:free`, o il router `openrouter/free`). */
    private fun isFree(model: String): Boolean =
        model.endsWith(":free") || model == "openrouter/free"

    /** Costo stimato in USD: 0 per i modelli `:free`, null (n/d) per gli altri. */
    fun costUsd(model: String, inputTokens: Int, outputTokens: Int): Double? =
        if (isFree(model)) 0.0 else null

    /** Riga informativa pronta da mostrare: token + costo stimato. */
    fun infoLine(model: String, inputTokens: Int, outputTokens: Int): String {
        val cost = costUsd(model, inputTokens, outputTokens)
        val costText = when {
            cost == null -> "n/d"
            cost == 0.0 -> "gratis"
            else -> "~ " + String.format(Locale.US, "$%.4f", cost)
        }
        return "Token: $inputTokens in / $outputTokens out · Costo: $costText · $model"
    }
}
