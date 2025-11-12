package com.ncviewer.idea.highlight

import com.intellij.lexer.LexerBase
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import kotlin.math.min
import com.ncviewer.idea.log.NcViewerLogConfig

class NcLexer : LexerBase() {
    private val logger = logger<NcLexer>()
    private var buffer: CharSequence = ""
    private var bufferEnd: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.bufferEnd = endOffset
        if (NcViewerLogConfig.lexerDebug) {
            logger.info("NcLexer.start range=[$startOffset,$endOffset) text='${buffer.subSequence(startOffset, endOffset)}'")
        }
        advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun advance() {
        if (tokenEnd >= bufferEnd) {
            tokenType = null
            if (NcViewerLogConfig.lexerDebug) {
                logger.info("NcLexer.advance reached EOF")
            }
            return
        }

        tokenStart = tokenEnd
        val c = buffer[tokenStart]

        when {
            c == '(' -> consumeComment()
            c.isWhitespace() -> consumeWhitespace()
            isCommandLetter(c) -> consumeCommand()
            isCoordinateLetter(c) -> consumeCoordinate()
            c.isDigit() || c == '-' || c == '+' -> consumeNumber()
            else -> consumeWord()
        }
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd

    private fun consumeComment() {
        var i = tokenStart + 1
        while (i < bufferEnd) {
            val ch = buffer[i]
            if (ch == ')' || ch == '\n' || ch == '\r') {
                if (ch == ')') {
                    i++
                }
                break
            }
            i++
        }
        tokenEnd = min(i, bufferEnd)
        tokenType = NcTokenTypes.COMMENT
        logToken()
    }

    private fun consumeWhitespace() {
        var i = tokenStart + 1
        while (i < bufferEnd && buffer[i].isWhitespace()) {
            i++
        }
        tokenEnd = i
        tokenType = TokenType.WHITE_SPACE
        logToken()
    }

    private fun consumeCommand() {
        var i = tokenStart + 1
        while (i < bufferEnd && buffer[i].isLetterOrDigit()) {
            i++
        }
        tokenEnd = i
        tokenType = NcTokenTypes.COMMAND
        logToken()
    }

    private fun consumeCoordinate() {
        var i = tokenStart + 1
        while (i < bufferEnd) {
            val ch = buffer[i]
            if (ch.isDigit() || ch == '.' || ch == '-' || ch == '+') {
                i++
            } else {
                break
            }
        }
        tokenEnd = i
        tokenType = NcTokenTypes.COORDINATE
        logToken()
    }

    private fun consumeNumber() {
        var i = tokenStart + 1
        while (i < bufferEnd) {
            val ch = buffer[i]
            if (ch.isDigit() || ch == '.') {
                i++
            } else {
                break
            }
        }
        tokenEnd = i
        tokenType = NcTokenTypes.NUMBER
        logToken()
    }

    private fun consumeWord() {
        var i = tokenStart + 1
        while (i < bufferEnd && !buffer[i].isWhitespace()) {
            i++
        }
        tokenEnd = i
        tokenType = NcTokenTypes.WORD
        logToken()
    }

    private fun Char.isWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'

    private fun isCommandLetter(ch: Char): Boolean {
        return when (ch.uppercaseChar()) {
            'G', 'M', 'T' -> true
            else -> false
        }
    }

    private fun isCoordinateLetter(ch: Char): Boolean {
        return when (ch.uppercaseChar()) {
            'X', 'Y', 'Z', 'I', 'J', 'K', 'A', 'B', 'C', 'F', 'S', 'P', 'R', 'E' -> true
            else -> false
        }
    }

    private fun logToken() {
        if (!NcViewerLogConfig.lexerDebug) return
        val text = buffer.subSequence(tokenStart, tokenEnd)
        logger.info("token type=$tokenType range=[$tokenStart,$tokenEnd) text='$text'")
    }
}
