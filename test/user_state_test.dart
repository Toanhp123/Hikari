import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/library/library.dart';

void main() {
  const ref = SourceMediaRef(sourceId: SourceId.local, itemId: 'same');
  test('source references compare both opaque components', () {
    expect(
      ref,
      const SourceMediaRef(sourceId: SourceId('local'), itemId: 'same'),
    );
    final equalRef = SourceMediaRef(sourceId: SourceId.local, itemId: 'same');
    expect({ref, equalRef}, hasLength(1));
    expect(
      ref,
      isNot(const SourceMediaRef(sourceId: SourceId('other'), itemId: 'same')),
    );
    expect(ref.toString(), contains('same'));
  });
  test('positions validate runtime values including empty pages and nonfinite text', () {
    expect(
      () => VideoPosition(
        position: const Duration(seconds: -1),
        duration: Duration.zero,
      ),
      throwsArgumentError,
    );
    expect(
      () => VideoPosition(
        position: Duration.zero,
        duration: const Duration(seconds: -1),
      ),
      throwsArgumentError,
    );
    expect(PagePosition(pageIndex: 0, pageCount: 0).pageIndex, 0);
    for (final pair in [(1, 0), (-1, 2), (2, 2), (0, -1)]) {
      expect(
        () => PagePosition(pageIndex: pair.$1, pageCount: pair.$2),
        throwsArgumentError,
      );
    }
    for (final value in [double.nan, double.infinity, -0.1, 1.1]) {
      expect(() => TextPosition(progression: value), throwsArgumentError);
    }
    expect(TextPosition(progression: 1).progression, 1);
  });
  test('completion explicit and timestamps normalized UTC', () {
    final progress = MediaProgress(
      media: ref,
      position: TextPosition(progression: 1),
      completed: false,
      updatedAt: DateTime(2026),
    );
    expect(progress.completed, isFalse);
    expect(progress.updatedAt.isUtc, isTrue);
    final entry = LibraryEntry(
      media: const Media(title: 'Snapshot', type: MediaType.manga, source: ref),
      addedAt: DateTime(2026),
    );
    expect(entry.addedAt.isUtc, isTrue);
    expect(entry.media.title, 'Snapshot');
  });
}
