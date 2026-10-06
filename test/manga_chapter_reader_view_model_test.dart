import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/progress/progress_session.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/remote_manga/manga_chapter_reader_view_model.dart';

const _id = SourceId('manga');
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

class _Source implements MangaPageSource {
  final gates = <String, Completer<List<SourceMediaRef>>>{};
  final started = <String, Completer<void>>{};
  final calls = <String>[];

  @override
  SourceId get id => _id;

  @override
  String get name => 'Manga';

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) {
    calls.add(readable.itemId);
    final signal = started.putIfAbsent(readable.itemId, Completer<void>.new);
    if (!signal.isCompleted) signal.complete();
    return gates[readable.itemId]?.future ?? Future.value([readable]);
  }

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

void main() {
  test('navigation follows sequence; busy requests do not queue', () async {
    final source = _Source()..gates['c'] = Completer();
    final model = await _model(source, _b, [_a, _b, _c]);
    addTearDown(model.dispose);
    await model.openPrevious();
    expect(model.state.target.chapter.source, _a);
    await model.openNext();
    expect(model.state.target.chapter.source, _b);

    final started = source.started.putIfAbsent('c', Completer<void>.new);
    final moving = model.openNext();
    await started.future;
    expect(model.state.openingAdjacent, isTrue);
    await model.openPrevious();
    await model.openNext();
    expect(source.calls.where((id) => id == 'c'), hasLength(1));
    source.gates['c']!.complete([_c]);
    await moving;
    expect(model.state.target.chapter.source, _c);
    expect(model.canOpenNext, isFalse);
  });

  test(
    'adjacent open can consume a reader-session page-list handoff',
    () async {
      final source = _Source();
      final workflow = OpenMangaChapter(SourceRegistry([source]), _Progress());
      var handoffs = 0;
      final model = MangaChapterReaderViewModel(
        initialTarget: await _open(source, _chapter(_a)),
        chaptersInReadingOrder: [_chapter(_a), _chapter(_b)],
        openChapter: workflow,
        pageListLoader: (resolvedSource, chapter) async {
          handoffs++;
          expect(resolvedSource, same(source));
          expect(chapter, _b);
          return [const SourceMediaRef(sourceId: _id, itemId: 'b-page')];
        },
      );
      addTearDown(model.dispose);

      expect(model.nextChapter?.source, _b);
      await model.openNext();

      expect(handoffs, 1);
      expect(source.calls, isEmpty);
      expect(model.state.target.pages.single.itemId, 'b-page');
      expect(model.nextChapter, isNull);
    },
  );

  test(
    'successful adjacent open activates the new chapter only after open',
    () async {
      final source = _Source();
      final workflow = OpenMangaChapter(SourceRegistry([source]), _Progress());
      final activations = <SourceMediaRef>[];
      final model = MangaChapterReaderViewModel(
        initialTarget: await _open(source, _chapter(_a)),
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
    final workflow = OpenMangaChapter(SourceRegistry([source]), _Progress());
    final activations = <SourceMediaRef>[];
    final model = MangaChapterReaderViewModel(
      initialTarget: await _open(source, _chapter(_a)),
      chaptersInReadingOrder: [_chapter(_a), _chapter(_b)],
      openChapter: workflow,
      onChapterActivated: (target) async =>
          activations.add(target.chapter.source),
    );
    addTearDown(model.dispose);
    final started = source.started.putIfAbsent('b', Completer<void>.new);
    final pending = model.openNext();
    await started.future;
    source.gates['b']!.completeError(StateError('offline'));

    await expectLater(pending, throwsStateError);
    await model.flush();

    expect(model.state.target.chapter.source, _a);
    expect(activations, isEmpty);
  });

  test('same-number chapters use distinct stable source refs', () async {
    final source = _Source();
    final first = MangaChapter(title: 'A', source: _a, chapterNumber: 1);
    final second = MangaChapter(title: 'B', source: _b, chapterNumber: 1);
    final model = MangaChapterReaderViewModel(
      initialTarget: await _open(source, first),
      chaptersInReadingOrder: [first, second],
      openChapter: OpenMangaChapter(SourceRegistry([source]), _Progress()),
    );
    addTearDown(model.dispose);
    await model.openNext();
    expect(model.state.target.chapter.source, _b);
  });

  test('unreadable chapters skipped; sequence copied', () async {
    final source = _Source();
    final first = _chapter(_a);
    final hidden = MangaChapter(
      title: 'Hidden',
      source: _b,
      canReadPages: false,
    );
    final last = _chapter(_c);
    final input = [first, hidden, last];
    final model = MangaChapterReaderViewModel(
      initialTarget: await _open(source, first),
      chaptersInReadingOrder: input,
      openChapter: OpenMangaChapter(SourceRegistry([source]), _Progress()),
    );
    input.clear();
    addTearDown(model.dispose);
    await model.openNext();
    expect(model.state.target.chapter.source, _c);
    final hiddenTarget = await _open(source, hidden);
    expect(
      () => MangaChapterReaderViewModel(
        initialTarget: hiddenTarget,
        chaptersInReadingOrder: [first, hidden],
        openChapter: OpenMangaChapter(SourceRegistry([source]), _Progress()),
      ),
      throwsArgumentError,
    );
  });

  test('boundary commands make no source calls', () async {
    final source = _Source();
    final first = await _model(source, _a, [_a, _b]);
    addTearDown(first.dispose);
    await first.openPrevious();
    final last = await _model(source, _b, [_a, _b]);
    addTearDown(last.dispose);
    await last.openNext();
    expect(source.calls, isEmpty);
  });

  test('open failure preserves target and permits retry', () async {
    final source = _Source()..gates['b'] = Completer();
    final started = source.started.putIfAbsent('b', Completer<void>.new);
    final model = await _model(source, _a, [_a, _b]);
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

  test(
    'close and dispose ignore stale failure without notifications',
    () async {
      for (final dispose in [false, true]) {
        final source = _Source()..gates['b'] = Completer();
        final started = source.started.putIfAbsent('b', Completer<void>.new);
        final model = await _model(source, _a, [_a, _b]);
        var notices = 0;
        model.addListener(() => notices++);
        final pending = model.openNext();
        await started.future;
        if (dispose) {
          model.dispose();
        } else {
          model.close();
        }
        source.gates['b']!.completeError(StateError('late'));
        await pending;
        expect(model.state.target.chapter.source, _a);
        expect(notices, 1);
        if (!dispose) model.dispose();
      }
    },
  );

  test(
    'close and dispose ignore stale success without notifications',
    () async {
      for (final dispose in [false, true]) {
        final source = _Source()..gates['b'] = Completer();
        final started = source.started.putIfAbsent('b', Completer<void>.new);
        final model = await _model(source, _a, [_a, _b]);
        var notices = 0;
        model.addListener(() => notices++);
        final pending = model.openNext();
        await started.future;
        if (dispose) {
          model.dispose();
        } else {
          model.close();
        }
        source.gates['b']!.complete([_b]);
        await pending;
        expect(model.state.target.chapter.source, _a);
        expect(notices, 1);
        if (!dispose) model.dispose();
      }
    },
  );

  test('missing selected, missing refs, and duplicate refs rejected', () async {
    final source = _Source();
    final workflow = OpenMangaChapter(SourceRegistry([source]), _Progress());
    final target = await workflow.execute(_chapter(_a));
    for (final sequence in [
      [_chapter(_b)],
      [_chapter(_a), _chapter(_a)],
      [
        _chapter(_a),
        MangaChapter(
          title: 'Unreadable duplicate',
          source: _a,
          canReadPages: false,
        ),
      ],
      [_chapter(const SourceMediaRef(sourceId: _id, itemId: ''))],
    ]) {
      expect(
        () => MangaChapterReaderViewModel(
          initialTarget: target,
          chaptersInReadingOrder: sequence,
          openChapter: workflow,
        ),
        throwsArgumentError,
      );
    }
  });
}

Future<MangaChapterReaderViewModel> _model(
  _Source source,
  SourceMediaRef current,
  List<SourceMediaRef> refs,
) async {
  final workflow = OpenMangaChapter(SourceRegistry([source]), _Progress());
  return MangaChapterReaderViewModel(
    initialTarget: await _open(source, _chapter(current)),
    chaptersInReadingOrder: refs.map(_chapter).toList(),
    openChapter: workflow,
  );
}

Future<MangaChapterOpenTarget> _open(
  _Source source,
  MangaChapter chapter,
) async => MangaChapterOpenTarget(
  chapter: chapter,
  source: source,
  pages: const [],
  progress: await ProgressSession.load(
    repository: _Progress(),
    media: chapter.source,
  ),
);

MangaChapter _chapter(SourceMediaRef ref) =>
    MangaChapter(title: ref.itemId, source: ref);
