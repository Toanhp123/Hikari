import 'package:hikari/application/progress/progress_session.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/publication.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';

sealed class MediaOpenTarget {
  const MediaOpenTarget(this.media, {this.lease});

  final Media media;
  final MediaOpenLease? lease;

  Future<void> release() async {
    if (lease != null) await lease!.release();
  }
}

final class VideoOpenTarget extends MediaOpenTarget {
  const VideoOpenTarget(
    super.media, {
    required this.locator,
    required this.progress,
    super.lease,
  });

  final String locator;
  final ProgressSession progress;
}

final class MangaSeriesOpenTarget extends MediaOpenTarget {
  const MangaSeriesOpenTarget(
    super.media, {
    required this.seriesSource,
    super.lease,
  });

  final MangaSeriesSource seriesSource;

  Future<MangaSeriesDetails> loadDetails() async {
    final details = await seriesSource.loadDetails(media.source);
    if (details.chapters.any(
          (chapter) => chapter.source.sourceId != seriesSource.id,
        ) ||
        (details.metadata.cover != null &&
            details.metadata.cover!.sourceId != seriesSource.id)) {
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
    super.lease,
  });

  final MangaPageSource pageSource;
  final ProgressSession progress;
}

final class NovelSeriesOpenTarget extends MediaOpenTarget {
  const NovelSeriesOpenTarget(super.media, {required this.source, super.lease});

  final NovelSeriesSource source;

  Future<NovelDetails> loadDetails() async {
    final details = await source.loadDetails(media.source);
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
    super.lease,
  });

  final NovelTextSource textSource;
  final ProgressSession progress;
}

final class PublicationReaderOpenTarget extends MediaOpenTarget {
  const PublicationReaderOpenTarget(
    super.media, {
    required this.publicationSource,
    required this.progress,
    super.lease,
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
      return MangaSeriesOpenTarget(
        media,
        seriesSource: source,
        lease: _acquireLease(source, media.source),
      );
    }

    if (media.type == MediaType.lightNovel &&
        source is NovelSeriesSource &&
        source is NovelChapterSource) {
      return NovelSeriesOpenTarget(
        media,
        source: source,
        lease: _acquireLease(source, media.source),
      );
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
        lease: _acquireLease(source, media.source),
      );
    }

    switch (media.type) {
      case MediaType.anime:
        final videoSource = _requireCapability<DirectVideoSource>(source);
        final locator = videoSource.playbackLocator(media.source);
        return VideoOpenTarget(
          media,
          locator: locator,
          progress: progress,
          lease: _acquireLease(source, media.source),
        );
      case MediaType.manga:
        final pageSource = _requireCapability<MangaPageSource>(source);
        return MangaReaderOpenTarget(
          media,
          pageSource: pageSource,
          progress: progress,
          lease: _acquireLease(source, media.source),
        );
      case MediaType.lightNovel:
        final textSource = _requireCapability<NovelTextSource>(source);
        return NovelReaderOpenTarget(
          media,
          textSource: textSource,
          progress: progress,
          lease: _acquireLease(source, media.source),
        );
    }
  }

  MediaOpenLease? _acquireLease(MediaSource source, SourceMediaRef media) {
    if (source is! MediaOpenLeaseSource) return null;
    return source.acquireOpenLease(media);
  }

  T _requireCapability<T extends MediaSource>(MediaSource source) {
    if (source is! T) {
      throw StateError('Source ${source.id} does not support $T.');
    }
    return source;
  }
}
