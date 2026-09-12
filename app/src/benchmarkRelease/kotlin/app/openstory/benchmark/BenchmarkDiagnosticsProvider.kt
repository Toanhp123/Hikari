package app.openstory.benchmark

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import app.openstory.catalog.feature.fixture.BenchmarkCoverFixture
import app.openstory.catalog.feature.fixture.BenchmarkCatalogDiagnostics

class BenchmarkDiagnosticsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val snapshot = BenchmarkCatalogDiagnostics.snapshot()
        val preparation = BenchmarkPreparationEvidenceStore.read(requireNotNull(context))
        return MatrixCursor(COLUMNS).apply {
            addRow(
                arrayOf<Any>(
                    snapshot.activationStarts,
                    snapshot.storageReadyEvents,
                    snapshot.acquisitionStarts,
                    snapshot.activeDiscoverCollectors,
                    snapshot.activeCoverDemands,
                    snapshot.activeCoverJobs,
                    snapshot.peakActiveCoverJobs,
                    snapshot.activeImageSessions,
                    snapshot.activeRuntimeWork,
                    snapshot.activeStoryPins,
                    snapshot.activeStoryCollectors,
                    snapshot.runtimeSessionCloses,
                    snapshot.transportRequests,
                    snapshot.discoverObservationQueries,
                    snapshot.storyObservationQueries,
                    snapshot.decodedMemoryBytes,
                    snapshot.peakDecodedMemoryBytes,
                    snapshot.encodedDiskBytes,
                    snapshot.peakEncodedDiskBytes,
                    snapshot.successfulDecodes,
                    snapshot.maxDecodeTargetWidth,
                    snapshot.maxDecodeTargetHeight,
                    snapshot.originalSizeDecodes,
                    snapshot.mainThreadDecodes,
                    preparation.agedUnrelatedRows,
                    maxOf(preparation.maxDiscoverTouchedStoryIds, snapshot.maxDiscoverTouchedStoryIds),
                    maxOf(preparation.maxReleaseTouchedStoryIds, snapshot.maxReleaseTouchedStoryIds),
                    preparation.orphanRetentionRows,
                    preparation.pinPruneRetentionRows,
                    preparation.pathologicalImageRejections,
                ),
            )
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.hikari.benchmark-diagnostics"

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = Bundle().apply {
        when (method) {
            "reset-transport" -> BenchmarkCoverFixture.resetTransportRequests()
            else -> throw IllegalArgumentException("Unknown benchmark diagnostic method: $method")
        }
        putBoolean("ok", true)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        val COLUMNS = arrayOf(
            "activation",
            "storage",
            "acquisition",
            "discoverCollectors",
            "coverDemands",
            "coverJobs",
            "peakCoverJobs",
            "imageSessions",
            "runtimeWork",
            "storyPins",
            "storyCollectors",
            "runtimeCloses",
            "transport",
            "discoverQueries",
            "storyQueries",
            "decodedBytes",
            "peakDecodedBytes",
            "encodedBytes",
            "peakEncodedBytes",
            "successfulDecodes",
            "maxDecodeWidth",
            "maxDecodeHeight",
            "originalSizeDecodes",
            "mainThreadDecodes",
            "agedRows",
            "maxDiscoverTouched",
            "maxReleaseTouched",
            "orphanRetentionRows",
            "pinPruneRetentionRows",
            "pathologicalImageRejections",
        )
    }
}
