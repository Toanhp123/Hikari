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
    final colors = context.hikariColors;
    final media = state is LocalMediaReady
        ? (state as LocalMediaReady).media
        : const <Media>[];

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(HikariSpacing.lg),
          child: Wrap(
            spacing: HikariSpacing.lg,
            runSpacing: HikariSpacing.sm,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text(
                'Local',
                style: HikariTypography.titleLarge.copyWith(
                  color: colors.textPrimary,
                ),
              ),
              if (supported)
                HikariButton(
                  label: 'Choose folder',
                  icon: const Icon(Icons.folder_open_rounded, size: 18),
                  variant: HikariButtonVariant.secondary,
                  onPressed: state is LocalMediaLoading ? null : onChooseRoot,
                ),
            ],
          ),
        ),
        Expanded(
          child: !supported
              ? const Center(
                  child: Text('Local media is not supported on this device.'),
                )
              : RefreshIndicator(
                  onRefresh: onScan,
                  child: CustomScrollView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    slivers: [
                      if (media.isNotEmpty)
                        SliverPadding(
                          padding: const EdgeInsets.fromLTRB(
                            HikariSpacing.lg,
                            0,
                            HikariSpacing.lg,
                            100,
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
                                    onTap: () =>
                                        openMedia(context, media[index]),
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
                            padding: const EdgeInsets.only(bottom: 96),
                            child: AsyncStateView(
                              status: switch (state) {
                                LocalMediaLoading() => AsyncViewStatus.loading,
                                LocalMediaFailure() => AsyncViewStatus.error,
                                _ => AsyncViewStatus.empty,
                              },
                              emptyTitle: state is LocalMediaInitial
                                  ? 'Your media, on this device'
                                  : 'No local media found.',
                              emptyMessage: state is LocalMediaInitial
                                  ? 'Choose a folder to find local media.'
                                  : 'Try another folder or pull down to scan again.',
                              emptyIcon: Icons.folder_open_rounded,
                              errorTitle: 'Folder unavailable',
                              errorMessage: 'Could not scan local media. Try again or choose the folder again.',
                              onRetry: onScan,
                              contentBuilder: (_) => const SizedBox.shrink(),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
        ),
      ],
    );
  }
}
