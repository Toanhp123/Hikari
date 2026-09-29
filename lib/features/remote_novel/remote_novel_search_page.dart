import 'package:flutter/material.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_page.dart';
import 'package:hikari/features/remote_novel/remote_novel_search_view_model.dart';

class RemoteNovelSearchPage extends StatefulWidget {
  const RemoteNovelSearchPage({
    super.key,
    required this.searchNovels,
    required this.openMedia,
    this.library,
  });
  final SearchNovels searchNovels;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  @override
  State<RemoteNovelSearchPage> createState() => _RemoteNovelSearchPageState();
}

class _RemoteNovelSearchPageState extends State<RemoteNovelSearchPage> {
  final _query = TextEditingController();
  late final _model = RemoteNovelSearchViewModel(widget.searchNovels);
  @override
  void dispose() {
    _query.dispose();
    _model.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: _model,
    builder: (context, _) => Scaffold(
      appBar: AppBar(title: const Text('Novel search')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: _model.sources.isEmpty
              ? const Center(
                  child: Text('No novel search source is available.'),
                )
              : Column(
                  children: [
                    if (_model.sources.length > 1)
                      DropdownButton<SourceId>(
                        value: _model.selectedSource!.id,
                        isExpanded: true,
                        onChanged: _model.selectSource,
                        items: [
                          for (final source in _model.sources)
                            DropdownMenuItem(
                              value: source.id,
                              child: Text(source.name),
                            ),
                        ],
                      ),
                    TextField(
                      controller: _query,
                      textInputAction: TextInputAction.search,
                      onSubmitted: _model.search,
                      decoration: InputDecoration(
                        labelText:
                            'Novel title · ${_model.selectedSource!.name}',
                        border: const OutlineInputBorder(),
                      ),
                    ),
                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton.icon(
                        onPressed: () => _model.search(_query.text),
                        icon: const Icon(Icons.search),
                        label: const Text('Search'),
                      ),
                    ),
                    Expanded(child: _results()),
                  ],
                ),
        ),
      ),
    ),
  );
  Widget _results() {
    if (_model.loading) return const Center(child: CircularProgressIndicator());
    if (_model.failed) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Could not search. Check source access and try again.'),
            TextButton(onPressed: _model.retry, child: const Text('Try again')),
          ],
        ),
      );
    }
    if (!_model.searched) {
      return const Center(child: Text('Search for a novel title.'));
    }
    return ListView.builder(
      itemCount: _model.results.length + 1,
      itemBuilder: (context, index) {
        if (index == _model.results.length) {
          return Column(
            children: [
              if (_model.results.isEmpty) const Text('No novels found.'),
              if (_model.loadingMore)
                const Center(child: CircularProgressIndicator())
              else if (_model.hasNextPage != false)
                TextButton(
                  onPressed: _model.loadMore,
                  child: Text(
                    _model.pageFailed ? 'Retry next page' : 'Load more',
                  ),
                ),
            ],
          );
        }
        final preview = _model.results[index];
        return ListTile(
          key: ValueKey(preview.media.source),
          title: Text(preview.media.title),
          subtitle: Text(
            [
              _model.selectedSource!.name,
              ...preview.metadata.authors,
            ].join(' · '),
          ),
          onTap: () => widget.openMedia(context, preview.media),
          trailing: widget.library == null
              ? null
              : LibraryButton(
                  repository: widget.library!,
                  media: preview.media,
                ),
        );
      },
    );
  }
}
