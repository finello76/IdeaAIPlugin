package com.andreafini.claudeplugin.api

import com.andreafini.claudeplugin.settings.ClaudeSettings
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Errore applicativo con messaggio leggibile per l'utente. */
class ClaudeException(message: String) : Exception(message)

/** Un messaggio della conversazione: ruolo ("user"/"assistant") + contenuto testuale. */
data class ClaudeMessage(val role: String, val content: String)

/** Risultato di una chiamata: testo della risposta + token usati. */
data class ClaudeResult(val text: String, val inputTokens: Int, val outputTokens: Int)

/**
 * Client minimale per l'endpoint Messages di Anthropic.
 * Usa [HttpClient] di JDK 11 e Gson (presente nel classpath della piattaforma).
 */
object ClaudeClient {

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val ANTHROPIC_VERSION = "2023-06-01"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    /**
     * Invia un singolo prompt come messaggio "user".
     * Comodità che delega a [sendMessages]. Da chiamare fuori dall'EDT.
     */
    @Throws(ClaudeException::class)
    fun sendMessage(prompt: String): ClaudeResult =
        sendMessages(listOf(ClaudeMessage("user", prompt)))

    /**
     * Invia l'intera conversazione [messages] a Claude e restituisce testo + token usati.
     * Da chiamare fuori dall'Event Dispatch Thread.
     */
    @Throws(ClaudeException::class)
    fun sendMessages(messages: List<ClaudeMessage>): ClaudeResult {
        val settings = ClaudeSettings.getInstance()
        val apiKey = settings.apiKey
        if (apiKey.isBlank()) {
            throw ClaudeException(
                "API key mancante. Impostala in Settings > Tools > Claude Assistant."
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
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

        val response: HttpResponse<String> = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw ClaudeException("Errore di rete durante la chiamata a Claude: ${e.message}")
        }

        val json = try {
            JsonParser.parseString(response.body()).asJsonObject
        } catch (e: Exception) {
            throw ClaudeException("Risposta non valida dall'API (HTTP ${response.statusCode()}).")
        }

        if (response.statusCode() != 200) {
            val message = json.getAsJsonObject("error")?.get("message")?.asString
                ?: "HTTP ${response.statusCode()}"
            throw ClaudeException("Errore dall'API di Claude: $message")
        }

        val content = json.getAsJsonArray("content")
        if (content == null || content.size() == 0) {
            throw ClaudeException("Risposta vuota da Claude.")
        }

        // Concatena tutti i blocchi di testo restituiti.
        val sb = StringBuilder()
        for (element in content) {
            val block = element.asJsonObject
            if (block.get("type")?.asString == "text") {
                sb.append(block.get("text")?.asString ?: "")
            }
        }
        val text = sb.toString()
        if (text.isBlank()) {
            val stopReason = json.get("stop_reason")?.takeIf { !it.isJsonNull }?.asString
            if (stopReason == "max_tokens") {
                throw ClaudeException(
                    "Risposta vuota: raggiunto il limite di token (max_tokens=${settings.maxTokens}) " +
                        "prima di produrre testo. Aumenta 'Max token risposta' in " +
                        "Settings > Tools > IdeaAIPlugin Claude."
                )
            }
            throw ClaudeException("Claude non ha restituito testo.")
        }

        val usage = json.getAsJsonObject("usage")
        val inputTokens = usage?.get("input_tokens")?.asInt ?: 0
        val outputTokens = usage?.get("output_tokens")?.asInt ?: 0
        return ClaudeResult(text, inputTokens, outputTokens)
    }
}
