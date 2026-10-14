import 'dart:convert';
import 'dart:typed_data';

import 'package:hikari/application/media/foreground_read_gate.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';

/// Reconstructible chapter page references, not a durable offline download.
/// Page bytes live in ReadMangaPage's separate, independently evicted cache.
final class ReadMangaPageList {
  ReadMangaPageList(this._cache, {ForegroundReadGate? readGate})
    : _foreground = readGate ?? ForegroundReadGate();

  static const cacheNamespace = 'manga-page-list-v1';
  static const _version = 1;
  static const _maxPages = 10000;
  static const _maxBytes = 2 * 1024 * 1024;

  final ByteCache _cache;
  final ForegroundReadGate _foreground;
  final Map<SourceMediaRef, Future<List<SourceMediaRef>>> _inFlight = {};

  /// Cache-first permits reopening a previously visited remote chapter offline.
  Future<List<SourceMediaRef>> execute(
    MangaPageSource source,
    SourceMediaRef chapter,
  ) => _foreground.track(() => _execute(source, chapter));

  /// Speculative callers wait for foreground work before entering here.
  Future<List<SourceMediaRef>> prefetch(
    MangaPageSource source,
    SourceMediaRef chapter,
  ) => _execute(source, chapter);

  Future<List<SourceMediaRef>> _execute(
    MangaPageSource source,
    SourceMediaRef chapter,
  ) async {
    _checkChapter(source, chapter);
    if (source.id == SourceId.local) return _fetch(source, chapter);
    try {
      final bytes = await _cache.read(cacheNamespace, _key(chapter));
      if (bytes != null && bytes.length <= _maxBytes) {
        final pages = _decode(source, chapter, bytes);
        if (pages != null && pages.isNotEmpty) return pages;
      }
    } catch (_) {
      // Broken or unavailable cache is a miss, never an opening error.
    }
    return _load(source, chapter);
  }

  /// Gets a fresh list without publishing it to disk. The reader commits only
  /// once it has successfully loaded the new current page.
  Future<List<SourceMediaRef>> fetchFresh(
    MangaPageSource source,
    SourceMediaRef chapter,
  ) => _foreground.track(() => _fetchFresh(source, chapter));

  Future<List<SourceMediaRef>> _fetchFresh(
    MangaPageSource source,
    SourceMediaRef chapter,
  ) async {
    _checkChapter(source, chapter);
    final pending = _inFlight[chapter];
    if (pending != null) {
      try {
        await pending;
      } catch (_) {
        // A failed old read must not prevent an explicit refresh.
      }
    }
    return _fetch(source, chapter);
  }

  Future<void> persist(
    MangaPageSource source,
    SourceMediaRef chapter,
    List<SourceMediaRef> pages,
  ) async {
    _checkChapter(source, chapter);
    _validate(source, pages);
    if (source.id == SourceId.local) return;
    try {
      final bytes = Uint8List.fromList(
        utf8.encode(
          jsonEncode({
            'version': _version,
            'source': source.id.value,
            'chapter': chapter.itemId,
            'pages': [for (final page in pages) page.itemId],
          }),
        ),
      );
      if (bytes.length <= _maxBytes) {
        await _cache.write(cacheNamespace, _key(chapter), bytes);
      }
    } catch (_) {
      // Disk failure cannot invalidate a successfully fetched page list.
    }
  }

  Future<List<SourceMediaRef>> _load(
    MangaPageSource source,
    SourceMediaRef chapter,
  ) {
    final existing = _inFlight[chapter];
    if (existing != null) return existing;
    final future = _fetch(source, chapter).then((pages) async {
      await persist(source, chapter, pages);
      return pages;
    });
    _inFlight[chapter] = future;
    return future.whenComplete(() {
      if (identical(_inFlight[chapter], future)) _inFlight.remove(chapter);
    });
  }

  Future<List<SourceMediaRef>> _fetch(
    MangaPageSource source,
    SourceMediaRef chapter,
  ) async {
    final pages = await source.pages(chapter);
    _validate(source, pages);
    return List.unmodifiable(pages);
  }

  static void _checkChapter(MangaPageSource source, SourceMediaRef chapter) {
    if (chapter.sourceId != source.id || chapter.itemId.isEmpty) {
      throw StateError('Invalid manga chapter reference for this source.');
    }
  }

  static void _validate(MangaPageSource source, List<SourceMediaRef> pages) {
    if (pages.length > _maxPages ||
        pages.any(
          (page) => page.sourceId != source.id || page.itemId.isEmpty,
        )) {
      throw StateError('Manga source returned invalid page references.');
    }
  }

  static List<SourceMediaRef>? _decode(
    MangaPageSource source,
    SourceMediaRef chapter,
    Uint8List bytes,
  ) {
    try {
      final data = jsonDecode(utf8.decode(bytes));
      if (data is! Map<String, dynamic> ||
          data['version'] != _version ||
          data['source'] != source.id.value ||
          data['chapter'] != chapter.itemId) {
        return null;
      }
      final items = data['pages'];
      if (items is! List ||
          items.length > _maxPages ||
          items.any((item) => item is! String || item.isEmpty)) {
        return null;
      }
      return List.unmodifiable([
        for (final item in items)
          SourceMediaRef(sourceId: source.id, itemId: item as String),
      ]);
    } catch (_) {
      return null;
    }
  }

  static String _key(SourceMediaRef chapter) =>
      jsonEncode([chapter.sourceId.value, chapter.itemId]);
}
