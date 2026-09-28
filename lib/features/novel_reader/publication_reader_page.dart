import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/publication.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/novel_reader/novel_content_view.dart';

final class PublicationReaderPage extends StatefulWidget {
  const PublicationReaderPage({
    super.key,
    required this.title,
    required this.publication,
    required this.source,
    this.initialProgress,
    this.saveProgress,
  });

  final String title;
  final SourceMediaRef publication;
  final PublicationSource source;
  final MediaProgress? initialProgress;
  final Future<void> Function(ProgressPosition, bool)? saveProgress;

  @override
  State<PublicationReaderPage> createState() => _PublicationReaderPageState();
}

final class _PublicationReaderPageState extends State<PublicationReaderPage>
    with WidgetsBindingObserver {
  final _scroll = ScrollController();
  Timer? _debounce;
  Publication? _book;
  NovelChapterContent? _content;
  Object? _error;
  String? _resource;
  int _index = 0;
  double _progression = 0;
  bool _restored = false;
  bool _loading = true;
  bool _completed = false;
  (String, double, bool)? _lastSaved;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _scroll.addListener(_changed);
    _loadPublication();
  }

  @override
  void dispose() {
    unawaited(_flush());
    _debounce?.cancel();
    _scroll.dispose();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) unawaited(_flush());
  }

  Future<void> _loadPublication() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final book = await widget.source.publication(widget.publication);
      if (!mounted) return;
      final position = widget.initialProgress?.position;
      final requested = position is DocumentPosition ? position.resource : null;
      final index = requested == null
          ? 0
          : book.spine.indexWhere((section) => section.resource == requested);
      setState(() {
        _book = book;
        _index = index < 0 ? 0 : index;
        _loading = false;
      });
      await _loadSection(book.spine[_index].resource, restore: true);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  Future<void> _loadSection(String resource, {bool restore = false}) async {
    final book = _book;
    if (book == null) return;
    setState(() {
      _loading = true;
      _error = null;
      _content = null;
      _resource = resource;
    });
    try {
      final content = await widget.source.readSection(widget.publication, resource);
      if (!mounted || _resource != resource) return;
      setState(() {
        _content = content;
        _loading = false;
      });
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || _resource != resource || !_scroll.hasClients) return;
        final position = widget.initialProgress?.position;
        final progression = restore && position is DocumentPosition &&
                position.resource == resource
            ? position.progression ?? 0
            : 0;
        _scroll.jumpTo(progression * _scroll.position.maxScrollExtent);
        _progression = _currentProgression();
        _completed = _index == book.spine.length - 1 && _atEnd();
        _restored = true;
        _lastSaved = (resource, _progression, _completed);
      });
    } catch (error) {
      if (!mounted || _resource != resource) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  void _changed() {
    if (!_restored || !_scroll.hasClients) return;
    _progression = _currentProgression();
    _completed = _index == (_book?.spine.length ?? 1) - 1 && _atEnd();
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 500), _flush);
  }

  double _currentProgression() {
    final max = _scroll.position.maxScrollExtent;
    return max <= 0 ? 1 : (_scroll.offset / max).clamp(0.0, 1.0);
  }

  bool _atEnd() => _currentProgression() >= 0.999;

  Future<void> _flush() async {
    _debounce?.cancel();
    final resource = _resource;
    if (!_restored || resource == null || _content == null) return;
    final value = (resource, _progression, _completed);
    if (_lastSaved == value) return;
    _lastSaved = value;
    try {
      await widget.saveProgress?.call(
        DocumentPosition(resource: resource, progression: _progression),
        _completed,
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

  Future<void> _select(int index) async {
    final book = _book;
    if (book == null || index < 0 || index >= book.spine.length) return;
    await _flush();
    setState(() => _index = index);
    _restored = false;
    await _loadSection(book.spine[index].resource);
  }

  Future<bool> _tapLink(String url) async {
    final uri = Uri.tryParse(url);
    if (uri == null || uri.hasScheme || uri.host.isNotEmpty) return false;
    final book = _book;
    if (book == null) return false;
    final target = uri.path.isEmpty ? _resource : uri.path;
    final index = book.spine.indexWhere((section) => section.resource == target);
    if (index < 0) return false;
    await _select(index);
    return false;
  }

  @override
  Widget build(BuildContext context) {
    final book = _book;
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
        actions: [
          IconButton(
            tooltip: 'Table of contents',
            icon: const Icon(Icons.list),
            onPressed: book == null ? null : () => _showToc(book),
          ),
        ],
      ),
      body: SafeArea(child: _body(book)),
      bottomNavigationBar: book == null
          ? null
          : SafeArea(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  IconButton(
                    tooltip: 'Previous section',
                    onPressed: _index == 0 ? null : () => _select(_index - 1),
                    icon: const Icon(Icons.chevron_left),
                  ),
                  Text('${_index + 1} of ${book.spine.length}'),
                  IconButton(
                    tooltip: 'Next section',
                    onPressed: _index == book.spine.length - 1
                        ? null
                        : () => _select(_index + 1),
                    icon: const Icon(Icons.chevron_right),
                  ),
                ],
              ),
            ),
    );
  }

  Widget _body(Publication? book) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Could not load this section.'),
            TextButton(onPressed: _retry, child: const Text('Try again')),
          ],
        ),
      );
    }
    final content = _content;
    if (content == null || book == null) return const SizedBox.shrink();
    return SingleChildScrollView(
      controller: _scroll,
      padding: const EdgeInsets.all(20),
      child: Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: NovelContentView(
            content: content,
            readResource: widget.source.readResource,
            onTapLink: _tapLink,
          ),
        ),
      ),
    );
  }

  Future<void> _retry() async {
    final resource = _resource;
    if (resource != null) await _loadSection(resource);
  }

  void _showToc(Publication book) {
    showModalBottomSheet<void>(
      context: context,
      builder: (context) => SafeArea(
        child: ListView.builder(
          itemCount: book.toc.length,
          itemBuilder: (context, index) {
            final link = book.toc[index];
            return ListTile(
              title: Text(link.label),
              onTap: () {
                Navigator.pop(context);
                final target = book.spine.indexWhere(
                  (section) => section.resource == link.resource,
                );
                if (target >= 0) unawaited(_select(target));
              },
            );
          },
        ),
      ),
    );
  }
}
