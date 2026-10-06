import 'package:flutter/material.dart';
import 'package:hikari/app/theme/design_system/design_system.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/home/home_view_model.dart';
import 'package:hikari/features/home/widgets/home_section_link.dart';

/// Horizontal shelf for the user's recently added saved library items on Home.
///
/// Keeps Home 100% consistent with horizontal discovery shelves rather than
/// an unbounded vertical grid, with a direct link to the full Library tab.
class HomeRecentShelf extends StatelessWidget {
  const HomeRecentShelf({
    super.key,
    required this.items,
    required this.openMedia,
    this.filters = const [],
    this.effectiveFilter,
    this.onSelectFilter,
    this.onOpenLibrary,
  });

  final List<Media> items;
  final void Function(BuildContext, Media) openMedia;
  final List<HomeFilterType> filters;
  final HomeFilterType? effectiveFilter;
  final ValueChanged<HomeFilterType>? onSelectFilter;
  final VoidCallback? onOpenLibrary;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final width = MediaQuery.sizeOf(context).width;
    final layout = HikariLayout.classify(width);
    final posterWidth = switch (layout) {
      HikariLayoutClass.compact => 132.0,
      HikariLayoutClass.medium => 144.0,
      HikariLayoutClass.expanded || HikariLayoutClass.wide => 156.0,
    };

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: HikariSpace.content),
          child: Row(
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
        ),
        if (filters.isNotEmpty && onSelectFilter != null) ...[
          const SizedBox(height: HikariSpace.micro),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(
              horizontal: HikariSpace.content,
            ),
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
                    onSelected: (_) => onSelectFilter!(filter),
                    showCheckmark: false,
                    shape: HikariShape.pill,
                  ),
                  const SizedBox(width: HikariSpace.inline),
                ],
              ],
            ),
          ),
        ],
        const SizedBox(height: HikariSpace.inline),
        if (items.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: HikariSpace.content,
              vertical: HikariSpace.section,
            ),
            child: Center(
              child: Text(
                'No items in this category.',
                style: (theme.textTheme.bodySmall ?? const TextStyle())
                    .copyWith(color: colors.onSurfaceVariant),
              ),
            ),
          )
        else
          SizedBox(
            height: posterWidth * 1.5,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(
                horizontal: HikariSpace.content,
              ),
              scrollDirection: Axis.horizontal,
              physics: const BouncingScrollPhysics(),
              itemCount: items.length,
              separatorBuilder: (_, _) =>
                  const SizedBox(width: HikariSpace.compact),
              itemBuilder: (context, index) {
                final item = items[index];
                return SizedBox(
                  width: posterWidth,
                  child: MediaPoster(
                    title: item.title,
                    badgeText: mediaTypeBadgeLabel(item.type),
                    badgeColor: _mediaTypeBadgeColor(colors, item.type),
                    onTap: () => openMedia(context, item),
                  ),
                );
              },
            ),
          ),
      ],
    );
  }
}

Color _mediaTypeBadgeColor(ColorScheme colors, MediaType type) =>
    switch (type) {
      MediaType.anime => colors.primary,
      MediaType.manga => const Color(0xFFFAB387), // Catppuccin Peach
      MediaType.lightNovel => const Color(0xFF89DCEB), // Catppuccin Sky
    };

class HomeRecentShelfSkeleton extends StatelessWidget {
  const HomeRecentShelfSkeleton({super.key});

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final width = MediaQuery.sizeOf(context).width;
    final layout = HikariLayout.classify(width);
    final posterWidth = switch (layout) {
      HikariLayoutClass.compact => 132.0,
      HikariLayoutClass.medium => 144.0,
      HikariLayoutClass.expanded || HikariLayoutClass.wide => 156.0,
    };

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: HikariSpace.content),
          child: Container(
            width: 140,
            height: 20,
            decoration: ShapeDecoration(
              color: colors.surfaceContainerHighest,
              shape: HikariShape.pill,
            ),
          ),
        ),
        const SizedBox(height: HikariSpace.inline),
        SizedBox(
          height: posterWidth * 1.5,
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(
              horizontal: HikariSpace.content,
            ),
            scrollDirection: Axis.horizontal,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: 4,
            separatorBuilder: (_, _) =>
                const SizedBox(width: HikariSpace.compact),
            itemBuilder: (_, _) => SizedBox(
              width: posterWidth,
              child: const MediaPoster(title: '', isLoading: true),
            ),
          ),
        ),
      ],
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
