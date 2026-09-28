import 'package:flutter/services.dart';

final class MihonSourceDescriptor {
  const MihonSourceDescriptor({
    required this.sourceKey,
    required this.name,
    required this.language,
    required this.packageName,
    required this.baseUrl,
  });

  final String sourceKey;
  final String name;
  final String language;
  final String packageName;
  final String baseUrl;
}

final class MihonMangaItem {
  const MihonMangaItem({required this.title, required this.url, this.memo});

  final String title;
  final String url;
  final String? memo;
}

final class MihonChapterItem {
  const MihonChapterItem({
    required this.title,
    required this.url,
    this.scanlator,
    this.chapterNumber = -1,
    this.dateUpload = 0,
    this.memo,
  });

  final String title;
  final String url;
  final String? scanlator;
  final double chapterNumber;
  final int dateUpload;
  final String? memo;
}

final class MihonPageItem {
  const MihonPageItem({
    required this.index,
    required this.url,
    this.imageUrl,
    this.uri,
  });

  final int index;
  final String url;
  final String? imageUrl;
  final String? uri;
}

abstract interface class MihonExtensionGateway {
  Future<List<MihonSourceDescriptor>> listSources();

  Future<List<MihonMangaItem>> search({
    required String sourceKey,
    required String query,
  });

  Future<List<MihonChapterItem>> chapters({
    required String sourceKey,
    required String mangaUrl,
    String? mangaTitle,
    String? mangaMemo,
  });

  Future<List<MihonPageItem>> pages({
    required String sourceKey,
    required String chapterUrl,
    String? chapterTitle,
    double? chapterNumber,
    String? chapterScanlator,
    int? chapterDateUpload,
    String? chapterMemo,
  });

  Future<Uint8List> readPage({
    required String sourceKey,
    required MihonPageItem page,
  });
}

final class MethodChannelMihonExtensionGateway
    implements MihonExtensionGateway {
  const MethodChannelMihonExtensionGateway();

  static const _channel = MethodChannel('hikari/mihon_extensions');

  @override
  Future<List<MihonSourceDescriptor>> listSources() async {
    try {
      final rows = await _channel.invokeListMethod<Object?>('listSources');
      if (rows == null) return const [];
      return rows
          .map((row) {
            final map = _map(row);
            return MihonSourceDescriptor(
              sourceKey: _string(map, 'sourceKey'),
              name: _string(map, 'name'),
              language: _string(map, 'language'),
              packageName: _string(map, 'packageName'),
              baseUrl: _string(map, 'baseUrl'),
            );
          })
          .toList(growable: false);
    } on MissingPluginException {
      return const [];
    }
  }

  @override
  Future<List<MihonMangaItem>> search({
    required String sourceKey,
    required String query,
  }) async {
    final rows = await _list('search', {
      'sourceKey': sourceKey,
      'query': query,
    });
    return rows
        .map((row) {
          final map = _map(row);
          return MihonMangaItem(
            title: _string(map, 'title'),
            url: _string(map, 'url'),
            memo: _optionalString(map, 'memo'),
          );
        })
        .toList(growable: false);
  }

  @override
  Future<List<MihonChapterItem>> chapters({
    required String sourceKey,
    required String mangaUrl,
    String? mangaTitle,
    String? mangaMemo,
  }) async {
    final rows = await _list('chapters', {
      'sourceKey': sourceKey,
      'mangaUrl': mangaUrl,
      'mangaTitle': mangaTitle,
      'mangaMemo': mangaMemo,
    });
    return rows
        .map((row) {
          final map = _map(row);
          return MihonChapterItem(
            title: _string(map, 'title'),
            url: _string(map, 'url'),
            scanlator: _optionalString(map, 'scanlator'),
            chapterNumber: _double(map, 'chapterNumber'),
            dateUpload: _int(map, 'dateUpload'),
            memo: _optionalString(map, 'memo'),
          );
        })
        .toList(growable: false);
  }

  @override
  Future<List<MihonPageItem>> pages({
    required String sourceKey,
    required String chapterUrl,
    String? chapterTitle,
    double? chapterNumber,
    String? chapterScanlator,
    int? chapterDateUpload,
    String? chapterMemo,
  }) async {
    final rows = await _list('pages', {
      'sourceKey': sourceKey,
      'chapterUrl': chapterUrl,
      'chapterTitle': chapterTitle,
      'chapterNumber': chapterNumber,
      'chapterScanlator': chapterScanlator,
      'chapterDateUpload': chapterDateUpload,
      'chapterMemo': chapterMemo,
    });
    return rows
        .map((row) {
          final map = _map(row);
          return MihonPageItem(
            index: _int(map, 'index'),
            url: _string(map, 'url'),
            imageUrl: _optionalString(map, 'imageUrl'),
            uri: _optionalString(map, 'uri'),
          );
        })
        .toList(growable: false);
  }

  @override
  Future<Uint8List> readPage({
    required String sourceKey,
    required MihonPageItem page,
  }) async {
    final bytes = await _channel.invokeMethod<Uint8List>('readPage', {
      'sourceKey': sourceKey,
      'page': {
        'index': page.index,
        'url': page.url,
        'imageUrl': page.imageUrl,
        'uri': page.uri,
      },
    });
    if (bytes == null) throw StateError('Extension page returned no bytes.');
    return bytes;
  }

  Future<List<Object?>> _list(
    String method,
    Map<String, Object?> arguments,
  ) async {
    final rows = await _channel.invokeListMethod<Object?>(method, arguments);
    if (rows == null) throw StateError('Extension runtime returned no result.');
    return rows;
  }
}

Map<Object?, Object?> _map(Object? value) {
  if (value is! Map<Object?, Object?>) {
    throw StateError('Extension runtime returned malformed data.');
  }
  return value;
}

String _string(Map<Object?, Object?> map, String key) {
  final value = map[key];
  if (value is! String) {
    throw StateError('Extension runtime returned an invalid $key.');
  }
  return value;
}

String? _optionalString(Map<Object?, Object?> map, String key) {
  final value = map[key];
  if (value == null) return null;
  if (value is! String) {
    throw StateError('Extension runtime returned an invalid $key.');
  }
  return value;
}

double _double(Map<Object?, Object?> map, String key) {
  final value = map[key];
  if (value is! num) {
    throw StateError('Extension runtime returned an invalid $key.');
  }
  return value.toDouble();
}

int _int(Map<Object?, Object?> map, String key) {
  final value = map[key];
  if (value is! int) {
    throw StateError('Extension runtime returned an invalid $key.');
  }
  return value;
}
