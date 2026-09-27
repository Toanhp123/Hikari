import 'package:flutter/material.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_page.dart';

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
  });

  final Future<List<Media>?> Function() scanSelectedRoot;
  final Future<bool> Function() chooseRoot;
  final void Function(BuildContext, Media) openMedia;
  final bool supported;
  final LibraryRepository? library;
  final Future<void> Function()? openLibrary;
  final VoidCallback? openRemote;

  @override
  State<LocalMediaPage> createState() => _LocalMediaPageState();
}

class _LocalMediaPageState extends State<LocalMediaPage> {
  List<Media> _media = const [];
  Object? _scanError;
  bool _isScanning = false;
  bool _hasScanResult = false;
  int _libraryRefreshKey = 0;

  @override
  void initState() {
    super.initState();
    if (widget.supported) _scanSelectedRoot();
  }

  Future<void> _openLibrary() async {
    await widget.openLibrary?.call();
    if (mounted) setState(() => _libraryRefreshKey++);
  }

  Future<void> _scanSelectedRoot() => _runScan(widget.scanSelectedRoot);

  Future<void> _chooseRoot() async {
    if (_isScanning) return;
    final previousError = _scanError;
    setState(() {
      _isScanning = true;
      _scanError = null;
    });

    try {
      final selected = await widget.chooseRoot();
      if (!mounted) return;
      if (!selected) {
        setState(() {
          _scanError = previousError;
          _isScanning = false;
        });
        return;
      }

      setState(() {
        _media = const [];
        _hasScanResult = false;
      });
      await _runScan(widget.scanSelectedRoot, scanAlreadyStarted: true);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _scanError = error;
        _isScanning = false;
      });
    }
  }

  Future<void> _runScan(
    Future<List<Media>?> Function() scan, {
    bool scanAlreadyStarted = false,
  }) async {
    if (_isScanning && !scanAlreadyStarted) return;
    if (!scanAlreadyStarted) {
      setState(() {
        _isScanning = true;
        _scanError = null;
      });
    }

    try {
      final result = await scan();
      if (!mounted) return;
      setState(() {
        _media = result ?? const [];
        _hasScanResult = result != null;
        _isScanning = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _scanError = error;
        _isScanning = false;
      });
    }
  }

  int get _itemCount {
    if (_isScanning) return 1;
    if (_scanError != null) return 3;
    if (!_hasScanResult) return 2;
    return (_media.isEmpty ? 1 : _media.length) + 1;
  }

  Widget _buildListItem(BuildContext context, int index) {
    if (_isScanning) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (_scanError != null) return _buildErrorItem(index);
    if (!_hasScanResult) {
      return index == 0
          ? const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(child: Text('Choose a folder to find local media.')),
            )
          : _buildChooseFolderButton();
    }
    if (_media.isEmpty) {
      return index == 0
          ? const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(child: Text('No local media found.')),
            )
          : _buildChooseFolderButton();
    }
    if (index == _media.length) return _buildChooseFolderButton();

    final media = _media[index];
    return Card(
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
    1 => TextButton(
      onPressed: _scanSelectedRoot,
      child: const Text('Try again'),
    ),
    _ => _buildChooseFolderButton(),
  };

  Widget _buildChooseFolderButton() => OutlinedButton.icon(
    onPressed: _isScanning ? null : _chooseRoot,
    icon: const Icon(Icons.folder_open),
    label: const Text('Choose folder'),
  );

  PreferredSizeWidget _buildAppBar() => AppBar(
    title: const Text('Local media'),
    actions: [
      if (widget.openRemote != null)
        IconButton(
          tooltip: 'Search manga',
          onPressed: widget.openRemote,
          icon: const Icon(Icons.search),
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
        child: RefreshIndicator(
          onRefresh: _scanSelectedRoot,
          child: ListView.builder(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            itemCount: _itemCount,
            itemBuilder: _buildListItem,
          ),
        ),
      ),
    );
  }
}

String _mediaTypeLabel(MediaType type) => switch (type) {
  MediaType.anime => 'Anime',
  MediaType.manga => 'Manga',
  MediaType.lightNovel => 'Light Novel',
};
