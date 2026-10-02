import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

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
      duration: HikariMotion.normal,
      curve: HikariMotion.curveStandard,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (widget.entries.isEmpty) return const SizedBox.shrink();

    final isCompact = context.isCompact;

    return Padding(
      padding: EdgeInsets.symmetric(
        horizontal: isCompact ? 0 : HikariSpacing.lg,
      ),
      child: AspectRatio(
        aspectRatio: isCompact ? (16 / 10) : (16 / 7),
        child: ClipRRect(
          borderRadius: isCompact ? BorderRadius.zero : HikariRadius.borderLg,
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
                  top: HikariSpacing.md,
                  right: HikariSpacing.lg,
                  child: ExcludeSemantics(
                    child: _PageCounter(
                      current: _currentPage + 1,
                      total: widget.entries.length,
                    ),
                  ),
                ),
                if (!isCompact) ...[
                  Positioned(
                    left: HikariSpacing.md,
                    top: 0,
                    bottom: 0,
                    child: Center(
                      child: HikariIconButton(
                        tooltip: 'Previous featured item',
                        variant: HikariIconButtonVariant.glass,
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
                    child: Center(
                      child: HikariIconButton(
                        tooltip: 'Next featured item',
                        variant: HikariIconButtonVariant.glass,
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
    final colors = context.hikariColors;
    final isCompact = context.isCompact;
    final typeLabel = switch (entry.type) {
      MediaType.anime => 'Anime',
      MediaType.manga => 'Manga',
      MediaType.lightNovel => 'Light novel',
    };
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
                  colors.background.withValues(alpha: 0.18),
                  colors.scrimMedium,
                  colors.scrimStrong,
                ],
                stops: const [0.0, 0.38, 0.74, 1.0],
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
                    colors.scrimStrong,
                    colors.scrimMedium,
                    Colors.transparent,
                  ],
                  stops: const [0.0, 0.46, 0.82],
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
                    decoration: BoxDecoration(
                      color: colors.glassSurface,
                      borderRadius: HikariRadius.borderCapsule,
                      border: Border.all(color: colors.glassBorder),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: HikariSpacing.sm,
                        vertical: HikariSpacing.xs,
                      ),
                      child: Text(
                        'Featured',
                        style: HikariTypography.labelSmall.copyWith(
                          color: Colors.white,
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
                                ? HikariTypography.headline
                                : HikariTypography.display)
                            .copyWith(
                              color: Colors.white,
                              fontWeight: FontWeight.w800,
                              shadows: [
                                Shadow(
                                  color: colors.scrimStrong,
                                  blurRadius: 12,
                                ),
                              ],
                            ),
                  ),
                  const SizedBox(height: HikariSpacing.xs),
                  Text(
                    metadata,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: HikariTypography.labelMedium.copyWith(
                      color: Colors.white.withValues(alpha: 0.84),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: HikariSpacing.md),
                  HikariButton(
                    label: 'View details',
                    icon: const Icon(Icons.info_outline_rounded, size: 18),
                    size: HikariButtonSize.medium,
                    onPressed: () => openDetail(entry),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFallbackArtwork(HikariColors colors) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: RadialGradient(
          center: const Alignment(0.4, -0.3),
          radius: 1.2,
          colors: [
            colors.primary.withValues(alpha: 0.34),
            colors.surfaceElevated,
            colors.surfaceContainer,
            colors.background,
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
              color: colors.primaryGlow.withValues(alpha: 0.08),
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
    final colors = context.hikariColors;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.glassSurface,
        borderRadius: HikariRadius.borderCapsule,
        border: Border.all(color: colors.glassBorder),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: HikariSpacing.sm,
          vertical: HikariSpacing.xs,
        ),
        child: Text(
          '$current / $total',
          style: HikariTypography.labelSmall.copyWith(
            color: colors.textPrimary,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    );
  }
}
