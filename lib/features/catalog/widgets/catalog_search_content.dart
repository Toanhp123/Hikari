import 'package:flutter/material.dart';
import 'package:hikari/core/ui/hikari_ui.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/features/catalog/catalog_search_view_model.dart';

class CatalogSearchContent extends StatelessWidget {
  const CatalogSearchContent({
    super.key,
    required this.controller,
    required this.state,
    required this.onQueryChanged,
    required this.onSubmitted,
    this.onCleared,
    required this.onSelectFilter,
    required this.onRetry,
    required this.onSearchSources,
    required this.openDetail,
  });

  final TextEditingController controller;
  final CatalogSearchUiState state;
  final ValueChanged<String> onQueryChanged;
  final ValueChanged<String> onSubmitted;
  final VoidCallback? onCleared;
  final ValueChanged<CatalogSearchFilter> onSelectFilter;
  final VoidCallback onRetry;
  final VoidCallback onSearchSources;
  final void Function(BuildContext context, CatalogEntry entry) openDetail;

  @override
  Widget build(BuildContext context) {
    return HikariContentRegion(
      child: LayoutBuilder(
        builder: (context, constraints) => Column(
          children: [
            ConstrainedBox(
              constraints: BoxConstraints(maxHeight: constraints.maxHeight / 2),
              child: SingleChildScrollView(
                primary: false,
                child: Padding(
                  padding: const EdgeInsets.only(
                    top: HikariSpacing.md,
                    bottom: HikariSpacing.sm,
                  ),
                  child: Column(
                    children: [
                      HikariSearchField(
                        controller: controller,
                        onChanged: onQueryChanged,
                        onSubmitted: onSubmitted,
                        onCleared: onCleared,
                      ),
                      const SizedBox(height: HikariSpacing.sm),
                      HikariChipRow(
                        children: [
                          for (final filter in CatalogSearchFilter.values)
                            HikariFilterChip(
                              label: filter.mediaType == null
                                  ? 'All'
                                  : mediaTypeLabel(filter.mediaType!),
                              selected: state.filter == filter,
                              onSelected: (_) => onSelectFilter(filter),
                            ),
                        ],
                      ),
                      Align(
                        alignment: AlignmentDirectional.centerEnd,
                        child: TextButton.icon(
                          onPressed: onSearchSources,
                          icon: const Icon(Icons.extension_outlined, size: 18),
                          label: const Text('Search sources'),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            Expanded(
              child: _CatalogSearchResults(
                state: state,
                onRetry: onRetry,
                openDetail: openDetail,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CatalogSearchResults extends StatelessWidget {
  const _CatalogSearchResults({
    required this.state,
    required this.onRetry,
    required this.openDetail,
  });

  final CatalogSearchUiState state;
  final VoidCallback onRetry;
  final void Function(BuildContext context, CatalogEntry entry) openDetail;

  @override
  Widget build(BuildContext context) {
    final status = switch (state.status) {
      CatalogSearchStatus.idle => HikariAsyncStatus.empty,
      CatalogSearchStatus.loading => HikariAsyncStatus.loading,
      CatalogSearchStatus.ready => HikariAsyncStatus.loaded,
      CatalogSearchStatus.empty => HikariAsyncStatus.empty,
      CatalogSearchStatus.error => HikariAsyncStatus.fatalError,
    };

    return HikariAsyncStateView(
      status: status,
      emptyTitle: state.status == CatalogSearchStatus.empty
          ? 'No results found'
          : 'Explore & Search',
      emptyMessage: state.status == CatalogSearchStatus.empty
          ? 'No catalog matches found for "${state.submittedQuery}".'
          : 'Search the catalog, then choose a source from details.',
      message: 'Catalog search unavailable. Catalog search could not load.',
      onRetry: onRetry,
      child: HikariAdaptiveGrid.builder(
        padding: const EdgeInsets.symmetric(vertical: HikariSpacing.lg),
        physics: const BouncingScrollPhysics(),
        itemExtentBuilder: (context, width) =>
            hikariCardExtent(context, width: width, hasSubtitle: true),
        itemKeyBuilder: (index) =>
            ValueKey((state.entries[index].id, state.entries[index].type)),
        itemCount: state.entries.length,
        itemBuilder: (context, index) {
          final entry = state.entries[index];
          return HikariPosterCard(
            title: entry.title,
            artwork: HikariArtwork(
              image: entry.coverUrl == null
                  ? null
                  : NetworkImage(entry.coverUrl!),
            ),
            subtitle: mediaTypeLabel(entry.type),
            badge: Badge(
              backgroundColor: Theme.of(context).colorScheme.primaryContainer,
              textColor: Theme.of(context).colorScheme.onPrimaryContainer,
              label: Text(mediaTypeBadgeLabel(entry.type)),
            ),
            onTap: () => openDetail(context, entry),
          );
        },
      ),
    );
  }
}
