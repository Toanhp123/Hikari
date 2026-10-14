import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/features/reading/continue_reading_page.dart';

typedef _Launch = ({
  Widget reader,
  void Function() abandon,
  Future<void> settled,
});

void main() {
  testWidgets('reader route appears immediately while content is pending', (
    tester,
  ) async {
    final gate = Completer<_Launch>();
    await tester.pumpWidget(
      MaterialApp(home: ContinueReadingPage(open: () => gate.future)),
    );
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    gate.complete((
      reader: const Scaffold(body: Text('Cached chapter')),
      abandon: () {},
      settled: Future<void>.value(),
    ));
    await tester.pump();
    expect(find.text('Cached chapter'), findsOneWidget);
  });

  testWidgets('cache miss shows reader error and retries inside the route', (
    tester,
  ) async {
    var requests = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: ContinueReadingPage(
          open: () async {
            requests++;
            if (requests == 1) throw StateError('offline');
            return (
              reader: const Scaffold(body: Text('Recovered chapter')),
              abandon: () {},
              settled: Future<void>.value(),
            );
          },
        ),
      ),
    );
    await tester.pump();
    expect(find.text('Could not load this chapter.'), findsOneWidget);
    await tester.tap(find.text('Retry'));
    await tester.pump();
    expect(requests, 2);
    expect(find.text('Recovered chapter'), findsOneWidget);
  });

  testWidgets('late open after navigation away releases unused reader', (
    tester,
  ) async {
    final gate = Completer<_Launch>();
    var abandoned = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
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
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
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
