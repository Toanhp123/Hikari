import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/prefetch_manga_pages.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';
import 'package:hikari/features/remote_manga/manga_chapter_reader_view_model.dart';

class MangaChapterReaderPage extends StatefulWidget {
  const MangaChapterReaderPage({
    super.key,
    required this.viewModel,
    required this.readPage,
    required this.reloadPage,
    required this.createPrefetch,
    required this.prefetchPages,
  });

  final MangaChapterReaderViewModel viewModel;
  final Future<Uint8List> Function(MangaPageSource, SourceMediaRef) readPage;
  final Future<Uint8List> Function(MangaPageSource, SourceMediaRef) reloadPage;
  final PrefetchMangaPages Function() createPrefetch;
  final Future<void> Function(
    PrefetchMangaPages,
    MangaPageSource,
    List<SourceMediaRef>,
    int,
  )
  prefetchPages;

  @override
  State<MangaChapterReaderPage> createState() => _MangaChapterReaderPageState();
}

class _MangaChapterReaderPageState extends State<MangaChapterReaderPage> {
  late final MangaChapterReaderViewModel _viewModel = widget.viewModel;
  late PrefetchMangaPages _prefetch;
  late MangaChapterOpenTarget _observedTarget;
  bool _closed = false;
  int _pageGeneration = 0;
  bool _exiting = false;
  bool _readyToPop = false;

  Future<void> _exit() async {
    if (_exiting) return;
    setState(() => _exiting = true);
    _closed = true;
    _viewModel.beginClose();
    _pageGeneration++;
    _prefetch.cancelPending();
    _viewModel.discardChapterPrefetch();
    _viewModel.removeListener(_targetChanged);
    await WidgetsBinding.instance.endOfFrame;
    try {
      await _viewModel.flush();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not save reading progress.')),
        );
      }
    } finally {
      _viewModel.close();
    }
    if (!mounted) return;
    setState(() => _readyToPop = true);
    await WidgetsBinding.instance.endOfFrame;
    if (mounted && (ModalRoute.of(context)?.isCurrent ?? false)) {
      await Navigator.of(context).maybePop();
    }
  }

  @override
  void initState() {
    super.initState();
    _observedTarget = _viewModel.state.target;
    _prefetch = widget.createPrefetch();
    _viewModel.addListener(_targetChanged);
  }

  void _targetChanged() {
    final target = _viewModel.state.target;
    if (identical(_observedTarget, target)) return;
    _prefetch.cancelPending();
    _viewModel.discardChapterPrefetch();
    _observedTarget = target;
    _prefetch = widget.createPrefetch();
  }

  void _close() {
    if (_closed) return;
    _closed = true;
    _pageGeneration++;
    _prefetch.cancelPending();
    _viewModel.discardChapterPrefetch();
    _viewModel.removeListener(_targetChanged);
    _viewModel.close();
  }

  Future<void> _move(Future<void> Function() action) async {
    if (_closed) return;
    _pageGeneration++;
    _prefetch.cancelPending();
    _viewModel.cancelChapterPrefetch();
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

  Future<void> _prefetchFrom({
    required MangaChapterOpenTarget target,
    required PrefetchMangaPages prefetch,
    required int generation,
    required int displayedIndex,
  }) async {
    await widget.prefetchPages(
      prefetch,
      target.source,
      target.pages,
      displayedIndex,
    );
    if (_closed ||
        _viewModel.state.openingAdjacent ||
        generation != _pageGeneration ||
        !identical(prefetch, _prefetch) ||
        !identical(target, _viewModel.state.target)) {
      return;
    }
    await _viewModel.prefetchNextChapter(
      target: target,
      displayedIndex: displayedIndex,
    );
  }

  @override
  void dispose() {
    _close();
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: _readyToPop,
    onPopInvokedWithResult: (didPop, _) {
      if (didPop) {
        _close();
      } else {
        _exit();
      }
    },
    child: ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) {
        final state = _viewModel.state;
        final target = state.target;
        final prefetch = _prefetch;
        final generation = _pageGeneration;
        return MangaReaderPage(
          key: ValueKey(target.chapter.source),
          title: target.chapter.title,
          credit: [
            target.source.name,
            if (target.chapter.scanlator != null) target.chapter.scanlator!,
          ].join(' · '),
          loadPages: () async => target.pages,
          readPage: (page) {
            prefetch.cancelPending();
            _viewModel.cancelChapterPrefetch();
            return widget.readPage(target.source, page);
          },
          reloadPage: (page) {
            prefetch.cancelPending();
            _viewModel.cancelChapterPrefetch();
            return widget.reloadPage(target.source, page);
          },
          onPageDisplayed: (index) {
            if (_closed ||
                _viewModel.state.openingAdjacent ||
                generation != _pageGeneration ||
                !identical(prefetch, _prefetch) ||
                !identical(target, _viewModel.state.target)) {
              return;
            }
            unawaited(
              _prefetchFrom(
                target: target,
                prefetch: prefetch,
                generation: generation,
                displayedIndex: index,
              ),
            );
          },
          onPreviousChapter: !_closed && _viewModel.canOpenPrevious
              ? () => _move(_viewModel.openPrevious)
              : null,
          onNextChapter: !_closed && _viewModel.canOpenNext
              ? () => _move(_viewModel.openNext)
              : null,
          chapterNavigationLoading: state.openingAdjacent,
          initialProgress: target.progress.initialProgress,
          saveProgress: (position, completed) =>
              _viewModel.saveProgress(target, position, completed),
          registerFlush: _viewModel.registerFlush,
          closing: _exiting,
        );
      },
    ),
  );
}
