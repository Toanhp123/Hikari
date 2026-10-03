import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/open_novel_chapter.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/playback/media_kit_video_session.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';

/// Composition root for application workflows and replaceable infrastructure.
final class AppDependencies {
  AppDependencies._(
    this._database,
    this._ownsDatabase, {
    required this.libraryRepository,
    required this.localMediaSource,
    required this.ownsLocalMediaSource,
    required this.openMedia,
    required this.openMangaChapter,
    required this.searchManga,
    required this.searchNovels,
    required this.openNovelChapter,
    required this.videoSession,
  });

  factory AppDependencies.create({
    UserDatabase? database,
    bool ownsDatabase = false,
    LocalMediaSource? localMediaSource,
    bool ownsLocalMediaSource = false,
    Iterable<MediaSource> additionalSources = const [],
  }) {
    final resolvedLocalSource = localMediaSource ?? LocalMediaSource();
    final sourceRegistry = SourceRegistry([
      resolvedLocalSource,
      ...additionalSources,
    ]);
    final resolvedDatabase = database ?? UserDatabase();
    final libraryRepository = SqliteLibraryRepository(resolvedDatabase);
    final progressRepository = SqliteProgressRepository(resolvedDatabase);

    return AppDependencies._(
      resolvedDatabase,
      database == null || ownsDatabase,
      libraryRepository: libraryRepository,
      localMediaSource: resolvedLocalSource,
      ownsLocalMediaSource: localMediaSource == null || ownsLocalMediaSource,
      openMedia: OpenMedia(sourceRegistry, progressRepository),
      openMangaChapter: OpenMangaChapter(sourceRegistry, progressRepository),
      searchManga: SearchManga(sourceRegistry),
      searchNovels: SearchNovels(sourceRegistry),
      openNovelChapter: OpenNovelChapter(sourceRegistry, progressRepository),
      videoSession: MediaKitVideoSession(),
    );
  }

  final LibraryRepository libraryRepository;
  final LocalMediaSource localMediaSource;
  final OpenMedia openMedia;
  final OpenMangaChapter openMangaChapter;
  final SearchManga searchManga;
  final SearchNovels searchNovels;
  final OpenNovelChapter openNovelChapter;
  final MediaKitVideoSession videoSession;

  final UserDatabase _database;
  final bool _ownsDatabase;
  final bool ownsLocalMediaSource;
  bool _disposed = false;
  Future<void>? _disposing;

  Future<void> dispose() {
    if (_disposed) return Future.value();
    return _disposing ??= _close();
  }

  bool _databaseClosed = false;
  Future<void> _close() async {
    try {
      try {
        await videoSession.shutdown();
      } finally {
        try {
          if (ownsLocalMediaSource) await localMediaSource.close();
        } finally {
          if (_ownsDatabase && !_databaseClosed) {
            await _database.close();
            _databaseClosed = true;
          }
        }
      }
      _disposed = true;
    } finally {
      _disposing = null;
    }
  }
}
