import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

void main() {
  test('catalog identity is provider-qualified and opaque', () {
    const first = CatalogEntryId(provider: 'anilist', value: '42');
    expect(first, const CatalogEntryId(provider: 'anilist', value: '42'));
    expect(first, isNot(const CatalogEntryId(provider: 'other', value: '42')));
    final identities = <CatalogEntryId>{first};
    identities.add(const CatalogEntryId(provider: 'anilist', value: '42'));
    expect(identities, hasLength(1));
    expect(first, isNot(isA<SourceMediaRef>()));
  });

  test('catalog entry collections are immutable', () {
    final entry = CatalogEntry(
      id: const CatalogEntryId(provider: 'anilist', value: '42'),
      title: 'Title',
      type: MediaType.anime,
      coverUrl: 'https://example/cover',
      genres: ['Drama'],
    );
    final details = CatalogEntryDetails(entry: entry, synonyms: ['Alt']);
    expect(() => entry.genres.add('x'), throwsUnsupportedError);
    expect(() => details.synonyms.add('x'), throwsUnsupportedError);
  });
}
