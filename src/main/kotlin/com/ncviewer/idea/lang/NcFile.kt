package com.ncviewer.idea.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class NcFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NcLanguage) {
    override fun getFileType() = NcFileType

    override fun toString(): String = "G-code File"
}
