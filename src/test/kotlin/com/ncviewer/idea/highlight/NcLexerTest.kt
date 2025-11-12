package com.ncviewer.idea.highlight

import com.intellij.psi.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals

class NcLexerTest {
    @Test
    fun `tokenizes basic gcode sample`() {
        val source = """
            G21
            G90
            G0 X0 Y0
            G1 Z-1 F100
            G2 X0 Y0 I10 J0 F200
            G0 Z5
            G0 X0 Y0
        """.trimIndent()

        val lexer = NcLexer()
        lexer.start(source, 0, source.length, 0)

        val tokens = mutableListOf<String>()
        while (true) {
            val type = lexer.tokenType ?: break
            if (type != TokenType.WHITE_SPACE) {
                val text = lexer.bufferSequence.subSequence(lexer.tokenStart, lexer.tokenEnd)
                tokens += "${type}:${text}"
            }
            lexer.advance()
        }

        val expected = listOf(
            "NC_COMMAND:G21",
            "NC_COMMAND:G90",
            "NC_COMMAND:G0",
            "NC_COORDINATE:X0",
            "NC_COORDINATE:Y0",
            "NC_COMMAND:G1",
            "NC_COORDINATE:Z-1",
            "NC_COORDINATE:F100",
            "NC_COMMAND:G2",
            "NC_COORDINATE:X0",
            "NC_COORDINATE:Y0",
            "NC_COORDINATE:I10",
            "NC_COORDINATE:J0",
            "NC_COORDINATE:F200",
            "NC_COMMAND:G0",
            "NC_COORDINATE:Z5",
            "NC_COMMAND:G0",
            "NC_COORDINATE:X0",
            "NC_COORDINATE:Y0",
        )

        assertEquals(expected, tokens)
    }
}
