package app.openstory.plugin.host.selector

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

interface HtmlDocument

interface HtmlElement

data class HtmlAttributeValue(
    val value: String,
    val present: Boolean,
)

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
        document: HtmlDocument,
        css: String,
    ): List<HtmlElement>

    fun selectText(
        elements: List<HtmlElement>,
        css: String,
    ): List<String>

    fun selectAttribute(
        elements: List<HtmlElement>,
        css: String,
        attribute: String,
    ): List<HtmlAttributeValue>
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
        document: HtmlDocument,
        css: String,
    ): List<HtmlElement> =
        document
            .requireJsoupDocument()
            .select(css)
            .map { element ->
                JsoupHtmlElement(element)
            }

    override fun selectText(
        elements: List<HtmlElement>,
        css: String,
    ): List<String> =
        elements.flatMap { value ->
            value
                .requireJsoupElement()
                .select(css)
                .map(Element::text)
        }

    override fun selectAttribute(
        elements: List<HtmlElement>,
        css: String,
        attribute: String,
    ): List<HtmlAttributeValue> =
        elements.flatMap { value ->
            value
                .requireJsoupElement()
                .select(css)
                .map { element ->
                    HtmlAttributeValue(
                        value =
                            element.normalizedAttribute(
                                attribute,
                            ),
                        present =
                            element.hasAttr(
                                attribute,
                            ),
                    )
                }
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

private fun HtmlElement.requireJsoupElement():
    Element =
    (this as? JsoupHtmlElement)?.value
        ?: throw IllegalArgumentException(
            "Unsupported HTML element value.",
        )
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
