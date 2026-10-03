import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';

class CatalogSourcePicker extends StatelessWidget {
  const CatalogSourcePicker({
    super.key,
    required this.viewModel,
    required this.onOpenMedia,
    required this.onManualSearch,
  });

  final CatalogSourcePickerViewModel viewModel;
  final ValueChanged<Media> onOpenMedia;
  final void Function(CatalogSourcePickerSource?, Set<SourceId>?)
  onManualSearch;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: viewModel,
      builder: (context, _) {
        final state = viewModel.state;
        return ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 540, maxHeight: 640),
          child: Padding(
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
                _Header(
                  title: viewModel.entry.title,
                  onClose: () => Navigator.of(context).maybePop(),
                ),
                const SizedBox(height: HikariSpacing.lg),
                Flexible(child: _buildBody(context, state)),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildBody(BuildContext context, CatalogSourcePickerUiState state) {
    final body = switch (state.status) {
      CatalogSourcePickerStatus.choosing => _SourceList(
        sources: state.visibleSources,
        languageCodes: state.languageCodes,
        selectedLanguage: state.selectedLanguage,
        showLanguageFilter: state.showLanguageFilter,
        onSelectLanguage: viewModel.selectLanguage,
        onSelect: (source) => unawaited(_selectSource(source)),
        onSearchAll: () => onManualSearch(
          null,
          state.selectedLanguage == null
              ? null
              : state.visibleSources.map((source) => source.id).toSet(),
        ),
      ),
      CatalogSourcePickerStatus.resolving => _ResolvingState(
        title: viewModel.entry.title,
        sourceName: state.selectedSource!.displayName,
      ),
      CatalogSourcePickerStatus.candidates => _CandidateList(
        source: state.selectedSource!,
        candidates: state.candidates,
        onSelect: (candidate) => onOpenMedia(candidate.media),
        onChooseAnother: viewModel.chooseAnotherSource,
        onManualSearch: () => onManualSearch(state.selectedSource, null),
      ),
      CatalogSourcePickerStatus.empty => _MessageState(
        icon: Icons.search_off_rounded,
        title: 'No match found',
        message:
            'Nothing on ${state.selectedSource!.displayName} matched the catalog title safely.',
        primaryLabel: 'Search manually',
        onPrimary: () => onManualSearch(state.selectedSource, null),
        secondaryLabel: 'Choose another source',
        onSecondary: viewModel.chooseAnotherSource,
      ),
      CatalogSourcePickerStatus.error => _MessageState(
        icon: Icons.cloud_off_rounded,
        title: 'Could not search this source',
        message:
            '${state.selectedSource!.displayName} did not respond. Retry it or choose another source.',
        primaryLabel: 'Retry',
        onPrimary: () => unawaited(_retry()),
        secondaryLabel: 'Choose another source',
        onSecondary: viewModel.chooseAnotherSource,
      ),
    };
    final duration = MediaQuery.disableAnimationsOf(context)
        ? Duration.zero
        : HikariMotion.fast;
    return AnimatedSwitcher(
      duration: duration,
      switchInCurve: HikariMotion.curveStandard,
      switchOutCurve: HikariMotion.curveExit,
      layoutBuilder: (currentChild, previousChildren) => Stack(
        alignment: Alignment.topCenter,
        children: [
          for (final child in previousChildren)
            IgnorePointer(
              child: ExcludeFocus(child: ExcludeSemantics(child: child)),
            ),
          ?currentChild,
        ],
      ),
      child: KeyedSubtree(key: ValueKey(state.status), child: body),
    );
  }

  Future<void> _selectSource(CatalogSourcePickerSource source) async {
    final media = await viewModel.selectSource(source);
    if (media != null) onOpenMedia(media);
  }

  Future<void> _retry() async {
    final media = await viewModel.retry();
    if (media != null) onOpenMedia(media);
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.title, required this.onClose});

