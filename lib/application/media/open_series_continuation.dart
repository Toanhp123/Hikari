import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

sealed class SeriesContinuationOpenTarget {
  const SeriesContinuationOpenTarget(this.parent, this.chapter);
  final MediaOpenTarget parent;
  final SourceMediaRef chapter;
  Media get series => parent.media;
  Future<void> release() => parent.release();
}

final class MangaContinuationOpenTarget extends SeriesContinuationOpenTarget {
  const MangaContinuationOpenTarget(super.parent, super.chapter, this.sequence);
  final List<MangaChapter> sequence;
  MangaChapter get selected =>
      sequence.singleWhere((item) => item.source == chapter);
}

final class NovelContinuationOpenTarget extends SeriesContinuationOpenTarget {
  const NovelContinuationOpenTarget(super.parent, super.chapter, this.sequence);
  final List<NovelChapter> sequence;
  NovelChapter get selected =>
      sequence.singleWhere((item) => item.source == chapter);
}

final class OpenSeriesContinuation {
  const OpenSeriesContinuation(this._openMedia);

  final OpenMedia _openMedia;

  Future<SeriesContinuationOpenTarget> execute(
    Media media,
    SourceMediaRef chapter,
  ) async {
    SeriesContinuation(series: media.source, chapter: chapter);
    final parent = await _openMedia.execute(media);
    try {
      switch (parent) {
        case MangaSeriesOpenTarget():
          final details = await parent.loadDetails();
          final sequence = details.chaptersInReadingOrder;
          validateSeriesChapterSequence(
            media.source,
            sequence.map((item) => item.source),
          );
          final selected = resolveSeriesReadingTarget(
            media.source,
            sequence
                .where((item) => item.canReadPages)
                .map((item) => item.source)
                .toList(),
            chapter,
          ).chapter;
          if (selected == null) {
            throw StateError('Series has no readable chapters.');
          }
          return MangaContinuationOpenTarget(parent, selected, sequence);
        case NovelSeriesOpenTarget():
          final details = await parent.loadDetails();
          final sequence = details.chaptersInReadingOrder;
          final selected = resolveSeriesReadingTarget(
            media.source,
            sequence.map((item) => item.source).toList(),
            chapter,
          ).chapter;
          if (selected == null) {
            throw StateError('Series has no readable chapters.');
          }
          return NovelContinuationOpenTarget(parent, selected, sequence);
        default:
          throw StateError('Saved continuation requires a remote series.');
      }
    } catch (_) {
      await parent.release();
      rethrow;
    }
  }
}
