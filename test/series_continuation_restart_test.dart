import 'dart:io';
import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_novel_chapter.dart';
import 'package:hikari/application/media/open_series_continuation.dart';
import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/progress/load_continue_reading.dart';
import 'package:hikari/application/plugins/manage_apk_plugins.dart';
import 'package:hikari/application/plugins/manage_novel_plugins.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/application/plugins/plugin_manager.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/infrastructure/plugins/file_plugin_state_store.dart';
import 'package:hikari/infrastructure/repositories/sqlite_catalog_source_selection_repository.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/series_continuation.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_series_continuation_repository.dart';

const _id = SourceId('mihon:123456789');
const _novelId = SourceId('lnreader:preservation-novel');
const _mangaSeriesRef = SourceMediaRef(sourceId: _id, itemId: 'manga-series');
const _mangaChapterRef = SourceMediaRef(sourceId: _id, itemId: 'manga-chapter');
const _novelSeriesRef = SourceMediaRef(
  sourceId: _novelId,
  itemId: 'novel-series',
);
const _novelChapterRef = SourceMediaRef(
  sourceId: _novelId,
  itemId: 'novel-chapter',
);

class _MangaSource
    implements MangaPageSource, MangaSeriesSource, MediaSourceAvailability {
  final bool available;
  _MangaSource({this.available = true});
  @override
  SourceId get id => _id;
  @override
  String get name => 'Manga';
  @override
  bool get isAvailable => available;
  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef chapter) async => [
    SourceMediaRef(sourceId: _id, itemId: '${chapter.itemId}-page'),
  ];
  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef manga) async =>
      MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Manga'),
        chapters: [MangaChapter(title: 'Chapter', source: _mangaChapterRef)],
        chapterListOrder: ChapterListOrder.readingOrder,
      );
}

final class _NovelSource implements NovelSeriesSource, NovelChapterSource {
  @override
  SourceId get id => _novelId;
  @override
  String get name => 'Novel';
  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => NovelDetails(
    metadata: MediaMetadata(title: 'Novel'),
    chapters: [NovelChapter(title: 'Chapter', source: _novelChapterRef)],
    chapterListOrder: ChapterListOrder.readingOrder,
  );
  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async =>
      RichReadingContent(html: '<p>Chapter</p>');
  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}

final class _NoCache implements ByteCache {
  @override
  Future<Uint8List?> read(String namespace, String key) async => null;
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {}
  @override
  Future<void> close() async {}
}

final _repository = RepositoryId('https://plugins.example/index.json');
final _novelRepository = RepositoryId('https://plugins.example/novels.json');
final _apkId = PluginId(
  PluginEcosystem.manga,
  'eu.kanade.tachiyomi.extension.en.preservation',
);
final _novelPluginId = PluginId(PluginEcosystem.novel, 'preservation-novel');

// Only package/VM boundary is simulated; application coordinators and persistence are real.
final class _ApkPackages implements ApkPluginGateway {
  final calls = <String>[];
  bool installed = true;
  final identity = ApkPluginIdentity(
    id: _apkId,
    version: ApkPluginVersion(1, '1.4.1'),
    library: '1.4',
    entrypoint: '.Source',
    currentSigners: ['a' * 64],
    signerHistory: [],
    compatibility: ApkCompatibility.compatible,
    trust: ApkTrust.approved,
  );
  ApkInstallOperation? operation;
  ApkInstallOperation _operation(PluginOperation kind) => ApkInstallOperation(
    id: '00000000-0000-0000-0000-000000000001',
    pluginId: _apkId,
    versionCode: 1,
    signer: 'a' * 64,
    kind: kind,
    status: ApkInstallStatus.succeeded,
    stageTransferred: kind == PluginOperation.install,
  );
  @override
  Future<List<ApkPluginIdentity>> inventory() async =>
      installed ? [identity] : [];
  @override
  Future<ApkInstallOperation> uninstall(ApkPluginIdentity displayed) async {
    expect(displayed, same(identity));
    calls.add('uninstall');
    installed = false;
    return operation = _operation(PluginOperation.uninstall);
  }

