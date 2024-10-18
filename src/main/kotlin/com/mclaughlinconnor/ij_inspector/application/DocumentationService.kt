package com.mclaughlinconnor.ij_inspector.application

import com.intellij.lang.LanguageDocumentation
import com.intellij.lang.documentation.impl.documentationTargets
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.AsyncDocumentation
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.startOffset
import kotlinx.coroutines.runBlocking

class DocumentationService(myProject: Project) {
    private val myDocumentationFormatter = DocumentationFormatter(myProject)
    private val injectedLanguageManager = InjectedLanguageManager.getInstance(myProject)

    fun fetchDocumentation(element: PsiElement, originalElement: PsiElement?): String {
        val provider = LanguageDocumentation.INSTANCE.forLanguage(element.language)
        if (provider != null) {
            val documentation: String? = provider.generateDoc(element, originalElement)
            val html = documentation ?: return ""

            return formatDocumentation(html)
        }

        return ""
    }

    @Suppress("UnstableApiUsage")
    fun fetchSourceDocumentationForElement(offset: Int, file: PsiFile): String {
        var psiFile = file
        var psiOffset = offset

        val injectedElement = injectedLanguageManager.findInjectedElementAt(file, offset)
        if (injectedElement != null) {
            psiFile = injectedElement.containingFile
            psiOffset = injectedElement.startOffset
        }

        val targets = documentationTargets(psiFile, psiOffset)

        if (targets.isEmpty()) {
            return ""
        }

        var documentationResult: DocumentationData?
        runBlocking {
            documentationResult = fetchDocumentationData(targets.first())
        }

        val html = documentationResult?.html ?: return ""
        return formatDocumentation(html)
    }

    private fun formatDocumentation(html: String): String {
        val md = myDocumentationFormatter.format(html)
        val trimmed = md.trim().replace(Regex("^&nbsp;"), "").trim()

        return trimmed
    }

    /** Stolen from com.intellij.platform.backend.documentation.impl.ImplKt.computeDocumentation */
    @Suppress("UnstableApiUsage")
    private suspend fun fetchDocumentationData(target: DocumentationTarget): DocumentationData? {
        @Suppress("OverrideOnly") // honestly no idea...
        val documentationResult: DocumentationResult? = readAction { target.computeDocumentation() }
        return when (documentationResult) {
            is DocumentationData -> documentationResult
            is AsyncDocumentation -> documentationResult.supplier.invoke() as DocumentationData?
            null -> null
        }
    }
}