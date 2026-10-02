import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_view_model.dart';
import 'package:hikari/features/library/widgets/library_content.dart';

class LibraryPage extends StatefulWidget {
  const LibraryPage({
    super.key,
    required this.repository,
    required this.openMedia,
  });

  final LibraryRepository repository;
  final void Function(BuildContext, Media) openMedia;

  @override
  State<LibraryPage> createState() => _LibraryPageState();
}

class _LibraryPageState extends State<LibraryPage> {
  late final LibraryViewModel _viewModel = LibraryViewModel(widget.repository);

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) {
        final state = _viewModel.state;
        return HikariScaffold(
          useSafeArea: true,
          appBar: AppBar(
            title: Text(
              'Library',
              style: HikariTypography.titleLarge.copyWith(
                color: colors.textPrimary,
                fontWeight: FontWeight.w700,
              ),
            ),
            actions: [
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
          ),
          body: LibraryContent(
            state: state,
            repository: widget.repository,
            openMedia: widget.openMedia,
            onReload: _viewModel.reload,
            onSelectMediaType: _viewModel.selectMediaType,
            onRepositoryChanged: _viewModel.repositoryChanged,
          ),
        );
      },
    );
  }
}
