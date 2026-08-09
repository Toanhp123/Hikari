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

run_case 'feature/catalog/src/main/kotlin/F.kt' 'import app.openstory.storage.room.OpenStoryDatabase' 1
run_case 'feature/catalog/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.PluginRuntime' 1
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.execution.PluginOperationRunner' 1
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.capabilities.http.HttpCapability' 1
run_case 'plugins/api/src/main/kotlin/F.kt' 'import android.content.Context' 1
run_case 'catalog/src/main/kotlin/F.kt' 'import androidx.compose.runtime.Composable' 1
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.persistence.PluginStateStore' 0
run_case 'catalog/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.PluginRuntime' 0
run_case 'feature/catalog/src/main/kotlin/F.kt' 'import app.openstory.catalog.model.Story' 0
