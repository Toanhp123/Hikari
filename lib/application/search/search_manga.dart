import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';

final class MangaSearchOption {
  const MangaSearchOption({required this.id, required this.name});

  final SourceId id;
  final String name;
}

/// Searches registered manga sources that can also open their search results.
///
/// Search-only sources remain valid domain capabilities, but they are not exposed
/// through this user workflow until Hikari has a separate use case for results
/// that cannot be opened by the current readers.
final class SearchManga {
  SearchManga(this._sources);

  final SourceRegistry _sources;

  List<MangaSearchOption> get options => List.unmodifiable(
    _sources
        .withCapability<MangaSearchSource>()
        .where(_canOpenSearchResults)
        .where(_isAvailable)
        .map((source) => MangaSearchOption(id: source.id, name: source.name)),
  );

  Future<List<Media>> execute({
    required SourceId sourceId,
    required String query,
  }) async {
    final normalizedQuery = query.trim();
    if (normalizedQuery.isEmpty) return const [];

    final source = _sources.requireCapability<MangaSearchSource>(sourceId);
    if (!_canOpenSearchResults(source)) {
      throw StateError(
        'Manga search source $sourceId cannot open its results.',
      );
    }
    if (!_isAvailable(source)) {
      throw StateError('Manga search source $sourceId is unavailable.');
    }

    final results = await source.search(normalizedQuery);
    for (final media in results) {
      if (media.type != MediaType.manga || media.source.sourceId != source.id) {
        throw StateError(
          'Manga search source $sourceId returned an invalid item.',
        );
      }
    }
    return List.unmodifiable(results);
  }

  bool _canOpenSearchResults(MangaSearchSource source) =>
      source is MangaPageSource;

  bool _isAvailable(MediaSource source) =>
      source is! MediaSourceAvailability || source.isAvailable;
}
