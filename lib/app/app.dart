import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/library/library_page.dart';
import 'package:hikari/features/local_media/local_media_page.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';
import 'package:hikari/features/novel_reader/novel_reader_page.dart';
import 'package:hikari/features/player/player_page.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/playback/local_video.dart';
import 'package:hikari/infrastructure/playback/video_progress.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/user_state_repositories.dart';

class HikariApp extends StatefulWidget {
  const HikariApp({super.key, this.database, this.source});
  final UserDatabase? database;
  final LocalMediaSource? source;
  @override
  State<HikariApp> createState() => _HikariAppState();
}

class _HikariAppState extends State<HikariApp> {
  late final UserDatabase _database;
  late final LibraryRepository _library;
  late final ProgressRepository _progress;
  late final LocalMediaSource _local;
  bool _opening = false;

  @override
  void initState() {
    super.initState();
    _database = widget.database ?? UserDatabase();
    _library = SqliteLibraryRepository(_database);
    _progress = SqliteProgressRepository(_database);
    _local = widget.source ?? LocalMediaSource();
  }

  @override
  void dispose() {
    if (widget.database == null) unawaited(_database.close());
    super.dispose();
  }

  Future<void> _open(BuildContext context, Media media) async {
    if (_opening) return;
    _opening = true;
    try {
      final MediaSource? source = media.source.sourceId == _local.id
          ? _local
          : null;
      if (source == null || !_local.supported) {
        throw StateError('Source is unavailable on this device.');
      }
      final saved = await _progress.load(media.source);
      if (!context.mounted) return;
      Future<void> save(ProgressPosition position, bool completed) =>
          _progress.save(
            MediaProgress(
              media: media.source,
              position: position,
              completed: completed,
              updatedAt: DateTime.now().toUtc(),
            ),
          );
      final Widget page;
      switch (media.type) {
        case MediaType.anime:
          final tracker = VideoProgressTracker(saveProgress: save);
          page = PlayerPage(
            title: media.title,
            beforeExit: tracker.finish,
            playback: LocalVideo(
              locator: media.source.itemId,
              initialProgress: saved,
              progress: tracker,
            ),
          );
        case MediaType.manga:
          if (source is! MangaPageSource) {
            throw StateError('Pages unavailable.');
          }
          page = MangaReaderPage(
            title: media.title,
            loadPages: () => source.pages(media.source),
            readPage: source.readPage,
            initialProgress: saved,
            saveProgress: save,
          );
        case MediaType.lightNovel:
          if (source is! NovelTextSource) throw StateError('Text unavailable.');
          page = NovelReaderPage(
            title: media.title,
            loadText: () => source.readText(media.source),
            initialProgress: saved,
            saveProgress: save,
          );
      }
      await Navigator.of(context)
          .push(MaterialPageRoute<void>(builder: (_) => page));
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
      _opening = false;
    }
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Hikari',
    theme: ThemeData(colorSchemeSeed: Colors.indigo),
    darkTheme: ThemeData(
      colorSchemeSeed: Colors.indigo,
      brightness: Brightness.dark,
    ),
    home: Builder(
      builder: (context) => LocalMediaPage(
        supported: _local.supported,
        scanSelectedRoot: _local.scanSelectedRoot,
        chooseRoot: _local.chooseRoot,
        library: _library,
        openMedia: _open,
        openLibrary: () async {
          await Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) =>
                  LibraryPage(repository: _library, openMedia: _open),
            ),
          );
        },
      ),
    ),
  );
}
