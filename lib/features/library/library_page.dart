import 'package:flutter/material.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_button.dart';

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
  late Future<List<LibraryEntry>> _entriesFuture;
  @override
  void initState() {
    super.initState();
    _entriesFuture = widget.repository.loadAll();
  }

  void _reloadLibrary() => setState(() {
    _entriesFuture = widget.repository.loadAll();
  });

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Library')),
    body: SafeArea(
      child: FutureBuilder<List<LibraryEntry>>(
        future: _entriesFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('Could not load your library.'),
                  TextButton(
                    onPressed: _reloadLibrary,
                    child: const Text('Try again'),
                  ),
                ],
              ),
            );
          }
          final entries = snapshot.data!;
          if (entries.isEmpty) {
            return const Center(child: Text('Your library is empty.'));
          }
          return ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: entries.length,
            itemBuilder: (context, index) {
              final media = entries[index].media;
              return Card(
                child: ListTile(
                  title: Text(media.title),
                  subtitle: Text(switch (media.type) {
                    MediaType.anime => 'Anime',
                    MediaType.manga => 'Manga',
                    MediaType.lightNovel => 'Light Novel',
                  }),
                  onTap: () => widget.openMedia(context, media),
                  trailing: LibraryButton(
                    repository: widget.repository,
                    media: media,
                    onChanged: _reloadLibrary,
                  ),
                ),
              );
            },
          );
        },
      ),
    ),
  );
}
