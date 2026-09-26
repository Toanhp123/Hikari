import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

const _layers = {
  'app',
  'core',
  'domain',
  'application',
  'infrastructure',
  'features',
};

const _allowed = <String, Set<String>>{
  'root': {'app'},
  'core': {'core'},
  'domain': {'core', 'domain'},
  'application': {'core', 'domain', 'application'},
  'infrastructure': {'core', 'domain', 'infrastructure'},
  'features': {'core', 'domain', 'application', 'features'},
  'app': _layers,
};

const _pureLayers = {'core', 'domain', 'application'};
const _platformLibraries = {
  'dart:ffi',
  'dart:html',
  'dart:io',
  'dart:isolate',
  'dart:js',
  'dart:js_interop',
  'dart:js_interop_unsafe',
  'dart:ui',
};

final _uriLiteral = RegExp(r'''[rR]?["']([^"']+)["']''');

void main() {
  test('lib obeys architecture boundaries', () {
    final violations = <String>[];

    for (final file
        in Directory('lib')
            .listSync(recursive: true)
            .whereType<File>()
            .where((file) => file.path.endsWith('.dart'))) {
      final sourcePath = _libPath(file.path);
      final sourceLayer = _layer(sourcePath);

      if (sourceLayer == null) {
        violations.add(
          '$sourcePath: unknown top-level architecture directory. '
          'Update ADR-001 and the architecture guard before adding a new boundary.',
        );
        continue;
      }

      for (final directive in _dependencyDirectives(file.readAsStringSync())) {
        for (final uri in directive.uris) {
          final problem = _checkDependency(
            sourcePath: sourcePath,
            sourceLayer: sourceLayer,
            kind: directive.kind,
            uri: uri,
          );
          if (problem != null) {
            violations.add('$sourcePath: $problem');
          }
        }
      }
    }

    expect(violations, isEmpty, reason: violations.join('\n'));
  });

  test('guard catches representative violations', () {
    const cases = [
      (
        sourcePath: 'main.dart',
        sourceLayer: 'root',
        kind: 'import',
        uri: 'package:hikari/domain/media.dart',
      ),
      (
        sourcePath: 'core/result.dart',
        sourceLayer: 'core',
        kind: 'import',
        uri: 'package:hikari/domain/media.dart',
      ),
      (
        sourcePath: 'domain/media.dart',
        sourceLayer: 'domain',
        kind: 'import',
        uri: 'package:hikari/infrastructure/network/client.dart',
      ),
      (
        sourcePath: 'infrastructure/repositories/library_repository.dart',
        sourceLayer: 'infrastructure',
        kind: 'import',
        uri: 'package:hikari/application/library/load_library.dart',
      ),
      (
        sourcePath: 'features/library/page.dart',
        sourceLayer: 'features',
        kind: 'export',
        uri: 'package:hikari/infrastructure/persistence/database.dart',
      ),
      (
        sourcePath: 'application/search.dart',
        sourceLayer: 'application',
        kind: 'import',
        uri: 'dart:io',
      ),
      (
        sourcePath: 'domain/media.dart',
        sourceLayer: 'domain',
        kind: 'import',
        uri: 'package:dio/dio.dart',
      ),
      (
        sourcePath: 'domain/media.dart',
        sourceLayer: 'domain',
        kind: 'import',
        uri: 'package:hikari/experimental/media.dart',
      ),
      (
        sourcePath: 'features/library/state.dart',
        sourceLayer: 'features',
        kind: 'part',
        uri: 'package:hikari/domain/state.g.dart',
      ),
    ];

    for (final item in cases) {
      expect(
        _checkDependency(
          sourcePath: item.sourcePath,
          sourceLayer: item.sourceLayer,
          kind: item.kind,
          uri: item.uri,
        ),
        isNotNull,
        reason: '${item.sourcePath} should reject ${item.kind} ${item.uri}',
      );
    }
  });

  test('guard accepts representative legal dependencies', () {
    const cases = [
      (
        sourcePath: 'main.dart',
        sourceLayer: 'root',
        kind: 'import',
        uri: 'package:hikari/app/app.dart',
      ),
      (
        sourcePath: 'main.dart',
        sourceLayer: 'root',
        kind: 'import',
        uri: 'package:flutter/widgets.dart',
      ),
      (
        sourcePath: 'core/result.dart',
        sourceLayer: 'core',
        kind: 'import',
        uri: 'result_error.dart',
      ),
      (
        sourcePath: 'domain/media.dart',
        sourceLayer: 'domain',
        kind: 'import',
        uri: 'package:hikari/core/result.dart',
      ),
      (
        sourcePath: 'infrastructure/repositories/library_repository.dart',
        sourceLayer: 'infrastructure',
        kind: 'import',
        uri: 'package:hikari/domain/library/library_repository.dart',
      ),
      (
        sourcePath: 'features/library/page.dart',
        sourceLayer: 'features',
        kind: 'import',
        uri: '../../application/library/load_library.dart',
      ),
      (
        sourcePath: 'features/library/state.dart',
        sourceLayer: 'features',
        kind: 'part',
        uri: 'state.g.dart',
      ),
      (
        sourcePath: 'app/app.dart',
        sourceLayer: 'app',
        kind: 'import',
        uri: 'package:hikari/infrastructure/repositories/library_repository.dart',
      ),
    ];

    for (final item in cases) {
      expect(
        _checkDependency(
          sourcePath: item.sourcePath,
          sourceLayer: item.sourceLayer,
          kind: item.kind,
          uri: item.uri,
        ),
        isNull,
        reason: '${item.sourcePath} should allow ${item.kind} ${item.uri}',
      );
    }
  });

  test('internal paths normalize package and relative URIs', () {
    expect(
      _internalPath('domain/media.dart', 'package:hikari/core/result.dart'),
      'core/result.dart',
    );
    expect(
      _internalPath(
        'features/library/page.dart',
        '../../application/library/load_library.dart',
      ),
      'application/library/load_library.dart',
    );
    expect(_internalPath('domain/media.dart', 'package:dio/dio.dart'), isNull);
    expect(_internalPath('domain/media.dart', 'dart:io'), isNull);
  });

  test('lib paths normalize Windows and POSIX separators', () {
    expect(
      _libPath(r'F:\project\hikari\lib\domain\media.dart'),
      'domain/media.dart',
    );
    expect(_libPath('lib/domain/media.dart'), 'domain/media.dart');
  });

  test('directive scanner captures multiline and conditional dependencies', () {
    const source =
        r'''// import 'package:hikari/infrastructure/commented_out.dart';
/*
export 'package:hikari/infrastructure/also_commented_out.dart';
*/
import 'package:hikari/core/result.dart'
    if (dart.library.io) 'package:hikari/infrastructure/io_adapter.dart'
    if (dart.library.js_interop) 'package:hikari/infrastructure/web_adapter.dart';
export /* keep parser honest */ 'package:hikari/domain/media.dart';
part 'state.g.dart';
part of 'library.dart';

class StopsDirectiveScanningHere {}
''';

    final directives = _dependencyDirectives(source).toList();

    expect(directives, hasLength(4));
    expect(directives[0].kind, 'import');
    expect(directives[0].uris, [
      'package:hikari/core/result.dart',
      'package:hikari/infrastructure/io_adapter.dart',
      'package:hikari/infrastructure/web_adapter.dart',
    ]);
    expect(directives[1].kind, 'export');
    expect(directives[1].uris, ['package:hikari/domain/media.dart']);
    expect(directives[2].kind, 'part');
    expect(directives[2].uris, ['state.g.dart']);
    expect(directives[3].kind, 'part of');
    expect(directives[3].uris, ['library.dart']);
  });
}

