import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

/// A persisted chapter is enough to start reading. Fetching the series chapter
/// list is an independent operation and must not block the initial reader.
sealed class SeriesContinuationOpenTarget {
  const SeriesContinuationOpenTarget(this.parent, this.chapter);

  final MediaOpenTarget parent;
  final SourceMediaRef chapter;
  Media get series => parent.media;
  Future<void> release() => parent.release();
}

final class MangaContinuationOpenTarget extends SeriesContinuationOpenTarget {
  const MangaContinuationOpenTarget(super.parent, super.chapter);

  MangaChapter get selected =>
      MangaChapter(title: series.title, source: chapter);

  /// An evictable cached sequence is optional navigation metadata, not a
  /// prerequisite to open the saved chapter.
  Future<List<MangaChapter>?> readCachedSequence() async {
    final parent = this.parent as MangaSeriesOpenTarget;
    final chapters = await parent.chapterLists?.readManga(series.source);
    if (chapters == null ||
        !chapters.any((item) => item.source == chapter && item.canReadPages)) {
      return null;
    }
    return chapters;
  }

  /// Only accept a complete, source-owned sequence containing this chapter.
  /// A missing chapter is not permission to silently resume another one.
  Future<List<MangaChapter>> refreshSequence() async {
    final parent = this.parent as MangaSeriesOpenTarget;
    final chapters = (await parent.loadDetails()).chaptersInReadingOrder;
    validateSeriesChapterSequence(
      series.source,
      chapters.map((item) => item.source),
    );
    if (!chapters.any((item) => item.source == chapter && item.canReadPages)) {
      throw StateError('Saved manga chapter is not in the source sequence.');
    }
    return chapters;
  }
}

final class NovelContinuationOpenTarget extends SeriesContinuationOpenTarget {
  const NovelContinuationOpenTarget(super.parent, super.chapter);

  NovelChapter get selected =>
      NovelChapter(title: series.title, source: chapter);

  Future<List<NovelChapter>?> readCachedSequence() async {
    final parent = this.parent as NovelSeriesOpenTarget;
    final chapters = await parent.chapterLists?.readNovel(series.source);
    if (chapters == null || !chapters.any((item) => item.source == chapter)) {
      return null;
    }
    return chapters;
  }

  Future<List<NovelChapter>> refreshSequence() async {
    final parent = this.parent as NovelSeriesOpenTarget;
    final chapters = (await parent.loadDetails()).chaptersInReadingOrder;
    validateSeriesChapterSequence(
      series.source,
      chapters.map((item) => item.source),
    );
    if (!chapters.any((item) => item.source == chapter)) {
      throw StateError('Saved novel chapter is not in the source sequence.');
    }
    return chapters;
  }
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
    switch (parent) {
      case MangaSeriesOpenTarget():
        return MangaContinuationOpenTarget(parent, chapter);
      case NovelSeriesOpenTarget():
        return NovelContinuationOpenTarget(parent, chapter);
      default:
        await parent.release();
        throw StateError('Saved continuation requires a remote series.');
    }
  }
}
