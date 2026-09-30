import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';

class MediaChapterItem {
  const MediaChapterItem({
    required this.id,
    required this.title,
    this.scanlator,
    this.date,
    this.isRead = false,
    this.duration,
  });

  final String id;
  final String title;
  final String? scanlator;
  final String? date;
  final bool isRead;
  final String? duration;
}

/// A unified details screen for Anime, Manga, and Light Novels with a cinematic
/// parallax backdrop, Hero poster, expandable synopsis, and chapter/episode list.
class MediaDetailsPage extends StatefulWidget {
  const MediaDetailsPage({
    super.key,
    required this.media,
    required this.openMedia,
    this.library,
    this.heroTag,
    this.synopsis,
    this.author,
    this.year,
    this.rating,
    this.imageBytes,
    this.imageUrl,
    this.chapters = const [],
    this.onSelectChapter,
  });

  final Media media;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  final String? heroTag;
  final String? synopsis;
  final String? author;
  final String? year;
  final double? rating;
  final Uint8List? imageBytes;
  final String? imageUrl;
  final List<MediaChapterItem> chapters;
  final void Function(BuildContext, MediaChapterItem)? onSelectChapter;

  @override
  State<MediaDetailsPage> createState() => _MediaDetailsPageState();
}

class _MediaDetailsPageState extends State<MediaDetailsPage> {
  bool _isInLibrary = false;
  bool _isSynopsisExpanded = false;
  bool _isLoadingLibrary = true;

  @override
  void initState() {
    super.initState();
    _checkLibraryStatus();
  }

  Future<void> _checkLibraryStatus() async {
    if (widget.library == null) {
      if (mounted) setState(() => _isLoadingLibrary = false);
      return;
    }
    final inLib = await widget.library!.contains(widget.media.source);
    if (mounted) {
      setState(() {
        _isInLibrary = inLib;
        _isLoadingLibrary = false;
      });
    }
  }

