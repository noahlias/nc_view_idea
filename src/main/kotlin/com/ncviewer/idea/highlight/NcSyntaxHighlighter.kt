package com.ncviewer.idea.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import java.awt.Font

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
            TextAttributes(Color(0x6B7B80), null, null, null, Font.ITALIC),
        )
        private val COMMAND = TextAttributesKey.createTextAttributesKey(
            "NC_COMMAND",
            TextAttributes(Color(0xE38F24), null, null, null, Font.BOLD),
        )
        private val COORDINATE = TextAttributesKey.createTextAttributesKey(
            "NC_COORDINATE",
            TextAttributes(Color(0x2F9C95), null, null, null, Font.PLAIN),
        )
        private val NUMBER = TextAttributesKey.createTextAttributesKey(
            "NC_NUMBER",
            TextAttributes(Color(0xB5BDC1), null, null, null, Font.PLAIN),
        )
        private val WORD = TextAttributesKey.createTextAttributesKey(
            "NC_WORD",
            TextAttributes(Color(0x939EA4), null, null, null, Font.PLAIN),
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
