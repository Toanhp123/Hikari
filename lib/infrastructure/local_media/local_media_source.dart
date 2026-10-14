import 'dart:async';
import 'dart:convert';
import 'dart:developer' as developer;
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:hikari/domain/media/local_media_scan_result.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/publication.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/local_media/bounded_archive.dart';
import 'package:hikari/infrastructure/local_media/local_artwork_ref.dart';
import 'package:hikari/infrastructure/local_media/archive_copy_pool.dart';
import 'package:hikari/infrastructure/local_media/classifier.dart';
import 'package:hikari/infrastructure/local_media/comic_info.dart';
import 'package:hikari/infrastructure/reading/epub_reader.dart';

const _materializeLimit = 1024 * 1024 * 1024;
const _pageReadLimit = 32 * 1024 * 1024;
const _textReadLimit = 4 * 1024 * 1024;

String _opaqueEpubResourceId(LocalArchiveRef publication, String resource) =>
    LocalArchiveRef(
      locator: publication.locator,
      entry: resource,
      format: 'epub',
    ).encode();

RichReadingContent _opaqueEpubContent(
  LocalArchiveRef publication,
  RichReadingContent content,
) => RichReadingContent(
  html: content.html,
  resources: {
    for (final entry in content.resources.entries)
      entry.key: SourceMediaRef(
        sourceId: SourceId.local,
        itemId: _opaqueEpubResourceId(publication, entry.value.itemId),
      ),
  },
);