  Future<void> _toggleLibrary() async {
    if (widget.library == null) return;
    if (_isInLibrary) {
      await widget.library!.remove(widget.media.source);
      if (mounted) setState(() => _isInLibrary = false);
    } else {
      await widget.library!.upsert(
        LibraryEntry(media: widget.media, addedAt: DateTime.now().toUtc()),
      );
      if (mounted) setState(() => _isInLibrary = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final isAnime = widget.media.type == MediaType.anime;
    final isManga = widget.media.type == MediaType.manga;

    final badgeColor = isAnime
        ? colors.badgeVideo
        : isManga
        ? colors.badgeManga
        : colors.badgeNovel;
    final badgeText = isAnime
        ? 'ANIME'
        : isManga
        ? 'MANGA'
        : 'NOVEL';

    return HikariScaffold(
      useSafeArea: false,
      body: CustomScrollView(
        physics: const BouncingScrollPhysics(),
        slivers: [
          // Collapsible Parallax Backdrop & Navigation Bar
          SliverAppBar(
            expandedHeight: 280,
            pinned: true,
            stretch: true,
            backgroundColor: colors.surface,
            elevation: 0,
            leading: Padding(
              padding: const EdgeInsets.all(8.0),
              child: HikariIconButton(
                icon: const Icon(Icons.arrow_back_rounded),
                variant: HikariIconButtonVariant.glass,
                size: 40,
                onPressed: () => Navigator.of(context).pop(),
              ),
            ),
            actions: [
              if (widget.library != null)
                Padding(
                  padding: const EdgeInsets.only(right: 8.0),
                  child: HikariIconButton(
                    icon: Icon(
                      _isInLibrary
                          ? Icons.bookmark_added_rounded
                          : Icons.bookmark_add_outlined,
                      color: _isInLibrary ? colors.primaryGlow : null,
                    ),
                    variant: HikariIconButtonVariant.glass,
                    size: 40,
                    tooltip: _isInLibrary
                        ? 'Remove from library'
                        : 'Add to library',
                    onPressed: _isLoadingLibrary ? null : _toggleLibrary,
                  ),
                ),
            ],
            flexibleSpace: FlexibleSpaceBar(
              collapseMode: CollapseMode.parallax,
              background: Stack(
                fit: StackFit.expand,
                children: [
                  // Backdrop gradient
                  Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topRight,
                        end: Alignment.bottomLeft,
                        colors: [
                          badgeColor.withValues(alpha: 0.25),
                          colors.surfaceElevated,
                          colors.background,
                        ],
                      ),
                    ),
                  ),

                  // Bottom scrim
                  Container(
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          Colors.transparent,
                          Color(0x990B0F17),
                          Color(0xFF0B0F17),
                        ],
                        stops: [0.4, 0.75, 1.0],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Header Content (Poster + Metadata + Actions)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Hero Poster
                      SizedBox(
                        width: 110,
                        child: MediaPoster(
                          title: widget.media.title,
                          heroTag: widget.heroTag ?? widget.media.title,
                          imageBytes: widget.imageBytes,
                          imageUrl: widget.imageUrl,
                          badgeText: badgeText,
                          badgeColor: badgeColor,
                        ),
                      ),
                      const SizedBox(width: HikariSpacing.md),

                      // Metadata column
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              widget.media.title,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: HikariTypography.titleLarge.copyWith(
                                color: colors.textPrimary,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: HikariSpacing.xs),
                            if (widget.author != null &&
                                widget.author!.isNotEmpty)
                              Text(
                                widget.author!,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: HikariTypography.bodyMedium.copyWith(
                                  color: colors.textSecondary,
                                ),
                              ),
                            const SizedBox(height: HikariSpacing.xs),

                            // Year & Star Rating
                            Row(
                              children: [
                                if (widget.year != null) ...[
                                  Text(
                                    widget.year!,
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: colors.textMuted,
                                    ),
                                  ),
                                  const SizedBox(width: HikariSpacing.sm),
                                ],
                                if (widget.rating != null) ...[
                                  const Icon(
                                    Icons.star_rounded,
                                    size: 16,
                                    color: Color(0xFFF59E0B),
                                  ),
                                  const SizedBox(width: 2),
                                  Text(
                                    widget.rating!.toStringAsFixed(1),
                                    style: const TextStyle(
                                      fontSize: 12,
                                      fontWeight: FontWeight.w600,
                                      color: Color(0xFFF59E0B),
                                    ),
                                  ),
                                ],
                              ],
                            ),
                            const SizedBox(height: HikariSpacing.md),

                            // Main Play/Read CTA Button
                            HikariButton(
                              label: isAnime
                                  ? 'Start Watching'
                                  : 'Start Reading',
                              icon: Icon(
                                isAnime
                                    ? Icons.play_arrow_rounded
                                    : Icons.menu_book_rounded,
                                size: 18,
                              ),
                              size: HikariButtonSize.small,
                              onPressed: () =>
                                  widget.openMedia(context, widget.media),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),

                  // Expandable Synopsis
                  if (widget.synopsis != null &&
                      widget.synopsis!.isNotEmpty) ...[
                    const SizedBox(height: HikariSpacing.lg),
                    Text(
                      'Synopsis',
                      style: HikariTypography.titleSmall.copyWith(
                        color: colors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: HikariSpacing.xs),
                    GestureDetector(
                      onTap: () => setState(
                        () => _isSynopsisExpanded = !_isSynopsisExpanded,
                      ),
                      child: Text(
                        widget.synopsis!,
                        maxLines: _isSynopsisExpanded ? null : 3,
                        overflow: _isSynopsisExpanded
                            ? TextOverflow.visible
                            : TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 13,
                          height: 1.45,
                          color: colors.textSecondary,
                        ),
                      ),
                    ),
                  ],

                  const SizedBox(height: HikariSpacing.xl),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        isAnime ? 'Episodes' : 'Chapters',
                        style: HikariTypography.titleMedium.copyWith(
                          color: colors.textPrimary,
                        ),
                      ),
                      if (widget.chapters.isNotEmpty)
                        Text(
                          '${widget.chapters.length} ${isAnime ? 'episodes' : 'chapters'}',
                          style: TextStyle(
                            fontSize: 12,
                            color: colors.textMuted,
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: HikariSpacing.sm),
                ],
              ),
            ),
          ),

          // Episodes or Chapters List
          if (widget.chapters.isNotEmpty)
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate((context, index) {
                  final ch = widget.chapters[index];
                  return Padding(
                    padding: const EdgeInsets.only(bottom: HikariSpacing.xs),
                    child: Material(
                      color: colors.surfaceContainer,
                      shape: RoundedRectangleBorder(
                        borderRadius: HikariRadius.borderSm,
                        side: BorderSide(color: colors.border),
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: ListTile(
                        dense: true,
                        title: Text(
                          ch.title,
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: ch.isRead
                                ? FontWeight.w400
                                : FontWeight.w600,
                            color: ch.isRead
                                ? colors.textMuted
                                : colors.textPrimary,
                          ),
                        ),
                        subtitle: ch.scanlator != null || ch.date != null
                            ? Text(
                                [
                                  if (ch.scanlator != null) ch.scanlator!,
                                  if (ch.date != null) ch.date!,
                                  if (ch.duration != null) ch.duration!,
                                ].join(' · '),
                                style: TextStyle(
                                  fontSize: 11,
                                  color: colors.textMuted,
                                ),
                              )
                            : null,
                        trailing: Icon(
                          isAnime
                              ? Icons.play_circle_outline_rounded
                              : Icons.arrow_forward_ios_rounded,
                          size: 16,
                          color: colors.textSecondary,
                        ),
                        onTap: () {
                          if (widget.onSelectChapter != null) {
                            widget.onSelectChapter!(context, ch);
                          } else {
                            widget.openMedia(context, widget.media);
                          }
                        },
                      ),
                    ),
                  );
                }, childCount: widget.chapters.length),
              ),
            )
          else
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.all(HikariSpacing.xl),
                child: Center(
                  child: Text(
                    'No chapters available.',
                    style: TextStyle(color: colors.textMuted, fontSize: 13),
                  ),
                ),
              ),
            ),

          const SliverToBoxAdapter(child: SizedBox(height: HikariSpacing.xxl)),
        ],
      ),
    );
  }
}
