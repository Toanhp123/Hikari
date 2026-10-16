import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_artwork_decode.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_picker_list_surface.dart';

class CatalogSourceCandidateList extends StatelessWidget {
  const CatalogSourceCandidateList({
    super.key,
    required this.source,
    required this.candidates,
    required this.onSelect,
    required this.onChooseAnother,
    required this.onManualSearch,
    required this.readArtwork,
  });

  final CatalogSourcePickerSource source;
  final List<CatalogSourcePickerCandidate> candidates;
  final ValueChanged<CatalogSourcePickerCandidate> onSelect;
  final VoidCallback onChooseAnother;
  final VoidCallback onManualSearch;
  final Future<Uint8List?> Function(SourceMediaRef artwork)? readArtwork;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return ListView.builder(
      shrinkWrap: candidates.length <= 8,
      itemCount: candidates.length + 1,
      itemBuilder: (context, index) {
        if (index == 0) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Choose matching title',
                style: Theme.of(context).textTheme.titleMedium!,
              ),
              const SizedBox(height: HikariSpacing.xs),
              Text(
                '${source.contextLabel} · ${candidates.length} possible matches',
                style: Theme.of(context).textTheme.bodyMedium!
                    .copyWith(color: colors.onSurfaceVariant, height: 1.45),
              ),
              const SizedBox(height: HikariSpacing.sm),
              Wrap(
                spacing: HikariSpacing.sm,
                children: [
                  OutlinedButton(
                    onPressed: onManualSearch,
                    child: const Text('Search manually'),
                  ),
                  TextButton(
                    onPressed: onChooseAnother,
                    child: const Text('Change source'),
                  ),
                ],
              ),
              const SizedBox(height: HikariSpacing.md),
            ],
          );
        }
        final candidate = candidates[index - 1];
        return CatalogSourcePickerListSurface(
          index: index - 1,
          count: candidates.length,
          child: _CandidateTile(
            candidate: candidate,
            readArtwork: readArtwork,
            onTap: () => onSelect(candidate),
          ),
        );
      },
    );
  }
}

class _CandidateTile extends StatelessWidget {
  const _CandidateTile({
    required this.candidate,
    required this.onTap,
    required this.readArtwork,
  });

  final CatalogSourcePickerCandidate candidate;
  final VoidCallback onTap;
  final Future<Uint8List?> Function(SourceMediaRef artwork)? readArtwork;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final authors = candidate.metadata?.authors ?? const <String>[];
    return Semantics(
      button: true,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(HikariSpacing.md),
          child: Row(
            children: [
              _CandidateArtwork(candidate: candidate, readArtwork: readArtwork),
              const SizedBox(width: HikariSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      candidate.media.title,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyLarge!
                          .copyWith(fontWeight: FontWeight.w600),
                    ),
                    if (authors.isNotEmpty)
                      Text(
                        authors.first,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodySmall!
                            .copyWith(color: colors.onSurfaceVariant),
                      ),
                  ],
                ),
              ),
              const SizedBox(width: HikariSpacing.xs),
              Icon(Icons.chevron_right_rounded, color: colors.onSurfaceVariant),
            ],
          ),
        ),
      ),
    );
  }
}

class _CandidateArtwork extends StatelessWidget {
  const _CandidateArtwork({required this.candidate, required this.readArtwork});

  final CatalogSourcePickerCandidate candidate;
  final Future<Uint8List?> Function(SourceMediaRef artwork)? readArtwork;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final artwork = candidate.metadata?.cover;
    Widget content(Uint8List? bytes, {bool loading = false}) {
      if (bytes != null) {
        return ClipRRect(
          borderRadius: HikariRadius.borderXs,
          child: Image.memory(
            bytes,
            fit: BoxFit.cover,
            cacheWidth: mediaArtworkCacheWidth(context, 50),
            errorBuilder: (_, _, _) => _placeholder(colors),
          ),
        );
      }
      return _placeholder(colors, loading: loading);
    }

    final image = artwork != null && readArtwork != null
        ? SourceArtwork(
            key: ValueKey(artwork),
            resource: artwork,
            read: readArtwork!,
            builder: (context, bytes, loading) =>
                content(bytes, loading: loading),
          )
        : content(null);

    return SizedBox(width: 50, height: 70, child: image);
  }

  Widget _placeholder(ColorScheme colors, {bool loading = false}) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.surfaceContainerHigh,
        borderRadius: HikariRadius.borderXs,
        border: Border.all(color: colors.outline),
      ),
      child: Center(
        child: loading
            ? const SizedBox.square(
                dimension: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : Icon(
                candidate.media.type == MediaType.lightNovel
                    ? Icons.auto_stories_outlined
                    : Icons.menu_book_outlined,
                color: colors.primary.withValues(alpha: 0.8),
              ),
      ),
    );
  }
}
