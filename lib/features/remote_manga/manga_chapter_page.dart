import 'package:flutter/material.dart';
import 'package:hikari/domain/media/source.dart';

class MangaChapterPage extends StatefulWidget {
  const MangaChapterPage({
    super.key,
    required this.title,
    required this.sourceName,
    required this.loadChapters,
    required this.openChapter,
  });
  final String title, sourceName;
  final Future<List<MangaChapter>> Function() loadChapters;
  final Future<void> Function(BuildContext, MangaChapter) openChapter;
  @override
  State<MangaChapterPage> createState() => _MangaChapterPageState();
}

class _MangaChapterPageState extends State<MangaChapterPage> {
  late Future<List<MangaChapter>> _chapters;
  bool _opening = false;
  @override
  void initState() {
    super.initState();
    _chapters = Future.sync(widget.loadChapters);
  }

  Future<void> _open(MangaChapter chapter) async {
    if (_opening) return;
    setState(() => _opening = true);
    try {
      await widget.openChapter(context, chapter);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Could not open chapter or saved progress.'),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _opening = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.title)),
    body: SafeArea(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(widget.sourceName),
          ),
          if (_opening) const LinearProgressIndicator(),
          Expanded(
            child: FutureBuilder<List<MangaChapter>>(
              future: _chapters,
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snapshot.hasError) {
                  return Center(
                    child: SingleChildScrollView(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Text(
                            'Could not load chapters. Check source access or rate limits.',
                          ),
                          TextButton(
                            onPressed: () => setState(() {
                              _chapters = Future.sync(widget.loadChapters);
                            }),
                            child: const Text('Try again'),
                          ),
                        ],
                      ),
                    ),
                  );
                }
                final chapters = snapshot.data!;
                if (chapters.isEmpty) {
                  return const Center(
                    child: Text('No readable English chapters found.'),
                  );
                }
                return ListView.builder(
                  itemCount: chapters.length,
                  itemBuilder: (context, index) {
                    final chapter = chapters[index];
                    return ListTile(
                      key: ValueKey(chapter.source),
                      title: Text(chapter.title),
                      subtitle: Text(chapter.scanlator ?? widget.sourceName),
                      enabled: !_opening,
                      onTap: () => _open(chapter),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    ),
  );
}
