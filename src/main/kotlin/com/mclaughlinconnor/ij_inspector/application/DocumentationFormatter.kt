package com.mclaughlinconnor.ij_inspector.application

import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTokenType

/**
 * Formats documentation provided in HTML as Markdown
 *
 * @see com.intellij.lang.documentation.DocumentationProvider.generateDoc
 */
class DocumentationFormatter(val myProject: Project) {
    private val formatter = Formatter()

    fun format(html: String): String {
        val htmlFile =
            PsiFileFactory.getInstance(myProject).createFileFromText("javadoc.html", HtmlFileType.INSTANCE, html)
        htmlFile.accept(formatter)
        val result = formatter.result
        formatter.reset()
        return result
    }


    inner class Formatter : XmlRecursiveElementVisitor() {
        private val htmlToMarkdownConverter = HtmlStripper()

        private val markdownBuilder = StringBuilder("")

        val result: String
            get() = markdownBuilder.toString()


        private fun convertToMarkDown(tag: XmlTag): String {
            return convertToMarkDown(tag.text)
        }

        private fun convertToMarkDown(html: String): String {
            val htmlFile = PsiFileFactory.getInstance(myProject).createFileFromText(
                "javadoc.html", HtmlFileType.INSTANCE, html
            )
            htmlFile.accept(htmlToMarkdownConverter)
            val result = htmlToMarkdownConverter.result
            htmlToMarkdownConverter.reset()
            return result
        }

        private fun handleBottom(tag: XmlTag) {
            tag.acceptChildren(object : XmlRecursiveElementVisitor() {
                override fun visitXmlTag(tag: XmlTag) {
                    when (tag.name) {
                        "icon" -> {
                            val src = tag.getAttributeValue("src")
                            if (src != null) {
                                val name = src.substring("AllIcons.Nodes.".length)
                                markdownBuilder.append("\\[${name}\\] ")
                            }

                            tag.acceptChildren(this)
                        }

                        "code" -> {
                            markdownBuilder.append("`")
                            markdownBuilder.append(convertToMarkDown(tag))
                            markdownBuilder.append("`")
                        }

                        else -> super.visitXmlTag(tag)
                    }
                }
            })

            markdownBuilder.append("\n---\n")
        }

        private fun handleContent(tag: XmlTag) {
            markdownBuilder.append("\n---\n")
            markdownBuilder.append(convertToMarkDown(tag.value.text))
            markdownBuilder.append("\n---\n")
        }

        private fun handleDefinition(tag: XmlTag) {
            markdownBuilder.append("\n```java\n")
            tag.acceptChildren(object : XmlRecursiveElementVisitor() {
                override fun visitXmlTag(tag: XmlTag) {
                    markdownBuilder.append(convertToMarkDown(tag))
                }
            })
            markdownBuilder.append("\n```\n")
        }

        private fun handleDiv(tag: XmlTag) {
            val clazz = tag.getAttributeValue("class")
            when (clazz) {
                "bottom" -> handleBottom(tag)
                "content" -> handleContent(tag)
                "definition" -> handleDefinition(tag)
            }
        }

        private fun handleSection(tag: XmlTag) {
            markdownBuilder.append("\n---\n")
            tag.acceptChildren(object : XmlRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    when (element.node.elementType) {
                        XmlTokenType.XML_DATA_CHARACTERS -> markdownBuilder.append(element.text)
                        XmlTokenType.XML_CHAR_ENTITY_REF -> markdownBuilder.append(
                            StringUtil.unescapeXmlEntities(
                                element.text
                            )
                        )

                        else -> if (element is PsiWhiteSpace && element.parent.elementType == XmlElementType.XML_TEXT && element.parent.firstChild != element) markdownBuilder.append(
                            element.text
                        )
                    }
                }

                override fun visitXmlTag(tag: XmlTag) {
                    if (tag.name == "p" || tag.name == "a" || tag.name == "code") {
                        tag.acceptChildren(this)
                        return
                    }

                    if (tag.name == "td") {
                        if (tag.getAttributeValue("class") != "section") markdownBuilder.append(" ")
                        tag.acceptChildren(this)
                        if (tag.getAttributeValue("class") != "section") markdownBuilder.append("\n")
                        return
                    }

                    super.visitXmlTag(tag)
                }
            })
        }

        fun reset() {
            markdownBuilder.clear()
        }

        override fun visitElement(element: PsiElement) {
            super.visitElement(element)
            when (element.node.elementType) {
                XmlTokenType.XML_DATA_CHARACTERS -> markdownBuilder.append(element.text)
                XmlTokenType.XML_CHAR_ENTITY_REF -> markdownBuilder.append(StringUtil.unescapeXmlEntities(element.text))
            }
        }

        override fun visitXmlTag(tag: XmlTag) {
            if (tag.name == "div") {
                handleDiv(tag)
                return
            }

            if (tag.name == "table" && tag.getAttributeValue("class") == "sections") {
                handleSection(tag)
                return
            }

            super.visitXmlTag(tag)
        }
    }
}