#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
service="$root/catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationService.kt"
lineage="$root/catalog/src/main/kotlin/app/openstory/catalog/reconciliation/StoryMergeLineage.kt"
room_lineage="$root/storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeLineageReader.kt"
merge_applier="$root/storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeApplier.kt"

fail() { echo "Retroactive reconciliation policy violation: $1" >&2; exit 1; }

[[ -f "$lineage" ]] || fail "merge-lineage domain boundary is missing"
[[ -f "$room_lineage" ]] || fail "Room merge-lineage adapter is missing"
grep -q 'StoryMergeLineageReader' "$service" || fail "reconciliation does not consume merge lineage"
grep -q 'persistPostMergeCorrection' "$service" || fail "post-merge correction path is missing"
grep -q 'semanticDecision = ReconciliationSemanticDecision.REVIEW' "$service" || fail "post-merge contradiction is not forced to review"
grep -q 'mergeEligibility = ReconciliationMergeEligibility.INVARIANT_BLOCKED' "$service" || fail "post-merge contradiction is not invariant-blocked"
grep -q 'STORY_MERGE_REVERSAL_PAYLOAD_VERSION' "$room_lineage" || fail "Room lineage is not version-gated"
grep -q 'survivorBefore' "$room_lineage" || fail "Room lineage does not recover survivor source provenance"
grep -q 'retiredBefore' "$room_lineage" || fail "Room lineage does not recover retired source provenance"
grep -q 'POST_MERGE_DERIVED' "$merge_applier" || fail "authoritative merge transaction lost post-merge derived work"

shortlist_line="$(grep -n 'val candidateStoryIds = shortlist(incoming)' "$service" | head -1 | cut -d: -f1)"
correction_line="$(grep -n 'persistPostMergeCorrection(canonicalStoryId, incoming)' "$service" | head -1 | cut -d: -f1)"
[[ -n "$shortlist_line" && -n "$correction_line" && "$shortlist_line" -lt "$correction_line" ]] || \
  fail "candidate index must refresh before a correction early-return"

! grep -q 'deleteStory\|reverseMerge\|detachSource' "$service" || \
  fail "reconciliation correction must not auto-split/detach/reverse ownership"

echo "Retroactive reconciliation policy verified."
