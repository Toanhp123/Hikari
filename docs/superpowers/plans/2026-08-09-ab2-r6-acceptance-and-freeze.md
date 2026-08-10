# Architecture Baseline 2 R6 - Acceptance and Freeze Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the rebuilt architecture on JVM, Room, plugin runtime, Compose, app launch, API 26 and API 37; record evidence; then freeze Baseline 2 and reopen Wave 06 Task 01.

**Architecture:** R6 changes no architecture unless a failing acceptance gate exposes a defect. It creates one comprehensive checkpoint runner/evidence package, repeats a manual ownership audit against final source, and updates canonical state only after every required gate actually passes.

**Tech Stack:** Gradle verification, Android instrumentation/API 26+37, Room, Compose, plugin runtime/security tests, Detekt, lint, APK assembly, shell checkpoint evidence.

## Global Constraints

- Architecture source of truth: `docs/superpowers/specs/2026-08-09-architecture-baseline-2-design.md`.
- This work is pre-Wave-06; do not implement Library, chapter sync, Reader, downloads, background sync, authentication, notifications, or release-hardening behavior.
- Android-native Kotlin remains fixed.
- Package namespace/application ID remains `app.openstory`.
- Minimum SDK remains 26; compile/target SDK remain 37 unless a dedicated architecture decision changes them.
- Build runtime remains JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0, Kotlin 2.4.10.
- Current retained libraries may be replaced only when a plan task explicitly does so; do not change versions opportunistically during this reset.
- Pre-MVP compatibility is intentionally breakable. Do not add permanent `Legacy*`, `Compat*`, `V1/V2` adapters, dual mappers, or Room migrations merely to preserve development-only contracts.
- Temporary migration-scoped bridges are allowed only when this plan names the bridge and its deletion task explicitly.
- Package-first, Gradle-module-second: do not create extra production modules beyond the approved target graph without a new architecture decision.
- TDD is mandatory for behavior changes: focused RED -> smallest GREEN -> affected module suite -> commit.
- Every task ends in a buildable, testable, independently reviewable repository state.
- Do not make a checkpoint green with `TODO()`, `error("not implemented")`, unconditional empty production results, or broad structural suppressions.
- Tests protect revalidated product/security invariants, not historical class shapes.
- Production Room entities/DAOs stay private to the storage adapter.
- Production plugin JavaScript receives only host-controlled capabilities and never Android `Context`, Room, filesystem paths, raw OkHttp, reflection, or plaintext managed credentials.


---
### Task 1: Create one comprehensive Architecture Baseline 2 checkpoint runner

**Files:**
- Create: `scripts/checkpoints/architecture-baseline-2.sh`
- Create: `scripts/tests/checkpoint-architecture-baseline-2-test.sh`
- Create: `scripts/instrumentation/architecture-baseline-2.sh`
- Create: `scripts/tests/instrumentation-architecture-baseline-2-test.sh`

**Interfaces:**
- `architecture-baseline-2.sh` requires `ANDROID_SERIAL_API_26` and `ANDROID_SERIAL_API_37`.
- It runs fast verification once, then target instrumentation on both API levels.

- [ ] **Step 1: Write RED shell contract tests**

Create `scripts/tests/checkpoint-architecture-baseline-2-test.sh` as a static contract test over the checkpoint script:

```bash
#!/usr/bin/env bash
set -euo pipefail
FILE=scripts/checkpoints/architecture-baseline-2.sh
[[ -f "$FILE" ]]
grep -q './scripts/verify.sh' "$FILE"
grep -q 'ANDROID_SERIAL_API_26' "$FILE"
grep -q 'ANDROID_SERIAL_API_37' "$FILE"
grep -q 'scripts/instrumentation/architecture-baseline-2.sh' "$FILE"
RUNNER=scripts/instrumentation/architecture-baseline-2.sh
for task in \
  ':plugins:runtime:connectedDebugAndroidTest' \
  ':storage:room:connectedDebugAndroidTest' \
  ':feature:catalog:connectedDebugAndroidTest' \
  ':app:connectedDebugAndroidTest'; do
  grep -q -- "$task" "$RUNNER"
done
grep -q 'app.openstory' "$RUNNER"
grep -q 'Status: ok' "$RUNNER"
```