  final String title;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text('Read from', style: HikariTypography.titleLarge),
            ),
            IconButton(
              tooltip: 'Close',
              onPressed: onClose,
              icon: const Icon(Icons.close_rounded),
            ),
          ],
        ),
        const SizedBox(height: HikariSpacing.xs),
        Text(
          title,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: HikariTypography.bodyMedium.copyWith(
            color: colors.textSecondary,
          ),
        ),
      ],
    );
  }
}

class _SourceList extends StatelessWidget {
  const _SourceList({
    required this.sources,
    required this.languageCodes,
    required this.selectedLanguage,
    required this.showLanguageFilter,
    required this.onSelectLanguage,
    required this.onSelect,
    required this.onSearchAll,
  });

  final List<CatalogSourcePickerSource> sources;
  final List<String> languageCodes;
  final String? selectedLanguage;
  final bool showLanguageFilter;
  final ValueChanged<String?> onSelectLanguage;
  final ValueChanged<CatalogSourcePickerSource> onSelect;
  final VoidCallback onSearchAll;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    if (sources.isEmpty) {
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

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Choose an installed source. Hikari will match this catalog title automatically.',
            style: HikariTypography.bodyMedium.copyWith(
              color: colors.textSecondary,
              height: 1.45,
            ),
          ),
          const SizedBox(height: HikariSpacing.md),
          if (showLanguageFilter) ...[
            Wrap(
              spacing: HikariSpacing.xs,
              children: [
                ChoiceChip(
                  label: const Text('All'),
                  selected: selectedLanguage == null,
                  onSelected: (_) => onSelectLanguage(null),
                ),
                for (final language in languageCodes)
                  ChoiceChip(
                    label: Text(language.toUpperCase()),
                    selected: selectedLanguage == language,
                    onSelected: (_) => onSelectLanguage(language),
                  ),
              ],
            ),
            const SizedBox(height: HikariSpacing.md),
          ],
          Material(
            color: colors.surfaceContainer,
            shape: RoundedRectangleBorder(
              side: BorderSide(color: colors.border),
              borderRadius: HikariRadius.borderLg,
            ),
            clipBehavior: Clip.antiAlias,
            child: Column(
              children: [
                for (var index = 0; index < sources.length; index++) ...[
                  _SourceTile(
                    source: sources[index],
                    onTap: () => onSelect(sources[index]),
                  ),
                  if (index != sources.length - 1)
                    Divider(height: 1, color: colors.border),
                ],
              ],
            ),
          ),
          const SizedBox(height: HikariSpacing.md),
          HikariButton(
            label: 'Search all sources',
            variant: HikariButtonVariant.ghost,
            isFullWidth: true,
            onPressed: onSearchAll,
          ),
        ],
      ),
    );
  }
}

class _SourceTile extends StatelessWidget {
  const _SourceTile({required this.source, required this.onTap});

  final CatalogSourcePickerSource source;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: HikariSpacing.md),
      leading: const Icon(Icons.extension_outlined),
      title: Text(
        source.displayName,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: HikariTypography.bodyLarge.copyWith(fontWeight: FontWeight.w600),
      ),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (source.languageCode != null)
            Text(
              source.languageCode!.toUpperCase(),
              style: HikariTypography.caption,
            ),
          Icon(Icons.chevron_right_rounded, color: colors.textMuted),
        ],
      ),
      onTap: onTap,
    );
  }
}

class _ResolvingState extends StatelessWidget {
  const _ResolvingState({required this.title, required this.sourceName});

