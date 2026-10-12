import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/infrastructure/catalog/anilist/anilist_catalog_provider.dart';
import 'package:hikari/infrastructure/catalog/anilist/anilist_metadata_cache.dart';

void main() {
  late _MemoryCache cache;
  late DateTime now;

  setUp(() {
    cache = _MemoryCache();
    now = DateTime.utc(2026, 10, 12);
  });

  test('discovery snapshot survives provider replacement until TTL', () async {
    var requests = 0;
    final first = AniListCatalogProvider(
      cache: cache,
      clock: () => now,
      post: (_, _) async {
        requests++;
        return AniListHttpResponse(200, jsonEncode(_discovery('First')));
      },
    );
    expect(
      (await first.discover()).sections[CatalogSection.featured]!.first.title,
      'First',
    );
    await first.close();

    final second = AniListCatalogProvider(
      cache: cache,
      clock: () => now,
      post: (_, _) async {
        requests++;
        return AniListHttpResponse(200, jsonEncode(_discovery('Updated')));
      },
    );
    addTearDown(second.close);
    expect((await second.cachedDiscovery())?.isFresh, isTrue);
    expect(
      (await second.discover()).sections[CatalogSection.featured]!.first.title,
      'First',
    );
    expect(requests, 1);

    now = now.add(const Duration(minutes: 31));
    expect((await second.cachedDiscovery())?.isFresh, isFalse);
    expect(
      (await second.discover()).sections[CatalogSection.featured]!.first.title,
      'Updated',
    );
    expect(requests, 2);
    expect((await second.cachedDiscovery())?.isFresh, isTrue);
  });

  test('new season does not reuse current season discovery as fresh', () async {
    final provider = AniListCatalogProvider(
      cache: cache,
      clock: () => now,
      post: (_, _) async =>
          AniListHttpResponse(200, jsonEncode(_discovery('Fall'))),
    );
    addTearDown(provider.close);
    await provider.discover();
    now = DateTime.utc(2027, 1, 1);
    expect(await provider.cachedDiscovery(), isNull);
  });

  test(
    'manual refresh bypasses fresh discovery and detail snapshots',
    () async {
      var count = 0;
      final provider = AniListCatalogProvider(
        cache: cache,
        clock: () => now,
        post: (_, body) async {
          count++;
          final isDetail = (jsonDecode(body)['query'] as String).contains(
            'Media(id:',
          );
          return AniListHttpResponse(
            200,
            jsonEncode(
              isDetail
                  ? _detail(count.toString())
                  : _discovery(count.toString()),
            ),
          );
        },
      );
      addTearDown(provider.close);
      await provider.discover();
      await provider.discover();
      expect(count, 1);
      await provider.refreshDiscovery();
      expect(count, 2);

      const id = CatalogEntryId(provider: 'anilist', value: '42');
      expect((await provider.loadDetails(id))?.entry.title, '3');
      expect((await provider.loadDetails(id))?.entry.title, '3');
      expect(count, 3);
      expect((await provider.refreshDetails(id))?.entry.title, '4');
      expect(count, 4);
    },
  );

  test(
    'rate limit and partial GraphQL results cannot overwrite snapshot',
    () async {
      var response = AniListHttpResponse(200, jsonEncode(_discovery('Good')));
      final provider = AniListCatalogProvider(
        cache: cache,
        clock: () => now,
        post: (_, _) async => response,
      );
      addTearDown(provider.close);
      await provider.discover();
      response = AniListHttpResponse(
        200,
        jsonEncode({
          ..._discovery('Partial'),
          'errors': [
            {'message': 'partial failure'},
          ],
        }),
      );
      await provider.refreshDiscovery();
      expect(
        (await provider.cachedDiscovery())
            ?.data
            .sections[CatalogSection.featured]!
            .first
            .title,
        'Good',
      );
      response = const AniListHttpResponse(429, 'rate limited');
      await expectLater(provider.refreshDiscovery(), throwsA(isA<Exception>()));
      expect(
        (await provider.cachedDiscovery())
            ?.data
            .sections[CatalogSection.featured]!
            .first
            .title,
        'Good',
      );
    },
  );

  test(
    'clear, corruption and future timestamps are cache misses or stale',
    () async {
      final provider = AniListCatalogProvider(
        cache: cache,
        clock: () => now,
        post: (_, _) async =>
            AniListHttpResponse(200, jsonEncode(_discovery('Content'))),
      );
      addTearDown(provider.close);
      await provider.discover();
      final original = await cache.read(
        AniListMetadataCache.namespace,
        'discovery:2026:FALL',
      );
      expect(original, isNotNull);
      await cache.write(
        AniListMetadataCache.namespace,
        'discovery:2026:FALL',
        Uint8List.fromList([0, 255, 12]),
      );
      expect(await provider.cachedDiscovery(), isNull);
      await cache.write(
        AniListMetadataCache.namespace,
        'discovery:2026:FALL',
        original!,
      );
      now = now.subtract(const Duration(days: 2));
      expect((await provider.cachedDiscovery())?.isFresh, isFalse);
      now = now.add(const Duration(days: 2));
      cache.data.clear();
      expect(await provider.cachedDiscovery(), isNull);
    },
  );

  test(
    'detail snapshots are keyed by identity and reject wrong media',
    () async {
      final provider = AniListCatalogProvider(
        cache: cache,
        clock: () => now,
        post: (_, _) async =>
            AniListHttpResponse(200, jsonEncode(_detail('A'))),
      );
      addTearDown(provider.close);
      const id = CatalogEntryId(provider: 'anilist', value: '42');
      const wrong = CatalogEntryId(provider: 'other', value: '42');
      expect((await provider.loadDetails(id))?.entry.title, 'A');
      expect((await provider.cachedDetails(id))?.isFresh, isTrue);
      expect(await provider.cachedDetails(wrong), isNull);
      expect(await provider.loadDetails(wrong), isNull);
    },
  );

  test(
    'search has five-minute memory TTL and concurrent request deduplication',
    () async {
      final completer = Completer<AniListHttpResponse>();
      var count = 0;
      final provider = AniListCatalogProvider(
        clock: () => now,
        post: (_, _) {
          count++;
          if (count == 1) return completer.future;
          return Future.value(AniListHttpResponse(200, jsonEncode(_search())));
        },
      );
      addTearDown(provider.close);
      final first = provider.search('Title');
      final second = provider.search('Title');
      completer.complete(AniListHttpResponse(200, jsonEncode(_search())));
      expect((await first).single.title, 'Title');
      expect((await second).single.title, 'Title');
      expect((await provider.search('Title')).single.title, 'Title');
      expect(count, 1);
      now = now.add(const Duration(minutes: 6));
      await provider.search('Title');
      expect(count, 2);
    },
  );
}

