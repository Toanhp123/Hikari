import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
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

PrefetchNovelChapter _prefetch(_Source source, _Cache cache) =>
    PrefetchNovelChapter(
      SourceRegistry([source]),
      ReadNovelChapterContent(cache),
      ReadNovelResource(cache),
    );

NovelChapter _chapter(String itemId) =>
    NovelChapter(title: itemId, source: _ref(itemId));

SourceMediaRef _ref(String itemId) =>
    SourceMediaRef(sourceId: _sourceId, itemId: itemId);

final class _Source implements NovelChapterSource {
  final List<String> chapterReads = [];
  final List<String> resourceReads = [];
  final Set<String> failResources = {};
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
      return Uint8List.fromList([1]);
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
