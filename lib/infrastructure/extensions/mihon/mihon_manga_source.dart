import 'dart:convert';

import 'package:drift/drift.dart';

import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/extensions/mihon/mihon_extension_gateway.dart';

final class MihonMangaSource
    implements
        MangaSearchSource,
        MangaSeriesSource,
        MangaPageSource,
        ArtworkSource {
  MihonMangaSource({
    required MihonSourceDescriptor descriptor,
    required this._gateway,
    required this._database,
  }) : _descriptor = descriptor,
       _references = _MihonReferenceCodec(descriptor);

  final MihonSourceDescriptor _descriptor;
  final MihonExtensionGateway _gateway;
  final UserDatabase _database;

  Future<void> _remember(String itemId, String payload) => _database
      .into(_database.mihonContinuationRecords)
      .insertOnConflictUpdate(
        MihonContinuationRecordsCompanion.insert(
          sourceId: id.value,
          itemId: itemId,
          payload: payload,
        ),
      );

  Future<_PluginReference> _restore(String itemId, String kind) async {
    final identity = _references._decodeStatefulReference(
      itemId,
      expectedKind: kind,
    );
    final record =
        await (_database.select(_database.mihonContinuationRecords)..where(
              (row) =>
                  row.sourceId.equals(id.value) & row.itemId.equals(itemId),
            ))
            .getSingleOrNull();
    if (record != null &&
        !record.payload.startsWith(_statefulReferencePrefix)) {
      throw StateError('Invalid extension continuation state.');
    }
    if (record == null && itemId.startsWith('mihon-v2:')) {
      throw StateError(
        'Missing extension continuation state. Refresh this series.',
      );
    }
    final continuation = _references._decodeStatefulReference(
      record?.payload ?? itemId,
      expectedKind: kind,
    );
    if (continuation.url != identity.url) {
      throw StateError('Extension continuation does not match identity.');
    }
    return continuation;
  }

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
  Future<MangaSearchPage> search(String query, {int page = 1}) async {
    final result = await _gateway.search(
      sourceKey: _descriptor.sourceKey,
      query: query,
      page: page,
    );
    await _database.transaction(() async {
      for (final item in result.items) {
        await _remember(
          _references.mangaFromPlugin(item),
          _references.mangaState(item),
        );
      }
    });
    return MangaSearchPage(
      results: result.items
          .map(
            (item) => MangaPreview(
              media: Media(
                title: item.title,
                type: MediaType.manga,
                source: SourceMediaRef(
                  sourceId: id,
                  itemId: _references.mangaFromPlugin(item),
                ),
              ),
              metadata: _metadata(item),
            ),
          )
          .toList(growable: false),
      hasNextPage: result.hasNextPage,
      page: page,
    );
  }

  @override
  Future<MangaSeriesDetails> loadSeries(SourceMediaRef manga) async {
    _requireOwns(manga);
    final mangaReference = await _restore(manga.itemId, 'manga');
    final result = await _gateway.loadSeries(
      sourceKey: _descriptor.sourceKey,
      mangaUrl: mangaReference.url,
      mangaTitle: mangaReference.title,
      mangaMemo: mangaReference.memo,
    );
    if (result.manga.url != mangaReference.url) {
      throw StateError('Extension details do not match requested identity.');
    }
    await _database.transaction(() async {
      await _remember(manga.itemId, _references.mangaState(result.manga));
      for (final chapter in result.chapters) {
        await _remember(
          _references.chapterFromPlugin(chapter),
          _references.chapterState(chapter),
        );
      }
    });
    return MangaSeriesDetails(
      metadata: _metadata(result.manga),
      chapters: result.chapters
          .map(
            (chapter) => MangaChapter(
              title: chapter.title,
              source: SourceMediaRef(
                sourceId: id,
                itemId: _references.chapterFromPlugin(chapter),
              ),
              scanlator: chapter.scanlator,
              chapterNumber: chapter.chapterNumber,
              dateUpload: chapter.dateUpload,
            ),
          )
          .toList(growable: false),
    );
  }

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async {
    _requireOwns(readable);
    final chapterReference = await _restore(readable.itemId, 'chapter');
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

  @override
  Future<Uint8List> readArtwork(SourceMediaRef artwork) {
    _requireOwns(artwork);
    return _gateway.readArtwork(
      sourceKey: _descriptor.sourceKey,
      url: _references.artworkToPlugin(artwork.itemId),
    );
  }

  MediaMetadata _metadata(MihonMangaItem item) {
    final cover = item.thumbnailUrl == null
        ? null
        : SourceMediaRef(
            sourceId: id,
            itemId: _references.artworkFromPlugin(item.thumbnailUrl!),
          );
    return MediaMetadata(
      title: item.title,
      cover: cover,
      summary: item.summary,
      authors: item.authors,
      artists: item.artists,
      genres: item.genres,
      status: _status(item.status),
      rawStatus: item.rawStatus ?? item.status,
      rating: item.rating,
    );
  }

  PublicationStatus _status(String? value) {
    final normalized = value?.trim().toLowerCase();
    return switch (normalized) {
      'ongoing' => PublicationStatus.ongoing,
      'completed' => PublicationStatus.completed,
      'licensed' => PublicationStatus.licensed,
      'publishingfinished' ||
      'publishing_finished' ||
      'publishing finished' => PublicationStatus.publishingFinished,
      'cancelled' || 'canceled' => PublicationStatus.cancelled,
      'onhiatus' ||
      'on_hiatus' ||
      'on hiatus' ||
      'hiatus' => PublicationStatus.onHiatus,
      _ => PublicationStatus.unknown,
    };
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

  String mangaFromPlugin(MihonMangaItem item) => _stable('manga', item.url);

  String chapterFromPlugin(MihonChapterItem item) =>
      _stable('chapter', item.url);

  String _stable(String kind, String url) {
    if (url.trim().isEmpty) throw StateError('Empty extension media URL.');
    return 'mihon-v2:${base64Url.encode(utf8.encode(jsonEncode({'kind': kind, 'url': url})))}';
  }

  String mangaState(MihonMangaItem item) => _encodeStatefulReference(
    kind: 'manga',
    url: item.url,
    title: item.title,
    memo: item.memo,
  );

  String chapterState(MihonChapterItem chapter) => _encodeStatefulReference(
    kind: 'chapter',
    url: chapter.url,
    title: chapter.title,
    scanlator: chapter.scanlator,
    chapterNumber: chapter.chapterNumber,
    dateUpload: chapter.dateUpload,
    memo: chapter.memo,
  );

  String artworkFromPlugin(String url) =>
      'mihon-art-v1:${base64Url.encode(utf8.encode(url))}';

  String artworkToPlugin(String itemId) {
    const prefix = 'mihon-art-v1:';
    if (!itemId.startsWith(prefix)) {
      throw StateError('Invalid extension artwork reference.');
    }
    try {
      return utf8.decode(base64Url.decode(itemId.substring(prefix.length)));
    } on FormatException {
      throw StateError('Invalid extension artwork reference.');
    }
  }

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
    if (!itemId.startsWith(_statefulReferencePrefix) &&
        !itemId.startsWith('mihon-v2:')) {
      throw StateError('Invalid extension media reference.');
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
          url.trim().isEmpty ||
          (title != null && title is! String) ||
          (memo != null && memo is! String) ||
          (scanlator != null && scanlator is! String) ||
          (chapterNumber != null &&
              (chapterNumber is! num || !chapterNumber.isFinite)) ||
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
