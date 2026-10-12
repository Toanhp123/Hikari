import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/foreground_read_gate.dart';
import 'package:hikari/application/media/prefetch_novel_chapter.dart';
import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/media/read_novel_resource.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';

const _sourceId = SourceId('novel');

void main() {
  test('warms next chapter content and unique resources serially', () async {
    final source = _Source()
      ..contentFor = (chapter) => RichReadingContent(
        html: '<p>${chapter.itemId}</p>',
        resources: {
          'first': _ref('image-1'),
          'duplicate': _ref('image-1'),
          'second': _ref('image-2'),
        },
      );
    final prefetch = _prefetch(source, _Cache());

    await prefetch.execute(_chapter('next'));

    expect(source.chapterReads, ['next']);
    expect(source.resourceReads, ['image-1', 'image-2']);
    expect(source.maxActiveResources, 1);
  });

  test('default request limit counts only unique resource attempts', () async {
    final source = _Source()
      ..contentFor = (_) => RichReadingContent(
        html: '<p>next</p>',
        resources: {
          'one': _ref('image-1'),
          'duplicate': _ref('image-1'),
          'two': _ref('image-2'),
          'three': _ref('image-3'),
          'four': _ref('image-4'),
          'five': _ref('image-5'),
        },
      );
    final prefetch = _prefetch(source, _Cache());

    await prefetch.execute(_chapter('next'));

    expect(source.chapterReads, ['next']);
    expect(source.resourceReads, ['image-1', 'image-2', 'image-3', 'image-4']);
  });

  test('stops after resource byte budget without starting the next read', () async {
    final source = _Source()
      ..resourceLengths.addAll({'image-1': 3, 'image-2': 4})
      ..contentFor = (_) => RichReadingContent(
        html: '<p>next</p>',
        resources: {
          'one': _ref('image-1'),
          'two': _ref('image-2'),
          'three': _ref('image-3'),
        },
      );
    final prefetch = _prefetch(source, _Cache(), maxResourceBytes: 5);

    await prefetch.execute(_chapter('next'));

    // Resource APIs return whole buffers: the second read crosses the soft cap.
    expect(source.resourceReads, ['image-1', 'image-2']);
  });

  test('failed resource attempts consume the request limit', () async {
    final source = _Source()
      ..failResources.add('broken')
      ..contentFor = (_) => RichReadingContent(
        html: '<p>next</p>',
        resources: {'broken': _ref('broken'), 'good': _ref('good')},
      );
    final prefetch = _prefetch(source, _Cache(), maxResourceRequests: 1);

    await prefetch.execute(_chapter('next'));

    expect(source.resourceReads, ['broken']);
  });

  test(
    'resource warming can be disabled without skipping chapter text',
    () async {
      final cache = _Cache();
      final source = _Source()
        ..contentFor = (_) => RichReadingContent(
          html: '<p>next</p>',
          resources: {'image': _ref('image')},
        );
      final prefetch = _prefetch(source, cache, maxResourceRequests: 0);

      await prefetch.execute(_chapter('next'));

      expect(source.chapterReads, ['next']);
      expect(source.resourceReads, isEmpty);
      final contentReader = ReadNovelChapterContent(cache);
      final content = await contentReader.execute(source, _ref('next'));
      expect(content.resources.containsKey('image'), isTrue);
      expect(source.chapterReads, ['next']);
    },
  );

  test(
    'foreground still fetches resources skipped by prefetch limits',
    () async {
      final cache = _Cache();
      final source = _Source()
        ..contentFor = (_) => RichReadingContent(
          html: '<p>next</p>',
          resources: {'first': _ref('image-1'), 'second': _ref('image-2')},
        );
      final resourceReader = ReadNovelResource(cache);
      final prefetch = PrefetchNovelChapter(
        SourceRegistry([source]),
        ReadNovelChapterContent(cache),
        resourceReader,
        maxResourceRequests: 1,
      );

      await prefetch.execute(_chapter('next'));
      final foreground = await resourceReader.execute(source, _ref('image-2'));

      expect(foreground, [1]);
      expect(source.resourceReads, ['image-1', 'image-2']);
    },
  );

  test('foreground joins an active chapter-content prefetch', () async {
    final cache = _Cache();
    final source = _Source()
      ..holdChapter = 'next'
      ..contentFor = (_) => RichReadingContent(html: '<p>next</p>');
    final contentReader = ReadNovelChapterContent(cache);
    final prefetch = PrefetchNovelChapter(
      SourceRegistry([source]),
      contentReader,
      ReadNovelResource(cache),
    );

    final pending = prefetch.execute(_chapter('next'));
    await source.chapterStarted.future;
    final foreground = contentReader.execute(source, _ref('next'));

    expect(source.chapterReads, ['next']);
    source.chapterGate.complete(RichReadingContent(html: '<p>next</p>'));
    expect((await foreground).html, '<p>next</p>');
    await pending;
    expect(source.chapterReads, ['next']);
  });

  test('foreground joins an active resource prefetch', () async {
    final cache = _Cache();
    final source = _Source()
      ..holdResource = 'image'
      ..contentFor = (_) => RichReadingContent(
        html: '<p>next</p>',
        resources: {'image': _ref('image')},
      );
    final contentReader = ReadNovelChapterContent(cache);
    final resourceReader = ReadNovelResource(cache);
    final prefetch = PrefetchNovelChapter(
      SourceRegistry([source]),
      contentReader,
      resourceReader,
    );

    final pending = prefetch.execute(_chapter('next'));
    await source.resourceStarted.future;
    final foreground = resourceReader.execute(source, _ref('image'));

    expect(source.resourceReads, ['image']);
    source.resourceGate.complete(Uint8List.fromList([1]));
    expect(await foreground, [1]);
    await pending;
    expect(source.resourceReads, ['image']);
  });

  test('warmed chapter and resources are reused by foreground reads', () async {
    final cache = _Cache();
    final source = _Source()
      ..contentFor = (_) => RichReadingContent(
        html: '<p>next</p>',
        resources: {'image': _ref('image')},
      );
    final contentReader = ReadNovelChapterContent(cache);
    final resourceReader = ReadNovelResource(cache);
    final prefetch = PrefetchNovelChapter(
      SourceRegistry([source]),
      contentReader,
      resourceReader,
    );

    await prefetch.execute(_chapter('next'));
    source.chapterReads.clear();
    source.resourceReads.clear();

    final content = await contentReader.execute(source, _ref('next'));
    await resourceReader.execute(source, content.resources['image']!);

    expect(source.chapterReads, isEmpty);
    expect(source.resourceReads, isEmpty);
  });

  test('foreground resource read pauses new chapter prefetch', () async {
    final source = _Source()
      ..holdResource = 'current-image'
      ..contentFor = (_) => RichReadingContent(
        html: '<p>next</p>',
        resources: {'image': _ref('next-image')},
      );
    final cache = _Cache();
    final gate = ForegroundReadGate();
    final contentReader = ReadNovelChapterContent(cache, readGate: gate);
    final resourceReader = ReadNovelResource(cache, readGate: gate);
    final prefetch = PrefetchNovelChapter(
      SourceRegistry([source]),
      contentReader,
      resourceReader,
    );

    final foreground = resourceReader.execute(source, _ref('current-image'));
    await source.resourceStarted.future;
    final pending = prefetch.execute(_chapter('next'));
    await Future<void>.delayed(Duration.zero);
    expect(source.chapterReads, isEmpty);
    expect(source.resourceReads, ['current-image']);

    source.resourceGate.complete(Uint8List.fromList([1]));
    await foreground;
    await pending;
    expect(source.chapterReads, ['next']);
    expect(source.resourceReads, ['current-image', 'next-image']);
  });

  test(
    'cancelled novel prefetch stays skipped after foreground finishes',
    () async {
      final source = _Source()..holdResource = 'current-image';
      final cache = _Cache();
      final gate = ForegroundReadGate();
      final resourceReader = ReadNovelResource(cache, readGate: gate);
      final prefetch = PrefetchNovelChapter(
        SourceRegistry([source]),
        ReadNovelChapterContent(cache, readGate: gate),
        resourceReader,
      );

      final foreground = resourceReader.execute(source, _ref('current-image'));
      await source.resourceStarted.future;
      final pending = prefetch.execute(_chapter('next'));
      prefetch.cancelPending();
      source.resourceGate.complete(Uint8List.fromList([1]));
      await Future.wait([foreground, pending]);
      expect(source.chapterReads, isEmpty);
    },
  );

  test(
    'latest request supersedes active remainder and queued chapters',
    () async {
      final source = _Source()
        ..holdChapter = 'b'
        ..contentFor = (chapter) => RichReadingContent(
          html: '<p>${chapter.itemId}</p>',
          resources: {'image': _ref('${chapter.itemId}-image')},
        );
      final prefetch = _prefetch(source, _Cache());

      final first = prefetch.execute(_chapter('b'));
      await source.chapterStarted.future;
      final stale = prefetch.execute(_chapter('c'));
      final newest = prefetch.execute(_chapter('d'));

      source.chapterGate.complete(
        RichReadingContent(
          html: '<p>b</p>',
          resources: {'image': _ref('b-image')},
        ),
      );
      await Future.wait([first, stale, newest]);

      expect(source.chapterReads, ['b', 'd']);
      expect(source.resourceReads, ['d-image']);
    },
  );

  test(
    'cancel lets active read finish but skips its remaining resources',
    () async {
      final source = _Source()
        ..holdResource = 'one'
        ..contentFor = (_) => RichReadingContent(
          html: '<p>next</p>',
          resources: {'one': _ref('one'), 'two': _ref('two')},
        );
      final prefetch = _prefetch(source, _Cache());

      final pending = prefetch.execute(_chapter('next'));
      await source.resourceStarted.future;
      prefetch.cancelPending();
      source.resourceGate.complete(Uint8List.fromList([1]));
      await pending;

      expect(source.resourceReads, ['one']);
    },
  );

  test(
    'speculative failures are swallowed and later work still runs',
    () async {
      final source = _Source()
        ..failChapter = 'broken'
        ..failResources.add('bad')
        ..contentFor = (chapter) => RichReadingContent(
          html: '<p>${chapter.itemId}</p>',
          resources: chapter.itemId == 'good'
              ? {'bad': _ref('bad'), 'ok': _ref('ok')}
              : const {},
        );
      final prefetch = _prefetch(source, _Cache());

      await prefetch.execute(_chapter('broken'));
      await prefetch.execute(_chapter('good'));

      expect(source.chapterReads, ['broken', 'good']);
      expect(source.resourceReads, ['bad', 'ok']);
    },
  );

  test('empty request does not cancel an active valid chapter', () async {
    final source = _Source()
      ..holdChapter = 'next'
      ..contentFor = (_) => RichReadingContent(html: '<p>next</p>');
    final prefetch = _prefetch(source, _Cache());

    final pending = prefetch.execute(_chapter('next'));
    await source.chapterStarted.future;
    await prefetch.execute(_chapter(''));
    source.chapterGate.complete(RichReadingContent(html: '<p>next</p>'));
    await pending;

    expect(source.chapterReads, ['next']);
  });
}

