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
    this.refreshPages,
    this.fetchFreshPage,
    this.onFreshPageValidated,
    this.onPagesRefreshed,
    this.onPageDisplayed,
    this.onPreviousChapter,
    this.onNextChapter,
    this.chapterNavigationLoading = false,
    this.registerFlush,
    this.closing = false,
  });

  final MediaProgress? initialProgress;
  final Future<void> Function(ProgressPosition, bool)? saveProgress;

  final String title;
  final String? credit;
  final Future<List<SourceMediaRef>> Function() loadPages;
  final Future<Uint8List> Function(SourceMediaRef) readPage;
  final Future<Uint8List> Function(SourceMediaRef)? reloadPage;
  final Future<List<SourceMediaRef>> Function()? refreshPages;
  final Future<Uint8List> Function(SourceMediaRef)? fetchFreshPage;
  final Future<void> Function(SourceMediaRef, Uint8List)? onFreshPageValidated;
  final Future<void> Function(List<SourceMediaRef>)? onPagesRefreshed;
  final ValueChanged<int>? onPageDisplayed;
  final Future<void> Function()? onPreviousChapter;
  final Future<void> Function()? onNextChapter;
  final bool chapterNavigationLoading;
  final ValueChanged<Future<void> Function()>? registerFlush;
  final bool closing;

  @override
  State<MangaReaderPage> createState() => _MangaReaderPageState();
}

class _MangaReaderPageState extends State<MangaReaderPage> {
  final _transform = TransformationController();
  List<SourceMediaRef>? _pages;
  ImageProvider? _image;
  bool _loading = true;
  bool _failed = false;
  bool _refreshing = false;
  int _index = 0;
  ImageProvider? _savedImage;
  ImageProvider? _notifiedImage;
  bool _hasNavigated = false;
  bool _showControls = true;
  int _loadGeneration = 0;
  bool _retryRequiresReload = false;
  bool _handingOffChapter = false;
  Future<void> _saveTail = Future<void>.value();

