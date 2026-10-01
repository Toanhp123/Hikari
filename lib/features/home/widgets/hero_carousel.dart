import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/domain/media/media.dart';

class FeaturedHeroItem {
  const FeaturedHeroItem({
    required this.media,
    required this.tagline,
    required this.genres,
    this.bannerUrl,
    this.primaryActionLabel,
    this.secondaryActionLabel,
  });

  final Media media;
  final String tagline;
  final List<String> genres;
  final String? bannerUrl;
  final String? primaryActionLabel;
  final String? secondaryActionLabel;
}

/// Manual cinematic hero for real featured content.
///
/// Mobile uses swipe navigation; medium and expanded layouts also expose
/// explicit previous/next controls. The carousel never auto-advances.
class HeroCarousel extends StatefulWidget {
  const HeroCarousel({
    super.key,
    required this.items,
    required this.onOpenMedia,
    this.onOpenDetails,
  });

  final List<FeaturedHeroItem> items;
  final void Function(BuildContext, Media) onOpenMedia;
  final void Function(BuildContext, Media)? onOpenDetails;

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
    if (page < 0 || page >= widget.items.length || page == _currentPage) return;
    await _pageController.animateToPage(
      page,
      duration: HikariMotion.normal,
      curve: HikariMotion.curveStandard,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (widget.items.isEmpty) return const SizedBox.shrink();

    final isCompact = context.isCompact;

    return Padding(
      padding: EdgeInsets.symmetric(
        horizontal: isCompact ? 0 : HikariSpacing.lg,
      ),
      child: AspectRatio(
        aspectRatio: isCompact ? (16 / 11) : (16 / 7),
        child: ClipRRect(
          borderRadius: isCompact ? BorderRadius.zero : HikariRadius.borderLg,
          child: Stack(
            fit: StackFit.expand,
            children: [
              PageView.builder(
                controller: _pageController,
                itemCount: widget.items.length,
                onPageChanged: (page) => setState(() => _currentPage = page),
                itemBuilder: (context, index) => _HeroSlide(
                  item: widget.items[index],
                  onOpenMedia: widget.onOpenMedia,
                  onOpenDetails: widget.onOpenDetails,
                ),
              ),
              if (widget.items.length > 1) ...[
                Positioned(
                  top: HikariSpacing.md,
                  right: HikariSpacing.lg,
                  child: _PageCounter(
                    current: _currentPage + 1,
                    total: widget.items.length,
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
                        onPressed: _currentPage < widget.items.length - 1
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
    required this.item,
    required this.onOpenMedia,
    required this.onOpenDetails,
  });

  final FeaturedHeroItem item;
  final void Function(BuildContext, Media) onOpenMedia;
  final void Function(BuildContext, Media)? onOpenDetails;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final typeLabel = switch (item.media.type) {
      MediaType.anime => 'Anime',
      MediaType.manga => 'Manga',
      MediaType.lightNovel => 'Light novel',
    };
    final metadata = [typeLabel, ...item.genres.take(2)].join(' · ');
    final bannerUrl = item.bannerUrl;

    return Semantics(
      container: true,
      label: 'Featured: ${item.media.title}',
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (bannerUrl != null && bannerUrl.isNotEmpty)
            Image.network(
              bannerUrl,
              fit: BoxFit.cover,
              filterQuality: FilterQuality.medium,
              errorBuilder: (_, _, _) => _buildFallbackArtwork(colors),
            )
          else
            _buildFallbackArtwork(colors),
          DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  const Color(0x24000000),
                  colors.background.withValues(alpha: 0.24),
                  colors.scrimStrong,
                  colors.background,
                ],
                stops: const [0.0, 0.35, 0.76, 1.0],
              ),
            ),
          ),
          Positioned(
            left: HikariSpacing.lg,
            right: HikariSpacing.lg,
            bottom: HikariSpacing.lg,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 580),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    metadata,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: HikariTypography.labelMedium.copyWith(
                      color: Colors.white.withValues(alpha: 0.82),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: HikariSpacing.xs),
                  Text(
                    item.media.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: HikariTypography.headline.copyWith(
                      color: Colors.white,
                      fontWeight: FontWeight.w800,
                      shadows: const [
                        Shadow(color: Colors.black54, blurRadius: 12),
                      ],
                    ),
                  ),
                  if (item.tagline.isNotEmpty) ...[
                    const SizedBox(height: HikariSpacing.xs),
                    Text(
                      item.tagline,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: HikariTypography.bodySmall.copyWith(
                        color: Colors.white.withValues(alpha: 0.78),
                        height: 1.4,
                      ),
                    ),
                  ],
                  const SizedBox(height: HikariSpacing.md),
                  Wrap(
                    spacing: HikariSpacing.sm,
                    runSpacing: HikariSpacing.sm,
                    children: [
                      HikariButton(
                        label:
                            item.primaryActionLabel ??
                            (item.media.type == MediaType.anime
                                ? 'Watch Now'
                                : 'Read Now'),
                        icon: Icon(
                          item.media.type == MediaType.anime
                              ? Icons.play_arrow_rounded
                              : Icons.menu_book_rounded,
                          size: 18,
                        ),
                        size: HikariButtonSize.small,
                        onPressed: () => onOpenMedia(context, item.media),
                      ),
                      if (onOpenDetails != null)
                        HikariButton(
                          label: item.secondaryActionLabel ?? 'Details',
                          icon: const Icon(
                            Icons.info_outline_rounded,
                            size: 17,
                          ),
                          variant: HikariButtonVariant.secondary,
                          size: HikariButtonSize.small,
                          onPressed: () => onOpenDetails!(context, item.media),
                        ),
                    ],
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
            const Color(0xFF1B1438),
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
