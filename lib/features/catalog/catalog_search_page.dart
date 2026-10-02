import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/application/catalog/search_catalog.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/features/catalog/catalog_search_view_model.dart';
import 'package:hikari/features/catalog/widgets/catalog_search_content.dart';

class CatalogSearchPage extends StatefulWidget {
  const CatalogSearchPage({
    super.key,
    required this.searchCatalog,
    required this.openDetail,
  });

  final SearchCatalog searchCatalog;
  final void Function(BuildContext context, CatalogEntry entry) openDetail;

  @override
  State<CatalogSearchPage> createState() => _CatalogSearchPageState();
}

class _CatalogSearchPageState extends State<CatalogSearchPage> {
  late final TextEditingController _controller = TextEditingController();
  late final CatalogSearchViewModel _viewModel = CatalogSearchViewModel(
    widget.searchCatalog,
  );

  @override
  void dispose() {
    _controller.dispose();
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) => HikariScaffold(
        useSafeArea: true,
        body: CatalogSearchContent(
          controller: _controller,
          state: _viewModel.state,
          onQueryChanged: _viewModel.updateQuery,
          onSubmitted: (query) => unawaited(_viewModel.submitQuery(query)),
          onSelectFilter: (filter) =>
              unawaited(_viewModel.selectFilter(filter)),
          onRetry: () => unawaited(_viewModel.retry()),
          openDetail: widget.openDetail,
        ),
      ),
    );
  }
}
