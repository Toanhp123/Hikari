import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/core/ui/feedback/hikari_notice_banner.dart';
import 'package:hikari/core/ui/layout/hikari_content_region.dart';
import 'package:hikari/core/ui/surfaces/dialog/hikari_dialog.dart';
import 'package:hikari/features/plugins/plugin_manager_page.dart';
import 'package:hikari/features/plugins/apk_actions_page.dart';
import 'package:hikari/features/plugins/novel_actions_page.dart';
import 'package:hikari/features/settings/settings_page.dart';
import 'package:hikari/domain/plugins/plugins.dart';

import 'apk_actions_page_test.dart' as apk;
import 'novel_actions_page_test.dart' as novel;
import 'plugin_management_test.dart' as helpers;

void main() {
  for (final ecosystem in ['APK', 'Novel']) {
    testWidgets('$ecosystem retains GlobalKey on session replacement', (
      tester,
    ) async {
      final firstApk = apk.Gateway()..installed = [apk.identity()];
      final secondApk = apk.Gateway()..installed = [apk.identity()];
      final firstNovel = novel.Gateway()..rows = [novel.row()];
      final secondNovel = novel.Gateway()..rows = [novel.row()];
      final first = ecosystem == 'APK'
          ? apk.session(firstApk)
          : novel.management(firstNovel);
      final second = ecosystem == 'APK'
          ? apk.session(secondApk)
          : novel.management(secondNovel);
      final pageKey = GlobalKey();
      Widget host(PluginManagement session) => MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: ecosystem == 'APK'
            ? ApkActionsPage(
                key: pageKey,
                management: session,
                pluginId: apk.pluginId,
              )
            : NovelActionsPage(
                key: pageKey,
                management: session,
                pluginId: novel.id,
              ),
      );
      await first.refresh();
      await second.refresh();
      await tester.pumpWidget(host(first));
      await tester.pumpAndSettle();
      expect(pageKey.currentWidget?.key, same(pageKey));
      final outerState = pageKey.currentState;
      expect(outerState, isNotNull);
      if (ecosystem == 'APK') {
        await apk.tap(tester, 'Approve installed signer');
      } else {
        await novel.tap(tester, 'Consent and admit current');
      }
      await tester.pumpWidget(host(second));
      await tester.pumpAndSettle();
      expect(pageKey.currentWidget?.key, same(pageKey));
      expect(pageKey.currentState, same(outerState));
      await tester.ensureVisible(
        find.text(ecosystem == 'APK' ? 'Approve signer' : 'Evaluate current'),
      );
      await tester.tap(
        find.text(ecosystem == 'APK' ? 'Approve signer' : 'Evaluate current'),
      );
      await tester.pumpAndSettle();
      expect(
        firstApk.calls.where((call) => call == 'approveInstalled'),
        isEmpty,
      );
      expect(
        secondApk.calls.where((call) => call == 'approveInstalled'),
        isEmpty,
      );
      expect(firstNovel.calls.where((call) => call == 'admit'), isEmpty);
      expect(secondNovel.calls.where((call) => call == 'admit'), isEmpty);
      await tester.pumpWidget(const SizedBox());
    });
  }

  for (final ecosystem in ['APK', 'Novel']) {
    testWidgets('$ecosystem retains GlobalKey on package switch', (
      tester,
    ) async {
      final apkGateway = apk.Gateway()..installed = [apk.identity()];
      final novelGateway = novel.Gateway()..rows = [novel.row()];
      final session = ecosystem == 'APK'
          ? apk.session(apkGateway)
          : novel.management(novelGateway);
      final original = ecosystem == 'APK' ? apk.pluginId : novel.id;
      final pageKey = GlobalKey();
      Widget host(PluginId id) => MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: ecosystem == 'APK'
            ? ApkActionsPage(key: pageKey, management: session, pluginId: id)
            : NovelActionsPage(key: pageKey, management: session, pluginId: id),
      );
      await session.refresh();
      await tester.pumpWidget(host(original));
      await tester.pumpAndSettle();
      expect(pageKey.currentWidget?.key, same(pageKey));
      final outerState = pageKey.currentState;
      expect(outerState, isNotNull);
      if (ecosystem == 'APK') {
        await apk.tap(tester, 'Approve installed signer');
      } else {
        await novel.tap(tester, 'Consent and admit current');
      }
      await tester.pumpWidget(
        host(PluginId(original.ecosystem, 'org.example.replacement')),
      );
      await tester.pumpAndSettle();
      expect(pageKey.currentWidget?.key, same(pageKey));
      expect(pageKey.currentState, same(outerState));
      final confirm = find.text(
        ecosystem == 'APK' ? 'Approve signer' : 'Evaluate current',
      );
      await tester.ensureVisible(confirm);
      await tester.tap(confirm);
      await tester.pumpAndSettle();
      expect(
        apkGateway.calls.where((call) => call == 'approveInstalled'),
        isEmpty,
      );
      expect(novelGateway.calls.where((call) => call == 'admit'), isEmpty);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
    });
  }

  testWidgets(
    'unsupported Manager uses canonical feedback without native calls',
    (tester) async {
      var calls = 0;
      final session = helpers.management(
        store: helpers.MemoryStore(),
        supported: false,
        nativeCall: () => calls++,
      );
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: PluginManagerPage(management: session),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(HikariNoticeBanner), findsOneWidget);
      expect(find.textContaining('Android only'), findsOneWidget);
      expect(find.byTooltip('Add repository'), findsNothing);
      expect(calls, 0);
    },
  );

  for (final width in [375.0, 840.0]) {
    testWidgets(
      'Settings to Manager form cancellation retains state at $width',
      (tester) async {
        tester.view.physicalSize = Size(width, 700);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final session = helpers.management(store: helpers.MemoryStore());
        await tester.pumpWidget(
          MaterialApp(
            theme: HikariTheme.darkTheme(),
            home: Builder(
              builder: (context) => SettingsPage(
                onManagePlugins: () => Navigator.of(context).push<void>(
                  MaterialPageRoute(
                    builder: (_) => PluginManagerPage(management: session),
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Plugins'));
        await tester.pumpAndSettle();
        await tester.tap(find.byTooltip('Add repository'));
        await tester.pumpAndSettle();
        expect(find.byType(HikariDialog), findsOneWidget);
        await tester.enterText(
          find.byType(TextField).last,
          'https://repo.example/index.json',
        );
        tester.view.physicalSize = Size(width == 375 ? 840 : 375, 500);
        await tester.pumpAndSettle();
        expect(find.text('https://repo.example/index.json'), findsOneWidget);
        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();
        expect(find.byType(HikariDialog), findsNothing);
        expect(session.manager!.state.repositories, isEmpty);
        await tester.binding.handlePopRoute();
        await tester.pumpAndSettle();
        expect(find.text('Settings'), findsOneWidget);
      },
    );
  }

  testWidgets('repository Cancel restores keyboard focus to opener', (
    tester,
  ) async {
    final session = helpers.management(store: helpers.MemoryStore());
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: PluginManagerPage(management: session),
      ),
    );
    await tester.pumpAndSettle();
    final opener = Focus.of(tester.element(find.byIcon(Icons.add_rounded)));
    opener.requestFocus();
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pumpAndSettle();
    expect(find.byType(HikariDialog), findsOneWidget);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(opener.hasFocus, isTrue);
    expect(session.manager!.state.repositories, isEmpty);
  });

  for (final width in [
    375.0,
    599.0,
    600.0,
    601.0,
    839.0,
    840.0,
    841.0,
    1200.0,
    1440.0,
  ]) {
    for (final scale in [1.0, 2.0, 3.0]) {
      testWidgets(
        'repository form remains reachable at $width and ${scale}x with IME RTL reduced motion',
        (tester) async {
          tester.view.physicalSize = Size(width, 500);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          final session = helpers.management(store: helpers.MemoryStore());
          await tester.pumpWidget(
            MaterialApp(
              theme: HikariTheme.darkTheme(),
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(context).copyWith(
                  textScaler: TextScaler.linear(scale),
                  viewInsets: const EdgeInsets.only(bottom: 120),
                  disableAnimations: true,
                ),
                child: Directionality(
                  textDirection: TextDirection.rtl,
                  child: child!,
                ),
              ),
              home: PluginManagerPage(management: session),
            ),
          );
          await tester.pumpAndSettle();
          await tester.tap(find.byTooltip('Add repository'));
          await tester.pumpAndSettle();
          await tester.ensureVisible(find.text('Cancel'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Cancel'));
          await tester.pumpAndSettle();
          expect(find.byType(HikariDialog), findsNothing);
          expect(session.manager!.state.repositories, isEmpty);
          expect(tester.takeException(), isNull);
        },
      );
    }
  }

  testWidgets('Manager rejects repository form from replaced session', (
    tester,
  ) async {
    final first = helpers.management(store: helpers.MemoryStore());
    final second = helpers.management(store: helpers.MemoryStore());
    Widget host(PluginManagement session) => MaterialApp(
      theme: HikariTheme.darkTheme(),
      home: PluginManagerPage(management: session),
    );
    await tester.pumpWidget(host(first));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Add repository'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Select Ecosystem'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(MenuItemButton, 'novel').last);
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byType(TextField).last,
      'https://repo.example/index.json',
    );
    await tester.pumpWidget(host(second));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Add').last);
    await tester.pumpAndSettle();
    expect(first.manager!.state.repositories, isEmpty);
    expect(second.manager!.state.repositories, isEmpty);
  });

  testWidgets(
    'Manager APK detail settles modal before route and refreshes on return',
    (tester) async {
      final gateway = apk.Gateway();
      final session = apk.session(gateway);
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: PluginManagerPage(management: session),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Anime plugin').first);
      await tester.pumpAndSettle();
      await apk.tap(tester, 'APK actions');
      expect(find.byType(HikariDialog), findsNothing);
      expect(find.text('APK actions'), findsOneWidget);
      expect(find.byType(HikariContentRegion), findsWidgets);
      final before = gateway.inventoryReads;
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('Plugins'), findsOneWidget);
      expect(gateway.inventoryReads, greaterThan(before));
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('Manager Novel detail return never evaluates bundle', (
    tester,
  ) async {
    final gateway = novel.Gateway();
    final session = novel.management(gateway);
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: PluginManagerPage(management: session),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Novel plugin').first);
    await tester.pumpAndSettle();
    await novel.tap(tester, 'Novel actions');
    expect(find.byType(HikariDialog), findsNothing);
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.text('Plugins'), findsOneWidget);
    expect(
      gateway.calls.where((call) => call == 'admit' || call == 'install'),
      isEmpty,
    );
    expect(tester.takeException(), isNull);
  });
}
