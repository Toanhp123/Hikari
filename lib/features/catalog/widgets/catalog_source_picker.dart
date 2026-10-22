import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_size.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/controls/hikari_choice_menu.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_candidate_list.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_picker_list_surface.dart';

class CatalogSourcePicker extends StatefulWidget {
  const CatalogSourcePicker({
    super.key,
    required this.viewModel,
    required this.onOpenMedia,
    required this.onManualSearch,
    this.readArtwork,
  });

  final CatalogSourcePickerViewModel viewModel;
  final ValueChanged<CatalogSourcePickerCandidate> onOpenMedia;
  final void Function(CatalogSourcePickerSource?, Set<SourceId>?)
  onManualSearch;
  final Future<Uint8List?> Function(SourceMediaRef artwork)? readArtwork;

  @override
  State<CatalogSourcePicker> createState() => _CatalogSourcePickerState();
}

class _CatalogSourcePickerState extends State<CatalogSourcePicker> {
  bool _completed = false;

  CatalogSourcePickerViewModel get viewModel => widget.viewModel;

  void _openMedia(CatalogSourcePickerCandidate candidate) {
    if (!mounted || _completed) return;
    _completed = true;
    widget.onOpenMedia(candidate);
  }

  void _manualSearch(CatalogSourcePickerSource? source, Set<SourceId>? ids) {
    if (!mounted || _completed) return;
    _completed = true;
    viewModel.chooseAnotherSource();
    widget.onManualSearch(source, ids);
  }

  void _close() {
    if (_completed) return;
    _completed = true;
    viewModel.chooseAnotherSource();
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: viewModel,
      builder: (context, _) {
        final state = viewModel.state;
        return ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 540, maxHeight: 640),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final content = Padding(
                padding: const EdgeInsets.fromLTRB(
                  HikariSpacing.lg,
                  HikariSpacing.sm,
                  HikariSpacing.lg,
                  HikariSpacing.lg,
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _Header(onClose: _close),
                    const SizedBox(height: HikariSpacing.sm),
                    Flexible(
                      child: PopScope<Object?>(
                        canPop:
                            state.status !=
                            CatalogSourcePickerStatus.choosingLanguage,
                        onPopInvokedWithResult: (didPop, _) {
                          if (didPop) {
                            _completed = true;
                            viewModel.chooseAnotherSource();
                          } else {
                            viewModel.handleSystemBack();
                          }
                        },
                        child: _buildBody(context, state),
                      ),
                    ),
                  ],
                ),
              );
              final headerHeight =
                  MediaQuery.textScalerOf(context).scale(28) +
                  HikariSpacing.sm * 2 +
                  HikariSpacing.lg;
              if (constraints.maxHeight <
                  headerHeight + HikariSize.touchTarget) {
                return SingleChildScrollView(
                  child: SizedBox(
                    height: headerHeight + HikariSize.touchTarget * 2,
                    child: content,
                  ),
                );
              }
              return content;
            },
          ),
        );
      },
    );
  }

  Widget _buildBody(BuildContext context, CatalogSourcePickerUiState state) {
    return switch (state.status) {
      CatalogSourcePickerStatus.choosing ||
      CatalogSourcePickerStatus.choosingLanguage => _SourceList(
        viewModel: viewModel,
        groups: state.sourceGroups,
        languageCodes: state.languageCodes,
        selectedLanguage: state.selectedLanguage,
        showLanguageFilter: state.showLanguageFilter,
        onSelectLanguage: viewModel.selectLanguage,
        onSelect: (source) => unawaited(_selectSource(source)),
        onSearchAll: () => _manualSearch(
          null,
          state.selectedLanguage == null
              ? null
              : state.visibleSources.map((source) => source.id).toSet(),
        ),
      ),
      CatalogSourcePickerStatus.resolving => _ResolvingState(
        sourceName: state.selectedSource!.contextLabel,
        onChooseAnother: viewModel.chooseAnotherSource,
      ),
      CatalogSourcePickerStatus.candidates => CatalogSourceCandidateList(
        source: state.selectedSource!,
        candidates: state.candidates,
        onSelect: _openMedia,
        onChooseAnother: viewModel.chooseAnotherSource,
        onManualSearch: () => _manualSearch(state.selectedSource, null),
        readArtwork: widget.readArtwork,
      ),
      CatalogSourcePickerStatus.empty => _MessageState(
        icon: Icons.search_off_rounded,
        title: 'No match found',
        message:
            'Nothing on ${state.selectedSource!.contextLabel} matched the catalog title safely.',
        primaryLabel: 'Search manually',
        onPrimary: () => _manualSearch(state.selectedSource, null),
        secondaryLabel: 'Change source',
        onSecondary: viewModel.chooseAnotherSource,
      ),
      CatalogSourcePickerStatus.error => _MessageState(
        icon: Icons.cloud_off_rounded,
        title: 'Could not search this source',
        message: [
          'Search on ${state.selectedSource!.contextLabel} could not be completed.',
          if (state.errorMessage != null) state.errorMessage!,
          'Retry or search manually.',
        ].join(' '),
        primaryLabel: 'Retry',
        onPrimary: () => unawaited(_retry()),
        onManualSearch: () => _manualSearch(state.selectedSource, null),
        secondaryLabel: 'Change source',
        onSecondary: viewModel.chooseAnotherSource,
      ),
    };
  }

  Future<void> _selectSource(CatalogSourcePickerSource source) async {
    final media = await viewModel.selectSource(source);
    if (media != null) _openMedia(media);
  }

  Future<void> _retry() async {
    final media = await viewModel.retry();
    if (media != null) _openMedia(media);
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.onClose});

  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            'Read from',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleLarge,
          ),
        ),
        IconButton(
          tooltip: 'Close',
          onPressed: onClose,
          icon: const Icon(Icons.close_rounded),
        ),
      ],
    );
  }
}

