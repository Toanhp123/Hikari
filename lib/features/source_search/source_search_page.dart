import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/features/source_search/source_search_view_model.dart';
import 'package:hikari/features/source_search/widgets/source_search_content.dart';

/// Source Search renders state owned by [SourceSearchViewModel].
class SourceSearchPage extends StatefulWidget {
  const SourceSearchPage({
    super.key,
    required this.openMedia,
    this.searchManga,
    this.searchNovels,
    this.scanLocalMedia,
    this.library,
    this.readArtwork,
    this.initialQuery = '',
    this.initialFilter = SourceSearchFilter.all,
    this.initialSourceId,
    this.sourceName,
    this.sourceIds,
    this.fixedMediaType,
    this.catalogScopeLabel,
  }) : assert((initialSourceId == null) == (sourceName == null));

  final void Function(BuildContext, Media, {MediaMetadata? previewMetadata})
  openMedia;
  final SearchManga? searchManga;
  final SearchNovels? searchNovels;
  final Future<List<Media>?> Function()? scanLocalMedia;
  final LibraryRepository? library;
  final Future<Uint8List?> Function(SourceMediaRef artwork)? readArtwork;
  final String initialQuery;
  final SourceSearchFilter initialFilter;
  final SourceId? initialSourceId;
  final String? sourceName;
  final Set<SourceId>? sourceIds;
  final MediaType? fixedMediaType;
  final String? catalogScopeLabel;

  @override
  State<SourceSearchPage> createState() => _SourceSearchPageState();
}

class _SourceSearchPageState extends State<SourceSearchPage> {
  late final TextEditingController _searchController;
  late final SourceSearchViewModel _viewModel;
  Timer? _queryDebounce;
  late String _lastText;

  void _cancelStaleEdit() {
    if (_lastText == _searchController.text) return;
    _lastText = _searchController.text;
    _queryDebounce?.cancel();
  }

  void _queryChanged(String query) {
    _queryDebounce?.cancel();
    _queryDebounce = Timer(const Duration(milliseconds: 300), () {
      _viewModel.updateQuery(query);
    });
  }

  void _clear() {
    _queryDebounce?.cancel();
    _viewModel.updateQuery('');
  }

  void _submit(String query) {
    _queryDebounce?.cancel();
    _viewModel.updateQuery(query);
    unawaited(_viewModel.submitQuery(query));
  }

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController(text: widget.initialQuery);
    _lastText = _searchController.text;
    _searchController.addListener(_cancelStaleEdit);
    _viewModel = SourceSearchViewModel(
      searchManga: widget.searchManga,
      searchNovels: widget.searchNovels,
      scanLocalMedia: widget.scanLocalMedia,
      initialQuery: widget.initialQuery,
      initialFilter: widget.fixedMediaType == null
          ? widget.initialFilter
          : switch (widget.fixedMediaType!) {
              MediaType.anime => SourceSearchFilter.anime,
              MediaType.manga => SourceSearchFilter.manga,
              MediaType.lightNovel => SourceSearchFilter.novel,
            },
      sourceId: widget.initialSourceId,
      sourceIds: widget.sourceIds,
    );
    if (widget.initialQuery.trim().isNotEmpty) {
      unawaited(_viewModel.search(widget.initialQuery));
    }
  }

  @override
  void dispose() {
    _queryDebounce?.cancel();
    _searchController.removeListener(_cancelStaleEdit);
    _searchController.dispose();
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) => HikariScaffold(
        useSafeArea: true,
        appBar: AppBar(
          leading: Navigator.of(context).canPop() ? const BackButton() : null,
          title: Text(
            widget.catalogScopeLabel ??
                (widget.sourceName == null
                    ? 'Search sources'
                    : 'Search source'),
          ),
        ),
        body: SourceSearchContent(
          controller: _searchController,
          state: _viewModel.state,
          onQueryChanged: _queryChanged,
          onSubmitted: _submit,
          onCleared: _clear,
          onSelectFilter: (filter) =>
              unawaited(_viewModel.selectFilter(filter)),
          onRetry: () => unawaited(_viewModel.retry()),
          openMedia: widget.openMedia,
          library: widget.library,
          readArtwork: widget.readArtwork,
          scopedSourceName: widget.sourceName,
          catalogScopeLabel: widget.catalogScopeLabel,
          fixedMediaType: widget.fixedMediaType,
        ),
      ),
    );
  }
}
