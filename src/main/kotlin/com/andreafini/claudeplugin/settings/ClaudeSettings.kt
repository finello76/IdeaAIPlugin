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
 * Stato persistente del plugin: il modello viene salvato nel file di configurazione,
 * mentre la API key viene conservata in modo sicuro tramite [PasswordSafe].
 */
@State(name = "ClaudeSettings", storages = [Storage("claudeAssistant.xml")])
class ClaudeSettings : PersistentStateComponent<ClaudeSettings.State> {

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
        CredentialAttributes(generateServiceName("Claude Assistant", "ANTHROPIC_API_KEY"))

    companion object {
        const val DEFAULT_MODEL = "claude-opus-4-8"
        const val DEFAULT_MAX_TOKENS = 4096
        val AVAILABLE_MODELS = listOf("claude-opus-4-8", "claude-sonnet-5", "claude-haiku-4-5")

        fun getInstance(): ClaudeSettings =
            ApplicationManager.getApplication().getService(ClaudeSettings::class.java)
    }
}