class _SourceList extends StatelessWidget {
  const _SourceList({
    required this.viewModel,
    required this.groups,
    required this.languageCodes,
    required this.selectedLanguage,
    required this.showLanguageFilter,
    required this.onSelectLanguage,
    required this.onSelect,
    required this.onSearchAll,
  });

  final CatalogSourcePickerViewModel viewModel;
  final List<List<CatalogSourcePickerSource>> groups;
  final List<String> languageCodes;
  final String? selectedLanguage;
  final bool showLanguageFilter;
  final ValueChanged<String?> onSelectLanguage;
  final ValueChanged<CatalogSourcePickerSource> onSelect;
  final VoidCallback onSearchAll;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final groups = this.groups;
    final languageCodes = this.languageCodes;
    final showLanguageFilter = this.showLanguageFilter;
    final selectedLanguage = this.selectedLanguage;
    final onSelectLanguage = this.onSelectLanguage;
    final onSelect = this.onSelect;
    final onSearchAll = this.onSearchAll;
    if (viewModel.state.status == CatalogSourcePickerStatus.choosingLanguage) {
      final variants = viewModel.variantsForSelectedGroup;
      return ListView.builder(
        shrinkWrap: variants.length <= 8,
        itemCount: variants.length + 1,
        itemBuilder: (context, index) {
          if (index == 0) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                TextButton.icon(
                  onPressed: viewModel.leaveGroup,
                  icon: const Icon(Icons.arrow_back_rounded),
                  label: const Text('Back'),
                ),
                Text(
                  'Choose language',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                Text(viewModel.state.selectedGroup!.displayName),
                const SizedBox(height: HikariSpacing.sm),
              ],
            );
          }
          final variant = variants[index - 1];
          return CatalogSourcePickerListSurface(
            index: index - 1,
            count: variants.length,
            child: _SourceTile(source: variant, onTap: () => onSelect(variant)),
          );
        },
      );
    }
    if (groups.isEmpty) {
      return _MessageState(
        icon: Icons.extension_off_outlined,
        title: 'No compatible reading sources',
        message:
            'No installed remote source can open this media type yet. '
            'You can still search local media if it is available.',
        primaryLabel: 'Search all sources',
        onPrimary: onSearchAll,
      );
    }

    return ListView.builder(
      // Small pickers stay compact; large extension catalogs remain lazy.
      shrinkWrap: groups.length <= 8,
      itemCount: groups.length + 2,
      itemBuilder: (context, index) {
        if (index == 0) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Choose a source for ${viewModel.entry.title}.',
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium!
                    .copyWith(color: colors.onSurfaceVariant, height: 1.45),
              ),
              const SizedBox(height: HikariSpacing.md),
              if (showLanguageFilter) ...[
                HikariChoiceMenu<String>(
                  label: 'Language',
                  value: selectedLanguage,
                  options: [
                    const HikariChoiceOption<String>(
                      value: null,
                      label: 'All languages',
                      shortLabel: 'All',
                    ),
                    for (final language in languageCodes)
                      HikariChoiceOption(
                        value: language,
                        label: language.toUpperCase(),
                      ),
                  ],
                  icon: Icons.language_rounded,
                  onChanged: onSelectLanguage,
                ),
                const SizedBox(height: HikariSpacing.md),
              ],
            ],
          );
        }
        if (index == groups.length + 1) {
          return Padding(
            padding: const EdgeInsets.only(top: HikariSpacing.md),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextButton(
                  onPressed: onSearchAll,
                  child: Text(
                    selectedLanguage == null
                        ? 'Search all sources'
                        : 'Search ${selectedLanguage.toUpperCase()} sources',
                  ),
                ),
                if (selectedLanguage != null) ...[
                  const SizedBox(height: HikariSpacing.xs),
                  TextButton(
                    onPressed: () => onSelectLanguage(null),
                    child: const Text('Show all languages'),
                  ),
                ],
              ],
            ),
          );
        }
        final group = groups[index - 1];
        return CatalogSourcePickerListSurface(
          index: index - 1,
          count: groups.length,
          child: _SourceTile(
            source: group.first,
            groupedCount: group.length > 1 ? group.length : null,
            onTap: group.length > 1
                ? () => viewModel.chooseGroup(group.first)
                : () => onSelect(group.single),
          ),
        );
      },
    );
  }
}

