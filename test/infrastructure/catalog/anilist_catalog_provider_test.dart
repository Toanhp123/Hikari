import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/infrastructure/catalog/anilist/anilist_catalog_provider.dart';

void main() {
  test(
    'discovery batches valid query sections and surfaces partial warnings',
    () async {
      late String body;
      final provider = AnilistCatalogProvider(
        clock: () => DateTime.utc(2026, 3, 31),
        post: (_, value) async {
          body = value;
          return AniListHttpResponse(
            200,
            jsonEncode({
              'errors': [
                {'message': 'section failed'},
              ],
              'data': {
                'trending': {
                  'media': [
                    {
                      'id': 8,
                      'type': 'ANIME',
                      'title': {'romaji': 'Show'},
                    },
                  ],
                },
                'anime': {'media': <Object?>[]},
                'manga': {'media': <Object?>[]},
                'novels': {'media': <Object?>[]},
                'seasonal': {
                  'media': [
                    {
                      'id': 8,
                      'type': 'ANIME',
                      'title': {'romaji': 'Show'},
                    },
                  ],
                },
              },
            }),
          );
        },
      );
      final result = await provider.discover();
      expect(result.sections[CatalogSection.featured]!.single.title, 'Show');
      expect(result.sections[CatalogSection.popularLightNovels], isEmpty);
      expect(result.warnings, contains(contains('section failed')));
      final query = jsonDecode(body)['query'] as String;
      expect(query, isNot(contains('type_in')));
      expect(query, contains('season: WINTER seasonYear: 2026'));
      expect(query, contains('format: NOVEL'));
      expect(query, contains('format_not: NOVEL'));
      expect(query, contains('TRENDING_DESC'));
      expect(query, isNot(contains('featured:')));
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
      final provider = AnilistCatalogProvider(
        clock: () => DateTime.utc(2026, entry.key, 1),
        post: (_, body) async {
          query = (jsonDecode(body)['query'] as String);
          return AniListHttpResponse(
            200,
            jsonEncode({
              'data': {
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

  test(
    'details normalize enum values, titles, year and authors safely',
    () async {
      final provider = AnilistCatalogProvider(
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
      final details = await provider.details(
        const CatalogMediaId(provider: 'anilist', value: '9'),
      );
      expect(details!.media.format, CatalogFormat.oneShot);
      expect(details.media.status, CatalogStatus.notYetReleased);
      expect(details.media.year, 2025);
      expect(details.media.alternateTitles, ['Romaji', 'Native']);
      expect(details.media.studios, ['Studio']);
      expect(details.media.staff, ['Author']);
      expect(details.description, 'Plain & text');
      expect(details.relations.map((r) => r.media.id.value), ['10']);
      expect(details.warnings, contains(contains('one field failed')));
      expect(
        details.warnings,
        contains(contains('related entries were omitted')),
      );
      await provider.close();
    },
  );

  test('detail GraphQL failure is not reported as a missing entry', () async {
    final provider = AnilistCatalogProvider(
      post: (_, _) async => const AniListHttpResponse(
        200,
        '{"data":{"Media":null},"errors":[{"message":"upstream failed"}]}',
      ),
    );
    addTearDown(provider.close);
    await expectLater(
      provider.details(const CatalogMediaId(provider: 'anilist', value: '9')),
      throwsA(isA<FormatException>()),
    );
  });

  test('detail response must match requested id; rate limit throws', () async {
    final provider = AnilistCatalogProvider(
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
      await provider.details(
        const CatalogMediaId(provider: 'anilist', value: '9'),
      ),
      isNull,
    );
    await provider.close();
    final limited = AnilistCatalogProvider(
      post: (_, _) async => const AniListHttpResponse(429, '{}'),
    );
    await expectLater(limited.discover(), throwsA(isA<Exception>()));
  });
}
