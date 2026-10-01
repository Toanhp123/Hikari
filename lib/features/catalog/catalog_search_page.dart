import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/search_catalog.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/components/hikari_search_bar.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

enum _CatalogSearchFilter {
  all('All', null),
  anime('Anime', MediaType.anime),
  manga('Manga', MediaType.manga),
  novel('Light Novels', MediaType.lightNovel);

  const _CatalogSearchFilter(this.label, this.type);

  final String label;
  final MediaType? type;
}

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
  final _controller = TextEditingController();
  _CatalogSearchFilter _filter = _CatalogSearchFilter.all;
  String _query = '';
  Future<List<CatalogEntry>>? _results;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _handleQueryChanged(String rawQuery) {
    if (rawQuery.trim() == _query) return;
    setState(() {
      _query = '';
      _results = null;
    });
  }

  void _search(String rawQuery) {
    final query = rawQuery.trim();
    setState(() {
      _query = query;
      _results = query.isEmpty
          ? null
          : widget.searchCatalog.execute(query, type: _filter.type);
    });
  }

  void _selectFilter(_CatalogSearchFilter filter) {
    if (_filter == filter) return;
    setState(() {
      _filter = filter;
      if (_query.isNotEmpty && _controller.text.trim() == _query) {
        _results = widget.searchCatalog.execute(_query, type: filter.type);
      }
    });
  }

  @override
  Widget build(BuildContext context) => HikariScaffold(
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
              Row(
                children: [
                  Expanded(
                    child: HikariSearchBar(
                      controller: _controller,
                      onChanged: _handleQueryChanged,
                      onSubmitted: _search,
                      hintText: 'Search anime, manga, novels...',
                    ),
                  ),
                  const SizedBox(width: HikariSpacing.sm),
                  HikariIconButton(
                    tooltip: 'Search catalog',
                    variant: HikariIconButtonVariant.primary,
                    onPressed: () => _search(_controller.text),
                    icon: const Icon(Icons.search_rounded),
                  ),
                ],
              ),
              const SizedBox(height: HikariSpacing.sm),
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    for (final filter in _CatalogSearchFilter.values)
                      Padding(
                        padding: const EdgeInsets.only(right: HikariSpacing.sm),
                        child: HikariChip(
                          label: filter.label,
                          isSelected: _filter == filter,
                          onTap: () => _selectFilter(filter),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
        Expanded(child: _buildResults()),
      ],
    ),
  );

  Widget _buildResults() {
    if (_query.isEmpty || _results == null) {
      return const AsyncStateView(
        status: AsyncViewStatus.empty,
        contentBuilder: _emptyContent,
        emptyTitle: 'Explore & Search',
        emptyMessage: 'Search the catalog, then choose a source from details.',
        emptyIcon: Icons.search_rounded,
      );
    }

    return FutureBuilder<List<CatalogEntry>>(
      future: _results,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return AsyncStateView(
            status: AsyncViewStatus.error,
            contentBuilder: _emptyContent,
            errorTitle: 'Catalog search unavailable',
            errorMessage: 'Catalog search could not load.',
            onRetry: () => _search(_query),
          );
        }
        if (!snapshot.hasData) {
          return const AsyncStateView(
            status: AsyncViewStatus.loading,
            contentBuilder: _emptyContent,
          );
        }
        final entries = snapshot.data!;
        if (entries.isEmpty) {
          return AsyncStateView(
            status: AsyncViewStatus.empty,
            contentBuilder: _emptyContent,
            emptyTitle: 'No results found',
            emptyMessage: 'No catalog matches found for "$_query".',
            emptyIcon: Icons.search_off_rounded,
          );
        }
        return GridView.builder(
          padding: const EdgeInsets.all(HikariSpacing.lg),
          physics: const BouncingScrollPhysics(),
          gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
            maxCrossAxisExtent: HikariBreakpoints.posterGridMaxExtent,
            crossAxisSpacing: HikariSpacing.md,
            mainAxisSpacing: HikariSpacing.md,
            childAspectRatio: 2 / 3,
          ),
          itemCount: entries.length,
          itemBuilder: (context, index) {
            final entry = entries[index];
            return MediaPoster(
              title: entry.title,
              imageUrl: entry.coverUrl,
              subtitle: entry.type.name,
              badgeText: switch (entry.type) {
                MediaType.anime => 'ANIME',
                MediaType.manga => 'MANGA',
                MediaType.lightNovel => 'NOVEL',
              },
              onTap: () => widget.openDetail(context, entry),
            );
          },
        );
      },
    );
  }
}

Widget _emptyContent(BuildContext _) => const SizedBox.shrink();
