import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/features/plugins/plugin_acquisition_message.dart';

void main() {
  test('safe actionable staging failures never echo signed URLs', () {
    final error = PlatformException(
      code: 'plugin_http_failed',
      message: 'https://cdn.example.com/private?sig=secret',
      details: {'status': 403},
    );
    expect(pluginAcquisitionMessage(error), contains('HTTP 403'));
    expect(pluginAcquisitionMessage(error), isNot(contains('secret')));
    expect(
      pluginAcquisitionMessage(PlatformException(code: 'plugin_url_rejected')),
      contains('URL'),
    );
    expect(
      pluginAcquisitionMessage(
        PlatformException(code: 'plugin_apk_identity_mismatch'),
      ),
      contains('signer'),
    );
    expect(
      pluginAcquisitionMessage(
        PlatformException(code: 'plugin_operation_failed'),
      ),
      isNull,
    );
    expect(pluginAcquisitionMessage(const FormatException('private')), isNull);
  });
}
