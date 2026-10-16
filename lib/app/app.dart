import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/app/navigation/app_navigation_shell.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/application/media/open_series_continuation.dart';
import 'package:hikari/application/progress/save_series_chapter_progress.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/continue_reading_item.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/settings/appearance_settings.dart';
import 'package:hikari/features/catalog/catalog_detail_page.dart';
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';
import 'package:hikari/features/catalog/catalog_search_page.dart';
import 'package:hikari/features/home/home_page.dart';
import 'package:hikari/features/library/library_page.dart';
import 'package:hikari/features/local_media/local_media_page.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';
import 'package:hikari/features/remote_manga/manga_chapter_reader_page.dart';
import 'package:hikari/features/remote_novel/novel_chapter_reader_page.dart';
import 'package:hikari/features/novel_reader/novel_reader_page.dart';
import 'package:hikari/features/novel_reader/publication_reader_page.dart';
import 'package:hikari/features/player/player_page.dart';
import 'package:hikari/features/player/widgets/video_surface.dart';
import 'package:hikari/features/remote_manga/manga_series_page.dart';
import 'package:hikari/features/remote_novel/novel_series_page.dart';
import 'package:hikari/features/remote_manga/manga_chapter_reader_view_model.dart';
import 'package:hikari/features/remote_novel/novel_chapter_reader_view_model.dart';
import 'package:hikari/features/reading/continue_reading_page.dart';
import 'package:hikari/features/source_search/source_search_page.dart';
import 'package:hikari/features/source_search/source_search_view_model.dart'
    show SourceSearchFilter;
import 'package:hikari/features/settings/settings_page.dart';

class HikariApp extends StatefulWidget {
  const HikariApp({
    super.key,
    required this.dependencies,
    this.initialAppearance = const AppearanceSettings(),
    this.appearanceRepository,
  });

  final AppDependencies dependencies;
  final AppearanceSettings initialAppearance;
  final AppearanceSettingsRepository? appearanceRepository;

  @override
  State<HikariApp> createState() => _HikariAppState();
}

class _HikariAppState extends State<HikariApp> with WidgetsBindingObserver {
  late final AppDependencies _dependencies;
  final _navigationController = AppNavigationController();
  bool _isOpeningMedia = false;
  late bool _isOled;
  Color? _accentColor;
  Future<void> _pendingAppearanceSave = Future<void>.value();
  AppearanceSaveStatus? _appearanceSaveStatus;
  int _appearanceSaveRevision = 0;
  int _homeRefreshRevision = 0;
  int _libraryProgressRevision = 0;
  int _localScanRevision = 0;
  int _settingsFolderRevision = 0;

  void _setAppearance({bool? oled, Color? accent, bool updateAccent = false}) {
    setState(() {
      if (oled != null) _isOled = oled;
      if (updateAccent) _accentColor = accent;
    });
    _saveAppearance();
  }

  void _saveAppearance() {
    final repository = widget.appearanceRepository;
    if (repository == null) return;

    // Capture the current selection, including on retry. Older completions
    // must never mark a newer selection as saved (or failed).
    final revision = ++_appearanceSaveRevision;
    final snapshot = AppearanceSettings(
      isOled: _isOled,
      accentArgb: _accentColor?.toARGB32(),
    );
    setState(() => _appearanceSaveStatus = AppearanceSaveStatus.saving);

    // Keep writes ordered so an older attempt cannot overwrite newer choices.
    // A failed attempt is handled inside the chain, allowing retries and later
    // changes to run normally.
    _pendingAppearanceSave = _pendingAppearanceSave.then((_) async {
      try {
        await repository.save(snapshot);
        if (mounted && revision == _appearanceSaveRevision) {
          setState(() => _appearanceSaveStatus = AppearanceSaveStatus.saved);
        }
      } catch (error, stack) {
        if (mounted && revision == _appearanceSaveRevision) {
          setState(() => _appearanceSaveStatus = AppearanceSaveStatus.failed);
        }
        FlutterError.reportError(
          FlutterErrorDetails(
            exception: error,
            stack: stack,
            context: ErrorDescription('persisting appearance settings'),
          ),
        );
      }
    });
  }

