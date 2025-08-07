@file:Suppress("UnstableApiUsage")

package com.mclaughlinconnor.ijInspector.languageService

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.Symbol
import com.intellij.model.psi.*
import com.intellij.model.psi.impl.DeclarationOrReference
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.findDocument
import com.intellij.polySymbols.webTypes.WebTypesSymbol
import com.intellij.psi.*
import com.intellij.psi.util.elementsAroundOffsetUp
import com.intellij.psi.util.leavesAroundOffset
import com.intellij.psi.util.startOffset
import com.intellij.util.SmartList
import com.mclaughlinconnor.ijInspector.lsp.*
import com.mclaughlinconnor.ijInspector.rpc.Connection
import com.mclaughlinconnor.ijInspector.utils.Utils.Companion.createDocument


class DefinitionService(
    private val myProject: Project,
    private val myConnection: Connection
) {
    private val injectedLanguageManager = InjectedLanguageManager.getInstance(myProject)
    private val messageFactory: com.mclaughlinconnor.ijInspector.rpc.MessageFactory =
        com.mclaughlinconnor.ijInspector.rpc.MessageFactory()
    private val myApplication: Application = ApplicationManager.getApplication()

    fun doDefinition(requestId: Int, params: DefinitionParams) {
        val filePath = params.textDocument.uri.substring("file://".length)
        val position = params.position
        val document = createDocument(myProject, filePath) ?: return

        val cursorOffset = document.getLineStartOffset(position.line) + position.character

        myApplication.invokeLater {
            val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return@invokeLater

            val locations: MutableList<Location> = mutableListOf()

            val onComplete = { myConnection.write(messageFactory.newMessage(Response(requestId, locations))) }
            val handleLocation = { location: Location -> locations.add(location) }

            var file = psiFile
            var offset = cursorOffset

            val injectedElement = injectedLanguageManager.findInjectedElementAt(file, offset)
            if (injectedElement != null) {
                file = injectedElement.containingFile
                offset = injectedElement.startOffset
            }

            fetchDefinitions(file, offset, handleLocation, onComplete)
        }
    }

    fun fetchDefinitions(
        psiFile: PsiFile,
        cursorOffset: Int,
        handleLocation: (Location) -> Any?,
        onComplete: () -> Any?
    ) {
        val decOrRefs: List<DeclarationOrReference> = declarationsOrReferences(psiFile, cursorOffset)

        for (decOrRef in decOrRefs) {
            if (decOrRef is DeclarationOrReference.Reference) {
                val references = decOrRef.reference.resolveReference()
                for (reference in references) {
                    val source = when (reference) {
                        is WebTypesSymbol -> reference.psiContext // IDK is this the right type?
                        else -> PsiSymbolService.getInstance().extractElementFromSymbol(reference)
                    }

                    if (source != null && source.isPhysical) {
                        handleLocation(psiElementToLocation(getNameIdentifier(source.navigationElement)))
                    }
                }
            }
        }

        onComplete()
    }

    private fun psiElementToLocation(element: PsiElement): Location {
        val range = injectedLanguageManager.injectedToHost(element, element.textRange)
        val psiFile = injectedLanguageManager.getTopLevelFile(element)

        val file = psiFile.containingFile.virtualFile!!
        val document = file.findDocument()!!

        val startOffset = range.startOffset
        val endOffset = range.endOffset

        val startLine = document.getLineNumber(startOffset)
        val endLine = document.getLineNumber(endOffset)

        val startColumn = startOffset - document.getLineStartOffset(startLine)
        val endColumn = endOffset - document.getLineStartOffset(endLine)

        val startPosition = Position(startLine, startColumn)
        val endPosition = Position(endLine, endColumn)

        return Location("file://${file.path}", Range(startPosition, endPosition))
    }

    private fun getNameIdentifier(element: PsiElement): PsiElement {
        if (element is PsiNameIdentifierOwner) {
            return element.nameIdentifier ?: return element
        }

        return element
    }
}

@Suppress("UnresolvedPluginConfigReference")
val declarationProviderEP = ExtensionPointName<PsiSymbolDeclarationProvider>("com.intellij.psi.declarationProvider")

