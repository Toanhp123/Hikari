import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/series_continuation.dart';
import 'package:hikari/domain/progress/continue_reading_item.dart';

final class ContinueReadingResult {
  const ContinueReadingResult(this.items, this.error);
  final List<ContinueReadingItem> items;
  final Object? error;
}

final class LoadContinueReading {
  const LoadContinueReading(this._progress, [this._continuations]);

  final ProgressRepository _progress;
  final SeriesContinuationRepository? _continuations;

  Future<ContinueReadingResult> execute(List<LibraryEntry> entries) async {
    final items = <({ContinueReadingItem item, DateTime updatedAt})>[];
    Object? error;
    for (final entry in entries) {
      try {
        final parent = entry.media;
        final series = parent.type != MediaType.anime && _continuations != null;
        final child = series ? await _continuations.load(parent.source) : null;
        if (child != null) {
          SeriesContinuation(series: parent.source, chapter: child);
        }
        final reference = child ?? parent.source;
        final progress = await _progress.load(reference);
        if (progress == null || (progress.completed && child == null)) continue;
        if (child != null &&
            ((parent.type == MediaType.manga &&
                    progress.position is! PagePosition) ||
                (parent.type == MediaType.lightNovel &&
                    progress.position is! TextPosition))) {
          throw const FormatException(
            'Continuation progress has wrong position.',
          );
        }
        final fraction = switch (progress.position) {
          VideoPosition(:final position, :final duration)
              when duration > Duration.zero =>
            position.inMilliseconds / duration.inMilliseconds,
          VideoPosition() => null,
          PagePosition(:final pageIndex, :final pageCount) when pageCount > 0 =>
            (pageIndex + 1) / pageCount,
          TextPosition(:final progression) => progression,
          DocumentPosition(:final progression, :final totalProgression) =>
            totalProgression ?? progression ?? 0.0,
          _ => null,
        };
        if (fraction == null) continue;
        items.add((
          item: ContinueReadingItem(
            media: parent,
            progress: fraction,
            position: progress.position,
            chapter: child,
            completed: child != null && progress.completed,
          ),
          updatedAt: progress.updatedAt,
        ));
      } catch (cause) {
        error ??= cause;
      }
    }
    items.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    return ContinueReadingResult(
      List.unmodifiable(items.map((row) => row.item)),
      error,
    );
  }
}
