import 'dart:typed_data';

import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/source.dart';

final class MangaPreview {
  const MangaPreview({required this.media, this.metadata});

  final Media media;
  final MediaMetadata? metadata;
}

final class MangaSearchPage {
  MangaSearchPage({
    required List<MangaPreview> results,
    required this.hasNextPage,
    required this.page,
  }) : results = List.unmodifiable(results) {
    if (page < 1) throw ArgumentError.value(page, 'page');
  }

  final List<MangaPreview> results;
  final bool hasNextPage;
  final int page;
}

final class MangaChapter {
  MangaChapter({
    required this.title,
    required this.source,
    this.scanlator,
    this.chapterNumber,
    this.uploadedAt,
    this.canReadPages = true,
  }) {
    if (chapterNumber != null && !chapterNumber!.isFinite) {
      throw ArgumentError.value(chapterNumber, 'chapterNumber');
    }
  }

  final String title;
  final SourceMediaRef source;
  final String? scanlator;
  final double? chapterNumber;
  final DateTime? uploadedAt;
  final bool canReadPages;
}

final class MangaSeriesDetails {
  MangaSeriesDetails({
    required this.metadata,
    required List<MangaChapter> chapters,
    this.chapterListOrder = ChapterListOrder.readingOrder,
  }) : chapters = List.unmodifiable(chapters);

  final MediaMetadata metadata;

  /// Chapters in structural source order. Input is snapshotted immutably.
  final List<MangaChapter> chapters;

  /// Defaults to reading order when callers do not declare source order.
  final ChapterListOrder chapterListOrder;

  List<MangaChapter> get chaptersInReadingOrder =>
      chapterListOrder == ChapterListOrder.readingOrder
      ? chapters
      : List.unmodifiable(chapters.reversed);
}

abstract interface class MangaSearchSource implements MediaSource {
  Future<MangaSearchPage> search(String query, {int page = 1});
}

abstract interface class MangaSeriesSource implements MediaSource {
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef manga);
}

abstract interface class MangaPageSource implements MediaSource {
  /// A readable can identify a local folder or a remote chapter, not just Media.
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable);
  Future<Uint8List> readPage(SourceMediaRef page);
}
