import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
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
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) =>
        _buildShelf(context, constraints.maxWidth),
  );

  Widget _buildShelf(BuildContext context, double width) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final layout = HikariBreakpoints.classify(width);
    final posterWidth = switch (layout) {
      HikariWidthClass.compact => 132.0,
      HikariWidthClass.medium => 144.0,
      HikariWidthClass.expanded || HikariWidthClass.wide => 156.0,
    };

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
          child: Row(
            children: [
              Expanded(
                child: Semantics(
                  headingLevel: 2,
                  child: Text(
                    'Recently added',
                    style: theme.textTheme.titleLarge?.copyWith(
                      color: colors.onSurface,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -0.15,
                    ),
                  ),
                ),
              ),
              if (onOpenLibrary != null)
                HomeSectionLink(label: 'Library', onTap: onOpenLibrary!),
            ],
          ),
        ),
        if (filters.isNotEmpty && onSelectFilter != null) ...[
          const SizedBox(height: HikariSpacing.xs),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
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
                    shape: HikariRadius.pill,
                  ),
                  const SizedBox(width: HikariSpacing.sm),
                ],
              ],
            ),
          ),
        ],
        const SizedBox(height: HikariSpacing.sm),
        if (items.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: HikariSpacing.lg,
              vertical: HikariSpacing.xl,
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
            height: mediaPosterShelfHeight(context, posterWidth),
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
              scrollDirection: Axis.horizontal,
              physics: const BouncingScrollPhysics(),
              itemCount: items.length,
              separatorBuilder: (_, _) =>
                  const SizedBox(width: HikariSpacing.md),
              itemBuilder: (context, index) {
                final item = items[index];
                return SizedBox(
                  width: posterWidth,
                  child: MediaPoster(
                    title: item.title,
                    badgeText: mediaTypeBadgeLabel(item.type),
                    badgeColor: _mediaTypeBadgeColor(theme, item.type),
                    badgeForegroundColor: _mediaTypeBadgeForeground(
                      theme,
                      item.type,
                    ),
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

Color _mediaTypeBadgeColor(ThemeData theme, MediaType type) {
  final colors = theme.colorScheme;
  final statusColors = theme.extension<HikariStatusColors>();
  return switch (type) {
    MediaType.anime => colors.primaryContainer,
    MediaType.manga =>
      statusColors?.warningContainer ?? const Color(0xFF453026),
    MediaType.lightNovel =>
      statusColors?.infoContainer ?? const Color(0xFF1E3547),
  };
}

Color _mediaTypeBadgeForeground(ThemeData theme, MediaType type) {
  final colors = theme.colorScheme;
  final statusColors = theme.extension<HikariStatusColors>();
  return switch (type) {
    MediaType.anime => colors.onPrimaryContainer,
    MediaType.manga =>
      statusColors?.onWarningContainer ?? const Color(0xFFFAB387),
    MediaType.lightNovel =>
      statusColors?.onInfoContainer ?? const Color(0xFF89DCEB),
  };
}

class HomeRecentShelfSkeleton extends StatelessWidget {
  const HomeRecentShelfSkeleton({super.key});

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) =>
        _buildSkeleton(context, constraints.maxWidth),
  );

  Widget _buildSkeleton(BuildContext context, double width) {
    final colors = Theme.of(context).colorScheme;
    final layout = HikariBreakpoints.classify(width);
    final posterWidth = switch (layout) {
      HikariWidthClass.compact => 132.0,
      HikariWidthClass.medium => 144.0,
      HikariWidthClass.expanded || HikariWidthClass.wide => 156.0,
    };

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
          child: Container(
            width: 140,
            height: 20,
            decoration: ShapeDecoration(
              color: colors.surfaceContainerHighest,
              shape: HikariRadius.pill,
            ),
          ),
        ),
        const SizedBox(height: HikariSpacing.sm),
        SizedBox(
          height: mediaPosterShelfHeight(context, posterWidth),
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
            scrollDirection: Axis.horizontal,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: 4,
            separatorBuilder: (_, _) => const SizedBox(width: HikariSpacing.md),
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
            maxWidth: HikariBreakpoints.maxContentWidth,
          ),
          child: child,
        ),
      ),
    );
  }
}
