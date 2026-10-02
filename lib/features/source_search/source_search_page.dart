import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/components/hikari_search_bar.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_button.dart';
import 'package:hikari/features/source_search/source_search_view_model.dart';

/// Source Search renders state owned by [SourceSearchViewModel].
class SourceSearchPage extends StatefulWidget {
  const SourceSearchPage({
    super.key,
    required this.openMedia,
    this.searchManga,
    this.searchNovels,
    this.scanLocalMedia,
    this.library,
    this.initialQuery = '',
    this.initialFilter = SourceSearchFilter.all,
  });

  final void Function(BuildContext, Media) openMedia;
  final SearchManga? searchManga;
  final SearchNovels? searchNovels;
  final Future<List<Media>?> Function()? scanLocalMedia;
  final LibraryRepository? library;
  final String initialQuery;
  final SourceSearchFilter initialFilter;

  @override
  State<SourceSearchPage> createState() => _SourceSearchPageState();
}

class _SourceSearchPageState extends State<SourceSearchPage> {
  late final TextEditingController _searchController;
  late final SourceSearchViewModel _model;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController(text: widget.initialQuery);
    _model = SourceSearchViewModel(
      searchManga: widget.searchManga,
      searchNovels: widget.searchNovels,
      scanLocalMedia: widget.scanLocalMedia,
      initialQuery: widget.initialQuery,
      initialFilter: widget.initialFilter,
    );
    if (widget.initialQuery.trim().isNotEmpty) {
      _model.search(widget.initialQuery);
    }
  }

  @override
  void dispose() {
    _searchController.dispose();
    _model.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;

    return ListenableBuilder(
      listenable: _model,
      builder: (context, _) {
        final state = _model.state;
        return HikariScaffold(
          useSafeArea: true,
          body: Column(
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
                      controller: _searchController,
                      initialQuery: state.query,
                      onChanged: _model.search,
                    ),
                    const SizedBox(height: HikariSpacing.sm),
                    SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: Row(
                        children: SourceSearchFilter.values.map((filter) {
                          return Padding(
                            padding: const EdgeInsets.only(
                              right: HikariSpacing.sm,
                            ),
                            child: HikariChip(
                              label: filter.mediaType == null
                                  ? 'All'
                                  : mediaTypeFilterLabel(filter.mediaType!),
                              isSelected: state.filter == filter,
                              onTap: () =>
                                  unawaited(_model.selectFilter(filter)),
                            ),
                          );
                        }).toList(),
                      ),
                    ),
                  ],
                ),
              ),
              if (state.failedSourceCount > 0 &&
                  state.status == SourceSearchStatus.ready)
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
              Expanded(child: _buildBody(context, state, colors)),
            ],
          ),
        );
      },
    );
  }

  Widget _buildBody(
    BuildContext context,
    SourceSearchUiState state,
    HikariColors colors,
  ) {
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
          : 'No matches found for "${state.query}".',
      emptyIcon: state.query.isEmpty
          ? Icons.search_rounded
          : Icons.search_off_rounded,
      errorMessage: 'All configured search sources failed. Try again.',
      onRetry: _model.retry,
      contentBuilder: (_) {
        final results = state.results;
        if (results.isEmpty) {
          return Center(
            child: Text(
              'No matches for the selected media type.',
              style: TextStyle(color: colors.textMuted),
            ),
          );
        }
        return _buildResultsGrid(context, results, colors);
      },
    );
  }

  Widget _buildResultsGrid(
    BuildContext context,
    List<SourceSearchResult> results,
    HikariColors colors,
  ) {
    return GridView.builder(
      padding: const EdgeInsets.all(HikariSpacing.lg),
      physics: const BouncingScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
        maxCrossAxisExtent: HikariBreakpoints.posterGridMaxExtent,
        crossAxisSpacing: HikariSpacing.md,
        mainAxisSpacing: HikariSpacing.md,
        childAspectRatio: 2 / 3,
      ),
      itemCount: results.length,
      itemBuilder: (context, index) {
        final result = results[index];
        final media = result.media;
        final badgeColor = mediaTypeBadgeColor(colors, media.type);
        final badgeText = mediaTypeBadgeLabel(media.type);
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
              badgeText: badgeText,
              badgeColor: badgeColor,
              onTap: () => widget.openMedia(context, media),
            ),
            if (widget.library != null)
              Positioned(
                top: HikariSpacing.xs,
                right: HikariSpacing.xs,
                child: LibraryButton(repository: widget.library!, media: media),
              ),
          ],
        );
      },
    );
  }
}
