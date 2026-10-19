import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/settings/plugin_manager_view_model.dart';

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
    model.setQuery('missing');
    expect(model.listing.rows, isEmpty);
    model.setQuery('visible');
    expect(model.listing.rows, [id]);
    model.setLanguage('fr');
    expect(model.listing.rows, [
      id,
    ]); // invalid language reset after catalog refresh
    expect(model.language, isNull);
    model.setTab(1);
    expect(model.listing.rows, isEmpty);
    model.setTab(0);
    await model.selectOwner(id, repository);
    expect(session.manager!.state.owners[id], repository);
    await model.removeRepository(repository);
    expect(session.manager!.state.repositories, isEmpty);
    expect(model.listing.rows, isEmpty);
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
