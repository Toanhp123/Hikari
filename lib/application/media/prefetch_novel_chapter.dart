import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/media/read_novel_resource.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';

/// Speculatively warms one remote novel chapter selected by the reader's
/// lookahead policy through the normal cache workflows. Work is intentionally
/// serial and best-effort so foreground reading remains authoritative.
final class PrefetchNovelChapter {
  PrefetchNovelChapter(
    this._sources,
    this._readContent,
    this._readResource, {
    this.maxResourceRequests = 4,
    this.maxResourceBytes = 8 * 1024 * 1024,
  }) : assert(maxResourceRequests >= 0),
       assert(maxResourceBytes >= 0);

  /// Maximum number of unique resource fetch attempts in one chapter lookahead.
  /// Failed attempts count too, avoiding unbounded speculative retries.
  final int maxResourceRequests;

  /// Soft per-chapter resource-byte budget. Resource APIs return whole byte
  /// buffers, so the last request can exceed this amount. Cached hits count
  /// toward the budget as well; no extra reads are made to classify hits.
  final int maxResourceBytes;

  final SourceRegistry _sources;
  final ReadNovelChapterContent _readContent;
  final ReadNovelResource _readResource;
  Future<void> _tail = Future<void>.value();
  int _generation = 0;

  Future<void> execute(NovelChapter chapter) {
    if (chapter.source.itemId.isEmpty) return Future<void>.value();
    final generation = ++_generation;
    return _tail = _tail.then((_) => _prefetch(chapter, generation));
  }

  Future<void> _prefetch(NovelChapter chapter, int generation) async {
    if (generation != _generation) return;
    try {
      final source = _sources.requireCapability<NovelChapterSource>(
        chapter.source.sourceId,
      );
      if (source is MediaSourceAvailability &&
          !(source as MediaSourceAvailability).isAvailable) {
        return;
      }

      await _readContent.waitForForeground();
      if (generation != _generation) return;
      final content = await _readContent.prefetch(source, chapter.source);
      if (generation != _generation) return;

      final seen = <SourceMediaRef>{};
      var attempted = 0;
      var warmedBytes = 0;
      for (final resource in content.resources.values) {
        if (generation != _generation) return;
        if (attempted >= maxResourceRequests ||
            warmedBytes >= maxResourceBytes) {
          break;
        }
        if (!seen.add(resource)) continue;
        await _readResource.waitForForeground();
        if (generation != _generation) return;
        attempted++;
        try {
          final bytes = await _readResource.prefetch(source, resource);
          warmedBytes += bytes.length;
        } catch (_) {
          // A failed speculative resource must not affect foreground reading.
        }
      }
    } catch (_) {
      // Chapter prefetch is best-effort and never surfaces reader errors.
    }
  }

  void cancelPending() {
    _generation++;
  }
}
