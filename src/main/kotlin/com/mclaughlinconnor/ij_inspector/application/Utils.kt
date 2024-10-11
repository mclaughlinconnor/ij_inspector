package com.mclaughlinconnor.ij_inspector.application

import com.intellij.codeInsight.lookup.LookupArranger.DefaultArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

class Utils {
    companion object {
        fun createDocument(project: Project, filePath: String): Document? {
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null

            @Suppress("UnstableApiUsage") val document = ReadAction.compute<Document?, RuntimeException> {
                FileDocumentManager.getInstance().getDocument(virtualFile, project)
            }

            return document
        }


        fun obtainLookup(editor: Editor, project: Project): LookupImpl {
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
}