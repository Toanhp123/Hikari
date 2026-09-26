import 'package:flutter/material.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';

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
  late Future<List<LibraryEntry>> _entries;
  @override
  void initState() {
    super.initState();
    _entries = widget.repository.loadAll();
  }

  void _reload() => setState(() {
    _entries = widget.repository.loadAll();
  });

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Library')),
    body: SafeArea(
      child: FutureBuilder<List<LibraryEntry>>(
        future: _entries,
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
                    onPressed: _reload,
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
                    onChanged: _reload,
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
  bool? _saved;
  bool _busy = false;
  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(LibraryButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.media.source != widget.media.source) {
      _saved = null;
      _load();
    }
  }

  Future<void> _load() async {
    final ref = widget.media.source;
    try {
      final saved = await widget.repository.contains(ref);
      if (mounted && widget.media.source == ref) setState(() => _saved = saved);
    } catch (_) {
      if (mounted) setState(() => _saved = null);
    }
  }

  Future<void> _toggle() async {
    setState(() => _busy = true);
    try {
      final saved = await widget.repository.contains(widget.media.source);
      if (saved) {
        await widget.repository.remove(widget.media.source);
      } else {
        await widget.repository.upsert(
          LibraryEntry(media: widget.media, addedAt: DateTime.now().toUtc()),
        );
      }
      if (!mounted) return;
      setState(() => _saved = !saved);
      widget.onChanged?.call();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not update your library.')),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => IconButton(
    tooltip: _saved == null
        ? 'Retry library status'
        : _saved!
        ? 'Remove from library'
        : 'Add to library',
    onPressed: _busy
        ? null
        : _saved == null
        ? _load
        : _toggle,
    icon: _busy
        ? const SizedBox(
            width: 24,
            height: 24,
            child: CircularProgressIndicator(strokeWidth: 2),
          )
        : Icon(
            _saved == null
                ? Icons.refresh
                : _saved!
                ? Icons.bookmark
                : Icons.bookmark_add_outlined,
          ),
  );
}
