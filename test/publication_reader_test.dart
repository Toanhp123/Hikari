import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/publication.dart';
import 'package:hikari/domain/media/reading.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/novel_reader/novel_content_view.dart';
import 'package:hikari/features/novel_reader/publication_reader_page.dart';

void main() {
  const publicationRef = SourceMediaRef(sourceId: SourceId.local, itemId: 'book');
  final sections = [
    const PublicationSection(resource: 'one.xhtml', title: 'One'),
    const PublicationSection(resource: 'two.xhtml', title: 'Two'),
  ];

  testWidgets('loads one spine section and navigates with accessible controls', (
    tester,
  ) async {
    final source = _FakePublicationSource(sections);
    final writes = <ProgressPosition>[];
    await tester.pumpWidget(
      MaterialApp(
        home: PublicationReaderPage(
          title: 'Book',
          publication: publicationRef,
          source: source,
          saveProgress: (position, _) async => writes.add(position),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(NovelContentView), findsOneWidget);
    expect(source.readSections, ['one.xhtml']);
    expect(find.byTooltip('Table of contents'), findsOneWidget);
    expect(find.byTooltip('Next section'), findsOneWidget);

    await tester.tap(find.byTooltip('Next section'));
    await tester.pumpAndSettle();
    expect(find.byType(NovelContentView), findsOneWidget);
    expect(source.readSections, ['one.xhtml', 'two.xhtml']);
    expect(find.byTooltip('Previous section'), findsOneWidget);

    await tester.tap(find.byTooltip('Table of contents'));
    await tester.pumpAndSettle();
    expect(find.text('One'), findsOneWidget);
    expect(find.text('Two'), findsOneWidget);
  });

  testWidgets('restores document progress and saves only loaded sections', (
    tester,
  ) async {
    final source = _FakePublicationSource(sections);
    final writes = <ProgressPosition>[];
    await tester.pumpWidget(
      MaterialApp(
        home: PublicationReaderPage(
          title: 'Book',
          publication: publicationRef,
          source: source,
          initialProgress: MediaProgress(
            media: publicationRef,
            position: DocumentPosition(resource: 'two.xhtml', progression: 0.5),
            completed: false,
            updatedAt: DateTime.utc(2026),
          ),
          saveProgress: (position, _) async => writes.add(position),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(NovelContentView), findsOneWidget);
    expect(source.readSections, ['two.xhtml']);
    expect(writes, isEmpty);

    await tester.pumpWidget(const SizedBox());
    expect(writes, isEmpty);
  });

  testWidgets('shows retry and never saves failed section', (tester) async {
    final source = _FakePublicationSource(sections, failResource: 'one.xhtml');
    var writes = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: PublicationReaderPage(
          title: 'Book',
          publication: publicationRef,
          source: source,
          saveProgress: (_, _) async => writes++,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Could not load this section.'), findsOneWidget);
    expect(find.text('Try again'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    expect(writes, 0);
  });
}

final class _FakePublicationSource implements PublicationSource {
  _FakePublicationSource(this.sections, {this.failResource});

  final List<PublicationSection> sections;
  final String? failResource;
  final readSections = <String>[];

  @override
  SourceId get id => SourceId.local;

  @override
  String get name => 'Test publication';

  @override
  bool canOpenPublication(SourceMediaRef publication) => true;

  @override
  Future<Publication> publication(SourceMediaRef publication) async => Publication(
        metadata: const MediaMetadata(title: 'Book'),
        spine: sections,
        toc: sections
            .map((section) => PublicationLink(label: section.title, resource: section.resource))
            .toList(),
      );

  @override
  Future<NovelChapterContent> readSection(
    SourceMediaRef publication,
    String resource,
  ) async {
    readSections.add(resource);
    if (resource == failResource) throw StateError('section unavailable');
    return NovelChapterContent(html: '<p>${resource.replaceAll('.xhtml', '')} content</p>');
  }

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}
