import 'package:flutter/services.dart';

/// Only exposes controlled staging/transport statuses, never native exception
/// strings, signed download URLs, filesystem paths, or executable code.
String? pluginAcquisitionMessage(Object error) {
  if (error is! PlatformException) return null;
  return switch (error.code) {
    'plugin_url_rejected' =>
      'Download URL rejected by HTTPS or path safety policy.',
    'plugin_redirect_rejected' =>
      'Download redirect rejected (unsafe target or too many redirects).',
    'plugin_http_failed' => _httpMessage(error.details),
    'plugin_network_timeout' => 'Plugin download timed out. Try again.',
    'plugin_network_failed' =>
      'Plugin download failed due to a network connection error.',
    'plugin_transfer_cancelled' => 'Plugin download was cancelled.',
    'plugin_download_too_large' =>
      'Plugin download exceeds the allowed size limit.',
    'plugin_apk_invalid' => 'Downloaded file is not a valid extension APK.',
    'plugin_apk_identity_mismatch' =>
      'Downloaded APK does not match the selected package, version or signer.',
    'plugin_stage_storage_failed' =>
      'Could not store the staged plugin bytes on this device.',
    _ => null,
  };
}

String _httpMessage(Object? details) {
  if (details is Map &&
      details.length == 1 &&
      details['status'] is int &&
      (details['status'] as int) >= 400 &&
      (details['status'] as int) <= 599) {
    return 'Plugin download failed (HTTP ${details['status']}).';
  }
  return 'Plugin download failed: server returned an unsuccessful response.';
}
