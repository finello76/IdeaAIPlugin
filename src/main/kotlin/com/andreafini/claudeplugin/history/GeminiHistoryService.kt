package com.andreafini.claudeplugin.history

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.XCollection
import java.util.UUID

/**
 * Cronologia delle interazioni con Gemini, persistente per progetto
 * (file `.idea/geminiAssistantHistory.xml`). Parallela e indipendente da
 * [ClaudeHistoryService] e [ChatGptHistoryService].
 */
@State(name = "GeminiHistory", storages = [Storage("geminiAssistantHistory.xml")])
class GeminiHistoryService : PersistentStateComponent<GeminiHistoryService.State> {

    /**
     * Una singola interazione. Classe con costruttore no-arg e campi `var`
     * per la serializzazione XML della piattaforma.
     */
    class Interaction() {
        var id: String = ""
        var conversationId: String = ""
        var timestampMillis: Long = 0
        var type: String = ""
        var request: String = ""
        var response: String = ""
        var model: String = ""
        var inputTokens: Int = 0
        var outputTokens: Int = 0

        constructor(
            id: String,
            timestampMillis: Long,
            type: String,
            request: String,
            response: String,
            model: String,
            inputTokens: Int,
            outputTokens: Int,
        ) : this() {
            this.id = id
            this.timestampMillis = timestampMillis
            this.type = type
            this.request = request
            this.response = response
            this.model = model
            this.inputTokens = inputTokens
            this.outputTokens = outputTokens
        }
    }

    class State {
        @get:XCollection(style = XCollection.Style.v2)
        var interactions: MutableList<Interaction> = mutableListOf()
        var useContext: Boolean = false
        var activeConversationId: String = ""
    }

    private var myState = State()

    // Listener notificati quando la cronologia cambia (per aggiornare la tool window).
    private val listeners = mutableListOf<Runnable>()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    var useContext: Boolean
        get() = myState.useContext
        set(value) {
            myState.useContext = value
        }

    var activeConversationId: String
        get() = myState.activeConversationId
        set(value) {
            myState.activeConversationId = value
            fireChanged()
        }

    /** Inizia una nuova conversazione (thread pulito) e la rende attiva. */
    fun startNewConversation(): String {
        val id = UUID.randomUUID().toString()
        activeConversationId = id
        return id
    }

    /** Restituisce le interazioni dalla più recente alla più vecchia. */
    fun all(): List<Interaction> = myState.interactions.toList()

    /** Interazioni della conversazione [conversationId], dalla più recente alla più vecchia. */
    fun conversation(conversationId: String): List<Interaction> =
        myState.interactions.filter { it.conversationId == conversationId }

    /** Titolo di una conversazione: la richiesta della sua interazione più vecchia. */
    fun conversationTitle(conversationId: String): String {
        val items = conversation(conversationId)
        if (items.isEmpty()) return "(nuova conversazione)"
        val oldest = items.last() // la lista è dal più recente al più vecchio
        return oldest.request.replace("\n", " ").take(40).ifBlank { "(conversazione)" }
    }

    /**
     * Aggiunge un'interazione alla conversazione attiva (creata se assente),
     * in testa, con un tetto massimo di voci.
     */
    fun add(interaction: Interaction) {
        if (myState.activeConversationId.isBlank()) {
            myState.activeConversationId = UUID.randomUUID().toString()
        }
        interaction.conversationId = myState.activeConversationId
        myState.interactions.add(0, interaction)
        while (myState.interactions.size > MAX_ENTRIES) {
            myState.interactions.removeAt(myState.interactions.size - 1)
        }
        fireChanged()
    }

    fun remove(id: String) {
        myState.interactions.removeAll { it.id == id }
        fireChanged()
    }

    fun clear() {
        myState.interactions.clear()
        fireChanged()
    }

    fun addListener(listener: Runnable) {
        listeners.add(listener)
    }

    fun removeListener(listener: Runnable) {
        listeners.remove(listener)
    }

    fun fireChanged() {
        listeners.toList().forEach { it.run() }
    }

    companion object {
        const val MAX_ENTRIES = 200

        /** Numero di interazioni passate incluse quando il contesto è attivo. */
        const val CONTEXT_WINDOW = 8

        fun getInstance(project: Project): GeminiHistoryService =
            project.getService(GeminiHistoryService::class.java)
    }
}
