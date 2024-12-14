package com.mclaughlinconnor.ijInspector.utils

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.mclaughlinconnor.ijInspector.lsp.Position
import com.mclaughlinconnor.ijInspector.lsp.Range

fun lspRangeToOffsets(document: Document, range: Range): Pair<Int, Int> {
    val startOffset = document.getLineStartOffset(range.start.line) + range.start.character
    val endOffset = document.getLineStartOffset(range.end.line) + range.end.character

    return Pair(startOffset, endOffset)
}

fun offsetsToLspRange(document: Document, offsets: Pair<Int, Int>): Range {
    val startLine = document.getLineNumber(offsets.first)
    val startLineOffset = document.getLineStartOffset(startLine)
    val startCharacter = offsets.first - startLineOffset

    val endLine = document.getLineNumber(offsets.second)
    val endLineOffset = document.getLineStartOffset(endLine)
    val endCharacter = offsets.second - endLineOffset

    return Range(Position(startLine, startCharacter), Position(endLine, endCharacter))
}

fun offsetsToLspRange(document: Document, textRange: TextRange): Range {
    return offsetsToLspRange(document, textRangeToOffsets(textRange))
}

fun textRangeToOffsets(textRange: TextRange): Pair<Int, Int> {
    return Pair(textRange.startOffset, textRange.endOffset)
}
