import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/mangadex/mangadex_source.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/playback/media_kit_video_session.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';

/// Composition root for application workflows and replaceable infrastructure.
final class AppDependencies {
  AppDependencies._(
    this._database,
    this._ownsDatabase,
    this._ownedMangaDexSource, {
    required this.libraryRepository,
    required this.localMediaSource,
    required this.openMedia,
    required this.openMangaChapter,
    required this.searchManga,
    required this.videoSession,
  });

  factory AppDependencies.create({
    UserDatabase? database,
    LocalMediaSource? localMediaSource,
    MangaSearchSource? remoteMangaSource,
    Iterable<MediaSource> additionalSources = const [],
  }) {
    final resolvedLocalSource = localMediaSource ?? LocalMediaSource();
    final extraSources = additionalSources.toList(growable: false);
    final hasExtensionMangaDex = extraSources.any(
      (source) => source.id == const SourceId('mangadex'),
    );
    final ownedMangaDexSource =
        remoteMangaSource == null && !hasExtensionMangaDex
        ? MangaDexSource()
        : null;
    final resolvedRemoteSource = remoteMangaSource ?? ownedMangaDexSource;

    late final SourceRegistry sourceRegistry;
    try {
      sourceRegistry = SourceRegistry([
        resolvedLocalSource,
        ?resolvedRemoteSource,
        ...extraSources,
      ]);
    } catch (_) {
      ownedMangaDexSource?.close();
      rethrow;
    }

    final resolvedDatabase = database ?? UserDatabase();
    final libraryRepository = SqliteLibraryRepository(resolvedDatabase);
    final progressRepository = SqliteProgressRepository(resolvedDatabase);

    return AppDependencies._(
      resolvedDatabase,
      database == null,
      ownedMangaDexSource,
      libraryRepository: libraryRepository,
      localMediaSource: resolvedLocalSource,
      openMedia: OpenMedia(sourceRegistry, progressRepository),
      openMangaChapter: OpenMangaChapter(sourceRegistry, progressRepository),
      searchManga: SearchManga(sourceRegistry),
      videoSession: MediaKitVideoSession(),
    );
  }

  final LibraryRepository libraryRepository;
  final LocalMediaSource localMediaSource;
  final OpenMedia openMedia;
  final OpenMangaChapter openMangaChapter;
  final SearchManga searchManga;
  final MediaKitVideoSession videoSession;

  final UserDatabase _database;
  final bool _ownsDatabase;
  final MangaDexSource? _ownedMangaDexSource;
  bool _disposed = false;

  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    _ownedMangaDexSource?.close();
    try {
      await videoSession.shutdown();
    } finally {
      if (_ownsDatabase) await _database.close();
    }
  }
}