class LocalMediaSource
    implements
        DirectVideoSource,
        ArtworkSource,
        MangaPageSource,
        NovelTextSource,
        PublicationSource,
        MediaSourceAvailability,
        MediaOpenLeaseSource {
  @override
  SourceId get id => SourceId.local;
  @override
  String get name => 'Local media';
  static const _channel = MethodChannel('hikari/local_media');
  late final _copies = ArchiveCopyPool(
    _materialize,
    _deleteMaterialized,
    exists: (path) => File(path).exists(),
  );

  @override
  MediaOpenLease? acquireOpenLease(SourceMediaRef ref) {
    final archive = _archiveRef(ref);
    if (archive == null) return null;
    _copies.retain(archive.locator);
    return _LocalArchiveLease(_copies, archive.locator);
  }

  String? _rootId;
  LocalMediaScanResult? _completed;
  Future<LocalMediaScanResult>? _flight;
  Future<bool>? _picker;
  int _generation = 0;
  String? _token;
  bool _closed = false;
  static int _nextToken = 0;
  static const _trace = bool.fromEnvironment('HIKARI_LOCAL_SCAN_TRACE');

  Future<void> _cancelScan() async {
    _generation++;
    _flight = null;
    final token = _token;
    _token = null;
    if (token != null) {
      try {
        await _channel.invokeMethod<void>('cancelScan', token);
      } on PlatformException {
        // Dart generation still prevents obsolete publication if native closed.
      } on MissingPluginException {
        // Engine teardown can detach the channel before source cleanup.
      }
    }
  }

  Future<void> close() async {
    if (_closed) return _copies.close();
    _closed = true;
    _completed = null;
    try {
      await _cancelScan();
    } finally {
      await _copies.close();
    }
  }

  LocalArchiveRef? _archiveRef(SourceMediaRef ref) {
    if (ref.sourceId != id) throw ArgumentError('Wrong source.');
    final archive = LocalArchiveRef.tryDecode(ref.itemId);
    if (archive == null && ref.itemId.startsWith('hikari-')) {
      throw const FormatException('Invalid local archive reference.');
    }
    return archive;
  }

  @override
  bool get isAvailable =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  @override
  String playbackLocator(SourceMediaRef media) {
    if (media.sourceId != id) throw ArgumentError('Wrong source.');
    return media.itemId;
  }

  /// Search revalidates the grant/root, then joins or reuses session discovery.
  Future<List<Media>?> scanSelectedRoot() async =>
      (await _selectedSnapshot(fresh: false))?.media;

  /// Explicit refresh enumerates again, unless same-root work is in flight.
  Future<LocalMediaScanResult?> scanSelectedRootSnapshot() =>
      _selectedSnapshot(fresh: true);

  void _checkGeneration(int generation) {
    if (_closed || generation != _generation) {
      throw const LocalMediaScanCancelledException();
    }
  }

  Future<LocalMediaScanResult?> _selectedSnapshot({required bool fresh}) async {
    while (_picker != null) {
      await _picker;
    }
    var generation = _generation;
    _checkGeneration(generation);
    try {
      final root = await _root('selectedTree');
      _checkGeneration(generation);
      if (root == null || root.id != _rootId) {
        final hadRoot = _rootId != null;
        _completed = null;
        _rootId = root?.id;
        if (hadRoot || _token != null) {
          final cancelled = _cancelScan();
          generation = _generation;
          await cancelled;
          _checkGeneration(generation);
        }
      }
      if (root == null) return null;
      final flight = _flight;
      if (flight != null) return await flight;
      final cached = _completed;
      if (!fresh && cached != null) {
        if (_trace) {
          developer.Timeline.instantSync(
            'LocalScan',
            arguments: {'reuse': 1, 'cancelled': 0, 'failed': 0},
          );
        }
        return LocalMediaScanResult(
          rootName: root.name,
          media: cached.media,
          artwork: cached.artwork,
        );
      }
      final token = '${++_nextToken}';
      _token = token;
      late final Future<LocalMediaScanResult> future;
      future = _scan(root, generation, token)
          .then((result) {
            _checkGeneration(generation);
            _completed = result;
            return result;
          })
          .catchError((Object error, StackTrace stack) {
            _checkGeneration(generation);
            if (error is PlatformException) {
              if (error.code == 'access') {
                _completed = null;
                throw const LocalMediaAccessException();
              }
              if (error.code == 'cancelled' || error.code == 'closed') {
                throw const LocalMediaScanCancelledException();
              }
            }
            Error.throwWithStackTrace(error, stack);
          })
          .whenComplete(() {
            if (identical(_flight, future)) {
              _flight = null;
              _token = null;
            }
          });
      _flight = future;
      return await future;
    } on PlatformException catch (error) {
      _checkGeneration(generation);
      if (error.code == 'cancelled' || error.code == 'closed') {
        throw const LocalMediaScanCancelledException();
      }
      if (error.code == 'access') {
        _completed = null;
        await _cancelScan();
        throw const LocalMediaAccessException();
      }
      rethrow;
    }
  }

  @override
  Future<Uint8List> readArtwork(SourceMediaRef artwork) {
    if (artwork.sourceId != id) throw ArgumentError('Wrong source.');
    final imageDocument = LocalArtworkRef.tryDecode(artwork.itemId);
    if (imageDocument == null) return Future.value(Uint8List(0));
    // The preview prefix is not proof of provenance; ContentResolver enforces
    // access to this URI just as it does for other SAF reads.
    return _read(
      SourceMediaRef(sourceId: id, itemId: imageDocument),
      8 * 1024 * 1024,
    );
  }

  Future<bool> chooseRoot() {
    if (_closed) return Future.error(const LocalMediaScanCancelledException());
    return _picker ??= _chooseRoot().whenComplete(() => _picker = null);
  }

  Future<bool> _chooseRoot() async {
    // Cancel before native picker can release the previous persisted grant.
    await _cancelScan();
    final root = await _root('pickTree');
    if (_closed) throw const LocalMediaScanCancelledException();
    if (root == null) return false;
    if (root.id != _rootId) _completed = null;
    _rootId = root.id;
    return true;
  }

  /// Read only the saved root's display name; do not enumerate its contents.
  /// The Android adapter verifies the persisted grant when resolving the root.
  Future<String?> selectedRootName() async =>
      (await _root('selectedTree'))?.name;

  Future<LocalEntry?> _root(String method) async {
    final selected = await _channel.invokeMapMethod<String, Object?>(method);
    if (selected == null) return null;
    final id = selected['id'];
    if (id is! String || id.isEmpty) {
      throw StateError('Invalid document identifier.');
    }
    return LocalEntry(
      id: id,
      parentId: null,
      name: selected['name']! as String,
      isDirectory: true,
    );
  }

  Future<LocalMediaScanResult> _scan(
    LocalEntry root,
    int generation,
    String token,
  ) async {
    final total = Stopwatch()..start();
    final traversal = Stopwatch()..start();
    final classification = Stopwatch();
    var queries = 0;
    var received = 1; // Synthetic root counts toward the total bound.
    var nativeQueue = 0;
    var nativeQuery = 0;
    var cancelled = false;
    var failed = false;
    final entries = <LocalEntry>[root];
    final pending = <String>[root.id];
    final visited = <String>{};
    try {
      while (pending.isNotEmpty) {
        _checkGeneration(generation);
        final id = pending.removeLast();
        if (!visited.add(id)) continue;
        queries++;
        final response = await _channel.invokeMapMethod<String, Object?>(
          'scanChildren',
          {'id': id, 'token': token, 'remaining': 50000 - received},
        );
        _checkGeneration(generation);
        final rows = response?['rows'];
        if (rows is! List) throw StateError('Folder could not be read.');
        nativeQueue += (response?['queueMicros'] as num?)?.toInt() ?? 0;
        nativeQuery += (response?['queryMicros'] as num?)?.toInt() ?? 0;
        received += rows.length;
        if (received > 50000) {
          throw StateError(
            'Folder exceeds 50,000 entries. Choose a smaller folder.',
          );
        }
        final seen = <String>{};
        for (final row in rows) {
          if (row is! Map ||
              row['id'] is! String ||
              (row['id'] as String).isEmpty ||
              row['name'] is! String ||
              row['isDirectory'] is! bool) {
            throw StateError('Invalid directory entry.');
          }
          final child = LocalEntry(
            id: row['id'] as String,
            parentId: id,
            name: row['name'] as String,
            isDirectory: row['isDirectory'] as bool,
          );
          if (!seen.add(child.id)) continue;
          // Files may legitimately have multiple parents. Keep each parent edge.
          if (child.isDirectory) {
            if (visited.contains(child.id)) continue;
            pending.add(child.id);
          }
          entries.add(child);
        }
        _checkGeneration(generation);
      }
      traversal.stop();
      classification.start();
      _checkGeneration(generation);
      final classified = await compute(classifyLocalEntries, entries);
      _checkGeneration(generation);
      final identities = <SourceMediaRef>{};
      final media = classified
          .where((item) => identities.add(item.source))
          .toList();
      final firstPage = <String, LocalEntry>{};
      for (final entry in entries.where(isPage)) {
        final parent = entry.parentId;
        if (parent == null) continue;
        final current = firstPage[parent];
        if (current == null || _compareArtwork(entry, current) < 0) {
          firstPage[parent] = entry;
        }
      }
      final artwork = <SourceMediaRef, SourceMediaRef>{};
      for (final item in media.where((item) => item.type == MediaType.manga)) {
        final page = firstPage[item.source.itemId];
        if (page == null) continue;
        final preview = LocalArtworkRef.tryEncode(page.id);
        if (preview != null) {
          artwork[item.source] = SourceMediaRef(sourceId: id, itemId: preview);
        }
      }
      classification.stop();
      _checkGeneration(generation);
      return LocalMediaScanResult(
        rootName: root.name,
        media: media,
        artwork: artwork,
      );
    } catch (error) {
      failed = true;
      cancelled =
          error is LocalMediaScanCancelledException ||
          (error is PlatformException &&
              (error.code == 'cancelled' || error.code == 'closed'));
      rethrow;
    } finally {
      if (_trace) {
        developer.Timeline.instantSync(
          'LocalScan',
          arguments: {
            'directories': visited.length,
            'entries': received,
            'queries': queries,
            'totalMicros': total.elapsedMicroseconds,
            'traversalMicros': traversal.elapsedMicroseconds,
            'classificationMicros': classification.elapsedMicroseconds,
            'nativeQueueMicros': nativeQueue,
            'nativeQueryMicros': nativeQuery,
            'reuse': 0,
            'cancelled': cancelled ? 1 : 0,
            'failed': failed && !cancelled ? 1 : 0,
          },
        );
      }
    }
  }

  static int _compareArtwork(LocalEntry a, LocalEntry b) {
    final aCover = a.name.toLowerCase().startsWith('cover.');
    final bCover = b.name.toLowerCase().startsWith('cover.');
    if (aCover != bCover) return aCover ? -1 : 1;
    final order = compareLocalNames(a.name, b.name);
    return order != 0 ? order : a.id.compareTo(b.id);
  }

  Future<List<LocalEntry>> _children(String id) async {
    final rows = await _channel.invokeListMethod<Object?>('children', id);
    if (rows == null) throw StateError('Folder could not be read.');
    return rows.map((row) {
      final map = row! as Map<Object?, Object?>;
      return LocalEntry(
        id: map['id']! as String,
        parentId: id,
        name: map['name']! as String,
        isDirectory: map['isDirectory']! as bool,
      );
    }).toList();
  }

  @override
  bool canOpenPublication(SourceMediaRef ref) {
    final archive = LocalArchiveRef.tryDecode(ref.itemId);
    return ref.sourceId == id &&
        archive?.isEpub == true &&
        archive?.entry == null;
  }

  @override
  Future<Publication> loadPublication(SourceMediaRef ref) async {
    final archive = _requireArchive(ref, 'epub');
    if (archive.entry != null) {
      throw const FormatException('EPUB publication reference expected.');
    }
    return _copies.read(archive.locator, (materialized) async {
      final epub = EpubReader.open(materialized);
      try {
        return await epub.loadPublication();
      } finally {
        epub.close();
      }
    });
  }

  @override
  Future<RichReadingContent> readSection(
    SourceMediaRef ref,
    String resource,
  ) async {
    final archive = _requireArchive(ref, 'epub');
    if (archive.entry != null) {
      throw const FormatException('EPUB publication reference expected.');
    }
    return _copies.read(archive.locator, (materialized) async {
      final epub = EpubReader.open(materialized);
      try {
        final content = await epub.readSection(resource);
        return _opaqueEpubContent(archive, content);
      } finally {
        epub.close();
      }
    });
  }

  @override
  Future<Uint8List> readResource(SourceMediaRef ref) async {
    final archive = _requireArchive(ref, 'epub');
    final resource = archive.entry;
    if (resource == null) {
      throw const FormatException('EPUB resource is missing.');
    }
    return _copies.read(archive.locator, (materialized) async {
      final epub = EpubReader.open(materialized);
      try {
        return await epub.readResource(
          SourceMediaRef(sourceId: SourceId.local, itemId: resource),
        );
      } finally {
        epub.close();
      }
    });
  }

  LocalArchiveRef _requireArchive(SourceMediaRef ref, String format) {
    if (ref.sourceId != id) throw ArgumentError('Wrong source.');
    final archive = _archiveRef(ref);
    if (archive == null || archive.format != format) {
      throw const FormatException('Unsupported local publication reference.');
    }
    return archive;
  }

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef ref) async {
    final archive = _archiveRef(ref);
    if (archive?.isEpub == true) {
      throw const FormatException('EPUB does not expose manga pages.');
    }
    if (archive == null || !archive.isCbz) {
      return _pagesFolder(ref);
    }
    if (archive.entry != null) {
      throw const FormatException('CBZ publication reference expected.');
    }
    return _pagesArchive(archive);
  }

  Future<List<SourceMediaRef>> _pagesFolder(SourceMediaRef ref) async {
    final entries = (await _children(ref.itemId)).where(isPage).toList()
      ..sort((a, b) {
        final order = compareLocalNames(a.name, b.name);
        return order != 0 ? order : a.id.compareTo(b.id);
      });
    return entries
        .map((e) => SourceMediaRef(sourceId: SourceId.local, itemId: e.id))
        .toList();
  }

  Future<List<SourceMediaRef>> _pagesArchive(LocalArchiveRef archive) async {
    return _copies.read(archive.locator, (materialized) async {
      final zip = BoundedArchive.open(materialized);
      try {
        final metadata = _readComicInfo(zip);
        final names = zip.names.where((name) {
          final extension = name.contains('.')
              ? name.split('.').last.toLowerCase()
              : '';
          return const {'jpg', 'jpeg', 'png', 'webp'}.contains(extension);
        }).toList();
        if (metadata?.pageOrder.isNotEmpty == true) {
          final ordered = <String>[];
          for (final page in metadata!.pageOrder) {
            if (page.image < names.length) ordered.add(names[page.image]);
          }
          final remaining = names.where((name) => !ordered.contains(name));
          names
            ..clear()
            ..addAll([...ordered, ...remaining]);
        } else {
          names.sort(compareLocalNames);
        }
        return names
            .map(
              (name) => SourceMediaRef(
                sourceId: SourceId.local,
                itemId: LocalArchiveRef(
                  locator: archive.locator,
                  entry: name,
                ).encode(),
              ),
            )
            .toList();
      } finally {
        zip.close();
      }
    });
  }

  @override
  Future<Uint8List> readPage(SourceMediaRef ref) async {
    final archive = _archiveRef(ref);
    if (archive?.isEpub == true) {
      throw const FormatException('EPUB does not expose manga pages.');
    }
    if (archive == null) return _read(ref, _pageReadLimit);
    if (archive.entry == null) {
      throw const FormatException('CBZ page entry is missing.');
    }
    return _copies.read(archive.locator, (materialized) async {
      final zip = BoundedArchive.open(materialized);
      try {
        return zip.readEntry(archive.entry!);
      } finally {
        zip.close();
      }
    });
  }

  @override
  Future<String> readText(SourceMediaRef ref) async {
    final bytes = await _read(ref, _textReadLimit);
    return compute(_decodeText, bytes);
  }

  Future<String> _materialize(String locator) async {
    final result = await _channel.invokeMethod<Map<Object?, Object?>>(
      'materialize',
      {'id': locator, 'limit': _materializeLimit},
    );
    final path = result?['path'] as String?;
    if (path == null || path.isEmpty) {
      throw StateError('Content could not be materialized.');
    }
    return path;
  }

  ComicInfo? _readComicInfo(BoundedArchive zip) {
    final name = zip.names.firstWhere(
      (name) =>
          name.toLowerCase() == 'comicinfo.xml' ||
          name.toLowerCase().endsWith('/comicinfo.xml'),
      orElse: () => '',
    );
    if (name.isEmpty) return null;
    try {
      return ComicInfo.parse(
        utf8.decode(zip.readEntry(name, maxBytes: 4 * 1024 * 1024)),
      );
    } catch (_) {
      return null;
    }
  }

  Future<void> _deleteMaterialized(String path) async {
    final deleted = await _channel.invokeMethod<bool>('deleteMaterialized', {
      'path': path,
    });
    if (deleted != true) {
      throw StateError('Materialized archive could not be deleted.');
    }
  }

  Future<Uint8List> _read(SourceMediaRef ref, int limit) async {
    if (ref.sourceId != id) throw ArgumentError('Wrong source.');
    final bytes = await _channel.invokeMethod<Uint8List>('read', {
      'id': ref.itemId,
      'limit': limit,
    });
    if (bytes == null) throw StateError('Content could not be read.');
    return bytes;
  }
}

final class _LocalArchiveLease implements MediaOpenLease {
  _LocalArchiveLease(this._copies, this._locator);

  ArchiveCopyPool? _copies;
  final String _locator;

  @override
  Future<void> release() async {
    final copies = _copies;
    if (copies == null) return;
    _copies = null;
    await copies.release(_locator);
  }
}

String _decodeText(Uint8List bytes) => utf8.decode(bytes, allowMalformed: true);
