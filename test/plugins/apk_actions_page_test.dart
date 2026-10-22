import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/apk_actions_page.dart';

import 'plugin_management_test.dart' as helpers;

final pluginId = PluginId(PluginEcosystem.anime, 'org.example.plugin');
final owner = RepositoryId('https://repository.example/full/index.json');
ApkPluginIdentity identity({bool approved = false, int code = 2}) =>
    ApkPluginIdentity(
      id: pluginId,
      version: ApkPluginVersion(code, '$code.0'),
      library: '17',
      entrypoint: '.Plugin',
      currentSigners: ['a' * 64],
      signerHistory: ['b' * 64],
      compatibility: ApkCompatibility.compatible,
      trust: approved ? ApkTrust.approved : ApkTrust.unapproved,
    );

class Gateway implements ApkPluginGateway {
  final calls = <String>[];
  List<ApkPluginIdentity> installed = [];
  List<ApkInstallOperation> operations = [];
  bool permission = true;
  bool submitThrows = false;
  bool retirementThrows = false;
  Completer<List<ApkInstallOperation>>? polling;
  int activePolls = 0;
  int maxPolls = 0;
  Completer<StagedApk>? staging;
  Completer<List<ApkPluginIdentity>>? inventoryRead;
  @override
  Future<StagedApk> stage(ApkStageRequest request, {String? requestId}) async {
    calls.add('stage:$requestId');
    return staging == null
        ? StagedApk(token: 'stage-token', identity: identity())
        : staging!.future;
  }

  @override
  Future<List<ApkPluginIdentity>> inventory() async =>
      inventoryRead == null ? installed : inventoryRead!.future;
  @override
  Future<ApkPluginIdentity> approveStaged(String token) async {
    calls.add('approve:$token');
    return identity(approved: true);
  }

  @override
  Future<ApkPluginIdentity> approveInstalled(
    ApkPluginIdentity displayed,
  ) async {
    calls.add('approveInstalled');
    installed = [identity(approved: true, code: displayed.version.code)];
    return installed.single;
  }

  @override
  Future<void> discardStaged(String token) async => calls.add('discard:$token');
  @override
  Future<void> cancel(String requestId) async => calls.add('cancel:$requestId');
  @override
  Future<bool> canInstall() async => permission;
  @override
  Future<void> openInstallSettings() async => calls.add('settings');
  ApkInstallOperation operation({
    PluginOperation kind = PluginOperation.install,
    ApkInstallStatus status = ApkInstallStatus.awaitingResult,
  }) => ApkInstallOperation(
    id: '123e4567-e89b-42d3-a456-426614174000',
    pluginId: pluginId,
    versionCode: 2,
    signer: 'a' * 64,
    kind: kind,
    status: status,
    stageTransferred: kind != PluginOperation.uninstall,
    sessionId: kind == PluginOperation.uninstall ? null : 42,
  );
  @override
  Future<ApkInstallOperation> installStaged(
    String token,
    PluginOperation kind,
  ) async {
    calls.add('install:$token:${kind.name}');
    operations = [operation(kind: kind)];
    if (submitThrows) throw StateError('Ambiguous native submission');
    return operations.single;
  }

  @override
  Future<ApkInstallOperation> uninstall(ApkPluginIdentity displayed) async {
    calls.add(
      'uninstall:${displayed.version.code}:${displayed.currentSigners.single}',
    );
    operations = [operation(kind: PluginOperation.uninstall)];
    return operations.single;
  }

  @override
  Future<List<ApkInstallOperation>> installOperations() async {
    calls.add('operations');
    activePolls++;
    if (activePolls > maxPolls) maxPolls = activePolls;
    try {
      return polling == null ? operations : await polling!.future;
    } finally {
      activePolls--;
    }
  }

  @override
  Future<ApkInstallOperation> resumeInstallConfirmation(String id) async {
    calls.add('resume:$id');
    return operations.single;
  }

  @override
  Future<ApkInstallOperation> cancelInstall(String id) async {
    calls.add('cancelInstall:$id');
    return operations.single;
  }

