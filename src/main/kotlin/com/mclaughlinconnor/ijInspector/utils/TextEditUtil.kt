package com.mclaughlinconnor.ijInspector.utils

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.fragments.LineFragment
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.util.TextRange
import com.mclaughlinconnor.ijInspector.lsp.Position
import com.mclaughlinconnor.ijInspector.lsp.Range
import com.mclaughlinconnor.ijInspector.lsp.TextEdit


class TextEditUtil {
    companion object {
        fun computeTextEdits(
            textBefore: String,
            textAfter: String
        ): Pair<MutableList<TextEdit>, MutableList<LineFragment>> {
            val documentBefore = DocumentImpl(textBefore)
            val documentAfter = DocumentImpl(textAfter)

            if (textBefore == textAfter) {
                return Pair(ArrayList(), ArrayList())
            }

            val fragments: List<LineFragment>?
            try {
                val manager = ComparisonManager.getInstance()
                fragments =
                    manager.compareLines(textBefore, textAfter, ComparisonPolicy.DEFAULT, DumbProgressIndicator())
            } catch (e: DiffTooBigException) {
                e.printStackTrace()
                return Pair(ArrayList(), ArrayList())
            }

            val textEdits: MutableList<TextEdit> = ArrayList()
            for (fragment in fragments) {
                val startLine = fragment.startLine1
                val endLine = fragment.endLine1

                val startColumn = fragment.startOffset1 - documentBefore.getLineStartOffset(startLine)
                val endColumn = fragment.endOffset1 - documentBefore.getLineStartOffset(endLine)

                val startPosition = Position(startLine, startColumn)
                val endPosition = Position(endLine, endColumn)

                val rangeAfter = TextRange(fragment.startOffset2, fragment.endOffset2)

                val textEdit = TextEdit(
                    Range(startPosition, endPosition),
                    documentAfter.getText(TextRange(rangeAfter.startOffset, rangeAfter.endOffset))
                )

                textEdits.add(textEdit)
            }

            return Pair(textEdits, fragments)
        }
    }
}
