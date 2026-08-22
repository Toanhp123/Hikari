package app.openstory.di

import android.util.Log
import app.openstory.BuildConfig
import app.openstory.catalog.diagnostics.CanonicalDecisionTrace
import app.openstory.catalog.diagnostics.CanonicalDiagnosticsSink
import app.openstory.catalog.diagnostics.NoOpCanonicalDiagnosticsSink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CanonicalDiagnosticsModule {
    @Provides
    @Singleton
    fun provideCanonicalDiagnosticsSink(): CanonicalDiagnosticsSink = if (BuildConfig.DEBUG) {
        CanonicalDiagnosticsSink { trace -> Log.d(TAG, trace.toSafeLogLine()) }
    } else {
        NoOpCanonicalDiagnosticsSink
    }

    private fun CanonicalDecisionTrace.toSafeLogLine(): String = buildString {
        append("kind=").append(kind)
        append(" stories=").append(storyIds.joinToString { it.value })
        append(" sources=").append(sourceKeys.joinToString { "${it.pluginId.value}/${it.sourceId}" })
        field?.let { append(" field=").append(it) }
        append(" policies=").append(policyVersions)
        append(" reasons=").append(reasonCodes)
        append(" fingerprints=").append(evidenceFingerprints)
    }

    private const val TAG = "CanonicalDecision"
}
