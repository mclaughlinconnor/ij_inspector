package com.mclaughlinconnor.ij_inspector.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.mclaughlinconnor.ij_inspector.application.lsp.CompletionItem
import com.mclaughlinconnor.ij_inspector.application.lsp.CompletionItemLabelDetails
import com.mclaughlinconnor.ij_inspector.application.lsp.CompletionList
import com.mclaughlinconnor.ij_inspector.application.lsp.Response

// var inputBySorter = MultiMap.createLinked<CompletionSorterImpl, LookupElement>()
// for (element: LookupElement? in source) {
//     inputBySorter.putValue(obtainSorter(element), element)
// }
// for (sorter: CompletionSorterImpl? in inputBySorter.keySet()) {
//     inputBySorter.put(sorter, sortByPresentation(inputBySorter[sorter]))
// }

class MessageFactory {
    private var objectMapper = ObjectMapper()

    private fun newMessage(data: String): String {
        val s = StringBuilder()
        s.append("Content-Length: ")
        s.append(data.length)
        s.append("\r\n\r\n")
        s.append(data)

        return s.toString()
    }

    public fun newCompletionMessage(id: Int, completions: List<CompletionResult>): String {
        val items = ArrayList<CompletionItem>()
        val list = CompletionList(false, items)
        for (completion in completions) {
            val presentation = LookupElementPresentation()
            completion.lookupElement.renderElement(presentation)
            // addElement(completion.lookupElement, completion.sorter, completion.prefixMatcher, presentation)

            val details = CompletionItemLabelDetails(presentation.tailText, presentation.typeText)

            list.pushItem(
                CompletionItem(
                    presentation.itemText ?: completion.lookupElement.lookupString,
                    completion.lookupElement.lookupString,
                    completion.lookupElement.lookupString,
                    completion.lookupElement.lookupString,
                    details,
                )
            )
        }

        val response = Response(id, list)
        val json = objectMapper.writeValueAsString(response)

        return newMessage(json.toString())
    }
}