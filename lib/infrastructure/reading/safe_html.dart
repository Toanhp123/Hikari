import 'dart:convert';

import 'package:html/dom.dart';
import 'package:html/parser.dart' as html;

/// Reader HTML is inert. Images are resolved by the source, never by a WebView.
String sanitizeNovelHtml(String input) {
  if (input.length > _maxHtmlBytes || utf8.encode(input).length > _maxHtmlBytes) {
    throw const FormatException('Chapter exceeds the reader size limit.');
  }
  final document = html.parseFragment(input);
  final elements = document.querySelectorAll('*');
  if (elements.length > 50000) {
    throw const FormatException('Chapter contains too many elements.');
  }
  for (final element in elements.reversed) {
    if (_discard.contains(element.localName)) {
      element.remove();
      continue;
    }
    if (!_tags.contains(element.localName)) {
      final parent = element.parentNode;
      if (parent != null) {
        final index = parent.nodes.indexOf(element);
        final children = element.nodes.toList();
        element.remove();
        parent.nodes.insertAll(index, children);
      }
      continue;
    }
    element.attributes.removeWhere((key, value) {
      final name = key.toString().toLowerCase();
      if (!_attributes.contains(name)) return true;
      if (name == 'src' || name == 'href') return !_safeResource(value);
      if (name == 'dir') return !{'rtl', 'ltr', 'auto'}.contains(value);
      return false;
    });
    final style = element.attributes['style'];
    if (style != null) {
      final safe = sanitizeReaderStyle(style);
      if (safe.isEmpty) {
        element.attributes.remove('style');
      } else {
        element.attributes['style'] = safe;
      }
    }
  }
  document.nodes.removeWhere((node) => node is Comment);
  return document.outerHtml;
}

String sanitizeReaderStyle(String input) => input
    .split(';')
    .where((declaration) {
      final colon = declaration.indexOf(':');
      if (colon < 1) return false;
      final property = declaration.substring(0, colon).trim().toLowerCase();
      final value = declaration.substring(colon + 1).trim();
      return _cssProperties.contains(property) &&
          value.length <= 128 &&
          RegExp(r'^[a-zA-Z0-9\s#.,%+\-]+$').hasMatch(value);
    })
    .join(';');

bool _safeResource(String value) {
  if (value.isEmpty || value.length > 8192 ||
      RegExp(r'[\x00-\x20\x7f\\]').hasMatch(value) ||
      value.startsWith('//')) {
    return false;
  }
  final uri = Uri.tryParse(value);
  return uri != null &&
      (!uri.hasScheme || uri.scheme == 'https' || uri.scheme == 'http');
}

const _maxHtmlBytes = 4 * 1024 * 1024;
const _discard = {
  'script', 'iframe', 'object', 'embed', 'form', 'input', 'button', 'select',
  'textarea', 'svg', 'math', 'meta', 'link', 'base', 'style', 'audio', 'video',
  'source', 'canvas', 'template',
};
const _tags = {
  'p', 'div', 'span', 'section', 'article', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'br', 'hr', 'em', 'strong', 'b', 'i', 'u', 's', 'del', 'small', 'sup', 'sub',
  'blockquote', 'pre', 'code', 'ul', 'ol', 'li', 'dl', 'dt', 'dd', 'table',
  'thead', 'tbody', 'tfoot', 'tr', 'td', 'th', 'caption', 'img', 'a', 'figure',
  'figcaption', 'ruby', 'rt', 'rp', 'bdi', 'bdo',
};
const _attributes = {
  'id', 'class', 'title', 'alt', 'src', 'href', 'dir', 'lang', 'style',
  'colspan', 'rowspan',
};
const _cssProperties = {
  'color', 'background-color', 'font-size', 'font-weight', 'font-style',
  'font-family', 'line-height', 'text-align', 'text-decoration', 'text-indent',
  'letter-spacing', 'word-spacing', 'direction', 'white-space', 'vertical-align',
  'margin', 'margin-top', 'margin-bottom', 'padding',
};
