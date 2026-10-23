import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/controls/hikari_filter_chip.dart';
import 'package:hikari/core/ui/controls/hikari_chip_row.dart';
import 'package:hikari/core/ui/controls/hikari_search_field.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/layout/hikari_adaptive_grid.dart';
import 'package:hikari/core/ui/media/hikari_artwork.dart';
import 'package:hikari/core/ui/media/hikari_card_metrics.dart';
import 'package:hikari/core/ui/media/hikari_poster_card.dart';
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
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(
          maxWidth: HikariBreakpoints.maxContentWidth,
        ),
        child: Column(
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
      CatalogSearchStatus.idle => AsyncViewStatus.empty,
      CatalogSearchStatus.loading => AsyncViewStatus.loading,
      CatalogSearchStatus.ready => AsyncViewStatus.content,
      CatalogSearchStatus.empty => AsyncViewStatus.empty,
      CatalogSearchStatus.error => AsyncViewStatus.error,
    };

    return AsyncStateView(
      status: status,
      contentBuilder: (_) => _buildGrid(context),
      emptyTitle: state.status == CatalogSearchStatus.empty
          ? 'No results found'
          : 'Explore & Search',
      emptyMessage: state.status == CatalogSearchStatus.empty
          ? 'No catalog matches found for "${state.submittedQuery}".'
          : 'Search the catalog, then choose a source from details.',
      emptyIcon: state.status == CatalogSearchStatus.empty
          ? Icons.search_off_rounded
          : Icons.search_rounded,
      errorTitle: 'Catalog search unavailable',
      errorMessage: 'Catalog search could not load.',
      onRetry: onRetry,
    );
  }

  Widget _buildGrid(BuildContext context) {
    return HikariAdaptiveGrid.builder(
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
    );
  }
}
