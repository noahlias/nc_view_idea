package com.ncviewer.idea.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object NcFileType : LanguageFileType(NcLanguage) {
    override fun getName(): String = "G-code"

    override fun getDescription(): String = "G-code NC machining programs"

    override fun getDefaultExtension(): String = "nc"

    override fun getIcon(): Icon? = null
}
