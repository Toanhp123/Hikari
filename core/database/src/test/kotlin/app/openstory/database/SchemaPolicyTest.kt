package app.openstory.database

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element
import org.w3c.dom.Node

class SchemaPolicyTest {

    @Test
    fun versionOneSchemaAndFixtureAreCommitted() {
        val moduleRoot =
            findRepositoryRoot()
                .resolve("core/database")

        val schema =
            moduleRoot.resolve(
                "schemas/" +
                    "app.openstory.database.OpenStoryDatabase/" +
                    "1.json",
            )

        val fixture =
            moduleRoot.resolve(
                "src/androidTest/assets/database/v1/openstory.db",
            )

        assertTrue(
            schema.isFile,
            "Commit the Room v1 schema JSON",
        )

        val schemaText =
            schema.readText()

        assertTrue(
            "canonical_chapters" in schemaText,
        )
        assertTrue(
            "chapter_releases" in schemaText,
        )
        assertTrue(
            fixture.isFile,
            "Commit the seeded Room v1 database fixture",
        )
        assertTrue(
            fixture.length() > 0L,
            "The v1 fixture must not be empty",
        )
    }

    @Test
    fun backupPolicyUsesDatabaseAllowlist() {
        val repositoryRoot =
            findRepositoryRoot()

        val legacyRoot =
            parse(
                repositoryRoot.resolve(
                    "app/src/main/res/xml/backup_rules.xml",
                ),
            )

        assertExactDatabaseAllowlist(legacyRoot)

        val modernRoot =
            parse(
                repositoryRoot.resolve(
                    "app/src/main/res/xml/data_extraction_rules.xml",
                ),
            )

        assertExactDatabaseAllowlist(
            modernRoot.requiredDirectChild(
                "cloud-backup",
            ),
        )

        assertExactDatabaseAllowlist(
            modernRoot.requiredDirectChild(
                "device-transfer",
            ),
        )
    }

    private fun assertExactDatabaseAllowlist(
        root: Element,
    ) {
        val rules =
            root.directChildElements()

        assertEquals(
            expected = 1,
            actual = rules.size,
            message =
                "Backup scope must contain exactly one allowlisted path",
        )

        val include =
            rules.single()

        assertEquals(
            "include",
            include.tagName,
        )
        assertEquals(
            "database",
            include.getAttribute("domain"),
        )
        assertEquals(
            "openstory.db",
            include.getAttribute("path"),
        )
    }

    private fun parse(
        file: File,
    ): Element {
        assertTrue(
            file.isFile,
            "Missing policy file: ${file.path}",
        )

        return DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun Element.requiredDirectChild(
        tagName: String,
    ): Element {
        val match =
            directChildElements()
                .singleOrNull { child ->
                    child.tagName == tagName
                }

        return requireNotNull(match) {
            "Missing direct <$tagName> policy block"
        }
    }

    private fun Element.directChildElements():
        List<Element> =
        buildList {
            val children =
                childNodes

            for (index in 0 until children.length) {
                val child =
                    children.item(index)

                if (
                    child.nodeType ==
                    Node.ELEMENT_NODE
                ) {
                    add(child as Element)
                }
            }
        }

    private fun findRepositoryRoot():
        File {
        var current =
            File(
                requireNotNull(
                    System.getProperty("user.dir"),
                ) {
                    "Missing user.dir system property"
                },
            ).absoluteFile

        while (
            !current.resolve(
                "settings.gradle.kts",
            ).isFile
        ) {
            current =
                requireNotNull(
                    current.parentFile,
                ) {
                    "Unable to locate repository root"
                }
        }

        return current
    }
}
