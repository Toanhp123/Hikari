package app.openstory.catalog.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.hikariBreakpoints
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariLayoutRatios
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun DiscoverLoadingContent(
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discover-loading"),
    ) {
        val heroHeight = if (maxWidth >= MaterialTheme.hikariBreakpoints.expandedContent) {
            MaterialTheme.hikariDimensions.discoverHeroExpandedHeight
        } else {
            MaterialTheme.hikariDimensions.discoverHeroCompactHeight
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.sectionGap),
        ) {
            HikariSkeleton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
                shape = MaterialTheme.hikariShapes.hero,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
            ) {
                repeat(2) {
                    HikariSkeleton(
                        modifier = Modifier
                            .weight(1f)
                            .height(MaterialTheme.hikariDimensions.minimumTouchTarget),
                        shape = MaterialTheme.hikariShapes.compactControl,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap)) {
                repeat(3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
                    ) {
                        repeat(3) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
                            ) {
                                HikariSkeleton(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(MaterialTheme.hikariLayoutRatios.posterCardAspectRatio),
                                    shape = MaterialTheme.hikariShapes.contentCard,
                                )
                                HikariSkeleton(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(MaterialTheme.hikariSpacing.space16),
                                    shape = MaterialTheme.hikariShapes.compactControl,
                                )
                                HikariSkeleton(
                                    modifier = Modifier
                                        .width(MaterialTheme.hikariDimensions.posterShelfNarrowWidth)
                                        .height(MaterialTheme.hikariSpacing.space12),
                                    shape = MaterialTheme.hikariShapes.compactControl,
                                )
                            }
                        }
                    }
                }
            }
            repeat(5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
                ) {
                    HikariSkeleton(
                        modifier = Modifier
                            .width(MaterialTheme.hikariSpacing.space32)
                            .height(MaterialTheme.hikariDimensions.posterUpdate.height),
                        shape = MaterialTheme.hikariShapes.compactControl,
                    )
                    HikariSkeleton(
                        modifier = Modifier
                            .width(MaterialTheme.hikariDimensions.posterUpdate.width)
                            .height(MaterialTheme.hikariDimensions.posterUpdate.height),
                        shape = MaterialTheme.hikariShapes.contentCard,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
                    ) {
                        HikariSkeleton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MaterialTheme.hikariSpacing.space16),
                            shape = MaterialTheme.hikariShapes.compactControl,
                        )
                        HikariSkeleton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MaterialTheme.hikariSpacing.space12),
                            shape = MaterialTheme.hikariShapes.compactControl,
                        )
                        Spacer(Modifier.height(MaterialTheme.hikariSpacing.space4))
                    }
                }
            }
        }
    }
}
