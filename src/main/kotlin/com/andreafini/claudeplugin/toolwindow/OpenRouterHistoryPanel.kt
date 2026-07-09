package com.andreafini.claudeplugin.toolwindow

import com.andreafini.claudeplugin.action.CrossEngine
import com.andreafini.claudeplugin.action.OpenRouterActionSupport
import com.andreafini.claudeplugin.api.OpenRouterPricing
import com.andreafini.claudeplugin.history.OpenRouterHistoryService
import com.andreafini.claudeplugin.ui.CodeViewer
import com.andreafini.claudeplugin.ui.MarkdownViewer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.ToolTipManager

/** Pannello della tool window OpenRouter: conversazioni + interazioni + dettaglio + azioni. */
class OpenRouterHistoryPanel(private val project: Project) : JBPanel<OpenRouterHistoryPanel>(BorderLayout()) {

    private val history = OpenRouterHistoryService.getInstance(project)
    private val listModel = DefaultListModel<OpenRouterHistoryService.Interaction>()
    private val list = object : JBList<OpenRouterHistoryService.Interaction>(listModel) {
        override fun getToolTipText(event: MouseEvent): String? {
            val index = locationToIndex(event.point)
            if (index < 0 || index >= model.size) return null
            return tooltipHtml(model.getElementAt(index))
        }
    }
    private val detailContainer = JPanel(BorderLayout())
    private val activeLabel = JBLabel()
    private val timeFormat = SimpleDateFormat("dd/MM HH:mm")

    // Vero mentre reload imposta la selezione a livello di codice: evita che il
    // listener di selezione cambi la conversazione attiva (e crei ricorsione).
    private var updatingSelection = false

    private val reloadListener = Runnable {
        ApplicationManager.getApplication().invokeLater { reload() }
    }

