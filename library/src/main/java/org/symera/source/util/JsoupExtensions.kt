package org.symera.source.util

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

fun Element.textOrNull(selector: String): String? =
    selectFirst(selector)?.text()

fun Element.attrOrNull(selector: String, attribute: String): String? =
    selectFirst(selector)?.attr(attribute)

fun Element.ownTextOrNull(): String? =
    ownText().ifBlank { null }

fun Document.selectFirstOrNull(cssQuery: String): Element? =
    selectFirst(cssQuery)

inline fun <T : Any> Elements.mapNotNullToList(transform: (Element) -> T?): List<T> {
    val result = mutableListOf<T>()
    for (element in this) {
        transform(element)?.let(result::add)
    }
    return result
}
