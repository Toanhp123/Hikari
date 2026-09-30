import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_button.dart';
import 'package:hikari/features/remote_novel/remote_novel_search_view_model.dart';

class RemoteNovelSearchPage extends StatefulWidget {
  const RemoteNovelSearchPage({
    super.key,
    required this.searchNovels,
    required this.openMedia,
    this.library,
  });

  final SearchNovels searchNovels;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;

  @override
  State<RemoteNovelSearchPage> createState() => _RemoteNovelSearchPageState();
}

class _RemoteNovelSearchPageState extends State<RemoteNovelSearchPage> {
  final _query = TextEditingController();
  late final _model = RemoteNovelSearchViewModel(widget.searchNovels);

  @override
  void dispose() {
    _query.dispose();
    _model.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;

    return ListenableBuilder(
      listenable: _model,
      builder: (context, _) => HikariScaffold(
        useSafeArea: true,
        appBar: AppBar(
          title: Text(
            'Novel search',
            style: HikariTypography.titleLarge.copyWith(
              color: colors.textPrimary,
              fontWeight: FontWeight.w700,
            ),
          ),
          backgroundColor: Colors.transparent,
          elevation: 0,
        ),
        body: _model.sources.isEmpty
            ? Center(
                child: Text(
                  'No novel search source is available.',
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
                    if (_model.sources.length > 1)
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
                            controller: _query,
                            textInputAction: TextInputAction.search,
                            onSubmitted: _model.search,
                            style: TextStyle(color: colors.textPrimary),
                            decoration: InputDecoration(
                              labelText:
                                  'Novel title · ${_model.selectedSource!.name}',
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
                                    _model.state is RemoteNovelSearchLoading
                                    ? null
                                    : () => _model.search(_query.text),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: HikariSpacing.md),
                    Expanded(child: _buildResults(_model.state, colors)),
                  ],
                ),
              ),
      ),
    );
  }

  Widget _buildSourcePicker(HikariColors colors) => DropdownButton<SourceId>(
    value: _model.selectedSource!.id,
    isExpanded: true,
    dropdownColor: colors.surfaceElevated,
    underline: const SizedBox.shrink(),
    style: TextStyle(color: colors.textPrimary, fontSize: 14),
    onChanged: _model.selectSource,
    items: [
      for (final source in _model.sources)
        DropdownMenuItem(value: source.id, child: Text(source.name)),
    ],
  );

  Widget _buildResults(RemoteNovelSearchUiState state, HikariColors colors) =>
      switch (state) {
        RemoteNovelSearchIdle() => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.menu_book_rounded,
                size: 48,
                color: colors.textMuted.withValues(alpha: 0.5),
              ),
              const SizedBox(height: HikariSpacing.sm),
              Text(
                'Search for a novel title.',
                style: TextStyle(color: colors.textMuted, fontSize: 14),
              ),
            ],
          ),
        ),
        RemoteNovelSearchLoading() => Center(
          child: CircularProgressIndicator(
            valueColor: AlwaysStoppedAnimation<Color>(colors.primary),
          ),
        ),
        RemoteNovelSearchFailure() => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.error_outline_rounded, size: 40, color: colors.error),
              const SizedBox(height: HikariSpacing.sm),
              Text(
                'Could not search. Check source access and try again.',
                style: TextStyle(color: colors.textSecondary, fontSize: 13),
              ),
              const SizedBox(height: HikariSpacing.sm),
              TextButton(
                onPressed: _model.retry,
                child: const Text('Try again'),
              ),
            ],
          ),
        ),
        RemoteNovelSearchReady() => _buildReady(state, colors),
      };

  Widget _buildReady(RemoteNovelSearchReady state, HikariColors colors) =>
      ListView.separated(
        physics: const BouncingScrollPhysics(),
        itemCount: state.results.length + 1,
        separatorBuilder: (context, index) =>
            const SizedBox(height: HikariSpacing.xs),
        itemBuilder: (context, index) {
          if (index == state.results.length) {
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: HikariSpacing.md),
              child: Column(
                children: [
                  if (state.results.isEmpty)
                    Text(
                      'No novels found.',
                      style: TextStyle(color: colors.textMuted),
                    ),
                  if (state.loadingMore)
                    Center(
                      child: CircularProgressIndicator(
                        valueColor: AlwaysStoppedAnimation<Color>(
                          colors.primary,
                        ),
                      ),
                    )
                  else if (state.hasNextPage != false)
                    TextButton(
                      onPressed: _model.loadMore,
                      child: Text(
                        state.pageFailed ? 'Retry next page' : 'Load more',
                        style: TextStyle(color: colors.primaryGlow),
                      ),
                    ),
                ],
              ),
            );
          }

          final preview = state.results[index];
          return Material(
            color: colors.surfaceContainer,
            shape: RoundedRectangleBorder(
              borderRadius: HikariRadius.borderMd,
              side: BorderSide(color: colors.border),
            ),
            clipBehavior: Clip.antiAlias,
            child: ListTile(
              key: ValueKey(preview.media.source),
              leading: Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: colors.badgeNovel.withValues(alpha: 0.15),
                  borderRadius: HikariRadius.borderSm,
                ),
                child: Icon(
                  Icons.auto_stories_rounded,
                  color: colors.badgeNovel,
                  size: 20,
                ),
              ),
              title: Text(
                preview.media.title,
                style: TextStyle(
                  fontWeight: FontWeight.w600,
                  color: colors.textPrimary,
                ),
              ),
              subtitle: Text(
                [
                  _model.selectedSource!.name,
                  ...preview.metadata.authors,
                ].join(' · '),
                style: TextStyle(fontSize: 12, color: colors.textSecondary),
              ),
              onTap: () => widget.openMedia(context, preview.media),
              trailing: widget.library == null
                  ? null
                  : SizedBox(
                      width: 80,
                      child: LibraryButton(
                        repository: widget.library!,
                        media: preview.media,
                      ),
                    ),
            ),
          );
        },
      );
}
