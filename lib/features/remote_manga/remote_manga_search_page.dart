import 'package:flutter/material.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/features/library/library_page.dart';

class RemoteMangaSearchPage extends StatefulWidget {
  const RemoteMangaSearchPage({
    super.key,
    required this.sourceName,
    required this.search,
    required this.openMedia,
    this.library,
  });
  final String sourceName;
  final Future<List<Media>> Function(String) search;
  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  @override
  State<RemoteMangaSearchPage> createState() => _RemoteMangaSearchPageState();
}

class _RemoteMangaSearchPageState extends State<RemoteMangaSearchPage> {
  final _query = TextEditingController();
  List<Media>? _results;
  bool _loading = false;
  bool _failed = false;
  @override
  void dispose() {
    _query.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    if (_loading || _query.text.trim().isEmpty) return;
    setState(() {
      _loading = true;
      _failed = false;
    });
    try {
      final results = await widget.search(_query.text.trim());
      if (mounted) setState(() => _results = results);
    } catch (_) {
      if (mounted) setState(() => _failed = true);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.sourceName)),
    body: SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: _query,
              enabled: !_loading,
              textInputAction: TextInputAction.search,
              onSubmitted: (_) => _search(),
              decoration: const InputDecoration(
                labelText: 'Manga title',
                border: OutlineInputBorder(),
              ),
            ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: _loading ? null : _search,
                icon: const Icon(Icons.search),
                label: const Text('Search'),
              ),
            ),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _failed
                  ? const Center(
                      child: Text(
                        'Could not search. Check source access or rate limits and try again.',
                      ),
                    )
                  : _results == null
                  ? const Center(
                      child: Text('Search for an English manga title.'),
                    )
                  : _results!.isEmpty
                  ? const Center(child: Text('No manga found.'))
                  : ListView.builder(
                      itemCount: _results!.length,
                      itemBuilder: (context, index) {
                        final media = _results![index];
                        return ListTile(
                          key: ValueKey(media.source),
                          title: Text(media.title),
                          subtitle: Text(widget.sourceName),
                          onTap: () => widget.openMedia(context, media),
                          trailing: widget.library == null
                              ? null
                              : LibraryButton(
                                  repository: widget.library!,
                                  media: media,
                                ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    ),
  );
}
