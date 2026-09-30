import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
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

/// A 16:9 cinematic hero banner with ambient glow and call-to-action buttons.
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

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    if (widget.items.isEmpty) return const SizedBox.shrink();

    final isCompact = context.isCompact;

    return AspectRatio(
      aspectRatio: isCompact ? (16 / 11) : (16 / 7),
      child: Stack(
        fit: StackFit.expand,
        children: [
          PageView.builder(
            controller: _pageController,
            itemCount: widget.items.length,
            onPageChanged: (i) => setState(() => _currentPage = i),
            itemBuilder: (context, index) {
              final item = widget.items[index];
              return Stack(
                fit: StackFit.expand,
                children: [
                  // Fallback dark celestial gradient banner
                  if (item.bannerUrl != null && item.bannerUrl!.isNotEmpty)
                    Image.network(
                      item.bannerUrl!,
                      fit: BoxFit.cover,
                      errorBuilder: (context, error, stackTrace) =>
                          _buildCelestialGradient(colors),
                    )
                  else
                    _buildCelestialGradient(colors),

                  // Bottom and Left Scrim Gradient fading into deep obsidian
                  Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          const Color(0x33000000),
                          colors.background.withValues(alpha: 0.4),
                          colors.scrimStrong,
                          colors.background,
                        ],
                        stops: [0.0, 0.35, 0.75, 1.0],
                      ),
                    ),
                  ),

                  // Text and Actions Overlay
                  Positioned(
                    left: HikariSpacing.lg,
                    right: HikariSpacing.lg,
                    bottom: HikariSpacing.md,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        // Wordmark
                        Text(
                          'H I K A R I',
                          style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w800,
                            letterSpacing: 6,
                            color: Colors.white.withValues(alpha: 0.9),
                            shadows: [
                              Shadow(
                                color: Colors.black.withValues(alpha: 0.8),
                                blurRadius: 10,
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 4),

                        // Title
                        Text(
                          item.media.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: HikariTypography.headline.copyWith(
                            fontSize: 22,
                            fontWeight: FontWeight.w800,
                            color: Colors.white,
                            letterSpacing: -0.2,
                            shadows: [
                              Shadow(
                                color: Colors.black.withValues(alpha: 0.9),
                                blurRadius: 12,
                              ),
                            ],
                          ),
                        ),

                        // Tagline / Synopsis
                        if (item.tagline.isNotEmpty) ...[
                          const SizedBox(height: 3),
                          Text(
                            item.tagline,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 12,
                              height: 1.35,
                              color: colors.textSecondary,
                            ),
                          ),
                        ],
                        const SizedBox(height: HikariSpacing.sm),

                        // CTA Buttons matching Mock 1
                        Row(
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
                              onPressed: () =>
                                  widget.onOpenMedia(context, item.media),
                            ),
                            const SizedBox(width: HikariSpacing.sm),
                            HikariButton(
                              label: item.secondaryActionLabel ?? 'My List',
                              variant: HikariButtonVariant.secondary,
                              size: HikariButtonSize.small,
                              onPressed: () {
                                if (widget.onOpenDetails != null) {
                                  widget.onOpenDetails!(context, item.media);
                                } else {
                                  widget.onOpenMedia(context, item.media);
                                }
                              },
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              );
            },
          ),

          // Page Indicator Dots
          if (widget.items.length > 1)
            Positioned(
              right: HikariSpacing.lg,
              bottom: HikariSpacing.lg,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: List.generate(widget.items.length, (i) {
                  final active = i == _currentPage;
                  return AnimatedContainer(
                    duration: HikariMotion.fast,
                    margin: const EdgeInsets.symmetric(horizontal: 3),
                    width: active ? 16 : 6,
                    height: 6,
                    decoration: BoxDecoration(
                      color: active
                          ? colors.primaryGlow
                          : colors.surfaceHighlight,
                      borderRadius: HikariRadius.borderFull,
                    ),
                  );
                }),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildCelestialGradient(HikariColors colors) {
    return Container(
      decoration: BoxDecoration(
        gradient: RadialGradient(
          center: const Alignment(0.4, -0.3),
          radius: 1.2,
          colors: [
            colors.primary.withValues(alpha: 0.35),
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
