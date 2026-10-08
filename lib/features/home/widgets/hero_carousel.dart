import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/catalog/catalog.dart';

/// Manual cinematic carousel for catalog Featured entries.
///
/// Catalog entries are metadata only, so the hero opens catalog detail instead
/// of pretending the entry is playable/readable source media.
class HeroCarousel extends StatefulWidget {
  const HeroCarousel({
    super.key,
    required this.entries,
    required this.openDetail,
  });

  final List<CatalogEntry> entries;
  final ValueChanged<CatalogEntry> openDetail;

  @override
  State<HeroCarousel> createState() => _HeroCarouselState();
}

class _HeroCarouselState extends State<HeroCarousel> {
  late final PageController _pageController;
  int _currentPage = 0;

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
  }

  @override
  void didUpdateWidget(HeroCarousel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_sameEntries(oldWidget.entries, widget.entries)) return;

    _currentPage = 0;
    if (_pageController.hasClients) {
      _pageController.jumpToPage(0);
    }
  }

  bool _sameEntries(List<CatalogEntry> before, List<CatalogEntry> after) {
    if (before.length != after.length) return false;
    for (var index = 0; index < before.length; index++) {
      if (before[index].id != after[index].id) return false;
    }
    return true;
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  Future<void> _goToPage(int page) async {
    if (page < 0 || page >= widget.entries.length || page == _currentPage) {
      return;
    }
    final duration = HikariMotion.duration(context, HikariMotion.standard);
    if (duration == Duration.zero) {
      _pageController.jumpToPage(page);
    } else {
      await _pageController.animateToPage(
        page,
        duration: duration,
        curve: Easing.standard,
      );
    }
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) =>
        _buildCarousel(context, constraints.maxWidth),
  );

  Widget _buildCarousel(BuildContext context, double width) {
    if (widget.entries.isEmpty) return const SizedBox.shrink();
    final isCompact =
        HikariBreakpoints.classify(width) == HikariWidthClass.compact;
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final horizontalPadding = isCompact ? HikariSpacing.lg : HikariSpacing.xl;
    final minimumHeight = _scaledSlideMinimumHeight(
      context,
      math.max(1.0, width - 2 * horizontalPadding),
      isCompact,
    );

    return Padding(
      padding: EdgeInsets.symmetric(horizontal: horizontalPadding),
      child: ConstrainedBox(
        constraints: BoxConstraints(minHeight: minimumHeight),
        child: AspectRatio(
          aspectRatio: isCompact ? (16 / 10) : (16 / 7),
          child: Material(
            color: colors.surfaceContainer,
            shape: RoundedRectangleBorder(
              borderRadius: HikariRadius.borderLg,
              side: BorderSide(
                color: colors.outlineVariant.withValues(alpha: 0.6),
                width: HikariRadius.borderWidth,
              ),
            ),
            clipBehavior: Clip.antiAlias,
            child: Stack(
              fit: StackFit.expand,
              children: [
                PageView.builder(
                  clipBehavior: Clip.none,
                  controller: _pageController,
                  itemCount: widget.entries.length,
                  onPageChanged: (page) => setState(() => _currentPage = page),
                  itemBuilder: (context, index) => _HeroSlide(
                    entry: widget.entries[index],
                    position: index + 1,
                    total: widget.entries.length,
                    isCompact: isCompact,
                    openDetail: widget.openDetail,
                  ),
                ),
                if (widget.entries.length > 1) ...[
                  Positioned(
                    top: HikariSpacing.md,
                    right: isCompact ? 0 : HikariSpacing.lg,
                    left: isCompact ? 0 : null,
                    child: Align(
                      alignment: isCompact
                          ? Alignment.topCenter
                          : Alignment.topRight,
                      child: ExcludeSemantics(
                        child: _PageCounter(
                          current: _currentPage + 1,
                          total: widget.entries.length,
                        ),
                      ),
                    ),
                  ),
                  Positioned(
                    left: HikariSpacing.md,
                    top: 0,
                    bottom: 0,
                    width: HikariSize.touchTarget,
                    child: Align(
                      alignment: isCompact
                          ? Alignment.topCenter
                          : Alignment.center,
                      child: IconButton.filledTonal(
                        tooltip: 'Previous featured item',
                        style: IconButton.styleFrom(
                          backgroundColor: colors.surfaceContainerHighest
                              .withValues(alpha: 0.72),
                          foregroundColor: colors.onSurface,
                        ),
                        onPressed: _currentPage > 0
                            ? () => _goToPage(_currentPage - 1)
                            : null,
                        icon: const Icon(Icons.chevron_left_rounded),
                      ),
                    ),
                  ),
                  Positioned(
                    right: HikariSpacing.md,
                    top: 0,
                    bottom: 0,
                    width: HikariSize.touchTarget,
                    child: Align(
                      alignment: isCompact
                          ? Alignment.topCenter
                          : Alignment.center,
                      child: IconButton.filledTonal(
                        tooltip: 'Next featured item',
                        style: IconButton.styleFrom(
                          backgroundColor: colors.surfaceContainerHighest
                              .withValues(alpha: 0.72),
                          foregroundColor: colors.onSurface,
                        ),
                        onPressed: _currentPage < widget.entries.length - 1
                            ? () => _goToPage(_currentPage + 1)
                            : null,
                        icon: const Icon(Icons.chevron_right_rounded),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  double _scaledSlideMinimumHeight(
    BuildContext context,
    double cardWidth,
    bool isCompact,
  ) {
    final scaler = MediaQuery.textScalerOf(context);
    // Preserve the original artwork geometry at normal text sizes.
    if (scaler.scale(12) <= 15) return 0;

    final textTheme = Theme.of(context).textTheme;
    final titleStyle = isCompact
        ? textTheme.headlineSmall
        : textTheme.headlineMedium;
    final contentWidth = math.max(
      1.0,
      math.min(620.0, cardWidth - 2 * HikariSpacing.lg),
    );

    double measure(String value, TextStyle? style, int lines) {
      final painter = TextPainter(
        text: TextSpan(text: value, style: style),
        textDirection: Directionality.of(context),
        textScaler: scaler,
        maxLines: lines,
        ellipsis: '…',
      )..layout(maxWidth: contentWidth);
      final height = painter.height;
      painter.dispose();
      return height;
    }

    var maxTitle = 0.0;
    var maxMetadata = 0.0;
    for (final entry in widget.entries) {
      maxTitle = math.max(maxTitle, measure(entry.title, titleStyle, 2));
      final metadata = [
        mediaTypeLabel(entry.type),
        ...entry.genres.take(2),
      ].join(' · ');
      maxMetadata = math.max(
        maxMetadata,
        measure(metadata, textTheme.bodySmall, 1),
      );
    }
    final badge =
        measure('Featured', textTheme.labelSmall, 1) + 2 * HikariSpacing.xs;
    final button = math.max(
      HikariSize.touchTarget,
      measure('View details', textTheme.labelLarge, 1) + 2 * HikariSpacing.md,
    );
    // Keep the header paging controls separate from the bottom CTA at high scale.
    return 60 +
        badge +
        HikariSpacing.sm +
        maxTitle +
        HikariSpacing.xs +
        maxMetadata +
        HikariSpacing.md +
        button +
        HikariSpacing.lg;
  }
}

class _HeroSlide extends StatelessWidget {
  const _HeroSlide({
    required this.entry,
    required this.position,
    required this.total,
    required this.isCompact,
    required this.openDetail,
  });

  final CatalogEntry entry;
  final int position;
  final int total;
  final bool isCompact;
  final ValueChanged<CatalogEntry> openDetail;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final typeLabel = mediaTypeLabel(entry.type);
    final metadata = [typeLabel, ...entry.genres.take(2)].join(' · ');
    final artworkUrl = entry.bannerUrl?.isNotEmpty == true
        ? entry.bannerUrl
        : entry.coverUrl;

    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: 'Item $position of $total',
      child: Stack(
        fit: StackFit.expand,
        children: [
          _buildFallbackArtwork(colors),
          if (artworkUrl != null && artworkUrl.isNotEmpty)
            Image.network(
              artworkUrl,
              fit: BoxFit.cover,
              alignment: Alignment.center,
              filterQuality: FilterQuality.medium,
              excludeFromSemantics: true,
              errorBuilder: (_, _, _) => const SizedBox.shrink(),
            ),
          DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  colors.surface.withValues(alpha: 0.35),
                  Colors.transparent,
                ],
                stops: const [0.0, 0.25],
              ),
            ),
          ),
          DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Colors.transparent,
                  colors.surface.withValues(alpha: 0.2),
                  colors.surface.withValues(alpha: 0.75),
                  colors.surfaceContainer,
                ],
                stops: const [0.0, 0.35, 0.72, 1.0],
              ),
            ),
          ),
          if (!isCompact)
            DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.centerLeft,
                  end: Alignment.centerRight,
                  colors: [
                    colors.surface,
                    colors.surface.withValues(alpha: 0.75),
                    Colors.transparent,
                  ],
                  stops: const [0.0, 0.45, 0.82],
                ),
              ),
            ),
          Positioned(
            left: HikariSpacing.lg,
            right: HikariSpacing.lg,
            bottom: HikariSpacing.lg,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 620),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  DecoratedBox(
                    decoration: ShapeDecoration(
                      color: colors.primaryContainer.withValues(alpha: 0.9),
                      shape: HikariRadius.pill,
                    ),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: HikariSpacing.md,
                        vertical: HikariSpacing.xs,
                      ),
                      child: Text(
                        'Featured',
                        style: (theme.textTheme.labelSmall ?? const TextStyle())
                            .copyWith(
                              color: colors.onPrimaryContainer,
                              fontWeight: FontWeight.w700,
                              letterSpacing: 0.8,
                            ),
                      ),
                    ),
                  ),
                  const SizedBox(height: HikariSpacing.sm),
                  Text(
                    entry.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style:
                        (isCompact
                                ? theme.textTheme.headlineSmall
                                : theme.textTheme.headlineMedium)
                            ?.copyWith(
                              color: colors.onSurface,
                              fontWeight: FontWeight.w800,
                              shadows: [
                                Shadow(
                                  color: Colors.black.withValues(alpha: 0.6),
                                  blurRadius: 12,
                                ),
                              ],
                            ) ??
                        const TextStyle(
                          color: Colors.white,
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                        ),
                  ),
                  const SizedBox(height: HikariSpacing.xs),
                  Text(
                    metadata,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: (theme.textTheme.bodySmall ?? const TextStyle())
                        .copyWith(
                          color: colors.onSurfaceVariant,
                          fontWeight: FontWeight.w600,
                        ),
                  ),
                  const SizedBox(height: HikariSpacing.md),
                  FilledButton.icon(
                    onPressed: () => openDetail(entry),
                    icon: const Icon(Icons.info_outline_rounded, size: 18),
                    label: const Text('View details'),
                    style: FilledButton.styleFrom(
                      shape: HikariRadius.pill,
                      padding: const EdgeInsets.symmetric(
                        horizontal: HikariSpacing.lg,
                        vertical: HikariSpacing.md,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFallbackArtwork(ColorScheme colors) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: RadialGradient(
          center: const Alignment(0.4, -0.3),
          radius: 1.2,
          colors: [
            colors.primaryContainer.withValues(alpha: 0.4),
            colors.surfaceContainerHigh,
            colors.surfaceContainer,
            colors.surface,
          ],
          stops: const [0.0, 0.4, 0.75, 1.0],
        ),
      ),
      child: Stack(
        children: [
          Positioned(
            top: 20,
            right: 40,
            child: Icon(
              Icons.auto_awesome,
              size: 120,
              color: colors.primary.withValues(alpha: 0.12),
            ),
          ),
        ],
      ),
    );
  }
}