Create `scripts/tests/instrumentation-architecture-baseline-2-test.sh` with fake `adb`/Gradle wrappers to prove a missing serial fails and that the supplied `ANDROID_SERIAL` is forwarded rather than auto-selecting an attached device.

- [ ] **Step 2: Verify RED**

```bash
bash scripts/tests/checkpoint-architecture-baseline-2-test.sh
bash scripts/tests/instrumentation-architecture-baseline-2-test.sh
```

Expected: **FAIL** because the R6 runners do not exist yet.

- [ ] **Step 3: Implement instrumentation runner**

For one resolved `ANDROID_SERIAL` + API, run:

```bash
./gradlew :plugins:runtime:connectedDebugAndroidTest   :storage:room:connectedDebugAndroidTest   :feature:catalog:connectedDebugAndroidTest   :app:connectedDebugAndroidTest --stacktrace
```

Then install/launch `app.openstory` and require `Status: ok`, matching the existing app-shell smoke behavior.

- [ ] **Step 4: Implement checkpoint wrapper**

Run API 26 then API 37 with explicit serials. Do not silently pick a device when two are attached.

- [ ] **Step 5: Commit**

```bash
git add scripts/checkpoints/architecture-baseline-2.sh   scripts/instrumentation/architecture-baseline-2.sh   scripts/tests/checkpoint-architecture-baseline-2-test.sh   scripts/tests/instrumentation-architecture-baseline-2-test.sh
git commit -m "verification: add baseline two checkpoint runner"
```

### Task 2: Run deterministic JVM/local verification and freeze the Room fingerprint

**Files:**
- No production files expected.
- Create/update only evidence after commands complete.

- [ ] **Step 1: Capture Room schema fingerprint before fast suite**

```bash
ROOM_SCHEMA_FINGERPRINT="$(./scripts/verify-room-schema-stability.sh)"
printf '%s
' "$ROOM_SCHEMA_FINGERPRINT"
```

- [ ] **Step 2: Run all target local suites explicitly**

```bash
./gradlew --no-daemon --dependency-verification strict   :build-logic:test   :core:common:test   :plugins:api:test   :plugins:runtime:testDebugUnitTest   :catalog:test   :storage:room:testDebugUnitTest   :feature:catalog:testDebugUnitTest   :app:testDebugUnitTest   detekt   lintDebug   :app:assembleDebug   --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 3: Run repository fast gate**

```bash
./scripts/verify.sh
```

Expected: **PASS**.

- [ ] **Step 4: Recheck Room fingerprint**

```bash
./scripts/verify-room-schema-stability.sh "$ROOM_SCHEMA_FINGERPRINT"
```

Expected: schema remained stable.

- [ ] **Step 5: Stop if any local gate fails**

Do not record R6 acceptance or update current state until the defect is fixed in the owning earlier subsystem and all local commands are rerun.

### Task 3: Run full Android checkpoint on API 26 and API 37

**Files:**
- No source changes expected unless a real defect is found.

- [ ] **Step 1: Verify connected emulator/device API levels**

```bash
adb -s "$ANDROID_SERIAL_API_26" shell getprop ro.build.version.sdk
adb -s "$ANDROID_SERIAL_API_37" shell getprop ro.build.version.sdk
```

Expected exact outputs `26` and `37`.

- [ ] **Step 2: Run comprehensive checkpoint**

```bash
ANDROID_SERIAL_API_26="$ANDROID_SERIAL_API_26" ANDROID_SERIAL_API_37="$ANDROID_SERIAL_API_37"   ./scripts/checkpoints/architecture-baseline-2.sh
```

Expected: **PASS**.

- [ ] **Step 3: Run MAL deterministic reference integration explicitly**

On one target device:

```bash
ANDROID_SERIAL="$ANDROID_SERIAL_API_37" ./gradlew :app:connectedDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.MyAnimeListCatalogContractIntegrationTest   --stacktrace
```

Expected: **PASS** using deterministic fixture/gateway behavior. The live MAL test is not required for checkpoint acceptance.

- [ ] **Step 4: Preserve logs on failure**

If any Android command fails, record exact serial/API, Gradle task, failing class, and relevant bounded log excerpt. Do not replace failed evidence with a later assumption.

- [ ] **Step 5: Proceed only when API 26 + 37 are both green**

### Task 4: Perform final manual architecture audit against the source tree

**Files:**
- Create: `docs/internal/architecture-baseline-2/r6-final-audit.md`

**Interfaces:**
- Produces explicit source-reviewed answers to Baseline 2 Definition of Done.

- [ ] **Step 1: Run final source assertions**

```bash
test ! -d core/model
test ! -d core/database
test ! -d core/matching
test ! -d core/plugin-api
test ! -d core/plugin-host
test ! -d core/network
test ! -d feature/home
test ! -d feature/story

