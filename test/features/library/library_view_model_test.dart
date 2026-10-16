import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/progress/load_continue_reading.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/library/library_view_model.dart';

void main() {
  test(
    'older progress completion cannot overwrite newer Library contents',
    () async {
      final library = _ControlledLibraryRepository();
      final progress = _ControlledProgressRepository();
      final model = LibraryViewModel(
        library,
        loadContinueReading: LoadContinueReading(progress),
      );
      addTearDown(model.dispose);

      await Future<void>.delayed(Duration.zero);
      library.pending.removeAt(0).complete([_entry('old')]);
      await Future<void>.delayed(Duration.zero);
      expect(progress.pending.length, 1);

      final reload = model.reload();
      await Future<void>.delayed(Duration.zero);
      library.pending.removeAt(0).complete([_entry('new')]);
      await reload;
      await Future<void>.delayed(Duration.zero);
      expect(progress.pending.length, 2);

      progress.pending[1].complete(
        MediaProgress(
          media: _entry('new').media.source,
          position: PagePosition(pageIndex: 2, pageCount: 10),
          completed: false,
          updatedAt: DateTime.utc(2026),
        ),
      );
      await Future<void>.delayed(Duration.zero);
      expect(model.state.continueItems.single.media.title, 'new');

      progress.pending[0].complete(
        MediaProgress(
          media: _entry('old').media.source,
          position: PagePosition(pageIndex: 3, pageCount: 10),
          completed: false,
          updatedAt: DateTime.utc(2026),
        ),
      );
      await Future<void>.delayed(Duration.zero);
      expect(model.state.continueItems.single.media.title, 'new');
    },
  );

  test('newer library reload wins over stale completion', () async {
    final repository = _ControlledLibraryRepository();
    final model = LibraryViewModel(repository);
    addTearDown(model.dispose);

    await Future<void>.delayed(Duration.zero);
    final first = repository.pending.removeAt(0);
    final secondReload = model.reload();
    await Future<void>.delayed(Duration.zero);
    final second = repository.pending.removeAt(0);

    second.complete([_entry('new')]);
    await secondReload;
    first.complete([_entry('old')]);
    await Future<void>.delayed(Duration.zero);

    expect(model.state.entries.single.media.title, 'new');
    expect(model.state.status, LibraryStatus.ready);
  });
}

LibraryEntry _entry(String title) => LibraryEntry(
  media: Media(
    title: title,
    type: MediaType.manga,
    source: SourceMediaRef(sourceId: SourceId.local, itemId: title),
  ),
  addedAt: DateTime.utc(2026),
);

final class _ControlledLibraryRepository implements LibraryRepository {
  final pending = <Completer<List<LibraryEntry>>>[];

  @override
  Future<List<LibraryEntry>> loadAll() {
    final completer = Completer<List<LibraryEntry>>();
    pending.add(completer);
    return completer.future;
  }

  @override
  Future<bool> contains(SourceMediaRef media) async => false;

  @override
  Future<void> remove(SourceMediaRef media) async {}

  @override
  Future<void> upsert(LibraryEntry entry) async {}
}

final class _ControlledProgressRepository implements ProgressRepository {
  final pending = <Completer<MediaProgress?>>[];

  @override
  Future<MediaProgress?> load(SourceMediaRef media) {
    final completer = Completer<MediaProgress?>();
    pending.add(completer);
    return completer.future;
  }

  @override
  Future<void> save(MediaProgress progress) async {}

  @override
  Future<void> delete(SourceMediaRef media) async {}
}
