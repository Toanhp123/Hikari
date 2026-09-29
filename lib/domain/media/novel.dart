import 'dart:typed_data';

import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/source.dart';

final class NovelChapter {
  NovelChapter({
    required this.title,
    required this.source,
    this.chapterNumber,
    this.releaseDate,
    this.releaseLabel,
    List<String> scanlators = const [],
  }) : scanlators = List.unmodifiable(scanlators) {
    if (chapterNumber != null && !chapterNumber!.isFinite) {
      throw ArgumentError.value(chapterNumber, 'chapterNumber');
    }
  }

  final String title;
  final SourceMediaRef source;
  final double? chapterNumber;
  final DateTime? releaseDate;
  final String? releaseLabel;
  final List<String> scanlators;
}

final class NovelPreview {
  const NovelPreview({required this.media, required this.metadata});
  final Media media;
  final MediaMetadata metadata;
}

final class NovelSearchPage {
  NovelSearchPage({
    required List<NovelPreview> results,
    required this.page,
    required this.hasNextPage,
  }) : results = List.unmodifiable(results) {
    if (page < 1) throw ArgumentError.value(page, 'page');
  }
  final List<NovelPreview> results;
  final int page;

  /// Null means the upstream contract does not report whether another page exists.
  final bool? hasNextPage;
}

final class NovelDetails {
  NovelDetails({required this.metadata, required List<NovelChapter> chapters})
    : chapters = List.unmodifiable(chapters);
  final MediaMetadata metadata;

  /// Complete list in source order, including all source chapter-list pages.
  final List<NovelChapter> chapters;
}

final class NovelChapterContent {
  NovelChapterContent({
    required this.html,
    Map<String, SourceMediaRef> resources = const {},
  }) : resources = Map.unmodifiable(resources);

  /// Sanitized inert HTML. Resource keys are placeholders, not provider URLs.
  final String html;
  final Map<String, SourceMediaRef> resources;
}

abstract interface class NovelSearchSource implements MediaSource {
  Future<NovelSearchPage> searchNovels(String query, {int page = 1});
}

abstract interface class NovelSeriesSource implements MediaSource {
  Future<NovelDetails> novelDetails(SourceMediaRef novel);
}

abstract interface class NovelChapterSource implements MediaSource {
  Future<NovelChapterContent> chapterContent(SourceMediaRef chapter);
  Future<Uint8List> readResource(SourceMediaRef resource);
}

abstract interface class NovelTextSource implements MediaSource {
  Future<String> readText(SourceMediaRef media);
}
