import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/sources/read_source_artwork.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/features/source_search/source_search_page.dart';
import 'package:hikari/features/source_search/source_search_view_model.dart';

void main() {
  testWidgets(
    'SourceSearchPage shows initial empty state and renders query results',
    (tester) async {
      const item1 = Media(
        title: 'Solo Leveling',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'sl'),
      );

      Media? tappedMedia;
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: SourceSearchPage(
            scanLocalMedia: () async => [item1],
            openMedia: (_, item) => tappedMedia = item,
          ),
        ),
      );

      expect(find.text('Find a source'), findsOneWidget);

      // Enter search query
      await tester.enterText(find.byType(TextField), 'Solo');
      await tester.pump(const Duration(milliseconds: 350));
      await tester.pumpAndSettle();

      expect(find.text('Solo Leveling'), findsOneWidget);

      await tester.tap(find.text('Solo Leveling'));
      await tester.pumpAndSettle();
      expect(tappedMedia, item1);
    },
  );

  testWidgets('remote source search renders source-owned cover metadata', (
    tester,
  ) async {
    final source = _ArtworkMangaSource();
    final registry = SourceRegistry([source]);
    final artwork = ReadSourceArtwork(registry);

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: SourceSearchPage(
          searchManga: SearchManga(registry),
          readArtwork: artwork.execute,
          initialQuery: 'Covered title',
          fixedMediaType: MediaType.manga,
          openMedia: (_, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byWidgetPredicate(
        (widget) => widget is Text && widget.data == 'Covered title',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('Cover Author'), findsOneWidget);
    expect(source.artworkReads, 1);
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is Image &&
            widget.image is ResizeImage &&
            (widget.image as ResizeImage).imageProvider is MemoryImage,
      ),
      findsOneWidget,
    );
  });

  testWidgets('artwork failure keeps remote search result usable', (
    tester,
  ) async {
    final source = _ArtworkMangaSource(failArtwork: true);
    final registry = SourceRegistry([source]);
    final artwork = ReadSourceArtwork(registry);

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: SourceSearchPage(
          searchManga: SearchManga(registry),
          readArtwork: artwork.execute,
          initialQuery: 'Covered title',
          fixedMediaType: MediaType.manga,
          openMedia: (_, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byWidgetPredicate(
        (widget) => widget is Text && widget.data == 'Covered title',
      ),
      findsOneWidget,
    );
    expect(source.artworkReads, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('scoped manual search keeps the selected source visible', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: SourceSearchPage(
          openMedia: (_, _) {},
          initialSourceId: const SourceId('test:manga'),
          sourceName: 'Manga source [en]',
          initialFilter: SourceSearchFilter.manga,
        ),
      ),
    );

    expect(find.text('Searching in Manga source [en]'), findsOneWidget);
    expect(find.text('All'), findsNothing);
    expect(find.text('Manga'), findsNothing);
  });
}

final class _ArtworkMangaSource
    implements MangaSearchSource, MangaPageSource, ArtworkSource {
  _ArtworkMangaSource({this.failArtwork = false});

  final bool failArtwork;
  static const _cover = SourceMediaRef(
    sourceId: SourceId('test:artwork-manga'),
    itemId: 'cover',
  );

  int artworkReads = 0;

  @override
  SourceId get id => const SourceId('test:artwork-manga');

  @override
  String get name => 'Artwork manga';

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async {
    return MangaSearchPage(
      results: [
        MangaPreview(
          media: Media(
            title: 'Covered title',
            type: MediaType.manga,
            source: SourceMediaRef(sourceId: id, itemId: 'covered-title'),
          ),
          metadata: MediaMetadata(
            title: 'Covered title',
            cover: _cover,
            authors: const ['Cover Author'],
          ),
        ),
      ],
      hasNextPage: false,
      page: page,
    );
  }

  @override
  Future<Uint8List> readArtwork(SourceMediaRef artwork) async {
    artworkReads++;
    if (failArtwork) throw StateError('offline');
    return Uint8List.fromList(
      base64Decode(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
      ),
    );
  }

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}
