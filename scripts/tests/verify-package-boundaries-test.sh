#!/usr/bin/env bash
set -euo pipefail

run_case() {
  local path="$1" import_line="$2" expected="$3"
  local root actual
  root="$(mktemp -d)"
  mkdir -p "$root/$(dirname "$path")"
  printf 'package fixture\n%s\n' "$import_line" > "$root/$path"
  if REPO_ROOT="$root" bash scripts/verify-package-boundaries.sh >/dev/null 2>&1; then
    actual=0
  else
    actual=1
  fi
  rm -rf "$root"
  [[ "$actual" == "$expected" ]] || {
    echo "case failed: $path $import_line" >&2
    exit 1
  }
}

run_case 'core/common/src/main/kotlin/F.kt' 'import app.openstory.catalog.model.Story' 1
run_case 'core/common/src/main/kotlin/F.kt' 'import app.openstory.common.id.StoryId' 0
run_case 'core/designsystem/src/main/kotlin/F.kt' 'import app.openstory.catalog.model.Story' 1
run_case 'core/designsystem/src/main/kotlin/F.kt' 'import app.openstory.designsystem.state.HikariEmptyState' 0
run_case 'plugins/api/src/main/kotlin/F.kt' 'import android.content.Context' 1
run_case 'plugins/api/src/main/kotlin/F.kt' 'import app.openstory.common.Outcome' 1
run_case 'plugins/api/src/main/kotlin/F.kt' 'import app.openstory.plugins.api.protocol.PluginOperation' 0
run_case 'plugins/runtime/src/main/kotlin/F.kt' 'import app.openstory.catalog.model.Story' 1
run_case 'plugins/runtime/src/main/kotlin/F.kt' 'import app.openstory.plugins.api.manifest.PluginManifest' 0
run_case 'catalog/src/main/kotlin/F.kt' 'import android.content.Context' 1
run_case 'catalog/src/main/kotlin/F.kt' 'import androidx.compose.runtime.Composable' 1
run_case 'catalog/src/main/kotlin/F.kt' 'import app.openstory.common.dispatchers.AppDispatchers' 1
run_case 'catalog/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.PluginRuntime' 0
run_case 'library/src/main/kotlin/F.kt' 'import androidx.room.Entity' 1
run_case 'library/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.PluginRuntime' 0
run_case 'library/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.execution.PluginOperationRunner' 1
run_case 'library/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.persistence.PluginStateStore' 1
run_case 'library/src/main/kotlin/F.kt' 'import app.openstory.plugins.api.protocol.PluginOperation' 0
run_case 'library/src/main/kotlin/F.kt' 'import app.openstory.common.id.StoryId' 0
run_case 'library/src/main/kotlin/F.kt' 'import app.openstory.catalog.model.ContentType' 0
run_case 'library/src/main/kotlin/F.kt' 'import app.openstory.catalog.projection.CatalogStoryProjection' 0
run_case 'library/src/main/kotlin/F.kt' 'import app.openstory.catalog.repository.CatalogRepository' 1
run_case 'library/src/main/kotlin/F.kt' 'import app.openstory.catalog.matching.StoryMatcher' 1
run_case 'chapters/src/main/kotlin/F.kt' 'import androidx.room.Entity' 1
run_case 'chapters/src/main/kotlin/F.kt' 'import app.openstory.library.mapping.ContentMapping' 0
run_case 'chapters/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.PluginRuntime' 0
run_case 'chapters/src/main/kotlin/F.kt' 'import app.openstory.storage.room.OpenStoryDatabase' 1
run_case 'reader/src/main/kotlin/F.kt' 'import app.openstory.chapters.model.ChapterRelease' 0
run_case 'reader/src/main/kotlin/F.kt' 'import androidx.compose.runtime.Composable' 1
run_case 'reader/src/main/kotlin/F.kt' 'import app.openstory.storage.room.OpenStoryDatabase' 1
run_case 'feature/reader/src/main/kotlin/F.kt' 'import app.openstory.reader.document.ReaderDocument' 0
run_case 'feature/reader/src/main/kotlin/F.kt' 'import app.openstory.designsystem.state.HikariErrorState' 0
run_case 'feature/reader/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.PluginRuntime' 1
run_case 'feature/catalog/src/main/kotlin/F.kt' 'import app.openstory.storage.room.OpenStoryDatabase' 1
run_case 'feature/catalog/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.PluginRuntime' 1
run_case 'feature/catalog/src/main/kotlin/F.kt' 'import app.openstory.catalog.model.Story' 0
run_case 'feature/catalog/src/main/kotlin/F.kt' 'import app.openstory.library.LibraryStatus' 0
run_case 'feature/catalog/src/main/kotlin/F.kt' 'import app.openstory.chapters.repository.ChapterRepository' 0
run_case 'feature/catalog/src/main/kotlin/F.kt' 'import app.openstory.designsystem.state.HikariEmptyState' 0
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.execution.PluginOperationRunner' 1
run_case 'storage/room/src/main/kotlin/F.kt' 'val runner = app.openstory.plugins.runtime.execution.PluginOperationRunner::class' 1
run_case 'storage/room/src/main/kotlin/F.kt' $'val runner = app.openstory.plugins\n  .runtime.execution.PluginOperationRunner::class' 1
run_case 'storage/room/src/main/kotlin/F.kt' 'val fake = app.openstory.plugins.runtime.persistenceEvil.PluginStateStore::class' 1
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.persistence.PluginStateStore' 0
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.plugins.api.manifest.PluginService' 0
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.catalog.repository.CatalogRepository' 0
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.library.LibraryRepository' 0
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.chapters.repository.ChapterRepository' 0
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.reader.progress.ReadingProgressRepository' 0
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.downloads.cache.CacheRepository' 0
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.downloads.blob.ChapterBlobKey' 0

echo 'verify-package-boundaries.sh contract verified.'
