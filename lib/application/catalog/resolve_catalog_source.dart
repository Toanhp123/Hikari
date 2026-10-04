import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';

final class CatalogSourceOption {
  const CatalogSourceOption({
    required this.id,
    required this.displayName,
    this.languageCode,
    this.presentationGroupId,
  });

  final SourceId id;
  final String displayName;
  final String? languageCode;
  final String? presentationGroupId;
}

final class CatalogSourceCandidate {
  const CatalogSourceCandidate({required this.media, this.metadata});

  final Media media;
  final MediaMetadata? metadata;
}

final class CatalogSourceResolution {
  CatalogSourceResolution({
    this.match,
    Iterable<CatalogSourceCandidate> candidates = const [],
  }) : candidates = List.unmodifiable(candidates);

  final CatalogSourceCandidate? match;
  final List<CatalogSourceCandidate> candidates;
}

/// Resolves catalog metadata against one explicit, compatible content source.
///
/// Only one unambiguous normalized title/alias match is opened automatically.
/// Near or duplicate matches remain candidates for explicit user confirmation.
final class ResolveCatalogSource {
  const ResolveCatalogSource({
    required this._searchManga,
    required this._searchNovels,
  });

  static const _maxQueries = 4;
  static const _maxCandidates = 8;

  final SearchManga _searchManga;
  final SearchNovels _searchNovels;

  List<CatalogSourceOption> optionsFor(MediaType type) => switch (type) {
    MediaType.manga =>
      _searchManga.options
          .map(
            (option) => CatalogSourceOption(
              id: option.id,
              displayName: option.displayName,
              languageCode: option.languageCode,
              presentationGroupId: option.presentationGroupId,
            ),
          )
          .toList(growable: false),
    MediaType.lightNovel =>
      _searchNovels.options
          .map(
            (option) => CatalogSourceOption(
              id: option.id,
              displayName: option.displayName,
              languageCode: option.languageCode,
              presentationGroupId: option.presentationGroupId,
            ),
          )
          .toList(growable: false),
    MediaType.anime => const [],
  };

  Future<CatalogSourceResolution> execute({
    required CatalogEntry entry,
    required SourceId sourceId,
    CatalogEntryDetails? details,
  }) async {
    if (entry.type == MediaType.anime) {
      throw StateError('Catalog source resolution is not available for anime.');
    }

    final titles = _catalogTitles(entry, details).take(_maxQueries).toList();
    final normalizedTitles = titles.map(_normalizeTitle).toSet();
    final candidates = <CatalogSourceCandidate>[];
    final seen = <SourceMediaRef>{};

    for (final query in titles) {
      final batch = await _search(entry.type, sourceId, query);
      final exactMatches = <CatalogSourceCandidate>[];

      for (final candidate in batch) {
        if (!seen.add(candidate.media.source)) continue;
        candidates.add(candidate);
        if (normalizedTitles.contains(_normalizeTitle(candidate.media.title))) {
          exactMatches.add(candidate);
        }
      }

      if (exactMatches.length == 1) {
        return CatalogSourceResolution(
          match: exactMatches.single,
          candidates: candidates,
        );
      }
      if (exactMatches.length > 1) {
        return CatalogSourceResolution(
          candidates: _prioritizeExact(candidates, normalizedTitles),
        );
      }
    }

    return CatalogSourceResolution(candidates: candidates.take(_maxCandidates));
  }

  Future<List<CatalogSourceCandidate>> _search(
    MediaType type,
    SourceId sourceId,
    String query,
  ) async {
    return switch (type) {
      MediaType.manga =>
        (await _searchManga.execute(sourceId: sourceId, query: query)).results
            .map(
              (preview) => CatalogSourceCandidate(
                media: preview.media,
                metadata: preview.metadata,
              ),
            )
            .toList(growable: false),
      MediaType.lightNovel =>
        (await _searchNovels.execute(sourceId: sourceId, query: query)).results
            .map(
              (preview) => CatalogSourceCandidate(
                media: preview.media,
                metadata: preview.metadata,
              ),
            )
            .toList(growable: false),
      MediaType.anime => throw StateError(
        'Catalog source resolution is not available for anime.',
      ),
    };
  }

  Iterable<String> _catalogTitles(
    CatalogEntry entry,
    CatalogEntryDetails? details,
  ) sync* {
    final seen = <String>{};
    for (final title in [
      entry.title,
      ...?details?.alternateTitles,
      ...?details?.synonyms,
    ]) {
      final trimmed = title.trim();
      if (trimmed.isEmpty) continue;
      final normalized = _normalizeTitle(trimmed);
      if (normalized.isEmpty || !seen.add(normalized)) continue;
      yield trimmed;
    }
  }

  List<CatalogSourceCandidate> _prioritizeExact(
    List<CatalogSourceCandidate> candidates,
    Set<String> normalizedTitles,
  ) {
    final exact = candidates.where(
      (candidate) =>
          normalizedTitles.contains(_normalizeTitle(candidate.media.title)),
    );
    final remaining = candidates.where(
      (candidate) =>
          !normalizedTitles.contains(_normalizeTitle(candidate.media.title)),
    );
    return [
      ...exact,
      ...remaining,
    ].take(_maxCandidates).toList(growable: false);
  }
}

String _normalizeTitle(String value) => value
    .trim()
    .toLowerCase()
    .replaceAll(RegExp(r'[\s\-_–—:;,.!?/\\()\[\]{}·・]+'), '')
    .replaceAll('"', '')
    .replaceAll("'", '')
    .replaceAll('’', '')
    .replaceAll('‘', '')
    .replaceAll('“', '')
    .replaceAll('”', '');
