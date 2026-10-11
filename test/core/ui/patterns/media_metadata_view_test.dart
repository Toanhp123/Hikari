import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';

void main() {
  testWidgets('artwork owner replacement preserves expanded synopsis', (
    tester,
  ) async {
    var reads = 0;
    final metadata = MediaMetadata(
      title: 'Series',
      summary: List.filled(20, 'Long synopsis text.').join(' '),
      cover: const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'cover'),
    );
    Widget page(Object owner) => _app(
      MediaMetadataView(
        metadata: metadata,
        sourceName: 'Source',
        artworkOwner: owner,
        readArtwork: (_) async {
          reads++;
          return null;
        },
      ),
    );
    await tester.pumpWidget(page('first'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Show more'));
    await tester.pumpAndSettle();
    await tester.pumpWidget(page('first'));
    await tester.pumpAndSettle();
    expect(reads, 1);
    await tester.pumpWidget(page('second'));
    await tester.pumpAndSettle();
    expect(reads, 2);
    expect(find.text('Show less'), findsOneWidget);
  });

  testWidgets('renders minimal metadata without crashing or empty gaps', (
    tester,
  ) async {
    final metadata = MediaMetadata(title: 'Minimal Book');
    await tester.pumpWidget(
      _app(MediaMetadataView(metadata: metadata, sourceName: 'Test Source')),
    );
    await tester.pumpAndSettle();

    expect(find.text('Test Source'), findsOneWidget);
    expect(find.textContaining('Authors:'), findsNothing);
    expect(find.textContaining('Genres:'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('renders rich metadata with status, rating, chips, and people', (
    tester,
  ) async {
    final metadata = MediaMetadata(
      title: 'One Piece',
      authors: ['Eiichiro Oda'],
      artists: ['Oda Studio'],
      summary: 'Pirate adventure across the grand line.',
      genres: ['Action', 'Adventure'],
      tags: ['Pirates', 'Shounen'],
      language: 'Japanese',
      publisher: 'Shueisha',
      status: PublicationStatus.ongoing,
      rating: 9.2,
      ratingMax: 10,
    );

    await tester.pumpWidget(
      _app(MediaMetadataView(metadata: metadata, sourceName: 'MangaPlus')),
    );
    await tester.pumpAndSettle();

    expect(find.text('MangaPlus'), findsOneWidget);
    expect(find.text('Author: Eiichiro Oda'), findsOneWidget);
    expect(find.text('Artist: Oda Studio'), findsOneWidget);
    expect(
      find.text('Pirate adventure across the grand line.'),
      findsOneWidget,
    );
    expect(find.text('Action'), findsOneWidget);
    expect(find.text('Adventure'), findsOneWidget);
    expect(find.text('Pirates'), findsOneWidget);
    expect(find.text('Shounen'), findsOneWidget);
    expect(find.text('Ongoing'), findsOneWidget);
    expect(find.text('Rating: 9.2 / 10.0'), findsOneWidget);
    expect(find.text('Language: Japanese'), findsOneWidget);
    expect(find.text('Publisher: Shueisha'), findsOneWidget);
  });

  testWidgets('long summary expands and collapses predictably', (tester) async {
    final longSummary = List.filled(
      15,
      'A very expansive and descriptive novel synopsis for testing.',
    ).join(' ');

    final metadata = MediaMetadata(title: 'Long Novel', summary: longSummary);

    await tester.pumpWidget(
      _app(MediaMetadataView(metadata: metadata, sourceName: 'NovelSource')),
    );
    await tester.pumpAndSettle();

    expect(find.text('Show more'), findsOneWidget);
    await tester.tap(find.text('Show more'));
    await tester.pumpAndSettle();

    expect(find.text('Show less'), findsOneWidget);
    await tester.tap(find.text('Show less'));
    await tester.pumpAndSettle();

    expect(find.text('Show more'), findsOneWidget);
  });

  testWidgets('renders cover artwork safely when provided', (tester) async {
    final metadata = MediaMetadata(
      title: 'Cover Book',
      cover: const SourceMediaRef(
        sourceId: SourceId('test:source'),
        itemId: 'cover-1',
      ),
    );

    await tester.pumpWidget(
      _app(
        MediaMetadataView(
          metadata: metadata,
          sourceName: 'Artwork Source',
          readArtwork: (_) async => Uint8List.fromList([1, 2, 3]),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(SourceArtwork), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('adapts layout between compact and wide constraints', (
    tester,
  ) async {
    final metadata = MediaMetadata(
      title: 'Adaptive Book',
      authors: ['Writer A'],
      summary: 'Short synopsis',
      genres: ['Fantasy'],
    );

    // Test wide layout
    tester.view.physicalSize = const Size(900, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      _app(MediaMetadataView(metadata: metadata, sourceName: 'Wide Source')),
    );
    await tester.pumpAndSettle();
    expect(find.text('Wide Source'), findsOneWidget);
    expect(tester.takeException(), isNull);

    // Test compact layout
    tester.view.physicalSize = const Size(360, 640);
    await tester.pumpWidget(
      _app(MediaMetadataView(metadata: metadata, sourceName: 'Compact Source')),
    );
    await tester.pumpAndSettle();
    expect(find.text('Compact Source'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

Widget _app(Widget child) => MaterialApp(
  theme: HikariTheme.darkTheme(),
  home: Scaffold(body: child),
);
