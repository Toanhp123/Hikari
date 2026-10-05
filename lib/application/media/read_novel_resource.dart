import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';

final class ReadNovelResource {
  static const cacheNamespace = 'novel-resource-v1';

  ReadNovelResource(this._cache);

  final ByteCache _cache;
  final Map<SourceMediaRef, _ResourceFlight> _inFlight = {};

  Future<Uint8List> execute(
    NovelChapterSource source,
    SourceMediaRef resource,
  ) {
    if (resource.sourceId != source.id) {
      return Future.error(
        StateError('Novel source received foreign resource reference.'),
      );
    }
    final current = _inFlight[resource];
    if (current != null) return current.future;
    return _start(source, resource, reload: false);
  }

  Future<Uint8List> reload(NovelChapterSource source, SourceMediaRef resource) {
    if (resource.sourceId != source.id) {
      return Future.error(
        StateError('Novel source received foreign resource reference.'),
      );
    }
    final current = _inFlight[resource];
    if (current?.reload == true) return current!.future;
    return _start(source, resource, reload: true, predecessor: current?.future);
  }

  Future<Uint8List> _start(
    NovelChapterSource source,
    SourceMediaRef resource, {
    required bool reload,
    Future<Uint8List>? predecessor,
  }) {
    final flight = _ResourceFlight(reload: reload);
    _inFlight[resource] = flight;
    flight.future =
        _load(
          source,
          resource,
          reload: reload,
          predecessor: predecessor,
        ).whenComplete(() {
          if (identical(_inFlight[resource], flight)) {
            _inFlight.remove(resource);
          }
        });
    return flight.future;
  }

  Future<Uint8List> _load(
    NovelChapterSource source,
    SourceMediaRef resource, {
    required bool reload,
    Future<Uint8List>? predecessor,
  }) async {
    if (predecessor != null) {
      try {
        await predecessor;
      } catch (_) {
        // Fresh reads run even when prior source read failed.
      }
    }
    if (!reload) {
      try {
        final cached = await _cache.read(cacheNamespace, cacheKey(resource));
        if (cached != null && cached.isNotEmpty) return cached;
      } catch (_) {
        // Cache failures never prevent source reads.
      }
    }
    final bytes = await source.readResource(resource);
    if (bytes.isNotEmpty) {
      try {
        await _cache.write(cacheNamespace, cacheKey(resource), bytes);
      } catch (_) {
        // Cache failures never hide successfully loaded source bytes.
      }
    }
    return bytes;
  }

  static String cacheKey(SourceMediaRef resource) =>
      jsonEncode([resource.sourceId.value, resource.itemId]);
}

final class _ResourceFlight {
  _ResourceFlight({required this.reload});
  final bool reload;
  late final Future<Uint8List> future;
}
