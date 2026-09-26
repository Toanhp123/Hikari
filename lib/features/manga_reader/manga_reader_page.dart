import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/domain/media/media.dart';

class MangaReaderPage extends StatefulWidget {
  const MangaReaderPage({
    super.key,
    required this.title,
    required this.loadPages,
    required this.readPage,
  });

  final String title;
  final Future<List<SourceMediaRef>> Function() loadPages;
  final Future<Uint8List> Function(SourceMediaRef) readPage;

  @override
  State<MangaReaderPage> createState() => _MangaReaderPageState();
}

class _MangaReaderPageState extends State<MangaReaderPage> {
  final _transform = TransformationController();
  List<SourceMediaRef>? _pages;
  ImageProvider? _image;
  bool _loading = true;
  bool _failed = false;
  int _index = 0;

  @override
  void initState() {
    super.initState();
    _loadPages();
  }

  void _releaseImage() {
    final image = _image;
    _image = null;
    if (image != null) unawaited(image.evict());
  }

  @override
  void dispose() {
    _releaseImage();
    _transform.dispose();
    super.dispose();
  }

  Future<void> _loadPages() async {
    setState(() {
      _loading = true;
      _failed = false;
    });
    try {
      final pages = await widget.loadPages();
      if (!mounted) return;
      _pages = pages;
      await _loadCurrent();
    } catch (_) {
      if (mounted) {
        setState(() {
          _failed = true;
          _loading = false;
        });
      }
    }
  }

  Future<void> _loadCurrent() async {
    _releaseImage();
    _transform.value = Matrix4.identity();
    setState(() {
      _loading = true;
      _failed = false;
    });
    try {
      if (_pages!.isNotEmpty) {
        final bytes = await widget.readPage(_pages![_index]);
        if (!mounted) return;
        // ponytail: one bounded decoded page; tiled long-page rendering is deferred.
        _image = ResizeImage(
          MemoryImage(bytes),
          width: 2048,
          height: 4096,
          policy: ResizeImagePolicy.fit,
          allowUpscaling: false,
        );
      }
      if (mounted) setState(() => _loading = false);
    } catch (_) {
      if (mounted) {
        setState(() {
          _failed = true;
          _loading = false;
        });
      }
    }
  }

  void _move(int delta) {
    if (_loading) return;
    _index += delta;
    _loadCurrent();
  }

  Widget _failure(String message) => Center(
    child: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(message),
          TextButton(
            onPressed: _loading
                ? null
                : (_pages == null ? _loadPages : _loadCurrent),
            child: const Text('Try again'),
          ),
        ],
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final pages = _pages;
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      body: SafeArea(
        child: Column(
          children: [
            if (pages != null && pages.isNotEmpty)
              Row(
                children: [
                  IconButton(
                    tooltip: 'Previous page',
                    onPressed: _loading || _index == 0 ? null : () => _move(-1),
                    icon: const Icon(Icons.chevron_left),
                  ),
                  Expanded(
                    child: Center(
                      child: Text('Page ${_index + 1} of ${pages.length}'),
                    ),
                  ),
                  IconButton(
                    tooltip: 'Next page',
                    onPressed: _loading || _index == pages.length - 1
                        ? null
                        : () => _move(1),
                    icon: const Icon(Icons.chevron_right),
                  ),
                ],
              ),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _failed
                  ? _failure('Could not load this page.')
                  : _image == null
                  ? const Center(child: Text('No pages found.'))
                  : InteractiveViewer(
                      transformationController: _transform,
                      minScale: 1,
                      maxScale: 4,
                      child: SizedBox.expand(
                        child: Image(
                          image: _image!,
                          fit: BoxFit.contain,
                          semanticLabel: 'Page ${_index + 1}',
                          errorBuilder: (_, _, _) =>
                              _failure('Could not decode this page.'),
                        ),
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
