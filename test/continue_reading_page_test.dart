import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/feedback/hikari_async_state_view.dart';
import 'package:hikari/core/ui/layout/hikari_content_region.dart';
import 'package:hikari/core/ui/layout/hikari_page_scaffold.dart';
import 'package:hikari/features/reading/continue_reading_page.dart';

typedef _Launch = ({
  Widget reader,
  void Function() abandon,
  Future<void> settled,
});

Widget _testApp(Widget child) {
  return MaterialApp(theme: HikariTheme.darkTheme(), home: child);
}

void main() {
  testWidgets('reader route appears immediately while content is pending', (
    tester,
  ) async {
    final gate = Completer<_Launch>();
    await tester.pumpWidget(
      _testApp(ContinueReadingPage(open: () => gate.future)),
    );
    expect(find.byType(HikariPageScaffold), findsOneWidget);
    expect(find.byType(HikariContentRegion), findsOneWidget);
    expect(
      tester
          .widget<HikariAsyncStateView>(find.byType(HikariAsyncStateView))
          .status,
      HikariAsyncStatus.loading,
    );
    expect(find.text('Loading content...'), findsOneWidget);
    gate.complete((
      reader: const Center(child: Text('Cached chapter')),
      abandon: () {},
      settled: Future<void>.value(),
    ));
    await tester.pump();
    expect(find.text('Cached chapter'), findsOneWidget);
    expect(find.byType(HikariPageScaffold), findsNothing);
    expect(find.byType(HikariAsyncStateView), findsNothing);
  });

  testWidgets('cache miss shows reader error and retries inside the route', (
    tester,
  ) async {
    var requests = 0;
    final retry = Completer<_Launch>();
    await tester.pumpWidget(
      _testApp(
        ContinueReadingPage(
          open: () async {
            requests++;
            if (requests == 1) throw StateError('offline');
            return retry.future;
          },
        ),
      ),
    );
    await tester.pump();
    expect(find.byType(HikariPageScaffold), findsOneWidget);
    expect(find.byType(HikariContentRegion), findsOneWidget);
    final feedback = tester.widget<HikariAsyncStateView>(
      find.byType(HikariAsyncStateView),
    );
    expect(feedback.status, HikariAsyncStatus.fatalError);
    expect(feedback.onRetry, isNotNull);
    expect(find.text('Could not load this chapter.'), findsOneWidget);
    await tester.tap(find.text('Try Again'));
    await tester.pump();
    expect(requests, 2);
    expect(
      tester
          .widget<HikariAsyncStateView>(find.byType(HikariAsyncStateView))
          .status,
      HikariAsyncStatus.loading,
    );
    retry.complete((
      reader: const Center(child: Text('Recovered chapter')),
      abandon: () {},
      settled: Future<void>.value(),
    ));
    await tester.pump();
    expect(find.text('Recovered chapter'), findsOneWidget);
    expect(find.byType(HikariPageScaffold), findsNothing);
  });

  testWidgets('late open after navigation away releases unused reader', (
    tester,
  ) async {
    final gate = Completer<_Launch>();
    var abandoned = 0;
    await tester.pumpWidget(
      _testApp(
        Builder(
          builder: (context) => Scaffold(
            body: FilledButton(
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => ContinueReadingPage(open: () => gate.future),
                ),
              ),
              child: const Text('Continue'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Continue'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 350));
    expect(find.byType(HikariAsyncStateView), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    gate.complete((
      reader: const Scaffold(body: Text('Too late')),
      abandon: () => abandoned++,
      settled: Future<void>.value(),
    ));
    await tester.pump();
    expect(abandoned, 1);
    expect(find.text('Too late'), findsNothing);
  });
}
