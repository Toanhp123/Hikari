import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/manga.dart';

final class ReadMangaPage {
  static const cacheNamespace = 'manga-page-v1';

  ReadMangaPage(this._cache);

  final ByteCache _cache;
  final Map<SourceMediaRef, _PageFlight> _inFlight = {};

  Future<Uint8List> execute(MangaPageSource source, SourceMediaRef page) {
    if (page.sourceId != source.id) {
      return Future.error(
        StateError('Manga source returned foreign page reference.'),
      );
    }
    final current = _inFlight[page];
    if (current != null) return current.future;
    return _start(source, page, reload: false);
  }

  Future<Uint8List> reload(MangaPageSource source, SourceMediaRef page) {
    if (page.sourceId != source.id) {
      return Future.error(
        StateError('Manga source returned foreign page reference.'),
      );
    }
    final current = _inFlight[page];
    if (current?.reload == true) return current!.future;
    final predecessor = current?.future;
    return _start(source, page, reload: true, predecessor: predecessor);
  }

  Future<Uint8List> _start(
    MangaPageSource source,
    SourceMediaRef page, {
    required bool reload,
    Future<Uint8List>? predecessor,
  }) {
    final flight = _PageFlight(reload: reload);
    _inFlight[page] = flight;
    flight.future =
        _load(
          source,
          page,
          reload: reload,
          predecessor: predecessor,
        ).whenComplete(() {
          if (identical(_inFlight[page], flight)) _inFlight.remove(page);
        });
    return flight.future;
  }

  Future<Uint8List> _load(
    MangaPageSource source,
    SourceMediaRef page, {
    required bool reload,
    Future<Uint8List>? predecessor,
  }) async {
    if (predecessor != null) {
      try {
        await predecessor;
      } catch (_) {
        // Reload must run even after an earlier read failed.
      }
    }
    if (!reload) {
      try {
        final cached = await _cache.read(cacheNamespace, cacheKey(page));
        if (cached != null && cached.isNotEmpty) return cached;
      } catch (_) {
        // Cache failures never prevent source reads.
      }
    }
    final bytes = await source.readPage(page);
    if (bytes.isEmpty) return bytes;
    try {
      await _cache.write(cacheNamespace, cacheKey(page), bytes);
    } catch (_) {
      // Cache failures never hide successfully loaded source bytes.
    }
    return bytes;
  }

  static String cacheKey(SourceMediaRef page) =>
      jsonEncode([page.sourceId.value, page.itemId]);
}

final class _PageFlight {
  _PageFlight({required this.reload});

  final bool reload;
  late final Future<Uint8List> future;
}
