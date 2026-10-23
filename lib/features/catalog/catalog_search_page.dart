import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/application/catalog/search_catalog.dart';
import 'package:hikari/core/ui/layout/hikari_page_scaffold.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/features/catalog/catalog_search_view_model.dart';
import 'package:hikari/features/catalog/widgets/catalog_search_content.dart';

class CatalogSearchPage extends StatefulWidget {
  const CatalogSearchPage({
    super.key,
    required this.searchCatalog,
    required this.openDetail,
    required this.openSourceSearch,
  });

  final SearchCatalog searchCatalog;
  final void Function(BuildContext context, CatalogEntry entry) openDetail;
  final void Function(BuildContext context) openSourceSearch;

  @override
  State<CatalogSearchPage> createState() => _CatalogSearchPageState();
}

class _CatalogSearchPageState extends State<CatalogSearchPage> {
  late final TextEditingController _controller = TextEditingController();
  late final CatalogSearchViewModel _viewModel = CatalogSearchViewModel(
    widget.searchCatalog,
  );

  Timer? _queryDebounce;
  String _lastText = '';

  @override
  void initState() {
    super.initState();
    _controller.addListener(_cancelStaleEdit);
  }

  void _cancelStaleEdit() {
    if (_lastText == _controller.text) return;
    _lastText = _controller.text;
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
  void dispose() {
    _queryDebounce?.cancel();
    _controller.removeListener(_cancelStaleEdit);
    _controller.dispose();
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) => HikariPageScaffold(
        useSafeArea: true,
        body: CatalogSearchContent(
          controller: _controller,
          state: _viewModel.state,
          onQueryChanged: _queryChanged,
          onSubmitted: _submit,
          onCleared: _clear,
          onSelectFilter: (filter) =>
              unawaited(_viewModel.selectFilter(filter)),
          onRetry: () => unawaited(_viewModel.retry()),
          onSearchSources: () => widget.openSourceSearch(context),
          openDetail: widget.openDetail,
        ),
      ),
    );
  }
}
