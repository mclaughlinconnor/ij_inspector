package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import java.lang.reflect.Constructor

@Suppress("UnstableApiUsage")
object IndicatorFactory {
    fun buildIndicator(
        editor: Editor?,
        caret: Caret,
        invocationCount: Int,
        handler: CodeCompletionHandlerBase?,
        offsetMap: OffsetMap,
        hostOffsets: OffsetsInFile,
        hasModifiers: Boolean,
        lookup: LookupImpl
    ): CompletionProgressIndicator {
        val constructor: Constructor<CompletionProgressIndicator> =
            CompletionProgressIndicator::class.java.getDeclaredConstructor(
                Editor::class.java,
                Caret::class.java,
                Int::class.java,
                CodeCompletionHandlerBase::class.java,
                OffsetMap::class.java,
                OffsetsInFile::class.java,
                Boolean::class.java,
                LookupImpl::class.java
            )
        constructor.isAccessible = true
        return constructor.newInstance(
            editor, caret, invocationCount, handler, offsetMap, hostOffsets, hasModifiers, lookup
        )
    }
}
