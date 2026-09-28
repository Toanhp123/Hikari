import 'package:flutter/material.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_page.dart';
import 'package:hikari/features/remote_manga/remote_manga_search_view_model.dart';

class RemoteMangaSearchPage extends StatefulWidget {
  const RemoteMangaSearchPage({
    super.key,
    required this.searchManga,
    required this.openMedia,
    this.library,
  });

  final SearchManga searchManga;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;

  @override
  State<RemoteMangaSearchPage> createState() => _RemoteMangaSearchPageState();
}

class _RemoteMangaSearchPageState extends State<RemoteMangaSearchPage> {
  final _queryController = TextEditingController();
  late final RemoteMangaSearchViewModel _viewModel;

  @override
  void initState() {
    super.initState();
    _viewModel = RemoteMangaSearchViewModel(searchManga: widget.searchManga);
  }

  @override
  void dispose() {
    _queryController.dispose();
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: _viewModel,
    builder: (context, _) => Scaffold(
      appBar: AppBar(title: const Text('Manga search')),
      body: _viewModel.sources.isEmpty
          ? const Center(child: Text('No manga search source is available.'))
          : SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    if (_viewModel.sources.length > 1) _buildSourcePicker(),
                    TextField(
                      controller: _queryController,
                      enabled: _viewModel.state is! RemoteMangaSearchLoading,
                      textInputAction: TextInputAction.search,
                      onSubmitted: _viewModel.search,
                      decoration: InputDecoration(
                        labelText: _viewModel.sources.length == 1
                            ? 'Manga title · ${_viewModel.selectedSource!.name}'
                            : 'Manga title',
                        border: const OutlineInputBorder(),
                      ),
                    ),
                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton.icon(
                        onPressed: _viewModel.state is RemoteMangaSearchLoading
                            ? null
                            : () => _viewModel.search(_queryController.text),
                        icon: const Icon(Icons.search),
                        label: const Text('Search'),
                      ),
                    ),
                    Expanded(child: _buildResults(_viewModel.state)),
                  ],
                ),
              ),
            ),
    ),
  );

  Widget _buildSourcePicker() => DropdownButton<SourceId>(
    value: _viewModel.selectedSource!.id,
    isExpanded: true,
    onChanged: _viewModel.state is RemoteMangaSearchLoading
        ? null
        : _viewModel.selectSource,
    items: [
      for (final source in _viewModel.sources)
        DropdownMenuItem(value: source.id, child: Text(source.name)),
    ],
  );

  Widget _buildResults(RemoteMangaSearchUiState state) => switch (state) {
    RemoteMangaSearchIdle() => const Center(
      child: Text('Search for a manga title.'),
    ),
    RemoteMangaSearchLoading() => const Center(
      child: CircularProgressIndicator(),
    ),
    RemoteMangaSearchFailure() => const Center(
      child: Text(
        'Could not search. Check source access or rate limits and try again.',
      ),
    ),
    RemoteMangaSearchReady(:final results) when results.isEmpty => const Center(
      child: Text('No manga found.'),
    ),
    RemoteMangaSearchReady(:final results) => ListView.builder(
      itemCount: results.length,
      itemBuilder: (context, index) {
        final preview = results[index];
        final media = preview.media;
        return ListTile(
          key: ValueKey(media.source),
          title: Text(media.title),
          subtitle: Text(_viewModel.selectedSource!.name),
          onTap: () => widget.openMedia(context, media),
          trailing: widget.library == null
              ? null
              : LibraryButton(repository: widget.library!, media: media),
        );
      },
    ),
  };
}
