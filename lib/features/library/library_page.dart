import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/application/progress/load_continue_reading.dart';
import 'package:hikari/core/ui/controls/hikari_icon_button.dart';
import 'package:hikari/core/ui/layout/hikari_page_scaffold.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_view_model.dart';
import 'package:hikari/features/library/widgets/library_content.dart';
import 'package:hikari/features/library/widgets/library_removal_confirmation.dart';

class LibraryPage extends StatefulWidget {
  const LibraryPage({
    super.key,
    required this.repository,
    required this.openMedia,
    this.readArtwork,
    this.loadContinueReading,
    this.progressRevision = 0,
    this.onNavigateToSearch,
  });

  final LibraryRepository repository;
  final LoadContinueReading? loadContinueReading;
  final int progressRevision;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;
  final void Function(BuildContext, Media) openMedia;
  final VoidCallback? onNavigateToSearch;

  @override
  State<LibraryPage> createState() => _LibraryPageState();
}

class _LibraryPageState extends State<LibraryPage> {
  late final LibraryViewModel _viewModel = LibraryViewModel(
    widget.repository,
    loadContinueReading: widget.loadContinueReading,
  );

  @override
  void didUpdateWidget(LibraryPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.progressRevision != widget.progressRevision) {
      _viewModel.refreshProgress();
    }
  }

  final _pendingRemovals = <SourceMediaRef>{};

  Future<void> _confirmRemove(LibraryEntry entry) async {
    if (!_pendingRemovals.add(entry.media.source)) return;
    try {
      await _removeAfterConfirmation(entry);
    } finally {
      _pendingRemovals.remove(entry.media.source);
    }
  }

  Future<void> _removeAfterConfirmation(LibraryEntry entry) async {
    final confirmed = await confirmLibraryMembershipChange(
      context,
      title: entry.media.title,
      removing: true,
    );
    if (!confirmed || !mounted) return;
    try {
      await _viewModel.remove(entry.media.source);
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Could not remove this title. Try again.'),
        ),
      );
    }
  }

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) {
        final state = _viewModel.state;
        return HikariPageScaffold(
          useSafeArea: true,
          appBar: AppBar(
            title: const Text('Library'),
            actions: [
              if (state.status == LibraryStatus.ready &&
                  state.entries.isNotEmpty) ...[
                HikariIconButton(
                  icon: Icon(
                    state.viewMode == LibraryViewMode.grid
                        ? Icons.view_list_rounded
                        : Icons.grid_view_rounded,
                  ),
                  tooltip: state.viewMode == LibraryViewMode.grid
                      ? 'Switch to list view'
                      : 'Switch to grid view',
                  onPressed: _viewModel.toggleViewMode,
                ),
                const SizedBox(width: HikariSpacing.xs),
              ],
            ],
          ),
          body: LibraryContent(
            state: state,
            readArtwork: widget.readArtwork,
            openMedia: widget.openMedia,
            onReload: _viewModel.reload,
            onSelectMediaType: _viewModel.selectMediaType,
            onRemove: _confirmRemove,
            onBrowseCatalog: widget.onNavigateToSearch,
          ),
        );
      },
    );
  }
}
