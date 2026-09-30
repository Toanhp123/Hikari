import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_button.dart';
import 'package:hikari/features/remote_manga/remote_manga_search_view_model.dart';

class RemoteMangaSearchPage extends StatefulWidget {
  const RemoteMangaSearchPage({
    super.key,
    required this.searchManga,
    required this.openMedia,
    this.library,
  });

  final SearchManga searchManga;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;

  @override
  State<RemoteMangaSearchPage> createState() => _RemoteMangaSearchPageState();
}

class _RemoteMangaSearchPageState extends State<RemoteMangaSearchPage> {
  final _queryController = TextEditingController();
  late final RemoteMangaSearchViewModel _viewModel;

  @override
  void initState() {
    super.initState();
    _viewModel = RemoteMangaSearchViewModel(searchManga: widget.searchManga);
  }

  @override
  void dispose() {
    _queryController.dispose();
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;

    return ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) => HikariScaffold(
        useSafeArea: true,
        appBar: AppBar(
          title: Text(
            'Manga search',
            style: HikariTypography.titleLarge.copyWith(
              color: colors.textPrimary,
              fontWeight: FontWeight.w700,
            ),
          ),
          backgroundColor: Colors.transparent,
          elevation: 0,
        ),
        body: _viewModel.sources.isEmpty
            ? Center(
                child: Text(
                  'No manga search source is available.',
                  style: TextStyle(color: colors.textMuted),
                ),
              )
            : Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: HikariSpacing.lg,
                  vertical: HikariSpacing.sm,
                ),
                child: Column(
                  children: [
                    if (_viewModel.sources.length > 1)
                      Container(
                        margin: const EdgeInsets.only(bottom: HikariSpacing.sm),
                        padding: const EdgeInsets.symmetric(
                          horizontal: HikariSpacing.md,
                        ),
                        decoration: BoxDecoration(
                          color: colors.surfaceContainer,
                          borderRadius: HikariRadius.borderMd,
                          border: Border.all(color: colors.border),
                        ),
                        child: _buildSourcePicker(colors),
                      ),
                    Container(
                      padding: const EdgeInsets.all(HikariSpacing.sm),
                      decoration: BoxDecoration(
                        color: colors.surfaceContainer,
                        borderRadius: HikariRadius.borderMd,
                        border: Border.all(color: colors.border),
                      ),
                      child: Column(
                        children: [
                          TextField(
                            controller: _queryController,
                            enabled: true,
                            textInputAction: TextInputAction.search,
                            onSubmitted: _viewModel.search,
                            style: TextStyle(color: colors.textPrimary),
                            decoration: InputDecoration(
                              labelText: _viewModel.sources.length == 1
                                  ? 'Manga title · ${_viewModel.selectedSource!.name}'
                                  : 'Manga title',
                              labelStyle: TextStyle(
                                color: colors.textSecondary,
                              ),
                              prefixIcon: Icon(
                                Icons.search_rounded,
                                color: colors.primaryGlow,
                              ),
                              filled: true,
                              fillColor: colors.surfaceElevated,
                              border: OutlineInputBorder(
                                borderRadius: HikariRadius.borderSm,
                                borderSide: BorderSide(color: colors.border),
                              ),
                              enabledBorder: OutlineInputBorder(
                                borderRadius: HikariRadius.borderSm,
                                borderSide: BorderSide(color: colors.border),
                              ),
                              focusedBorder: OutlineInputBorder(
                                borderRadius: HikariRadius.borderSm,
                                borderSide: BorderSide(
                                  color: colors.primaryGlow,
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(height: HikariSpacing.sm),
                          Align(
                            alignment: Alignment.centerRight,
                            child: SizedBox(
                              height: 38,
                              child: HikariButton(
                                label: 'Search',
                                icon: const Icon(
                                  Icons.manage_search_rounded,
                                  size: 18,
                                ),
                                size: HikariButtonSize.small,
                                onPressed:
                                    _viewModel.state is RemoteMangaSearchLoading
                                    ? null
                                    : () => _viewModel.search(
                                        _queryController.text,
                                      ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: HikariSpacing.md),
                    Expanded(child: _buildResults(_viewModel.state, colors)),
                  ],
                ),
              ),
      ),
    );
  }

  Widget _buildSourcePicker(HikariColors colors) => DropdownButton<SourceId>(
    value: _viewModel.selectedSource!.id,
    isExpanded: true,
    dropdownColor: colors.surfaceElevated,
    underline: const SizedBox.shrink(),
    style: TextStyle(color: colors.textPrimary, fontSize: 14),
    onChanged: _viewModel.selectSource,
    items: [
      for (final source in _viewModel.sources)
        DropdownMenuItem(value: source.id, child: Text(source.name)),
    ],
  );

  Widget _buildResults(RemoteMangaSearchUiState state, HikariColors colors) =>
      switch (state) {
        RemoteMangaSearchIdle() => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.auto_stories_rounded,
                size: 48,
                color: colors.textMuted.withValues(alpha: 0.5),
              ),
              const SizedBox(height: HikariSpacing.sm),
              Text(
                'Search for a manga title.',
                style: TextStyle(color: colors.textMuted, fontSize: 14),
              ),
            ],
          ),
        ),
        RemoteMangaSearchLoading() => Center(
          child: CircularProgressIndicator(
            valueColor: AlwaysStoppedAnimation<Color>(colors.primary),
          ),
        ),
        RemoteMangaSearchFailure() => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.error_outline_rounded, size: 40, color: colors.error),
              const SizedBox(height: HikariSpacing.sm),
              Text(
                'Could not search. Check source access or rate limits and try again.',
                style: TextStyle(color: colors.textSecondary, fontSize: 13),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: HikariSpacing.sm),
              TextButton(
                onPressed: _viewModel.retry,
                child: const Text('Try again'),
              ),
            ],
          ),
        ),
        RemoteMangaSearchReady(
          :final results,
          :final hasNextPage,
          :final loadingMore,
          :final pageFailed,
        ) =>
          ListView.separated(
            physics: const BouncingScrollPhysics(),
            itemCount: results.length + 1,
            separatorBuilder: (context, index) =>
                const SizedBox(height: HikariSpacing.xs),
            itemBuilder: (context, index) {
              if (index == results.length) {
                return Padding(
                  padding: const EdgeInsets.symmetric(
                    vertical: HikariSpacing.md,
                  ),
                  child: Column(
                    children: [
                      if (results.isEmpty)
                        Text(
                          'No manga found.',
                          style: TextStyle(color: colors.textMuted),
                        ),
                      if (loadingMore)
                        Center(
                          child: CircularProgressIndicator(
                            valueColor: AlwaysStoppedAnimation<Color>(
                              colors.primary,
                            ),
                          ),
                        )
                      else if (hasNextPage)
                        TextButton(
                          onPressed: _viewModel.loadMore,
                          child: Text(
                            pageFailed ? 'Retry next page' : 'Load more',
                            style: TextStyle(color: colors.primaryGlow),
                          ),
                        ),
                    ],
                  ),
                );
              }
              final preview = results[index];
              final media = preview.media;
              return Material(
                color: colors.surfaceContainer,
                shape: RoundedRectangleBorder(
                  borderRadius: HikariRadius.borderMd,
                  side: BorderSide(color: colors.border),
                ),
                clipBehavior: Clip.antiAlias,
                child: ListTile(
                  key: ValueKey(media.source),
                  leading: Container(
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                      color: colors.badgeManga.withValues(alpha: 0.15),
                      borderRadius: HikariRadius.borderSm,
                    ),
                    child: Icon(
                      Icons.book_rounded,
                      color: colors.badgeManga,
                      size: 20,
                    ),
                  ),
                  title: Text(
                    media.title,
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: colors.textPrimary,
                    ),
                  ),
                  subtitle: Text(
                    _viewModel.selectedSource!.name,
                    style: TextStyle(fontSize: 12, color: colors.textSecondary),
                  ),
                  onTap: () => widget.openMedia(context, media),
                  trailing: widget.library == null
                      ? null
                      : SizedBox(
                          width: 80,
                          child: LibraryButton(
                            repository: widget.library!,
                            media: media,
                          ),
                        ),
                ),
              );
            },
          ),
      };
}
