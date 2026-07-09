package com.andreafini.claudeplugin.api

import com.andreafini.claudeplugin.settings.OpenRouterSettings
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Errore applicativo con messaggio leggibile per l'utente. */
class OpenRouterException(message: String) : Exception(message)

/** Un messaggio della conversazione: ruolo ("user"/"assistant") + contenuto testuale. */
data class OpenRouterMessage(val role: String, val content: String)

/** Risultato di una chiamata: testo della risposta + token usati. */
data class OpenRouterResult(val text: String, val inputTokens: Int, val outputTokens: Int)

/**
 * Client minimale per l'endpoint Chat Completions di OpenRouter (aggregatore OpenAI-compatibile).
 * Con una sola API key dà accesso a centinaia di modelli, molti dei quali gratuiti (suffisso
 * `:free`). Il protocollo è identico a quello di OpenAI, quindi la struttura ricalca
 * [ChatGptClient] cambiando solo endpoint e header. Usa [HttpClient] di JDK 11 e Gson.
 */
object OpenRouterClient {

    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    // Header facoltativi consigliati da OpenRouter per identificare l'app nelle statistiche.
    private const val REFERER = "https://github.com/andreafini/IdeaClaudePlugin"
    private const val APP_TITLE = "IdeaAIPlugin"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    /**
     * Invia un singolo prompt come messaggio "user".
     * Comodità che delega a [sendMessages]. Da chiamare fuori dall'EDT.
     */
    @Throws(OpenRouterException::class)
    fun sendMessage(prompt: String): OpenRouterResult =
        sendMessages(listOf(OpenRouterMessage("user", prompt)))

    /**
     * Invia l'intera conversazione [messages] a OpenRouter e restituisce testo + token usati.
     * Da chiamare fuori dall'Event Dispatch Thread.
     */
    @Throws(OpenRouterException::class)
    fun sendMessages(messages: List<OpenRouterMessage>): OpenRouterResult {
        val settings = OpenRouterSettings.getInstance()
        val apiKey = settings.apiKey
        if (apiKey.isBlank()) {
            throw OpenRouterException(
                "API key mancante. Impostala in Settings > Tools > IdeaAIPlugin OpenRouter."
            )
        }

        val body = JsonObject().apply {
            addProperty("model", settings.model)
            addProperty("max_tokens", settings.maxTokens)
            add("messages", JsonArray().apply {
                for (msg in messages) {
                    add(JsonObject().apply {
                        addProperty("role", msg.role)
                        addProperty("content", msg.content)
                    })
                }
            })
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT))
            .timeout(Duration.ofSeconds(120))
            .header("content-type", "application/json")
            .header("authorization", "Bearer $apiKey")
            .header("HTTP-Referer", REFERER)
            .header("X-Title", APP_TITLE)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

        val response: HttpResponse<String> = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw OpenRouterException("Errore di rete durante la chiamata a OpenRouter: ${e.message}")
        }

        val json = try {
            JsonParser.parseString(response.body()).asJsonObject
        } catch (e: Exception) {
            throw OpenRouterException("Risposta non valida dall'API (HTTP ${response.statusCode()}).")
        }

        if (response.statusCode() != 200) {
            val message = json.getAsJsonObject("error")?.get("message")?.asString
                ?: "HTTP ${response.statusCode()}"
            throw OpenRouterException("Errore dall'API di OpenRouter: $message")
        }

        val choices = json.getAsJsonArray("choices")
        if (choices == null || choices.size() == 0) {
            // OpenRouter può restituire 200 con un errore annidato (es. modello non disponibile).
            val message = json.getAsJsonObject("error")?.get("message")?.asString
            if (message != null) throw OpenRouterException("Errore dall'API di OpenRouter: $message")
            throw OpenRouterException("Risposta vuota da OpenRouter.")
        }

        val message = choices[0].asJsonObject.getAsJsonObject("message")
        val text = message?.get("content")?.let {
            if (it.isJsonNull) "" else it.asString
        } ?: ""
        if (text.isBlank()) {
            val finishReason = choices[0].asJsonObject.get("finish_reason")
                ?.takeIf { !it.isJsonNull }?.asString
            if (finishReason == "length") {
                throw OpenRouterException(
                    "Risposta vuota: raggiunto il limite di token (max=${settings.maxTokens}) " +
                        "prima di produrre testo (i modelli con ragionamento consumano il budget). " +
                        "Aumenta 'Max token risposta' in Settings > Tools > IdeaAIPlugin OpenRouter."
                )
            }
            throw OpenRouterException("OpenRouter non ha restituito testo.")
        }

        val usage = json.getAsJsonObject("usage")
        val inputTokens = usage?.get("prompt_tokens")?.asInt ?: 0
        val outputTokens = usage?.get("completion_tokens")?.asInt ?: 0
        return OpenRouterResult(text, inputTokens, outputTokens)
    }
}
