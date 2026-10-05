import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/application/media/prefetch_novel_chapter.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/novel_reader/novel_reader_page.dart';
import 'package:hikari/features/remote_novel/novel_chapter_reader_view_model.dart';

class NovelChapterReaderPage extends StatefulWidget {
  const NovelChapterReaderPage({
    super.key,
    required this.viewModel,
    required this.reloadContent,
    required this.readResource,
    required this.reloadResource,
    required this.createPrefetch,
    required this.prefetchChapter,
  });

  final NovelChapterReaderViewModel viewModel;
  final Future<RichReadingContent> Function(NovelChapterSource, SourceMediaRef)
  reloadContent;
  final Future<Uint8List> Function(NovelChapterSource, SourceMediaRef)
  readResource;
  final Future<Uint8List> Function(NovelChapterSource, SourceMediaRef)
  reloadResource;
  final PrefetchNovelChapter Function() createPrefetch;
  final Future<void> Function(PrefetchNovelChapter, NovelChapter)
  prefetchChapter;

  @override
  State<NovelChapterReaderPage> createState() => _NovelChapterReaderPageState();
}

class _NovelChapterReaderPageState extends State<NovelChapterReaderPage> {
  bool _closed = false;
  late final NovelChapterReaderViewModel _viewModel = widget.viewModel;
  late final PrefetchNovelChapter _prefetch;
  SourceMediaRef? _prefetchOrigin;

  @override
  void initState() {
    super.initState();
    _prefetch = widget.createPrefetch();
    _viewModel.addListener(_scheduleNextChapter);
    WidgetsBinding.instance.addPostFrameCallback((_) => _scheduleNextChapter());
  }

  void _scheduleNextChapter() {
    if (_closed) return;
    final current = _viewModel.state.target.chapter.source;
    if (_prefetchOrigin == current) return;
    _prefetchOrigin = current;
    _prefetch.cancelPending();
    final next = _viewModel.nextChapter;
    if (next != null) {
      unawaited(widget.prefetchChapter(_prefetch, next));
    }
  }

  void _close() {
    if (_closed) return;
    _closed = true;
    _prefetch.cancelPending();
    _viewModel.removeListener(_scheduleNextChapter);
    _viewModel.close();
  }

  Future<void> _move(Future<void> Function() action) async {
    if (_closed) return;
    _prefetch.cancelPending();
    try {
      await action();
    } catch (_) {
      if (!_closed && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Could not open this chapter. Try again.'),
          ),
        );
      }
    }
  }

  @override
  void dispose() {
    _close();
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => PopScope(
    onPopInvokedWithResult: (didPop, _) {
      if (didPop) _close();
    },
    child: ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) {
        final state = _viewModel.state;
        final target = state.target;
        final chapter = target.chapter;
        return NovelReaderPage(
          key: ValueKey(chapter.source),
          title: chapter.title,
          loadContent: () async => target.content,
          reloadContent: () =>
              widget.reloadContent(target.source, chapter.source),
          readResource: (resource) =>
              widget.readResource(target.source, resource),
          reloadResource: (resource) =>
              widget.reloadResource(target.source, resource),
          initialProgress: target.progress.initialProgress,
          saveProgress: target.progress.save,
          onPreviousChapter: !_closed && _viewModel.canOpenPrevious
              ? () => _move(_viewModel.openPrevious)
              : null,
          onNextChapter: !_closed && _viewModel.canOpenNext
              ? () => _move(_viewModel.openNext)
              : null,
          chapterNavigationLoading: state.openingAdjacent,
        );
      },
    ),
  );
}
