package com.mclaughlinconnor.ij_inspector.application

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.psi.xml.XmlTokenType

private fun XmlTag.attributesAsString() =
    if (attributes.isNotEmpty()) attributes.joinToString(separator = " ", prefix = " ") { it.text }
    else ""

class HtmlStripper : XmlRecursiveElementVisitor() {
    private enum class ListType { Ordered, Unordered; }
    data class MarkdownSpan(val prefix: String, val suffix: String) {
        companion object {
            val Empty = MarkdownSpan("", "")

            @Suppress("unused")
            fun wrap(text: String) = MarkdownSpan(text, text)
            fun prefix(text: String) = MarkdownSpan(text, "")

            fun preserveTag(tag: XmlTag) = MarkdownSpan("<${tag.name}${tag.attributesAsString()}>", "</${tag.name}>")
        }
    }

    val result: String
        get() = markdownBuilder.toString()

    private val markdownBuilder = StringBuilder("")
    private var afterLineBreak = false
    private var whitespaceIsPartOfText = true
    private var currentListType = ListType.Unordered

    fun reset() {
        markdownBuilder.clear()
    }

    override fun visitWhiteSpace(space: PsiWhiteSpace) {
        super.visitWhiteSpace(space)

        if (whitespaceIsPartOfText) {
            val lines = space.text.lines()
            if (lines.size == 1) {
                markdownBuilder.append(space.text)
            } else {
                lines.drop(1).dropLast(1).forEach { _ ->
                    markdownBuilder.append("\n")
                }
                markdownBuilder.append("\n")
                afterLineBreak = true
            }
        }
    }

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)

        when (element.node.elementType) {
            XmlTokenType.XML_DATA_CHARACTERS -> markdownBuilder.append(element.text)
            XmlTokenType.XML_CHAR_ENTITY_REF -> markdownBuilder.append(StringUtil.unescapeXmlEntities(element.text))
        }
    }

    override fun visitXmlTag(tag: XmlTag) {
        withWhitespaceAsPartOfText(false) {
            val oldListType = currentListType
            val atLineStart = afterLineBreak

            val (openingMarkdown, closingMarkdown) = getMarkdownForTag(tag, atLineStart)
            markdownBuilder.append(openingMarkdown)

            super.visitXmlTag(tag)

            markdownBuilder.append(closingMarkdown)
            currentListType = oldListType
        }
    }

    override fun visitXmlText(text: XmlText) {
        withWhitespaceAsPartOfText(true) {
            super.visitXmlText(text)
        }
    }

    private inline fun withWhitespaceAsPartOfText(newValue: Boolean, block: () -> Unit) {
        val oldValue = whitespaceIsPartOfText
        whitespaceIsPartOfText = newValue
        try {
            block()
        } finally {
            whitespaceIsPartOfText = oldValue
        }
    }

    private fun getMarkdownForTag(tag: XmlTag, atLineStart: Boolean): MarkdownSpan = when (tag.name.lowercase()) {
        "a" -> {
            val href = tag.getAttributeValue("href")
            if (href != null) {
                if (href.startsWith("psi_element") || href.startsWith("https://www.jetbrains")) {
                    MarkdownSpan.Empty
                } else {
                    MarkdownSpan("[", "](${tag.getAttributeValue("href") ?: ""})")
                }
            } else {
                MarkdownSpan.Empty
            }
        }

        "ul" -> {
            currentListType = ListType.Unordered; MarkdownSpan.Empty
        }

        "ol" -> {
            currentListType = ListType.Ordered; MarkdownSpan.Empty
        }

        "li" -> if (currentListType == ListType.Unordered) MarkdownSpan.prefix(" * ") else MarkdownSpan.prefix(" 1. ")

        "p" -> if (atLineStart) MarkdownSpan.prefix("\n") else MarkdownSpan.prefix("\n\n")

        "b", "strong" -> MarkdownSpan.Empty // "b", "strong" -> MarkdownSpan.wrap("**")

        "i", "em" -> MarkdownSpan.Empty // "i", "em" -> MarkdownSpan.wrap("*")

        "s", "del" -> MarkdownSpan.Empty // "s", "del" -> MarkdownSpan.wrap("~~")

        "span", "font", "pre", "icon", "sup", "code", "br", "table" -> MarkdownSpan.Empty

        "div" -> MarkdownSpan.prefix("\n")

        else -> MarkdownSpan.preserveTag(tag)
    }
}
