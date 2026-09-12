package app.openstory.benchmark

import android.content.Context
import app.openstory.catalog.feature.fixture.BenchmarkCatalogPreparationEvidence

internal object BenchmarkPreparationEvidenceStore {
    fun write(context: Context, evidence: BenchmarkCatalogPreparationEvidence) {
        check(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_AGED_ROWS, evidence.agedUnrelatedRows)
                .putInt(KEY_MAX_DISCOVER_TOUCHED, evidence.maxDiscoverTouchedStoryIds)
                .putInt(KEY_MAX_RELEASE_TOUCHED, evidence.maxReleaseTouchedStoryIds)
                .putInt(KEY_ORPHAN_RETENTION_ROWS, evidence.orphanRetentionRows)
                .putInt(KEY_PIN_PRUNE_RETENTION_ROWS, evidence.pinPruneRetentionRows)
                .putInt(KEY_PATHOLOGICAL_IMAGE_REJECTIONS, evidence.pathologicalImageRejections)
                .commit(),
        ) { "Benchmark preparation evidence could not be persisted." }
    }

    fun read(context: Context): BenchmarkCatalogPreparationEvidence {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return BenchmarkCatalogPreparationEvidence(
            agedUnrelatedRows = preferences.getInt(KEY_AGED_ROWS, 0),
            maxDiscoverTouchedStoryIds = preferences.getInt(KEY_MAX_DISCOVER_TOUCHED, 0),
            maxReleaseTouchedStoryIds = preferences.getInt(KEY_MAX_RELEASE_TOUCHED, 0),
            orphanRetentionRows = preferences.getInt(KEY_ORPHAN_RETENTION_ROWS, 0),
            pinPruneRetentionRows = preferences.getInt(KEY_PIN_PRUNE_RETENTION_ROWS, 0),
            pathologicalImageRejections = preferences.getInt(KEY_PATHOLOGICAL_IMAGE_REJECTIONS, 0),
        )
    }

    private const val PREFERENCES_NAME = "benchmark_catalog_preparation_evidence"
    private const val KEY_AGED_ROWS = "aged_rows"
    private const val KEY_MAX_DISCOVER_TOUCHED = "max_discover_touched"
    private const val KEY_MAX_RELEASE_TOUCHED = "max_release_touched"
    private const val KEY_ORPHAN_RETENTION_ROWS = "orphan_retention_rows"
    private const val KEY_PIN_PRUNE_RETENTION_ROWS = "pin_prune_retention_rows"
    private const val KEY_PATHOLOGICAL_IMAGE_REJECTIONS = "pathological_image_rejections"
}
