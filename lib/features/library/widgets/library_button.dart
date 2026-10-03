import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_button_view_model.dart';

class LibraryButton extends StatefulWidget {
  const LibraryButton({
    super.key,
    required this.repository,
    required this.media,
    this.onChanged,
  });

  final LibraryRepository repository;
  final Media media;
  final VoidCallback? onChanged;

  @override
  State<LibraryButton> createState() => _LibraryButtonState();
}

class _LibraryButtonState extends State<LibraryButton> {
  late LibraryButtonViewModel _viewModel;

  @override
  void initState() {
    super.initState();
    _viewModel = LibraryButtonViewModel(widget.repository, widget.media);
  }

  @override
  void didUpdateWidget(LibraryButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.repository == widget.repository &&
        oldWidget.media.source == widget.media.source &&
        oldWidget.media.title == widget.media.title &&
        oldWidget.media.type == widget.media.type) {
      return;
    }
    _viewModel.dispose();
    _viewModel = LibraryButtonViewModel(widget.repository, widget.media);
  }

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  Future<void> _toggle() async {
    final updated = await _viewModel.toggle();
    if (!mounted) return;
    if (updated) {
      widget.onChanged?.call();
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Could not update your library.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) {
        final state = _viewModel.state;
        return IconButton(
          tooltip: state.saved == null
              ? 'Retry library status'
              : state.saved!
              ? 'Remove from library'
              : 'Add to library',
          onPressed: state.busy
              ? null
              : state.saved == null
              ? () => unawaited(_viewModel.load())
              : () => unawaited(_toggle()),
          icon: state.busy
              ? const SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : Icon(
                  state.saved == null
                      ? Icons.refresh
                      : state.saved!
                      ? Icons.bookmark
                      : Icons.bookmark_add_outlined,
                ),
        );
      },
    );
  }
}
