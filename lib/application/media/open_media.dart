import 'package:hikari/application/progress/progress_session.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';

sealed class MediaOpenTarget {
  const MediaOpenTarget(this.media);

  final Media media;
}

final class VideoOpenTarget extends MediaOpenTarget {
  const VideoOpenTarget(
    super.media, {
    required this.locator,
    required this.progress,
  });

  final String locator;
  final ProgressSession progress;
}

final class MangaSeriesOpenTarget extends MediaOpenTarget {
  const MangaSeriesOpenTarget(super.media, {required this.chapterSource});

  final MangaChapterSource chapterSource;
}

final class MangaReaderOpenTarget extends MediaOpenTarget {
  const MangaReaderOpenTarget(
    super.media, {
    required this.pageSource,
    required this.progress,
  });

  final MangaPageSource pageSource;
  final ProgressSession progress;
}

final class NovelReaderOpenTarget extends MediaOpenTarget {
  const NovelReaderOpenTarget(
    super.media, {
    required this.textSource,
    required this.progress,
  });

  final NovelTextSource textSource;
  final ProgressSession progress;
}

/// Resolves a persisted/scanned [Media] into the capability needed to open it.
///
/// Navigation and widgets stay in presentation. Source-specific details stay
/// behind the domain source capabilities registered in [SourceRegistry].
final class OpenMedia {
  const OpenMedia(this._sources, this._progressRepository);

  final SourceRegistry _sources;
  final ProgressRepository _progressRepository;

  Future<MediaOpenTarget> execute(Media media) async {
    final source = _sources.require(media.source.sourceId);
    if (source is MediaSourceAvailability && !source.isAvailable) {
      throw StateError('Source is unavailable on this device.');
    }

    if (media.type == MediaType.manga && source is MangaChapterSource) {
      return MangaSeriesOpenTarget(media, chapterSource: source);
    }

    final progress = await ProgressSession.load(
      repository: _progressRepository,
      media: media.source,
    );

    return switch (media.type) {
      MediaType.anime => VideoOpenTarget(
        media,
        locator: _requireCapability<DirectVideoSource>(source)
            .playbackLocator(media.source),
        progress: progress,
      ),
      MediaType.manga => MangaReaderOpenTarget(
        media,
        pageSource: _requireCapability<MangaPageSource>(source),
        progress: progress,
      ),
      MediaType.lightNovel => NovelReaderOpenTarget(
        media,
        textSource: _requireCapability<NovelTextSource>(source),
        progress: progress,
      ),
    };
  }

  T _requireCapability<T extends MediaSource>(MediaSource source) {
    if (source is! T) {
      throw StateError('Source ${source.id} does not support $T.');
    }
    return source;
  }
}
