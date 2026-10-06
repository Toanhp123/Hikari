import 'package:hikari/application/media/open_media.dart';
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
    final parent = await _openMedia.execute(media);
    try {
      SeriesContinuation(series: media.source, chapter: chapter);
      switch (parent) {
        case MangaSeriesOpenTarget():
          final details = await parent.loadDetails();
          final sequence = details.chaptersInReadingOrder;
          _validate(sequence.map((item) => item.source), chapter, media.source);
          if (sequence
                  .singleWhere((item) => item.source == chapter)
                  .canReadPages ==
              false) {
            throw StateError('Saved manga chapter is not readable.');
          }
          return MangaContinuationOpenTarget(parent, chapter, sequence);
        case NovelSeriesOpenTarget():
          final details = await parent.loadDetails();
          final sequence = details.chaptersInReadingOrder;
          _validate(sequence.map((item) => item.source), chapter, media.source);
          return NovelContinuationOpenTarget(parent, chapter, sequence);
        default:
          throw StateError('Saved continuation requires a remote series.');
      }
    } catch (_) {
      await parent.release();
      rethrow;
    }
  }

  static void _validate(
    Iterable<SourceMediaRef> refs,
    SourceMediaRef chapter,
    SourceMediaRef series,
  ) {
    final list = refs.toList(growable: false);
    if (list.any(
          (ref) => ref.sourceId != series.sourceId || ref.itemId.isEmpty,
        ) ||
        list.toSet().length != list.length ||
        list.where((ref) => ref == chapter).length != 1) {
      throw StateError(
        'Saved chapter is missing or duplicated in fresh sequence.',
      );
    }
  }
}
