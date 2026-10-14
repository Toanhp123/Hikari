import 'package:hikari/application/media/read_remote_chapter_list.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/application/progress/progress_session.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/publication.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

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
    this.chapterLists,
    this.previewMetadata,
    super.lease,
  });

  final MangaSeriesSource seriesSource;
  final ReadRemoteChapterList? chapterLists;
  final MediaMetadata? previewMetadata;

  /// A chapter-list snapshot is navigation metadata. Full source metadata is
  /// intentionally not cached; use the preview (or the persisted media title).
  Future<MangaSeriesDetails?> readCachedDetails() async {
    final snapshots = chapterLists;
    if (snapshots == null) return null;
    final snapshot = await snapshots.readMangaSnapshot(media.source);
    if (snapshot == null) return null;
    return MangaSeriesDetails(
      metadata: previewMetadata ?? MediaMetadata(title: media.title),
      chapters: snapshot.chapterListOrder == ChapterListOrder.readingOrder
          ? snapshot.chapters
          : snapshot.chapters.reversed.toList(growable: false),
      chapterListOrder: snapshot.chapterListOrder,
    );
  }

  Future<MangaSeriesDetails> loadDetails() async {
    final request = chapterLists?.beginRefresh(media.source, MediaType.manga);
    try {
      final details = await seriesSource.loadDetails(media.source);
      if (details.chapters.any(
            (chapter) => chapter.source.sourceId != seriesSource.id,
          ) ||
          (details.metadata.cover != null &&
              details.metadata.cover!.sourceId != seriesSource.id)) {
        throw StateError('Manga source returned foreign references.');
      }
      validateSeriesChapterSequence(
        media.source,
        details.chapters.map((chapter) => chapter.source),
      );
      await chapterLists?.persistManga(
        media.source,
        details.chaptersInReadingOrder,
        request: request,
        chapterListOrder: details.chapterListOrder,
      );
      return details;
    } finally {
      if (request != null) {
        chapterLists!.finishRefresh(media.source, MediaType.manga, request);
      }
    }
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
  const NovelSeriesOpenTarget(
    super.media, {
    required this.source,
    this.chapterLists,
    this.previewMetadata,
    super.lease,
  });

  final NovelSeriesSource source;
  final ReadRemoteChapterList? chapterLists;
  final MediaMetadata? previewMetadata;

  Future<NovelDetails?> readCachedDetails() async {
    final snapshots = chapterLists;
    if (snapshots == null) return null;
    final snapshot = await snapshots.readNovelSnapshot(media.source);
    if (snapshot == null) return null;
    return NovelDetails(
      metadata: previewMetadata ?? MediaMetadata(title: media.title),
      chapters: snapshot.chapterListOrder == ChapterListOrder.readingOrder
          ? snapshot.chapters
          : snapshot.chapters.reversed.toList(growable: false),
      chapterListOrder: snapshot.chapterListOrder,
    );
  }

  Future<NovelDetails> loadDetails() async {
    final request = chapterLists?.beginRefresh(
      media.source,
      MediaType.lightNovel,
    );
    try {
      final details = await source.loadDetails(media.source);
      if (details.chapters.any(
            (chapter) => chapter.source.sourceId != source.id,
          ) ||
          (details.metadata.cover != null &&
              details.metadata.cover!.sourceId != source.id)) {
        throw StateError('Novel source returned foreign references.');
      }
      validateSeriesChapterSequence(
        media.source,
        details.chapters.map((chapter) => chapter.source),
      );
      await chapterLists?.persistNovel(
        media.source,
        details.chaptersInReadingOrder,
        request: request,
        chapterListOrder: details.chapterListOrder,
      );
      return details;
    } finally {
      if (request != null) {
        chapterLists!.finishRefresh(
          media.source,
          MediaType.lightNovel,
          request,
        );
      }
    }
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
  const OpenMedia(this._sources, this._progressRepository, {this.chapterLists});

  final SourceRegistry _sources;
  final ProgressRepository _progressRepository;
  final ReadRemoteChapterList? chapterLists;

  String? sourceNameFor(SourceId id) => _sources.find(id)?.name;

  Future<MediaOpenTarget> execute(
    Media media, {
    MediaMetadata? previewMetadata,
  }) async {
    if (previewMetadata?.cover case final cover?) {
      if (cover.sourceId != media.source.sourceId) {
        throw ArgumentError.value(
          previewMetadata,
          'previewMetadata',
          'Preview cover belongs to another source.',
        );
      }
    }
    final source = _sources.require(media.source.sourceId);
    if (source is MediaSourceAvailability && !source.isAvailable) {
      throw StateError('Source is unavailable on this device.');
    }

    if (media.type == MediaType.manga && source is MangaSeriesSource) {
      return MangaSeriesOpenTarget(
        media,
        seriesSource: source,
        chapterLists: chapterLists,
        previewMetadata: previewMetadata,
        lease: _acquireLease(source, media.source),
      );
    }

    if (media.type == MediaType.lightNovel &&
        source is NovelSeriesSource &&
        source is NovelChapterSource) {
      return NovelSeriesOpenTarget(
        media,
        source: source,
        chapterLists: chapterLists,
        previewMetadata: previewMetadata,
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
