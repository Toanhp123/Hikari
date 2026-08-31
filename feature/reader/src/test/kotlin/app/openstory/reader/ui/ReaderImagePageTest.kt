package app.openstory.reader.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.reader.document.ReaderBlock
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderImagePageTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(DelicateCoilApi::class)
    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun imageRequestReceivesConstraintsAndLeavesLoadingState() {
        val context = RuntimeEnvironment.getApplication()
        val originalImageLoader = SingletonImageLoader.get(context)
        var requests = 0
        var measuredHeight = 0
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            chain.request.sizeResolver.size()
                            requests += 1
                            SuccessResult(
                                image = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).asImage(),
                                request = chain.request,
                            )
                        },
                    )
                }
                .build(),
        )
        try {
            compose.setContent {
                HikariTheme {
                    ReaderImagePage(
                        block = ReaderBlock.ImagePage(
                            "page-1",
                            "hash/page-1.jpg",
                            "https://example.test/page-1.jpg",
                        ),
                        documentFingerprint = "document-1",
                        onReloadDocument = {},
                        onImageMeasured = { measuredHeight = it },
                    )
                }
            }

            compose.waitUntil(timeoutMillis = 2_000L) { requests > 0 && measuredHeight > 0 }
            compose.onNodeWithText("Loading page").assertDoesNotExist()
            assertTrue(requests > 0)
            assertTrue(measuredHeight > 0)
        } finally {
            SingletonImageLoader.setUnsafe(originalImageLoader)
        }
    }
}
