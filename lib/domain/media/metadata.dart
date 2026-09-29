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
    this.ratingMax,
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

  /// Source-declared upper bound; null means the scale is unknown.
  final double? ratingMax;
}
