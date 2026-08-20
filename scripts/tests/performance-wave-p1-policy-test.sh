#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
store="$root/storage/files/src/main/kotlin/app/openstory/storage/files/AtomicFileChapterBlobStore.kt"
inventory="$root/storage/files/src/main/kotlin/app/openstory/storage/files/FileBlobInventory.kt"
locks="$root/storage/files/src/main/kotlin/app/openstory/storage/files/ChapterBlobFileLocks.kt"
blob="$root/downloads/src/main/kotlin/app/openstory/downloads/blob/BlobModels.kt"
reader_store="$root/downloads/src/main/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStore.kt"
cache_service="$root/downloads/src/main/kotlin/app/openstory/downloads/cache/CacheService.kt"
artwork="$root/core/designsystem/src/main/kotlin/app/openstory/designsystem/artwork/HikariArtwork.kt"
plugin_storage="$root/plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/TransactionalPluginPackageStorage.kt"
storage_files_build="$root/storage/files/build.gradle.kts"

fail() { echo "Performance Wave P1 policy violation: $1" >&2; exit 1; }

[[ -f "$locks" ]] || fail "per-file chapter blob lock coordinator is missing"
grep -q 'Mutex' "$locks" || fail "chapter blob lock coordinator is not coroutine-aware"
grep -q 'withLock' "$locks" || fail "chapter blob lock coordinator does not guard keyed operations"

for file in "$store" "$inventory"; do
  grep -q 'withContext(ioDispatcher)' "$file" || fail "blocking filesystem work is not dispatched to injected IO: $file"
  ! grep -q 'ChapterBlobFileLayout.operationLock' "$file" || fail "global chapter blob operation lock remains: $file"
done
grep -q 'implementation(libs.kotlinx.coroutines.core)' "$storage_files_build" || fail "storage/files production coroutines dependency is missing"
grep -q 'withContext(ioDispatcher)' "$plugin_storage" || fail "plugin package filesystem work is not dispatched to injected IO"
grep -q 'Dispatchers.IO' "$plugin_storage" || fail "plugin package storage has no production IO dispatcher default"
! grep -q 'operationLock' "$inventory" || fail "global blob operation lock owner still exists"
grep -q 'ChapterBlobFileLocks.withLock' "$store" || fail "blob store does not coordinate same-file operations"
grep -q 'ChapterBlobFileLocks.withLock' "$inventory" || fail "inventory deletion does not coordinate committed blob deletion"

grep -q 'val sizeBytes' "$blob" || fail "ChapterBlob has no zero-copy size accessor"
grep -q 'fun inputStream()' "$blob" || fail "ChapterBlob has no zero-copy input stream"
grep -q 'offset: Int' "$blob" || fail "ChapterBlob cannot verify an encoded payload slice"
grep -q 'MessageDigest' "$blob" || fail "ChapterBlob checksum owner is missing"
! grep -q 'blob\.bytes()\.size' "$reader_store" || fail "reader cache admission still copies the blob only to read its size"
! grep -q 'blob\.bytes()\.size' "$cache_service" || fail "cache metadata still copies the blob only to read its size"
! grep -q 'ByteArrayInputStream(blob\.bytes())' "$reader_store" || fail "reader decode still copies the full blob before parsing"
! grep -q 'blob\.checksum\.value\.encodeToByteArray() + byteArrayOf' "$store" || fail "atomic store still concatenates header and payload into one full encoded array"
grep -q 'blob.inputStream().use' "$store" || fail "atomic store does not stream blob payload without a full defensive copy"

grep -q 'rememberConstraintsSizeResolver' "$artwork" || fail "artwork request does not use Coil constraints sizing"
grep -q '\.size(sizeResolver)' "$artwork" || fail "artwork ImageRequest is not sized by the Compose constraints resolver"
grep -q 'then(state\.sizeResolver)' "$artwork" || fail "artwork Image does not feed layout constraints into the request size resolver"

echo "Performance Wave P1 policy verified."
