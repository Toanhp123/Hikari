import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/infrastructure/catalog/anilist/anilist_catalog_provider.dart';

void main() {
  test(
    'discovery batches sections and builds balanced cross-media Featured',
    () async {
      late String body;
      var requests = 0;
      final provider = AniListCatalogProvider(
        clock: () => DateTime.utc(2026, 3, 31),
        post: (_, value) async {
          requests++;
          body = value;
          return AniListHttpResponse(
            200,
            jsonEncode({
              'errors': [
                {'message': 'section failed'},
              ],
              'data': {
                'featuredAnime': {
                  'media': [
                    _row(101, 'ANIME', 'Anime 1', banner: true),
                    _row(102, 'ANIME', 'Anime 2'),
                    _row(103, 'ANIME', 'Anime 3'),
                  ],
                },
                'featuredManga': {
                  'media': [_row(201, 'MANGA', 'Manga 1')],
                },
                'featuredNovels': {
                  'media': [
                    _row(301, 'MANGA', 'Novel 1', format: 'NOVEL'),
                    _row(302, 'MANGA', 'Novel 2', format: 'NOVEL'),
                  ],
                },
                'trending': {'media': <Object?>[]},
                'anime': {'media': <Object?>[]},
                'manga': {'media': <Object?>[]},
                'novels': {'media': <Object?>[]},
                'seasonal': {'media': <Object?>[]},
              },
            }),
          );
        },
      );
      final result = await provider.discover();
      expect(requests, 1);
      final featured = result.sections[CatalogSection.featured]!;
      expect(featured.map((entry) => entry.title), [
        'Anime 1',
        'Manga 1',
        'Novel 1',
        'Anime 2',
        'Novel 2',
      ]);
      expect(featured.map((entry) => entry.type), [
        MediaType.anime,
        MediaType.manga,
        MediaType.lightNovel,
        MediaType.anime,
        MediaType.lightNovel,
      ]);
      expect(featured.first.bannerUrl, 'https://example/banner/101');
      expect(featured.first.genres, ['Action']);
      expect(featured.map((entry) => entry.title), isNot(contains('Anime 3')));
      expect(result.sections[CatalogSection.popularLightNovels], isEmpty);
      expect(result.warnings, contains(contains('section failed')));

      final query = jsonDecode(body)['query'] as String;
      expect(query, contains('featuredAnime: Page(perPage: 4)'));
      expect(query, contains('featuredManga: Page(perPage: 4)'));
      expect(query, contains('featuredNovels: Page(perPage: 4)'));
      expect(query, contains('type: ANIME sort: [TRENDING_DESC, SCORE_DESC]'));
      expect(
        query,
        contains(
          'type: MANGA format_not: NOVEL sort: [TRENDING_DESC, SCORE_DESC]',
        ),
      );
      expect(
        query,
        contains('type: MANGA format: NOVEL sort: [TRENDING_DESC, SCORE_DESC]'),
      );
      expect(query, contains('season: WINTER seasonYear: 2026'));
      expect(query, isNot(contains('type_in')));
      expect(query, isNot(contains('isAdult: true')));
      await provider.close();
    },
  );

  test('season calendar boundaries use AniList quarters', () async {
    for (final entry in {
      1: 'WINTER',
      3: 'WINTER',
      4: 'SPRING',
      6: 'SPRING',
      7: 'SUMMER',
      9: 'SUMMER',
      10: 'FALL',
      12: 'FALL',
    }.entries) {
      late String query;
      final provider = AniListCatalogProvider(
        clock: () => DateTime.utc(2026, entry.key, 1),
        post: (_, body) async {
          query = (jsonDecode(body)['query'] as String);
          return AniListHttpResponse(
            200,
            jsonEncode({
              'data': {
                'featuredAnime': {'media': <Object?>[]},
                'featuredManga': {'media': <Object?>[]},
                'featuredNovels': {'media': <Object?>[]},
                'seasonal': {'media': <Object?>[]},
                'trending': {'media': <Object?>[]},
                'anime': {'media': <Object?>[]},
                'manga': {'media': <Object?>[]},
                'novels': {'media': <Object?>[]},
              },
            }),
          );
        },
      );
      await provider.discover();
      expect(query, contains('season: ${entry.value} seasonYear: 2026'));
      await provider.close();
    }
  });

  test('search uses search match and media-type filters', () async {
    for (final testCase in <(MediaType?, Map<String, Object?>)>[
      (null, const {}),
      (MediaType.anime, const {'type': 'ANIME'}),
      (MediaType.manga, const {'type': 'MANGA', 'formatNot': 'NOVEL'}),
      (MediaType.lightNovel, const {'type': 'MANGA', 'format': 'NOVEL'}),
    ]) {
      late Map<String, dynamic> request;
      final provider = AniListCatalogProvider(
        post: (_, body) async {
          request = jsonDecode(body) as Map<String, dynamic>;
          return AniListHttpResponse(
            200,
            jsonEncode({
              'data': {
                'Page': {
                  'media': [_row(1, 'ANIME', 'Match')],
                },
              },
            }),
          );
        },
      );
      addTearDown(provider.close);

      final result = await provider.search('  Match  ', type: testCase.$1);

      expect(result.single.title, 'Match');
      final query = request['query'] as String;
      expect(query, contains('search: \$search'));
      expect(query, contains('sort: SEARCH_MATCH'));
      expect(query, contains('isAdult: false'));
      expect(request['variables'], {'search': 'Match', ...testCase.$2});
    }
  });

  test('catalog search reports GraphQL failure instead of empty results', () async {
    final provider = AniListCatalogProvider(
      post: (_, _) async => const AniListHttpResponse(
        200,
        '{"data":{"Page":{"media":[]}},"errors":[{"message":"search failed"}]}',
      ),
    );
    addTearDown(provider.close);

    await expectLater(
      provider.search('Frieren'),
      throwsA(isA<FormatException>()),
    );
  });

  test('empty catalog search does not call AniList', () async {
    var requests = 0;
    final provider = AniListCatalogProvider(
      post: (_, _) async {
        requests++;
        return const AniListHttpResponse(200, '{}');
      },
    );
    addTearDown(provider.close);

    expect(await provider.search('   '), isEmpty);
    expect(requests, 0);
  });

  test(
    'details normalize enum values, titles, year and authors safely',
    () async {
      final provider = AniListCatalogProvider(
        post: (_, body) async {
          expect(body, contains('description(asHtml: true)'));
          expect(body, contains('staff(perPage: 12'));
          expect(body, contains('{ role node {'));
          return AniListHttpResponse(
            200,
            jsonEncode({
              'errors': [
                {'message': 'one field failed'},
              ],
              'data': {
                'Media': {
                  'id': 9,
                  'type': 'MANGA',
                  'format': 'ONE_SHOT',
                  'status': 'NOT_YET_RELEASED',
                  'title': {
                    'english': 'Novel',
                    'romaji': 'Romaji',
                    'native': 'Native',
                  },
                  'description': '<b>Plain</b> &amp; text',
                  'genres': ['Fantasy'],
                  'synonyms': ['Alt'],
                  'startDate': {'year': 2025},
                  'studios': {
                    'nodes': [
                      null,
                      {'name': 'Studio'},
                    ],
                  },
                  'staff': {
                    'edges': [
                      null,
                      {
                        'role': 'Character Design',
                        'node': {
                          'name': {'full': 'Artist'},
                        },
                      },
                      {
                        'role': 'Story & Art',
                        'node': {
                          'name': {'full': 'Author'},
                        },
                      },
                    ],
                  },
                  'relations': {
                    'edges': [
                      null,
                      {
                        'relationType': 'SEQUEL',
                        'node': {
                          'id': 10,
                          'type': 'MANGA',
                          'title': {'romaji': 'Next'},
                        },
                      },
                    ],
                  },
                },
              },
            }),
          );
        },
      );
      final details = await provider.loadDetails(
        const CatalogEntryId(provider: 'anilist', value: '9'),
      );
      expect(details!.format, CatalogFormat.oneShot);
      expect(details.status, CatalogStatus.notYetReleased);
      expect(details.year, 2025);
      expect(details.alternateTitles, ['Romaji', 'Native']);
      expect(details.studios, ['Studio']);
      expect(details.staff, ['Author']);
      expect(details.description, 'Plain & text');
      expect(details.relations.map((relation) => relation.entry.id.value), [
        '10',
      ]);
      expect(details.warnings, contains(contains('one field failed')));
      expect(
        details.warnings,
        contains(contains('related entries were omitted')),
      );
      await provider.close();
    },
  );

  test('detail GraphQL failure is not reported as a missing entry', () async {
    final provider = AniListCatalogProvider(
      post: (_, _) async => const AniListHttpResponse(
        200,
        '{"data":{"Media":null},"errors":[{"message":"upstream failed"}]}',
      ),
    );
    addTearDown(provider.close);
    await expectLater(
      provider.loadDetails(
        const CatalogEntryId(provider: 'anilist', value: '9'),
      ),
      throwsA(isA<FormatException>()),
    );
  });

  test('detail response must match requested id; rate limit throws', () async {
    final provider = AniListCatalogProvider(
      post: (_, _) async => AniListHttpResponse(
        200,
        jsonEncode({
          'data': {
            'Media': {'id': 7},
          },
        }),
      ),
    );
    expect(
      await provider.loadDetails(
        const CatalogEntryId(provider: 'anilist', value: '9'),
      ),
      isNull,
    );
    await provider.close();
    final limited = AniListCatalogProvider(
      post: (_, _) async => const AniListHttpResponse(429, '{}'),
    );
    await expectLater(limited.discover(), throwsA(isA<Exception>()));
  });
}

Map<String, Object?> _row(
  int id,
  String type,
  String title, {
  String? format,
  bool banner = false,
}) => {
  'id': id,
  'type': type,
  'format': ?format,
  'title': {'romaji': title},
  if (banner) 'bannerImage': 'https://example/banner/$id',
  if (banner) 'genres': ['Action'],
};
