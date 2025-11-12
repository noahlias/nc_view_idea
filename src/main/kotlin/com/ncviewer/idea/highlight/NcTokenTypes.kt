package com.ncviewer.idea.highlight

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.ncviewer.idea.lang.NcLanguage

object NcTokenTypes {
    val COMMENT: IElementType = NcElementType("NC_COMMENT")
    val COMMAND: IElementType = NcElementType("NC_COMMAND")
    val COORDINATE: IElementType = NcElementType("NC_COORDINATE")
    val NUMBER: IElementType = NcElementType("NC_NUMBER")
    val WORD: IElementType = NcElementType("NC_WORD")
    val BAD: IElementType = TokenType.BAD_CHARACTER
}

class NcElementType(debugName: String) : IElementType(debugName, NcLanguage)
