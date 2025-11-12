package com.ncviewer.idea.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile
import com.ncviewer.idea.log.NcViewerLogService
import com.ncviewer.idea.service.NcViewerProjectService

class OpenNcViewerAction : AnAction(), DumbAware {

    private val logger = logger<OpenNcViewerAction>()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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
        val fileType = editor?.virtualFile?.fileType?.name.orEmpty()
        val filePath = editor?.virtualFile?.path.orEmpty()
        logger.info("Opening NC Viewer for fileType=$fileType path=$filePath")
        NcViewerLogService.getInstance(project).log("OpenNcViewerAction: fileType=$fileType path=$filePath")
        NcViewerProjectService.getInstance(project).openViewer(editor)
    }

    private fun isSupportedFile(file: VirtualFile): Boolean {
        return when (file.extension?.lowercase()) {
            "nc", "gcode", "cnc" -> true
            else -> false
        }
    }
}
