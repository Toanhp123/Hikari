import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/infrastructure/mangadex/mangadex_source.dart';

const mangaId = '00000000-0000-4000-8000-000000000001';
const chapterId = '00000000-0000-4000-8000-000000000002';
const ref = SourceMediaRef(sourceId: SourceId('mangadex'), itemId: chapterId);
http.Response jsonResponse(Object body) => http.Response(jsonEncode(body), 200);
Map<String, Object?> chapter(
  String uuid, {
  Map<String, Object?> attributes = const {},
}) => {
  'id': uuid,
  'attributes': {
    'pages': 2,
    'readableAt': '2020-01-01T00:00:00Z',
    'translatedLanguage': 'en',
    'volume': '1',
    'chapter': '1',
    'title': 'Beginning',
    ...attributes,
  },
  'relationships': [
    {
      'type': 'scanlation_group',
      'attributes': {'name': 'Group'},
    },
  ],
};
Map<String, Object> session([String base = 'https://node.example/path']) => {
  'result': 'ok',
  'baseUrl': base,
  'chapter': {
    'hash': 'abc',
    'data': ['1.png', '2.png'],
  },
};

class PendingClient extends http.BaseClient {
  final started = Completer<void>();
  bool aborted = false;
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    started.complete();
    if (request is! http.Abortable) throw StateError('Request cannot abort');
    await request.abortTrigger;
    aborted = true;
    throw http.RequestAbortedException(request.url);
  }
}

