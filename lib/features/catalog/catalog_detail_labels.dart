import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

String catalogMediaTypeLabel(MediaType type) => switch (type) {
  MediaType.anime => 'ANIME',
  MediaType.manga => 'MANGA',
  MediaType.lightNovel => 'LIGHT NOVEL',
};

String catalogStatusLabel(CatalogStatus status) => switch (status) {
  CatalogStatus.finished => 'Finished',
  CatalogStatus.releasing => 'Releasing',
  CatalogStatus.notYetReleased => 'Not yet released',
  CatalogStatus.cancelled => 'Cancelled',
  CatalogStatus.hiatus => 'Hiatus',
};

String catalogFormatLabel(CatalogFormat format) => switch (format) {
  CatalogFormat.tv => 'TV',
  CatalogFormat.movie => 'Movie',
  CatalogFormat.ova => 'OVA',
  CatalogFormat.ona => 'ONA',
  CatalogFormat.special => 'Special',
  CatalogFormat.manga => 'Manga',
  CatalogFormat.novel => 'Novel',
  CatalogFormat.oneShot => 'One-shot',
};

String catalogSeasonLabel(CatalogSeason season) => switch (season) {
  CatalogSeason.winter => 'Winter',
  CatalogSeason.spring => 'Spring',
  CatalogSeason.summer => 'Summer',
  CatalogSeason.fall => 'Fall',
};

String catalogRelationLabel(CatalogRelation relation) => switch (relation) {
  CatalogRelation.adaptation => 'Adaptation',
  CatalogRelation.prequel => 'Prequel',
  CatalogRelation.sequel => 'Sequel',
  CatalogRelation.parent => 'Parent',
  CatalogRelation.sideStory => 'Side story',
  CatalogRelation.character => 'Character',
  CatalogRelation.other => 'Other',
};

String compactCatalogNumber(int value) {
  if (value >= 1000000) {
    final number = value / 1000000;
    return '${number.toStringAsFixed(number >= 10 ? 0 : 1)}M';
  }
  if (value >= 1000) {
    final number = value / 1000;
    return '${number.toStringAsFixed(number >= 10 ? 0 : 1)}K';
  }
  return '$value';
}
