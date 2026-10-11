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
  testWidgets('scoped source label is announced once in full', (tester) async {
    tester.view.physicalSize = const Size(320, 720);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final semantics = tester.ensureSemantics();
    const name = 'A very long source name that may be clipped visually';
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: SourceSearchPage(catalogScopeLabel: name, openMedia: (_, _) {}),
      ),
    );

    expect(find.bySemanticsLabel('Searching in $name'), findsOneWidget);
    semantics.dispose();
  });

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
      await tester.enterText(find.byType(SearchBar), 'Solo');
      await tester.pump(const Duration(milliseconds: 350));
      expect(find.text('Solo Leveling'), findsNothing);
      await tester.tap(find.byTooltip('Submit search'));
      await tester.pumpAndSettle();

      expect(find.text('Solo Leveling'), findsOneWidget);

      await tester.tapAt(tester.getCenter(find.text('Solo Leveling')));
      await tester.pumpAndSettle();
      expect(tappedMedia, item1);
    },
  );

  testWidgets('source search has one submit and singular filters', (
    tester,
  ) async {
    const novel = Media(
      title: 'Novel title',
      type: MediaType.lightNovel,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'novel'),
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: SourceSearchPage(
          scanLocalMedia: () async => [novel],
          openMedia: (_, _) {},
        ),
      ),
    );

    expect(find.text('All'), findsOneWidget);
    expect(find.text('Light Novel'), findsOneWidget);
    expect(find.text('Light Novels'), findsNothing);
    await tester.enterText(find.byType(SearchBar), 'Novel');
    await tester.pump();
    expect(find.byTooltip('Submit search'), findsOneWidget);
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    expect(find.text('Novel title'), findsOneWidget);
    await tester.tap(find.text('Light Novel'));
    await tester.pumpAndSettle();
    expect(find.text('Novel title'), findsOneWidget);
    await tester.tap(find.text('All'));
    await tester.pumpAndSettle();
    expect(find.text('Novel title'), findsOneWidget);
  });

  testWidgets('source search stays centered and bounded as width changes', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(375, 900);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    const manga = Media(
      title: 'Manga title',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'manga'),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: SourceSearchPage(
          scanLocalMedia: () async => [manga],
          openMedia: (_, _) {},
        ),
      ),
    );
    await tester.enterText(find.byType(SearchBar), 'Manga');
    await tester.pump();
    await tester.tap(find.byTooltip('Submit search'));
    await tester.pumpAndSettle();
    expect(find.text('Manga title'), findsOneWidget);

    for (final width in [320.0, 375.0, 600.0, 840.0, 1200.0, 1800.0]) {
      tester.view.physicalSize = Size(width, 900);
      await tester.pumpAndSettle();
      final contentWidth = width < HikariBreakpoints.maxContentWidth
          ? width
          : HikariBreakpoints.maxContentWidth;
      expect(
        tester.getSize(find.byType(SearchBar)).width,
        moreOrLessEquals(contentWidth - HikariSpacing.lg * 2),
      );
      expect(
        tester.getSize(find.byType(GridView)).width,
        moreOrLessEquals(contentWidth),
      );
      expect(
        tester.getCenter(find.byType(SearchBar)).dx,
        closeTo(width / 2, 1),
      );
      expect(tester.takeException(), isNull);
    }
  });

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
