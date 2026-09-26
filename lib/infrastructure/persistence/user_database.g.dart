// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'user_database.dart';

// ignore_for_file: type=lint
class $ProgressRecordsTable extends ProgressRecords
    with TableInfo<$ProgressRecordsTable, ProgressRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $ProgressRecordsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _sourceIdMeta = const VerificationMeta(
    'sourceId',
  );
  @override
  late final GeneratedColumn<String> sourceId = GeneratedColumn<String>(
    'source_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _itemIdMeta = const VerificationMeta('itemId');
  @override
  late final GeneratedColumn<String> itemId = GeneratedColumn<String>(
    'item_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _positionMsMeta = const VerificationMeta(
    'positionMs',
  );
  @override
  late final GeneratedColumn<int> positionMs = GeneratedColumn<int>(
    'position_ms',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _durationMsMeta = const VerificationMeta(
    'durationMs',
  );
  @override
  late final GeneratedColumn<int> durationMs = GeneratedColumn<int>(
    'duration_ms',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _pageIndexMeta = const VerificationMeta(
    'pageIndex',
  );
  @override
  late final GeneratedColumn<int> pageIndex = GeneratedColumn<int>(
    'page_index',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _pageCountMeta = const VerificationMeta(
    'pageCount',
  );
  @override
  late final GeneratedColumn<int> pageCount = GeneratedColumn<int>(
    'page_count',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _textProgressionMeta = const VerificationMeta(
    'textProgression',
  );
  @override
  late final GeneratedColumn<double> textProgression = GeneratedColumn<double>(
    'text_progression',
    aliasedName,
    true,
    type: DriftSqlType.double,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _completedMeta = const VerificationMeta(
    'completed',
  );
  @override
  late final GeneratedColumn<int> completed = GeneratedColumn<int>(
    'completed',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<int> updatedAt = GeneratedColumn<int>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    sourceId,
    itemId,
    kind,
    positionMs,
    durationMs,
    pageIndex,
    pageCount,
    textProgression,
    completed,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'progress_records';
  @override
  VerificationContext validateIntegrity(
    Insertable<ProgressRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('source_id')) {
      context.handle(
        _sourceIdMeta,
        sourceId.isAcceptableOrUnknown(data['source_id']!, _sourceIdMeta),
      );
    } else if (isInserting) {
      context.missing(_sourceIdMeta);
    }
    if (data.containsKey('item_id')) {
      context.handle(
        _itemIdMeta,
        itemId.isAcceptableOrUnknown(data['item_id']!, _itemIdMeta),
      );
    } else if (isInserting) {
      context.missing(_itemIdMeta);
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('position_ms')) {
      context.handle(
        _positionMsMeta,
        positionMs.isAcceptableOrUnknown(data['position_ms']!, _positionMsMeta),
      );
    }
    if (data.containsKey('duration_ms')) {
      context.handle(
        _durationMsMeta,
        durationMs.isAcceptableOrUnknown(data['duration_ms']!, _durationMsMeta),
      );
    }
    if (data.containsKey('page_index')) {
      context.handle(
        _pageIndexMeta,
        pageIndex.isAcceptableOrUnknown(data['page_index']!, _pageIndexMeta),
      );
    }
    if (data.containsKey('page_count')) {
      context.handle(
        _pageCountMeta,
        pageCount.isAcceptableOrUnknown(data['page_count']!, _pageCountMeta),
      );
    }
    if (data.containsKey('text_progression')) {
      context.handle(
        _textProgressionMeta,
        textProgression.isAcceptableOrUnknown(
          data['text_progression']!,
          _textProgressionMeta,
        ),
      );
    }
    if (data.containsKey('completed')) {
      context.handle(
        _completedMeta,
        completed.isAcceptableOrUnknown(data['completed']!, _completedMeta),
      );
    } else if (isInserting) {
      context.missing(_completedMeta);
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {sourceId, itemId};
  @override
  ProgressRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ProgressRecord(
      sourceId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_id'],
      )!,
      itemId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}item_id'],
      )!,
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      positionMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}position_ms'],
      ),
      durationMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}duration_ms'],
      ),
      pageIndex: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}page_index'],
      ),
      pageCount: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}page_count'],
      ),
      textProgression: attachedDatabase.typeMapping.read(
        DriftSqlType.double,
        data['${effectivePrefix}text_progression'],
      ),
      completed: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}completed'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $ProgressRecordsTable createAlias(String alias) {
    return $ProgressRecordsTable(attachedDatabase, alias);
  }
}

