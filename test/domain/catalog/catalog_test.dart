import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

void main() {
  test('catalog identity is provider-qualified and opaque', () {
    const first = CatalogMediaId(provider: 'anilist', value: '42');
    expect(first, const CatalogMediaId(provider: 'anilist', value: '42'));
    expect(first, isNot(const CatalogMediaId(provider: 'other', value: '42')));
    final identities = <CatalogMediaId>{first};
    identities.add(const CatalogMediaId(provider: 'anilist', value: '42'));
    expect(identities, hasLength(1));
    expect(first, isNot(isA<SourceMediaRef>()));
  });

  test('media collections are immutable', () {
    final media = CatalogMedia(
      id: const CatalogMediaId(provider: 'anilist', value: '42'),
      title: 'Title',
      type: MediaType.anime,
      coverUrl: 'https://example/cover',
      synonyms: ['Alt'],
      genres: ['Drama'],
    );
    expect(() => media.synonyms.add('x'), throwsUnsupportedError);
  });
}
