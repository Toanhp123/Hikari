import 'dart:async';

import 'package:hikari/application/media/read_manga_page.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';

final class PrefetchMangaPages {
  PrefetchMangaPages(this._readMangaPage);

  final ReadMangaPage _readMangaPage;
  Future<void> _tail = Future.value();
  int _generation = 0;

  Future<void> execute(
    MangaPageSource source,
    List<SourceMediaRef> pages,
    int displayedIndex,
  ) {
    if (displayedIndex < 0 || displayedIndex >= pages.length) {
      return Future.value();
    }
    final generation = ++_generation;
    return _tail = _tail.then((_) async {
      for (var offset = 1; offset <= 2; offset++) {
        if (generation != _generation) return;
        final index = displayedIndex + offset;
        if (index >= pages.length) return;
        try {
          await _readMangaPage.execute(source, pages[index]);
        } catch (_) {
          // Speculative read failures do not affect foreground reading.
        }
      }
    });
  }

  void cancelPending() {
    _generation++;
  }
}
