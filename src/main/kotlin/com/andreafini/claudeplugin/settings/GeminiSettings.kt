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
 * Stato persistente per l'integrazione Gemini (Google): il modello viene salvato nel
 * file di configurazione, mentre la API key viene conservata in modo sicuro tramite
 * [PasswordSafe]. Parallela e indipendente da [ClaudeSettings] e [ChatGptSettings].
 */
@State(name = "GeminiSettings", storages = [Storage("geminiAssistant.xml")])
class GeminiSettings : PersistentStateComponent<GeminiSettings.State> {

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

    var apiKey: String
        get() = PasswordSafe.instance.getPassword(credentialAttributes()) ?: ""
        set(value) {
            PasswordSafe.instance.setPassword(credentialAttributes(), value.ifBlank { null })
        }

    private fun credentialAttributes(): CredentialAttributes =
        CredentialAttributes(generateServiceName("Gemini Assistant", "GEMINI_API_KEY"))

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-pro"
        const val DEFAULT_MAX_TOKENS = 4096
        val AVAILABLE_MODELS = listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-pro")

        fun getInstance(): GeminiSettings =
            ApplicationManager.getApplication().getService(GeminiSettings::class.java)
    }
}
