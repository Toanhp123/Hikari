package app.openstory.build.architecture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FoundationPolicyLoaderTest {
    @Test
    fun loadsVersionOnePolicy() {
        val policy = FoundationPolicyLoader.parse(
            """
            {
              "schemaVersion": 1,
              "maxProductionKotlinLines": 300,
              "forbiddenSourceTokens": ["androidx.room."],
              "forbiddenBuildTokens": ["implementation(project("],
              "forbiddenBroadTypeSuffixes": ["Manager"],
              "forbiddenManifestPermissions": ["android.permission.INTERNET"],
              "allowedStartupInitializers": ["androidx.profileinstaller.ProfileInstallerInitializer"]
            }
            """.trimIndent(),
        )

        assertEquals(1, policy.schemaVersion)
        assertEquals(300, policy.maxProductionKotlinLines)
        assertEquals(setOf("androidx.room."), policy.forbiddenSourceTokens)
    }

    @Test
    fun rejectsUnknownPolicyField() {
        val error = assertFailsWith<IllegalArgumentException> {
            FoundationPolicyLoader.parse(
                """
                {
                  "schemaVersion": 1,
                  "maxProductionKotlinLines": 300,
                  "forbiddenSourceTokens": [],
                  "forbiddenBuildTokens": [],
                  "forbiddenBroadTypeSuffixes": [],
                  "forbiddenManifestPermissions": [],
                  "allowedStartupInitializers": [],
                  "surprise": true
                }
                """.trimIndent(),
            )
        }

        assertTrue("v2_foundation.policy_keys" in error.message.orEmpty())
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val error = assertFailsWith<IllegalArgumentException> {
            FoundationPolicyLoader.parse(validPolicyJson(schemaVersion = 2))
        }

        assertTrue("v2_foundation.schema:2" in error.message.orEmpty())
    }

    @Test
    fun rejectsNonPositiveProductionLineLimit() {
        val error = assertFailsWith<IllegalArgumentException> {
            FoundationPolicyLoader.parse(validPolicyJson(maxProductionKotlinLines = 0))
        }

        assertTrue("v2_foundation.max_lines:0" in error.message.orEmpty())
    }

    @Test
    fun rejectsBlankPolicyValue() {
        val error = assertFailsWith<IllegalArgumentException> {
            FoundationPolicyLoader.parse(
                validPolicyJson(allowedStartupInitializers = listOf(" ")),
            )
        }

        assertTrue(
            "v2_foundation.blank_value:allowedStartupInitializers" in
                error.message.orEmpty(),
        )
    }

    @Test
    fun rejectsDuplicatePolicyValue() {
        val error = assertFailsWith<IllegalArgumentException> {
            FoundationPolicyLoader.parse(
                validPolicyJson(
                    forbiddenSourceTokens = listOf("androidx.room.", "androidx.room."),
                ),
            )
        }

        assertTrue(
            "v2_foundation.duplicate_value:forbiddenSourceTokens" in
                error.message.orEmpty(),
        )
    }

    @Test
    fun rejectsNonStringPolicyValue() {
        val error = assertFailsWith<IllegalArgumentException> {
            FoundationPolicyLoader.parse(
                """
                {
                  "schemaVersion": 1,
                  "maxProductionKotlinLines": 300,
                  "forbiddenSourceTokens": [7],
                  "forbiddenBuildTokens": [],
                  "forbiddenBroadTypeSuffixes": [],
                  "forbiddenManifestPermissions": [],
                  "allowedStartupInitializers": []
                }
                """.trimIndent(),
            )
        }

        assertTrue(
            "v2_foundation.non_string_value:forbiddenSourceTokens" in
                error.message.orEmpty(),
        )
    }

    @Test
    fun retainsPolicyValueOrder() {
        val policy = FoundationPolicyLoader.parse(
            validPolicyJson(
                forbiddenSourceTokens = listOf("second.token", "first.token"),
            ),
        )

        assertEquals(
            listOf("second.token", "first.token"),
            policy.forbiddenSourceTokens.toList(),
        )
    }

    @Test
    fun rejectsInitializerWithoutQualifiedClassName() {
        listOf(
            "ProfileInstallerInitializer",
            "androidx.profile-installer.ProfileInstallerInitializer",
            "androidx.profileinstaller.1Initializer",
        ).forEach { initializer ->
            val error = assertFailsWith<IllegalArgumentException>(initializer) {
                FoundationPolicyLoader.parse(
                    validPolicyJson(allowedStartupInitializers = listOf(initializer)),
                )
            }

            assertTrue("v2_foundation.initializer_name" in error.message.orEmpty())
        }
    }

    private fun validPolicyJson(
        schemaVersion: Int = 1,
        maxProductionKotlinLines: Int = 300,
        forbiddenSourceTokens: List<String> = emptyList(),
        allowedStartupInitializers: List<String> = listOf(
            "androidx.profileinstaller.ProfileInstallerInitializer",
        ),
    ): String =
        """
        {
          "schemaVersion": $schemaVersion,
          "maxProductionKotlinLines": $maxProductionKotlinLines,
          "forbiddenSourceTokens": ${forbiddenSourceTokens.toJsonArray()},
          "forbiddenBuildTokens": [],
          "forbiddenBroadTypeSuffixes": [],
          "forbiddenManifestPermissions": [],
          "allowedStartupInitializers": ${allowedStartupInitializers.toJsonArray()}
        }
        """.trimIndent()

    private fun List<String>.toJsonArray(): String =
        joinToString(prefix = "[", postfix = "]") { value ->
            "\"$value\""
        }
    }
