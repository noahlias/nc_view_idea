package com.ncviewer.idea.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class NcColorSettingsPage : ColorSettingsPage {

    private val descriptors = arrayOf(
        AttributesDescriptor("Commands (G/M)", NcSyntaxHighlighter.COMMAND_KEYS.first()),
        AttributesDescriptor("Coordinates (X/Y/Z...)", NcSyntaxHighlighter.COORDINATE_KEYS.first()),
        AttributesDescriptor("Numbers", NcSyntaxHighlighter.NUMBER_KEYS.first()),
        AttributesDescriptor("Comments", NcSyntaxHighlighter.COMMENT_KEYS.first()),
        AttributesDescriptor("Other words", NcSyntaxHighlighter.WORD_KEYS.first()),
        AttributesDescriptor("Bad characters", NcSyntaxHighlighter.BAD_KEYS.first()),
    )

    override fun getDisplayName(): String = "G-code"

    override fun getIcon(): Icon? = null

    override fun getHighlighter() = NcSyntaxHighlighter()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = descriptors

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDemoText(): String = """
        ( Example pocket )
        G21 G90 G1 F100
        G0 X0 Y0 Z5
        G1 Z-1.0 F50
        G1 X10 Y0
        G1 X10 Y10
        G1 X0 Y10
        G1 X0 Y0
        M30
    """.trimIndent()
}
