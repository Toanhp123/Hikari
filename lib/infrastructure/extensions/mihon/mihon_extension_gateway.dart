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
  const MihonMangaItem({
    required this.title,
    required this.url,
    this.thumbnailUrl,
    this.summary,
    this.authors = const [],
    this.artists = const [],
    this.genres = const [],
    this.status,
    this.rawStatus,
    this.rating,
    this.memo,
  });

  final String title;
  final String url;
  final String? thumbnailUrl;
  final String? summary;
  final List<String> authors;
  final List<String> artists;
  final List<String> genres;
  final String? status;
  final String? rawStatus;
  final double? rating;
  final String? memo;
}

final class MihonSearchPage {
  const MihonSearchPage({required this.items, required this.hasNextPage});

  final List<MihonMangaItem> items;
  final bool hasNextPage;
}

final class MihonChapterItem {
  const MihonChapterItem({
    required this.title,
    required this.url,
    this.scanlator,
    this.chapterNumber,
    this.dateUpload,
    this.memo,
  });

  final String title;
  final String url;
  final String? scanlator;
  final double? chapterNumber;
  final int? dateUpload;
  final String? memo;
}

final class MihonSeriesResult {
  const MihonSeriesResult({required this.manga, required this.chapters});

  final MihonMangaItem manga;
  final List<MihonChapterItem> chapters;
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

  Future<MihonSearchPage> search({
    required String sourceKey,
    required String query,
    required int page,
  });

  Future<MihonSeriesResult> loadSeries({
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

  Future<Uint8List> readArtwork({
    required String sourceKey,
    required String url,
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
  Future<MihonSearchPage> search({
    required String sourceKey,
    required String query,
    required int page,
  }) async {
    final raw = await _channel.invokeMethod<Object?>('search', {
      'sourceKey': sourceKey,
      'query': query,
      'page': page,
    });
    final map = _map(raw);
    final rows = map['items'];
    if (rows is! List<Object?>) {
      throw StateError('Extension runtime returned invalid search items.');
    }
    return MihonSearchPage(
      items: rows.map(_manga).toList(growable: false),
      hasNextPage: _bool(map, 'hasNextPage'),
    );
  }

  @override
  Future<MihonSeriesResult> loadSeries({
    required String sourceKey,
    required String mangaUrl,
    String? mangaTitle,
    String? mangaMemo,
  }) async {
    final raw = await _channel.invokeMethod<Object?>('chapters', {
      'sourceKey': sourceKey,
      'mangaUrl': mangaUrl,
      'mangaTitle': mangaTitle,
      'mangaMemo': mangaMemo,
    });
    final map = _map(raw);
    final rows = map['chapters'];
    if (rows is! List<Object?>) {
      throw StateError('Extension runtime returned invalid chapters.');
    }
    return MihonSeriesResult(
      manga: _manga(map['manga']),
      chapters: rows.map(_chapter).toList(growable: false),
    );
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
    return rows.map(_page).toList(growable: false);
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

  @override
  Future<Uint8List> readArtwork({
    required String sourceKey,
    required String url,
  }) async {
    final bytes = await _channel.invokeMethod<Uint8List>('readArtwork', {
      'sourceKey': sourceKey,
      'url': url,
    });
    if (bytes == null) throw StateError('Extension artwork returned no bytes.');
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

MihonMangaItem _manga(Object? value) {
  final map = _map(value);
  final authors = _strings(map, 'authors');
  final artists = _strings(map, 'artists');
  final genres = _strings(map, 'genres');
  return MihonMangaItem(
    title: _string(map, 'title'),
    url: _string(map, 'url'),
    thumbnailUrl: _optionalString(map, 'thumbnailUrl'),
    summary: _optionalString(map, 'summary'),
    authors: authors,
    artists: artists,
    genres: genres,
    status: _optionalString(map, 'status'),
    rawStatus: _optionalString(map, 'rawStatus'),
    rating: _optionalDouble(map, 'rating'),
    memo: _optionalString(map, 'memo'),
  );
}

MihonChapterItem _chapter(Object? value) {
  final map = _map(value);
  return MihonChapterItem(
    title: _string(map, 'title'),
    url: _string(map, 'url'),
    scanlator: _optionalString(map, 'scanlator'),
    chapterNumber: _optionalDouble(map, 'chapterNumber'),
    dateUpload: _optionalInt(map, 'dateUpload'),
    memo: _optionalString(map, 'memo'),
  );
}

MihonPageItem _page(Object? value) {
  final map = _map(value);
  return MihonPageItem(
    index: _int(map, 'index'),
    url: _string(map, 'url'),
    imageUrl: _optionalString(map, 'imageUrl'),
    uri: _optionalString(map, 'uri'),
  );
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

List<String> _strings(Map<Object?, Object?> map, String key) {
  final value = map[key];
  if (value == null) return const [];
  if (value is! List<Object?> || value.any((item) => item is! String)) {
    throw StateError('Extension runtime returned an invalid $key.');
  }
  return value.cast<String>().toList(growable: false);
}

bool _bool(Map<Object?, Object?> map, String key) {
  final value = map[key];
  if (value is! bool) {
    throw StateError('Extension runtime returned an invalid $key.');
  }
  return value;
}

double? _optionalDouble(Map<Object?, Object?> map, String key) {
  final value = map[key];
  if (value == null) return null;
  if (value is! num) {
    throw StateError('Extension runtime returned an invalid $key.');
  }
  return value.toDouble();
}

int? _optionalInt(Map<Object?, Object?> map, String key) {
  final value = map[key];
  if (value == null) return null;
  if (value is! int) {
    throw StateError('Extension runtime returned an invalid $key.');
  }
  return value;
}

int _int(Map<Object?, Object?> map, String key) =>
    _optionalInt(map, key) ??
    (throw StateError('Extension runtime returned an invalid $key.'));
