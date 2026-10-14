import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/novel_reader/widgets/novel_content_view.dart';

void main() {
  const illustration = SourceMediaRef(
    sourceId: SourceId('test-novel'),
    itemId: 'illustration',
  );
  final png = base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==',
  );
  final chapter = RichReadingContent(
    html: '<p>Chapter</p><img src="figure" alt="Illustration">',
    resources: {'figure': illustration},
  );

  testWidgets('novel illustration decode width follows reader layout and DPR', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(800, 1200);
    tester.view.devicePixelRatio = 2;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Align(
            alignment: Alignment.topLeft,
            child: SizedBox(
              width: 240,
              child: NovelContentView(
                content: chapter,
                readResource: (_) async => png,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final imageFinder = find.descendant(
      of: find.byType(NovelContentView),
      matching: find.byType(Image),
    );
    expect(imageFinder, findsOneWidget);
    final image = tester.widget<Image>(imageFinder);
    final decoded = image.image as ResizeImage;
    expect(decoded.width, 512); // 240 logical * 2 DPR, rounded to 64px.
    expect(decoded.height, 4096);
    expect(decoded.policy, ResizeImagePolicy.fit);
    expect(decoded.allowUpscaling, isFalse);
    expect(decoded.imageProvider, isA<MemoryImage>());
    expect(tester.takeException(), isNull);
  });

  testWidgets('large novel illustrations cap decoded dimensions', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(3600, 2400);
    tester.view.devicePixelRatio = 3;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Align(
            alignment: Alignment.topLeft,
            child: SizedBox(
              width: 1000,
              child: NovelContentView(
                content: chapter,
                readResource: (_) async => png,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final image = tester.widget<Image>(
      find.descendant(
        of: find.byType(NovelContentView),
        matching: find.byType(Image),
      ),
    );
    final decoded = image.image as ResizeImage;
    expect(decoded.width, 2048);
    expect(decoded.height, 4096);
    expect(decoded.policy, ResizeImagePolicy.fit);
    expect(tester.takeException(), isNull);
  });

  testWidgets('resizing the reader updates decode width without refetching', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(900, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });
    var reads = 0;
    Future<Uint8List> readResource(SourceMediaRef _) async {
      reads++;
      return png;
    }

    Widget reader(double width) => MaterialApp(
      home: Scaffold(
        body: Align(
          alignment: Alignment.topLeft,
          child: SizedBox(
            width: width,
            child: NovelContentView(
              content: chapter,
              readResource: readResource,
            ),
          ),
        ),
      ),
    );

    ResizeImage currentImage() =>
        tester
                .widget<Image>(
                  find.descendant(
                    of: find.byType(NovelContentView),
                    matching: find.byType(Image),
                  ),
                )
                .image
            as ResizeImage;

    await tester.pumpWidget(reader(200));
    await tester.pumpAndSettle();
    expect(currentImage().width, 256);
    expect(reads, 1);

    await tester.pumpWidget(reader(300));
    await tester.pumpAndSettle();
    expect(currentImage().width, 320);
    expect(reads, 1);
  });
}
