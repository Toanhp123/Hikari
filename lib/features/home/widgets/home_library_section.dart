import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/home/home_view_model.dart';
import 'package:hikari/features/home/widgets/home_section_link.dart';

class HomeFeedHeader extends StatelessWidget {
  const HomeFeedHeader({
    super.key,
    required this.filters,
    required this.effectiveFilter,
    required this.onSelectFilter,
    this.onOpenLibrary,
  });

  final List<HomeFilterType> filters;
  final HomeFilterType effectiveFilter;
  final ValueChanged<HomeFilterType> onSelectFilter;
  final VoidCallback? onOpenLibrary;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Recently added',
                  style: HikariTypography.titleLarge.copyWith(
                    color: colors.textPrimary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              if (onOpenLibrary != null)
                HomeSectionLink(label: 'Library', onTap: onOpenLibrary!),
            ],
          ),
          if (filters.isNotEmpty) ...[
            const SizedBox(height: HikariSpacing.xs),
            HikariChipRow(
              children: [
                for (final filter in filters)
                  HikariChip(
                    label: filter.mediaType == null
                        ? 'All'
                        : mediaTypeFilterLabel(filter.mediaType!),
                    isSelected: effectiveFilter == filter,
                    onTap: () => onSelectFilter(filter),
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class HomeMediaGridSliver extends StatelessWidget {
  const HomeMediaGridSliver({
    super.key,
    required this.items,
    required this.openMedia,
  });

  final List<Media> items;
  final void Function(BuildContext, Media) openMedia;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return HomeBoundedSliverBox(
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: HikariSpacing.lg,
            vertical: HikariSpacing.xl,
          ),
          child: Center(
            child: Text(
              'No items in this category.',
              style: HikariTypography.bodySmall.copyWith(
                color: context.hikariColors.textMuted,
              ),
            ),
          ),
        ),
      );
    }

    return _HomePosterGridSliver(
      childCount: items.length,
      itemBuilder: (context, index) {
        final item = items[index];
        final colors = context.hikariColors;
        return MediaPoster(
          title: item.title,
          badgeText: mediaTypeBadgeLabel(item.type),
          badgeColor: mediaTypeBadgeColor(colors, item.type),
          onTap: () => openMedia(context, item),
        );
      },
    );
  }
}

class HomeLoadingGridSliver extends StatelessWidget {
  const HomeLoadingGridSliver({super.key});

  @override
  Widget build(BuildContext context) {
    return _HomePosterGridSliver(
      childCount: 6,
      itemBuilder: (context, index) =>
          const MediaPoster(title: '', isLoading: true),
    );
  }
}

class HomeLibraryError extends StatelessWidget {
  const HomeLibraryError({super.key, required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return AsyncStateView(
      status: AsyncViewStatus.error,
      contentBuilder: (_) => const SizedBox.shrink(),
      errorTitle: 'Could not load your Library',
      errorMessage: 'Home could not refresh your saved media. Retry the Library load or keep exploring while the rest of Hikari remains available.',
      onRetry: onRetry,
    );
  }
}

class HomeBoundedSliverBox extends StatelessWidget {
  const HomeBoundedSliverBox({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return SliverToBoxAdapter(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: HikariBreakpoints.maxContentWidth,
          ),
          child: child,
        ),
      ),
    );
  }
}

class _HomePosterGridSliver extends StatelessWidget {
  const _HomePosterGridSliver({
    required this.childCount,
    required this.itemBuilder,
  });

  final int childCount;
  final Widget Function(BuildContext, int) itemBuilder;

  @override
  Widget build(BuildContext context) {
    return SliverLayoutBuilder(
      builder: (context, constraints) {
        final extraWidth =
            constraints.crossAxisExtent > HikariBreakpoints.maxContentWidth
            ? constraints.crossAxisExtent - HikariBreakpoints.maxContentWidth
            : 0.0;
        final horizontalPadding = (extraWidth / 2) + HikariSpacing.lg;

        return SliverPadding(
          padding: EdgeInsets.symmetric(horizontal: horizontalPadding),
          sliver: SliverGrid(
            gridDelegate: mediaPosterGridDelegate,
            delegate: SliverChildBuilderDelegate(
              itemBuilder,
              childCount: childCount,
            ),
          ),
        );
      },
    );
  }
}
