import 'dart:convert';
import 'dart:typed_data';

import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/mihon/mihon_extension_gateway.dart';

final class MihonMangaSource
    implements MangaSearchSource, MangaChapterSource, MangaPageSource {
  MihonMangaSource({
    required MihonSourceDescriptor descriptor,
    required this._gateway,
  }) : _descriptor = descriptor,
       _references = _MihonReferenceCodec(descriptor);

  final MihonSourceDescriptor _descriptor;
  final MihonExtensionGateway _gateway;
  final _MihonReferenceCodec _references;

  @override
  SourceId get id => _references.sourceId;

  @override
  String get name {
    final language = _descriptor.language.trim();
    return language.isEmpty
        ? _descriptor.name
        : '${_descriptor.name} [$language]';
  }

  @override
  Future<List<Media>> search(String query) async {
    final results = await _gateway.search(
      sourceKey: _descriptor.sourceKey,
      query: query,
    );
    return results
        .map(
          (item) => Media(
            title: item.title,
            type: MediaType.manga,
            source: SourceMediaRef(
              sourceId: id,
              itemId: _references.mangaFromPlugin(item),
            ),
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<List<MangaChapter>> chapters(SourceMediaRef manga) async {
    _requireOwns(manga);
    final mangaReference = _references.mangaToPlugin(manga.itemId);
    final chapters = await _gateway.chapters(
      sourceKey: _descriptor.sourceKey,
      mangaUrl: mangaReference.url,
      mangaTitle: mangaReference.title,
      mangaMemo: mangaReference.memo,
    );
    return chapters
        .map(
          (chapter) => MangaChapter(
            title: chapter.title,
            source: SourceMediaRef(
              sourceId: id,
              itemId: _references.chapterFromPlugin(chapter),
            ),
            scanlator: chapter.scanlator,
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async {
    _requireOwns(readable);
    final chapterReference = _references.chapterToPlugin(readable.itemId);
    final pages = await _gateway.pages(
      sourceKey: _descriptor.sourceKey,
      chapterUrl: chapterReference.url,
      chapterTitle: chapterReference.title,
      chapterNumber: chapterReference.chapterNumber,
      chapterScanlator: chapterReference.scanlator,
      chapterDateUpload: chapterReference.dateUpload,
      chapterMemo: chapterReference.memo,
    );
    return pages
        .map(
          (page) => SourceMediaRef(
            sourceId: id,
            itemId: _references.encodePage(page),
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<Uint8List> readPage(SourceMediaRef page) {
    _requireOwns(page);
    return _gateway.readPage(
      sourceKey: _descriptor.sourceKey,
      page: _references.decodePage(page.itemId),
    );
  }

  void _requireOwns(SourceMediaRef ref) {
    if (ref.sourceId != id) {
      throw ArgumentError.value(
        ref,
        'ref',
        'Reference belongs to another source.',
      );
    }
  }
}

final class _MihonReferenceCodec {
  _MihonReferenceCodec(this._descriptor);

  final MihonSourceDescriptor _descriptor;

  SourceId get sourceId => SourceId('mihon:${_descriptor.sourceKey}');

  String mangaFromPlugin(MihonMangaItem item) => _encodeStatefulReference(
    kind: 'manga',
    url: item.url,
    title: item.title,
    memo: item.memo,
  );

  _PluginReference mangaToPlugin(String itemId) =>
      _decodeStatefulReference(itemId, expectedKind: 'manga');

  String chapterFromPlugin(MihonChapterItem chapter) =>
      _encodeStatefulReference(
        kind: 'chapter',
        url: chapter.url,
        title: chapter.title,
        scanlator: chapter.scanlator,
        chapterNumber: chapter.chapterNumber,
        dateUpload: chapter.dateUpload,
        memo: chapter.memo,
      );

  _PluginReference chapterToPlugin(String itemId) =>
      _decodeStatefulReference(itemId, expectedKind: 'chapter');

  String _encodeStatefulReference({
    required String kind,
    required String url,
    String? title,
    String? memo,
    String? scanlator,
    double? chapterNumber,
    int? dateUpload,
  }) {
    final payload = jsonEncode({
      'kind': kind,
      'url': url,
      'title': title,
      'memo': memo,
      'scanlator': scanlator,
      'chapterNumber': chapterNumber,
      'dateUpload': dateUpload,
    });
    return '$_statefulReferencePrefix${base64Url.encode(utf8.encode(payload))}';
  }

  _PluginReference _decodeStatefulReference(
    String itemId, {
    required String expectedKind,
  }) {
    if (!itemId.startsWith(_statefulReferencePrefix)) {
      return _PluginReference(itemId);
    }
    try {
      final encoded = itemId.substring(_statefulReferencePrefix.length);
      final map = jsonDecode(utf8.decode(base64Url.decode(encoded)));
      if (map is! Map<String, dynamic>) throw const FormatException();
      final kind = map['kind'];
      final url = map['url'];
      final title = map['title'];
      final memo = map['memo'];
      final scanlator = map['scanlator'];
      final chapterNumber = map['chapterNumber'];
      final dateUpload = map['dateUpload'];
      if (kind != expectedKind ||
          url is! String ||
          (title != null && title is! String) ||
          (memo != null && memo is! String) ||
          (scanlator != null && scanlator is! String) ||
          (chapterNumber != null && chapterNumber is! num) ||
          (dateUpload != null && dateUpload is! int)) {
        throw const FormatException();
      }
      return _PluginReference(
        url,
        title: title as String?,
        memo: memo as String?,
        scanlator: scanlator as String?,
        chapterNumber: (chapterNumber as num?)?.toDouble(),
        dateUpload: dateUpload as int?,
      );
    } on FormatException {
      throw StateError('Invalid extension media reference.');
    }
  }

  String encodePage(MihonPageItem page) => base64Url.encode(
    utf8.encode(
      jsonEncode({
        'index': page.index,
        'url': page.url,
        'imageUrl': page.imageUrl,
        'uri': page.uri,
      }),
    ),
  );

  MihonPageItem decodePage(String itemId) {
    try {
      final map = jsonDecode(utf8.decode(base64Url.decode(itemId)));
      if (map is! Map<String, dynamic>) throw const FormatException();
      final index = map['index'];
      final url = map['url'];
      final imageUrl = map['imageUrl'];
      final uri = map['uri'];
      if (index is! int ||
          url is! String ||
          (imageUrl != null && imageUrl is! String) ||
          (uri != null && uri is! String)) {
        throw const FormatException();
      }
      return MihonPageItem(
        index: index,
        url: url,
        imageUrl: imageUrl as String?,
        uri: uri as String?,
      );
    } on FormatException {
      throw StateError('Invalid extension page reference.');
    }
  }
}

final class _PluginReference {
  const _PluginReference(
    this.url, {
    this.title,
    this.memo,
    this.scanlator,
    this.chapterNumber,
    this.dateUpload,
  });

  final String url;
  final String? title;
  final String? memo;
  final String? scanlator;
  final double? chapterNumber;
  final int? dateUpload;
}

const _statefulReferencePrefix = 'mihon-v1:';
