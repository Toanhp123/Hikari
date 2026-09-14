package app.openstory.catalog.runtime

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.source.CatalogCapabilitySet
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import app.openstory.catalog.runtime.trace.CatalogTraceSink
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import org.junit.Test

class CatalogRuntimeHostTest {
    @Test
    fun hostSnapshotsMutableRegistrationMetadata() = runTest {
        val mediaTypes = linkedSetOf(CatalogMediaType.MANGA)
        val source = RecordingSource()
        val binding = CatalogSourceBinding(
            catalogSourceKey = CatalogSourceKey("authority.mutable"),
            sourceVersion = "v1",
            discoverCapability = source,
            storyCapability = source,
            displayName = "Mutable authority",
            mediaTypes = mediaTypes,
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = CatalogRuntimeFactory(
            bindings = listOf(binding),
            openStorage = { RuntimeFakeStorage() },
            wallClockEpochMs = { 44L },
            dispatchers = CatalogExecutionDispatchers(cpu = dispatcher, io = dispatcher),
        ).createHost()

        mediaTypes.clear()

        assertEquals(setOf(CatalogMediaType.MANGA), host.descriptor(binding.catalogSourceKey)?.mediaTypes)
        assertEquals(binding.catalogSourceKey, host.authorityResolver().authorityFor(CatalogMediaType.MANGA))
        host.close()
    }

    @Test
    fun descriptorCapabilitiesMustMatchRegisteredExecutionPorts() {
        val source = RecordingSource()
        val binding = CatalogSourceBinding(
            catalogSourceKey = CatalogSourceKey("authority.truthful"),
            sourceVersion = "v1",
            discoverCapability = source,
            storyCapability = source,
        )

        assertEquals(CatalogCapabilitySet(discover = true, storyDetail = true), binding.capabilities)
        assertThrows(IllegalArgumentException::class.java) {
            CatalogSourceBinding(
                catalogSourceKey = CatalogSourceKey("authority.liar"),
                sourceVersion = "v1",
                capabilities = CatalogCapabilitySet(search = true),
            )
        }
    }

    @Test
    fun directSessionRejectsMultipleAuthorities() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val factory = CatalogRuntimeFactory(
            bindings = listOf(
                binding("authority.first", CatalogMediaType.MANGA),
                binding("authority.second", CatalogMediaType.LIGHT_NOVEL),
            ),
            openStorage = { RuntimeFakeStorage() },
            wallClockEpochMs = { 44L },
            dispatchers = CatalogExecutionDispatchers(cpu = dispatcher, io = dispatcher),
        )

        assertThrows(IllegalArgumentException::class.java) { factory.createSession() }
    }

    @Test
    fun descriptorLookupAndAuthorityResolutionDoNotOpenStorage() = runTest {
        var openCount = 0
        val manga = binding("authority.manga", CatalogMediaType.MANGA)
        val lightNovel = binding("authority.novel", CatalogMediaType.LIGHT_NOVEL)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = CatalogRuntimeFactory(
            bindings = listOf(manga, lightNovel),
            openStorage = {
                openCount += 1
                RuntimeFakeStorage()
            },
            wallClockEpochMs = { 44L },
            dispatchers = CatalogExecutionDispatchers(cpu = dispatcher, io = dispatcher),
        ).createHost()

        assertEquals("authority.manga", host.descriptor(manga.catalogSourceKey)?.displayName)
        assertEquals(manga.catalogSourceKey, host.authorityResolver().authorityFor(CatalogMediaType.MANGA))
        assertEquals(lightNovel.catalogSourceKey, host.authorityResolver().authorityFor(CatalogMediaType.LIGHT_NOVEL))
        assertEquals(0, openCount)

        host.close()
        assertEquals(0, openCount)
    }

