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
    fun schemasAreContiguousThroughCurrentDatabaseVersion() {
        val repositoryRoot = findRepositoryRoot()
        val moduleRoot = repositoryRoot.resolve("core/database")
        val databaseSource = moduleRoot.resolve(
            "src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt",
        ).readText()
        val currentVersion = requireNotNull(
            Regex("version = (\\d+)").find(databaseSource),
        ).groupValues[1].toInt()
        val schemaDirectory = moduleRoot.resolve(
            "schemas/app.openstory.database.OpenStoryDatabase",
        )
        val committedVersions = schemaDirectory
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .map { file -> file.nameWithoutExtension.toInt() }
            .sorted()

        assertEquals((1..currentVersion).toList(), committedVersions)

        val fixture = moduleRoot.resolve(
            "src/androidTest/assets/database/v1/openstory.db",
        )
        assertTrue(fixture.isFile)
        assertTrue(fixture.length() > 0L)
    }

    @Test
    fun backedUpDatabaseSchemaContainsNoSecretSessionTables() {
        val moduleRoot = findRepositoryRoot().resolve("core/database")
        val schema = moduleRoot.resolve(
            "schemas/app.openstory.database.OpenStoryDatabase/3.json",
        ).readText()
        val tableNames = Regex("\"tableName\": \"([^\"]+)\"")
            .findAll(schema)
            .map { match -> match.groupValues[1].lowercase() }
            .toList()
        val forbiddenTokens = listOf(
            "session",
            "cookie",
            "token",
            "auth_secret",
        )

        assertTrue(
            tableNames.none { table ->
                forbiddenTokens.any(table::contains)
            },
            "Backed-up Room database must not contain session secret tables",
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
