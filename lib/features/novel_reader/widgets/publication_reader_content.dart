import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_widget_from_html_core/flutter_widget_from_html_core.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/publication.dart';
import 'package:hikari/features/novel_reader/widgets/novel_content_view.dart';

class PublicationReaderContent extends StatelessWidget {
  const PublicationReaderContent({
    super.key,
    required this.loading,
    required this.hasError,
    required this.book,
    required this.content,
    required this.scrollController,
    required this.htmlKey,
    required this.readResource,
    required this.onTapLink,
    required this.onRetry,
  });

  final bool loading;
  final bool hasError;
  final Publication? book;
  final RichReadingContent? content;
  final ScrollController scrollController;
  final GlobalKey<HtmlWidgetState> htmlKey;
  final Future<Uint8List> Function(SourceMediaRef) readResource;
  final Future<bool> Function(String) onTapLink;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    if (loading) return const Center(child: CircularProgressIndicator());
    if (hasError) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Could not load this section.'),
            TextButton(onPressed: onRetry, child: const Text('Try again')),
          ],
        ),
      );
    }
    if (content == null || book == null) return const SizedBox.shrink();

    return SingleChildScrollView(
      controller: scrollController,
      padding: const EdgeInsets.all(20),
      child: Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: NovelContentView(
            content: content!,
            htmlKey: htmlKey,
            readResource: readResource,
            onTapLink: onTapLink,
          ),
        ),
      ),
    );
  }
}

class PublicationReaderNavigation extends StatelessWidget {
  const PublicationReaderNavigation({
    super.key,
    required this.index,
    required this.count,
    required this.onPrevious,
    required this.onNext,
  });

  final int index;
  final int count;
  final VoidCallback onPrevious;
  final VoidCallback onNext;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          IconButton(
            tooltip: 'Previous section',
            onPressed: index == 0 ? null : onPrevious,
            icon: const Icon(Icons.chevron_left),
          ),
          Text('${index + 1} of $count'),
          IconButton(
            tooltip: 'Next section',
            onPressed: index == count - 1 ? null : onNext,
            icon: const Icon(Icons.chevron_right),
          ),
        ],
      ),
    );
  }
}

class PublicationTocSheet extends StatelessWidget {
  const PublicationTocSheet({
    super.key,
    required this.book,
    required this.onSelect,
  });

  final Publication book;
  final void Function(int index, String? fragment) onSelect;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: ListView.builder(
        itemCount: book.toc.length,
        itemBuilder: (context, index) {
          final link = book.toc[index];
          return ListTile(
            title: Text(link.label),
            onTap: () {
              Navigator.pop(context);
              final target = book.spine.indexWhere(
                (section) => section.resource == link.resource,
              );
              if (target >= 0) onSelect(target, link.fragment);
            },
          );
        },
      ),
    );
  }
}
