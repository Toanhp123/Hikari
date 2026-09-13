package app.openstory.build.architecture

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

object MergedManifestStartupVerifier {
    fun verify(
        xml: String,
        policy: FoundationPolicy,
        allowedProviders: Set<String> = emptySet(),
    ): List<FoundationViolation> {
        val document = manifestDocument(xml)

        return buildList {
            document.getElementsByTagName("meta-data").elements()
                .filterNot { metadata ->
                    metadata.hasAttributeNS(ANDROID_NS, "value") ||
                        metadata.hasAttributeNS(ANDROID_NS, "resource")
                }
                .forEach { metadata ->
                    add(
                        FoundationViolation(
                            code = "v2_manifest.metadata_value_missing",
                            detail = metadata.androidName(),
                        ),
                    )
                }

            document.getElementsByTagName("service").elements().forEach { service ->
                add(
                    FoundationViolation(
                        code = "v2_manifest.service_forbidden",
                        detail = service.androidName(),
                    ),
                )
            }

            document.getElementsByTagName("provider").elements().forEach { provider ->
                val providerName = provider.androidName()
                if (providerName in allowedProviders) {
                    return@forEach
                }
                if (providerName != ANDROIDX_STARTUP_PROVIDER) {
                    add(
                        FoundationViolation(
                            code = "v2_manifest.provider_forbidden",
                            detail = providerName,
                        ),
                    )
                    return@forEach
                }

                val initializerNames = provider.childNodes.elements()
                    .filter { child ->
                        child.tagName == "meta-data" &&
                            child.getAttributeNS(ANDROID_NS, "value") ==
                            ANDROIDX_STARTUP_VALUE
                    }
                    .map { initializer -> initializer.androidName() }
                    .toList()
                if (initializerNames.isEmpty()) {
                    add(
                        FoundationViolation(
                            code = "v2_manifest.initializer_missing",
                            detail = providerName,
                        ),
                    )
                }
                initializerNames
                    .filterNot(policy.allowedStartupInitializers::contains)
                    .forEach { initializerName ->
                        add(
                            FoundationViolation(
                                code = "v2_manifest.initializer_unclassified",
                                detail = initializerName,
                            ),
                        )
                    }
            }

            document.getElementsByTagName("uses-permission").elements()
                .map { permission -> permission.androidName() }
                .filter(policy.forbiddenManifestPermissions::contains)
                .forEach { permission ->
                    add(
                        FoundationViolation(
                            code = "v2_manifest.permission_forbidden",
                            detail = permission,
                        ),
                    )
                }
        }.distinct().sorted()
    }

    private fun manifestDocument(xml: String) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }.newDocumentBuilder().parse(InputSource(StringReader(xml)))

    private fun Element.androidName(): String =
        getAttributeNS(ANDROID_NS, "name").ifBlank { "<missing-android-name>" }

    private fun org.w3c.dom.NodeList.elements(): Sequence<Element> = sequence {
        for (index in 0 until length) {
            val element = item(index) as? Element ?: continue
            yield(element)
        }
    }

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val ANDROIDX_STARTUP_PROVIDER =
        "androidx.startup.InitializationProvider"
    private const val ANDROIDX_STARTUP_VALUE = "androidx.startup"
}
