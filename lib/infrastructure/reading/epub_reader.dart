import 'dart:convert';
import 'dart:typed_data';

import 'package:html/dom.dart';
import 'package:html/parser.dart' as html_parser;
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/publication.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/infrastructure/local_media/bounded_archive.dart';
import 'package:hikari/infrastructure/reading/safe_html.dart';
import 'package:xml/xml.dart';

final class EpubReader {
  EpubReader._(this._archive, this._publication, this._manifest);

  final BoundedArchive _archive;
  final Publication _publication;
  final Map<String, _ManifestItem> _manifest;
  bool _closed = false;

  static EpubReader open(String materializedPath) {
    final archive = BoundedArchive.open(materializedPath);
    try {
      final names = archive.names.toSet();
      if (names.any((name) => name.toLowerCase() == 'meta-inf/rights.xml') ||
          names.any(
            (name) => name.toLowerCase() == 'meta-inf/encryption.xml',
          )) {
        throw const FormatException(
          'Encrypted or DRM-protected EPUB is unsupported.',
        );
      }
      final containerName = _find(names, 'META-INF/container.xml');
      if (containerName == null) {
        throw const FormatException('EPUB container.xml is missing.');
      }
      final container = _xml(archive.readEntry(containerName));
      final rootfile = container
          .findAllElements('rootfile')
          .firstWhereOrNull(
            (node) =>
                (node.getAttribute('media-type') ?? '') ==
                'application/oebps-package+xml',
          );
      if (rootfile == null) {
        throw const FormatException('EPUB package rootfile is missing.');
      }
      final rootfilePath = _resolve('', rootfile.getAttribute('full-path'));
      if (rootfilePath == null || !names.contains(rootfilePath)) {
        throw const FormatException('EPUB package document is missing.');
      }
      final package = _xml(archive.readEntry(rootfilePath));
      final packageDir = _directory(rootfilePath);
      final manifest = <String, _ManifestItem>{};
      final manifestNode = package.findAllElements('manifest').firstOrNull;
      if (manifestNode == null) {
        throw const FormatException('EPUB manifest is missing.');
      }
      for (final item in manifestNode.findElements('item')) {
        final id = item.getAttribute('id');
        final href = item.getAttribute('href');
        final mediaType = item.getAttribute('media-type');
        if (id == null || href == null || mediaType == null || id.isEmpty) {
          throw const FormatException('EPUB manifest item is malformed.');
        }
        final resource = _resolve(packageDir, href);
        if (resource == null || !names.contains(resource)) {
          throw const FormatException(
            'EPUB manifest path is unsafe or missing.',
          );
        }
        final properties = (item.getAttribute('properties') ?? '')
            .split(RegExp(r'\s+'))
            .where((value) => value.isNotEmpty)
            .toSet();
        if (manifest.containsKey(id) ||
            manifest.values.any((item) => item.resource == resource)) {
          throw const FormatException(
            'EPUB manifest contains duplicate items.',
          );
        }
        manifest[id] = _ManifestItem(resource, mediaType, properties);
      }
      final spineNode = package.findAllElements('spine').firstOrNull;
      if (spineNode == null) {
        throw const FormatException('EPUB spine is missing.');
      }
      final spine = <PublicationSection>[];
      for (final itemref in spineNode.findElements('itemref')) {
        final idref = itemref.getAttribute('idref');
        final item = idref == null ? null : manifest[idref];
        if (item == null || !_isDocument(item.mediaType)) {
          throw const FormatException('EPUB spine item is malformed.');
        }
        if (spine.any((section) => section.resource == item.resource)) {
          throw const FormatException(
            'Repeated EPUB spine resources are unsupported.',
          );
        }
        spine.add(
          PublicationSection(
            resource: item.resource,
            title: item.resource.split('/').last,
          ),
        );
      }
      if (spine.isEmpty) throw const FormatException('EPUB spine is empty.');
      final metadata = _metadata(package);
      final nav = manifest.values
          .where((item) => item.properties.contains('nav'))
          .firstOrNull;
      final toc = nav == null
          ? _readNcx(archive, package, manifest, packageDir)
          : _readNav(archive, nav.resource, packageDir);
      if (toc.any(
        (link) => !spine.any((section) => section.resource == link.resource),
      )) {
        throw const FormatException(
          'EPUB navigation target is missing from spine.',
        );
      }
      final resources = manifest.values
          .where((item) => !_isDocument(item.mediaType))
          .map(
            (item) => PublicationResource(
              resource: item.resource,
              mediaType: item.mediaType,
            ),
          )
          .toList();
      return EpubReader._(
        archive,
        Publication(
          metadata: metadata,
          spine: spine,
          toc: toc,
          resources: resources,
        ),
        manifest,
      );
    } catch (_) {
      archive.close();
      rethrow;
    }
  }

  Future<Publication> loadPublication() async {
    _checkOpen();
    return _publication;
  }