class ProgressRecord extends DataClass implements Insertable<ProgressRecord> {
  final String sourceId;
  final String itemId;
  final String kind;
  final int? positionMs;
  final int? durationMs;
  final int? pageIndex;
  final int? pageCount;
  final double? textProgression;
  final int completed;
  final int updatedAt;
  const ProgressRecord({
    required this.sourceId,
    required this.itemId,
    required this.kind,
    this.positionMs,
    this.durationMs,
    this.pageIndex,
    this.pageCount,
    this.textProgression,
    required this.completed,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['source_id'] = Variable<String>(sourceId);
    map['item_id'] = Variable<String>(itemId);
    map['kind'] = Variable<String>(kind);
    if (!nullToAbsent || positionMs != null) {
      map['position_ms'] = Variable<int>(positionMs);
    }
    if (!nullToAbsent || durationMs != null) {
      map['duration_ms'] = Variable<int>(durationMs);
    }
    if (!nullToAbsent || pageIndex != null) {
      map['page_index'] = Variable<int>(pageIndex);
    }
    if (!nullToAbsent || pageCount != null) {
      map['page_count'] = Variable<int>(pageCount);
    }
    if (!nullToAbsent || textProgression != null) {
      map['text_progression'] = Variable<double>(textProgression);
    }
    map['completed'] = Variable<int>(completed);
    map['updated_at'] = Variable<int>(updatedAt);
    return map;
  }

  ProgressRecordsCompanion toCompanion(bool nullToAbsent) {
    return ProgressRecordsCompanion(
      sourceId: Value(sourceId),
      itemId: Value(itemId),
      kind: Value(kind),
      positionMs: positionMs == null && nullToAbsent
          ? const Value.absent()
          : Value(positionMs),
      durationMs: durationMs == null && nullToAbsent
          ? const Value.absent()
          : Value(durationMs),
      pageIndex: pageIndex == null && nullToAbsent
          ? const Value.absent()
          : Value(pageIndex),
      pageCount: pageCount == null && nullToAbsent
          ? const Value.absent()
          : Value(pageCount),
      textProgression: textProgression == null && nullToAbsent
          ? const Value.absent()
          : Value(textProgression),
      completed: Value(completed),
      updatedAt: Value(updatedAt),
    );
  }

  factory ProgressRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ProgressRecord(
      sourceId: serializer.fromJson<String>(json['sourceId']),
      itemId: serializer.fromJson<String>(json['itemId']),
      kind: serializer.fromJson<String>(json['kind']),
      positionMs: serializer.fromJson<int?>(json['positionMs']),
      durationMs: serializer.fromJson<int?>(json['durationMs']),
      pageIndex: serializer.fromJson<int?>(json['pageIndex']),
      pageCount: serializer.fromJson<int?>(json['pageCount']),
      textProgression: serializer.fromJson<double?>(json['textProgression']),
      completed: serializer.fromJson<int>(json['completed']),
      updatedAt: serializer.fromJson<int>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'sourceId': serializer.toJson<String>(sourceId),
      'itemId': serializer.toJson<String>(itemId),
      'kind': serializer.toJson<String>(kind),
      'positionMs': serializer.toJson<int?>(positionMs),
      'durationMs': serializer.toJson<int?>(durationMs),
      'pageIndex': serializer.toJson<int?>(pageIndex),
      'pageCount': serializer.toJson<int?>(pageCount),
      'textProgression': serializer.toJson<double?>(textProgression),
      'completed': serializer.toJson<int>(completed),
      'updatedAt': serializer.toJson<int>(updatedAt),
    };
  }

