import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/infrastructure/reading/safe_html.dart';

void main() {
  test('keeps novel formatting and source-owned images', () {
    final content = sanitizeNovelHtml(
      '<h2>第一章</h2><p dir="rtl">مرحبا <em>world</em></p>'
      '<img src="/image.png" alt="Illustration">',
    );
    expect(content, contains('<h2>第一章</h2>'));
    expect(content, contains('dir="rtl"'));
    expect(content, contains('<em>world</em>'));
    expect(content, contains('src="/image.png"'));
  });

  test('removes executable content and unsafe navigation', () {
    final content = sanitizeNovelHtml(
      '<script>secret()</script><iframe src="https://evil.test"></iframe>'
      '<object data="file:///private"></object><form><input></form>'
      '<p onclick="steal()">Safe</p><img src="file:///private" onerror="x()">'
      '<a href="javascript:steal()">link</a><svg onload="x()"></svg>'
      '<meta http-equiv="refresh" content="0;url=https://evil.test">',
    );
    for (final forbidden in [
      '<script',
      '<iframe',
      '<object',
      '<form',
      '<input',
      '<svg',
      '<meta',
      'onclick',
      'onerror',
      'javascript:',
      'file:',
      'secret()',
    ]) {
      expect(content, isNot(contains(forbidden)));
    }
    expect(content, contains('Safe'));
  });

  test('rejects oversized input rather than truncating content', () {
    expect(
      () => sanitizeNovelHtml('x' * (4 * 1024 * 1024 + 1)),
      throwsFormatException,
    );
  });

  test('strips network-bearing CSS and retains safe typography', () {
    final content = sanitizeNovelHtml(
      '<p style="color: red; background: url(https://evil.test); font-size: 18px;'
      ' position:fixed; display:none">Text</p>',
    );
    expect(content, contains('color: red'));
    expect(content, contains('font-size: 18px'));
    expect(content, isNot(contains('url(')));
    expect(content, isNot(contains('fixed')));
    expect(content, isNot(contains('display')));
  });

  test(
    'keeps internal links but rejects encoded and protocol-relative URLs',
    () {
      final content = sanitizeNovelHtml(
        '<a href="#section">Section</a><a href="chapter2.xhtml#next">Next</a>'
        '<a href="java&#x09;script:alert(1)">Bad</a>'
        '<img src="//evil.test/image"><img src="data:image/svg+xml,evil">',
      );
      expect(content, contains('href="#section"'));
      expect(content, contains('href="chapter2.xhtml#next"'));
      expect(content, isNot(contains('alert')));
      expect(content, isNot(contains('//evil')));
      expect(content, isNot(contains('data:')));
    },
  );
}
