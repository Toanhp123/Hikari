import 'dart:convert';

/// Private preview reference, not an archive or media identity.
/// The prefix validates *shape*, not provenance or SAF permissions.
abstract final class LocalArtworkRef {
  static const _prefix = 'hikari-local-art:';

  static String? tryEncode(String locator) => _valid(locator)
      ? '$_prefix${base64Url.encode(utf8.encode(locator))}'
      : null;

  static String? tryDecode(String encoded) {
    if (!encoded.startsWith(_prefix)) return null;
    try {
      final locator = utf8.decode(
        base64Url.decode(encoded.substring(_prefix.length)),
      );
      return _valid(locator) ? locator : null;
    } on FormatException {
      return null;
    }
  }

  static bool _valid(String locator) {
    final uri = Uri.tryParse(locator);
    return locator.length <= 8192 &&
        uri != null &&
        uri.scheme == 'content' &&
        uri.host.isNotEmpty &&
        !RegExp(r'[\x00-\x20\x7f]').hasMatch(locator);
  }
}
