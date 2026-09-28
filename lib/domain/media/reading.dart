import 'package:hikari/domain/media/media.dart';

enum PublicationStatus {
  unknown,
  ongoing,
  completed,
  licensed,
  publishingFinished,
  cancelled,
  onHiatus,
}

final class MediaMetadata {
  const MediaMetadata({
    required this.title,
    this.cover,
    this.summary,
    this.authors = const [],
    this.artists = const [],
    this.genres = const [],
    this.tags = const [],
    this.language,
    this.publisher,
    this.status = PublicationStatus.unknown,
    this.rawStatus,
    this.rating,
  });

  final String title;
  final SourceMediaRef? cover;
  final String? summary;
  final List<String> authors;
  final List<String> artists;
  final List<String> genres;
  final List<String> tags;
  final String? language;
  final String? publisher;
  final PublicationStatus status;
  final String? rawStatus;
  final double? rating;
}

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
