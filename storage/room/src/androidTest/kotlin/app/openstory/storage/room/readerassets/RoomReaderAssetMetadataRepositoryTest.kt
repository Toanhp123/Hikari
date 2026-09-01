package app.openstory.storage.room.readerassets

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.assets.ReaderAssetMetadata
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.reader.assets.ReaderAssetIdentityHash
import app.openstory.reader.assets.ReaderAssetIdentityMode
import app.openstory.reader.assets.ReaderAssetKeyHash
import app.openstory.reader.assets.ReaderAssetKeySchemaVersion
import app.openstory.reader.assets.ReaderAssetPersistenceMode
import app.openstory.reader.assets.ReaderAssetSourceNamespace
import app.openstory.reader.assets.ReaderContentVariant
import app.openstory.reader.assets.ReaderImageSetNamespace
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomReaderAssetMetadataRepositoryTest {
    private lateinit var database: OpenStoryDatabase
    private lateinit var repository: RoomReaderAssetMetadataRepository

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OpenStoryDatabase::class.java,
        ).build()
        repository = RoomReaderAssetMetadataRepository(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun roundTripPreservesHashedIdentityIntegrityAndCanonicalSourceNamespace() = runTest {
        val metadata = metadata("01", "plugin.reader", securityScopeHash = hash('a'))

        repository.upsert(metadata)

        assertEquals(metadata, repository.find(setOf(metadata.logicalAssetKeyHash))[metadata.logicalAssetKeyHash])
        assertEquals("plugin.reader", repository.find(setOf(metadata.logicalAssetKeyHash)).values.single().sourceNamespace.value)
    }

    @Test
    fun lookupUsageAndIndexedInvalidationAreBoundedByKeySourceAndAccount() = runTest {
        val accountA = metadata("01", "plugin.reader", securityScopeHash = hash('a'), byteSize = 10)
        val accountB = metadata("02", "plugin.reader", securityScopeHash = hash('b'), byteSize = 20)
        val otherSource = metadata("03", "plugin.other", securityScopeHash = hash('a'), byteSize = 30)
        listOf(accountA, accountB, otherSource).forEach { repository.upsert(it) }

        assertEquals(60, repository.usageBytes())
        assertEquals(
            setOf(accountA.logicalAssetKeyHash, otherSource.logicalAssetKeyHash),
            repository.find(setOf(accountA.logicalAssetKeyHash, otherSource.logicalAssetKeyHash))
                .keys,
        )

        assertEquals(listOf(accountA), repository.detachAccount(accountA.sourceNamespace, hash('a')))
        assertNull(repository.find(setOf(accountA.logicalAssetKeyHash))[accountA.logicalAssetKeyHash])
        assertEquals(listOf(accountB), repository.detachSource(accountB.sourceNamespace))
        assertEquals(listOf(otherSource), repository.all())
    }

    private fun metadata(
        suffix: String,
        pluginId: String,
        securityScopeHash: String?,
        byteSize: Long = 10,
    ) = ReaderAssetMetadata(
        logicalAssetKeyHash = ReaderAssetKeyHash(hash(suffix.last())),
        keySchemaVersion = ReaderAssetKeySchemaVersion(1),
        storyId = StoryId("story-$suffix"),
        canonicalChapterId = CanonicalChapterId("chapter-$suffix"),
        chapterReleaseId = ChapterReleaseId("release-$suffix"),
        sourceNamespace = ReaderAssetSourceNamespace.fromPluginId(PluginId(pluginId)),
        securityScopeHash = securityScopeHash,
        contentVariant = ReaderContentVariant.ORIGINAL,
        identityMode = ReaderAssetIdentityMode.TRUSTED_STABLE,
        persistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
        imageSetNamespaceHash = ReaderImageSetNamespace(hash('c')),
        pageIdentityHash = ReaderAssetIdentityHash(hash('d')),
        pageOrdinal = suffix.toInt(),
        blobId = "blob-$suffix",
        byteSize = byteSize,
        localBlobChecksum = BlobChecksum(hash('e')),
        sourceIntegrityHash = hash('f'),
        createdAtEpochMillis = 100,
        lastAccessedAtEpochMillis = 200,
        lastConsumedAtEpochMillis = 300,
    )

    private fun hash(character: Char): String = character.toString().repeat(64)
}
