import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/infrastructure/extensions/lnreader/lnreader_source_loader.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('hikari/lnreader');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final descriptor = {
    'id': 'frozen',
    'name': 'Frozen',
    'site': 'https://example.com/',
  };
  tearDown(() {
    debugDefaultTargetPlatformOverride = null;
    messenger.setMockMethodCallHandler(channel, null);
  });
  test('existing loader consumes frozen source IDs and rejects malformed snapshots', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'listSources');
      return [descriptor];
    });
    final sources = await const LnReaderSourceLoader().loadSources();
    expect(sources.single.id, SourceId('lnreader:frozen'));
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => [
        {...descriptor, 'id': 'ln.hako'},
      ],
    );
    expect(
      (await const LnReaderSourceLoader().loadSources()).single.id,
      SourceId('lnreader:ln.hako'),
    );

    for (final invalid in <Object?>[
      null,
      [descriptor, descriptor],
      [
        {...descriptor, 'id': 'bad/id'},
      ],
      [
        {...descriptor, 'site': 'http://example.com/'},
      ],
      [
        {...descriptor, 'unknown': true},
      ],
    ]) {
      messenger.setMockMethodCallHandler(channel, (_) async => invalid);
      await expectLater(
        const LnReaderSourceLoader().loadSources(),
        throwsFormatException,
      );
    }
  });
}
