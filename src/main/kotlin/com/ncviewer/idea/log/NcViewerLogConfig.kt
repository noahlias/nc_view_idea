package com.ncviewer.idea.log

object NcViewerLogConfig {
    val verbose: Boolean by lazy {
        val explicit = System.getProperty("ncviewer.verboseLog")?.trim()?.lowercase()
        explicit?.let { it == "true" || it == "1" } ?: java.lang.Boolean.getBoolean("idea.is.internal")
    }

    val lexerDebug: Boolean by lazy {
        val explicit = System.getProperty("ncviewer.lexerDebug")?.trim()?.lowercase()
        when {
            explicit == "true" || explicit == "1" -> true
            explicit == "false" || explicit == "0" -> false
            else -> verbose
        }
    }
}
