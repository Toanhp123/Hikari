import 'package:hikari/domain/media/media.dart';

sealed class ProgressPosition {
  const ProgressPosition();
}

final class VideoPosition extends ProgressPosition {
  VideoPosition({required this.position, required this.duration}) {
    if (position.isNegative || duration.isNegative) {
      throw ArgumentError('Video times must be nonnegative.');
    }
  }
  final Duration position;
  final Duration duration;
}

final class PagePosition extends ProgressPosition {
  PagePosition({required this.pageIndex, required this.pageCount}) {
    if (pageCount < 0 ||
        pageIndex < 0 ||
        (pageCount == 0 ? pageIndex != 0 : pageIndex >= pageCount)) {
      throw ArgumentError(
        'Page index must be in bounds; empty content uses zero.',
      );
    }
  }
  final int pageIndex;
  final int pageCount;
}

final class TextPosition extends ProgressPosition {
  TextPosition({required this.progression}) {
    if (!progression.isFinite || progression < 0 || progression > 1) {
      throw ArgumentError.value(
        progression,
        'progression',
        'Expected finite [0, 1].',
      );
    }
  }
  final double progression;
}

final class MediaProgress {
  MediaProgress({
    required this.media,
    required this.position,
    required this.completed,
    required DateTime updatedAt,
  }) : updatedAt = updatedAt.toUtc();
  final SourceMediaRef media;
  final ProgressPosition position;
  final bool completed;
  final DateTime updatedAt;
}

abstract interface class ProgressRepository {
  Future<MediaProgress?> load(SourceMediaRef media);
  Future<void> save(MediaProgress progress);
  Future<void> delete(SourceMediaRef media);
}
