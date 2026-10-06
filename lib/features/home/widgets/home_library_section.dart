import 'package:flutter/material.dart';
import 'package:hikari/app/theme/design_system/design_system.dart';
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
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: HikariSpace.content),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Recently added',
                  style: theme.textTheme.titleLarge?.copyWith(
                    color: colors.onSurface,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.15,
                  ),
                ),
              ),
              if (onOpenLibrary != null)
                HomeSectionLink(label: 'Library', onTap: onOpenLibrary!),
            ],
          ),
          if (filters.isNotEmpty) ...[
            const SizedBox(height: HikariSpace.micro),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  for (final filter in filters) ...[
                    FilterChip(
                      label: Text(
                        filter.mediaType == null
                            ? 'All'
                            : mediaTypeFilterLabel(filter.mediaType!),
                      ),
                      selected: effectiveFilter == filter,
                      onSelected: (_) => onSelectFilter(filter),
                      showCheckmark: false,
                      shape: HikariShape.pill,
                    ),
                    const SizedBox(width: HikariSpace.inline),
                  ],
                ],
              ),
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
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    if (items.isEmpty) {
      return HomeBoundedSliverBox(
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: HikariSpace.content,
            vertical: HikariSpace.section,
          ),
          child: Center(
            child: Text(
              'No items in this category.',
              style: (theme.textTheme.bodySmall ?? const TextStyle()).copyWith(
                color: colors.onSurfaceVariant,
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
        return MediaPoster(
          title: item.title,
          badgeText: mediaTypeBadgeLabel(item.type),
          badgeColor: _mediaTypeBadgeColor(colors, item.type),
          onTap: () => openMedia(context, item),
        );
      },
    );
  }
}

Color _mediaTypeBadgeColor(ColorScheme colors, MediaType type) =>
    switch (type) {
      MediaType.anime => colors.primary,
      MediaType.manga => const Color(0xFFFAB387), // Catppuccin Peach
      MediaType.lightNovel => const Color(0xFF89DCEB), // Catppuccin Sky
    };

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
            maxWidth: HikariLayout.contentMaxWidth,
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
        final width = constraints.crossAxisExtent;
        final extraWidth = width > HikariLayout.contentMaxWidth
            ? width - HikariLayout.contentMaxWidth
            : 0.0;
        final horizontalPadding = (extraWidth / 2) + HikariLayout.gutter(width);
        final columnCount = HikariLayout.posterColumnCount(width);

        return SliverPadding(
          padding: EdgeInsets.symmetric(horizontal: horizontalPadding),
          sliver: SliverGrid(
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: columnCount,
              mainAxisSpacing: HikariSpace.content,
              crossAxisSpacing: HikariSpace.content,
              childAspectRatio: HikariSize.posterAspectRatio,
            ),
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
