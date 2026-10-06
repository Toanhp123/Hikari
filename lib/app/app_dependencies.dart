import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/application/catalog/resolve_catalog_source.dart';
import 'package:hikari/application/catalog/search_catalog.dart';
import 'package:hikari/application/progress/load_continue_reading.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_series_continuation.dart';
import 'package:hikari/application/media/prefetch_manga_chapter.dart';
import 'package:hikari/application/media/prefetch_novel_chapter.dart';
import 'package:hikari/application/media/prefetch_manga_pages.dart';
import 'package:hikari/application/media/read_manga_page.dart';
import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/media/read_novel_resource.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/open_novel_chapter.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/sources/read_source_artwork.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/infrastructure/cache/disk_byte_cache.dart';
import 'package:hikari/infrastructure/catalog/anilist/anilist_catalog_provider.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/playback/media_kit_video_session.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_series_continuation_repository.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

/// Composition root for application workflows and replaceable infrastructure.
final class AppDependencies {
  static const _sourceArtworkCacheBudgetBytes = 64 * 1024 * 1024;
  static const _mangaPageCacheBudgetBytes = 128 * 1024 * 1024;
  static const _novelChapterCacheBudgetBytes = 32 * 1024 * 1024;
  static const _novelResourceCacheBudgetBytes = 64 * 1024 * 1024;

  AppDependencies._(
    this._database,
    this._ownsDatabase, {
    required this.libraryRepository,
    required this.progressRepository,
    required this.seriesContinuationRepository,
    required this.openSeriesContinuation,
    required this.loadContinueReading,
    required this.localMediaSource,
    required this.ownsLocalMediaSource,
    required this.catalogProvider,
    required this.ownsCatalogProvider,
    required this.discoverCatalog,
    required this.loadCatalogEntryDetails,
    required this.searchCatalog,
    required this.resolveCatalogSource,
    required this.openMedia,
    required this.openMangaChapter,
    required this.createMangaChapterPrefetch,
    required this.searchManga,
    required this.searchNovels,
    required this.readSourceArtwork,
    required this.readMangaPage,
    required this.readNovelChapterContent,
    required this.readNovelResource,
    required this.openNovelChapter,
    required this.createNovelChapterPrefetch,
    required this.videoSession,
    required this.cache,
    required this.ownsCache,
  });

  factory AppDependencies.create({
    UserDatabase? database,
    bool ownsDatabase = false,
    LocalMediaSource? localMediaSource,
    ByteCache? cache,
    bool ownsCache = false,
    bool ownsLocalMediaSource = false,
    CatalogProvider? catalogProvider,
    bool ownsCatalogProvider = false,
    Iterable<MediaSource> additionalSources = const [],
  }) {
    final resolvedLocalSource = localMediaSource ?? LocalMediaSource();
    final resolvedCatalogProvider = catalogProvider ?? AniListCatalogProvider();
    final sourceRegistry = SourceRegistry([
      resolvedLocalSource,
      ...additionalSources,
    ]);
    final resolvedDatabase = database ?? UserDatabase();
    final resolvedCache =
        cache ??
        DiskByteCache(
          namespaceByteBudgets: const {
            ReadSourceArtwork.cacheNamespace: _sourceArtworkCacheBudgetBytes,
            ReadMangaPage.cacheNamespace: _mangaPageCacheBudgetBytes,
            ReadNovelChapterContent.cacheNamespace:
                _novelChapterCacheBudgetBytes,
            ReadNovelResource.cacheNamespace: _novelResourceCacheBudgetBytes,
          },
        );
    final readMangaPage = ReadMangaPage(resolvedCache);
    final readNovelChapterContent = ReadNovelChapterContent(resolvedCache);
    final readNovelResource = ReadNovelResource(resolvedCache);
    final libraryRepository = SqliteLibraryRepository(resolvedDatabase);
    final progressRepository = SqliteProgressRepository(resolvedDatabase);
    final seriesContinuationRepository = SqliteSeriesContinuationRepository(
      resolvedDatabase,
    );
    final openMedia = OpenMedia(sourceRegistry, progressRepository);
    final searchManga = SearchManga(sourceRegistry);
    final searchNovels = SearchNovels(sourceRegistry);

    return AppDependencies._(
      resolvedDatabase,
      database == null || ownsDatabase,
      libraryRepository: libraryRepository,
      progressRepository: progressRepository,
      seriesContinuationRepository: seriesContinuationRepository,
      openSeriesContinuation: OpenSeriesContinuation(openMedia),
      loadContinueReading: LoadContinueReading(
        progressRepository,
        seriesContinuationRepository,
      ),
      localMediaSource: resolvedLocalSource,
      ownsLocalMediaSource: localMediaSource == null || ownsLocalMediaSource,
      catalogProvider: resolvedCatalogProvider,
      ownsCatalogProvider: catalogProvider == null || ownsCatalogProvider,
      discoverCatalog: DiscoverCatalog(resolvedCatalogProvider),
      loadCatalogEntryDetails: LoadCatalogEntryDetails(resolvedCatalogProvider),
      searchCatalog: SearchCatalog(resolvedCatalogProvider),
      resolveCatalogSource: ResolveCatalogSource(
        searchManga: searchManga,
        searchNovels: searchNovels,
      ),
      openMedia: openMedia,
      openMangaChapter: OpenMangaChapter(sourceRegistry, progressRepository),
      createMangaChapterPrefetch: () =>
          PrefetchMangaChapter(sourceRegistry, readMangaPage),
      searchManga: searchManga,
      searchNovels: searchNovels,
      readSourceArtwork: ReadSourceArtwork(
        sourceRegistry,
        cache: resolvedCache,
      ),
      readMangaPage: readMangaPage,
      readNovelChapterContent: readNovelChapterContent,
      readNovelResource: readNovelResource,
      openNovelChapter: OpenNovelChapter(
        sourceRegistry,
        progressRepository,
        readNovelChapterContent,
      ),
      createNovelChapterPrefetch: () => PrefetchNovelChapter(
        sourceRegistry,
        readNovelChapterContent,
        readNovelResource,
      ),
      videoSession: MediaKitVideoSession(),
      cache: resolvedCache,
      ownsCache: cache == null || ownsCache,
    );
  }

