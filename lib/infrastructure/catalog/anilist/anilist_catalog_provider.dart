import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:html/parser.dart' as html;
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

typedef AniListHttpPost = Future<AniListHttpResponse> Function(
  Uri uri,
  String body,
);

final class AniListHttpResponse {
  const AniListHttpResponse(this.statusCode, this.body);
  final int statusCode;
  final String body;
}

final class AniListCatalogProvider implements CatalogProvider {
  AniListCatalogProvider({AniListHttpPost? post, DateTime Function()? clock})
    : _clock = clock ?? DateTime.now {
    _post = post ?? _send;
  }

  static const _endpoint = 'https://graphql.anilist.co';
  static const _featuredCandidatePoolSize = 4;
  static const _featuredEntriesPerType = 2;
  late final AniListHttpPost _post;
  final DateTime Function() _clock;
  HttpClient? _client;
  bool _closed = false;

  @override
  String get id => 'anilist';

  Future<AniListHttpResponse> _send(Uri uri, String body) async {
    final client = _client ??= HttpClient()
      ..connectionTimeout = const Duration(seconds: 12);
    final request = await client
        .postUrl(uri)
        .timeout(const Duration(seconds: 12));
    request.headers.contentType = ContentType.json;
    request.write(body);
    final response = await request.close().timeout(const Duration(seconds: 18));
    return AniListHttpResponse(
      response.statusCode,
      await utf8.decoder
          .bind(response)
          .join()
          .timeout(const Duration(seconds: 12)),
    );
  }

