import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/publication.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/novel_reader/novel_content_view.dart';
import 'package:hikari/features/novel_reader/publication_reader_page.dart';

void main() {
  const publicationRef = SourceMediaRef(
    sourceId: SourceId.local,
    itemId: 'book',
  );
  final sections = [
    const PublicationSection(resource: 'one.xhtml', title: 'One'),
    const PublicationSection(resource: 'two.xhtml', title: 'Two'),
  ];

  testWidgets(
    'loads one spine section and navigates with accessible controls',
    (tester) async {
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
    },
  );

  testWidgets(
    'canonical links and TOC fragments scroll, missing anchors are inert',
    (tester) async {
      final source = _FakePublicationSource(
        sections,
        fragment: 'target',
        html:
            '<a href="two.xhtml#target">Chapter target</a><a href="#missing">Missing</a>${'<p>Long paragraph</p>' * 90}<p id="target">Destination</p>',
      );
      await tester.pumpWidget(
        MaterialApp(
          home: PublicationReaderPage(
            title: 'Book',
            publication: publicationRef,
            source: source,
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester
          .widget<NovelContentView>(find.byType(NovelContentView))
          .htmlKey!
          .currentState!
          .scrollToAnchor('missing');
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester
          .widget<NovelContentView>(find.byType(NovelContentView))
          .onTapLink!('two.xhtml#target');
      await tester.pumpAndSettle();
      final scroll = tester.widget<SingleChildScrollView>(
        find.byType(SingleChildScrollView),
      );
      expect(scroll.controller!.offset, greaterThan(0));
      expect(source.readSections, ['one.xhtml', 'two.xhtml']);
      await tester.tap(find.byTooltip('Table of contents'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('One'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<SingleChildScrollView>(find.byType(SingleChildScrollView))
            .controller!
            .offset,
        greaterThan(0),
      );
      expect(source.readSections.last, 'one.xhtml');
    },
  );

  testWidgets('encoded percent filenames navigate without double decoding', (
    tester,
  ) async {
    final source = _FakePublicationSource([
      const PublicationSection(resource: 'one.xhtml', title: 'One'),
      const PublicationSection(resource: 'OPS/100%25.xhtml', title: 'Percent'),
    ]);
    await tester.pumpWidget(
      MaterialApp(
        home: PublicationReaderPage(
          title: 'Book',
          publication: publicationRef,
          source: source,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester
        .widget<NovelContentView>(find.byType(NovelContentView))
        .onTapLink!('OPS/100%2525.xhtml');
    await tester.pumpAndSettle();
    expect(source.readSections.last, 'OPS/100%25.xhtml');
  });

  testWidgets('failed progress write retries on lifecycle flush', (
    tester,
  ) async {
    var attempts = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: PublicationReaderPage(
          title: 'Book',
          publication: publicationRef,
          source: _FakePublicationSource(sections),
          saveProgress: (_, _) async {
            if (++attempts == 1) throw StateError('disk');
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Next section'));
    await tester.pumpAndSettle();
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    await tester.pump();
    expect(attempts, 1);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    expect(attempts, 2);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    expect(attempts, 2);
  });

  testWidgets('successful next section persists on immediate exit', (
    tester,
  ) async {
    final writes = <ProgressPosition>[];
    await tester.pumpWidget(
      MaterialApp(
        home: PublicationReaderPage(
          title: 'Book',
          publication: publicationRef,
          source: _FakePublicationSource(sections),
          saveProgress: (position, _) async => writes.add(position),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Next section'));
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    expect((writes.last as DocumentPosition).resource, 'two.xhtml');
  });

  testWidgets('failed next section never overwrites progress', (tester) async {
    final writes = <ProgressPosition>[];
    await tester.pumpWidget(
      MaterialApp(
        home: PublicationReaderPage(
          title: 'Book',
          publication: publicationRef,
          source: _FakePublicationSource(sections, failResource: 'two.xhtml'),
          saveProgress: (position, _) async => writes.add(position),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Next section'));
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    expect(
      writes.whereType<DocumentPosition>().any(
        (p) => p.resource == 'two.xhtml',
      ),
      isFalse,
    );
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

  testWidgets('publication metadata failure can retry', (tester) async {
    final source = _FakePublicationSource(sections, failPublicationOnce: true);
    await tester.pumpWidget(
      MaterialApp(
        home: PublicationReaderPage(
          title: 'Book',
          publication: publicationRef,
          source: source,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.byType(NovelContentView), findsOneWidget);
  });

  testWidgets('pending save survives failed next section and retries on exit', (
    tester,
  ) async {
    final writes = <ProgressPosition>[];
    var attempts = 0;
    final source = _FakePublicationSource([
      ...sections,
      const PublicationSection(resource: 'three.xhtml', title: 'Three'),
    ], failResource: 'three.xhtml');
    await tester.pumpWidget(
      MaterialApp(
        home: PublicationReaderPage(
          title: 'Book',
          publication: publicationRef,
          source: source,
          saveProgress: (position, _) async {
            if (++attempts == 1) throw StateError('disk');
            writes.add(position);
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Next section'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Next section'));
    await tester.pumpAndSettle();
    expect(find.text('Could not load this section.'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    expect(attempts, 2);
    expect((writes.single as DocumentPosition).resource, 'two.xhtml');
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
  _FakePublicationSource(
    this.sections, {
    this.failResource,
    this.html,
    this.fragment,
    this.failPublicationOnce = false,
  });

  bool failPublicationOnce;

  final String? html;
  final String? fragment;

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
  Future<Publication> loadPublication(SourceMediaRef publication) async {
    if (failPublicationOnce) {
      failPublicationOnce = false;
      throw StateError('book unavailable');
    }
    return Publication(
      metadata: MediaMetadata(title: 'Book'),
      spine: sections,
      toc: sections
          .map(
            (section) => PublicationLink(
              label: section.title,
              resource: section.resource,
              fragment: fragment,
            ),
          )
          .toList(),
    );
  }

  @override
  Future<NovelChapterContent> readSection(
    SourceMediaRef publication,
    String resource,
  ) async {
    readSections.add(resource);
    if (resource == failResource) throw StateError('section unavailable');
    return NovelChapterContent(
      html: html ?? '<p>${resource.replaceAll('.xhtml', '')} content</p>',
    );
  }

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}
