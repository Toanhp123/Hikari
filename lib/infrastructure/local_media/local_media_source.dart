import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/publication.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/local_media/bounded_archive.dart';
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

NovelChapterContent _opaqueEpubContent(
  LocalArchiveRef publication,
  NovelChapterContent content,
) => NovelChapterContent(
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
        MangaPageSource,
        NovelTextSource,
        PublicationSource,
        MediaSourceAvailability {
  @override
  SourceId get id => SourceId.local;
  @override
  String get name => 'Local media';
  static const _channel = MethodChannel('hikari/local_media');
  late final _copies = ArchiveCopyPool(_materialize, _deleteMaterialized);

  Future<void> retainArchive(SourceMediaRef ref) async {
    final archive = _archiveRef(ref);
    if (archive != null) _copies.retain(archive.locator);
  }

  Future<void> releaseArchive(SourceMediaRef ref) async {
    final archive = _archiveRef(ref);
    if (archive != null) await _copies.release(archive.locator);
  }

  Future<void> close() => _copies.close();

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

  Future<List<Media>?> scanSelectedRoot() async {
    final root = await _root('selectedTree');
    if (root == null) return null;
    return _scan(root);
  }

  Future<bool> chooseRoot() async => await _root('pickTree') != null;

  Future<LocalEntry?> _root(String method) async {
    final selected = await _channel.invokeMapMethod<String, Object?>(method);
    if (selected == null) return null;
    return LocalEntry(
      id: selected['id']! as String,
      parentId: null,
      name: selected['name']! as String,
      isDirectory: true,
    );
  }

  Future<List<Media>> _scan(LocalEntry root) async {
    final entries = <LocalEntry>[root];
    final pending = <String>[root.id];
    final visited = <String>{};
    while (pending.isNotEmpty) {
      final id = pending.removeLast();
      if (!visited.add(id)) continue;
      final children = await _children(id);
      entries.addAll(children);
      if (entries.length > 50000) {
        throw StateError(
          'Folder exceeds 50,000 entries. Choose a smaller folder.',
        );
      }
      pending.addAll(children.where((e) => e.isDirectory).map((e) => e.id));
    }
    return compute(classifyLocalEntries, entries);
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
  Future<NovelChapterContent> readSection(
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

String _decodeText(Uint8List bytes) => utf8.decode(bytes, allowMalformed: true);
