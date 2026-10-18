import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/settings/plugin_manager_page.dart';
import 'package:hikari/features/settings/settings_page.dart';

import 'plugin_management_test.dart' as helpers;

void main() {
  for (final width in [320.0, 360.0]) {
    testWidgets(
      'Settings plugins navigation and owner choice at $width large text',
      (tester) async {
        tester.view.physicalSize = Size(width, 1100);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final store = helpers.MemoryStore();
        final id = PluginId(PluginEcosystem.anime, 'org.example.anime');
        final first = RepositoryId('https://first.example/index.json');
        final second = RepositoryId('https://second.example/index.json');
        PluginRepository repository(RepositoryId repositoryId) =>
            PluginRepository(
              repositoryId,
              PluginEcosystem.anime,
              snapshot: PluginRepositorySnapshot(
                name: 'Test repository',
                offers: [
                  PluginOffer(
                    repositoryId: repositoryId,
                    id: id,
                    name: 'Example Anime',
                    version: ApkPluginVersion(2, '2.0'),
                    downloadUrl: 'https://example.com/plugin.apk',
                    iconUrl: 'https://example.com/icon.png',
                    sources: [
                      PluginSourceDescriptor(
                        upstreamId: '1',
                        name: 'English source',
                        language: 'en',
                      ),
                    ],
                  ),
                ],
              ),
            );
        store.state = PluginState(
          repositories: [repository(first), repository(second)],
        );
        final session = helpers.management(store: store);
        await tester.pumpWidget(
          MaterialApp(
            theme: HikariTheme.darkTheme(),
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context)
                  .copyWith(textScaler: const TextScaler.linear(1.5)),
              child: child!,
            ),
            home: Builder(
              builder: (context) => SettingsPage(
                onManagePlugins: () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (_) => PluginManagerPage(management: session),
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Plugins'));
        await tester.pumpAndSettle();
        expect(find.text('Available'), findsOneWidget);
        expect(find.textContaining('Runtime deferred'), findsOneWidget);
        await tester.ensureVisible(find.text('Example Anime'));
        await tester.tap(find.text('Example Anime'));
        await tester.pumpAndSettle();
        expect(find.text('Owner: Not selected'), findsOneWidget);
        await tester.ensureVisible(find.text('Select repository owner').first);
        await tester.tap(find.text('Select repository owner').first);
        await tester.pumpAndSettle();
        expect(session.manager!.state.owners[id], first);
        expect(tester.takeException(), isNull);
      },
    );
  }
  testWidgets(
    'repository add refresh failure and confirmed removal use real policy',
    (tester) async {
      final store = helpers.MemoryStore();
      final reader = helpers.Reader();
      final session = helpers.management(store: store, reader: reader);
      await tester.pumpWidget(
        MaterialApp(home: PluginManagerPage(management: session)),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Add repository'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Add').last);
      await tester.pumpAndSettle();
      expect(
        find.text('Choose ecosystem and valid public HTTPS URL.'),
        findsOneWidget,
      );
      await tester.tap(
        find.byType(DropdownButtonFormField<PluginEcosystem>).last,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('novel').last);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byType(TextField).last,
        'https://repo.example/index.json',
      );
      await tester.tap(find.text('Add').last);
      await tester.pumpAndSettle();
      expect(
        session.manager!.state.repositories.single.ecosystem,
        PluginEcosystem.novel,
      );
      await tester.ensureVisible(find.text('Refresh repository'));
      await tester.tap(find.text('Refresh repository'));
      await tester.pumpAndSettle();
      expect(session.manager!.state.repositories.single.snapshot, isNotNull);
      reader.fail = true;
      await tester.tap(find.text('Refresh repository'));
      await tester.pumpAndSettle();
      expect(session.manager!.state.repositories.single.snapshot, isNotNull);
      expect(find.textContaining('Last good offers retained'), findsOneWidget);
      await tester.ensureVisible(find.text('Remove repository'));
      await tester.tap(find.text('Remove repository'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining(
          'Installed plugins, selected ownership and consent remain',
        ),
        findsOneWidget,
      );
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(session.manager!.state.repositories, hasLength(1));
      await tester.tap(find.text('Remove repository'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Remove repository').last);
      await tester.pumpAndSettle();
      expect(session.manager!.state.repositories, isEmpty);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets('typed inventory retains unsupported multi-signer APK details', (
    tester,
  ) async {
    final id = PluginId(PluginEcosystem.manga, 'org.example.unsupported');
    final session = PluginManagement(
      supported: true,
      store: helpers.MemoryStore(),
      reader: helpers.Reader(),
      inventory: const CombinedPluginInventory([]),
      readApks: () async => [
        ApkPluginIdentity(
          id: id,
          version: ApkPluginVersion(1, '1.0'),
          library: '1',
          entrypoint: 'Main',
          currentSigners: ['a' * 64, 'b' * 64],
          signerHistory: [],
          compatibility: ApkCompatibility.unsupportedSigner,
          trust: ApkTrust.unapproved,
        ),
      ],
      readNovels: () async => [],
      readApkRuntime: () async =>
          ApkRuntimeEvidence(initialized: true, loaded: [], errors: {}),
      readNovelRuntime: () async => NovelRuntimeSnapshot(
        initialization: NovelRuntimeInitialization.notInitialized,
        failure: null,
        loaded: [],
        issues: [],
      ),
    );
    await tester.pumpWidget(
      MaterialApp(home: PluginManagerPage(management: session)),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Installed'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text(id.upstreamId));
    await tester.tap(find.text(id.upstreamId));
    await tester.pumpAndSettle();
    expect(find.text('Compatibility: unsupportedSigner'), findsOneWidget);
    expect(find.text('Native trust: unapproved'), findsOneWidget);
    expect(find.textContaining('a' * 64), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets('unsupported platform notice performs no native calls', (
    tester,
  ) async {
    var calls = 0;
    final session = helpers.management(
      store: helpers.MemoryStore(),
      supported: false,
      nativeCall: () => calls++,
    );
    await tester.pumpWidget(
      MaterialApp(home: PluginManagerPage(management: session)),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('available on Android only'), findsOneWidget);
    expect(find.text('Add repository'), findsNothing);
    expect(calls, 0);
  });
}
