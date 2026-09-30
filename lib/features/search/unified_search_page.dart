import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/components/hikari_search_bar.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_button.dart';
import 'package:hikari/features/search/unified_search_view_model.dart';

/// Unified Search renders state owned by [UnifiedSearchViewModel].
class UnifiedSearchPage extends StatefulWidget {
  const UnifiedSearchPage({
    super.key,
    required this.openMedia,
    this.searchManga,
    this.searchNovels,
    this.scanLocalMedia,
    this.library,
    this.initialQuery = '',
  });

  final void Function(BuildContext, Media) openMedia;
  final SearchManga? searchManga;
  final SearchNovels? searchNovels;
  final Future<List<Media>?> Function()? scanLocalMedia;
  final LibraryRepository? library;
  final String initialQuery;

  @override
  State<UnifiedSearchPage> createState() => _UnifiedSearchPageState();
}

class _UnifiedSearchPageState extends State<UnifiedSearchPage> {
  late final TextEditingController _searchController;
  late final UnifiedSearchViewModel _model;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController(text: widget.initialQuery);
    _model = UnifiedSearchViewModel(
      searchManga: widget.searchManga,
      searchNovels: widget.searchNovels,
      scanLocalMedia: widget.scanLocalMedia,
      initialQuery: widget.initialQuery,
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
                        children: SearchMediaTypeFilter.values.map((filter) {
                          return Padding(
                            padding: const EdgeInsets.only(
                              right: HikariSpacing.sm,
                            ),
                            child: HikariChip(
                              label: filter.label,
                              isSelected: state.filter == filter,
                              onTap: () => _model.selectFilter(filter),
                            ),
                          );
                        }).toList(),
                      ),
                    ),
                  ],
                ),
              ),
              if (state.failedSourceCount > 0 &&
                  state.status == UnifiedSearchStatus.ready)
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
    UnifiedSearchUiState state,
    HikariColors colors,
  ) {
    final status = switch (state.status) {
      UnifiedSearchStatus.idle => AsyncViewStatus.empty,
      UnifiedSearchStatus.loading => AsyncViewStatus.loading,
      UnifiedSearchStatus.ready => AsyncViewStatus.content,
      UnifiedSearchStatus.empty => AsyncViewStatus.empty,
      UnifiedSearchStatus.error => AsyncViewStatus.error,
    };

    return AsyncStateView(
      status: status,
      emptyTitle: state.query.isEmpty ? 'Explore & Search' : 'No results found',
      emptyMessage: state.query.isEmpty
          ? 'Type a title above to search your configured media sources.'
          : 'No matches found for "${state.query}".',
      emptyIcon: state.query.isEmpty
          ? Icons.search_rounded
          : Icons.search_off_rounded,
      errorMessage: state.errorMessage,
      onRetry: _model.retry,
      contentBuilder: (_) {
        final results = state.visibleResults;
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
    List<UnifiedSearchResult> results,
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
        final badgeColor = switch (media.type) {
          MediaType.anime => colors.badgeVideo,
          MediaType.manga => colors.badgeManga,
          MediaType.lightNovel => colors.badgeNovel,
        };
        final badgeText = switch (media.type) {
          MediaType.anime => 'ANIME',
          MediaType.manga => 'MANGA',
          MediaType.lightNovel => 'NOVEL',
        };
        final authors = result.metadata?.authors ?? const <String>[];
        final subtitle = [
          result.sourceName,
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
