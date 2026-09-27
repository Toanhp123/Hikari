import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

/// MangaDex-specific HTTP policy: headers, throttling, aborts and size caps.
final class MangaDexClient {
  MangaDexClient({http.Client? client})
    : _client = client ?? http.Client(),
      _ownsClient = client == null;

  static const _headers = {
    'User-Agent': 'Hikari/0.1 (experimental manga reader)',
  };

  final http.Client _client;
  final bool _ownsClient;
  final _pendingAborts = <Completer<void>>{};
  Future<void> _requestQueue = Future<void>.value();
  DateTime? _lastApiRequestAt;
  DateTime? _lastAtHomeRequestAt;
  bool _closed = false;

  void close() {
    if (_closed) return;
    _closed = true;
    for (final abort in _pendingAborts) {
      if (!abort.isCompleted) abort.complete();
    }
    if (_ownsClient) _client.close();
  }

  Future<Map<String, dynamic>> getApi(
    String path, [
    Map<String, dynamic>? query,
  ]) {
    final result = _requestQueue.then((_) async {
      await _throttle(path);
      final response = await request(
        http.Request('GET', Uri.https('api.mangadex.org', path, query)),
      );
      checkStatus(response.statusCode);
      final body = _expectObject(jsonDecode(utf8.decode(response.bodyBytes)));
      if (body['result'] != 'ok') {
        throw const FormatException('MangaDex response was not successful.');
      }
      return body;
    });
    _requestQueue = result.then<void>(
      (_) {},
      onError: (Object _, StackTrace _) {},
    );
    return result;
  }

  Future<void> _throttle(String path) async {
    final isAtHome = path.startsWith('/at-home/');
    final now = DateTime.now();
    var wait = _lastApiRequestAt == null
        ? Duration.zero
        : const Duration(milliseconds: 250) -
              now.difference(_lastApiRequestAt!);
    if (isAtHome && _lastAtHomeRequestAt != null) {
      final atHomeWait =
          const Duration(milliseconds: 1500) -
          now.difference(_lastAtHomeRequestAt!);
      if (atHomeWait > wait) wait = atHomeWait;
    }
    if (wait > Duration.zero) await Future<void>.delayed(wait);

    _lastApiRequestAt = DateTime.now();
    if (isAtHome) _lastAtHomeRequestAt = _lastApiRequestAt;
  }

  Future<http.Response> request(
    http.Request request, {
    int cap = 8 * 1024 * 1024,
    Duration timeout = const Duration(seconds: 30),
  }) async {
    if (_closed) throw StateError('MangaDex source is closed.');
    final abort = Completer<void>();
    _pendingAborts.add(abort);
    final outgoing =
        http.AbortableRequest(
            request.method,
            request.url,
            abortTrigger: abort.future,
          )
          ..headers.addAll(request.headers)
          ..headers.addAll(_headers)
          ..bodyBytes = request.bodyBytes;
    final timer = Timer(timeout, () {
      if (!abort.isCompleted) abort.complete();
    });

    try {
      return await (() async {
        final response = await _client.send(outgoing);
        final bytes = BytesBuilder(copy: false);
        await for (final chunk in response.stream) {
          if (bytes.length + chunk.length > cap) {
            throw StateError('MangaDex response exceeds size limit.');
          }
          bytes.add(chunk);
        }
        return http.Response.bytes(
          bytes.takeBytes(),
          response.statusCode,
          headers: response.headers,
        );
      })().timeout(timeout);
    } finally {
      timer.cancel();
      if (!abort.isCompleted) abort.complete();
      _pendingAborts.remove(abort);
    }
  }

  void checkStatus(int status) {
    if (status >= 200 && status < 300) return;
    throw StateError(
      status == 429
          ? 'MangaDex rate limit (429). Wait before trying again.'
          : 'MangaDex request failed (HTTP $status).',
    );
  }
}

Map<String, dynamic> _expectObject(Object? value) {
  if (value is! Map<String, dynamic>) {
    throw const FormatException('Invalid MangaDex object.');
  }
  return value;
}
