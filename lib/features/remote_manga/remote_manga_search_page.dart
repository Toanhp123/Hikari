import 'package:flutter/material.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/features/library/library_page.dart';

class RemoteMangaSearchPage extends StatefulWidget {
  const RemoteMangaSearchPage({
    super.key,
    required this.sources,
    required this.openMedia,
    this.library,
  }) : assert(sources.length > 0);

  final List<MangaSearchSource> sources;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;

  @override
  State<RemoteMangaSearchPage> createState() => _RemoteMangaSearchPageState();
}

class _RemoteMangaSearchPageState extends State<RemoteMangaSearchPage> {
  final _queryController = TextEditingController();
  late MangaSearchSource _selectedSource;
  List<Media>? _results;
  bool _isSearching = false;
  bool _searchFailed = false;

  @override
  void initState() {
    super.initState();
    _selectedSource = widget.sources.first;
  }

  @override
  void dispose() {
    _queryController.dispose();
    super.dispose();
  }

  void _selectSource(SourceId? sourceId) {
    if (sourceId == null || _isSearching || sourceId == _selectedSource.id) {
      return;
    }
    final source = widget.sources.firstWhere((source) => source.id == sourceId);
    setState(() {
      _selectedSource = source;
      _results = null;
      _searchFailed = false;
    });
  }

  Future<void> _search() async {
    final query = _queryController.text.trim();
    if (_isSearching || query.isEmpty) return;
    final source = _selectedSource;
    setState(() {
      _isSearching = true;
      _searchFailed = false;
    });

    try {
      final results = await source.search(query);
      if (mounted && identical(source, _selectedSource)) {
        setState(() => _results = results);
      }
    } catch (_) {
      if (mounted && identical(source, _selectedSource)) {
        setState(() => _searchFailed = true);
      }
    } finally {
      if (mounted && identical(source, _selectedSource)) {
        setState(() => _isSearching = false);
      }
    }
  }

  Widget _buildSourcePicker() => DropdownButton<SourceId>(
    value: _selectedSource.id,
    isExpanded: true,
    onChanged: _isSearching ? null : _selectSource,
    items: [
      for (final source in widget.sources)
        DropdownMenuItem(value: source.id, child: Text(source.name)),
    ],
  );

  Widget _buildResults() {
    if (_isSearching) return const Center(child: CircularProgressIndicator());
    if (_searchFailed) {
      return const Center(
        child: Text(
          'Could not search. Check source access or rate limits and try again.',
        ),
      );
    }
    final results = _results;
    if (results == null) {
      return const Center(child: Text('Search for an English manga title.'));
    }
    if (results.isEmpty) return const Center(child: Text('No manga found.'));

    return ListView.builder(
      itemCount: results.length,
      itemBuilder: (context, index) {
        final media = results[index];
        return ListTile(
          key: ValueKey(media.source),
          title: Text(media.title),
          subtitle: Text(_selectedSource.name),
          onTap: () => widget.openMedia(context, media),
          trailing: widget.library == null
              ? null
              : LibraryButton(repository: widget.library!, media: media),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Manga search')),
    body: SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            if (widget.sources.length > 1) _buildSourcePicker(),
            TextField(
              controller: _queryController,
              enabled: !_isSearching,
              textInputAction: TextInputAction.search,
              onSubmitted: (_) => _search(),
              decoration: InputDecoration(
                labelText: widget.sources.length == 1
                    ? 'Manga title · ${_selectedSource.name}'
                    : 'Manga title',
                border: const OutlineInputBorder(),
              ),
            ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: _isSearching ? null : _search,
                icon: const Icon(Icons.search),
                label: const Text('Search'),
              ),
            ),
            Expanded(child: _buildResults()),
          ],
        ),
      ),
    ),
  );
}
