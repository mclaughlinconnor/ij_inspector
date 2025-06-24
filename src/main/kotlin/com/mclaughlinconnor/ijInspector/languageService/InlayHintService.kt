package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.codeInsight.codeVision.ui.renderers.InlineCodeVisionInlayRenderer
import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.impl.ActionWithContent
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.util.containers.toArray
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.rpc.MessageFactory
import com.mclaughlinconnor.ijInspector.utils.RequestId
import com.mclaughlinconnor.ijInspector.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class InlayHintService(
    private val myProject: Project,
    private val myConnection: Connection,
) {
    private val messageFactory: MessageFactory = MessageFactory()
    private val myApplication: Application = ApplicationManager.getApplication()
    private val myListener = Listener()
    private val listenedEditors = mutableSetOf<Editor>()

    /*
     * This was an attempt to fix Neovim's buggy workspace/inlayHint/refresh handler. Even though it doesn't fix the
     * bug, it's still nice to debounce
     * See https://github.com/neovim/neovim/pull/32446
     */
    private val refreshInlayHintFlow = MutableSharedFlow<Unit>(0, 100, BufferOverflow.DROP_OLDEST)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            refreshInlayHints()
        }
    }

    fun registerEditorForListening(editor: Editor) {
        if (!listenedEditors.contains(editor)) {
            editor.inlayModel.addListener(myListener) {}
        }
    }

    fun getInlayHints(requestId: Int, params: InlayHintParams) {
        val filePath = params.textDocument.uri.substring("file://".length)
        val document = Utils.createDocument(myProject, filePath) ?: return

        myApplication.invokeLater {
            val editor = FileEditorManager.getInstance(myProject).selectedTextEditor ?: return@invokeLater
            registerEditorForListening(editor)

            val scrollPane = (editor as EditorImpl).scrollPane
            scrollPane.viewport.setSize(2000, 2000)
            scrollPane.viewport.setBounds(0, 0, 2000, 2000)

            val startLine = params.range.start.line
            val endLine = params.range.end.line
            val middleLine = endLine - startLine

            editor.scrollingModel.scrollTo(LogicalPosition(middleLine, 0), ScrollType.CENTER)

            myApplication.executeOnPooledThread {
                val hints = editor.inlayModel.getInlineElementsInRange(0, document.textLength)
                for (hint in editor.inlayModel.getAfterLineEndElementsInRange(0, document.textLength)) {
                    hints.add(hint)
                }

                val inlayHints = mutableListOf<InlayHint>()
                for (hint in hints) {
                    val f = hint.renderer as InlineCodeVisionInlayRenderer

                    val renderer = hint.renderer as DeclarativeInlayRenderer

                    val toInlayData = renderer::class.functions.find { it.name == "toInlayData" } ?: continue
                    val positionProperty = renderer::class.memberProperties.find { it.name == "position" } ?: continue
                    toInlayData.isAccessible = true
                    positionProperty.isAccessible = true

                    val data = toInlayData.call(renderer) as InlayData

                    inlayHints.add(createInlayHint(data, document))
                }

                println(inlayHints.size)
                val response = Response(requestId, inlayHints)
                myConnection.write(messageFactory.newMessage(response))
            }
        }
    }

    fun instructRefreshInlayHints() {
        refreshInlayHintFlow.tryEmit(Unit)
    }

    @OptIn(FlowPreview::class)
    suspend fun refreshInlayHints() {
        refreshInlayHintFlow
            .debounce(250)
            .collect {
                val request = Request(RequestId.getNextRequestId(), null, "workspace/inlayHint/refresh")
                myConnection.write(messageFactory.newMessage(request))
            }
    }

    private fun createInlayHint(inlayData: InlayData, document: Document): InlayHint {
        val position: Position

        when (val pos = inlayData.position) {
            is InlineInlayPosition -> {
                val offset = pos.offset
                val line = document.getLineNumber(offset)
                val column = offset - document.getLineStartOffset(line)
                position = Position(line, column)
            }

            is EndOfLinePosition -> {
                val line = pos.line
                val endOffset = document.getLineEndOffset(line)
                val startOffset = document.getLineEndOffset(line)
                val column = endOffset - startOffset
                position = Position(line, column)
            }
        }

        val sb = mutableListOf<String>()
        for (i in 0..<inlayData.tree.size) {
            when (val data = inlayData.tree.getDataPayload(i.toByte())) {
                is String -> sb.add(data)
                is ActionWithContent -> {
                    when (val content = data.content) {
                        is String -> sb.add(content)
                    }
                }
            }
        }

        val label = sb.map { InlayHintLabelPart(it) }.toArray(arrayOf())

        return InlayHint(position, label)
    }

    inner class Listener : InlayModel.SimpleAdapter() {
        override fun onUpdated(inlay: Inlay<*>) {
            instructRefreshInlayHints()
        }
    }
}