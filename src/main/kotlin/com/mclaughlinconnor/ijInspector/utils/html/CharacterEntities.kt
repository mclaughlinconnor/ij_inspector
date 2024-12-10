package com.mclaughlinconnor.ijInspector.utils.html

import com.intellij.openapi.util.text.Strings

fun unescapeXmlEntities(text: String): String {
    return Strings.unescapeXmlEntities(text)
        .replace("&nbsp;", " ")
        .replace("&#32;", " ")
        .replace("&lbrace;", "{")
        .replace("&rbrace;", "}")
}

fun escapeOnlyBraces(text: String): String {
    return text
        .replace("{", "&lbrace;")
        .replace("}", "&rbrace;")
}

@Suppress("unused")
fun escapeXmlEntities(text: String): String {
    return Strings.escapeXmlEntities(text)
        .replace(" ", "&nbsp;")
        .replace("{", "&lbrace;")
        .replace("}", "&rbrace;")
}