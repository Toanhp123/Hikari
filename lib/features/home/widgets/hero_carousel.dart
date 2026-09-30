import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/domain/media/media.dart';

class FeaturedHeroItem {
  const FeaturedHeroItem({
    required this.media,
    required this.tagline,
    required this.genres,
    this.bannerUrl,
  });

  final Media media;
  final String tagline;
  final List<String> genres;
  final String? bannerUrl;
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

    return AspectRatio(
      aspectRatio: 16 / 9,
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
                  // Fallback dark gradient banner
                  Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topRight,
                        end: Alignment.bottomLeft,
                        colors: [
                          colors.primary.withValues(alpha: 0.3),
                          colors.surfaceContainer,
                          colors.background,
                        ],
                      ),
                    ),
                  ),

                  // Bottom and Left Scrim Gradient
                  Container(
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          Colors.transparent,
                          Color(0x800B0F17),
                          Color(0xFA0B0F17),
                        ],
                        stops: [0.3, 0.7, 1.0],
                      ),
                    ),
                  ),

                  // Text and Actions
                  Positioned(
                    left: HikariSpacing.lg,
                    right: HikariSpacing.lg,
                    bottom: HikariSpacing.md,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        // Genre pills
                        if (item.genres.isNotEmpty)
                          Wrap(
                            spacing: HikariSpacing.xs,
                            children: item.genres.take(3).map((g) {
                              return HikariChip(label: g);
                            }).toList(),
                          ),
                        const SizedBox(height: HikariSpacing.xs),

                        // Title
                        Text(
                          item.media.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: HikariTypography.headline.copyWith(
                            color: colors.textPrimary,
                            shadows: [
                              Shadow(
                                color: Colors.black.withValues(alpha: 0.8),
                                blurRadius: 8,
                              ),
                            ],
                          ),
                        ),

                        // Tagline
                        if (item.tagline.isNotEmpty) ...[
                          const SizedBox(height: 2),
                          Text(
                            item.tagline,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: HikariTypography.bodySmall.copyWith(
                              color: colors.textSecondary,
                            ),
                          ),
                        ],
                        const SizedBox(height: HikariSpacing.sm),

                        // Buttons
                        Row(
                          children: [
                            HikariButton(
                              label: item.media.type == MediaType.anime
                                  ? 'Watch Now'
                                  : 'Read Now',
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
                            if (widget.onOpenDetails != null) ...[
                              const SizedBox(width: HikariSpacing.sm),
                              HikariButton(
                                label: 'Details',
                                variant: HikariButtonVariant.secondary,
                                size: HikariButtonSize.small,
                                onPressed: () =>
                                    widget.onOpenDetails!(context, item.media),
                              ),
                            ],
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
}
