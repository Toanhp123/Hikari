import 'package:hikari/application/progress/progress_session.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';

final class MangaChapterOpenTarget {
  const MangaChapterOpenTarget({
    required this.chapter,
    required this.source,
    required this.pages,
    required this.progress,
  });

  final MangaChapter chapter;
  final MangaPageSource source;
  final List<SourceMediaRef> pages;
  final ProgressSession progress;
}

/// Prepares a chapter before navigation so stale remote references fail before
/// the reader route is pushed.
final class OpenMangaChapter {
  const OpenMangaChapter(this._sources, this._progressRepository);

  final SourceRegistry _sources;
  final ProgressRepository _progressRepository;

  Future<MangaChapterOpenTarget> execute(
    MangaChapter chapter, {
    Future<List<SourceMediaRef>> Function(
      MangaPageSource source,
      SourceMediaRef chapter,
    )?
    pageListLoader,
  }) async {
    final source = _sources.requireCapability<MangaPageSource>(
      chapter.source.sourceId,
    );
    if (!_isAvailable(source)) {
      throw StateError('Source is unavailable on this device.');
    }

    final progress = await ProgressSession.load(
      repository: _progressRepository,
      media: chapter.source,
    );
    final pages =
        await (pageListLoader?.call(source, chapter.source) ??
            source.pages(chapter.source));
    if (pages.any((page) => page.sourceId != source.id)) {
      throw StateError('Manga source returned foreign page references.');
    }

    return MangaChapterOpenTarget(
      chapter: chapter,
      source: source,
      pages: pages,
      progress: progress,
    );
  }

  bool _isAvailable(MediaSource source) =>
      source is! MediaSourceAvailability || source.isAvailable;
}
