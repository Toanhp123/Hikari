import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/hikari_ui.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/continue_reading_item.dart';

/// Maps saved continuation identity and progress into a lazy landscape shelf.
class ContinueShelf extends StatelessWidget {
  const ContinueShelf({
    super.key,
    required this.items,
    required this.onOpenMedia,
    this.artworkByMedia = const {},
    this.readArtwork,
    this.onContinue,
    this.onSeeAll,
  });

  final List<ContinueReadingItem> items;
  final Map<SourceMediaRef, SourceMediaRef> artworkByMedia;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;
  final void Function(BuildContext, Media) onOpenMedia;
  final void Function(BuildContext, ContinueReadingItem)? onContinue;
  final VoidCallback? onSeeAll;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) return const SizedBox.shrink();
    return HikariHorizontalShelf(
      itemCount: items.length,
      itemKeyBuilder: (index) => ValueKey(items[index].media.source),
      itemExtent: 208,
      itemHeightBuilder: (context, width) => hikariCardExtent(
        context,
        width: width,
        hasSubtitle: true,
        aspectRatio: HikariAspectRatios.landscape,
      ),
      header: HikariSectionHeader(
        title: 'Continue',
        description: 'Pick up where you left off',
        actions: [
          if (onSeeAll != null)
            TextButton(onPressed: onSeeAll, child: const Text('Library')),
        ],
      ),
      itemBuilder: (context, index) {
        final item = items[index];
        final resource = artworkByMedia[item.media.source];
        final colors = mediaTypeBadgeColors(context, item.media.type);
        final icon = switch (item.media.type) {
          MediaType.anime => Icons.play_arrow_rounded,
          MediaType.manga => Icons.auto_stories_rounded,
          MediaType.lightNovel => Icons.menu_book_rounded,
        };
        void open() => onContinue != null
            ? onContinue!(context, item)
            : onOpenMedia(context, item.media);
        final label = _progressLabel(item);
        Widget card(Uint8List? bytes) => Semantics(
          button: true,
          label: 'Continue ${item.media.title}, $label',
          onTap: open,
          child: ExcludeSemantics(
            child: HikariLandscapeCard(
              title: item.media.title,
              subtitle: label,
              progress: item.progress,
              onTap: open,
              badge: Text(
                mediaTypeBadgeLabel(item.media.type),
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: colors.foreground,
                  backgroundColor: colors.background,
                ),
              ),
              artwork: HikariArtwork(
                image: bytes == null ? null : MemoryImage(bytes),
                placeholder: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      colors: [
                        colors.background,
                        Theme.of(context).colorScheme.surfaceContainer,
                      ],
                    ),
                  ),
                  child: Center(child: Icon(icon, color: colors.foreground)),
                ),
              ),
            ),
          ),
        );
        return resource != null && readArtwork != null
            ? SourceArtwork(
                key: ValueKey(resource),
                resource: resource,
                read: readArtwork!,
                builder: (_, bytes, _) => card(bytes),
              )
            : card(null);
      },
    );
  }
}

String _progressLabel(ContinueReadingItem item) {
  if (item.chapter != null) {
    if (item.position case PagePosition(:final pageIndex, :final pageCount)) {
      return item.completed
          ? 'Chapter completed · Page ${pageIndex + 1} of $pageCount'
          : 'Last read chapter · Page ${pageIndex + 1} of $pageCount';
    }
    return item.completed ? 'Chapter completed' : 'Last read chapter';
  }
  return switch (item.position) {
    VideoPosition() => 'Resume video',
    PagePosition(:final pageIndex, :final pageCount) =>
      'Page ${pageIndex + 1} of $pageCount',
    TextPosition(:final progression) => '${(progression * 100).round()}% read',
    DocumentPosition() => 'Resume reading',
    _ => 'Resume',
  };
}
