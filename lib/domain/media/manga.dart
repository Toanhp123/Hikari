import 'dart:typed_data';

import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/source.dart';

final class MangaPreview {
  const MangaPreview({required this.media, this.metadata});

  final Media media;
  final MediaMetadata? metadata;
}

final class MangaSearchPage {
  const MangaSearchPage({
    required this.results,
    required this.hasNextPage,
    required this.page,
  }) : assert(page > 0);

  final List<MangaPreview> results;
  final bool hasNextPage;
  final int page;
}

final class MangaChapter {
  const MangaChapter({
    required this.title,
    required this.source,
    this.scanlator,
    this.chapterNumber,
    this.dateUpload,
    this.canReadPages = true,
  });

  final String title;
  final SourceMediaRef source;
  final String? scanlator;
  final double? chapterNumber;
  final int? dateUpload;
  final bool canReadPages;
}

final class MangaSeriesDetails {
  const MangaSeriesDetails({required this.metadata, required this.chapters});

  final MediaMetadata metadata;
  final List<MangaChapter> chapters;
}

abstract interface class MangaSearchSource implements MediaSource {
  Future<MangaSearchPage> search(String query, {int page = 1});
}

abstract interface class MangaSeriesSource implements MediaSource {
  Future<MangaSeriesDetails> loadSeries(SourceMediaRef manga);
}

abstract interface class MangaPageSource implements MediaSource {
  /// A readable can identify a local folder or a remote chapter, not just Media.
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable);
  Future<Uint8List> readPage(SourceMediaRef page);
}
