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
        get() = cachedApiKey
            ?: (PasswordSafe.instance.getPassword(credentialAttributes()) ?: "").also { cachedApiKey = it }
        set(value) {
            PasswordSafe.instance.setPassword(credentialAttributes(), value.ifBlank { null })
            cachedApiKey = value.ifBlank { "" }
        }

    private fun credentialAttributes(): CredentialAttributes =
        CredentialAttributes(generateServiceName("ChatGPT Assistant", "OPENAI_API_KEY"))

    companion object {
        const val DEFAULT_MODEL = "gpt-5"

        // I modelli con ragionamento (es. gpt-5) consumano token di reasoning che
        // rientrano nel limite: con un budget piccolo la risposta può arrivare vuota.
        const val DEFAULT_MAX_TOKENS = 16000
        val AVAILABLE_MODELS = listOf("gpt-5", "gpt-5-mini", "gpt-4.1", "gpt-4o")

        fun getInstance(): ChatGptSettings =
            ApplicationManager.getApplication().getService(ChatGptSettings::class.java)
    }
}
