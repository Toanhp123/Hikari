import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/resume.dart';
import 'package:hikari/features/manga_reader/widgets/manga_reader_chrome.dart';

/// Immersive Manga Reader with floating chrome and real page navigation.
class MangaReaderPage extends StatefulWidget {
  const MangaReaderPage({
    super.key,
    required this.title,
    required this.loadPages,
    required this.readPage,
    this.credit,
    this.initialProgress,
    this.saveProgress,
    this.reloadPage,
    this.onPageDisplayed,
  });

  final MediaProgress? initialProgress;
  final Future<void> Function(ProgressPosition, bool)? saveProgress;

  final String title;
  final String? credit;
  final Future<List<SourceMediaRef>> Function() loadPages;
  final Future<Uint8List> Function(SourceMediaRef) readPage;
  final Future<Uint8List> Function(SourceMediaRef)? reloadPage;
  final ValueChanged<int>? onPageDisplayed;

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
  ImageProvider? _savedImage;
  ImageProvider? _notifiedImage;
  bool _hasNavigated = false;
  bool _showControls = true;
  int _loadGeneration = 0;
  bool _retryRequiresReload = false;

  void _displayed(ImageProvider image, int index) {
    if (_notifiedImage != image) {
      _notifiedImage = image;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || _image != image) return;
        widget.onPageDisplayed?.call(index);
      });
    }
    if (widget.initialProgress?.completed == true && !_hasNavigated) return;
    if (_savedImage == image) return;
    _savedImage = image;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted || _image != image) return;
      try {
        await widget.saveProgress?.call(
          PagePosition(pageIndex: index, pageCount: _pages!.length),
          index == _pages!.length - 1,
        );
      } catch (_) {
        if (!mounted) return;
        _savedImage = null;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not save reading progress.')),
        );
      }
    });
  }

  @override
  void initState() {
    super.initState();
    _loadPages();
  }

  void _releaseImage() {
    _image = null;
    _savedImage = null;
    _notifiedImage = null;
  }

  @override
  void dispose() {
    _loadGeneration++;
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
      _index = resumePage(widget.initialProgress, pages.length);
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

  Future<void> _loadCurrent({bool reloadFromSource = false}) async {
    final generation = ++_loadGeneration;
    _retryRequiresReload = reloadFromSource;
    _releaseImage();
    _transform.value = Matrix4.identity();
    setState(() {
      _loading = true;
      _failed = false;
    });
    try {
      ImageProvider? image;
      if (_pages!.isNotEmpty) {
        final read = reloadFromSource
            ? widget.reloadPage ?? widget.readPage
            : widget.readPage;
        final bytes = await read(_pages![_index]);
        if (!mounted || generation != _loadGeneration) return;
        image = ResizeImage(
          MemoryImage(bytes),
          width: 2048,
          height: 4096,
          policy: ResizeImagePolicy.fit,
          allowUpscaling: false,
        );
      }
      if (!mounted || generation != _loadGeneration) return;
      _retryRequiresReload = false;
      setState(() {
        _image = image;
        _loading = false;
      });
    } catch (_) {
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _failed = true;
        _loading = false;
      });
    }
  }

  void _move(int delta) {
    if (_loading || _pages == null) return;
    final target = _index + delta;
    if (target < 0 || target >= _pages!.length) return;
    _hasNavigated = true;
    _index = target;
    _loadCurrent();
  }

  void _jumpToPage(int page) {
    if (_loading || _pages == null) return;
    if (page < 0 || page >= _pages!.length) return;
    _hasNavigated = true;
    _index = page;
    _loadCurrent();
  }

  void _toggleControls() {
    setState(() => _showControls = !_showControls);
  }

  @override
  Widget build(BuildContext context) {
    final pages = _pages;

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          // Interactive Viewer for manga page
          Positioned.fill(
            child: GestureDetector(
              onTap: _toggleControls,
              behavior: HitTestBehavior.opaque,
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _failed
                  ? MangaReaderFailure(
                      message: 'Could not load this page.',
                      onRetry: _loading
                          ? null
                          : (_pages == null
                                ? _loadPages
                                : () => _loadCurrent(
                                    reloadFromSource: _retryRequiresReload,
                                  )),
                    )
                  : _image == null
                  ? const Center(child: Text('No pages found.'))
                  : InteractiveViewer(
                      transformationController: _transform,
                      minScale: 1,
                      maxScale: 4,
                      child: SizedBox.expand(
                        child: Image(
                          key: ValueKey(_image),
                          image: _image!,
                          fit: BoxFit.contain,
                          frameBuilder: (_, child, frame, synchronouslyLoaded) {
                            if (frame != null || synchronouslyLoaded) {
                              _displayed(_image!, _index);
                            }
                            return child;
                          },
                          semanticLabel: 'Page ${_index + 1}',
                          errorBuilder: (_, _, _) => MangaReaderFailure(
                            message: 'Could not decode this page.',
                            onRetry: _loading
                                ? null
                                : (_pages == null
                                      ? _loadPages
                                      : () => _loadCurrent(
                                          reloadFromSource: true,
                                        )),
                          ),
                        ),
                      ),
                    ),
            ),
          ),

          if (_showControls)
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: MangaReaderTopBar(
                title: widget.title,
                credit: widget.credit,
                onBack: () => Navigator.of(context).pop(),
              ),
            ),

          if (_showControls && pages != null && pages.isNotEmpty)
            Positioned(
              bottom: 0,
              left: 0,
              right: 0,
              child: MangaReaderPageControls(
                pageIndex: _index,
                pageCount: pages.length,
                loading: _loading,
                onPrevious: () => _move(-1),
                onNext: () => _move(1),
                onJumpToPage: _jumpToPage,
              ),
            ),
        ],
      ),
    );
  }
}