  final LibraryRepository libraryRepository;
  final ProgressRepository progressRepository;
  final SeriesContinuationRepository seriesContinuationRepository;
  final OpenSeriesContinuation openSeriesContinuation;
  final LoadContinueReading loadContinueReading;
  final LocalMediaSource localMediaSource;
  final CatalogProvider catalogProvider;
  final DiscoverCatalog discoverCatalog;
  final LoadCatalogEntryDetails loadCatalogEntryDetails;
  final SearchCatalog searchCatalog;
  final ResolveCatalogSource resolveCatalogSource;
  final OpenMedia openMedia;
  final OpenMangaChapter openMangaChapter;
  final PrefetchMangaChapter Function() createMangaChapterPrefetch;
  final SearchManga searchManga;
  final SearchNovels searchNovels;
  final ReadSourceArtwork readSourceArtwork;
  final ReadMangaPage readMangaPage;
  final ReadNovelChapterContent readNovelChapterContent;
  final ReadNovelResource readNovelResource;
  final OpenNovelChapter openNovelChapter;
  final PrefetchNovelChapter Function() createNovelChapterPrefetch;
  final MediaKitVideoSession videoSession;
  final ByteCache cache;
  final bool ownsCache;

  final UserDatabase _database;
  final bool _ownsDatabase;
  final bool ownsLocalMediaSource;
  final bool ownsCatalogProvider;

  PrefetchMangaPages createMangaPagePrefetch() =>
      PrefetchMangaPages(readMangaPage);

  Future<void> prefetchMangaPages(
    PrefetchMangaPages prefetch,
    MangaPageSource source,
    List<SourceMediaRef> pages,
    int index,
  ) => prefetch.execute(source, pages, index);

  Future<void> prefetchNovelChapter(
    PrefetchNovelChapter prefetch,
    NovelChapter chapter,
  ) => prefetch.execute(chapter);

  bool _disposed = false;
  Future<void>? _disposing;

  Future<void> dispose() {
    if (_disposed) return Future.value();
    return _disposing ??= _close();
  }

  bool _databaseClosed = false;
  bool _cacheClosed = false;

  Future<void> _close() async {
    try {
      try {
        await videoSession.shutdown();
      } finally {
        try {
          if (ownsLocalMediaSource) await localMediaSource.close();
        } finally {
          try {
            if (ownsCatalogProvider) await catalogProvider.close();
          } finally {
            try {
              if (_ownsDatabase && !_databaseClosed) {
                await _database.close();
                _databaseClosed = true;
              }
            } finally {
              if (ownsCache && !_cacheClosed) {
                await cache.close();
                _cacheClosed = true;
              }
            }
          }
        }
      }
      _disposed = true;
    } finally {
      _disposing = null;
    }
  }
}
