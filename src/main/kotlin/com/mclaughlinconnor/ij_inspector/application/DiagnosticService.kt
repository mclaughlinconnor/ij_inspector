package com.mclaughlinconnor.ij_inspector.application

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.GlobalInspectionContext
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.*
import com.intellij.lang.Language
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectTypeService
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.mclaughlinconnor.ij_inspector.application.lsp.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext


class DiagnosticService(private val myProject: Project) {
    private val application = ApplicationManager.getApplication()
    private val connection = Connection.getInstance()
    private val messageFactory = MessageFactory()

    private val profile = InspectionProjectProfileManager.getInstance(myProject).currentProfile
    private val profileWrapper = InspectionProfileWrapper(profile)
    private val context = InspectionManager.getInstance(myProject).createNewGlobalContext()

    private val scope = CoroutineScope(Dispatchers.Default)
    private val jobs = ConcurrentHashMap<Document, Job>()

    fun computeAndPublish(document: Document) {
        val file = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return
        if (jobs[document] != null) {
            jobs[document]?.cancel()
            jobs.remove(document)
        }

        val job = scope.launch {
            val diagnostics = fetchDiagnostics(file, document, coroutineContext)

            val publishDiagnosticsParams =
                PublishDiagnosticsParams("file://${file.virtualFile.path}", null, diagnostics)
            val notification = Notification("textDocument/publishDiagnostics", publishDiagnosticsParams)
            val message = messageFactory.newMessage(notification)

            connection.write(message)

            jobs.remove(document)
        }

        jobs[document] = job
    }

    private fun fetchDiagnostics(
        file: PsiFile, document: Document, coroutineContext: CoroutineContext
    ): List<Diagnostic> {
        val tools = getInspectionTools(profileWrapper, file)
        val resultDiagnostics: MutableList<Diagnostic> = ArrayList()

        resultDiagnostics.addAll(runInspections(tools, file, context, profile, document, coroutineContext))

        return resultDiagnostics
    }

    private val lock = Any()

    private fun runInspections(
        tools: List<LocalInspectionToolWrapper>,
        file: PsiFile,
        context: GlobalInspectionContext,
        profile: InspectionProfileImpl,
        document: Document,
        coroutineContext: CoroutineContext
    ): List<Diagnostic> {
        val resultDiagnostics = mutableListOf<Diagnostic>()
        coroutineContext.ensureActive()

        synchronized(lock) {
            for (tool in tools) {
                coroutineContext.ensureActive()

                var problems: List<ProblemDescriptor> = listOf()
                application.runReadAction {
                    coroutineContext.ensureActive()
                    problems = InspectionEngine.runInspectionOnFile(file, tool, context)
                }
                val diagnostics = constructDiagnostics(profile, document, tool, problems)
                resultDiagnostics.addAll(diagnostics)

                coroutineContext.ensureActive()
            }
        }

        return resultDiagnostics
    }

    private fun constructDiagnostics(
        profile: InspectionProfileImpl,
        document: Document,
        tool: LocalInspectionToolWrapper,
        problems: List<ProblemDescriptor>
    ): List<Diagnostic> {
        val displayKey = HighlightDisplayKey.find(tool.shortName)
        val errorLevel = profile.getErrorLevel(displayKey!!, null)
        val toolName = tool.shortName

        val diagnostics: MutableList<Diagnostic> = ArrayList(problems.size)

        for (problem in problems) {
            diagnostics.add(constructDiagnostic(document, errorLevel, toolName, problem))
        }

        return diagnostics
    }

    private fun constructDiagnostic(
        document: Document, severity: HighlightDisplayLevel, toolName: String, problem: ProblemDescriptor
    ): Diagnostic {
        var startElement: PsiElement? = null
        var endElement: PsiElement? = null

        application.runReadAction {
            startElement = problem.startElement
            endElement = problem.endElement
        }

        val startOffset = startElement!!.startOffset
        val endOffset = endElement!!.endOffset

        val startLine = document.getLineNumber(startOffset)
        val endLine = document.getLineNumber(endOffset)

        val startLinePosition = startOffset - document.getLineStartOffset(startLine)
        val endLinePosition = endOffset - document.getLineStartOffset(endLine)

        val startPosition = Position(startLine, startLinePosition)
        val endPosition = Position(endLine, endLinePosition)

        return Diagnostic(
            Range(startPosition, endPosition),
            severity = convertSeverity(severity.severity),
            code = toolName,
            codeDescription = null,
            message = problem.tooltipTemplate,
            tags = null,
            relatedInformation = null,
            data = null
        )
    }

    private fun convertSeverity(severity: HighlightSeverity): DiagnosticSeverity {
        @Suppress("DEPRECATION")
        return when (severity) {
            HighlightSeverity.INFORMATION -> DiagnosticSeverityEnum.Hint
            HighlightSeverity.TEXT_ATTRIBUTES -> DiagnosticSeverityEnum.Information
            HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING -> DiagnosticSeverityEnum.Warning
            HighlightSeverity.INFO -> DiagnosticSeverityEnum.Hint
            HighlightSeverity.WEAK_WARNING -> DiagnosticSeverityEnum.Information
            HighlightSeverity.WARNING -> DiagnosticSeverityEnum.Warning
            HighlightSeverity.ERROR -> DiagnosticSeverityEnum.Error
            else -> DiagnosticSeverityEnum.Error
        }
    }

    // Adapted from com.intellij.codeInsight.daemon.impl.LocalInspectionsPass.getInspectionTools
    private fun getInspectionTools(profile: InspectionProfileWrapper, file: PsiFile): List<LocalInspectionToolWrapper> {
        var toolWrappers: MutableList<InspectionToolWrapper<*, *>> = mutableListOf()
        application.runReadAction {
            toolWrappers = profile.inspectionProfile.getInspectionTools(file)
        }

        val enabled: MutableList<LocalInspectionToolWrapper> = java.util.ArrayList()
        val projectTypes = ProjectTypeService.getProjectTypeIds(myProject)

        for (toolWrapper in toolWrappers) {
            if (!toolWrapper.isApplicable(projectTypes)) continue

            val key = toolWrapper.displayKey
            if (!shouldIncludeTool(key, file)) {
                continue
            }

            var wrapper: LocalInspectionToolWrapper?
            if (toolWrapper is LocalInspectionToolWrapper) {
                wrapper = toolWrapper
            } else {
                wrapper = (toolWrapper as GlobalInspectionToolWrapper).sharedLocalInspectionToolWrapper
                if (wrapper == null) continue
            }
            val language = wrapper.language
            if (language != null && Language.findLanguageByID(language) == null) {
                continue  // filter out at least unknown languages
            }

            // inspections that do not match file language are excluded later in InspectionRunner.inspect
            if (wrapper.isApplicable(file.language) && wrapper.tool.isSuppressedFor(file)) continue

            enabled.add(wrapper)
        }

        return enabled
    }

    private fun shouldIncludeTool(key: HighlightDisplayKey?, file: PsiFile): Boolean {
        if (key == null) {
            return false
        }

        if (!profile.isToolEnabled(key, file)) {
            return false
        }

        return HighlightDisplayLevel.DO_NOT_SHOW != profileWrapper.getErrorLevel(key, file)
                && profile.getErrorLevel(key, file) != HighlightDisplayLevel.CONSIDERATION_ATTRIBUTES
    }

    companion object {
        private val instance = Connection()

        fun getInstance() = instance
    }
}