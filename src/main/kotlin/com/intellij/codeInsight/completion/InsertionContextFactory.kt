package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class InsertionContextFactory {
    companion object {
        fun buildInsertionContext(
            lookupItems: List<LookupElement>?,
            item: LookupElement,
            completionChar: Char,
            editor: Editor,
            psiFile: PsiFile,
            caretOffset: Int,
            idEndOffset: Int,
            offsetMap: OffsetMap
        ): InsertionContext {
            return CompletionUtil.createInsertionContext(
                // listOf(resultToResolve.lookupElement),
                lookupItems,
                item,
                completionChar,
                editor,
                psiFile,
                caretOffset,
                idEndOffset,
                offsetMap,
            )
        }
    }
}