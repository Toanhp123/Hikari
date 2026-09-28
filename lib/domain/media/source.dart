import 'dart:typed_data';

import 'package:hikari/domain/media/media.dart';

abstract interface class MediaSource {
  SourceId get id;
  String get name;
}

abstract interface class MediaSourceAvailability implements MediaSource {
  bool get isAvailable;
}

abstract interface class MangaSearchSource implements MediaSource {
  Future<List<Media>> search(String query);
}

/// A source that can hand the current player a directly playable locator.
///
/// Sources that need episode discovery, stream selection, headers, DRM or other
/// playback policy should introduce a richer capability instead of pretending to
/// satisfy this direct contract.
abstract interface class DirectVideoSource implements MediaSource {
  String playbackLocator(SourceMediaRef media);
}

final class MangaChapter {
  const MangaChapter({
    required this.title,
    required this.source,
    this.scanlator,
    this.canReadPages = true,
  });
  final String title;
  final SourceMediaRef source;
  final String? scanlator;
  final bool canReadPages;
}

abstract interface class MangaChapterSource implements MediaSource {
  Future<List<MangaChapter>> chapters(SourceMediaRef manga);
}

abstract interface class MangaPageSource implements MediaSource {
  /// A readable can identify a local folder or a remote chapter, not just Media.
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable);
  Future<Uint8List> readPage(SourceMediaRef page);
}

abstract interface class NovelTextSource implements MediaSource {
  Future<String> readText(SourceMediaRef media);
}