private fun referencesInElement(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolReference> {
    if (offsetInElement < 0) {
        return PsiSymbolReferenceService.getService().getReferences(element)
    } else {
        val hints = PsiSymbolReferenceHints.offsetHint(offsetInElement)
        return PsiSymbolReferenceService.getService().getReferences(element, hints)
    }
}

private data class NamedElementAndLeaf(val namedElement: PsiElement, val leaf: PsiElement)

private fun namedElement(file: PsiFile, offset: Int): NamedElementAndLeaf? {
    for ((leaf, _) in file.leavesAroundOffset(offset)) {
        val namedElement: PsiElement? = TargetElementUtil.getNamedElement(leaf)
        if (namedElement != null) {
            return NamedElementAndLeaf(namedElement, leaf)
        }
    }
    return null
}

private fun implicitReference(element: PsiElement, offsetInElement: Int): PsiSymbolReference? {
    for (handler in ImplicitReferenceProvider.EP_NAME.extensions) {
        return handler.getImplicitReference(element, offsetInElement) ?: continue
    }
    return null
}

private fun allReferencesInElement(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolReference> {
    val references: Collection<PsiSymbolReference> = referencesInElement(element, offsetInElement)
    if (references.isNotEmpty()) {
        return references
    }
    val implicitReference = implicitReference(element, offsetInElement)
    if (implicitReference != null) {
        return listOf(implicitReference)
    }
    return emptyList()
}

internal fun PsiFile.allReferencesAround(offset: Int): Collection<PsiSymbolReference> {
    for ((element, offsetInElement) in elementsAroundOffsetUp(offset)) {
        val referencesInElement = allReferencesInElement(element, offsetInElement)
        if (referencesInElement.isNotEmpty()) {
            return referencesInElement
        }
    }
    return emptyList()
}

internal class PsiElement2Declaration(
    private val myTargetElement: PsiElement,
    private val myDeclaringElement: PsiElement,
    private val myDeclarationRange: TextRange
) : PsiSymbolDeclaration {
    override fun getSymbol(): Symbol {
        return PsiSymbolService.getInstance().asSymbol(myTargetElement)
    }

    override fun getDeclaringElement(): PsiElement {
        return myDeclaringElement
    }

    override fun getRangeInDeclaringElement(): TextRange {
        return myDeclarationRange
    }

    companion object {
        /**
         * Adapts target element obtained from an element at caret to a `PsiSymbolDeclaration`.
         *
         * @param declaredElement  target element (symbol); used for target-based actions, e.g. Find Usages
         * @param declaringElement element at caret from which `declaredElement` was obtained; used to determine the declaration range
         */
        fun createFromDeclaredPsiElement(
            declaredElement: PsiElement, declaringElement: PsiElement
        ): PsiSymbolDeclaration {
            val declarationRange = getDeclarationRangeFromPsi(declaredElement, declaringElement)
            return PsiElement2Declaration(declaredElement, declaringElement, declarationRange)
        }

        private fun getDeclarationRangeFromPsi(declaredElement: PsiElement, declaringElement: PsiElement): TextRange {
            val identifyingElement = getIdentifyingElement(declaredElement) ?: return rangeOf(declaringElement)
            val identifyingRange =
                relateRange(identifyingElement, rangeOf(identifyingElement), declaringElement) ?: return rangeOf(
                    declaringElement
                )
            return identifyingRange
        }

        private fun getIdentifyingElement(targetElement: PsiElement): PsiElement? {
            if (targetElement is PsiNameIdentifierOwner) {
                return getIdentifyingElement(targetElement)
            }
            return null
        }

        private fun getIdentifyingElement(nameIdentifierOwner: PsiNameIdentifierOwner): PsiElement? {
            val identifyingElement = nameIdentifierOwner.nameIdentifier ?: return null
            return identifyingElement
        }

        /**
         * @return range in identifying element relative to range of declaring element,
         * or `null` if the elements are from different files
         */
        private fun relateRange(
            identifyingElement: PsiElement, rangeInIdentifyingElement: TextRange, declaringElement: PsiElement
        ): TextRange? {
            if (identifyingElement === declaringElement) {
                return rangeInIdentifyingElement
            } else if (identifyingElement.containingFile === declaringElement.containingFile) {
                val rangeInFile = rangeInIdentifyingElement.shiftRight(identifyingElement.textRange.startOffset)
                val declaringElementRange = declaringElement.textRange
                return if (declaringElementRange.contains(rangeInFile)) {
                    rangeInFile.shiftLeft(declaringElementRange.startOffset)
                } else {
                    null
                }
            } else {
                return null
            }
        }

        /**
         * @return range of `element` relative to itself
         */
        private fun rangeOf(element: PsiElement): TextRange {
            return TextRange.from(0, element.textLength)
        }
    }
}

private fun declarationsOrReferences(file: PsiFile, offset: Int): List<DeclarationOrReference> {
    val result = SmartList<DeclarationOrReference>()

    var foundNamedElement: PsiElement? = null

    val allDeclarations = file.allDeclarationsAround(offset)
    if (allDeclarations.isEmpty()) {
        namedElement(file, offset)?.let { (namedElement, leaf) ->
            foundNamedElement = namedElement
            val declaration: PsiSymbolDeclaration =
                PsiElement2Declaration.createFromDeclaredPsiElement(namedElement, leaf)
            result += DeclarationOrReference.Declaration(declaration)
        }
    } else {
        allDeclarations.mapTo(result, DeclarationOrReference::Declaration)
    }

    val allReferences = file.allReferencesAround(offset)
    if (allReferences.isEmpty()) {
        fromTargetEvaluator(file, offset)?.let { evaluatorReference ->
            if (foundNamedElement != null && evaluatorReference.targetElements.singleOrNull() === foundNamedElement) {
                return@let // treat self-reference as a declaration
            }
            result += DeclarationOrReference.Reference(evaluatorReference, offset)
        }
    } else {
        allReferences.mapTo(result) { DeclarationOrReference.Reference(it, offset) }
    }

    return result
}

internal fun mockEditor(file: PsiFile): Editor? {
    val project = file.project
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return null
    return object : ImaginaryEditor(project, document) {
        override fun toString(): String = "API compatibility editor"
    }
}

private fun fromTargetEvaluator(file: PsiFile, offset: Int): EvaluatorReference? {
    val editor = mockEditor(file) ?: return null
    val flags =
        TargetElementUtil.getInstance().allAccepted and TargetElementUtil.ELEMENT_NAME_ACCEPTED.inv() and TargetElementUtil.LOOKUP_ITEM_ACCEPTED.inv()
    val reference = TargetElementUtil.findReference(editor, offset)
    val origin: PsiOrigin = if (reference != null) {
        PsiOrigin.Reference(reference)
    } else {
        val leaf = file.findElementAt(TargetElementUtil.adjustOffset(file, editor.document, offset)) ?: return null
        PsiOrigin.Leaf(leaf)
    }
    val targetElement = TargetElementUtil.getInstance().findTargetElement(editor, flags, offset)
    val targetElements: List<PsiElement> = when {
        targetElement != null -> listOf(targetElement)
        reference != null -> TargetElementUtil.getInstance().getTargetCandidates(reference).toList()
        else -> emptyList()
    }
    if (targetElements.isEmpty()) {
        return null
    }
    return EvaluatorReference(origin, targetElements)
}

internal class EvaluatorReference(
    private val origin: PsiOrigin, val targetElements: Collection<PsiElement>
) : PsiSymbolReference {

    override fun getElement(): PsiElement = origin.elementAtPointer

    override fun getRangeInElement(): TextRange = absoluteRange.shiftLeft(element.textRange.startOffset)

    override fun getAbsoluteRange(): TextRange {
        val absoluteRanges = origin.absoluteRanges
        return absoluteRanges.singleOrNull() ?: TextRange.create(
            absoluteRanges.first().startOffset, absoluteRanges.last().endOffset
        )
    }

    override fun resolveReference(): Collection<Symbol> {
        return targetElements.map(PsiSymbolService.getInstance()::asSymbol)
    }

    override fun toString(): String = "EvaluatorReference(origin=$origin, targetElements=$targetElements)"
}


internal fun getReferenceRanges(elementAtPointer: PsiElement): List<TextRange> {
    if (!elementAtPointer.isPhysical) {
        return emptyList()
    }
    var textOffset = elementAtPointer.textOffset
    val range = elementAtPointer.textRange
        ?: throw AssertionError("Null range for " + elementAtPointer + " of " + elementAtPointer.javaClass)
    if (textOffset < range.startOffset || textOffset < 0) {
        textOffset = range.startOffset
    }
    return listOf(TextRange(textOffset, range.endOffset))
}

internal sealed class PsiOrigin {

    abstract val absoluteRanges: List<TextRange>

    abstract val elementAtPointer: PsiElement

    class Leaf(private val leaf: PsiElement) : PsiOrigin() {

        override val absoluteRanges: List<TextRange> get() = getReferenceRanges(leaf)

        override val elementAtPointer: PsiElement get() = leaf

        override fun toString(): String = "Leaf($leaf)"
    }

    class Reference(private val reference: PsiReference) : PsiOrigin() {

        override val absoluteRanges: List<TextRange> get() = ReferenceRange.getAbsoluteRanges(reference)

        override val elementAtPointer: PsiElement get() = reference.element

        override fun toString(): String = "Reference($reference)"
    }
}

internal fun PsiFile.allDeclarationsAround(offsetInFile: Int): Collection<PsiSymbolDeclaration> {
    for ((element: PsiElement, offsetInElement: Int) in elementsAroundOffsetUp(offsetInFile)) {
        ProgressManager.checkCanceled()
        val declarations: Collection<PsiSymbolDeclaration> = declarationsInElement(element, offsetInElement)
        if (declarations.isNotEmpty()) {
            return declarations
        }
    }
    return emptyList()
}

private fun declarationsInElement(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
    val result = SmartList<PsiSymbolDeclaration>()
    @Suppress("OverrideOnly")
    result.addAll(element.ownDeclarations)
    for (extension: PsiSymbolDeclarationProvider in declarationProviderEP.lazySequence()) {
        ProgressManager.checkCanceled()
        result.addAll(extension.getDeclarations(element, offsetInElement))
    }
    return result.filterTo(SmartList()) {
        element === it.declaringElement && (offsetInElement < 0 || it.rangeInDeclaringElement.containsOffset(
            offsetInElement
        ))
    }
}
