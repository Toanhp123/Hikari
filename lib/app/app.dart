import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/features/library/library_page.dart';
import 'package:hikari/features/local_media/local_media_page.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';
import 'package:hikari/features/novel_reader/novel_reader_page.dart';
import 'package:hikari/features/player/player_page.dart';
import 'package:hikari/features/remote_manga/manga_chapter_page.dart';
import 'package:hikari/features/remote_manga/remote_manga_search_page.dart';
import 'package:hikari/infrastructure/playback/local_video.dart';

class HikariApp extends StatefulWidget {
  const HikariApp({super.key, required this.dependencies});

  final AppDependencies dependencies;

  @override
  State<HikariApp> createState() => _HikariAppState();
}

class _HikariAppState extends State<HikariApp> with WidgetsBindingObserver {
  late final AppDependencies _dependencies;
  bool _isOpeningMedia = false;

  @override
  void initState() {
    super.initState();
    _dependencies = widget.dependencies;
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
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

  Future<void> _openMedia(BuildContext context, Media media) async {
    if (_isOpeningMedia) return;
    _isOpeningMedia = true;
    try {
      final target = await _dependencies.openMedia.execute(media);
      if (!context.mounted) return;
      await _pushMediaTarget(context, target);
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
    };
    await Navigator.of(
      context,
    ).push(MaterialPageRoute<void>(builder: (_) => page));
  }

  Widget _buildVideoPage(VideoOpenTarget target) {
    final playback = _dependencies.videoSession.open(
      locator: target.locator,
      initialProgress: target.progress.initialProgress,
      saveProgress: target.progress.save,
    );
    return PlayerPage(
      title: target.media.title,
      beforeExit: playback.finish,
      playback: LocalVideo(playback: playback),
    );
  }

  Widget _buildMangaSeriesPage(MangaSeriesOpenTarget target) =>
      MangaChapterPage(
        title: target.media.title,
        sourceName: target.chapterSource.name,
        loadChapters: () => target.chapterSource.chapters(target.media.source),
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
      theme: ThemeData(colorSchemeSeed: Colors.indigo),
      darkTheme: ThemeData(
        colorSchemeSeed: Colors.indigo,
        brightness: Brightness.dark,
      ),
      home: Builder(
        builder: (context) => LocalMediaPage(
          supported: localSource.isAvailable,
          scanSelectedRoot: localSource.scanSelectedRoot,
          chooseRoot: localSource.chooseRoot,
          library: libraryRepository,
          openMedia: _openMedia,
          openRemote: () => Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => RemoteMangaSearchPage(
                sources: _dependencies.mangaSearchSources,
                openMedia: _openMedia,
                library: libraryRepository,
              ),
            ),
          ),
          openLibrary: () async {
            await Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => LibraryPage(
                  repository: libraryRepository,
                  openMedia: _openMedia,
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}
