import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/features/home/widgets/hero_carousel_slide.dart';

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
                  itemBuilder: (context, index) => HeroCarouselSlide(
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
