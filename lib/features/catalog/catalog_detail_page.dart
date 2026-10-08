import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/application/catalog/resolve_catalog_source.dart';
import 'package:hikari/core/ui/components/hikari_refresh_action.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_detail_view_model.dart';
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';
import 'package:hikari/features/catalog/widgets/catalog_detail_content.dart';
import 'package:hikari/features/catalog/widgets/catalog_detail_hero.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_picker.dart';

typedef _CatalogSourcePickerResult = ({
  Media? media,
  CatalogSourcePickerSource? source,
  Set<SourceId>? sourceIds,
  String? sourceLanguage,
});

class CatalogDetailPage extends StatefulWidget {
  const CatalogDetailPage({
    super.key,
    required this.initialEntry,
    required this.loadDetails,
    required this.resolveCatalogSource,
    required this.openMedia,
    required this.openRelated,
    required this.openSourceSearch,
    this.readArtwork,
  });

  final CatalogEntry initialEntry;
  final LoadCatalogEntryDetails loadDetails;
  final ResolveCatalogSource resolveCatalogSource;
  final Future<void> Function(BuildContext context, Media media) openMedia;
  final void Function(CatalogEntry entry) openRelated;
  final Future<Uint8List?> Function(SourceMediaRef artwork)? readArtwork;
  final void Function(
    CatalogEntry entry,
    SourceId? sourceId,
    String? sourceName,
    Set<SourceId>? sourceIds,
    String? sourceLanguage,
  )
  openSourceSearch;

  @override
  State<CatalogDetailPage> createState() => _CatalogDetailPageState();
}

class _CatalogDetailPageState extends State<CatalogDetailPage> {
  late final CatalogDetailViewModel _viewModel = CatalogDetailViewModel(
    initialEntry: widget.initialEntry,
    loadDetails: widget.loadDetails,
  );
  bool _descriptionExpanded = false;

  @override
  void initState() {
    super.initState();
    unawaited(_viewModel.load());
  }

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  Future<void> _handlePrimaryAction() async {
    final state = _viewModel.state;
    final entry = state.entry;
    if (entry.type == MediaType.anime) {
      widget.openSourceSearch(entry, null, null, null, null);
      return;
    }

    final picker = CatalogSourcePickerViewModel(
      entry: entry,
      details: state.details,
      resolver: widget.resolveCatalogSource,
    );
    late final _CatalogSourcePickerResult? result;
    try {
      result = context.isExpanded
          ? await _showSourceDialog(picker)
          : await _showSourceSheet(picker);
    } finally {
      picker.dispose();
    }
    if (!mounted || result == null) return;

    final media = result.media;
    if (media != null) {
      await widget.openMedia(context, media);
      return;
    }
    widget.openSourceSearch(
      entry,
      result.source?.id,
      result.source?.displayName,
      result.sourceIds,
      result.sourceLanguage,
    );
  }

  Future<_CatalogSourcePickerResult?> _showSourceSheet(
    CatalogSourcePickerViewModel picker,
  ) {
    return showModalBottomSheet<_CatalogSourcePickerResult>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerHigh,
      builder: (sheetContext) => ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(sheetContext).height * 0.78,
        ),
        child: CatalogSourcePicker(
          viewModel: picker,
          onOpenMedia: (media) => Navigator.of(sheetContext).pop((
            media: media,
            source: null,
            sourceIds: null,
            sourceLanguage: null,
          )),
          readArtwork: widget.readArtwork,
          onManualSearch: (source, sourceIds) =>
              Navigator.of(sheetContext).pop((
                media: null,
                source: source,
                sourceIds: sourceIds,
                sourceLanguage: source == null
                    ? picker.state.selectedLanguage
                    : source.languageCode,
              )),
        ),
      ),
    );
  }

  Future<_CatalogSourcePickerResult?> _showSourceDialog(
    CatalogSourcePickerViewModel picker,
  ) {
    return showDialog<_CatalogSourcePickerResult>(
      context: context,
      builder: (dialogContext) => Dialog(
        backgroundColor: Theme.of(context).colorScheme.surfaceContainerHigh,
        clipBehavior: Clip.antiAlias,
        child: CatalogSourcePicker(
          viewModel: picker,
          onOpenMedia: (media) => Navigator.of(dialogContext).pop((
            media: media,
            source: null,
            sourceIds: null,
            sourceLanguage: null,
          )),
          readArtwork: widget.readArtwork,
          onManualSearch: (source, sourceIds) =>
              Navigator.of(dialogContext).pop((
                media: null,
                source: source,
                sourceIds: sourceIds,
                sourceLanguage: source == null
                    ? picker.state.selectedLanguage
                    : source.languageCode,
              )),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final textScale = MediaQuery.textScalerOf(context).scale(1);
    final heroHeight =
        (context.isCompact ? 430.0 : 390.0) +
        (textScale - 1).clamp(0.0, 1.0).toDouble() * 120;

    return ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) {
        final state = _viewModel.state;
        return HikariScaffold(
          useSafeArea: false,
          body: CustomScrollView(
            physics: const BouncingScrollPhysics(),
            slivers: [
              SliverAppBar(
                pinned: true,
                stretch: true,
                expandedHeight: heroHeight,
                backgroundColor: colors.surface.withValues(alpha: 0.96),
                surfaceTintColor: Colors.transparent,
                title: Text(
                  state.entry.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                actions: [
                  HikariRefreshAction(
                    tooltip: 'Refresh',
                    refreshing: state.refreshing,
                    onPressed: state.status == CatalogDetailStatus.loading
                        ? null
                        : () => unawaited(_viewModel.load()),
                  ),
                ],
                flexibleSpace: FlexibleSpaceBar(
                  collapseMode: CollapseMode.pin,
                  background: CatalogDetailHero(
                    entry: state.entry,
                    details: state.details,
                    onPrimaryAction: () => unawaited(_handlePrimaryAction()),
                  ),
                ),
              ),
              SliverToBoxAdapter(
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(
                      maxWidth: HikariBreakpoints.maxContentWidth,
                    ),
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(
                        HikariSpacing.lg,
                        HikariSpacing.xl,
                        HikariSpacing.lg,
                        HikariSpacing.xxxl,
                      ),
                      child: state.initialLoading
                          ? CatalogDetailLoadingBody(title: state.entry.title)
                          : state.details != null
                          ? CatalogDetailContent(
                              details: state.details!,
                              refreshFailed: state.refreshFailed,
                              descriptionExpanded: _descriptionExpanded,
                              onToggleDescription: () => setState(
                                () => _descriptionExpanded =
                                    !_descriptionExpanded,
                              ),
                              onRetry: () => unawaited(_viewModel.load()),
                              openRelated: widget.openRelated,
                            )
                          : CatalogDetailFallbackBody(
                              entry: state.entry,
                              failed: state.failed,
                              onRetry: () => unawaited(_viewModel.load()),
                            ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
