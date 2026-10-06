import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_novel_chapter.dart';
import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/remote_novel/novel_chapter_reader_view_model.dart';

const _id = SourceId('novel');
const _a = SourceMediaRef(sourceId: _id, itemId: 'a');
const _b = SourceMediaRef(sourceId: _id, itemId: 'b');
const _c = SourceMediaRef(sourceId: _id, itemId: 'c');

class _Progress implements ProgressRepository {
  @override
  Future<void> delete(SourceMediaRef media) async {}

  @override
  Future<MediaProgress?> load(SourceMediaRef media) async => null;

  @override
  Future<void> save(MediaProgress progress) async {}
}

class _Cache implements ByteCache {
  @override
  Future<Uint8List?> read(String namespace, String key) async => null;

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {}

  @override
  Future<void> close() async {}
}

class _Source implements NovelChapterSource {
  final gates = <String, Completer<RichReadingContent>>{};
  final started = <String, Completer<void>>{};
  final calls = <String>[];

  Completer<void> startedFor(String id) =>
      started.putIfAbsent(id, Completer<void>.new);

  @override
  SourceId get id => _id;

  @override
  String get name => 'Novel';

  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) {
    calls.add(chapter.itemId);
    final signal = startedFor(chapter.itemId);
    if (!signal.isCompleted) signal.complete();
    return gates[chapter.itemId]?.future ??
        Future.value(RichReadingContent(html: '<p>${chapter.itemId}</p>'));
  }

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}

OpenNovelChapter _workflow(_Source source) => OpenNovelChapter(
  SourceRegistry([source]),
  _Progress(),
  ReadNovelChapterContent(_Cache()),
);

