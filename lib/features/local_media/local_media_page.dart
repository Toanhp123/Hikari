import 'package:flutter/material.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/library/library.dart';
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

  final LibraryRepository? library;
  final Future<void> Function()? openLibrary;
  final VoidCallback? openRemote;

  final Future<List<Media>?> Function() scanSelectedRoot;
  final Future<bool> Function() chooseRoot;
  final void Function(BuildContext, Media) openMedia;
  final bool supported;

  @override
  State<LocalMediaPage> createState() => _LocalMediaPageState();
}

class _LocalMediaPageState extends State<LocalMediaPage> {
  List<Media> _results = const [];
  Object? _error;
  bool _loading = false;
  bool _started = false;
  int _libraryRevision = 0;

  Future<void> _openLibrary() async {
    await widget.openLibrary?.call();
    if (mounted) setState(() => _libraryRevision++);
  }

  @override
  void initState() {
    super.initState();
    if (widget.supported) _scanSelectedRoot();
  }

  Future<void> _scanSelectedRoot() => _load(widget.scanSelectedRoot);

  Future<void> _choose() async {
    if (_loading) return;
    final previousError = _error;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final selected = await widget.chooseRoot();
      if (!mounted) return;
      if (!selected) {
        setState(() {
          _error = previousError;
          _loading = false;
        });
        return;
      }

      setState(() {
        _results = const [];
        _started = false;
      });
      await _load(widget.scanSelectedRoot, alreadyLoading: true);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  Future<void> _load(
    Future<List<Media>?> Function() operation, {
    bool alreadyLoading = false,
  }) async {
    if (_loading && !alreadyLoading) return;
    if (!alreadyLoading) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final results = await operation();
      if (!mounted) return;
      setState(() {
        if (results != null) {
          _results = results;
          _started = true;
        } else {
          _results = const [];
          _started = false;
        }
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  String _typeLabel(MediaType type) => switch (type) {
    MediaType.anime => 'Anime',
    MediaType.manga => 'Manga',
    MediaType.lightNovel => 'Light Novel',
  };

  int get _itemCount {
    if (_loading) return 1;
    if (_error != null) return 3;
    if (!_started) return 2;
    return (_results.isEmpty ? 1 : _results.length) + 1;
  }

  Widget _item(BuildContext context, int index) {
    if (_loading) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (_error != null) {
      return switch (index) {
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
        _ => _chooseButton(),
      };
    }
    if (!_started) {
      return index == 0
          ? const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(
                child: Text('Choose a folder to find local media.'),
              ),
            )
          : _chooseButton();
    }
    if (_results.isEmpty) {
      return index == 0
          ? const Padding(
              padding: EdgeInsets.symmetric(vertical: 48),
              child: Center(child: Text('No local media found.')),
            )
          : _chooseButton();
    }
    if (index == _results.length) return _chooseButton();
    final media = _results[index];
    return Card(
      child: ListTile(
        title: Text(media.title),
        subtitle: Text(_typeLabel(media.type)),
        onTap: () => widget.openMedia(context, media),
        trailing: widget.library == null
            ? null
            : LibraryButton(
                key: ValueKey((media.source, _libraryRevision)),
                repository: widget.library!,
                media: media,
              ),
      ),
    );
  }

  Widget _chooseButton() => OutlinedButton.icon(
    onPressed: _loading ? null : _choose,
    icon: const Icon(Icons.folder_open),
    label: const Text('Choose folder'),
  );

  @override
  Widget build(BuildContext context) {
    if (!widget.supported) {
      return Scaffold(
        appBar: AppBar(
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
        ),
        body: const Center(
          child: Text('Local media is not supported on this device.'),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(
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
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _scanSelectedRoot,
          child: ListView.builder(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            itemCount: _itemCount,
            itemBuilder: _item,
          ),
        ),
      ),
    );
  }
}
