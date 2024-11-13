package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.refactoring.rename.RenameProcessor
import com.mclaughlinconnor.ijInspector.lsp.RenameParams
import com.mclaughlinconnor.ijInspector.lsp.Response
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.MessageFactory
import com.mclaughlinconnor.ijInspector.utils.Utils

class RenameService(private val myProject: Project, private val connection: Connection) {
    private val messageFactory: MessageFactory = MessageFactory()
    private var myApplication: Application = ApplicationManager.getApplication()

    fun handleRename(requestId: Int, params: RenameParams) {
        val filename = params.textDocument.uri.substring("file://".length)
        val document = Utils.createDocument(myProject, filename) ?: return
        val cursorOffset = document.getLineStartOffset(params.position.line) + params.position.character

        val newName = params.newName

        myApplication.invokeLater {
            val editor = EditorFactory.getInstance().createEditor(document, myProject) ?: return@invokeLater
            val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater

            editor.caretModel.currentCaret.moveToOffset(cursorOffset)

            myApplication.executeOnPooledThread {
                val element = extractTargetElement(psiFile, cursorOffset) ?: return@executeOnPooledThread
                myApplication.invokeLater {
                    // Searching in text and in comments forces a dialogue I can't handle headlessly
                    val processor = RenameProcessor(myProject, element, newName, false, false)
                    val workspaceEdit = CommandService.trackChanges(myProject) {
                        Utils.runCatching(requestId, connection) {
                            processor.doRun()
                        }
                    }

                    val request = Response(requestId, workspaceEdit)
                    val message = messageFactory.newMessage(request)
                    connection.write(message)
                }
            }
        }
    }

    private fun extractTargetElement(psiFile: PsiFile, cursorOffset: Int): PsiElement? {
        var element: PsiElement? = psiFile.findElementAt(cursorOffset) ?: return null

        while (element != null) {
            when (element) {
                is PsiReference -> {
                    element = element.resolve()
                    break
                }

                is PsiNamedElement -> {
                    break
                }

                else -> myApplication.runReadAction { element = element!!.parent }
            }
        }

        // var targetElement: PsiElement? = null
        // myApplication.runReadAction {
        //     targetElement = TargetElementUtil.getNamedElement(element)
        // }

        return element
    }
}