PrefetchNovelChapter _prefetch(
  _Source source,
  _Cache cache, {
  int maxResourceRequests = 4,
  int maxResourceBytes = 8 * 1024 * 1024,
}) => PrefetchNovelChapter(
  SourceRegistry([source]),
  ReadNovelChapterContent(cache),
  ReadNovelResource(cache),
  maxResourceRequests: maxResourceRequests,
  maxResourceBytes: maxResourceBytes,
);

NovelChapter _chapter(String itemId) =>
    NovelChapter(title: itemId, source: _ref(itemId));

SourceMediaRef _ref(String itemId) =>
    SourceMediaRef(sourceId: _sourceId, itemId: itemId);

final class _Source implements NovelChapterSource {
  final List<String> chapterReads = [];
  final List<String> resourceReads = [];
  final Set<String> failResources = {};
  final Map<String, int> resourceLengths = {};
  RichReadingContent Function(SourceMediaRef chapter)? contentFor;
  String? failChapter;
  String? holdChapter;
  String? holdResource;
  int activeResources = 0;
  int maxActiveResources = 0;
  final chapterStarted = Completer<void>();
  final chapterGate = Completer<RichReadingContent>();
  final resourceStarted = Completer<void>();
  final resourceGate = Completer<Uint8List>();

  @override
  SourceId get id => _sourceId;

