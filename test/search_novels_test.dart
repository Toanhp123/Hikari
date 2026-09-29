import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';

void main() {
  test('trims query and preserves unknown pagination', () async {
    final source = _Source();
    final search = SearchNovels(SourceRegistry([source]));
    final result = await search.execute(
      sourceId: source.id,
      query: ' Title ',
      page: 2,
    );
    expect(source.query, 'Title');
    expect(source.page, 2);
    expect(result.hasNextPage, isNull);
    expect(search.options.single.id, source.id);
    expect(() => result.results.clear(), throwsUnsupportedError);
  });

  test('empty query does not invoke source', () async {
    final source = _Source();
    final result = await SearchNovels(SourceRegistry([source]))
        .execute(sourceId: source.id, query: '  ');
    expect(result.results, isEmpty);
    expect(result.hasNextPage, false);
    expect(source.query, isNull);
  });

  test('rejects invalid page before calling source', () async {
    final source = _Source();
    await expectLater(
      SearchNovels(SourceRegistry([source]))
          .execute(sourceId: source.id, query: 'Title', page: 0),
      throwsArgumentError,
    );
    expect(source.query, isNull);
  });

  test('rejects foreign result and mismatched response page', () async {
    final source = _Source()..foreign = true;
    final search = SearchNovels(SourceRegistry([source]));
    await expectLater(
      search.execute(sourceId: source.id, query: 'Title'),
      throwsStateError,
    );
    source.foreign = false;
    source.wrongPage = true;
    await expectLater(
      search.execute(sourceId: source.id, query: 'Title'),
      throwsStateError,
    );
  });

  test('unavailable sources are hidden and rejected', () async {
    final source = _Source()..isAvailable = false;
    final search = SearchNovels(SourceRegistry([source]));
    expect(search.options, isEmpty);
    await expectLater(
      search.execute(sourceId: source.id, query: 'Title'),
      throwsStateError,
    );
  });
}

class _Source
    implements
        NovelSearchSource,
        NovelSeriesSource,
        NovelChapterSource,
        MediaSourceAvailability {
  @override
  final id = const SourceId('novel:test');
  @override
  final name = 'Test novels';
  @override
  bool isAvailable = true;
  String? query;
  int? page;
  bool foreign = false;
  bool wrongPage = false;

  @override
  Future<NovelSearchPage> search(String query, {int page = 1}) async {
    this.query = query;
    this.page = page;
    return NovelSearchPage(
      results: [
        NovelPreview(
          media: Media(
            title: 'Title',
            type: MediaType.lightNovel,
            source: SourceMediaRef(
              sourceId: foreign ? const SourceId('other') : id,
              itemId: 'book',
            ),
          ),
          metadata: MediaMetadata(title: 'Title'),
        ),
      ],
      page: wrongPage ? page + 1 : page,
      hasNextPage: null,
    );
  }

  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) =>
      throw UnimplementedError();
  @override
  Future<NovelChapterContent> chapterContent(SourceMediaRef chapter) =>
      throw UnimplementedError();
  @override
  Future<Uint8List> readResource(SourceMediaRef resource) =>
      throw UnimplementedError();
}
