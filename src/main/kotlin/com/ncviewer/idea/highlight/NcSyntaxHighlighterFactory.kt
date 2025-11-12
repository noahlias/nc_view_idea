package com.ncviewer.idea.highlight

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.ncviewer.idea.log.NcViewerLogConfig

class NcSyntaxHighlighterFactory : SingleLazyInstanceSyntaxHighlighterFactory() {
    private val logger = logger<NcSyntaxHighlighterFactory>()

    override fun createHighlighter(): SyntaxHighlighter = NcSyntaxHighlighter()
        .also {
            if (NcViewerLogConfig.lexerDebug) {
                logger.info("NcSyntaxHighlighterFactory#createHighlighter invoked")
            }
        }
}
