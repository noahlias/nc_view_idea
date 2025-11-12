package com.ncviewer.idea.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon
import javax.swing.ImageIcon

object NcFileType : LanguageFileType(NcLanguage) {
    override fun getName(): String = "G-code"

    override fun getDescription(): String = "G-code NC machining programs"

    override fun getDefaultExtension(): String = "nc"

    private val icon: Icon? = NcIcons.File

    override fun getIcon(): Icon? = icon
}
