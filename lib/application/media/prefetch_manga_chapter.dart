import 'package:hikari/application/media/read_manga_page.dart';
import 'package:hikari/application/media/read_manga_page_list.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';

/// Warms one manga chapter selected by the reader's lookahead policy.
///
/// One-slot session handoff avoids duplicate speculative requests. The
/// optional page-list store persists remote results across app restarts.
final class PrefetchMangaChapter {
  PrefetchMangaChapter(this._sources, this._readMangaPage, [this._pageList]);

  static const _pageWarmCount = 2;

  final SourceRegistry _sources;
  final ReadMangaPage _readMangaPage;
  final ReadMangaPageList? _pageList;
  Future<void> _tail = Future<void>.value();
  _PreparedMangaChapter? _prepared;
  int _generation = 0;

  Future<void> execute(MangaChapter chapter) {
    if (chapter.source.itemId.isEmpty) return Future<void>.value();

    final prepared = _prepare(chapter);
    if (prepared == null || prepared.warmAttempted) {
      return Future<void>.value();
    }

    final generation = ++_generation;
    return _tail = _tail.then((_) => _warm(prepared, generation));
  }

  _PreparedMangaChapter? _prepare(MangaChapter chapter) {
    final current = _prepared;
    if (current?.chapter == chapter.source) return current;

    try {
      final source = _sources.requireCapability<MangaPageSource>(
        chapter.source.sourceId,
      );
      if (source is MediaSourceAvailability &&
          !(source as MediaSourceAvailability).isAvailable) {
        return null;
      }
      final prepared = _PreparedMangaChapter(
        chapter: chapter.source,
        source: source,
        pages: _loadSpeculativePages(source, chapter.source),
      );
      _prepared = prepared;
      return prepared;
    } catch (_) {
      return null;
    }
  }

  Future<List<SourceMediaRef>?> _loadSpeculativePages(
    MangaPageSource source,
    SourceMediaRef chapter,
  ) async {
    try {
      final pages =
          await (_pageList?.execute(source, chapter) ?? source.pages(chapter));
      if (pages.any((page) => page.sourceId != source.id)) return null;
      return List.unmodifiable(pages);
    } catch (_) {
      return null;
    }
  }

  Future<void> _warm(_PreparedMangaChapter prepared, int generation) async {
    if (generation != _generation || !identical(_prepared, prepared)) return;
    final pages = await prepared.pages;
    if (generation != _generation || !identical(_prepared, prepared)) return;
    if (pages == null || pages.isEmpty) {
      prepared.warmAttempted = true;
      return;
    }

    final limit = pages.length < _pageWarmCount ? pages.length : _pageWarmCount;
    for (var index = 0; index < limit; index++) {
      if (generation != _generation || !identical(_prepared, prepared)) return;
      try {
        await _readMangaPage.execute(prepared.source, pages[index]);
      } catch (_) {
        // Speculative page failures do not affect foreground reading.
      }
    }
    if (generation == _generation && identical(_prepared, prepared)) {
      prepared.warmAttempted = true;
    }
  }

  /// Returns a valid prepared page list once, otherwise performs the normal
  /// foreground source request. Empty or invalid speculative results are never
  /// handed off to the reader.
  Future<List<SourceMediaRef>> loadPages(
    MangaPageSource source,
    SourceMediaRef chapter,
  ) async {
    final prepared = _prepared;
    if (prepared == null || prepared.chapter != chapter) {
      return _pageList?.execute(source, chapter) ?? source.pages(chapter);
    }

    _generation++;
    _prepared = null;
    final pages = await prepared.pages;
    if (pages != null && pages.isNotEmpty) return pages;
    return _pageList?.execute(source, chapter) ?? source.pages(chapter);
  }

  /// Stops queued/speculative byte warming while preserving the prepared page
  /// list so a foreground open can still consume or join it.
  void cancelPending() {
    _generation++;
  }

  /// Drops all session-local speculative state for the previous reader target.
  void discard() {
    _generation++;
    _prepared = null;
  }
}

final class _PreparedMangaChapter {
  _PreparedMangaChapter({
    required this.chapter,
    required this.source,
    required this.pages,
  });

  final SourceMediaRef chapter;
  final MangaPageSource source;
  final Future<List<SourceMediaRef>?> pages;
  bool warmAttempted = false;
}
