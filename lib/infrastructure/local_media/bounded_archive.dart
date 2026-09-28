import 'dart:convert';
import 'dart:typed_data';

import 'package:archive/archive.dart';

const maxArchiveEntries = 10000;
const maxArchiveEntryBytes = 32 * 1024 * 1024;
const maxArchiveTotalBytes = 512 * 1024 * 1024;
const maxArchiveCompressedBytes = 1024 * 1024 * 1024;

final class LocalArchiveRef {
  const LocalArchiveRef({
    required this.locator,
    required this.displayName,
    this.entry,
    this.format = 'cbz',
  });

  final String locator;
  final String displayName;
  final String? entry;
  final String format;

  bool get isCbz => format == 'cbz';
  bool get isEpub => format == 'epub';

  String encode() {
    if (!isCbz && !isEpub) throw ArgumentError.value(format, 'format');
    final prefix = 'hikari-$format:';
    final payload = jsonEncode({
      'locator': locator,
      'displayName': displayName,
      if (entry != null) 'entry': entry,
    });
    return '$prefix${base64Url.encode(utf8.encode(payload))}';
  }

  static LocalArchiveRef? tryDecode(String value) {
    final separator = value.indexOf(':');
    if (separator < 0 || !value.startsWith('hikari-')) return null;
    final format = value.substring(7, separator);
    if (format != 'cbz' && format != 'epub') return null;
    try {
      final decoded = jsonDecode(
        utf8.decode(base64Url.decode(value.substring(separator + 1))),
      ) as Map<String, dynamic>;
      final locator = decoded['locator'] as String?;
      final displayName = decoded['displayName'] as String?;
      final entry = decoded['entry'] as String?;
      if (locator == null ||
          displayName == null ||
          locator.isEmpty ||
          displayName.isEmpty) {
        return null;
      }
      return LocalArchiveRef(
        locator: locator,
        displayName: displayName,
        entry: entry,
        format: format,
      );
    } catch (_) {
      return null;
    }
  }
}

final class BoundedArchive {
  BoundedArchive._(this._input, this._entries);

  final InputFileStream _input;
  final List<ArchiveFile> _entries;
  int _actualTotal = 0;
  bool _closed = false;

  static BoundedArchive open(String path) {
    final inspection = InputFileStream(path);
    late final int headerCount;
    try {
      final directory = ZipDirectory()..read(inspection);
      if (directory.filePosition < 0) {
        throw const FormatException('Invalid ZIP archive.');
      }
      _validateHeaders(directory.fileHeaders);
      headerCount = directory.fileHeaders.length;
    } finally {
      inspection.closeSync();
    }

    final input = InputFileStream(path);
    final entries = <ArchiveFile>[];
    ZipDecoder().decodeStream(input, callback: entries.add);
    if (entries.length != headerCount) {
      input.closeSync();
      throw const FormatException('Truncated ZIP central directory.');
    }
    return BoundedArchive._(input, entries);
  }

  List<String> get names => List.unmodifiable(
    _entries
        .where((entry) => entry.isFile && !entry.isSymbolicLink)
        .map((entry) => entry.name),
  );

  Uint8List readEntry(String name, {int maxBytes = maxArchiveEntryBytes}) {
    if (_closed) throw StateError('Archive is closed.');
    if (maxBytes < 1 || maxBytes > maxArchiveEntryBytes) {
      throw ArgumentError.value(maxBytes, 'maxBytes');
    }
    final entry = _entries
        .where((candidate) => candidate.name == name)
        .singleOrNull;
    if (entry == null || !entry.isFile || entry.isSymbolicLink) {
      throw const FormatException('Archive entry is not readable.');
    }
    if (entry.size > maxBytes ||
        _actualTotal > maxArchiveTotalBytes - entry.size) {
      throw const FormatException('Archive output exceeds safety limit.');
    }

    final output = _BoundedOutput(maxBytes);
    entry.writeContent(output);
    if (output.length != entry.size) {
      throw const FormatException('Truncated ZIP entry.');
    }
    final expected = entry.crc32;
    if (expected != null && getCrc32(output.bytes) != expected) {
      throw const FormatException('Invalid ZIP entry CRC.');
    }
    _actualTotal += output.length;
    return output.bytes;
  }

  void close() {
    if (_closed) return;
    _closed = true;
    for (final entry in _entries) {
      entry.closeSync();
    }
    _input.closeSync();
  }

  static void _validateHeaders(List<ZipFileHeader> headers) {
    if (headers.length > maxArchiveEntries) {
      throw const FormatException('ZIP contains too many entries.');
    }
    final names = <String>{};
    var total = 0;
    var compressed = 0;
    for (final header in headers) {
      final normalized = _safeName(header.filename);
      if (!names.add(normalized)) {
        throw const FormatException('ZIP contains duplicate entries.');
      }
      final mode = header.externalFileAttributes >> 16;
      if (header.versionMadeBy >> 8 == 3 && mode & 0xf000 == 0xa000) {
        throw const FormatException('ZIP contains symbolic links.');
      }
      if (header.uncompressedSize > maxArchiveEntryBytes ||
          header.compressedSize > maxArchiveCompressedBytes - compressed) {
        throw const FormatException('ZIP entry exceeds safety limit.');
      }
      total += header.uncompressedSize;
      compressed += header.compressedSize;
      if (total > maxArchiveTotalBytes) {
        throw const FormatException('ZIP output exceeds safety limit.');
      }
    }
  }

  static String _safeName(String raw) {
    final name = raw.replaceAll('\\', '/');
    if (name.isEmpty ||
        name.startsWith('/') ||
        name.contains('\u0000') ||
        RegExp(r'^[A-Za-z]:').hasMatch(name)) {
      throw const FormatException('ZIP entry path is unsafe.');
    }
    final parts = name.split('/');
    if (parts.last.isEmpty) parts.removeLast();
    if (parts.isEmpty ||
        parts.any((part) => part.isEmpty || part == '.' || part == '..')) {
      throw const FormatException('ZIP entry path is unsafe.');
    }
    return parts.join('/');
  }
}

final class _BoundedOutput extends OutputStream {
  _BoundedOutput(this.limit) : super(byteOrder: ByteOrder.littleEndian);

  final int limit;
  final _data = <int>[];

  Uint8List get bytes => Uint8List.fromList(_data);
  @override
  int get length => _data.length;
  @override
  void clear() => _data.clear();
  @override
  void flush() {}
  @override
  void writeByte(int value) {
    if (_data.length == limit) {
      throw const FormatException('ZIP entry output exceeds limit.');
    }
    _data.add(value);
  }

  @override
  void writeBytes(List<int> bytes, {int? length}) {
    final count = length ?? bytes.length;
    if (count < 0 || count > bytes.length || _data.length > limit - count) {
      throw const FormatException('ZIP entry output exceeds limit.');
    }
    _data.addAll(bytes.take(count));
  }

  @override
  void writeStream(InputStream stream) {
    while (!stream.isEOS) {
      final count = stream.length < 64 * 1024 ? stream.length : 64 * 1024;
      writeBytes(stream.readBytes(count).toUint8List());
    }
  }

  @override
  Uint8List subset(int start, [int? end]) {
    final from = start < 0 ? _data.length + start : start;
    final to = end == null
        ? _data.length
        : (end < 0 ? _data.length + end : end);
    return Uint8List.fromList(_data.sublist(from, to));
  }
}