  final String title;
  final String sourceName;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return Semantics(
      label: 'Finding $title on $sourceName',
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: HikariSpacing.xxl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Center(child: CircularProgressIndicator()),
            const SizedBox(height: HikariSpacing.lg),
            Text(
              'Finding “$title” on $sourceName…',
              textAlign: TextAlign.center,
              style: HikariTypography.titleMedium,
            ),
            const SizedBox(height: HikariSpacing.xs),
            Text(
              'Checking the catalog title and known aliases.',
              textAlign: TextAlign.center,
              style: HikariTypography.bodyMedium.copyWith(
                color: colors.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CandidateList extends StatelessWidget {
  const _CandidateList({
    required this.source,
    required this.candidates,
    required this.onSelect,
    required this.onChooseAnother,
    required this.onManualSearch,
  });

  final CatalogSourcePickerSource source;
  final List<CatalogSourcePickerCandidate> candidates;
  final ValueChanged<CatalogSourcePickerCandidate> onSelect;
  final VoidCallback onChooseAnother;
  final VoidCallback onManualSearch;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Is this the right title?', style: HikariTypography.titleMedium),
          const SizedBox(height: HikariSpacing.xs),
          Text(
            'Hikari found possible matches on ${source.displayName}, but none was safe '
            'to open automatically.',
            style: HikariTypography.bodyMedium.copyWith(
              color: colors.textSecondary,
              height: 1.45,
            ),
          ),
          const SizedBox(height: HikariSpacing.md),
          Material(
            color: colors.surfaceContainer,
            shape: RoundedRectangleBorder(
              side: BorderSide(color: colors.border),
              borderRadius: HikariRadius.borderLg,
            ),
            clipBehavior: Clip.antiAlias,
            child: Column(
              children: [
                for (var index = 0; index < candidates.length; index++) ...[
                  _CandidateTile(
                    candidate: candidates[index],
                    onTap: () => onSelect(candidates[index]),
                  ),
                  if (index != candidates.length - 1)
                    Divider(height: 1, color: colors.border),
                ],
              ],
            ),
          ),
          const SizedBox(height: HikariSpacing.md),
          HikariButton(
            label: 'Search manually in ${source.displayName}',
            variant: HikariButtonVariant.secondary,
            isFullWidth: true,
            onPressed: onManualSearch,
          ),
          const SizedBox(height: HikariSpacing.sm),
          HikariButton(
            label: 'Choose another source',
            variant: HikariButtonVariant.ghost,
            isFullWidth: true,
            onPressed: onChooseAnother,
          ),
        ],
      ),
    );
  }
}

class _CandidateTile extends StatelessWidget {
  const _CandidateTile({required this.candidate, required this.onTap});

  final CatalogSourcePickerCandidate candidate;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final authors = candidate.metadata?.authors ?? const <String>[];
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(
        horizontal: HikariSpacing.md,
        vertical: HikariSpacing.xs,
      ),
      leading: Icon(
        candidate.media.type == MediaType.lightNovel
            ? Icons.auto_stories_outlined
            : Icons.menu_book_outlined,
        color: colors.primaryGlow,
      ),
      title: Text(
        candidate.media.title,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: HikariTypography.bodyLarge.copyWith(fontWeight: FontWeight.w600),
      ),
      subtitle: authors.isEmpty
          ? null
          : Text(
              authors.first,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: HikariTypography.caption.copyWith(
                color: colors.textSecondary,
              ),
            ),
      trailing: Icon(Icons.arrow_forward_rounded, color: colors.textMuted),
      onTap: onTap,
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
  });

  final IconData icon;
  final String title;
  final String message;
  final String primaryLabel;
  final VoidCallback onPrimary;
  final String? secondaryLabel;
  final VoidCallback? onSecondary;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: HikariSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Icon(icon, size: 40, color: colors.textMuted),
            const SizedBox(height: HikariSpacing.md),
            Text(
              title,
              textAlign: TextAlign.center,
              style: HikariTypography.titleMedium,
            ),
            const SizedBox(height: HikariSpacing.xs),
            Text(
              message,
              textAlign: TextAlign.center,
              style: HikariTypography.bodyMedium.copyWith(
                color: colors.textSecondary,
                height: 1.45,
              ),
            ),
            const SizedBox(height: HikariSpacing.lg),
            HikariButton(
              label: primaryLabel,
              isFullWidth: true,
              onPressed: onPrimary,
            ),
            if (secondaryLabel != null && onSecondary != null) ...[
              const SizedBox(height: HikariSpacing.sm),
              HikariButton(
                label: secondaryLabel!,
                variant: HikariButtonVariant.ghost,
                isFullWidth: true,
                onPressed: onSecondary,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
