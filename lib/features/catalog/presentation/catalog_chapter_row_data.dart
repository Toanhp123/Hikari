import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';

final class CatalogChapterRowData {
  const CatalogChapterRowData({
    required this.source,
    required this.title,
    required this.subtitle,
    required this.readable,
  });
  factory CatalogChapterRowData.manga(
    MangaChapter chapter, {
    required String sourceName,
  }) => CatalogChapterRowData(
    source: chapter.source,
    title: chapter.title,
    subtitle: [
      chapter.scanlator ?? sourceName,
      if (chapter.chapterNumber != null) 'Chapter ${chapter.chapterNumber}',
      if (chapter.uploadedAt != null)
        chapter.uploadedAt!.toIso8601String().split('T').first,
      if (!chapter.canReadPages) 'Not readable in Hikari',
    ].join(' · '),
    readable: chapter.canReadPages,
  );
  factory CatalogChapterRowData.novel(NovelChapter chapter) =>
      CatalogChapterRowData(
        source: chapter.source,
        title: chapter.title,
        subtitle: [
          if (chapter.chapterNumber != null) 'Chapter ${chapter.chapterNumber}',
          ...chapter.scanlators,
          if (chapter.releaseLabel != null)
            chapter.releaseLabel!
          else if (chapter.releaseDate != null)
            chapter.releaseDate!.toIso8601String().split('T').first,
        ].join(' · '),
        readable: true,
      );
  final SourceMediaRef source;
  final String title;
  final String subtitle;
  final bool readable;
}
