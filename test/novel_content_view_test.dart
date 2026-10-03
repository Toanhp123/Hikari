import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/novel_reader/widgets/novel_content_view.dart';

void main() {
  testWidgets('renders prose without fetching unregistered image URLs', (
    tester,
  ) async {
    var requests = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: NovelContentView(
            content: RichReadingContent(
              html: '<p>Hello reader</p><img src="https://private.test/a">',
            ),
            readResource: (_) async {
              requests++;
              return Uint8List(0);
            },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Hello reader', findRichText: true), findsOneWidget);
    expect(requests, 0);
    expect(tester.takeException(), isNull);
  });

  testWidgets('routes registered images through source and contains errors', (
    tester,
  ) async {
    const ref = SourceMediaRef(
      sourceId: SourceId('novel:test'),
      itemId: 'image',
    );
    final requests = <SourceMediaRef>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: NovelContentView(
            content: RichReadingContent(
              html: '<img src="illustration" alt="Map">',
              resources: {'illustration': ref},
            ),
            readResource: (resource) async {
              requests.add(resource);
              throw StateError('private failure');
            },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(requests, [ref]);
    expect(find.text('Could not load illustration.'), findsOneWidget);
    expect(find.textContaining('private failure'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
