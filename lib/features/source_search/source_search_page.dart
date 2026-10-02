import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
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
  late final SourceSearchViewModel _viewModel;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController(text: widget.initialQuery);
    _viewModel = SourceSearchViewModel(
      searchManga: widget.searchManga,
      searchNovels: widget.searchNovels,
      scanLocalMedia: widget.scanLocalMedia,
      initialQuery: widget.initialQuery,
      initialFilter: widget.initialFilter,
    );
    if (widget.initialQuery.trim().isNotEmpty) {
      unawaited(_viewModel.search(widget.initialQuery));
    }
  }

  @override
  void dispose() {
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
        body: SourceSearchContent(
          controller: _searchController,
          state: _viewModel.state,
          onQueryChanged: (query) => unawaited(_viewModel.search(query)),
          onSelectFilter: (filter) =>
              unawaited(_viewModel.selectFilter(filter)),
          onRetry: () => unawaited(_viewModel.retry()),
          openMedia: widget.openMedia,
          library: widget.library,
        ),
      ),
    );
  }
}
