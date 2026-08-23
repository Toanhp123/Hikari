#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
home="$root/catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshService.kt"
details="$root/catalog/src/main/kotlin/app/openstory/catalog/details/CatalogDetailsLoader.kt"
search="$root/catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt"
story="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt"
review="$root/catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationReviewService.kt"
room_catalog="$root/storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt"
room_canonical="$root/storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepository.kt"
merge_applier="$root/storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeApplier.kt"
orchestrator="$root/catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestrator.kt"
main_activity="$root/app/src/main/kotlin/app/openstory/MainActivity.kt"
work_contract="$root/catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineWork.kt"

fail() { echo "Canonical engine orchestration policy violation: $1" >&2; exit 1; }

for file in "$home" "$details" "$search"; do
  grep -q 'CanonicalEngineEventSink' "$file" || fail "$(basename "$file") does not depend on the canonical event sink"
  grep -Eq 'orchestrator\.onEvidenceChange(d|s)' "$file" || fail "$(basename "$file") does not route committed facts"
  ! grep -q 'CatalogReconciliationService' "$file" || fail "$(basename "$file") still owns reconciliation routing"
  ! grep -q 'CanonicalGenerationRebuilder' "$file" || fail "$(basename "$file") still owns Fusion routing"
  ! grep -q 'reconciliation\.reconcile' "$file" || fail "$(basename "$file") calls reconciliation directly"
  ! grep -q 'fusion\.rebuild' "$file" || fail "$(basename "$file") calls Fusion directly"
done

grep -q 'CatalogEvidenceLevel.SUMMARY' "$home" || fail "Home evidence is not classified as Summary"
grep -q 'CatalogEvidenceLevel.FULL' "$details" || fail "Details evidence is not classified as Full"
grep -q 'CatalogEvidenceLevel.SUMMARY' "$search" || fail "Search evidence is not classified as Summary"

grep -q 'orchestrator\.onSourcePreferenceChanged' "$story" || fail "Story preference does not route through orchestration"
! grep -q 'bootstrap\.rebuild' "$story" || fail "Story still performs a direct canonical rebuild"
! grep -q 'CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED' "$story" || fail "Story Full refresh still duplicates evidence Fusion"
grep -q 'orchestrator\.onStoryMerged' "$review" || fail "User-approved merge does not notify canonical orchestration"

! grep -q 'search-summary-changed\|story-created' "$room_catalog" || fail "RoomCatalogRepository still owns runtime engine work"
! grep -q 'source-preference-changed' "$room_canonical" || fail "RoomCanonicalCatalogRepository still owns preference engine work"

grep -q 'POST_MERGE_DERIVED' "$merge_applier" || fail "merge transaction lost conditional post-merge derived work"
grep -q 'CanonicalEngineWorkReasons.postMergeDerived' "$merge_applier" || fail "merge transaction lost the durable derived-work reason"
grep -q 'story-merge-derived-state' "$work_contract" || fail "legacy post-merge durable reason is no longer backward-compatible"
! grep -q 'POST_MERGE_DERIVED' "$orchestrator" || fail "orchestrator must not recreate conditional post-merge derived work"

grep -q 'withFrameNanos' "$main_activity" || fail "canonical maintenance bootstrap is not deferred beyond the startup frame"
grep -q 'canonicalEngineWorkScheduler.scheduleDrain()' "$main_activity" || fail "app shell does not wake durable canonical engine work"
grep -q 'canonicalEngineWorkScheduler.ensureDailySafety()' "$main_activity" || fail "app shell does not register canonical safety maintenance"

echo "Canonical engine orchestration policy verified."
