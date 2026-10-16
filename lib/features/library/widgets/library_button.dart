import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_button_view_model.dart';
import 'package:hikari/features/library/widgets/library_removal_confirmation.dart';

class LibraryButton extends StatefulWidget {
  const LibraryButton({
    super.key,
    required this.repository,
    required this.media,
    this.artwork,
    this.onChanged,
  });

  final LibraryRepository repository;
  final Media media;
  final SourceMediaRef? artwork;
  final VoidCallback? onChanged;

  @override
  State<LibraryButton> createState() => _LibraryButtonState();
}

class _LibraryButtonState extends State<LibraryButton> {
  late LibraryButtonViewModel _viewModel;
  bool _confirmingRemoval = false;

  @override
  void initState() {
    super.initState();
    _viewModel = LibraryButtonViewModel(
      widget.repository,
      widget.media,
      artwork: widget.artwork,
    );
  }

  @override
  void didUpdateWidget(LibraryButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.repository == widget.repository &&
        oldWidget.media.source == widget.media.source &&
        oldWidget.media.title == widget.media.title &&
        oldWidget.media.type == widget.media.type) {
      _viewModel.updateArtwork(widget.artwork);
      return;
    }
    _viewModel.dispose();
    _viewModel = LibraryButtonViewModel(
      widget.repository,
      widget.media,
      artwork: widget.artwork,
    );
  }

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  Future<void> _handlePressed() async {
    if (_confirmingRemoval) return;
    final viewModel = _viewModel;
    if (viewModel.state.saved == true) {
      setState(() => _confirmingRemoval = true);
      try {
        final confirmed = await confirmLibraryRemoval(
          context,
          title: widget.media.title,
        );
        if (!mounted || viewModel != _viewModel || !confirmed) return;
        await _updateMembership(viewModel, viewModel.remove());
      } finally {
        if (mounted) setState(() => _confirmingRemoval = false);
      }
      return;
    }
    await _updateMembership(viewModel, viewModel.add());
  }

  Future<void> _updateMembership(
    LibraryButtonViewModel viewModel,
    Future<bool> update,
  ) async {
    final updated = await update;
    if (!mounted || viewModel != _viewModel) return;
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
          onPressed: state.busy || _confirmingRemoval
              ? null
              : state.saved == null
              ? () => unawaited(_viewModel.load())
              : () => unawaited(_handlePressed()),
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
