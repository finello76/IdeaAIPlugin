package com.andreafini.claudeplugin.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Stato persistente per l'integrazione ChatGPT (OpenAI): il modello viene salvato nel
 * file di configurazione, mentre la API key viene conservata in modo sicuro tramite
 * [PasswordSafe]. Parallela e indipendente da [ClaudeSettings].
 */
@State(name = "ChatGptSettings", storages = [Storage("chatGptAssistant.xml")])
class ChatGptSettings : PersistentStateComponent<ChatGptSettings.State> {

    data class State(
        var model: String = DEFAULT_MODEL,
        var maxTokens: Int = DEFAULT_MAX_TOKENS,
        // Copia in chiaro della API key, usata SOLO come fallback quando il PasswordSafe
        // dell'IDE è in modalità solo-memoria (non sopravvive al riavvio). Resta vuota
        // quando lo store sicuro è disponibile.
        var apiKeyFallback: String = "",
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    var model: String
        get() = myState.model.ifBlank { DEFAULT_MODEL }
        set(value) {
            myState.model = value
        }

    var maxTokens: Int
        get() = if (myState.maxTokens > 0) myState.maxTokens else DEFAULT_MAX_TOKENS
        set(value) {
            myState.maxTokens = value
        }

    // Cache in memoria della API key: la visibilità delle azioni la legge in update(),
    // chiamato sull'EDT a ogni apertura del menu, e PasswordSafe può essere lento.
    @Volatile
    private var cachedApiKey: String? = null

    var apiKey: String
        get() {
            cachedApiKey?.let { return it }
            val safe = PasswordSafe.instance
            val value = if (safe.isMemoryOnly) {
                // Store sicuro non persistente su questo IDE: leggi la copia dal config.
                myState.apiKeyFallback
            } else {
                // Store sicuro disponibile. Se è rimasta una key nel fallback (configurata
                // quando il secure store era disattivato), migrala e ripulisci il config.
                val secure = safe.getPassword(credentialAttributes()) ?: ""
                if (secure.isBlank() && myState.apiKeyFallback.isNotBlank()) {
                    safe.setPassword(credentialAttributes(), myState.apiKeyFallback)
                    myState.apiKeyFallback.also { myState.apiKeyFallback = "" }
                } else {
                    secure
                }
            }
            return value.also { cachedApiKey = it }
        }
        set(value) {
            val normalized = value.ifBlank { "" }
            val safe = PasswordSafe.instance
            if (safe.isMemoryOnly) {
                // Nessun keychain persistente: salva in chiaro nel config così sopravvive al riavvio.
                myState.apiKeyFallback = normalized
            } else {
                safe.setPassword(credentialAttributes(), normalized.ifBlank { null })
                myState.apiKeyFallback = ""
            }
            cachedApiKey = normalized
        }

    /** true se il PasswordSafe dell'IDE non persiste tra i riavvii (fallback in chiaro attivo). */
    fun isSecureStorageUnavailable(): Boolean = PasswordSafe.instance.isMemoryOnly

    private fun credentialAttributes(): CredentialAttributes =
        CredentialAttributes(generateServiceName("ChatGPT Assistant", "OPENAI_API_KEY"))

    companion object {
        const val DEFAULT_MODEL = "gpt-5"

        // I modelli con ragionamento (es. gpt-5) consumano token di reasoning che
        // rientrano nel limite: con un budget piccolo la risposta può arrivare vuota.
        const val DEFAULT_MAX_TOKENS = 16000
        val AVAILABLE_MODELS = listOf("gpt-5.8", "gpt-5", "gpt-5-mini", "gpt-4.1", "gpt-4o")

        fun getInstance(): ChatGptSettings =
            ApplicationManager.getApplication().getService(ChatGptSettings::class.java)
    }
}
