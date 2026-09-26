import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/infrastructure/local_media/classifier.dart';

void main() {
  LocalEntry file(String name, {String parent = 'root'}) => LocalEntry(
    id: '$parent/$name',
    parentId: parent,
    name: name,
    isDirectory: false,
  );
  const root = LocalEntry(
    id: 'root',
    parentId: null,
    name: 'My Manga',
    isDirectory: true,
  );

  test('video and text extensions classify case insensitively', () {
    final items = classifyLocalEntries([
      root,
      for (final ext in ['mp4', 'MKV', 'webm', 'm4v']) file('test.$ext'),
      file('sample.TXT'),
      file('chapter.one.md'),
    ]);
    expect(items.where((m) => m.type == MediaType.anime), hasLength(4));
    expect(items.where((m) => m.type == MediaType.lightNovel), hasLength(2));
    expect(
      items.map((m) => m.title),
      containsAll(['test', 'sample', 'chapter.one']),
    );
    expect(items.every((m) => m.source.sourceId == SourceId.local), isTrue);
    expect(items.map((m) => m.source.itemId), contains('root/test.MKV'));
  });

  test('only direct image parent becomes manga, images never become media', () {
    final entries = [
      root,
      const LocalEntry(
        id: 'child',
        parentId: 'root',
        name: 'Pages',
        isDirectory: true,
      ),
      for (final ext in ['jpg', 'JPEG', 'png', 'webp'])
        file('1.$ext', parent: 'child'),
      file('ignored.pdf'),
      file('archive.cbz'),
      file('book.epub'),
    ];
    final items = classifyLocalEntries(entries);
    expect(items, hasLength(1));
    expect(items.single.title, 'Pages');
    expect(items.single.type, MediaType.manga);
    expect(items.single.source.itemId, 'child');
  });

  test(
    'selected root itself can be manga and mixed files remain discoverable',
    () {
      final items = classifyLocalEntries([
        root,
        file('1.jpg'),
        file('movie.mp4'),
        file('note.txt'),
      ]);
      expect(items.map((m) => m.type).toSet(), MediaType.values.toSet());
      expect(
        items.singleWhere((m) => m.type == MediaType.manga).title,
        'My Manga',
      );
    },
  );

  test('unsupported and empty trees emit nothing', () {
    expect(
      classifyLocalEntries([root, file('a.zip'), file('a'), file('a.gif')]),
      isEmpty,
    );
  });

  test(
    'numeric order and tie breaks are deterministic without integer limits',
    () {
      final names = [
        '10.jpg',
        '2.jpg',
        '1.jpg',
        '01.jpg',
        'A2.jpg',
        'a2.jpg',
        '999999999999999999999999999999.jpg',
      ];
      final sorted = [...names]..sort(compareLocalNames);
      expect(sorted.take(4), ['01.jpg', '1.jpg', '2.jpg', '10.jpg']);
      expect([...names.reversed]..sort(compareLocalNames), sorted);
    },
  );

  test('result order does not depend on provider enumeration order', () {
    final entries = [root, file('10.txt'), file('2.txt'), file('1.txt')];
    expect(classifyLocalEntries(entries).map((m) => m.title), ['1', '2', '10']);
    expect(
      classifyLocalEntries(entries.reversed.toList()).map((m) => m.title),
      ['1', '2', '10'],
    );
  });
}