! grep -R -n 'OpenStoryAppGraph\|LambdaViewModelFactory' app catalog feature storage plugins
! grep -R -n 'selector.json\|PluginRuntime.DECLARATIVE' bundled-plugins plugins catalog feature app
```

Expected: exit 0.

- [ ] **Step 2: Audit target module public surfaces**

For each module, list public Kotlin declarations and justify them. Package-private/internal declarations are preferred unless cross-module access is required.

- [ ] **Step 3: Answer the seven ownership questions**

Record one concrete answer for:
1. Who owns this model?
2. Who may call this class?
3. Who stores this data?
4. Who executes plugins?
5. Who decides canonical matching?
6. Who owns the transaction?
7. Who converts application state into UI state?

The expected owners are respectively capability/common, declared consumers, storage adapter, plugin runtime, catalog, Room adapter, feature catalog.

- [ ] **Step 4: Review structural report**

Attach or summarize every remaining >300-line production file and constructor with unusually high dependency count. Any unresolved mixed responsibility blocks acceptance.

- [ ] **Step 5: Save final audit**

No `TBD`, `TODO`, or “shared by convenience” wording.

### Task 5: Record R6 acceptance and reopen Wave 06

**Files:**
- Create: `docs/internal/checkpoints/architecture-baseline-2.md`
- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
- Modify: `docs/project/document-governance.md`

**Interfaces:**
- Produces canonical post-reset execution state.

- [ ] **Step 1: Write checkpoint evidence from actual runs**

Include:
- environment/JDK/Gradle;
- exact local commands;
- exact API 26/API 37 serials;
- Room fingerprint;
- architecture/detekt/lint/assembly results;
- plugin runtime/security results;
- Compose/app results;
- pointer to `r6-final-audit.md`.

Unrun gates remain `NOT RUN`; do not infer them from another command.

- [ ] **Step 2: Update current state only after evidence is complete**

Set:

```text
Architecture Baseline 2: ACCEPTED
Current production modules: 7
Next: Wave 06 Task 01 - metadata-only Library persistence and story matching foundations
```

Document the new independent baselines:
- Room schema 1 = Baseline 2 schema;
- plugin protocol major 1 = Baseline 2 JS-only protocol;
- `.osp` = JS-only package layout with detached artifact checksum/signature.

- [ ] **Step 3: Restore roadmap sequence**

Wave 01-05 remains historical implementation history; Baseline 2 sits between Wave 05 and Wave 06; Wave 06 becomes `Ready to start`.

- [ ] **Step 4: Run docs/state tests one final time**

```bash
for test_script in scripts/tests/*.sh; do bash "$test_script"; done
./scripts/verify.sh
```

Expected: **PASS** with the accepted state.

- [ ] **Step 5: Commit acceptance**

```bash
git add docs/internal/checkpoints/architecture-baseline-2.md   docs/internal/architecture-baseline-2/r6-final-audit.md   docs/project/current-state.md docs/implementation/current-roadmap.md   docs/project/document-governance.md
git commit -m "architecture: accept baseline two"
```
