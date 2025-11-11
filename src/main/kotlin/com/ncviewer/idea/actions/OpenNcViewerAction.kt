package com.ncviewer.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile
import com.ncviewer.idea.service.NcViewerProjectService

class OpenNcViewerAction : AnAction(), DumbAware {

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.let {
            val file = FileDocumentManager.getInstance().getFile(it.document)
            file?.let(::isSupportedFile) ?: false
        } ?: false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        NcViewerProjectService.getInstance(project).openViewer(editor)
    }

    private fun isSupportedFile(file: VirtualFile): Boolean {
        return when (file.extension?.lowercase()) {
            "nc", "gcode", "cnc" -> true
            else -> false
        }
    }
}