void main() {
  test('navigation follows sequence; busy requests do not queue', () async {
    final source = _Source()..gates['c'] = Completer();
    final workflow = _workflow(source);
    final model = await _model(workflow, _b, [_a, _b, _c]);
    addTearDown(model.dispose);
    await model.openPrevious();
    expect(model.state.target.chapter.source, _a);
    await model.openNext();
    expect(model.state.target.chapter.source, _b);
    final started = source.startedFor('c');
    final moving = model.openNext();
    await started.future;
    await model.openPrevious();
    await model.openNext();
    expect(source.calls.where((id) => id == 'c'), hasLength(1));
    source.gates['c']!.complete(RichReadingContent(html: '<p>C</p>'));
    await moving;
    expect(model.state.target.chapter.source, _c);
    await model.openNext();
    expect(source.calls.where((id) => id == 'c'), hasLength(1));
  });

  test(
    'successful adjacent open activates the new chapter without scrolling',
    () async {
      final source = _Source();
      final workflow = _workflow(source);
      final activations = <SourceMediaRef>[];
      final model = NovelChapterReaderViewModel(
        initialTarget: await workflow.execute(_chapter(_a)),
        chaptersInReadingOrder: [_chapter(_a), _chapter(_b)],
        openChapter: workflow,
        onChapterActivated: (target) async =>
            activations.add(target.chapter.source),
      );
      addTearDown(model.dispose);

      await model.openNext();
      await model.flush();

      expect(model.state.target.chapter.source, _b);
      expect(activations, [_b]);
    },
  );

  test('failed adjacent open does not activate the target chapter', () async {
    final source = _Source()..gates['b'] = Completer();
    final workflow = _workflow(source);
    final activations = <SourceMediaRef>[];
    final model = NovelChapterReaderViewModel(
      initialTarget: await workflow.execute(_chapter(_a)),
      chaptersInReadingOrder: [_chapter(_a), _chapter(_b)],
      openChapter: workflow,
      onChapterActivated: (target) async =>
          activations.add(target.chapter.source),
    );
    addTearDown(model.dispose);
    final pending = model.openNext();
    await source.startedFor('b').future;
    source.gates['b']!.completeError(StateError('offline'));

    await expectLater(pending, throwsStateError);
    await model.flush();

    expect(model.state.target.chapter.source, _a);
    expect(activations, isEmpty);
  });

  test('same-number refs distinct and sequence input snapshotted', () async {
    final source = _Source();
    final workflow = _workflow(source);
    final chapters = [
      NovelChapter(title: 'A', source: _a, chapterNumber: 1),
      NovelChapter(title: 'B', source: _b, chapterNumber: 1),
    ];
    final target = await workflow.execute(chapters.first);
    final model = NovelChapterReaderViewModel(
      initialTarget: target,
      chaptersInReadingOrder: chapters,
      openChapter: workflow,
    );
    chapters.clear();
    addTearDown(model.dispose);
    await model.openNext();
    expect(model.state.target.chapter.source, _b);
  });

  test('boundary commands make no source calls', () async {
    final source = _Source();
    final workflow = _workflow(source);
    final first = await _model(workflow, _a, [_a, _b]);
    addTearDown(first.dispose);
    final last = await _model(workflow, _b, [_a, _b]);
    addTearDown(last.dispose);
    source.calls.clear();
    await first.openPrevious();
    await last.openNext();
    expect(source.calls, isEmpty);
  });

  test('failure preserves current target and allows retry', () async {
    final source = _Source()..gates['b'] = Completer();
    final workflow = _workflow(source);
    final started = source.startedFor('b');
    final model = await _model(workflow, _a, [_a, _b]);
    addTearDown(model.dispose);
    final pending = model.openNext();
    await started.future;
    source.gates['b']!.completeError(StateError('offline'));
    await expectLater(pending, throwsStateError);
    expect(model.state.target.chapter.source, _a);
    expect(model.state.openingAdjacent, isFalse);
    source.gates.remove('b');
    source.started.remove('b');
    await model.openNext();
    expect(model.state.target.chapter.source, _b);
  });

  test('close and dispose ignore stale failure', () async {
    for (final disposed in [false, true]) {
      final source = _Source()..gates['b'] = Completer();
      final workflow = _workflow(source);
      final started = source.startedFor('b');
      final model = await _model(workflow, _a, [_a, _b]);
      var notices = 0;
      model.addListener(() => notices++);
      final pending = model.openNext();
      await started.future;
      if (disposed) {
        model.dispose();
      } else {
        model.close();
      }
      source.gates['b']!.completeError(StateError('late'));
      await pending;
      expect(model.state.target.chapter.source, _a);
      expect(notices, 1);
      if (!disposed) model.dispose();
    }
  });

  test('close and dispose ignore stale success', () async {
    for (final disposed in [false, true]) {
      final source = _Source()..gates['b'] = Completer();
      final workflow = _workflow(source);
      final started = source.startedFor('b');
      final model = await _model(workflow, _a, [_a, _b]);
      var notices = 0;
      model.addListener(() => notices++);
      final pending = model.openNext();
      await started.future;
      if (disposed) {
        model.dispose();
      } else {
        model.close();
      }
      source.gates['b']!.complete(RichReadingContent(html: '<p>B</p>'));
      await pending;
      expect(model.state.target.chapter.source, _a);
      expect(notices, 1);
      if (!disposed) model.dispose();
    }
  });

  test(
    'missing selected, missing, and duplicate source refs rejected',
    () async {
      final source = _Source();
      final workflow = _workflow(source);
      final target = await workflow.execute(_chapter(_a));
      for (final sequence in [
        [_chapter(_b)],
        [_chapter(_a), _chapter(_a)],
        [_chapter(const SourceMediaRef(sourceId: _id, itemId: ''))],
      ]) {
        expect(
          () => NovelChapterReaderViewModel(
            initialTarget: target,
            chaptersInReadingOrder: sequence,
            openChapter: workflow,
          ),
          throwsArgumentError,
        );
      }
    },
  );
}

Future<NovelChapterReaderViewModel> _model(
  OpenNovelChapter workflow,
  SourceMediaRef current,
  List<SourceMediaRef> refs,
) async => NovelChapterReaderViewModel(
  initialTarget: await workflow.execute(_chapter(current)),
  chaptersInReadingOrder: refs.map(_chapter).toList(),
  openChapter: workflow,
);

NovelChapter _chapter(SourceMediaRef ref) =>
    NovelChapter(title: ref.itemId, source: ref);
