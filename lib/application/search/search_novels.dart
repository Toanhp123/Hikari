import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';

final class NovelSearchOption {
  const NovelSearchOption({
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

final class SearchNovels {
  const SearchNovels(this._sources);

  final SourceRegistry _sources;

  List<NovelSearchOption> get options => List.unmodifiable(
    _sources
        .withCapability<NovelSearchSource>()
        .where(_canOpen)
        .where(_isAvailable)
        .map((source) {
          final presentation = source is MediaSourcePresentation
              ? source as MediaSourcePresentation
              : null;
          return NovelSearchOption(
            id: source.id,
            displayName: presentation?.displayName ?? source.name,
            languageCode: normalizeSourceLanguageCode(
              presentation?.languageCode,
            ),
            presentationGroupId: presentation?.presentationGroupId,
          );
        }),
  );

  Future<NovelSearchPage> execute({
    required SourceId sourceId,
    required String query,
    int page = 1,
  }) async {
    if (page < 1) throw ArgumentError.value(page, 'page');
    final normalizedQuery = query.trim();
    if (normalizedQuery.isEmpty) {
      return NovelSearchPage(results: const [], page: page, hasNextPage: false);
    }
    final source = _sources.requireCapability<NovelSearchSource>(sourceId);
    if (!_canOpen(source) || !_isAvailable(source)) {
      throw StateError('Novel source $sourceId cannot open search results.');
    }
    final result = await source.search(normalizedQuery, page: page);
    if (result.page != page ||
        result.results.any(
          (preview) =>
              preview.media.type != MediaType.lightNovel ||
              preview.media.source.sourceId != sourceId ||
              (preview.metadata.cover != null &&
                  preview.metadata.cover!.sourceId != sourceId),
        )) {
      throw StateError(
        'Novel source $sourceId returned an invalid search page.',
      );
    }
    return result;
  }

  bool _canOpen(NovelSearchSource source) =>
      source is NovelSeriesSource && source is NovelChapterSource;

  bool _isAvailable(MediaSource source) =>
      source is! MediaSourceAvailability || source.isAvailable;
}