  Future<bool> _chooseLocalRoot({bool fromSettings = false}) async {
    final selected = await _dependencies.localMediaSource.chooseRoot();
    if (selected && mounted) {
      setState(() {
        _homeRefreshRevision++;
        // Local already scans its own selection; only external changes refresh it.
        if (fromSettings) {
          _localScanRevision++;
        } else {
          _settingsFolderRevision++;
        }
      });
    }
    return selected;
  }

  @override
  void initState() {
    super.initState();
    _dependencies = widget.dependencies;
    _isOled = widget.initialAppearance.isOled;
    final accent = widget.initialAppearance.accentArgb;
    _accentColor = accent == null ? null : Color(accent);
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _navigationController.dispose();
    unawaited(
      _dependencies.dispose().catchError((Object error) {
        FlutterError.reportError(FlutterErrorDetails(exception: error));
      }),
    );
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    unawaited(
      _dependencies.videoSession
          .setForeground(state == AppLifecycleState.resumed)
          .catchError((Object error) {
            FlutterError.reportError(FlutterErrorDetails(exception: error));
          }),
    );
  }

  late final _catalogReadingTarget = LoadSeriesReadingTarget(
    _dependencies.seriesContinuationRepository,
  );

  Future<void> _openMangaSeriesChapter(
    BuildContext context,
    Media selected,
    MangaChapter chapter,
    List<MangaChapter> sequence,
    bool Function() current,
  ) async {
    MediaOpenTarget? target;
    try {
      target = await _dependencies.openMedia.execute(selected);
      if (!context.mounted || !current()) return;
      if (target is! MangaSeriesOpenTarget) {
        throw StateError('Series source unavailable.');
      }
      await _openMangaChapter(
        context,
        chapter,
        sequence,
        seriesRef: selected.source,
        canNavigate: current,
        retainUntilSettled: (settled) {
          final owned = target!;
          target = null;
          unawaited(
            settled.then<void>((_) {}).whenComplete(owned.release).catchError((
              Object error,
              StackTrace stack,
            ) {
              FlutterError.reportError(
                FlutterErrorDetails(exception: error, stack: stack),
              );
            }),
          );
        },
      );
    } finally {
      await target?.release();
    }
  }

  Future<void> _openNovelSeriesChapter(
    BuildContext context,
    Media selected,
    NovelChapter chapter,
    List<NovelChapter> sequence,
    bool Function() current,
  ) async {
    MediaOpenTarget? target;
    try {
      target = await _dependencies.openMedia.execute(selected);
      if (!context.mounted || !current()) return;
      if (target is! NovelSeriesOpenTarget) {
        throw StateError('Series source unavailable.');
      }
      await _openNovelChapter(
        context,
        chapter,
        sequence,
        seriesRef: selected.source,
        canNavigate: current,
        retainUntilSettled: (settled) {
          final owned = target!;
          target = null;
          unawaited(
            settled.then<void>((_) {}).whenComplete(owned.release).catchError((
              Object error,
              StackTrace stack,
            ) {
              FlutterError.reportError(
                FlutterErrorDetails(exception: error, stack: stack),
              );
            }),
          );
        },
      );
    } finally {
      await target?.release();
    }
  }

