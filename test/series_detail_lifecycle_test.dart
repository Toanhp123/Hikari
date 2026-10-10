import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/remote_manga/manga_series_page.dart';
import 'package:hikari/features/remote_novel/novel_series_page.dart';
import 'package:hikari/features/remote_manga/widgets/manga_series_content.dart';
import 'package:hikari/features/remote_novel/widgets/novel_series_content.dart';

const _source = SourceId('fake');
SourceMediaRef _ref(String id) => SourceMediaRef(sourceId: _source, itemId: id);

class _NovelSource implements NovelSeriesSource {
  @override
  SourceId get id => _source;
  @override
  String get name => 'Source';
  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => NovelDetails(
    metadata: MediaMetadata(title: 'Series'),
    chapterListOrder: ChapterListOrder.readingOrder,
    chapters: [
      NovelChapter(title: 'Chapter', source: _ref('${novel.itemId}/chapter')),
    ],
  );
}

Widget _page(
  bool manga,
  String id,
  Future<void> Function() open, {
  Object? owner,
}) {
  final media = Media(
    title: 'Series',
    type: manga ? MediaType.manga : MediaType.lightNovel,
    source: _ref(id),
  );
  return MaterialApp(
    home: manga
        ? MangaSeriesPage(
            media: media,
            dependencyOwner: owner,
            sourceName: 'Source',
            loadDetails: () async => MangaSeriesDetails(
              metadata: MediaMetadata(title: 'Series'),
              chapterListOrder: ChapterListOrder.readingOrder,
              chapters: [
                MangaChapter(title: 'Chapter', source: _ref('$id/chapter')),
              ],
            ),
            openChapter: (_, _, _) => open(),
          )
        : NovelSeriesPage(
            dependencyOwner: owner,
            target: NovelSeriesOpenTarget(media, source: _NovelSource()),
            openChapter: (_, _, _) => open(),
          ),
  );
}

void main() {
  for (final manga in [true, false]) {
    final kind = manga ? 'manga' : 'novel';
    testWidgets(
      '$kind rejects repeated taps and restores interaction after failure',
      (tester) async {
        var calls = 0;
        final pending = Completer<void>();
        await tester.pumpWidget(
          _page(manga, 'one', () {
            calls++;
            return pending.future;
          }),
        );
        await tester.pumpAndSettle();
        final tap = tester.widget<ListTile>(find.byType(ListTile)).onTap!;
        tap();
        tap();
        await tester.pump();
        expect(calls, 1);
        expect(tester.widget<ListTile>(find.byType(ListTile)).onTap, isNull);
        expect(
          tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
          isNull,
        );
        pending.completeError(StateError('unavailable'));
        await tester.pumpAndSettle();
        expect(tester.widget<ListTile>(find.byType(ListTile)).onTap, isNotNull);
        expect(
          tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
          isNotNull,
        );
        expect(tester.takeException(), isNull);
      },
    );
    testWidgets('$kind owner replacement ignores pending old open', (
      tester,
    ) async {
      final oldOpen = Completer<void>();
      final newOpen = Completer<void>();
      await tester.pumpWidget(
        _page(manga, 'one', () => oldOpen.future, owner: 'old'),
      );
      await tester.pumpAndSettle();
      tester.widget<ListTile>(find.byType(ListTile)).onTap!();
      await tester.pump();
      await tester.pumpWidget(
        _page(manga, 'one', () => newOpen.future, owner: 'new'),
      );
      await tester.pump();
      await tester.pump();
      expect(tester.widget<ListTile>(find.byType(ListTile)).onTap, isNotNull);
      tester.widget<ListTile>(find.byType(ListTile)).onTap!();
      await tester.pump();
      oldOpen.completeError(StateError('old provider'));
      await tester.pump();
      expect(find.byType(SnackBar), findsNothing);
      expect(tester.widget<ListTile>(find.byType(ListTile)).onTap, isNull);
      newOpen.complete();
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    });

    testWidgets('$kind rebuild updates callbacks without resetting state', (
      tester,
    ) async {
      var oldCalls = 0;
      var newCalls = 0;
      await tester.pumpWidget(
        _page(manga, 'one', () async {
          oldCalls++;
        }, owner: 'same'),
      );
      await tester.pumpAndSettle();
      dynamic viewModel() => manga
          ? tester
                .widget<MangaSeriesContent>(find.byType(MangaSeriesContent))
                .viewModel
          : tester
                .widget<NovelSeriesContent>(find.byType(NovelSeriesContent))
                .viewModel;
      final dynamic before = viewModel();
      before.setSearchQuery('Chapter');
      before.toggleSourceOrder();
      await tester.pump();
      final Object sequence = before.readingSequence as Object;
      await tester.pumpWidget(
        _page(manga, 'one', () async {
          newCalls++;
        }, owner: 'same'),
      );
      await tester.pumpAndSettle();
      expect(identical(viewModel(), before), isTrue);
      expect(identical(viewModel().readingSequence, sequence), isTrue);
      expect(viewModel().searchQuery, 'Chapter');
      expect(viewModel().reverseSourceOrder, isTrue);
      tester.widget<ListTile>(find.byType(ListTile)).onTap!();
      await tester.pumpAndSettle();
      expect(oldCalls, 0);
      expect(newCalls, 1);
      await tester.pumpWidget(
        _page(manga, 'one', () async {}, owner: 'replacement'),
      );
      await tester.pumpAndSettle();
      expect(identical(viewModel(), before), isTrue);
      expect(viewModel().searchQuery, 'Chapter');
      expect(viewModel().reverseSourceOrder, isTrue);
    });

    testWidgets('$kind ignores stale callbacks after disposal', (tester) async {
      var calls = 0;
      await tester.pumpWidget(
        _page(manga, 'one', () async {
          calls++;
        }),
      );
      await tester.pumpAndSettle();
      final tap = tester.widget<ListTile>(find.byType(ListTile)).onTap!;
      await tester.pumpWidget(const SizedBox());
      tap();
      await tester.pump();
      expect(calls, 0);
      expect(tester.takeException(), isNull);
    });
    testWidgets(
      '$kind ignores old series completion while new open is pending',
      (tester) async {
        final oldOpen = Completer<void>();
        final newOpen = Completer<void>();
        await tester.pumpWidget(_page(manga, 'one', () => oldOpen.future));
        await tester.pumpAndSettle();
        final oldTap = tester.widget<ListTile>(find.byType(ListTile)).onTap!;
        oldTap();
        await tester.pump();
        var calls = 0;
        await tester.pumpWidget(
          _page(manga, 'two', () {
            calls++;
            return newOpen.future;
          }),
        );
        await tester.pump();
        await tester.pump();
        oldTap();
        expect(calls, 0);
        tester.widget<ListTile>(find.byType(ListTile)).onTap!();
        await tester.pump();
        oldOpen.completeError(StateError('old failure'));
        await tester.pump();
        expect(find.byType(SnackBar), findsNothing);
        expect(tester.widget<ListTile>(find.byType(ListTile)).onTap, isNull);
        newOpen.complete();
        await tester.pumpAndSettle();
        expect(calls, 1);
        expect(tester.widget<ListTile>(find.byType(ListTile)).onTap, isNotNull);
        expect(tester.takeException(), isNull);
      },
    );
  }
}
