import 'package:hikari/application/progress/progress_session.dart';
import 'package:hikari/application/sources/source_registry.dart';
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
  const OpenMangaChapter({
    required SourceRegistry sources,
    required ProgressRepository progressRepository,
  }) : _sources = sources,
       _progressRepository = progressRepository;

  final SourceRegistry _sources;
  final ProgressRepository _progressRepository;

  Future<MangaChapterOpenTarget> execute(MangaChapter chapter) async {
    final source = _sources.requireCapability<MangaPageSource>(
      chapter.source.sourceId,
    );
    if (source is MediaSourceAvailability && !source.isAvailable) {
      throw StateError('Source is unavailable on this device.');
    }

    final progress = await ProgressSession.load(
      repository: _progressRepository,
      media: chapter.source,
    );
    final pages = await source.pages(chapter.source);

    return MangaChapterOpenTarget(
      chapter: chapter,
      source: source,
      pages: pages,
      progress: progress,
    );
  }
}
