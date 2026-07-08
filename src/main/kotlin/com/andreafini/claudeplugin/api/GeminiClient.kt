package com.andreafini.claudeplugin.api

import com.andreafini.claudeplugin.settings.GeminiSettings
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/** Errore applicativo con messaggio leggibile per l'utente. */
class GeminiException(message: String) : Exception(message)

/** Un messaggio della conversazione: ruolo ("user"/"model") + contenuto testuale. */
data class GeminiMessage(val role: String, val content: String)

/** Risultato di una chiamata: testo della risposta + token usati. */
data class GeminiResult(val text: String, val inputTokens: Int, val outputTokens: Int)

/**
 * Client minimale per l'endpoint generateContent di Google Gemini.
 * Usa [HttpClient] di JDK 11 e Gson (presente nel classpath della piattaforma).
 */
object GeminiClient {

    private const val BASE_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    /**
     * Invia un singolo prompt come messaggio "user".
     * Comodità che delega a [sendMessages]. Da chiamare fuori dall'EDT.
     */
    @Throws(GeminiException::class)
    fun sendMessage(prompt: String): GeminiResult =
        sendMessages(listOf(GeminiMessage("user", prompt)))

    /**
     * Invia l'intera conversazione [messages] a Gemini e restituisce testo + token usati.
     * Da chiamare fuori dall'Event Dispatch Thread.
     */
    @Throws(GeminiException::class)
    fun sendMessages(messages: List<GeminiMessage>): GeminiResult {
        val settings = GeminiSettings.getInstance()
        val apiKey = settings.apiKey
        if (apiKey.isBlank()) {
            throw GeminiException(
                "API key mancante. Impostala in Settings > Tools > Gemini Assistant."
            )
        }

        val body = JsonObject().apply {
            add("contents", JsonArray().apply {
                for (msg in messages) {
                    add(JsonObject().apply {
                        // Gemini usa i ruoli "user" e "model".
                        addProperty("role", if (msg.role == "assistant") "model" else msg.role)
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", msg.content) })
                        })
                    })
                }
            })
            add("generationConfig", JsonObject().apply {
                addProperty("maxOutputTokens", settings.maxTokens)
            })
        }

        val model = settings.model
        val encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
        val endpoint = "$BASE_ENDPOINT/$model:generateContent?key=$encodedKey"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofSeconds(120))
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

        val response: HttpResponse<String> = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw GeminiException("Errore di rete durante la chiamata a Gemini: ${e.message}")
        }

        val json = try {
            JsonParser.parseString(response.body()).asJsonObject
        } catch (e: Exception) {
            throw GeminiException("Risposta non valida dall'API (HTTP ${response.statusCode()}).")
        }

        if (response.statusCode() != 200) {
            val message = json.getAsJsonObject("error")?.get("message")?.asString
                ?: "HTTP ${response.statusCode()}"
            throw GeminiException("Errore dall'API di Gemini: $message")
        }

        val candidates = json.getAsJsonArray("candidates")
        if (candidates == null || candidates.size() == 0) {
            throw GeminiException("Risposta vuota da Gemini.")
        }

        val content = candidates[0].asJsonObject.getAsJsonObject("content")
        val parts = content?.getAsJsonArray("parts")
        val text = buildString {
            if (parts != null) {
                for (part in parts) {
                    val t = part.asJsonObject.get("text")
                    if (t != null && !t.isJsonNull) append(t.asString)
                }
            }
        }
        if (text.isBlank()) {
            throw GeminiException("Gemini non ha restituito testo.")
        }

        val usage = json.getAsJsonObject("usageMetadata")
        val inputTokens = usage?.get("promptTokenCount")?.asInt ?: 0
        val outputTokens = usage?.get("candidatesTokenCount")?.asInt ?: 0
        return GeminiResult(text, inputTokens, outputTokens)
    }
}
