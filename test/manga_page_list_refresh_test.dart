import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';

const _id = SourceId('manga');
SourceMediaRef _page(String id) => SourceMediaRef(sourceId: _id, itemId: id);

void main() {
  final png = base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==',
  );

  testWidgets(
    'refresh remaps same page after insertion and updates page count',
    (tester) async {
      final saved = <PagePosition>[];
      var accepted = <SourceMediaRef>[];
      final commits = <String>[];
      await tester.pumpWidget(
        MaterialApp(
          home: MangaReaderPage(
            title: 'Chapter',
            loadPages: () async => [_page('a'), _page('c')],
            readPage: (_) async => png,
            reloadPage: (_) async => png,
            refreshPages: () async => [_page('a'), _page('b'), _page('c')],
            onFreshPageValidated: (page, bytes) async {
              expect(page, _page('c'));
              expect(bytes, png);
              commits.add('page bytes');
            },
            onPagesRefreshed: (pages) async {
              commits.add('page list');
              accepted = pages;
            },
            saveProgress: (position, _) async =>
                saved.add(position as PagePosition),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.runAsync(
        () => precacheImage(
          tester.widget<Image>(find.byType(Image)).image,
          tester.element(find.byType(MangaReaderPage)),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Next page'));
      await tester.pumpAndSettle();
      expect(find.text('Page 2 of 2'), findsOneWidget);

      await tester.tap(find.byTooltip('Refresh page list'));
      await tester.pumpAndSettle();
      expect(find.text('Page 3 of 3'), findsOneWidget);
      expect(accepted, [_page('a'), _page('b'), _page('c')]);
      expect(commits, ['page bytes', 'page list']);
      expect(find.text('Could not refresh chapter pages.'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('network refresh failure rolls back visible page list', (
    tester,
  ) async {
    var accepted = false;
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Chapter',
          loadPages: () async => [_page('a'), _page('b')],
          readPage: (_) async => png,
          reloadPage: (_) async => png,
          refreshPages: () async => throw StateError('offline'),
          onPagesRefreshed: (_) async => accepted = true,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Refresh page list'));
    await tester.pumpAndSettle();
    expect(find.text('Page 1 of 2'), findsOneWidget);
    expect(find.byType(Image), findsOneWidget);
    expect(accepted, isFalse);
  });

  testWidgets('refreshed list is not committed when selected page fails', (
    tester,
  ) async {
    var accepted = false;
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Chapter',
          loadPages: () async => [_page('old')],
          readPage: (_) async => png,
          reloadPage: (_) async => throw StateError('image unavailable'),
          refreshPages: () async => [_page('new')],
          onPagesRefreshed: (_) async => accepted = true,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Refresh page list'));
    await tester.pumpAndSettle();
    expect(find.text('Page 1 of 1'), findsOneWidget);
    expect(find.byType(Image), findsOneWidget);
    expect(accepted, isFalse);
  });

  testWidgets('removed current reference clamps to a valid new page', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Chapter',
          loadPages: () async => [_page('a'), _page('b')],
          readPage: (_) async => png,
          reloadPage: (_) async => png,
          refreshPages: () async => [_page('replacement')],
          onPagesRefreshed: (_) async {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Next page'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Refresh page list'));
    await tester.pumpAndSettle();
    expect(find.text('Page 1 of 1'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('invalid refreshed image bytes keep previous visible page', (
    tester,
  ) async {
    var accepted = false;
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Chapter',
          loadPages: () async => [_page('a')],
          readPage: (_) async => png,
          reloadPage: (_) async => Uint8List.fromList([1, 2, 3]),
          refreshPages: () async => [_page('b')],
          onPagesRefreshed: (_) async => accepted = true,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Refresh page list'));
    await tester.pumpAndSettle();
    expect(find.text('Page 1 of 1'), findsOneWidget);
    expect(find.byType(Image), findsOneWidget);
    expect(accepted, isFalse);
    expect(tester.takeException(), isNull);
  });

  testWidgets('truncated PNG header does not commit pages or progress', (
    tester,
  ) async {
    // A valid PNG signature and IHDR are not proof of decodable pixels.
    final truncated = Uint8List.fromList(png.sublist(0, 33));
    final saved = <PagePosition>[];
    var persistedBytes = false;
    var persistedPages = false;
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Chapter',
          loadPages: () async => [_page('a'), _page('b')],
          readPage: (_) async => png,
          fetchFreshPage: (_) async => truncated,
          refreshPages: () async => [_page('replacement')],
          onFreshPageValidated: (_, _) async => persistedBytes = true,
          onPagesRefreshed: (_) async => persistedPages = true,
          saveProgress: (position, _) async =>
              saved.add(position as PagePosition),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Next page'));
    await tester.pumpAndSettle();
    final previousImage = tester.widget<Image>(find.byType(Image)).image;
    final previousProgress = List<PagePosition>.of(saved);

    await tester.tap(find.byTooltip('Refresh page list'));
    await tester.pumpAndSettle();

    expect(find.text('Page 2 of 2'), findsOneWidget);
    expect(tester.widget<Image>(find.byType(Image)).image, same(previousImage));
    expect(persistedBytes, isFalse);
    expect(persistedPages, isFalse);
    expect(saved, previousProgress);
    expect(find.text('Could not refresh chapter pages.'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('late refresh cannot replace pages after reader is removed', (
    tester,
  ) async {
    final pending = Completer<List<SourceMediaRef>>();
    var accepted = false;
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Chapter',
          loadPages: () async => [_page('old')],
          readPage: (_) async => png,
          reloadPage: (_) async => png,
          refreshPages: () => pending.future,
          onPagesRefreshed: (_) async => accepted = true,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Refresh page list'));
    await tester.pump();
    await tester.pumpWidget(const MaterialApp(home: SizedBox()));
    pending.complete([_page('new')]);
    await tester.pump();
    expect(accepted, isFalse);
    expect(tester.takeException(), isNull);
  });
}
