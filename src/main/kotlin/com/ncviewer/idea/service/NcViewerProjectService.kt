package com.ncviewer.idea.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.ncviewer.idea.ui.NcViewerPanel

@Service(Service.Level.PROJECT)
class NcViewerProjectService(private val project: Project) : DumbAware {

    private val panel = NcViewerPanel(project).also { Disposer.register(project, it) }

    fun openViewer(editor: Editor?) {
        if (editor == null) {
            Messages.showWarningDialog(
                project,
                "Focus a .nc/.gcode/.cnc editor and try again.",
                "NC Viewer",
            )
            return
        }

        panel.attachEditor(editor)
        ToolWindowManager.getInstance(project)
            .getToolWindow("NC Viewer")
            ?.show()
    }

    fun getPanelComponent() = panel.component

    companion object {
        fun getInstance(project: Project): NcViewerProjectService = project.service()
    }
}
