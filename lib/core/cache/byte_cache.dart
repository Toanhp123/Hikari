import 'dart:typed_data';

abstract interface class ByteCache {
  Future<Uint8List?> read(String namespace, String key);
  Future<void> write(String namespace, String key, Uint8List bytes);
  Future<void> close();
}
