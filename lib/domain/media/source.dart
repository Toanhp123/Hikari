import 'dart:typed_data';

import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/reading.dart';

abstract interface class MediaSource {
  SourceId get id;
  String get name;
}

abstract interface class MediaSourceAvailability implements MediaSource {
  bool get isAvailable;
}

abstract interface class MangaSearchSource implements MediaSource {
  Future<MangaSearchPage> search(String query, {int page = 1});
}

/// A source that resolves one series and its chapters in one provider call.
abstract interface class DirectVideoSource implements MediaSource {
  String playbackLocator(SourceMediaRef media);
}

abstract interface class MangaSeriesSource implements MediaSource {
  Future<MangaSeriesDetails> loadSeries(SourceMediaRef manga);
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

abstract interface class ArtworkSource implements MediaSource {
  Future<Uint8List> readArtwork(SourceMediaRef artwork);
}

abstract interface class MangaPageSource implements MediaSource {
  /// A readable can identify a local folder or a remote chapter, not just Media.
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable);
  Future<Uint8List> readPage(SourceMediaRef page);
}

abstract interface class NovelTextSource implements MediaSource {
  Future<String> readText(SourceMediaRef media);
}
