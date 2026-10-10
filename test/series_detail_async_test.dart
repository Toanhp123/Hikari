import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/remote_manga/manga_series_view_model.dart';
import 'package:hikari/features/remote_novel/novel_series_view_model.dart';

SourceMediaRef _ref(String id) =>
    SourceMediaRef(sourceId: const SourceId('fake'), itemId: id);

void main() {
  for (final manga in [true, false]) {
    final kind = manga ? 'manga' : 'novel';
    dynamic model(
      Future<List<SourceMediaRef>> Function() load, {
      Future<SeriesReadingTarget> Function(List<SourceMediaRef>)? resolve,
    }) => manga
        ? MangaSeriesViewModel(
            () async => MangaSeriesDetails(
              metadata: MediaMetadata(title: 'Series'),
              chapterListOrder: ChapterListOrder.readingOrder,
              chapters: (await load())
                  .map((ref) => MangaChapter(title: ref.itemId, source: ref))
                  .toList(),
            ),
            series: _ref('series'),
            loadReadingTarget: resolve,
          )
        : NovelSeriesViewModel(
            () async => NovelDetails(
              metadata: MediaMetadata(title: 'Series'),
              chapterListOrder: ChapterListOrder.readingOrder,
              chapters: (await load())
                  .map((ref) => NovelChapter(title: ref.itemId, source: ref))
                  .toList(),
            ),
            series: _ref('series'),
            loadReadingTarget: resolve,
          );

    test(
      '$kind details refresh completes while continuation is pending',
      () async {
        final continuation = Completer<SeriesReadingTarget>();
        final started = Completer<void>();
        final dynamic vm = model(
          () async => [_ref('one'), _ref('two')],
          resolve: (_) {
            if (!started.isCompleted) started.complete();
            return continuation.future;
          },
        );
        addTearDown(() => vm.dispose());
        var completed = false;
        final load = (vm.load() as Future<void>).then((_) => completed = true);
        await started.future;
        await Future<void>.value();
        expect(completed, isTrue);
        expect(vm.state.refreshing, isFalse);
        expect(vm.primaryChapter.source, _ref('one'));
        continuation.complete(
          SeriesReadingTarget(_ref('two'), isContinuation: true),
        );
        await load;
      },
    );

    test('$kind storage failure preserves ready chapters', () async {
      final dynamic vm = model(
        () async => [_ref('one')],
        resolve: (_) async => throw StateError('storage unavailable'),
      );
      addTearDown(() => vm.dispose());
      await (vm.load() as Future<void>);
      await Future<void>.value();
      expect(vm.state.failed, isFalse);
      expect(vm.state.refreshFailed, isFalse);
      expect(vm.primaryChapter.source, _ref('one'));
    });

    test('$kind new snapshot rejects old continuation completion', () async {
      final pending = <Completer<SeriesReadingTarget>>[];
      var refs = [_ref('old')];
      final dynamic vm = model(
        () async => refs,
        resolve: (_) {
          final result = Completer<SeriesReadingTarget>();
          pending.add(result);
          return result.future;
        },
      );
      addTearDown(() => vm.dispose());
      await (vm.load() as Future<void>);
      refs = [_ref('new')];
      await (vm.load() as Future<void>);
      pending.first.complete(
        SeriesReadingTarget(_ref('old'), isContinuation: true),
      );
      await Future<void>.value();
      expect(vm.primaryChapter.source, _ref('new'));
      pending.last.completeError(StateError('storage'));
      await Future<void>.value();
      expect(vm.primaryChapter.source, _ref('new'));
      expect(vm.state.refreshFailed, isFalse);
    });

    test('$kind provider replacement rejects old pending details', () async {
      final oldDetails = Completer<List<SourceMediaRef>>();
      final dynamic vm = model(() => oldDetails.future);
      addTearDown(() => vm.dispose());
      final oldLoad = vm.load() as Future<void>;
      vm.setSearchQuery('new');
      vm.toggleSourceOrder();
      final replacementReady = Completer<void>();
      Future<SeriesReadingTarget> resolve(List<SourceMediaRef> refs) async {
        replacementReady.complete();
        return SeriesReadingTarget(refs.first);
      }

      if (manga) {
        (vm as MangaSeriesViewModel).updateDependencies(
          () async => MangaSeriesDetails(
            metadata: MediaMetadata(title: 'New'),
            chapters: [MangaChapter(title: 'new', source: _ref('new'))],
            chapterListOrder: ChapterListOrder.readingOrder,
          ),
          resolve,
          ownerChanged: true,
        );
      } else {
        (vm as NovelSeriesViewModel).updateDependencies(
          () async => NovelDetails(
            metadata: MediaMetadata(title: 'New'),
            chapters: [NovelChapter(title: 'new', source: _ref('new'))],
            chapterListOrder: ChapterListOrder.readingOrder,
          ),
          resolve,
          ownerChanged: true,
        );
      }
      await replacementReady.future;
      oldDetails.complete([_ref('old')]);
      await oldLoad;
      expect(vm.primaryChapter.source, _ref('new'));
      expect(vm.searchQuery, 'new');
      expect(vm.reverseSourceOrder, isTrue);
      expect(vm.state.refreshing, isFalse);
    });

    test('$kind replacing provider clears stale details on failure', () async {
      final dynamic vm = model(() async => [_ref('old')]);
      addTearDown(() => vm.dispose());
      await (vm.load() as Future<void>);
      expect(vm.state.details, isNotNull);
      expect(vm.primaryChapter.source, _ref('old'));
      vm.setSearchQuery('keep');
      vm.toggleSourceOrder();

      final replacement = Completer<void>();
      if (manga) {
        (vm as MangaSeriesViewModel).updateDependencies(
          () async {
            await replacement.future;
            throw StateError('new provider unavailable');
          },
          null,
          ownerChanged: true,
        );
      } else {
        (vm as NovelSeriesViewModel).updateDependencies(
          () async {
            await replacement.future;
            throw StateError('new provider unavailable');
          },
          null,
          ownerChanged: true,
        );
      }

      // The old provider snapshot must disappear *before* the new load ends.
      expect(vm.state.initialLoading, isTrue);
      expect(vm.state.details, isNull);
      expect(vm.primaryChapter, isNull);
      expect(vm.readingSequence, isEmpty);
      expect(vm.displayChapters, isEmpty);
      expect(vm.searchQuery, 'keep');
      expect(vm.reverseSourceOrder, isTrue);

      replacement.complete();
      await Future<void>.delayed(Duration.zero);
      expect(vm.state.failed, isTrue);
      expect(vm.state.refreshFailed, isFalse);
      expect(vm.state.details, isNull);
      expect(vm.primaryChapter, isNull);
    });

    test('$kind latest overlapping details load wins', () async {
      final pending = <Completer<List<SourceMediaRef>>>[];
      final dynamic vm = model(() {
        final next = Completer<List<SourceMediaRef>>();
        pending.add(next);
        return next.future;
      });
      addTearDown(() => vm.dispose());
      final first = vm.load() as Future<void>;
      final second = vm.load() as Future<void>;
      pending.last.complete([_ref('new')]);
      await second;
      pending.first.complete([_ref('old')]);
      await first;
      expect(vm.primaryChapter.source, _ref('new'));
    });
    test('$kind disposal ignores pending details and continuation', () async {
      final details = Completer<List<SourceMediaRef>>();
      final dynamic loading = model(() => details.future);
      final load = loading.load() as Future<void>;
      loading.dispose();
      details.complete([_ref('one')]);
      await load;
      expect(loading.primaryChapter, isNull);
      final continuation = Completer<SeriesReadingTarget>();
      final dynamic resolving = model(
        () async => [_ref('one')],
        resolve: (_) => continuation.future,
      );
      final resume = resolving.load() as Future<void>;
      await Future<void>.delayed(Duration.zero);
      resolving.dispose();
      continuation.complete(
        SeriesReadingTarget(_ref('one'), isContinuation: true),
      );
      await resume;
      expect(resolving.primaryChapter.source, _ref('one'));
      expect(resolving.isContinuation, isFalse);
    });
    test(
      '$kind rejects invalid whole sequence and keeps last valid snapshot',
      () async {
        var refs = [_ref('one')];
        final dynamic vm = model(() async => refs);
        addTearDown(() => vm.dispose());
        await (vm.load() as Future<void>);
        final Object snapshot = vm.readingSequence as Object;
        for (final invalid in [
          [_ref('one'), _ref('one')],
          [
            _ref('one'),
            const SourceMediaRef(sourceId: SourceId('foreign'), itemId: 'two'),
          ],
          [_ref('one'), _ref('')],
          [_ref('one'), _ref('series')],
        ]) {
          refs = invalid;
          await (vm.load() as Future<void>);
          expect(vm.state.refreshFailed, isTrue);
          expect(identical(snapshot, vm.readingSequence), isTrue);
          expect(vm.primaryChapter.source, _ref('one'));
        }
      },
    );
    test(
      '$kind newer continuation result wins without changing sequence',
      () async {
        final pending = <Completer<SeriesReadingTarget>>[];
        final dynamic vm = model(
          () async => [_ref('one'), _ref('two')],
          resolve: (_) {
            final next = Completer<SeriesReadingTarget>();
            pending.add(next);
            return next.future;
          },
        );
        addTearDown(() => vm.dispose());
        final load = vm.load() as Future<void>;
        await Future<void>.delayed(Duration.zero);
        final refresh = vm.refreshReadingTarget() as Future<void>;
        pending.last.complete(
          SeriesReadingTarget(_ref('two'), isContinuation: true),
        );
        await refresh;
        pending.first.complete(SeriesReadingTarget(_ref('one')));
        await load;
        expect(vm.primaryChapter.source, _ref('two'));
        expect(vm.isContinuation, isTrue);
      },
    );
  }
}
