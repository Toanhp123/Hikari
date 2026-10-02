import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/catalog/catalog.dart';
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
  late Future<_CatalogDetailLoadResult> _future;
  CatalogEntryDetails? _lastDetails;
  int _loadRevision = 0;
  bool _descriptionExpanded = false;

  @override
  void initState() {
    super.initState();
    _future = _loadDetails();
  }

  Future<_CatalogDetailLoadResult> _loadDetails() async {
    final revision = ++_loadRevision;
    await Future<void>.delayed(Duration.zero);
    try {
      final details = await widget.loadDetails.execute(widget.initialEntry.id);
      if (revision == _loadRevision) _lastDetails = details;
      return _CatalogDetailLoadResult(details: details);
    } catch (_) {
      return const _CatalogDetailLoadResult(failed: true);
    }
  }

  void _retry() {
    setState(() {
      _future = _loadDetails();
    });
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final textScale = MediaQuery.textScalerOf(context).scale(1);
    final heroHeight =
        (context.isCompact ? 430.0 : 390.0) +
        (textScale - 1).clamp(0.0, 1.0).toDouble() * 120;
    return FutureBuilder<_CatalogDetailLoadResult>(
      future: _future,
      builder: (context, snapshot) {
        final details = snapshot.data?.details ?? _lastDetails;
        final entry = details?.entry ?? widget.initialEntry;
        final loading = snapshot.connectionState == ConnectionState.waiting;
        final failed = snapshot.data?.failed ?? false;
        final initialLoading = loading && details == null;

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
                  entry.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                flexibleSpace: FlexibleSpaceBar(
                  collapseMode: CollapseMode.pin,
                  background: CatalogDetailHero(
                    entry: entry,
                    details: details,
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
                      child: initialLoading
                          ? CatalogDetailLoadingBody(title: entry.title)
                          : details != null
                          ? CatalogDetailContent(
                              details: details,
                              refreshFailed: failed,
                              descriptionExpanded: _descriptionExpanded,
                              onToggleDescription: () => setState(
                                () => _descriptionExpanded =
                                    !_descriptionExpanded,
                              ),
                              onRetry: _retry,
                              openRelated: widget.openRelated,
                            )
                          : CatalogDetailFallbackBody(
                              entry: entry,
                              failed: failed,
                              onRetry: _retry,
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

final class _CatalogDetailLoadResult {
  const _CatalogDetailLoadResult({this.details, this.failed = false});

  final CatalogEntryDetails? details;
  final bool failed;
}