  @override
  Future<StagedApk> stage(ApkStageRequest request, {String? requestId}) async {
    expect(request.offer.id, _apkId);
    expect(request.expectedSigner, 'a' * 64);
    calls.add('stage');
    return StagedApk(token: 'preserved-apk', identity: identity);
  }

  @override
  Future<ApkPluginIdentity> approveStaged(String token) async {
    expect(token, 'preserved-apk');
    calls.add('approve');
    return identity;
  }

  @override
  Future<ApkInstallOperation> installStaged(
    String token,
    PluginOperation kind,
  ) async {
    expect(token, 'preserved-apk');
    expect(kind, PluginOperation.install);
    calls.add('install');
    installed = true;
    return operation = _operation(kind);
  }

  @override
  Future<List<ApkInstallOperation>> installOperations() async => [?operation];
  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnsupportedError(
    'Unexpected package call: ${invocation.memberName}',
  );
}

final class _NovelPackages implements NovelPluginGateway {
  final calls = <String>[];
  bool installed = true;
  final artifact = NovelArtifact(
    metadata: NovelArtifactMetadata(
      id: _novelPluginId,
      name: 'Novel package',
      version: NovelPluginVersion('1.0'),
      repositoryId: _novelRepository,
      site: 'https://novel.example',
      origins: ['https://novel.example'],
    ),
    digest: 'b' * 64,
  );
  @override
  Future<List<NovelArtifactInventory>> inventory() async => installed
      ? [
          NovelArtifactInventory(
            current: artifact,
            previous: null,
            status: NovelArtifactStatus.ready,
            previousStatus: null,
            admission: NovelAdmission.admitted,
          ),
        ]
      : [];
  @override
  Future<List<NovelArtifactInventory>> uninstall(
    NovelArtifact displayed,
  ) async {
    expect(sameNovelArtifact(displayed, artifact), isTrue);
    calls.add('uninstall');
    installed = false;
    return inventory();
  }

  @override
  Future<StagedNovelArtifact> stage(
    PluginOffer offer,
    Iterable<String> origins, {
    String? requestId,
  }) async {
    expect(offer.id, _novelPluginId);
    expect(origins, artifact.metadata.origins);
    calls.add('stage');
    return StagedNovelArtifact(token: 'preserved-novel', artifact: artifact);
  }

