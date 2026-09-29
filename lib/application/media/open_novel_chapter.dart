import 'package:hikari/application/progress/progress_session.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';

final class NovelChapterOpenTarget {
  const NovelChapterOpenTarget({
    required this.chapter,
    required this.source,
    required this.content,
    required this.progress,
  });
  final NovelChapter chapter;
  final NovelChapterSource source;
  final NovelChapterContent content;
  final ProgressSession progress;
}

final class OpenNovelChapter {
  const OpenNovelChapter(this._sources, this._progressRepository);
  final SourceRegistry _sources;
  final ProgressRepository _progressRepository;

  Future<NovelChapterOpenTarget> execute(NovelChapter chapter) async {
    final source = _sources.requireCapability<NovelChapterSource>(
      chapter.source.sourceId,
    );
    if (source is MediaSourceAvailability &&
        !(source as MediaSourceAvailability).isAvailable) {
      throw StateError('Source is unavailable on this device.');
    }
    final content = await source.chapterContent(chapter.source);
    if (content.resources.values.any((ref) => ref.sourceId != source.id)) {
      throw StateError('Novel source returned foreign resources.');
    }
    return NovelChapterOpenTarget(
      chapter: chapter,
      source: source,
      content: content,
      progress: await ProgressSession.load(
        repository: _progressRepository,
        media: chapter.source,
      ),
    );
  }
}