  @override
  String get name => 'Novel';

  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async {
    chapterReads.add(chapter.itemId);
    if (chapter.itemId == holdChapter) {
      if (!chapterStarted.isCompleted) chapterStarted.complete();
      return chapterGate.future;
    }
    if (chapter.itemId == failChapter) throw StateError('offline');
    return contentFor?.call(chapter) ??
        RichReadingContent(html: '<p>${chapter.itemId}</p>');
  }

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async {
    resourceReads.add(resource.itemId);
    activeResources++;
    if (activeResources > maxActiveResources) {
      maxActiveResources = activeResources;
    }
    try {
      if (resource.itemId == holdResource) {
        if (!resourceStarted.isCompleted) resourceStarted.complete();
        return await resourceGate.future;
      }
      if (failResources.contains(resource.itemId)) {
        throw StateError('offline');
      }
      return Uint8List.fromList(
        List<int>.filled(resourceLengths[resource.itemId] ?? 1, 1),
      );
    } finally {
      activeResources--;
    }
  }
}

final class _Cache implements ByteCache {
  final Map<String, Uint8List> values = {};

  String _key(String namespace, String key) => jsonEncode([namespace, key]);

  @override
  Future<Uint8List?> read(String namespace, String key) async =>
      values[_key(namespace, key)];

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {
    values[_key(namespace, key)] = bytes;
  }

  @override
  Future<void> close() async {}
}
