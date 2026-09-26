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
