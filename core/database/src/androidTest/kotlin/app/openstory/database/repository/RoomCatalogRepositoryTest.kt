package app.openstory.database.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.common.AppResult
import app.openstory.common.FakeClock
import app.openstory.database.OpenStoryDatabase
import app.openstory.model.CatalogEntryWithStory
import app.openstory.model.CatalogSnapshot
import app.openstory.model.CatalogSnapshotItem
import app.openstory.model.CatalogSnapshotSection
import app.openstory.model.CatalogSourceMetadata
import app.openstory.model.ContentType
import app.openstory.model.LanguageTag
import app.openstory.model.PluginId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCatalogRepositoryTest {

    @Test
    fun refreshPreservesOtherCatalogMetadata() = runTest {
        withFixture { fixture ->
            fixture.ingest("catalog.a", "a-story", score = 8.2, scale = 10.0)
            fixture.ingest("catalog.b", "b-story", score = 91.0, scale = 100.0)
            val catalogBBefore = assertNotNull(
                fixture.repository.observeCatalogHome(PluginId("catalog.b")).first(),
            )
            fixture.clock.advanceBy(1_000L)
            fixture.ingest("catalog.a", "a-story", score = 8.4, scale = 10.0)
            val catalogBAfter = assertNotNull(
                fixture.repository.observeCatalogHome(PluginId("catalog.b")).first(),
            )

            assertEquals(catalogBBefore, catalogBAfter)
            assertEquals(
                8.4,
                fixture.entry("catalog.a", "a-story").entry.score,
            )
            assertEquals(
                91.0,
                fixture.entry("catalog.b", "b-story").entry.score,
            )
            assertEquals(
                1,
                fixture.rowCount(
                    "catalog_entries",
                    "catalog_plugin_id = 'catalog.a'",
                ),
            )
            assertEquals(
                1,
                fixture.rowCount(
                    "catalog_entries",
                    "catalog_plugin_id = 'catalog.b'",
                ),
            )
        }
    }

    @Test
    fun samePluginAndSourceUpdatesWithoutDuplicateRow() = runTest {
        withFixture { fixture ->
            fixture.ingest("catalog.a", "story-1", title = "Old")
            val originalStoryId = fixture.entry("catalog.a", "story-1").storyId
            fixture.ingest("catalog.a", "story-1", title = "New")
            val refreshed = fixture.entry("catalog.a", "story-1")

            assertEquals("New", refreshed.entry.title)
            assertEquals(originalStoryId, refreshed.storyId)
            assertEquals(1, fixture.rowCount("catalog_entries"))
            assertEquals(1, fixture.rowCount("story_catalog_entries"))
        }
    }

    @Test
    fun sameSourceIdFromDifferentPluginsRemainsDistinct() = runTest {
        withFixture { fixture ->
            fixture.ingest("catalog.a", "shared")
            fixture.ingest("catalog.b", "shared")

            val a = fixture.entry("catalog.a", "shared")
            val b = fixture.entry("catalog.b", "shared")

            assertTrue(a.entry.id != b.entry.id)
            assertTrue(a.storyId != b.storyId)
            assertEquals(2, fixture.rowCount("catalog_entries"))
            assertEquals(2, fixture.rowCount("canonical_stories"))
        }
    }

    @Test
    fun databaseIdentityRejectsDuplicateSourceIdentity() = runTest {
        withFixture { fixture ->
            fixture.ingest("catalog.a", "story-1")

            assertFailsWith<SQLiteConstraintException> {
                fixture.database.openHelper.writableDatabase.execSQL(
                    """
                    INSERT INTO catalog_entries(
                        catalog_entry_id,
                        catalog_plugin_id,
                        external_story_id,
                        source_url,
                        title,
                        aliases_json,
                        authors_json,
                        description,
                        genres_json,
                        content_type,
                        language_tags_json,
                        cover_reference,
                        publication_status,
                        score,
                        score_scale,
                        popularity_rank,
                        plugin_version,
                        fetched_at_epoch_millis
                    ) VALUES(
                        ?, ?, ?, NULL, ?, '[]', '[]', NULL, '[]', ?, '[]',
                        NULL, NULL, NULL, NULL, NULL, ?, ?
                    )
                    """.trimIndent(),
                    arrayOf<Any?>(
                        "different-primary-key",
                        "catalog.a",
                        "story-1",
                        "Duplicate",
                        "WEB_NOVEL",
                        "1.0.0",
                        1_000L,
                    ),
                )
            }
        }
    }

    @Test
    fun sectionAndItemOrderRoundTrips() = runTest {
        withFixture { fixture ->
            fixture.repository.ingest(
                CatalogSnapshot(
                    pluginId = PluginId("catalog.a"),
                    pluginVersion = "1.0.0",
                    sections = listOf(
                        section(
                            id = "featured",
                            title = "Featured",
                            items = listOf(item("story-2"), item("story-1")),
                        ),
                        section(
                            id = "new",
                            title = "New",
                            items = listOf(item("story-3")),
                        ),
                    ),
                ),
            ).requireSuccess()

            val home =
                assertNotNull(
                    fixture.repository
                        .observeCatalogHome(PluginId("catalog.a"))
                        .first(),
                )

            assertEquals(listOf("featured", "new"), home.sections.map { it.sourceId })
            assertEquals(
                listOf("story-2", "story-1"),
                home.sections.first().items.map { it.entry.externalStoryId },
            )
        }
    }

    @Test
    fun contentTypeRoundTripsFromSnapshotToCachedHome() = runTest {
        withFixture { fixture ->
            fixture.repository.ingest(
                snapshot(
                    plugin = "catalog.a",
                    sectionItems =
                        listOf(
                            item(
                                sourceId = "light-novel-1",
                                contentType = ContentType.LIGHT_NOVEL,
                            ),
                        ),
                ),
            ).requireSuccess()

            val home =
                assertNotNull(
                    fixture.repository
                        .observeCatalogHome(PluginId("catalog.a"))
                        .first(),
                )

            assertEquals(
                ContentType.LIGHT_NOVEL,
                home.sections.single().items.single().entry.contentType,
            )
        }
    }

    @Test
    fun observeCatalogHomesReturnsIndependentPluginSnapshots() = runTest {
        withFixture { fixture ->
            fixture.ingest("catalog.b", "b-story")
            fixture.ingest("catalog.a", "a-story")

            val homes = fixture.repository.observeCatalogHomes().first()

            assertEquals(
                listOf("catalog.a", "catalog.b"),
                homes.map { it.pluginId.value },
            )
            assertEquals(
                "a-story",
                homes[0].sections.single().items.single().entry.externalStoryId,
            )
            assertEquals(
                "b-story",
                homes[1].sections.single().items.single().entry.externalStoryId,
            )
        }
    }

    @Test
    fun removedHomeCardKeepsCatalogEntryAndCanonicalStory() = runTest {
        withFixture { fixture ->
            fixture.repository.ingest(
                snapshot(
                    plugin = "catalog.a",
                    sectionItems = listOf(item("story-1"), item("story-2")),
                ),
            ).requireSuccess()
            val removedStoryId = fixture.entry("catalog.a", "story-2").storyId

            fixture.repository.ingest(
                snapshot(
                    plugin = "catalog.a",
                    sectionItems = listOf(item("story-1")),
                ),
            ).requireSuccess()

            val home = assertNotNull(
                fixture.repository.observeCatalogHome(PluginId("catalog.a")).first(),
            )
            assertEquals(
                listOf("story-1"),
                home.sections.single().items.map { it.entry.externalStoryId },
            )
            assertNotNull(fixture.repository.catalogEntry(PluginId("catalog.a"), "story-2").value())
            assertEquals(
                1,
                fixture.rowCount(
                    "canonical_stories",
                    "story_id = '${removedStoryId.value}'",
                ),
            )
        }
    }

    @Test
    fun pluginVersionAndSingleRefreshTimestampAreRetained() = runTest {
        withFixture(now = 5_000L) { fixture ->
            fixture.repository.ingest(
                CatalogSnapshot(
                    pluginId = PluginId("catalog.a"),
                    pluginVersion = "2.3.4",
                    sections = listOf(
                        section(
                            id = "home",
                            title = "Home",
                            items = listOf(item("one"), item("two")),
                        ),
                    ),
                ),
            ).requireSuccess()

            val home = assertNotNull(
                fixture.repository.observeCatalogHome(PluginId("catalog.a")).first(),
            )
            assertEquals("2.3.4", home.pluginVersion)
            assertEquals(5_000L, home.refreshedAtEpochMillis)
            assertEquals(
                setOf(5_000L),
                home.sections.flatMap { it.items }.map { it.entry.fetchedAtEpochMillis }.toSet(),
            )
            assertEquals(
                setOf("2.3.4"),
                home.sections.flatMap { it.items }.map { it.entry.pluginVersion }.toSet(),
            )
        }
    }

    @Test
    fun homeRefreshDoesNotEraseRicherCatalogDetails() = runTest {
        withFixture(now = 1_000L) { fixture ->
            fixture.ingest("catalog.a", "story-1", title = "Card Title")
            fixture.clock.advanceBy(1_000L)
            fixture.repository.upsertSourceMetadata(
                pluginId = PluginId("catalog.a"),
                pluginVersion = "1.0.0",
                metadata = richMetadata("story-1"),
            ).requireSuccess()
            fixture.clock.advanceBy(1_000L)
            fixture.ingest(
                plugin = "catalog.a",
                sourceId = "story-1",
                title = "Fresh Card Title",
                score = 9.0,
                scale = 10.0,
            )

            val entry = fixture.entry("catalog.a", "story-1").entry
            assertEquals("Fresh Card Title", entry.title)
            assertEquals(9.0, entry.score)
            assertEquals("https://catalog.example/story-1", entry.sourceUrl)
            assertEquals(setOf("Rich Alias"), entry.aliases)
            assertEquals("Rich description", entry.description)
            assertEquals(setOf("Fantasy"), entry.genres)
            assertEquals(setOf(LanguageTag("en")), entry.languageTags)
            assertEquals("ONGOING", entry.publicationStatus)
            assertEquals(7L, entry.popularityRank)
            assertEquals(3_000L, entry.fetchedAtEpochMillis)
        }
    }

    @Test
    fun sourceMetadataUpsertDoesNotChangeHomeMembership() = runTest {
        withFixture { fixture ->
            fixture.repository.ingest(
                snapshot(
                    plugin = "catalog.a",
                    sectionItems = listOf(item("story-2"), item("story-1")),
                ),
            ).requireSuccess()
            val before = assertNotNull(
                fixture.repository.observeCatalogHome(PluginId("catalog.a")).first(),
            )

            fixture.repository.upsertSourceMetadata(
                pluginId = PluginId("catalog.a"),
                pluginVersion = "1.0.1",
                metadata = richMetadata("story-1"),
            ).requireSuccess()

            val after = assertNotNull(
                fixture.repository.observeCatalogHome(PluginId("catalog.a")).first(),
            )
            assertEquals(
                before.sections.map { section ->
                    section.sourceId to section.items.map { it.entry.externalStoryId }
                },
                after.sections.map { section ->
                    section.sourceId to section.items.map { it.entry.externalStoryId }
                },
            )
        }
    }

    @Test
    fun discoveryIngestDoesNotCreateLibraryMembership() = runTest {
        withFixture { fixture ->
            fixture.ingest("catalog.a", "story-1")

            assertEquals(0, fixture.rowCount("library_entries"))
        }
    }

    @Test
    fun failedTransactionKeepsPreviousCompleteSnapshot() = runTest {
        withFixture { fixture ->
            fixture.repository.ingest(
                snapshot(
                    plugin = "catalog.a",
                    sectionItems = listOf(item("old-1"), item("old-2")),
                ),
            ).requireSuccess()
            val before = assertNotNull(
                fixture.repository.observeCatalogHome(PluginId("catalog.a")).first(),
            )

            fixture.database.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER fail_wave05_home_item
                BEFORE INSERT ON catalog_home_items
                WHEN NEW.catalog_plugin_id = 'catalog.a'
                BEGIN
                    SELECT RAISE(ABORT, 'forced task01 rollback');
                END
                """.trimIndent(),
            )

            val result = fixture.repository.ingest(
                snapshot(
                    plugin = "catalog.a",
                    version = "2.0.0",
                    sectionItems = listOf(item("new-1")),
                ),
            )
            assertIs<AppResult.Failure>(result)

            val after = assertNotNull(
                fixture.repository.observeCatalogHome(PluginId("catalog.a")).first(),
            )
            assertEquals(before, after)
            assertNull(
                fixture.repository.catalogEntry(PluginId("catalog.a"), "new-1").value(),
            )
            assertEquals(
                0,
                fixture.rowCount(
                    "catalog_entries",
                    "catalog_entry_id = 'catalog:catalog.a:new-1'",
                ),
            )
            assertEquals(
                0,
                fixture.rowCount(
                    "canonical_stories",
                    "story_id = 'catalog:catalog.a:new-1'",
                ),
            )
            assertEquals(
                0,
                fixture.rowCount(
                    "story_catalog_entries",
                    "catalog_entry_id = 'catalog:catalog.a:new-1'",
                ),
            )
        }
    }

    private suspend fun withFixture(
        now: Long = 1_000L,
        block: suspend (Fixture) -> Unit,
    ) {
        val database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                OpenStoryDatabase::class.java,
            ).build()
        val clock = FakeClock(now)
        val fixture =
            Fixture(
                database = database,
                clock = clock,
                repository = RoomCatalogRepository(database, clock = clock),
            )

        try {
            block(fixture)
        } finally {
            database.close()
        }
    }

    private data class Fixture(
        val database: OpenStoryDatabase,
        val clock: FakeClock,
        val repository: RoomCatalogRepository,
    ) {
        suspend fun ingest(
            plugin: String,
            sourceId: String,
            title: String = "Example",
            score: Double? = 8.4,
            scale: Double? = 10.0,
        ) {
            repository.ingest(
                snapshot(
                    plugin = plugin,
                    sectionItems =
                        listOf(
                            item(
                                sourceId = sourceId,
                                title = title,
                                score = score,
                                scale = scale,
                            ),
                        ),
                ),
            ).requireSuccess()
        }

        suspend fun entry(
            plugin: String,
            sourceId: String,
        ): CatalogEntryWithStory =
            assertNotNull(
                repository.catalogEntry(PluginId(plugin), sourceId).value(),
            )

        fun rowCount(
            table: String,
            where: String? = null,
        ): Int =
            database.openHelper.writableDatabase
                .query(
                    buildString {
                        append("SELECT COUNT(*) FROM ")
                        append(table)
                        if (where != null) {
                            append(" WHERE ")
                            append(where)
                        }
                    },
                )
                .use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                }
    }

    private companion object {
        fun snapshot(
            plugin: String,
            version: String = "1.0.0",
            sectionItems: List<CatalogSnapshotItem>,
        ): CatalogSnapshot =
            CatalogSnapshot(
                pluginId = PluginId(plugin),
                pluginVersion = version,
                sections =
                    listOf(
                        section(
                            id = "home",
                            title = "Home",
                            items = sectionItems,
                        ),
                    ),
            )

        fun section(
            id: String,
            title: String,
            items: List<CatalogSnapshotItem>,
        ): CatalogSnapshotSection =
            CatalogSnapshotSection(
                sourceId = id,
                title = title,
                items = items,
            )

        fun item(
            sourceId: String,
            title: String = "Example $sourceId",
            contentType: ContentType = ContentType.WEB_NOVEL,
            score: Double? = 8.4,
            scale: Double? = 10.0,
        ): CatalogSnapshotItem =
            CatalogSnapshotItem(
                sourceId = sourceId,
                title = title,
                contentType = contentType,
                authors = listOf("Author"),
                coverReference = "https://catalog.example/$sourceId.jpg",
                score = score,
                scoreScale = scale,
            )

        fun richMetadata(
            sourceId: String,
        ): CatalogSourceMetadata =
            CatalogSourceMetadata(
                sourceId = sourceId,
                sourceUrl = "https://catalog.example/$sourceId",
                title = "Rich Title",
                aliases = setOf("Rich Alias"),
                authors = setOf("Author", "Second Author"),
                description = "Rich description",
                genres = setOf("Fantasy"),
                contentType = ContentType.WEB_NOVEL,
                languageTags = setOf(LanguageTag("en")),
                coverReference = "https://catalog.example/rich.jpg",
                publicationStatus = "ONGOING",
                score = 8.8,
                scoreScale = 10.0,
                popularityRank = 7L,
            )
    }
}

private fun <T> AppResult<T>.value(): T? =
    when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> null
    }

private fun <T> AppResult<T>.requireSuccess(): T =
    when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> error("Expected success, got ${error.code}")
    }
