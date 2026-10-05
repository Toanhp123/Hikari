import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/media/read_novel_resource.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';

/// Speculatively warms the next remote novel chapter through the normal cache
/// workflows. Work is intentionally serial and best-effort so foreground
/// reading remains authoritative.
final class PrefetchNovelChapter {
  PrefetchNovelChapter(this._sources, this._readContent, this._readResource);

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

      final content = await _readContent.execute(source, chapter.source);
      if (generation != _generation) return;

      final seen = <SourceMediaRef>{};
      for (final resource in content.resources.values) {
        if (generation != _generation) return;
        if (!seen.add(resource)) continue;
        try {
          await _readResource.execute(source, resource);
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
