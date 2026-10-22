import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tool/ui_audit_inventory.dart';

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
const _allowedInfrastructureFlutterLibraries = {
  'package:flutter/foundation.dart',
  'package:flutter/services.dart',
};

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
  test(
    'staged UI fixtures reject export relative bypass and raw feature modals',
    () {
      for (final fixture in [
        ('core_imports_domain.dart.txt', 'lib/core/ui/content/row.dart'),
        (
          'feature_show_dialog.dart.txt',
          'lib/features/new_feature/new_page.dart',
        ),
        (
          'relative_barrel_export.dart.txt',
          'lib/core/ui/foundation/barrel.dart',
        ),
      ]) {
        final source = File('test/fixtures/ui_architecture/${fixture.$1}')
            .readAsStringSync();
        expect(
          _uiViolations(fixture.$2, source, legacy: const {}).toList(),
          isNotEmpty,
          reason: fixture.$1,
        );
      }
    },
  );

  test('UI guard ignores comments and strings but catches token inversion', () {
    final source = File(
      'test/fixtures/ui_architecture/comment_false_positive.dart.txt',
    ).readAsStringSync();
    expect(
      _uiViolations(
        'lib/features/new_feature/view.dart',
        source,
        legacy: const {},
      ),
      isEmpty,
    );
    expect(
      _uiViolations(
        'lib/core/ui/foundation/tokens.dart',
        "export '../controls/button.dart';",
        legacy: const {},
      ),
      isNotEmpty,
    );
    expect(
      _uiViolations(
        'lib/core/ui/foundation/tokens.dart',
        "import 'package:hikari/app/theme/theme.dart';",
        legacy: const {},
      ),
      isNotEmpty,
    );
    expect(
      _uiViolations(
        'lib/app/theme/theme.dart',
        "import 'package:hikari/core/ui/foundation/tokens.dart';",
        legacy: const {},
      ),
      isEmpty,
    );
  });

  test('theme composition accepts foundation through existing guard', () {
    expect(
      _checkDependency(
        sourcePath: 'app/theme/hikari_theme.dart',
        sourceLayer: 'app',
        kind: 'import',
        uri: 'package:hikari/core/ui/foundation/tokens.dart',
      ),
      isNull,
    );
  });

  test('UI part directives cannot bypass foundation boundaries', () {
    expect(
      _uiViolations(
        'lib/core/ui/foundation/tokens.dart',
        "part '../controls/button.dart';",
        legacy: const {},
      ),
      isNotEmpty,
    );
  });

  test('live UI exceptions are exact inventoried sites with owner and expiry', () {
    final matrix = File('docs/ui/MIGRATION_MATRIX.md').readAsStringSync();
    // Frozen separately: inventory regeneration must never grant exemptions.
    final legacy = File('test/fixtures/ui_architecture/legacy_sites.txt')
        .readAsLinesSync()
        .where((line) => line.isNotEmpty)
        .toSet();
    for (final id in legacy) {
      final row = matrix
          .split('\n')
          .singleWhere((line) => line.startsWith('| `$id` |'));
      final columns = row.split('|');
      expect(columns[4].trim(), isNotEmpty, reason: '$id owner');
      expect(
        columns[6],
        matches(RegExp(r'\d{2}/\d{2}')),
        reason: '$id removal task',
      );
      expect(columns[9], contains('until listed task'), reason: id);
    }
    final violations = <String>[];
    for (final source in readUiSources(Directory.current).entries) {
      violations.addAll(
        _uiViolations(source.key, source.value, legacy: legacy),
      );
    }
    expect(violations, isEmpty, reason: violations.join('\n'));
    const existingPath = 'lib/features/catalog/catalog_detail_page.dart';
    expect(
      _uiViolations(
        existingPath,
        '${File(existingPath).readAsStringSync()}\nvoid added() { showDialog(); }',
        legacy: legacy,
      ),
      isNotEmpty,
      reason: 'New modal in an existing legacy file cannot inherit exemption',
    );
    expect(
      _uiViolations(
        'lib/features/new_feature/view.dart',
        'void f() { showDialog(); }',
        legacy: legacy,
      ),
      isNotEmpty,
    );
    expect(
      _uiViolations(
        'lib/features/new_feature/view.dart',
        'void f() { requestNativeConsent(); }',
        legacy: legacy,
      ),
      isEmpty,
    );
  });
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

  test('multi-route plugin management presentation stays feature-owned', () {
    final pluginFeature = Directory('lib/features/plugins');
    expect(pluginFeature.existsSync(), isTrue);
    for (final route in [
      'plugin_manager_page.dart',
      'apk_actions_page.dart',
      'novel_actions_page.dart',
    ]) {
      expect(File('${pluginFeature.path}/$route').existsSync(), isTrue);
      expect(File('lib/features/settings/$route').existsSync(), isFalse);
    }
    // Settings is a navigation entry, not an alternate Plugin Manager owner.
    for (final file in Directory(
      'lib/features/settings',
    ).listSync().whereType<File>()) {
      expect(
        file.path.contains('plugin_') || file.path.contains('actions_'),
        isFalse,
        reason: 'Plugin-specific presentation belongs in features/plugins.',
      );
    }
  });

  test('feature-root visual widgets are pages only', () {
    final violations = <String>[];

    for (final feature in Directory(
      'lib/features',
    ).listSync().whereType<Directory>()) {
      for (final file in feature.listSync().whereType<File>()) {
        if (!file.path.endsWith('.dart') || file.path.endsWith('_page.dart')) {
          continue;
        }
        final source = file.readAsStringSync();
        if (RegExp(r'extends\s+\w*Widget\b').hasMatch(source)) {
          violations.add(
            '${_libPath(file.path)}: feature visual components belong under widgets/.',
          );
        }
      }
    }

    expect(violations, isEmpty, reason: violations.join('\n'));
  });

  test('feature pages declare only their route widget', () {
    final violations = <String>[];
    final widgetDeclaration = RegExp(r'class\s+(\w+)\s+extends\s+\w*Widget\b');

    for (final file
        in Directory('lib/features')
            .listSync(recursive: true)
            .whereType<File>()
            .where((file) => file.path.endsWith('_page.dart'))) {
      final sourcePath = _libPath(file.path);
      final declarations = widgetDeclaration
          .allMatches(file.readAsStringSync())
          .map((match) => match.group(1)!)
          .toList(growable: false);

      if (declarations.length != 1 || !declarations.single.endsWith('Page')) {
        violations.add(
          '$sourcePath: pages declare one route widget only; '
          'move visual sub-widgets under the feature widgets/ directory '
          '(found: ${declarations.join(', ')}).',
        );
      }
    }

    expect(violations, isEmpty, reason: violations.join('\n'));
  });

  test('feature widgets do not depend on application workflows', () {
    final violations = <String>[];

    for (final file
        in Directory('lib/features')
            .listSync(recursive: true)
            .whereType<File>()
            .where(
              (file) =>
                  file.path.endsWith('.dart') &&
                  _libPath(file.path).contains('/widgets/'),
            )) {
      for (final directive in _dependencyDirectives(file.readAsStringSync())) {
        for (final uri in directive.uris) {
          if (uri.startsWith('package:hikari/application/') ||
              uri.contains('/application/')) {
            violations.add(
              '${_libPath(file.path)}: feature widgets render state; application workflows belong in a ViewModel ($uri).',
            );
          }
        }
      }
    }

    expect(violations, isEmpty, reason: violations.join('\n'));
  });

  test('feature views do not execute application workflows directly', () {
    final violations = <String>[];

    for (final file
        in Directory('lib/features')
            .listSync(recursive: true)
            .whereType<File>()
            .where((file) {
              final path = _libPath(file.path);
              return file.path.endsWith('.dart') &&
                  (path.endsWith('_page.dart') || path.contains('/widgets/'));
            })) {
      if (RegExp(r'\.execute\s*\(').hasMatch(file.readAsStringSync())) {
        violations.add(
          '${_libPath(file.path)}: views render state and forward intent; '
          'execute application workflows in a feature ViewModel.',
        );
      }
    }

    expect(violations, isEmpty, reason: violations.join('\n'));
  });

  test('feature view models do not depend on views', () {
    final violations = <String>[];

    for (final file
        in Directory('lib/features')
            .listSync(recursive: true)
            .whereType<File>()
            .where((file) => file.path.endsWith('_view_model.dart'))) {
      final source = file.readAsStringSync();
      for (final directive in _dependencyDirectives(source)) {
        for (final uri in directive.uris) {
          if (uri.startsWith('package:flutter/') &&
              uri != 'package:flutter/foundation.dart') {
            violations.add(
              '${_libPath(file.path)}: view models may depend on Flutter foundation.dart only ($uri).',
            );
          }
          if (uri.contains('/widgets/') || uri.endsWith('_page.dart')) {
            violations.add(
              '${_libPath(file.path)}: view models must not import view code ($uri).',
            );
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
        sourcePath: 'infrastructure/playback/video_surface.dart',
        sourceLayer: 'infrastructure',
        kind: 'import',
        uri: 'package:flutter/material.dart',
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
      (
        sourcePath: 'app/theme/hikari_theme.dart',
        sourceLayer: 'app',
        kind: 'import',
        uri: 'package:hikari/features/library/library_page.dart',
      ),
      (
        sourcePath: 'core/ui/components/hikari_button.dart',
        sourceLayer: 'core',
        kind: 'import',
        uri: 'package:hikari/features/library/library_page.dart',
      ),
      (
        sourcePath: 'core/ui/components/hikari_button.dart',
        sourceLayer: 'core',
        kind: 'import',
        uri: 'package:hikari/infrastructure/persistence/user_database.dart',
      ),
      (
        sourcePath: 'features/library/library_page.dart',
        sourceLayer: 'features',
        kind: 'import',
        uri: 'package:hikari/app/app.dart',
      ),
      (
        sourcePath: 'core/result.dart',
        sourceLayer: 'core',
        kind: 'import',
        uri: 'package:flutter/material.dart',
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
        sourcePath: 'infrastructure/local_media/channel.dart',
        sourceLayer: 'infrastructure',
        kind: 'import',
        uri: 'package:flutter/services.dart',
      ),
      (
        sourcePath: 'infrastructure/local_media/source.dart',
        sourceLayer: 'infrastructure',
        kind: 'import',
        uri: 'package:flutter/foundation.dart',
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
      (
        sourcePath: 'core/ui/components/hikari_button.dart',
        sourceLayer: 'core',
        kind: 'import',
        uri: 'package:flutter/material.dart',
      ),
      (
        sourcePath: 'core/ui/components/hikari_button.dart',
        sourceLayer: 'core',
        kind: 'import',
        uri: 'package:hikari/core/ui/foundation/hikari_spacing.dart',
      ),
      (
        sourcePath: 'core/ui/patterns/media_poster.dart',
        sourceLayer: 'core',
        kind: 'import',
        uri: 'package:hikari/core/ui/controls/hikari_filter_chip.dart',
      ),
      (
        sourcePath: 'core/ui/patterns/media_poster.dart',
        sourceLayer: 'core',
        kind: 'import',
        uri: 'package:hikari/domain/media/media.dart',
      ),
      (
        sourcePath: 'features/library/library_page.dart',
        sourceLayer: 'features',
        kind: 'import',
        uri: 'package:hikari/core/ui/foundation/hikari_spacing.dart',
      ),
      (
        sourcePath: 'features/library/library_page.dart',
        sourceLayer: 'features',
        kind: 'import',
        uri: 'package:hikari/core/ui/components/hikari_button.dart',
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

  test('theme inversion is forbidden even with exact legacy exemption', () {
    for (final path in [
      'lib/core/ui/components/example.dart',
      'lib/features/example/page.dart',
    ]) {
      for (final kind in ['import', 'export', 'part']) {
        const uri = 'package:hikari/app/theme/hikari_theme.dart';
        final source = "$kind '$uri';";
        final offset = source.indexOf("'");
        final id =
            '$path:${offset.toString().padLeft(8, '0')}:legacy-$kind:$uri';
        expect(_uiViolations(path, source, legacy: {id}), isNotEmpty);
        expect(
          _checkDependency(
            sourcePath: path.substring(4),
            sourceLayer: path.contains('/features/') ? 'features' : 'core',
            kind: kind,
            uri: uri,
          ),
          isNotNull,
        );
      }
    }
  });

  test('core and features contain zero app theme dependencies', () {
    for (final root in ['lib/core/ui', 'lib/features']) {
      for (final file
          in Directory(root)
              .listSync(recursive: true)
              .whereType<File>()
              .where((file) => file.path.endsWith('.dart'))) {
        final path = 'lib/${_libPath(file.path)}';
        for (final directive in _dependencyDirectives(
          file.readAsStringSync(),
        )) {
          for (final uri in directive.uris) {
            expect(
              _internalPath(path.substring(4), uri)?.startsWith('app/theme/') ??
                  false,
              isFalse,
              reason: '$path: ${directive.kind} $uri',
            );
          }
        }
      }
    }
  });

  test('temporary token alias files are removed after caller migration', () {
    for (final name in [
      'breakpoints',
      'motion',
      'radius',
      'size',
      'spacing',
      'status_colors',
      'typography',
    ]) {
      expect(File('lib/app/theme/hikari_$name.dart').existsSync(), isFalse);
    }
  });

  test('temporary component forwarders have no remaining callers', () {
    for (final path in [
      'components/hikari_button.dart',
      'components/hikari_icon_button.dart',
      'components/hikari_choice_menu.dart',
      'patterns/hikari_section_header.dart',
    ]) {
      expect(File('lib/core/ui/$path').existsSync(), isFalse, reason: path);
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

Iterable<String> _uiViolations(
  String path,
  String source, {
  required Set<String> legacy,
}) sync* {
  final tokens = dartTokens(source);
  final coreUi = path.startsWith('lib/core/ui/');
  final foundation = path.startsWith('lib/core/ui/foundation/');
  final feature = path.startsWith('lib/features/');
  for (var i = 0; i < tokens.length; i++) {
    final token = tokens[i];
    if (token.isString ||
        !const {'import', 'export', 'part'}.contains(token.text)) {
      continue;
    }
    for (var j = i + 1; j < tokens.length && tokens[j].text != ';'; j++) {
      if (!tokens[j].isString) continue;
      final uri = tokens[j].text;
      final target = internalLibPath(path, uri);
      if (target == null) continue;
      final forbidden =
          coreUi && !target.startsWith('lib/core/ui/') ||
          feature &&
              (target.startsWith('lib/app/') ||
                  target.startsWith('lib/infrastructure/')) ||
          foundation && !target.startsWith('lib/core/ui/foundation/');
      if (!forbidden) continue;
      final id =
          '$path:${tokens[j].offset.toString().padLeft(8, '0')}:legacy-${token.text}:$uri';
      if (foundation ||
          target.startsWith('lib/app/theme/') ||
          !legacy.contains(id)) {
        yield '$path:${tokens[j].line}: forbidden ${token.text} $uri';
      }
    }
  }
  if (feature) {
    for (final row in inventorySources({path: source})) {
      if (row['kind'] == 'modal-entrypoint' && !legacy.contains(row['id'])) {
        yield '$path:${row['line']}: direct modal ${row['symbol']} needs shared host or exact legacy exemption';
      }
    }
  }
}

String? _checkDependency({
  required String sourcePath,
  required String sourceLayer,
  required String kind,
  required String uri,
}) {
  final parsed = Uri.tryParse(uri);
  final isCoreUi = sourcePath.startsWith('core/ui/');
  final isAppTheme = sourcePath.startsWith('app/theme/');

  if (_pureLayers.contains(sourceLayer) && !isCoreUi) {
    if (parsed?.scheme == 'package' &&
        (parsed!.pathSegments.isEmpty ||
            parsed.pathSegments.first != 'hikari')) {
      return '$sourceLayer cannot depend on external packages: $kind $uri';
    }
    if (_platformLibraries.contains(uri)) {
      return '$sourceLayer must stay platform-independent: $kind $uri';
    }
  }

  if (isCoreUi || isAppTheme) {
    if (parsed?.scheme == 'package') {
      final package = parsed!.pathSegments.isEmpty
          ? ''
          : parsed.pathSegments.first;
      if (package != 'hikari' && package != 'flutter') {
        return '$sourcePath cannot depend on external package: $kind $uri';
      }
    }
    if (_platformLibraries.contains(uri)) {
      return '$sourcePath must stay platform-independent: $kind $uri';
    }
  }

  if (sourceLayer == 'infrastructure' &&
      parsed != null &&
      parsed.scheme == 'package' &&
      parsed.pathSegments.isNotEmpty &&
      parsed.pathSegments.first == 'flutter' &&
      !_allowedInfrastructureFlutterLibraries.contains(uri)) {
    return 'infrastructure may only use Flutter foundation/services APIs: $kind $uri';
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

  if ((isCoreUi || sourceLayer == 'features') &&
      targetPath.startsWith('app/theme/')) {
    return '$sourcePath cannot depend on app/theme: $kind $uri';
  }

  if (kind == 'part' || kind == 'part of') {
    return sourceLayer == targetLayer
        ? null
        : '$kind must stay inside $sourceLayer: $uri';
  }

  if (isAppTheme) {
    if (targetPath.startsWith('app/theme/') ||
        targetPath.startsWith('core/ui/foundation/')) {
      return null;
    }
    return 'app/theme may only compose foundation tokens: $targetPath ($kind $uri)';
  }

  if (sourceLayer == 'features' && targetLayer == 'app') {
    return 'features cannot depend on app root/wiring ($targetPath): $kind $uri';
  }

  if (isCoreUi) {
    if (targetPath.startsWith('core/ui/')) {
      return null;
    }
    if (sourcePath.startsWith('core/ui/patterns/') && targetLayer == 'domain') {
      return null;
    }
    return '$sourcePath cannot depend on $targetPath: $kind $uri';
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
