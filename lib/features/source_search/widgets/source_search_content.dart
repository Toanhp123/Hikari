import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/components/hikari_search_bar.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
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
    required this.onSelectFilter,
    required this.onRetry,
    required this.openMedia,
    this.library,
    this.scopedSourceName,
    this.catalogScopeLabel,
    this.fixedMediaType,
  });

  final TextEditingController controller;
  final SourceSearchUiState state;
  final ValueChanged<String> onQueryChanged;
  final ValueChanged<SourceSearchFilter> onSelectFilter;
  final VoidCallback onRetry;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  final String? scopedSourceName;
  final String? catalogScopeLabel;
  final MediaType? fixedMediaType;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
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
                initialQuery: state.query,
                onChanged: onQueryChanged,
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
              style: TextStyle(color: colors.warning, fontSize: 12),
            ),
          ),
        Expanded(
          child: _SourceSearchResults(
            state: state,
            onRetry: onRetry,
            openMedia: openMedia,
            library: library,
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
    final colors = context.hikariColors;
    return Semantics(
      label: 'Searching in $sourceName',
      child: Row(
        children: [
          Icon(Icons.extension_outlined, size: 16, color: colors.textMuted),
          const SizedBox(width: HikariSpacing.xs),
          Expanded(
            child: Text(
              'Searching in $sourceName',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: HikariTypography.caption.copyWith(
                color: colors.textSecondary,
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
    required this.scopedSourceName,
    required this.catalogScopeLabel,
  });

  final SourceSearchUiState state;
  final VoidCallback onRetry;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  final String? scopedSourceName;
  final String? catalogScopeLabel;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final scope = catalogScopeLabel ?? scopedSourceName;
    final status = switch (state.status) {
      SourceSearchStatus.idle => AsyncViewStatus.empty,
      SourceSearchStatus.loading => AsyncViewStatus.loading,
      SourceSearchStatus.ready => AsyncViewStatus.content,
      SourceSearchStatus.empty => AsyncViewStatus.empty,
      SourceSearchStatus.error => AsyncViewStatus.error,
    };

    return AsyncStateView(
      status: status,
      emptyTitle: state.query.isEmpty ? 'Find a source' : 'No results found',
      emptyMessage: state.query.isEmpty
          ? 'Type a title above to search your configured media sources.'
          : scope == null
          ? 'No matches found for "${state.query}".'
          : 'No matches found for "${state.query}" in $scope.',
      emptyIcon: state.query.isEmpty
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
              style: TextStyle(color: colors.textMuted),
            ),
          );
        }
        return _SourceSearchGrid(
          results: state.results,
          openMedia: openMedia,
          library: library,
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
  });

  final List<SourceSearchResult> results;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return GridView.builder(
      padding: const EdgeInsets.all(HikariSpacing.lg),
      physics: const BouncingScrollPhysics(),
      gridDelegate: mediaPosterGridDelegate,
      itemCount: results.length,
      itemBuilder: (context, index) {
        final result = results[index];
        final media = result.media;
        final authors = result.metadata?.authors ?? const <String>[];
        final subtitle = [
          result.sourceName ?? 'Local media',
          if (authors.isNotEmpty) authors.first,
        ].join(' · ');

        return Stack(
          fit: StackFit.expand,
          children: [
            MediaPoster(
              title: media.title,
              subtitle: subtitle,
              badgeText: mediaTypeBadgeLabel(media.type),
              badgeColor: mediaTypeBadgeColor(colors, media.type),
              onTap: () => openMedia(context, media),
            ),
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
