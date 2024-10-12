package com.mclaughlinconnor.ij_inspector.application

import com.intellij.lang.LanguageDocumentation
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

class DocumentationService(myProject: Project) {
    private val myDocumentationFormatter = DocumentationFormatter(myProject)

    fun fetchDocumentation(element: PsiElement, originalElement: PsiElement?): String {
        val provider = LanguageDocumentation.INSTANCE.forLanguage(element.language)
        if (provider != null) {
            val documentation: String? = provider.generateDoc(element, originalElement)
            val html = documentation ?: return ""

            return formatDocumentation(html)
        }

        return ""
    }

    private fun formatDocumentation(html: String): String {
        val md = myDocumentationFormatter.format(html)
        val trimmed = md.trim().replace(Regex("^&nbsp;"), "").trim()

        return trimmed
    }
}