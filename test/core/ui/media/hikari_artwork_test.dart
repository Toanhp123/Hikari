import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import '../../../fixtures/counting_artwork_provider.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/media/hikari_artwork.dart';
import 'package:hikari/core/ui/media/hikari_artwork_decode_policy.dart';

final _png = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGOQNJvzHwADQgHrKgED3AAAAABJRU5ErkJggg==',
);

Widget _host(Widget child) => MaterialApp(
  home: MediaQuery(
    data: const MediaQueryData(devicePixelRatio: 1),
    child: Scaffold(
      body: Align(
        alignment: Alignment.topLeft,
        child: SizedBox(width: 160, height: 240, child: child),
      ),
    ),
  ),
);

class _ImageClient extends Fake implements HttpClient {
  int requests = 0;
  @override
  Future<HttpClientRequest> getUrl(Uri url) async {
    requests++;
    return _ImageRequest(url.path.endsWith('missing') ? 404 : 200);
  }
}

class _ImageRequest extends Fake implements HttpClientRequest {
  _ImageRequest(this.status);
  final int status;
  @override
  Future<HttpClientResponse> close() async => _ImageResponse(status);
}

class _ImageResponse extends Stream<List<int>> implements HttpClientResponse {
  _ImageResponse(this.statusCode);
  @override
  final int statusCode;
  @override
  int get contentLength => _png.length;
  @override
  HttpClientResponseCompressionState get compressionState =>
      HttpClientResponseCompressionState.notCompressed;
  @override
  StreamSubscription<List<int>> listen(
    void Function(List<int>)? onData, {
    Function? onError,
    void Function()? onDone,
    bool? cancelOnError,
  }) => Stream<List<int>>.value(_png).listen(
    onData,
    onError: onError,
    onDone: onDone,
    cancelOnError: cancelOnError,
  );
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  testWidgets(
    'network image success caches request and HTTP failure falls back',
    (tester) async {
      final client = _ImageClient();
      final previous = debugNetworkImageHttpClientProvider;
      debugNetworkImageHttpClientProvider = () => client;
      try {
        for (var i = 0; i < 2; i++) {
          await tester.pumpWidget(
            _host(
              const HikariArtwork(
                image: NetworkImage('https://artwork.invalid/phase03/success'),
                semanticLabel: 'Network cover',
              ),
            ),
          );
          await tester.runAsync(() async {
            await Future<void>.delayed(const Duration(milliseconds: 30));
          });
          await tester.pumpAndSettle();
          expect(find.byType(RawImage), findsOneWidget);
          expect(
            tester.widget<RawImage>(find.byType(RawImage)).image,
            isNotNull,
          );
          expect(client.requests, 1);
        }
        await tester.pumpWidget(
          _host(
            const HikariArtwork(
              image: NetworkImage('https://artwork.invalid/phase03/missing'),
              semanticLabel: 'Missing network cover',
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(client.requests, 2);
        expect(find.byIcon(Icons.image_not_supported_outlined), findsOneWidget);
        expect(tester.takeException(), isNull);
      } finally {
        debugNetworkImageHttpClientProvider = previous;
      }
    },
  );
  testWidgets(
    'slow provider retains explicit artwork semantics during loading',
    (tester) async {
      final pending = Completer<Uint8List>();
      final provider = CountingArtworkProvider(pending.future);
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        _host(HikariArtwork(image: provider, semanticLabel: 'Pending cover')),
      );
      expect(find.byIcon(Icons.image_not_supported_outlined), findsOneWidget);
      expect(find.bySemanticsLabel('Pending cover'), findsOneWidget);
      pending.complete(_png);
      await tester.runAsync(() async {
        await Future<void>.delayed(const Duration(milliseconds: 30));
      });
      await tester.pumpAndSettle();
      expect(provider.loads, 1);
      expect(provider.decodes, 1);
      semantics.dispose();
    },
  );
  testWidgets(
    'same cached provider loads and decodes once across rebuild and remount',
    (tester) async {
      final provider = CountingArtworkProvider(Future.value(_png));
      for (var i = 0; i < 3; i++) {
        await tester.pumpWidget(_host(HikariArtwork(image: provider)));
        await tester.runAsync(() async {
          await Future<void>.delayed(const Duration(milliseconds: 30));
        });
        await tester.pumpAndSettle();
        if (i == 1) {
          await tester.pumpWidget(const SizedBox());
          await tester.pump();
        }
      }
      expect(provider.loads, 1);
      expect(provider.decodes, 1);
      expect(tester.takeException(), isNull);
    },
  );
  test('decode policy preserves width-only 64px buckets and 2048px cap', () {
    expect(
      decodeTargetSize(logicalSize: const Size(160, 240), devicePixelRatio: 3),
      (width: 512, height: null),
    );
    for (final size in [const Size(1, 1), const Size(0.1, 400)]) {
      expect(decodeTargetSize(logicalSize: size, devicePixelRatio: 1), (
        width: 64,
        height: null,
      ));
    }
    expect(
      decodeTargetSize(logicalSize: const Size(9999, 1), devicePixelRatio: 8),
      (width: 2048, height: null),
    );
    for (final width in [0.0, -1.0, double.infinity, double.nan]) {
      expect(
        decodeTargetSize(logicalSize: Size(width, 200), devicePixelRatio: 2),
        isNull,
      );
    }
    for (final dpr in [0.0, -1.0, double.infinity, double.nan]) {
      expect(
        decodeTargetSize(
          logicalSize: const Size(100, 200),
          devicePixelRatio: dpr,
        ),
        isNull,
      );
    }
  });

  testWidgets('missing artwork is placeholder only and decorative', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_host(const HikariArtwork()));
    expect(find.byType(Image), findsNothing);
    expect(find.byIcon(Icons.image_not_supported_outlined), findsOneWidget);
    expect(find.bySemanticsLabel('Cover'), findsNothing);
    semantics.dispose();
  });

  testWidgets('memory image uses one bounded resize and explicit label', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(HikariArtwork(image: MemoryImage(_png), semanticLabel: 'Cover')),
    );
    await tester.pumpAndSettle();
    final image = tester.widget<Image>(find.byType(Image));
    final resize = image.image as ResizeImage;
    expect(resize.width, 192);
    expect(resize.height, isNull);
    expect(resize.imageProvider, isA<MemoryImage>());
    expect(image.semanticLabel, 'Cover');
    expect(image.excludeFromSemantics, isFalse);
    expect(tester.takeException(), isNull);
  });

  testWidgets('corrupt memory image falls back without uncaught error', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(HikariArtwork(image: MemoryImage(Uint8List.fromList([1, 2, 3])))),
    );
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.image_not_supported_outlined), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('already resized provider is never wrapped again', (
    tester,
  ) async {
    final provider = ResizeImage(MemoryImage(_png), width: 64);
    await tester.pumpWidget(_host(HikariArtwork(image: provider)));
    expect(tester.widget<Image>(find.byType(Image)).image, same(provider));
    await tester.pumpAndSettle();
  });

  testWidgets(
    'equal provider and bucket retain cache key across parent rebuild',
    (tester) async {
      Object? first;
      for (var i = 0; i < 2; i++) {
        await tester.pumpWidget(_host(HikariArtwork(image: MemoryImage(_png))));
        await tester.pumpAndSettle();
        final provider = tester.widget<Image>(find.byType(Image)).image;
        final key = await provider.obtainKey(ImageConfiguration.empty);
        if (first != null) expect(key, first);
        first = key;
      }
    },
  );
}
