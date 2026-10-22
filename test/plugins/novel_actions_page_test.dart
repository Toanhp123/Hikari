import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/application/plugins/plugin_manager.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/novel_actions_page.dart';
import 'package:hikari/features/plugins/plugin_manager_page.dart';

import 'plugin_management_test.dart' as helpers;

final id = PluginId(PluginEcosystem.novel, 'novel');
final owner = RepositoryId('https://repo.example/full/index.json');
NovelArtifact artifact({
  String version = '2.0',
  String digest = 'a',
  List<String>? origins,
}) => NovelArtifact(
  metadata: NovelArtifactMetadata(
    id: id,
    name: 'Novel plugin',
    version: NovelPluginVersion(version),
    repositoryId: owner,
    site: 'https://site.example/novels',
    origins: origins ?? ['https://site.example'],
  ),
  digest: digest * 64,
);
NovelArtifactInventory row({
  NovelArtifactStatus status = NovelArtifactStatus.ready,
  NovelAdmission admission = NovelAdmission.unknown,
  bool previous = false,
}) => NovelArtifactInventory(
  current: artifact(),
  previous: previous ? artifact(version: '1.0', digest: 'b') : null,
  status: status,
  previousStatus: previous ? NovelArtifactStatus.ready : null,
  admission: admission,
  previousAdmission: previous ? NovelAdmission.unknown : null,
);

class Gateway implements NovelPluginGateway {
  final calls = <String>[];
  List<NovelArtifactInventory> rows = [];
  Completer<List<NovelArtifactInventory>>? inventoryRead;
  Completer<StagedNovelArtifact>? staging;
  Object? failure;
  List<NovelArtifactInventory>? mutationRows;
  int reads = 0;
  List<String>? origins;
  @override
  Future<List<NovelArtifactInventory>> inventory() async {
    reads++;
    return inventoryRead == null ? rows : inventoryRead!.future;
  }

  @override
  Future<NovelRuntimeSnapshot> runtimeSnapshot() async => NovelRuntimeSnapshot(
    initialization: NovelRuntimeInitialization.notInitialized,
    failure: null,
    loaded: [],
    issues: [],
  );
  @override
  Future<StagedNovelArtifact> stage(
    PluginOffer offer,
    Iterable<String> origins, {
    String? requestId,
  }) async {
    calls.add('stage:$requestId');
    this.origins = origins.toList();
    return staging == null
        ? StagedNovelArtifact(
            token: 'novel-stage',
            artifact: artifact(origins: this.origins),
          )
        : staging!.future;
  }

  Future<List<NovelArtifactInventory>> mutate(String action) async {
    calls.add(action);
    if (failure != null) throw failure!;
    rows =
        mutationRows ??
        (action == 'uninstall'
            ? []
            : [row(admission: NovelAdmission.admitted, previous: true)]);
    return rows;
  }

  @override
  Future<List<NovelArtifactInventory>> install(StagedNovelArtifact stage) =>
      mutate('install');
  @override
  Future<List<NovelArtifactInventory>> admit(NovelArtifact displayed) =>
      mutate('admit');
  @override
  Future<List<NovelArtifactInventory>> rollback(
    NovelArtifactInventory displayed,
  ) => mutate('rollback');
  @override
  Future<List<NovelArtifactInventory>> uninstall(NovelArtifact displayed) =>
      mutate('uninstall');
  @override
  Future<void> cancel(String requestId) async {
    calls.add('cancel:$requestId');
  }

  @override
  Future<void> discard(String token) async {
    calls.add('discard:$token');
  }
}

