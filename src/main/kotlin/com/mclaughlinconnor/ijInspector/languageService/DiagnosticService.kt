package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.MainPassesRunner
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findDocument
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.messages.MessageBusConnection
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


class DiagnosticService(
    private val myProject: Project,
    private val myConnection: Connection,
) {
    private val application = ApplicationManager.getApplication()
    private val profile = InspectionProjectProfileManager.getInstance(myProject).currentProfile

    private lateinit var codeAnalyzer: DaemonCodeAnalyzerImpl
    private var connection: MessageBusConnection? = null

    init {
        application.runReadAction {
            codeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject) as DaemonCodeAnalyzerImpl
        }
        startListening()
    }

    fun triggerDiagnostics(files: List<PsiFile>, timeoutMillis: Long = 50) {
        println("Triggering diagnostics for files: ${files.map { it.virtualFile.path }}")
        ProgressManager.getInstance().run(object : Task.Backgroundable(myProject, "Running diagnostics...", false) {
            override fun run(indicator: ProgressIndicator) {
                // Ensure we're using the correct thread and progress indicator
                ApplicationManager.getApplication().runReadAction {
                    val mainPassesRunner = MainPassesRunner(project, "Running diagnostics...", null)

                    // Use a timeout mechanism within the same thread
                    val timeoutFuture = CompletableFuture.supplyAsync {
                        try {
                            ProgressManager.getInstance().executeProcessUnderProgress({
                                try {
                                    mainPassesRunner.runMainPasses(files.map { it.virtualFile })
                                } catch (_: CancellationException) {
                                    // Intentionally do nothing. Logged below
                                }
                            }, indicator)
                            true
                        } catch (e: Exception) {
                            println("Error running diagnostics: $e")
                            false
                        }
                    }

                    try {
                        val result = timeoutFuture.get(timeoutMillis, TimeUnit.MILLISECONDS)
                        if (!result) {
                            indicator.cancel()
                        }
                    } catch (e: TimeoutException) {
                        indicator.cancel()
                        timeoutFuture.cancel(true)
                        println("Diagnostics main passes timed out after $timeoutMillis milliseconds")
                    }
                }
            }
        })
    }

    private fun startListening() {
        connection = myProject.messageBus.connect()

        connection?.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
            override fun daemonStarting(fileEditors: MutableCollection<out FileEditor>) {
            }

            override fun daemonFinished(fileEditors: MutableCollection<out FileEditor>) {
                onDaemonCompleted(fileEditors)
            }

            override fun daemonCanceled(reason: String, fileEditors: MutableCollection<out FileEditor>) {
                onDaemonCompleted(fileEditors)
            }
        })
    }

    private fun onDaemonCompleted(fileEditors: MutableCollection<out FileEditor>) {
        for (fileEditor in fileEditors) {
            application.invokeLater {
                application.runReadAction {
                    val document = fileEditor.file.findDocument() ?: return@runReadAction
                    val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@runReadAction

                    val highlights = DaemonCodeAnalyzerImpl.getHighlights(
                        document,
                        HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING,
                        myProject
                    )
                    val diagnostics: MutableList<Diagnostic> = mutableListOf()
                    for (highlight in highlights) {
                        diagnostics.add(constructDiagnostic(profile, highlight, document))
                    }

                    publishDiagnostics(myConnection, psiFile, diagnostics)
                }
            }
        }
    }

    companion object {
        private val messageFactory = com.mclaughlinconnor.ijInspector.rpc.MessageFactory()

        fun constructDiagnostic(
            profile: InspectionProfileImpl,
            highlighter: HighlightInfo,
            document: Document,
        ): Diagnostic {
            var severity = DiagnosticSeverityEnum.Information

            val message: String = highlighter.description ?: "" // can somehow be null without types saying so
            val toolName: String? = highlighter.inspectionToolId
            if (toolName != null) {
                val displayKey = HighlightDisplayKey.findById(toolName)
                if (displayKey != null) {
                    severity = convertSeverity(profile.getErrorLevel(displayKey, null).severity)
                }
            } else {
                severity = convertSeverity(highlighter.severity)
            }

            val startOffset = highlighter.startOffset
            val endOffset = highlighter.endOffset

            val startLine = document.getLineNumber(startOffset)
            val endLine = document.getLineNumber(endOffset)

            val startLinePosition = startOffset - document.getLineStartOffset(startLine)
            val endLinePosition = endOffset - document.getLineStartOffset(endLine)

            val startPosition = Position(startLine, startLinePosition)
            val endPosition = Position(endLine, endLinePosition)

            return Diagnostic(
                Range(startPosition, endPosition),
                severity,
                code = toolName,
                codeDescription = null,
                message,
                tags = null,
                relatedInformation = null,
                data = null
            )
        }

        private fun convertSeverity(severity: HighlightSeverity): DiagnosticSeverity {
            @Suppress("DEPRECATION") return when (severity) {
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

        private fun publishDiagnostics(connection: Connection, file: PsiFile, diagnostics: List<Diagnostic>) {
            val publishDiagnosticsParams =
                PublishDiagnosticsParams("file://${file.virtualFile.path}", null, diagnostics)
            val notification = Notification("textDocument/publishDiagnostics", publishDiagnosticsParams)
            val message = messageFactory.newMessage(notification)

            connection.write(message)
        }
    }

}

