package app.openstory.catalog.feature.evidence

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.core.app.ApplicationProvider
import java.io.File

internal object ScreenshotEvidence {
    fun capture(
        root: SemanticsNodeInteraction,
        scenarioName: String,
        context: Context = ApplicationProvider.getApplicationContext(),
    ): File {
        require(SCENARIO_NAME.matches(scenarioName))

        val directory = evidenceDirectory(context)
        check(directory.isDirectory || directory.mkdirs())

        val widthClass = if (context.resources.configuration.screenWidthDp >= WIDE_WIDTH_DP) {
            "wide"
        } else {
            "compact"
        }

        val evidenceFile = directory.resolve("$widthClass-$scenarioName.png")
        val bitmap = root.captureToImage().asAndroidBitmap()

        evidenceFile.outputStream().buffered().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
        }

        check(evidenceFile.isFile && evidenceFile.length() > 0L)
        return evidenceFile
    }

    private fun evidenceDirectory(context: Context): File =
        context.externalMediaDirs
            .firstOrNull()
            ?.resolve(EVIDENCE_DIRECTORY)
            ?: context.getExternalFilesDir(EVIDENCE_DIRECTORY)
            ?: context.cacheDir.resolve(EVIDENCE_DIRECTORY)

    private val SCENARIO_NAME = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    private const val EVIDENCE_DIRECTORY = "catalog-screenshot-evidence"
    private const val WIDE_WIDTH_DP = 600
    private const val PNG_QUALITY = 100
}