package app.openstory.designsystem.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Stable
class HikariBackdropScope internal constructor(
    internal val token: HikariBackdropToken,
)

internal class HikariBackdropToken(internal val backdrop: Backdrop)

@Composable
fun HikariBackdropHost(
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit,
    overlay: @Composable HikariBackdropScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val scope = HikariBackdropScope(HikariBackdropToken(backdrop))
    Box(modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            content = background,
        )
        scope.overlay()
    }
}
