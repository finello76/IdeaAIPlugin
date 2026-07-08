# IdeaAIPlugin (Claude + ChatGPT + Gemini) — Plugin per IntelliJ IDEA 2021.2.1

Plugin (Kotlin) che integra **tre** provider AI in IntelliJ IDEA — Claude (Anthropic),
ChatGPT (OpenAI) e Gemini (Google) — in modo indipendente e simmetrico. Per ciascun
provider ci sono tre voci nel menu contestuale dell'editor (click destro):

- **Refactor / Migliora** — invia il testo selezionato chiedendo di rifattorizzarlo;
  il risultato appare in un popup con i pulsanti **Copia** e **Inserisci nel file**
  (sostituisce la selezione).
- **Genera codice** — apre un dialog dove scrivere cosa creare; allega automaticamente
  il contenuto del file aperto come contesto e mostra il risultato nello stesso popup.
- **Analizza codice** — verifica il codice cercando errori, bug potenziali e possibili
  miglioramenti. Se c'è una **selezione** analizza solo quella, altrimenti l'**intero
  file** aperto. Un dialog permette di scegliere il **tipo di analisi** (Analisi completa,
  Bug e correttezza, Miglioramenti/refactoring, Performance, Sicurezza, oppure una
  **Domanda specifica**) e di aggiungere una richiesta libera; la risposta è un elenco
  puntato con gravità e correzioni suggerite (o la risposta diretta alla domanda).

Ogni provider ha inoltre la propria **tool window laterale** con la cronologia delle
conversazioni (raggruppate per thread, con contesto conversazionale opzionale).

### Visibilità condizionata alle API key

I pulsanti e la tool window di un provider **compaiono solo se la relativa API key è
configurata**:

- se manca la key di Anthropic, le voci "Claude" e la tool window "Claude" non si vedono;
- se manca la key di OpenAI, le voci "ChatGPT" e la tool window "ChatGPT" non si vedono;
- se manca la key di Google, le voci "Gemini" e la tool window "Gemini" non si vedono.

I tre provider possono essere **attivi contemporaneamente**: configurando tutte le key
compaiono tutti e sei i pulsanti e tutte e tre le tool window.

> Nota: le tool window valutano la presenza della key all'apertura del progetto. Se
> aggiungi una key mentre il progetto è già aperto, riaprilo per far comparire il pannello.
> I pulsanti nel menu editor, invece, si aggiornano immediatamente.

## Configurazione

Impostazioni (`Settings/Preferences`) → **Tools** (le tre pagine sono raggruppate dal
prefisso comune **IdeaAIPlugin**):

- **IdeaAIPlugin Claude**
  - **API key (Anthropic)** — salvata in modo sicuro nel PasswordSafe dell'IDE.
  - **Modello** — default `claude-opus-4-8` il più capace (anche `claude-sonnet-5`
    equilibrato, `claude-haiku-4-5` veloce ed economico).
- **IdeaAIPlugin ChatGPT**
  - **API key (OpenAI)** — salvata in modo sicuro nel PasswordSafe dell'IDE.
  - **Modello** — default `gpt-5` (anche `gpt-5-mini` economico, `gpt-4.1`, `gpt-4o`).
- **IdeaAIPlugin Gemini**
  - **API key (Google AI Studio)** — salvata in modo sicuro nel PasswordSafe dell'IDE.
  - **Modello** — default `gemini-2.5-pro` (anche `gemini-2.5-flash` veloce e ideale per
    refactor semplici, `gemini-2.0-flash`, `gemini-1.5-pro`).

## Prerequisiti di build

- **JDK 11** (obbligatorio per la piattaforma IntelliJ 212).
- **Gradle** (o il wrapper generato aprendo il progetto in IntelliJ).

Questo repository non include il binario `gradle/wrapper/gradle-wrapper.jar`.
Per generarlo:

```bash
# opzione A: hai Gradle installato
gradle wrapper --gradle-version 7.6

# opzione B: apri la cartella in IntelliJ IDEA: importerà il progetto Gradle
# e scaricherà automaticamente il wrapper.
```

## Build ed esecuzione

```bash
./gradlew buildPlugin   # crea build/distributions/claude-idea-plugin-1.1.zip
./gradlew runIde        # avvia una IntelliJ 2021.2.1 sandbox con il plugin caricato
```

## Struttura

```
src/main/resources/META-INF/plugin.xml   Registrazione azioni, settings, tool window, notifiche
src/main/resources/icons/                claude.svg / chatgpt.svg / gemini.svg (menu e tool window)

src/main/kotlin/com/andreafini/claudeplugin/
    ui/ResultPopup.kt            Popup risultato condiviso (Copia / Inserisci)
    ui/PromptDialog.kt           Dialog input condiviso per "Genera codice"
    ui/CodeViewer.kt             Editor read-only condiviso con evidenziazione sintassi
    action/AnalyzeCodeAction.kt  Pulsante Claude "Analizza codice" + AnalyzePrompt (prompt condiviso)

    # --- Claude (Anthropic) ---
    api/ClaudeClient.kt          Chiamata HTTP a /v1/messages (JDK 11 HttpClient + Gson)
    api/ClaudePricing.kt         Stima costo per modello Anthropic
    settings/ClaudeSettings.kt   Modello persistente + API key nel PasswordSafe
    settings/ClaudeConfigurable.kt   Pagina Impostazioni ("IdeaAIPlugin Claude")
    action/RefactorAction.kt / GenerateCodeAction.kt / AnalyzeCodeAction.kt   I tre pulsanti
    action/ClaudeActionSupport.kt   Esecuzione in background + cronologia + notifiche
    history/ClaudeHistoryService.kt Cronologia persistente per progetto
    toolwindow/ClaudeToolWindowFactory.kt / ClaudeHistoryPanel.kt   Tool window laterale

    # --- ChatGPT (OpenAI): replica simmetrica ---
    api/ChatGptClient.kt         Chiamata HTTP a /v1/chat/completions
    api/ChatGptPricing.kt        Stima costo per modello OpenAI
    settings/ChatGptSettings.kt / ChatGptConfigurable.kt   Settings + Impostazioni ("IdeaAIPlugin ChatGPT")
    action/ChatGptRefactorAction.kt / ChatGptGenerateCodeAction.kt / ChatGptAnalyzeCodeAction.kt   I tre pulsanti
    action/ChatGptActionSupport.kt   Esecuzione in background + cronologia + notifiche
    history/ChatGptHistoryService.kt Cronologia persistente per progetto
    toolwindow/ChatGptToolWindowFactory.kt / ChatGptHistoryPanel.kt   Tool window laterale

    # --- Gemini (Google): replica simmetrica ---
    api/GeminiClient.kt          Chiamata HTTP a /v1beta/models/{model}:generateContent
    api/GeminiPricing.kt         Stima costo per modello Google
    settings/GeminiSettings.kt / GeminiConfigurable.kt   Settings + Impostazioni ("IdeaAIPlugin Gemini")
    action/GeminiRefactorAction.kt / GeminiGenerateCodeAction.kt / GeminiAnalyzeCodeAction.kt   I tre pulsanti
    action/GeminiActionSupport.kt   Esecuzione in background + cronologia + notifiche
    history/GeminiHistoryService.kt Cronologia persistente per progetto
    toolwindow/GeminiToolWindowFactory.kt / GeminiHistoryPanel.kt   Tool window laterale
```
