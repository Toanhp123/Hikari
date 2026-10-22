import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/application/catalog/resolve_catalog_source.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/read_remote_chapter_list.dart';
import 'package:hikari/core/ui/components/hikari_modal_sheet.dart';
import 'package:hikari/core/ui/components/hikari_refresh_action.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/patterns/chapter_control_bar.dart';
import 'package:hikari/core/ui/patterns/chapter_row.dart';
import 'package:hikari/core/ui/patterns/primary_reading_cta.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/catalog/catalog_source_selection.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/catalog/catalog_chapters_view_model.dart';
import 'package:hikari/features/catalog/catalog_detail_view_model.dart';
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';
import 'package:hikari/features/catalog/widgets/catalog_detail_content.dart';
import 'package:hikari/features/catalog/widgets/catalog_detail_hero.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_picker.dart';
import 'package:hikari/features/library/widgets/library_button.dart';

typedef _CatalogSourcePickerResult = ({
  CatalogSourcePickerCandidate? candidate,
  CatalogSourcePickerSource? source,
  Set<SourceId>? sourceIds,
  String? sourceLanguage,
});

class CatalogDetailPage extends StatefulWidget {
  const CatalogDetailPage({
    super.key,
    required this.initialEntry,
    required this.loadDetails,
    required this.resolveCatalogSource,
    required this.openMedia,
    required this.openRelated,
    required this.openSourceSearch,
    this.readArtwork,
    required this.chapterOpener,
    required this.chapterLists,
    required this.loadReadingTarget,
    this.sourceSelections,
    this.library,
    required this.selectSourceSearch,
    required this.openMangaChapter,
    required this.openNovelChapter,
  });

  final OpenMedia chapterOpener;
  final ReadRemoteChapterList chapterLists;
  final LoadSeriesReadingTarget loadReadingTarget;
  final LibraryRepository? library;
  final CatalogSourceSelectionRepository? sourceSelections;
  final Future<CatalogSourcePickerCandidate?> Function(
    CatalogEntry,
    SourceId?,
    String?,
    Set<SourceId>?,
    String?,
  )
  selectSourceSearch;
  final Future<void> Function(
    BuildContext,
    Media,
    MangaChapter,
    List<MangaChapter>,
    bool Function(),
  )
  openMangaChapter;
  final Future<void> Function(
    BuildContext,
    Media,
    NovelChapter,
    List<NovelChapter>,
    bool Function(),
  )
  openNovelChapter;
  final CatalogEntry initialEntry;
  final LoadCatalogEntryDetails loadDetails;
  final ResolveCatalogSource resolveCatalogSource;
  final Future<void> Function(
    BuildContext context,
    Media media, {
    MediaMetadata? previewMetadata,
    bool Function()? canNavigate,
  })
  openMedia;
  final void Function(CatalogEntry entry) openRelated;
  final Future<Uint8List?> Function(SourceMediaRef artwork)? readArtwork;
  final void Function(
    CatalogEntry entry,
    SourceId? sourceId,
    String? sourceName,
    Set<SourceId>? sourceIds,
    String? sourceLanguage,
  )
  openSourceSearch;

  @override
  State<CatalogDetailPage> createState() => _CatalogDetailPageState();
}