  void _displayed(ImageProvider image, int index) {
    if (widget.closing) return;
    if (_notifiedImage != image) {
      _notifiedImage = image;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || widget.closing || _image != image) return;
        widget.onPageDisplayed?.call(index);
      });
    }
    if (widget.initialProgress?.completed == true && !_hasNavigated) return;
    if (_savedImage == image) return;
    _savedImage = image;
    final frameReady = Completer<void>();
    final previous = _saveTail;
    final save = widget.saveProgress;
    final pageCount = _pages!.length;
    _saveTail = () async {
      await previous;
      await frameReady.future;
      try {
        await save?.call(
          PagePosition(pageIndex: index, pageCount: pageCount),
          index == pageCount - 1,
        );
      } catch (_) {
        if (_savedImage == image) _savedImage = null;
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Could not save reading progress.')),
          );
        }
      }
    }();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!frameReady.isCompleted) frameReady.complete();
    });
    unawaited(_saveTail);
  }

  Future<void> _saveBarrier() => _saveTail;

  bool get _readerInteractionBusy =>
      widget.closing ||
      _loading ||
      _refreshing ||
      _handingOffChapter ||
      widget.chapterNavigationLoading;

  Future<void> _handoffChapter(Future<void> Function()? action) async {
    if (action == null || _readerInteractionBusy) {
      return;
    }
    setState(() => _handingOffChapter = true);
    try {
      await _saveBarrier();
      if (!mounted) return;
      await action();
    } finally {
      if (mounted) setState(() => _handingOffChapter = false);
    }
  }

  @override
  void initState() {
    super.initState();
    widget.registerFlush?.call(_saveBarrier);
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

  Future<void> _loadCurrent({
    bool reloadFromSource = false,
    bool preserveOnFailure = false,
  }) async {
    final previousImage = preserveOnFailure ? _image : null;
    final previousSavedImage = preserveOnFailure ? _savedImage : null;
    final previousNotifiedImage = preserveOnFailure ? _notifiedImage : null;
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
      if (previousImage != null) {
        setState(() {
          _image = previousImage;
          _savedImage = previousSavedImage;
          _notifiedImage = previousNotifiedImage;
          _retryRequiresReload = false;
          _loading = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not reload this page.')),
        );
      } else {
        setState(() {
          _failed = true;
          _loading = false;
        });
      }
    }
  }

  /// Reconcile references without moving the reader until the replacement
  /// page bytes are available. A failed refresh leaves the existing session.
  Future<void> _refreshPages() async {
    final previousPages = _pages;
    final fetch = widget.refreshPages;
    if (_readerInteractionBusy || previousPages == null || fetch == null) {
      return;
    }
    final generation = ++_loadGeneration;
    final previousPage = previousPages.isEmpty ? null : previousPages[_index];
    setState(() => _refreshing = true);
    try {
      final freshPages = await fetch();
      if (!mounted || widget.closing || generation != _loadGeneration) return;
      var index = _index;
      if (previousPage != null) {
        final matched = freshPages.indexOf(previousPage);
        if (matched >= 0) index = matched;
      }
      if (freshPages.isEmpty) {
        index = 0;
      } else if (index >= freshPages.length) {
        index = freshPages.length - 1;
      }

      ImageProvider? image;
      Uint8List? selectedBytes;
      if (freshPages.isNotEmpty) {
        // Stage source bytes until the replacement image has been decoded.
        final read =
            widget.fetchFreshPage ?? widget.reloadPage ?? widget.readPage;
        final bytes = await read(freshPages[index]);
        if (bytes.isEmpty || !_isValidImageBytes(bytes)) {
          throw StateError('Could not decode refreshed manga page.');
        }
        if (!mounted || widget.closing || generation != _loadGeneration) return;
        final candidateImage = ResizeImage(
          MemoryImage(bytes),
          width: 2048,
          height: 4096,
          policy: ResizeImagePolicy.fit,
          allowUpscaling: false,
        );
        image = candidateImage;
        selectedBytes = bytes;
      }
      if (!mounted || widget.closing || generation != _loadGeneration) return;
      await _saveBarrier();
      if (!mounted || widget.closing || generation != _loadGeneration) return;
      if (selectedBytes != null) {
        await widget.onFreshPageValidated?.call(
          freshPages[index],
          selectedBytes,
        );
      }
      await widget.onPagesRefreshed?.call(freshPages);
      if (!mounted || widget.closing || generation != _loadGeneration) return;
      _transform.value = Matrix4.identity();
      setState(() {
        _pages = List.unmodifiable(freshPages);
        _index = index;
        _image = image;
        _savedImage = null;
        _notifiedImage = null;
        _hasNavigated = true;
        _failed = false;
        _refreshing = false;
        _retryRequiresReload = false;
      });
    } catch (_) {
      if (!mounted || widget.closing || generation != _loadGeneration) return;
      setState(() => _refreshing = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not refresh chapter pages.')),
      );
    }
  }

  static bool _isValidImageBytes(Uint8List bytes) {
    if (bytes.length < 8) return false;
    // PNG
    if (bytes[0] == 0x89 &&
        bytes[1] == 0x50 &&
        bytes[2] == 0x4E &&
        bytes[3] == 0x47) {
      return true;
    }
    // JPEG
    if (bytes[0] == 0xFF && bytes[1] == 0xD8 && bytes[2] == 0xFF) return true;
    // GIF
    if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) return true;
    // WebP: 'RIFF'....'WEBP'
    if (bytes.length >= 12 &&
        bytes[0] == 0x52 &&
        bytes[1] == 0x49 &&
        bytes[2] == 0x46 &&
        bytes[3] == 0x46 &&
        bytes[8] == 0x57 &&
        bytes[9] == 0x45 &&
        bytes[10] == 0x42 &&
        bytes[11] == 0x50) {
      return true;
    }
    // BMP: 'BM'
    if (bytes[0] == 0x42 && bytes[1] == 0x4D) return true;
    return false;
  }

  void _move(int delta) {
    if (_readerInteractionBusy || _pages == null) {
      return;
    }
    final target = _index + delta;
    if (target < 0 || target >= _pages!.length) return;
    _hasNavigated = true;
    _index = target;
    _loadCurrent();
  }

  void _jumpToPage(int page) {
    if (_readerInteractionBusy || _pages == null) {
      return;
    }
    if (page < 0 || page >= _pages!.length) return;
    _hasNavigated = true;
    _index = page;
    _loadCurrent();
  }

  void _toggleControls() {
    if (_handingOffChapter) return;
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
                      onRetry: _readerInteractionBusy
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
                            onRetry: _readerInteractionBusy
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
                onBack: () => Navigator.of(context).maybePop(),
                onPreviousChapter: widget.onPreviousChapter == null
                    ? null
                    : () => _handoffChapter(widget.onPreviousChapter),
                onNextChapter: widget.onNextChapter == null
                    ? null
                    : () => _handoffChapter(widget.onNextChapter),
                chapterNavigationLoading: _readerInteractionBusy,
                onRefreshPages: widget.refreshPages == null
                    ? null
                    : _refreshPages,
                onReloadPage: widget.reloadPage == null
                    ? null
                    : () => _loadCurrent(
                        reloadFromSource: true,
                        preserveOnFailure: true,
                      ),
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
                loading: _readerInteractionBusy,
                onPrevious: () => _move(-1),
                onNext: () => _move(1),
                onJumpToPage: _jumpToPage,
              ),
            ),
          if (_handingOffChapter || _refreshing)
            const Positioned(
              top: 4,
              left: 0,
              right: 0,
              child: LinearProgressIndicator(minHeight: 2),
            ),
        ],
      ),
    );
  }
}
