import 'package:hikari/domain/media/media.dart';

final class CatalogMediaId {
  const CatalogMediaId({required this.provider, required this.value});
  final String provider;
  final String value;
  @override
  bool operator ==(Object other) =>
      other is CatalogMediaId &&
      other.provider == provider &&
      other.value == value;
  @override
  int get hashCode => Object.hash(provider, value);
}

enum CatalogCapability { discovery, details }

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

final class CatalogMedia {
  CatalogMedia({
    required this.id,
    required this.title,
    required this.type,
    this.coverUrl,
    this.bannerUrl,
    Iterable<String> synonyms = const [],
    Iterable<String> alternateTitles = const [],
    Iterable<String> genres = const [],
    Iterable<String> warnings = const [],
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
  }) : synonyms = List.unmodifiable(synonyms),
       alternateTitles = List.unmodifiable(alternateTitles),
       genres = List.unmodifiable(genres),
       warnings = List.unmodifiable(warnings),
       studios = List.unmodifiable(studios),
       staff = List.unmodifiable(staff);
  final CatalogMediaId id;
  final String title;
  final MediaType type;
  final String? coverUrl, bannerUrl;
  final List<String> synonyms,
      alternateTitles,
      genres,
      studios,
      staff,
      warnings;
  final int? averageScore, popularity, episodes, chapters, volumes, year;
  final CatalogFormat? format;
  final CatalogStatus? status;
  final CatalogSeason? season;
}

final class CatalogDetails {
  CatalogDetails({
    required this.media,
    this.description,
    Iterable<CatalogRelationMedia> relations = const [],
    Iterable<String> warnings = const [],
  }) : relations = List.unmodifiable(relations),
       warnings = List.unmodifiable(warnings);
  final CatalogMedia media;
  final String? description;
  final List<CatalogRelationMedia> relations;
  final List<String> warnings;
}

final class CatalogDiscovery {
  CatalogDiscovery({
    required Map<CatalogSection, List<CatalogMedia>> sections,
    Iterable<String> warnings = const [],
  }) : sections = Map.unmodifiable({
         for (final entry in sections.entries)
           entry.key: List<CatalogMedia>.unmodifiable(entry.value),
       }),
       warnings = List.unmodifiable(warnings);
  final Map<CatalogSection, List<CatalogMedia>> sections;
  final List<String> warnings;
}

final class CatalogRelationMedia {
  const CatalogRelationMedia({required this.relation, required this.media});
  final CatalogRelation relation;
  final CatalogMedia media;
}

abstract interface class CatalogProvider {
  String get id;
  Set<CatalogCapability> get capabilities;
  Future<CatalogDiscovery> discover();
  Future<CatalogDetails?> details(CatalogMediaId id);
  Future<void> close();
}