    @Test
    fun authoritiesShareOneStoreAndOnlyHostCloseClosesIt() = runTest {
        val storage = RuntimeFakeStorage()
        var openCount = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = CatalogRuntimeFactory(
            bindings = listOf(
                binding("authority.manga", CatalogMediaType.MANGA),
                binding("authority.novel", CatalogMediaType.LIGHT_NOVEL),
            ),
            openStorage = {
                openCount += 1
                storage
            },
            wallClockEpochMs = { 44L },
            dispatchers = CatalogExecutionDispatchers(cpu = dispatcher, io = dispatcher),
        ).createHost()

        val manga = host.activate(CatalogSourceKey("authority.manga")) as CatalogCapabilityActivation.Available
        val novel = host.activate(CatalogSourceKey("authority.novel"))
        manga.discoverSession(CatalogMediaType.MANGA).quiesce()

        assertEquals(1, openCount)
        assertEquals(0, storage.closeCount)
        assertSame(novel, host.activate(CatalogSourceKey("authority.novel")))

        host.close()
        assertEquals(1, storage.closeCount)
        assertEquals(CatalogCapabilityActivation.Unavailable(), host.activate(CatalogSourceKey("missing")))
    }

    @Test
    fun closingOneAuthoritySessionDoesNotCloseSharedStore() = runTest {
        val storage = RuntimeFakeStorage()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val storeOwner = CatalogStoreOwner(
            openStorage = { storage },
            dispatchers = CatalogExecutionDispatchers(cpu = dispatcher, io = dispatcher),
            ownershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
        )
        val first = CatalogCapabilitySession(
            binding = binding("authority.first", CatalogMediaType.MANGA),
            storeOwner = storeOwner,
            wallClockEpochMs = { 44L },
            dispatchers = CatalogExecutionDispatchers(cpu = dispatcher, io = dispatcher),
            traceSink = CatalogTraceSink {},
            ownershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
        )
        val second = CatalogCapabilitySession(
            binding = binding("authority.second", CatalogMediaType.LIGHT_NOVEL),
            storeOwner = storeOwner,
            wallClockEpochMs = { 44L },
            dispatchers = CatalogExecutionDispatchers(cpu = dispatcher, io = dispatcher),
            traceSink = CatalogTraceSink {},
            ownershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
        )

        first.activate()
        val secondActivation = second.activate()
        first.close()

        assertEquals(0, storage.closeCount)
        assertSame(secondActivation, second.activate())

        second.close()
        assertEquals(0, storage.closeCount)
        storeOwner.close()
        assertEquals(1, storage.closeCount)
    }

    @Test
    fun activeStoryPinsProtectSharedRetentionAcrossAuthorities() = runTest {
        val storage = RuntimeFakeStorage()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstBinding = binding("authority.first", CatalogMediaType.MANGA)
        val secondBinding = binding("authority.second", CatalogMediaType.LIGHT_NOVEL)
        val host = CatalogRuntimeFactory(
            bindings = listOf(firstBinding, secondBinding),
            openStorage = { storage },
            wallClockEpochMs = { 44L },
            dispatchers = CatalogExecutionDispatchers(cpu = dispatcher, io = dispatcher),
        ).createHost()
        val first = host.activate(firstBinding.catalogSourceKey) as CatalogCapabilityActivation.Available
        val second = host.activate(secondBinding.catalogSourceKey) as CatalogCapabilityActivation.Available
        val firstRef = testRef("first", firstBinding.catalogSourceKey)
        val secondRef = testRef("second", secondBinding.catalogSourceKey)

        first.storyDetailSession(firstRef).activate()
        second.storyDetailSession(secondRef).activate()
        first.storyDetailSession(firstRef).release()

        assertEquals(setOf(secondRef.storyId), storage.releaseProtectedSets.single())
        host.close()
    }

    private fun binding(id: String, mediaType: CatalogMediaType) = CatalogSourceBinding(
        catalogSourceKey = CatalogSourceKey(id),
        sourceVersion = "v1",
        discoverCapability = RecordingSource(),
        storyCapability = RecordingSource(),
        displayName = id,
        mediaTypes = setOf(mediaType),
    )
}