Map<String, Object?> _row(int id, String name) => {
  'id': id,
  'type': 'ANIME',
  'format': 'TV',
  'title': {'english': name},
  'coverImage': {'large': 'https://example.org/cover/$id'},
};

Map<String, Object?> _discovery(String title) => {
  'data': {
    'featuredAnime': {
      'media': [_row(42, title)],
    },
    'featuredManga': {'media': <Object>[]},
    'featuredNovels': {'media': <Object>[]},
    'trending': {'media': <Object>[]},
    'anime': {'media': <Object>[]},
    'manga': {'media': <Object>[]},
    'novels': {'media': <Object>[]},
    'seasonal': {'media': <Object>[]},
  },
};

Map<String, Object?> _detail(String title) => {
  'data': {
    'Media': {
      ..._row(42, title),
      'relations': {'edges': <Object>[]},
    },
  },
};

Map<String, Object?> _search() => {
  'data': {
    'Page': {
      'media': [_row(42, 'Title')],
    },
  },
};

final class _MemoryCache implements ByteCache {
  final data = <(String, String), Uint8List>{};

  @override
  Future<Uint8List?> read(String namespace, String key) async =>
      data[(namespace, key)];

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {
    data[(namespace, key)] = bytes;
  }

  @override
  Future<void> close() async {}
}
