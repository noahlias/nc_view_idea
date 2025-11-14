package com.ncviewer.idea.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType

class NcSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = NcLexer()

    override fun getTokenHighlights(tokenType: com.intellij.psi.tree.IElementType?): Array<TextAttributesKey> = when (tokenType) {
        NcTokenTypes.COMMENT -> COMMENT_KEYS
        NcTokenTypes.COMMAND -> COMMAND_KEYS
        NcTokenTypes.COORDINATE -> COORDINATE_KEYS
        NcTokenTypes.NUMBER -> NUMBER_KEYS
        NcTokenTypes.WORD -> WORD_KEYS
        TokenType.WHITE_SPACE -> emptyArray()
        NcTokenTypes.BAD -> BAD_KEYS
        else -> emptyArray()
    }

    companion object {
        private val COMMENT = TextAttributesKey.createTextAttributesKey(
            "NC_COMMENT",
            DefaultLanguageHighlighterColors.LINE_COMMENT,
        )
        private val COMMAND = TextAttributesKey.createTextAttributesKey(
            "NC_COMMAND",
            DefaultLanguageHighlighterColors.KEYWORD,
        )
        private val COORDINATE = TextAttributesKey.createTextAttributesKey(
            "NC_COORDINATE",
            DefaultLanguageHighlighterColors.PARAMETER,
        )
        private val NUMBER = TextAttributesKey.createTextAttributesKey(
            "NC_NUMBER",
            DefaultLanguageHighlighterColors.NUMBER,
        )
        private val WORD = TextAttributesKey.createTextAttributesKey(
            "NC_WORD",
            DefaultLanguageHighlighterColors.IDENTIFIER,
        )
        private val BAD = TextAttributesKey.createTextAttributesKey(
            "NC_BAD_CHARACTER",
            HighlighterColors.BAD_CHARACTER,
        )

        val COMMENT_KEYS = arrayOf(COMMENT)
        val COMMAND_KEYS = arrayOf(COMMAND)
        val COORDINATE_KEYS = arrayOf(COORDINATE)
        val NUMBER_KEYS = arrayOf(NUMBER)
        val WORD_KEYS = arrayOf(WORD)
        val BAD_KEYS = arrayOf(BAD)
    }
}