  Future<RichReadingContent> readSection(String resource) async {
    _checkOpen();
    final item = _manifest.values
        .where((item) => item.resource == resource)
        .firstOrNull;
    if (item == null || !_isDocument(item.mediaType)) {
      throw const FormatException('EPUB chapter resource is unknown.');
    }
    final source = utf8.decode(
      _archive.readEntry(resource),
      allowMalformed: true,
    );
    final document = html_parser.parse(source);
    _applyLinkedStyles(document, resource);
    final resources = <String, SourceMediaRef>{};
    for (final element in document.querySelectorAll('img, image, link, a')) {
      final attribute = element.localName == 'link' || element.localName == 'a'
          ? 'href'
          : 'src';
      final value = element.attributes[attribute];
      if (value == null) continue;
      if (value.startsWith('#') && element.localName == 'a') continue;
      final uri = Uri.tryParse(value);
      final resolved = _resolve(_directory(resource), value);
      if (resolved == null) {
        if (uri?.scheme == 'http' || uri?.scheme == 'https') {
          element.attributes.remove(attribute);
          continue;
        }
        throw const FormatException('EPUB resource path escapes archive root.');
      }
      final target = _manifest.values
          .where((item) => item.resource == resolved)
          .firstOrNull;
      if (element.localName == 'a') {
        if (target == null || !_isDocument(target.mediaType)) {
          throw const FormatException('EPUB link target is unknown.');
        }
        element.attributes[attribute] = Uri(
          path: target.resource,
          fragment: uri!.hasFragment ? uri.fragment : null,
        ).toString();
        continue;
      }
      if (target == null || !_isResource(target.mediaType)) {
        element.attributes.remove(attribute);
        continue;
      }
      final placeholder = 'hikari-resource:${target.resource}';
      element.attributes[attribute] = placeholder;
      resources[placeholder] = SourceMediaRef(
        sourceId: SourceId.local,
        itemId: target.resource,
      );
    }
    return RichReadingContent(
      html: sanitizeNovelHtml(
        document.body?.innerHtml ?? document.outerHtml,
        registeredResources: resources.keys.toSet(),
      ),
      resources: resources,
    );
  }

  void _applyLinkedStyles(Document document, String resource) {
    final links = document.querySelectorAll('link[rel="stylesheet"]');
    for (final link in links) {
      final href = link.attributes['href'];
      final stylesheet = href == null
          ? null
          : _resolve(_directory(resource), href);
      if (stylesheet == null) continue;
      final item = _manifest.values
          .where((item) => item.resource == stylesheet)
          .firstOrNull;
      if (item == null || item.mediaType != 'text/css') continue;
      final css = utf8.decode(
        _archive.readEntry(stylesheet, maxBytes: 256 * 1024),
        allowMalformed: true,
      );
      _applySafeStyles(document, css);
    }
  }

  void _applySafeStyles(Document document, String css) {
    if (css.length > 256 * 1024) {
      throw const FormatException('Stylesheet exceeds the reader size limit.');
    }
    for (final rule in css.split('}')) {
      final brace = rule.indexOf('{');
      if (brace < 1) continue;
      final selector = rule.substring(0, brace).trim();
      final declarations = rule.substring(brace + 1);
      if (selector.isEmpty ||
          selector.contains('@') ||
          selector.contains(':') ||
          selector.contains('[') ||
          selector.contains(']') ||
          selector.contains(',') ||
          selector.contains('>') ||
          selector.contains('+') ||
          selector.contains('~') ||
          selector.length > 128) {
        continue;
      }
      final safe = sanitizeReaderStyle(declarations);
      if (safe.isEmpty) continue;
      for (final element in document.querySelectorAll(selector)) {
        final existing = element.attributes['style'];
        element.attributes['style'] = existing == null || existing.isEmpty
            ? safe
            : '$existing;$safe';
      }
    }
  }

  Future<Uint8List> readResource(SourceMediaRef resource) async {
    _checkOpen();
    if (resource.sourceId != SourceId.local) {
      throw ArgumentError('Wrong source.');
    }
    final item = _manifest.values
        .where((item) => item.resource == resource.itemId)
        .firstOrNull;
    if (item == null || !_isResource(item.mediaType)) {
      throw const FormatException('EPUB resource is unknown.');
    }
    return _archive.readEntry(item.resource);
  }

  void close() {
    if (_closed) return;
    _closed = true;
    _archive.close();
  }

  void _checkOpen() {
    if (_closed) throw StateError('Publication is closed.');
  }
}

final class _ManifestItem {
  const _ManifestItem(
    this.resource,
    this.mediaType, [
    this.properties = const {},
  ]);
  final String resource;
  final String mediaType;
  final Set<String> properties;
}

bool _isDocument(String type) =>
    type == 'application/xhtml+xml' || type == 'text/html';
bool _isResource(String type) =>
    type.startsWith('image/') ||
    type == 'text/css' ||
    type == 'font/otf' ||
    type == 'font/ttf';
