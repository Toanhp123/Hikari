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
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';

enum SearchMediaTypeFilter {
  all('All'),
  anime('Anime'),
  manga('Manga'),
  novel('Light Novels');

  const SearchMediaTypeFilter(this.label);
  final String label;
}

/// Unified Search Screen supporting debounced querying across Manga, Novel, and Local media sources.
class UnifiedSearchPage extends StatefulWidget {
  const UnifiedSearchPage({
    super.key,
    required this.openMedia,
    this.searchManga,
    this.searchNovels,
    this.scanLocalMedia,
    this.library,
    this.onOpenDetails,
    this.initialQuery = '',
  });

  final void Function(BuildContext, Media) openMedia;
  final SearchManga? searchManga;
  final SearchNovels? searchNovels;
  final Future<List<Media>?> Function()? scanLocalMedia;
  final LibraryRepository? library;
  final void Function(BuildContext, Media)? onOpenDetails;
  final String initialQuery;

  @override
  State<UnifiedSearchPage> createState() => _UnifiedSearchPageState();
}

class _UnifiedSearchPageState extends State<UnifiedSearchPage> {
  String _currentQuery = '';
  SearchMediaTypeFilter _typeFilter = SearchMediaTypeFilter.all;
  AsyncViewStatus _status = AsyncViewStatus.empty;
  String? _errorMessage;
  List<Media> _results = [];
  int _searchRequestId = 0;

  @override
  void initState() {
    super.initState();
    _currentQuery = widget.initialQuery;
    if (_currentQuery.trim().isNotEmpty) {
      _executeSearch(_currentQuery);
    }
  }

  Future<void> _executeSearch(String query) async {
    final trimmed = query.trim();
    _currentQuery = trimmed;
    if (trimmed.isEmpty) {
      setState(() {
        _status = AsyncViewStatus.empty;
        _results = [];
      });
      return;
    }

    final requestId = ++_searchRequestId;
    setState(() {
      _status = AsyncViewStatus.loading;
      _errorMessage = null;
    });

    try {
      final combined = <Media>[];

      // 1. Search Manga if enabled
      if (widget.searchManga != null &&
          widget.searchManga!.options.isNotEmpty &&
          (_typeFilter == SearchMediaTypeFilter.all ||
              _typeFilter == SearchMediaTypeFilter.manga)) {
        try {
          final firstSource = widget.searchManga!.options.first;
          final mangaPage = await widget.searchManga!.execute(
            sourceId: firstSource.id,
            query: trimmed,
          );
          combined.addAll(mangaPage.results.map((p) => p.media));
        } catch (_) {
          // Keep searching other sources even if one fails
        }
      }

      // 2. Search Novels if enabled
      if (widget.searchNovels != null &&
          widget.searchNovels!.options.isNotEmpty &&
          (_typeFilter == SearchMediaTypeFilter.all ||
              _typeFilter == SearchMediaTypeFilter.novel)) {
        try {
          final firstNovelSource = widget.searchNovels!.options.first;
          final novelPage = await widget.searchNovels!.execute(
            sourceId: firstNovelSource.id,
            query: trimmed,
          );
          combined.addAll(novelPage.results.map((p) => p.media));
        } catch (_) {
          // Keep searching other sources
        }
      }

      // 3. Search Local Media if enabled
      if (widget.scanLocalMedia != null &&
          (_typeFilter == SearchMediaTypeFilter.all ||
              _typeFilter == SearchMediaTypeFilter.anime)) {
        try {
          final localItems = await widget.scanLocalMedia!();
          if (localItems != null) {
            final filtered = localItems.where(
              (m) => m.title.toLowerCase().contains(trimmed.toLowerCase()),
            );
            combined.addAll(filtered);
          }
        } catch (_) {}
      }

      if (requestId != _searchRequestId || !mounted) return;

      setState(() {
        _results = combined;
        _status = combined.isEmpty
            ? AsyncViewStatus.empty
            : AsyncViewStatus.content;
      });
    } catch (e) {
      if (requestId != _searchRequestId || !mounted) return;
      setState(() {
        _errorMessage = 'Search failed: ${e.toString()}';
        _status = AsyncViewStatus.error;
      });
    }
  }

  List<Media> get _filteredResults {
    if (_typeFilter == SearchMediaTypeFilter.all) return _results;
    return _results.where((item) {
      return switch (_typeFilter) {
        SearchMediaTypeFilter.all => true,
        SearchMediaTypeFilter.anime => item.type == MediaType.anime,
        SearchMediaTypeFilter.manga => item.type == MediaType.manga,
        SearchMediaTypeFilter.novel => item.type == MediaType.lightNovel,
      };
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;

    return HikariScaffold(
      useSafeArea: true,
      body: Column(
        children: [
          // Search Bar & Filter Header
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
                  initialQuery: _currentQuery,
                  onChanged: (q) => _executeSearch(q),
                ),
                const SizedBox(height: HikariSpacing.sm),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: SearchMediaTypeFilter.values.map((f) {
                      return Padding(
                        padding: const EdgeInsets.only(right: HikariSpacing.sm),
                        child: HikariChip(
                          label: f.label,
                          isSelected: _typeFilter == f,
                          onTap: () {
                            setState(() => _typeFilter = f);
                            if (_currentQuery.isNotEmpty) {
                              _executeSearch(_currentQuery);
                            }
                          },
                        ),
                      );
                    }).toList(),
                  ),
                ),
              ],
            ),
          ),

          // Search Body / Results Grid
          Expanded(
            child: AsyncStateView(
              status: _status,
              emptyTitle: _currentQuery.isEmpty
                  ? 'Explore & Search'
                  : 'No results found',
              emptyMessage: _currentQuery.isEmpty
                  ? 'Type a title above to find anime, manga, and light novels across your sources.'
                  : 'No matches found for "$_currentQuery". Try a different title or change source filter.',
              emptyIcon: _currentQuery.isEmpty
                  ? Icons.search_rounded
                  : Icons.search_off_rounded,
              errorMessage: _errorMessage,
              onRetry: () => _executeSearch(_currentQuery),
              contentBuilder: (context) {
                final displayItems = _filteredResults;
                if (displayItems.isEmpty) {
                  return Center(
                    child: Text(
                      'No matches for "$_currentQuery" with selected filter.',
                      style: TextStyle(color: colors.textMuted),
                    ),
                  );
                }

                return GridView.builder(
                  padding: const EdgeInsets.all(HikariSpacing.lg),
                  physics: const BouncingScrollPhysics(),
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: context.responsiveGridColumns,
                    crossAxisSpacing: HikariSpacing.md,
                    mainAxisSpacing: HikariSpacing.md,
                    childAspectRatio: 2 / 3,
                  ),
                  itemCount: displayItems.length,
                  itemBuilder: (context, index) {
                    final item = displayItems[index];
                    final badgeColor = item.type == MediaType.anime
                        ? colors.badgeVideo
                        : item.type == MediaType.manga
                        ? colors.badgeManga
                        : colors.badgeNovel;
                    final badgeText = item.type == MediaType.anime
                        ? 'ANIME'
                        : item.type == MediaType.manga
                        ? 'MANGA'
                        : 'NOVEL';

                    return MediaPoster(
                      title: item.title,
                      badgeText: badgeText,
                      badgeColor: badgeColor,
                      onTap: () {
                        if (widget.onOpenDetails != null) {
                          widget.onOpenDetails!(context, item);
                        } else {
                          widget.openMedia(context, item);
                        }
                      },
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