  Future<void> _openCatalogDetail(
    BuildContext context,
    CatalogEntry entry,
  ) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => CatalogDetailPage(
          initialEntry: entry,
          loadDetails: _dependencies.loadCatalogEntryDetails,
          resolveCatalogSource: _dependencies.resolveCatalogSource,
          readArtwork: _dependencies.readSourceArtwork.execute,
          openMedia: _openMedia,
          chapterOpener: _dependencies.openMedia,
          chapterLists: _dependencies.openMedia.chapterLists!,
          loadReadingTarget: _catalogReadingTarget,
          library: _dependencies.libraryRepository,
          sourceSelections: _dependencies.catalogSourceSelections,
          openMangaChapter: _openMangaSeriesChapter,
          openNovelChapter: _openNovelSeriesChapter,
          selectSourceSearch:
              (item, sourceId, sourceName, sourceIds, language) =>
                  _openCatalogSourceSearch(
                    context,
                    item,
                    sourceId: sourceId,
                    sourceName: sourceName,
                    sourceIds: sourceIds,
                    sourceLanguage: language,
                    selectOnly: true,
                  ),
          openRelated: (related) => _openCatalogDetail(context, related),
          openSourceSearch: (item, sourceId, sourceName, sourceIds, language) =>
              _openCatalogSourceSearch(
                context,
                item,
                sourceId: sourceId,
                sourceName: sourceName,
                sourceIds: sourceIds,
                sourceLanguage: language,
              ),
        ),
      ),
    );
    if (mounted) setState(() => _homeRefreshRevision++);
  }

  void _openStandaloneSourceSearch(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => SourceSearchPage(
          openMedia: _openMedia,
          searchManga: _dependencies.searchManga,
          searchNovels: _dependencies.searchNovels,
          scanLocalMedia: _dependencies.localMediaSource.isAvailable
              ? _dependencies.localMediaSource.scanSelectedRoot
              : null,
          library: _dependencies.libraryRepository,
          readArtwork: _dependencies.readSourceArtwork.execute,
        ),
      ),
    );
  }

  Future<CatalogSourcePickerCandidate?> _openCatalogSourceSearch(
    BuildContext context,
    CatalogEntry entry, {
    SourceId? sourceId,
    String? sourceName,
    Set<SourceId>? sourceIds,
    String? sourceLanguage,
    bool selectOnly = false,
  }) {
    final mediaLabel = switch (entry.type) {
      MediaType.anime => 'Anime',
      MediaType.manga => 'Manga',
      MediaType.lightNovel => 'Novel',
    };
    final languageLabel = switch (sourceLanguage) {
      'all' => 'Multiple languages',
      final language? => language.toUpperCase(),
      null => sourceName == null ? 'All languages' : 'Language unspecified',
    };
    return Navigator.of(context).push<CatalogSourcePickerCandidate>(
      MaterialPageRoute<CatalogSourcePickerCandidate>(
        builder: (_) => SourceSearchPage(
          openMedia: selectOnly
              ? (searchContext, media, {previewMetadata}) =>
                    Navigator.of(searchContext).pop(
                      CatalogSourcePickerCandidate(
                        media: media,
                        metadata: previewMetadata,
                      ),
                    )
              : _openMedia,
          library: _dependencies.libraryRepository,
          readArtwork: _dependencies.readSourceArtwork.execute,
          searchManga: _dependencies.searchManga,
          searchNovels: _dependencies.searchNovels,
          scanLocalMedia: _dependencies.localMediaSource.isAvailable
              ? _dependencies.localMediaSource.scanSelectedRoot
              : null,
          initialQuery: entry.title,
          initialSourceId: sourceId,
          sourceName: sourceName,
          sourceIds: sourceIds,
          fixedMediaType: entry.type,
          catalogScopeLabel: '${sourceName ?? mediaLabel} · $languageLabel',
          initialFilter: switch (entry.type) {
            MediaType.anime => SourceSearchFilter.anime,
            MediaType.manga => SourceSearchFilter.manga,
            MediaType.lightNovel => SourceSearchFilter.novel,
          },
        ),
      ),
    );
  }

  Future<void> _openMedia(
    BuildContext context,
    Media media, {
    MediaMetadata? previewMetadata,
    bool Function()? canNavigate,
  }) async {
    final contextual = canNavigate != null;
    bool current() => context.mounted && (canNavigate?.call() ?? true);
    if (!current() || (!contextual && _isOpeningMedia)) return;
    if (!contextual) _isOpeningMedia = true;
    MediaOpenTarget? target;
    try {
      target = await _dependencies.openMedia.execute(
        media,
        previewMetadata: previewMetadata,
      );
      if (!context.mounted || !current()) return;
      await _pushMediaTarget(context, target);
      if (mounted) setState(() => _homeRefreshRevision++);
    } catch (_) {
      if (context.mounted && current()) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Could not open this item or its saved progress. Check source access and try again.',
            ),
          ),
        );
      }
    } finally {
      if (!contextual) _isOpeningMedia = false;
      try {
        await target?.release();
      } catch (error, stackTrace) {
        FlutterError.reportError(
          FlutterErrorDetails(exception: error, stack: stackTrace),
        );
        if (context.mounted && current()) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'Could not clean up temporary reading files. Restart to retry cleanup.',
              ),
            ),
          );
        }
      }
    }
  }

  Future<void> _pushMediaTarget(
    BuildContext context,
    MediaOpenTarget target,
  ) async {
    final page = switch (target) {
      VideoOpenTarget video => _buildVideoPage(video),
      MangaSeriesOpenTarget series => _buildMangaSeriesPage(series),
      MangaReaderOpenTarget reader => _buildMangaReaderPage(reader),
      NovelReaderOpenTarget novel => _buildNovelReaderPage(novel),
      NovelSeriesOpenTarget novel => NovelSeriesPage(
        target: novel,
        dependencyOwner: (
          novel.source,
          _dependencies.seriesContinuationRepository,
        ),
        loadReadingTarget: (chapters) =>
            LoadSeriesReadingTarget(_dependencies.seriesContinuationRepository)
                .execute(novel.media.source, chapters),
        openChapter: (context, chapter, sequence, canNavigate) =>
            _openNovelSeriesChapter(
              context,
              novel.media,
              chapter,
              sequence,
              canNavigate,
            ),
        library: _dependencies.libraryRepository,
        readArtwork: _dependencies.readSourceArtwork.execute,
      ),
      PublicationReaderOpenTarget publication => _buildPublicationReaderPage(
        publication,
      ),
    };
    await Navigator.of(context)
        .push(MaterialPageRoute<void>(builder: (_) => page));
  }

  Widget _buildVideoPage(VideoOpenTarget target) {
    final playback = _dependencies.videoSession.open(
      locator: target.locator,
      initialProgress: target.progress.initialProgress,
      saveProgress: target.progress.save,
    );
    return PlayerPage(
      title: target.media.title,
      controls: playback,
      beforeExit: playback.finish,
      playback: VideoSurface(
        changes: playback.surfaceChanges,
        controller: () => playback.controller,
        loading: () => playback.loading,
        error: () => playback.error,
      ),
    );
  }

  Widget _buildMangaSeriesPage(MangaSeriesOpenTarget target) => MangaSeriesPage(
    media: target.media,
    previewMetadata: target.previewMetadata,
    dependencyOwner: (
      target.seriesSource,
      _dependencies.seriesContinuationRepository,
    ),
    sourceName: target.seriesSource.name,
    loadDetails: target.loadDetails,
    readCachedDetails: target.readCachedDetails,
    loadReadingTarget: (chapters) =>
        LoadSeriesReadingTarget(_dependencies.seriesContinuationRepository)
            .execute(target.media.source, chapters),
    readArtwork: target.seriesSource is ArtworkSource
        ? _dependencies.readSourceArtwork.execute
        : null,
    library: _dependencies.libraryRepository,
    openChapter: (context, chapter, sequence, canNavigate) =>
        _openMangaSeriesChapter(
          context,
          target.media,
          chapter,
          sequence,
          canNavigate,
        ),
  );

  Future<void> _continueReading(
    BuildContext context,
    ContinueReadingItem item,
  ) async {
    if (item.chapter == null) {
      await _openMedia(context, item.media);
      return;
    }
    if (_isOpeningMedia) return;
    _isOpeningMedia = true;
    try {
      await _continueReadingInner(context, item);
    } finally {
      _isOpeningMedia = false;
      if (mounted) setState(() => _homeRefreshRevision++);
    }
  }

  Future<void> _continueReadingInner(
    BuildContext context,
    ContinueReadingItem item,
  ) async {
    final chapter = item.chapter;
    if (chapter == null) return;
    SeriesContinuationOpenTarget? target;
    final pendingOpens = <Future<ReaderLaunch>>[];
    try {
      // Resolve stable source capability only. Details must not gate the route.
      final continuation = await _dependencies.openSeriesContinuation.execute(
        item.media,
        chapter,
      );
      target = continuation;
      if (!context.mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => ContinueReadingPage(
            open: () {
              final opening = switch (continuation) {
                MangaContinuationOpenTarget() => _prepareMangaChapterReader(
                  continuation.selected,
                  [continuation.selected],
                  seriesRef: continuation.series.source,
                  cachedSequence: continuation.readCachedSequence,
                  refreshSequence: continuation.refreshSequence,
                ),
                NovelContinuationOpenTarget() => _prepareNovelChapterReader(
                  continuation.selected,
                  [continuation.selected],
                  seriesRef: continuation.series.source,
                  cachedSequence: continuation.readCachedSequence,
                  refreshSequence: continuation.refreshSequence,
                ),
              };
              pendingOpens.add(opening);
              return opening;
            },
          ),
        ),
      );
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Could not open saved chapter. Try again.'),
          ),
        );
      }
    } finally {
      final opened = target;
      if (opened != null) {
        // A user can back out while a source request is still in flight.
        // Keep the source lease until the request has finished, but unblock Home.
        unawaited(() async {
          try {
            for (final pending in pendingOpens) {
              try {
                final reader = await pending;
                await reader.settled;
              } catch (_) {
                // The route owns the loading error UI.
              }
            }
            await opened.release();
          } catch (error, stack) {
            FlutterError.reportError(
              FlutterErrorDetails(
                exception: error,
                stack: stack,
                context: ErrorDescription('releasing Continue Reading source'),
              ),
            );
          }
        }());
      }
    }
  }

  Widget _buildMangaReaderPage(MangaReaderOpenTarget target) => MangaReaderPage(
    title: target.media.title,
    loadPages: () => target.pageSource.pages(target.media.source),
    readPage: target.pageSource.readPage,
    initialProgress: target.progress.initialProgress,
    saveProgress: target.progress.save,
  );

  Widget _buildNovelReaderPage(NovelReaderOpenTarget target) => NovelReaderPage(
    title: target.media.title,
    loadText: () => target.textSource.readText(target.media.source),
    initialProgress: target.progress.initialProgress,
    saveProgress: target.progress.save,
  );

  Widget _buildPublicationReaderPage(PublicationReaderOpenTarget target) =>
      PublicationReaderPage(
        title: target.media.title,
        publication: target.media.source,
        source: target.publicationSource,
        initialProgress: target.progress.initialProgress,
        saveProgress: target.progress.save,
      );

  Future<void> _openNovelChapter(
    BuildContext context,
    NovelChapter chapter,
    List<NovelChapter> chaptersInReadingOrder, {
    SourceMediaRef? seriesRef,
    bool Function()? canNavigate,
    void Function(Future<void>)? retainUntilSettled,
  }) async {
    final ready = await _prepareNovelChapterReader(
      chapter,
      chaptersInReadingOrder,
      seriesRef: seriesRef,
    );
    MaterialPageRoute<void>? route;
    var installed = false;
    try {
      if (!context.mounted || (canNavigate != null && !canNavigate())) {
        ready.abandon();
        return;
      }
      route = MaterialPageRoute<void>(builder: (_) => ready.reader);
      final returned = Navigator.of(context).push(route);
      installed = true;
      await returned;
    } catch (_) {
      ready.abandon();
      rethrow;
    } finally {
      retainUntilSettled?.call(
        Future.wait<void>([
          ready.settled,
          if (installed) route!.completed.then<void>((_) {}),
        ]),
      );
    }
  }

  Future<ReaderLaunch> _prepareNovelChapterReader(
    NovelChapter chapter,
    List<NovelChapter> chaptersInReadingOrder, {
    SourceMediaRef? seriesRef,
    Future<List<NovelChapter>?> Function()? cachedSequence,
    Future<List<NovelChapter>> Function()? refreshSequence,
  }) async {
    final initialTarget = await _dependencies.openNovelChapter.execute(chapter);
    final saveProgress = seriesRef == null
        ? null
        : SaveSeriesChapterProgress(
            _dependencies.seriesContinuationRepository,
            seriesRef,
          );
    final viewModel = NovelChapterReaderViewModel(
      initialTarget: initialTarget,
      chaptersInReadingOrder: chaptersInReadingOrder,
      openChapter: _dependencies.openNovelChapter,
      onChapterActivated: saveProgress == null
          ? null
          : (target) => saveProgress.activate(
              target.progress,
              target.chapter.source,
              TextPosition(progression: 0),
            ),
      onProgress: (target, position, completed) async {
        if (saveProgress == null) {
          await target.progress.save(position, completed);
        } else {
          await saveProgress.execute(
            target.progress,
            target.chapter.source,
            position,
            completed,
          );
        }
      },
    );
    final settled = cachedSequence == null && refreshSequence == null
        ? Future<void>.value()
        : () async {
            if (cachedSequence != null) {
              try {
                final cached = await cachedSequence();
                if (cached != null) viewModel.reconcileSequence(cached);
              } catch (_) {
                // Missing or corrupt cache must not block reading.
              }
            }
            if (refreshSequence != null) {
              try {
                viewModel.reconcileSequence(await refreshSequence());
              } catch (_) {
                // The last valid sequence remains available offline.
              }
            }
          }();
    return (
      reader: NovelChapterReaderPage(
        viewModel: viewModel,
        reloadContent: _dependencies.readNovelChapterContent.reload,
        readResource: _dependencies.readNovelResource.execute,
        reloadResource: _dependencies.readNovelResource.reload,
        createPrefetch: _dependencies.createNovelChapterPrefetch,
        prefetchChapter: _dependencies.prefetchNovelChapter,
      ),
      settled: Future.wait([settled, viewModel.settled]),
      abandon: () {
        viewModel.close();
        viewModel.dispose();
      },
    );
  }

  Future<void> _openMangaChapter(
    BuildContext context,
    MangaChapter chapter,
    List<MangaChapter> chaptersInReadingOrder, {
    SourceMediaRef? seriesRef,
    bool Function()? canNavigate,
    void Function(Future<void>)? retainUntilSettled,
  }) async {
    final ready = await _prepareMangaChapterReader(
      chapter,
      chaptersInReadingOrder,
      seriesRef: seriesRef,
    );
    MaterialPageRoute<void>? route;
    var installed = false;
    try {
      if (!context.mounted || (canNavigate != null && !canNavigate())) {
        ready.abandon();
        return;
      }
      route = MaterialPageRoute<void>(builder: (_) => ready.reader);
      final returned = Navigator.of(context).push(route);
      installed = true;
      await returned;
    } catch (_) {
      ready.abandon();
      rethrow;
    } finally {
      retainUntilSettled?.call(
        Future.wait<void>([
          ready.settled,
          if (installed) route!.completed.then<void>((_) {}),
        ]),
      );
    }
  }

  Future<ReaderLaunch> _prepareMangaChapterReader(
    MangaChapter chapter,
    List<MangaChapter> chaptersInReadingOrder, {
    SourceMediaRef? seriesRef,
    Future<List<MangaChapter>?> Function()? cachedSequence,
    Future<List<MangaChapter>> Function()? refreshSequence,
  }) async {
    final initialTarget = await _dependencies.openMangaChapter.execute(chapter);
    final chapterPrefetch = _dependencies.createMangaChapterPrefetch();
    final saveProgress = seriesRef == null
        ? null
        : SaveSeriesChapterProgress(
            _dependencies.seriesContinuationRepository,
            seriesRef,
          );
    final viewModel = MangaChapterReaderViewModel(
      initialTarget: initialTarget,
      chaptersInReadingOrder: chaptersInReadingOrder,
      openChapter: _dependencies.openMangaChapter,
      pageListLoader: chapterPrefetch.loadPages,
      chapterPrefetch: chapterPrefetch,
      onChapterActivated: saveProgress == null
          ? null
          : (target) => saveProgress.activate(
              target.progress,
              target.chapter.source,
              PagePosition(pageIndex: 0, pageCount: target.pages.length),
            ),
      onProgress: (target, position, completed) async {
        if (saveProgress == null) {
          await target.progress.save(position, completed);
        } else {
          await saveProgress.execute(
            target.progress,
            target.chapter.source,
            position,
            completed,
          );
        }
      },
    );
    final settled = cachedSequence == null && refreshSequence == null
        ? Future<void>.value()
        : () async {
            if (cachedSequence != null) {
              try {
                final cached = await cachedSequence();
                if (cached != null) viewModel.reconcileSequence(cached);
              } catch (_) {
                // Missing or corrupt cache must not block reading.
              }
            }
            if (refreshSequence != null) {
              try {
                viewModel.reconcileSequence(await refreshSequence());
              } catch (_) {
                // The last valid sequence remains available offline.
              }
            }
          }();
    return (
      reader: MangaChapterReaderPage(
        viewModel: viewModel,
        readPage: _dependencies.readMangaPage.execute,
        reloadPage: _dependencies.readMangaPage.reload,
        fetchFreshPage: _dependencies.readMangaPage.fetchFresh,
        persistFreshPage: _dependencies.readMangaPage.persist,
        fetchFreshPages: _dependencies.readMangaPageList.fetchFresh,
        persistPages: _dependencies.readMangaPageList.persist,
        createPrefetch: _dependencies.createMangaPagePrefetch,
        prefetchPages: _dependencies.prefetchMangaPages,
      ),
      settled: Future.wait([settled, viewModel.settled]),
      abandon: () {
        viewModel.close();
        viewModel.dispose();
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final localSource = _dependencies.localMediaSource;
    final libraryRepository = _dependencies.libraryRepository;

    return MaterialApp(
      title: 'Hikari',
      theme: HikariTheme.darkTheme(oled: _isOled, accentColor: _accentColor),
      home: Builder(
        builder: (context) => AppNavigationShell(
          controller: _navigationController,
          onTabChanged: (tab) {
            if (tab == AppTab.library) {
              setState(() => _libraryProgressRevision++);
            }
          },
          tabs: {
            AppTab.home: HomePage(
              openMedia: _openMedia,
              library: libraryRepository,
              readArtwork: _dependencies.readSourceArtwork.execute,
              loadContinueReading: _dependencies.loadContinueReading,
              onContinue: _continueReading,
              discoverCatalog: _dependencies.discoverCatalog,
              openCatalogDetail: _openCatalogDetail,
              refreshRevision: _homeRefreshRevision,
              onNavigateToSearch: () =>
                  _navigationController.selectTab(AppTab.search),
              onNavigateToLibrary: () =>
                  _navigationController.selectTab(AppTab.library),
            ),
            AppTab.search: CatalogSearchPage(
              searchCatalog: _dependencies.searchCatalog,
              openDetail: _openCatalogDetail,
              openSourceSearch: _openStandaloneSourceSearch,
            ),
            AppTab.local: LocalMediaPage(
              scanSelectedRoot: localSource.scanSelectedRootSnapshot,
              readArtwork: _dependencies.readSourceArtwork.execute,
              chooseRoot: _chooseLocalRoot,
              openMedia: _openMedia,
              library: libraryRepository,
              supported: localSource.isAvailable,
              scanRevision: _localScanRevision,
            ),
            AppTab.library: LibraryPage(
              repository: libraryRepository,
              loadContinueReading: _dependencies.loadContinueReading,
              progressRevision: _homeRefreshRevision + _libraryProgressRevision,
              readArtwork: _dependencies.readSourceArtwork.execute,
              openMedia: _openMedia,
              onNavigateToSearch: () =>
                  _navigationController.selectTab(AppTab.search),
            ),
            AppTab.settings: SettingsPage(
              loadCacheUsage: _dependencies.cacheManagement?.usage,
              onClearCache: _dependencies.cacheManagement?.clear,
              isOled: _isOled,
              selectedAccent: _accentColor,
              appearanceSaveStatus: widget.appearanceRepository == null
                  ? null
                  : _appearanceSaveStatus,
              onRetryAppearanceSave: widget.appearanceRepository == null
                  ? null
                  : _saveAppearance,
              onToggleOled: (value) => _setAppearance(oled: value),
              onSelectAccent: (value) =>
                  _setAppearance(accent: value, updateAccent: true),
              localFolderRevision: _settingsFolderRevision,
              loadLocalFolderName: localSource.isAvailable
                  ? localSource.selectedRootName
                  : null,
              onChooseLocalFolder: localSource.isAvailable
                  ? () => _chooseLocalRoot(fromSettings: true)
                  : null,
            ),
          },
        ),
      ),
    );
  }
}
