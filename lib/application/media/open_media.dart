import 'package:hikari/application/progress/progress_session.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/publication.dart';
import 'package:hikari/domain/media/novel.dart';
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

  final MangaSeriesSource chapterSource;

  Future<MangaSeriesDetails> loadDetails() async {
    final details = await chapterSource.loadSeries(media.source);
    if (details.chapters.any(
          (chapter) => chapter.source.sourceId != chapterSource.id,
        ) ||
        (details.metadata.cover != null &&
            details.metadata.cover!.sourceId != chapterSource.id)) {
      throw StateError('Manga source returned foreign references.');
    }
    return details;
  }
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

final class NovelSeriesOpenTarget extends MediaOpenTarget {
  const NovelSeriesOpenTarget(super.media, {required this.source});

  final NovelSeriesSource source;

  Future<NovelDetails> loadDetails() async {
    final details = await source.novelDetails(media.source);
    if (details.chapters.any(
          (chapter) => chapter.source.sourceId != source.id,
        ) ||
        (details.metadata.cover != null &&
            details.metadata.cover!.sourceId != source.id)) {
      throw StateError('Novel source returned foreign references.');
    }
    return details;
  }
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

final class PublicationReaderOpenTarget extends MediaOpenTarget {
  const PublicationReaderOpenTarget(
    super.media, {
    required this.publicationSource,
    required this.progress,
  });

  final PublicationSource publicationSource;
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

    if (media.type == MediaType.manga && source is MangaSeriesSource) {
      return MangaSeriesOpenTarget(media, chapterSource: source);
    }

    if (media.type == MediaType.lightNovel &&
        source is NovelSeriesSource &&
        source is NovelChapterSource) {
      return NovelSeriesOpenTarget(media, source: source);
    }

    final progress = await ProgressSession.load(
      repository: _progressRepository,
      media: media.source,
    );

    if (media.type == MediaType.lightNovel &&
        source is PublicationSource &&
        source.canOpenPublication(media.source)) {
      return PublicationReaderOpenTarget(
        media,
        publicationSource: source,
        progress: progress,
      );
    }

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
