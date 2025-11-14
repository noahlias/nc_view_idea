package com.ncviewer.idea.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.ncviewer.idea.highlight.NcLexer
import com.ncviewer.idea.highlight.NcTokenTypes

class NcParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = NcLexer()

    override fun createParser(project: Project?): PsiParser = NcPsiParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = WHITESPACE

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = NcFile(viewProvider)

    companion object {
        private val FILE = IFileElementType(NcLanguage)
        private val WHITESPACE = TokenSet.create(TokenType.WHITE_SPACE)
        private val COMMENTS = TokenSet.create(NcTokenTypes.COMMENT)
    }
}