class _CatalogDetailPageState extends State<CatalogDetailPage>
    with SingleTickerProviderStateMixin {
  late CatalogDetailViewModel _viewModel = CatalogDetailViewModel(
    initialEntry: widget.initialEntry,
    loadDetails: widget.loadDetails,
  );
  bool _descriptionExpanded = false;
  CatalogChaptersViewModel? _chapters;
  late final TabController _tabs = TabController(length: 2, vsync: this);
  bool get _chapterTab => _tabs.index == 1;
  bool _searchExpanded = false;
  int _selectionGeneration = 0;
  int _identityGeneration = 0;
  Future<void>? _restoreOperation;
  Future<void> _saveOperation = Future<void>.value();
  int _chapterScrollVersion = 0;

  void _createChapters() {
    _chapters = CatalogChaptersViewModel(
      openMedia: widget.chapterOpener,
      chapterLists: widget.chapterLists,
      loadReadingTarget: widget.loadReadingTarget,
    );
  }

  @override
  void didUpdateWidget(CatalogDetailPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.initialEntry.id != widget.initialEntry.id ||
        oldWidget.initialEntry.type != widget.initialEntry.type ||
        oldWidget.chapterOpener != widget.chapterOpener ||
        oldWidget.chapterLists != widget.chapterLists ||
        oldWidget.loadDetails != widget.loadDetails ||
        oldWidget.loadReadingTarget != widget.loadReadingTarget ||
        oldWidget.sourceSelections != widget.sourceSelections) {
      _selectionGeneration++;
      _restoreOperation = null;
      _identityGeneration++;
      _viewModel.dispose();
      _viewModel = CatalogDetailViewModel(
        initialEntry: widget.initialEntry,
        loadDetails: widget.loadDetails,
      );
      _chapters?.dispose();
      _chapters = null;
      _createChapters();
      _tabs.index = 0;
      _descriptionExpanded = false;
      _searchExpanded = false;
      unawaited(_viewModel.load());
    }
  }

  @override
  void initState() {
    super.initState();
    _tabs.addListener(_tabChanged);
    _createChapters();
    unawaited(_viewModel.load());
  }

  void _tabChanged() {
    if (!mounted) return;
    setState(() {});
    if (_chapterTab) unawaited(_restoreSelection());
  }

  // The chapter tab owns the lazy read. Opening Information never touches the
  // selected source, its cached chapters or its extension.
  Future<void> _restoreSelection() =>
      _restoreOperation ??= _loadSavedSelection();

  Future<void> _loadSavedSelection() async {
    final repository = widget.sourceSelections;
    final chapters = _chapters;
    if (repository == null || chapters == null || chapters.media != null) {
      return;
    }
    final entry = _viewModel.state.entry;
    final generation = _selectionGeneration;
    final identityGeneration = _identityGeneration;
    try {
      final saved = await repository.load(entry);
      if (!mounted ||
          generation != _selectionGeneration ||
          identityGeneration != _identityGeneration ||
          chapters != _chapters ||
          chapters.media != null ||
          saved == null) {
        return;
      }
      if (saved.type != entry.type) return;
      await chapters.select(saved, null);
    } catch (_) {
      if (mounted &&
          generation == _selectionGeneration &&
          identityGeneration == _identityGeneration) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not restore selected source.')),
        );
      }
    }
  }

  Future<void> _restoreAfterCancelledSelection() async {
    if (_chapters?.media != null) return;
    // A pending restore can have been superseded by the opened picker.
    _restoreOperation = null;
    await _restoreSelection();
  }

  // Serialize writes in confirmation order: slow A/B/A persistence must not
  // replace a newer user choice. Keep writes alive after route disposal.
  void _rememberSelection(CatalogEntry entry, Media selected) {
    final repository = widget.sourceSelections;
    if (repository == null) return;
    _saveOperation = _saveOperation
        .then((_) => repository.save(entry, selected))
        .catchError((Object _) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Could not save selected source.')),
            );
          }
        });
  }

  @override
  void dispose() {
    _selectionGeneration++;
    _tabs.dispose();
    _chapters?.dispose();
    _viewModel.dispose();
    super.dispose();
  }

  Future<void> _handlePrimaryAction() async {
    final state = _viewModel.state;
    final entry = state.entry;
    if (entry.type == MediaType.anime) {
      widget.openSourceSearch(entry, null, null, null, null);
      return;
    }

    _tabs.index = 1;
    await _restoreSelection();
    if (!mounted || _chapters?.media != null) return;
    await _chooseSource();
  }

  Object? _sourcePickerSession;

  Future<void> _chooseSource() async {
    if (_sourcePickerSession != null || !mounted) return;
    final session = Object();
    _sourcePickerSession = session;
    try {
      await _chooseSourceOnce();
    } finally {
      if (identical(_sourcePickerSession, session)) _sourcePickerSession = null;
    }
  }

  Future<void> _chooseSourceOnce() async {
    final state = _viewModel.state;
    final entry = state.entry;
    final generation = ++_selectionGeneration;
    final picker = CatalogSourcePickerViewModel(
      entry: entry,
      details: state.details,
      resolver: widget.resolveCatalogSource,
    );
    late final _CatalogSourcePickerResult? result;
    try {
      result = context.isExpanded
          ? await _showSourceDialog(picker)
          : await _showSourceSheet(picker);
    } finally {
      picker.dispose();
    }
    if (!mounted || generation != _selectionGeneration) return;
    if (result == null) {
      await _restoreAfterCancelledSelection();
      return;
    }

    var candidate = result.candidate;
    if (candidate == null) {
      candidate = await widget.selectSourceSearch(
        entry,
        result.source?.id,
        result.source?.displayName,
        result.sourceIds,
        result.sourceLanguage,
      );
      if (!mounted || generation != _selectionGeneration) return;
    }
    if (candidate == null) {
      await _restoreAfterCancelledSelection();
      return;
    }
    final selected = candidate.media;
    if (selected.type != entry.type ||
        selected.source.sourceId == SourceId.local ||
        selected.source.sourceId.value.isEmpty ||
        selected.source.itemId.isEmpty ||
        selected.title.isEmpty ||
        (candidate.metadata?.cover != null &&
            candidate.metadata!.cover!.sourceId != selected.source.sourceId)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Invalid source selection.')),
      );
      await _restoreAfterCancelledSelection();
      return;
    }
    // Confirmation owns the preference even when a subsequent chapter
    // request fails. Cancel/failure before confirmation leaves it unchanged.
    _rememberSelection(entry, selected);
    _searchExpanded = false;
    _chapterScrollVersion++;
    _sourcePickerSession = null;
    await _chapters!.select(candidate.media, candidate.metadata);
  }

  Future<void> _openChapter(Object? chapter) async {
    final owner = _chapters;
    if (owner == null) return;
    bool Function() operationCurrent = () => false;
    try {
      await owner.open((selected, current) async {
        operationCurrent = current;
        switch (chapter) {
          case MangaChapter c:
            if (!c.canReadPages || !owner.mangaSequence.contains(c)) return;
            await widget.openMangaChapter(
              context,
              selected,
              c,
              List.unmodifiable(owner.mangaSequence),
              current,
            );
          case NovelChapter c:
            if (!owner.novelSequence.contains(c)) return;
            await widget.openNovelChapter(
              context,
              selected,
              c,
              List.unmodifiable(owner.novelSequence),
              current,
            );
          default:
            if (owner.direct && current()) {
              await widget.openMedia(
                context,
                selected,
                previewMetadata: owner.metadata,
                canNavigate: current,
              );
            }
        }
      });
    } catch (_) {
      if (mounted && operationCurrent()) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Could not open this chapter. Check source access and try again.',
            ),
          ),
        );
      }
    }
  }

  List<Widget> _chapterSlivers() {
    final colors = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final owner = _chapters;
    final selected = owner?.media;
    final chapters = selected?.type == MediaType.manga
        ? owner!.displayManga
        : owner?.displayNovel ?? const [];
    final Object? primary = selected?.type == MediaType.manga
        ? owner!.manga.where((c) => c.source == owner.primary).firstOrNull
        : owner?.novel.where((c) => c.source == owner.primary).firstOrNull;
    return [
      SliverToBoxAdapter(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: HikariBreakpoints.maxContentWidth,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: HikariSpacing.md,
                    vertical: HikariSpacing.xs,
                  ),
                  child: Wrap(
                    alignment: WrapAlignment.spaceBetween,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    spacing: HikariSpacing.md,
                    runSpacing: HikariSpacing.xs,
                    children: [
                      Wrap(
                        spacing: HikariSpacing.xs,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          if (selected != null)
                            Text(
                              owner!.sourceName ?? selected.title,
                              style: textTheme.titleSmall?.copyWith(
                                fontWeight: FontWeight.w600,
                                color: colors.onSurface,
                              ),
                            ),
                          TextButton(
                            style: TextButton.styleFrom(
                              visualDensity: VisualDensity.compact,
                              padding: const EdgeInsets.symmetric(
                                horizontal: HikariSpacing.sm,
                                vertical: HikariSpacing.xs,
                              ),
                            ),
                            onPressed: () => unawaited(_chooseSource()),
                            child: Text(
                              selected == null
                                  ? 'Choose source'
                                  : 'Change source',
                            ),
                          ),
                        ],
                      ),
                      if (selected != null && widget.library != null)
                        LibraryButton(
                          repository: widget.library!,
                          media: selected,
                          artwork: owner?.metadata?.cover,
                        ),
                    ],
                  ),
                ),
                if (owner?.fromCache == true)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(
                      HikariSpacing.md,
                      0,
                      HikariSpacing.md,
                      HikariSpacing.xs,
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          owner!.cacheStale
                              ? Icons.cloud_off_outlined
                              : Icons.cloud_done_outlined,
                          size: 14,
                          color: owner.cacheStale
                              ? colors.error
                              : colors.onSurfaceVariant,
                        ),
                        const SizedBox(width: HikariSpacing.xs),
                        Flexible(
                          child: Text(
                            owner.cacheStale
                                ? 'Cached chapters · stale'
                                : 'Cached chapters · fresh',
                            style: textTheme.labelSmall?.copyWith(
                              color: owner.cacheStale
                                  ? colors.error
                                  : colors.onSurfaceVariant,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                if (owner?.loading == true &&
                    owner?.hasChapters == false &&
                    owner?.direct == false)
                  const Padding(
                    padding: EdgeInsets.all(HikariSpacing.md),
                    child: Center(
                      child: CircularProgressIndicator.adaptive(
                        semanticsLabel: 'Loading chapters',
                      ),
                    ),
                  ),
                // Opening a chapter is an explicit navigation operation, not
                // a refresh. Keep its feedback and interaction guard.
                if (owner?.opening == true)
                  const LinearProgressIndicator(
                    semanticsLabel: 'Opening chapter',
                  ),
                if (owner?.failed == true)
                  Semantics(
                    liveRegion: true,
                    child: Padding(
                      padding: const EdgeInsets.all(HikariSpacing.md),
                      child: Column(
                        children: [
                          Text(
                            owner!.hasChapters
                                ? 'Could not refresh chapters. Showing the last loaded list.'
                                : 'Could not load chapters. Check source access and try again.',
                          ),
                          TextButton(
                            onPressed: () =>
                                unawaited(owner.load(forceRefresh: true)),
                            child: const Text('Retry'),
                          ),
                        ],
                      ),
                    ),
                  ),
                if (owner?.hasChapters == true || owner?.direct == true)
                  PrimaryReadingCta(
                    label: owner!.isContinuation
                        ? 'Continue reading'
                        : 'Start reading',
                    enabled: !owner.opening,
                    onPressed: primary == null && !owner.direct
                        ? null
                        : () => unawaited(_openChapter(primary)),
                  ),
                if (owner?.hasChapters == true)
                  ChapterControlBar(
                    autofocus: false,
                    totalChapters: owner!.manga.length + owner.novel.length,
                    filteredChapters: owner.searchQuery.trim().isEmpty
                        ? null
                        : chapters.length,
                    reverseSourceOrder: owner.reverseSourceOrder,
                    onToggleSourceOrder: owner.toggleSourceOrder,
                    isSearchExpanded: _searchExpanded,
                    onToggleSearchExpanded: () => setState(() {
                      _searchExpanded = !_searchExpanded;
                      if (!_searchExpanded) owner.setSearchQuery('');
                    }),
                    searchQuery: owner.searchQuery,
                    onSearchChanged: owner.setSearchQuery,
                    onClearSearch: () => owner.setSearchQuery(''),
                  ),
                if (owner?.hasChapters == true && chapters.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(HikariSpacing.lg),
                    child: Text(
                      owner!.searchQuery.isEmpty
                          ? 'No readable chapters found.'
                          : 'No chapters matching "${owner.searchQuery}"',
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
      SliverList.builder(
        itemCount: chapters.length,
        itemBuilder: (context, index) => Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: HikariBreakpoints.maxContentWidth,
            ),
            child: ChapterRow(
              isContinuation:
                  owner?.isContinuation == true &&
                  switch (chapters[index]) {
                    MangaChapter c => c.source == owner?.primary,
                    NovelChapter c => c.source == owner?.primary,
                    _ => false,
                  },
              chapter: chapters[index],
              sourceName: owner?.sourceName ?? '',
              enabled: owner?.opening != true,
              onTap: () => unawaited(_openChapter(chapters[index])),
            ),
          ),
        ),
      ),
      const SliverToBoxAdapter(child: SizedBox(height: HikariSpacing.xxxl)),
    ];
  }

  Future<_CatalogSourcePickerResult?> _showSourceSheet(
    CatalogSourcePickerViewModel picker,
  ) {
    return showHikariModalSheet<_CatalogSourcePickerResult>(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerHigh,
      builder: (sheetContext) => Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.viewInsetsOf(sheetContext).bottom,
        ),
        child: SafeArea(
          top: false,
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.sizeOf(sheetContext).height * 0.88,
            ),
            child: CatalogSourcePicker(
              viewModel: picker,
              onOpenMedia: (candidate) => Navigator.of(sheetContext).pop((
                candidate: candidate,
                source: null,
                sourceIds: null,
                sourceLanguage: null,
              )),
              readArtwork: widget.readArtwork,
              onManualSearch: (source, sourceIds) =>
                  Navigator.of(sheetContext).pop((
                    candidate: null,
                    source: source,
                    sourceIds: sourceIds,
                    sourceLanguage: source == null
                        ? picker.state.selectedLanguage
                        : source.languageCode,
                  )),
            ),
          ),
        ),
      ),
    );
  }

  Future<_CatalogSourcePickerResult?> _showSourceDialog(
    CatalogSourcePickerViewModel picker,
  ) {
    return showDialog<_CatalogSourcePickerResult>(
      context: context,
      builder: (dialogContext) => Dialog(
        backgroundColor: Theme.of(context).colorScheme.surfaceContainerHigh,
        clipBehavior: Clip.antiAlias,
        child: CatalogSourcePicker(
          viewModel: picker,
          onOpenMedia: (candidate) => Navigator.of(dialogContext).pop((
            candidate: candidate,
            source: null,
            sourceIds: null,
            sourceLanguage: null,
          )),
          readArtwork: widget.readArtwork,
          onManualSearch: (source, sourceIds) =>
              Navigator.of(dialogContext).pop((
                candidate: null,
                source: source,
                sourceIds: sourceIds,
                sourceLanguage: source == null
                    ? picker.state.selectedLanguage
                    : source.languageCode,
              )),
        ),
      ),
    );
  }

  Widget _tabContent({required bool chapters}) => KeyedSubtree(
    key: ValueKey(
      chapters ? 'catalog-chapters-tab' : 'catalog-information-tab',
    ),
    child: Builder(
      builder: (nestedContext) => RefreshIndicator.adaptive(
        onRefresh: () => chapters
            ? (_chapters?.load(forceRefresh: true) ?? Future<void>.value())
            : _viewModel.load(forceRefresh: true),
        child: CustomScrollView(
          key: PageStorageKey((
            widget.initialEntry.id,
            widget.initialEntry.type,
            _identityGeneration,
            chapters,
            chapters ? _chapterScrollVersion : 0,
          )),
          // NestedScrollView owns the controllers that coordinate the shared
          // collapsing hero with each tab's independently retained scroll.
          physics: const BouncingScrollPhysics(
            parent: AlwaysScrollableScrollPhysics(),
          ),
          slivers: [
            SliverOverlapInjector(
              handle: NestedScrollView.sliverOverlapAbsorberHandleFor(
                nestedContext,
              ),
            ),
            if (chapters)
              ..._chapterSlivers()
            else
              SliverToBoxAdapter(
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(
                      maxWidth: HikariBreakpoints.maxContentWidth,
                    ),
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(
                        HikariSpacing.lg,
                        HikariSpacing.xl,
                        HikariSpacing.lg,
                        HikariSpacing.xxxl,
                      ),
                      child: _viewModel.state.initialLoading
                          ? CatalogDetailLoadingBody(
                              title: _viewModel.state.entry.title,
                            )
                          : _viewModel.state.details != null
                          ? CatalogDetailContent(
                              details: _viewModel.state.details!,
                              refreshFailed: _viewModel.state.refreshFailed,
                              descriptionExpanded: _descriptionExpanded,
                              onToggleDescription: () => setState(
                                () => _descriptionExpanded =
                                    !_descriptionExpanded,
                              ),
                              onRetry: () => unawaited(
                                _viewModel.load(forceRefresh: true),
                              ),
                              openRelated: widget.openRelated,
                            )
                          : CatalogDetailFallbackBody(
                              entry: _viewModel.state.entry,
                              failed: _viewModel.state.failed,
                              onRetry: () => unawaited(
                                _viewModel.load(forceRefresh: true),
                              ),
                            ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final textScale = MediaQuery.textScalerOf(context).scale(1);
    final heroHeight =
        (context.isCompact ? 370.0 : 380.0) +
        (textScale - 1.0).clamp(0.0, 1.0) * 160.0;

    return ListenableBuilder(
      listenable: Listenable.merge([_viewModel, ?_chapters]),
      builder: (context, _) {
        final state = _viewModel.state;
        final hasTabBar = state.entry.type != MediaType.anime;
        return HikariScaffold(
          useSafeArea: false,
          body: SafeArea(
            top: false,
            child: NestedScrollView(
              key: ValueKey((
                widget.initialEntry.id,
                widget.initialEntry.type,
                _identityGeneration,
              )),
              headerSliverBuilder: (context, innerBoxIsScrolled) => [
                SliverOverlapAbsorber(
                  handle: NestedScrollView.sliverOverlapAbsorberHandleFor(
                    context,
                  ),
                  sliver: SliverAppBar(
                    pinned: true,
                    // NestedScrollView does not support an outer stretching
                    // header. The shared hero retains its collapsing behavior.
                    expandedHeight: heroHeight + (hasTabBar ? 48.0 : 0.0),
                    bottom: !hasTabBar
                        ? null
                        : TabBar(
                            controller: _tabs,
                            tabs: const [
                              Tab(text: 'Information'),
                              Tab(text: 'Chapters'),
                            ],
                          ),
                    backgroundColor: colors.surface.withValues(alpha: 0.96),
                    surfaceTintColor: Colors.transparent,
                    title: Text(
                      state.entry.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    actions: [
                      HikariRefreshAction(
                        tooltip: 'Refresh',
                        refreshing: _chapterTab
                            ? _chapters?.loading == true
                            : state.refreshing,
                        onPressed: _chapterTab
                            ? (_chapters?.media == null || _chapters!.loading
                                  ? null
                                  : () => unawaited(
                                      _chapters!.load(forceRefresh: true),
                                    ))
                            : state.status == CatalogDetailStatus.loading
                            ? null
                            : () => unawaited(
                                _viewModel.load(forceRefresh: true),
                              ),
                      ),
                    ],
                    flexibleSpace: FlexibleSpaceBar(
                      collapseMode: CollapseMode.pin,
                      background: CatalogDetailHero(
                        entry: state.entry,
                        details: state.details,
                        hasTabBar: hasTabBar,
                        onPrimaryAction: () =>
                            unawaited(_handlePrimaryAction()),
                      ),
                    ),
                  ),
                ),
              ],
              body: state.entry.type == MediaType.anime
                  ? _tabContent(chapters: false)
                  : TabBarView(
                      controller: _tabs,
                      children: [
                        _tabContent(chapters: false),
                        _tabContent(chapters: true),
                      ],
                    ),
            ),
          ),
        );
      },
    );
  }
}
