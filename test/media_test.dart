import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';

void main() {
  test('source identifiers compare by opaque value', () {
    expect(const SourceId('local'), SourceId.local);
    expect(
      const SourceId('extension.example'),
      const SourceId('extension.example'),
    );
    expect(const SourceId('extension.example'), isNot(const SourceId('other')));
    expect(const SourceId('local').hashCode, SourceId.local.hashCode);
  });

  test('source media reference keeps its source-owned locator', () {
    const ref = SourceMediaRef(
      sourceId: SourceId('extension.example'),
      itemId: 'opaque://provider-owned/item',
    );

    expect(ref.sourceId, const SourceId('extension.example'));
    expect(ref.itemId, 'opaque://provider-owned/item');
  });
}
