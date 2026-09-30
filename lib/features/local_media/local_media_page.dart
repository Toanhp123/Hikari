import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_button.dart';
import 'package:hikari/features/local_media/local_media_view_model.dart';

class LocalMediaPage extends StatefulWidget {
  const LocalMediaPage({
    super.key,
    required this.scanSelectedRoot,
    required this.chooseRoot,
    required this.openMedia,
    this.supported = true,
    this.library,
    this.catalogRevision = 0,
  });

  final Future<List<Media>?> Function() scanSelectedRoot;
  final Future<bool> Function() chooseRoot;
  final void Function(BuildContext, Media) openMedia;
  final bool supported;
  final LibraryRepository? library;
  final int catalogRevision;

  @override
  State<LocalMediaPage> createState() => _LocalMediaPageState();
}

class _LocalMediaPageState extends State<LocalMediaPage> {
  late final LocalMediaViewModel _viewModel;
  StreamSubscription<List<LibraryEntry>>? _librarySubscription;
  int _libraryRevision = 0;

  @override
  void initState() {
    super.initState();
    _viewModel = LocalMediaViewModel(
      () => widget.scanSelectedRoot(),
      () => widget.chooseRoot(),
    );
    if (widget.supported) _viewModel.scan();
    final library = widget.library;
    if (library is ObservableLibraryRepository) {
      _librarySubscription = library.watchAll().listen(
        (_) {
          if (mounted) setState(() => _libraryRevision++);
        },
        onError: (Object _) {
          if (mounted) setState(() => _libraryRevision++);
        },
      );
    }
  }

  @override
  void didUpdateWidget(LocalMediaPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.supported &&
        oldWidget.catalogRevision != widget.catalogRevision) {
      _viewModel.scan(refresh: true);
    }
  }

  @override
  void dispose() {
    _librarySubscription?.cancel();
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return HikariScaffold(
      useSafeArea: true,
      body: ListenableBuilder(
        listenable: _viewModel,
        builder: (context, _) {
          final state = _viewModel.state;
          final media = state is LocalMediaReady
              ? state.media
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
                    if (widget.supported)
                      HikariButton(
                        label: 'Choose folder',
                        icon: const Icon(Icons.folder_open_rounded, size: 18),
                        variant: HikariButtonVariant.secondary,
                        onPressed: state is LocalMediaLoading
                            ? null
                            : _viewModel.chooseRoot,
                      ),
                  ],
                ),
              ),
              Expanded(
                child: !widget.supported
                    ? const Center(
                        child: Text(
                          'Local media is not supported on this device.',
                        ),
                      )
                    : RefreshIndicator(
                        onRefresh: _viewModel.scan,
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
                                  gridDelegate:
                                      const SliverGridDelegateWithMaxCrossAxisExtent(
                                        maxCrossAxisExtent: HikariBreakpoints
                                            .posterGridMaxExtent,
                                        crossAxisSpacing: HikariSpacing.md,
                                        mainAxisSpacing: HikariSpacing.md,
                                        childAspectRatio: 2 / 3,
                                      ),
                                  itemCount: media.length,
                                  itemBuilder: (context, index) => Stack(
                                    children: [
                                      Positioned.fill(
                                        child: MediaPoster(
                                          title: media[index].title,
                                          subtitle: _mediaTypeLabel(
                                            media[index].type,
                                          ),
                                          onTap: () => widget.openMedia(
                                            context,
                                            media[index],
                                          ),
                                        ),
                                      ),
                                      if (widget.library != null)
                                        Positioned(
                                          top: 0,
                                          right: 0,
                                          child: LibraryButton(
                                            key: ValueKey((
                                              media[index].source,
                                              _libraryRevision,
                                            )),
                                            repository: widget.library!,
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
                                      LocalMediaLoading() =>
                                        AsyncViewStatus.loading,
                                      LocalMediaFailure() =>
                                        AsyncViewStatus.error,
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
                                    onRetry: _viewModel.scan,
                                    contentBuilder: (_) =>
                                        const SizedBox.shrink(),
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
              ),
            ],
          );
        },
      ),
    );
  }
}

String _mediaTypeLabel(MediaType type) => switch (type) {
  MediaType.anime => 'Anime',
  MediaType.manga => 'Manga',
  MediaType.lightNovel => 'Light Novel',
};
