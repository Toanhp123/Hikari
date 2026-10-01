import 'package:hikari/domain/media/media.dart';

final class CatalogEntryId {
  const CatalogEntryId({required this.provider, required this.value});
  final String provider;
  final String value;
  @override
  bool operator ==(Object other) =>
      other is CatalogEntryId &&
      other.provider == provider &&
      other.value == value;
  @override
  int get hashCode => Object.hash(provider, value);
}

enum CatalogSection {
  featured,
  trending,
  popularAnime,
  popularManga,
  popularLightNovels,
  seasonalAnime,
}

enum CatalogFormat { tv, movie, ova, ona, special, manga, novel, oneShot }

enum CatalogStatus { finished, releasing, notYetReleased, cancelled, hiatus }

enum CatalogSeason { winter, spring, summer, fall }

enum CatalogRelation {
  adaptation,
  prequel,
  sequel,
  parent,
  sideStory,
  character,
  other,
}

final class CatalogEntry {
  CatalogEntry({
    required this.id,
    required this.title,
    required this.type,
    this.coverUrl,
    this.bannerUrl,
    Iterable<String> genres = const [],
  }) : genres = List.unmodifiable(genres);

  final CatalogEntryId id;
  final String title;
  final MediaType type;
  final String? coverUrl;
  final String? bannerUrl;
  final List<String> genres;
}

final class CatalogEntryDetails {
  CatalogEntryDetails({
    required this.entry,
    this.description,
    Iterable<String> synonyms = const [],
    Iterable<String> alternateTitles = const [],
    this.averageScore,
    this.popularity,
    this.format,
    this.status,
    this.season,
    this.year,
    this.episodes,
    this.chapters,
    this.volumes,
    Iterable<String> studios = const [],
    Iterable<String> staff = const [],
    Iterable<CatalogRelatedEntry> relations = const [],
    Iterable<String> warnings = const [],
  }) : synonyms = List.unmodifiable(synonyms),
       alternateTitles = List.unmodifiable(alternateTitles),
       studios = List.unmodifiable(studios),
       staff = List.unmodifiable(staff),
       relations = List.unmodifiable(relations),
       warnings = List.unmodifiable(warnings);

  final CatalogEntry entry;
  final String? description;
  final List<String> synonyms;
  final List<String> alternateTitles;
  final int? averageScore;
  final int? popularity;
  final CatalogFormat? format;
  final CatalogStatus? status;
  final CatalogSeason? season;
  final int? year;
  final int? episodes;
  final int? chapters;
  final int? volumes;
  final List<String> studios;
  final List<String> staff;
  final List<CatalogRelatedEntry> relations;
  final List<String> warnings;
}

final class CatalogDiscovery {
  CatalogDiscovery({
    required Map<CatalogSection, List<CatalogEntry>> sections,
    Iterable<String> warnings = const [],
  }) : sections = Map.unmodifiable({
         for (final entry in sections.entries)
           entry.key: List<CatalogEntry>.unmodifiable(entry.value),
       }),
       warnings = List.unmodifiable(warnings);
  final Map<CatalogSection, List<CatalogEntry>> sections;
  final List<String> warnings;
}

final class CatalogRelatedEntry {
  const CatalogRelatedEntry({required this.relation, required this.entry});
  final CatalogRelation relation;
  final CatalogEntry entry;
}

abstract interface class CatalogProvider {
  String get id;

  Future<CatalogDiscovery> discover();
  Future<List<CatalogEntry>> search(String query, {MediaType? type});
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id);
  Future<void> close();
}
