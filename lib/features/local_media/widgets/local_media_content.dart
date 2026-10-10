import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/local_media/local_media_view_model.dart';
import 'package:hikari/features/local_media/widgets/local_media_folder_summary.dart';
import 'package:hikari/features/library/widgets/library_button.dart';

class LocalMediaContent extends StatelessWidget {
  const LocalMediaContent({
    super.key,
    required this.state,
    required this.supported,
    required this.openMedia,
    required this.onChooseRoot,
    required this.onScan,
    required this.onSelectFilter,
    this.readArtwork,
    this.library,
  });

  final LocalMediaUiState state;
  final bool supported;
  final void Function(BuildContext, Media) openMedia;
  final VoidCallback onChooseRoot;
  final Future<void> Function() onScan;
  final ValueChanged<MediaType?> onSelectFilter;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;
  final LibraryRepository? library;

  @override
  Widget build(BuildContext context) {
    if (!supported) {
      return const Center(
        child: Text('Local media is not supported on this device.'),
      );
    }

    final colors = Theme.of(context).colorScheme;
    final visible = state.visibleMedia;
    final hasItems = state.media.isNotEmpty;
    final scrollView = Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(
          maxWidth: HikariBreakpoints.maxContentWidth,
        ),
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverToBoxAdapter(
              child: LocalMediaFolderSummary(
                rootName: state.rootName,
                status: state.status,
                total: state.hasScanResult ? state.media.length : null,
                busy: state.busy,
                onChooseRoot: onChooseRoot,
              ),
            ),
            if (state.refreshing)
              const SliverToBoxAdapter(
                child: LinearProgressIndicator(minHeight: 2),
              ),
            if (state.refreshFailed)
              SliverToBoxAdapter(
                child: MaterialBanner(
                  content: Text(switch (state.failure) {
                    LocalMediaFailure.accessLost =>
                      'Access to this folder was lost. The previous scan may no longer '
                          'open. Choose the folder again.',
                    LocalMediaFailure.pickerFailed =>
                      'Could not choose another folder. Showing the last scan.',
                    _ => 'Could not rescan this folder. Showing the last scan.',
                  }),
                  actions: [
                    TextButton(
                      onPressed: state.failure == LocalMediaFailure.unavailable
                          ? () => unawaited(onScan())
                          : (state.busy ? null : onChooseRoot),
                      child: Text(
                        state.failure == LocalMediaFailure.unavailable
                            ? 'Retry'
                            : 'Choose again',
                      ),
                    ),
                  ],
                ),
              ),
            if (hasItems) ...[
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(
                    HikariSpacing.lg,
                    HikariSpacing.sm,
                    HikariSpacing.lg,
                    0,
                  ),
                  child: HikariChipRow(
                    children: [
                      HikariChip(
                        label: 'All',
                        isSelected: state.filter == null,
                        onTap: () => onSelectFilter(null),
                      ),
                      for (final type in MediaType.values)
                        HikariChip(
                          label: mediaTypeLabel(type),
                          isSelected: state.filter == type,
                          customBadgeColor: mediaTypeBadgeColors(
                            context,
                            type,
                          ).foreground,
                          onTap: () => onSelectFilter(type),
                        ),
                    ],
                  ),
                ),
              ),
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(
                    HikariSpacing.lg,
                    HikariSpacing.sm,
                    HikariSpacing.lg,
                    HikariSpacing.md,
                  ),
                  child: Text(
                    state.filter == null
                        ? '${state.media.length} items'
                        : '${visible.length} of ${state.media.length} items',
                    style: Theme.of(context).textTheme.labelMedium
                        ?.copyWith(color: colors.onSurfaceVariant),
                  ),
                ),
              ),
            ],
            if (visible.isNotEmpty)
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(
                  HikariSpacing.lg,
                  0,
                  HikariSpacing.lg,
                  HikariSpacing.xl,
                ),
                sliver: SliverGrid.builder(
                  gridDelegate: mediaPosterGridDelegate,
                  itemCount: visible.length,
                  itemBuilder: (context, index) {
                    final item = visible[index];
                    final badge = mediaTypeBadgeColors(context, item.type);
                    Widget poster(Uint8List? bytes) => MediaPoster(
                      title: item.title,
                      subtitle: mediaTypeLabel(item.type),
                      imageBytes: bytes,
                      badgeText: mediaTypeBadgeLabel(item.type),
                      badgeColor: badge.background,
                      badgeForegroundColor: badge.foreground,
                      onTap: () => openMedia(context, item),
                    );

                    final preview = state.artwork[item.source];
                    return Stack(
                      key: ValueKey(item.source),
                      children: [
                        Positioned.fill(
                          child: preview != null && readArtwork != null
                              ? SourceArtwork(
                                  resource: preview,
                                  read: readArtwork!,
                                  builder: (_, bytes, _) => poster(bytes),
                                )
                              : poster(null),
                        ),
                        if (library != null)
                          Positioned(
                            top: 4,
                            right: 4,
                            child: LibraryButton(
                              repository: library!,
                              media: item,
                            ),
                          ),
                      ],
                    );
                  },
                ),
              )
            else
              SliverFillRemaining(
                hasScrollBody: false,
                child: Padding(
                  padding: const EdgeInsets.only(bottom: HikariSpacing.xl),
                  child: hasItems
                      ? AsyncStateView(
                          status: AsyncViewStatus.empty,
                          emptyTitle: 'No matching local media',
                          emptyMessage: 'Try another type or show all items.',
                          emptyIcon: Icons.filter_list_off_rounded,
                          emptyAction: TextButton(
                            onPressed: () => onSelectFilter(null),
                            child: const Text('Show all types'),
                          ),
                          contentBuilder: (_) => const SizedBox.shrink(),
                        )
                      : AsyncStateView(
                          status: state.hasScanResult
                              ? AsyncViewStatus.empty
                              : switch (state.status) {
                                  LocalMediaStatus.loading =>
                                    AsyncViewStatus.loading,
                                  LocalMediaStatus.error =>
                                    AsyncViewStatus.error,
                                  _ => AsyncViewStatus.empty,
                                },
                          emptyTitle: state.hasScanResult
                              ? 'No local media found.'
                              : 'Your media, on this device',
                          emptyMessage: state.hasScanResult
                              ? 'Try another folder or pull down to scan again.'
                              : 'Choose a folder to find local media.',
                          emptyIcon: Icons.folder_open_rounded,
                          errorTitle: switch (state.failure) {
                            LocalMediaFailure.accessLost =>
                              'Folder access lost',
                            LocalMediaFailure.pickerFailed =>
                              'Folder selection failed',
                            _ => 'Folder unavailable',
                          },
                          errorMessage: switch (state.failure) {
                            LocalMediaFailure.accessLost =>
                              'Grant access to the folder again to continue.',
                            LocalMediaFailure.pickerFailed =>
                              'Please try choosing a folder again.',
                            _ =>
                              'Could not scan local media. Try again or choose '
                                  'the folder again.',
                          },
                          onRetry:
                              state.failure == LocalMediaFailure.unavailable
                              ? () => unawaited(onScan())
                              : null,
                          contentBuilder: (_) => const SizedBox.shrink(),
                        ),
                ),
              ),
          ],
        ),
      ),
    );
    return state.hasScanResult
        ? RefreshIndicator.adaptive(onRefresh: onScan, child: scrollView)
        : scrollView;
  }
}
