package com.ncviewer.idea.ui

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
        jsBridge.addHandler { payload ->
            handleIncomingMessage(payload)
            null
        }

        client.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (browser?.url?.startsWith("file:") == true && frame != null) {
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
            sendLoadGCode(editor)
            return
        }

        detachListeners()
        currentEditor = editor

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
        val html = Files.readString(indexFile)
        val patchedHtml = BRIDGE_SNIPPET + "\n" + html
        browser.loadHTML(patchedHtml, indexFile.parent.toUri().toString())
    }

    private fun injectBridge(frame: CefFrame) {
        val script = """
            window.__ideaSetBridge && window.__ideaSetBridge(function (message) {
              ${jsBridge.inject("JSON.stringify(message)")}
            });
            window.__ideaReceiveMessage = function (message) {
              window.dispatchEvent(new MessageEvent("message", { data: message }));
            };
            window.__ideaFlushBridge && window.__ideaFlushBridge();
        """.trimIndent()
        frame.executeJavaScript(script, frame.url, 0)
    }

    private fun handleIncomingMessage(payload: String) {
        val message = gson.fromJson(payload, IncomingMessage::class.java)
        when (message.type) {
            "webviewReady" -> {
                isWebviewReady = true
                flushPendingMessages()
                currentEditor?.let { sendLoadGCode(it) }
            }

            "highlightLine" -> message.lineNumber?.let { moveCaretToLine(it) }
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
            dispatchToWebview(json)
        } else {
            pendingMessages += json
        }
    }

    private fun flushPendingMessages() {
        if (pendingMessages.isEmpty()) return
        pendingMessages.forEach { dispatchToWebview(it) }
        pendingMessages.clear()
    }

    private fun dispatchToWebview(jsonPayload: String) {
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            browser.cefBrowser.mainFrame?.executeJavaScript(
                "window.__ideaReceiveMessage($jsonPayload);",
                browser.cefBrowser.url,
                0,
            )
        })
    }

    private fun detachListeners() {
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
    )

    companion object {
        private val BRIDGE_SNIPPET = """
            <script>
            if (typeof acquireVsCodeApi !== "function") {
              window.__ideaBridgeQueue = [];
              window.__ideaBridgePost = null;
              window.__ideaSetBridge = function (postFn) {
                window.__ideaBridgePost = postFn;
                window.__ideaFlushBridge();
              };
              window.__ideaFlushBridge = function () {
                if (!window.__ideaBridgePost) {
                  return;
                }
                while (window.__ideaBridgeQueue.length) {
                  const message = window.__ideaBridgeQueue.shift();
                  window.__ideaBridgePost(message);
                }
              };
              window.acquireVsCodeApi = function () {
                return {
                  postMessage: function (message) {
                    if (window.__ideaBridgePost) {
                      window.__ideaBridgePost(message);
                    } else {
                      window.__ideaBridgeQueue.push(message);
                    }
                  }
                };
              };
            }
            </script>
        """.trimIndent()
    }
}
