import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/hikari_ui.dart';
import 'package:hikari/features/media/widgets/media_metadata_view.dart';
import 'package:hikari/features/media/widgets/media_type_presentation.dart';
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
    final emptyFolderMessage = switch (state.failure) {
      LocalMediaFailure.accessLost when state.hasScanResult => 'The previous scan is empty. Choose the folder again to restore access.',
      _ when state.refreshFailed =>
        'The previous scan is empty. Try rescanning or choose another folder.',
      _ when state.hasScanResult =>
        'Try another folder or pull down to scan again.',
      _ => 'Choose a folder to find local media.',
    };
    final scrollView = CustomScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        HikariContentRegion.sliver(
          sliver: SliverMainAxisGroup(
            slivers: [
              SliverToBoxAdapter(
                child: LocalMediaFolderSummary(
                  rootName: state.rootName,
                  status: state.status,
                  failure: state.failure,
                  total: state.hasScanResult ? state.media.length : null,
                  busy: state.busy,
                  onChooseRoot: onChooseRoot,
                ),
              ),
              if (state.refreshFailed)
                SliverToBoxAdapter(
                  child: HikariNoticeBanner(
                    tone: HikariNoticeTone.warning,
                    message: switch (state.failure) {
                      LocalMediaFailure.accessLost =>
                        'Access to this folder was lost. The previous scan may no longer '
                            'open. Choose the folder again.',
                      LocalMediaFailure.pickerFailed => 'Could not choose another folder. Showing the last scan.',
                      _ =>
                        'Could not rescan this folder. Showing the last scan.',
                    },
                    action: TextButton(
                      onPressed: state.failure == LocalMediaFailure.unavailable
                          ? () => unawaited(onScan())
                          : (state.busy ? null : onChooseRoot),
                      child: Text(
                        state.failure == LocalMediaFailure.unavailable
                            ? 'Retry'
                            : 'Choose again',
                      ),
                    ),
                  ),
                ),
              if (hasItems) ...[
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.only(top: HikariSpacing.sm),
                    child: HikariChipRow(
                      children: [
                        HikariFilterChip(
                          label: 'All',
                          selected: state.filter == null,
                          onSelected: (_) => onSelectFilter(null),
                        ),
                        for (final type in MediaType.values)
                          HikariFilterChip(
                            label: mediaTypeLabel(type),
                            selected: state.filter == type,
                            onSelected: (_) => onSelectFilter(type),
                          ),
                      ],
                    ),
                  ),
                ),
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.only(
                      top: HikariSpacing.sm,
                      bottom: HikariSpacing.md,
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
                HikariAdaptiveGrid.sliver(
                  padding: const EdgeInsets.only(bottom: HikariSpacing.xl),
                  itemCount: visible.length,
                  itemKeyBuilder: (index) => ValueKey(visible[index].source),
                  itemExtentBuilder: (context, width) => hikariCardExtent(
                    context,
                    width: width,
                    hasSubtitle: true,
                  ),
                  itemBuilder: (context, index) {
                    final item = visible[index];
                    final badge = mediaTypeBadgeColors(context, item.type);
                    Widget poster(Uint8List? bytes) => HikariPosterCard(
                      title: item.title,
                      subtitle: mediaTypeLabel(item.type),
                      artwork: HikariArtwork(
                        image: bytes == null ? null : MemoryImage(bytes),
                      ),
                      badge: Badge(
                        backgroundColor: badge.background,
                        textColor: badge.foreground,
                        label: Text(mediaTypeBadgeLabel(item.type)),
                      ),
                      onTap: () => openMedia(context, item),
                    );
                    final preview = state.artwork[item.source];
                    return Stack(
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
                              artwork: preview,
                            ),
                          ),
                      ],
                    );
                  },
                )
              else
                SliverFillRemaining(
                  hasScrollBody: false,
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: HikariSpacing.xl),
                    child: hasItems
                        ? HikariAsyncStateView(
                            status: HikariAsyncStatus.empty,
                            emptyTitle: 'No matching local media',
                            emptyMessage: 'Try another type or show all items.',
                            emptyAction: TextButton(
                              onPressed: () => onSelectFilter(null),
                              child: const Text('Show all types'),
                            ),
                            child: const SizedBox.shrink(),
                          )
                        : HikariAsyncStateView(
                            status: state.hasScanResult
                                ? HikariAsyncStatus.empty
                                : switch (state.status) {
                                    LocalMediaStatus.loading =>
                                      HikariAsyncStatus.loading,
                                    LocalMediaStatus.error =>
                                      HikariAsyncStatus.fatalError,
                                    _ => HikariAsyncStatus.empty,
                                  },
                            emptyTitle: state.hasScanResult
                                ? 'No local media found.'
                                : 'Your media, on this device',
                            emptyMessage: emptyFolderMessage,
                            message: switch (state.failure) {
                              LocalMediaFailure.accessLost => 'Folder access lost. Grant access to the folder again to continue.',
                              LocalMediaFailure.pickerFailed => 'Folder selection failed. Please try choosing a folder again.',
                              _ =>
                                'Folder unavailable. Could not scan local media. Try again or choose '
                                    'the folder again.',
                            },
                            onRetry:
                                state.failure == LocalMediaFailure.unavailable
                                ? () => unawaited(onScan())
                                : null,
                            child: const SizedBox.shrink(),
                          ),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
    return state.hasScanResult
        ? RefreshIndicator.adaptive(onRefresh: onScan, child: scrollView)
        : scrollView;
  }
}