  @override
  Future<List<NovelArtifactInventory>> install(
    StagedNovelArtifact stage,
  ) async {
    expect(stage.token, 'preserved-novel');
    expect(sameNovelArtifact(stage.artifact, artifact), isTrue);
    calls.add('install');
    installed = true;
    return inventory();
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnsupportedError(
    'Unexpected package call: ${invocation.memberName}',
  );
}

final class _NoRepositoryFetch implements PluginRepositoryReader {
  @override
  Future<PluginRepositorySnapshot> read(PluginRepository repository) =>
      throw UnsupportedError('No network in preservation regression');
}

void main() {
  late UserDatabase db;
  late SqliteProgressRepository progress;
  late SqliteLibraryRepository library;
  late SqliteSeriesContinuationRepository continuations;
  late _MangaSource mangaSource;
  late _NovelSource novelSource;
  late SourceRegistry registry;

  late Directory directory;
  late File file;

  setUp(() async {
    directory = await Directory.systemTemp.createTemp('hikari-series-restart-');
    file = File('${directory.path}/state.sqlite');
    db = UserDatabase(NativeDatabase.createInBackground(file));
    progress = SqliteProgressRepository(db);
    library = SqliteLibraryRepository(db);
    continuations = SqliteSeriesContinuationRepository(db);
    mangaSource = _MangaSource();
    novelSource = _NovelSource();
    registry = SourceRegistry([mangaSource, novelSource]);
  });
  tearDown(() async {
    await db.close();
    await directory.delete(recursive: true);
  });

  for (final mangaCompleted in [false, true]) {
    test(
      'simulated package boundary preserves file-backed Manga/Novel state across uninstall, absent restart and reinstall (manga completed: $mangaCompleted)',
      () async {
        const manga = Media(
          title: 'Saved manga title',
          type: MediaType.manga,
          source: _mangaSeriesRef,
        );
        const novel = Media(
          title: 'Saved novel title',
          type: MediaType.lightNovel,
          source: _novelSeriesRef,
        );
        final catalogManga = CatalogEntry(
          id: const CatalogEntryId(provider: 'anilist', value: '100'),
          title: 'Different catalog manga title',
          type: MediaType.manga,
        );
        final catalogNovel = CatalogEntry(
          id: const CatalogEntryId(provider: 'anilist', value: '200'),
          title: 'Different catalog novel title',
          type: MediaType.lightNovel,
        );
        final addedAt = DateTime.utc(2026, 10, 1, 2, 3, 4);
        final mangaAt = DateTime.utc(2026, 10, 2, 3, 4, 5);
        final novelAt = DateTime.utc(2026, 10, 3, 4, 5, 6);
        var selections = SqliteCatalogSourceSelectionRepository(db);
        await library.upsert(LibraryEntry(media: manga, addedAt: addedAt));
        await library.upsert(LibraryEntry(media: novel, addedAt: addedAt));
        await progress.save(
          MediaProgress(
            media: _mangaChapterRef,
            position: PagePosition(pageIndex: 2, pageCount: 8),
            completed: mangaCompleted,
            updatedAt: mangaAt,
          ),
        );
        await progress.save(
          MediaProgress(
            media: _novelChapterRef,
            position: TextPosition(progression: .42),
            completed: !mangaCompleted,
            updatedAt: novelAt,
          ),
        );
        await continuations.save(
          SeriesContinuation(
            series: _mangaSeriesRef,
            chapter: _mangaChapterRef,
          ),
        );
        await continuations.save(
          SeriesContinuation(
            series: _novelSeriesRef,
            chapter: _novelChapterRef,
          ),
        );
        await selections.save(catalogManga, manga);
        await selections.save(catalogNovel, novel);

        Future<List<List<Map<String, Object?>>>> rows() async => [
          for (final table in [
            'library_records',
            'progress_records',
            'series_continuation_records',
            'catalog_source_selections',
          ])
            (await db.customSelect('SELECT * FROM $table ORDER BY 1, 2').get())
                .map((row) => row.data)
                .toList(),
        ];
        final seeded = await rows();
        Future<void> unchanged() async {
          expect(
            await rows(),
            seeded,
            reason: 'All four user repositories retain every field, not only record counts',
          );
          final savedLibrary = await library.loadAll();
          expect(savedLibrary, hasLength(2));
          for (final media in [manga, novel]) {
            final entry = savedLibrary.singleWhere(
              (entry) => entry.media.source == media.source,
            );
            expect(entry.media.title, media.title);
            expect(entry.addedAt, addedAt);
          }
          final mangaProgress = (await progress.load(_mangaChapterRef))!;
          expect((mangaProgress.position as PagePosition).pageIndex, 2);
          expect((mangaProgress.position as PagePosition).pageCount, 8);
          expect(mangaProgress.completed, mangaCompleted);
          expect(mangaProgress.updatedAt, mangaAt);
          final novelProgress = (await progress.load(_novelChapterRef))!;
          expect((novelProgress.position as TextPosition).progression, .42);
          expect(novelProgress.completed, !mangaCompleted);
          expect(novelProgress.updatedAt, novelAt);
          expect(await continuations.load(_mangaSeriesRef), _mangaChapterRef);
          expect(await continuations.load(_novelSeriesRef), _novelChapterRef);
          expect(
            (await selections.load(catalogManga))!.source,
            _mangaSeriesRef,
          );
          expect(
            (await selections.load(catalogNovel))!.source,
            _novelSeriesRef,
          );
        }

        Future<void> reopen() async {
          await db.close();
          db = UserDatabase(NativeDatabase.createInBackground(file));
          library = SqliteLibraryRepository(db);
          progress = SqliteProgressRepository(db);
          continuations = SqliteSeriesContinuationRepository(db);
          selections = SqliteCatalogSourceSelectionRepository(db);
        }

        final apk = _ApkPackages();
        final bundles = _NovelPackages();
        final inventory = CombinedPluginInventory([
          NativeApkInventory(apk),
          NativeNovelInventory(bundles),
        ]);
        final store = FilePluginStateStore(directory: directory);
        await store.save(
          PluginState(
            repositories: [
              PluginRepository(
                _repository,
                PluginEcosystem.manga,
                snapshot: PluginRepositorySnapshot(
                  name: 'Selected packages',
                  signerFingerprint: 'a' * 64,
                  offers: [
                    PluginOffer(
                      repositoryId: _repository,
                      id: _apkId,
                      name: 'Manga package',
                      version: apk.identity.version,
                      downloadUrl: 'https://plugins.example/manga.apk',
                      iconUrl: 'https://plugins.example/icon.png',
                      sources: [],
                    ),
                  ],
                ),
              ),
              PluginRepository(
                bundles.artifact.metadata.repositoryId,
                PluginEcosystem.novel,
                snapshot: PluginRepositorySnapshot(
                  name: 'Novel packages',
                  offers: [
                    PluginOffer(
                      repositoryId: _novelRepository,
                      id: _novelPluginId,
                      name: 'Novel package',
                      version: bundles.artifact.metadata.version,
                      downloadUrl: 'https://plugins.example/novel.js',
                      iconUrl: 'https://plugins.example/icon.png',
                      siteUrl: 'https://novel.example',
                      sources: [],
                    ),
                  ],
                ),
              ),
            ],
            owners: {_apkId: _repository, _novelPluginId: _novelRepository},
          ),
        );
        Future<PluginManager> openManager() => PluginManager.open(
          store: FilePluginStateStore(directory: directory),
          reader: _NoRepositoryFetch(),
          inventory: inventory,
        );
        var manager = await openManager();
        expect(manager.state.installed, hasLength(2));
        var apkActions = ManageApkPlugins(gateway: apk, manager: manager);
        var novelActions = ManageNovelPlugins(
          gateway: bundles,
          manager: manager,
        );
        expect(
          (await apkActions.uninstall(apk.identity)).status,
          ApkInstallStatus.succeeded,
        );
        await apkActions.installOperations();
        expect(manager.state.installed.map((row) => row.id), [_novelPluginId]);
        await unchanged();
        await novelActions.uninstall(bundles.artifact);
        expect(manager.state.installed, isEmpty);
        expect(apk.calls, ['uninstall']);
        expect(bundles.calls, ['uninstall']);
        await unchanged();

        await reopen();
        registry = SourceRegistry([]);
        manager = await openManager();
        expect(manager.state.installed, isEmpty);
        await unchanged();
        final absentOpen = OpenSeriesContinuation(
          OpenMedia(registry, progress),
        );
        await expectLater(
          absentOpen.execute(
            manga,
            (await continuations.load(_mangaSeriesRef))!,
          ),
          throwsStateError,
        );
        await expectLater(
          absentOpen.execute(
            novel,
            (await continuations.load(_novelSeriesRef))!,
          ),
          throwsStateError,
        );
        await unchanged();

        apkActions = ManageApkPlugins(gateway: apk, manager: manager);
        novelActions = ManageNovelPlugins(gateway: bundles, manager: manager);
        final apkStage = await apkActions.stage(_apkId);
        await apkActions.approveStaged(apkStage.token);
        await apkActions.installStaged(apkStage.token, PluginOperation.install);
        await apkActions.installOperations();
        final novelStage = await novelActions.stage(
          _novelPluginId,
          origins: bundles.artifact.metadata.origins,
        );
        await novelActions.install(novelStage);
        expect(apk.calls, ['uninstall', 'stage', 'approve', 'install']);
        expect(bundles.calls, ['uninstall', 'stage', 'install']);
        expect(manager.state.installed, hasLength(2));
        await unchanged();
        await reopen();
        manager = await openManager();
        expect(manager.state.installed, hasLength(2));
        registry = SourceRegistry([_MangaSource(), _NovelSource()]);
        final resume = OpenSeriesContinuation(OpenMedia(registry, progress));
        final openedManga = await resume.execute(
          (await selections.load(catalogManga))!,
          (await continuations.load(_mangaSeriesRef))!,
        ) as MangaContinuationOpenTarget;
        expect(openedManga.selected.source, _mangaChapterRef);
        final mangaTarget = await OpenMangaChapter(
          registry,
          progress,
        ).execute(openedManga.selected);
        expect(
          (mangaTarget.progress.initialProgress!.position as PagePosition)
              .pageIndex,
          2,
        );
        expect(
          (mangaTarget.progress.initialProgress!.position as PagePosition)
              .pageCount,
          8,
        );
        expect(mangaTarget.progress.initialProgress!.completed, mangaCompleted);
        await openedManga.release();
        final openedNovel = await resume.execute(
          (await selections.load(catalogNovel))!,
          (await continuations.load(_novelSeriesRef))!,
        ) as NovelContinuationOpenTarget;
        expect(openedNovel.selected.source, _novelChapterRef);
        final novelTarget = await OpenNovelChapter(
          registry,
          progress,
          ReadNovelChapterContent(_NoCache()),
        ).execute(openedNovel.selected);
        expect(
          (novelTarget.progress.initialProgress!.position as TextPosition)
              .progression,
          .42,
        );
        expect(
          novelTarget.progress.initialProgress!.completed,
          !mangaCompleted,
        );
        await openedNovel.release();
        await unchanged();
      },
    );
  }

  test('reopen resolves saved manga and novel continuations through fresh workflows', () async {
    const manga = Media(
      title: 'Manga',
      type: MediaType.manga,
      source: _mangaSeriesRef,
    );
    const novel = Media(
      title: 'Novel',
      type: MediaType.lightNovel,
      source: _novelSeriesRef,
    );
    await library.upsert(
      LibraryEntry(media: manga, addedAt: DateTime.utc(2026)),
    );
    await library.upsert(
      LibraryEntry(media: novel, addedAt: DateTime.utc(2026)),
    );
    await progress.save(
      MediaProgress(
        media: _mangaChapterRef,
        position: PagePosition(pageIndex: 2, pageCount: 8),
        completed: false,
        updatedAt: DateTime.utc(2026, 1, 2),
      ),
    );
    await progress.save(
      MediaProgress(
        media: _novelChapterRef,
        position: TextPosition(progression: .42),
        completed: false,
        updatedAt: DateTime.utc(2026, 1, 3),
      ),
    );
    await continuations.save(
      SeriesContinuation(series: _mangaSeriesRef, chapter: _mangaChapterRef),
    );
    await continuations.save(
      SeriesContinuation(series: _novelSeriesRef, chapter: _novelChapterRef),
    );

    await db.close();
    db = UserDatabase(NativeDatabase.createInBackground(file));
    progress = SqliteProgressRepository(db);
    library = SqliteLibraryRepository(db);
    continuations = SqliteSeriesContinuationRepository(db);

    final detailResolver = LoadSeriesReadingTarget(continuations);
    final mangaDetails = await mangaSource.loadDetails(_mangaSeriesRef);
    final mangaCta = await detailResolver.execute(
      _mangaSeriesRef,
      mangaDetails.chaptersInReadingOrder
          .map((chapter) => chapter.source)
          .toList(),
    );
    expect(mangaCta.isContinuation, isTrue);
    final detailMangaOpen = await OpenMangaChapter(registry, progress).execute(
      mangaDetails.chapters.singleWhere(
        (chapter) => chapter.source == mangaCta.chapter,
      ),
    );
    expect(
      (detailMangaOpen.progress.initialProgress!.position as PagePosition)
          .pageIndex,
      2,
    );
    final novelDetails = await novelSource.loadDetails(_novelSeriesRef);
    final novelCta = await detailResolver.execute(
      _novelSeriesRef,
      novelDetails.chaptersInReadingOrder
          .map((chapter) => chapter.source)
          .toList(),
    );
    expect(novelCta.isContinuation, isTrue);
    final detailNovelOpen =
        await OpenNovelChapter(
          registry,
          progress,
          ReadNovelChapterContent(_NoCache()),
        ).execute(
          novelDetails.chapters.singleWhere(
            (chapter) => chapter.source == novelCta.chapter,
          ),
        );
    expect(
      (detailNovelOpen.progress.initialProgress!.position as TextPosition)
          .progression,
      .42,
    );

    final home = await LoadContinueReading(
      progress,
      continuations,
    ).execute(await library.loadAll());
    expect(home.items.map((item) => item.media.type), [
      MediaType.lightNovel,
      MediaType.manga,
    ]);

    final mangaContinuation = home.items.singleWhere(
      (item) => item.media.type == MediaType.manga,
    );
    final mangaOpen = OpenSeriesContinuation(OpenMedia(registry, progress));
    final openedManga = await mangaOpen.execute(
      mangaContinuation.media,
      mangaContinuation.chapter!,
    );
    final openedMangaTarget = openedManga as MangaContinuationOpenTarget;
    final mangaTarget = await OpenMangaChapter(
      registry,
      progress,
    ).execute(openedMangaTarget.selected);
    expect(
      (await openedMangaTarget.refreshSequence()).single.source,
      _mangaChapterRef,
    );
    expect(openedMangaTarget.selected.source, _mangaChapterRef);
    final mangaPosition =
        mangaTarget.progress.initialProgress!.position as PagePosition;
    expect(mangaPosition.pageIndex, 2);
    expect(mangaPosition.pageCount, 8);
    await openedManga.release();

    final novelContinuation = home.items.singleWhere(
      (item) => item.media.type == MediaType.lightNovel,
    );
    final novelOpen = OpenSeriesContinuation(OpenMedia(registry, progress));
    final openedNovel = await novelOpen.execute(
      novelContinuation.media,
      novelContinuation.chapter!,
    );
    final openedNovelTarget = openedNovel as NovelContinuationOpenTarget;
    final novelTarget = await OpenNovelChapter(
      registry,
      progress,
      ReadNovelChapterContent(_NoCache()),
    ).execute(openedNovelTarget.selected);
    expect(
      (await openedNovelTarget.refreshSequence()).single.source,
      _novelChapterRef,
    );
    expect(openedNovelTarget.selected.source, _novelChapterRef);
    final novelPosition =
        novelTarget.progress.initialProgress!.position as TextPosition;
    expect(novelPosition.progression, .42);
    await openedNovel.release();
  });

  test('resume keeps saved chapter and rejects unavailable source', () async {
    const manga = Media(
      title: 'Manga',
      type: MediaType.manga,
      source: _mangaSeriesRef,
    );
    const missing = SourceMediaRef(sourceId: _id, itemId: 'missing');
    final openSeries = OpenSeriesContinuation(OpenMedia(registry, progress));
    final fallback = await openSeries.execute(manga, missing);
    expect(fallback.chapter, missing);
    await expectLater(
      (fallback as MangaContinuationOpenTarget).refreshSequence(),
      throwsStateError,
    );
    await fallback.release();

    final unavailableRegistry = SourceRegistry([
      _MangaSource(available: false),
      novelSource,
    ]);
    await expectLater(
      OpenSeriesContinuation(OpenMedia(unavailableRegistry, progress))
          .execute(manga, _mangaChapterRef),
      throwsStateError,
    );
  });

  test(
    'resume opens independently of a broken sequence and releases lease',
    () async {
      final leased = _LeasedMangaSource();
      final leasedRegistry = SourceRegistry([leased]);
      const manga = Media(
        title: 'Manga',
        type: MediaType.manga,
        source: _mangaSeriesRef,
      );

      final result = await OpenSeriesContinuation(
        OpenMedia(leasedRegistry, progress),
      ).execute(manga, _mangaChapterRef);
      await expectLater(
        (result as MangaContinuationOpenTarget).refreshSequence(),
        throwsStateError,
      );
      await result.release();
      expect(leased.released, 1);
    },
  );
}

final class _LeasedMangaSource extends _MangaSource
    implements MediaOpenLeaseSource {
  int released = 0;
  _LeasedMangaSource() : super();
  @override
  MediaOpenLease acquireOpenLease(SourceMediaRef media) =>
      _TestLease(() => released++);
  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef manga) async =>
      MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Manga'),
        chapters: [
          MangaChapter(title: 'First', source: _mangaChapterRef),
          MangaChapter(title: 'Duplicate', source: _mangaChapterRef),
        ],
        chapterListOrder: ChapterListOrder.readingOrder,
      );
}

final class _TestLease implements MediaOpenLease {
  _TestLease(this.onRelease);
  final void Function() onRelease;
  bool _released = false;
  @override
  Future<void> release() async {
    if (_released) return;
    _released = true;
    onRelease();
  }
}
