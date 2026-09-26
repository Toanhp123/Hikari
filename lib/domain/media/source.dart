import 'dart:typed_data';

import 'package:hikari/domain/media/media.dart';

abstract interface class MediaSource {
  SourceId get id;
  String get name;
}

abstract interface class MediaSearchSource implements MediaSource {
  Future<List<Media>> search(String query);
}

final class MangaChapter {
  const MangaChapter({
    required this.title,
    required this.source,
    this.scanlator,
  });
  final String title;
  final SourceMediaRef source;
  final String? scanlator;
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