class _PageCounter extends StatelessWidget {
  const _PageCounter({required this.current, required this.total});

  final int current;
  final int total;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    return DecoratedBox(
      decoration: ShapeDecoration(
        color: colors.surfaceContainerHighest.withValues(alpha: 0.75),
        shape: HikariRadius.pill,
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: HikariSpacing.md,
          vertical: HikariSpacing.xs,
        ),
        child: Text(
          '$current / $total',
          style: (theme.textTheme.labelSmall ?? const TextStyle()).copyWith(
            color: colors.onSurface,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    );
  }
}

class HeroCarouselSkeleton extends StatelessWidget {
  const HeroCarouselSkeleton({super.key});

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) =>
        _buildSkeleton(context, constraints.maxWidth),
  );

  Widget _buildSkeleton(BuildContext context, double width) {
    final isCompact =
        HikariBreakpoints.classify(width) == HikariWidthClass.compact;
    final colors = Theme.of(context).colorScheme;

    return Padding(
      padding: EdgeInsets.symmetric(
        horizontal: isCompact ? HikariSpacing.lg : HikariSpacing.xl,
      ),
      child: AspectRatio(
        aspectRatio: isCompact ? (16 / 10) : (16 / 7),
        child: DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: HikariRadius.borderLg,
            border: Border.all(
              color: colors.outlineVariant.withValues(alpha: 0.6),
              width: HikariRadius.borderWidth,
            ),
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                colors.surfaceContainerHighest,
                colors.surfaceContainer,
                colors.surface,
              ],
            ),
          ),
        ),
      ),
    );
  }
}
