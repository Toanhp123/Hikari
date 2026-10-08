import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/components/hikari_search_bar.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/widgets/library_button.dart';
import 'package:hikari/features/source_search/source_search_view_model.dart';

class SourceSearchContent extends StatelessWidget {
  const SourceSearchContent({
    super.key,
    required this.controller,
    required this.state,
    required this.onQueryChanged,
    required this.onSubmitted,
    required this.onSelectFilter,
    required this.onRetry,
    required this.openMedia,
    this.library,
    this.readArtwork,
    this.scopedSourceName,
    this.catalogScopeLabel,
    this.fixedMediaType,
  });

  final TextEditingController controller;
  final SourceSearchUiState state;
  final ValueChanged<String> onQueryChanged;
  final ValueChanged<String> onSubmitted;
  final ValueChanged<SourceSearchFilter> onSelectFilter;
  final VoidCallback onRetry;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  final Future<Uint8List?> Function(SourceMediaRef artwork)? readArtwork;
  final String? scopedSourceName;
  final String? catalogScopeLabel;
  final MediaType? fixedMediaType;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(
            HikariSpacing.lg,
            HikariSpacing.md,
            HikariSpacing.lg,
            HikariSpacing.sm,
          ),
          child: Column(
            children: [
              HikariSearchBar(
                controller: controller,
                initialQuery: state.inputQuery,
                onChanged: onQueryChanged,
                onSubmitted: onSubmitted,
              ),
              const SizedBox(height: HikariSpacing.sm),
              if (catalogScopeLabel != null)
                _ScopedSourceLabel(sourceName: catalogScopeLabel!)
              else if (scopedSourceName != null)
                _ScopedSourceLabel(sourceName: scopedSourceName!)
              else if (fixedMediaType == null)
                HikariChipRow(
                  children: [
                    for (final filter in SourceSearchFilter.values)
                      HikariChip(
                        label: filter.mediaType == null
                            ? 'All'
                            : mediaTypeFilterLabel(filter.mediaType!),
                        isSelected: state.filter == filter,
                        onTap: () => onSelectFilter(filter),
                      ),
                  ],
                ),
            ],
          ),
        ),
        if (state.failedSourceCount > 0 &&
            (state.status == SourceSearchStatus.ready ||
                state.status == SourceSearchStatus.empty))
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: HikariSpacing.lg,
              vertical: HikariSpacing.xs,
            ),
            child: Text(
              'Some configured sources could not be searched.',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: context.hikariStatusColors.onWarningContainer,
              ),
            ),
          ),
        if (state.status == SourceSearchStatus.loading &&
            state.results.isNotEmpty)
          const LinearProgressIndicator(minHeight: 2),
        Expanded(
          child: _SourceSearchResults(
            state: state,
            onRetry: onRetry,
            openMedia: openMedia,
            library: library,
            readArtwork: readArtwork,
            scopedSourceName: scopedSourceName,
            catalogScopeLabel: catalogScopeLabel,
          ),
        ),
      ],
    );
  }
}

class _ScopedSourceLabel extends StatelessWidget {
  const _ScopedSourceLabel({required this.sourceName});

  final String sourceName;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Semantics(
      label: 'Searching in $sourceName',
      child: Row(
        children: [
          Icon(
            Icons.extension_outlined,
            size: 16,
            color: colors.onSurfaceVariant,
          ),
          const SizedBox(width: HikariSpacing.xs),
          Expanded(
            child: Text(
              'Searching in $sourceName',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall!.copyWith(
                color: colors.onSurfaceVariant,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SourceSearchResults extends StatelessWidget {
  const _SourceSearchResults({
    required this.state,
    required this.onRetry,
    required this.openMedia,
    required this.library,
    required this.readArtwork,
    required this.scopedSourceName,
    required this.catalogScopeLabel,
  });

  final SourceSearchUiState state;
  final VoidCallback onRetry;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  final Future<Uint8List?> Function(SourceMediaRef artwork)? readArtwork;
  final String? scopedSourceName;
  final String? catalogScopeLabel;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final scope = catalogScopeLabel ?? scopedSourceName;
    final status = switch (state.status) {
      SourceSearchStatus.idle => AsyncViewStatus.empty,
      SourceSearchStatus.loading =>
        state.results.isEmpty
            ? AsyncViewStatus.loading
            : AsyncViewStatus.content,
      SourceSearchStatus.ready => AsyncViewStatus.content,
      SourceSearchStatus.empty => AsyncViewStatus.empty,
      SourceSearchStatus.error => AsyncViewStatus.error,
    };

    return AsyncStateView(
      status: status,
      emptyTitle: state.status == SourceSearchStatus.idle
          ? 'Find a source'
          : 'No results found',
      emptyMessage: state.status == SourceSearchStatus.idle
          ? 'Enter a title and press Search to search your configured sources.'
          : scope == null
          ? 'No matches found for "${state.query}".'
          : 'No matches found for "${state.query}" in $scope.',
      emptyIcon: state.status == SourceSearchStatus.idle
          ? Icons.search_rounded
          : Icons.search_off_rounded,
      errorMessage: scope == null
          ? 'All configured search sources failed. Try again.'
          : 'All search sources in $scope failed. Try again.',
      onRetry: onRetry,
      contentBuilder: (_) {
        if (state.results.isEmpty) {
          return Center(
            child: Text(
              'No matches for the selected media type.',
              style: Theme.of(context).textTheme.bodyMedium
                  ?.copyWith(color: colors.onSurfaceVariant),
            ),
          );
        }
        return _SourceSearchGrid(
          results: state.results,
          openMedia: openMedia,
          library: library,
          readArtwork: readArtwork,
        );
      },
    );
  }
}

class _SourceSearchGrid extends StatelessWidget {
  const _SourceSearchGrid({
    required this.results,
    required this.openMedia,
    required this.library,
    required this.readArtwork,
  });

  final List<SourceSearchResult> results;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  final Future<Uint8List?> Function(SourceMediaRef artwork)? readArtwork;

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      padding: const EdgeInsets.all(HikariSpacing.lg),
      physics: const BouncingScrollPhysics(),
      gridDelegate: mediaPosterGridDelegate,
      itemCount: results.length,
      itemBuilder: (context, index) {
        final result = results[index];
        final media = result.media;
        final badge = mediaTypeBadgeColors(context, media.type);
        final authors = result.metadata?.authors ?? const <String>[];
        final subtitle = [
          result.sourceName ?? 'Local media',
          if (authors.isNotEmpty) authors.first,
        ].join(' · ');

        Widget poster(Uint8List? imageBytes, {bool loading = false}) =>
            MediaPoster(
              title: media.title,
              imageBytes: imageBytes,
              isLoading: loading,
              subtitle: subtitle,
              badgeText: mediaTypeBadgeLabel(media.type),
              badgeColor: badge.background,
              badgeForegroundColor: badge.foreground,
              onTap: () => openMedia(context, media),
            );

        final cover = result.metadata?.cover;
        final artwork = cover != null && readArtwork != null
            ? SourceArtwork(
                key: ValueKey(cover),
                resource: cover,
                read: readArtwork!,
                builder: (context, bytes, loading) =>
                    poster(bytes, loading: loading),
              )
            : poster(null);

        return Stack(
          fit: StackFit.expand,
          children: [
            artwork,
            if (library != null)
              Positioned(
                top: HikariSpacing.xs,
                right: HikariSpacing.xs,
                child: LibraryButton(repository: library!, media: media),
              ),
          ],
        );
      },
    );
  }
}
