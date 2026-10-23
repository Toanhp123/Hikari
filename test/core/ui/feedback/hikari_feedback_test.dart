import 'dart:io';
import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/feedback/hikari_async_state_view.dart';
import 'package:hikari/core/ui/content/hikari_section_header.dart';
import 'package:hikari/core/ui/content/hikari_list_row.dart';

void main() {
  testWidgets(
    'canonical loading/empty/fatal feedback remains usable in short viewports',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(320, 240));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      for (final sliver in [false, true]) {
        for (final status in [
          HikariAsyncStatus.loading,
          HikariAsyncStatus.empty,
          HikariAsyncStatus.fatalError,
        ]) {
          var actions = 0;
          final view = HikariAsyncStateView(
            status: status,
            emptyTitle: 'No matching content',
            emptyMessage: List.filled(8, 'More explanation.').join(' '),
            emptyAction: TextButton(
              onPressed: () => actions++,
              child: const Text('Explore'),
            ),
            message: List.filled(8, 'Source inaccessible.').join(' '),
            onRetry: () => actions++,
            child: const Text('Loaded content'),
          );
          await tester.pumpWidget(
            MaterialApp(
              theme: HikariTheme.darkTheme(),
              home: MediaQuery(
                data: const MediaQueryData(textScaler: TextScaler.linear(2)),
                child: Scaffold(
                  body: sliver
                      ? CustomScrollView(
                          slivers: [SliverToBoxAdapter(child: view)],
                        )
                      : view,
                ),
              ),
            ),
          );
          expect(tester.takeException(), isNull);
          final action = switch (status) {
            HikariAsyncStatus.empty => find.text('Explore'),
            HikariAsyncStatus.fatalError => find.text('Try Again'),
            _ => null,
          };
          if (action != null) {
            await tester.ensureVisible(action);
            await tester.pump();
            await tester.tap(action);
            expect(actions, 1);
          } else {
            expect(find.text('Loading content...'), findsOneWidget);
            expect(actions, 0);
          }
          expect(tester.takeException(), isNull);
        }
      }
    },
  );

  testWidgets('refresh and recoverable error retain same content element', (
    tester,
  ) async {
    final controller = TextEditingController(text: 'retained');
    addTearDown(controller.dispose);
    var retries = 0;
    Widget app(HikariAsyncStatus status) => MaterialApp(
      theme: HikariTheme.darkTheme(),
      home: Scaffold(
        body: HikariAsyncStateView(
          status: status,
          message: 'Offline',
          onRetry: () => retries++,
          child: TextField(
            key: const ValueKey('retained'),
            controller: controller,
          ),
        ),
      ),
    );
    await tester.pumpWidget(app(HikariAsyncStatus.loaded));
    final element = tester.element(find.byKey(const ValueKey('retained')));
    await tester.pumpWidget(app(HikariAsyncStatus.refreshing));
    expect(
      tester.element(find.byKey(const ValueKey('retained'))),
      same(element),
    );
    expect(find.byType(LinearProgressIndicator), findsOneWidget);
    await tester.pumpWidget(app(HikariAsyncStatus.recoverableError));
    expect(
      tester.element(find.byKey(const ValueKey('retained'))),
      same(element),
    );
    expect(find.text('retained'), findsOneWidget);
    expect(find.text('No items found'), findsNothing);
    await tester.tap(find.text('Try Again'));
    expect(retries, 1);
    await tester.pumpWidget(app(HikariAsyncStatus.empty));
    expect(find.text('No items found'), findsOneWidget);
    expect(find.byKey(const ValueKey('retained')), findsNothing);
  });

  testWidgets('recoverable notice never covers retained actions at 3x text', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(375, 320));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var calls = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(3)),
          child: Scaffold(
            body: HikariAsyncStateView(
              status: HikariAsyncStatus.recoverableError,
              message: 'Connection failed. Previous content remains available. Try refreshing again later.',
              onRetry: () {},
              child: Align(
                alignment: Alignment.bottomCenter,
                child: TextButton(
                  onPressed: () => calls++,
                  child: const Text('Open'),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    final action = find.widgetWithText(TextButton, 'Open');
    final notice = find.textContaining('Connection failed.');
    expect(
      tester.getRect(action).bottom,
      lessThanOrEqualTo(tester.getRect(notice).top),
    );
    await tester.tap(action);
    expect(calls, 1);
    await tester.ensureVisible(find.text('Try Again'));
    await tester.pump();
    expect(tester.takeException(), isNull);
  });

  testWidgets('section actions and disabled rows reflow at 3x text', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(375, 600));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var calls = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(3)),
          child: Scaffold(
            body: SingleChildScrollView(
              child: Column(
                children: [
                  HikariSectionHeader(
                    title: 'Long section heading',
                    description: 'Description',
                    actions: [
                      TextButton(
                        onPressed: () {},
                        child: const Text('Long translated action'),
                      ),
                    ],
                  ),
                  HikariListRow(
                    title: const Text('Long title that wraps'),
                    subtitle: const Text('Long subtitle that wraps too'),
                    leading: const Icon(Icons.book),
                    trailing: const Icon(Icons.chevron_right),
                    enabled: false,
                    onTap: () => calls++,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
    await tester.ensureVisible(find.text('Long title that wraps'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Long title that wraps'));
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    expect(calls, 0);
    expect(tester.takeException(), isNull);
  });
  testWidgets('passive list row is not announced as disabled action', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: const Scaffold(body: HikariListRow(title: Text('Passive row'))),
      ),
    );
    final node = tester.getSemantics(find.byType(HikariListRow));
    expect(node.getSemanticsData().flagsCollection.isEnabled, Tristate.none);
    expect(node.getSemanticsData().flagsCollection.isButton, isFalse);
    semantics.dispose();
  });

  test('feedback and shared content have domain-free owners', () {
    for (final path in [
      'feedback/hikari_async_state_view.dart',
      'feedback/hikari_notice_banner.dart',
      'content/hikari_section_header.dart',
      'content/hikari_list_row.dart',
    ]) {
      final file = File('lib/core/ui/$path');
      expect(file.existsSync(), isTrue, reason: path);
      expect(
        file.readAsStringSync(),
        isNot(contains('package:hikari/domain/')),
      );
    }
  });
}