  @override
  Future<ApkInstallOperation> retireInstall(
    String id, {
    bool abandonOwnedSession = false,
    bool acknowledgeUnrevokedRemoval = false,
  }) async {
    calls.add('retire:$abandonOwnedSession:$acknowledgeUnrevokedRemoval');
    if (retirementThrows) throw StateError('Owned session remains');
    operations = [
      operation(
        kind: operations.single.kind,
        status: ApkInstallStatus.retiredUnknown,
      ),
    ];
    return operations.single;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class FailingStore extends helpers.MemoryStore {
  @override
  Future<void> save(PluginState value) async {
    if (super.fail) throw StateError('Persistence failed');
    await super.save(value);
  }
}

PluginManagement session(
  Gateway gateway, {
  helpers.MemoryStore? store,
  bool supported = true,
}) {
  final state = store ?? helpers.MemoryStore();
  state.state = PluginState(
    owners: {pluginId: owner},
    repositories: [
      PluginRepository(
        owner,
        PluginEcosystem.anime,
        snapshot: PluginRepositorySnapshot(
          name: 'Repository',
          signerFingerprint: 'a' * 64,
          offers: [
            PluginOffer(
              repositoryId: owner,
              id: pluginId,
              name: 'Anime plugin',
              version: ApkPluginVersion(2, '2.0'),
              downloadUrl: 'https://example.com/plugin.apk',
              iconUrl: 'https://example.com/icon.png',
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
    readApks: gateway.inventory,
    readNovels: () async => [],
    readApkRuntime: () async =>
        ApkRuntimeEvidence(initialized: true, loaded: [], errors: {}),
    readNovelRuntime: () async => NovelRuntimeSnapshot(
      initialization: NovelRuntimeInitialization.notInitialized,
      failure: null,
      loaded: [],
      issues: [],
    ),
    apkGateway: gateway,
  );
}

Future<void> tap(WidgetTester tester, String label) async {
  if (find.text(label).evaluate().isEmpty &&
      find.byType(ListView).evaluate().isNotEmpty) {
    final scrollable = find
        .descendant(
          of: find.byType(ListView),
          matching: find.byType(Scrollable),
        )
        .first;
    tester.state<ScrollableState>(scrollable).position.jumpTo(0);
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text(label),
      250,
      scrollable: scrollable,
      maxScrolls: 50,
    );
    await tester.pumpAndSettle();
  }
  await Scrollable.ensureVisible(
    tester.element(find.text(label).last),
    alignment: 0.5,
  );
  await tester.pumpAndSettle();
  await tester.tap(find.text(label).last);
  await tester.pumpAndSettle();
}

Future<void> open(WidgetTester tester, PluginManagement management) async {
  await management.refresh();
  await tester.pumpWidget(
    MaterialApp(
      home: ApkActionsPage(management: management, pluginId: pluginId),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  for (final dismiss in ['Cancel', 'Back']) {
    testWidgets(
      'APK signer $dismiss never authorizes native approval or install (fake gateway)',
      (tester) async {
        final gateway = Gateway();
        await open(tester, session(gateway));
        await tap(tester, 'Download and inspect APK');
        await tap(tester, 'Approve staged signer');
        if (dismiss == 'Back') {
          await tester.binding.handlePopRoute();
          await tester.pumpAndSettle();
        } else {
          await tap(tester, 'Cancel');
        }
        expect(
          gateway.calls.where(
            (call) =>
                call.startsWith('approve:') || call.startsWith('install:'),
          ),
          isEmpty,
        );
        expect(find.text('Install APK'), findsNothing);
        await tester.pumpWidget(const SizedBox());
      },
    );
  }
  testWidgets(
    'delayed inventory publishes transfer cancellation before stage completes',
    (tester) async {
      final gateway = Gateway();
      await open(tester, session(gateway));
      gateway.inventoryRead = Completer<List<ApkPluginIdentity>>();
      gateway.staging = Completer<StagedApk>();
      await tester.tap(find.text('Download and inspect APK'));
      await tester.pump();
      expect(find.text('Cancel download'), findsNothing);
      gateway.inventoryRead!.complete([]);
      await tester.pumpAndSettle();
      expect(find.text('Cancel download'), findsOneWidget);
      final request = gateway.calls
          .singleWhere((call) => call.startsWith('stage:'))
          .substring('stage:'.length);
      await tap(tester, 'Cancel download');
      expect(gateway.calls, contains('cancel:$request'));
      expect(find.text('Cancel download'), findsNothing);
      gateway.staging!.complete(
        StagedApk(token: 'late-cancelled', identity: identity()),
      );
      await tester.pumpAndSettle();
      expect(gateway.calls, contains('discard:late-cancelled'));
      expect(find.text('Inspected staged APK'), findsNothing);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('unsupported platform never invokes injected native gateway', (
    tester,
  ) async {
    final gateway = Gateway();
    await open(tester, session(gateway, supported: false));
    expect(find.textContaining('APK actions unavailable'), findsOneWidget);
    expect(gateway.calls, isEmpty);
    await tester.pumpWidget(const SizedBox());
  });

  for (final width in [320.0, 360.0]) {
    testWidgets('installed approval and update work at $width large text', (
      tester,
    ) async {
      tester.view.physicalSize = Size(width, 1100);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final gateway = Gateway()..installed = [identity(code: 1)];
      final management = session(gateway);
      await management.refresh();
      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(textScaler: const TextScaler.linear(1.5)),
            child: child!,
          ),
          home: ApkActionsPage(management: management, pluginId: pluginId),
        ),
      );
      await tester.pumpAndSettle();
      await tap(tester, 'Approve installed signer');
      await tap(tester, 'Approve signer');
      expect(gateway.calls, contains('approveInstalled'));
      await tap(tester, 'Download and inspect APK');
      await tap(tester, 'Approve staged signer');
      await tap(tester, 'Approve signer');
      await tap(tester, 'Update APK');
      await tap(tester, 'Submit update');
      expect(gateway.calls, contains('install:stage-token:update'));
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
    });
  }

  testWidgets(
    'approval commit survives reconciliation failure without repeating consent',
    (tester) async {
      final gateway = Gateway();
      final store = FailingStore();
      final management = session(gateway, store: store);
      await open(tester, management);
      await tap(tester, 'Download and inspect APK');
      store.fail = true;
      await tap(tester, 'Approve staged signer');
      await tap(tester, 'Approve signer');
      await tester.drag(find.byType(ListView), const Offset(0, 1000));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Native signer approval committed'),
        findsOneWidget,
      );
      expect(find.text('Approve staged signer'), findsNothing);
      await tap(tester, 'Refresh native evidence');
      expect(
        gateway.calls.where((c) => c.startsWith('approve:')),
        hasLength(1),
      );
      await tester.pumpWidget(const SizedBox());
    },
  );

  test(
    'native operation evidence survives Dart reconciliation failure',
    () async {
      final gateway = Gateway();
      final store = FailingStore();
      final management = session(gateway, store: store);
      await management.refresh();
      store.fail = true;
      gateway.operations = [
        gateway.operation(status: ApkInstallStatus.succeeded),
      ];
      await management.refreshApkOperations();
      expect(
        management.apkOperations.single.status,
        ApkInstallStatus.succeeded,
      );
      expect(
        management.operationError,
        contains('Native operation evidence retained'),
      );
      expect(gateway.calls.where((c) => c.startsWith('install:')), isEmpty);
    },
  );

  testWidgets(
    'ambiguous submission retains native operation and never resubmits stage',
    (tester) async {
      final gateway = Gateway()..submitThrows = true;
      await open(tester, session(gateway));
      await tap(tester, 'Download and inspect APK');
      await tap(tester, 'Approve staged signer');
      await tap(tester, 'Approve signer');
      await tap(tester, 'Install APK');
      await tap(tester, 'Submit install');
      await tester.drag(find.byType(ListView), const Offset(0, -1000));
      await tester.pumpAndSettle();
      expect(find.textContaining('awaitingResult'), findsOneWidget);
      expect(find.text('Install APK'), findsNothing);
      await tap(tester, 'Refresh native evidence');
      expect(
        gateway.calls.where((c) => c.startsWith('install:')),
        hasLength(1),
      );
      await tester.pumpWidget(const SizedBox());
      expect(
        gateway.calls.where((c) => c.startsWith('cancelInstall:')),
        isEmpty,
      );
    },
  );

  for (final boundary in ['owner', 'installed', 'background']) {
    testWidgets('changed $boundary invalidates staged consent boundary', (
      tester,
    ) async {
      final gateway = Gateway();
      final management = session(gateway);
      await open(tester, management);
      await tap(tester, 'Download and inspect APK');
      await tap(tester, 'Approve staged signer');
      if (boundary == 'owner') {
        await management.manager!.removeRepository(owner);
      } else if (boundary == 'installed') {
        gateway.installed = [identity(code: 1)];
      } else {
        tester.binding.handleAppLifecycleStateChanged(
          AppLifecycleState.inactive,
        );
      }
      await tap(tester, 'Approve signer');
      expect(gateway.calls.where((c) => c.startsWith('approve:')), isEmpty);
      await tester.pumpWidget(const SizedBox());
      if (boundary == 'background') {
        tester.binding.handleAppLifecycleStateChanged(
          AppLifecycleState.resumed,
        );
      }
    });
  }

  testWidgets(
    'pending confirmation requires explicit resume; cancellation is a request',
    (tester) async {
      final gateway = Gateway();
      gateway.operations = [
        gateway.operation(status: ApkInstallStatus.pendingUserAction),
      ];
      await open(tester, session(gateway));
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpAndSettle();
      expect(gateway.calls.where((c) => c.startsWith('resume:')), isEmpty);
      await tap(tester, 'Resume Android confirmation');
      await tap(tester, 'Cancel');
      expect(gateway.calls.where((c) => c.startsWith('resume:')), isEmpty);
      await tap(tester, 'Resume Android confirmation');
      await tap(tester, 'Resume Android confirmation');
      expect(gateway.calls.where((c) => c.startsWith('resume:')), hasLength(1));
      await tap(tester, 'Request cancellation');
      await tap(tester, 'Request cancellation');
      expect(
        gateway.calls.where((c) => c.startsWith('cancelInstall:')),
        hasLength(1),
      );
      expect(find.textContaining('pendingUserAction'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets(
    'serial foreground polling stops when covered, backgrounded and disposed',
    (tester) async {
      final gateway = Gateway();
      await open(tester, session(gateway));
      gateway.polling = Completer<List<ApkInstallOperation>>();
      await tester.pump(const Duration(seconds: 3));
      final reads = gateway.calls.where((c) => c == 'operations').length;
      await tester.pump(const Duration(seconds: 10));
      expect(gateway.calls.where((c) => c == 'operations'), hasLength(reads));
      gateway.polling!.complete([]);
      await tester.pumpAndSettle();
      gateway.polling = null;
      final context = tester.element(find.byType(ApkActionsPage));
      unawaited(
        Navigator.of(context).push(
          MaterialPageRoute<void>(
            builder: (_) => const Scaffold(body: Text('Covered')),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final coveredReads = gateway.calls.where((c) => c == 'operations').length;
      await tester.pump(const Duration(seconds: 3));
      expect(
        gateway.calls.where((c) => c == 'operations'),
        hasLength(coveredReads),
      );
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      final pausedReads = gateway.calls.where((c) => c == 'operations').length;
      await tester.pump(const Duration(seconds: 3));
      expect(
        gateway.calls.where((c) => c == 'operations'),
        hasLength(pausedReads),
      );
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(gateway.maxPolls, 1);
      await tester.pumpWidget(const SizedBox());
      final disposedReads = gateway.calls.length;
      await tester.pump(const Duration(seconds: 5));
      expect(gateway.calls, hasLength(disposedReads));
    },
  );

  testWidgets(
    'native retirement denial retains evidence and explicit abandonment flag',
    (tester) async {
      final gateway = Gateway()..retirementThrows = true;
      gateway.operations = [
        gateway.operation(status: ApkInstallStatus.interrupted),
      ];
      await open(tester, session(gateway));
      await tap(tester, 'Retire unknown outcome');
      await tap(tester, 'Abandon remaining owned install sessions');
      await tap(tester, 'Retire operation');
      expect(gateway.calls, contains('retire:true:false'));
      expect(find.textContaining('install · interrupted'), findsOneWidget);
      expect(find.textContaining('install · retiredUnknown'), findsNothing);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets(
    'cancel transport and discard staged APK use distinct identifiers',
    (tester) async {
      final gateway = Gateway()..staging = Completer<StagedApk>();
      await open(tester, session(gateway));
      await tester.tap(find.text('Download and inspect APK'));
      await tester.pump();
      await tap(tester, 'Cancel download');
      gateway.staging!.complete(
        StagedApk(token: 'cancelled-stage', identity: identity()),
      );
      await tester.pumpAndSettle();
      expect(
        gateway.calls.where((c) => c.startsWith('cancel:apk-ui-')),
        hasLength(1),
      );
      expect(gateway.calls, contains('discard:cancelled-stage'));
      gateway.staging = null;
      await tap(tester, 'Download and inspect APK');
      await tap(tester, 'Discard staged APK');
      expect(gateway.calls, contains('discard:stage-token'));
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('multi-signer installed APK blocks executable actions honestly', (
    tester,
  ) async {
    final gateway = Gateway()
      ..installed = [
        ApkPluginIdentity(
          id: pluginId,
          version: ApkPluginVersion(2, '2.0'),
          library: '17',
          entrypoint: '.Plugin',
          currentSigners: ['a' * 64, 'b' * 64],
          signerHistory: [],
          compatibility: ApkCompatibility.unsupportedSigner,
          trust: ApkTrust.unapproved,
        ),
      ];
    await open(tester, session(gateway));
    expect(find.textContaining('Unsupported APK identity'), findsOneWidget);
    expect(find.text('Download and inspect APK'), findsNothing);
    expect(find.text('Approve installed signer'), findsNothing);
    expect(find.text('Uninstall APK'), findsNothing);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets(
    'stage inspection, signer approval and install are separate explicit actions',
    (tester) async {
      final gateway = Gateway();
      await open(tester, session(gateway));
      await tap(tester, 'Download and inspect APK');
      expect(find.textContaining('a' * 64), findsWidgets);
      expect(find.textContaining('b' * 64), findsWidgets);
      expect(find.textContaining('Runtime deferred'), findsOneWidget);
      expect(
        gateway.calls.where(
          (c) => c.startsWith('approve:') || c.startsWith('install:'),
        ),
        isEmpty,
      );
      await tap(tester, 'Approve staged signer');
      await tap(tester, 'Cancel');
      expect(gateway.calls.where((c) => c.startsWith('approve:')), isEmpty);
      await tap(tester, 'Approve staged signer');
      await tap(tester, 'Approve signer');
      expect(
        gateway.calls.where((c) => c.startsWith('approve:')),
        hasLength(1),
      );
      expect(gateway.calls.where((c) => c.startsWith('install:')), isEmpty);
      await tap(tester, 'Install APK');
      await tap(tester, 'Submit install');
      expect(
        gateway.calls.where((c) => c.startsWith('install:')),
        hasLength(1),
      );
      expect(find.textContaining('awaitingResult'), findsOneWidget);
      expect(find.text('Installation succeeded'), findsNothing);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('permission settings and foreground resume never auto-submit', (
    tester,
  ) async {
    final gateway = Gateway()..permission = false;
    await open(tester, session(gateway));
    await tap(tester, 'Download and inspect APK');
    await tap(tester, 'Approve staged signer');
    await tap(tester, 'Approve signer');
    await tap(tester, 'Open install settings');
    expect(gateway.calls, contains('settings'));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    gateway.permission = true;
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();
    expect(gateway.calls.where((c) => c.startsWith('install:')), isEmpty);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets(
    'dispose cancels pending transfer and discards late stage, not submitted operations',
    (tester) async {
      final gateway = Gateway()..staging = Completer<StagedApk>();
      await open(tester, session(gateway));
      await tester.tap(find.text('Download and inspect APK'));
      await tester.pump();
      await tester.pumpWidget(const SizedBox());
      gateway.staging!.complete(
        StagedApk(token: 'late-token', identity: identity()),
      );
      await tester.pumpAndSettle();
      expect(gateway.calls.where((c) => c.startsWith('cancel:')), hasLength(1));
      expect(gateway.calls, contains('discard:late-token'));
      expect(
        gateway.calls.where((c) => c.startsWith('cancelInstall:')),
        isEmpty,
      );
    },
  );

  testWidgets(
    'uninstall confirms identity and unresolved retirement needs removal acknowledgement',
    (tester) async {
      final gateway = Gateway()..installed = [identity(approved: true)];
      await open(tester, session(gateway));
      await tap(tester, 'Uninstall APK');
      expect(
        find.textContaining('Library and Progress remain'),
        findsOneWidget,
      );
      expect(find.textContaining('loaded reader may fail'), findsOneWidget);
      await tap(tester, 'Cancel');
      expect(gateway.calls.where((c) => c.startsWith('uninstall:')), isEmpty);
      await tap(tester, 'Uninstall APK');
      await tap(tester, 'Open system removal');
      expect(gateway.calls, contains('uninstall:2:${'a' * 64}'));
      await tap(tester, 'Retire unknown outcome');
      expect(find.textContaining('could remove a replacement'), findsOneWidget);
      await tap(tester, 'Retire operation');
      expect(gateway.calls.where((c) => c.startsWith('retire:')), isEmpty);
      await tap(tester, 'I dismissed the old system removal dialog');
      await tap(tester, 'Retire operation');
      expect(gateway.calls, contains('retire:false:true'));
      expect(find.textContaining('uninstall · retiredUnknown'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );
}