  @override
  Future<CatalogDiscovery> discover() async {
    final now = _clock().toUtc();
    final year = now.year;
    final season = _season(now.month);
    const fields =
        'id type format title { english romaji native } coverImage { large }';
    const featuredFields = '$fields bannerImage genres';
    final query =
        '''query { featuredAnime: Page(perPage: $_featuredCandidatePoolSize) { media(type: ANIME sort: [TRENDING_DESC, SCORE_DESC] isAdult: false) { $featuredFields } } featuredManga: Page(perPage: $_featuredCandidatePoolSize) { media(type: MANGA format_not: NOVEL sort: [TRENDING_DESC, SCORE_DESC] isAdult: false) { $featuredFields } } featuredNovels: Page(perPage: $_featuredCandidatePoolSize) { media(type: MANGA format: NOVEL sort: [TRENDING_DESC, SCORE_DESC] isAdult: false) { $featuredFields } } trending: Page(perPage: 10) { media(sort: TRENDING_DESC isAdult: false) { $fields } } anime: Page(perPage: 10) { media(type: ANIME sort: POPULARITY_DESC isAdult: false) { $fields } } manga: Page(perPage: 10) { media(type: MANGA format_not: NOVEL sort: POPULARITY_DESC isAdult: false) { $fields } } novels: Page(perPage: 10) { media(type: MANGA format: NOVEL sort: POPULARITY_DESC isAdult: false) { $fields } } seasonal: Page(perPage: 10) { media(type: ANIME season: $season seasonYear: $year sort: POPULARITY_DESC isAdult: false) { $fields } } }''';
    final response = await _request(query);
    final root = _asMap(response['data']);
    if (root == null) {
      throw const FormatException('Catalog response has no data object.');
    }
    final warnings = _errors(response['errors']);
    final sections = <CatalogSection, List<CatalogEntry>>{};

    List<CatalogEntry> readSection(String key) {
      final rows = _asMap(root[key])?['media'];
      if (rows is! List) {
        warnings.add('Some catalog sections are unavailable.');
        return const [];
      }
      final items = <CatalogEntry>[];
      for (final row in rows) {
        final catalogEntry = _entry(row);
        if (catalogEntry == null) {
          warnings.add('Some invalid catalog entries were omitted.');
        } else {
          items.add(catalogEntry);
        }
      }
      return List.unmodifiable(items);
    }

    sections[CatalogSection.featured] = _balancedFeatured([
      readSection('featuredAnime'),
      readSection('featuredManga'),
      readSection('featuredNovels'),
    ]);
    for (final entry in const {
      'trending': CatalogSection.trending,
      'anime': CatalogSection.popularAnime,
      'manga': CatalogSection.popularManga,
      'novels': CatalogSection.popularLightNovels,
      'seasonal': CatalogSection.seasonalAnime,
    }.entries) {
      sections[entry.value] = readSection(entry.key);
    }
    return CatalogDiscovery(sections: sections, warnings: warnings.toSet());
  }

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async {
    final normalized = query.trim();
    if (normalized.isEmpty) return const [];

    const fields =
        'id type format title { english romaji native } coverImage { large }';
    final request =
        r'''query($search: String!, $type: MediaType, $format: MediaFormat, $formatNot: MediaFormat) { Page(perPage: 30) { media(search: $search type: $type format: $format format_not: $formatNot sort: SEARCH_MATCH isAdult: false) { '''
        '$fields } } }';
    final variables = <String, Object?>{
      'search': normalized,
      ...switch (type) {
        MediaType.anime => {'type': 'ANIME'},
        MediaType.manga => {'type': 'MANGA', 'formatNot': 'NOVEL'},
        MediaType.lightNovel => {'type': 'MANGA', 'format': 'NOVEL'},
        null => const <String, Object?>{},
      },
    };

    final response = await _request(request, variables: variables);
    final rows = _asMap(_asMap(response['data'])?['Page'])?['media'];
    final errors = _errors(response['errors']);
    if (rows is! List) {
      if (errors.isNotEmpty) throw FormatException(errors.join(' '));
      throw const FormatException('Catalog search response is invalid.');
    }
    if (rows.isEmpty && errors.isNotEmpty) {
      throw FormatException(errors.join(' '));
    }

    return List.unmodifiable(rows.map(_entry).whereType<CatalogEntry>());
  }

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) async {
    final numericId = int.tryParse(id.value);
    if (id.provider != this.id || numericId == null || numericId <= 0) {
      return null;
    }
    const query =
        r'''query($id: Int) { Media(id: $id) { id type format status season seasonYear startDate { year } episodes chapters volumes averageScore popularity description(asHtml: true) genres synonyms title { english romaji native } coverImage { large } bannerImage studios(isMain: true) { nodes { name } } staff(perPage: 12, sort: RELEVANCE) { edges { role node { name { full } } } } relations { edges { relationType node { id type format title { english romaji native } coverImage { large } } } } } }''';
    final response = await _request(query, variables: {'id': numericId});
    final item = _asMap(_asMap(response['data'])?['Media']);
    final warnings = _errors(response['errors']);
    if (item == null && warnings.isNotEmpty) {
      throw FormatException(warnings.join(' '));
    }
    if (item == null || item['id'] != numericId) return null;
    final entry = _entry(item);
    if (entry == null) return null;
    final relations = <CatalogRelatedEntry>[];
    final edges = _asMap(item['relations'])?['edges'];
    if (edges is! List) {
      warnings.add('Related catalog entries are unavailable.');
    } else {
      for (final rawEdge in edges) {
        final edge = _asMap(rawEdge);
        final relatedRaw = _asMap(edge?['node']);
        if (edge == null || relatedRaw == null) {
          warnings.add(
            'Some related entries were omitted because metadata was invalid.',
          );
          continue;
        }
        final relatedEntry = _entry(relatedRaw);
        if (relatedEntry == null) {
          warnings.add(
            'Some related entries were omitted because metadata was invalid.',
          );
          continue;
        }
        relations.add(
          CatalogRelatedEntry(
            relation: _relation(edge['relationType']),
            entry: relatedEntry,
          ),
        );
      }
    }
    final titles = _asMap(item['title']);
    return CatalogEntryDetails(
      entry: entry,
      description: _plain(item['description']),
      synonyms: _strings(item['synonyms']),
      alternateTitles: _alternateTitles(titles, entry.title),
      averageScore: _integer(item['averageScore']),
      popularity: _integer(item['popularity']),
      format: _format(item['format']),
      status: _status(item['status']),
      season: _enumValue(CatalogSeason.values, item['season']),
      year:
          _integer(item['seasonYear']) ??
          _integer(_asMap(item['startDate'])?['year']),
      episodes: _integer(item['episodes']),
      chapters: _integer(item['chapters']),
      volumes: _integer(item['volumes']),
      studios: _studioNames(item['studios']),
      staff: _staffNames(item['staff']),
      relations: relations,
      warnings: warnings.toSet(),
    );
  }

  Future<Map<String, dynamic>> _request(
    String query, {
    Map<String, Object?> variables = const {},
  }) async {
    if (_closed) throw StateError('Catalog provider is closed.');
    final response = await _post(
      Uri.parse(_endpoint),
      jsonEncode({'query': query, 'variables': variables}),
    ).timeout(const Duration(seconds: 25));
    if (response.statusCode == 429) {
      throw const HttpException('Catalog rate limit reached (HTTP 429).');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw HttpException('Catalog HTTP ${response.statusCode}.');
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('Catalog response is not an object.');
    }
    return decoded;
  }

  CatalogEntry? _entry(Object? value) {
    final raw = _asMap(value);
    if (raw == null) return null;
    final id = raw['id'];
    final titles = _asMap(raw['title']);
    if (id is! int || id <= 0 || titles == null) return null;
    final title =
        _text(titles['english']) ??
        _text(titles['romaji']) ??
        _text(titles['native']);
    final formatName = raw['format'];
    final type = raw['type'] == 'ANIME'
        ? MediaType.anime
        : raw['type'] == 'MANGA'
        ? (formatName == 'NOVEL' ? MediaType.lightNovel : MediaType.manga)
        : null;
    if (title == null || type == null) return null;
    final cover = _asMap(raw['coverImage'])?['large'];
    return CatalogEntry(
      id: CatalogEntryId(provider: this.id, value: '$id'),
      title: title,
      type: type,
      coverUrl: cover is String ? cover : null,
      bannerUrl: raw['bannerImage'] is String
          ? raw['bannerImage'] as String
          : null,
      genres: _strings(raw['genres']),
    );
  }

  static List<String> _alternateTitles(
    Map<Object?, Object?>? titles,
    String primaryTitle,
  ) {
    if (titles == null) return const [];
    final romaji = _text(titles['romaji']);
    final native = _text(titles['native']);
    return [
      if (romaji != null && romaji != primaryTitle) romaji,
      if (native != null && native != primaryTitle && native != romaji) native,
    ];
  }

  static List<CatalogEntry> _balancedFeatured(List<List<CatalogEntry>> pools) {
    final featured = <CatalogEntry>[];
    for (var index = 0; index < _featuredEntriesPerType; index++) {
      for (final pool in pools) {
        if (index < pool.length) featured.add(pool[index]);
      }
    }
    return List.unmodifiable(featured);
  }

  static Map<Object?, Object?>? _asMap(Object? value) =>
      value is Map<Object?, Object?> ? value : null;
  static List<String> _strings(Object? value) => value is List
      ? value
            .whereType<String>()
            .where((s) => s.trim().isNotEmpty)
            .toList(growable: false)
      : const [];
  static List<String> _studioNames(Object? value) {
    final nodes = _asMap(value)?['nodes'];
    return nodes is List
        ? nodes
              .map(_asMap)
              .whereType<Map<Object?, Object?>>()
              .map((v) => v['name'])
              .whereType<String>()
              .toList(growable: false)
        : const [];
  }

  static List<String> _staffNames(Object? value) {
    final edges = _asMap(value)?['edges'];
    if (edges is! List) return const [];
    return edges
        .map(_asMap)
        .whereType<Map<Object?, Object?>>()
        .where((edge) {
          final role = edge['role'];
          return role is String &&
              RegExp(
                r'(story|creator|original)',
                caseSensitive: false,
              ).hasMatch(role);
        })
        .map((edge) => _asMap(_asMap(edge['node'])?['name'])?['full'])
        .whereType<String>()
        .toList(growable: false);
  }

  static String? _text(Object? value) =>
      value is String && value.trim().isNotEmpty ? value.trim() : null;
  static int? _integer(Object? value) => value is int ? value : null;
  static CatalogFormat? _format(Object? value) => switch (value) {
    'TV' => CatalogFormat.tv,
    'MOVIE' => CatalogFormat.movie,
    'OVA' => CatalogFormat.ova,
    'ONA' => CatalogFormat.ona,
    'SPECIAL' => CatalogFormat.special,
    'MANGA' => CatalogFormat.manga,
    'NOVEL' => CatalogFormat.novel,
    'ONE_SHOT' => CatalogFormat.oneShot,
    _ => null,
  };
  static CatalogStatus? _status(Object? value) => switch (value) {
    'FINISHED' => CatalogStatus.finished,
    'RELEASING' => CatalogStatus.releasing,
    'NOT_YET_RELEASED' => CatalogStatus.notYetReleased,
    'CANCELLED' => CatalogStatus.cancelled,
    'HIATUS' => CatalogStatus.hiatus,
    _ => null,
  };
  static T? _enumValue<T extends Enum>(List<T> values, Object? raw) {
    if (raw is! String) return null;
    for (final value in values) {
      if (value.name.toUpperCase() == raw) return value;
    }
    return null;
  }

  static List<String> _errors(Object? value) {
    if (value is! List) return [];
    return value
        .map(_asMap)
        .whereType<Map<Object?, Object?>>()
        .map((error) => error['message'])
        .whereType<String>()
        .map((message) => 'Some catalog data could not be loaded: $message')
        .toList(growable: true);
  }

  static String? _plain(Object? value) =>
      value is String ? html.parse(value).body?.text.trim() : null;
  static CatalogRelation _relation(Object? value) => switch (value) {
    'ADAPTATION' => CatalogRelation.adaptation,
    'PREQUEL' => CatalogRelation.prequel,
    'SEQUEL' => CatalogRelation.sequel,
    'PARENT' => CatalogRelation.parent,
    'SIDE_STORY' => CatalogRelation.sideStory,
    'CHARACTER' => CatalogRelation.character,
    _ => CatalogRelation.other,
  };
  static String _season(int month) => switch (month) {
    1 || 2 || 3 => 'WINTER',
    4 || 5 || 6 => 'SPRING',
    7 || 8 || 9 => 'SUMMER',
    _ => 'FALL',
  };

  @override
  Future<void> close() async {
    _closed = true;
    _client?.close(force: true);
    _client = null;
  }
}