  ProgressRecord copyWith({
    String? sourceId,
    String? itemId,
    String? kind,
    Value<int?> positionMs = const Value.absent(),
    Value<int?> durationMs = const Value.absent(),
    Value<int?> pageIndex = const Value.absent(),
    Value<int?> pageCount = const Value.absent(),
    Value<double?> textProgression = const Value.absent(),
    int? completed,
    int? updatedAt,
  }) => ProgressRecord(
    sourceId: sourceId ?? this.sourceId,
    itemId: itemId ?? this.itemId,
    kind: kind ?? this.kind,
    positionMs: positionMs.present ? positionMs.value : this.positionMs,
    durationMs: durationMs.present ? durationMs.value : this.durationMs,
    pageIndex: pageIndex.present ? pageIndex.value : this.pageIndex,
    pageCount: pageCount.present ? pageCount.value : this.pageCount,
    textProgression: textProgression.present
        ? textProgression.value
        : this.textProgression,
    completed: completed ?? this.completed,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  ProgressRecord copyWithCompanion(ProgressRecordsCompanion data) {
    return ProgressRecord(
      sourceId: data.sourceId.present ? data.sourceId.value : this.sourceId,
      itemId: data.itemId.present ? data.itemId.value : this.itemId,
      kind: data.kind.present ? data.kind.value : this.kind,
      positionMs: data.positionMs.present
          ? data.positionMs.value
          : this.positionMs,
      durationMs: data.durationMs.present
          ? data.durationMs.value
          : this.durationMs,
      pageIndex: data.pageIndex.present ? data.pageIndex.value : this.pageIndex,
      pageCount: data.pageCount.present ? data.pageCount.value : this.pageCount,
      textProgression: data.textProgression.present
          ? data.textProgression.value
          : this.textProgression,
      completed: data.completed.present ? data.completed.value : this.completed,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('ProgressRecord(')
          ..write('sourceId: $sourceId, ')
          ..write('itemId: $itemId, ')
          ..write('kind: $kind, ')
          ..write('positionMs: $positionMs, ')
          ..write('durationMs: $durationMs, ')
          ..write('pageIndex: $pageIndex, ')
          ..write('pageCount: $pageCount, ')
          ..write('textProgression: $textProgression, ')
          ..write('completed: $completed, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    sourceId,
    itemId,
    kind,
    positionMs,
    durationMs,
    pageIndex,
    pageCount,
    textProgression,
    completed,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is ProgressRecord &&
          other.sourceId == this.sourceId &&
          other.itemId == this.itemId &&
          other.kind == this.kind &&
          other.positionMs == this.positionMs &&
          other.durationMs == this.durationMs &&
          other.pageIndex == this.pageIndex &&
          other.pageCount == this.pageCount &&
          other.textProgression == this.textProgression &&
          other.completed == this.completed &&
          other.updatedAt == this.updatedAt);
}

class ProgressRecordsCompanion extends UpdateCompanion<ProgressRecord> {
  final Value<String> sourceId;
  final Value<String> itemId;
  final Value<String> kind;
  final Value<int?> positionMs;
  final Value<int?> durationMs;
  final Value<int?> pageIndex;
  final Value<int?> pageCount;
  final Value<double?> textProgression;
  final Value<int> completed;
  final Value<int> updatedAt;
  final Value<int> rowid;
  const ProgressRecordsCompanion({
    this.sourceId = const Value.absent(),
    this.itemId = const Value.absent(),
    this.kind = const Value.absent(),
    this.positionMs = const Value.absent(),
    this.durationMs = const Value.absent(),
    this.pageIndex = const Value.absent(),
    this.pageCount = const Value.absent(),
    this.textProgression = const Value.absent(),
    this.completed = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  ProgressRecordsCompanion.insert({
    required String sourceId,
    required String itemId,
    required String kind,
    this.positionMs = const Value.absent(),
    this.durationMs = const Value.absent(),
    this.pageIndex = const Value.absent(),
    this.pageCount = const Value.absent(),
    this.textProgression = const Value.absent(),
    required int completed,
    required int updatedAt,
    this.rowid = const Value.absent(),
  }) : sourceId = Value(sourceId),
       itemId = Value(itemId),
       kind = Value(kind),
       completed = Value(completed),
       updatedAt = Value(updatedAt);
  static Insertable<ProgressRecord> custom({
    Expression<String>? sourceId,
    Expression<String>? itemId,
    Expression<String>? kind,
    Expression<int>? positionMs,
    Expression<int>? durationMs,
    Expression<int>? pageIndex,
    Expression<int>? pageCount,
    Expression<double>? textProgression,
    Expression<int>? completed,
    Expression<int>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (sourceId != null) 'source_id': sourceId,
      if (itemId != null) 'item_id': itemId,
      if (kind != null) 'kind': kind,
      if (positionMs != null) 'position_ms': positionMs,
      if (durationMs != null) 'duration_ms': durationMs,
      if (pageIndex != null) 'page_index': pageIndex,
      if (pageCount != null) 'page_count': pageCount,
      if (textProgression != null) 'text_progression': textProgression,
      if (completed != null) 'completed': completed,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  ProgressRecordsCompanion copyWith({
    Value<String>? sourceId,
    Value<String>? itemId,
    Value<String>? kind,
    Value<int?>? positionMs,
    Value<int?>? durationMs,
    Value<int?>? pageIndex,
    Value<int?>? pageCount,
    Value<double?>? textProgression,
    Value<int>? completed,
    Value<int>? updatedAt,
    Value<int>? rowid,
  }) {
    return ProgressRecordsCompanion(
      sourceId: sourceId ?? this.sourceId,
      itemId: itemId ?? this.itemId,
      kind: kind ?? this.kind,
      positionMs: positionMs ?? this.positionMs,
      durationMs: durationMs ?? this.durationMs,
      pageIndex: pageIndex ?? this.pageIndex,
      pageCount: pageCount ?? this.pageCount,
      textProgression: textProgression ?? this.textProgression,
      completed: completed ?? this.completed,
      updatedAt: updatedAt ?? this.updatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (sourceId.present) {
      map['source_id'] = Variable<String>(sourceId.value);
    }
    if (itemId.present) {
      map['item_id'] = Variable<String>(itemId.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (positionMs.present) {
      map['position_ms'] = Variable<int>(positionMs.value);
    }
    if (durationMs.present) {
      map['duration_ms'] = Variable<int>(durationMs.value);
    }
    if (pageIndex.present) {
      map['page_index'] = Variable<int>(pageIndex.value);
    }
    if (pageCount.present) {
      map['page_count'] = Variable<int>(pageCount.value);
    }
    if (textProgression.present) {
      map['text_progression'] = Variable<double>(textProgression.value);
    }
    if (completed.present) {
      map['completed'] = Variable<int>(completed.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<int>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('ProgressRecordsCompanion(')
          ..write('sourceId: $sourceId, ')
          ..write('itemId: $itemId, ')
          ..write('kind: $kind, ')
          ..write('positionMs: $positionMs, ')
          ..write('durationMs: $durationMs, ')
          ..write('pageIndex: $pageIndex, ')
          ..write('pageCount: $pageCount, ')
          ..write('textProgression: $textProgression, ')
          ..write('completed: $completed, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $LibraryRecordsTable extends LibraryRecords
    with TableInfo<$LibraryRecordsTable, LibraryRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LibraryRecordsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _sourceIdMeta = const VerificationMeta(
    'sourceId',
  );
  @override
  late final GeneratedColumn<String> sourceId = GeneratedColumn<String>(
    'source_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _itemIdMeta = const VerificationMeta('itemId');
  @override
  late final GeneratedColumn<String> itemId = GeneratedColumn<String>(
    'item_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _mediaTypeMeta = const VerificationMeta(
    'mediaType',
  );
  @override
  late final GeneratedColumn<String> mediaType = GeneratedColumn<String>(
    'media_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _addedAtMeta = const VerificationMeta(
    'addedAt',
  );
  @override
  late final GeneratedColumn<int> addedAt = GeneratedColumn<int>(
    'added_at',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    sourceId,
    itemId,
    title,
    mediaType,
    addedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'library_records';
  @override
  VerificationContext validateIntegrity(
    Insertable<LibraryRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('source_id')) {
      context.handle(
        _sourceIdMeta,
        sourceId.isAcceptableOrUnknown(data['source_id']!, _sourceIdMeta),
      );
    } else if (isInserting) {
      context.missing(_sourceIdMeta);
    }
    if (data.containsKey('item_id')) {
      context.handle(
        _itemIdMeta,
        itemId.isAcceptableOrUnknown(data['item_id']!, _itemIdMeta),
      );
    } else if (isInserting) {
      context.missing(_itemIdMeta);
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('media_type')) {
      context.handle(
        _mediaTypeMeta,
        mediaType.isAcceptableOrUnknown(data['media_type']!, _mediaTypeMeta),
      );
    } else if (isInserting) {
      context.missing(_mediaTypeMeta);
    }
    if (data.containsKey('added_at')) {
      context.handle(
        _addedAtMeta,
        addedAt.isAcceptableOrUnknown(data['added_at']!, _addedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_addedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {sourceId, itemId};
  @override
  LibraryRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LibraryRecord(
      sourceId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source_id'],
      )!,
      itemId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}item_id'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      mediaType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}media_type'],
      )!,
      addedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}added_at'],
      )!,
    );
  }

  @override
  $LibraryRecordsTable createAlias(String alias) {
    return $LibraryRecordsTable(attachedDatabase, alias);
  }
}

class LibraryRecord extends DataClass implements Insertable<LibraryRecord> {
  final String sourceId;
  final String itemId;
  final String title;
  final String mediaType;
  final int addedAt;
  const LibraryRecord({
    required this.sourceId,
    required this.itemId,
    required this.title,
    required this.mediaType,
    required this.addedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['source_id'] = Variable<String>(sourceId);
    map['item_id'] = Variable<String>(itemId);
    map['title'] = Variable<String>(title);
    map['media_type'] = Variable<String>(mediaType);
    map['added_at'] = Variable<int>(addedAt);
    return map;
  }

  LibraryRecordsCompanion toCompanion(bool nullToAbsent) {
    return LibraryRecordsCompanion(
      sourceId: Value(sourceId),
      itemId: Value(itemId),
      title: Value(title),
      mediaType: Value(mediaType),
      addedAt: Value(addedAt),
    );
  }

  factory LibraryRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LibraryRecord(
      sourceId: serializer.fromJson<String>(json['sourceId']),
      itemId: serializer.fromJson<String>(json['itemId']),
      title: serializer.fromJson<String>(json['title']),
      mediaType: serializer.fromJson<String>(json['mediaType']),
      addedAt: serializer.fromJson<int>(json['addedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'sourceId': serializer.toJson<String>(sourceId),
      'itemId': serializer.toJson<String>(itemId),
      'title': serializer.toJson<String>(title),
      'mediaType': serializer.toJson<String>(mediaType),
      'addedAt': serializer.toJson<int>(addedAt),
    };
  }

  LibraryRecord copyWith({
    String? sourceId,
    String? itemId,
    String? title,
    String? mediaType,
    int? addedAt,
  }) => LibraryRecord(
    sourceId: sourceId ?? this.sourceId,
    itemId: itemId ?? this.itemId,
    title: title ?? this.title,
    mediaType: mediaType ?? this.mediaType,
    addedAt: addedAt ?? this.addedAt,
  );
  LibraryRecord copyWithCompanion(LibraryRecordsCompanion data) {
    return LibraryRecord(
      sourceId: data.sourceId.present ? data.sourceId.value : this.sourceId,
      itemId: data.itemId.present ? data.itemId.value : this.itemId,
      title: data.title.present ? data.title.value : this.title,
      mediaType: data.mediaType.present ? data.mediaType.value : this.mediaType,
      addedAt: data.addedAt.present ? data.addedAt.value : this.addedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LibraryRecord(')
          ..write('sourceId: $sourceId, ')
          ..write('itemId: $itemId, ')
          ..write('title: $title, ')
          ..write('mediaType: $mediaType, ')
          ..write('addedAt: $addedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(sourceId, itemId, title, mediaType, addedAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LibraryRecord &&
          other.sourceId == this.sourceId &&
          other.itemId == this.itemId &&
          other.title == this.title &&
          other.mediaType == this.mediaType &&
          other.addedAt == this.addedAt);
}

class LibraryRecordsCompanion extends UpdateCompanion<LibraryRecord> {
  final Value<String> sourceId;
  final Value<String> itemId;
  final Value<String> title;
  final Value<String> mediaType;
  final Value<int> addedAt;
  final Value<int> rowid;
  const LibraryRecordsCompanion({
    this.sourceId = const Value.absent(),
    this.itemId = const Value.absent(),
    this.title = const Value.absent(),
    this.mediaType = const Value.absent(),
    this.addedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  LibraryRecordsCompanion.insert({
    required String sourceId,
    required String itemId,
    required String title,
    required String mediaType,
    required int addedAt,
    this.rowid = const Value.absent(),
  }) : sourceId = Value(sourceId),
       itemId = Value(itemId),
       title = Value(title),
       mediaType = Value(mediaType),
       addedAt = Value(addedAt);
  static Insertable<LibraryRecord> custom({
    Expression<String>? sourceId,
    Expression<String>? itemId,
    Expression<String>? title,
    Expression<String>? mediaType,
    Expression<int>? addedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (sourceId != null) 'source_id': sourceId,
      if (itemId != null) 'item_id': itemId,
      if (title != null) 'title': title,
      if (mediaType != null) 'media_type': mediaType,
      if (addedAt != null) 'added_at': addedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  LibraryRecordsCompanion copyWith({
    Value<String>? sourceId,
    Value<String>? itemId,
    Value<String>? title,
    Value<String>? mediaType,
    Value<int>? addedAt,
    Value<int>? rowid,
  }) {
    return LibraryRecordsCompanion(
      sourceId: sourceId ?? this.sourceId,
      itemId: itemId ?? this.itemId,
      title: title ?? this.title,
      mediaType: mediaType ?? this.mediaType,
      addedAt: addedAt ?? this.addedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (sourceId.present) {
      map['source_id'] = Variable<String>(sourceId.value);
    }
    if (itemId.present) {
      map['item_id'] = Variable<String>(itemId.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (mediaType.present) {
      map['media_type'] = Variable<String>(mediaType.value);
    }
    if (addedAt.present) {
      map['added_at'] = Variable<int>(addedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LibraryRecordsCompanion(')
          ..write('sourceId: $sourceId, ')
          ..write('itemId: $itemId, ')
          ..write('title: $title, ')
          ..write('mediaType: $mediaType, ')
          ..write('addedAt: $addedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

abstract class _$UserDatabase extends GeneratedDatabase {
  _$UserDatabase(QueryExecutor e) : super(e);
  $UserDatabaseManager get managers => $UserDatabaseManager(this);
  late final $ProgressRecordsTable progressRecords = $ProgressRecordsTable(
    this,
  );
  late final $LibraryRecordsTable libraryRecords = $LibraryRecordsTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    progressRecords,
    libraryRecords,
  ];
}

typedef $$ProgressRecordsTableCreateCompanionBuilder =
    ProgressRecordsCompanion Function({
      required String sourceId,
      required String itemId,
      required String kind,
      Value<int?> positionMs,
      Value<int?> durationMs,
      Value<int?> pageIndex,
      Value<int?> pageCount,
      Value<double?> textProgression,
      required int completed,
      required int updatedAt,
      Value<int> rowid,
    });
typedef $$ProgressRecordsTableUpdateCompanionBuilder =
    ProgressRecordsCompanion Function({
      Value<String> sourceId,
      Value<String> itemId,
      Value<String> kind,
      Value<int?> positionMs,
      Value<int?> durationMs,
      Value<int?> pageIndex,
      Value<int?> pageCount,
      Value<double?> textProgression,
      Value<int> completed,
      Value<int> updatedAt,
      Value<int> rowid,
    });

class $$ProgressRecordsTableFilterComposer
    extends Composer<_$UserDatabase, $ProgressRecordsTable> {
  $$ProgressRecordsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get sourceId => $composableBuilder(
    column: $table.sourceId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get itemId => $composableBuilder(
    column: $table.itemId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get positionMs => $composableBuilder(
    column: $table.positionMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get pageIndex => $composableBuilder(
    column: $table.pageIndex,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get pageCount => $composableBuilder(
    column: $table.pageCount,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<double> get textProgression => $composableBuilder(
    column: $table.textProgression,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get completed => $composableBuilder(
    column: $table.completed,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$ProgressRecordsTableOrderingComposer
    extends Composer<_$UserDatabase, $ProgressRecordsTable> {
  $$ProgressRecordsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get sourceId => $composableBuilder(
    column: $table.sourceId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get itemId => $composableBuilder(
    column: $table.itemId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get positionMs => $composableBuilder(
    column: $table.positionMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get pageIndex => $composableBuilder(
    column: $table.pageIndex,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get pageCount => $composableBuilder(
    column: $table.pageCount,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<double> get textProgression => $composableBuilder(
    column: $table.textProgression,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get completed => $composableBuilder(
    column: $table.completed,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$ProgressRecordsTableAnnotationComposer
    extends Composer<_$UserDatabase, $ProgressRecordsTable> {
  $$ProgressRecordsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get sourceId =>
      $composableBuilder(column: $table.sourceId, builder: (column) => column);

  GeneratedColumn<String> get itemId =>
      $composableBuilder(column: $table.itemId, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<int> get positionMs => $composableBuilder(
    column: $table.positionMs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get durationMs => $composableBuilder(
    column: $table.durationMs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get pageIndex =>
      $composableBuilder(column: $table.pageIndex, builder: (column) => column);

  GeneratedColumn<int> get pageCount =>
      $composableBuilder(column: $table.pageCount, builder: (column) => column);

  GeneratedColumn<double> get textProgression => $composableBuilder(
    column: $table.textProgression,
    builder: (column) => column,
  );

  GeneratedColumn<int> get completed =>
      $composableBuilder(column: $table.completed, builder: (column) => column);

  GeneratedColumn<int> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);
}

class $$ProgressRecordsTableTableManager
    extends
        RootTableManager<
          _$UserDatabase,
          $ProgressRecordsTable,
          ProgressRecord,
          $$ProgressRecordsTableFilterComposer,
          $$ProgressRecordsTableOrderingComposer,
          $$ProgressRecordsTableAnnotationComposer,
          $$ProgressRecordsTableCreateCompanionBuilder,
          $$ProgressRecordsTableUpdateCompanionBuilder,
          (
            ProgressRecord,
            BaseReferences<
              _$UserDatabase,
              $ProgressRecordsTable,
              ProgressRecord
            >,
          ),
          ProgressRecord,
          PrefetchHooks Function()
        > {
  $$ProgressRecordsTableTableManager(
    _$UserDatabase db,
    $ProgressRecordsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$ProgressRecordsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$ProgressRecordsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$ProgressRecordsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> sourceId = const Value.absent(),
                Value<String> itemId = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<int?> positionMs = const Value.absent(),
                Value<int?> durationMs = const Value.absent(),
                Value<int?> pageIndex = const Value.absent(),
                Value<int?> pageCount = const Value.absent(),
                Value<double?> textProgression = const Value.absent(),
                Value<int> completed = const Value.absent(),
                Value<int> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => ProgressRecordsCompanion(
                sourceId: sourceId,
                itemId: itemId,
                kind: kind,
                positionMs: positionMs,
                durationMs: durationMs,
                pageIndex: pageIndex,
                pageCount: pageCount,
                textProgression: textProgression,
                completed: completed,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String sourceId,
                required String itemId,
                required String kind,
                Value<int?> positionMs = const Value.absent(),
                Value<int?> durationMs = const Value.absent(),
                Value<int?> pageIndex = const Value.absent(),
                Value<int?> pageCount = const Value.absent(),
                Value<double?> textProgression = const Value.absent(),
                required int completed,
                required int updatedAt,
                Value<int> rowid = const Value.absent(),
              }) => ProgressRecordsCompanion.insert(
                sourceId: sourceId,
                itemId: itemId,
                kind: kind,
                positionMs: positionMs,
                durationMs: durationMs,
                pageIndex: pageIndex,
                pageCount: pageCount,
                textProgression: textProgression,
                completed: completed,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$ProgressRecordsTable, ProgressRecord>(table),
                  BaseReferences<
                    _$UserDatabase,
                    $ProgressRecordsTable,
                    ProgressRecord
                  >(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$ProgressRecordsTableProcessedTableManager =
    ProcessedTableManager<
      _$UserDatabase,
      $ProgressRecordsTable,
      ProgressRecord,
      $$ProgressRecordsTableFilterComposer,
      $$ProgressRecordsTableOrderingComposer,
      $$ProgressRecordsTableAnnotationComposer,
      $$ProgressRecordsTableCreateCompanionBuilder,
      $$ProgressRecordsTableUpdateCompanionBuilder,
      (
        ProgressRecord,
        BaseReferences<_$UserDatabase, $ProgressRecordsTable, ProgressRecord>,
      ),
      ProgressRecord,
      PrefetchHooks Function()
    >;
typedef $$LibraryRecordsTableCreateCompanionBuilder =
    LibraryRecordsCompanion Function({
      required String sourceId,
      required String itemId,
      required String title,
      required String mediaType,
      required int addedAt,
      Value<int> rowid,
    });
typedef $$LibraryRecordsTableUpdateCompanionBuilder =
    LibraryRecordsCompanion Function({
      Value<String> sourceId,
      Value<String> itemId,
      Value<String> title,
      Value<String> mediaType,
      Value<int> addedAt,
      Value<int> rowid,
    });

class $$LibraryRecordsTableFilterComposer
    extends Composer<_$UserDatabase, $LibraryRecordsTable> {
  $$LibraryRecordsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get sourceId => $composableBuilder(
    column: $table.sourceId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get itemId => $composableBuilder(
    column: $table.itemId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get mediaType => $composableBuilder(
    column: $table.mediaType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get addedAt => $composableBuilder(
    column: $table.addedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$LibraryRecordsTableOrderingComposer
    extends Composer<_$UserDatabase, $LibraryRecordsTable> {
  $$LibraryRecordsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get sourceId => $composableBuilder(
    column: $table.sourceId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get itemId => $composableBuilder(
    column: $table.itemId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get mediaType => $composableBuilder(
    column: $table.mediaType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get addedAt => $composableBuilder(
    column: $table.addedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$LibraryRecordsTableAnnotationComposer
    extends Composer<_$UserDatabase, $LibraryRecordsTable> {
  $$LibraryRecordsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get sourceId =>
      $composableBuilder(column: $table.sourceId, builder: (column) => column);

  GeneratedColumn<String> get itemId =>
      $composableBuilder(column: $table.itemId, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get mediaType =>
      $composableBuilder(column: $table.mediaType, builder: (column) => column);

  GeneratedColumn<int> get addedAt =>
      $composableBuilder(column: $table.addedAt, builder: (column) => column);
}

class $$LibraryRecordsTableTableManager
    extends
        RootTableManager<
          _$UserDatabase,
          $LibraryRecordsTable,
          LibraryRecord,
          $$LibraryRecordsTableFilterComposer,
          $$LibraryRecordsTableOrderingComposer,
          $$LibraryRecordsTableAnnotationComposer,
          $$LibraryRecordsTableCreateCompanionBuilder,
          $$LibraryRecordsTableUpdateCompanionBuilder,
          (
            LibraryRecord,
            BaseReferences<_$UserDatabase, $LibraryRecordsTable, LibraryRecord>,
          ),
          LibraryRecord,
          PrefetchHooks Function()
        > {
  $$LibraryRecordsTableTableManager(
    _$UserDatabase db,
    $LibraryRecordsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$LibraryRecordsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$LibraryRecordsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$LibraryRecordsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> sourceId = const Value.absent(),
                Value<String> itemId = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String> mediaType = const Value.absent(),
                Value<int> addedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => LibraryRecordsCompanion(
                sourceId: sourceId,
                itemId: itemId,
                title: title,
                mediaType: mediaType,
                addedAt: addedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String sourceId,
                required String itemId,
                required String title,
                required String mediaType,
                required int addedAt,
                Value<int> rowid = const Value.absent(),
              }) => LibraryRecordsCompanion.insert(
                sourceId: sourceId,
                itemId: itemId,
                title: title,
                mediaType: mediaType,
                addedAt: addedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable<$LibraryRecordsTable, LibraryRecord>(table),
                  BaseReferences<
                    _$UserDatabase,
                    $LibraryRecordsTable,
                    LibraryRecord
                  >(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$LibraryRecordsTableProcessedTableManager =
    ProcessedTableManager<
      _$UserDatabase,
      $LibraryRecordsTable,
      LibraryRecord,
      $$LibraryRecordsTableFilterComposer,
      $$LibraryRecordsTableOrderingComposer,
      $$LibraryRecordsTableAnnotationComposer,
      $$LibraryRecordsTableCreateCompanionBuilder,
      $$LibraryRecordsTableUpdateCompanionBuilder,
      (
        LibraryRecord,
        BaseReferences<_$UserDatabase, $LibraryRecordsTable, LibraryRecord>,
      ),
      LibraryRecord,
      PrefetchHooks Function()
    >;

class $UserDatabaseManager {
  final _$UserDatabase _db;
  $UserDatabaseManager(this._db);
  $$ProgressRecordsTableTableManager get progressRecords =>
      $$ProgressRecordsTableTableManager(_db, _db.progressRecords);
  $$LibraryRecordsTableTableManager get libraryRecords =>
      $$LibraryRecordsTableTableManager(_db, _db.libraryRecords);
}
