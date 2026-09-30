import 'package:hikari/domain/media/media.dart';

final class LibraryEntry {
  LibraryEntry({required this.media, required DateTime addedAt})
    : addedAt = addedAt.toUtc();

  final Media media;
  final DateTime addedAt;
}

abstract interface class LibraryRepository {
  Future<void> upsert(LibraryEntry entry);
  Future<void> remove(SourceMediaRef media);
  Future<bool> contains(SourceMediaRef media);
  Future<List<LibraryEntry>> loadAll();
}

/// Optional capability for repositories that can publish live library changes.
///
/// Keeping this separate preserves the small base contract for tests or future
/// non-reactive implementations.
abstract interface class ObservableLibraryRepository
    implements LibraryRepository {
  Stream<List<LibraryEntry>> watchAll();
}