void main() {
  test('search rejects oversized result list', () async {
    final source = MangaDexSource(
      client: MockClient(
        (_) async => jsonResponse({
          'result': 'ok',
          'data': List.generate(
            21,
            (_) => {
              'id': mangaId,
              'attributes': {
                'title': {'en': 'Title'},
              },
            },
          ),
        }),
      ),
    );
    await expectLater(source.search('title'), throwsFormatException);
  });
  test('at-home rejects encoded path separators', () async {
    final source = MangaDexSource(
      client: MockClient(
        (_) async => jsonResponse({
          'result': 'ok',
          'baseUrl': 'https://node.example',
          'chapter': {
            'hash': 'abc',
            'data': ['..%2Fa.png'],
          },
        }),
      ),
    );
    await expectLater(source.pages(ref), throwsFormatException);
  });
  test('invalid readable references fail before HTTP', () async {
    final source = MangaDexSource(
      client: MockClient((_) async => throw StateError('HTTP must not run')),
    );
    await expectLater(
      source.pages(
        const SourceMediaRef(sourceId: SourceId('other'), itemId: chapterId),
      ),
      throwsArgumentError,
    );
    await expectLater(
      source.pages(
        const SourceMediaRef(
          sourceId: SourceId('mangadex'),
          itemId: 'https://node.example',
        ),
      ),
      throwsFormatException,
    );
    await expectLater(source.readPage(ref), throwsFormatException);
    await expectLater(
      source.readPage(
        const SourceMediaRef(
          sourceId: SourceId('mangadex'),
          itemId: '$chapterId/-1',
        ),
      ),
      throwsFormatException,
    );
  });
  test(
    'malformed at-home metadata and excessive feed fail explicitly',
    () async {
      for (final body in <Object>[
        {
          'result': 'ok',
          'baseUrl': 'http://node.example',
          'chapter': {
            'hash': 'abc',
            'data': ['a.png'],
          },
        },
        {
          'result': 'ok',
          'baseUrl': 'https://node.example',
          'chapter': {'hash': 'abc', 'data': <String>[]},
        },
        {
          'result': 'ok',
          'baseUrl': 'https://node.example',
          'chapter': {
            'hash': 'abc',
            'data': ['../a.png'],
          },
        },
      ]) {
        final source = MangaDexSource(
          client: MockClient((_) async => jsonResponse(body)),
        );
        await expectLater(source.pages(ref), throwsFormatException);
      }
      for (final total in [1, 10001]) {
        final source = MangaDexSource(
          client: MockClient(
            (_) async => jsonResponse({
              'result': 'ok',
              'total': total,
              'data': <Object>[],
            }),
          ),
        );
        await expectLater(source.chapters(ref), throwsFormatException);
      }
    },
  );
  test('empty chapter feed succeeds', () async {
    final source = MangaDexSource(
      client: MockClient(
        (_) async =>
            jsonResponse({'result': 'ok', 'total': 0, 'data': <Object>[]}),
      ),
    );
    expect(await source.chapters(ref), isEmpty);
  });
  test('closing source aborts pending transport', () async {
    final client = PendingClient();
    final source = MangaDexSource(client: client);
    final result = source.search('title');
    final assertion = expectLater(
      result,
      throwsA(isA<http.RequestAbortedException>()),
    );
    await client.started.future;
    source.close();
    await assertion;
    expect(client.aborted, isTrue);
  });
  test('non-200 image response never returns a readable page', () async {
    final source = MangaDexSource(
      client: MockClient((r) async {
        if (r.url.path.startsWith('/at-home/')) {
          return jsonResponse(session('https://uploads.mangadex.org'));
        }
        return http.Response('', 204);
      }),
    );
    await expectLater(
      source.readPage(
        const SourceMediaRef(
          sourceId: SourceId('mangadex'),
          itemId: '$chapterId/0',
        ),
      ),
      throwsStateError,
    );
  });
  test(
    'closed source rejects new requests without touching injected client',
    () async {
      var calls = 0;
      final source = MangaDexSource(
        client: MockClient((_) async {
          calls++;
          return jsonResponse({'result': 'ok', 'data': <Object>[]});
        }),
      );
      source.close();
      await expectLater(source.search('title'), throwsStateError);
      expect(calls, 0);
    },
  );
  test('title fallback uses sorted language keys then untitled', () async {
    for (final titles in [
      <String, String>{'ja': 'Japanese', 'fr': 'French'},
      <String, String>{},
    ]) {
      final source = MangaDexSource(
        client: MockClient(
          (_) async => jsonResponse({
            'result': 'ok',
            'data': [
              {
                'id': mangaId,
                'attributes': {'title': titles},
              },
            ],
          }),
        ),
      );
      expect(
        (await source.search('q')).single.title,
        titles.isEmpty ? 'Untitled manga' : 'French',
      );
    }
  });
  test(
    'feed paginates, credits groups, preserves duplicate chapter numbers',
    () async {
      var calls = 0;
      final source = MangaDexSource(
        client: MockClient((r) async {
          expect(r.url.queryParameters['limit'], '500');
          expect(r.url.queryParameters['includeEmptyPages'], isNull);
          expect(r.url.queryParameters['translatedLanguage[]'], 'en');
          expect(r.url.queryParameters['includes[]'], 'scanlation_group');
          expect(r.url.queryParameters['offset'], '${calls++}');
          return jsonResponse({
            'result': 'ok',
            'total': 2,
            'data': [chapter(calls == 1 ? chapterId : mangaId)],
          });
        }),
      );
      final result = await source.chapters(
        const SourceMediaRef(sourceId: SourceId('mangadex'), itemId: mangaId),
      );
      expect(result, hasLength(2));
      expect(result.first.source.itemId, chapterId);
      expect(result.last.source.itemId, mangaId);
      expect(result.first.title, 'Vol. 1 · Ch. 1 · Beginning');
      expect(result.first.scanlator, 'Group');
    },
  );
  test('feed filters unavailable external future and empty chapters', () async {
    final source = MangaDexSource(
      client: MockClient(
        (_) async => jsonResponse({
          'result': 'ok',
          'total': 5,
          'data': [
            chapter(
              chapterId,
              attributes: {'externalUrl': 'https://example.com'},
            ),
            chapter(chapterId, attributes: {'isUnavailable': true}),
            chapter(
              chapterId,
              attributes: {'readableAt': '2100-01-01T00:00:00Z'},
            ),
            chapter(chapterId, attributes: {'pages': 0}),
            chapter(
              mangaId,
              attributes: {'volume': null, 'chapter': null, 'title': null},
            ),
          ],
        }),
      ),
    );
    final result = await source.chapters(ref);
    expect(result.single.title, 'Oneshot');
  });
  test(
    'expired session resolves again with same stable page reference',
    () async {
      var now = DateTime.utc(2026);
      var resolves = 0;
      final source = MangaDexSource(
        now: () => now,
        client: MockClient((r) async {
          if (r.url.path.startsWith('/at-home/')) {
            resolves++;
            return jsonResponse(session('https://uploads.mangadex.org'));
          }
          return http.Response.bytes([1], 200);
        }),
      );
      final pages = await source.pages(ref);
      now = now.add(const Duration(minutes: 15));
      await source.readPage(pages.first);
      expect(resolves, 2);
    },
  );
  test(
    '403 refreshes once and reports both failed and successful requests',
    () async {
      var resolves = 0;
      var images = 0;
      final reports = <Map<String, dynamic>>[];
      final source = MangaDexSource(
        client: MockClient((r) async {
          if (r.url.path.startsWith('/at-home/')) {
            resolves++;
            return jsonResponse(session());
          }
          if (r.method == 'POST') {
            reports.add(jsonDecode(r.body) as Map<String, dynamic>);
            expect(r.headers['content-type'], 'application/json');
            return http.Response('', 200);
          }
          images++;
          return http.Response('body', images == 1 ? 403 : 200);
        }),
      );
      await source.readPage(
        const SourceMediaRef(
          sourceId: SourceId('mangadex'),
          itemId: '$chapterId/0',
        ),
      );
      expect(resolves, 2);
      expect(images, 2);
      expect(reports.map((r) => r['success']), [false, true]);
    },
  );
  test('second 403 stops and reports without retry loop', () async {
    var resolves = 0;
    var images = 0;
    final source = MangaDexSource(
      client: MockClient((r) async {
        if (r.url.path.startsWith('/at-home/')) {
          resolves++;
          return jsonResponse(session());
        }
        if (r.method == 'POST') return http.Response('', 503);
        images++;
        return http.Response('', 403);
      }),
    );
    await expectLater(
      source.readPage(
        const SourceMediaRef(
          sourceId: SourceId('mangadex'),
          itemId: '$chapterId/0',
        ),
      ),
      throwsStateError,
    );
    expect(resolves, 2);
    expect(images, 2);
  });
  test(
    'report failure preserves successful bytes and uploads are exempt',
    () async {
      for (final base in [
        'https://node.example',
        'https://uploads.mangadex.org',
      ]) {
        var reports = 0;
        final source = MangaDexSource(
          client: MockClient((r) async {
            if (r.url.path.startsWith('/at-home/')) {
              return jsonResponse(session(base));
            }
            if (r.method == 'POST') {
              reports++;
              throw http.ClientException('offline');
            }
            return http.Response.bytes([3, 4], 200);
          }),
        );
        expect(
          await source.readPage(
            const SourceMediaRef(
              sourceId: SourceId('mangadex'),
              itemId: '$chapterId/0',
            ),
          ),
          [3, 4],
        );
        expect(reports, base.contains('uploads') ? 0 : 1);
      }
    },
  );
  test(
    'connection failure reports zero bytes and invalidates session',
    () async {
      Map<String, dynamic>? report;
      final source = MangaDexSource(
        client: MockClient((r) async {
          if (r.url.path.startsWith('/at-home/')) {
            return jsonResponse(session());
          }
          if (r.method == 'POST') {
            report = jsonDecode(r.body) as Map<String, dynamic>;
            return http.Response('', 200);
          }
          throw http.ClientException('offline');
        }),
      );
      await expectLater(
        source.readPage(
          const SourceMediaRef(
            sourceId: SourceId('mangadex'),
            itemId: '$chapterId/0',
          ),
        ),
        throwsA(isA<http.ClientException>()),
      );
      expect(report, containsPair('bytes', 0));
      expect(report, containsPair('success', false));
    },
  );
  test(
    'HTTP and malformed JSON fail deterministically without retry',
    () async {
      for (final response in [
        http.Response('', 429),
        http.Response('', 500),
        http.Response('html', 200),
        jsonResponse({'result': 'ok'}),
        jsonResponse({
          'result': 'ok',
          'data': [
            {
              'id': 'invalid',
              'attributes': {
                'title': {'en': 'Test'},
              },
            },
          ],
        }),
      ]) {
        var calls = 0;
        final source = MangaDexSource(
          client: MockClient((_) async {
            calls++;
            return response;
          }),
        );
        await expectLater(
          source.search('test'),
          throwsA(
            response.statusCode == 200
                ? isA<FormatException>()
                : isA<StateError>(),
          ),
        );
        expect(calls, 1);
      }
    },
  );

  test('search trims title and maps stable manga identity', () async {
    final source = MangaDexSource(
      client: MockClient((request) async {
        expect(request.headers['user-agent'], startsWith('Hikari/'));
        expect(request.url.queryParameters['title'], 'Test');
        expect(request.url.queryParametersAll['contentRating[]'], [
          'safe',
          'suggestive',
        ]);
        return jsonResponse({
          'result': 'ok',
          'data': [
            {
              'id': mangaId,
              'attributes': {
                'title': {'en': 'Test'},
              },
            },
          ],
        });
      }),
    );
    final result = await source.search(' Test ');
    expect(
      result.single.source,
      const SourceMediaRef(sourceId: SourceId('mangadex'), itemId: mangaId),
    );
    expect(result.single.title, 'Test');
    expect(result.single.type, MediaType.manga);
  });
  test('empty query makes no request', () async {
    final source = MangaDexSource(
      client: MockClient((_) async => throw StateError('unexpected')),
    );
    expect(await source.search('  '), isEmpty);
  });
  test('page refs stay stable and session is reused with reports', () async {
    var resolves = 0;
    final reports = <Map<String, dynamic>>[];
    final source = MangaDexSource(
      client: MockClient((r) async {
        if (r.url.path.startsWith('/at-home/')) {
          resolves++;
          return jsonResponse({
            'result': 'ok',
            'baseUrl': 'https://node.example/prefix',
            'chapter': {
              'hash': 'abc',
              'data': ['1.png', '2.png'],
            },
          });
        }
        if (r.method == 'POST') {
          reports.add(jsonDecode(r.body) as Map<String, dynamic>);
          return http.Response('', 200);
        }
        expect(r.headers.containsKey('authorization'), false);
        return http.Response.bytes(
          [1, 2, 3],
          200,
          headers: {'x-cache': 'HIT local'},
        );
      }),
    );
    final pages = await source.pages(ref);
    expect(pages.map((p) => p.itemId), ['$chapterId/0', '$chapterId/1']);
    await source.readPage(pages.first);
    await source.readPage(pages.last);
    expect(resolves, 1);
    expect(reports, hasLength(2));
    expect(reports.first, containsPair('cached', true));
    expect(reports.first, containsPair('bytes', 3));
    expect(reports.first, containsPair('success', true));
    expect(reports.first['duration'], isNonNegative);
  });
}
