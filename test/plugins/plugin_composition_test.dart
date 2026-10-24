import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/plugin_composition.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/features/plugins/plugin_manager_page.dart';

import 'apk_plugin_gateway_test.dart' as apk;
import 'novel_plugin_gateway_test.dart' as novel;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  const plugins = MethodChannel('hikari/plugins');
  const mihon = MethodChannel('hikari/mihon_extensions');
  const paths = MethodChannel('plugins.flutter.io/path_provider');
  late Directory directory;
  late List<Map<String, Object?>> installed;
  late List<Map<String, Object?>> novels;
  late Map<String, Object?> novelRuntime;
  late Map<String, Object?> apkRuntime;
  late List<String> calls;

  setUp(() {
    directory = Directory.systemTemp.createTempSync('hikari-composition-');
    installed = [apk.row()];
    novels = [novel.inventoryRow()..['admission'] = 'admitted'];
    novelRuntime = {
      'initialization': 'ready',
      'failure': null,
      'loaded': [
        {
          'id': 'inert',
          'name': 'Inert',
          'site': 'https://example.com/novels',
          'artifact': novel.artifact(),
        },
      ],
      'issues': [],
    };
    apkRuntime = {
      'initialized': true,
      'preferencesSupported': false,
      'errors': [],
      'sources': [
        {
          'sourceKey': '1',
          'name': 'Source',
          'language': 'en',
          'packageName': 'org.example.plugin',
          'baseUrl': 'https://example.com',
          'versionCode': 10,
          'signer': 'a' * 64,
          'preferencesSupported': false,
        },
      ],
    };
    calls = [];
    messenger.setMockMethodCallHandler(paths, (_) async => directory.path);
    messenger.setMockMethodCallHandler(plugins, (call) async {
      calls.add(call.method);
      return switch (call.method) {
        'inventory' => installed,
        'novelInventory' => novels,
        'novelRuntimeSnapshot' => novelRuntime,
        _ => throw StateError('Unexpected management call ${call.method}'),
      };
    });
    messenger.setMockMethodCallHandler(mihon, (call) async {
      calls.add(call.method);
      expect(
        call.method,
        'runtimeSnapshot',
        reason: 'management must never capture content sources',
      );
      return apkRuntime;
    });
  });
  tearDown(() {
    for (final channel in [plugins, mihon, paths]) {
      messenger.setMockMethodCallHandler(channel, null);
    }
    debugDefaultTargetPlatformOverride = null;
    directory.deleteSync(recursive: true);
  });

  testWidgets(
    'production composition shows native current, previous and removed revisions without Dart approval',
    (tester) async {
      tester.view.physicalSize = const Size(1000, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final session = (await tester.runAsync(() async {
        debugDefaultTargetPlatformOverride = TargetPlatform.android;
        final session = createPluginManagement();
        debugDefaultTargetPlatformOverride = null;
        await session.refresh();
        return session;
      }))!;
      expect(session.error, isNull);
      expect(session.apks.single.trust, ApkTrust.approved);
      expect(session.manager!.state.approvals, isEmpty);
      expect(
        session.novelRuntime!.loadedCurrent(session.novels.single.current),
        isTrue,
      );
      await tester.runAsync(() async {
        await tester.pumpWidget(
          MaterialApp(
            theme: HikariTheme.darkTheme(),
            home: PluginManagerPage(management: session),
          ),
        );
        await session.refresh();
      });
      await tester.pumpAndSettle();
      await tester.tap(find.text('Installed'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Current revision loaded'), findsNWidgets(2));
      installed = [apk.row()..['versionCode'] = 11];
      novels = [
        novel.inventoryRow()..['current'] = novel.artifact(version: '2.0'),
      ];
      await tester.runAsync(() async {
        await tester.tap(find.byTooltip('Refresh inventory'));
        await session.refresh();
      });
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Different revision loaded; restart required'),
        findsNWidgets(2),
      );
      installed = [];
      novels = [];
      await tester.runAsync(() async {
        await tester.tap(find.byTooltip('Refresh inventory'));
        await session.refresh();
      });
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Loaded removed revision; restart required'),
        findsNWidgets(2),
      );
      expect(calls.toSet(), {
        'inventory',
        'novelInventory',
        'runtimeSnapshot',
        'novelRuntimeSnapshot',
      });
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'production composition separates not-loaded APK trust, Novel rejection and Anime deferral',
    (tester) async {
      tester.view.physicalSize = const Size(1000, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      installed = [
        apk.row()..['trusted'] = false,
        apk.row()
          ..['family'] = 'anime'
          ..['packageName'] = 'org.example.anime',
      ];
      apkRuntime = {
        'initialized': true,
        'preferencesSupported': false,
        'errors': [
          {'packageName': 'org.example.plugin', 'reason': 'untrusted'},
        ],
        'sources': [],
      };
      novels = [novel.inventoryRow()];
      novelRuntime = {
        'initialization': 'ready',
        'failure': null,
        'loaded': [],
        'issues': [
          {'artifact': novel.artifact(), 'reason': 'admission-unknown'},
        ],
      };
      final session = (await tester.runAsync(() async {
        debugDefaultTargetPlatformOverride = TargetPlatform.android;
        final session = createPluginManagement();
        debugDefaultTargetPlatformOverride = null;
        await session.refresh();
        return session;
      }))!;
      await tester.runAsync(() async {
        await tester.pumpWidget(
          MaterialApp(
            theme: HikariTheme.darkTheme(),
            home: PluginManagerPage(management: session),
          ),
        );
        await session.refresh();
      });
      await tester.pumpAndSettle();
      await tester.tap(find.text('Installed'));
      await tester.pumpAndSettle();
      expect(session.apks.first.trust, ApkTrust.unapproved);
      expect(find.textContaining('untrusted'), findsOneWidget);
      expect(find.textContaining('admission-unknown'), findsOneWidget);
      expect(
        find.textContaining('Runtime deferred (runtimeDeferred)'),
        findsOneWidget,
      );
      installed = [apk.row()];
      apkRuntime = {
        'initialized': true,
        'preferencesSupported': false,
        'errors': [],
        'sources': [],
      };
      await tester.runAsync(() async {
        await tester.tap(find.byTooltip('Refresh inventory'));
        await session.refresh();
      });
      await tester.pumpAndSettle();
      expect(session.apks.single.trust, ApkTrust.approved);
      expect(session.manager!.state.approvals, isEmpty);
      expect(
        find.textContaining('manga · org.example.plugin\nNot loaded'),
        findsOneWidget,
      );
      expect(calls.toSet(), {
        'inventory',
        'novelInventory',
        'runtimeSnapshot',
        'novelRuntimeSnapshot',
      });
    },
  );

  test('production composition retains failure, notInitialized, admission and native trust independently', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    final session = createPluginManagement();
    novelRuntime = {
      'initialization': 'failed',
      'failure': 'snapshot-failed',
      'loaded': [],
      'issues': [],
    };
    await session.refresh();
    expect(
      session.novelRuntime!.initialization,
      NovelRuntimeInitialization.failed,
    );
    expect(session.novels.single.admission, NovelAdmission.admitted);
    novelRuntime = {
      'initialization': 'notInitialized',
      'failure': null,
      'loaded': [],
      'issues': [],
    };
    apkRuntime = {
      'initialized': false,
      'preferencesSupported': false,
      'errors': [],
      'sources': [],
    };
    await session.refresh();
    expect(
      session.novelRuntime!.initialization,
      NovelRuntimeInitialization.notInitialized,
    );
    expect(session.apkRuntime!.initialized, isFalse);
    expect(session.apks.single.trust, ApkTrust.approved);
    expect(session.manager!.state.approvals, isEmpty);
    expect(calls.toSet(), {
      'inventory',
      'novelInventory',
      'runtimeSnapshot',
      'novelRuntimeSnapshot',
    });
  });
}
