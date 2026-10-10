import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/widgets/library_button.dart';
import 'package:hikari/features/local_media/local_media_view_model.dart';

class LocalMediaContent extends StatelessWidget {
  const LocalMediaContent({
    super.key,
    required this.state,
    required this.supported,
    required this.openMedia,
    required this.onChooseRoot,
    required this.onScan,
    this.library,
  });

  final LocalMediaUiState state;
  final bool supported;
  final void Function(BuildContext, Media) openMedia;
  final VoidCallback onChooseRoot;
  final Future<void> Function() onScan;
  final LibraryRepository? library;

  @override
  Widget build(BuildContext context) {
    final media = state.media;
    final scrollView = CustomScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        if (state.refreshFailed)
          SliverToBoxAdapter(
            child: MaterialBanner(
              content: const Text(
                'Could not rescan this folder. Showing the last scan.',
              ),
              actions: [
                TextButton(
                  onPressed: () => unawaited(onScan()),
                  child: const Text('Retry'),
                ),
              ],
            ),
          ),
        if (media.isNotEmpty)
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(
              HikariSpacing.lg,
              0,
              HikariSpacing.lg,
              HikariSpacing.xl,
            ),
            sliver: SliverGrid.builder(
              gridDelegate: mediaPosterGridDelegate,
              itemCount: media.length,
              itemBuilder: (context, index) => Stack(
                children: [
                  Positioned.fill(
                    child: MediaPoster(
                      title: media[index].title,
                      subtitle: mediaTypeLabel(media[index].type),
                      onTap: () => openMedia(context, media[index]),
                    ),
                  ),
                  if (library != null)
                    Positioned(
                      top: 0,
                      right: 0,
                      child: LibraryButton(
                        repository: library!,
                        media: media[index],
                      ),
                    ),
                ],
              ),
            ),
          )
        else
          SliverFillRemaining(
            hasScrollBody: false,
            child: Padding(
              padding: const EdgeInsets.only(bottom: HikariSpacing.xl),
              child: AsyncStateView(
                status: state.hasScanResult
                    ? AsyncViewStatus.empty
                    : switch (state.status) {
                        LocalMediaStatus.loading => AsyncViewStatus.loading,
                        LocalMediaStatus.error => AsyncViewStatus.error,
                        _ => AsyncViewStatus.empty,
                      },
                emptyTitle: state.hasScanResult
                    ? 'No local media found.'
                    : 'Your media, on this device',
                emptyMessage: state.hasScanResult
                    ? 'Try another folder or pull down to scan again.'
                    : 'Choose a folder to find local media.',
                emptyIcon: Icons.folder_open_rounded,
                errorTitle: 'Folder unavailable',
                errorMessage:
                    'Could not scan local media. '
                    'Try again or choose the folder again.',
                onRetry: () => unawaited(onScan()),
                contentBuilder: (_) => const SizedBox.shrink(),
              ),
            ),
          ),
      ],
    );
    final content = state.hasScanResult
        ? RefreshIndicator.adaptive(onRefresh: onScan, child: scrollView)
        : scrollView;

    return Column(
      children: [
        if (supported)
          Padding(
            padding: const EdgeInsets.fromLTRB(
              HikariSpacing.lg,
              HikariSpacing.sm,
              HikariSpacing.lg,
              HikariSpacing.sm,
            ),
            child: Align(
              alignment: AlignmentDirectional.centerEnd,
              child: HikariButton(
                label: 'Choose folder',
                icon: const Icon(Icons.folder_open_rounded, size: 18),
                variant: HikariButtonVariant.secondary,
                onPressed: state.busy ? null : onChooseRoot,
              ),
            ),
          ),
        Expanded(
          child: supported
              ? content
              : const Center(
                  child: Text('Local media is not supported on this device.'),
                ),
        ),
      ],
    );
  }
}
