import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_view_model.dart';

void main() {
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
