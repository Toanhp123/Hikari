import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/components/hikari_choice_menu.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/plugin_manager_page.dart';

import 'plugin_management_test.dart' as helpers;

PluginOffer offer(
  RepositoryId repository,
  String id,
  String name,
  String language,
) => PluginOffer(
  repositoryId: repository,
  id: PluginId(PluginEcosystem.manga, id),
  name: name,
  version: ApkPluginVersion(2, '2.0'),
  downloadUrl: 'https://example.com/plugin.apk',
  iconUrl: 'https://example.com/icon.png',
  sources: [
    PluginSourceDescriptor(
      upstreamId: id,
      name: '$name source',
      language: language,
    ),
  ],
);
PluginRepository repository(RepositoryId id, List<PluginOffer> offers) =>
    PluginRepository(
      id,
      PluginEcosystem.manga,
      snapshot: PluginRepositorySnapshot(name: 'Repository', offers: offers),
    );

class RefreshReader extends helpers.Reader {
  PluginRepositorySnapshot? replacement;
  @override
  Future<PluginRepositorySnapshot> read(PluginRepository repository) async =>
      replacement ?? repository.snapshot!;
}

Future<void> selectEnglish(WidgetTester tester) async {
  await tester.tap(find.text('Language: All'));
  await tester.pumpAndSettle();
  await tester.tap(find.widgetWithText(MenuItemButton, 'EN'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    '5000-entry catalog builds visible cards lazily and searches all source metadata',
    (tester) async {
      final url = RepositoryId('https://catalog.example/index.json');
      final store = helpers.MemoryStore()
        ..state = PluginState(
          repositories: [
            repository(url, [
              for (var i = 4999; i >= 0; i--)
                offer(
                  url,
                  'org.example.plugin$i',
                  'Plugin ${i.toString().padLeft(4, '0')}',
                  'en',
                ),
            ]),
          ],
        );
      final session = helpers.management(store: store);
      await tester.pumpWidget(
        MaterialApp(home: PluginManagerPage(management: session)),
      );
      await tester.pumpAndSettle();
      expect(find.text('Plugin 0000'), findsOneWidget);
      expect(find.text('Plugin 4999'), findsNothing);
      // Structural laziness check, not a wall-clock threshold.
      expect(find.byType(Card).evaluate().length, lessThan(100));
      await tester.enterText(find.byType(TextField), 'Plugin 4999 source');
      await tester.pumpAndSettle();
      expect(find.text('Plugin 4999'), findsOneWidget);
      expect(find.text('Plugin 0000'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );
  for (final mutation in ['remove', 'refresh']) {
    testWidgets(
      'language selection resets when $mutation removes sole English option',
      (tester) async {
        final en = RepositoryId('https://english.example/index.json');
        final fr = RepositoryId('https://french.example/index.json');
        final reader = RefreshReader();
        final store = helpers.MemoryStore()
          ..state = PluginState(
            repositories: [
              repository(en, [
                offer(en, 'org.example.en', 'English plugin', 'en'),
              ]),
              repository(fr, [
                offer(fr, 'org.example.fr', 'French plugin', 'fr'),
              ]),
            ],
          );
        final session = helpers.management(store: store, reader: reader);
        await tester.pumpWidget(
          MaterialApp(home: PluginManagerPage(management: session)),
        );
        await tester.pumpAndSettle();
        await selectEnglish(tester);
        expect(find.text('French plugin'), findsNothing);
        if (mutation == 'remove') {
          await Scrollable.ensureVisible(
            tester.element(find.text('Remove repository').first),
            alignment: 0.5,
          );
          await tester.pumpAndSettle();
          await tester.tap(find.text('Remove repository').first);
          await tester.pumpAndSettle();
          await tester.tap(find.text('Remove repository').last);
        } else {
          reader.replacement = PluginRepositorySnapshot(
            name: 'No English',
            offers: [],
          );
          await Scrollable.ensureVisible(
            tester.element(find.text('Refresh repository').first),
            alignment: 0.5,
          );
          await tester.pumpAndSettle();
          await tester.tap(find.text('Refresh repository').first);
        }
        await tester.pumpAndSettle();
        final language = tester.widget<HikariChoiceMenu<String>>(
          find.byType(HikariChoiceMenu<String>),
        );
        expect(language.value, isNull);
        expect(find.text('Language: All'), findsOneWidget);
        await tester.ensureVisible(find.byType(TextField));
        await tester.pumpAndSettle();
        expect(find.text('French plugin'), findsOneWidget);
        expect(
          find.text(
            'No plugins match. Add or refresh a repository to discover offers.',
          ),
          findsNothing,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }
  testWidgets(
    'existing owner change requires confirmation and never transfers old consent',
    (tester) async {
      final old = RepositoryId('https://old.example/full/index.json');
      final next = RepositoryId('https://new.example/full/index.json');
      final id = PluginId(PluginEcosystem.manga, 'org.example.plugin');
      final approval = PluginApproval(
        id: id,
        repositoryId: old,
        kind: PluginApprovalKind.signer,
        digest: 'a' * 64,
      );
      final store = helpers.MemoryStore()
        ..state = PluginState(
          repositories: [
            repository(old, [offer(old, id.upstreamId, 'Owned plugin', 'en')]),
            repository(next, [
              offer(next, id.upstreamId, 'Owned plugin', 'en'),
            ]),
          ],
          owners: {id: old},
          approvals: [approval],
        );
      final session = helpers.management(store: store);
      await tester.pumpWidget(
        MaterialApp(home: PluginManagerPage(management: session)),
      );
      await tester.pumpAndSettle();
      Future<void> requestChange() async {
        await tester.ensureVisible(find.text('Owned plugin'));
        await tester.tap(find.text('Owned plugin'));
        await tester.pumpAndSettle();
        await tester.ensureVisible(find.text('Select repository owner'));
        await tester.tap(find.text('Select repository owner'));
        await tester.pumpAndSettle();
        expect(find.text('Current: $old'), findsOneWidget);
        expect(find.text('New: $next'), findsOneWidget);
        expect(
          find.textContaining(
            'Signing, code and origin consent do not transfer',
          ),
          findsOneWidget,
        );
      }

      await requestChange();
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(session.manager!.state.owners[id], old);
      await requestChange();
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(session.manager!.state.owners[id], old);
      await requestChange();
      await tester.tap(find.text('Change owner'));
      await tester.pumpAndSettle();
      expect(session.manager!.state.owners[id], next);
      final retained = session.manager!.state.approvals.single;
      expect(retained.repositoryId, old);
      expect(retained.digest, approval.digest);
      expect(retained.origins, approval.origins);
      expect(tester.takeException(), isNull);
    },
  );
}
