import 'package:flutter/services.dart';

// The native channel intentionally hides raw JavaScript, URL and filesystem errors.
// Only public reason codes emitted by the bounded runtime may reach the picker.
String? catalogSourceSearchMessage(Object error) {
  if (error is! PlatformException) return null;
  if (error.code == 'busy') return 'Novel runtime is busy. Try again.';
  if (error.code != 'lnreader_failure' || error.details is! Map) return null;
  final details = error.details as Map;
  if (details.length != 1) return null;
  return switch (details['reason']) {
    'source-unavailable' =>
      'This Novel plugin is not active in the current runtime. '
          'Restart Hikari.',
    'execution-rejected' =>
      'Novel runtime rejected execution (execution-rejected). '
          'Check provider compatibility.',
    'invalid-output' =>
      'Novel plugin returned an unsupported result (invalid-output).',
    'invalid-request' =>
      'Novel host rejected this operation (invalid-request).',
    'snapshot-failed' || 'collision' =>
      'Novel runtime initialization failed. '
          'Review Plugin Manager runtime evidence.',
    _ => null,
  };
}

