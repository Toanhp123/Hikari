import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
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
  });

  final NovelChapterReaderViewModel viewModel;
  final Future<RichReadingContent> Function(NovelChapterSource, SourceMediaRef)
  reloadContent;
  final Future<Uint8List> Function(NovelChapterSource, SourceMediaRef)
  readResource;
  final Future<Uint8List> Function(NovelChapterSource, SourceMediaRef)
  reloadResource;

  @override
  State<NovelChapterReaderPage> createState() => _NovelChapterReaderPageState();
}

class _NovelChapterReaderPageState extends State<NovelChapterReaderPage> {
  bool _closed = false;
  late final NovelChapterReaderViewModel _viewModel = widget.viewModel;

  void _close() {
    if (_closed) return;
    _closed = true;
    _viewModel.close();
  }

  Future<void> _move(Future<void> Function() action) async {
    if (_closed) return;
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
              ? () => _move(_viewModel.previous)
              : null,
          onNextChapter: !_closed && _viewModel.canOpenNext
              ? () => _move(_viewModel.next)
              : null,
          chapterNavigationLoading: state.openingAdjacent,
        );
      },
    ),
  );
}
