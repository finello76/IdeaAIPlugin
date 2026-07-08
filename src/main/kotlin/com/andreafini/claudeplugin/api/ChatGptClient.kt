package com.andreafini.claudeplugin.api

import com.andreafini.claudeplugin.settings.ChatGptSettings
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Errore applicativo con messaggio leggibile per l'utente. */
class ChatGptException(message: String) : Exception(message)

/** Un messaggio della conversazione: ruolo ("user"/"assistant") + contenuto testuale. */
data class ChatGptMessage(val role: String, val content: String)

/** Risultato di una chiamata: testo della risposta + token usati. */
data class ChatGptResult(val text: String, val inputTokens: Int, val outputTokens: Int)

/**
 * Client minimale per l'endpoint Chat Completions di OpenAI.
 * Usa [HttpClient] di JDK 11 e Gson (presente nel classpath della piattaforma).
 */
object ChatGptClient {

    private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    /**
     * Invia un singolo prompt come messaggio "user".
     * Comodità che delega a [sendMessages]. Da chiamare fuori dall'EDT.
     */
    @Throws(ChatGptException::class)
    fun sendMessage(prompt: String): ChatGptResult =
        sendMessages(listOf(ChatGptMessage("user", prompt)))

    /**
     * Invia l'intera conversazione [messages] a ChatGPT e restituisce testo + token usati.
     * Da chiamare fuori dall'Event Dispatch Thread.
     */
    @Throws(ChatGptException::class)
    fun sendMessages(messages: List<ChatGptMessage>): ChatGptResult {
        val settings = ChatGptSettings.getInstance()
        val apiKey = settings.apiKey
        if (apiKey.isBlank()) {
            throw ChatGptException(
                "API key mancante. Impostala in Settings > Tools > ChatGPT Assistant."
            )
        }

        val body = JsonObject().apply {
            addProperty("model", settings.model)
            // Parametro moderno accettato dai modelli recenti (gpt-5, gpt-4.1, gpt-4o, ...).
            addProperty("max_completion_tokens", settings.maxTokens)
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
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

        val response: HttpResponse<String> = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw ChatGptException("Errore di rete durante la chiamata a ChatGPT: ${e.message}")
        }

        val json = try {
            JsonParser.parseString(response.body()).asJsonObject
        } catch (e: Exception) {
            throw ChatGptException("Risposta non valida dall'API (HTTP ${response.statusCode()}).")
        }

        if (response.statusCode() != 200) {
            val message = json.getAsJsonObject("error")?.get("message")?.asString
                ?: "HTTP ${response.statusCode()}"
            throw ChatGptException("Errore dall'API di ChatGPT: $message")
        }

        val choices = json.getAsJsonArray("choices")
        if (choices == null || choices.size() == 0) {
            throw ChatGptException("Risposta vuota da ChatGPT.")
        }

        val message = choices[0].asJsonObject.getAsJsonObject("message")
        val text = message?.get("content")?.let {
            if (it.isJsonNull) "" else it.asString
        } ?: ""
        if (text.isBlank()) {
            throw ChatGptException("ChatGPT non ha restituito testo.")
        }

        val usage = json.getAsJsonObject("usage")
        val inputTokens = usage?.get("prompt_tokens")?.asInt ?: 0
        val outputTokens = usage?.get("completion_tokens")?.asInt ?: 0
        return ChatGptResult(text, inputTokens, outputTokens)
    }
}
