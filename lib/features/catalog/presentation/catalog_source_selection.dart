import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';

/// Feature interaction only; coordinator validates identity before applying it.
sealed class CatalogSourceSelection {
  const CatalogSourceSelection();
}

final class CatalogCandidateSelection extends CatalogSourceSelection {
  const CatalogCandidateSelection(this.candidate);
  final CatalogSourcePickerCandidate candidate;
}

final class CatalogManualSearchSelection extends CatalogSourceSelection {
  CatalogManualSearchSelection({
    this.source,
    Set<SourceId>? sourceIds,
    this.language,
  }) : sourceIds = sourceIds == null ? null : Set.unmodifiable(sourceIds);
  final CatalogSourcePickerSource? source;
  final Set<SourceId>? sourceIds;
  final String? language;
}
