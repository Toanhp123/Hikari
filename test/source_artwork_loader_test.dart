import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/patterns/source_artwork_loader.dart';
import 'package:hikari/domain/media/media.dart';

void main() {
  const artwork = SourceMediaRef(
    sourceId: SourceId('test:artwork'),
    itemId: 'cover',
  );

  testWidgets('artwork failures degrade to fallback content', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SourceArtworkLoader(
          artwork: artwork,
          readArtwork: (_) async => throw StateError('offline'),
          builder: (_, bytes, loading) => Text(
            loading
                ? 'loading'
                : bytes == null
                ? 'fallback'
                : 'loaded',
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('fallback'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
