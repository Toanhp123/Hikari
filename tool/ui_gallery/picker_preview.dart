import 'package:flutter/material.dart';
import 'package:hikari/application/catalog/resolve_catalog_source.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/adaptive/show_hikari_adaptive.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';
import 'package:hikari/features/catalog/presentation/catalog_source_selection.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_picker.dart';

/// Debug-only family previews, not native authorization or production data.
Future<void> showPickerPreview(
  BuildContext context, {
  required bool compact,
}) async {
  final registry = SourceRegistry(const []);
  final picker = CatalogSourcePickerViewModel(
    entry: CatalogEntry(
      id: const CatalogEntryId(provider: 'gallery', value: 'preview'),
      title: 'Picker preview',
      type: MediaType.manga,
    ),
    resolver: ResolveCatalogSource(
      searchManga: SearchManga(registry),
      searchNovels: SearchNovels(registry),
    ),
  );
  try {
    await showHikariAdaptive<CatalogSourceSelection>(
      context: context,
      purpose: HikariPresentationPurpose.selection,
      useRootNavigator: true,
      // Explicit preview override. Production uses actual window/IME policy.
      policy: (_) => compact
          ? HikariPresentationMode.bottomSheet
          : HikariPresentationMode.dialog,
      contentBuilder: (_, controller) =>
          CatalogSourcePicker(viewModel: picker, controller: controller),
    );
  } finally {
    picker.dispose();
  }
}
