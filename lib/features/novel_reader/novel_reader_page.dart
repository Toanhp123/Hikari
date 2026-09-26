import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/resume.dart';

class NovelReaderPage extends StatefulWidget {
  const NovelReaderPage({
    super.key,
    required this.title,
    required this.loadText,
    this.initialProgress,
    this.saveProgress,
  });

  final MediaProgress? initialProgress;
  final Future<void> Function(ProgressPosition, bool)? saveProgress;

  final String title;
  final Future<String> Function() loadText;

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

  void _changed() {
    if (!_restored || !_scroll.hasClients) return;
    _position = textProgression(
      _scroll.offset,
      _scroll.position.maxScrollExtent,
    );
    _completed = textAtEnd(_scroll.offset, _scroll.position.maxScrollExtent);
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
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  String? _text;
  Object? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _scroll.addListener(_changed);
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final text = await widget.loadText();
      if (!mounted) return;
      setState(() {
        _text = text;
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
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      body: SafeArea(
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : _error != null
            ? Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text('Could not load this novel.'),
                    TextButton(
                      onPressed: _load,
                      child: const Text('Try again'),
                    ),
                  ],
                ),
              )
            : SingleChildScrollView(
                controller: _scroll,
                padding: const EdgeInsets.all(20),
                child: Align(
                  alignment: Alignment.topCenter,
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 720),
                    child: SelectableText(
                      _text ?? '',
                      style: Theme.of(context).textTheme.bodyLarge
                          ?.copyWith(height: 1.6),
                    ),
                  ),
                ),
              ),
      ),
    );
  }
}
