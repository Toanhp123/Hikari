package app.openstory.plugin.host.selector

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

interface HtmlScope

interface HtmlDocument : HtmlScope

interface HtmlElement : HtmlScope

data class HtmlAttributeValue(
    val value: String,
    val present: Boolean,
)

data class HtmlSemanticText(
    val value: String,
    val spans: List<HtmlSemanticSpan>,
)

data class HtmlSemanticSpan(
    val start: Int,
    val endExclusive: Int,
    val style: HtmlSemanticStyle,
)

enum class HtmlSemanticStyle {
    EMPHASIS,
    STRONG,
}

interface HtmlDocumentAdapter {
    fun parse(
        html: String,
        baseUri: String,
    ): HtmlDocument

    fun nodeCount(
        document: HtmlDocument,
    ): Int

    fun removeElements(
        document: HtmlDocument,
        css: String,
    ): HtmlDocument

    fun selectAll(
        scope: HtmlScope,
        css: String,
    ): List<HtmlElement>

    fun text(
        scope: HtmlScope,
        css: String? = null,
    ): String?

    fun attribute(
        scope: HtmlScope,
        css: String? = null,
        attribute: String,
    ): HtmlAttributeValue

    fun baseUri(scope: HtmlScope): String

    fun matches(
        element: HtmlElement,
        css: String,
    ): Boolean

    fun semanticText(
        scope: HtmlScope,
        css: String? = null,
    ): HtmlSemanticText
}

class JsoupHtmlDocumentAdapter :
    HtmlDocumentAdapter {

    override fun parse(
        html: String,
        baseUri: String,
    ): HtmlDocument =
        JsoupHtmlDocument(
            value = Jsoup.parse(
                html,
                baseUri,
            ),
        )

    override fun nodeCount(
        document: HtmlDocument,
    ): Int =
        document
            .requireJsoupDocument()
            .allElements
            .size

    override fun removeElements(
        document: HtmlDocument,
        css: String,
    ): HtmlDocument {
        val jsoupDocument =
            document.requireJsoupDocument()

        jsoupDocument
            .select(css)
            .remove()

        return document
    }

    override fun selectAll(
        scope: HtmlScope,
        css: String,
    ): List<HtmlElement> =
        scope
            .requireJsoupElement()
            .select(css)
            .map(::JsoupHtmlElement)

    override fun text(
        scope: HtmlScope,
        css: String?,
    ): String? =
        selectedElement(scope, css)?.text()

    override fun attribute(
        scope: HtmlScope,
        css: String?,
        attribute: String,
    ): HtmlAttributeValue {
        val element = selectedElement(scope, css)
            ?: return HtmlAttributeValue(value = "", present = false)

        return HtmlAttributeValue(
            value = element.normalizedAttribute(attribute),
            present = element.hasAttr(attribute),
        )
    }

    override fun baseUri(scope: HtmlScope): String =
        scope.requireJsoupElement().baseUri()

    override fun matches(
        element: HtmlElement,
        css: String,
    ): Boolean = element.requireJsoupElement().`is`(css)

    override fun semanticText(
        scope: HtmlScope,
        css: String?,
    ): HtmlSemanticText {
        val element = selectedElement(scope, css)
            ?: return HtmlSemanticText(value = "", spans = emptyList())
        val value = element.text()
        var searchStart = 0
        val spans = element.select("em, i, strong, b").mapNotNull { styled ->
            val text = styled.text()
            val start = value.indexOf(text, startIndex = searchStart)
            if (text.isEmpty() || start < 0) {
                null
            } else {
                searchStart = start + text.length
                HtmlSemanticSpan(
                    start = start,
                    endExclusive = start + text.length,
                    style = when (styled.normalName()) {
                        "em", "i" -> HtmlSemanticStyle.EMPHASIS
                        else -> HtmlSemanticStyle.STRONG
                    },
                )
            }
        }
        return HtmlSemanticText(value = value, spans = spans)
    }
}

private data class JsoupHtmlDocument(
    val value: Document,
) : HtmlDocument

private data class JsoupHtmlElement(
    val value: Element,
) : HtmlElement

private fun HtmlDocument.requireJsoupDocument():
    Document =
    (this as? JsoupHtmlDocument)?.value
        ?: throw IllegalArgumentException(
            "Unsupported HTML document value.",
        )

private fun HtmlScope.requireJsoupElement(): Element =
    when (this) {
        is JsoupHtmlDocument -> value
        is JsoupHtmlElement -> value
        else -> throw IllegalArgumentException(
            "Unsupported HTML scope value.",
        )
    }

private fun selectedElement(
    scope: HtmlScope,
    css: String?,
): Element? {
    val element = scope.requireJsoupElement()
    return if (css == null) element else element.selectFirst(css)
}
private val urlAttributeNames =
    setOf(
        "action",
        "background",
        "cite",
        "formaction",
        "href",
        "manifest",
        "poster",
        "src",
    )

private fun Element.normalizedAttribute(
    attribute: String,
): String {
    val rawValue =
        attr(attribute)

    if (
        rawValue.isBlank() ||
        attribute.lowercase() !in urlAttributeNames
    ) {
        return rawValue
    }

    return absUrl(attribute)
        .ifBlank {
            rawValue
        }
}
