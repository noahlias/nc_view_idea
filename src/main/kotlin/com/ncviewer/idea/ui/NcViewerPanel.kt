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
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.JBUI
import com.ncviewer.idea.log.NcViewerLogService
import com.ncviewer.idea.settings.NcViewerSettings
import com.ncviewer.idea.util.MediaExtractor
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.nio.file.Files
import javax.swing.JComponent
import javax.swing.JPanel

class NcViewerPanel(private val project: Project) : Disposable {

    private val logger = logger<NcViewerPanel>()
    private val logService = NcViewerLogService.getInstance(project)
    private val gson = Gson()
    private val browser: JBCefBrowser = JBCefBrowserBuilder().setUrl("about:blank").build()
    private val client: JBCefClient = browser.jbCefClient
    private val jsBridge = JBCefJSQuery.create(browser)

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
    private val pendingMessages = mutableListOf<String>()

    init {
        logger.info("Initializing NcViewerPanel")
        logService.log("NcViewerPanel: initializing")
        jsBridge.addHandler { payload ->
            logger.debug("Received JS bridge payload: ${payload.take(200)}")
            logService.log("Bridge message from webview: ${payload.take(200)}")
            handleIncomingMessage(payload)
            null
        }

        client.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (browser?.url?.startsWith("file:") == true && frame != null) {
                        val url = browser?.url
                        logger.info("Page load finished for $url – injecting bridge")
                        logService.log("NcViewerPanel: page load finished url=$url")
                        injectBridge(frame)
                    }
                }
            },
            browser.cefBrowser,
        )

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
        logger.info("Loading viewer assets from $indexFile")
        logService.log("NcViewerPanel: loading assets from $indexFile")
        val html = Files.readString(indexFile)
        val patchedHtml = BRIDGE_SNIPPET + "\n" + html
        browser.loadHTML(patchedHtml, indexFile.parent.toUri().toString())
    }

    private fun injectBridge(frame: CefFrame) {
        val script = """
            if (window.ideaBridge && window.ideaBridge.__intellijHooked) {
              return;
            }
            (function() {
              const listeners = new Set();
              function notify(payload) {
                listeners.forEach(function(listener) {
                  try {
                    listener(payload);
                  } catch (error) {
                    console.error('ideaBridge listener error', error);
                  }
                });
                window.dispatchEvent(new MessageEvent('message', { data: payload }));
              }
              window.ideaBridge = {
                __intellijHooked: true,
                postMessage: function(message) {
                  const payload = typeof message === 'string' ? message : JSON.stringify(message);
                  ${jsBridge.inject("payload")}
                },
                addMessageListener: function(listener) {
                  if (typeof listener !== 'function') {
                    return function() {};
                  }
                  listeners.add(listener);
                  return function() { listeners.delete(listener); };
                },
                _emit: function(message) {
                  notify(message);
                }
              };
            })();
        """.trimIndent()
        frame.executeJavaScript(script, frame.url, 0)
    }

    private fun handleIncomingMessage(payload: String) {
        val message = gson.fromJson(payload, IncomingMessage::class.java)
        logger.debug("Incoming message: $message")
        when (message.type) {
            "webviewReady" -> {
                isWebviewReady = true
                val count = pendingMessages.size
                logger.info("Webview signaled ready; flushing $count pending messages")
                logService.log("NcViewerPanel: webviewReady pending=$count")
                flushPendingMessages()
                currentEditor?.let { sendLoadGCode(it) }
            }

            "highlightLine" -> message.lineNumber?.let { moveCaretToLine(it) }
            "bridgeDebug" -> message.debugMessage?.let { logService.log("Webview debug: $it") }
            else -> logger.warn("Unknown message type: ${message.type}")
        }
    }

    private fun moveCaretToLine(line: Int) {
        val editor = currentEditor ?: return
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
        if (isWebviewReady) {
            logger.debug("Dispatching message immediately: ${message.type}")
            logService.log("Dispatching message ${message.type}")
            dispatchToWebview(json)
        } else {
            logger.debug("Queueing message while webview not ready: ${message.type}")
            logService.log("Queue message ${message.type}")
            pendingMessages += json
        }
    }

    private fun flushPendingMessages() {
        if (pendingMessages.isEmpty()) return
        logger.debug("Flushing ${pendingMessages.size} pending messages")
        pendingMessages.forEach { dispatchToWebview(it) }
        pendingMessages.clear()
    }

    private fun dispatchToWebview(jsonPayload: String) {
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            logger.trace("Executing script payload (len=${jsonPayload.length})")
            logService.log("Executing JS payload len=${jsonPayload.length}")
            browser.cefBrowser.mainFrame?.executeJavaScript(
                "window.ideaBridge && window.ideaBridge._emit($jsonPayload);",
                browser.cefBrowser.url,
                0,
            )
        })
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
    }

    override fun dispose() {
        detachListeners()
        jsBridge.dispose()
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

    companion object {
        private const val BRIDGE_SNIPPET = ""
    }
}
