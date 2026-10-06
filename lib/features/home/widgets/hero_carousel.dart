import 'package:flutter/material.dart';
import 'package:hikari/app/theme/design_system/design_system.dart';
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
    await _pageController.animateToPage(
      page,
      duration: HikariDesignMotion.standard,
      curve: Easing.standard,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (widget.entries.isEmpty) return const SizedBox.shrink();

    final width = MediaQuery.sizeOf(context).width;
    final isCompact = HikariLayout.classify(width) == HikariLayoutClass.compact;
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Padding(
      padding: EdgeInsets.symmetric(
        horizontal: isCompact ? HikariSpace.content : HikariSpace.section,
      ),
      child: AspectRatio(
        aspectRatio: isCompact ? (16 / 10) : (16 / 7),
        child: ClipRRect(
          borderRadius: HikariShape.large,
          child: Stack(
            fit: StackFit.expand,
            children: [
              PageView.builder(
                controller: _pageController,
                itemCount: widget.entries.length,
                onPageChanged: (page) => setState(() => _currentPage = page),
                itemBuilder: (context, index) => _HeroSlide(
                  entry: widget.entries[index],
                  position: index + 1,
                  total: widget.entries.length,
                  openDetail: widget.openDetail,
                ),
              ),
              if (widget.entries.length > 1) ...[
                Positioned(
                  top: HikariSpace.compact,
                  right: HikariSpace.content,
                  child: ExcludeSemantics(
                    child: _PageCounter(
                      current: _currentPage + 1,
                      total: widget.entries.length,
                    ),
                  ),
                ),
                if (!isCompact) ...[
                  Positioned(
                    left: HikariSpace.compact,
                    top: 0,
                    bottom: 0,
                    child: Center(
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
                    right: HikariSpace.compact,
                    top: 0,
                    bottom: 0,
                    child: Center(
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
            ],
          ),
        ),
      ),
    );
  }
}

class _HeroSlide extends StatelessWidget {
  const _HeroSlide({
    required this.entry,
    required this.position,
    required this.total,
    required this.openDetail,
  });

  final CatalogEntry entry;
  final int position;
  final int total;
  final ValueChanged<CatalogEntry> openDetail;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final width = MediaQuery.sizeOf(context).width;
    final isCompact = HikariLayout.classify(width) == HikariLayoutClass.compact;
    final typeLabel = mediaTypeLabel(entry.type);
    final metadata = [typeLabel, ...entry.genres.take(2)].join(' · ');
    final artworkUrl = entry.bannerUrl?.isNotEmpty == true
        ? entry.bannerUrl
        : entry.coverUrl;

    return Semantics(
      container: true,
      label: 'Featured $position of $total: ${entry.title}',
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
                  Colors.transparent,
                  colors.surface.withValues(alpha: 0.2),
                  colors.surface.withValues(alpha: 0.8),
                  colors.surface,
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
            left: HikariSpace.content,
            right: HikariSpace.content,
            bottom: HikariSpace.content,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 620),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  DecoratedBox(
                    decoration: ShapeDecoration(
                      color: colors.primaryContainer.withValues(alpha: 0.9),
                      shape: HikariShape.pill,
                    ),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: HikariSpace.compact,
                        vertical: HikariSpace.micro,
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
                  const SizedBox(height: HikariSpace.inline),
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
                  const SizedBox(height: HikariSpace.micro),
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
                  const SizedBox(height: HikariSpace.compact),
                  FilledButton.icon(
                    onPressed: () => openDetail(entry),
                    icon: const Icon(Icons.info_outline_rounded, size: 18),
                    label: const Text('View details'),
                    style: FilledButton.styleFrom(
                      shape: HikariShape.pill,
                      padding: const EdgeInsets.symmetric(
                        horizontal: HikariSpace.content,
                        vertical: HikariSpace.compact,
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
        shape: HikariShape.pill,
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: HikariSpace.compact,
          vertical: HikariSpace.micro,
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
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final isCompact = HikariLayout.classify(width) == HikariLayoutClass.compact;
    final colors = Theme.of(context).colorScheme;

    return Padding(
      padding: EdgeInsets.symmetric(
        horizontal: isCompact ? HikariSpace.content : HikariSpace.section,
      ),
      child: AspectRatio(
        aspectRatio: isCompact ? (16 / 10) : (16 / 7),
        child: DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: HikariShape.large,
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
