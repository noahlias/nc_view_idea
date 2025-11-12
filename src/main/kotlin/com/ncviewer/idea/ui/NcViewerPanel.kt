package com.ncviewer.idea.ui

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.JBUI
import com.ncviewer.idea.bridge.NcViewerHttpBridge
import com.ncviewer.idea.log.NcViewerLogConfig
import com.ncviewer.idea.log.NcViewerLogService
import com.ncviewer.idea.settings.NcViewerSettings
import com.ncviewer.idea.util.MediaExtractor
import java.awt.BorderLayout
import java.nio.file.Files
import javax.swing.JComponent
import javax.swing.JPanel

class NcViewerPanel(private val project: Project) : Disposable {

    private val logger = logger<NcViewerPanel>()
    private val logService = NcViewerLogService.getInstance(project)
    private val gson = Gson()
    private val browser: JBCefBrowser = JBCefBrowserBuilder().setUrl("about:blank").build()
    private val httpBridge = NcViewerHttpBridge(logService)

    private val rootPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty()
        add(browser.component, BorderLayout.CENTER)
    }

    val component: JComponent
        get() = rootPanel

    private var currentEditor: Editor? = null
    private var caretListener: CaretListener? = null
    private var documentListener: DocumentListener? = null
    private var isWebviewReady = false

    init {
        logger.info("Initializing NcViewerPanel")
        logService.log("NcViewerPanel: initializing")

        ensureDevToolsContextMenuSystemProperty()
        enableJcefDevToolsContextMenu()
        httpBridge.addIncomingListener { payload ->
            logger.debug("HTTP bridge payload: ${payload.take(200)}")
            logService.log("HTTP bridge payload: ${payload.take(200)}")
            handleIncomingMessage(payload)
        }

        loadViewerAssets()
    }

    fun attachEditor(editor: Editor) {
        if (currentEditor == editor) {
            logger.debug("attachEditor called with same editor; re-sending load")
            sendLoadGCode(editor)
            return
        }

        detachListeners()
        currentEditor = editor
        logger.info("Attached editor ${editor.document}")
        logService.log("NcViewerPanel: attached editor fileType=${editor.virtualFile?.fileType?.name} path=${editor.virtualFile?.path}")

        caretListener = object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                val line = event.newPosition.line
                sendMessage(NcViewerMessage(type = "cursorPositionChanged", lineNumber = line))
            }
        }.also { editor.caretModel.addCaretListener(it) }

        documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                sendMessage(
                    NcViewerMessage(
                        type = "contentChanged",
                        ncText = event.document.text,
                        settings = buildSettingsPayload(),
                    ),
                )
            }
        }.also { editor.document.addDocumentListener(it, this) }

        sendLoadGCode(editor)
    }

    private fun sendLoadGCode(editor: Editor) {
        val length = editor.document.textLength
        logger.info("Sending loadGCode message ($length chars)")
        logService.log("NcViewerPanel: send loadGCode length=$length")
        sendMessage(
            NcViewerMessage(
                type = "loadGCode",
                ncText = editor.document.text,
                settings = buildSettingsPayload(),
            ),
        )
    }

    private fun buildSettingsPayload() =
        SettingsPayload(excludeCodes = NcViewerSettings.getInstance().getExcludedCodes())

    private fun loadViewerAssets() {
        val mediaRoot = MediaExtractor.ensureMediaExtracted()
        val indexFile = mediaRoot.resolve("index.html")
        val bundleFile = mediaRoot.resolve("bundle.js")
        val bundleExists = Files.exists(bundleFile)
        logService.log("NcViewerPanel: bundle exists=$bundleExists at $bundleFile")
        if (!bundleExists) {
            logger.warn("bundle.js missing at $bundleFile")
        }
        logger.info("Loading viewer assets from $indexFile")
        logService.log("NcViewerPanel: loading assets from $indexFile")
        val html = Files.readString(indexFile)
        val bundleUrl = bundleFile.toUri().toString()
        val htmlWithBundle = html.replace("__BUNDLE_PLACEHOLDER__", bundleUrl)
        val patchedFile = mediaRoot.resolve("index_patched.html")
        val htmlWithBridgeConfig = htmlWithBundle.replaceFirst(
            "<head>",
            "<head>\n${httpBridge.endpointScript()}",
        )
        Files.writeString(patchedFile, htmlWithBridgeConfig)
        browser.loadURL(patchedFile.toUri().toString())
    }

    private fun enableJcefDevToolsContextMenu() {
        try {
            val key = "ide.browser.jcef.contextMenu.devTools.enabled"
            val registryValue = Registry.get(key)
            if (!registryValue.asBoolean()) {
                registryValue.setValue(true)
                logService.log("NcViewerPanel: enabled $key")
            }
        } catch (t: Throwable) {
            logger.warn("Failed to enable JCEF DevTools context menu", t)
            logService.log("NcViewerPanel: failed to enable JCEF DevTools context menu: ${'$'}{t.message}")
        }
    }

    private fun ensureDevToolsContextMenuSystemProperty() {
        val keys = listOf(
            "ide.browser.jcef.contextMenu.enabled",
            "ide.browser.jcef.contextMenu.devTools.enabled",
        )
        keys.forEach { key ->
            if (System.getProperty(key).isNullOrBlank()) {
                System.setProperty(key, "true")
            }
        }
    }

    private fun handleIncomingMessage(payload: String) {
        val message = gson.fromJson(payload, IncomingMessage::class.java)
        logger.debug("Incoming message: $message")
        val editor = currentEditor
        if (editor == null || editor.isDisposed) {
            logger.debug("Ignoring message ${message.type} because editor is null/disposed")
            return
        }

        when (message.type) {
            "webviewReady" -> {
                isWebviewReady = true
                logger.info("Webview signaled ready via HTTP bridge")
                logService.log("NcViewerPanel: webviewReady (http)")
                currentEditor?.let { sendLoadGCode(it) }
            }

            "highlightLine" -> message.lineNumber?.let { moveCaretToLine(it) }
            "bridgeDebug" -> message.debugMessage?.let { logService.log("Webview debug: $it") }
            else -> logger.warn("Unknown message type: ${message.type}")
        }
    }

    private fun moveCaretToLine(line: Int) {
        val editor = currentEditor ?: return
        if (editor.isDisposed || project.isDisposed) {
            logger.debug("Ignoring moveCaretToLine for disposed editor")
            return
        }
        val document = editor.document
        if (document.lineCount == 0) return
        val clampedLine = line.coerceIn(0, document.lineCount - 1)
        val lineStartOffset = document.getLineStartOffset(clampedLine)
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            editor.caretModel.moveToOffset(lineStartOffset)
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        })
    }

    private fun sendMessage(message: NcViewerMessage) {
        val json = gson.toJson(message)
        logger.debug("Publishing HTTP bridge message: ${message.type}")
        if (NcViewerLogConfig.verbose) {
            logService.log("Http bridge publish type=${message.type} len=${json.length}")
        }
        httpBridge.publish(json)
    }

    private fun detachListeners() {
        logger.debug("Detaching editor listeners")
        caretListener?.let { listener ->
            currentEditor?.caretModel?.removeCaretListener(listener)
        }
        caretListener = null

        documentListener?.let { listener ->
            currentEditor?.document?.removeDocumentListener(listener)
        }
        documentListener = null
        currentEditor = null
        isWebviewReady = false
    }

    override fun dispose() {
        detachListeners()
        httpBridge.stop()
        browser.dispose()
        logger.info("NcViewerPanel disposed")
    }

    private data class NcViewerMessage(
        val type: String,
        val ncText: String? = null,
        val lineNumber: Int? = null,
        val settings: SettingsPayload? = null,
    )

    private data class SettingsPayload(
        @SerializedName("excludeCodes") val excludeCodes: List<String>,
    )

    private data class IncomingMessage(
        val type: String,
        val lineNumber: Int? = null,
        val debugMessage: String? = null,
    )

}