    init {
        // Barra superiore: contesto + nuova conversazione + svuota.
        val top = JPanel(FlowLayout(FlowLayout.LEFT))
        val contextCheck = JBCheckBox("Contesto conversazionale", history.useContext).apply {
            toolTipText = "Se attivo, ogni richiesta include le ultime interazioni della " +
                "conversazione attiva (più token = più costo)."
            addActionListener { history.useContext = isSelected }
        }
        top.add(contextCheck)
        top.add(JButton("Nuova conversazione").apply {
            toolTipText = "Inizia un thread pulito: le prossime azioni non useranno il contesto precedente."
            addActionListener { history.startNewConversation() }
        })
        top.add(JButton("Svuota cronologia").apply {
            addActionListener {
                val answer = Messages.showYesNoDialog(
                    project, "Svuotare tutta la cronologia?", "OpenRouter Assistant", null,
                )
                if (answer == Messages.YES) history.clear()
            }
        })

        val header = JPanel(BorderLayout())
        header.add(top, BorderLayout.NORTH)
        header.add(activeLabel, BorderLayout.SOUTH)
        add(header, BorderLayout.NORTH)

        // Lista a sinistra, raggruppata per conversazione.
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = object : ColoredListCellRenderer<OpenRouterHistoryService.Interaction>() {
            override fun customizeCellRenderer(
                list: JList<out OpenRouterHistoryService.Interaction>,
                value: OpenRouterHistoryService.Interaction,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                val active = value.conversationId == history.activeConversationId
                val idAttr = if (active) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                else SimpleTextAttributes.GRAYED_ATTRIBUTES
                val time = timeFormat.format(Date(value.timestampMillis))
                append("[${shortId(value.conversationId)}] ", idAttr)
                append("$time · ${value.type}: ${value.request.replace("\n", " ").take(50)}")
            }
        }
        list.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val sel = list.selectedValue
                if (!updatingSelection && sel != null && sel.conversationId != history.activeConversationId) {
                    history.activeConversationId = sel.conversationId
                }
                showDetail(sel)
            }
        }

        ToolTipManager.sharedInstance().registerComponent(list)

        val splitter = JBSplitter(false, 0.35f)
        splitter.firstComponent = JBScrollPane(list)
        splitter.secondComponent = detailContainer
        add(splitter, BorderLayout.CENTER)

        showDetail(null)
    }

    override fun addNotify() {
        super.addNotify()
        history.addListener(reloadListener)
        reload()
    }

    override fun removeNotify() {
        history.removeListener(reloadListener)
        super.removeNotify()
    }

    private fun shortId(conversationId: String): String =
        if (conversationId.isBlank()) "legacy" else conversationId.take(8)

    /**
     * Tooltip multiriga (HTML) con la richiesta completa dell'interazione, mandata a capo
     * su larghezza fissa così non viene troncato quando il testo è lungo.
     */
    private fun tooltipHtml(interaction: OpenRouterHistoryService.Interaction): String {
        val text = interaction.request.ifBlank { history.conversationTitle(interaction.conversationId) }
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
        return "<html><body style='width:320px'>$escaped</body></html>"
    }

    private fun reload() {
        activeLabel.text = " Conversazione attiva: " + shortId(history.activeConversationId) +
            " — " + history.conversationTitle(history.activeConversationId)

        val selectedId = list.selectedValue?.id
        listModel.clear()
        val items = history.all()
        val lastTs = items.groupBy { it.conversationId }
            .mapValues { e -> e.value.maxOfOrNull { it.timestampMillis } ?: 0L }
        val sorted = items.sortedWith(
            compareByDescending<OpenRouterHistoryService.Interaction> { lastTs[it.conversationId] ?: 0L }
                .thenByDescending { it.timestampMillis }
        )
        sorted.forEach { listModel.addElement(it) }

        var targetIndex = -1
        if (selectedId != null) {
            for (i in 0 until listModel.size()) {
                if (listModel.get(i).id == selectedId) {
                    targetIndex = i
                    break
                }
            }
        }
        updatingSelection = true
        try {
            if (targetIndex >= 0) list.selectedIndex = targetIndex else list.clearSelection()
        } finally {
            updatingSelection = false
        }
        showDetail(list.selectedValue)
    }

    private fun showDetail(interaction: OpenRouterHistoryService.Interaction?) {
        detailContainer.removeAll()
        if (interaction == null) {
            detailContainer.add(
                JBLabel("Seleziona un'interazione dalla lista.", SwingConstants.CENTER),
                BorderLayout.CENTER,
            )
        } else {
            val info = OpenRouterPricing.infoLine(
                interaction.model, interaction.inputTokens, interaction.outputTokens,
            )
            detailContainer.add(JBLabel(" $info"), BorderLayout.NORTH)

            // Le analisi sono in Markdown (prosa): mostrate formattate; il resto è codice.
            val viewer = if (interaction.type == "Analizza")
                MarkdownViewer.create(interaction.response)
            else
                CodeViewer.create(project, interaction.response, softWraps = true)
            detailContainer.add(viewer, BorderLayout.CENTER)

            val buttons = JPanel(FlowLayout(FlowLayout.LEFT))
            buttons.add(JButton("Copia").apply {
                addActionListener { copyToClipboard(interaction.response) }
            })
            buttons.add(JButton("Inserisci nel file").apply {
                addActionListener {
                    copyToClipboard(interaction.response)
                    val ok = OpenRouterActionSupport.insertIntoEditor(project, null, interaction.response)
                    if (!ok) {
                        OpenRouterActionSupport.notifyError(project, "Nessun file aperto in cui inserire.")
                    }
                }
            })
            buttons.add(rerunButton(interaction))
            buttons.add(JButton("Elimina").apply {
                addActionListener { history.remove(interaction.id) }
            })
            detailContainer.add(buttons, BorderLayout.SOUTH)
        }
        detailContainer.revalidate()
        detailContainer.repaint()
    }

    /** Pulsante che rilancia la richiesta selezionata su un altro motore AI configurato. */
    private fun rerunButton(interaction: OpenRouterHistoryService.Interaction): JButton {
        val button = JButton("Rilancia su ▾")
        button.toolTipText = "Invia la stessa richiesta a un altro motore AI configurato"
        button.addActionListener {
            val targets = CrossEngine.availableTargets(CrossEngine.Engine.OPENROUTER)
            if (targets.isEmpty()) {
                Messages.showInfoMessage(
                    project,
                    "Nessun altro motore configurato. Aggiungi una API key in Settings > Tools.",
                    "Rilancia su",
                )
                return@addActionListener
            }
            val promptToSend = interaction.prompt.ifBlank { interaction.request }
            if (promptToSend.isBlank()) {
                OpenRouterActionSupport.notifyError(project, "Questa interazione non ha un prompt da rilanciare.")
                return@addActionListener
            }
            val menu = JPopupMenu()
            for (target in targets) {
                menu.add(JMenuItem(target.displayName).apply {
                    addActionListener {
                        CrossEngine.resend(project, target, interaction.type, interaction.request, promptToSend)
                    }
                })
            }
            menu.show(button, 0, button.height)
        }
        return button
    }

    private fun copyToClipboard(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }
}
