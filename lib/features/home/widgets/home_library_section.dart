import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/hikari_ui.dart';
import 'package:hikari/features/media/widgets/media_metadata_view.dart';
import 'package:hikari/features/media/widgets/media_type_presentation.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/home/home_view_model.dart';

/// Maps the saved Library snapshot and filter into canonical presentation.
class HomeRecentShelf extends StatelessWidget {
  const HomeRecentShelf({
    super.key,
    required this.items,
    required this.openMedia,
    this.artworkByMedia = const {},
    this.readArtwork,
    this.filters = const [],
    this.effectiveFilter,
    this.onSelectFilter,
    this.onOpenLibrary,
  });

  final List<Media> items;
  final Map<SourceMediaRef, SourceMediaRef> artworkByMedia;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;
  final void Function(BuildContext, Media) openMedia;
  final List<HomeFilterType> filters;
  final HomeFilterType? effectiveFilter;
  final ValueChanged<HomeFilterType>? onSelectFilter;
  final VoidCallback? onOpenLibrary;

  @override
  Widget build(BuildContext context) => HikariHorizontalShelf(
    itemCount: items.length,
    itemKeyBuilder: (index) => ValueKey(items[index].source),
    itemHeightBuilder: (context, width) =>
        hikariCardExtent(context, width: width),
    header: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        HikariSectionHeader(
          title: 'Recently added',
          actions: [
            if (onOpenLibrary != null)
              TextButton(
                onPressed: onOpenLibrary,
                child: const Text('Library'),
              ),
          ],
        ),
        if (filters.isNotEmpty && onSelectFilter != null)
          HikariChipRow(
            children: [
              for (final filter in filters)
                HikariFilterChip(
                  label: filter.mediaType == null
                      ? 'All'
                      : mediaTypeLabel(filter.mediaType!),
                  selected: effectiveFilter == filter,
                  onSelected: (_) => onSelectFilter!(filter),
                ),
            ],
          ),
      ],
    ),
    itemBuilder: (context, index) {
      final item = items[index];
      final resource = artworkByMedia[item.source];
      Widget poster(Uint8List? bytes) => HikariPosterCard(
        title: item.title,
        artwork: HikariArtwork(
          image: bytes == null ? null : MemoryImage(bytes),
        ),
        badge: Text(mediaTypeBadgeLabel(item.type)),
        onTap: () => openMedia(context, item),
      );
      return resource != null && readArtwork != null
          ? SourceArtwork(
              key: ValueKey(item.source),
              resource: resource,
              read: readArtwork!,
              builder: (_, bytes, _) => poster(bytes),
            )
          : poster(null);
    },
  );
}

class HomeRecentShelfSkeleton extends StatelessWidget {
  const HomeRecentShelfSkeleton({super.key});

  @override
  Widget build(BuildContext context) => Semantics(
    label: 'Loading Library',
    child: ExcludeSemantics(
      child: HikariHorizontalShelf(
        itemCount: 4,
        header: const HikariSectionHeader(title: 'Recently added'),
        itemHeightBuilder: (context, width) =>
            hikariCardExtent(context, width: width),
        itemBuilder: (_, _) =>
            const HikariPosterCard(title: '', artwork: HikariArtwork()),
      ),
    ),
  );
}

class HomeLibraryError extends StatelessWidget {
  const HomeLibraryError({super.key, required this.onRetry});
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => HikariAsyncStateView(
    status: HikariAsyncStatus.fatalError,
    message: 'Could not load your Library',
    onRetry: onRetry,
    child: const SizedBox.shrink(),
  );
}