PluginManagement management(
  Gateway gateway, {
  bool supported = true,
  helpers.MemoryStore? store,
  bool auxiliaryCss = false,
}) {
  final state = store ?? helpers.MemoryStore();
  state.state = PluginState(
    owners: {id: owner},
    repositories: [
      PluginRepository(
        owner,
        PluginEcosystem.novel,
        snapshot: PluginRepositorySnapshot(
          name: 'Repository',
          offers: [
            PluginOffer(
              repositoryId: owner,
              id: id,
              name: 'Novel plugin',
              version: NovelPluginVersion('2.0'),
              downloadUrl: 'https://repo.example/plugin.js',
              iconUrl: 'https://repo.example/icon.png',
              siteUrl: 'https://site.example/novels',
              customCssUrl: auxiliaryCss
                  ? 'https://repo.example/auxiliary.css'
                  : null,
              sources: [],
            ),
          ],
        ),
      ),
    ],
  );
  return PluginManagement(
    supported: supported,
    store: state,
    reader: helpers.Reader(),
    inventory: const CombinedPluginInventory([]),
    readApks: () async => [],
    readNovels: gateway.inventory,
    readApkRuntime: () async =>
        ApkRuntimeEvidence(initialized: false, loaded: [], errors: {}),
    readNovelRuntime: gateway.runtimeSnapshot,
    novelGateway: gateway,
  );
}

