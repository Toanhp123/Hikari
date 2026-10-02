import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/core/ui/components/hikari_search_bar.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
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
    required this.onSelectFilter,
    required this.onRetry,
    required this.openDetail,
  });

  final TextEditingController controller;
  final CatalogSearchUiState state;
  final ValueChanged<String> onQueryChanged;
  final ValueChanged<String> onSubmitted;
  final ValueChanged<CatalogSearchFilter> onSelectFilter;
  final VoidCallback onRetry;
  final void Function(BuildContext context, CatalogEntry entry) openDetail;

  @override
  Widget build(BuildContext context) {
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
              Row(
                children: [
                  Expanded(
                    child: HikariSearchBar(
                      controller: controller,
                      onChanged: onQueryChanged,
                      onSubmitted: onSubmitted,
                      hintText: 'Search anime, manga, novels...',
                    ),
                  ),
                  const SizedBox(width: HikariSpacing.sm),
                  HikariIconButton(
                    tooltip: 'Search catalog',
                    variant: HikariIconButtonVariant.primary,
                    onPressed: () => onSubmitted(controller.text),
                    icon: const Icon(Icons.search_rounded),
                  ),
                ],
              ),
              const SizedBox(height: HikariSpacing.sm),
              HikariChipRow(
                children: [
                  for (final filter in CatalogSearchFilter.values)
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
        Expanded(
          child: _CatalogSearchResults(
            state: state,
            onRetry: onRetry,
            openDetail: openDetail,
          ),
        ),
      ],
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
    return GridView.builder(
      padding: const EdgeInsets.all(HikariSpacing.lg),
      physics: const BouncingScrollPhysics(),
      gridDelegate: mediaPosterGridDelegate,
      itemCount: state.entries.length,
      itemBuilder: (context, index) {
        final entry = state.entries[index];
        return MediaPoster(
          title: entry.title,
          imageUrl: entry.coverUrl,
          subtitle: mediaTypeLabel(entry.type),
          badgeText: mediaTypeBadgeLabel(entry.type),
          onTap: () => openDetail(context, entry),
        );
      },
    );
  }
}
