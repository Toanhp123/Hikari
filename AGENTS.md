# Universal Media — Agent Contract

This file is the always-loaded routing layer. Keep it small. Product, architecture, project state, and gate truth live in one canonical document:

`docs/foundation/android-universal-media-app-foundation.md`

## Start here

1. Read only the foundation's **Current Control Block** first.
2. Inspect the code/tests/docs that own the requested scope.
3. Read only the relevant foundation decision record(s); search by ID/heading instead of reading the whole foundation.
4. Read a task plan only when the user or Current Control Block names it.

Useful lookup:

```sh
rg -n 'Current Control Block|Q-(DOM|SRC|STO|ID|REC|SCN|PROG|HIST|LIB|META|RUN|BACK|PER|MOD|API|BOOT)-|Development Order|Quality Gate' docs/foundation/android-universal-media-app-foundation.md
```

Do not ask the user to repeat decisions already present there.

## Authority and scope

- Current user instruction controls the current task.
- The canonical foundation controls product/domain/persistence/module/API/toolchain decisions and current project/gate state.
- Code/tests/configuration control implementation facts when they do not contradict a canonical decision.
- Historical plans and evidence files are supporting context, not current-state authority.
- If executable evidence contradicts a provisional foundation decision, stop breadth expansion and reopen that decision explicitly.

## Read by concern

- Canonical shape: `Q-DOM-*`.
- Source/storage/identity/reconciliation/scan: `Q-SRC-*`, `Q-STO-*`, `Q-ID-*`, `Q-REC-*`, `Q-SCN-*`.
- Progress/history/library/metadata: `Q-PROG-*`, `Q-HIST-*`, `Q-LIB-*`, `Q-META-*`.
- Android execution/backup: `Q-RUN-*`, `Q-BACK-*`.
- Persistence/module/API/toolchain: `Q-PER-*`, `Q-MOD-*`, `Q-API-*`, `Q-BOOT-*`.

Do not load unrelated decision records "just in case".

## Change discipline

- Prefer the smallest coherent change; no speculative abstraction or unrelated cleanup.
- Preserve app-owned identity and the established `StorageRoot` / `Source` / `SourceBinding` / `Asset` / `ResolvedContent` boundaries.
- UI must not depend directly on storage/database/provider implementations; Player/Reader consume resolved runtime content, not persistence/provider DTOs.
- Partial/failed/cancelled observation is never deletion evidence.
- `:app` owns composition; feature modules do not depend on one another unless the canonical module decision is explicitly revised.
- Do not add a DI framework, broad storage permission, network permission, new module/public API/table family, or cross-module dependency without the owning decision/use case.
- Preserve unrelated user changes. Do not use destructive Git operations unless explicitly requested.

## Verification

Use repository-owned gates. `PASS` means actually executed; `PENDING` means not run; `BLOCKED` means an environmental prerequisite prevented execution; `FAIL` means execution exposed a defect. Static inspection never becomes executable PASS.

```powershell
.\scripts\verify-bootstrap.ps1 -DoctorOnly
.\scripts\verify-bootstrap.ps1 -HostOnly
.\scripts\verify-bootstrap.ps1
```

```sh
bash scripts/verify-bootstrap.sh --doctor-only
bash scripts/verify-bootstrap.sh --host-only
bash scripts/verify-bootstrap.sh
```

Run focused tests while developing, then the owning canonical gate when the environment permits it. Verification must be proportional to the change: do not rerun a broad gate merely because it exists in a plan when the current diff cannot affect it. Prefer one batched Gradle invocation for the affected modules, reuse configuration/build cache, and use instrumentation class filters when only a small Android behavior changed.

For long-running commands, keep agent context lean: successful runs should be summarized by command + exit status/key counts, not pasted in full. Capture stdout/stderr to an OS-temp or repository-ignored local log when useful; on failure, surface only the first actionable error plus a small relevant tail, then rerun the failing task with `--stacktrace`/`--info` only if needed.

Long-running execution is **blocking, not observed**:

- launch one focused invocation and wait for its exit using the tool/runtime's native blocking wait with a finite wall-clock bound appropriate to that gate; a command may legitimately take minutes, so elapsed time alone is not failure;
- never `tail -f`, `Get-Content -Wait`, repeatedly reopen the log, poll a process/job/device in a loop, or spend model/tool turns merely checking whether the command is still running;
- if a tool returns a live-process/session handle instead of blocking, use one native bounded wait when available; do not create a `sleep`/poll/`feed` loop just to watch progress;
- if the invocation exceeds its chosen execution bound or is clearly non-terminating, stop that invocation, preserve the log, and report the execution as timed out/blocked rather than continuing to observe it indefinitely; do not silently convert such a stop into a test `FAIL`;
- after exit, read only the bounded summary or failure slice needed for the next decision. Do not paste the whole log back into agent context.

Do not rerun doctor/bootstrap/full verification unless the environment, build logic, or owning surface changed. Full-suite/release/device matrices belong at explicit checkpoint/closure gates, not after every task.

Self-review the final diff for accidental scope expansion, stale docs, dead code, and architecture/security regression.

## Documentation rule

The foundation is a **living canonical file**. In a writable repo, update it in place and fix stale wording in the same change. Do **not** create revision-suffixed foundation copies, `docs/state/`, persistent handoff files, or side-note sources of truth; Git history is the revision history. Create a separate ADR/technical spec only when the foundation explicitly delegates a deep/platform-specific decision.

Keep this `AGENTS.md` as routing + non-negotiable operating discipline. Do not copy current versions, current gate status, long architecture explanations, or handoff history into it. Add nested `AGENTS.md` only when a subtree gains stable local rules; nested files contain deltas only, never repeat this file.
