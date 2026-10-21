import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/plugin_manager_view_model.dart';

import 'plugin_management_test.dart' as helpers;

void main() {
  test('view model owns query, language, tab and repository changes', () async {
    final store = helpers.MemoryStore();
    final repository = RepositoryId('https://example.org/index.json');
    final id = PluginId(PluginEcosystem.manga, 'org.example.manga');
    store.state = PluginState(
      repositories: [
        PluginRepository(
          repository,
          PluginEcosystem.manga,
          snapshot: PluginRepositorySnapshot(
            name: 'Test',
            offers: [
              PluginOffer(
                repositoryId: repository,
                id: id,
                name: 'Visible manga',
                version: ApkPluginVersion(1, '1'),
                downloadUrl: 'https://example.org/plugin.apk',
                iconUrl: 'https://example.org/icon.png',
                sources: [
                  PluginSourceDescriptor(
                    upstreamId: '1',
                    name: 'Source',
                    language: 'en',
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
    final session = helpers.management(store: store);
    await session.refresh();
    final model = PluginManagerViewModel(session);
    addTearDown(model.dispose);
    expect(model.listing.rows, [id]);
    final initialState = model.state;
    model.setQuery('missing');
    expect(initialState.query, isEmpty);
    expect(initialState.listing.rows, [id]);
    expect(model.state.query, 'missing');
    expect(model.listing.rows, isEmpty);
    model.setQuery('visible');
    expect(model.listing.rows, [id]);
    model.setLanguage('fr');
    expect(model.listing.rows, [
      id,
    ]); // invalid choice normalizes during the state update
    expect(model.language, isNull);
    final snapshot = model.state;
    expect(identical(snapshot, model.state), isTrue); // read-only getter
    model.setTab(1);
    expect(model.listing.rows, isEmpty);
    model.setTab(0);
    await model.selectOwner(id, repository);
    expect(session.manager!.state.owners[id], repository);
    await model.removeRepository(repository);
    expect(session.manager!.state.repositories, isEmpty);
    expect(model.listing.rows, isEmpty);
  });

  test('language facets do not hide native-only installed plugins', () async {
    final mangaRepo = RepositoryId('https://manga.example/index.json');
    final animeRepo = RepositoryId('https://anime.example/index.json');
    final mangaId = PluginId(PluginEcosystem.manga, 'org.example.manga');
    final animeId = PluginId(PluginEcosystem.anime, 'org.example.anime');
    final nativeOnly = PluginId(PluginEcosystem.manga, 'org.example.offline');
    PluginRepository repository(
      RepositoryId repositoryId,
      PluginId id,
      String language,
    ) => PluginRepository(
      repositoryId,
      id.ecosystem,
      snapshot: PluginRepositorySnapshot(
        name: 'Example',
        offers: [
          PluginOffer(
            repositoryId: repositoryId,
            id: id,
            name: id.upstreamId,
            version: ApkPluginVersion(1, '1'),
            downloadUrl: 'https://example.org/plugin.apk',
            iconUrl: 'https://example.org/icon.png',
            sources: [
              PluginSourceDescriptor(
                upstreamId: 'source',
                name: 'Example source',
                language: language,
              ),
            ],
          ),
        ],
      ),
    );
    final store = helpers.MemoryStore()
      ..state = PluginState(
        repositories: [
          repository(mangaRepo, mangaId, 'en'),
          repository(animeRepo, animeId, 'ja'),
        ],
      );
    final session = PluginManagement(
      supported: true,
      inventory: const CombinedPluginInventory([]),
      store: store,
      reader: helpers.Reader(),
      readApks: () async => [],
      readNovels: () async => [],
      readApkRuntime: () async => ApkRuntimeEvidence(
        initialized: true,
        loaded: [(id: nativeOnly, versionCode: 2, signer: 'a' * 64)],
        errors: {},
      ),
      readNovelRuntime: () async => NovelRuntimeSnapshot(
        initialization: NovelRuntimeInitialization.notInitialized,
        failure: null,
        loaded: [],
        issues: [],
      ),
    );
    await session.refresh();
    final model = PluginManagerViewModel(session);
    addTearDown(model.dispose);
    expect(model.listing.languages, ['en', 'ja']);
    model.setEcosystem(PluginEcosystem.manga);
    expect(model.listing.languages, ['en']);
    model.setLanguage('en');
    expect(model.language, 'en');
    model.setQuery('not-found');
    expect(model.language, 'en'); // A search must not clear the facet.
    model.setQuery('');
    model.setTab(1);
    expect(model.language, isNull);
    expect(model.listing.languages, isEmpty);
    expect(model.listing.rows, [nativeOnly]);
    model.setTab(0);
    model.setEcosystem(PluginEcosystem.anime);
    expect(model.listing.languages, ['ja']);
    expect(model.language, isNull);
    expect(model.listing.rows, [animeId]);
  });

  test(
    'view model serializes busy actions and ignores completion after dispose',
    () async {
      final session = helpers.management(store: helpers.MemoryStore());
      final model = PluginManagerViewModel(session);
      final pending = Completer<void>();
      var secondRan = false;
      final first = model.run(() => pending.future);
      await model.run(() async {
        secondRan = true;
      });
      expect(secondRan, isFalse);
      model.dispose();
      pending.complete();
      await first;
    },
  );
}
