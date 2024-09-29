package org.jetbrains.plugins.template.application

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupArranger.DefaultArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.CliResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.Consumer

/**
 * Gets completions at the cursor using the project and filename provided as arguments.
 *
 * The application's command *must* end with `"inspect"` and be less than 20 characters, otherwise, it will not
 * launch headlessly.
 *
 * @see com.intellij.idea.AppMode.isHeadless(java.util.List<java.lang.String>)
 */
@Suppress("UnstableApiUsage")
class CompletionStarter : ApplicationStarter {
    override val isHeadless: Boolean
        get() = true

    override suspend fun processExternalCommandLine(args: List<String>, currentDirectory: String?): CliResult {
        return CliResult(0, "Completion")
    }

    override fun canProcessExternalCommandLine(): Boolean = true

    override fun main(args: List<String>) {
        val projectPath = args[1]
        val filePath = args[2]
        val cursorOffset = 11
        val completionType = CompletionType.BASIC

        val project = ProjectManager.getInstance().loadAndOpenProject(projectPath) ?: return
        DumbService.getInstance(project).runWhenSmart {
            val results: MutableList<CompletionResult> = ArrayList()
            val consumer: Consumer<CompletionResult> = Consumer { result -> results.add(result) }

            val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return@runWhenSmart

            val document = ReadAction.compute<Document?, RuntimeException> {
                FileDocumentManager.getInstance().getDocument(virtualFile, project)
            }
            if (document == null) return@runWhenSmart

            ApplicationManager.getApplication().invokeLater {
                val editor = EditorFactory.getInstance().createEditor(document, project) ?: return@invokeLater
                editor.project
                val caret = editor.caretModel.primaryCaret
                caret.moveToOffset(cursorOffset)

                val initContext = CompletionInitializationUtil.createCompletionInitializationContext(
                    project, editor, caret, 1, completionType
                )

                val lookup: LookupImpl = obtainLookup(editor, initContext.project)
                val handler = CodeCompletionHandlerBase.createHandler(completionType, true, false, true)

                val indicator = IndicatorFactory.buildIndicator(
                    editor,
                    initContext.caret,
                    initContext.invocationCount,
                    handler,
                    initContext.offsetMap,
                    initContext.hostOffsets,
                    false,
                    lookup
                )

                val applyPsiChanges = CompletionInitializationUtil.insertDummyIdentifier(initContext, indicator)
                val hostCopyOffsets = applyPsiChanges.get()

                val finalOffsets = CompletionInitializationUtil.toInjectedIfAny(initContext.file, hostCopyOffsets)
                val parameters =
                    CompletionInitializationUtil.createCompletionParameters(initContext, indicator, finalOffsets)
                parameters.setIsTestingMode(false)

                val completionService = CompletionService.getCompletionService()
                completionService.performCompletion(parameters, consumer)

                println(results)
            }
        }

    }

    private fun obtainLookup(editor: Editor, project: Project): LookupImpl {
        val existing = LookupManager.getActiveLookup(editor) as LookupImpl?
        if (existing != null && existing.isCompletion) {
            existing.markReused()
            existing.lookupFocusDegree = LookupFocusDegree.FOCUSED
            return existing
        }

        val lookup = LookupManager.getInstance(project).createLookup(
            editor, LookupElement.EMPTY_ARRAY, "", DefaultArranger()
        ) as LookupImpl
        if (editor.isOneLineMode) {
            lookup.setCancelOnClickOutside(true)
            lookup.setCancelOnOtherWindowOpen(true)
        }
        lookup.lookupFocusDegree = LookupFocusDegree.UNFOCUSED
        return lookup
    }
}