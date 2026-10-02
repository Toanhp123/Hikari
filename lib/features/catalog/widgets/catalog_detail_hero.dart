import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_detail_labels.dart';

class CatalogDetailHero extends StatelessWidget {
  const CatalogDetailHero({
    super.key,
    required this.entry,
    required this.details,
    required this.openSourceSearch,
  });

  final CatalogEntry entry;
  final CatalogEntryDetails? details;
  final ValueChanged<CatalogEntry> openSourceSearch;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final imageUrl = (entry.bannerUrl?.isNotEmpty ?? false)
        ? entry.bannerUrl
        : entry.coverUrl;
    final actionLabel = entry.type == MediaType.anime ? 'Watch' : 'Read';
    final metadata = <String>[
      catalogMediaTypeLabel(entry.type),
      if (details?.year != null) '${details!.year}',
      if (details?.format != null) catalogFormatLabel(details!.format!),
    ];

    return Stack(
      fit: StackFit.expand,
      children: [
        _HeroArtwork(imageUrl: imageUrl),
        DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                colors.background.withValues(alpha: 0.04),
                colors.scrimMedium,
                colors.scrimStrong,
                colors.background,
              ],
              stops: const [0, 0.44, 0.76, 1],
            ),
          ),
        ),
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
              stops: const [0, 0.5, 0.9],
            ),
          ),
        ),
        SafeArea(
          bottom: false,
          child: Align(
            alignment: Alignment.bottomCenter,
            child: ConstrainedBox(
              constraints: const BoxConstraints(
                maxWidth: HikariBreakpoints.maxContentWidth,
              ),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(
                  HikariSpacing.lg,
                  72,
                  HikariSpacing.lg,
                  HikariSpacing.xl,
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    SizedBox(
                      width: context.isCompact ? 104 : 132,
                      child: AspectRatio(
                        aspectRatio: 2 / 3,
                        child: _CoverArtwork(
                          imageUrl: entry.coverUrl,
                          label: entry.title,
                        ),
                      ),
                    ),
                    const SizedBox(width: HikariSpacing.lg),
                    Expanded(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _TypePill(type: entry.type),
                          const SizedBox(height: HikariSpacing.sm),
                          Text(
                            entry.title,
                            maxLines: context.isCompact ? 3 : 2,
                            overflow: TextOverflow.ellipsis,
                            style:
                                (context.isCompact
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
                          if (metadata.isNotEmpty) ...[
                            const SizedBox(height: HikariSpacing.xs),
                            Text(
                              metadata.join(' · '),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: HikariTypography.labelMedium.copyWith(
                                color: Colors.white.withValues(alpha: 0.82),
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                          if (details?.averageScore != null) ...[
                            const SizedBox(height: HikariSpacing.xs),
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(
                                  Icons.star_rounded,
                                  size: 17,
                                  color: Color(0xFFFACC15),
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  '${details!.averageScore}%',
                                  style: HikariTypography.labelLarge.copyWith(
                                    color: Colors.white,
                                  ),
                                ),
                              ],
                            ),
                          ],
                          const SizedBox(height: HikariSpacing.md),
                          Semantics(
                            button: true,
                            label: '$actionLabel ${entry.title}',
                            child: HikariButton(
                              label: actionLabel,
                              icon: Icon(
                                entry.type == MediaType.anime
                                    ? Icons.play_arrow_rounded
                                    : Icons.menu_book_rounded,
                                size: 19,
                              ),
                              onPressed: () => openSourceSearch(entry),
                            ),
                          ),
                          const SizedBox(height: HikariSpacing.xs),
                          Text(
                            'Choose a source to continue',
                            style: HikariTypography.caption.copyWith(
                              color: Colors.white.withValues(alpha: 0.72),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _HeroArtwork extends StatelessWidget {
  const _HeroArtwork({required this.imageUrl});

  final String? imageUrl;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final url = imageUrl;
    if (url == null || url.isEmpty) return _fallback(colors);
    return Image.network(
      url,
      fit: BoxFit.cover,
      alignment: Alignment.center,
      excludeFromSemantics: true,
      errorBuilder: (_, _, _) => _fallback(colors),
      loadingBuilder: (_, child, progress) =>
          progress == null ? child : _fallback(colors),
    );
  }

  Widget _fallback(HikariColors colors) => DecoratedBox(
    decoration: BoxDecoration(
      gradient: RadialGradient(
        center: const Alignment(0.4, -0.35),
        radius: 1.15,
        colors: [
          colors.primary.withValues(alpha: 0.34),
          colors.surfaceElevated,
          colors.surfaceContainer,
          colors.background,
        ],
        stops: const [0, 0.38, 0.72, 1],
      ),
    ),
  );
}

class _CoverArtwork extends StatelessWidget {
  const _CoverArtwork({required this.imageUrl, required this.label});

  final String? imageUrl;
  final String label;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final url = imageUrl;
    final artwork = url == null || url.isEmpty
        ? _fallback(colors)
        : Image.network(
            url,
            fit: BoxFit.cover,
            excludeFromSemantics: true,
            errorBuilder: (_, _, _) => _fallback(colors),
            loadingBuilder: (_, image, progress) =>
                progress == null ? image : _fallback(colors),
          );

    return Semantics(
      image: true,
      label: '$label cover',
      child: DecoratedBox(
        decoration: BoxDecoration(
          borderRadius: HikariRadius.borderMd,
          border: Border.all(color: colors.glassBorder),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.42),
              blurRadius: 18,
              offset: const Offset(0, 8),
            ),
          ],
        ),
        child: ClipRRect(borderRadius: HikariRadius.borderMd, child: artwork),
      ),
    );
  }

  Widget _fallback(HikariColors colors) => DecoratedBox(
    decoration: BoxDecoration(
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [
          colors.surfaceHighlight,
          colors.surfaceElevated,
          colors.surfaceContainer,
        ],
      ),
    ),
    child: Center(
      child: Icon(
        Icons.movie_filter_rounded,
        size: 30,
        color: colors.primaryGlow.withValues(alpha: 0.8),
      ),
    ),
  );
}

class _TypePill extends StatelessWidget {
  const _TypePill({required this.type});

  final MediaType type;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final color = mediaTypeBadgeColor(colors, type);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.18),
        borderRadius: HikariRadius.borderCapsule,
        border: Border.all(color: color.withValues(alpha: 0.48)),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        child: Text(
          catalogMediaTypeLabel(type),
          style: HikariTypography.labelSmall.copyWith(
            color: Colors.white,
            fontWeight: FontWeight.w800,
            letterSpacing: 0.6,
          ),
        ),
      ),
    );
  }
}
