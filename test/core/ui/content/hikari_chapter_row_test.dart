import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/content/hikari_chapter_row.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/catalog/presentation/catalog_chapter_row_data.dart';

void main() {
  const ref = SourceMediaRef(sourceId: SourceId('fixture'), itemId: 'chapter');
  test(
    'typed manga mapper preserves source, scanlator, date and unreadable label',
    () {
      final data = CatalogChapterRowData.manga(
        MangaChapter(
          title: 'One',
          source: ref,
          chapterNumber: 1,
          uploadedAt: DateTime.utc(2026, 10, 23),
          canReadPages: false,
        ),
        sourceName: 'Source',
      );
      expect(data.source, ref);
      expect(data.title, 'One');
      expect(
        data.subtitle,
        'Source · Chapter 1.0 · 2026-10-23 · Not readable in Hikari',
      );
      expect(data.readable, isFalse);
      expect(
        CatalogChapterRowData.manga(
          MangaChapter(title: 'Two', source: ref, scanlator: 'Team'),
          sourceName: 'Source',
        ).subtitle,
        'Team',
      );
    },
  );
  test(
    'typed novel mapper preserves release label precedence and scanlator order',
    () {
      final chapter = NovelChapter(
        title: 'Novel',
        source: ref,
        chapterNumber: 2.5,
        scanlators: ['A', 'B'],
        releaseLabel: 'Yesterday',
        releaseDate: DateTime.utc(2026),
      );
      final data = CatalogChapterRowData.novel(chapter);
      expect(data.source, ref);
      expect(data.subtitle, 'Chapter 2.5 · A · B · Yesterday');
      expect(data.readable, isTrue);
      expect(
        CatalogChapterRowData.novel(
          NovelChapter(
            title: 'Date',
            source: ref,
            releaseDate: DateTime.utc(2026, 10, 23),
          ),
        ).subtitle,
        '2026-10-23',
      );
      expect(
        CatalogChapterRowData.novel(NovelChapter(title: 'Empty', source: ref))
            .subtitle,
        isEmpty,
      );
    },
  );
  testWidgets(
    'disabled chapter cannot activate; continuation announced at 3x RTL',
    (tester) async {
      var taps = 0;
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: const MediaQueryData(textScaler: TextScaler.linear(3)),
            child: Directionality(
              textDirection: TextDirection.rtl,
              child: Scaffold(
                body: SingleChildScrollView(
                  child: HikariChapterRow(
                    key: const ValueKey(ref),
                    title: 'Hành trình العربية',
                    subtitle: 'Nguồn · Chapter 1',
                    enabled: false,
                    isContinuation: true,
                    onTap: () => taps++,
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Hành trình العربية'));
      expect(taps, 0);
      expect(find.bySemanticsLabel(RegExp('Continue reading')), findsOneWidget);
      expect(tester.widget<ListTile>(find.byType(ListTile)).enabled, isFalse);
      expect(tester.takeException(), isNull);
      semantics.dispose();
    },
  );
}
