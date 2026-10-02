import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/core/ui/components/hikari_refresh_action.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/features/catalog/catalog_detail_view_model.dart';
import 'package:hikari/features/catalog/widgets/catalog_detail_content.dart';
import 'package:hikari/features/catalog/widgets/catalog_detail_hero.dart';

class CatalogDetailPage extends StatefulWidget {
  const CatalogDetailPage({
    super.key,
    required this.initialEntry,
    required this.loadDetails,
    required this.openRelated,
    required this.openSourceSearch,
  });

  final CatalogEntry initialEntry;
  final LoadCatalogEntryDetails loadDetails;
  final void Function(CatalogEntry entry) openRelated;
  final void Function(CatalogEntry entry) openSourceSearch;

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

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
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
                backgroundColor: colors.background.withValues(alpha: 0.96),
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
                    openSourceSearch: widget.openSourceSearch,
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
