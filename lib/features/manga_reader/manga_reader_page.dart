import 'dart:async';
import 'dart:ui';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/resume.dart';

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
  });

  final MediaProgress? initialProgress;
  final Future<void> Function(ProgressPosition, bool)? saveProgress;

  final String title;
  final String? credit;
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
  ImageProvider? _savedImage;
  bool _hasNavigated = false;
  bool _showControls = true;
  double? _dragPage;
  int _loadGeneration = 0;

  void _displayed(ImageProvider image, int index) {
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

  Future<void> _loadCurrent() async {
    final generation = ++_loadGeneration;
    _releaseImage();
    _transform.value = Matrix4.identity();
    setState(() {
      _loading = true;
      _failed = false;
    });
    try {
      ImageProvider? image;
      if (_pages!.isNotEmpty) {
        final bytes = await widget.readPage(_pages![_index]);
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

  Widget _failure(String message) => Center(
    child: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            message,
            style: TextStyle(color: context.hikariColors.textPrimary),
          ),
          const SizedBox(height: HikariSpacing.sm),
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
    final colors = context.hikariColors;
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
                          frameBuilder: (_, child, frame, synchronouslyLoaded) {
                            if (frame != null || synchronouslyLoaded) {
                              _displayed(_image!, _index);
                            }
                            return child;
                          },
                          semanticLabel: 'Page ${_index + 1}',
                          errorBuilder: (_, _, _) =>
                              _failure('Could not decode this page.'),
                        ),
                      ),
                    ),
            ),
          ),

          // Top Floating Glassmorphism Toolbar
          if (_showControls)
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: ClipRect(
                child: BackdropFilter(
                  filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
                  child: Container(
                    padding: EdgeInsets.only(
                      top: MediaQuery.paddingOf(context).top + 4,
                      bottom: 8,
                      left: HikariSpacing.sm,
                      right: HikariSpacing.sm,
                    ),
                    decoration: BoxDecoration(
                      color: colors.background.withValues(alpha: 0.85),
                      border: Border(
                        bottom: BorderSide(color: colors.borderSubtle),
                      ),
                    ),
                    child: Row(
                      children: [
                        HikariIconButton(
                          icon: const Icon(Icons.arrow_back_rounded),
                          tooltip: 'Back',
                          onPressed: () => Navigator.of(context).pop(),
                        ),
                        const SizedBox(width: HikariSpacing.xs),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                widget.title,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.w600,
                                  color: colors.textPrimary,
                                ),
                              ),
                              if (widget.credit != null)
                                Text(
                                  widget.credit!,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    fontSize: 11,
                                    color: colors.textSecondary,
                                  ),
                                ),
                            ],
                          ),
                        ),

                      ],
                    ),
                  ),
                ),
              ),
            ),

          // Bottom Floating Glassmorphism Page Control Bar
          if (_showControls && pages != null && pages.isNotEmpty)
            Positioned(
              bottom: 0,
              left: 0,
              right: 0,
              child: ClipRect(
                child: BackdropFilter(
                  filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
                  child: Container(
                    padding: EdgeInsets.only(
                      bottom: MediaQuery.paddingOf(context).bottom + 4,
                      top: 4,
                      left: HikariSpacing.md,
                      right: HikariSpacing.md,
                    ),
                    decoration: BoxDecoration(
                      color: colors.background.withValues(alpha: 0.85),
                      border: Border(
                        top: BorderSide(color: colors.borderSubtle),
                      ),
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Row(
                          children: [
                            IconButton(
                              tooltip: 'Previous page',
                              onPressed: _loading || _index == 0
                                  ? null
                                  : () => _move(-1),
                              icon: const Icon(Icons.chevron_left_rounded),
                            ),
                            Expanded(
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    'Page ${_index + 1} of ${pages.length}',
                                    style: TextStyle(
                                      fontSize: 12,
                                      fontWeight: FontWeight.w600,
                                      color: colors.textPrimary,
                                    ),
                                  ),
                                  SliderTheme(
                                    data: SliderThemeData(
                                      activeTrackColor: colors.primary,
                                      inactiveTrackColor:
                                          colors.surfaceHighlight,
                                      thumbColor: colors.primaryGlow,
                                      overlayColor: colors.primary.withValues(
                                        alpha: 0.2,
                                      ),
                                      trackHeight: 3,
                                      thumbShape: const RoundSliderThumbShape(
                                        enabledThumbRadius: 6,
                                      ),
                                    ),
                                    child: Slider(
                                      value:
                                          _dragPage ?? (_index + 1).toDouble(),
                                      min: 1,
                                      max: pages.length.toDouble(),
                                      divisions: pages.length > 1
                                          ? pages.length - 1
                                          : 1,
                                      onChanged: _loading
                                          ? null
                                          : (value) {
                                              setState(
                                                () => _dragPage = value,
                                              );
                                            },
                                      onChangeEnd: _loading
                                          ? null
                                          : (value) {
                                              setState(
                                                () => _dragPage = null,
                                              );
                                              _jumpToPage(
                                                value.round() - 1,
                                              );
                                            },
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            IconButton(
                              tooltip: 'Next page',
                              onPressed: _loading || _index == pages.length - 1
                                  ? null
                                  : () => _move(1),
                              icon: const Icon(Icons.chevron_right_rounded),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
