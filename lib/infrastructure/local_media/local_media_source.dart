import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/infrastructure/local_media/classifier.dart';

class LocalMediaSource {
  static const _channel = MethodChannel('hikari/local_media');

  bool get supported =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

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

  Future<List<SourceMediaRef>> pages(SourceMediaRef ref) async {
    final entries = (await _children(ref.itemId)).where(isPage).toList()
      ..sort((a, b) {
        final order = compareLocalNames(a.name, b.name);
        return order != 0 ? order : a.id.compareTo(b.id);
      });
    return entries
        .map((e) => SourceMediaRef(sourceId: SourceId.local, itemId: e.id))
        .toList();
  }

  Future<Uint8List> image(SourceMediaRef ref) => _read(ref, 32 * 1024 * 1024);

  Future<String> text(SourceMediaRef ref) async {
    final bytes = await _read(ref, 4 * 1024 * 1024);
    return compute(_decodeText, bytes);
  }

  Future<Uint8List> _read(SourceMediaRef ref, int limit) async {
    final bytes = await _channel.invokeMethod<Uint8List>('read', {
      'id': ref.itemId,
      'limit': limit,
    });
    if (bytes == null) throw StateError('Content could not be read.');
    return bytes;
  }
}

String _decodeText(Uint8List bytes) => utf8.decode(bytes, allowMalformed: true);
