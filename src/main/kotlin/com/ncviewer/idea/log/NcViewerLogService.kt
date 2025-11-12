package com.ncviewer.idea.log

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBScrollPane
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.JComponent
import javax.swing.SwingUtilities

@Service(Service.Level.PROJECT)
class NcViewerLogService(private val project: Project) {

    private val textArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val panel = JBScrollPane(textArea)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun getComponent(): JComponent = panel

    fun log(message: String) {
        val timestamp = LocalTime.now().format(timeFormatter)
        val text = "[$timestamp] $message\n"
        if (SwingUtilities.isEventDispatchThread()) {
            appendText(text)
        } else {
            ApplicationManager.getApplication().invokeLater { appendText(text) }
        }
    }

    private fun appendText(text: String) {
        textArea.append(text)
        textArea.caretPosition = textArea.document.length
    }

    companion object {
        fun getInstance(project: Project): NcViewerLogService = project.service()
    }
}