Future<void> open(WidgetTester tester, PluginManagement session) async {
  await session.refresh();
  await tester.pumpWidget(
    MaterialApp(
      home: NovelActionsPage(management: session, pluginId: id),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> tap(WidgetTester tester, String label) async {
  final matches = find.text(label);
  if (matches.evaluate().isEmpty) {
    final scroll = find
        .descendant(
          of: find.byType(ListView),
          matching: find.byType(Scrollable),
        )
        .first;
    tester.state<ScrollableState>(scroll).position.jumpTo(0);
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text(label),
      250,
      scrollable: scroll,
      maxScrolls: 50,
    );
  }
  final finder = matches.last;
  await Scrollable.ensureVisible(tester.element(finder), alignment: 0.5);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

class FailingStore extends helpers.MemoryStore {
  bool failSave = false;
  @override
  Future<void> save(PluginState value) async {
    if (failSave) throw StateError('private persistence failure');
    await super.save(value);
  }
}

void main() {
  testWidgets('Novel consent Cancel never admits or installs (fake gateway)', (
    tester,
  ) async {
    final gateway = Gateway()..rows = [row()];
    await open(tester, management(gateway));
    await tap(tester, 'Consent and admit current');
    await tap(tester, 'Cancel');
    expect(
      gateway.calls.where((call) => call == 'admit' || call == 'install'),
      isEmpty,
    );
    expect(gateway.rows.single.admission, NovelAdmission.unknown);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('unsupported auxiliary Novel assets cannot be staged', (
    tester,
  ) async {
    final gateway = Gateway();
    await open(tester, management(gateway, auxiliaryCss: true));
    expect(find.text('Download inert Novel bundle'), findsNothing);
    expect(
      find.textContaining('unsupported origin or auxiliary CSS/JS'),
      findsOneWidget,
    );
    expect(gateway.calls, isEmpty);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('manager details opens injected real Novel route', (
    tester,
  ) async {
    final gateway = Gateway();
    final session = management(gateway);
    await tester.pumpWidget(
      MaterialApp(home: PluginManagerPage(management: session)),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Novel plugin').first);
    await tester.pumpAndSettle();
    await tap(tester, 'Novel actions');
    expect(find.byType(NovelActionsPage), findsOneWidget);
    expect(gateway.calls, isEmpty);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets(
    'committed native inventory survives failed mirror and retry never mutates',
    (tester) async {
      final gateway = Gateway()..rows = [row()];
      final store = FailingStore();
      final session = management(gateway, store: store);
      await open(tester, session);
      store.failSave = true;
      await tap(tester, 'Consent and admit current');
      await tap(tester, 'Evaluate current');
      expect(session.novels.single.admission, NovelAdmission.admitted);
      expect(
        session.novelActionError,
        contains('Native Novel state committed'),
      );
      store.failSave = false;
      await tap(tester, 'Refresh Novel evidence');
      expect(gateway.calls.where((c) => c == 'admit'), hasLength(1));
      expect(session.novelActionError, isNull);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets(
    'historical-owner rollback retains committed inventory without owner transfer',
    (tester) async {
      final historical = RepositoryId('https://historical.example/index.json');
      final old = NovelArtifact(
        metadata: NovelArtifactMetadata(
          id: id,
          name: 'Historical Novel',
          version: NovelPluginVersion('1.0'),
          repositoryId: historical,
          site: 'https://old.example',
          origins: ['https://old.example'],
        ),
        digest: 'b' * 64,
      );
      final committed = NovelArtifactInventory(
        current: old,
        previous: artifact(),
        status: NovelArtifactStatus.ready,
        previousStatus: NovelArtifactStatus.ready,
        admission: NovelAdmission.admitted,
        previousAdmission: NovelAdmission.unknown,
      );
      final gateway = Gateway()
        ..rows = [
          NovelArtifactInventory(
            current: artifact(),
            previous: old,
            status: NovelArtifactStatus.ready,
            previousStatus: NovelArtifactStatus.ready,
          ),
        ]
        ..mutationRows = [committed];
      final session = management(gateway);
      await open(tester, session);
      await tap(tester, 'Rollback Novel');
      expect(find.textContaining(historical.toString()), findsWidgets);
      await tap(tester, 'Consent and rollback');
      expect(session.novels.single.current, same(old));
      expect(session.manager!.state.owners[id], owner);
      expect(
        session.novelActionError,
        contains('Native Novel state committed'),
      );
      await tap(tester, 'Refresh Novel evidence');
      expect(gateway.calls.where((c) => c == 'rollback'), hasLength(1));
      await tester.pumpWidget(const SizedBox());
    },
  );
  for (final kind in NovelPublicationFailureKind.values) {
    testWidgets('$kind keeps distinct validated publication evidence', (
      tester,
    ) async {
      final confirmed = row();
      final attempted = artifact(version: '3.0', digest: 'c');
      final gateway = Gateway()
        ..rows = [confirmed]
        ..failure = NovelPublicationFailure(
          kind: kind,
          inventory: [confirmed],
          attempted: kind == NovelPublicationFailureKind.publicationUnknown
              ? [(current: attempted, previous: confirmed.current)]
              : null,
        );
      final session = management(gateway);
      await open(tester, session);
      await tap(tester, 'Consent and admit current');
      await tap(tester, 'Evaluate current');
      expect(session.novelMutationsLocked, isTrue);
      await tap(tester, 'Refresh Novel evidence');
      expect(
        session.novelPublicationFailure!.inventory!.single.current.digest,
        'a' * 64,
      );
      expect(
        session.novelPublicationFailure!.attempted?.single.current.digest,
        kind == NovelPublicationFailureKind.publicationUnknown
            ? 'c' * 64
            : null,
      );
      expect(gateway.calls.where((c) => c == 'admit'), hasLength(1));
      await tester.pumpWidget(const SizedBox());
    });
  }
  testWidgets(
    'explicit additional origins stay exact; invalid origin inputs never stage',
    (tester) async {
      final gateway = Gateway();
      await open(tester, management(gateway));
      for (final input in [
        'https://site.example https://*.cdn.example',
        'https://site.example/path',
        'https://site.example?x=1',
        'https://site.example https://site.example',
        'http://site.example',
      ]) {
        await tester.enterText(find.byType(TextFormField), input);
        await tap(tester, 'Download inert Novel bundle');
        expect(gateway.calls, isEmpty);
      }
      await tester.enterText(
        find.byType(TextFormField),
        'https://site.example\nhttps://cdn.example',
      );
      await tap(tester, 'Download inert Novel bundle');
      expect(gateway.origins, ['https://cdn.example', 'https://site.example']);
      expect(gateway.calls.where((c) => c == 'install'), isEmpty);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets(
    'ambiguous submission consumes stage and refresh never evaluates again',
    (tester) async {
      final gateway = Gateway()
        ..failure = StateError('secret https://credentials.example');
      await open(tester, management(gateway));
      await tap(tester, 'Download inert Novel bundle');
      await tap(tester, 'Consent and install Novel');
      await tap(tester, 'Evaluate and install');
      expect(find.text('Consent and install Novel'), findsNothing);
      expect(find.textContaining('credentials.example'), findsNothing);
      await tap(tester, 'Refresh Novel evidence');
      expect(gateway.calls.where((c) => c == 'install'), hasLength(1));
      await tester.pumpWidget(const SizedBox());
    },
  );
  for (final change in ['revision', 'foreground', 'owner', 'offer']) {
    testWidgets('$change change invalidates open exact tuple consent', (
      tester,
    ) async {
      final gateway = Gateway()..rows = [row()];
      final session = management(gateway);
      await open(tester, session);
      await tap(tester, 'Consent and admit current');
      if (change == 'revision') {
        gateway.rows = [row(status: NovelArtifactStatus.corrupt)];
      }
      if (change == 'owner') {
        final other = RepositoryId('https://other.example/index.json');
        final state = session.manager!.state;
        session.manager = await PluginManager.open(
          store: helpers.MemoryStore()
            ..state = PluginState(
              repositories: state.repositories,
              owners: {id: other},
            ),
          reader: helpers.Reader(),
          inventory: const CombinedPluginInventory([]),
        );
      }
      if (change == 'offer') await session.manager!.removeRepository(owner);
      if (change == 'foreground') {
        tester.binding.handleAppLifecycleStateChanged(
          AppLifecycleState.inactive,
        );
        tester.binding.handleAppLifecycleStateChanged(
          AppLifecycleState.resumed,
        );
        await tester.pump();
      }
      await tap(tester, 'Evaluate current');
      expect(gateway.calls, isNot(contains('admit')));
      await tester.pumpWidget(const SizedBox());
    });
  }
  testWidgets('back from consent never evaluates', (tester) async {
    final gateway = Gateway()..rows = [row()];
    await open(tester, management(gateway));
    await tap(tester, 'Consent and admit current');
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(gateway.calls, isNot(contains('admit')));
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('dispose cancels transport and discards late stage only', (
    tester,
  ) async {
    final gateway = Gateway()..staging = Completer<StagedNovelArtifact>();
    await open(tester, management(gateway));
    await tap(tester, 'Download inert Novel bundle');
    final request = gateway.calls
        .singleWhere((c) => c.startsWith('stage:'))
        .substring(6);
    await tester.pumpWidget(const SizedBox());
    expect(gateway.calls, contains('cancel:$request'));
    gateway.staging!.complete(
      StagedNovelArtifact(token: 'late-disposed', artifact: artifact()),
    );
    await tester.pumpAndSettle();
    expect(gateway.calls, contains('discard:late-disposed'));
    expect(gateway.calls, isNot(contains('install')));
  });
  testWidgets('unsupported platform makes no native calls', (tester) async {
    final gateway = Gateway();
    await open(tester, management(gateway, supported: false));
    expect(find.textContaining('unavailable'), findsOneWidget);
    expect(gateway.calls, isEmpty);
    expect(gateway.reads, 0);
    await tester.pumpWidget(const SizedBox());
  });
  for (final width in [320.0, 360.0]) {
    testWidgets(
      'compact $width with large text exposes exact consent without overflow',
      (tester) async {
        tester.view.physicalSize = Size(width, 700);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final gateway = Gateway()..rows = [row(previous: true)];
        final session = management(gateway);
        await session.refresh();
        await tester.pumpWidget(
          MaterialApp(
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context)
                  .copyWith(textScaler: const TextScaler.linear(1.5)),
              child: child!,
            ),
            home: NovelActionsPage(management: session, pluginId: id),
          ),
        );
        await tester.pumpAndSettle();
        await tap(tester, 'Rollback Novel');
        expect(tester.takeException(), isNull);
        await tap(tester, 'Cancel');
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox());
      },
    );
  }
  testWidgets(
    'stage is inert and full exact tuple precedes explicit install evaluation',
    (tester) async {
      final gateway = Gateway();
      await open(tester, management(gateway));
      await tap(tester, 'Download inert Novel bundle');
      expect(gateway.origins, ['https://site.example']);
      expect(gateway.calls.where((c) => c == 'install'), isEmpty);
      await tap(tester, 'Consent and install Novel');
      for (final text in [
        'novel',
        'Novel plugin',
        '2.0',
        owner.toString(),
        'a' * 64,
        'https://site.example/novels',
        'https://site.example',
      ]) {
        expect(find.textContaining(text), findsWidgets);
      }
      await tap(tester, 'Cancel');
      expect(gateway.calls, isNot(contains('install')));
      await tap(tester, 'Consent and install Novel');
      await tap(tester, 'Evaluate and install');
      expect(gateway.calls.where((c) => c == 'install'), hasLength(1));
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets(
    'delayed inventory publishes cancel and late stage is discarded',
    (tester) async {
      final gateway = Gateway();
      await open(tester, management(gateway));
      gateway.inventoryRead = Completer<List<NovelArtifactInventory>>();
      gateway.staging = Completer<StagedNovelArtifact>();
      await tester.ensureVisible(find.text('Download inert Novel bundle'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Download inert Novel bundle'));
      await tester.pump();
      gateway.inventoryRead!.complete([]);
      await tester.pumpAndSettle();
      expect(find.text('Cancel download'), findsOneWidget);
      await tap(tester, 'Cancel download');
      final request = gateway.calls
          .singleWhere((c) => c.startsWith('stage:'))
          .substring(6);
      expect(gateway.calls, contains('cancel:$request'));
      gateway.staging!.complete(
        StagedNovelArtifact(token: 'late-novel', artifact: artifact()),
      );
      await tester.pumpAndSettle();
      expect(gateway.calls, contains('discard:late-novel'));
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets(
    'unknown admission and rollback require exact displayed tuple consent',
    (tester) async {
      final gateway = Gateway()..rows = [row(previous: true)];
      await open(tester, management(gateway));
      await tap(tester, 'Consent and admit current');
      await tap(tester, 'Cancel');
      expect(gateway.calls, isNot(contains('admit')));
      await tap(tester, 'Consent and admit current');
      await tap(tester, 'Evaluate current');
      expect(gateway.calls, contains('admit'));
      await tap(tester, 'Rollback Novel');
      expect(find.textContaining('b' * 64), findsWidgets);
      expect(find.textContaining('a' * 64), findsWidgets);
      await tap(tester, 'Cancel');
      expect(gateway.calls, isNot(contains('rollback')));
      await tap(tester, 'Rollback Novel');
      await tap(tester, 'Consent and rollback');
      expect(gateway.calls, contains('rollback'));
      await tester.pumpWidget(const SizedBox());
    },
  );
  for (final status in [
    NovelArtifactStatus.missing,
    NovelArtifactStatus.corrupt,
  ]) {
    testWidgets(
      '$status removal preserves references and never evaluates code',
      (tester) async {
        final gateway = Gateway()..rows = [row(status: status)];
        await open(tester, management(gateway));
        expect(find.text('Consent and admit current'), findsNothing);
        await tap(tester, 'Remove Novel bundle');
        expect(
          find.textContaining('Library, Progress and references remain'),
          findsOneWidget,
        );
        await tap(tester, 'Remove stored bundle');
        expect(gateway.calls, contains('uninstall'));
        expect(gateway.calls, isNot(contains('admit')));
        await tester.pumpWidget(const SizedBox());
      },
    );
  }
  testWidgets(
    'malformed publication evidence locks mutations across refresh and route recreation',
    (tester) async {
      final gateway = Gateway()
        ..rows = [row()]
        ..failure = NovelPublicationFailure(
          kind: NovelPublicationFailureKind.publicationUnknown,
        );
      final session = management(gateway);
      await open(tester, session);
      await tap(tester, 'Consent and admit current');
      await tap(tester, 'Evaluate current');
      expect(session.novelMutationsLocked, isTrue);
      await session.refresh();
      await tester.pumpWidget(const SizedBox());
      await open(tester, session);
      expect(
        find.textContaining('Native process restart required'),
        findsOneWidget,
      );
      expect(find.text('Consent and admit current'), findsNothing);
      expect(gateway.calls.where((c) => c == 'admit'), hasLength(1));
      await tester.pumpWidget(const SizedBox());
    },
  );
}
