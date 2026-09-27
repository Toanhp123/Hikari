import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/mangadex/mangadex_source.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/playback/local_video_session.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';

/// Composition root for application workflows and replaceable infrastructure.
final class AppDependencies {
  AppDependencies._({
    required UserDatabase database,
    required this.libraryRepository,
    required this.localMediaSource,
    required this.sourceRegistry,
    required this.openMedia,
    required this.openMangaChapter,
    required this.videoSession,
    required bool ownsDatabase,
    required MangaDexSource? ownedMangaDexSource,
  }) : _database = database,
       _ownsDatabase = ownsDatabase,
       _ownedMangaDexSource = ownedMangaDexSource;

  factory AppDependencies.create({
    UserDatabase? database,
    LocalMediaSource? localMediaSource,
    MangaSearchSource? remoteMangaSource,
    Iterable<MediaSource> additionalSources = const [],
  }) {
    final resolvedLocalSource = localMediaSource ?? LocalMediaSource();
    final ownedMangaDexSource = remoteMangaSource == null
        ? MangaDexSource()
        : null;
    final resolvedRemoteSource = remoteMangaSource ?? ownedMangaDexSource!;
    late final SourceRegistry sourceRegistry;
    try {
      sourceRegistry = SourceRegistry([
        resolvedLocalSource,
        resolvedRemoteSource,
        ...additionalSources,
      ]);
    } catch (_) {
      ownedMangaDexSource?.close();
      rethrow;
    }

    final resolvedDatabase = database ?? UserDatabase();
    final libraryRepository = SqliteLibraryRepository(resolvedDatabase);
    final progressRepository = SqliteProgressRepository(resolvedDatabase);

    return AppDependencies._(
      database: resolvedDatabase,
      libraryRepository: libraryRepository,
      localMediaSource: resolvedLocalSource,
      sourceRegistry: sourceRegistry,
      openMedia: OpenMedia(
        sources: sourceRegistry,
        progressRepository: progressRepository,
      ),
      openMangaChapter: OpenMangaChapter(
        sources: sourceRegistry,
        progressRepository: progressRepository,
      ),
      videoSession: LocalVideoSession(),
      ownsDatabase: database == null,
      ownedMangaDexSource: ownedMangaDexSource,
    );
  }

  final LibraryRepository libraryRepository;
  final LocalMediaSource localMediaSource;
  final SourceRegistry sourceRegistry;
  List<MangaSearchSource> get mangaSearchSources =>
      sourceRegistry.withCapability<MangaSearchSource>();
  final OpenMedia openMedia;
  final OpenMangaChapter openMangaChapter;
  final LocalVideoSession videoSession;

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