String? _checkDependency({
  required String sourcePath,
  required String sourceLayer,
  required String kind,
  required String uri,
}) {
  final parsed = Uri.tryParse(uri);
  if (_pureLayers.contains(sourceLayer)) {
    if (parsed?.scheme == 'package' &&
        (parsed!.pathSegments.isEmpty ||
            parsed.pathSegments.first != 'hikari')) {
      return '$sourceLayer cannot depend on external packages: $kind $uri';
    }
    if (_platformLibraries.contains(uri)) {
      return '$sourceLayer must stay platform-independent: $kind $uri';
    }
  }
  if (sourceLayer == 'root' && parsed?.scheme == 'package') {
    final package = parsed!.pathSegments.isEmpty
        ? ''
        : parsed.pathSegments.first;
    if (package != 'hikari' && package != 'flutter') {
      return 'root entrypoints may only depend on app/ and Flutter: $kind $uri';
    }
  }
  if (sourceLayer == 'root' && _platformLibraries.contains(uri)) {
    return 'root entrypoints must stay thin: $kind $uri';
  }

  final targetPath = _internalPath(sourcePath, uri);
  if (targetPath == null) {
    return null;
  }

  final targetLayer = _layer(targetPath);
  if (targetLayer == null) {
    return 'dependency targets unknown architecture directory: $kind $uri';
  }

  if (kind == 'part' || kind == 'part of') {
    return sourceLayer == targetLayer
        ? null
        : '$kind must stay inside $sourceLayer: $uri';
  }

  return (_allowed[sourceLayer] ?? const <String>{}).contains(targetLayer)
      ? null
      : '$sourceLayer cannot depend on $targetLayer: $kind $uri';
}