class _SourceTile extends StatelessWidget {
  const _SourceTile({
    required this.source,
    required this.onTap,
    this.groupedCount,
  });

  final CatalogSourcePickerSource source;
  final VoidCallback onTap;
  final int? groupedCount;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: HikariSpacing.md),
      leading: const Icon(Icons.extension_outlined),
      title: Text(
        source.displayName,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context).textTheme.bodyLarge!
            .copyWith(fontWeight: FontWeight.w600),
      ),
      subtitle: Text(
        groupedCount != null
            ? '$groupedCount languages'
            : source.languageCode == 'all'
            ? 'Multiple languages'
            : source.languageCode?.toUpperCase() ?? 'Unspecified',
      ),
      trailing: Icon(
        Icons.chevron_right_rounded,
        color: colors.onSurfaceVariant,
      ),
      onTap: onTap,
    );
  }
}

class _ResolvingState extends StatelessWidget {
  const _ResolvingState({
    required this.sourceName,
    required this.onChooseAnother,
  });

  final String sourceName;
  final VoidCallback onChooseAnother;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Semantics(
      liveRegion: true,
      child: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: HikariSpacing.sm),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextButton(
                onPressed: onChooseAnother,
                child: const Text('Change source'),
              ),
              const SizedBox(height: HikariSpacing.sm),
              const Center(child: CircularProgressIndicator()),
              const SizedBox(height: HikariSpacing.lg),
              Text(
                'Finding matches on $sourceName…',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleMedium!,
              ),
              const SizedBox(height: HikariSpacing.xs),
              Text(
                'Checking the catalog title and known aliases.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium!
                    .copyWith(color: colors.onSurfaceVariant),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MessageState extends StatelessWidget {
  const _MessageState({
    required this.icon,
    required this.title,
    required this.message,
    required this.primaryLabel,
    required this.onPrimary,
    this.secondaryLabel,
    this.onSecondary,
    this.onManualSearch,
  });

  final IconData icon;
  final String title;
  final String message;
  final String primaryLabel;
  final VoidCallback onPrimary;
  final String? secondaryLabel;
  final VoidCallback? onSecondary;
  final VoidCallback? onManualSearch;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: HikariSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Icon(icon, size: 40, color: colors.onSurfaceVariant),
            const SizedBox(height: HikariSpacing.md),
            Text(
              title,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleMedium!,
            ),
            const SizedBox(height: HikariSpacing.xs),
            Text(
              message,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium!
                  .copyWith(color: colors.onSurfaceVariant, height: 1.45),
            ),
            const SizedBox(height: HikariSpacing.lg),
            FilledButton(onPressed: onPrimary, child: Text(primaryLabel)),
            if (onManualSearch != null)
              TextButton(
                onPressed: onManualSearch,
                child: const Text('Search manually'),
              ),
            if (secondaryLabel != null && onSecondary != null) ...[
              const SizedBox(height: HikariSpacing.sm),
              TextButton(onPressed: onSecondary, child: Text(secondaryLabel!)),
            ],
          ],
        ),
      ),
    );
  }
}
