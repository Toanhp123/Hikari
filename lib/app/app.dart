import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/app/navigation/app_navigation_shell.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/features/catalog/catalog_detail_page.dart';
import 'package:hikari/features/catalog/catalog_search_page.dart';
import 'package:hikari/features/home/home_page.dart';
import 'package:hikari/features/library/library_page.dart';
import 'package:hikari/features/local_media/local_media_page.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';
import 'package:hikari/features/novel_reader/novel_reader_page.dart';
import 'package:hikari/features/novel_reader/publication_reader_page.dart';
import 'package:hikari/features/player/player_page.dart';
import 'package:hikari/features/player/widgets/video_surface.dart';
import 'package:hikari/features/remote_manga/manga_series_page.dart';
import 'package:hikari/features/remote_novel/novel_series_page.dart';
import 'package:hikari/features/source_search/source_search_page.dart';
import 'package:hikari/features/source_search/source_search_view_model.dart'
    show SourceSearchFilter;
import 'package:hikari/features/settings/settings_page.dart';

class HikariApp extends StatefulWidget {
  const HikariApp({super.key, required this.dependencies});

  final AppDependencies dependencies;

  @override
  State<HikariApp> createState() => _HikariAppState();
}

class _HikariAppState extends State<HikariApp> with WidgetsBindingObserver {
  late final AppDependencies _dependencies;
  final _navigationController = AppNavigationController();
  bool _isOpeningMedia = false;
  bool _isOled = false;
  Color? _accentColor;
  int _homeRefreshRevision = 0;
  int _localScanRevision = 0;

  Future<bool> _chooseLocalRoot({bool fromSettings = false}) async {
    final selected = await _dependencies.localMediaSource.chooseRoot();
    if (selected && mounted) {
      setState(() {
        _homeRefreshRevision++;
        // Local already scans its own selection; only external changes refresh it.
        if (fromSettings) _localScanRevision++;
      });
    }
    return selected;
  }

  @override
  void initState() {
    super.initState();
    _dependencies = widget.dependencies;
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

  void _openCatalogDetail(BuildContext context, CatalogEntry entry) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => CatalogDetailPage(
          initialEntry: entry,
          loadDetails: _dependencies.loadCatalogEntryDetails,
          openRelated: (related) => _openCatalogDetail(context, related),
          openSourceSearch: (item) => _openCatalogSourceSearch(context, item),
        ),
      ),
    );
  }

  void _openCatalogSourceSearch(BuildContext context, CatalogEntry entry) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => SourceSearchPage(
          openMedia: _openMedia,
          library: _dependencies.libraryRepository,
          searchManga: _dependencies.searchManga,
          searchNovels: _dependencies.searchNovels,
          scanLocalMedia: _dependencies.localMediaSource.isAvailable
              ? _dependencies.localMediaSource.scanSelectedRoot
              : null,
          initialQuery: entry.title,
          initialFilter: switch (entry.type) {
            MediaType.anime => SourceSearchFilter.anime,
            MediaType.manga => SourceSearchFilter.manga,
            MediaType.lightNovel => SourceSearchFilter.novel,
          },
        ),
      ),
    );
  }

  Future<void> _openMedia(BuildContext context, Media media) async {
    if (_isOpeningMedia) return;
    _isOpeningMedia = true;
    MediaOpenTarget? target;
    try {
      target = await _dependencies.openMedia.execute(media);
      if (!context.mounted) return;
      await _pushMediaTarget(context, target);
      if (mounted) setState(() => _homeRefreshRevision++);
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Could not open this item or its saved progress. Check source access and try again.',
            ),
          ),
        );
      }
    } finally {
      _isOpeningMedia = false;
      try {
        await target?.release();
      } catch (error, stackTrace) {
        FlutterError.reportError(
          FlutterErrorDetails(exception: error, stack: stackTrace),
        );
        if (context.mounted) {
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
        openChapter: _openNovelChapter,
        library: _dependencies.libraryRepository,
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
    title: target.media.title,
    sourceName: target.seriesSource.name,
    loadDetails: target.loadDetails,
    readArtwork: target.seriesSource is ArtworkSource
        ? (target.seriesSource as ArtworkSource).readArtwork
        : null,
    openChapter: _openMangaChapter,
  );

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
  ) async {
    final target = await _dependencies.openNovelChapter.execute(chapter);
    if (!context.mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => NovelReaderPage(
          title: chapter.title,
          loadContent: () async => target.content,
          readResource: target.source.readResource,
          initialProgress: target.progress.initialProgress,
          saveProgress: target.progress.save,
        ),
      ),
    );
  }

  Future<void> _openMangaChapter(
    BuildContext context,
    MangaChapter chapter,
  ) async {
    final target = await _dependencies.openMangaChapter.execute(chapter);
    if (!context.mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => MangaReaderPage(
          title: target.chapter.title,
          credit: [
            target.source.name,
            if (target.chapter.scanlator != null) target.chapter.scanlator!,
          ].join(' · '),
          loadPages: () async => target.pages,
          readPage: target.source.readPage,
          initialProgress: target.progress.initialProgress,
          saveProgress: target.progress.save,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final localSource = _dependencies.localMediaSource;
    final libraryRepository = _dependencies.libraryRepository;

    return MaterialApp(
      title: 'Hikari',
      theme: HikariTheme.darkTheme(oled: _isOled, accentColor: _accentColor),
      darkTheme: HikariTheme.darkTheme(
        oled: _isOled,
        accentColor: _accentColor,
      ),
      home: Builder(
        builder: (context) => AppNavigationShell(
          controller: _navigationController,
          tabs: {
            AppTab.home: HomePage(
              openMedia: _openMedia,
              library: libraryRepository,
              progressRepository: _dependencies.progressRepository,
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
            ),
            AppTab.local: LocalMediaPage(
              scanSelectedRoot: localSource.scanSelectedRoot,
              chooseRoot: _chooseLocalRoot,
              openMedia: _openMedia,
              library: libraryRepository,
              supported: localSource.isAvailable,
              scanRevision: _localScanRevision,
            ),
            AppTab.library: LibraryPage(
              repository: libraryRepository,
              openMedia: _openMedia,
            ),
            AppTab.settings: SettingsPage(
              isOled: _isOled,
              onToggleOled: (value) => setState(() => _isOled = value),
              onSelectAccent: (value) => setState(() => _accentColor = value),
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
