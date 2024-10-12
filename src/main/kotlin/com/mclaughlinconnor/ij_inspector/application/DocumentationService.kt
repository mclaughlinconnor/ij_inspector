// *all* of this is internal, experimental, or deprecated :(
@file:Suppress("removal", "DEPRECATION")

package com.mclaughlinconnor.ij_inspector.application

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.LanguageDocumentation
import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.platform.backend.documentation.AsyncDocumentation
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking

class DocumentationService(myProject: Project) {
    private val myDocumentationFormatter = DocumentationFormatter(myProject)

    // See com.intellij.lang.documentation.impl.TargetsKt.documentationTargets
    @Suppress("removal")
    private val documentationManager = DocumentationManager.getInstance(myProject)

    fun fetchDocumentation(element: PsiElement, originalElement: PsiElement?): String {
        val provider = LanguageDocumentation.INSTANCE.forLanguage(element.language)
        if (provider != null) {
            val documentation: String? = provider.generateDoc(element, originalElement)
            val html = documentation ?: return ""

            return formatDocumentation(html)
        }

        return ""
    }

    @Suppress("UnstableApiUsage", "removal")
    fun fetchSourceDocumentationForElement(editor: Editor, offset: Int, file: PsiFile): String {
        val (targetElement, sourceElement) = documentationManager.findTargetElementAndContext(editor, offset, file)
            ?: return ""
        val targets = psiDocumentationTargets(targetElement, sourceElement)

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