String? _internalPath(String sourcePath, String rawUri) {
  final uri = Uri.tryParse(rawUri);
  if (uri == null) {
    return null;
  }
  if (uri.scheme == 'package') {
    return uri.pathSegments.isNotEmpty && uri.pathSegments.first == 'hikari'
        ? uri.pathSegments.skip(1).join('/')
        : null;
  }
  if (uri.scheme.isNotEmpty) {
    return null;
  }
  return Uri(path: sourcePath).resolveUri(uri).path;
}

String? _layer(String path) {
  final segments = path.split('/');
  if (segments.length == 1) {
    return 'root';
  }
  return _layers.contains(segments.first) ? segments.first : null;
}

String _libPath(String path) {
  final normalized = path.replaceAll('\\', '/');
  final marker = normalized.lastIndexOf('/lib/');
  return marker >= 0
      ? normalized.substring(marker + 5)
      : normalized.replaceFirst(RegExp(r'^lib/'), '');
}

Iterable<_Directive> _dependencyDirectives(String source) sync* {
  final lines = source.split('\n');
  var inBlockComment = false;
  var buffer = StringBuffer();
  String? kind;

  for (final rawLine in lines) {
    var line = rawLine.trim();

    if (inBlockComment) {
      final end = line.indexOf('*/');
      if (end < 0) {
        continue;
      }
      line = line.substring(end + 2).trim();
      inBlockComment = false;
    }

    while (line.startsWith('/*')) {
      final end = line.indexOf('*/', 2);
      if (end < 0) {
        inBlockComment = true;
        line = '';
        break;
      }
      line = line.substring(end + 2).trim();
    }

    if (line.isEmpty || line.startsWith('//') || line.startsWith('@')) {
      continue;
    }

    if (kind == null) {
      kind = _directiveKind(line);
      if (kind == null) {
        if (line.startsWith('library ')) {
          continue;
        }
        return;
      }
      buffer = StringBuffer();
    }

    buffer.writeln(line);
    if (!line.contains(';')) {
      continue;
    }

    final text = buffer
        .toString()
        .replaceAll(RegExp(r'/\*[\s\S]*?\*/'), ' ')
        .replaceAll(RegExp(r'//[^\n\r]*'), ' ');
    final uris = _uriLiteral
        .allMatches(text)
        .map((match) => match.group(1)!)
        .toList(growable: false);
    if (uris.isNotEmpty) {
      yield _Directive(kind, uris);
    }
    kind = null;
  }
}

String? _directiveKind(String line) {
  if (line.startsWith('import ')) return 'import';
  if (line.startsWith('export ')) return 'export';
  if (line.startsWith('part of ')) return 'part of';
  if (line.startsWith('part ')) return 'part';
  return null;
}

class _Directive {
  const _Directive(this.kind, this.uris);

  final String kind;
  final List<String> uris;
}
