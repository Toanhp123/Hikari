import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_button_view_model.dart';

void main() {
  test('LibraryButtonViewModel owns membership load and toggle', () async {
    const media = Media(
      title: 'Saved manga',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'saved'),
    );
    final repository = _LibraryRepository(saved: true);
    final model = LibraryButtonViewModel(repository, media);
    addTearDown(model.dispose);

    await Future<void>.delayed(Duration.zero);
    expect(model.state.saved, isTrue);

    expect(await model.toggle(), isTrue);
    expect(model.state.saved, isFalse);
    expect(repository.removed, media.source);
  });
}

final class _LibraryRepository implements LibraryRepository {
  _LibraryRepository({required this.saved});

  bool saved;
  SourceMediaRef? removed;

  @override
  Future<bool> contains(SourceMediaRef media) async => saved;

  @override
  Future<List<LibraryEntry>> loadAll() async => const [];

  @override
  Future<void> remove(SourceMediaRef media) async {
    removed = media;
    saved = false;
  }

  @override
  Future<void> upsert(LibraryEntry entry) async {
    saved = true;
  }
}
