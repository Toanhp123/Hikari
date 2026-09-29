import 'package:flutter/material.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_page.dart';
import 'package:hikari/features/local_media/local_media_view_model.dart';

class LocalMediaPage extends StatefulWidget {
  const LocalMediaPage({
    super.key,
    required this.scanSelectedRoot,
    required this.chooseRoot,
    required this.openMedia,
    this.supported = true,
    this.library,
    this.openLibrary,
    this.openRemote,
    this.openNovels,
  });

  final Future<List<Media>?> Function() scanSelectedRoot;
  final Future<bool> Function() chooseRoot;
  final void Function(BuildContext, Media) openMedia;
  final bool supported;
  final LibraryRepository? library;
  final Future<void> Function()? openLibrary;
  final VoidCallback? openRemote;
  final VoidCallback? openNovels;

  @override
  State<LocalMediaPage> createState() => _LocalMediaPageState();
}

class _LocalMediaPageState extends State<LocalMediaPage> {
  late final LocalMediaViewModel _viewModel;
  int _libraryRefreshKey = 0;

  @override
  void initState() {
    super.initState();
    _viewModel = LocalMediaViewModel(
      widget.scanSelectedRoot,
      widget.chooseRoot,
    );
    if (widget.supported) _viewModel.scan();
  }

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  Future<void> _openLibrary() async {
    await widget.openLibrary?.call();
    if (mounted) setState(() => _libraryRefreshKey++);
  }

  PreferredSizeWidget _buildAppBar() => AppBar(
    title: const Text('Local media'),
    actions: [
      if (widget.openRemote != null)
        IconButton(
          tooltip: 'Search manga',
          onPressed: widget.openRemote,
          icon: const Icon(Icons.search),
        ),
      if (widget.openNovels != null)
        IconButton(
          tooltip: 'Search novels',
          onPressed: widget.openNovels,
          icon: const Icon(Icons.menu_book),
        ),
      if (widget.openLibrary != null)
        IconButton(
          tooltip: 'Library',
          onPressed: _openLibrary,
          icon: const Icon(Icons.bookmarks_outlined),
        ),
    ],
  );

  @override
  Widget build(BuildContext context) {
    if (!widget.supported) {
      return Scaffold(
        appBar: _buildAppBar(),
        body: const Center(
          child: Text('Local media is not supported on this device.'),
        ),
      );
    }

    return Scaffold(
      appBar: _buildAppBar(),
      body: SafeArea(
        child: ListenableBuilder(
          listenable: _viewModel,
          builder: (context, _) {
            final state = _viewModel.state;
            return RefreshIndicator(
              onRefresh: _viewModel.scan,
              child: ListView.builder(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(16),
                itemCount: _itemCount(state),
                itemBuilder: (context, index) =>
                    _buildListItem(context, state, index),
              ),
            );
          },
        ),
      ),
    );
  }

  int _itemCount(LocalMediaUiState state) => switch (state) {
    LocalMediaLoading() => 1,
    LocalMediaFailure() => 3,
    LocalMediaInitial() => 2,
    LocalMediaReady(:final media) => (media.isEmpty ? 1 : media.length) + 1,
  };

  Widget _buildListItem(
    BuildContext context,
    LocalMediaUiState state,
    int index,
  ) => switch (state) {
    LocalMediaLoading() => const Padding(
      padding: EdgeInsets.all(24),
      child: Center(child: CircularProgressIndicator()),
    ),
    LocalMediaFailure() => _buildErrorItem(index),
    LocalMediaInitial() =>
      index == 0
          ? const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(
                child: Text('Choose a folder to find local media.'),
              ),
            )
          : _buildChooseFolderButton(),
    LocalMediaReady(:final media) => _buildReadyItem(context, media, index),
  };

  Widget _buildReadyItem(BuildContext context, List<Media> media, int index) {
    if (media.isEmpty) {
      return index == 0
          ? const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(child: Text('No local media found.')),
            )
          : _buildChooseFolderButton();
    }
    if (index == media.length) return _buildChooseFolderButton();
    return _buildMediaItem(context, media[index]);
  }

  Widget _buildErrorItem(int index) => switch (index) {
    0 => const Padding(
      padding: EdgeInsets.symmetric(vertical: 24),
      child: Center(
        child: Text(
          'Could not scan local media. Try again or choose the folder again.',
          textAlign: TextAlign.center,
        ),
      ),
    ),
    1 => TextButton(onPressed: _viewModel.scan, child: const Text('Try again')),
    _ => _buildChooseFolderButton(),
  };

  Widget _buildMediaItem(BuildContext context, Media media) => Card(
    child: ListTile(
      title: Text(media.title),
      subtitle: Text(_mediaTypeLabel(media.type)),
      onTap: () => widget.openMedia(context, media),
      trailing: widget.library == null
          ? null
          : LibraryButton(
              key: ValueKey((media.source, _libraryRefreshKey)),
              repository: widget.library!,
              media: media,
            ),
    ),
  );

  Widget _buildChooseFolderButton() => OutlinedButton.icon(
    onPressed: _viewModel.state is LocalMediaLoading
        ? null
        : _viewModel.chooseRoot,
    icon: const Icon(Icons.folder_open),
    label: const Text('Choose folder'),
  );
}

String _mediaTypeLabel(MediaType type) => switch (type) {
  MediaType.anime => 'Anime',
  MediaType.manga => 'Manga',
  MediaType.lightNovel => 'Light Novel',
};