String _directory(String path) =>
    path.contains('/') ? path.substring(0, path.lastIndexOf('/') + 1) : '';
String? _find(Set<String> names, String wanted) => names.firstWhereOrNull(
  (name) => name.toLowerCase() == wanted.toLowerCase(),
);

String? _resolve(String base, String? raw) {
  if (raw == null ||
      raw.isEmpty ||
      RegExp(r'[\x00-\x20\x7f\\]').hasMatch(raw)) {
    return null;
  }
  final uri = Uri.tryParse(raw);
  if (uri == null ||
      uri.hasScheme ||
      uri.hasAuthority ||
      uri.hasQuery ||
      raw.startsWith('/')) {
    return null;
  }
  final parts = [...base.split('/').where((part) => part.isNotEmpty)];
  try {
    for (final encoded in uri.path.split('/')) {
      final part = Uri.decodeComponent(encoded);
      if (RegExp(r'[/\\:\x00-\x1f\x7f]').hasMatch(part)) return null;
      if (part.isEmpty || part == '.') continue;
      if (part == '..') {
        if (parts.isEmpty) return null;
        parts.removeLast();
      } else {
        parts.add(part);
      }
    }
  } on FormatException {
    return null;
  }
  final result = parts.join('/');
  return result.isEmpty ? null : result;
}

XmlDocument _xml(Uint8List bytes) =>
    XmlDocument.parse(utf8.decode(bytes, allowMalformed: true));
MediaMetadata _metadata(XmlDocument package) {
  final elements = package.descendants.whereType<XmlElement>();
  String? value(String name) => elements
      .where((element) => element.localName == name)
      .firstOrNull
      ?.innerText
      .trim();
  final title = value('title');
  if (title == null || title.isEmpty) {
    throw const FormatException('EPUB title is missing.');
  }
  return MediaMetadata(
    title: title,
    authors: elements
        .where((element) => element.localName == 'creator')
        .map((node) => node.innerText.trim())
        .where((value) => value.isNotEmpty)
        .toList(),
    language: value('language'),
    publisher: value('publisher'),
    summary: value('description'),
  );
}

List<PublicationLink> _readNav(
  BoundedArchive archive,
  String resource,
  String base,
) {
  final document = html_parser.parse(
    utf8.decode(archive.readEntry(resource), allowMalformed: true),
  );
  final links = <PublicationLink>[];
  final navs = document.querySelectorAll('nav');
  final toc = navs
      .where(
        (nav) => nav.attributes.entries.any(
          (attribute) =>
              attribute.key.toString().endsWith('type') &&
              attribute.value.split(RegExp(r'\s+')).contains('toc'),
        ),
      )
      .firstOrNull;
  // Older exports omit epub:type on their sole navigation element.
  final navigation = toc ?? (navs.length == 1 ? navs.single : null);
  if (navigation == null) {
    throw const FormatException('EPUB table of contents is missing.');
  }
  for (final anchor in navigation.querySelectorAll('a')) {
    final href = anchor.attributes['href'];
    final target = href == null ? null : _resolve(_directory(resource), href);
    if (target == null) {
      throw const FormatException('EPUB navigation path is unsafe.');
    }
    final uri = Uri.parse(href!);
    links.add(
      PublicationLink(
        label: anchor.text.trim(),
        resource: target,
        fragment: uri.fragment.isEmpty ? null : uri.fragment,
      ),
    );
  }
  return links;
}

List<PublicationLink> _readNcx(
  BoundedArchive archive,
  XmlDocument package,
  Map<String, _ManifestItem> manifest,
  String base,
) {
  final tocId = package
      .findAllElements('spine')
      .firstOrNull
      ?.getAttribute('toc');
  final item = tocId == null
      ? manifest.values
            .where((item) => item.mediaType == 'application/x-dtbncx+xml')
            .firstOrNull
      : manifest[tocId];
  if (tocId != null && item?.mediaType != 'application/x-dtbncx+xml') {
    throw const FormatException('EPUB NCX reference is invalid.');
  }
  if (item == null) return const [];
  final document = _xml(archive.readEntry(item.resource));
  return document
      .findAllElements('navPoint')
      .map((point) {
        final label =
            point.findAllElements('text').firstOrNull?.innerText.trim() ?? '';
        final src = point
            .findAllElements('content')
            .firstOrNull
            ?.getAttribute('src');
        if (src == null) {
          throw const FormatException('EPUB NCX target is missing.');
        }
        final target = _resolve(_directory(item.resource), src);
        if (target == null) {
          throw const FormatException('EPUB navigation path is unsafe.');
        }
        final uri = Uri.parse(src);
        return PublicationLink(
          label: label,
          resource: target,
          fragment: uri.fragment.isEmpty ? null : uri.fragment,
        );
      })
      .whereType<PublicationLink>()
      .toList();
}

extension _FirstOrNull<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
  T? firstWhereOrNull(bool Function(T value) test) {
    for (final value in this) {
      if (test(value)) return value;
    }
    return null;
  }
}
