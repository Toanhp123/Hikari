import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/core/ui/components/hikari_refresh_action.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/resume.dart';
import 'package:hikari/features/novel_reader/novel_reader_theme.dart';
import 'package:hikari/features/novel_reader/widgets/novel_content_view.dart';
import 'package:hikari/features/novel_reader/widgets/novel_reader_preferences_sheet.dart';
import 'package:hikari/features/novel_reader/widgets/novel_reader_progress.dart';

class NovelReaderPage extends StatefulWidget {
  const NovelReaderPage({
    super.key,
    required this.title,
    this.loadText,
    this.loadContent,
    this.readResource,
    this.reloadContent,
    this.reloadResource,
    this.initialProgress,
    this.saveProgress,
  }) : assert((loadText != null) != (loadContent != null)),
       assert(loadContent == null || readResource != null),
       assert(reloadContent == null || loadContent != null),
       assert(reloadResource == null || readResource != null);

  final MediaProgress? initialProgress;
  final Future<void> Function(ProgressPosition, bool)? saveProgress;

  final String title;
  final Future<String> Function()? loadText;
  final Future<RichReadingContent> Function()? loadContent;
  final Future<Uint8List> Function(SourceMediaRef)? readResource;
  final Future<RichReadingContent> Function()? reloadContent;
  final Future<Uint8List> Function(SourceMediaRef)? reloadResource;

  @override
  State<NovelReaderPage> createState() => _NovelReaderPageState();
}

class _NovelReaderPageState extends State<NovelReaderPage>
    with WidgetsBindingObserver {
  final _scroll = ScrollController();
  Timer? _debounce;
  bool _restored = false;
  double _position = 0;
  bool _completed = false;
  (double, bool)? _lastSaved;
  final _progress = ValueNotifier<double>(0);

  NovelReaderTheme _readerTheme = NovelReaderTheme.charcoal;
  double _fontSize = 16.0;
  final double _lineHeight = 1.6;
  final double _horizontalPadding = 20.0;

  void _changed() {
    if (!_restored || !_scroll.hasClients || _restoringReload) return;
    _position = textProgression(
      _scroll.offset,
      _scroll.position.maxScrollExtent,
    );
    _completed = textAtEnd(_scroll.offset, _scroll.position.maxScrollExtent);
    _progress.value = _position;
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 500), _flush);
  }

  Future<void> _flush() async {
    _debounce?.cancel();
    if (!_restored || _lastSaved == (_position, _completed)) return;
    final value = (_position, _completed);
    _lastSaved = value;
    try {
      await widget.saveProgress?.call(
        TextPosition(progression: value.$1),
        value.$2,
      );
    } catch (_) {
      _lastSaved = null;
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not save reading progress.')),
        );
      }
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) unawaited(_flush());
  }

  @override
  void dispose() {
    unawaited(_flush());
    _scroll.dispose();
    _progress.dispose();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  String? _text;
  RichReadingContent? _content;
  Object? _error;
  bool _loading = true;
  bool _reloading = false;
  bool _restoringReload = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _scroll.addListener(_changed);
    _load();
  }

  Future<void> _load({bool fresh = false}) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final text = await widget.loadText?.call();
      final content = fresh && widget.reloadContent != null
          ? await widget.reloadContent!.call()
          : await widget.loadContent?.call();
      if (!mounted) return;
      setState(() {
        _text = text;
        _content = content;
        _loading = false;
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || !_scroll.hasClients) return;
        _position = resumeText(widget.initialProgress);
        _scroll.jumpTo(_position * _scroll.position.maxScrollExtent);
        _position = textProgression(
          _scroll.offset,
          _scroll.position.maxScrollExtent,
        );
        _completed = textAtEnd(
          _scroll.offset,
          _scroll.position.maxScrollExtent,
        );
        _lastSaved = (_position, _completed);
        _restored = true;
        _progress.value = _position;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  Future<void> _reload() async {
    final reload = widget.reloadContent;
    if (reload == null || _reloading) return;
    setState(() => _reloading = true);
    try {
      final content = await reload();
      if (!mounted) return;
      final progression = _restored
          ? _position
          : resumeText(widget.initialProgress);
      setState(() {
        _content = content;
        _text = null;
        _error = null;
        _reloading = false;
        _restoringReload = true;
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        if (_scroll.hasClients && _scroll.position.maxScrollExtent > 0) {
          final offset =
              progression.clamp(0.0, 1.0) * _scroll.position.maxScrollExtent;
          _scroll.jumpTo(offset);
        }
        _position = progression;
        _progress.value = progression;
        _restoringReload = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() => _reloading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not reload this chapter.')),
      );
    }
  }

  void _showPreferencesSheet() {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: context.hikariColors.surfaceElevated,
      builder: (context) => NovelReaderPreferencesSheet(
        theme: _readerTheme,
        fontSize: _fontSize,
        onThemeChanged: (theme) => setState(() => _readerTheme = theme),
        onFontSizeChanged: (fontSize) => setState(() => _fontSize = fontSize),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _readerTheme.bg,
      appBar: AppBar(
        title: Text(widget.title),
        backgroundColor: _readerTheme.bg,
        foregroundColor: _readerTheme.fg,
        elevation: 0,
        actions: [
          if (widget.reloadContent != null)
            HikariRefreshAction(
              tooltip: 'Reload chapter',
              refreshing: _reloading,
              onPressed: _reload,
            ),
          HikariIconButton(
            icon: const Icon(Icons.format_size_rounded),
            tooltip: 'Reading Preferences',
            color: _readerTheme.fg,
            onPressed: _showPreferencesSheet,
          ),
          const SizedBox(width: HikariSpacing.xs),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _error != null
                  ? Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Text('Could not load this novel.'),
                          const SizedBox(height: HikariSpacing.sm),
                          TextButton(
                            onPressed: () =>
                                _load(fresh: widget.reloadContent != null),
                            child: const Text('Try again'),
                          ),
                        ],
                      ),
                    )
                  : SingleChildScrollView(
                      controller: _scroll,
                      padding: EdgeInsets.symmetric(
                        horizontal: _horizontalPadding,
                        vertical: 20,
                      ),
                      child: Align(
                        alignment: Alignment.topCenter,
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 720),
                          child: _content != null
                              ? NovelContentView(
                                  content: _content!,
                                  readResource: widget.readResource!,
                                  reloadResource: widget.reloadResource,
                                  textStyle: TextStyle(
                                    color: _readerTheme.fg,
                                    fontSize: _fontSize,
                                    height: _lineHeight,
                                  ),
                                )
                              : SelectableText(
                                  _text ?? '',
                                  style: TextStyle(
                                    color: _readerTheme.fg,
                                    fontSize: _fontSize,
                                    height: _lineHeight,
                                  ),
                                ),
                        ),
                      ),
                    ),
            ),
            if (!_loading && _error == null)
              ValueListenableBuilder<double>(
                valueListenable: _progress,
                builder: (context, progress, _) => NovelReaderProgress(
                  progress: progress,
                  backgroundColor: _readerTheme.bg,
                  foregroundColor: _readerTheme.fg,
                ),
              ),
          ],
        ),
      ),
    );
  }
}
