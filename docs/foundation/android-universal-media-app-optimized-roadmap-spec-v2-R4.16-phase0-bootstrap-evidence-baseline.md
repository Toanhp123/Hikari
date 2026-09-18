# Android Universal Media App

## Project Foundation, Product Scope & Development Roadmap

**Version:** Pre-V1 Foundation R4.16 — Phase-0 Bootstrap Evidence Baseline  
**Status:** Project Baseline / Pre-Implementation  
**Platform:** Android  
**Primary language:** Kotlin  
**UI direction:** Jetpack Compose  
**Last foundation review:** 2026-09-18 — Phase-0 project skeleton generated from Q-BOOT-001; architecture/security/config static verification passed, while Gradle/JDK17/SDK37 build, lint, unit, instrumentation and CI execution remain pending clean-checkout evidence; next work is executable bootstrap verification before first local vertical slice  

---

# Project Agent Operating Rules — Mandatory Continuation Protocol

Phần này là **hướng dẫn bắt buộc cho mọi phiên/agent tiếp tục dự án**. Nó tồn tại để một phiên mới có thể tiếp tục đúng trạng thái mà không phải đoán lại architecture direction hoặc tự mở rộng scope.

## A.1 Start-of-Session Protocol

Trước khi research, sửa spec hoặc viết code, agent phải:

1. Xác định **bản foundation mới nhất** và coi nó là canonical project baseline.
2. Đọc tối thiểu: phần Agent Rules này, `Document Map`, `Architecture Foundation`, `V1 Decision Queue`, `Required Resolution Order`, decision record gần nhất và quality gates liên quan.
3. Xác định question/stage hiện tại từ Decision Queue. Nếu còn question `OPEN`, mặc định tiếp tục **question chưa giải đầu tiên theo dependency order**; nếu Decision Queue đã complete, tiếp tục downstream design/work được foundation mở khóa, trừ khi user chủ động đổi ưu tiên.
4. Không hỏi lại quyết định đã có trong spec chỉ vì phiên mới không có context; phải đọc spec trước.

## A.2 Research Standard Before a Decision

Không được khóa một architecture/domain decision quan trọng chỉ từ intuition hoặc một project tham khảo.

Với mỗi question:

- Nếu có standard/spec chính thức, phải đọc **primary source** trước (Android, W3C, Readium specification, format spec...).
- Với question cross-media/domain, mặc định so sánh **ít nhất 5 mature projects/libraries** thuộc **ít nhất 3 independent architecture families** khi evidence công khai đủ; nếu không đủ phải ghi rõ limitation.
- Với Android runtime/platform question, ưu tiên official Android documentation và bổ sung production/reference implementations khi chúng làm rõ trade-off.
- Phải phân biệt rõ:
  - **Observed fact** — project/spec bên ngoài thực sự làm gì.
  - **Inference** — bài học suy ra cho dự án này.
  - **Project decision** — lựa chọn của dự án sau khi cân nhắc trade-off.
- Không copy architecture của một project chỉ vì nó nổi tiếng. App này là universal media client; một design series-centric, server-centric hoặc reader-centric có thể chỉ là evidence, không phải template.

## A.3 Decision Workflow

Mỗi question phải đi qua:

```text
Question ID
    ↓
Research primary sources + multiple mature projects
    ↓
Representative examples + edge/failure cases
    ↓
Compare alternatives
    ↓
Choose PROVISIONAL / LOCKED / DEFERRED semantics
    ↓
Update this foundation
    ↓
Self-review contradictions + stale wording
    ↓
Update downstream assumptions / Question Ledger
    ↓
Only then move to next question
```

Decision record phải dùng `Decision Closure Template` của tài liệu này.

## A.4 Blocking and Scope Rules

- Không deep-design production Room schema, public API, class graph hoặc module graph nếu một question `OPEN` trực tiếp block nó.
- Không nhảy sang V2/V3 để “giải hộ” một ambiguity của V1.
- Không tạo abstraction chỉ để thống nhất syntax khi chưa có domain meaning/use case.
- Không biến filename, path, URI, provider ID, EPUB spine item hoặc engine object thành canonical identity nếu Decision Queue chưa cho phép.
- Nếu một câu sau mâu thuẫn với decision trước, **reopen question trước**, ghi impact review và sửa foundation; không vá exception ở implementation.
- `LOCKED` decision chỉ được đổi bằng explicit architecture impact review; không silently rewrite.

## A.5 Spec Update Rule

Foundation này là **living canonical document** trong giai đoạn Pre-V1. Sau mỗi question:

1. Cập nhật status trong Question Ledger.
2. Thêm/sửa Decision Record với research references.
3. Sửa mọi wording cũ bị decision mới làm stale.
4. Thêm question mới nếu research phát hiện ambiguity thực sự ảnh hưởng downstream.
5. Cập nhật stage/gate/next question.
6. Self-review toàn vùng ảnh hưởng trước khi giao file.

Không tạo side-note thay cho việc cập nhật foundation. ADR/technical spec riêng chỉ được tạo khi tài liệu này chỉ ra rằng decision đủ sâu hoặc platform-specific để cần tài liệu riêng. Nếu file nguồn read-only trong môi trường làm việc, tạo **một replacement file hoàn chỉnh** kế thừa toàn bộ nội dung và ghi rõ revision mới; không giao patch rời làm canonical source.

## A.6 End-of-Session Handoff

Mỗi phiên kết thúc phải để lại trong spec đủ thông tin để phiên tiếp theo biết:

- question nào vừa được giải;
- status hiện tại;
- evidence/references chính;
- decision + rejected alternatives;
- downstream assumptions mới được mở khóa;
- risks/questions còn mở;
- **next question theo dependency order**, hoặc next downstream work nếu Decision Queue đã complete.

Nếu implementation evidence về sau bác một provisional decision, quay lại Question Ledger trước khi mở rộng breadth.

---

## Document Map

1. **Product Vision** — app là gì và nguyên tắc sản phẩm.
2. **Product Capability Map** — toàn bộ nhóm chức năng app hướng tới.
3. **Architecture Foundation** — vocabulary, identity, source, progress và boundaries nền.
4. **V1 Architecture Audit & Decision Queue** — semantic questions A–F plus downstream persistence/module/API design ledger and decision records.
5. **Three-Version Product Roadmap** — V1 Local-first, V2 Unified Sources, V3 Platform & Ecosystem.
6. **Pre-Development Setup** — những quyết định Android/engineering phải chuẩn bị trước feature coding.
7. **Development Order & Quality Gates** — tách semantic-design order khỏi implementation order, kèm Definition of Done và exit checklist.
8. **Research Basis** — official Android references dùng để định hướng setup.

## Decision Status Legend

- **Locked direction:** nguyên tắc/product boundary đã đủ ổn để subsystem specs phải tuân theo; thay đổi cần architecture review.
- **Provisional design:** hướng mặc định hiện tại nhưng có thể đổi sau technical research/benchmark mà không đổi product direction.
- **Deferred decision:** cố ý chưa khóa; phải được quyết định bằng technical spec/ADR trước khi implementation phụ thuộc sâu vào nó.

Roadmap V1/V2/V3 và các non-negotiable architecture rules là **locked direction**. Các implementation suggestions trong Pre-Development Setup là **provisional design** trừ khi được ghi rõ khác. Các câu hỏi trong **V1 Decision Queue** mặc định là **open** cho đến khi được review và đổi trạng thái. Mục Explicit Deferred Decisions chứa các quyết định cố ý chưa khóa.

---

# 0. Purpose of This Document

Tài liệu này là **project baseline** cho Android Universal Media App.

Nó phải trả lời bốn câu hỏi trước khi implementation bắt đầu:

1. **Ứng dụng sẽ có những chức năng gì?**
2. **Những nguyên tắc kiến trúc nào phải được giữ xuyên suốt dự án?**
3. **V1, V2 và V3 phát triển những gì, theo thứ tự nào và qua gate nào?**
4. **Trước khi tạo project Android và viết feature code cần khóa/setup những gì?**

Tài liệu này **không phải** implementation specification cho từng subsystem. Những chi tiết như Room schema, API contract, class graph, navigation graph hoặc UI spec chỉ được thiết kế sau khi foundation liên quan đã được khóa.

## 0.0 R4 Audit Update

R4 không mở rộng product scope. Nó tích hợp kết quả **V1 architecture audit pass 1** vào foundation bằng cách:

- tách Source/SourceBinding/Asset roles;
- thêm StorageRoot, ScanRun và availability semantics như concepts cần giải;
- tách Identity khỏi Reconciliation responsibility;
- tách Progress khỏi History;
- ghi rõ Library membership chưa được phép suy ra trực tiếp từ filesystem;
- thêm V1 Decision Queue và closure process;
- tách semantic decision order khỏi implementation dependency order;
- thêm **Project Agent Operating Rules** để phiên/agent sau tiếp tục đúng Decision Queue, research standard và self-review protocol;
- hoàn tất Stage A canonical-shape audit qua `Q-DOM-001..003`, đồng thời mở `Q-PROG-002` ban đầu từ publication locator portability; Q-PROG-001 về sau generalize question này thành cross-media resume-anchor portability/revision migration;
- hoàn tất `Q-SRC-001` ở mức PROVISIONAL, tách configured `Source`, durable/reconstructable `SourceBinding`, optional stable representation `Asset` và ephemeral `ResolvedContent`.
- hoàn tất `Q-STO-001` ở mức PROVISIONAL: `StorageRoot` là app-owned registration của user-authorized discovery boundary; SAF tree là primary V1 universal-root mechanism, MediaStore là supplemental indexed-media adapter, và broad `MANAGE_EXTERNAL_STORAGE` không thuộc baseline.
- hoàn tất `Q-ID-001` ở mức PROVISIONAL: internal app IDs vẫn là identity; SAF/MediaStore locators, hashes, filenames, structural context và metadata/external IDs là typed evidence có scope/provenance; matching phải bảo thủ, multi-evidence và không dùng full-file hashing bắt buộc cho mọi asset.
- hoàn tất `Q-REC-001` ở mức PROVISIONAL: access/scope state, Asset presence và lifecycle disposition được tách; authoritative absence trước hết tạo recoverable `MISSING`; rename/move có thể giữ `AssetId`; copy tạo representation riêng; same-lineage material replacement dùng `AssetRevision`; removal/retirement cần explicit lifecycle authority.
- hoàn tất `Q-SCN-001` ở mức PROVISIONAL: `ScanRun` có declared immutable observation scope + outcome/coverage; positive evidence được phép publish từ incomplete run, nhưng absence chỉ được derive sau finalization từ authoritative completed coverage; SAF `EXTRA_LOADING`, access/provider errors, cancellation/process death, stale/superseded scope hoặc non-snapshot incremental scans không có blanket absence authority.
- hoàn tất `Q-PROG-001` ở mức PROVISIONAL: Progress là current durable state thuộc `ConsumptionTargetRef`; resume anchor typed theo Video/ImageSequence/Publication; completion được persist explicit và độc lập với resume; progress value không monotonic; runtime updates được checkpoint/coalesce; parent aggregate progress là derived projection.
- generalize `Q-PROG-002` từ publication-only locator portability thành cross-media resume-anchor portability/migration/fallback qua Asset/Source/format/revision; publication vẫn là hard case đại diện.
- hoàn tất `Q-PROG-002` ở mức PROVISIONAL: canonical completion/state ownership portable theo `ConsumptionTargetRef`, nhưng precise resume anchor là representation-contextual; exact reuse cần compatible context/evidence, migration/fallback phải typed và không được biến normalized percentage thành exact truth.
- hoàn tất `Q-HIST-001` ở mức PROVISIONAL: `HistoryEntry` là append-oriented summary của meaningful target-scoped consumption activity session; không phải `Progress.updatedAt`, không phải mỗi checkpoint, không fabricate từ manual mark read/watched, và không được dùng `COUNT(history rows)` như authoritative reread/rewatch count.
- hoàn tất `Q-LIB-001` ở mức PROVISIONAL: Library membership là durable Media-level user/app state tách khỏi filesystem/source availability; registered local root mặc định auto-admit newly recognized Media khi chưa có explicit user exclusion; explicit Remove from Library tạo durable suppression intent để rescan không tự add lại; unavailable/missing/out-of-scope không xóa membership; `Recently Added` dùng current membership-epoch `libraryAddedAt`, không dùng file time/discovery/canonical creation time.
- hoàn tất `Q-META-001` ở mức PROVISIONAL: metadata enrichment dùng field-level authority/provenance; explicit user override (bao gồm explicit empty) thắng automated refresh/rematch cho tới khi user reset; matched metadata mặc định thắng locally-derived display fallback; declared local metadata (NFO/ComicInfo/EPUB/embedded) là source category riêng với deterministic source policy; provider failure không xóa last-known metadata; metadata không sở hữu canonical identity, Library, Progress/History hay Asset technical truth.
- hoàn tất `Q-RUN-001` ở mức PROVISIONAL: execution ownership được tách khỏi domain truth; bounded work có thể chạy in-process, persistent/deferrable scan orchestration dùng WorkManager, foreground escalation chỉ là explicit runtime choice; WorkRequest/Worker state không phải `ScanRun` truth; persistent execution phải restartable/idempotent, cancellation cooperative nhưng không dựa vào callback để đảm bảo correctness, retry chỉ dành cho retryable runtime failure, và negative reconciliation chỉ được publish trong guarded finalization của authoritative non-superseded `ScanRun`.
- hoàn tất `Q-BACK-001` ở mức PROVISIONAL: V1 product backup là versioned logical app-state archive tách khỏi Room schema; canonical/user state portable nhưng external media bytes, runtime/scheduler authority, SAF grants và device-bound secrets không phải portable truth; restored roots/assets bắt đầu unverified/unconfirmed cho tới reauthorization + fresh observation; restore V1 dùng validated/staged replacement thay vì generic merge; Android Auto Backup/D2D chỉ là supplemental path với explicit classification rules.
- **R4.14 downstream design update:** hoàn tất `Q-PER-001` ở mức PROVISIONAL với one-canonical-Room-DB baseline, persistence-only `ConsumptionTargetRef` bridge, normalized Source/Root/Asset/Progress/History/Library/scan/metadata table families, bounded semantic transactions, non-destructive migration policy và logical-backup mapping; hoàn tất `Q-MOD-001` + `Q-API-001` ở mức PROVISIONAL với initial module graph, constructor-injection/app-composition-root strategy, minimal public APIs, semantic repository/transaction ports và implementation-detail isolation. Historical handoff của R4.14 là `Q-BOOT-001`; R4.15 đã hoàn tất question này.
- **R4.15 bootstrap update:** hoàn tất `Q-BOOT-001` ở mức PROVISIONAL với compatibility-first matrix Gradle 9.4.1 / AGP 9.2.1 / JDK 17 / Kotlin 2.4.20 / Compose Compiler 2.4.20 / Compose BOM 2026.08.00 / compileSdk 37 / targetSdk 37 / minSdk 23; dùng AGP built-in Kotlin, root version catalog, included `build-logic` convention plugins, pinned Wrapper checksum, release-like R8 baseline và wrapper-driven CI. `Q-COMP-001` không còn là blocker mặc định; manual constructor injection/app composition root tiếp tục cho skeleton + first slice.
- **R4.16 Phase-0 implementation evidence update:** generated the reviewed multi-module skeleton, capability convention plugins, minimal Compose launch surface, JVM/instrumentation smoke harnesses, Macrobenchmark boundary, deny-by-default manifest/security docs, static architecture/security gates and GitHub Actions workflow. Self-review fixed two bootstrap-gate defects before handoff: Android built-in Kotlin compiler options now use the official `kotlin.compilerOptions` path, and `verifyArchitecture` now uses a functioning project-dependency regex rather than a double-escaped pattern that could have produced a false PASS. Static scans/parsers/security checks pass in the generation environment; executable Gradle/Android evidence remains explicitly pending because that environment has Java 21 only, no JDK 17, no Android SDK and no system Gradle/wrapper binary.

Các tên/type được audit đề xuất chưa mặc định là final implementation. Question Ledger là nơi xác định cái gì còn OPEN và khi nào được phép downstream dependency.

## 0.1 Role of This Document

Tài liệu này đóng vai trò:

- Product scope baseline.
- Architecture direction baseline.
- Three-version roadmap baseline.
- Pre-development engineering checklist.
- Guardrail để tránh feature development phá vỡ core model.
- Nguồn tham chiếu để tạo technical spec và ADR sau này.

## 0.2 What This Document Does Not Do

Tài liệu này không cố:

- Thiết kế toàn bộ class ngay từ đầu.
- Khóa schema database trước khi domain semantics rõ.
- Khóa module graph chi tiết trước khi dependency boundaries được review.
- Chọn một framework chỉ vì nó phổ biến.
- Tạo abstraction cho use case chưa tồn tại.
- Giải quyết universal identity tuyệt đối giữa mọi provider.
- Thiết kế dynamic plugin runtime trước khi provider contracts được chứng minh ổn định.

## 0.3 Change Policy

Các quyết định nền sau khi implementation bắt đầu phải được thay đổi có chủ ý:

```text
Product requirement change
        ↓
Architecture impact review
        ↓
ADR nếu thay đổi boundary / ownership / identity
        ↓
Spec update
        ↓
Implementation
```

Không thay đổi silently các khái niệm lõi chỉ để giải quyết nhanh một feature cục bộ.

---

# 1. Product Vision

## 1.1 Product Direction

Ứng dụng là một **universal media client trên Android** dành cho nhiều loại nội dung:

### Video

- Anime
- Movie
- TV Series

### Sequential image media

- Manga
- Manhwa
- Manhua
- Comic

### Publication / text media

- Light Novel
- Novel

Ứng dụng phải có khả năng làm việc với:

- Local media.
- Downloaded media.
- Online content providers.
- Metadata providers.
- Tracking providers.
- Future sync providers.

Mục tiêu cốt lõi là tạo ra **một library và consumption experience thống nhất** trong đó local file, downloaded content và online content chỉ là các nguồn khác nhau cung cấp cùng một media/unit.

UI, Library, Player và Reader không được phụ thuộc trực tiếp vào một provider cụ thể.

## 1.2 Product Principles

### Local-first

Local media là first-class citizen, không phải fallback của online media.

Ứng dụng V1 phải hoạt động đầy đủ khi hoàn toàn offline.

### Provider-independent identity

AniList, TMDB, MangaDex, filesystem path hoặc bất kỳ provider ID nào không được trở thành primary identity nội bộ của app.

### Unified consumption

Người dùng có thể chuyển giữa local, downloaded và online source mà progress logic không bị tách thành ba hệ thống khác nhau.

### Explicit mapping

Matching và mapping phải:

- Có thể persist.
- Có thể inspect.
- Có thể sửa.
- Có thể xóa.
- Có thể rematch.
- Không auto-merge mơ hồ một cách không thể đảo ngược.

### Offline-capable architecture

Network không được trở thành requirement để app boot, mở local library hoặc tiếp tục local playback/reading.

Metadata/network enrichment có thể tồn tại trong V1, nhưng phải là capability tùy chọn: mất mạng hoặc provider metadata lỗi không được làm local core unusable.

### Android-native runtime ownership

Architecture phải tôn trọng Android process lifecycle, component lifecycle, background execution constraints, storage model và system resource ownership.

### Capability before ecosystem

Provider contracts phải được chứng minh bằng built-in implementations trước khi biến chúng thành dynamic plugin platform.

---

# 2. Product Capability Map

Phần này mô tả **app cuối cùng có thể làm gì**. Roadmap ở phần sau quyết định capability nào xuất hiện trong V1, V2 hoặc V3.

## 2.1 Media Library

- Register local folders / document trees.
- Scan local content.
- Group files thành media hợp lý.
- Detect add / rename / move / delete khi có thể.
- Maintain canonical library independent from filename/path/provider.
- Media detail.
- Library status.
- Recently added.
- Continue Watching.
- Continue Reading.
- History.
- Search.
- Future custom lists / tags / smart collections.

## 2.2 Video

### Core

- MKV.
- MP4.
- WebM.
- Playback.
- Pause / resume.
- Seek.
- Track selection.
- Resume playback position.

### Future advanced

- Picture in Picture.
- Casting.
- External subtitles.
- Advanced subtitle styling.
- Decoder selection.
- Intro / outro skip.

## 2.3 Manga / Comic

### Core

- CBZ.
- ZIP-based image archive.
- Image folders.
- JPEG.
- PNG.
- WebP.
- Page navigation.
- Reading direction.
- Continuous mode.
- Zoom.
- Resume reading position.

### Future advanced

- Dual page.
- Spread detection.
- Webtoon crop.
- Image filters.
- Panel navigation.

## 2.4 Novel / Publication

### Core

- EPUB 2.
- EPUB 3.
- Publication navigation.
- Locator-based resume.
- Typography settings.
- Theme settings.

### Future advanced

- Highlight.
- Bookmark.
- Notes.
- Dictionary.
- Translation.
- Text-to-speech.
- Reading statistics.

## 2.5 Metadata

- Metadata search.
- Metadata detail.
- Local media matching.
- Manual matching.
- Rematching.
- External IDs.
- User metadata override.
- Future multiple metadata providers.

Metadata enriches canonical media; metadata provider identity does not replace internal identity.

Sau `Q-META-001`, effective descriptive metadata phải giữ field-level authority/provenance: explicit user override thắng automated refresh/rematch; matched metadata mặc định thắng filename/path-derived display fallback; declared local metadata như NFO/ComicInfo/EPUB package metadata là source riêng chứ không bị trộn với parser inference. Provider failure không được blank last-known metadata, và metadata refresh không sở hữu canonical identity/Progress/History/Library/Asset technical facts.

## 2.6 Content Sources

- Local source.
- Downloaded source.
- Online content provider source.
- Source availability per media unit.
- Manual source selection.
- Source preference policy.
- Future automatic source selection.

## 2.7 Online Content

- Built-in video provider.
- Built-in manga provider.
- Optional publication provider.
- Provider search.
- Provider unit listing.
- Stream/page/publication resolution.
- Provider mapping.
- Candidate matching.
- User-confirmed mapping.

## 2.8 Downloads

- Download queue.
- Pause / resume.
- Offline playback.
- Offline reading.
- Storage management.
- Downloaded content treated as a normal source.

## 2.9 Tracking

Future capability:

- Authentication.
- Import library.
- Progress sync.
- Status sync.
- Score sync.
- Mapping.
- Conflict handling.

Tracking service state must not become canonical media identity.

## 2.10 Discovery

Future capability:

- Trending.
- Popular.
- Seasonal.
- Upcoming.
- Top Rated.
- Filters.
- Provider-specific discovery capabilities behind a stable app contract.

## 2.11 Plugin Platform

Future capability:

### Initial plugin types

- Metadata plugin.
- Content plugin.
- Tracking plugin.

### Possible later plugin types

- Subtitle plugin.
- Recommendation plugin.
- Sync plugin.

Plugin runtime phải có:

- Manifest.
- API version.
- Compatibility rules.
- Capability declaration.
- Allowed hosts.
- Permission model.
- Plugin manager.
- Explicit security model.

## 2.12 User Data

- Settings.
- Local backup.
- Local restore.
- Future cloud/self-hosted/peer sync.
- Future multi-device state reconciliation.

## 2.13 Capability-to-Version Matrix

| Capability | V1 | V2 | V3 |
|---|:---:|:---:|:---:|
| Local library | ✓ | ✓ | ✓ |
| Canonical media/unit identity | ✓ | ✓ | ✓ |
| Local video playback | ✓ | ✓ | ✓ |
| Local manga/comic reading | ✓ | ✓ | ✓ |
| Local EPUB reading | ✓ | ✓ | ✓ |
| Progress/history | ✓ | ✓ | ✓ |
| Metadata enrichment/matching | ✓ | ✓ | ✓ |
| Local backup/restore | ✓ | ✓ | ✓ |
| Online content providers | — | ✓ | ✓ |
| Unified source switching | — | ✓ | ✓ |
| Downloads as source | — | ✓ | ✓ |
| Unified search across local/provider | — | ✓ | ✓ |
| Dynamic plugins | — | — | ✓ |
| Tracking services | — | — | ✓ |
| Advanced discovery | — | — | ✓ |
| Multi-device/cloud sync | — | — | ✓ |
| Advanced player/reader features | — | — | ✓ |

---

# 3. Architecture Foundation

## 3.1 Domain Vocabulary

V1 giữ vocabulary nhỏ, nhưng audit pass 1 cho thấy cần phân biệt rõ **canonical domain**, **storage/runtime concepts** và **derived observations** để tránh một object gánh nhiều vai.

### Canonical/user-facing concepts

- `Media` — identity cấp tác phẩm/library item do app kiểm soát.
- `MediaGrouping` — grouping tùy chọn như Season/Volume; **provisional**, chưa mặc định là consumable unit.
- `MediaUnit` — đơn vị có thể consume/progress/source-map độc lập, ví dụ Episode hoặc Chapter.
- `Source` — configured capability/origin instance có thể cung cấp content; không phải file, folder hay canonical media identity.
- `SourceBinding` — durable/reconstructable relation giữa một canonical consumption target và một source-native content identity/reference.
- `Asset` — representation concept chỉ materialize/persist khi một representation có identity/lifecycle đủ ổn định để app cần theo dõi (đặc biệt local/downloaded); không bắt buộc cho mọi source.
- `ResolvedContent` — ephemeral typed runtime result mà Player/Reader thực sự consume; có thể chứa URI/URL, headers, token, expiry, stream/page/publication handles và không phải canonical/persistent truth.
- `Progress` — vị trí/trạng thái consumption hiện tại.
- `HistoryEntry` — record về activity consumption; không đồng nhất với `Progress.updatedAt`.
- `LibraryEntry` — user/library membership state.
- `ExternalId` / `Mapping` — external evidence/linkage; không phải primary identity.
- `Relation` — quan hệ domain chỉ thêm khi có use case rõ.

### Storage/runtime concepts

- `StorageRoot` — app-owned registration/identity cho một user-authorized local discovery boundary. `RootId` thuộc app; SAF tree URI, MediaStore scope/volume hoặc platform locator chỉ là access descriptor/evidence, không phải canonical media/asset identity.
- `AccessGrant` — quyền/capability Android hiện tại để truy cập root/resource; có thể persist qua reboot trong một số SAF cases nhưng vẫn phải revalidate và không phải `StorageRoot` identity hay asset identity.
- `ScanRun` / `ScanGeneration` — app-owned observation attempt có declared scan scope, run outcome, coverage/authority và provenance; run identity không phải content identity.
- Availability/reconciliation state — Q-REC-001 tách thành **access/scope state**, **Asset presence observation** và **Asset lifecycle disposition**; không dùng một enum duy nhất và không đồng nhất với library membership.

Conceptual relationship hiện tại:

```text
StorageRoot / AccessGrant
        ↓ observe
     ScanRun
        ↓
  Reconciliation
        ↓
Media
 ├── MediaGrouping?
 └── MediaUnit?

Media or MediaUnit
      ↓ typed ConsumptionTargetRef
      ├── Progress
      ├── History
      └── SourceBinding
           ├── optional durable Asset representation(s)
           └── resolve now → ResolvedContent
```

`MediaGrouping`, `SourceBinding` và `Asset` vẫn là **provisional concepts**. Availability/reconciliation không còn được giả định là một `AvailabilityState` duy nhất: `Q-REC-001` đã tách access/scope, Asset presence và lifecycle disposition. `Q-SRC-001` đã khóa source/binding/runtime-resolution roles; `Q-STO-001` đã khóa StorageRoot/access/discovery roles. R4.14 `Q-PER-001` now locks provisional persistence table families, ownership, FK/transaction/migration rules and the persistence-only consumption-target bridge; exact Kotlin entity/DAO names, column spellings and performance-driven indexes remain implementation details. `Q-ID-001` + `Q-REC-001` still own identity-evidence, Asset-lineage/revision and rename/move/missing semantics.

Không ép mọi media type vào một hierarchy cứng nếu use case không cần.

## 3.2 Canonical Media Core

Canonical Media là internal representation do app kiểm soát.

Canonical Media không thuộc:

- AniList.
- TMDB.
- MangaDex.
- Local filesystem.
- Dynamic plugin.
- Tracking service.

Canonical Media là điểm hội tụ cho library, mapping, progress và source availability.

## 3.3 Identity Core

Identity Core chịu trách nhiệm:

- Cấp stable internal ID cho Media.
- Cấp stable internal ID cho Media Unit.
- Không dùng filename/path/provider ID làm primary identity.
- Quản lý External ID như evidence/mapping.
- Quy định khi nào tạo Media mới.
- Quy định khi nào tạo Unit mới.
- Quy định khi nào chỉ thêm source availability/asset.
- Hạn chế duplicate.
- Không auto-merge mơ hồ.
- Cho phép mapping/rematch/reconciliation.

Nguyên tắc:

```text
Internal ID = identity app kiểm soát
External ID = evidence / mapping information
Location    = nơi asset hiện tồn tại
Observation = bằng chứng thu được trong một scan/provider query
```

Các khái niệm này không được trộn lẫn. `Identity Policy` quyết định identity ổn định; `Reconciliation` dùng observations/evidence để cập nhật canonical state nhưng không được biến evidence thành identity một cách ngầm định.

## 3.3.1 Reconciliation Core

Audit pass 1 tách `Reconciliation` khỏi `Identity Core` về mặt trách nhiệm. Hai phần liên quan chặt nhưng trả lời hai câu hỏi khác nhau:

- **Identity:** media/unit nào là cùng một identity nội bộ?
- **Reconciliation:** observation mới từ storage/provider nên create, update, rebind, mark unavailable hay remove state nào?

Potential evidence có thể gồm:

- existing source binding.
- document/media URI.
- root relationship.
- filename/path-derived facts.
- file size / metadata.
- parsed episode/chapter number.
- optional hash/fingerprint nếu thực sự cần.
- external metadata evidence.

Rule:

```text
evidence != identity
absence in a partial/failed scan != deletion
```

Exact reconciliation policy vẫn **open** và được theo dõi trong Decision Queue.

## 3.4 Media Unit Core

Sau Q-DOM-002 và Q-DOM-003, `MediaUnit` là **independently consumable canonical identity dành cho Media có subdivision canonical tự nhiên**. Không còn giả định mọi consumption bắt buộc phải materialize một Unit. Atomic Media có thể là consumption target trực tiếp thông qua typed `ConsumptionTargetRef`; EPUB package/internal resources không tự làm phát sinh Unit.

Ví dụ đã tương đối rõ:

```text
Video Series
└── Season? (MediaGrouping)
    └── Episode (MediaUnit)

Manga / Comic Series
└── Volume? (MediaGrouping)
    └── Chapter (MediaUnit)
```

`MediaGrouping` là optional; Unit có thể không thuộc Grouping. Movie/atomic one-shot/standalone publication có thể được consume trực tiếp ở Media-level khi canonical work và consumption identity trùng nhau. Với publication series, independently consumable book/volume có thể là Unit; internal EPUB spine/TOC/resources không tự động là Unit.

Không gắn playback/reading progress vào provider episode/chapter ID trực tiếp.

## 3.5 Source Core

`Q-SRC-001` đã tách bốn vai khác nhau để tránh provider/storage/runtime state trộn vào một object.

### Source

`Source` là **configured capability/origin instance** có thể cung cấp content. Nó đại diện cho nơi/capability logic mà app có thể gọi, không đại diện cho một file cụ thể hay một canonical title.

Examples ở mức semantics:

```text
LocalContentSource
DownloadedContentSource
MangaProvider(language/config/account)
VideoProvider(config/account)
```

Một Source phải có identity/configuration scope đủ ổn định để binding có thể tham chiếu tới nó. Source implementation có thể long-lived, nhưng runtime object/framework instance không tự trở thành persisted domain truth. `StorageRoot` là discovery/access boundary riêng và không đồng nhất với Local Source.

### SourceBinding

`SourceBinding` là **app-owned relation**:

```text
ConsumptionTargetRef
        ↕
Source + source-native content reference
```

Binding trả lời: *"Source này biết/đang có content tương ứng với canonical target nào?"* Nó không phải file/URL và không phải object mà Player/Reader mở trực tiếp.

Properties/invariants ở mức semantics:

- target là `MediaTarget(MediaId)` hoặc `UnitTarget(UnitId)`;
- tham chiếu đúng một configured `Source`;
- giữ source-native key/reference/evidence đủ để re-resolve content; exact local/provider key shape do Storage/Identity audit khóa;
- có thể persist hoặc reconstruct deterministically, nhưng association semantics phải sống lâu hơn một expiring URL/session;
- có thể tồn tại khi source/storage tạm unavailable; availability không đồng nhất với binding existence;
- không chứa access token/expiring signed URL như canonical truth;
- nhiều bindings có thể trỏ tới cùng canonical target; cùng một source cũng có thể có nhiều binding nếu source thực sự có nhiều source-native releases/entries.

### Asset

`Asset` là **representation-level concept**, không phải canonical identity và không phải mandatory node cho mọi source.

Một Asset chỉ đáng materialize/persist khi app cần theo dõi một representation cụ thể có lifecycle/identity tương đối ổn định, ví dụ:

```text
local MKV/MP4/CBZ/EPUB
downloaded package/file
provider-native release/file reference nếu source contract thực sự cần
```

Một canonical target có thể có nhiều representation mà không tạo Media/Unit mới. Một SourceBinding cũng có thể dẫn tới nhiều representation/candidates. Exact durable Asset identity, revision và local locator semantics vẫn do `Q-STO-001`, `Q-ID-001` và `Q-REC-001` khóa.

### ResolvedContent

`ResolvedContent` là **ephemeral typed runtime result** của resolution. Đây mới là boundary mà consumption layer nhận.

```text
ConsumptionTargetRef
        ↓ select binding
SourceBinding
        ↓ resolve now
ResolvedContent
        ↓
Player / Reader
```

Representative shapes:

```text
ResolvedVideo
- playable URI/stream descriptor
- mime/container hints
- request headers/cookies when needed
- subtitles/track sidecars when applicable
- expiry/session information when applicable

ResolvedImageSequence
- ordered page descriptors or lazy page resolver
- per-page request data when needed

ResolvedPublication
- publication/package resource or handle suitable for publication opener
- runtime access context needed to open/read it
```

ResolvedContent **không được persist như source of truth**. Expiring URLs, cookies, tokens, live-session IDs, transcoding URLs, opened `Publication` objects, Media3 `MediaItem`s hoặc equivalent engine objects phải được re-resolve/reconstruct.

### Selection and resolution are separate

Source selection chọn **binding nào** sẽ được dùng. Resolution materialize **content gì có thể mở ngay lúc này**. Hai việc không được trộn thành một provider DTO.

```text
Canonical target
    ↓
choose SourceBinding
    ↓
resolve(binding, current capabilities/context)
    ↓
ResolvedContent
```

V1 local-first có thể có policy rất đơn giản, nhưng boundary này phải tồn tại để V2 thêm downloaded/online source mà không đổi Player/Reader contract.

### Cardinality

Working cardinality:

```text
Canonical Consumption Target
        0..N SourceBindings

SourceBinding
        0..N stable Assets/representations when meaningful
        resolve → ResolvedContent (runtime)

ResolvedContent
        may expose one or more runtime variants/candidates
```

Không suy ra `1 binding = 1 file = 1 stream`. Jellyfin có nhiều media sources/play methods, Stremio một source/addon có thể trả nhiều streams, calibre một book có nhiều formats, và manga source có thể resolve nhiều page resources cho một chapter.

### Failure boundary

Resolution phải trả failure có semantics typed đủ để orchestration phân biệt tối thiểu các lớp như unavailable/access denied/auth required/not found/unsupported/transient failure. Exact sealed types/API chưa khóa ở foundation, nhưng Player/Reader không được parse provider exception strings để quyết định business state.

### Locked direction from Q-SRC-001

- `Source != StorageRoot != Asset`.
- `SourceBinding != ResolvedContent`.
- `SourceBinding` không chứa canonical media truth ngoài target reference.
- Player/Reader consume `ResolvedContent`, không consume Source/Binding/provider DTO/database entity.
- Ephemeral transport/session data không được trở thành canonical/persistent identity.
- Representation multiplicity không tự tạo canonical Media/Unit multiplicity.
- Local/downloaded/online có thể khác cách materialize Asset nhưng phải hội tụ về cùng resolution boundary.

Exact production interfaces, DTO/class names và persistence columns chưa được khóa ở đây.

## 3.5.1 Storage Root & Availability Boundary

`Q-STO-001` khóa direction sau ở mức semantics.

`StorageRoot` là **app-owned registration của một discovery boundary mà user đã authorize**, không phải filesystem path và không phải quyền Android:

```text
StorageRoot
├── internal RootId owned by app
├── access strategy / platform descriptor
├── user registration intent
├── last-known access / availability observations
└── scan bookkeeping
```

Primary V1 universal-root path là **Storage Access Framework tree access** vì V1 phải đọc đồng thời video, CBZ/ZIP, image folders và EPUB dưới một boundary do user chọn. SAF cho phép user cấp quyền cho đúng subtree thay vì app xin broad filesystem access.

`MediaStore` là **supplemental indexed-media discovery adapter**, hữu ích cho system-indexed video/audio/image collections và volume discovery, nhưng không phải universal source of truth cho V1 filesystem observation. Nó không bao phủ đầy đủ các format/sidecar/package mà universal media library cần.

App-specific internal/external storage là **managed app storage**, dùng cho DB/cache/runtime artifacts và future app-managed downloads khi scope tương ứng yêu cầu. Nó không mặc định là user local-library `StorageRoot`, và dữ liệu app-specific có lifecycle uninstall khác user-owned shared files.

Rules:

- `RootId` là app-owned identity; tree URI/path/MediaStore volume name không phải RootId.
- `StorageRoot != Source != SourceBinding != Asset`.
- Một logical Local Source có thể span nhiều StorageRoots.
- `AccessGrant != StorageRoot`; mất/revoke grant không tự xóa root registration.
- Persisted SAF permission giúp qua reboot nhưng vẫn phải revalidate; move/delete/provider changes có thể làm locator/grant không dùng được.
- SAF document/tree URI và document ID là locators/evidence. Chúng không được dùng làm canonical Media/Unit identity và chưa được coi là final Asset identity trước `Q-ID-001`.
- MediaStore item URI/row ID/volume name cũng là platform locator/evidence, không phải canonical identity.
- Root/storage availability được quan sát lại mỗi run. Last-known state có thể persist để UX/reconciliation, nhưng không được giả định là current truth.
- Removable volume mất tạm thời phải preserve indexed/root metadata; absence do unmounted/access-lost không mặc định là deletion.
- V1 không dựa vào `MANAGE_EXTERNAL_STORAGE` / All files access. Reconsideration chỉ được phép bằng ADR + distribution/policy review nếu một future core use case thật sự không thể dùng privacy-friendly APIs.
- Backup có thể lưu app-owned root registration metadata/hints, nhưng không được tuyên bố restore Android URI grant. Re-authorization vẫn là platform/user action.
- SAF traversal authority follows `Q-SCN-001`; execution ownership/cancellation/retry follows `Q-RUN-001`. Exact traversal optimization remains benchmark/technical design and must not make scanner domain depend directly on framework API.

Conceptual boundary:

```text
User intent
   ↓ register
StorageRoot(RootId)
   ↓ acquire/revalidate
AccessGrant / AccessDescriptor
   ↓ observe through adapter
SAF tree OR supplemental MediaStore scope
   ↓
Observed local representations
   ↓
Ingestion / Reconciliation
   ↓
SourceBinding / optional Asset
```

Exact `StorageRoot` DB fields, SAF URI normalization, MediaStore query optimization và local Asset recognition remain implementation/Identity concerns, not foundation identity rules.

## 3.6 Progress Core

Progress thuộc **canonical consumption identity**, không thuộc provider/source implementation. Sau Q-DOM-002, target có thể là `Media` cho atomic media hoặc `MediaUnit` cho subdivided media, được biểu diễn qua typed `ConsumptionTargetRef`. EPUB/publication boundary theo Q-DOM-003: canonical target là Media/Unit; EPUB package là representation/Asset; internal reading resources không tự động là Units.

Sau `Q-PROG-001`, `Progress` được hiểu là **một current durable consumption state cho mỗi canonical target**, không phải event log/history và không phải generic percentage. Unstarted target thường không cần synthetic `0%` Progress row.

Conceptual shape:

```text
Progress
├── target: ConsumptionTargetRef
├── typed resume anchor?
│   ├── Video: media-time position (+ optional duration snapshot)
│   ├── Image sequence: page ordinal (+ optional page-count snapshot)
│   └── Publication: structured locator/progression data
├── explicit completion state
├── optional SourceBinding/Asset/AssetRevision resume context
└── operational freshness/revision metadata
```

Rules:

- Source/Asset context có thể lưu như last-used compatibility/provenance, nhưng không phải owner của Progress.
- Resume anchor và completion là **orthogonal**: completed item vẫn có thể có resume anchor khi reread/rewatch; backward seek/page/navigation là valid state.
- Completion được quyết bởi media-specific policy/runtime signals rồi **persist explicit**; không re-derive mỗi lần đọc chỉ từ current percentage.
- Position/page/progression không phải version clock; không dùng numeric `max(old,new)` như universal merge rule.
- Runtime engine có thể phát updates thường xuyên, nhưng durable Progress dùng checkpoint/coalescing thay vì write mỗi render/player tick; exact cadence là technical/performance decision.
- Duration/page-count/normalized percentage là extent/projection evidence, không thay precise typed resume anchor.
- Media có child Units không có thêm một authoritative parent Progress row; series-level counts/overall progress là projection.
- Missing/unavailable source/Asset không tự xóa Progress.

Mục tiêu:

```text
Local target
   ↓ switch representation/source
Online target
   ↓ switch representation/source
Downloaded target
```

vẫn quy về **cùng canonical Progress owner** khi mapping tới cùng `ConsumptionTargetRef`. Exact anchor portability qua representation/source/revision được giải ở `Q-PROG-002`.

## 3.6.1 History Boundary

Sau `Q-HIST-001`, `Progress` và `History` có ownership/lifecycle riêng:

```text
Progress
= current mutable durable consumption state for one canonical target

HistoryEntry
= append-oriented durable summary that meaningful consumption activity
  occurred for one canonical target during a bounded activity session
```

Rules:

- một target có tối đa một current Progress nhưng có thể có nhiều HistoryEntries theo thời gian;
- many Progress checkpoints trong cùng target-scoped activity session không tạo many History rows;
- partial consumption có thể là History; completion không phải điều kiện bắt buộc;
- opening/restoring Player/Reader một mình không đủ tạo History nếu chưa có meaningful activity;
- manual Mark Read/Watched/Completed thay current user/progress state nhưng **không fabricate consumption History**;
- Reset Progress không tự xóa History; Clear History không tự reset Progress;
- History target là `ConsumptionTargetRef`, không phải Source/Asset; source/storage availability loss không xóa history;
- History session count không mặc định bằng reread/rewatch count;
- V1 không event-source every seek/page/locator callback.

Exact meaningful-activity threshold, inactivity/session timeout, durable open-session strategy, optional active-duration/source telemetry và future reread/rewatch pass grouping để technical/later product design quyết.

## 3.7 Mapping / Resolver Core

Hệ thống phải có khả năng liên kết:

```text
Canonical Media / Unit
        ↕
External Metadata Entry
Provider Media / Unit
Local Asset Group
```

Mapping phải:

- Persist khi có giá trị lâu dài.
- Có provenance/evidence phù hợp.
- Có thể inspect.
- Có thể sửa.
- Có thể xóa.
- Có thể rematch.
- Không auto-merge khi confidence không đủ.

Advanced confidence/scoring chỉ được thêm khi có use case V2 thực tế.

## 3.7.1 Metadata Enrichment & Override Boundary

Sau `Q-META-001`, descriptive metadata được resolve theo field-level authority/provenance thay vì last-write-wins.

Conceptual layers:

```text
USER_OVERRIDE
DECLARED_LOCAL_METADATA
MATCHED_PROVIDER_METADATA
LOCALLY_DERIVED_OBSERVATION
```

Rules:

- explicit user override là durable app-owned intent và thắng automated refresh/rematch cho field đó cho tới khi user reset/unlock;
- explicit empty phải khác no-override/inherit;
- matched provider metadata mặc định có authority cao hơn filename/path/parser-derived display fallback;
- declared local metadata như NFO/ComicInfo/EPUB package metadata là source category riêng, không phải parser inference; exact local-declared vs provider priority phải deterministic/explicit theo field/source policy, không phụ thuộc query arrival order;
- manual metadata mapping authority và field override authority là hai khái niệm khác nhau; rematch không clear user field overrides;
- provider/network refresh failure không tự xóa last-known metadata;
- multi-valued fields không implicit union-all mọi source;
- metadata provider/local descriptive source không sở hữu canonical IDs, Library/Progress/History, SourceBinding lifecycle hoặc Asset technical truth.

Persistence phải giữ đủ provenance để effective field có thể giải thích/recompute sau refresh/rematch. R4.14 `Q-PER-001` now defines Media-level metadata candidate/override table families as the V1 baseline; Unit/Grouping provenance tables are added only when a concrete use case needs them.

## 3.8 Persistence Core

Mỗi loại durable state phải có **một source of truth rõ ràng**, thay vì coi mọi thứ là state trong RAM hoặc dồn mọi dữ liệu vào một storage mechanism.

### Relational internal state

Room/database là source of truth phù hợp cho normalized relational state như:

- Canonical Media.
- Canonical Media Units.
- Stable source bindings cần persist.
- Library entries.
- Progress.
- History.
- External IDs.
- Mappings.
- User overrides.
- Metadata mappings/candidates/provenance cần persist theo Q-META-001 khi chúng có durable value.

### Small preferences

Small user/app preferences có thể dùng DataStore hoặc mechanism phù hợp thay vì ép vào relational schema.

### External content availability

Database **không phải authority cho việc một external file/document/stream thực tế còn tồn tại hay truy cập được hay không**. Filesystem, Android document/media provider hoặc online provider vẫn là authority của external availability; ingestion/reconciliation cập nhật internal state tương ứng.

Database schema không được sao chép schema của metadata/content provider.

Domain model và persistence model được phép khác nhau.

## 3.9 Ingestion Core

Local scanner là một ingestion pipeline, không phải một god-class.

Pipeline mục tiêu:

```text
Discover
  ↓
Classify
  ↓
Parse
  ↓
Group
  ↓
Identify
  ↓
Reconcile
  ↓
Persist
```

Responsibilities:

- File/document discovery.
- Type detection.
- Filename/path metadata parsing.
- Grouping.
- Identity candidate generation.
- Rename/move/delete/add reconciliation.
- Duplicate handling.
- Local source registration.

Không chứa:

- UI logic.
- Player implementation.
- Reader implementation.
- Remote metadata lookup inside filesystem discovery step.

Metadata enrichment là bước riêng.

### Scan completeness invariant

Scanner phải có semantics cho một lần scan (`ScanRun`/generation), không chỉ stream một loạt file-found events.

Ví dụ:

```text
scan starts
  ↓
3,000 / 20,000 assets observed
  ↓
permission / provider / IO failure
  ↓
scan aborts
```

Trong tình huống này, 17,000 asset chưa quan sát được **không được tự động coi là deleted**.

Baseline rule:

```text
complete + authoritative comparable scan coverage
→ absence MAY become authoritative absence evidence
→ Q-REC-001 maps that evidence to recoverable MISSING or another explicit reconciliation action

partial / failed / cancelled / interrupted / access-lost coverage
→ absence is NOT authoritative evidence
```

Q-SCN-001 đã khóa scan-run **semantics** ở mức PROVISIONAL: declared scope, outcome/coverage và authoritative absence. `Q-RUN-001` now locks execution ownership, retry/cancellation/idempotency and guarded-finalization semantics; exact persistence shape, batch sizing/checkpoint cadence and performance tuning remain downstream persistence/benchmark work.

## 3.10 Consumption Core

Consumption gồm ba engine/boundary độc lập.

### Video Playback

- Play.
- Pause.
- Seek.
- Track selection.
- Resume.

Player nhận playable/resolved input từ app boundary; không nhận database entity, scanner object hoặc provider object trực tiếp.

### Manga / Comic Reader

- Page navigation.
- Reading direction.
- Continuous mode.
- Zoom.
- Resume.

Reader không sở hữu canonical matching.

### Publication / EPUB Reader

Conceptual flow:

```text
Asset
  ↓
Publication
  ↓
Navigator / Reader
```

Resume dựa trên publication locator/progression thay vì chỉ chapter/page index.

## 3.11 Library Core

Library phản ánh **media của user**, không phản ánh trực tiếp filesystem tree hoặc provider catalog.

Responsibilities:

- Library entries.
- Status.
- Continue Watching.
- Continue Reading.
- Recently Added.
- Basic History.

Sau `Q-LIB-001`, Library membership có semantics riêng khỏi discovery/availability:

- `LibraryEntry` là Media-level current membership state; canonical Media có thể tồn tại ngoài Library.
- registered local root mang default admission intent cho newly recognized Media, nhưng không sở hữu membership identity.
- explicit user `Remove from Library` thắng auto-admission và phải survive rescan/rename/move/rebind của cùng `MediaId`.
- `MISSING`, `OUT_OF_SCOPE`, root unavailable hay access loss không tự remove Library membership.
- remove khỏi Library không mặc định xóa Progress, History, SourceBinding, Asset hay physical media.
- `Recently Added` dựa trên thời điểm current active Library membership epoch bắt đầu (`libraryAddedAt` semantics), không dựa trên file `mtime/ctime`, discovery timestamp hay canonical creation time.
- new Episode/Chapter/Asset của một Media đã nằm trong Library không bump Media-level `libraryAddedAt`; nếu cần “Recently Updated” đó là projection khác.

Filesystem/source presence vì vậy **không phải synonym của Library membership**.

## 3.12 Architecture Layering Rule

Default flow:

```text
UI / Android component
        ↓
State holder / ViewModel
        ↓
Domain operation when it adds real policy/orchestration
        ↓
Repository / boundary contract
        ↓
Database / storage / provider / platform implementation
```

`Use Case` **không bắt buộc cho mọi repository method**.

Use case/domain operation chỉ nên tồn tại khi nó thực sự mang:

- Business policy.
- Cross-repository orchestration.
- Reusable domain operation.
- Transaction/consistency boundary.
- Complex transformation.

Ví dụ hợp lý:

- `ReconcileScannedMedia`
- `ResolvePlayableSource`
- `UpdateCanonicalProgress`
- `MatchMetadata`

Tránh wrapper 1:1 chỉ để tăng layer count.

## 3.13 Non-Negotiable Architecture Rules

1. UI không gọi provider trực tiếp.
2. UI không truy cập database trực tiếp.
3. Composable không chứa scanner/mapping/reconciliation logic.
4. Player không biết provider nào cung cấp video.
5. Reader không biết content đến từ local/download/online implementation nào.
6. Canonical Media không dùng provider ID làm primary ID.
7. Filename/path/URI không tự động trở thành canonical identity.
8. Progress thuộc canonical consumption target (`Media` hoặc `MediaUnit`), không thuộc source/provider.
9. Local content là first-class source.
10. Downloaded content là first-class source.
11. Metadata và Content Source là hai khái niệm độc lập.
12. Provider DTO/entity không trở thành canonical domain model.
13. Mapping phải reversible/rematchable.
14. Local mode phải hoạt động khi hoàn toàn offline.
15. Dynamic plugin runtime không được phát triển trước khi provider contracts ổn định.
16. Long-running work không được mặc định gắn vào screen lifecycle.
17. ViewModel không được dùng như persistence layer.
18. Android Service/Worker chỉ điều phối runtime work; domain semantics vẫn thuộc core/domain boundary phù hợp.
19. Performance-critical work không chạy trên main thread.
20. Architecture boundary phải có test/verification phù hợp khi project đủ lớn để regression trở thành rủi ro.
21. Explicit user metadata override không được automated refresh/rematch silently overwrite; explicit empty phải phân biệt với inherit/no override.
22. Effective descriptive metadata phải giữ đủ provenance để phân biệt user override, declared-local, matched-provider và derived fallback; metadata provider không sở hữu canonical/domain/Asset technical truth.
23. Product backup phải preserve app-owned canonical/user state bằng versioned logical contract; raw Room schema không phải public backup format.
24. Restore không được coi URI/grant/Asset availability là current truth; storage access phải reauthorize/revalidate và negative evidence vẫn thuộc Q-SCN/Q-REC.
25. Backup/restore không được resurrect runtime scheduler/ScanRun authority hoặc silently destructive-migrate core user state.

---

# 4. V1 Architecture Audit & Decision Queue

Phần này là **working control surface** cho V1. Nó ghi lại những gì audit đã phát hiện, điểm nào đã đủ chắc để giữ, điểm nào còn mơ hồ và câu hỏi nào phải được giải trước khi subsystem tiếp theo được thiết kế sâu.

Mục tiêu là tránh hai cực đoan:

- code trước rồi mới phát hiện semantics sai;
- thiết kế toàn bộ implementation từ đầu dù chưa có đủ evidence.

## 4.1 Audit Pass 1 — Conclusions

Audit pass 1 giữ nguyên product scope V1 nhưng phát hiện các vùng semantics phải được tách/khóa rõ hơn:

1. `MediaUnit` chưa đủ rõ cho Movie/TV/Manga/EPUB nếu coi mọi hierarchy level giống nhau.
2. `Source` đang có nguy cơ gánh ba vai: provider/source type, unit binding và resolved asset.
3. Local storage cần `StorageRoot`/access semantics riêng khỏi canonical identity.
4. `Identity` và `Reconciliation` liên quan nhưng không phải cùng trách nhiệm.
5. Scanner cần scan-run/completeness semantics để partial failure không biến thành mass deletion.
6. `unavailable`, `access lost`, `missing` và `removed` không được ngầm coi là một trạng thái.
7. Progress phải typed theo consumption kind; không dùng một generic scalar cho video/manga/publication.
8. `History` không phải alias của `Progress`.
9. Library membership không được suy ra ngầm từ filesystem presence.
10. Metadata cần precedence/provenance rule để rematch không silently overwrite user override.
11. Semantic design order và implementation dependency order phải tách riêng.

Những kết luận trên là **audit findings**. Chúng không tự động biến mọi proposed type/name thành locked design; các điểm còn mở nằm trong ledger bên dưới.

## 4.2 V1 Decision Queue

Status values:

- `OPEN` — chưa đủ quyết định để downstream design phụ thuộc vào.
- `RESEARCHING` — đang thu thập use case/evidence.
- `PROVISIONAL` — có hướng mặc định, cần downstream review/benchmark.
- `LOCKED` — đủ ổn để subsystem sau phụ thuộc; thay đổi cần impact review.
- `DEFERRED` — cố ý để version/scope sau.

| ID | Question / ambiguity | Why it matters | Blocks | Status |
|---|---|---|---|---|
| `Q-DOM-001` | `Media`, `MediaGrouping`, `MediaUnit` khác nhau thế nào? Season/Volume có identity riêng không? | Đây là shape gốc cho progress/source/persistence. | Identity, Persistence | PROVISIONAL |
| `Q-DOM-002` | Movie/single-publication media được biểu diễn thế nào khi không có Episode/Chapter tự nhiên? | Tránh tạo fake unit hoặc special-case lan rộng. | Unit model, Consumption | PROVISIONAL |
| `Q-DOM-003` | EPUB/package, runtime Publication và internal spine/chapter nằm ở boundary nào? | Ảnh hưởng canonical target, locator, progress và source mapping. | Publication, Progress | PROVISIONAL |
| `Q-SRC-001` | Contract chính xác giữa `Source`, `SourceBinding` và `Asset/ResolvedContent` là gì? | Tránh Player/Reader phụ thuộc local/provider model. | Source API, Persistence | PROVISIONAL |
| `Q-STO-001` | `StorageRoot` được định nghĩa/persist thế nào và SAF/MediaStore chia trách nhiệm ra sao? | Ảnh hưởng discovery, grants, backup, rescan. | Storage, Ingestion | PROVISIONAL |
| `Q-ID-001` | Evidence nào được dùng để recognize existing Media/Unit/Asset mà không biến evidence thành primary identity? | Quyết định duplicate/move/rename behavior. | Reconciliation | PROVISIONAL |
| `Q-REC-001` | Rename/move/missing/unavailable/access-lost/remove khác nhau thế nào? | Tránh mất Library state khi storage tạm mất. | Ingestion, Library | PROVISIONAL |
| `Q-SCN-001` | Một `ScanRun` complete/partial/failed được xác định thế nào; absence khi nào đủ thành delete evidence? | Ngăn partial scan gây mass deletion. | Persistence, Ingestion | PROVISIONAL |
| `Q-LIB-001` | Scan thấy media có auto-add Library không? Nếu user remove nhưng file còn thì rescan xử lý thế nào? | Tách user intent khỏi filesystem mirror. | Library schema/UX | PROVISIONAL |
| `Q-PROG-001` | Typed progress model tối thiểu cho Video/Manga/EPUB là gì? Completion được suy ra hay persist? | Engine contract + resume correctness. | Consumption, Persistence | PROVISIONAL |
| `Q-PROG-002` | Typed resume anchor có portable qua Asset/source/format/revision không; khi representation đổi thì validate/migrate/fallback thế nào? | Video time, image page ordinal và publication locator đều có thể phụ thuộc exact representation; blind reuse có thể resume sai. | Progress, Reconciliation, Source switching | PROVISIONAL |
| `Q-HIST-001` | Basic History lưu event gì và relation với Progress ra sao? | Tránh khóa schema sai cho future reread/rewatch. | Persistence, Library | PROVISIONAL |
| `Q-META-001` | Precedence giữa user override, matched metadata và locally-derived metadata là gì? | Rematch không được silently ghi đè user intent. | Metadata, Persistence | PROVISIONAL |
| `Q-RUN-001` | Scan nào chạy in-process, scan nào cần persistent work; cancellation/retry/idempotency ra sao? | Android runtime correctness. | Runtime implementation | PROVISIONAL |
| `Q-BACK-001` | Backup chứa những app-owned state nào; restore xử lý storage re-authorization và unavailable assets thế nào? | Backup không được giả vờ restore external permissions/content. | Backup/Restore | PROVISIONAL |

## 4.3 Required Resolution Order

Các câu hỏi không giải ngẫu nhiên. Thứ tự mặc định:

```text
Stage A — Canonical shape
Q-DOM-001 → Q-DOM-002 → Q-DOM-003
        ↓
Stage B — Source/storage shape
Q-SRC-001 → Q-STO-001
        ↓
Stage C — Identity observation & reconciliation
Q-ID-001 → Q-REC-001 → Q-SCN-001
        ↓
Stage D — User state semantics
Q-PROG-001 → Q-PROG-002 → Q-HIST-001 → Q-LIB-001
        ↓
Stage E — Enrichment/user override
Q-META-001
        ↓
Stage F — Android execution & portability
Q-RUN-001 → Q-BACK-001
        ↓
Stage G — Persistence deep design
Q-PER-001
        ↓
Stage H — Module/API boundary design
Q-MOD-001 → Q-API-001
        ↓
Stage I — Project/toolchain bootstrap
Q-BOOT-001
```

Có thể quay lại câu trước nếu câu sau lộ ra contradiction. Không được “đóng” một câu chỉ để tiếp tục roadmap nếu invariants/examples vẫn mâu thuẫn.

**Current audit position (2026-09-18):** Stages A–F remain **PROVISIONAL BASELINE** for domain/source/storage/identity/reconciliation/progress/library/metadata/runtime/backup semantics. R4.14 completed `Q-PER-001`, `Q-MOD-001` and `Q-API-001`; R4.15 completes `Q-BOOT-001` at **PROVISIONAL** level. **Stages G–I status: PROVISIONAL BASELINE / COMPLETE FOR PROJECT SKELETON + QUALITY/SECURITY BOOTSTRAP.**

## 4.4 Decision Closure Template

Mỗi question chỉ chuyển sang `LOCKED` hoặc `PROVISIONAL` khi có tối thiểu:

1. **Problem statement** — câu hỏi đang giải là gì.
2. **Representative examples** — ít nhất video + sequential image + publication nếu question cross-media.
3. **Edge/failure cases** — missing, duplicate, partial, process death hoặc permission loss nếu liên quan.
4. **Decision** — vocabulary/ownership/invariant đã chọn.
5. **Rejected alternatives** — chỉ ghi alternatives có trade-off đáng kể.
6. **Downstream impact** — subsystem/schema/API nào được phép phụ thuộc vào decision.
7. **Verification idea** — unit/integration/property test hoặc acceptance scenario sẽ chứng minh invariant.
8. **Status** — PROVISIONAL/LOCKED/DEFERRED.
9. **ADR requirement** — tạo ADR nếu cost thay đổi lớn hoặc decision platform/framework-specific.

## 4.5 Decision Record — Q-DOM-001: Media / MediaGrouping / MediaUnit

**Status:** `PROVISIONAL`

Q-DOM-001 khóa vocabulary nền cho Stage A. Q-DOM-002 và Q-DOM-003 đã kiểm chứng direct atomic consumption và publication boundary mà không làm contradiction; record vẫn `PROVISIONAL` cho tới khi Source/Identity audit kiểm chứng downstream ownership.

### 4.5.1 Problem Statement

V1 phải biểu diễn được nhiều shape khác nhau mà không tạo một hierarchy giả:

```text
TV / Anime
Series → Season → Episode

Manga
Series → Volume? → Chapter

Movie
Movie → ?

Publication / EPUB
Work / Publication → internal reading resources?
```

Nếu `Season`, `Volume`, `Episode`, `Chapter`, file và publication resource đều bị coi là cùng một loại node, progress/source/persistence sẽ trở nên mơ hồ. Ngược lại, nếu mỗi media type có một domain graph riêng hoàn toàn thì Library, Source Core và Progress Core sẽ mất khả năng dùng chung.

### 4.5.2 Research Findings

Các hệ thống thực tế không dùng một hierarchy chung tuyệt đối:

- **Jellyfin** tổ chức TV Show theo `Series → Season → Episode`; Season và Episode có thể có metadata/provider identifiers riêng. Điều này cho thấy Season đôi khi cần một referent ổn định ở mức grouping, nhưng không chứng minh mọi media type đều cần grouping.
- **Komga** chủ yếu dùng `Series → Book`; `Volume` có thể xuất hiện như metadata và không bắt buộc trở thành một node nằm giữa Series và Book. Read progress được track ở Book.
- **Mihon local source** dùng series folder và coi mỗi folder/archive con là một Chapter; không có universal Volume node bắt buộc.
- **Readium** coi một ebook/comic là `Publication` chứa `readingOrder`, resources và locators. Internal reading resources phục vụ navigation/rendering và không tự nhiên tương đương Episode/Chapter canonical của library.

Kết luận từ research: **grouping phải là optional semantic structure**, còn independently consumable content cần một boundary riêng. Không được suy ra một universal tree chỉ từ folder structure hoặc format internals.

Research references:

- Jellyfin — TV Shows: https://jellyfin.org/docs/general/server/media/shows/
- Komga — Scanning, Analyzing and Refreshing Metadata: https://komga.org/docs/guides/scan-analysis-refresh/
- Komga — Read progress: https://komga.org/docs/guides/read-progress/
- Mihon — Local source: https://mihon.app/docs/guides/local-source/
- Readium Kotlin — Getting started / Publication model: https://readium.org/kotlin-toolkit/latest/guides/getting-started/

### 4.5.3 Provisional Decision

V1 dùng ba vai trò domain khác nhau:

```text
Media
├── MediaGrouping?      optional structural entity
│    └── MediaUnit *
└── MediaUnit *         unit may exist without a grouping
```

Không diễn giải graph trên thành yêu cầu mọi Media phải có Grouping hoặc mọi Media phải có nhiều Unit.

#### `Media`

`Media` là **canonical work/library-level identity** do app kiểm soát.

Properties/invariants ở mức semantics:

- Có stable internal `MediaId`.
- Đại diện identity mà Library, metadata matching và high-level user intent quy chiếu tới.
- Không lấy folder, filename, URI hoặc provider ID làm identity.
- Có thể tồn tại khi storage/source tạm unavailable.
- Không encode Season/Volume/Chapter hierarchy trực tiếp vào `MediaId`.

Ví dụ dự kiến:

```text
TV/Anime  → series/work
Manga     → series/work
Novel     → standalone publication có thể direct Media target; series có independently consumable book/volume có thể dùng Unit theo Q-DOM-003
Movie     → atomic movie work; direct `MediaTarget(MediaId)` theo Q-DOM-002
```

#### `MediaGrouping`

`MediaGrouping` là **optional organizational/structural entity bên trong một Media**.

V1 chỉ cho phép nó tồn tại khi grouping có semantics thực ngoài việc “folder này nằm trong folder kia”. Typical examples:

```text
SEASON
VOLUME
```

Rules:

- Grouping **không phải consumption target**.
- Không sở hữu playback/reading `Progress`.
- Không có `SourceBinding` dùng để mở content trực tiếp.
- Một `MediaUnit` có thể không thuộc grouping nào.
- Khi grouping được materialize, nó có stable internal `GroupingId` để có thể rename/reorder/remap mà không biến label/number thành identity.
- Unit identity không phụ thuộc grouping identity. Chuyển một Unit từ grouping A sang B không tự tạo Unit mới.
- Xóa/reclassify grouping không mặc định xóa Units bên trong; reconciliation phải xử lý reassignment có chủ ý.
- V1 **không** tạo recursive generic grouping tree. Franchise, arc, collection hierarchy và nested arbitrary groups để version/use case sau quyết định.

`GroupingId` là internal referent, không có nghĩa Season/Volume được xem là global canonical media identity ngang hàng với `Media`.

#### `MediaUnit`

`MediaUnit` là **canonical independently consumable identity** nằm trong phạm vi một `Media`.

Rules:

- Có stable internal `UnitId`.
- Là target mặc định cho `SourceBinding` và consumption progress.
- Có thể có `groupingId = null`.
- Reorder/regroup/rename không được tự thay `UnitId`.
- File/document/provider resource không tự động là `MediaUnit`; chúng là evidence/source/asset cho Unit.
- Page, image, EPUB spine item, XHTML resource hoặc player segment **không mặc định trở thành MediaUnit**. Chúng thuộc consumption format/runtime trừ khi một use case canonical thực sự chứng minh ngược lại.

### 4.5.4 Representative Examples

#### TV / Anime

```text
Media: Frieren
└── Grouping: Season 1
    ├── Unit: Episode 1
    ├── Unit: Episode 2
    └── Unit: Episode 3
```

Season có thể có metadata/order riêng, nhưng Episode mới là consumption/progress target.

#### Manga / Comic

Khi volume information đáng tin cậy:

```text
Media: Berserk
└── Grouping: Volume 1
    ├── Unit: Chapter 1
    └── Unit: Chapter 2
```

Khi volume chưa biết hoặc source không mô hình hóa volume:

```text
Media: Manga X
├── Unit: Chapter 1
├── Unit: Chapter 2
└── Unit: Chapter 3
```

Không tạo `Volume Unknown` chỉ để thỏa hierarchy.

#### Specials / irregular ordering

Một Unit có thể tồn tại ngoài grouping thông thường. `Season 0` chỉ được tạo nếu dữ liệu/domain evidence thực sự mô hình hóa nó; không bắt scanner phải map mọi “special” vào một fake group.

#### Movie / one-shot / single publication

Q-DOM-002 đã chọn hướng **direct atomic consumption**: nếu canonical work tự nó là independently consumable identity và không có canonical subdivision tự nhiên, `Media` có thể là consumption target trực tiếp. Không tạo single `MediaUnit` chỉ để chuẩn hóa API.

```text
Atomic Media
    ↓
ConsumptionTargetRef.MediaTarget(MediaId)
```

Nếu content có subdivision canonical thật sự, consumption vẫn đi qua `MediaUnit`. Q-DOM-003 đã xác nhận EPUB/package structure không tự tạo subdivision canonical.

#### EPUB / Publication

Q-DOM-003 đã xác nhận publication boundary: EPUB package là representation/Asset, runtime `Publication` là consumption model, còn internal `readingOrder`/spine/TOC resources **không tự động** được nâng thành canonical `MediaUnit`.

### 4.5.5 Creation and Reconciliation Guardrails

`MediaGrouping` không được tạo chỉ vì scanner thấy subfolder.

Possible evidence có thể bao gồm:

- parsed Season/Volume marker;
- embedded/local metadata;
- matched metadata provider structure;
- explicit user correction;
- existing canonical grouping binding.

Folder/path chỉ là observation/evidence.

```text
folder structure ≠ canonical hierarchy
```

Reconciliation phải cho phép:

```text
same Unit + changed grouping
→ preserve UnitId
→ preserve Progress
→ preserve SourceBinding when source identity remains valid
```

### 4.5.6 Rejected Alternatives

#### Fixed hierarchy: `Media → Season/Volume → Unit`

Rejected vì:

- Manga có thể không có volume grouping.
- Movie/single publication không có middle grouping tự nhiên.
- Folder structure dễ tạo fake groups.

#### Flatten Season/Volume thành vài field trực tiếp trên Unit và không có grouping concept

Không chọn làm default vì season-level metadata, ordering, external mapping hoặc UI grouping có thể cần stable referent riêng. Tuy nhiên Grouping vẫn optional và lightweight; không được biến thành generic tree engine.

#### Generic recursive `MediaNode` tree cho mọi loại media

Rejected cho V1 vì quá tổng quát, làm identity/progress/source ownership khó chứng minh và mở đường cho over-engineering trước use case.

### 4.5.7 Downstream Impact

Q-DOM-001 cho phép các vòng sau tạm dựa vào các invariant:

```text
MediaId is stable
Grouping is optional
Grouping is non-consumable
UnitId is stable
Unit may be ungrouped
Regrouping does not redefine Unit identity
```

Nhưng production Persistence schema chưa được khóa vì:

- Q-DOM-002 đã quyết single-unit/atomic media có thể target trực tiếp `Media` mà không tạo fake Unit.
- Q-DOM-003 đã kiểm chứng Publication/EPUB boundary: package/resource structure không buộc tạo canonical Unit mới.
- Q-ID-001/Q-REC-001 sẽ quyết evidence/reconciliation cụ thể.

### 4.5.8 Verification Ideas

Sau implementation, cần có tests chứng minh tối thiểu:

1. Media có Units nhưng không có Grouping vẫn hợp lệ.
2. Regroup một Unit không thay `UnitId`.
3. Regroup không làm mất Progress.
4. Rename Grouping không thay identity của Grouping hoặc Unit.
5. Folder rename/restructure không tự tạo canonical grouping mới nếu semantic evidence không thay đổi.
6. Grouping không thể trực tiếp trở thành playback/reader target.
7. Internal EPUB resource/page không tự xuất hiện như library Unit chỉ vì engine có thể navigate tới nó.

### 4.5.9 Self-Review / Remaining Risks

Decision này giải được distinction giữa work, grouping và independently consumable item mà không ép một universal tree.

Các điểm **cố ý chưa giải**:

- Exact portability/migration của typed resume anchor qua Asset/source/revision (`Q-PROG-002`).
- Standalone volume/edition có phải atomic `Media` hay thuộc một series/work hierarchy khác khi use case edition xuất hiện.
- Grouping mapping với external providers được persist cụ thể ra sao (`Q-ID-001` + persistence design).

Q-DOM-002 đã loại bỏ constraint “mọi consumption/progress phải gắn với Unit”, và Q-DOM-003 không tạo contradiction với quyết định đó. Q-DOM-001 vẫn giữ `PROVISIONAL` để Source/Identity audit tiếp tục kiểm chứng ownership/reconciliation downstream.

## 4.6 Decision Record — Q-DOM-002: Atomic Media / Single-Unit Representation

**Status:** `PROVISIONAL`

Q-DOM-002 quyết định cách biểu diễn Movie, one-shot và các work có **một consumption identity tự nhiên** mà không có Episode/Chapter canonical. Mục tiêu là tránh cả hai cực: tạo fake Unit cho mọi thứ hoặc để Player/Reader/Progress phải special-case từng media kind.

### 4.6.1 Problem Statement

Sau Q-DOM-001, `MediaUnit` có semantics rõ cho Episode/Chapter. Nhưng một số work không có subdivision canonical tự nhiên:

```text
Movie
└── one feature

One-shot comic
└── one readable work

Standalone book
└── one publication
```

Nếu bắt buộc:

```text
Media → synthetic Unit → SourceBinding → Asset
```

thì Unit chỉ là wrapper kỹ thuật, không có domain meaning riêng. Nếu cho Media được consume trực tiếp nhưng không có một target vocabulary chung, Source/Progress/History sẽ phải chứa nhiều nhánh special-case.

Q-DOM-002 cần trả lời:

1. `Media` có thể trực tiếp là consumption identity không?
2. Khi nào một child Unit là **real canonical unit** và khi nào chỉ là fake wrapper?
3. SourceBinding/Progress tham chiếu đồng nhất thế nào mà không tạo thêm một canonical entity dư thừa?

### 4.6.2 Cross-Project Research

Research vòng này ưu tiên nhiều project ở các domain khác nhau để tránh lấy hierarchy của một app rồi áp lên universal client.

| Project | Shape quan sát được | Ý nghĩa cho Q-DOM-002 |
|---|---|---|
| **Kodi** | Movie là record trực tiếp gắn file; TV Show và Episode là các record khác nhau. Movie không cần child Episode/Unit giả. | Atomic video có thể tự là playable identity. |
| **Plex** | `movie`, `show`, `season`, `episode` là metadata types riêng. Movie là item trực tiếp; Show/Season có children; Episode là playable child. | Work đơn và work phân cấp không cần chung một depth. |
| **Jellyfin** | Movie và Episode đều là `BaseItemKind`; Series/Season là các item cấu trúc khác. User/playback data có thể gắn trực tiếp vào Movie/Episode. | Movie có thể là consumption item trực tiếp mà không có synthetic episode. |
| **Emby** | Movie/Episode là specialized `Video`; Series/Season là specialized `Folder`; `UserData` nằm trên item. | Phân biệt container vs playable item dựa semantics, không dựa “mọi work phải có unit”. |
| **Stremio** | Với Movie, video ID chính là Meta ID; với Series, video ID tách thành episode identity như `metaId:season:episode`. | Atomic item dùng work ID trực tiếp; serialized item dùng child video ID. |
| **calibre** | Một Book record có thể có nhiều format (EPUB/MOBI/...) nhưng vẫn là một book identity. | Nhiều file/format không đồng nghĩa nhiều canonical units. |
| **Audiobookshelf** | Book là Library Item có progress; chapters/tracks là structure bên trong media. | Internal segments không bắt buộc trở thành library-level units. |
| **Readium** | `Publication` là đối tượng được mở/điều hướng; `readingOrder` và `Locator` mô tả nội dung/vị trí bên trong publication. | Một publication có thể là direct consumption object; internal resources không cần canonical units. |
| **Komga** | One-shot vẫn được biểu diễn `Series + single Book`; progress gắn Book. | Đây là lựa chọn series-centric hợp lý cho comic server, nhưng tạo wrapper vì domain của Komga xoay quanh Series/Book. Không nên mặc định áp cho universal app. |
| **Mihon** | Local source yêu cầu Series → Chapter; một folder/archive/EPUB được coi là một Chapter và EPUB không tự split thành internal chapters. | Reader-centric source model có thể ép child unit vì toàn app xoay quanh Manga/Chapter; đây là domain-specific convention. |
| **Kavita** | Series/Volume/Chapter hierarchy được dùng rộng; các trường hợp “một file là cả volume” có lịch sử phải bridge giữa Volume và Chapter semantics. | Ép hierarchy đồng nhất có thể tạo ambiguity khi physical item và logical child không khớp. |

### 4.6.3 Research Interpretation

Các project cross-domain như Kodi, Plex, Jellyfin, Emby và Stremio đều cho thấy:

```text
Movie
= canonical item
= playable identity
```

không cần một Episode/Unit con giả.

Các hệ thống sách như calibre và Audiobookshelf cũng cho thấy:

```text
Book
= library identity
= consumption/progress identity

format / track / chapter
!= automatically another canonical work/unit
```

Komga, Mihon và Kavita chứng minh hướng ngược lại **cũng có thể hoạt động** trong domain truyện, nhưng đó là khi product vocabulary vốn đã series-centric/chapter-centric. Vì app này phải hỗ trợ video + manga + novel trong cùng core, không nên chọn convention của một domain làm universal law.

### 4.6.4 Provisional Decision

V1 cho phép **atomic `Media` được consume trực tiếp**.

Không tạo `MediaUnit` chỉ vì Source/Progress API muốn một child ID.

Để upper layers không phải special-case `MediaId` và `UnitId`, dùng một typed reference/value concept:

```text
ConsumptionTargetRef
├── MediaTarget(MediaId)
└── UnitTarget(UnitId)
```

`ConsumptionTargetRef`:

- không phải canonical entity mới;
- không có metadata/library identity riêng;
- chỉ biểu diễn “canonical identity nào đang được consume”;
- được phép dùng ở SourceBinding, Progress, History và consumption orchestration;
- production representation cụ thể (sealed type, tagged ID, separate columns/tables...) để Persistence/API design quyết sau.

### 4.6.5 Atomic vs Subdivided Rule

Một `Media` có thể là direct target khi **canonical work và independently consumable identity là cùng một referent**.

Typical atomic examples:

```text
Movie
Standalone film/special
One-shot comic without canonical chapter subdivision
Standalone book/publication
```

Typical subdivided examples:

```text
TV / Anime Series
  → Episode Units

Serialized Manga
  → Chapter Units
```

Rule quan trọng:

```text
current child count != consumption shape
```

Một TV series hiện chỉ có **1 episode** vẫn là subdivided media:

```text
Media: Series
└── Unit: Episode 1
```

Không chuyển thành direct Media chỉ vì cardinality hiện tại bằng 1.

Ngược lại, một Movie không cần fake Unit chỉ vì storage có đúng 1 file.

### 4.6.6 Source and Variant Semantics

Atomic Media vẫn có thể có nhiều availability/representations:

```text
MediaTarget(Movie A)
├── Local binding → 1080p file
├── Local binding → 4K file
├── Download binding
└── Future Provider binding
```

Các binding/asset trên **không phải MediaUnit** nếu chúng chỉ là alternative representations của cùng consumption identity.

Tương tự:

```text
Book
├── EPUB asset
└── another supported format
```

không tự động thành hai Units.

Multi-file physical representation cũng không tự động tạo Units:

```text
one movie split into parts
one audiobook split into tracks
one publication containing many resources
```

Physical segmentation chỉ trở thành canonical Unit nếu product/domain semantics thực sự coi mỗi segment là independently consumable/progress/source-switchable item.

### 4.6.7 One-Shot Guardrail

“One-shot” không được xác định đơn giản bằng `unitCount == 1`.

Hai trường hợp khác nhau:

```text
A. Canonical work itself is a one-shot
→ direct MediaTarget may be correct.

B. A serialized work currently has only one known chapter
→ still Media + Unit.
```

Nếu metadata/source về sau chứng minh work có canonical chapter subdivision, việc chuyển shape từ atomic → subdivided là **explicit reconciliation/migration decision**, không được scanner âm thầm đổi chỉ vì thấy thêm file.

### 4.6.8 Progress / History Ownership

Sau Q-DOM-002:

```text
Progress
History
SourceBinding
      ↓
ConsumptionTargetRef
```

Ví dụ:

```text
Movie progress
→ MediaTarget(movieId)

Episode progress
→ UnitTarget(episodeId)

Manga chapter progress
→ UnitTarget(chapterId)
```

Không được đồng thời lưu một “primary progress” trên cả parent Media và child Unit cho cùng một consumption path, vì sẽ tạo hai source of truth.

Library membership vẫn là Media-level user state; consumption target không thay Library identity.

### 4.6.9 Representative Scenarios

#### Movie with one file

```text
Media: Interstellar
ConsumptionTargetRef: MediaTarget(interstellarId)
SourceBinding: local
Asset: content://.../Interstellar.mkv
```

Không tạo:

```text
Media: Interstellar
└── Unit: Interstellar
```

chỉ để có Unit.

#### Movie with multiple encodes

```text
MediaTarget(interstellarId)
├── binding → 1080p
└── binding → 4K
```

Vẫn một consumption identity.

Exact semantics cho theatrical/director's-cut/edition khác nhau **deferred**; V1 không giả định encode variant = edition.

#### TV series with one discovered episode

```text
Media: Series X
└── Unit: Episode 1
```

Không collapse thành direct Media.

#### One-shot CBZ

Nếu one-shot là canonical work hoàn chỉnh, không có chapter identity hữu ích:

```text
Media: One-shot X
ConsumptionTargetRef: MediaTarget(mediaId)
Asset: one-shot.cbz
```

Nếu metadata xác nhận Chapter 0/Chapter 1 là canonical unit của một larger serialized work, đó là Unit semantics thay vì file-count semantics.

#### Standalone publication

Working expectation:

```text
MediaTarget(bookId)
→ publication source/binding
→ publication asset
```

và Q-DOM-003 đã xác nhận boundary ở mức semantics: EPUB package là Asset/representation, runtime Publication là reader model, Locator là internal resume position. Q-PROG-001 đã khóa typed Locator/completion semantics; Q-PROG-002 giờ khóa portability qua representation/revision bằng precise-first compatibility, exact migration và explicitly approximate fallback semantics.

### 4.6.10 Rejected Alternatives

#### Alternative A — Always create exactly one Unit for atomic Media

Rejected làm default vì:

- Unit không có domain meaning riêng.
- Duplicate identity (`MediaId` và `UnitId`) cho cùng một real-world referent.
- Metadata/mapping dễ phải quyết định cái gì nằm ở Media và cái gì nằm ở fake Unit.
- Scanner/reconciliation phải duy trì wrapper vô nghĩa.
- Các project video/book cross-domain không cần pattern này.

#### Alternative B — Allow direct Media but không có typed target abstraction

Rejected vì SourceBinding/Progress/History sẽ liên tục có API dạng:

```text
mediaId? OR unitId?
```

hoặc branch theo media kind. Typed `ConsumptionTargetRef` giữ domain explicit mà không tạo entity mới.

#### Alternative C — Every physical file/resource is a Unit

Rejected vì file/format/track/page/resource là representation/runtime structure, không mặc định là canonical consumption identity.

#### Alternative D — Universal recursive node tree

Vẫn rejected như Q-DOM-001: nó giải syntactic uniformity nhưng làm ownership/identity quá tổng quát trước use case.

### 4.6.11 Downstream Impact

Q-DOM-002 mở khóa các assumptions sau cho audit tiếp theo:

```text
Media can be directly consumable.
MediaUnit exists only when subdivision has canonical semantics.
ConsumptionTargetRef = MediaTarget | UnitTarget.
No synthetic Unit solely for API normalization.
Multiple sources/assets do not imply multiple Units.
Progress/History/SourceBinding target canonical consumption identity.
```

Nó thay đổi wording của Source/Progress foundation từ `Canonical Unit` sang `Canonical Consumption Target`.

Chưa khóa:

- production DB representation của target;
- exact local Asset locator/revision and StorageRoot relation (`Q-STO-001`, `Q-ID-001`, `Q-REC-001`);
- cross-representation resume-anchor portability/revision migration (`Q-PROG-002`);
- edition/cut/release hierarchy;
- atomic → subdivided migration policy chi tiết (`Q-ID-001`/`Q-REC-001`).

### 4.6.12 Verification Ideas

Sau implementation, cần test ít nhất:

1. Movie có thể play/resume mà không có MediaUnit row/object giả.
2. Series có một Episode vẫn dùng Unit progress.
3. Thêm source/encode thứ hai cho Movie không tạo Unit mới.
4. Đổi source của Movie giữ cùng progress target.
5. One-shot direct Media không tạo duplicate progress ở parent/child.
6. Scanner thấy file split/track/resource không tự tạo canonical Unit.
7. Nếu work được explicit reclassified atomic → subdivided, migration/reconciliation phải preserve user state theo policy được khóa sau.
8. Library Entry vẫn tham chiếu Media dù progress target là Media hay Unit.

### 4.6.13 Remaining Risks

Q-DOM-003 đã xác nhận mô hình trên EPUB/Publication: publication package có thể chứa nhiều navigation resources nhưng các resource đó không tự trở thành canonical Units. Vì vậy Q-DOM-002 không còn bị publication semantics block. Record vẫn `PROVISIONAL` cho tới khi Source/Identity audit kiểm chứng direct target ownership và migration/reconciliation rules.

### 4.6.14 Research References

- Kodi MyVideos database: https://kodi.wiki/view/Databases/MyVideos
- Plex Media Server API — metadata types: https://developer.plex.tv/pms/
- Jellyfin BaseItem kinds: https://github.com/jellyfin/jellyfin/blob/master/Jellyfin.Data/Enums/BaseItemKind.cs
- Jellyfin BaseItemDto/UserData: https://github.com/jellyfin/jellyfin/blob/master/MediaBrowser.Model/Dto/BaseItemDto.cs
- Emby Item Types: https://dev.emby.media/doc/restapi/Item-Types.html
- Emby Item Information/UserData: https://dev.emby.media/doc/restapi/Item-Information.html
- Stremio Addon Protocol: https://stremio.github.io/stremio-addon-sdk/protocol.html
- Stremio Stream handler: https://stremio.github.io/stremio-addon-sdk/api/requests/defineStreamHandler.html
- calibre metadata / multiple book formats: https://manual.calibre-ebook.com/metadata.html
- Audiobookshelf API: https://api.audiobookshelf.org/
- Audiobookshelf book library structure: https://audiobookshelf.org/docs/documentation/libraries/book-library/directory-structure/
- Readium Kotlin Publication model: https://readium.org/kotlin-toolkit/latest/guides/getting-started/
- Komga One-Shots: https://komga.org/docs/guides/oneshots/
- Komga Books/read progress API: https://komga.org/docs/openapi/books/
- Mihon Local source: https://mihon.app/docs/guides/local-source/
- Kavita project/release notes and scanner discussions: https://github.com/Kareadita/Kavita


## 4.7 Decision Record — Q-DOM-003: EPUB / Publication Boundary

**Status:** `PROVISIONAL`

Q-DOM-003 quyết định boundary giữa canonical identity của app, EPUB package/file, runtime publication model, internal reading resources và persisted reading location. Mục tiêu là tránh biến implementation detail của EPUB thành `MediaUnit`, đồng thời không làm Reader engine trở thành nơi sở hữu canonical progress.

### 4.7.1 Problem Statement

Một EPUB vừa là một file/package vật lý, vừa chứa metadata, navigation, `spine`/reading order, XHTML/SVG resources, images/fonts và các anchor nội bộ. Nếu map trực tiếp các phần này sang canonical domain, app có thể tạo hierarchy sai:

```text
Media
└── EPUB file?
    ├── spine item?
    ├── chapter heading?
    └── XHTML resource?
```

Q-DOM-003 phải trả lời:

1. EPUB file/package là canonical `Media`, `MediaUnit` hay chỉ là representation/Asset?
2. `Publication` nên là persisted canonical entity hay runtime consumption model?
3. Internal `spine`/`readingOrder`/TOC chapter có trở thành `MediaUnit` không?
4. Reading progress thuộc canonical target nào và locator nằm ở boundary nào?
5. Một series có nhiều EPUB volume/book khác gì một standalone EPUB?

### 4.7.2 Research Findings

#### W3C EPUB 3.3

EPUB 3.3 định nghĩa **EPUB Publication** là một logical document entity gồm các interrelated resources packaged trong EPUB container. `spine` chỉ định ordered list của content documents tạo **default reading order**. Standard cũng nói một EPUB Publication thường đại diện một intellectual/artistic work, nhưng không bắt mọi internal resource trở thành một independent work.

Architecture implication:

```text
EPUB container/package
contains
→ publication resources
→ default reading order (spine)
```

`spine item` là rendering/navigation structure của publication, không phải bằng chứng đủ để tạo canonical library Unit.

#### Readium Kotlin / Readium Architecture

Readium tách rõ:

```text
Asset
  ↓ parse/open
Publication
  ↓ present
Navigator
  ↕
Locator
```

- `Asset` là một file/package/resource đầu vào.
- `Publication` chứa metadata, `readingOrder`, secondary `resources`, TOC và services.
- `Navigator` render/navigate Publication.
- `Locator` biểu diễn một vị trí có thể persist/share trong Publication.
- Navigator **không persist user progress**; host app phải lưu `currentLocator` và restore nó sau.

Readium cũng tránh khái niệm “screen page” bền vững cho reflowable EPUB; positions/locators được dùng thay vì giả định page number không đổi theo font size/screen size.

#### calibre

calibre cho phép **một Book entry có nhiều format** (ví dụ EPUB + MOBI) nhưng vẫn là một book/library identity. Điều này là evidence mạnh rằng physical ebook format/file không nên mặc định trở thành canonical book identity riêng.

#### Komga

Komga track reading progress **per Book**. EPUB reader của Komga dùng Readium-style Web Publication Manifest, Positions API và Progression API. Internal EPUB resources được expose để reader render, trong khi user progress vẫn gắn với Book-level reading object.

#### Foliate

Foliate lưu `lastLocation` và annotations bằng EPUB CFI/location bên trong book. CFI/location dùng để quay lại vị trí nội bộ; nó không tạo library entities cho từng XHTML/spine resource.

#### KOReader / Komga integration

KOReader coi file/book là reading object có status/progress. Khi sync với Komga, regular EPUB progress có thể giảm precision khi chuyển hệ thống (ví dụ chỉ đồng bộ được tới chapter boundary trong một số flow). Đây là evidence rằng **location token có representation/engine compatibility constraints** và không nên bị coi là universal canonical identity.

#### Kavita

Kavita là architecture series/chapter-centric nên progress DTO vẫn tham chiếu series/chapter IDs, nhưng EPUB-specific `BookScrollId` được lưu như resume token bên trong reading object. Đây là useful counter-example: even khi outer hierarchy khác app của ta, internal EPUB location vẫn là reader-position data chứ không phải tự động là new canonical chapter entity.

### 4.7.3 Provisional Decision

V1 dùng boundary sau:

```text
Canonical Consumption Target
MediaTarget(MediaId)
      OR
UnitTarget(UnitId)
        ↓
SourceBinding
        ↓
Publication Asset / Resolved Content
        ↓
Publication parser / reader engine
        ↓
Runtime Publication
├── readingOrder / spine-derived resources
├── resources
├── table of contents
└── navigation services
        ↓
Navigator / Reader
        ↓
Locator
        ↓
Progress Core persists resume state for the canonical target
```

#### Rule 1 — EPUB package/file is a representation, not canonical identity

Một `.epub` file/package mặc định là **Asset/representation được một SourceBinding cung cấp**.

```text
.epub URI/path/file
≠ MediaId
≠ UnitId
```

Rename/move/replacement của file vì vậy không được tự động tạo canonical work mới nếu reconciliation evidence nói đó vẫn là cùng content target.

#### Rule 2 — Runtime `Publication` is not a new canonical entity by default

`Publication` là **consumption/runtime model** được parser/reader engine tạo từ resolved publication content.

Nó có thể chứa metadata/navigation/services hữu ích, nhưng V1 không tạo một persisted canonical `PublicationId` riêng chỉ vì reader engine có object `Publication`.

Nếu về sau product cần publication identity độc lập với Media/Unit (ví dụ editions có lifecycle riêng), đó là decision mới; không pre-model trong V1.

#### Rule 3 — EPUB internals do not automatically become `MediaUnit`

Các phần sau không tự động là canonical Unit:

```text
spine item
readingOrder Link
XHTML/SVG document
TOC heading
anchor
CSS/image/font resource
rendered page
```

Chúng là **publication navigation/rendering structure**.

Một internal chapter heading chỉ trở thành canonical `MediaUnit` nếu app có independent domain evidence/use case cho nó — ví dụ content đó thật sự là separately sourceable/trackable canonical chapter outside the package. EPUB structure một mình không đủ.

#### Rule 4 — Standalone publication can use direct `MediaTarget`

Standalone novel/book mà canonical work và independently consumable object trùng nhau:

```text
Media: Book A
ConsumptionTargetRef: MediaTarget(bookId)
SourceBinding: local
Asset: BookA.epub
Runtime: Publication
Progress: Locator inside Publication
```

Không tạo fake Unit chỉ để chứa EPUB.

#### Rule 5 — Series with independently consumable books/volumes may use `MediaUnit`

Nếu domain thực sự có canonical subdivisions như từng Light Novel volume/book độc lập:

```text
Media: Series A
├── Unit: Volume 1 / Book 1
│     └── source → volume1.epub
└── Unit: Volume 2 / Book 2
      └── source → volume2.epub
```

Ở đây Unit tồn tại vì **book/volume là independently consumable canonical subdivision**, không phải vì file format là EPUB.

Một EPUB omnibus chứa nhiều headings/sections vẫn không tự động split thành Units. Packaging structure không quyết canonical segmentation.

### 4.7.4 Progress Boundary

Q-DOM-003 ban đầu khóa ownership nhưng chưa khóa typed progress semantics. Sau Q-PROG-001, publication progress có structured Locator + explicit completion; exact DB serialization vẫn deferred:

```text
Canonical target owns progress semantics.
Reader/Navigator produces a publication locator.
Host app persists/restores progress.
```

Conceptual form:

```text
PublicationProgress
├── target: ConsumptionTargetRef
├── locator: structured publication location token
├── normalized progression?   // projection/fallback evidence
└── completion: explicit durable state
```

Không persist “current chapter entity” chỉ vì TOC có chapter label.

Không dùng screen page number làm durable identity cho reflowable EPUB.

### 4.7.5 Locator Portability Is Explicitly Not Assumed

Một locator có thể phụ thuộc exact publication representation/layout/resource structure. Vì vậy:

```text
same canonical target
≠ locator guaranteed portable across every asset/source/revision/format
```

Ví dụ một EPUB được replace bằng revision mới có thể đổi XHTML paths/CFI/resource ordering. Một EPUB và MOBI/PDF representation của cùng book cũng không mặc định dùng cùng resume token.

Q-DOM-003 **không giải quyết bằng cách gắn progress vào Asset**; canonical progress ownership vẫn thuộc `ConsumptionTargetRef`. Nhưng exact strategy để giữ/migrate/fallback resume state qua representation changes được mở thành:

```text
Q-PROG-002 — Cross-media resume-anchor portability / revision migration
```

Q-PROG-001 đã khóa precise typed anchor + explicit completion và cho phép normalized progression làm projection/fallback evidence. Q-PROG-002 giờ khóa rằng precise anchor là representation-contextual: chỉ exact-reuse khi compatibility được chứng minh, nếu không phải migrate/fallback typed và bảo thủ.

### 4.7.6 Representative Scenarios

#### Standalone EPUB

```text
MediaTarget(bookId)
→ Local SourceBinding
→ book.epub Asset
→ Runtime Publication
→ Locator
```

No synthetic Unit.

#### Light Novel series with separate volumes

```text
Media: Series X
├── Unit: Volume 1 → v1.epub
├── Unit: Volume 2 → v2.epub
└── Unit: Volume 3 → v3.epub
```

Unit identity đến từ canonical volume/book semantics.

#### One EPUB with 40 XHTML spine items

```text
1 canonical target
1 publication asset
40 internal reading resources
```

Không suy ra 40 MediaUnits.

#### EPUB TOC has 25 chapter headings inside 5 XHTML files

TOC cardinality và file cardinality đều không quyết Unit cardinality.

#### EPUB asset replaced by a new revision

Canonical target có thể vẫn giữ nguyên, nhưng old locator **không được assumed valid**. Q-REC cung cấp representation/revision transition; Q-PROG-002 dùng transition đó để exact-reuse, migrate, approximate-fallback hoặc từ chối blind restore.

#### Same book available as EPUB and another ebook format

Hai representation không tự tạo hai Media/Units. Source/Asset layer phân biệt chúng; resume portability giữa formats không được assumed.

### 4.7.7 Rejected Alternatives

#### Alternative A — Every EPUB is always a `MediaUnit`

Rejected vì format/package không phải domain identity. Standalone book sẽ lại cần parent Media + fake Unit giống vấn đề Q-DOM-002.

#### Alternative B — Every spine/TOC chapter becomes a `MediaUnit`

Rejected vì:

- spine item != semantic chapter;
- một chapter có thể span nhiều resources;
- một resource có thể chứa nhiều TOC chapters;
- reflow/navigation structure là publication implementation detail;
- sẽ làm metadata/source mapping phụ thuộc file internals.

#### Alternative C — EPUB file itself is canonical Media identity

Rejected vì rename/move/alternate format/revision sẽ làm identity phụ thuộc representation.

#### Alternative D — Persist reader-engine `Publication` object as domain truth

Rejected vì engine/runtime model có lifecycle/versioning khác canonical app state. Domain chỉ persist app-owned identity/mapping/progress semantics; publication object được reconstruct từ resolved asset.

#### Alternative E — Persist only percentage and bỏ locator

Rejected làm default vì percentage không đủ để resume chính xác trong reflowable/structured publication. Exact dual representation sẽ do Progress audit quyết.

### 4.7.8 Downstream Impact

Q-DOM-003 mở khóa Stage B với các assumptions:

```text
EPUB package/file = Asset/representation.
Runtime Publication = reader/consumption model, not canonical entity by default.
Internal spine/resources/TOC nodes != automatic MediaUnits.
Standalone publication may use MediaTarget.
Series book/volume may use UnitTarget when canonical semantics justify it.
Progress ownership remains canonical target-level.
Locator is reader-position data, not identity.
Locator portability across representations is NOT guaranteed.
```

Stage A (`Q-DOM-001..003`) có một coherent provisional canonical shape. `Q-SRC-001` sau đó đã xác lập source/binding/representation/runtime-resolution boundary mà không làm contradiction với publication model.

### 4.7.9 Verification Ideas

Khi implementation tới publication slice, cần test ít nhất:

1. Standalone EPUB open/resume không cần fake Unit.
2. Light-novel series volumes có Unit identity độc lập với file URI.
3. 100 spine resources không tạo 100 canonical Units.
4. TOC restructure không tự mutate canonical Unit graph.
5. Reader restart restores persisted locator for same compatible publication asset.
6. Reflow/font/orientation change không phụ thuộc durable screen page number.
7. Replacing EPUB asset không blindly apply old locator nếu compatibility chưa được xác nhận.
8. Same canonical book có multiple representations mà không duplicate Media identity.
9. Reader engine object có thể destroy/recreate từ Asset mà canonical state không mất.

### 4.7.10 Remaining Risks / New Questions

Q-DOM-003 đóng publication **identity boundary**, nhưng cố ý để mở:

- durable Asset identity/revision/local locator details (`Q-STO-001`, `Q-ID-001`, `Q-REC-001`);
- asset identity/revision evidence (`Q-ID-001` / `Q-REC-001`);
- exact DB/engine serialization của publication locator sau typed semantics đã khóa ở `Q-PROG-001`;
- typed resume-anchor portability/migration across revision/source/format (`Q-PROG-002`);
- exact reader engine choice và adapter boundary (Pre-Development deferred decision).

Không question nào ở trên yêu cầu biến spine/chapter resources thành canonical Units.

### 4.7.11 Research References

Primary/specification references:

- W3C EPUB 3.3: https://www.w3.org/TR/epub-33/
- W3C EPUB Reading Systems 3.3: https://www.w3.org/TR/epub-rs-33/
- Readium Kotlin — Getting started / Publication + Asset: https://readium.org/kotlin-toolkit/latest/guides/getting-started/
- Readium Kotlin — Navigator / reading progression: https://github.com/readium/kotlin-toolkit/blob/develop/docs/guides/navigator/navigator.md
- Readium Kotlin — Locator API: https://readium.org/kotlin-toolkit/latest/api/readium/readium-shared/org.readium.r2.shared.publication/-locator/
- Readium Web Publication Manifest: https://readium.org/webpub-manifest/

Mature project evidence:

- calibre — one book, multiple formats: https://manual.calibre-ebook.com/metadata.html
- Komga — Read progress: https://komga.org/docs/guides/read-progress/
- Komga — WebPub/Positions/Progression APIs: https://komga.org/docs/openapi/web-pub-manifest/
- Komga — EPUB progression endpoint: https://komga.org/docs/openapi/update-book-progression/
- Komga ↔ KOReader progress behavior: https://komga.org/docs/guides/koreader/
- Foliate — locations / EPUB CFI: https://github.com/johnfactotum/foliate/blob/gtk4/docs/faq.md
- KOReader project: https://github.com/koreader/koreader
- Kavita EPUB-specific resume field: https://github.com/Kareadita/Kavita/blob/develop/Kavita.Models/DTOs/Progress/ProgressDto.cs

Research note: project-specific storage/progress models above are evidence of practical boundaries, not templates to copy. W3C/Readium semantics receive higher weight for EPUB structure itself.


### 4.7.12 Stage A Closure Summary

Stage A không khóa implementation types/schema, nhưng đã đạt **coherent provisional canonical shape**:

```text
Media
├── optional MediaGrouping
└── optional MediaUnit(s) when canonical subdivision exists

ConsumptionTargetRef
├── MediaTarget(MediaId)
└── UnitTarget(UnitId)

Source/representation details
do not define canonical identity

EPUB
package/file → representation/Asset
runtime Publication → reader model
spine/TOC/resources → internal navigation/rendering structure
Locator → resume position, not canonical identity
```

**Stage A status:** `PROVISIONAL BASELINE / COMPLETE FOR NEXT AUDIT STAGE`

**Historical Stage A closure handoff:** later stages A–F were completed PROVISIONAL. R4.14 subsequently completed `Q-PER-001`, `Q-MOD-001` and `Q-API-001`; R4.15 then completed `Q-BOOT-001`. R4.16 has generated the skeleton; current work is executable bootstrap verification (official wrapper + JDK 17 + SDK 37 + Gradle/Android/CI evidence).


## 4.8 Decision Record — Q-SRC-001: Source / SourceBinding / Asset / ResolvedContent

**Status:** `PROVISIONAL`

Q-SRC-001 là câu cross-media đầu tiên của Stage B. Research được thực hiện trên video/provider systems, manga source systems, publication/book systems và Android playback consumer boundary để tránh copy một architecture family duy nhất.

### 4.8.1 Problem Statement

Foundation cần một contract chịu được đồng thời:

```text
Movie / episode
→ local video file
→ downloaded representation
→ future online stream / transcode

Manga chapter
→ CBZ / image folder
→ future provider chapter
→ page URLs có thể resolve lazily

Publication
→ local EPUB
→ alternate format / downloaded package
→ runtime Publication opened from representation
```

Nếu `Source`, mapping, file/stream và runtime player input đều là một object:

- canonical state sẽ chứa expiring URLs/tokens;
- Player/Reader sẽ phụ thuộc provider/storage DTO;
- local/download/online sẽ cần ba contract khác nhau;
- source switching và retry sẽ khó tách selection khỏi access;
- persistence schema sẽ bị transport details kéo lệch.

Ngược lại, nếu chia quá nhiều entity bắt buộc thì V1 local-first sẽ bị over-engineer.

### 4.8.2 Research Findings

#### Jellyfin — library item vs media source vs playback result

Jellyfin `MediaSourceInfo` chứa representation/playback-specific state như `Id`, `Path`, protocol, container, bitrate, required HTTP headers, direct-play/direct-stream/transcoding support và `TranscodingUrl`. Playback logic có thể open source rồi quyết định direct play hay transcode theo client/runtime conditions.

**Observed fact:** playable representation và runtime playback method không phải cùng thing với canonical library item identity.

**Inference:** app của ta không nên persist runtime transcode URL/session information vào canonical source mapping.

References:

- https://github.com/jellyfin/jellyfin/blob/master/MediaBrowser.Model/Dto/MediaSourceInfo.cs
- https://github.com/jellyfin/jellyfin/blob/master/Jellyfin.Api/Helpers/MediaInfoHelper.cs

#### Stremio — addon/source capability vs resolved stream object

Stremio addon manifest có stable addon `id` và khai báo resources/types. Khi client request `stream` cho một content ID, addon trả runtime `Stream` objects; stream có thể là direct `url`, torrent `infoHash`, external URL, request proxy headers và behavior hints.

**Observed fact:** provider/addon identity/capability tách khỏi stream object mà consumption dùng.

**Inference:** `Source` nên là configured provider capability; runtime stream descriptor phù hợp hơn với `ResolvedContent` chứ không phải SourceBinding truth.

References:

- https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md
- https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md

#### Mihon/Tachiyomi source family — source-native chapter reference vs lazily resolved pages

Mihon source API mô tả source như interface có thể là online hoặc local. Extension guidance khuyến nghị manga/chapter `url` giữ source-native ID/slug; khi chapter được mở, `getPageList(chapter)` trả page list, và nếu image URL chưa biết thì `getImageUrl(page)` resolve lazily.

**Observed fact:** source-native chapter reference có thể sống bền hơn image URLs được resolve khi đọc.

**Inference:** `SourceBinding` có thể giữ source-native reference nhưng không nên persist page/transport resolution như binding identity.

References:

- https://git.bubbletea.moe/mirror/mihon/commit/4c37f4c764afc47e0be6c63b1389b54f240b03f5?files=source-api
- https://github.com/yuzono/tachiyomi-extensions/blob/main/CONTRIBUTING.md

#### Readium — representation Asset vs opened runtime Publication

Readium `AssetRetriever` obtains an `Asset` representing a file/package; `PublicationOpener` builds a runtime `Publication` from that Asset. `Resource`/Container abstractions handle actual access, including local files, HTTP and Android Content Providers.

**Observed fact:** a stable-ish representation/package is distinct from the runtime object used by the reader.

**Inference:** publication file/package may be an Asset representation, while opened Publication belongs in `ResolvedContent`/reader runtime rather than persisted canonical state.

References:

- https://readium.org/kotlin-toolkit/latest/guides/getting-started/
- https://readium.org/kotlin-toolkit/3.1.2/guides/open-publication/

#### calibre — one canonical book can have multiple durable formats

calibre allows one Book entry to contain several formats, e.g. EPUB and MOBI.

**Observed fact:** multiple durable representations do not require multiple canonical book identities.

**Inference:** Asset/representation multiplicity must remain below canonical Media/Unit identity.

Reference:

- https://manual.calibre-ebook.com/metadata.html

#### Komga — content item/file is trackable but file identity remains representation-level

Komga exposes a Book separately from its downloadable file and can compute file hashes for duplicate detection/move/restore workflows. Read progress remains attached to Book rather than file hash.

**Observed fact:** file evidence/representation can be tracked independently from user-facing reading/progress identity.

**Inference:** durable local Asset records can be useful, but they must not own canonical progress identity.

References:

- https://komga.org/docs/openapi/books/
- https://komga.org/docs/guides/libraries/
- https://komga.org/docs/guides/duplicate-files/

#### Android Media3 — consumer wants a playback descriptor, not provider model

Media3 `MediaItem.LocalConfiguration` centers playback on runtime values such as URI, mime type, DRM/subtitle/stream configuration. ExoPlayer consumes a `MediaItem` created from a URI/manifest, independent of how a provider was discovered/mapped.

**Observed fact:** Android playback boundary naturally consumes a resolved playback configuration.

**Inference:** mapping/provider objects should be adapted before crossing into Player.

References:

- https://developer.android.com/reference/androidx/media3/common/MediaItem.LocalConfiguration
- https://developer.android.com/media/media3/exoplayer/hls

### 4.8.3 Provisional Decision

V1 adopts four semantic roles:

```text
Source
= configured capability/origin instance

SourceBinding
= canonical target ↔ source-native content relation

Asset
= optional stable representation concept

ResolvedContent
= ephemeral runtime-openable result
```

#### A. `Source`

A Source is not "a URL" or "a file". It is the configured capability/origin that knows how to discover/resolve content.

Semantics:

- has stable app-visible/source identity scope;
- may carry language/config/account/capabilities;
- local/download/provider are Source categories/instances;
- may be unavailable/disabled while bindings remain known;
- implementation object lifetime is runtime detail;
- authentication secrets/session tokens are not SourceBinding identity.

For V1, a single logical Local Content Source may span multiple `StorageRoot`s. `StorageRoot` remains an Android access/discovery boundary, not Source identity.

#### B. `SourceBinding`

A binding relates exactly one canonical consumption target to exactly one Source plus source-native reference/mapping evidence.

```text
Binding = ConsumptionTargetRef + SourceId + SourceNativeReference + binding metadata
```

Exact field/schema is intentionally not locked.

Binding properties:

- stable/reconstructable enough to re-resolve later;
- survives runtime process death;
- may survive temporary source/storage unavailability;
- does not directly contain canonical title/progress truth;
- does not persist expiring URL/cookie/token/transcoding session as its identity;
- rematch/remove/rebind remains possible;
- many bindings per target are allowed.

#### C. `Asset`

Asset is a representation-level concept and **optional semantic node**, not a mandatory universal entity.

Use it when a representation has meaningful durable lifecycle/identity the app needs to track, especially:

- local files/packages;
- downloaded files/packages;
- provider-native release/file representations when genuinely stable/useful.

Do not force an Asset row/entity for every online stream response. A provider can resolve a binding directly to runtime content when no durable representation identity exists.

One binding may own/reference multiple durable Assets; multiple representations do not create extra Media/Units.

#### D. `ResolvedContent`

ResolvedContent is ephemeral and typed by consumption family. It is what the Player/Reader adapter consumes.

```text
SourceBinding (+ optional Asset/intent/capabilities)
        ↓ Source resolution
ResolvedContent
        ↓ adapter
Media3 / Manga Reader / Publication Reader
```

It may contain runtime-sensitive access data such as:

- local/content URI;
- HTTP URL;
- request headers/cookies;
- short-lived signed tokens;
- transcoding/live-session URL;
- selected stream variant;
- lazy page resolver;
- publication access handle/context;
- subtitles/sidecar runtime descriptors;
- expiry/revalidation hints.

It must be reconstructable/re-resolvable and is not persisted as canonical truth.

### 4.8.4 Source Selection vs Resolution

These are separate operations:

```text
available bindings
      ↓ policy/user choice
selected SourceBinding
      ↓ runtime resolution
ResolvedContent
```

Selection answers *which source association should be used?* Resolution answers *what can the consumption engine open now?*

This split is required even if V1 initially has only Local Source and selection is trivial.

### 4.8.5 Cardinality and Representation Multiplicity

Provisional cardinality:

```text
ConsumptionTargetRef 1
      ↓
      0..N SourceBinding

SourceBinding 1
      ↓
      0..N durable Asset representations when meaningful
      ↓ resolve
      1 runtime ResolvedContent result
          which may expose N runtime variants/resources
```

Examples:

- Movie → Local binding → 1080p MKV + 4K MKV Assets → resolver chooses/exposes candidates.
- Movie → Provider binding → current stream request returns 720p/1080p streams; those may exist only in ResolvedContent and need no persistent Asset entities.
- Manga chapter → Provider binding → chapter source key → runtime ordered pages; image URLs may resolve lazily.
- Standalone book → Local binding → EPUB + alternate durable format Assets → publication resolution for selected representation.

Exact V1 policy for choosing among multiple local representations can be simple and may be designed with the first consumption slice; it must not redefine canonical identity.

### 4.8.6 Availability and Failure Semantics

Binding existence and current availability are distinct:

```text
known binding
≠ currently resolvable
≠ currently readable/playable
```

Resolution failures must eventually be typed enough to distinguish at least:

- access/permission failure;
- authentication required/expired;
- source unavailable/offline;
- source-native item not found;
- representation unsupported;
- transient network/runtime failure.

Exact Kotlin error hierarchy is deferred; upper layers must not infer these states by parsing exception strings.

### 4.8.7 Rejected Alternatives

#### Alternative A — `Source` is just an enum (`LOCAL`, `ONLINE`, `DOWNLOADED`)

Rejected as the full model because future providers/config/language/account instances need independent identity/capabilities. A source kind enum may still exist as metadata.

#### Alternative B — `SourceBinding` stores final playable URL/file and Player opens it directly

Rejected because remote URLs/headers/sessions can expire, playback may need runtime capability negotiation/transcoding, and manga pages can resolve lazily.

#### Alternative C — Every resolved stream/page/publication becomes a persistent Asset entity

Rejected because many online/runtime resources are ephemeral. Persistence must be justified by durable representation identity, not by the fact that a runtime resource exists.

#### Alternative D — No Asset concept; only Binding → ResolvedContent

Rejected as a universal rule because local/downloaded files/packages have durable lifecycle, availability, hashing/revision and reconciliation needs that should not be hidden inside ephemeral resolution.

#### Alternative E — Player/Reader receives provider DTO/database entity and resolves internally

Rejected because it couples consumption engines to source/storage implementation and defeats V2 unified-source goal.

### 4.8.8 Downstream Impact

Q-SRC-001 unlocks these assumptions for Stage B/C:

```text
Source identity/configuration
is separate from StorageRoot and representation.

SourceBinding
is the durable/reconstructable mapping boundary.

Asset
is optional representation-level state, not canonical identity.

ResolvedContent
is runtime-only consumption input.
```

It also narrows the next audits:

- `Q-STO-001` decides how Local Source uses StorageRoot/SAF/MediaStore and which local representations deserve durable Asset state;
- `Q-ID-001` decides what evidence recognizes the same Asset/Binding after rename/move;
- `Q-REC-001` decides what happens when binding/asset becomes unavailable/missing;
- `Q-PROG-002` defines when typed representation-specific resume anchors can be exactly reused, migrated or only approximately restored across Assets/sources/revisions;
- V2 later owns richer source-selection preference policy, not this V1 foundation question.

### 4.8.9 Verification Ideas

Future architecture/contract tests should prove at least:

1. Player can play local and remote resolved video without importing LocalSource/provider DTO types.
2. Manga Reader opens pages from local archive and provider-style lazy resolver through the same consumption-side boundary.
3. Publication Reader can recreate runtime Publication from resolved representation after process recreation.
4. Expired remote URL can be re-resolved without replacing canonical target or binding identity.
5. Removing an Asset does not automatically delete canonical Media/Unit or Progress.
6. Temporary Source/Storage unavailability preserves binding until reconciliation policy says otherwise.
7. Two representations of the same target do not create duplicate canonical identity.
8. Persisted database contains no runtime Media3/Readium objects or expiring transport session as canonical truth.
9. Source selection can change binding while Progress target remains unchanged.

### 4.8.10 Remaining Risks / Open Questions

Q-SRC-001 intentionally does **not** decide:

- exact Android local locator/grant/storage-root representation (`Q-STO-001`);
- exact Asset revision persistence shape and hash/fingerprint implementation thresholds; identity/reconciliation semantics are now constrained by `Q-ID-001` + `Q-REC-001`;
- scan completeness/absence-authority semantics are now constrained by `Q-SCN-001`; destructive retirement/removal remains a separate Q-REC/lifecycle concern;
- typed progress and cross-representation resume-anchor portability (`Q-PROG-001`, `Q-PROG-002`);
- final Kotlin interfaces/class names/persistence columns;
- V2 automatic source preference/ranking policy.

No remaining ambiguity above requires Player/Reader to depend on provider/storage DTOs.

### 4.8.11 Research References

Video/provider family:

- Jellyfin `MediaSourceInfo`: https://github.com/jellyfin/jellyfin/blob/master/MediaBrowser.Model/Dto/MediaSourceInfo.cs
- Jellyfin playback determination: https://github.com/jellyfin/jellyfin/blob/master/Jellyfin.Api/Helpers/MediaInfoHelper.cs
- Stremio addon manifest: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md
- Stremio Stream object: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md

Manga/comic family:

- Mihon current source API evolution: https://git.bubbletea.moe/mirror/mihon/commit/4c37f4c764afc47e0be6c63b1389b54f240b03f5?files=source-api
- Tachiyomi/Mihon extension chapter-page resolution guidance: https://github.com/yuzono/tachiyomi-extensions/blob/main/CONTRIBUTING.md
- Komga Books API: https://komga.org/docs/openapi/books/
- Komga library/file hashing: https://komga.org/docs/guides/libraries/

Publication/book family:

- Readium Asset/Resource/Publication: https://readium.org/kotlin-toolkit/latest/guides/getting-started/
- Readium opening a Publication from Asset: https://readium.org/kotlin-toolkit/3.1.2/guides/open-publication/
- calibre multiple formats per Book: https://manual.calibre-ebook.com/metadata.html

Android consumption boundary:

- Media3 `MediaItem.LocalConfiguration`: https://developer.android.com/reference/androidx/media3/common/MediaItem.LocalConfiguration
- Media3 HLS / MediaItem runtime playback: https://developer.android.com/media/media3/exoplayer/hls

Research note: names used by external projects are evidence of boundary separation, not type names to copy. In particular Readium's `Asset` is a toolkit runtime abstraction; this project's `Asset` remains a representation-level domain concept whose exact implementation is still provisional.

### 4.8.12 Stage B Partial Closure Summary

Stage B now has a provisional source-shape baseline:

```text
Configured Source
      ↓
SourceBinding
(target ↔ source-native reference)
      ↓
optional durable Asset representation(s)
      ↓ runtime resolve
ResolvedContent
      ↓
Player / Reader adapter
```

`Q-SRC-001` is **PROVISIONAL**. `Q-STO-001` below completes Stage B's storage/access half and validates that Local Source can span multiple roots without making root identity equal Source identity.

## 4.9 Decision Record — Q-STO-001: StorageRoot / SAF / MediaStore Responsibility

**Status:** `PROVISIONAL`

`Q-STO-001` is Android/platform-heavy rather than purely cross-media. Research therefore prioritizes official Android storage contracts and policy, then uses mature local-library apps to expose practical trade-offs across video and manga/publication workflows.

### 4.9.1 Problem Statement

V1 needs a local discovery model that can safely ingest:

```text
Video
→ MKV / MP4 / WebM

Manga / Comic
→ CBZ / ZIP
→ image directories

Publication
→ EPUB
```

while preserving user intent, Android privacy boundaries, removable-storage behavior and future source abstraction.

The key ambiguities were:

1. Is a `StorageRoot` a path/URI, an Android permission, a physical volume, or an app-owned registration?
2. Should V1 discover local content through SAF, MediaStore, broad filesystem access, or a hybrid?
3. What survives reboot, process death, permission loss, SD/USB removal and backup/restore?
4. Which platform references may be persisted without accidentally becoming canonical/Asset identity?
5. Can a single Local Source span multiple storage roots without duplicating Source identity?

### 4.9.2 Observed Platform Facts

#### Android Storage Access Framework

Official Android guidance establishes that `ACTION_OPEN_DOCUMENT_TREE` lets the user grant access to a selected directory tree and descendants. It is user-scoped access rather than broad storage permission.

Persistable URI permission can extend access across reboot, but Android explicitly warns that access is not retained if the associated document is moved or deleted. Therefore **persisted grant != permanent identity/availability guarantee**.

Android 11+ also restricts tree selection for storage roots, SD-card roots considered reliable, `Download`, `Android/data` and `Android/obb`. V1 UX cannot assume the user can authorize arbitrary device-root traversal.

DocumentsProvider `document_id` values are provider-owned opaque identifiers. Providers are expected to offer durable IDs for long-term grants, but rename may return a new document ID/URI. This makes document references useful locators/evidence, not app-owned identity.

#### MediaStore

MediaStore is an optimized system index for shared media collections. It is strong for indexed video/audio/images and exposes external volume names, but its view is not equivalent to an arbitrary filesystem tree.

For scoped-storage apps, access to other apps' media is collection/permission dependent, and non-media/document formats use different access paths. Android's own shared-storage overview points documents/books such as EPUB toward SAF rather than MediaStore.

#### Removable volumes

`StorageManager.getRecentStorageVolumes()` explicitly notes that recently available volumes may reappear and encourages apps to preserve indexed metadata. This supports treating temporary volume loss as availability loss, not deletion.

#### App-specific storage

App-specific internal/external files are appropriate for app-owned data and require no broad storage permission, but are removed on uninstall. External app-specific storage can itself become unavailable when removable media is removed. It therefore has different ownership/lifecycle semantics from user-owned local-library content.

#### All files access

`MANAGE_EXTERNAL_STORAGE` provides broad shared-storage access but Google Play treats it as high-risk and says media-file access is not a valid general use when MediaStore/SAF can serve the use case. V1 must not make its architecture depend on obtaining this permission.

### 4.9.3 Mature Project Evidence

#### Mihon — user-owned storage location

Mihon asks the user to select a storage location and keeps its Local Source under that authorized hierarchy. Its local source can contain folders, CBZ/ZIP and EPUB, showing that a user-selected root works for mixed sequential-image/publication content without device-wide file access.

#### VLC Android — roots/devices separate from media items

VLC's medialibrary exposes explicit discovery roots, removable devices, root reload/remove operations and discovery callbacks separately from media objects. This is useful evidence for keeping discovery-root/device lifecycle distinct from canonical/library media identity.

#### Nova Video Player — MediaStore-only limitations

Nova documents that moving to MediaStore under modern Android storage rules made some non-indexed/sidecar formats such as ASS/NFO invisible for local/USB workflows. This is strong negative evidence against using MediaStore as the universal observation layer for a mixed-format media app.

#### Kodi — broad access is a product/distribution trade-off, not a baseline

Kodi currently requests `MANAGE_EXTERNAL_STORAGE` and documents platform-specific local-file permission behavior. This demonstrates that broad file access can support media-center workflows, but Google Play policy makes it a reviewed/sensitive route. It is evidence of a trade-off, not a template for this project's default architecture.

### 4.9.4 Decision

#### A. `StorageRoot` is app-owned registration identity

`StorageRoot` is not a raw path, URI, permission grant or physical volume. It represents **user intent that the app should discover/manage local content within a particular authorized boundary**.

Conceptually:

```text
StorageRoot
= RootId owned by app
+ access strategy/descriptor
+ registration state
+ scan bookkeeping
+ last-known availability/access observations
```

`RootId` is stable under app control. Platform locators may change or become unavailable without forcing a new canonical media identity.

#### B. SAF tree is V1's primary universal user-root mechanism

For user-registered local-library folders, V1 defaults to SAF tree access because it:

- is explicitly user-authorized;
- handles arbitrary document/package types needed by video + CBZ/image-folder + EPUB workflows;
- avoids architecture dependence on broad all-files permission;
- naturally models a bounded discovery scope.

This is an architecture direction, not a claim that SAF traversal is always fastest.

#### C. MediaStore is supplemental, not authoritative universal storage

MediaStore may be used where it provides value:

- system-indexed video/audio/image discovery;
- volume-aware queries;
- optional acceleration/preflight for media collections;
- future UX that intentionally exposes system media collections.

But V1 must not define:

```text
local library == MediaStore rows
```

because the product supports formats and folder semantics outside MediaStore's complete authority.

#### D. App-specific storage is a separate managed-storage area

DB, caches, generated thumbnails and other app-owned mechanics belong in app-specific/internal storage as appropriate. Future app-managed downloads may also use managed storage policy, but this does not turn app-private directories into user local-library roots.

#### E. AccessGrant is capability state, not root identity

A SAF URI grant or runtime media permission answers **whether the app can access something now**, not **which root the user registered**.

Therefore:

```text
StorageRoot exists
      ≠
AccessGrant currently valid
```

A root may remain registered as unavailable/access-lost and later be reauthorized.

#### F. Root locator and item locators are evidence

For SAF:

- tree URI;
- provider authority;
- tree/document ID;
- child content URI;

may be persisted as access descriptors/current locators when useful.

For MediaStore:

- volume name;
- item content URI / row reference;

may likewise be persisted as current platform locators/evidence.

None of these become canonical `MediaId`/`UnitId`, and `Q-ID-001` still decides whether/how they participate in durable Asset recognition after rename/move/reindex.

### 4.9.5 Root/Source/Asset Relationship

Q-STO-001 validates Q-SRC-001 rather than reopening it:

```text
LocalContentSource
      │
      ├── StorageRoot A (SAF tree)
      ├── StorageRoot B (SAF tree / removable)
      └── optional MediaStore discovery surface

StorageRoot
      ↓ observe
local representation evidence
      ↓ reconcile
SourceBinding
      ↓
optional durable Asset
      ↓ resolve
ResolvedContent
```

One logical Local Source may span multiple roots. Adding/removing a root does not create/remove Source identity.

### 4.9.6 Persistence Semantics — What May Be Durable

Foundation-level persistence intent:

**App-owned durable state may include:**

- internal `RootId`;
- root registration/enabled state;
- access strategy kind;
- current serialized access descriptor/locator needed to attempt re-open;
- user-facing label/hint;
- last attempted/successful scan timestamps/generation references;
- last-known availability/access observation and diagnostic reason.

**Must be revalidated, never trusted as current truth:**

- URI permission still held;
- provider reachable;
- volume mounted;
- read/write capability;
- tree/document still exists.

**Must not become root/canonical identity:**

- raw filesystem path;
- SAF URI;
- DocumentsProvider document ID;
- MediaStore row ID;
- storage volume name;
- permission bit/grant token.

Exact columns/types remain blocked until persistence design; this record only locks ownership/semantics.

### 4.9.7 Failure / Availability Semantics

At the storage boundary, these observations must remain distinguishable enough for later reconciliation:

```text
AVAILABLE
temporarily unavailable / volume absent
ACCESS_LOST / grant revoked or invalid
provider unavailable
root not found / descriptor stale
unsupported root/provider capability
```

Q-REC-001 subsequently resolves this into separate access/scope, Asset-presence and lifecycle semantics rather than one universal `AvailabilityState` enum. Q-STO-001 already locks this rule:

> Failure to access a registered root is not deletion evidence for its previously indexed media.

On removable storage, app-owned root metadata and indexed media metadata should survive temporary absence so a later reappearance can reconcile against prior state.

### 4.9.8 Rejected Alternatives

#### Alternative A — Path/URI itself is StorageRoot identity

Rejected because platform locators are provider/filesystem-owned, can become stale/change, and cannot represent user registration intent independently of current access.

#### Alternative B — MediaStore-only local library

Rejected because MediaStore is optimized for indexed media collections but does not provide universal authority over CBZ/ZIP/EPUB/folder/sidecar workflows.

#### Alternative C — SAF-only for every storage operation including app-owned cache/database

Rejected because app-specific storage has simpler and more appropriate ownership/lifecycle for internal mechanics. SAF is primarily the user-authorized shared-content boundary.

#### Alternative D — `MANAGE_EXTERNAL_STORAGE` as required baseline

Rejected because it is broad/high-risk, distribution-policy constrained, unnecessary for the core architecture and contrary to least-privilege defaults.

#### Alternative E — Losing grant deletes StorageRoot and media

Rejected because permission/availability is transient platform state, while root registration and user library state are app-owned durable intent.

### 4.9.9 Downstream Impact

Q-STO-001 unlocks these assumptions for Stage C:

```text
RootId is app-owned.
Platform locators/grants are evidence/capability, not identity.
SAF tree is primary V1 universal root access.
MediaStore is supplemental discovery/indexing.
Temporary root/volume/grant loss preserves prior indexed state.
Local Source may span multiple roots.
```

It narrows following questions:

- `Q-ID-001`: which evidence recognizes the same local Asset/Binding after URI/path/MediaStore changes;
- `Q-REC-001`: exact availability/missing/access-lost/removed transitions and reauthorization semantics;
- `Q-SCN-001`: when a SAF/MediaStore observation is complete enough for absence evidence;
- `Q-RUN-001` subsequently defines persistent scan ownership, restart/retry/cancellation and WorkManager orchestration; exact traversal batching/threading remains benchmark/technical design;
- `Q-BACK-001`: backup may preserve root metadata/hints but cannot restore Android grants.

### 4.9.10 Verification Ideas

Future tests should prove at least:

1. Registering the same authorized tree through normal app flow creates/reuses app root semantics without making tree URI a Media identity.
2. Device reboot retains access when persistable grant remains valid, but code revalidates instead of assuming.
3. Revoked SAF permission marks root inaccessible without deleting canonical media/progress/library state.
4. Removable SD/USB absence preserves indexed state and root registration.
5. Reappearance/reauthorization can reconnect to prior root/reconciliation flow without re-creating all canonical identities by default.
6. Video discovered through MediaStore and the same video observed under an authorized tree do not automatically become two canonical items; Identity/Reconciliation owns deduplication.
7. CBZ/EPUB under an authorized tree remain discoverable even though MediaStore is not their universal index.
8. App-specific DB/cache remains functional without treating its directory as user library root.
9. Backup/restore does not claim a restored URI grant before user reauthorization.
10. V1 runs without `MANAGE_EXTERNAL_STORAGE`.

### 4.9.11 Remaining Risks / Open Questions

Q-STO-001 intentionally does **not** decide:

- stable Asset evidence/fingerprint/hash policy (`Q-ID-001`);
- whether rename/move should retain Asset identity and how to reconnect stale locators (`Q-REC-001`);
- complete vs partial scan authority across SAF/MediaStore (`Q-SCN-001`);
- exact performance strategy for large SAF trees after Q-RUN ownership semantics (benchmark work);
- exact production `StorageRootEntity` columns/classes;
- whether future V2 downloads use app-specific external, shared storage or user-selected destination;
- support guarantees for cloud-backed DocumentsProvider roots; V1's product guarantee remains local/offline media.

No remaining ambiguity requires treating permission, URI or volume identity as canonical media identity.

### 4.9.12 Research References

Primary Android/platform references:

- Android — Access documents and other files / SAF tree + persistable grants: https://developer.android.com/training/data-storage/shared/documents-files
- Android — Android 11 storage/SAF restrictions: https://developer.android.com/about/versions/11/privacy/storage
- Android — Shared storage overview: https://developer.android.com/training/data-storage/shared
- Android — MediaStore shared media access and storage volumes: https://developer.android.com/training/data-storage/shared/media
- Android — DocumentsContract / provider-owned document IDs: https://developer.android.com/reference/android/provider/DocumentsContract.Document
- Android — StorageManager recent/removable volumes: https://developer.android.com/reference/android/os/storage/StorageManager
- Android — App-specific storage lifecycle: https://developer.android.com/training/data-storage/app-specific
- Android — All files access: https://developer.android.com/training/data-storage/manage-all-files
- Google Play — MANAGE_EXTERNAL_STORAGE policy: https://support.google.com/googleplay/android-developer/answer/10467955

Mature project evidence:

- Mihon storage location: https://mihon.app/docs/faq/storage
- Mihon local source: https://mihon.app/docs/guides/local-source/
- VLC Android medialibrary roots/devices/discovery: https://github.com/videolan/vlc-android/blob/master/medialibrary/jni/AndroidMediaLibrary.h
- VLC Android discovery implementation: https://github.com/videolan/vlc-android/blob/master/medialibrary/jni/AndroidMediaLibrary.cpp
- Nova Video Player MediaStore limitations: https://github.com/nova-video-player/aos-AVP/blob/nova/faq/faq.md
- Kodi Android manifest/broad storage permission: https://github.com/xbmc/xbmc/blob/master/tools/android/packaging/xbmc/AndroidManifest.xml.in

### 4.9.13 Stage B Closure Summary

Stage B now has a coherent provisional source/storage shape:

```text
Configured Local Source
      ↓ spans
StorageRoot(s) with app-owned RootId
      ↓ access via
SAF tree (primary universal V1 path)
+ optional MediaStore indexed discovery
      ↓ observe/reconcile
SourceBinding
      ↓
optional durable Asset
      ↓ resolve
ResolvedContent
```

`Q-SRC-001` and `Q-STO-001` are **PROVISIONAL** and mutually consistent.

**Stage B status:** `PROVISIONAL BASELINE / COMPLETE FOR NEXT AUDIT STAGE`

**Historical handoff from Q-STO-001:** `Q-ID-001` was the next blocker at Stage B closure. Stage C (`Q-ID-001`, `Q-REC-001`, `Q-SCN-001`) is now completed at **PROVISIONAL** level. Stage D has now completed `Q-PROG-001`, `Q-PROG-002`, `Q-HIST-001` and `Q-LIB-001`; Q-META-001 is now **PROVISIONAL**; Q-BACK-001 is now **PROVISIONAL**; next work is deep persistence/module/API design.

## 4.10 Decision Record — Q-ID-001: Identity Evidence Without Evidence-as-Identity

**Status:** `PROVISIONAL`

Q-ID-001 mở Stage C bằng cách xác định **evidence nào được phép dùng để nhận ra một canonical Media/Unit hoặc representation Asset đã tồn tại** sau rescan, rename, move, replace hay re-index — nhưng không biến path, URI, hash, filename hoặc provider/platform ID thành primary identity của app.

### 4.10.1 Problem Statement

V1 phải giữ được user state khi local content thay đổi location hoặc platform locator, đồng thời tránh auto-merge sai khi hai file chỉ tình cờ giống nhau.

Các tình huống tối thiểu:

```text
A. Rename
/movie/Frieren 01.mkv
→ /movie/Frieren - S01E01.mkv

B. Move
/rootA/Manga/Series/ch01.cbz
→ /rootB/Archive/Series/ch01.cbz

C. Copy / duplicate
/rootA/book.epub
AND
/rootB/book-copy.epub

D. Replace in place
same locator/path
old bytes → new encode / corrected file

E. SAF/provider change
same logical document
but returned document URI/documentId may change

F. MediaStore re-index/store reset
cached row/URI observations need resync
```

Nếu path/URI được dùng làm identity, rename/move có thể làm mất progress/library relation. Nếu hash được dùng làm identity, hai copies byte-identical có thể bị merge sai. Nếu filename/title được dùng làm identity, những media cùng tên hoặc naming convention thay đổi sẽ gây duplicate/merge sai.

### 4.10.2 Observed Platform Facts

#### Android DocumentsProvider / SAF

Android yêu cầu `DocumentsProvider` trả `COLUMN_DOCUMENT_ID` **unique trong provider và durable** vì ID này được dùng cho long-term URI grants. Tuy nhiên ID là **opaque provider-owned value**, không phải app-owned identity.

Quan trọng hơn, Android contract cho phép `renameDocument()` tạo **một document ID mới** nếu provider cần; URI mới được trả về và URI cũ có thể không còn valid. Vì vậy:

```text
Durable document ID
!= immutable forever
!= app AssetId
```

Document URI/document ID là evidence mạnh trong scope của provider/root hiện tại, nhưng không được làm primary key cho canonical state.

#### MediaStore

MediaStore `_ID` là row identity trong MediaStore và content URI dùng `_ID`, nhưng Android yêu cầu client cache/index phải kiểm tra `MediaStore.getVersion()`. Khi version thay đổi đáng kể, app phải xem generation state là reset và thực hiện full resync. `GENERATION_MODIFIED` hữu ích cho incremental observation, không phải canonical identity.

Vì vậy MediaStore `(volume, _ID)` là **platform observation locator/evidence**, không phải app-owned Media/Unit/Asset identity.

### 4.10.3 Mature Project Evidence

#### Jellyfin — path-centric continuity exposes rename risk

Jellyfin scanner architecture has used/path-matched item identity in scanner flows. Real-world issues in 2025 report rename/replace causing watched state to reset. Đây là evidence trực tiếp rằng path-only matching có thể làm user state continuity phụ thuộc filesystem naming.

Bài học cho dự án này:

```text
path is useful evidence
but path must not own progress/canonical identity
```

#### Komga — hash useful for move/restore/duplicates, but optional because expensive

Komga hỗ trợ optional file hashing. Documentation ghi hashing cần cho duplicate-file detection và trash-bin/library-move restoration, đồng thời cảnh báo hashing tiêu tốn resources trên large library/slow hardware.

Komga cũng dùng same file hash để tìm duplicate files. Điều này chứng minh content hash là evidence rất mạnh cho **byte/content equivalence**, nhưng cũng chứng minh nó có cost và không nên mặc định trở thành universal primary identity.

#### Kavita — semantic transitions cannot be solved by hashing alone

Kavita hiện có behavior giữ progress trong một số filename rename nhỏ, trong khi volume/chapter/series structure change cần special handling. Maintainer explicitly notes rằng một số transitions không cần hashing mà cần logic hiểu semantic structure.

Bài học: representation continuity và canonical Media/Unit identity là hai bài toán khác nhau.

#### calibre — app-owned logical book identity separate from formats/files

calibre dùng internal `book_id` trong database để tham chiếu logical book; các formats được add/remove/replace dưới book đó. Calibre paths thậm chí chứa database id trong managed library layout.

Bài học:

```text
logical identity is app/database-owned
file/format is attached representation
```

#### Syncthing — hashes are powerful evidence, but hashing is real work

Syncthing hashes files bằng SHA-256/block hashes và có thể xử lý rename/update hiệu quả mà không retransmit toàn bộ file. Documentation cũng nêu hashing là một nguyên nhân đáng kể gây CPU usage, đặc biệt trên low-powered devices.

Syncthing là sync system nên correctness trade-off khác app media, nhưng nó cho bằng chứng tốt rằng content fingerprinting mạnh và đắt — phù hợp làm selective evidence thay vì mandatory identity strategy trên Android media client.

### 4.10.4 Decision — Identity Is App-Owned, Evidence Is Typed and Scoped

V1 giữ nguyên nguyên tắc:

```text
MediaId
UnitId
AssetId
BindingId
RootId

= app-owned identity
```

External/platform observations chỉ là `IdentityEvidence` ở mức khái niệm. Đây **chưa phải final Kotlin type/table**.

Evidence phải có ít nhất ba properties về semantics:

```text
scope      — evidence đang nói về Media / Unit / Asset / Binding / locator nào
provenance — đến từ Android provider, filesystem observation, embedded metadata,
             external metadata, parser hay user confirmation
strength   — evidence continuity mạnh/yếu trong context nào
```

Không có một evidence đơn lẻ nào mặc định được promote thành internal ID.

### 4.10.5 Evidence Families

#### A. App-owned linkage evidence — strongest continuity evidence

Ví dụ:

```text
existing AssetId ↔ current observed locator alias
existing SourceBinding ↔ source-native reference
explicit user-confirmed Media/Unit mapping
```

Nếu app đã có explicit relation của chính nó và observation hiện tại vẫn thỏa constraints, đây là evidence continuity mạnh nhất.

Tuy nhiên relation cũ vẫn có thể bị invalidated bởi replacement/reclassification; `Q-REC-001` quyết lifecycle action, không phải Q-ID-001.

#### B. Platform locator evidence

Ví dụ:

```text
StorageRootId + SAF provider/documentId/tree relation
StorageRootId + current Document URI
MediaStore volume + row _ID
current content URI
relative path within registered root
```

Properties:

- nhanh và rẻ để kiểm tra;
- thường rất hữu ích cho incremental scans;
- scoped vào provider/store/root;
- có thể thay đổi sau rename/move/re-index;
- same locator không chứng minh bytes chưa bị replace.

Do đó locator evidence **không phải content identity**.

#### C. Content-equivalence evidence

Ví dụ:

```text
full-file cryptographic hash
strong content fingerprint
archive/page fingerprint
size + selected-content fingerprint
```

Rule:

```text
same strong hash
→ strong evidence that bytes/content are equivalent

same strong hash
!= automatically same AssetId
!= automatically same MediaId/UnitId
```

Hai file byte-identical tồn tại đồng thời ở hai roots có thể là **hai physical representations/copies**. Hash chỉ cho thấy equivalence, không tự quyết move/copy/duplicate ownership.

#### D. Semantic identity evidence

Dùng để recognize **Media/Unit meaning**, không phải physical file identity:

```text
user-confirmed mapping
trusted embedded identifier
external provider ID / ISBN / other identifiers
parent canonical MediaId
season/episode/chapter/volume coordinates
title + year + media kind
author/creator
embedded ComicInfo/EPUB/video metadata
```

External IDs và embedded identifiers vẫn chỉ là evidence/mapping; chúng không thay internal `MediaId`/`UnitId`.

#### E. Structural / temporal supporting evidence

Ví dụ:

```text
filename
relative path
parent folder/group context
extension / MIME
size
mtime
video duration
page count
archive entry structure
EPUB package characteristics
```

Những evidence này hữu ích để narrow candidates và tránh hashing không cần thiết, nhưng không đủ cho ambiguous auto-merge nếu đứng một mình.

#### F. Conflict / negative evidence

Identity matching không chỉ gom evidence “giống nhau”. Nó phải có khả năng reject candidate khi có contradiction rõ, ví dụ:

```text
same filename but incompatible media kind
same title but incompatible episode/chapter coordinate
same locator but file was replaced with structurally unrelated content
same hash candidate while original still exists as another representation
```

Exact conflict rules do Q-REC-001 và parser/domain slice cụ thể hóa.

### 4.10.6 Conservative Matching Model — No Global Numeric Score in V1

V1 **không tạo một fuzzy global confidence engine** hoặc numeric resolver score.

Conceptual result classes đủ cho foundation:

```text
EXACT_APP_LINK
STRONG_CONTINUITY_CANDIDATE
SEMANTIC_CANDIDATE
AMBIGUOUS
CONFLICT
```

Tên enum chưa locked; đây là decision shape.

Rules:

1. Existing app-owned relation được ưu tiên khi vẫn valid.
2. Platform locator match là strong continuity evidence nhưng có thể cần replacement/revision validation.
3. Locator thay đổi thì matcher có thể kết hợp strong content evidence + semantic/context evidence.
4. Một candidate duy nhất với evidence mạnh có thể được chuyển cho reconciliation như move/rename candidate.
5. Nhiều candidates hợp lý → `AMBIGUOUS`; không auto-merge.
6. Conflict rõ → không reuse identity chỉ vì một evidence khác match.
7. User-confirmed mapping có authority cao cho canonical semantic relation, nhưng không làm hai physical copies biến thành cùng Asset instance.

### 4.10.7 Hashing Policy — Adaptive, Not Mandatory Everywhere

Q-ID-001 **reject** chiến lược “full hash toàn library trước khi identity hoạt động”.

V1 direction:

```text
cheap evidence first
        ↓
strong locator / existing relation?
        ↓ yes
avoid unnecessary full hash

ambiguous / moved / restore / duplicate case?
        ↓ yes
compute stronger fingerprint/hash when useful
```

Full-file/strong hashing có thể được:

- tính lazy khi reconciliation cần;
- tính background sau ingestion cho assets đáng quan tâm;
- dùng cho duplicate detection / move restoration;
- cache với provenance/revision evidence để không rehash vô ích.

Exact hash algorithm, sampling strategy và cache invalidation **chưa khóa**; chúng là implementation/performance decision sau semantics + benchmark.

### 4.10.8 Move vs Copy vs Replace — Evidence Does Not Decide the Action Alone

Q-ID-001 chỉ cung cấp evidence; `Q-REC-001` quyết state transition.

Nhưng foundation khóa các invariants sau:

```text
old asset still observable
+ new byte-equivalent candidate appears
→ cannot assume move
→ likely separate copy/duplicate representation candidate

old asset disappears
+ exactly one strong equivalent candidate appears
→ eligible move/rename continuity candidate
→ Q-REC-001 decides reattach policy

same locator remains
+ content materially changes
→ locator continuity does not prove same revision
→ Q-REC-001 decides replace/revision semantics
```

Đây là lý do `hash == identity` và `path == identity` đều bị reject.

### 4.10.9 Media/Unit Matching Is Not Asset Matching

Một Asset có thể được recognize/re-attached mà canonical classification vẫn cần review, và ngược lại nhiều Assets có thể map tới cùng canonical target.

```text
Asset continuity evidence
!= canonical semantic identity evidence
```

Ví dụ:

```text
same episode, new encode
→ likely new/revised Asset representation
→ same Unit target

same bytes copied to another root
→ separate physical observation/representation possible
→ may still map to same Media/Unit

filename changed from Ch.12 to Vol.2 Ch.12
→ bytes may be same
→ Unit meaning may still be Chapter 12
→ grouping metadata can change without Asset hash deciding domain identity
```

Therefore:

- content hash cannot create Media/Unit identity;
- parsed title/episode/chapter cannot create Asset identity;
- reconciliation composes both evidence scopes.

### 4.10.10 Persistence Semantics

R4.14 `Q-PER-001` now provides the production persistence baseline; the durable identity/evidence matrix below remains a required invariant:

**Persist as truth:**

- app-owned internal IDs;
- app-owned relations (`SourceBinding`, Library/Progress target refs, etc.);
- selected evidence/provenance cần cho future reconciliation;
- current locator plus optionally prior locator aliases/history nếu later reconciliation design chứng minh cần.

**Persist as evidence/cache, not identity:**

- document/provider locators;
- MediaStore locators;
- hashes/fingerprints;
- size/mtime/duration/page count;
- parsed semantic observations;
- embedded/external IDs.

**Never assume from persistence alone:**

- locator vẫn reachable;
- hash cache vẫn valid sau replacement nếu revision evidence thay đổi;
- external identifier luôn đúng;
- one matching filename means same object.

### 4.10.11 Rejected Alternatives

#### Alternative A — Path/URI/documentId is Asset identity

Rejected: Android provider IDs/URIs có lifecycle riêng; rename có thể yield ID mới; path reuse/replacement tồn tại; Jellyfin rename regressions cho thấy user state continuity dễ bị mất khi identity quá path-centric.

#### Alternative B — Full content hash is AssetId

Rejected: identical copies có thể cùng tồn tại; hash nói content equivalence chứ không nói physical representation identity; hashing toàn library tốn I/O/CPU.

#### Alternative C — Filename/parsed title is canonical identity

Rejected: rename, localization, release naming, ambiguous titles và parser changes sẽ mutate identity.

#### Alternative D — Provider/embedded external ID is primary MediaId

Rejected: external IDs là mapping/evidence, có thể missing/wrong/change; canonical identity vẫn do app sở hữu.

#### Alternative E — Global numeric fuzzy score in V1

Rejected: tạo complexity và false-merge risk trước khi use cases đủ rõ. V1 dùng conservative evidence classes + ambiguity handling.

### 4.10.12 Downstream Impact

Q-ID-001 unlocks assumptions cho Q-REC-001/Q-SCN-001:

```text
Internal IDs own identity.
Observations produce typed evidence.
Locator and hash are evidence, not identity.
Media/Unit matching and Asset matching are distinct scopes.
Ambiguous candidates do not auto-merge.
Hashing is adaptive/optional, not mandatory baseline.
```

Q-REC-001 đã giải quyết ở mức PROVISIONAL các vấn đề trên bằng Asset-lineage/revision semantics, recoverable `MISSING`, explicit retirement/removal authority, conservative move/copy/replacement rules và manual reconciliation scopes.

Q-SCN-001 tiếp theo quyết observation completeness/absence authority — tức khi nào scanner được phép cung cấp `authoritative absence evidence` cho các transition đã khóa ở Q-REC-001.

### 4.10.13 Verification Ideas

Future tests phải bao phủ ít nhất:

1. Rename file không tạo canonical Media/Unit mới khi continuity evidence đủ và reconciliation cho phép.
2. Move giữa folders/root không mất Progress/Library relation nếu move được recognize.
3. Hai identical copies cùng tồn tại không bị collapse thành một physical Asset chỉ vì hash giống nhau.
4. Same path/URI bị replace bằng unrelated bytes không blindly reuse old representation revision.
5. SAF rename trả documentId/URI mới vẫn có thể trở thành continuity candidate.
6. MediaStore version reset không làm MediaStore row ID trở thành canonical identity.
7. Filename/title-only collision không auto-merge hai Media khác nhau.
8. External provider ID hỗ trợ semantic match nhưng internal MediaId không thay đổi khi mapping sửa/xóa.
9. Hashing không chạy bắt buộc cho mọi unchanged asset trong normal rescan path.
10. Ambiguous move candidates không silently merge; reconciliation exposes unresolved state/manual path.
11. Same episode với encode mới giữ Unit identity nhưng representation lifecycle được xử lý riêng.
12. Parser/grouping metadata change không mutate internal IDs nếu canonical semantics không đổi.

### 4.10.14 Remaining Risks / Open Questions

Q-ID-001 originally left the following to Q-REC-001/Q-SCN-001. Q-REC-001 has now resolved the **semantics** for `AssetRevision`, reattachment, recoverable missing/restoration, replacement-vs-new-Asset and manual reconciliation scopes. Still deferred are:

- exact hash/fingerprint algorithm and sampling thresholds;
- exact locator alias/history retention shape;
- exact tombstone/garbage-collection retention policy;
- structural split/merge transitions (loose chapters → volume package, etc.);
- scan completeness required before disappearance becomes meaningful (`Q-SCN-001`).

Không ambiguity nào ở trên yêu cầu path/URI/hash trở thành app-owned identity.

### 4.10.15 Research References

Android primary/platform references:

- `DocumentsContract.Document.COLUMN_DOCUMENT_ID` durable/opaque ID: https://developer.android.com/reference/android/provider/DocumentsContract.Document
- `DocumentsContract.renameDocument()` may return a new document ID/URI: https://developer.android.com/reference/android/provider/DocumentsContract
- `DocumentsProvider.renameDocument()` and permission update semantics: https://developer.android.com/reference/android/provider/DocumentsProvider
- MediaStore version/generation synchronization contract: https://developer.android.com/reference/android/provider/MediaStore
- MediaStore shared-media synchronization guidance: https://developer.android.com/training/data-storage/shared/media

Mature project/library evidence:

- Jellyfin scanner refactoring discussion / path-matching architecture: https://github.com/jellyfin/jellyfin-meta/discussions/125
- Jellyfin rename watched-state regression: https://github.com/jellyfin/jellyfin/issues/15389
- Jellyfin replace/rename watched-state issue: https://github.com/jellyfin/jellyfin/issues/15001
- Komga duplicate-file hashing: https://komga.org/docs/guides/duplicate-files/
- Komga library hashing / trash restoration requirements: https://komga.org/docs/guides/libraries/
- Komga file-move/trash design history: https://github.com/gotson/komga/issues/217
- Kavita rename/change-detection discussion: https://github.com/Kareadita/Kavita/discussions/4283
- Kavita scanner dirty-checking notes: https://github.com/Kareadita/Kavita/issues/3785
- calibre database API (`book_id`, formats attached to logical book): https://manual.calibre-ebook.com/db_api.html
- calibre logical book URLs / internal book id: https://manual.calibre-ebook.com/template_lang.html
- Syncthing synchronization/block hashing: https://docs.syncthing.net/users/syncing
- Syncthing FAQ on hashing CPU cost and rename behavior: https://docs.syncthing.net/users/faq.html

### 4.10.16 Stage C Partial Closure Summary

Stage C now has a provisional evidence model:

```text
Observed local content
      ↓
collect scoped evidence
├── app-owned linkage
├── platform locator
├── content fingerprint/hash
├── semantic metadata/coordinates
└── structural/supporting facts
      ↓
conservative candidate classification
      ↓
Q-REC-001 decides lifecycle/reconciliation action (now PROVISIONAL)
```

`Q-ID-001` is **PROVISIONAL**. It validates Stage A/B rather than reopening them: canonical IDs remain app-owned, `SourceBinding` remains app-owned relation, `StorageRoot` remains access/discovery registration, and Assets remain representation-level concepts.

**Historical handoff:** Q-SCN-001 was the next blocker after Q-ID-001/Q-REC-001 and is now completed at **PROVISIONAL** level. Stage D has now completed `Q-PROG-001`, `Q-PROG-002`, `Q-HIST-001` and `Q-LIB-001`; `Q-META-001`, `Q-RUN-001` and `Q-BACK-001` are now **PROVISIONAL**. Stages A–F are complete for deep persistence/module/API design.

## 4.11 Decision Record — Q-REC-001: Reconciliation State Transitions

**Status:** `PROVISIONAL`

Q-REC-001 tiếp tục Stage C sau Q-ID-001. Q-ID-001 đã quyết evidence nào được phép tham gia recognition; Q-REC-001 quyết **reconciliation action nào được phép xảy ra khi evidence thay đổi**. Record này tập trung vào rename, move, copy, replace, missing, temporary unavailability, access loss, explicit removal và restoration.

Q-REC-001 **không tự định nghĩa một scan khi nào complete/authoritative**. Nó chỉ định nghĩa precondition dạng `authoritative absence evidence`; `Q-SCN-001` tiếp theo phải quyết một ScanRun nào được phép tạo evidence đó.

### 4.11.1 Problem Statement

V1 phải giữ user state đúng qua các thay đổi storage bình thường mà không biến mọi absence thành deletion hoặc mọi content-equivalence thành cùng một physical Asset.

Representative cases:

```text
A. Rename in place
/root/Anime/Frieren 01.mkv
→ /root/Anime/Frieren - S01E01.mkv

B. Move inside one root
/root/Manga/Series/ch01.cbz
→ /root/Manga/Archive/Series/ch01.cbz

C. Move across registered roots
/rootA/Books/book.epub
→ /rootB/Books/book.epub

D. Copy / duplicate
/rootA/book.epub
AND
/rootB/book-copy.epub

E. Replace in place with same semantic target
/root/Movie.mkv
old encode → new encode

F. Replace locator with unrelated content
/root/Movie.mkv
old movie → different movie

G. Root temporarily unavailable
SD / USB removed, DocumentsProvider offline

H. Access lost
persisted SAF grant revoked/stale

I. External delete
asset no longer exists when an authoritative observation is eventually possible

J. Partial/failed scan
asset not observed because traversal did not complete
```

Các outcomes trên không được collapse thành một generic `missing=true` hoặc một destructive delete path.

### 4.11.2 Primary Android / Platform Findings

#### SAF rename may change the locator itself

Android `DocumentsContract.renameDocument()` explicitly allows the underlying provider to create a **new document ID/URI** for the renamed document; in that case the old document reference is no longer valid.

**Observed fact:** rename does not guarantee locator continuity.

**Inference:** rename continuity must belong to app reconciliation/Asset identity, not URI identity.

#### Move/delete/remove are distinct provider operations

`DocumentsContract` distinguishes:

```text
renameDocument → same logical document may return a new URI
moveDocument   → move under another parent
removeDocument → remove one parent relation
                  (document may have multiple parents)
deleteDocument → delete the document
```

**Observed fact:** Android itself distinguishes parent membership from document deletion, and one provider-native document may be reachable through more than one parent relation.

**Inference:** multiple observed locators/parents do not automatically prove multiple physical Assets; provider-native alias evidence must be allowed.

#### Persisted access is not permanent availability

Android's SAF guide warns that even a persistable URI permission does not retain access if the associated document is moved or deleted.

**Observed fact:** a previously valid locator can fail because access/locator semantics changed.

**Inference:** a failed open cannot by itself distinguish `ACCESS_LOST`, moved/stale locator or actual deletion.

#### MediaStore version/generation are observation aids, not identity

`MediaStore.getVersion()` is an opaque state version for detecting substantial index changes. Android says that if the overall version changes, generation counters should be considered reset and clients should perform a full synchronization pass. A version query may also return `null` when a requested volume is not mounted. `GENERATION_MODIFIED` is useful for incremental change detection only within a stable overall version.

**Inference:** MediaStore re-index/reset or volume absence must not be translated directly into per-Asset deletion.

### 4.11.3 Mature Project Evidence

Research intentionally compares different architecture families rather than copying one media server.

#### Jellyfin — destructive/path-sensitive reconciliation can leak into user state

Recent Jellyfin regressions report watched state being reset when an episode is renamed/replaced, and folder rename flows have historically caused content/artwork to be removed/re-added.

**Observed fact:** rename/replacement can accidentally cross the boundary between representation identity and user progress when scanner identity is too path-sensitive.

**Inference:** V1 must preserve canonical progress/library state independently of Asset locator churn.

#### Komga — soft-unavailable/trash before permanent removal

Komga places missing/unavailable library items into trash rather than immediately discarding them. A file becoming available again can restore the item; file hashing is used to restore data across file/folder rename or move. Komga explicitly warns that automatically emptying trash after every scan removes the restoration window and prevents moves between libraries.

**Observed fact:** delayed destructive cleanup materially improves move/rename/restoration behavior and protects read progress/metadata.

**Inference:** authoritative absence should first produce a recoverable `MISSING` representation state, not hard removal.

#### Kavita — representation continuity and semantic restructuring are different problems

Kavita maintainers note that minor filename changes need not lose progress, while transitions that change volume/chapter/series structure require semantic code rather than hashing alone.

**Observed fact:** byte/file continuity cannot decide every canonical restructuring operation.

**Inference:** Q-REC must keep Asset reconciliation separate from Media/Unit reclassification. Structural split/merge remains an explicit domain migration problem.

#### calibre — logical book remains separate from format lifecycle

calibre maintains database `book_id` separately from formats. A format can be added/replaced for an existing book, formats/books can be moved to trash and restored, and format hashes exist independently from logical book identity.

**Observed fact:** representation replacement/trash does not require replacing the logical book identity.

**Inference:** an Asset/AssetRevision lifecycle can change while canonical Media/Unit and user state remain stable.

#### Syncthing — content evidence helps rename efficiency, but conflict policy stays conservative

Syncthing hashes file blocks and can handle file renames without retransmitting the whole file. It still distinguishes conflicting changes and does not assume that every equal-looking event has one obvious user-intended winner; modification-vs-deletion conflicts are handled conservatively.

**Observed fact:** strong content evidence is useful, but lifecycle action still requires policy/context.

**Inference:** V1 should use hashes/fingerprints as evidence while keeping reconciliation outcomes conservative and explicit.

### 4.11.4 Decision — Do Not Model Reconciliation as One State Enum

Q-REC-001 rejects a single enum that mixes access, observation and lifecycle.

Conceptually, reconciliation has at least three orthogonal dimensions:

```text
1. Access/scope state
   Can the app currently observe/resolve the registered boundary?

2. Asset presence observation
   Was this representation observed, authoritatively absent, or not proven either way?

3. Asset lifecycle disposition
   Is this representation lineage still tracked, or was it explicitly retired/removed?
```

Exact Kotlin enum/type names are not locked. The semantic distinctions are.

### 4.11.5 Access / Scope Semantics

Representative access states:

```text
ACCESSIBLE
TEMPORARILY_UNAVAILABLE   // volume/provider/root unavailable
ACCESS_LOST               // grant revoked/invalid/stale
OUT_OF_SCOPE              // root disabled/unregistered or no longer observed by user intent
```

Rules:

- `TEMPORARILY_UNAVAILABLE` does **not** imply any child Asset is `MISSING`.
- `ACCESS_LOST` does **not** imply any child Asset is `MISSING` or removed.
- `OUT_OF_SCOPE` means the app is no longer observing that boundary by configuration/user intent; it is not evidence that files were deleted.
- Root-level access state should be allowed to determine effective availability for many Assets **without requiring destructive per-Asset transitions or mass row rewriting**.
- Reauthorization of the same logical registered root may restore access under the same `RootId` when the explicit flow/evidence supports that relation.

### 4.11.6 Asset Presence Semantics

At representation level, V1 needs these distinctions:

```text
PRESENT
MISSING
UNCONFIRMED / LAST_KNOWN
```

The exact persisted shape is deferred.

#### `PRESENT`

The representation was successfully observed under the current valid observation context.

#### `MISSING`

`MISSING` means:

```text
previously-known Asset
+ its relevant scope was accessible
+ Q-SCN-001 says the observation was authoritative for that Asset
+ the representation was absent
```

`MISSING` is **recoverable**. It is not hard deletion.

#### `UNCONFIRMED / LAST_KNOWN`

A partial/failed/cancelled scan, transient provider error or a one-off failed open does not provide enough evidence to move `PRESENT → MISSING`.

The app may record diagnostics/staleness, but it must preserve the last-known durable association until authoritative evidence exists.

### 4.11.7 `MISSING` Preserves Canonical and User State

When an Asset becomes `MISSING`, V1 preserves by default:

```text
AssetId
SourceBinding / BindingId
canonical MediaId / UnitId relation
Library state
Progress
History
user metadata / mappings
selected reconciliation evidence needed for restoration
```

A local Binding may therefore be known but currently unresolvable.

Rule:

```text
Asset missing
!= Binding removed
!= Media/Unit deleted
!= LibraryEntry removed
!= Progress deleted
```

`Q-LIB-001` subsequently locks the complementary rule: Library membership is durable Media-level user/app state, and storage absence is insufficient to mutate it destructively.

### 4.11.8 `REMOVED` / `RETIRED` Is an Explicit Lifecycle Decision

Q-REC distinguishes `MISSING` from representation removal.

A representation can become `REMOVED/RETIRED` only from an explicit lifecycle decision, for example:

- user explicitly asks the app to forget/remove that representation;
- the app initiates a delete operation and receives successful platform/provider confirmation;
- a future cleanup/retention policy explicitly retires old missing representation records.

Rules:

- External disappearance discovered by scan first becomes `MISSING`, not automatically `REMOVED`.
- Time elapsed alone is not an identity rule. A missing representation does not become a different identity merely because it has been absent for N days.
- Exact tombstone retention/garbage-collection window is a later persistence/maintenance policy. It must not silently delete canonical user state.
- Removing an Asset does not imply removing its canonical target or LibraryEntry.

### 4.11.9 Asset Is a Representation Lineage; `AssetRevision` Is Required Semantics

Q-REC-001 resolves the open Q-ID-001 question about replacement/revision by introducing **Asset revision semantics**.

```text
AssetId
= stable identity of one durable representation lineage

AssetRevision
= materially different content incarnation inside that lineage
```

`AssetRevision` is **not** a canonical Media/Unit identity and does not need to be a first-class table/entity. Production persistence may represent it as an opaque revision token, ordinal or equivalent revision marker.

Rules:

- locator-only rename/move with unchanged representation → same `AssetId`, same revision;
- in-place replacement/re-encode/correction that is strongly established as continuation of the same representation lineage and canonical target → same `AssetId`, **new revision**;
- same locator reused for semantically unrelated content → **new `AssetId`**; old Asset lineage does not silently mutate into another work/unit;
- concurrent alternate encodes/copies are separate Assets, not revisions of one Asset;
- a revision change may invalidate representation-specific caches/analysis/locator compatibility even though canonical Progress ownership remains unchanged.

Exact revision detection strategy is implementation/performance work; Q-ID-001's adaptive evidence policy remains in force.

### 4.11.10 Rename / Move Reconciliation

#### A. App-mediated rename/move

If the app invokes a supported provider operation and receives a successful returned locator, that operation result is direct continuity evidence.

Default action:

```text
same AssetId
same AssetRevision unless content changed
update current locator/access descriptor
retain old locator only as stale/previous evidence when useful
keep BindingId + canonical target + Progress
```

The returned URI may differ from the original; this does not create a new Asset by itself.

#### B. Externally observed rename/move

A previously known Asset may be reattached to a new locator when:

```text
old representation has authoritative absence evidence
+ exactly one strong continuity candidate exists
+ evidence scopes are compatible
+ no conflict/contradiction exists
```

Then:

```text
reuse AssetId
preserve BindingId/canonical target
update locator/root association
preserve revision if content-equivalent
or create new AssetRevision if same lineage but content changed
```

Move across `StorageRoot`s is allowed to preserve Asset identity because one logical Local Source can span multiple roots.

#### C. Ambiguous move

If multiple strong candidates exist or conflict evidence remains:

```text
do not reuse old AssetId automatically
preserve old Asset as missing/last-known
persist/retain unresolved candidate evidence as implementation permits
require later evidence or manual reconciliation
```

No fuzzy score is allowed to silently choose a winner.

### 4.11.11 Copy / Duplicate Semantics

When the old representation is still authoritatively observed and another byte/content-equivalent representation appears:

```text
old Asset remains
new physical representation → new AssetId
```

They may map to the same canonical target and may sit under the same Local `SourceBinding` if that mapping is clear.

Exception: if provider-native evidence proves that two locators/parents are aliases to the **same underlying provider document**, they may remain one Asset with multiple locator/parent observations. `removeDocument()` semantics are explicit evidence that multi-parent documents can exist.

Therefore:

```text
same hash != same AssetId
multiple locators != automatically multiple AssetIds
```

Reconciliation uses provider/app linkage + content + structural evidence together.

### 4.11.12 Replacement at the Same Locator

Same locator continuity is insufficient by itself.

#### Case 1 — material changes, same representation lineage

Example:

```text
Episode 01.mkv
old encode → corrected/new encode
old representation no longer coexists
semantic target continuity is strong
```

Action:

```text
same AssetId
new AssetRevision
same UnitTarget / Binding
canonical Progress remains owned by UnitTarget
```

Representation-specific resume compatibility is **not assumed**. `Q-PROG-002` defines cross-media anchor reuse/migration/fallback for video time, image page and publication locator semantics.

#### Case 2 — locator reused for unrelated content

If evidence conflicts with the previous target:

```text
old Asset lineage ends/is superseded
new representation gets new AssetId
no automatic inheritance of old Asset-specific evidence
```

Canonical old Media/Unit/Progress remain unless separate domain/user policy removes them.

#### Case 3 — uncertain replacement

If evidence cannot establish same-lineage vs unrelated replacement:

- do not blindly reuse the old Asset identity;
- do not silently transfer canonical mapping based on path alone;
- preserve an unresolved reconciliation path until stronger evidence or user confirmation exists.

### 4.11.13 Restoration Semantics

A previously `MISSING` Asset can become present again.

If restoration evidence identifies the same representation lineage:

```text
MISSING AssetId
+ matching representation appears
→ same AssetId restored
```

- Same content-equivalent representation → revision can remain unchanged.
- Same lineage but material representation changed → new AssetRevision.
- If the old representation had been explicitly `RETIRED/REMOVED`, reappearance is not automatically allowed to resurrect it; it is treated as a new/reconciliation candidate unless the user explicitly restores/relinks it.

Identity restoration eligibility is not automatically expired by wall-clock age. A later garbage-collection policy may prune tombstone/evidence state, but that policy must be explicit.

### 4.11.14 SourceBinding Lifecycle During Reconciliation

For V1 Local Source:

- file rename/move does not create a new Binding merely because locator/root changed;
- Asset revision does not create a new Binding;
- adding another local representation/copy does not require another Binding if it is the same target ↔ Local Source association;
- temporary root/source unavailability does not remove Binding;
- one missing Asset does not remove Binding;
- Binding is removed/rebound only when the **target ↔ Source association itself** is intentionally changed/forgotten or becomes semantically invalid.

Thus:

```text
locator lifecycle
  below
Asset lineage/revision
  below
SourceBinding association
  below
canonical target identity
```

Changes at a lower layer do not automatically recreate higher-layer identity.

### 4.11.15 Root Unregistration / Reauthorization

Unregistering or disabling a `StorageRoot` means the user no longer wants that boundary actively observed.

Default semantics:

```text
Root registration → OUT_OF_SCOPE / disabled
previous Assets → preserve last-known identity/evidence
not automatically MISSING
not automatically RETIRED
```

A separate explicit “forget data from this root” action may later retire representation records, but it must not be conflated with unregistering access.

If the user reauthorizes the same logical root through an explicit reauthorization flow and evidence is compatible, the existing `RootId` and prior Assets remain eligible for restoration/reconciliation.

### 4.11.16 Direct Resolution Failure Is Not Deletion Evidence

Player/Reader/opening code may encounter:

- `FileNotFoundException`;
- stale URI;
- permission/access denial;
- unmounted storage;
- provider transient failure.

A runtime resolution failure should:

```text
produce typed failure
mark/schedule reconciliation need when appropriate
NOT hard-delete Asset/Binding/Media/Progress
NOT by itself transition Asset to MISSING
```

Only Q-SCN-001-authorized absence observation (or explicit app-mediated destructive operation) may produce the corresponding lifecycle transition.

### 4.11.17 Manual Reconciliation Semantics

Manual correction must respect identity scopes rather than expose a generic “merge everything” action.

At minimum, future UX/use cases must be able to express semantically different actions such as:

1. **Same representation moved/renamed** → reattach old `AssetId` to new locator.
2. **Separate representation of same target** → keep distinct `AssetId`, attach/map to same canonical target/binding.
3. **Candidate belongs to a different target** → bind/reclassify without mutating old canonical identity/progress.
4. **Forget/retire representation or binding** → explicit lifecycle removal without implicitly deleting Media/Progress/Library.

User confirmation has high authority, but it does not erase the difference between representation identity and canonical target identity.

### 4.11.18 Locator Alias / History Semantics

Q-REC does not require unbounded path/URI history.

It locks these rules:

- one current locator/access descriptor may be treated as the preferred attempt point;
- prior locators may be retained as scoped reconciliation evidence/diagnostic aliases;
- stale locators are never canonical identity and should not be blindly opened as current truth;
- exact alias retention count/time and DB shape are deferred to persistence design/benchmark needs.

### 4.11.19 State Transition Matrix

Conceptual transitions:

| Observation / action | Asset identity | Revision | Presence/access outcome | Binding/canonical user state |
|---|---|---|---|---|
| successful observation, unchanged | same | same | PRESENT | preserve |
| app-mediated rename/move | same | same unless content changed | PRESENT at new locator | preserve |
| unique strong external move candidate + authoritative old absence | same | same/new revision depending content | PRESENT at new locator | preserve |
| old + equivalent new representation both present | old + **new AssetId** | independent | both PRESENT | may share target/binding |
| root/volume/provider unavailable | same | same | effective unavailable at scope | preserve |
| access grant lost | same | same | ACCESS_LOST at scope | preserve |
| partial/failed scan does not observe Asset | same | same | last-known / unconfirmed | preserve |
| authoritative absence | same | same | MISSING | preserve |
| missing representation returns | same when continuity proven | same/new | PRESENT | preserve |
| same locator, same lineage, material content replacement | same | **new revision** | PRESENT | preserve canonical target |
| same locator reused for unrelated content | **new AssetId** | new | new Asset PRESENT; old lineage superseded/missing/retired by policy | old canonical state preserved |
| explicit successful delete/forget | may retain tombstone/history record | n/a | RETIRED/REMOVED | no automatic canonical cascade |
| ambiguous candidate | no auto-reuse | unchanged until resolved | unresolved | preserve old state |

Names in the table are semantic labels, not final persistence enums.

### 4.11.20 Rejected Alternatives

#### Alternative A — Any absent file is immediately deleted from DB

Rejected because temporary mount/provider/access failures and partial scans would destroy user state; it also prevents reliable move/restore detection.

#### Alternative B — One `AvailabilityState` enum owns access + missing + deletion + user removal

Rejected because these states have different authorities, scopes and recovery paths. Root access failure is not per-Asset absence; user removal is not an observation.

#### Alternative C — Same hash means move / same Asset

Rejected because identical copies can coexist. Hash proves content equivalence, not physical representation lineage.

#### Alternative D — Same path/URI means same Asset forever

Rejected because locator can be reused for unrelated bytes/content and Android providers may change IDs/URIs on rename.

#### Alternative E — Every content change creates a new AssetId

Rejected as a universal rule because a representation lineage can legitimately be replaced/re-encoded in place while remaining the same source association. `AssetRevision` captures content-incarnation change without forcing identity churn.

#### Alternative F — Any same-target representation is one Asset with revisions

Rejected because alternate encodes/copies can coexist simultaneously. Coexisting representations need independent Asset identity even when they map to the same canonical target.

#### Alternative G — Missing Assets expire into new identity after a fixed number of days

Rejected as a semantic rule. Time alone does not prove a new representation identity. Cleanup/GC retention is a separate maintenance policy.

#### Alternative H — Removing an Asset cascades to Media/Progress/Library

Rejected because canonical/user state is owned above representation availability. Such a cascade would violate Q-DOM/Q-SRC/Progress/Library boundaries.

### 4.11.21 Downstream Impact

Q-REC-001 unlocks these assumptions:

```text
Access failure and asset absence are separate.
Authoritative absence first yields recoverable MISSING.
Removal/retirement requires explicit lifecycle authority.
Rename/move may preserve AssetId.
Copy creates another Asset unless provider proves locator aliasing.
AssetId represents a durable representation lineage.
Material same-lineage replacement produces AssetRevision change.
Binding/canonical/progress state survives representation churn.
Ambiguity never silently merges identities.
```

`Q-SCN-001` subsequently completed this sharply bounded job at PROVISIONAL level:

- defined complete/partial/failed/cancelled/interrupted ScanRun semantics without requiring final enum names;
- defined declared observation scope, coverage and absence authority;
- defined when absence evidence is authoritative enough for `PRESENT → MISSING`;
- defined SAF/MediaStore cross-adapter authority so supplemental observation cannot create false negative evidence.

Later questions consume Q-REC as follows:

- `Q-PROG-002` uses AssetRevision compatibility when evaluating typed resume-anchor reuse/migration/fallback;
- `Q-LIB-001` subsequently confirms MISSING/OUT_OF_SCOPE/ACCESS_LOST do not equal Library removal;
- persistence design must keep lifecycle/observation semantics without turning locators into identity;
- `Q-RUN-001` subsequently defines batching ownership/cancellation/retry/finalization mechanics without changing reconciliation meaning.

### 4.11.22 Verification Ideas

Future tests must prove at least:

1. SAF rename returning a new URI can retain the same AssetId/Binding/Progress.
2. Move within one root preserves AssetId when continuity evidence is unique and authoritative.
3. Move across two registered roots can preserve AssetId without changing Local Source identity.
4. Two byte-identical copies simultaneously present receive distinct AssetIds unless provider alias evidence proves one native document.
5. Root/SD removal never mass-transitions children to MISSING/REMOVED.
6. Revoked SAF grant preserves RootId, Assets, Binding and canonical user state.
7. Failed/partial scan cannot transition unseen Assets to MISSING.
8. Authoritative absence transitions Asset to recoverable MISSING without deleting Progress/History/Library.
9. Restoring a missing file can recover the same AssetId.
10. Same-locator re-encode for the same canonical target can retain AssetId while changing AssetRevision.
11. Same locator reused for unrelated content creates a new AssetId and does not inherit old canonical mapping automatically.
12. A runtime `FileNotFound` from Player/Reader does not hard-delete anything.
13. Manual “same representation moved” differs from “another representation of same target”.
14. Explicit Asset removal does not cascade to canonical Media/Unit/Progress/Library by default.
15. Locator alias/history can be pruned without changing app-owned identity.
16. Manga loose-chapter → volume structural transitions are not solved by Asset hash/revision alone and remain explicit canonical migration/reclassification work.
17. Asset revision signals Q-PROG-002 compatibility evaluation when a prior video/page/publication anchor may no longer be safe for blind restore.

### 4.11.23 Remaining Risks / Deferred Details

Q-REC-001 intentionally does **not** decide:

- exact ScanRun completeness algorithm (`Q-SCN-001`);
- final Kotlin enum/sealed-class names;
- exact Room schema/tables/foreign keys;
- exact fingerprint/hash algorithm or thresholds;
- exact locator alias retention duration/count;
- exact tombstone/garbage-collection policy;
- cross-media resume-anchor migration/compatibility (`Q-PROG-002`);
- canonical atomic ↔ subdivided or loose-chapter ↔ volume migration algorithm;
- exact persistence representation of the now-resolved Q-LIB membership/suppression semantics;
- exact Worker/class wiring, checkpoint cadence and runtime tuning under the now-resolved `Q-RUN-001` semantics.

None of these deferred details require destructive absence handling or evidence-as-identity.

### 4.11.24 ADR Requirement

No separate ADR is required for the Q-REC semantic decision itself at this stage because it extends the canonical foundation without choosing a platform/framework implementation.

An ADR may be required later if persistence chooses a costly irreversible tombstone/revision model, or if a platform-specific scanner architecture changes the guarantees above.

### 4.11.25 Research References

Android primary/platform sources:

- Android SAF documents/files + persistable URI access: https://developer.android.com/training/data-storage/shared/documents-files
- `DocumentsContract.renameDocument()`: https://developer.android.com/reference/android/provider/DocumentsContract#renameDocument(android.content.ContentResolver,android.net.Uri,java.lang.String)
- `DocumentsContract.moveDocument()`: https://developer.android.com/reference/android/provider/DocumentsContract#moveDocument(android.content.ContentResolver,android.net.Uri,android.net.Uri,android.net.Uri)
- `DocumentsContract.removeDocument()`: https://developer.android.com/reference/android/provider/DocumentsContract#removeDocument(android.content.ContentResolver,android.net.Uri,android.net.Uri)
- `DocumentsContract.deleteDocument()`: https://developer.android.com/reference/android/provider/DocumentsContract#deleteDocument(android.content.ContentResolver,android.net.Uri)
- Android DocumentsProvider supported management operations: https://developer.android.com/guide/topics/providers/create-document-provider
- `MediaStore.getVersion()`: https://developer.android.com/reference/android/provider/MediaStore#getVersion(android.content.Context,java.lang.String)
- `MediaStore.MediaColumns.GENERATION_MODIFIED`: https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#GENERATION_MODIFIED

Mature project/library evidence:

- Jellyfin watched-state rename/replace regression: https://github.com/jellyfin/jellyfin/issues/15001
- Jellyfin folder rename/re-add behavior: https://github.com/jellyfin/jellyfin/issues/12714
- Komga trash/restore semantics: https://komga.org/docs/guides/trash/
- Komga library hashing/performance: https://komga.org/docs/guides/libraries/
- Komga original trash/move design issue: https://github.com/gotson/komga/issues/217
- Kavita rename/semantic transition discussion: https://github.com/Kareadita/Kavita/discussions/4283
- calibre database API — book identity, format replacement, trash/restore/hash: https://manual.calibre-ebook.com/db_api.html
- Syncthing synchronization/block hashing/conflict semantics: https://docs.syncthing.net/users/syncing
- Syncthing FAQ — rename efficiency and folder-move caution: https://docs.syncthing.net/users/faq.html

Research note: Komga/calibre/Syncthing mechanisms are evidence of practical recovery/continuity patterns, not contracts to copy. The project decision remains local-first Android-specific and keeps Q-SCN-001 responsible for absence authority.

### 4.11.26 Stage C Reconciliation Closure Summary

Stage C now has two coherent provisional layers:

```text
Q-ID-001
observations → typed/scoped evidence → conservative candidates
        ↓
Q-REC-001
candidate + access/scan authority → lifecycle/reconciliation action
        ↓
Q-SCN-001
defines when declared scan coverage is authoritative enough
for absence evidence while keeping destructive lifecycle decisions separate
```

`Q-ID-001`, `Q-REC-001` and `Q-SCN-001` are **PROVISIONAL** and mutually consistent.

**Stage C current status:** `PROVISIONAL BASELINE / COMPLETE FOR USER-STATE AUDIT`

**Q-REC closure handoff (historical):** Q-SCN-001 was the next blocker after Q-ID-001/Q-REC-001 and is now completed at **PROVISIONAL** level. Stage D has now completed `Q-PROG-001`, `Q-PROG-002`, `Q-HIST-001` and `Q-LIB-001`; `Q-META-001`, `Q-RUN-001` and `Q-BACK-001` are now **PROVISIONAL**. Stages A–F are complete for deep persistence/module/API design.

## 4.12 Decision Record — Q-SCN-001: ScanRun Completeness / Coverage / Authoritative Absence

**Status:** `PROVISIONAL`

Q-SCN-001 completes Stage C by deciding **when an observation run is allowed to say that a previously-known representation was not present**. Q-ID-001 owns identity evidence; Q-REC-001 owns reconciliation transitions. Q-SCN-001 supplies the missing precondition: whether the scanner actually observed enough of the relevant scope for absence to become evidence.

The decision is intentionally about **observation semantics**, not WorkManager/coroutine scheduling, Room tables or a specific traversal algorithm. Those mechanics remain downstream.

### 4.12.1 Problem Statement

Positive evidence is naturally local:

```text
I successfully observed file/document X
→ X was present at observation time.
```

Negative evidence is fundamentally harder:

```text
I did not observe X
→ was X deleted?
→ was its directory skipped?
→ was the provider still loading?
→ did permission fail midway?
→ was the volume unmounted?
→ was this only an incremental scan that never intended to enumerate X?
→ did the process die before traversal completed?
```

V1 therefore needs to answer:

1. What exactly is a `ScanRun` observing?
2. What makes a run `COMPLETE`, `PARTIAL`, `FAILED`, `CANCELLED` or interrupted?
3. Can useful positive observations from an incomplete run still be kept?
4. At what scope may absence be considered authoritative?
5. How do SAF and MediaStore differ in completeness semantics?
6. What happens when filters/root configuration or another overlapping run changes while scanning?
7. How do we prevent a stale/partial run from mass-marking Assets missing?

Representative failure cases:

```text
A. SAF tree has 10,000 files; one directory query fails after 7,500 observations.
B. DocumentsProvider returns cached children with EXTRA_LOADING=true.
C. SD/USB disappears halfway through traversal.
D. Persisted grant is revoked during scan.
E. User cancels the run.
F. Android process dies after some observations were persisted.
G. MediaStore overall version changes during incremental synchronization.
H. Incremental MediaStore query returns no row for a known Asset.
I. Root/filter configuration changes while the old run is still executing.
J. Two overlapping scans of the same root finish out of order.
```

### 4.12.2 Primary Android / Platform Findings

#### SAF child enumeration is recursive and may be incomplete even without a thrown exception

`DocumentsProvider.queryChildDocuments()` returns **immediate descendants** of a directory; callers issue additional queries to recursively explore the tree.

For cloud-backed/cached providers, Android explicitly permits a provider to return locally cached child data immediately while setting `DocumentsContract.EXTRA_LOADING` on the returned Cursor. The provider can later notify clients to requery when complete data becomes available.

**Observed fact:** a successful child query can represent an **incomplete listing**.

**Inference:** `query succeeded` cannot mean `scan branch complete`. A branch with `EXTRA_LOADING=true` is not absence-authoritative until it is observed again in a complete state.

Android query APIs also support `CancellationSignal`, reinforcing that traversal is a cancellable operation whose termination must be distinguished from successful completion.

#### SAF/document operations do not provide a transactional tree snapshot

SAF exposes documents through provider queries and provider-owned semantics. The platform does not guarantee that a recursive multi-query traversal represents one atomic filesystem snapshot.

**Inference:** V1's `COMPLETE` means *the declared traversal/observation scope was fully enumerated according to the adapter contract*, not that the underlying storage was frozen for the entire run.

That limitation is acceptable because Q-REC-001 makes authoritative absence recoverable `MISSING`, not irreversible deletion.

#### MediaStore version and generation have explicit synchronization limits

Android documents `MediaStore.getGeneration()` as a monotonically increasing aid for identifying added/changed media. Before comparing detailed generation values, clients must verify that the overall `MediaStore.getVersion()` has not changed. If it changed, Android says generation values should be treated as reset and a **full synchronization pass** should occur.

`getVersion(context, volume)` can return `null` when the requested volume is not mounted.

**Observed facts:** MediaStore exposes an adapter epoch/version distinct from per-item generation; incremental generation queries are change detection, not a universal full-snapshot/deletion oracle.

**Inference:** absence cannot be inferred simply because an existing row did not appear in an incremental `GENERATION_MODIFIED` query. Version reset or unmounted volume invalidates blanket negative inference.

### 4.12.3 Mature Project Evidence

Research compares multiple architecture families rather than copying one scanner.

#### Syncthing — prove folder health before treating mass absence as deletion

Syncthing uses a `.stfolder` marker as a safety check. If the marker disappears — for example because a drive was unmounted — Syncthing stops syncing that folder rather than treating every missing child as deletion. Once the folder is healthy again, ordinary change/deletion processing resumes. Its scan API also distinguishes successful scan completion from scan error.

**Observed fact:** a system whose job includes propagating deletions still gates negative interpretation on scope health.

**Inference:** V1 should require root/scope observability before absence authority, rather than interpreting an unreadable scope as an empty one.

#### Jellyfin — complete on-disk picture is useful, but empty/mount failure is dangerous

Jellyfin scanner-refactoring discussion describes deletion candidates as database items not seen after building a complete on-disk picture, while explicitly refusing to prune when a library unexpectedly appears empty because a missing network mount looks like mass deletion.

Real-world Jellyfin issues also show the damage caused when scan failures and removal logic interact incorrectly.

**Inference:** negative reconciliation belongs after observation/finalization, not inline during recursive discovery.

#### Plex — unavailable is staged before destructive cleanup

Plex places missing/unavailable library items into trash and keeps them restorable by default. Automatic trash-emptying after every scan is optional and explicitly warned as removing the restoration safety window.

**Inference:** scan detection and destructive cleanup are separate lifecycle decisions. This is consistent with Q-REC-001's `MISSING` versus explicit retirement/removal split.

#### Komga — scan-time absence is recoverable and root outages are a known hazard

Komga likewise places moved/deleted/unavailable items in library trash so that temporary drive/network unavailability does not immediately discard metadata/progress. Hashing can later reconnect moved content.

**Inference:** a scan can legitimately produce recoverable negative observations without making them canonical/user-state deletion.

#### Kavita — scanner design explicitly needs an all-files-missing/inaccessible safeguard

Kavita scanner improvement work calls out aborting a scan when all files are missing / a drive is inaccessible.

**Inference:** large-scale negative observations require a health/completeness gate; an inaccessible root must not be interpreted as an authoritative empty library.

### 4.12.4 Decision — ScanRun Is an App-Owned Observation Attempt

Conceptually:

```text
ScanRun
├── RunId                  // app-owned provenance for this attempt
├── StorageRoot / adapter context
├── DeclaredScanScope
├── ObservationMode
├── scope/config revision snapshot
├── started/finalized timestamps
├── Outcome
├── Coverage / incomplete regions
└── diagnostics / adapter epoch evidence
```

Exact fields/classes are **not** locked.

Rules:

- `RunId` identifies an observation attempt, not Media/Unit/Asset identity.
- Every run has a **declared scope** before negative evidence can be interpreted.
- The scope/config snapshot used for comparison must not silently mutate underneath the run.
- Positive observations retain their own provenance/time even if the run later ends incomplete.
- Negative evidence is derived only after a run/coverage region is finalized as eligible for absence authority.

### 4.12.5 DeclaredScanScope Is Required

A scanner must know what it claimed to observe.

Representative scopes:

```text
Full SAF StorageRoot
SAF subtree under one registered root
Targeted known document/subtree recheck
MediaStore volume + collection selection
MediaStore incremental change window
```

Declared scope includes semantically relevant inclusion/exclusion configuration:

```text
root / subtree boundary
adapter family
supported media/type filters
explicit exclusions
scope/config revision
observation mode
```

Rule:

```text
not in declared scope
!= absent
```

If filters/root configuration changes, the old run cannot compare its unseen set against the new scope as though nothing changed. Items newly excluded become an `OUT_OF_SCOPE`/configuration concern, not `MISSING` evidence merely because the scanner stopped looking at them.

### 4.12.6 Observation Modes Have Different Negative Authority

V1 distinguishes semantics similar to:

```text
FULL_SNAPSHOT
TARGETED_SNAPSHOT
INCREMENTAL_DELTA
PROBE / REVALIDATE
```

Names are provisional.

#### Full snapshot

Attempts to enumerate the entire declared root/collection scope. It **may** produce absence evidence after authoritative finalization.

#### Targeted snapshot

Attempts to enumerate one explicit subtree/item scope. It may produce absence only inside that exact scope.

#### Incremental delta

Asks for added/modified/change candidates since a prior point. Success does **not** mean every unchanged item was observed.

Therefore:

```text
not returned by incremental query
!= absent
```

A delta adapter may produce negative evidence only if the underlying platform contract supplies an explicit trustworthy deletion/removal signal for that item; V1 does not assume MediaStore generation queries provide blanket deletion evidence.

#### Probe/revalidate

Checks access/existence/capability for a narrow target. It cannot be promoted into full-root absence authority.

### 4.12.7 Run Outcome and Coverage Are Separate Concepts

Foundation-level outcome classes:

```text
RUNNING
COMPLETE
PARTIAL
FAILED
CANCELLED
INTERRUPTED / ABANDONED   // e.g. process death before finalization
```

Exact enum names are not locked.

#### `COMPLETE`

The run finished all work required by its declared scope/mode without an unresolved branch that invalidates that scope's coverage.

For a full snapshot this requires, conceptually:

- root/scope access was valid;
- every included branch/query reached a terminal complete result;
- no relevant provider loading state remained unresolved;
- no branch-level access/IO/auth error left unknown descendants;
- the run was not cancelled/interrupted;
- scope/config was not superseded in a way that invalidates comparison;
- adapter epoch/version assumptions required by the adapter remained valid.

`COMPLETE` does **not** automatically mean “authoritative for every possible absence”; the observation mode and exact coverage still matter.

#### `PARTIAL`

Some useful observations were obtained, but at least one part of the declared scope remained incomplete/unknown.

Examples:

- one SAF subtree failed;
- a provider returned `EXTRA_LOADING` for one branch;
- a subset became inaccessible;
- adapter state changed so a full comparison cannot be trusted.

#### `FAILED`

The run could not establish useful trusted coverage for its intended operation, for example root access failed before meaningful traversal.

#### `CANCELLED`

Explicit cancellation terminated the run before normal finalization.

#### `INTERRUPTED / ABANDONED`

A run found in `RUNNING` state after process/runtime loss never gets silently upgraded to complete. On recovery it is treated as non-authoritative until a new/recovered execution explicitly establishes coverage.

### 4.12.8 Positive Evidence May Survive an Incomplete Run

Q-SCN deliberately does **not** throw away everything from a partial scan.

```text
successfully observed representation
→ valid positive observation
```

A `PARTIAL`, `FAILED-after-some-work`, cancelled or interrupted run may have already discovered valid new/changed representations.

Those observations may feed candidate matching/reconciliation according to Q-ID/Q-REC, provided their own evidence is valid.

But:

```text
unseen item in incomplete coverage
→ NO absence evidence
```

This asymmetric rule is central to V1:

```text
presence can be local
absence requires coverage authority
```

### 4.12.9 Authoritative Absence Is Coverage-Scoped, Not a Global Boolean

Q-SCN rejects `scanRun.complete == true → everything unseen is missing` as the universal model.

Absence authority is conceptually:

```text
Run/coverage region
+ observation mode capable of negative comparison
+ healthy accessible scope
+ complete enumeration for that region
+ valid comparison epoch/config
+ prior Asset known to belong to the comparable scope
→ authoritative absence evidence for that Asset
```

A root-wide `PARTIAL` run **may** contain completely observed subscopes. The domain model is allowed to express authoritative completed coverage inside a globally partial run.

However, the first V1 implementation may choose the safer simplification:

```text
PARTIAL run
→ publish positive evidence only
→ no negative authority anywhere
```

until finer-grained coverage bookkeeping is proven necessary. This conservative implementation remains compliant with the foundation.

### 4.12.10 SAF Completeness Rules

For SAF tree traversal:

1. The registered `StorageRoot`/tree must be accessible for the observed scope.
2. Every included directory branch must be queried to completion for that authoritative coverage region.
3. A null/failed query, `SecurityException`, authentication/access error or equivalent makes the affected region incomplete.
4. `DocumentsContract.EXTRA_LOADING=true` means the returned listing is not complete; that branch cannot yield authoritative absence until a later requery returns complete data.
5. Cancellation/interruption prevents unfinished branches from gaining negative authority.
6. A provider changing document IDs/URIs during traversal is handled through evidence/reconciliation; locator churn does not itself make the scan destructive.

A SAF scan being `COMPLETE` is therefore an **application observation guarantee**, not an atomic filesystem snapshot guarantee.

### 4.12.11 MediaStore Completeness Rules

MediaStore is supplemental in V1 and has different observation semantics.

Rules:

- `MediaStore.getVersion()`/volume availability are adapter epoch/health evidence.
- If the version changes such that generation counters are no longer comparable, incremental comparison is invalid and a full resynchronization is required before new blanket negative conclusions.
- `getVersion(...)=null` / volume not present is unavailability, not empty authoritative content.
- `GENERATION_ADDED` / `GENERATION_MODIFIED` are useful for incremental additions/changes but “not returned” is not deletion evidence.
- A successful incremental query cannot mark all unseen prior rows/Assets missing.
- Full enumeration may produce scoped negative evidence only when the adapter can demonstrate complete accessible coverage for its declared collection/volume scope.

Exact generation windows/query predicates/retry algorithm remain implementation/benchmark work.

### 4.12.12 Cross-Adapter Authority — Supplemental Evidence Cannot Create False Negatives

Q-STO-001 already makes SAF the primary universal-root mechanism and MediaStore supplemental.

Therefore V1 locks:

```text
absence from adapter A
cannot negate presence/authority owned by adapter B
unless the Asset's observation contract explicitly says A owns that scope.
```

Examples:

- A CBZ/EPUB under a registered SAF tree cannot become missing because MediaStore does not return it.
- A video observed by both MediaStore and the registered SAF tree is not duplicated solely because two adapters saw it; Q-ID/Q-REC reconcile the evidence.
- MediaStore index reset cannot by itself mark a SAF-backed Asset missing.
- A supplemental adapter may accelerate positive discovery without gaining destructive/negative authority over the universal root.

### 4.12.13 Finalization Boundary — Negative Reconciliation Happens After Observation

Conceptual pipeline:

```text
begin ScanRun
    ↓
collect positive observations
    ↓
record branch/provider errors + incomplete/loading regions
    ↓
finalize run outcome + coverage
    ↓
compute authoritative absence candidates ONLY for eligible coverage
    ↓
Q-REC-001 maps absence evidence → recoverable MISSING / other action
```

Forbidden pattern:

```text
recursive traversal
→ "haven't seen old row yet"
→ delete/mark missing inline
```

The implementation does **not** need one giant database transaction for the entire scan. It only needs a correctness boundary ensuring negative reconciliation cannot commit before coverage authority is known.

### 4.12.14 Process Death / Cancellation / Retry

Semantics:

- process death before finalization never produces authoritative absence;
- explicit cancellation never produces blanket absence for unfinished scope;
- positive observations already durably recorded may remain useful;
- retry/new run receives its own `RunId`/observation provenance;
- a previous unfinished run is not silently resumed as if it had completed unless the runtime implementation can explicitly prove/resume its coverage contract.

`Q-RUN-001` now resolves this runtime boundary: persistent full/root scans use restartable WorkManager orchestration by default, retries earn fresh ScanRun authority by default, cancellation is cooperative but callback-independent, and negative evidence remains guarded by finalization authority.

### 4.12.15 Overlapping / Stale Runs

Two overlapping runs may finish out of order. A root/filter configuration may also change while a run is executing.

Foundation rule:

```text
stale or superseded run
→ cannot publish new negative authority against a newer scope/generation
```

The app needs a conceptual scope/config revision or equivalent freshness check at finalization.

Positive observations from an older run may still be evidence that content existed at a specific observation time, but they must not blindly overwrite newer contradictory state.

Q-RUN-001 now requires serialization/coalescing of overlapping negative-authority scopes plus a stale-run authority check; exact lock/lease/unique-work naming and persistence mechanism remain downstream persistence design.

### 4.12.16 Empty Scope / Mass-Absence Guardrail

An accessible, completely enumerated root may legitimately be empty. Q-SCN does **not** define “empty means scan failure”.

Instead:

```text
accessible healthy scope
+ complete authoritative full enumeration
+ zero matching representations
→ may yield MISSING evidence for previously-known Assets in that exact scope
```

This remains safe because Q-REC-001 maps absence to recoverable `MISSING`, not hard deletion.

Conversely:

```text
root unavailable / grant lost / provider loading / traversal failure
+ zero observed representations
→ NOT an authoritative empty root
```

Implementations may add anomaly diagnostics (for example “suddenly empty root”) without redefining identity/lifecycle semantics.

### 4.12.17 Move/Copy Reconciliation Interaction

Q-REC-001 requires authoritative old-location absence before an external move can be confidently reattached.

Therefore:

```text
new strong candidate observed
+ old location not seen in partial/non-authoritative coverage
→ do not finalize move solely from non-observation
```

The new representation can still be recorded/candidate-matched. Move continuity waits until authoritative absence or explicit user/provider evidence closes the old side.

This prevents a partial scan from converting a temporary duplicate observation into a false move.

### 4.12.18 Persistence Intent Without Locking Schema

R4.14 `Q-PER-001` now represents these semantics with `scan_run`, declared scope and run-local observation/finalization table families; implementation may still tune exact columns/retention:

- app-owned `RunId`/generation provenance;
- declared scope/config revision;
- run outcome/finalization;
- diagnostics/incomplete coverage when needed;
- last-seen observation provenance for Assets/evidence;
- stale/superseded-run protection;
- deriving absence only from finalized authoritative coverage.

This does **not** require storing every visited directory or every transient observation forever.

A common implementation shape such as `lastSeenRunId`/generation marking is permitted but **not mandated**. The invariant matters more than the schema:

```text
unseen by authoritative finalized comparable coverage
!=
unseen by any arbitrary run
```

### 4.12.19 Representative Scenarios

#### Full SAF root, clean completion

```text
scope = Root A
all included branches complete
no loading/errors/cancellation
known Asset X not observed
→ authoritative absence candidate for X
→ Q-REC: X becomes recoverable MISSING
```

#### SAF branch fails midway

```text
7,500 assets observed
subtree B query throws access/IO error
→ run PARTIAL
→ observed Assets remain valid positive evidence
→ unseen Assets in unknown coverage cannot become MISSING
```

#### SAF provider returns `EXTRA_LOADING`

```text
children returned
EXTRA_LOADING=true
→ useful positive rows may be consumed
→ branch incomplete for absence
→ requery required before negative authority
```

#### Process killed at 80%

```text
run never finalized
→ positive observations already persisted may remain
→ zero blanket absence transitions
```

#### MediaStore incremental pass

```text
query generation > previousGeneration
row X not returned
→ X may simply be unchanged
→ no absence evidence
```

#### MediaStore version reset

```text
stored version != current version
→ generation comparison invalidated
→ schedule/perform full resync
→ do not infer mass deletion from delta gap
```

#### Removable storage disappears

```text
volume/root unavailable
→ access state changes
→ scan FAILED/PARTIAL as appropriate
→ no child MISSING transitions merely from unavailability
```

#### Filter changes while scanning

```text
scope revision 10 starts
user changes exclusions → revision 11
revision 10 finishes later
→ old run cannot publish negative authority against revision 11
```

#### Two scans overlap

```text
Run A starts
Run B starts later for overlapping scope
Run B finalizes current scope
Run A finishes last
→ Run A cannot overwrite newer negative-state decisions as current authority
```

### 4.12.20 Rejected Alternatives

#### Alternative A — Delete/mark missing inline while recursively scanning

Rejected because an item may simply be encountered later, its branch may fail, or the process may die before full coverage is known.

#### Alternative B — “No exception” means the scan is complete

Rejected because SAF can explicitly return partial cached data with `EXTRA_LOADING=true`, and incremental scans may succeed without enumerating the full scope.

#### Alternative C — One global `complete: Boolean` is enough

Rejected as the semantic model because authority belongs to a declared scope/mode/coverage. A complete targeted subtree is not a complete root; a partial root may contain completed subscopes.

An implementation may start conservatively with run-wide authority, but the domain meaning must remain scoped.

#### Alternative D — A PARTIAL/FAILED run is useless; discard all observations

Rejected because directly observed presence can still be trustworthy even when negative coverage is incomplete.

#### Alternative E — Missing from MediaStore incremental generation query means deleted

Rejected because generation queries identify added/modified items; unchanged items are naturally absent from the result, and version resets require full resynchronization.

#### Alternative F — Make the whole scan one giant atomic DB transaction

Rejected as a foundation requirement. Large scans may be long-running and need incremental positive persistence/checkpointing. Correctness only requires negative transitions to wait for authoritative finalization.

#### Alternative G — Any zero-result scan is either always authoritative or always failure

Rejected. Zero results are authoritative only when the exact scope was healthy and completely enumerated; inaccessible/loading/failed scopes are not authoritative empty scopes.

#### Alternative H — SAF and MediaStore each independently own negative truth for the same local library

Rejected because MediaStore is supplemental in V1 and does not universally index CBZ/EPUB/folder content. Cross-adapter absence would create false negatives and contradictory ownership.

### 4.12.21 Downstream Impact

Q-SCN-001 unlocks these assumptions:

```text
ScanRun has declared observation scope and provenance.
Presence evidence can be accepted before whole-run completion.
Absence requires finalized authoritative comparable coverage.
Outcome and coverage are separate concepts.
Partial/cancelled/interrupted/unavailable scopes do not create blanket absence.
Incremental delta success does not imply full-snapshot authority.
SAF EXTRA_LOADING means incomplete negative coverage.
MediaStore version reset invalidates generation comparison.
Supplemental adapters cannot create cross-adapter false negatives.
Stale/superseded runs cannot publish newer negative truth.
```

Stage C (`Q-ID-001`, `Q-REC-001`, `Q-SCN-001`) now provides a coherent provisional ingestion/reconciliation semantics baseline.

Historical Q-SCN closure note: later Stages D–F reached PROVISIONAL baseline and thereby unblocked deep persistence/module/API design. R4.14 has now completed that design in `Q-PER-001`, `Q-MOD-001` and `Q-API-001`.

Historical Q-SCN closure note: Q-META-001 later removed the metadata blocker, and Stage F subsequently removed the runtime/backup blockers. R4.14 `Q-PER-001` now supplies the explicit persistence design constrained by Progress/History/Library/Metadata semantics.

### 4.12.22 Verification Ideas

Future tests should prove at least:

1. Full healthy SAF scan can create absence candidates only after finalization.
2. One failed SAF subtree prevents unseen Assets in that unknown region from becoming MISSING.
3. Positive observations from a partial run can still be ingested.
4. `EXTRA_LOADING=true` prevents absence authority for that branch.
5. Successful requery with loading cleared can complete the branch.
6. Cancellation at 90% creates no blanket absence for unfinished scope.
7. Process death with persisted positive observations creates no absence on restart merely because the run was left RUNNING.
8. Root/volume access loss produces no child missing evidence.
9. MediaStore incremental query not returning an unchanged item does not mark it missing.
10. MediaStore version reset forces a new full synchronization boundary before blanket negative comparison.
11. Targeted subtree scan can affect absence only inside its declared subtree.
12. Changed filters/scope revision prevent the old run from marking newly excluded content missing.
13. Older overlapping run cannot finalize negative authority after a newer run/scope supersedes it.
14. Empty but healthy fully enumerated root can mark prior Assets MISSING without deleting canonical/user state.
15. Zero-result inaccessible root cannot mark any child MISSING.
16. SAF and MediaStore observing the same video do not create competing negative truth.
17. A new move candidate seen during a partial run does not finalize move solely because the old locator was not encountered.
18. `MISSING` from authoritative scan remains recoverable under Q-REC and does not cascade to Progress/History/Library.

### 4.12.23 Remaining Risks / Deferred Details

Q-SCN-001 intentionally does **not** decide:

- exact Room `ScanRunEntity`/coverage schema;
- whether V1 stores per-directory coverage or initially uses conservative run-wide negative authority;
- exact traversal batching/page sizes;
- exact Worker/class names, traversal batch/checkpoint cadence and scheduler tuning under the now-resolved `Q-RUN-001` ownership/retry semantics;
- exact MediaStore generation query/window algorithm;
- file watcher/change-observer integration;
- performance budgets for very large SAF trees;
- hash/fingerprint scheduling;
- automatic cleanup/GC of old run diagnostics;
- Library membership when every representation is missing is resolved by `Q-LIB-001`; exact UI presentation remains deferred;
- Progress/typed-anchor migration semantics (`Q-PROG-001`, `Q-PROG-002`).

No deferred item above requires interpreting partial/incremental/unavailable absence as deletion.

### 4.12.24 ADR Requirement

No separate ADR is required for Q-SCN semantics itself.

An ADR may become appropriate when choosing a persistent scan checkpoint/coverage schema or a long-running Android execution architecture whose recovery model materially constrains these guarantees.

### 4.12.25 Research References

Primary Android/platform sources:

- Android `DocumentsProvider.queryChildDocuments()` / `EXTRA_LOADING` / recursive child queries: https://developer.android.com/reference/android/provider/DocumentsProvider
- Android `DocumentsContract` tree/document query semantics: https://developer.android.com/reference/android/provider/DocumentsContract
- Android `MediaStore.getVersion()` / `getGeneration()`: https://developer.android.com/reference/android/provider/MediaStore
- Android `MediaStore.MediaColumns.GENERATION_ADDED` / `GENERATION_MODIFIED`: https://developer.android.com/reference/android/provider/MediaStore.MediaColumns

Mature project/library evidence:

- Syncthing FAQ — folder marker safety / unmounted folder: https://docs.syncthing.net/users/faq.html
- Syncthing scan API — full folder vs subtree scan and scan failure: https://docs.syncthing.net/rest/db-scan-post.html
- Syncthing scan/pull error surface: https://docs.syncthing.net/rest/folder-errors-get.html
- Jellyfin scanner refactoring discussion: https://github.com/jellyfin/jellyfin-meta/discussions/125
- Jellyfin scan/missing-mount safety discussion: https://github.com/jellyfin/jellyfin.org/issues/1427
- Plex trash/unavailable semantics after library scan: https://support.plex.tv/articles/200289326-emptying-library-trash/
- Komga library/trash/scanner guidance: https://komga.org/docs/guides/libraries/
- Komga trash recovery semantics: https://komga.org/docs/guides/trash/
- Kavita scanner improvement work / inaccessible drive guard: https://github.com/Kareadita/Kavita/issues/3785

Research note: server/sync projects above are used only as failure-mode and reconciliation evidence. Android SAF/MediaStore contracts remain primary for V1 observation semantics.

### 4.12.26 Stage C Closure Summary

Stage C now has a coherent three-step provisional model:

```text
Observed storage/provider facts
        ↓
Q-ID-001 — typed/scoped identity evidence
        ↓
Q-REC-001 — representation/access/lifecycle reconciliation action
        ↓
Q-SCN-001 — declared scan scope + coverage authority for negative evidence
```

Critical invariant:

```text
presence can be local;
absence requires authoritative finalized coverage.
```

`Q-ID-001`, `Q-REC-001` and `Q-SCN-001` are **PROVISIONAL** and mutually consistent.

**Stage C status:** `PROVISIONAL BASELINE / COMPLETE FOR NEXT AUDIT STAGE`

**Historical handoff:** Q-PROG-001 was the next blocker at Q-SCN closure and is now completed at **PROVISIONAL** level. Q-PROG-002, Q-HIST-001 and Q-LIB-001 are also now **PROVISIONAL**; Stage D is complete at provisional baseline level and Q-META-001 is now **PROVISIONAL**; Q-BACK-001 is now **PROVISIONAL**; next work is deep persistence/module/API design.

## 4.13 Decision Record — Q-PROG-001: Typed Progress / Completion Semantics

**Status:** `PROVISIONAL`

Q-PROG-001 mở Stage D bằng cách xác định durable **current consumption state** tối thiểu cho Video, sequential-image media và Publication mà không biến mọi progress thành một generic scalar, không trộn progress với history, và không gắn resume ownership vào source/Asset.

Cross-media research cũng phát hiện wording cũ của `Q-PROG-002` quá hẹp khi chỉ nói publication locator. Không chỉ EPUB locator mà video time offset và image page ordinal cũng có thể phụ thuộc representation/source/revision. Vì vậy Q-PROG-001 **generalizes Q-PROG-002** thành cross-media resume-anchor portability question; publication vẫn là representative hard case.

### 4.13.1 Problem Statement

Foundation đã khóa:

```text
Progress owner = canonical ConsumptionTargetRef
ConsumptionTargetRef = MediaTarget(MediaId) | UnitTarget(UnitId)
Source/Asset does not own canonical progress
History != Progress
```

Nhưng exact semantics còn thiếu:

1. Video, manga/comic và publication cần lưu position bằng cùng một scalar hay typed payload?
2. `completed/read/watched` nên derive mỗi lần từ position hay persist như durable user state?
3. Một completed item có còn được giữ resume anchor không?
4. Seeking/page-back/navigation-back có được ghi lại hay progress phải monotonic tăng?
5. Runtime engine có thể phát position rất thường xuyên; durable persistence cần semantics gì để sống qua process death mà không write mỗi frame/event?
6. `duration`, `pageCount` và `totalProgression` là progress truth hay chỉ extent/projection evidence?
7. Resume anchor thuộc canonical target nhưng có thể representation-sensitive; context đó được giữ ở đâu mà không làm Asset/Source thành owner?
8. Parent Media có Units có nên có thêm một aggregate Progress row/source of truth không?

Nếu dùng một generic `Double 0..1`, app sẽ mất precise resume semantics. Nếu chỉ persist raw engine position mà không có completion state, watched/read semantics sẽ thay đổi khi threshold/duration/page-count thay đổi. Nếu completion xóa position, reread/rewatch và manual state trở nên lossy. Nếu progress chỉ tăng bằng `max(old,new)`, seeking backward hợp lệ sẽ không thể persist.

### 4.13.2 Primary / Platform Findings

#### Android Media3 — playback position is a typed time offset, not a universal progress model

Media3 `Player`/`ExoPlayer` exposes current playback position in milliseconds and duration separately; duration may be unknown (`TIME_UNSET`) for some states/content. Player lifecycle also distinguishes runtime states such as `STATE_ENDED`.

**Observed fact:** the playback engine gives a media-time anchor and runtime end signal; it does not define the app's watched/history semantics.

**Inference:** Video progress should preserve a time-based resume anchor. Completion policy belongs above Media3 and should not be encoded as `position == duration` only.

#### Android durable-state guidance — business state needs local persistence

Android guidance distinguishes durable local persistence from in-memory `ViewModel`/UI saved state. Activity/process lifecycle does not guarantee that arbitrary screen state remains alive indefinitely.

**Observed fact:** durable business/user state must not depend on a screen object surviving.

**Inference:** progress checkpoints must reach app persistence during active consumption; relying only on ViewModel lifetime or one final screen callback is insufficient.

#### Readium — precise Locator + optional progression, host-owned persistence

Readium's `Navigator` does not permanently store reading progression. The host observes `currentLocator`, serializes the `Locator`, and restores it later. A Locator can carry resource progression, publication `totalProgression`, position and fragments. Readium explicitly avoids durable screen-page numbers for reflowable publications.

**Observed fact:** a publication resume anchor is structured location data; normalized progression is useful but not a substitute for the precise Locator.

**Inference:** Publication progress must persist a locator-shaped anchor and may use normalized progression as projection/fallback evidence, not as the only source of truth.

### 4.13.3 Mature Project Evidence

#### Jellyfin — position and played state are distinct fields

Jellyfin user data exposes `PlaybackPositionTicks`, `Played`/`IsPlayed`, percentage and play-count/timestamp data separately. Its playback logic can apply resume/completion thresholds instead of equating exact end position with watched state.

**Inference:** durable completion and resume position should be independently representable. Threshold policy should not dictate schema identity.

#### Kodi — watched and resume can coexist

Kodi stores a resume point separately from watched/play-count state. Its documentation explicitly notes that an item can be both watched and resumable, for example when a previously watched video is partially rewatched. Kodi also exposes separate configurable thresholds for when to save resume and when to mark watched.

**Inference:** `completed` must not imply `resumeAnchor = null`; completion and current resume position are orthogonal state dimensions.

#### Mihon — chapter `read` and `last_page_read` are separate

Mihon's chapter persistence contains both `read` and `last_page_read`.

**Inference:** sequential-image progress naturally needs a discrete page anchor plus completion/read state; a generic percentage would discard useful semantics.

#### Komga — page progress and completed state are separate and manually controllable

Komga tracks books as unread/in-progress/read, supports manual mark read/unread, and its API/model uses page progress plus `completed`. Its history/issues also call out page-count changes as a progress-adjustment concern.

**Inference:** page ordinal and completion should be independent. Page-count change belongs to representation-progress compatibility, not identity.

#### Audiobookshelf — current time, percentage and finished state coexist

Audiobookshelf media progress exposes `currentTime`, total `duration`, derived/normalized `progress`, `isFinished`, plus lifecycle timestamps.

**Inference:** real systems frequently retain both a precise anchor and a normalized projection while keeping explicit finished state. This project can adopt the separation without copying Audiobookshelf's history/timestamp ownership.

### 4.13.4 Decision — Progress Is Current Durable State Per Canonical Target

V1 defines `Progress` semantically as:

```text
Progress
= current durable consumption/resume state
  for exactly one ConsumptionTargetRef
```

There is at most one **current** Progress state per canonical consumption target in V1.

`Progress` is not:

- a history event log;
- a play/read count;
- a source/provider-specific state namespace;
- an Asset-owned record;
- a generic percentage;
- a parent-series aggregate when child Units own actual consumption.

An untouched/unstarted target normally has **no Progress state** rather than a synthetic `0%` row.

### 4.13.5 Conceptual Typed Shape

The foundation-level shape is:

```text
Progress
├── target: ConsumptionTargetRef
├── resumeAnchor: TypedResumeAnchor?
├── completion: CompletionState
├── resumeContext: ResumeContext?          // compatibility/provenance only
└── updatedAt / revision metadata          // persistence freshness, not History

TypedResumeAnchor
├── VideoResume
├── ImageSequenceResume
└── PublicationResume
```

Names above are semantic placeholders, **not final Kotlin classes or DB columns**.

#### Video resume

Conceptually:

```text
VideoResume
├── positionMs
└── durationSnapshotMs?     // extent observation, not identity
```

Rules:

- `positionMs >= 0`.
- Unknown duration does not make current position invalid.
- Duration is a last-known extent snapshot useful for UI/completion/compatibility checks; it is not canonical target identity.
- Do not persist `playedPercentage` as the only authoritative resume state when a precise time offset exists.

#### Sequential-image resume

Conceptually:

```text
ImageSequenceResume
├── pageIndex / pageOrdinal
└── pageCountSnapshot?      // extent observation
```

Rules:

- Resume anchor is the current page/ordinal inside the resolved representation.
- Exact zero/one-based production encoding is deferred; domain semantics only require an ordered page anchor.
- Page count is an extent snapshot, not Unit identity and not proof that an anchor is portable to another representation.
- `read/completed` is not derived permanently from `page == pageCount - 1` on every load.

#### Publication resume

Conceptually:

```text
PublicationResume
└── Locator-like structured position
    ├── href/resource reference
    ├── fragments / precise location when available
    ├── resource progression?
    ├── publication position?
    └── total progression?
```

Rules:

- Preserve a structured publication locator suitable for precise restore.
- `totalProgression` is useful normalized projection/fallback evidence but is not enough by itself for precise resume.
- Reflowable screen page number is not durable progress identity.
- Exact serialized Locator format/engine adapter belongs to publication technical design; foundation locks the semantics, not a Readium class dependency.

### 4.13.6 Completion Is Explicit Durable State, Not a Derived Getter

V1 completion semantics are explicit.

Conceptually:

```text
CompletionState
├── IN_PROGRESS
└── COMPLETED
```

`UNSTARTED` is normally represented by absence of Progress rather than a third persisted state.

Rules:

1. Runtime/reader signals may **propose** completion.
2. Domain progress policy decides whether that signal crosses the current auto-completion rule.
3. The resulting completion decision is persisted.
4. Future changes to threshold/config/duration/page count do **not** silently re-derive and rewrite already persisted completion on read.
5. Manual `MarkCompleted` is allowed and does not require a precise resume anchor.
6. Completion can be explicitly reset through a progress-reset/user action path; exact UI wording is deferred.

Completion may be auto-triggered by media-specific evidence such as:

- Video: natural playback end or an explicit near-end policy.
- Image sequence: reaching/passing the last consumable page under the current representation.
- Publication: reaching an engine-supported end/near-end condition or explicit user action.

Exact numeric thresholds are **not locked** in the foundation. Mature systems vary and often make them configurable. Thresholds are policy, not persistence identity.

### 4.13.7 Resume Anchor and Completion Are Orthogonal

Critical invariant:

```text
resume position != completion status
```

A target may be:

```text
IN_PROGRESS + resume at middle
COMPLETED   + resume at/near end
COMPLETED   + resume earlier during a reread/rewatch
COMPLETED   + no resume anchor after manual mark-complete
```

Therefore:

- setting `COMPLETED` does not automatically require clearing the resume anchor;
- seeking/navigating backward does not automatically clear `COMPLETED`;
- UI can choose whether a completed item defaults to start-over or resume, but that launch policy does not redefine stored progress semantics;
- future reread/rewatch history can coexist with one current resume state without forcing History into Progress.

This deliberately follows the stronger cross-media invariant rather than Jellyfin-style or Kodi-style exact clearing behavior.

### 4.13.8 Progress Values Are Not Monotonic

A valid user action can move position backward:

```text
Video: seek 42:00 → 10:00
Manga: page 80 → page 25
Publication: chapter 9 → chapter 4
```

Therefore:

```text
newProgress = max(oldProgress, observedProgress)
```

is **invalid** as a universal persistence rule.

Rules:

- Numeric position/page/progression is not a version number.
- A causally newer valid observation may contain a smaller position.
- Delayed/stale asynchronous checkpoint must not overwrite a newer observation merely because it arrives later at storage.
- Exact local session sequencing/version-token implementation is deferred to persistence/consumption technical design.
- Future multi-device merge/conflict semantics remain V3 sync scope; Q-PROG-001 only locks single-device durable-state meaning.

### 4.13.9 Progress Write Semantics — Checkpointed, Not Every Render Tick

Consumption engines may emit position changes very frequently. Foundation direction:

```text
high-frequency runtime state
        ↓ coalesce/debounce/checkpoint
current durable Progress
```

V1 must support:

- periodic durable checkpoints during meaningful active consumption;
- persistence on important boundaries such as pause/stop/reader exit/service transition when available;
- process recreation restoring from the latest durable checkpoint rather than ViewModel-only memory;
- coalescing so video playback does not write Room every frame/tick and a scrolling reader does not perform pathological writes.

Exact checkpoint interval/debounce policy is deferred to technical implementation + benchmark. The architecture must not rely exclusively on one final Activity/Composable callback, because durable progress is business/user state rather than screen state.

### 4.13.10 Resume Eligibility Is Policy, Not Schema

Opening an item and immediately leaving at position zero should not necessarily create durable "in progress" noise.

V1 may use media-specific **resume eligibility** policies, for example:

- minimum meaningful video position/time;
- first meaningful page transition;
- publication locator advancement beyond initial position.

Exact thresholds are deferred. The invariant is:

```text
engine observation
!= automatically a durable Progress row
```

This is distinct from completion policy.

### 4.13.11 Extent and Percentage Are Derived/Observed Data

`duration`, `pageCount` and normalized progression are useful, but they have different semantics from the precise anchor.

Rules:

- Video `durationSnapshotMs` is observed extent.
- Manga `pageCountSnapshot` is observed extent.
- Publication `totalProgression` may be carried by the locator/engine.
- UI percentage can be derived when extent is known.
- Percentages must not replace the precise typed anchor as the universal source of truth.
- Extent changes after Asset revision/source switch are compatibility signals; do not silently clamp/remap solely because the old percentage "looks close".

### 4.13.12 Resume Context Is Compatibility Provenance, Not Ownership

A Progress record may retain optional last-used context such as:

```text
ResumeContext
├── SourceBindingId?
├── AssetId?
└── AssetRevision?
```

Purpose:

- remember which representation produced the precise resume anchor;
- validate whether that anchor can be reused directly after resolution changes;
- support the diagnostics/fallback semantics defined by `Q-PROG-002`.

Rules:

- `target` remains the owner of Progress.
- Missing/unavailable/retired Asset does not automatically delete Progress.
- Changing SourceBinding does not create a new canonical Progress namespace.
- Context can change while target identity remains stable.
- Context is not proof that all anchors are portable to every other representation.

### 4.13.13 Q-PROG-002 Is Generalized to Cross-Media Anchor Portability

Research shows portability risk exists for all three families:

```text
Video
same canonical target
→ alternate cut/encode/runtime may have different timeline/duration

Image sequence
same canonical chapter
→ alternate scan/release may insert/remove cover/bonus pages

Publication
same canonical book
→ new EPUB revision/format may change href/CFI/resource structure
```

Therefore the canonical question becomes:

> **Q-PROG-002 — Can a typed resume anchor be directly reused across Asset/source/format/revision changes, and what validation/migration/fallback is required when it cannot?**

Publication locator remains the hardest representative case but no longer owns the entire portability problem.

Q-PROG-001 only locks:

- same canonical target keeps the same Progress owner;
- precise anchor may have representation provenance;
- portability is not assumed across changed representation context;
- incompatible-anchor handling must preserve user state instead of silently discarding or blindly applying it.

Exact migration/fallback was intentionally deferred from Q-PROG-001 and is now defined by Q-PROG-002 below: exact reuse requires compatibility evidence; deterministic mapping yields exact migration; normalized projection remains explicitly approximate.

### 4.13.14 Parent / Aggregate Progress Is Derived

For subdivided media:

```text
Media: Series
├── Unit: Episode 1 → Progress
├── Unit: Episode 2 → Progress
└── Unit: Episode 3 → no Progress
```

V1 does not create a second authoritative "series Progress" row that duplicates child consumption truth.

Series-level values such as:

- episodes completed;
- chapters read;
- current/next Unit;
- overall percentage;

are **derived projections/query policy** from Unit Progress + canonical ordering/library semantics.

Atomic Media can own direct Progress through `MediaTarget(MediaId)`.

This prevents two sources of truth for the same consumption path.

### 4.13.15 Reset Semantics and History Boundary

A deliberate reset/unread/unwatched action may clear the **current Progress state** for the target:

```text
ResetProgress(target)
→ no current resume anchor
→ no current completion state
```

But:

```text
ResetProgress
!= erase History automatically
```

Q-HIST-001 below resolves this boundary: meaningful consumption sessions remain as HistoryEntries, while exact future reread/rewatch pass/count semantics stay deferred rather than being inferred from Progress.

Likewise:

- `Progress.updatedAt` is operational freshness metadata, not a history event stream;
- `PlayCount`, `ReadDate`, `FinishedAt`, session history and reread/rewatch count are not pulled into Progress merely because external projects colocate some of them;
- `Q-LIB-001` subsequently confirms Library-scoped Continue eligibility is membership-gated while Progress remains independent; exact ranking remains projection detail.

### 4.13.16 Source / Availability Interaction

Progress survives source/storage failure:

```text
Asset MISSING
StorageRoot ACCESS_LOST
Source unavailable
Binding temporarily unresolved
```

must not automatically clear canonical Progress.

After content becomes available again, resolver/Progress portability logic decides whether the stored precise anchor can be reused, migrated or degraded to a fallback.

This preserves the Stage B/C invariant that availability does not own user state.

### 4.13.17 Representative Scenarios

#### Movie — ordinary resume

```text
MediaTarget(movieId)
Progress
├── VideoResume(position = 42m13s, durationSnapshot = 1h58m)
└── completion = IN_PROGRESS
```

Source/Asset can disappear temporarily without losing this state.

#### Movie — completed then partially rewatched

```text
completion = COMPLETED
resumeAnchor = VideoResume(18m20s)
```

This is valid. The user previously completed the movie but currently has a newer resume anchor from a rewatch.

#### Episode — seek backward

```text
old = 31m
user seeks to 8m
new = 8m
```

The newer 8m checkpoint is valid; persistence must not `max()` it back to 31m.

#### Manga chapter

```text
UnitTarget(chapterId)
Progress
├── ImageSequenceResume(page = 24, pageCountSnapshot = 42)
└── completion = IN_PROGRESS
```

Reaching the last page may set `COMPLETED`, but page-count changes after a representation revision are not silently treated as compatible.

#### Manga chapter marked read manually

```text
completion = COMPLETED
resumeAnchor = null OR retained previous anchor
```

Manual completion does not require manufacturing a fake "last page" anchor.

#### Standalone EPUB

```text
MediaTarget(bookId)
Progress
├── PublicationResume(locator = structured precise location)
├── completion = IN_PROGRESS
└── resumeContext = AssetRevision R3
```

Reader restart on the same compatible representation restores the Locator.

#### EPUB revision changes

```text
canonical target unchanged
AssetRevision R3 → R4
old Locator exists
```

Do not delete Progress and do not blindly apply R3 locator to R4. Q-PROG-002 decides compatibility/migration/fallback.

#### Series aggregate

Series itself has no duplicate progress row when Episodes own progress. "5/12 watched" is a projection.

### 4.13.18 Rejected Alternatives

#### Alternative A — One generic `progress: Double 0..1` for every media kind

Rejected because it cannot precisely resume video time, discrete image page position or structured publication location. It also turns a display projection into canonical state.

#### Alternative B — Completion is always derived from current anchor/percentage on read

Rejected because thresholds/extents may change, manual mark-complete must be representable, and watched/read state can remain true during reread/rewatch at an earlier position.

#### Alternative C — Setting completed always clears resume position

Rejected as a universal rule. It destroys useful current-state information and prevents representing completed + resumable reread/rewatch state observed in mature systems.

#### Alternative D — Progress must monotonically increase

Rejected because seek/page/back navigation is legitimate. Numeric position is not a version clock.

#### Alternative E — Persist every engine position event directly

Rejected as architecture baseline because playback/scroll/location signals may be high-frequency and would create unnecessary write amplification. Durable periodic/boundary checkpoints are sufficient; exact cadence is an implementation/performance decision.

#### Alternative F — Store progress separately per Source/Asset

Rejected because it breaks unified canonical progress. Representation context can be stored as provenance without becoming owner.

#### Alternative G — Persist both parent Media progress and Unit progress as authoritative truth

Rejected because it creates duplicate sources of truth. Parent completion/continue state is derived when child Units own consumption.

#### Alternative H — Put play count/read dates/session log into Progress

Rejected for this project because Q-HIST already separates activity history from current durable position. External colocated models are evidence, not a template.

### 4.13.19 Downstream Impact

Q-PROG-001 unlocks these assumptions:

```text
Progress owner = ConsumptionTargetRef.
One current Progress state per canonical target.
Resume anchor is typed by consumption family.
Completion is explicit durable state.
Resume and completion are orthogonal.
Unstarted normally means no Progress state.
Progress values are not monotonic.
Percent/extent is not universal resume truth.
Source/Asset context is provenance, not ownership.
Parent aggregate progress is derived.
Runtime progress is checkpointed/coalesced into durable persistence.
```

It also changes the next question scope:

```text
Q-PROG-002
= cross-media resume-anchor portability / migration / fallback
  across Asset, Source, format and revision changes
```

R4.14 `Q-PER-001` now maps typed current Progress, resume-compatibility context, append-oriented History, Library membership intent and Media-level metadata provenance/override semantics into provisional relational table families. Exact Kotlin class names and serialization codecs remain implementation details.

### 4.13.20 Verification Ideas

Future tests should prove at least:

1. Atomic Movie can persist VideoProgress directly on `MediaTarget`.
2. Episode/Chapter progress uses `UnitTarget`; parent Media does not get duplicate authoritative progress.
3. Video precise time position persists independently from completion.
4. Completed video can later have an earlier resume anchor during rewatch without auto-clearing completion.
5. Backward seek persists the newer lower position; no numeric-max merge.
6. Stale delayed checkpoint cannot overwrite a causally newer checkpoint.
7. Unknown video duration still allows a valid time resume anchor.
8. Manga page progress persists page ordinal separately from `COMPLETED`.
9. Manual mark-read does not require manufacturing a last-page anchor.
10. Changed page count/AssetRevision triggers portability validation rather than blind clamp/remap.
11. Publication precise Locator can serialize/restore on the same compatible representation.
12. Reflow/font/orientation change does not depend on durable screen page number.
13. Publication completion is not re-derived on every load solely from `totalProgression`.
14. Missing/unavailable Asset preserves Progress.
15. Source switch keeps the same canonical Progress owner.
16. Progress reset clears current resume/completion state without requiring History deletion.
17. High-frequency playback updates are coalesced/checkpointed rather than one DB write per render/player tick.
18. Process recreation restores from durable Progress without requiring the original ViewModel/Reader object.
19. Threshold configuration change does not silently flip already persisted completion state.
20. Series progress/count/next-item projection can be recomputed from Unit progress without a parent truth row.

### 4.13.21 Remaining Risks / Deferred Details

Q-PROG-001 intentionally leaves open:

- exact implementation algorithms/tolerances for the now-defined Q-PROG-002 compatibility/migration policy;
- exact video resume/completion thresholds;
- exact manga/page resume eligibility and auto-complete threshold behavior;
- exact publication end/completion policy;
- exact checkpoint/debounce interval and write batching;
- exact persistence encoding for typed payloads;
- exact causal/stale-write token implementation inside one device/session;
- exact manual mark-complete/reset UX;
- History session semantics are resolved by `Q-HIST-001`; exact future reread/rewatch pass/count modeling remains deferred there;
- `Q-LIB-001` resolves membership gating for Continue; exact Continue ranking/next-item policy remains deferred;
- future multi-device conflict resolution (V3 sync);
- source preference/automatic selection (V2);
- engine-specific serialization/version migration details.

No deferred item above requires making Source/Asset the owner of Progress or replacing typed anchors with a universal scalar.

### 4.13.22 ADR Requirement

No separate ADR is required for Q-PROG-001 semantics itself.

An ADR/technical spec may be appropriate when choosing:

- final Room encoding/migration strategy for typed progress;
- publication locator serialization/engine adapter;
- Media3 playback checkpoint/service ownership implementation;
- a compatibility algorithm whose persisted format would be expensive to change.

### 4.13.23 Research References

Primary/platform/library references:

- Android Media3 `ExoPlayer` / `Player` position, duration and playback state: https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer
- Android Activity/process persistence guidance: https://developer.android.com/reference/android/app/Activity
- Android architecture state persistence guidance: https://developer.android.com/topic/architecture/views/resources/runtime-changes-views
- Readium Kotlin Navigator — save/restore `currentLocator`: https://readium.org/kotlin-toolkit/3.2.0/guides/navigator/navigator/
- Readium Kotlin Locator locations/progression: https://readium.org/kotlin-toolkit/latest/api/readium/readium-shared/org.readium.r2.shared.publication/-locator/-locations/

Mature project evidence:

- Jellyfin user-data fields (`Played`, `PlaybackPositionTicks`, percentage): https://github.com/jellyfin/jellyfin-sdk-swift/blob/main/Sources/Entities/UpdateUserItemDataDto.swift
- Jellyfin resume/completion threshold behavior: https://github.com/jellyfin/jellyfin/pull/16969/files
- Kodi watched vs resume semantics: https://kodi.wiki/view/HOW-TO:Modify_automatic_watch_and_resume_points
- Kodi video DB resume/play-count schema: https://kodi.wiki/view/Databases/MyVideos
- Mihon chapter persistence (`read`, `last_page_read`): https://github.com/mihonapp/mihon/blob/main/data/src/main/sqldelight/tachiyomi/data/chapters.sq
- Komga Read progress: https://komga.org/docs/guides/read-progress/
- Komga Books/read-progress API: https://komga.org/docs/openapi/books/
- Komga read-progress/page-count design history: https://github.com/gotson/komga/issues/25
- Audiobookshelf media progress (`currentTime`, `progress`, `isFinished`): https://api.audiobookshelf.org/

Research note: external projects differ in thresholds, reset behavior and whether history timestamps live beside progress. This project adopts only the cross-media separation principles that preserve its existing `Progress != History` and canonical-target ownership invariants.

### 4.13.24 Stage D Partial Closure Summary

Stage D now begins with a coherent current-state model:

```text
ConsumptionTargetRef
        ↓ owns
Progress
├── typed precise resume anchor
├── explicit completion
├── optional representation provenance
└── operational freshness

History remains separate.
```

Critical invariants:

```text
precise resume anchor != generic percentage
resume anchor != completion
position value != version/freshness
representation context != progress owner
```

`Q-PROG-001` is **PROVISIONAL**.

**Historical handoff:** Q-PROG-002 was the next blocker after Q-PROG-001 and is now completed at **PROVISIONAL** level. Q-HIST-001, Q-LIB-001, Q-META-001, Q-RUN-001 and Q-BACK-001 are also now **PROVISIONAL**. Current next work is deep persistence/module/API design.


## 4.14 Decision Record — Q-PROG-002: Cross-Media Resume-Anchor Portability / Migration / Fallback

**Status:** `PROVISIONAL`

Q-PROG-002 completes the progress-position half of Stage D by deciding what happens when canonical progress survives but the representation used to resume changes. The central distinction is:

```text
canonical Progress ownership may remain stable
while
precise resume-anchor validity may change
```

The decision applies to local V1 first, but its semantics deliberately survive V2 source switching: a Movie, Episode, Chapter or Publication can retain the same `ConsumptionTargetRef` while the selected `SourceBinding`, `Asset`, `AssetRevision`, file format or provider representation changes.

### 4.14.1 Problem Statement

Q-PROG-001 already locked:

```text
Progress owner = ConsumptionTargetRef
resume anchor = typed precise position
completion = explicit durable state
Source / Asset context = provenance, not ownership
```

That leaves a separate compatibility problem.

Representative cases:

```text
Video
same Episode target
MKV encode A → MKV encode B
or theatrical cut → director's cut

Manga
same Chapter target
CBZ A: cover + 30 story pages
CBZ B: 30 story pages + bonus page

Publication
same Book target
EPUB revision R3 → EPUB revision R4
or EPUB → another publication format
```

A numeric anchor can remain syntactically valid while becoming semantically wrong:

```text
00:42:10 in cut A
!= guaranteed same story moment in cut B

page 17 in archive A
!= guaranteed same image/page in archive B

href + fragment in EPUB R3
!= guaranteed valid location in EPUB R4
```

Q-PROG-002 must therefore answer:

1. Which progress state survives representation change unconditionally?
2. What context is needed to judge precise anchor compatibility without making Asset/Source the progress owner?
3. When may an old anchor be reused exactly?
4. When may it be migrated to a new precise anchor?
5. When may normalized progression be used only as an approximate fallback?
6. What happens when compatibility is unknown or contradictory?
7. When is a migrated/fallback anchor allowed to replace the durable current anchor?
8. How do Q-REC `AssetRevision` and future V2 source switching participate without creating per-source progress namespaces?

### 4.14.2 Primary / Platform Findings

#### Android Media3 — playback time is relative to the currently loaded media timeline

Media3 exposes playback position and duration for the current content in milliseconds. Duration may be unknown, and the timeline can change when the source updates.

**Observed fact:** Media3 provides a position inside the current playable timeline; it does not claim that the same millisecond offset has semantic equivalence in another encode, cut or representation.

**Inference:** `positionMs` can be exact for the representation/timeline in which it was captured. Cross-representation reuse needs compatibility evidence; same canonical Episode/Movie identity alone is insufficient.

#### Readium — a Locator is publication/resource contextual

Readium Locator semantics include `href`/resource identity plus optional fragments, progression, position, total progression and text context. Readium publication services can attempt to locate an existing Locator and can locate a position from total progression.

**Observed fact:** Readium distinguishes a precise structured locator from a normalized publication progression. A locator references a resource in a publication, while total progression is another expression of location.

**Inference:** publication resume should first try precise-location compatibility/migration. `totalProgression` is useful fallback evidence, but it must not be promoted to an exact cross-revision locator merely because it is in `[0,1]`.

#### W3C EPUB multiple-rendition mapping — cross-representation correspondence is explicit, not automatic

The EPUB Multiple-Rendition specification defines an explicit rendition mapping document for identifying corresponding content locations across renditions and does not impose a universal mapping granularity.

**Observed fact:** even within the EPUB ecosystem, mapping a content location across renditions is an explicit relation when precise correspondence matters.

**Inference:** the app must not infer exact anchor portability from “same book” or “same format” alone. Explicit or strongly validated mapping is the correct architectural shape.

### 4.14.3 Mature Project Evidence

#### Jellyfin — one canonical video item can expose materially different versions

Jellyfin supports multiple versions of the same Movie/Episode, including labels such as resolution variants and director's cuts.

**Observed fact:** representations grouped under one logical video item can differ in more than bitrate/container; some can be different edits.

**Inference:** same canonical video target does not guarantee timeline equivalence. Duration similarity or common item identity cannot by itself authorize exact timestamp reuse.

#### Komga + KOReader — cross-system publication sync can lose precision

Komga's KOReader integration documents that pre-paginated books can sync at page precision, while regular EPUB progress may degrade to chapter-boundary precision when moving between systems. It also requires document matching for the book.

**Observed fact:** the same logical publication can have a precise local anchor that is not fully expressible/portable through another progress representation.

**Inference:** migration quality must be explicit. A lower-precision fallback must not masquerade as an exact anchor.

#### Mihon — page ordinal is durable current state but source-native content can evolve

Mihon persists chapter `last_page_read` independently of read/completion state. Its source architecture resolves pages from the current source representation.

**Inference:** page ordinal is a useful exact anchor only relative to an ordered page sequence. Different source/release page lists need validation or page mapping before ordinal reuse.

#### Kavita — page and EPUB-specific scroll markers coexist

Kavita progress includes a page number and, for EPUB, a `BookScrollId` to improve resume within content that combines multiple logical sections. Its KOReader integration converts between external progress encodings and Kavita's own page/scroll model; real issues also show that layout/page interpretation can produce inaccurate resume when the representation/model differs.

**Inference:** a portable progress system benefits from preserving the source anchor's representation context and from distinguishing exact migration from approximate conversion.

#### Readium locator/position services — precise and normalized location forms are deliberately separate

Readium offers a list of discrete publication positions, `locate(locator)` and `locateProgression(totalProgression)`.

**Inference:** a migration pipeline can preserve precise-first semantics while still offering a normalized fallback mechanism. This is preferable to collapsing every reader into a universal percentage.

### 4.14.4 Decision — Canonical Progress Survives; Precise Anchor Is Representation-Contextual

The core V1 rule is:

```text
same ConsumptionTargetRef
→ same canonical Progress owner

but

same ConsumptionTargetRef
↛ precise anchor automatically portable
```

`completion` and other canonical current-state semantics survive a Source/Asset change as long as canonical target identity remains the same.

The precise `resumeAnchor` is interpreted together with `resumeContext`.

Conceptually:

```text
Progress
├── target: ConsumptionTargetRef
├── resumeAnchor: TypedResumeAnchor?
├── completion: CompletionState
├── resumeContext: ResumeContext?
└── operational freshness
```

`resumeContext` does **not** create a second owner. It records enough representation provenance/compatibility facts to answer whether the current anchor can be applied to a newly resolved representation.

### 4.14.5 ResumeContext Semantics

Foundation-level context may include, when meaningful:

```text
ResumeContext
├── representation family / media kind
├── AssetId?                  // local/downloaded durable representation
├── AssetRevision?            // Q-REC representation revision
├── SourceBindingId?          // when no durable Asset exists / future V2
├── representation fingerprint/equivalence evidence?
├── extent snapshot           // duration/pageCount/etc. from Q-PROG-001
└── anchor/engine schema version when serialization semantics require it
```

Exact Kotlin shape/columns remain deferred.

Rules:

1. Context is provenance/compatibility data, not Progress identity.
2. No auth token, expiring URL, opened engine object or runtime session belongs in durable ResumeContext.
3. Same `AssetId + AssetRevision` is strong evidence for exact representation continuity, but the stored anchor encoding/schema must still be readable by the current adapter/engine.
4. Same `AssetId` with a different `AssetRevision` requires compatibility validation.
5. Different AssetIds may still be exact-equivalent when strong evidence proves byte/ordered-content equivalence.
6. Same SourceBinding alone does not prove the newly resolved content has an equivalent timeline/page sequence/publication structure.
7. If no durable Asset exists, the source adapter may expose stable revision/equivalence facts; absence of such facts means compatibility must remain conservative.

### 4.14.6 Compatibility Result Is Typed, Not a Fuzzy Global Score

V1 does not introduce a numeric cross-media confidence engine.

Conceptual result classes:

```text
EXACT_COMPATIBLE
MIGRATED_EXACT
APPROXIMATE_FALLBACK
INCOMPATIBLE
UNKNOWN
```

Names are provisional; semantics are what matter.

#### `EXACT_COMPATIBLE`

The existing typed anchor can be applied directly to the target representation with strong evidence that its coordinate system is unchanged/equivalent.

Typical evidence:

- same Asset + same AssetRevision **and** compatible anchor encoding/schema;
- byte-identical durable representation where anchor semantics are deterministic and the anchor encoding remains supported;
- explicit source/provider representation equivalence contract.

#### `MIGRATED_EXACT`

The old anchor itself cannot be applied verbatim, but a deterministic/validated mapping produces a new precise anchor in the target representation.

Examples:

- page fingerprints map old page 17 uniquely to new page 18 after a cover insertion;
- publication locator resolves through a valid resource/fragment/text mapping in the new revision;
- an explicit video timeline map converts an old timestamp to a corresponding new timestamp.

#### `APPROXIMATE_FALLBACK`

Only a lower-precision correspondence is available, such as normalized progression.

This result must remain explicitly approximate. It is not allowed to mutate evidence so that the resulting position is later described as an exact migration.

#### `INCOMPATIBLE`

Known conflict makes reuse unsafe, such as a different cut with materially different timeline semantics or a publication revision whose old resource target maps to unrelated content.

#### `UNKNOWN`

Evidence is insufficient. V1 treats unknown as **not safe for blind exact resume**.

### 4.14.7 Resume Resolution Pipeline

When a canonical target is about to be consumed through a resolved representation:

```text
Progress(target, old anchor + context)
        ↓
Resolve selected SourceBinding / representation
        ↓
Collect target representation context
        ↓
Compatibility evaluation
        ├── EXACT_COMPATIBLE → restore old precise anchor
        ├── MIGRATED_EXACT → restore mapped precise anchor
        ├── APPROXIMATE_FALLBACK → expose approximate restore plan
        ├── INCOMPATIBLE → do not auto-apply old anchor
        └── UNKNOWN → do not blind-reuse old anchor
```

The compatibility evaluator operates **after source selection/resolution** but before the Player/Reader blindly applies the old anchor.

This preserves Q-SRC's split:

```text
selection != resolution != resume compatibility
```

### 4.14.8 Exact Reuse Rules

Exact direct reuse is allowed when the anchor coordinate space is proven unchanged/equivalent.

Foundation examples:

```text
rename/move only
AssetId A, Revision R1
locator/path changes
→ exact compatible

same bytes copied to another AssetId
strong byte-equivalence proven
→ anchor may be exact compatible

same canonical target
new encode/revision
no timeline/page/resource equivalence proof
→ NOT exact by default
```

Important:

```text
same format      != exact compatibility
same duration    != exact compatibility
same page count  != exact compatibility
same target      != exact compatibility
same filename    != exact compatibility
```

These may be supporting evidence, not sufficient proof on their own.

### 4.14.9 Video Anchor Portability

For `VideoResume(positionMs)`:

#### Exact-safe cases

- Same representation revision after path/URI move/rename.
- Byte-equivalent representation where timeline semantics are unchanged.
- Explicitly declared equivalent media timeline.

#### Requires migration/validation

- New encode with changed intro/outro, frame rate/timestamp normalization or container timeline differences.
- Alternate cut/edition/version under the same canonical Movie/Episode.
- Source/provider replacement where stream composition is not guaranteed equivalent.

Rules:

1. `positionMs` is not transformed solely by `oldPosition / oldDuration * newDuration` and then labeled exact.
2. Similar duration may support an **approximate** fallback policy but does not establish semantic timeline equivalence.
3. Known alternate cuts/editions are conflict evidence against blind time reuse unless an explicit timeline map exists.
4. Completion remains canonical-target state even if the precise timestamp is unusable on the new representation.

### 4.14.10 Sequential-Image Anchor Portability

For `ImageSequenceResume(pageOrdinal)`:

#### Exact-safe cases

- Same ordered page sequence/revision.
- Byte-equivalent archive/folder snapshot with stable page ordering.

#### Migratable cases

A deterministic page mapping can migrate the anchor when page identity/order evidence is strong, for example unique per-page fingerprints or stable source-native page references.

Example:

```text
old pages: [cover, p1, p2, p3]
new pages: [new-cover, cover, p1, p2, p3]

old page p2 ordinal = 2
page mapping proves p2 → new ordinal 3
→ MIGRATED_EXACT
```

Rules:

1. Same `pageCount` alone is insufficient.
2. Clamping old page ordinal into `[0, newLastPage]` is not migration proof.
3. Page proportion may be an approximate fallback only when policy explicitly permits it.
4. Inserting/removing scans, covers, spreads, ads or bonus pages can invalidate ordinal equivalence without changing canonical Chapter identity.
5. Completion remains stable unless user/domain action changes it.

### 4.14.11 Publication Anchor Portability

For `PublicationResume(locator)`:

#### Same compatible publication revision

Use the precise locator directly when its resource/fragment coordinate space remains compatible.

#### Revision changes

Migration should be precise-first:

```text
old Locator
    ↓
try exact/current-publication locator resolution
    ↓ if resolvable + validated
new precise Locator
```

A publication adapter may use available locator signals such as:

- resource `href`;
- fragment/CFI/DOM range or equivalent;
- position/progression;
- textual before/highlight/after context;
- explicit rendition/revision mapping.

No single signal is universally mandatory; the adapter uses what its format/engine supports.

#### Normalized fallback

If precise migration fails but a reliable `totalProgression` exists, the reader may derive an approximate destination using the current publication's progression/positions service.

Rule:

```text
totalProgression fallback
!= exact migrated Locator
```

#### Cross-format representation

EPUB → PDF/other format does not inherit exact locator semantics by default. Exact migration requires an explicit content-location map. Otherwise only approximate fallback (if policy permits) or restart/no-auto-resume is valid.

### 4.14.12 Completion Portability

Completion belongs to the canonical target and is independent from precise anchor compatibility.

Therefore:

```text
same canonical target
+ Source/Asset/revision change
→ preserve completion
```

Examples:

- watched Movie stays `COMPLETED` when switching from 1080p to 4K;
- read Chapter stays `COMPLETED` when a CBZ is replaced by a corrected scan;
- completed Book stays `COMPLETED` when EPUB is updated.

A representation change does **not** silently flip completion back to `IN_PROGRESS` merely because the old precise anchor cannot be restored.

If reconciliation determines the canonical target itself was wrong and remaps to another Media/Unit, that is an identity/mapping transition, not Q-PROG-002 portability.

### 4.14.13 Approximate Fallback Is Non-Destructive

An approximate result is a **restore plan**, not an immediate destructive rewrite of Progress.

Rules:

1. Merely opening a new representation and computing an approximate destination does not overwrite the stored precise anchor/context.
2. If the user actually consumes from the new representation, the normal Q-PROG-001 checkpoint flow may establish a new precise current anchor/context.
3. Deterministic `MIGRATED_EXACT` may be persisted after successful validation/application, subject to stale-write/causal revision rules.
4. `UNKNOWN` or `INCOMPATIBLE` must not clear canonical Progress or completion.
5. The exact UI for “resume approximately / start over / choose source” is deferred, but domain semantics must expose enough quality/reason information for UI/policy to make that choice without parsing exceptions.

This rule preserves the ability to switch back to an older compatible representation before the user establishes newer progress.

### 4.14.14 Source Switching Does Not Create Per-Source Progress

V2 source switching must still converge on one canonical Progress state:

```text
Target T
Progress(T)

Local binding A
Provider binding B
Downloaded binding C
```

Switching A → B performs resume compatibility evaluation; it does not create `Progress(T, A)` and `Progress(T, B)` as separate truths.

Representation-specific provenance may change as the user establishes progress on another source, but ownership remains `T`.

### 4.14.15 Relationship to AssetRevision / Reconciliation

Q-REC introduced `AssetRevision` semantics so material replacement can be recognized without changing canonical Media/Unit identity.

Q-PROG-002 uses this distinction:

```text
same AssetId + same Revision
→ strong exact-continuity evidence

same AssetId + new Revision
→ validate/migrate/fallback

new AssetId + same target
→ validate/migrate/fallback
```

Q-PROG-002 does not decide whether a replacement deserves a new revision or new AssetId; Q-REC owns that lifecycle classification.

Conversely, Q-REC does not decide whether an old video/page/publication anchor remains usable after that transition; Q-PROG-002 owns resume compatibility.

### 4.14.16 Persistence Semantics

Foundation-level durable requirements:

**Persist canonical truth:**

- `ConsumptionTargetRef` owner;
- explicit completion;
- current typed precise anchor when one exists;
- enough resume context/provenance to validate future restore;
- operational revision/freshness needed to reject stale writes.

**May persist supporting compatibility evidence/cache:**

- Asset/AssetRevision ref;
- extent snapshot;
- representation hash/fingerprint reference;
- anchor schema/engine version;
- last successful migration basis when useful for diagnostics.

**Do not persist as canonical truth:**

- a guessed percentage converted to an “exact” anchor without validation;
- expiring URL/session/token;
- runtime reader/player object;
- every historical per-source anchor merely to avoid portability design.

Exact normalized schema/serialization remains persistence-design work after remaining Stage D semantics are closed.

### 4.14.17 Failure Semantics

Resume compatibility must produce typed/domain-meaningful failure/reason state, for example:

```text
anchor format unsupported
representation revision changed
resource no longer exists
page mapping ambiguous
known alternate edition/cut
insufficient compatibility evidence
migration resolver failed
```

Upper layers must not parse Media3/Readium/provider exception strings to decide whether exact resume is safe.

Failure to migrate an anchor is **not data loss**:

```text
migration failure
→ preserve canonical Progress + completion
→ return a non-exact/unavailable restore result
```

### 4.14.18 Representative Scenarios

#### Video — file moved only

```text
Target: Movie A
AssetId: X
Revision: R1
old URI → new URI
position = 2_400_000 ms
```

Exact restore is allowed because representation revision is unchanged; path/URI relocation is irrelevant to the media timeline.

#### Video — 1080p encode replaced by director's cut

```text
Target: Movie A
old AssetRevision R1 = theatrical
new AssetRevision R2 = director's cut
```

Do not blindly seek to the same millisecond and do not proportional-scale it as exact. Preserve completion; precise resume requires explicit timeline mapping or remains approximate/unavailable.

#### Manga — corrected archive inserts a cover

```text
Chapter target unchanged
old page ordinal = 12
new archive inserted one page before story pages
```

If page fingerprints/source identifiers uniquely map the old page to new ordinal 13, use `MIGRATED_EXACT`. Same page count/nearby ordinal alone is insufficient.

#### Manga — provider B has ads/bonus pages

Same Chapter target through another SourceBinding does not make page 12 portable. Evaluate ordered-page equivalence or use non-exact fallback policy.

#### EPUB — file moved only

Same AssetRevision: restore old Locator directly after normal reader validation.

#### EPUB — updated revision keeps href/fragment

If the current publication can resolve and validate the old structured Locator, produce a migrated/current Locator and resume precisely.

#### EPUB — XHTML paths/structure replaced

If precise locator migration fails but total progression is available, an approximate progression fallback may be offered. It must remain tagged approximate and must not overwrite the old precise anchor merely because the book opened.

#### EPUB → another format

Same canonical Book target retains completion, but no exact anchor portability is assumed. Explicit cross-format mapping is required for exact migration.

#### Switch to incompatible representation, then back immediately

Opening representation B does not destroy anchor context for A. If the user does not establish new progress and switches back to compatible A, exact restore remains possible.

### 4.14.19 Rejected Alternatives

#### Alternative A — Same canonical target means the anchor is always portable

Rejected because target identity and representation coordinate system are different concerns. Director's cuts, changed page sequences and EPUB resource revisions are direct counterexamples.

#### Alternative B — Move Progress ownership down to Asset/Source

Rejected because it recreates per-source progress namespaces and breaks the V2 unified-progress invariant. Representation context is sufficient for compatibility without changing ownership.

#### Alternative C — Convert every anchor to a universal percentage and discard the precise anchor

Rejected because normalized percentage loses exact video/page/publication position and can still be wrong across structural changes.

#### Alternative D — Always scale/clamp numerically to the new extent

Rejected because numeric validity is not semantic correspondence. `min(oldPage, newLastPage)` or proportional time scaling can land on unrelated content.

#### Alternative E — Reset progress on every AssetRevision change

Rejected because many revisions are compatible enough for exact reuse or deterministic migration, and reset would unnecessarily lose user state.

#### Alternative F — Store one independent progress record per representation forever

Rejected for V1/V2 canonical semantics. It complicates source switching, creates divergent truths and pushes reconciliation into UI. Historical/session information belongs elsewhere if a later use case requires it.

#### Alternative G — Global fuzzy numeric compatibility score

Rejected for V1. Compatibility should be based on typed media-specific evidence and explicit result classes, matching Q-ID's conservative non-global-score direction.

### 4.14.20 Downstream Impact

Q-PROG-002 unlocks these assumptions:

```text
completion is portable with canonical target identity
precise anchor is representation-contextual
ResumeContext is provenance, not owner
same revision strongly supports exact reuse
revision/source/Asset changes require compatibility evaluation
exact migration and approximate fallback are distinct
normalized progression never silently becomes exact truth
failed migration preserves Progress
source switching keeps one canonical Progress namespace
```

Progress persistence/API design may now rely on a typed anchor plus compatibility context without inventing per-source progress rows.

Q-HIST-001 below resolves History semantics; `Q-LIB-001` subsequently completes Stage D user-state semantics at PROVISIONAL level.

### 4.14.21 Verification Ideas

Future tests should prove at least:

1. Rename/move with unchanged AssetRevision restores the exact Video anchor.
2. Same target + same revision does not lose completion/source-independent Progress.
3. Director's cut/new timeline does not blindly reuse old `positionMs`.
4. Duration-only similarity cannot classify an anchor `EXACT_COMPATIBLE`.
5. Byte-identical representation under another Asset can reuse exact anchor when equivalence is proven.
6. Manga same ordered page sequence preserves page ordinal.
7. Inserted cover with unique page mapping migrates old ordinal to the mapped new ordinal.
8. Same page count with reordered pages does not authorize exact resume.
9. Clamping an out-of-range old page is never labeled exact migration.
10. Publication old Locator restores directly on same compatible revision.
11. Publication revision with resolvable old locator produces `MIGRATED_EXACT`/current precise locator.
12. Failed precise publication migration can yield an explicitly approximate progression fallback.
13. Approximate fallback does not overwrite durable precise anchor merely by opening the new representation.
14. EPUB → other format never claims exact portability without mapping.
15. Completion survives an incompatible anchor transition.
16. Unknown compatibility returns no blind exact seek/page/locator.
17. Migration failure preserves canonical Progress rather than deleting/resetting it.
18. Switching to another source does not create a second authoritative Progress row.
19. Switching back before new consumption can still use the previous exact-compatible anchor.
20. Once the user actually consumes on the new representation, normal checkpointing establishes new current anchor/context with causal stale-write protection.
21. Same AssetId + new AssetRevision invokes compatibility evaluation rather than assuming either reset or exact reuse.
22. Runtime/expiring transport data never appears in durable ResumeContext.

### 4.14.22 Remaining Risks / Deferred Details

Q-PROG-002 intentionally does **not** lock:

- exact hash/fingerprint/page-mapping algorithm;
- exact duration tolerance or rules for considering two video encodes timeline-equivalent;
- exact automatic-vs-user-confirmed policy for approximate fallback;
- exact cross-format publication mapping technology;
- exact Readium/reader-engine serialization details;
- exact persisted `ResumeContext` columns/type names;
- editions/cuts canonical-domain modeling beyond current V1 scope;
- History meaningful-session semantics are resolved by `Q-HIST-001`; exact future reread/rewatch pass/count modeling remains deferred;
- `Q-LIB-001` resolves Library membership and membership-gating for Continue; exact ranking/next-item projection remains deferred;
- future cloud multi-device anchor conflict resolution (V3 Sync).

No deferred item requires per-source progress ownership or blind percentage reuse.

### 4.14.23 ADR Requirement

No standalone ADR is required for the semantic rule itself.

A technical spec/ADR is appropriate if implementation chooses a compatibility/migration format with high migration cost, for example:

- persisted page-fingerprint mapping;
- video timeline alignment data;
- publication cross-format location map;
- engine-specific serialized locator schema with versioning guarantees.

### 4.14.24 Research References

Primary/platform/specification references:

- Android Media3 `Player` — current position, duration and timeline semantics: https://developer.android.com/reference/androidx/media3/common/Player
- Readium Locator architecture: https://readium.org/architecture/models/locators/
- Readium Kotlin publication locator/position services: https://readium.org/kotlin-toolkit/2.3.0/readium/readium-shared/readium-shared/org.readium.r2.shared.publication.services/
- Readium locator best practices by format: https://readium.org/architecture/models/locators/best-practices/format.html
- W3C EPUB Multiple-Rendition Publications — explicit rendition mappings: https://www.w3.org/TR/epub-multi-rend-11/
- W3C EPUB 3.3: https://www.w3.org/TR/epub-33/

Mature project evidence:

- Jellyfin multiple video versions: https://jellyfin.org/docs/general/server/media/movies/#multiple-versions-of-a-movie
- Komga read progress: https://komga.org/docs/guides/read-progress/
- Komga ↔ KOReader progress behavior: https://komga.org/docs/guides/koreader/
- Mihon chapter persistence (`read`, `last_page_read`): https://github.com/mihonapp/mihon/blob/main/data/src/main/sqldelight/tachiyomi/data/chapters.sq
- Kavita Progress DTO (`PageNum`, `BookScrollId`): https://github.com/Kareadita/Kavita/blob/develop/Kavita.Models/DTOs/Progress/ProgressDto.cs
- Kavita KOReader progress conversion/hash matching: https://github.com/Kareadita/Kavita/blob/develop/Kavita.Services/KoreaderService.cs

Research note: project-specific behavior is evidence of portability/failure modes, not a contract to copy. Primary format/runtime specifications receive higher weight for anchor semantics themselves.

### 4.14.25 Stage D Progress-Portability Closure Summary

Stage D now has a coherent two-part progress model:

```text
Q-PROG-001
current canonical progress state
├── typed precise anchor
└── explicit completion
        ↓
Q-PROG-002
representation compatibility
├── exact reuse
├── exact migration
├── approximate fallback
└── incompatible / unknown
```

Critical invariants:

```text
canonical target continuity != anchor-coordinate continuity
completion portability != precise-anchor portability
approximate fallback != exact migration
resume context != progress owner
migration failure != progress deletion
```

`Q-PROG-001` and `Q-PROG-002` are **PROVISIONAL** and mutually consistent with Q-REC `AssetRevision` semantics.

**Historical handoff:** `Q-HIST-001` subsequently resolves Basic History semantics at **PROVISIONAL** level; `Q-LIB-001` then completes Stage D and `Q-META-001` completes Stage E at **PROVISIONAL** level. Q-BACK-001 is now **PROVISIONAL**; Stages A–F are complete for deep persistence/module/API design.

## 4.15 Decision Record — Q-HIST-001: Basic History / Meaningful Consumption Activity

**Status:** `PROVISIONAL`

Q-HIST-001 tiếp tục Stage D bằng cách xác định **History ghi lại điều gì đã xảy ra**, khác với `Progress` đang lưu **state hiện tại** của một canonical consumption target. Mục tiêu V1 là có Basic History đủ trung thực cho “đã xem/đọc gì và khi nào” mà không biến every progress checkpoint thành event, không giả manual state change thành consumption, và không khóa app vào event-sourcing architecture.

### 4.15.1 Problem Statement

Sau Q-PROG-001/002, app đã có một current durable `Progress` cho mỗi `ConsumptionTargetRef`:

```text
Progress
= current resume/completion state now
```

Nhưng các use case History khác bản chất:

```text
User watched half a movie yesterday
User read Chapter 12 this morning
User reopened the same book tonight
User completed a movie, then rewatched part of it later
User manually marks an item watched without actually consuming it
```

Nếu `History = Progress.updatedAt`, mỗi checkpoint mới sẽ overwrite chronology cũ. Nếu mỗi seek/page/locator checkpoint trở thành một event, V1 sẽ tạo write volume và schema complexity giống event sourcing. Nếu “Mark watched/read” được ghi như một viewing/reading session, history sẽ không còn phản ánh consumption thực tế.

Q-HIST-001 cần trả lời:

1. History entry đại diện event/activity gì?
2. Khi nào một consumption activity đủ meaningful để tạo History?
3. Relation giữa một mutable Progress và nhiều historical activities là gì?
4. Manual mark/reset/clear history tương tác thế nào?
5. Reread/rewatch có thể được giữ đường phát triển mà không giả `COUNT(history rows)` là số lần consume hoàn chỉnh?
6. Process death/source switching có được phép làm duplicate/lossy history semantics không?

### 4.15.2 Cross-Project Research Findings

Question này không có một Android/W3C standard quy định History domain semantics, nên research dùng official project documentation/source và mature implementations ở nhiều media families.

#### Plex — current watch state and watch history are distinct

Plex documentation phân biệt trực tiếp:

- **watch state** = current watched/unwatched value;
- **watch history** = ongoing log of watch-state/watch activities over time.

Plex còn cho phép xóa Watch History mà không xóa Watch State, chứng minh hai loại data có lifecycle riêng. Tuy nhiên Plex cũng cho phép manually marking watched tạo history activity trong một số flows.

**Observed fact:** current state và chronology có thể được tách, nhưng Plex intentionally mixes some manual state actions into its history model.

**Inference:** project này nên giữ separation nhưng không copy conflation manual-mark-as-consumption vì V1 muốn Basic History phản ánh consumption activity thực tế.

References:

- https://support.plex.tv/articles/sync-watch-state-and-ratings/
- https://support.plex.tv/articles/profile/
- https://support.plex.tv/articles/201018487-mark-as-watched-or-unwatched/

#### Kodi — aggregate watched/resume data is not a session history

Kodi MyVideos database lưu `playCount`, `lastPlayed`, `resumeTimeInSeconds` và `totalTimeInSeconds`. Watched state và resume point là distinct aggregate/current-style values; import/export cũng xử lý watched state và resume points riêng.

**Observed fact:** aggregate counters/timestamps đủ cho watched/resume UX cơ bản nhưng không mô tả từng viewing session.

**Inference:** `lastPlayed`/play count không nên được dùng làm substitute cho HistoryEntry nếu product đã tuyên bố có Basic History.

References:

- https://kodi.wiki/view/Databases/MyVideos
- https://kodi.wiki/view/Import-export_library/Video
- https://kodi.wiki/view/Video_management

#### Audiobookshelf — progress and listening sessions are separate stores/concerns

Audiobookshelf expose `Media Progress` cho current book/podcast progress, đồng thời expose/list **Listening Sessions** riêng. Documentation nói statistics được tính từ listening sessions và khuyến nghị giữ sessions để stats chính xác.

**Observed fact:** one current progress record có thể coexist với many historical session records.

**Inference:** session-summary model phù hợp với History mà không yêu cầu event-per-tick.

References:

- https://api.audiobookshelf.org/
- https://audiobookshelf.org/docs/documentation/server-management/listening-sessions/

#### Mihon — current chapter state alone is lossy for full reread chronology

Mihon chapter persistence có `read` và `last_page_read` trên chapter. Một history feature request mô tả History UI hiện ưu tiên chapter được touch gần nhất của mỗi series và người dùng không dễ xem full chapter chronology/reread trail.

**Observed fact:** chapter current state và lightweight recency can support reader UX nhưng không tự tạo một complete chronological activity model.

**Inference:** V1 nên giữ separate target-scoped HistoryEntry thay vì cố reconstruct lịch sử từ `read`, `last_page_read` hoặc last-touch timestamp.

References:

- https://github.com/mihonapp/mihon/blob/main/data/src/main/sqldelight/tachiyomi/data/chapters.sq
- https://github.com/mihonapp/mihon/issues/1139

#### Jellyfin — explicit proposal documents the cost of conflating aggregate watch state and history

A 2026 Jellyfin architecture proposal mô tả current `UserData` played fields như lossy aggregate và đề xuất first-class playback history với one row per playback session; proposal cũng tách “mark as played/unplayed” thành override thay vì fabricate/destroy play history.

**Observed fact:** đây là **proposal**, không phải evidence rằng Jellyfin production hiện đã dùng architecture này. Giá trị của nó là documented failure analysis trên mature media-server model.

**Inference:** project này có thể áp dụng distinction “what happened” vs “what is true now” ngay từ V1 mà không cần copy toàn bộ telemetry/session schema của proposal.

Reference:

- https://github.com/jellyfin/jellyfin-meta/discussions/136

### 4.15.3 Provisional Decision — History Is an Append-Oriented Meaningful Activity Summary

V1 định nghĩa:

```text
Progress
= mutable current durable state for one ConsumptionTargetRef

HistoryEntry
= durable summary that meaningful consumption activity
  occurred for one ConsumptionTargetRef during a bounded activity session
```

History **không** là event sourcing log của mọi state transition.

Conceptual minimum shape:

```text
HistoryEntry
├── HistoryEntryId                 // app-owned record identity
├── target: ConsumptionTargetRef   // MediaTarget or UnitTarget
├── activityStartedAt
├── activityEndedAt / lastActiveAt
├── optional activity summary      // only facts worth preserving
├── optional source/representation context for provenance/diagnostics
└── session/correlation identity sufficient for idempotency
```

Exact Kotlin names, timestamps, Room columns, open-session persistence strategy và optional summary fields chưa locked.

### 4.15.4 Canonical Target Owns History Semantics

History entry target là canonical consumption identity:

```text
Movie
→ HistoryEntry(MediaTarget(movieId))

Episode
→ HistoryEntry(UnitTarget(episodeId))

Manga chapter
→ HistoryEntry(UnitTarget(chapterId))

Standalone publication
→ HistoryEntry(MediaTarget(bookId))
```

Rules:

- Source/Binding/Asset/AssetRevision không làm History owner.
- Source switching không tạo một history namespace mới.
- Missing/unavailable representation không xóa existing History.
- Parent Series/Manga không có duplicate authoritative History entry chỉ vì child Unit được consume; series-level “recent activity” là projection từ child history/canonical ordering nếu UX cần.

### 4.15.5 Meaningful Consumption, Not Mere Open/Navigation

Một HistoryEntry chỉ tồn tại khi có **meaningful consumption evidence**.

Representative signals có thể gồm:

```text
Video
→ actual playback/meaningful watched interval or terminal playback activity

Manga / sequential image
→ meaningful page reading/navigation activity for the target

Publication
→ meaningful reading/navigation activity inside the publication target
```

Không đủ evidence chỉ vì:

- detail screen được mở;
- reader/player object được constructed;
- Source resolved thành công;
- asset được probed;
- user scrub/seek một lần nhưng không có meaningful consumption;
- app chỉ restore old Progress rồi đóng ngay.

Exact thresholds (seconds, pages, inactivity window) là media-specific technical/product policy và chưa khóa ở foundation.

### 4.15.6 Session Semantics

`HistoryEntry` đại diện **bounded activity session summary**, không đại diện every checkpoint.

A logical activity session:

- chỉ thuộc một `ConsumptionTargetRef`;
- có thể chứa nhiều Progress checkpoints;
- có thể partial, không cần completion;
- pause ngắn không mặc định tạo session mới;
- target change kết thúc target-scoped session hiện tại và activity ở target mới có entry riêng;
- Activity/Compose screen recreation không tự định nghĩa session boundary;
- source/representation switch không tự đổi History owner.

Ví dụ một manga reader runtime session đọc Chapter 10 → 11 → 12 có thể tạo ba target-scoped HistoryEntries, vì canonical consumption target thay đổi dù UI reader chưa đóng.

Exact inactivity/session-timeout policy và việc runtime segments có được coalesce qua brief interruptions hay không là implementation policy sau.

### 4.15.7 Progress Checkpoints Do Not Create History Rows

Flow đúng:

```text
consumption session starts
        ↓
meaningful activity occurs
        ↓
0..N Progress checkpoints
        ↓
session summary is updated/finalized idempotently
        ↓
1 HistoryEntry for that target-scoped activity session
```

Không làm:

```text
position tick/page movement/locator callback
        ↓
INSERT HistoryEntry every time
```

Progress checkpoint frequency và History entry cardinality là independent concerns.

### 4.15.8 History Does Not Reconstruct Current Progress

Current state vẫn thuộc Progress:

```text
Progress(target)
= current resume anchor + current completion
```

History entries có thể chứa historical anchors/outcomes/provenance nếu use case chứng minh cần, nhưng chúng **không trở thành restore source of truth** cho current resume.

Do đó:

```text
latest HistoryEntry.endAnchor
!= automatically Progress.resumeAnchor

COUNT(HistoryEntry)
!= Progress completion
```

Một old HistoryEntry cũng không được rewrite chỉ vì current Progress thay đổi sau này.

### 4.15.9 Manual State Changes Are Not Consumption History

V1 khóa rule:

```text
MarkRead / MarkWatched / MarkCompleted
→ mutate explicit current user/progress state as allowed by Q-PROG
→ DO NOT fabricate a consumption HistoryEntry
```

Tương tự:

```text
MarkUnread / MarkUnwatched / ResetProgress
→ current state change
→ does not erase past consumption History
```

Nếu future product cần audit “user manually changed watched state”, đó là **user-action/audit semantics khác**, không được masquerade thành viewing/reading session.

### 4.15.10 Completion During a Session Is Historical Context, Not Current Truth

Một session có thể ghi optional fact như “terminal/end reached during this activity” nếu consumption engine có evidence đáng tin. Nhưng:

```text
History session outcome
!= current Progress.completion source of truth
```

Ví dụ user đã completed Movie A, sau đó rewatch 20 phút:

```text
Progress(Movie A)
completion = COMPLETED
resumeAnchor = newer rewatch position

History
entry 1 = earlier completed viewing activity
entry 2 = later partial rewatch activity
```

Không cần clear completion chỉ để History ghi nhận rewatch activity.

### 4.15.11 Clear / Reset Symmetry

Hai durable domains có lifecycle độc lập:

```text
ResetProgress(target)
!= ClearHistory(target)

ClearHistory(target/all)
!= ResetProgress(target)
```

User có thể muốn:

- giữ watched/read state nhưng xóa activity history;
- reset current progress nhưng vẫn giữ record rằng trước đây đã consume;
- remove một erroneous history entry mà không ảnh hưởng current resume.

Exact UI/confirmation semantics để sau Library/Settings design.

### 4.15.12 Source, Asset and Library Independence

History persists as app-owned user data independent from external availability:

```text
Asset MISSING
StorageRoot ACCESS_LOST
Source unavailable
Binding unresolved
```

không tự xóa History.

Library membership cũng không phải history existence condition. `Q-LIB-001` subsequently confirms Removing a Media from Library does not delete History; exact History-screen filtering remains a query/UX choice.

Exact canonical-target tombstone/snapshot strategy nếu target itself later gets permanently purged thuộc persistence/retention design; History không được FK-cascade accidentally chỉ vì representation/library membership disappears.

### 4.15.13 Process Death, Duplicate Callbacks and Idempotency

History semantics phải chịu Android process/runtime failure:

- clean `onStop`/screen disposal không được là cơ hội duy nhất để History tồn tại;
- repeated stop/finalize callbacks không được tạo duplicate session entries;
- process death giữa activity có thể để lại partial meaningful session mà later recovery/finalization xử lý idempotently;
- Activity recreation không tự tạo another history entry khi consumption session logical vẫn tiếp tục;
- Playback Service ownership có thể sống ngoài Activity, vì vậy UI lifecycle không phải History lifecycle.

Foundation chỉ khóa **semantic requirement**. Exact durable open-session checkpointing, correlation token, crash recovery và WorkManager/service mechanics để technical/runtime design quyết.

### 4.15.14 Rewatch / Reread Counts Are Not `COUNT(HistoryEntry)`

Basic History lưu activity sessions, không hứa “one row = one complete consumption pass”.

```text
one full movie watch
may span 3 sessions

one reading session
may only cover part of one chapter/book

one reread pass
may span many target/session entries
```

Vì vậy:

```text
rewatchCount != COUNT(video HistoryEntry)
rereadCount  != COUNT(reading HistoryEntry)
```

V3 Advanced Library có thể thêm explicit `ConsumptionPass`, completion-occurrence grouping hoặc derived statistics khi use case thật sự tồn tại. V1 không pre-model một generic pass engine.

HistoryEntry phải đủ trung thực để không **ngăn** future analysis, nhưng foundation không tuyên bố current Basic History có thể reconstruct exact rewatch/reread counts.

### 4.15.15 Representative Scenarios

#### Movie watched halfway and app closed

Meaningful playback occurred:

```text
Progress(Movie A)
→ current position

HistoryEntry #1
→ Movie A
→ activity time window
→ partial activity summary if retained
```

One session, many player checkpoints, one HistoryEntry.

#### Movie completed yesterday, partially rewatched today

```text
HistoryEntry yesterday = completed/terminal activity context
HistoryEntry today     = partial rewatch activity
Progress now           = COMPLETED + today's current resume anchor
```

No contradiction.

#### User presses “Mark watched” without playback

```text
Progress completion/user state changes
HistoryEntry count unchanged
```

#### Manga Chapter 12 opened but no meaningful reading occurs

No HistoryEntry solely because Reader opened/restored the old page.

#### Manga reads Chapter 12 then Chapter 13

Two canonical targets → two HistoryEntries, even if one continuous Reader UI session.

#### Standalone EPUB read in three evenings

One `MediaTarget(bookId)` may have three HistoryEntries and one current Progress record.

#### Switch Local → Downloaded representation while reading same target

No new history namespace. Source/Asset may appear as optional provenance, not owner.

#### Reset progress after finishing a book

Current Progress resets according to Q-PROG policy; historical reading entries remain until explicit History clear.

### 4.15.16 Rejected Alternatives

#### Alternative A — `History = Progress.updatedAt`

Rejected because it keeps only latest mutation, loses chronology/reread activity, and operational persistence freshness is not consumption history.

#### Alternative B — Insert one History row per Progress checkpoint

Rejected because it creates write amplification, duplicates low-level engine noise and effectively turns V1 into event sourcing without product value.

#### Alternative C — History only when item becomes COMPLETED

Rejected because partial viewing/reading is meaningful history and Basic History should not erase abandoned/interrupted consumption.

#### Alternative D — Manual Mark Watched/Read creates a fake consumption session

Rejected because user intent/state change does not prove consumption occurred. This project intentionally differs from systems that conflate manual watched activity with viewing history.

#### Alternative E — Keep only `playCount/readCount + lastDate`

Rejected as the complete History model because aggregates cannot answer chronology or preserve multiple activities. Aggregates may later be derived/materialized projections.

#### Alternative F — History keyed by Source/Asset

Rejected because source switching would fragment one canonical user's activity and missing/replaced representations would threaten durable user history.

#### Alternative G — One History row equals one reread/rewatch

Rejected because a complete consumption pass may span multiple sessions/targets, and one session may be partial.

#### Alternative H — Full immutable event sourcing for every player/reader action

Rejected for V1. The product needs Basic History, not a replayable event log of every seek/page/gesture.

### 4.15.17 Downstream Impact

Q-HIST-001 unlocks these assumptions:

```text
Progress = current mutable state.
History = append-oriented meaningful activity summaries.
History target = ConsumptionTargetRef.
One session may contain many Progress checkpoints.
Partial consumption can create History.
Manual mark watched/read does not fabricate consumption history.
Reset Progress does not clear History.
Clear History does not reset Progress.
Source/Asset/Library availability does not own History.
History entries are not direct rewatch/reread counts.
History session finalization must be idempotent/process-death tolerant.
```

Historical Q-HIST closure note: Q-LIB-001, Q-META-001 and Stage F later completed the blockers that unlocked deep persistence/module/API design. R4.14 has now completed that downstream design.

Historical note: at Q-HIST closure production persistence was still deferred. R4.14 `Q-PER-001` has now completed that downstream design under the finished Stage D–F invariants.

### 4.15.18 Verification Ideas

Future tests should prove at least:

1. Ten Progress checkpoints in one target-scoped activity session do not create ten HistoryEntries.
2. Partial video consumption can create History without completion.
3. Opening player/reader and immediately closing without meaningful activity creates no HistoryEntry.
4. Manual Mark Watched changes current state without creating a consumption HistoryEntry.
5. Manual Mark Unwatched/ResetProgress does not delete existing History.
6. Clearing History does not alter current Progress.
7. Completed Movie can have a later partial rewatch HistoryEntry while completion remains `COMPLETED`.
8. Two distinct meaningful sessions for the same target create two HistoryEntries.
9. Reading Chapter 12 then Chapter 13 creates target-scoped entries for each Unit.
10. Standalone publication may accumulate multiple HistoryEntries while retaining one current Progress.
11. Source switch for same target does not create a separate history namespace.
12. Asset MISSING / StorageRoot ACCESS_LOST preserves History.
13. Removing from Library does not automatically delete History.
14. Activity/Compose recreation does not by itself duplicate History.
15. Repeated session-finalize callback is idempotent.
16. Process death after meaningful activity does not require pretending no activity happened merely because UI did not cleanly close.
17. A finalized old HistoryEntry does not mutate when current Progress later changes.
18. `COUNT(HistoryEntry)` is not exposed as authoritative rewatch/reread count.
19. Series-level recent/history projection can derive from child Unit history without parent duplicate truth rows.
20. Manual history deletion/correction does not cascade into SourceBinding/Asset/Progress.

### 4.15.19 Remaining Risks / Deferred Details

Q-HIST-001 intentionally does **not** lock:

- exact meaningful-activity threshold per media kind;
- exact inactivity/session timeout;
- whether open session is persisted as mutable row then finalized or journaled through another mechanism;
- exact start/end anchor snapshot fields in HistoryEntry;
- exact active-duration accounting and pause/seek exclusion;
- exact device/client/source provenance retained for stats;
- exact retention/pruning policy;
- exact history-edit/delete UX;
- exact target snapshot/tombstone strategy if canonical target is explicitly purged;
- exact future reread/rewatch `ConsumptionPass` semantics;
- Library membership/Recently Added semantics are resolved by `Q-LIB-001`; exact Continue ranking/UI remains deferred;
- metadata/user-override precedence is now governed by `Q-META-001`;
- exact persistent-session storage/checkpoint details under the now-resolved `Q-RUN-001` runtime ownership rules;
- backup/export implementation details under `Q-BACK-001` semantics;
- V3 sync conflict/history merge semantics.

No deferred item requires History to become Progress, Source-owned state or event sourcing.

### 4.15.20 ADR Requirement

No standalone ADR is required for the semantic distinction itself.

A technical spec/ADR becomes appropriate if implementation chooses a high-cost durable session mechanism, long-term telemetry schema, analytics retention model or cross-device history merge strategy.

### 4.15.21 Research References

Official/project documentation and source:

- Plex — Sync Watch State and Ratings / watch state vs history: https://support.plex.tv/articles/sync-watch-state-and-ratings/
- Plex — Profile / Watch History semantics: https://support.plex.tv/articles/profile/
- Plex — Mark Watched or Unwatched: https://support.plex.tv/articles/201018487-mark-as-watched-or-unwatched/
- Kodi — MyVideos database (`playCount`, `lastPlayed`, resume fields): https://kodi.wiki/view/Databases/MyVideos
- Kodi — Watched state import/export: https://kodi.wiki/view/Import-export_library/Video
- Audiobookshelf — API / Media Progress + Listening Sessions: https://api.audiobookshelf.org/
- Audiobookshelf — Listening Sessions: https://audiobookshelf.org/docs/documentation/server-management/listening-sessions/
- Mihon — chapter current state (`read`, `last_page_read`): https://github.com/mihonapp/mihon/blob/main/data/src/main/sqldelight/tachiyomi/data/chapters.sq
- Mihon — full reading-history request documenting lightweight-history limitation: https://github.com/mihonapp/mihon/issues/1139
- Jellyfin — playback history/watch statistics proposal and current aggregate-state failure analysis: https://github.com/jellyfin/jellyfin-meta/discussions/136

Research note: Plex manual-watch activity and Jellyfin proposal semantics are not copied wholesale. They are counterexamples/evidence used to distinguish user-state mutation, current aggregate state and actual consumption history. Audiobookshelf session statistics are evidence for session separation, not a requirement that V1 store telemetry-heavy device/codec data.

### 4.15.22 Stage D History Closure Summary

Stage D now has three coherent provisional pieces:

```text
Q-PROG-001
current canonical Progress
        ↓
Q-PROG-002
resume-anchor portability / migration / fallback
        ↓
Q-HIST-001
meaningful historical consumption activity
```

Critical separation:

```text
what is true now
= Progress

what happened
= History

manual user-state action
!= consumption activity

History session
!= every checkpoint
!= exact reread/rewatch pass
```

`Q-HIST-001` is **PROVISIONAL** and consistent with Q-PROG current-state ownership, Q-REC availability/lifecycle semantics and Android process-lifecycle requirements.

**Historical handoff:** `Q-LIB-001` subsequently completes Stage D Library semantics and `Q-META-001` completes Stage E metadata semantics at **PROVISIONAL** level. Q-BACK-001 is now **PROVISIONAL**; Stages A–F are complete for deep persistence/module/API design.



## 4.16 Decision Record — Q-LIB-001: Library Membership / User Intent / Recently Added

**Status:** `PROVISIONAL`

Q-LIB-001 hoàn tất Stage D bằng cách xác định **Library membership là user/app state nào**, quan hệ của nó với registered storage roots/discovery ra sao, explicit remove phải survive rescan thế nào và `Recently Added` thực sự đo cái gì. Mục tiêu là tránh biến Library thành filesystem mirror trong khi vẫn giữ V1 local-first UX đơn giản: user register một root thì media mới hợp lệ có thể tự xuất hiện trong Library mà không cần add từng title bằng tay.

### 4.16.1 Problem Statement

Foundation trước Q-LIB đã khóa:

```text
filesystem/source observation
!= canonical identity

Asset missing/unavailable
!= canonical removal

Progress/History
belong to canonical consumption target
```

Nhưng vẫn còn ambiguity ở Media-level user state:

1. Registered root scan thấy một Media mới thì có auto-add vào Library không?
2. Nếu user explicit `Remove from Library` nhưng file vẫn còn trong registered root, scan sau có add lại không?
3. Nếu toàn bộ representations của Media tạm unavailable/missing/out-of-scope, Library membership có mất không?
4. Remove from Library có đồng nghĩa delete local file, canonical Media, Progress hoặc History không?
5. `Recently Added` dùng file time, discovery time, canonical creation time hay user/app Library-admission time?
6. Rename/move/rebind/new Asset/new Unit có làm một item “recently added” lại không?
7. Một Media có thể tồn tại canonical mà không nằm trong Library không?

Nếu scan presence tự định nghĩa membership, user không thể thực sự remove một item khi file vẫn còn. Nếu removal xóa toàn canonical/user state, app sẽ mất Progress/History chỉ vì user muốn dọn Library view. Nếu `Recently Added` dùng file timestamp, copy/restore/move hoặc metadata/parser changes có thể làm chronology sai nghĩa.

### 4.16.2 Research Findings

Q-LIB là cross-domain question. Research dùng manga reader, ebook library, media-center và media-server families để tránh copy convention của một product.

#### Mihon — source/catalog existence khác Library membership

Mihon có explicit `Add to library` / `Remove from library` behavior. Local Source content có thể tồn tại/browse ngoài Library và được re-add sau. FAQ local-source thậm chí hướng dẫn remove title khỏi Library, clear non-library database data, quay lại Local Source rồi re-add.

**Observed fact:** source content existence và Library membership là hai trạng thái khác nhau.

**Inference:** canonical/source availability không nên tự đồng nhất với active Library membership.

#### calibre — adding/removing is a library operation, not raw folder observation

calibre yêu cầu `Add books` để import vào library database; scan folder trong flow đó là một explicit admission operation. Documentation cũng tách book record/library operations khỏi original files và cho phép create empty book record không có format.

**Observed fact:** logical library membership có thể tồn tại độc lập với một current source file, và presence của arbitrary file ngoài library không tự là membership.

**Inference:** LibraryEntry nên là app-owned state, không phải derived boolean `hasAsset`.

#### Kodi — scanner-mirror model exposes remove/re-add tension

Kodi Video Library dùng configured sources + scan/update để đưa filesystem content vào library. `Remove from library` tách khỏi file deletion, nhưng nếu source/file vẫn thuộc scan surface thì scanner-driven systems phải có policy rõ để tránh item user vừa remove bị discovery path đưa trở lại. Kodi còn có separate library clean/update behavior và configurable `dateAdded` based on current time/file timestamps.

**Observed fact:** source scanning thuận tiện cho auto-admission nhưng scanner semantics và explicit user removal có thể conflict; `dateAdded` tied to file timestamps can mean something different from “user/app added to Library”.

**Inference:** project này cần precedence rule: explicit membership intent > root auto-admission.

#### Plex — unavailable content remains a library state before destructive cleanup

Plex đưa missing/unavailable items vào Trash thay vì lập tức xóa; mục đích explicit là tránh mất library state khi drive/network share unavailable. Item có thể restore khi file trở lại; empty-trash là một action khác.

**Observed fact:** external availability loss không bắt buộc đồng nghĩa library removal.

**Inference:** Q-REC/Q-SCN availability rules phải tiếp tục preserve Library membership.

#### Komga — root auto-ingest is useful, but trash protects user state

Komga library gắn với root folder; scan phát hiện new media và pull vào library. Missing media đi vào trash; hash hỗ trợ restore qua move/rename mà không mất metadata/read progress. Documentation cảnh báo auto-empty trash có thể làm content bị remove ngay và phá move handling.

**Observed fact:** registered-root auto-ingest là UX thực tế tốt, nhưng destructive membership cleanup cần tách khỏi observation.

**Inference:** V1 có thể auto-admit từ registered root nhưng không được để scan overwrite explicit user intent hoặc temporary availability state.

### 4.16.3 Decision — Library Membership Is Durable Media-Level User/App State

V1 khóa semantic separation:

```text
Canonical Media existence
!= Library membership
!= Source/Asset availability
```

`LibraryEntry` là **Media-level current membership state**. Nó trả lời:

> “Media này hiện có thuộc user Library của app hay không?”

Nó không trả lời:

- file hiện có reachable không;
- root còn grant không;
- có bao nhiêu Assets/Units không;
- Media được canonicalize lúc nào;
- user đã consume chưa.

Canonical `Media` có thể tồn tại mà **không có active Library membership**, ví dụ item user đã remove nhưng app còn Progress/History/mapping/suppression cần giữ.

### 4.16.4 Registered Root = Default Admission Intent, Not Membership Identity

V1 local-first cần low-friction behavior. Khi user đăng ký `StorageRoot`, baseline hiểu đó là intent:

```text
newly recognized canonical Media
inside an enabled registered root
+ no explicit exclusion
→ eligible for automatic Library admission
```

V1 default policy là **AUTO_ADD_DISCOVERED_MEDIA** ở semantics level. Exact config/type name chưa locked.

Quan trọng:

```text
StorageRoot registration
!= LibraryEntry identity
```

Một Media sau khi được admitted không “thuộc” RootId theo membership. Move sang root khác, đổi Asset locator hoặc mất root tạm thời không tạo/xóa Library membership.

Nếu future UX cho phép root-level “do not auto-add” hoặc browse-only mode, đó là extension của admission policy; không thay ownership rule.

### 4.16.5 Explicit User Intent Has Precedence Over Auto-Admission

V1 cần một **durable exclusion/suppression semantic** cho explicit `Remove from Library`.

Conceptually:

```text
no explicit membership decision
+ discovered under auto-admit root
→ add to Library

user explicitly removes Media M
→ M becomes NOT_IN_LIBRARY
→ persist suppression/exclusion intent for MediaId M

later rescan sees same MediaId M again
→ keep NOT_IN_LIBRARY
→ do NOT auto-add
```

Exact schema có thể là inactive LibraryEntry, membership-intent state, tombstone/exclusion record hoặc representation khác; foundation **không khóa table design**.

Invariant được khóa là:

> Explicit user exclusion survives ordinary rescan, rename, move, locator change, Asset revision and SourceBinding reattachment of the same canonical `MediaId`.

Scanner không được “thắng” user chỉ vì content vẫn tồn tại.

### 4.16.6 Re-add Starts a New Membership Epoch

Nếu user explicit `Add/Re-add to Library`:

```text
explicit exclusion
→ cleared/overridden

Media
→ active Library membership again
```

Re-add là user intent mới và bắt đầu một **new active membership epoch**.

Foundation cho phép giữ historical first-added information nếu later UX cần, nhưng `Recently Added` của current Library dùng current membership epoch, không first-ever discovery.

### 4.16.7 Remove from Library Is Not Delete / Forget

`Remove from Library` mặc định chỉ thay đổi membership intent.

Nó **không mặc định**:

```text
delete local files
remove StorageRoot
remove SourceBinding
remove Asset/AssetRevision evidence
delete canonical Media/Units
delete Progress
delete History
delete metadata mappings
```

Physical delete là destructive storage operation riêng.

“Forget/purge all app-owned state” nếu product sau này cần cũng là explicit destructive operation riêng.

UI có thể về sau offer combined action như “Remove from Library and delete files”, nhưng semantics phải compose hai operations; không collapse chúng thành một action domain duy nhất.

### 4.16.8 Availability Does Not Own Membership

Các trạng thái sau **không tự thay Library membership**:

```text
Asset MISSING
Asset OUT_OF_SCOPE
StorageRoot ACCESS_LOST
removable volume absent
provider temporarily unavailable
Source disabled temporarily
zero currently resolvable Assets
```

Một Library Media có thể tồn tại ở trạng thái unavailable.

Q-REC/Q-SCN quyết khi nào representation absence có authority; Q-LIB khóa rằng **ngay cả authoritative absence cũng không đủ để remove Library membership**.

Nếu user unregister một StorageRoot, representations của root đó có thể trở thành out-of-scope/unavailable theo storage/reconciliation policy; active Library membership vẫn được preserve trừ khi user đồng thời explicit chọn remove corresponding Library items.

### 4.16.9 Rediscovery / Rename / Move / Rebinding

Nếu Q-ID/Q-REC nhận ra observation mới vẫn thuộc cùng `MediaId`:

```text
same MediaId
+ new locator / moved Asset / new root / new representation
→ preserve Library membership decision
```

Do đó:

- IN_LIBRARY Media vẫn in Library;
- SUPPRESSED/NOT_IN_LIBRARY Media vẫn không tự re-add;
- `libraryAddedAt` không bump chỉ vì representation churn.

Nếu canonical identity thực sự split/merge/reclassified, migration của membership intent phải là explicit reconciliation policy. Q-LIB không cho scanner dùng filename/path để bypass MediaId-level intent.

### 4.16.10 Library Membership Is Media-Level, Not Unit-Level

V1 `LibraryEntry` target là `Media`.

```text
Series Media in Library
├── Episode Unit 1
├── Episode Unit 2
└── Episode Unit 3
```

New Episode/Chapter/Volume Unit không tạo một LibraryEntry mới cho parent Media và không làm parent “added again”.

Atomic Movie/one-shot/standalone book vẫn có Media-level LibraryEntry dù consumption target có thể trực tiếp là `MediaTarget(MediaId)`.

Nếu future product cần pin/favorite một individual Unit, đó là feature khác, không đổi Library membership ownership.

### 4.16.11 Recently Added = Current Library Admission Time

V1 định nghĩa một semantic timestamp:

```text
libraryAddedAt
= when the current active Library membership epoch began
```

Tên field chưa locked; semantics được khóa.

#### Auto-admission

Media lần đầu được auto-admit từ registered root:

```text
libraryAddedAt = admission transaction time
```

#### Explicit add/re-add

User add/re-add Media:

```text
libraryAddedAt = explicit admission time
```

#### Not used as `libraryAddedAt`

Không dùng trực tiếp:

```text
file ctime
file mtime
SAF provider timestamp
MediaStore date
first discovery timestamp
canonical Media row creation time
metadata release/publish date
Asset replacement time
latest Unit discovery time
```

Các timestamps trên có thể được lưu như evidence/domain metadata nếu use case khác cần, nhưng không định nghĩa “Recently Added to my Library”.

### 4.16.12 Recently Added Projection Rules

Default projection:

```text
active LibraryEntry
ORDER BY libraryAddedAt DESC
```

Important invariants:

- rename/move same MediaId → no bump;
- re-scan unchanged Media → no bump;
- new Asset/encode/EPUB revision → no bump;
- new Episode/Chapter under existing series → no bump parent Media;
- metadata rematch/title change → no bump;
- temporary remove/reappearance of storage → no bump;
- explicit remove + later re-add → new membership epoch → new `libraryAddedAt`.

Nếu future UX cần “Recently Updated”, “New Episodes”, “New Chapters” hoặc “Recently Discovered Assets”, đó là **separate projection with separate timestamp semantics**.

### 4.16.13 Bulk Initial Scan Is Not a Reason to Use File Time

Registering a root containing 10,000 existing items có thể khiến nhiều Media có gần-cùng `libraryAddedAt`.

Đây là accurate statement rằng app vừa admit chúng vào user's Library.

Không sửa semantic này bằng cách lấy historical file `mtime` để làm “Added”. UX có thể:

- group initial import;
- paginate;
- suppress notification flood;
- show import batch/context;

nhưng không được đổi meaning của membership chronology.

### 4.16.14 Continue Watching / Continue Reading Projection

Progress truth đã thuộc canonical target từ Q-PROG.

Q-LIB khóa default Library/Home projection:

```text
active Library membership
+ target has resumable/in-progress current Progress
→ eligible for Continue Watching/Reading
```

Remove Media khỏi Library:

```text
hide from Library-scoped Continue projection
but preserve Progress
```

Re-add:

```text
same preserved Progress
→ Continue can reappear
```

Availability có thể làm item temporarily non-openable và UI phải biểu diễn degraded state phù hợp; nó không xóa Progress hoặc membership.

Exact ranking/threshold/next-unit UX vẫn là feature projection detail, không phải persistence identity.

### 4.16.15 History Relation

Q-HIST đã khóa History độc lập với membership.

Q-LIB giữ invariant:

```text
Remove from Library
!= erase historical consumption
```

History storage không bị membership cascade-delete.

Exact History screen có filter “Library only” hay hiển thị historical non-library targets là UX/query choice sau; foundation chỉ khóa data ownership và non-destructive removal.

### 4.16.16 Representative Scenarios

#### A. First local movie discovery

```text
registered Root A
→ discover Interstellar.mkv
→ canonical Media M
→ no explicit exclusion
→ auto-admit M
→ LibraryEntry active
→ libraryAddedAt = admission time
```

#### B. User removes movie but keeps file

```text
M remains on disk
user: Remove from Library
→ membership inactive
→ durable exclusion for M
→ Progress/History/Asset preserved
```

Next scan sees same file:

```text
same MediaId M
→ exclusion wins
→ no re-add
```

#### C. Removed movie renamed/moved

```text
Interstellar.mkv
→ Movies/Archive/Interstellar 4K.mkv
```

If reconciliation says same canonical Media M:

```text
M remains excluded
```

Filename/path change does not bypass user intent.

#### D. User re-adds

```text
explicit Add to Library
→ exclusion cleared
→ active membership
→ new libraryAddedAt
→ old Progress/History still available
```

#### E. SD card removed

```text
Library Media M active
SD card absent
→ Asset/root unavailable
→ LibraryEntry remains active
→ UI may show unavailable
```

SD card returns:

```text
reconcile Assets
→ membership unchanged
→ libraryAddedAt unchanged
```

#### F. Manga series gets Chapter 101

```text
Media: Series X already in Library
new Unit: Chapter 101
→ parent membership unchanged
→ parent libraryAddedAt unchanged
```

A future “New Chapters” feed may use Unit/discovery semantics separately.

#### G. Standalone EPUB replaced with corrected revision

```text
Book Media M in Library
AssetRevision R1 → R2
→ membership unchanged
→ libraryAddedAt unchanged
→ Q-PROG-002 handles anchor compatibility
```

#### H. Root intentionally unregistered

```text
user unregisters Root A
→ stop discovery/access through Root A
→ affected representations may become out-of-scope
→ Library membership remains unless user explicitly chooses membership removal too
```

### 4.16.17 Precedence Model

Foundation-level precedence:

```text
explicit user membership intent
        >
root/source auto-admission policy
        >
raw discovery presence
```

Availability does not participate as membership authority.

This avoids both failure modes:

```text
scanner re-adds what user removed
```

and

```text
temporary storage failure silently removes user Library
```

### 4.16.18 Rejected Alternatives

#### Alternative A — Library = every currently present Media under registered roots

Rejected because user cannot remove an item while keeping the file, and temporary availability changes mutate user intent.

#### Alternative B — Explicit Remove only deletes LibraryEntry; next scan may add again

Rejected because it makes Remove non-durable and gives scanner higher authority than user intent.

#### Alternative C — Remove from Library deletes canonical Media + Progress + History

Rejected because membership cleanup is not evidence that identity/user consumption state should be forgotten.

#### Alternative D — Missing/unavailable Media automatically leaves Library

Rejected by Q-REC/Q-SCN invariants and removable/offline failure cases.

#### Alternative E — Registered root never auto-adds; user manually adds every discovered title

Rejected as V1 default because local-first universal library would become unnecessarily high-friction. Manual/browse-only admission may be future configurable policy, but V1 registered-root baseline auto-admits when no explicit exclusion exists.

#### Alternative F — `Recently Added = canonical Media createdAt`

Rejected because canonicalization can happen before/after membership and canonical Media may exist outside Library.

#### Alternative G — `Recently Added = first discovery time`

Rejected because discovery is an observation event, not membership event; explicit later add/re-add would have misleading chronology.

#### Alternative H — `Recently Added = file mtime/ctime`

Rejected because copy/restore/provider timestamps describe representation history, not user Library admission. Kodi's configurable behavior is evidence that these semantics are product choices, not universal truth.

#### Alternative I — New Unit/Asset bumps parent Media `libraryAddedAt`

Rejected because it turns “Recently Added” into “Recently Updated” and causes long-running series to continuously masquerade as newly added Media.

### 4.16.19 Downstream Impact

Q-LIB-001 unlocks these assumptions:

```text
Library membership is Media-level durable user/app state.
Canonical Media may exist outside Library.
Registered root defaults to auto-admit newly recognized Media.
Explicit user exclusion overrides auto-admission and survives rescan.
Availability/missing/out-of-scope does not remove membership.
Remove from Library is non-destructive to Progress/History/source state by default.
Re-add starts a new active membership epoch.
Recently Added uses library admission time.
Representation/Unit churn does not bump libraryAddedAt.
Library-scoped Continue is membership-gated but Progress is preserved outside Library.
```

Stage D user-state semantics are now coherent enough for the next stage:

```text
Q-PROG-001
        ↓
Q-PROG-002
        ↓
Q-HIST-001
        ↓
Q-LIB-001

STAGE D — PROVISIONAL BASELINE COMPLETE
```

Production persistence no longer waits on metadata precedence or runtime ownership; `Q-META-001` and `Q-RUN-001` are PROVISIONAL. Deep schema/module/API design is now unblocked because `Q-BACK-001` is PROVISIONAL; it must proceed under all completed foundation invariants.

### 4.16.20 Verification Ideas

Future tests should prove at least:

1. Newly recognized Media under a registered auto-admit root enters Library exactly once.
2. User removes Media while file remains; next full scan does not re-add it.
3. Rename/move of excluded Media that preserves MediaId does not re-add it.
4. Same Media rediscovered under another root/source remains excluded until explicit re-add.
5. Explicit re-add clears suppression and starts a new membership epoch.
6. Re-add preserves existing Progress and History.
7. Asset MISSING does not remove LibraryEntry.
8. StorageRoot ACCESS_LOST/removable volume absence does not remove LibraryEntry.
9. Unregistering a root does not silently delete Library membership.
10. Remove from Library does not delete physical media by default.
11. Remove from Library does not delete Progress/History/SourceBinding/Asset state.
12. New Episode/Chapter under existing Media does not update Media `libraryAddedAt`.
13. New Asset/encode/publication revision does not update `libraryAddedAt`.
14. Metadata rematch/title change does not update `libraryAddedAt`.
15. Initial auto-admission sets `libraryAddedAt` from admission time, not file mtime/ctime.
16. Remove + later re-add results in new current `libraryAddedAt`.
17. Removed Media disappears from Library-scoped Continue while Progress remains durable.
18. Re-added Media with resumable Progress can reappear in Continue.
19. Canonical Media without active LibraryEntry remains valid when retained by Progress/History/mapping/suppression needs.
20. Explicit exclusion is keyed to canonical identity semantics rather than current filename/path.
21. Partial/failed scan cannot mutate membership just because some items were unseen.
22. Two physical copies mapping to same Media do not create duplicate LibraryEntries.

### 4.16.21 Remaining Risks / Deferred Details

Q-LIB-001 intentionally does **not** lock:

- exact Room representation of active membership vs suppression/exclusion intent;
- whether root admission policy is configurable in initial V1 UI or only later;
- exact purge/garbage-collection policy for canonical Media outside Library with no remaining durable user state;
- canonical split/merge migration of membership intent in rare reclassification cases;
- exact Library status/category model (reading/planned/completed etc.) beyond membership itself;
- exact Continue ranking, progress threshold and next-unit behavior;
- Recently Updated / New Episodes / New Chapters projections;
- notification behavior for bulk initial import;
- History screen filter behavior for non-library targets;
- exact user confirmation UX for unregister-root + optional remove-items action;
- Metadata/user override precedence is now governed by `Q-META-001`;
- exact scheduler wiring/cadence under the now-resolved `Q-RUN-001` semantics;
- backup/restore of membership/exclusion (`Q-BACK-001`).

None of these require filesystem presence to become Library truth.

### 4.16.22 ADR Requirement

No standalone ADR is required for the Library membership semantic distinction itself.

A technical spec/ADR may become appropriate if later implementation chooses a complex multi-library/category model, cross-device membership merge model or destructive purge policy with high migration cost.

### 4.16.23 Research References

Manga/reader family:

- Mihon Local Source FAQ — local/source item can be outside Library and re-added: https://mihon.app/docs/faq/browse/local-source
- Mihon issue showing explicit Add-to-Library semantics across sources: https://github.com/mihonapp/mihon/issues/3012
- Mihon issue showing downloaded/source presence can diverge: https://github.com/mihonapp/mihon/issues/3501

Book/library family:

- calibre GUI — Add books / Remove books / library database semantics: https://manual.calibre-ebook.com/gui.html

Media-center/server family:

- Kodi — Updating or removing videos / explicit Remove from Library: https://kodi.wiki/view/Updating_or_removing_videos
- Kodi — Video DB `dateAdded` is “Date & Time Added to Library”: https://kodi.wiki/view/Databases/MyVideos
- Kodi — configurable `dateAdded` behavior demonstrates file-time vs admission-time is a product choice: https://kodi.wiki/view/Advancedsettings.xml
- Plex — Emptying Library Trash / unavailable media preserved before destructive cleanup: https://support.plex.tv/articles/200289326-emptying-library-trash/
- Plex — reasons content can be unavailable without user removal: https://support.plex.tv/articles/201806463-why-does-plex-media-server-say-my-content-is-unavailable/
- Komga — Libraries / scanner auto-ingest: https://komga.org/docs/guides/libraries/
- Komga — Trash protects metadata/progress across unavailable/move/rename: https://komga.org/docs/guides/trash/
- Komga — scanner behavior for new/removed content: https://komga.org/docs/guides/scan-analysis-refresh/

Research note: calibre manages its own copies and Plex/Komga/Kodi are server/media-center oriented, so none is a direct architecture template. The project decision combines the low-friction registered-root admission behavior proven by scanner-based libraries with the explicit membership control seen in reader/book systems, while retaining this foundation's app-owned canonical identity and Android availability semantics.

### 4.16.24 Stage D Closure Summary

Stage D now has a coherent provisional user-state model:

```text
Progress
= current durable consumption state

History
= meaningful past consumption activity

Library membership
= current Media-level user/app inclusion intent

Availability
= external/source/storage observation
```

These states interact but do not own each other.

Critical invariants:

```text
Remove from Library
!= delete Progress/History

Asset missing
!= remove from Library

Rescan presence
!= override explicit exclusion

Recently Added
= current Library admission chronology
!= file chronology
```

`Q-LIB-001` is **PROVISIONAL** and completes Stage D at provisional baseline level.

**Historical handoff:** `Q-META-001` was next after Q-LIB-001 and is now completed at **PROVISIONAL** level. Q-BACK-001 is now **PROVISIONAL**; Stages A–F are complete for deep persistence/module/API design.


## 4.17 Decision Record — Q-META-001: Metadata Precedence / Provenance / User Override

**Status:** `PROVISIONAL`

Q-META-001 closes Stage E by defining how descriptive enrichment values are selected and refreshed without allowing metadata providers, local parsing, rescans or rematching to silently erase explicit user intent.

This question is deliberately about **metadata value authority**, not canonical identity. `MediaId`, `UnitId`, `ConsumptionTargetRef`, Library membership, Progress, History, SourceBinding and Asset lifecycle remain owned by the decisions that already govern them.

### 4.17.1 Problem Statement

V1 can observe or fetch multiple plausible values for the same descriptive field:

```text
local filename/path parser
→ "Frieren S01E01"

embedded/local-declared metadata
→ "Frieren: Beyond Journey's End"

matched metadata provider
→ "Frieren: Beyond Journey's End"

user edit
→ "Frieren"
```

Without explicit precedence and provenance, refresh/rematch can create several failure modes:

```text
user edits title
→ provider refresh silently restores remote title

user clears summary
→ next refresh silently repopulates it

metadata match changes
→ old provider fields and new provider fields become an unexplained mixture

provider request fails
→ existing metadata is blanked or replaced by filename guesses

filename parser improves
→ previously curated display metadata unexpectedly changes
```

Q-META-001 must answer:

1. What wins when user override, matched metadata and locally-derived values disagree?
2. How is an explicit user clear different from “no override”?
3. What happens to user edits during refresh/rematch?
4. Is local embedded/sidecar metadata the same thing as filename/path inference?
5. Which facts are metadata at all, versus canonical/domain or representation facts owned elsewhere?
6. How much provenance must survive so effective values remain explainable and reversible?
7. What happens when a metadata provider is offline, stale or returns partial data?
8. How should multi-valued fields avoid accidental union/duplication across sources?

### 4.17.2 Research Findings

#### Plex — manual edits become locked field-level user intent

Plex automatically locks a field when the user manually changes it. Locked fields are not changed by metadata refresh until the user explicitly unlocks them.

**Observed fact:** refresh does not outrank an explicit field edit.

**Inference:** user override should be a durable field-scoped authority state rather than merely another candidate value with a high timestamp.

References:

- https://support.plex.tv/articles/201272763-edit-details/
- https://support.plex.tv/articles/200289306-scanning-vs-refreshing-a-library/

#### Komga — edit locks survive refresh

Komga also automatically locks a metadata field after manual editing; a locked field is not altered on refresh until it is unlocked.

**Observed fact:** a comic/book scanner can safely refresh source metadata while preserving user corrections per field.

**Inference:** V1 does not need an all-or-nothing “metadata object lock”; field-level override semantics are sufficient and compose better with partial enrichment.

References:

- https://komga.org/docs/guides/edit-metadata/
- https://komga.org/docs/guides/scan-analysis-refresh/

#### Jellyfin — local declared metadata is a source, not filename inference

Jellyfin treats local `.nfo` data as metadata input and documents that local NFO metadata has priority over remote providers such as TMDb. Local artwork can also take precedence over remote artwork.

**Observed fact:** mature systems treat local declared metadata as an explicit metadata source with its own priority, distinct from a filename parser.

**Inference:** this foundation must not put `ComicInfo.xml`, EPUB package metadata or NFO-style data into the same low-authority bucket as path/title guesses.

References:

- https://jellyfin.org/docs/general/server/metadata/nfo/
- https://jellyfin.org/docs/general/server/metadata/

#### Kodi — local metadata can override selected scraped fields

Kodi imports NFO metadata before online scraping in relevant flows, and Combination NFO files can override selected scraped data while allowing other values to come from the scraper. Multi-valued fields need explicit clear/add semantics to avoid duplicate entries.

**Observed fact:** metadata precedence may be field-specific, and naïvely unioning multiple-value sources can create duplicates.

**Inference:** V1 should use deterministic source selection/override semantics per field rather than concatenate every candidate source automatically.

References:

- https://kodi.wiki/view/NFO_files
- https://kodi.wiki/view/NFO_files/Combination

#### calibre — metadata import is an explicit user-controlled transformation

calibre allows users to download metadata and explicitly choose replacement or append/prepend behavior for destination fields. Manual metadata editing remains separate from downloaded metadata.

**Observed fact:** there is no universal safe merge rule for every metadata field.

**Inference:** arbitrary provider/local value merging must not be hidden behind refresh. A selected source/policy should produce the effective value unless a user explicitly chooses another transformation.

Reference:

- https://manual.calibre-ebook.com/metadata.html

#### EPUB / Readium — publication packages carry declared metadata

EPUB packages contain authored bibliographic metadata such as title, identifier, language, description, publication date and series/collection information; Readium parses this metadata into its publication model.

**Observed fact:** publication metadata embedded in a package is declared content metadata, not an inference from filename/path.

**Inference:** publication package metadata belongs in `DECLARED_LOCAL_METADATA`, while filename/path parsing remains `LOCALLY_DERIVED_OBSERVATION`.

Reference:

- https://readium.org/architecture/streamer/parser/metadata.html

### 4.17.3 Decision — Separate Metadata Authority from Metadata Provenance

Foundation adopts a field-oriented effective metadata model.

Conceptually:

```text
Metadata field
    ↓ collect candidates with provenance
    ├── USER_OVERRIDE
    ├── DECLARED_LOCAL_METADATA
    ├── MATCHED_PROVIDER_METADATA
    └── LOCALLY_DERIVED_OBSERVATION
    ↓ apply deterministic field/source policy
Effective metadata value
```

Names above are semantic categories, not final Kotlin enums/tables.

Every effective descriptive value must be explainable at least at the category level:

```text
value
+ field
+ provenance category
+ source/provider/reference when applicable
+ observed/fetched/edited context sufficient for refresh/rematch
```

Exact storage shape remains a persistence-design decision.

### 4.17.4 User Override Is Highest Field-Level Authority

For a user-editable descriptive field:

```text
explicit user override
        >
non-user metadata sources
```

Once the user edits a field, automated operations must not silently replace that field until the user explicitly clears/unlocks/resets the override.

This applies to:

```text
provider refresh
provider rematch
local metadata refresh
filename/path reparsing
Asset rename/move
Asset revision
app restart
process death
```

The user edit is durable app-owned intent, not a transient UI value.

Representative fields may include:

- display title;
- sort title;
- summary/description;
- creator/author display metadata;
- genres/tags when exposed as editable metadata;
- release date/year when user-editable;
- selected artwork/poster when product UI exposes an explicit choice.

Exact editable field set is deferred to metadata/UI technical design.

### 4.17.5 Explicit Empty Is Different from Inherit

V1 must preserve the semantic difference between:

```text
INHERIT
→ no user override; resolve from metadata sources

OVERRIDE(value)
→ user supplied a value

OVERRIDE_EMPTY
→ user intentionally cleared the field
```

Names are provisional.

Rule:

```text
user clears Summary
→ refresh must NOT silently restore provider Summary
```

For fields that require a non-empty UI display, presentation may use a fallback label without pretending the user override disappeared.

Example:

```text
user clears optional tagline
→ effective domain tagline = explicitly empty
→ UI does not repopulate provider tagline
```

This avoids the common schema bug where `null` means both “inherit” and “explicitly clear”.

### 4.17.6 Matched Metadata Beats Locally-Derived Display Fallback

For ordinary descriptive fields **when no user override exists**, V1 default semantics are:

```text
active matched metadata candidate
        >
locally-derived parser fallback
```

`LOCALLY_DERIVED_OBSERVATION` includes low-authority inferred/display candidates such as:

- title parsed from filename/folder;
- season/episode/chapter label inferred from naming;
- guessed creator/title fragments from path conventions;
- parser-generated fallback display labels.

These values remain useful for:

- initial offline display before metadata match;
- matching evidence;
- diagnostics;
- fallback if no richer metadata source exists.

But a later successful metadata match must not require deleting those observations; it simply stops using them as the effective display value where higher-authority metadata is available.

### 4.17.7 Declared Local Metadata Is Not Locally-Derived Inference

Foundation distinguishes:

```text
DECLARED_LOCAL_METADATA
```

from:

```text
LOCALLY_DERIVED_OBSERVATION
```

Declared local metadata may include:

- `ComicInfo.xml`;
- EPUB package metadata;
- NFO/OPF-style sidecar metadata;
- embedded tags that explicitly declare bibliographic/descriptive information;
- local artwork explicitly associated with the item.

Filename/path parsing does **not** belong to this category.

The exact ordering between `DECLARED_LOCAL_METADATA` and `MATCHED_PROVIDER_METADATA` is **not hard-coded globally across all media types/fields** at foundation level because mature systems legitimately choose different policies.

Instead:

```text
USER_OVERRIDE
    always highest

DECLARED_LOCAL_METADATA vs MATCHED_PROVIDER_METADATA
    deterministic field/source policy

LOCALLY_DERIVED_OBSERVATION
    fallback below active richer metadata source
```

A production metadata policy must therefore make local-vs-provider preference explicit rather than allowing repository/query order to accidentally decide it.

V1 may start with a simple fixed policy per media family; no dynamic metadata rules engine is required.

### 4.17.8 Mapping Authority Is Separate from Field Override Authority

A metadata mapping answers:

```text
which external entry corresponds to this canonical Media/Unit?
```

A field override answers:

```text
what value does the user want this field to show/use?
```

These are different authorities.

Rules:

- manual user-confirmed mapping outranks automatic rematch for that mapping relation until user explicitly rematches/unlinks;
- changing the mapping changes the provider-backed candidate set;
- changing the mapping does **not** clear user field overrides;
- provider IDs remain external mapping/evidence, not `MediaId`/`UnitId`;
- removing a metadata mapping does not delete canonical Media, Library membership, Progress or History.

Example:

```text
Media M
user override title = "Frieren"
matched TMDb entry A
        ↓ user rematches
matched TMDb entry B

result:
user title remains "Frieren"
provider-backed summary/artwork/etc. may refresh from B
```

### 4.17.9 Refresh Semantics

Metadata refresh means:

```text
re-observe/re-fetch non-user metadata candidates
→ update their provenance/snapshot
→ re-evaluate effective fields
→ preserve user overrides
```

Refresh must not mean:

```text
replace canonical Media object wholesale with provider DTO
```

or:

```text
overwrite every database metadata column regardless of provenance
```

A refresh that succeeds for one source may update fields governed by that source policy while leaving other source candidates intact.

### 4.17.10 Provider Failure Is Not Metadata Deletion

A provider/network failure does not prove that prior metadata values are invalid.

Therefore:

```text
refresh request fails / times out / provider offline
→ retain last-known provider candidate/snapshot
→ record failure/staleness separately as needed
→ do not blank fields
→ do not fall back to filename guesses merely because refresh failed
```

A provider's **successful authoritative response that explicitly lacks/removes a field** is different from a transport failure. Exact source contract for authoritative absence is provider-specific and deferred to the metadata adapter contract.

This mirrors Q-SCN's broader principle:

```text
failure to observe
!= authoritative absence
```

without reusing scanner semantics mechanically.

### 4.17.11 Rematch Must Not Produce Frankenstein Metadata Silently

When the active metadata mapping changes:

```text
old provider mapping A
        ↓ rematch
new provider mapping B
```

provider-backed values must be re-evaluated against B with provenance preserved.

V1 must not silently retain arbitrary old-provider values beside new-provider values merely because individual database columns happened not to be refreshed.

Allowed outcomes include:

- value now sourced from B;
- user override remains effective;
- declared-local source remains effective if source policy says so;
- derived local fallback becomes effective if no richer candidate exists.

Exact snapshot replacement/transaction mechanics are persistence design, but the effective state must be explainable and deterministic after rematch.

### 4.17.12 Multi-Valued Fields Are Not Automatically Union-All

Fields such as:

- genres;
- tags;
- creators/contributors;
- studios;
- alternate titles;

can have multiple values.

Foundation rejects this default:

```text
all provider values
+ all local metadata values
+ all parser values
+ all historical values
= effective field
```

because it creates duplicates, contradictory classifications and values that cannot be removed reliably.

Default V1 semantics:

- source policy selects the effective candidate/set when no user override exists;
- a user override may replace the effective field/set;
- exact add/remove-overlay semantics for complex multi-value editing are deferred;
- explicit merge behavior may be added later for fields where product UX requires it.

### 4.17.13 Artwork Selection Follows the Same Authority Principle

Artwork has candidate multiplicity, but selection authority follows the same rule:

```text
explicit user-selected artwork
        >
automatic source selection
```

Provider/local refresh may discover additional artwork candidates without silently replacing an explicit user selection.

Exact image cache/file ownership remains outside Q-META-001.

### 4.17.14 Metadata Does Not Own Canonical or Representation Truth

The following are **not ordinary descriptive metadata fields that provider refresh may overwrite**:

#### Canonical/domain truth

- `MediaId`;
- `UnitId`;
- `GroupingId`;
- `ConsumptionTargetRef`;
- Library membership/exclusion;
- Progress;
- History;
- SourceBinding identity/lifecycle;
- canonical split/merge decisions.

#### Representation/technical truth

Examples:

- current file size;
- container/MIME;
- video dimensions/codec/duration observations;
- archive/page count observations;
- actual document locator;
- `AssetId` / `AssetRevision`;
- current storage availability.

These values belong to Asset/analysis/reconciliation boundaries and may be used as metadata display inputs, but a remote metadata provider cannot make them authoritative over observed local representation facts.

#### External mappings

Provider IDs/ISBN/external identifiers are mapping/evidence records with provenance. They are not silently treated as editable display strings that redefine canonical identity.

### 4.17.15 User Override Does Not Rewrite Source Data

A user edit changes app-owned effective metadata intent.

It does not automatically mutate:

- EPUB package metadata;
- ComicInfo/NFO files;
- remote provider records;
- source-native provider objects.

Writing edits back to external/local source files would be a separate explicit capability with its own permissions/failure model if ever added.

This keeps:

```text
app override
!= source mutation
```

### 4.17.16 Provenance Requirements

Foundation does not lock the exact schema, but production persistence must retain enough information to answer for an effective field:

```text
Why is this the value?
```

At minimum the semantics must distinguish:

- explicit user override vs inherited value;
- explicit empty vs no override;
- declared local metadata vs provider metadata vs derived fallback;
- provider/source identity/reference when needed for rematch;
- enough revision/freshness information to refresh deterministically.

It is not sufficient to persist only:

```text
title = "Frieren"
```

if the app can no longer determine whether that title came from a user edit, provider refresh or filename parser.

Exact candidate-history retention is not required; current provenance sufficient for correct refresh/rematch is the V1 requirement.

### 4.17.17 Representative Scenarios

#### A. User edits provider title

```text
provider title = "Sousou no Frieren"
user changes title = "Frieren"
→ field becomes user override
→ provider refresh later returns "Frieren: Beyond Journey's End"
→ effective title remains "Frieren"
```

#### B. User explicitly clears summary

```text
provider summary exists
user clears Summary
→ explicit empty override
→ refresh does not repopulate Summary
```

#### C. User resets/unlocks title

```text
user override title exists
user chooses reset/inherit
→ override authority removed
→ effective title recomputed from configured metadata source policy
```

#### D. Offline local file before metadata match

```text
filename parser → "Frieren S01E01"
no provider/local-declared title available
→ locally-derived fallback may be displayed
```

Later:

```text
metadata match succeeds
→ matched metadata title becomes effective
→ parser observation remains evidence/fallback
```

#### E. Provider refresh fails

```text
last-known provider title/summary exist
network timeout
→ keep last-known values
→ record refresh failure/staleness
→ do not replace with filename guesses
```

#### F. Manual rematch

```text
Media M mapped to provider entry A
user overrides title only
user rematches to entry B
→ mapping changes to B
→ user title override remains
→ non-overridden provider fields re-evaluate from B
```

#### G. ComicInfo and provider disagree

```text
ComicInfo title = X
provider title = Y
no user override
```

Foundation does not let repository arrival order choose the result. The configured deterministic metadata source policy for that field/media family decides whether declared-local X or matched-provider Y is effective.

#### H. EPUB embedded metadata and filename disagree

```text
EPUB dc:title = "Book A"
filename parser = "book_a_final_v2"
→ embedded title is declared metadata
→ filename value is derived fallback
```

They are not treated as equal-quality observations.

#### I. User selects poster

```text
provider refresh discovers new poster
→ candidate may be added/refreshed
→ explicit user-selected poster remains selected
```

#### J. Parser version changes

```text
old filename parser guessed Chapter 12
new parser guesses Chapter 12.5
```

This may update derived observation/evidence, but it cannot overwrite a user field override and cannot by itself redefine canonical Unit identity outside Identity/Reconciliation rules.

### 4.17.18 Rejected Alternatives

#### Alternative A — “Latest write wins” metadata

Rejected because refresh timestamps would silently outrank user intent and source authority would depend on operation ordering.

#### Alternative B — Provider metadata always wins everything

Rejected because manual correction becomes non-durable, offline/local-first behavior degrades, and remote descriptive data must not own canonical/technical truth.

#### Alternative C — Local parser/filename always wins provider metadata

Rejected because parser output is inference/fallback and would prevent a confirmed metadata match from improving display data.

#### Alternative D — Treat local declared metadata and filename inference as one source class

Rejected because `ComicInfo.xml`/EPUB/NFO metadata is explicitly authored/declared, while filename/path parsing is heuristic observation. Their authority and refresh semantics differ.

#### Alternative E — Manual edit overwrites provider/local candidate in place

Rejected because provenance is destroyed; later “reset to source” or safe rematch becomes impossible without guessing.

#### Alternative F — Null means both no override and explicit clear

Rejected because refresh cannot tell whether to inherit a source value or honor the user's request for an empty field.

#### Alternative G — Union all multi-valued metadata sources

Rejected because it produces duplicate/conflicting tags/creators and makes removals non-deterministic.

#### Alternative H — Rematch replaces the whole canonical Media object

Rejected because external metadata identity is not canonical identity and would endanger Library/Progress/History/source relations.

### 4.17.19 Downstream Impact

Q-META-001 unlocks the following assumptions for Stage F and persistence design:

```text
User override is durable field-scoped app intent.
Explicit empty is distinct from inherit/no override.
Matched metadata outranks locally-derived display fallback by default.
Declared local metadata is a separate source category, not filename inference.
Declared-local vs matched-provider priority must be deterministic and explicit, not accidental query order.
Manual mapping authority is separate from field override authority.
Refresh/rematch preserve user overrides.
Provider failure does not erase last-known metadata.
Effective fields retain enough provenance to explain and recompute them.
Metadata does not own canonical identity, Progress, History, Library or Asset technical truth.
Multi-valued metadata is not implicit union-all.
```

Stage E is now complete at provisional baseline level.

Production persistence can now design metadata candidate/provenance/override state without an unresolved precedence question, but final schema/module/API deep design still follows Stage F runtime/backup decisions according to the required resolution order.

### 4.17.20 Verification Ideas

Future tests should prove at least:

1. User-edited title survives provider refresh.
2. User-edited title survives manual rematch.
3. User-edited title survives filename parser changes/rescan.
4. Explicitly cleared nullable field remains empty after refresh.
5. Reset/unlock removes override and recomputes effective source value.
6. Matched provider title replaces filename-derived fallback when no user override exists.
7. Filename/path-derived observations remain available for matching/fallback after provider match.
8. ComicInfo/EPUB/NFO metadata is represented as declared-local provenance, not derived-parser provenance.
9. Provider refresh failure retains last-known provider metadata.
10. Successful authoritative provider field removal can be distinguished from transport failure.
11. Manual user-confirmed mapping is not silently replaced by automatic matching.
12. Rematch changes provider-backed non-overridden fields while preserving canonical `MediaId`.
13. Rematch does not change Library membership, Progress or History.
14. Provider metadata cannot overwrite measured local file duration/resolution/page count as representation truth.
15. User-selected artwork remains selected when refresh discovers new candidates.
16. Multi-valued provider/local candidates are not automatically unioned into unexplained duplicates.
17. Effective metadata exposes provenance sufficient to distinguish user/provider/local-declared/derived origins.
18. Process restart preserves override/explicit-empty state.
19. Offline boot can render local-derived/declared metadata without network.
20. Later provider availability can enrich non-overridden fields without rewriting explicit user edits.
21. Deleting/unlinking a metadata mapping does not delete canonical Media or user state.
22. External provider ID correction does not mutate internal `MediaId`.
23. Parser reclassification evidence cannot bypass Identity/Reconciliation to mutate canonical Unit structure.
24. User override does not mutate EPUB/NFO/ComicInfo/provider source data unless a separate explicit write-back capability is invoked.

### 4.17.21 Remaining Risks / Deferred Details

Q-META-001 intentionally does **not** lock:

- exact metadata provider(s) used in V1;
- exact Kotlin metadata candidate/override type names;
- exact Room tables/columns/normalization strategy;
- exact per-field list of editable vs source-owned fields;
- exact declared-local vs matched-provider priority per media family/field;
- locale/language preference and fallback policy for multilingual metadata;
- provider snapshot retention/history depth;
- exact artwork candidate/cache storage;
- complex multi-valued add/remove overlay semantics;
- user UI wording for lock/unlock/reset-to-source;
- metadata write-back to NFO/ComicInfo/EPUB or other files;
- V2 multi-provider aggregation/ranking;
- V3 plugin metadata-provider compatibility/security;
- cross-device merge of user overrides;
- exact backup serialization for metadata provenance/overrides under `Q-BACK-001` semantics;
- exact batching/cadence of bulk metadata refresh under the now-resolved `Q-RUN-001` scheduling/retry semantics.

None of these deferred details require refresh/rematch to gain authority over explicit user override.

### 4.17.22 ADR Requirement

No standalone ADR is required for the semantic rule that explicit user override outranks automated enrichment.

A later ADR/technical spec may be appropriate for:

- provider stack/priority if multiple providers are enabled in V1/V2;
- metadata persistence schema if candidate/provenance modeling has significant migration cost;
- write-back of edits into external local files;
- multilingual field model.

### 4.17.23 Research References

Manual override / refresh behavior:

- Plex — Edit Details / locked user-edited fields: https://support.plex.tv/articles/201272763-edit-details/
- Plex — Scan vs Refresh Metadata: https://support.plex.tv/articles/200289306-scanning-vs-refreshing-a-library/
- Komga — Edit Metadata / lock and unlock: https://komga.org/docs/guides/edit-metadata/
- Komga — Scan, Analyze and Refresh Metadata: https://komga.org/docs/guides/scan-analysis-refresh/

Local metadata source precedence:

- Jellyfin — Local NFO metadata: https://jellyfin.org/docs/general/server/metadata/nfo/
- Jellyfin — Metadata providers overview: https://jellyfin.org/docs/general/server/metadata/
- Kodi — NFO files/import precedence: https://kodi.wiki/view/NFO_files
- Kodi — Combination NFO selective overrides: https://kodi.wiki/view/NFO_files/Combination

Book/publication metadata behavior:

- calibre — Editing/downloading metadata: https://manual.calibre-ebook.com/metadata.html
- Readium — Parsing EPUB Metadata: https://readium.org/architecture/streamer/parser/metadata.html

Research note: Plex/Komga strongly validate sticky manual field overrides; Jellyfin/Kodi demonstrate that declared local metadata may intentionally outrank remote metadata; calibre demonstrates explicit per-field import transformation instead of universal merge. These differences are why this project locks user authority and provenance while refusing to hard-code one global local-declared-vs-provider ordering for every media family.

### 4.17.24 Stage E Closure Summary

Stage E now has a coherent provisional enrichment model:

```text
Canonical Media / Unit
        ↓
metadata mapping + source candidates
        ↓
field-level provenance / source policy
        ↓
user override overlay
        ↓
Effective Metadata Projection
```

Critical invariants:

```text
User override
> automated refresh/rematch

Matched metadata
> locally-derived display fallback
when no stronger configured source/user override applies

Declared local metadata
!= filename/path inference

Provider failure
!= metadata deletion

Metadata rematch
!= canonical identity replacement

Metadata enrichment
!= Progress/History/Library/Asset authority
```

`Q-META-001` is **PROVISIONAL** and completes Stage E at provisional baseline level.

**Historical Stage E handoff:** `Q-RUN-001` was the next blocking question and was completed at **PROVISIONAL** level. Stage F then completed `Q-BACK-001`; R4.14 completed deep persistence/module/API design; R4.15 completed `Q-BOOT-001`. R4.16 has generated the skeleton; current work is executable bootstrap verification (official wrapper + JDK 17 + SDK 37 + Gradle/Android/CI evidence).



## 4.18 Decision Record — Q-RUN-001: Android Execution Ownership / Persistent Work / Cancellation-Retry-Idempotency

**Status:** `PROVISIONAL`

Q-RUN-001 opens Stage F by deciding which Android runtime owns scan/maintenance execution and what guarantees persistent work is allowed to assume. Earlier stages already define `ScanRun` semantics, reconciliation authority and user-state preservation. This record intentionally does **not** redefine those semantics; it decides how Android process/scheduler mechanics may execute them without making lifecycle state a hidden source of truth.

### 4.18.1 Problem Statement

The app must safely execute work across very different lifetimes:

```text
small DB/query/parse work
→ bounded to current process/scope

manual or scheduled root scan
→ may outlive screen
→ may be stopped by Android
→ may be retried/recreated after process death

playback
→ user-visible continuous runtime
→ session/service lifecycle

future backup/download/import/export
→ potentially long-running and policy-constrained
```

Q-SCN-001 already proves that an interrupted scan must not gain absence authority. Q-RUN-001 therefore needs to answer:

1. Which work may remain in-process and which work needs persistent scheduling?
2. Is a WorkManager `WorkRequest` or Worker instance the same thing as a domain `ScanRun`?
3. What happens when Android stops a Worker, the process dies or the user cancels?
4. How is retry classified so terminal permission/content failures do not loop forever?
5. What idempotency/transaction rules make re-execution safe?
6. How are overlapping scans serialized/superseded so stale work cannot finalize negative evidence?
7. When is foreground execution justified under current Android limits?

Representative failure cases:

```text
A. User leaves the screen while a full root scan is running.
B. Process dies after 80% of positive observations were persisted.
C. WorkManager retries after a transient provider/IO failure.
D. User cancels; process is killed before cleanup callback runs.
E. SAF permission is revoked during a scheduled scan.
F. Two scan requests for the same root overlap and finish out of order.
G. Scope/config changes while an older Worker is still running.
H. A scan exceeds ordinary Worker execution time.
I. Android stops background work because constraints/quota/system health changed.
J. A repeated execution sees observations already persisted by a prior attempt.
```

### 4.18.2 Primary Android / Jetpack Findings

#### Android process lifetime is not an app-owned guarantee

Android may kill a background process to reclaim resources. Screen/ViewModel scope therefore cannot own durable correctness. A process restart must reconstruct state from durable app-owned data rather than assuming an old coroutine/service object still exists.

**Inference:** domain truth cannot live only inside a Worker, Service, singleton, ViewModel or coroutine scope.

#### WorkManager is persistent scheduling, not an immortal execution

AndroidX describes WorkManager as the recommended library for persistent work. Scheduled work is retained and can run after constraints are met; WorkManager also reschedules across relevant lifecycle/platform events. However an individual Worker has a bounded execution window unless promoted through long-running foreground support, and WorkManager may stop a running Worker when constraints change, the request is cancelled or the system preempts it.

**Inference:** WorkManager can preserve the *request to do work*, but the app must design the work itself as restartable. It must not interpret "WorkManager persisted the request" as "the same execution/coroutine continued".

#### Cancellation is cooperative and stop callbacks are not a durable finalization contract

WorkManager cancellation changes scheduler state and signals running workers to stop. `CoroutineWorker` integrates cancellation with coroutines, while platform queries such as SAF can expose cancellation primitives. But Android can also terminate the app in situations where application cleanup callbacks are not guaranteed; foreground-service Task Manager stop can remove the whole process without delivering a final app callback.

**Inference:** cancellation callbacks are useful for prompt cleanup but cannot be the only mechanism that marks a `ScanRun` terminal or protects negative reconciliation.

#### Unique work provides an orchestration guard, not canonical authority

WorkManager supports uniquely named work with conflict policies such as KEEP/REPLACE/APPEND. This can prevent redundant concurrent scheduler work, but the policy is an Android orchestration mechanism.

**Inference:** stale/superseded `ScanRun` authority still requires an app-owned generation/scope check because scheduler identity is not domain identity.

#### Long-running WorkManager/foreground execution has evolving platform limits

WorkManager can run long-running workers via foreground-service machinery. Android documentation now warns that WorkManager still uses JobScheduler and, on Android 16, long-running workers can consume job runtime quota. Android 15 also places aggregate background time limits on `dataSync`/`mediaProcessing` foreground services. `dataSync` covers local file processing, but using that service type does not make execution unlimited.

**Inference:** a large media scan must be designed to stop/restart safely. Foreground execution may improve continuity/user visibility for a specific operation, but it cannot be the correctness foundation.

#### User-initiated data transfer jobs are not the default local-scan primitive

UIDT jobs target long user-initiated **data transfers** such as remote uploads/downloads. A local SAF tree scan is observation/local processing, not inherently a data-transfer job.

**Inference:** do not route local scanning into UIDT merely because it is long. Future download/import/export slices may evaluate UIDT separately when their semantics match.

### 4.18.3 Production / Reference Implementation Evidence

#### Now in Android — WorkManager schedules sync; durable data remains below the Worker

The official Now in Android sample uses `CoroutineWorker` and unique WorkManager work for synchronization. Repository synchronization writes into durable local data, and retry/backoff are orchestration concerns rather than making Worker memory the source of truth.

**Observed fact:** persistent scheduler + repository/database state are separated.

**Inference:** V1 should use WorkManager as an execution envelope around scan domain operations, not as the scan domain model itself.

#### VLC Android — media parsing lives outside screen ownership

VLC Android has an explicit medialibrary runtime/database and callbacks for discovery/rescan/background task state. Its Android app also declares service capabilities for long-running media work.

**Observed fact:** mature local-media software treats media discovery/parsing as a subsystem with process/runtime ownership separate from Activity UI.

**Inference:** V1 must not attach full-library scanning to a Compose/ViewModel lifecycle. VLC's exact native/service architecture remains a product-specific implementation, not a template to copy.

#### Media3 offline download stack — continuous user-visible transfer is purpose-built

Media3's offline `DownloadService` and `DownloadManager` separate durable download management from service presentation/scheduling and contain explicit handling for foreground-service restrictions.

**Observed fact:** Android media stacks use purpose-built runtime ownership for continuous transfer rather than forcing every long operation through one generic abstraction.

**Inference:** Q-RUN should keep playback/download/service lifetimes distinct from scanner scheduling even though they share cancellation/restart principles.

### 4.18.4 Decision — Runtime Ownership Classes

V1 uses four semantic execution classes:

```text
A. bounded in-process work
B. persistent/deferrable scheduled work
C. user-visible continuous engine/runtime work
D. explicit foreground escalation when a real requirement proves it
```

These are runtime ownership categories, not domain layers.

#### A. Bounded in-process work

Use coroutine/structured concurrency when losing the current execution with the owning process/scope is acceptable and the operation can be reconstructed/retriggered safely.

Typical examples:

- DB query/read transformation;
- small parse/classification step;
- screen fetch/request;
- image decode coordination;
- small targeted observation whose interruption does not need persistent continuation.

Rules:

- main-thread blocking is forbidden for I/O/CPU-heavy work;
- cancellation propagates cooperatively;
- if the operation publishes durable scan observations, it still obeys `ScanRun`/Q-SCN semantics even though the executor is in-process;
- `ViewModel` is never the durability boundary.

#### B. Persistent / deferrable scheduled work

Use WorkManager when the **request to do work** must survive leaving the screen/process or when Android should reschedule it under constraints.

V1 default scan cases:

```text
full/root library scan
scheduled rescan
maintenance reconciliation pass
large durable metadata-refresh batch (when implemented)
```

The UI requests/observes such work; it does not own the execution lifetime.

#### C. User-visible continuous engine/runtime work

Use the purpose-built runtime that matches the product behavior:

- playback → Media3 session/service ownership;
- future downloads → download-specific runtime chosen by that slice;
- other continuous user-visible operations → explicit service/job design matching current Android policy.

#### D. Foreground escalation

Foreground execution is allowed only when a concrete user-visible requirement justifies it and the chosen service type/permissions/timeouts/quotas are valid for the target SDK.

It is **not** the default durability mechanism for scanning.

### 4.18.5 ScanRun Is Domain Observation State; WorkRequest Is Scheduler State

Q-RUN locks this separation:

```text
WorkRequest / Worker / unique work name
= Android scheduling/execution envelope

ScanRun
= app-owned observation attempt with declared scope/coverage/outcome
```

Therefore:

- `WorkRequest.id` is not `ScanRunId`;
- Worker instance lifetime is not scan lifetime authority;
- `runAttemptCount` is scheduler telemetry, not reconciliation generation;
- WorkInfo state (`RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`) is not a substitute for Q-SCN run outcome/coverage;
- WorkManager input/output/progress should carry small orchestration references/telemetry, not the authoritative observation dataset;
- domain persistence must remain sufficient to explain what observations were committed and whether negative authority ever finalized.

### 4.18.6 Retry / Re-execution Creates a New Observation Attempt by Default

For V1, a Worker retry/recreation after an interrupted execution does **not** silently continue the old run's negative authority.

Conservative default:

```text
execution attempt A
→ ScanRun A
→ partial/failed/interrupted/cancelled terminal semantics

scheduler retries/restarts
→ execution attempt B
→ fresh ScanRun B
→ fresh coverage authority
```

The same logical user/scheduler trigger may therefore cause more than one `ScanRun` over time.

A future optimized checkpoint/resume implementation may reuse finer-grained durable coverage only if it proves that the resumed comparison context is still valid. Until then, a retry re-enumerates enough scope to earn fresh absence authority.

This avoids combining observations from widely separated executions into one fake snapshot.

### 4.18.7 Cancellation Semantics

Cancellation has two layers:

```text
request cancellation / supersession
        ↓
cooperative runtime stop
```

Rules:

- user cancellation requests the scheduler/runtime to stop and records enough app-owned intent/state that a late/stale execution cannot finalize destructive authority;
- coroutine cancellation is checked between bounded batches/loops;
- platform cancellation handles/signals should be used where supported by the adapter;
- cancellation does not roll back already-valid positive observations by default;
- a cancelled run cannot publish new negative absence authority;
- `onStopped()` or service stop callbacks may perform lightweight cleanup, but correctness cannot depend on receiving them;
- on recovery, a stale `RUNNING` run that cannot be proven actively owned is finalized/reclassified as non-authoritative interrupted/abandoned according to Q-SCN semantics.

### 4.18.8 Retry and Failure Classification

Automatic retry is for **retryable runtime failure**, not every unsuccessful domain outcome.

Conceptual classes:

```text
TRANSIENT / RETRYABLE
- temporary provider/IO failure
- temporary system resource/preemption condition
- retryable transport failure for future network work

TERMINAL UNTIL EXTERNAL CHANGE
- SAF permission/access lost
- root deliberately disabled/out-of-scope
- unsupported provider capability
- permanently malformed/unsupported individual asset

PROGRAMMING / INVARIANT FAILURE
- bug / impossible state
- must surface diagnostics rather than enter infinite retry loop
```

Rules:

- `Result.retry()` is used only when another attempt can plausibly succeed without user/domain reconfiguration;
- backoff belongs to scheduler policy and should be explicit;
- access loss updates domain/root diagnostics according to Q-STO/Q-REC and should not blindly retry forever;
- item-local malformed content should normally isolate that item rather than failing/retrying an entire root scan;
- WorkManager `success/failure/retry` controls orchestration and does not replace the richer domain `ScanRun` outcome.

### 4.18.9 Idempotency — Assume Re-execution, Not Exactly-Once

Persistent operations must be correct if an execution step is repeated.

Q-RUN therefore rejects exactly-once assumptions.

For scan/reconciliation:

- repeated positive observation of the same representation must upsert/reconcile idempotently;
- internal IDs/evidence constraints prevent duplicate canonical creation when the same observation is replayed;
- side effects that cannot naturally be idempotent require an app-owned deduplication/operation token or a transactional boundary before they become part of a persistent workflow;
- scheduler retry count is not the deduplication key;
- a Worker may be recreated while prior committed batches remain in the DB.

### 4.18.10 Transaction Boundaries

V1 does **not** wrap an entire large tree scan in one giant database transaction.

Reasons:

- scan duration may be long;
- process death/cancellation should not discard all useful positive evidence;
- long transactions increase lock/contention and make recovery expensive;
- Q-SCN already allows positive evidence from incomplete runs.

Default pattern:

```text
observe bounded batch
        ↓
short transaction: persist valid positive evidence/checkpoint
        ↓
repeat
        ↓
finalize run
        ↓
short guarded transaction:
  re-check scope/config generation
  re-check cancellation/supersession
  re-check coverage authority
  derive/publish allowed negative evidence
  mark terminal ScanRun outcome
```

Negative reconciliation must never be emitted incrementally merely because an item has not yet been seen.

### 4.18.11 Overlap, Serialization and Supersession

Q-SCN already forbids stale overlapping runs from gaining negative authority. Q-RUN chooses a conservative V1 runtime policy:

- full/root scans whose negative-authority scopes overlap are serialized/coalesced by default;
- WorkManager unique work may be used as the scheduler guard;
- same-scope/same-config duplicate triggers may coalesce instead of creating redundant work;
- changed root/filter/config generation or explicit restart can supersede older work;
- an older run that was superseded may keep already-valid positive observations but cannot finalize negative authority;
- DB/domain authority check remains mandatory even if WorkManager unique work is also used.

Exact unique-work naming and `KEEP`/`REPLACE` policy are implementation details; the semantic rule is **no stale finalizer**.

### 4.18.12 Scheduled / Periodic Rescan Semantics

Periodic scheduling is a **trigger policy**, not canonical scan identity.

Each actual observation execution creates its own `ScanRun` attempt/coverage semantics. Missing or delayed periodic execution does not mutate library state by itself.

Local scans do not require network constraints. Charging/battery/storage constraints should be added only when workload measurements/product expectations justify them; they are not identity/correctness rules.

### 4.18.13 Foreground / Long-Running Scan Policy

V1 baseline is **restartable persistent scanning**, not "one Worker must remain alive until the whole library finishes".

Preferred order:

```text
can work be chunked/restarted safely?
→ yes: persistent WorkManager orchestration + durable state

must user see/expect immediate continuous execution beyond ordinary limits?
→ evaluate explicit foreground path
→ notification + cancel affordance + current service-type policy
→ benchmark/policy ADR if adopted
```

For local file processing, Android exposes `dataSync` as a possible foreground-service type, but Android 15 time limits and Android 16 job quota changes mean this path is not free/unbounded. A long-running WorkManager Worker also uses foreground-service machinery and remains a job under Android scheduling.

Therefore no architecture invariant may depend on uninterrupted foreground execution.

### 4.18.14 Process-Death Recovery

After abrupt process/runtime loss:

1. already-committed positive observations remain valid if their own evidence is valid;
2. unfinished negative-authority finalization is not assumed to have happened;
3. stale `RUNNING` ScanRuns are treated as non-authoritative interrupted/abandoned until explicitly recovered/reclassified;
4. a rescheduled/retried execution starts fresh authority by default;
5. UI reconstructs status from durable app state plus current scheduler telemetry;
6. no screen/singleton/service object is required to recover canonical Library/Progress/Reconciliation truth.

This directly preserves Q-SCN's process-death invariant.

### 4.18.15 Threading / Parallelism Guardrails

Q-RUN locks only the ownership rules, not a numeric thread pool.

- storage/provider/Room access runs off the main thread;
- CPU-heavy parsing/fingerprinting uses an appropriate bounded dispatcher/executor;
- do not launch unbounded per-file coroutines across very large trees;
- cancellation checks must remain frequent enough that cancel/supersede is responsive;
- exact traversal batch size, parallelism, hash scheduling and checkpoint frequency require benchmark evidence.

### 4.18.16 Work-Type Decision Table

| Work | Default V1 owner | Durability expectation |
|---|---|---|
| DB query / small parse | coroutine in owning scope | rerun/reconstruct |
| Small targeted observation | in-process allowed when bounded | interruption safe; `ScanRun` semantics if durable evidence is published |
| Full/root scan | WorkManager orchestration | request survives screen/process; execution restartable |
| Scheduled rescan | unique/periodic WorkManager trigger | each execution has fresh `ScanRun` semantics |
| Active playback | Media3 session/service | purpose-built continuous runtime |
| Future persistent download | download-specific runtime; evaluate current Android transfer APIs | resumable/restartable |
| Backup/export | semantics fixed by `Q-BACK-001`; exact executor/WorkManager use remains technical design | durable app-owned operation state if long-running |

### 4.18.17 Rejected Alternatives

#### Alternative A — Run all scans in ViewModel/application coroutine scope

Rejected because screen/process lifetime is not a persistent-work contract and process death would silently terminate work.

#### Alternative B — Put every operation in WorkManager

Rejected because short bounded work does not need persistent scheduling, while playback/continuous media runtime has purpose-built ownership.

#### Alternative C — Treat WorkInfo/WorkRequest state as `ScanRun` truth

Rejected because scheduler state does not encode Q-SCN scope/coverage/authority and Worker retries/recreation are runtime mechanics.

#### Alternative D — Whole scan in one database transaction

Rejected because it loses useful positive partial work, holds locks too long and creates fragile process-death behavior.

#### Alternative E — `onStopped()`/service callback finalizes correctness

Rejected because cancellation/process/user-stop paths may not provide a reliable final callback. Recovery must come from durable state.

#### Alternative F — Foreground service for every large scan

Rejected because foreground services are user-visible/restricted, Android 15 adds time limits and Android 16 job quota changes affect WorkManager long-running workers. It is an explicit runtime tool, not a universal durability primitive.

#### Alternative G — Retry every failure automatically

Rejected because permission loss, unsupported content or invariant bugs require different recovery. Infinite retry can waste battery and hide required user action.

#### Alternative H — Exactly-once execution assumption

Rejected because persistent schedulers/process recovery can re-enter work. Durable writes must be safe under repeat execution.

### 4.18.18 Downstream Impact

Q-RUN-001 unlocks these assumptions for runtime/persistence design:

```text
Scheduler state != domain run state.
WorkManager is the default persistent scheduler for full/scheduled scans.
Persistent scan execution is restartable/idempotent.
Each retry/restart earns fresh ScanRun authority by default.
Positive observations may commit incrementally.
Negative evidence is published only in guarded authoritative finalization.
Cancellation/supersession prevents stale finalization even without callbacks.
Foreground execution is optional/explicit, never a correctness dependency.
```

This permits later persistence/module/API design to introduce the minimum records/contracts needed for scan orchestration and recovery without baking WorkManager classes into domain models.

`Q-BACK-001` is **PROVISIONAL** and Stage F is complete. **Historical handoff:** this unlocked deep persistence/module/API design, which R4.14 has now completed.

### 4.18.19 Verification Ideas

Future tests must prove at least:

1. Full scan started from UI continues/gets rescheduled independently of the initiating screen.
2. Process death after committed positive batches leaves no blanket negative evidence.
3. Restart/retry creates fresh scan authority by default; an interrupted old run cannot later become authoritative accidentally.
4. Replaying the same positive observations does not duplicate Media/Unit/Asset identity.
5. User cancellation prevents final negative reconciliation even if cleanup callback is skipped.
6. `onStopped()` is not required for stale-run recovery.
7. Revoked SAF permission ends/diagnoses the run without infinite automatic retry or child mass-missing.
8. Transient provider/IO failure may retry with backoff without corrupting prior state.
9. Malformed single asset does not force root-wide retry when the rest of the scan can proceed safely.
10. Two same-root full-scan triggers coalesce/serialize; only current authority may finalize negative evidence.
11. A scope/config revision change supersedes the older run before negative finalization.
12. WorkManager WorkInfo can be lost/pruned/changed without losing canonical scan/history/library truth.
13. Killing the process from Android Task Manager leaves recoverable durable state and no dependence on a callback.
14. A normal bounded Worker stop/constraint loss does not require rolling back already-valid positive observations.
15. No long scan blocks the main thread or launches unbounded per-file concurrency.
16. Long-running/foreground execution path, if adopted, is tested against target-SDK foreground-service restrictions/timeouts/quota behavior.
17. Scheduled rescan delay/miss does not mutate content state merely because the scheduler did not run.
18. UI can reconstruct scan status after process recreation from durable operation state plus scheduler telemetry.

### 4.18.20 Remaining Risks / Deferred Details

Q-RUN-001 intentionally does **not** decide:

- exact WorkManager/version/toolchain configuration;
- exact Worker/class/interface names;
- exact Room schema for ScanRun/checkpoints/orchestration records;
- exact unique-work name format or KEEP/REPLACE choice for each trigger;
- exact periodic rescan cadence;
- exact batch size/checkpoint interval;
- exact coroutine dispatcher/thread-pool/parallelism values;
- exact hash/fingerprint scheduling thresholds;
- whether V1 eventually needs long-running WorkManager foreground mode or a direct foreground service for exceptional manual scans;
- foreground notification UX;
- exact stale-run age/lease detection mechanism;
- backup/export runtime details under `Q-BACK-001` semantics;
- future download API choice, including whether UIDT is appropriate for a concrete transfer use case.

None of these details may weaken Q-SCN/Q-REC authority or make process lifetime a source of truth.

### 4.18.21 ADR Requirement

No separate ADR is required for the Q-RUN semantic boundary itself.

Create an ADR before adopting any runtime choice that materially changes guarantees, especially:

- a direct foreground-service scanner architecture;
- JobScheduler/UIDT in place of WorkManager for a core persistent operation;
- fine-grained cross-attempt scan checkpoint resumption that reuses prior coverage for negative authority;
- a scheduler-specific schema/API that would leak Android WorkManager identity into domain contracts.

### 4.18.22 Research References

Primary Android / Jetpack sources:

- AndroidX WorkManager package/reference — persistent work guarantees and execution window: https://developer.android.com/reference/androidx/work/package-summary
- WorkManager management / unique work / cancellation and stopping: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work
- `CoroutineWorker` cancellation/stop contract: https://developer.android.com/reference/androidx/work/CoroutineWorker
- WorkManager long-running workers / foreground support / Android 16 quota note: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
- Android foreground services overview: https://developer.android.com/develop/background-work/services/fgs
- Foreground service start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Foreground-service timeout behavior: https://developer.android.com/develop/background-work/services/fgs/timeout
- Android 15 behavior changes — `dataSync` / `mediaProcessing` timeout: https://developer.android.com/about/versions/15/behavior-changes-15
- Android 16 behavior changes — job runtime quota changes: https://developer.android.com/about/versions/16/behavior-changes-all
- Foreground service types — `dataSync` includes local file processing: https://developer.android.com/develop/background-work/services/fgs/service-types
- User-initiated data transfer jobs: https://developer.android.com/develop/background-work/background-tasks/uidt
- Android process lifecycle: https://developer.android.com/reference/android/app/Activity

Production/reference implementations:

- Now in Android `SyncWorker`: https://github.com/android/nowinandroid/blob/main/sync/work/src/main/kotlin/com/google/samples/apps/nowinandroid/sync/workers/SyncWorker.kt
- Android offline-first guidance / Now in Android unique sync work: https://developer.android.com/topic/architecture/data-layer/offline-first
- VLC Android medialibrary integration: https://github.com/videolan/vlc-android/blob/master/medialibrary/jni/AndroidMediaLibrary.h
- VLC Android manifest/service permissions: https://github.com/videolan/vlc-android/blob/master/application/vlc-android/AndroidManifest.xml
- Media3 offline `DownloadService`: https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/offline/DownloadService.java

Research note: Android official scheduler/runtime contracts have higher authority than any single app implementation. Project examples are used to expose lifetime trade-offs, not copied as architecture templates.

### 4.18.23 Stage F Partial Closure Summary

Stage F now has a provisional execution baseline:

```text
bounded process-scoped work
→ structured concurrency

persistent scan/maintenance request
→ WorkManager orchestration
→ fresh/restartable ScanRun attempt(s)
→ incremental positive commits
→ guarded authoritative finalization

continuous playback
→ Media3 session/service

foreground escalation
→ explicit exceptional runtime choice only
```

Critical invariants:

```text
Worker lifetime != domain truth
WorkRequestId != ScanRunId
process death != deletion evidence
retry != continuation of old negative authority by default
cancellation callback != required correctness hook
persistent execution assumes replay/re-entry
foreground service != unlimited durability
```

`Q-RUN-001` is **PROVISIONAL**.

**Stage F status:** `PROVISIONAL BASELINE / COMPLETE`

**Historical Stage F next work:** deep persistence schema + module/API boundary design. R4.14 completed that work and R4.15 subsequently completed `Q-BOOT-001`; R4.16 has generated the skeleton; current work is executable bootstrap verification (official wrapper + JDK 17 + SDK 37 + Gradle/Android/CI evidence).

## 4.19 Decision Record — Q-BACK-001: Backup / Restore Ownership / Portable App State / Storage Re-Authorization

**Status:** `PROVISIONAL`

Q-BACK-001 completes Stage F by deciding what V1 means by **backup/restore** for a local-first Android media client. Earlier stages already define canonical identity, source/storage boundaries, reconciliation, typed progress/history/library state, metadata provenance and Android execution ownership. This record keeps those semantics intact while defining which state is portable, which state is only device/runtime-local, how restore interacts with SAF grants and external media, and how version migration avoids turning a backup into a raw persistence-format dependency.

The product capability in V1 is a **user-controlled local backup/restore of app-owned durable state**. Android Auto Backup and device-to-device transfer may supplement that experience, but they are platform convenience mechanisms and are not the product's sole recovery contract.

### 4.19.1 Problem Statement

A universal local-media app can restore its own database bytes and still be semantically broken if it pretends external content/access survived unchanged.

Representative scenarios:

```text
A. User exports backup on phone A and restores on phone B.
   Media files are copied separately, possibly into different folders/URIs.

B. App is uninstalled/reinstalled on the same device.
   Android app data may restore, but SAF access must not be assumed valid.

C. Android device-to-device migration restores app-private state.
   Old document URIs may not resolve on the new device.

D. Backup was produced by an older app/database version.
   Current app must preserve user state without destructive DB fallback.

E. Restore occurs while current installation already contains library/progress data.
   Blind merge can collide canonical IDs and duplicate History/Library/Assets.

F. Backup contains a StorageRoot registration for an SD card not currently mounted.

G. Backup contains Asset hashes/old locators but no media bytes.
   Those facts may help reconciliation but cannot prove current availability.

H. Backup/export is interrupted or archive is corrupted/truncated.
   Live state must not become half-restored.

I. Backup contains credentials/device-bound encrypted state.
   Restoring ciphertext without its key can make state unusable or leak secrets.

J. A scan/WorkManager job was RUNNING when backup was created.
   Restore must not resurrect stale negative authority or scheduler execution.
```

Q-BACK-001 must answer:

1. What is the V1 portable backup boundary?
2. Is a public backup a raw Room/database copy or a separate versioned contract?
3. Which canonical/user state is included and which runtime/cache/external state is excluded?
4. How are `StorageRoot`, SourceBinding, Asset locator and identity evidence restored without pretending URI grants survived?
5. How does restore handle old backup versions and current schema versions?
6. Is restore merge or replace by default?
7. What consistency/atomicity guarantees apply if import fails?
8. What role, if any, do Android Auto Backup and device-to-device transfer play?

### 4.19.2 Primary Android / Platform Findings

#### Android Auto Backup is useful but quota-limited and file-oriented

Android Auto Backup includes most app-private files by default, including SharedPreferences, internal files and databases, with an app-level cloud backup quota of 25 MB. Android allows explicit include/exclude rules and, on Android 12+, separate `dataExtractionRules` for cloud backup and device-to-device transfer.

**Observed fact:** the platform can preserve app-private files/database state automatically, but its cloud mechanism has a small fixed quota and operates on files rather than this project's canonical domain semantics.

**Inference:** Auto Backup can be a convenience path for carefully selected state, but it cannot be the only V1 backup guarantee for a potentially large media-library database.

#### Restore happens before normal first launch

For Auto Backup, Android restores data after APK installation and before the app becomes available to launch. The restored database/preferences can therefore be from an older schema/app version and must survive normal application migration/opening rules.

**Inference:** restored state must use supported persistence migrations; destructive migration of core user state is not an acceptable hidden recovery strategy.

#### Android explicitly warns against treating restored URIs as stable

Android backup guidance warns that URIs can be unstable across restoration to another device and recommends backing up semantic metadata instead of assuming a restored URI still identifies the intended resource. SAF persistable URI permission also guarantees only a platform access capability under its own lifecycle; even a persisted grant can fail after document move/delete.

**Inference:** `StorageRoot`/Asset locators may be carried as hints/evidence, but restore must revalidate access and must not treat restored URI/document IDs as current truth or app-owned identity.

#### Android 12+ separates cloud backup and device-to-device rules

The platform distinguishes cloud backup from D2D transfer and provides separate extraction-rule sections. `allowBackup` behavior also differs for modern D2D migration, so relying on one manifest boolean as the entire portability policy is insufficient.

**Inference:** the project needs an explicit data-classification matrix for platform backup rather than accepting Android's default “backup most app files” behavior accidentally.

#### Sensitive/device-bound encrypted data needs explicit exclusion/recovery design

Android backup security guidance recommends excluding sensitive state when appropriate. AndroidX documentation for keystore-backed encrypted preferences warns that restoring encrypted preference files can fail because the encryption key may not exist on the destination device.

**Inference:** credentials, device-bound keys and encrypted blobs that depend on non-portable key material cannot be included blindly in backup. Secret recovery is a separate concern from library/progress restoration.

#### Room migrations are the compatibility boundary for restored raw platform DB files

Room requires a valid migration path to preserve existing database contents when schema changes. `fallbackToDestructiveMigration` explicitly deletes data when no path exists.

**Inference:** platform restore of a Room database must either migrate successfully or fail/recover explicitly. Core canonical/user state must never silently disappear because a restored database encountered destructive fallback.

### 4.19.3 Mature Project Evidence

Research compares Android client apps, media/library systems and desktop/server libraries so the project does not copy one ecosystem's backup shape.

#### Mihon — backup logical user/app state, not downloaded/local media bytes

Mihon backups include library entries, chapter data, tracking, history, categories and settings. Extensions and downloaded/local chapter files are not bundled; after restore the user restores storage/source capability separately and may reindex downloads.

**Observed fact:** a mature Android reader separates portable app state from external/content bytes and external capabilities.

**Inference:** this project should restore canonical/user state independently from the physical local-media corpus.

#### AntennaPod — database backup restores app state, media must be reacquired

AntennaPod documents database export/import as the high-fidelity migration path. After importing the database, episodes may need to be downloaded again; OPML is intentionally a lower-fidelity portable format that loses episode/user state.

**Observed fact:** backup fidelity is an explicit contract, and content availability is not equivalent to restoring the state database.

**Inference:** V1 should define one high-fidelity app-state backup rather than imply that lightweight metadata exports are equivalent restoration.

#### AnkiDroid — automatic collection backups preserve logical collection, not media files

AnkiDroid's automatic backups include cards/statistics but not sound/image media. Full collection export/import is a separate user-controlled operation.

**Observed fact:** user-owned state and potentially large media payloads can have independent backup lifecycles even in an offline-first app.

**Inference:** external media bytes should remain outside the default V1 state backup.

#### Kodi — application data is backed separately from external media

Kodi's backup guidance explicitly says its media files are external to Kodi backup; the application data/config/database can be backed, while media remains separately managed. Kodi also warns that whole-data-folder restore across different major versions/operating systems is not universally portable.

**Observed fact:** raw application-directory copies are tightly coupled to software/platform versions.

**Inference:** this project should not expose “copy the whole app directory/Room database” as its stable public backup format.

#### calibre — explicit export/import owns migration/relocation semantics

calibre provides an explicit “export/import all calibre data” workflow and asks users to choose library locations during import to another machine.

**Observed fact:** a library application's portable export can preserve logical state while rebinding location-dependent resources at restore time.

**Inference:** restored StorageRoot/access boundaries should require explicit location/access reattachment rather than pretend the old storage topology still exists.

#### Jellyfin — consistent backup must respect database/runtime version boundaries

Jellyfin's built-in backup separates database and optional generated/supporting data; its manual backup requires stopping the server for consistency, and restore/version compatibility is treated explicitly.

**Observed fact:** live database copying and version mismatch are correctness concerns, not merely file-copy mechanics.

**Inference:** V1 explicit backup must produce a consistent logical snapshot and validate compatibility before mutating live state.

### 4.19.4 Decision — Two Backup Channels, One Domain Truth

V1 distinguishes two channels:

```text
1. Product backup
   user-controlled portable app-state export/import
   = V1 recovery contract

2. Android platform backup / D2D
   selected app-private state under Android rules
   = supplemental convenience only
```

Neither channel changes domain ownership.

```text
backup medium != canonical identity
backup file    != live database authority
restored URI   != current access authority
```

### 4.19.5 V1 Product Backup Is a Versioned Logical Contract

The public/user-visible V1 backup is **not defined as a raw Room database file**.

Conceptually:

```text
BackupArchive
├── manifest
│   ├── backupFormatVersion
│   ├── producer app/version metadata
│   ├── creation metadata
│   └── integrity/feature compatibility metadata
└── logical app-owned state payload(s)
```

Exact serialization/container/encryption technology is deferred.

Rules:

- `backupFormatVersion` is independent from Room schema version and app version;
- older supported backup formats are migrated through an import model/pipeline before applying to live persistence;
- a future backup format unknown to the current app is rejected clearly rather than partially interpreted;
- raw Room entity/table layout is not a public portable format contract;
- backup restore preserves app-owned canonical IDs/relations from the backup instead of regenerating them from filenames/URIs;
- implementation may use a consistent database snapshot internally while exporting, but the externally supported restore contract remains the versioned logical backup model.

### 4.19.6 Included Portable State

V1 product backup includes durable state whose authority belongs to the app/user and whose loss would violate previously locked semantics.

At minimum, subject to exact persistence design:

```text
Canonical identity
- Media / MediaGrouping / MediaUnit internal IDs and required canonical relations

Library/user intent
- LibraryEntry membership
- explicit remove/suppression intent
- current membership epoch / libraryAddedAt semantics

Consumption state
- Progress including typed resume anchor + completion
- representation compatibility/provenance context needed by Q-PROG-002
- HistoryEntry

Source/mapping state
- configured V1 Source identity/config that is portable
- SourceBinding target associations
- ExternalId / Mapping state

Local representation/reconciliation state
- AssetId / AssetRevision lineage
- selected portable identity evidence/provenance useful for reattachment
- hashes/fingerprints if already computed and semantically useful

Storage registration intent
- RootId
- user-facing root label/hints
- access-strategy kind and non-secret reattachment metadata
- NOT an assertion that access is currently valid

Metadata
- user overrides, including explicit empty
- metadata mappings/candidates/provenance with durable value
- last-known effective/descriptive state where needed to preserve offline behavior

Settings
- portable user preferences whose meaning is device-independent
```

Exact field list belongs to persistence/backup technical design, but it may not omit state required to preserve the invariants above.

### 4.19.7 Explicitly Excluded or Non-Authoritative State

Default V1 product backup does **not** include as portable truth:

```text
External/user media bytes
- MKV / MP4 / WebM
- CBZ / ZIP / image folders
- EPUB files under registered user storage

Regenerable data
- image/artwork/thumbnail caches
- temporary parser output
- transient search/cache data

Android/runtime execution state
- WorkRequest/WorkInfo IDs
- running Worker/service/coroutine state
- current scheduler leases
- active ScanRun negative authority / unfinished coverage
- process/session objects

Ephemeral resolved content
- signed URLs
- cookies/session headers
- opened Media3/Readium/runtime objects

Android access authority
- SAF permission grants as if they were transferable
- current URI reachability assertion
- MediaStore row reachability assertion

Device-bound or sensitive state by default
- Android Keystore key material
- auth/session tokens or credentials unless a future explicit secure export contract allows them
- encrypted blobs whose decrypt key is not demonstrably portable
```

If future V1 UI permits a user-authored custom asset (for example an explicitly imported cover) whose bytes are themselves user state rather than cache, that feature must define its own backup classification before implementation. It cannot be silently dropped merely because other artwork is cache.

### 4.19.8 StorageRoot Restore = Registration Intent, Not Grant Restore

A restored `StorageRoot` preserves app-owned registration identity and user intent, but enters restore in a **not-yet-authorized/revalidated** access posture.

Conceptually:

```text
restored RootId + label/hints
        ↓
NEEDS_REAUTHORIZATION / UNVERIFIED_ACCESS
        ↓ user selects/grants tree or platform access
revalidate + reconcile
        ↓
accessible root under existing RootId when evidence supports continuity
```

Exact enum/UI wording is not locked.

Rules:

- restored tree/document URI is only locator/historical evidence;
- restored URI is never trusted solely because it is syntactically present;
- app checks actual current platform grant/capability before use;
- on another device, user normally re-selects/re-authorizes the relevant root;
- a newly authorized tree may reattach to the existing `RootId` when explicit user flow + evidence support it;
- if user chooses a different physical root containing the same corpus, Q-ID/Q-REC decide Asset continuity; backup does not rewrite identity by path;
- unmounted/unavailable root remains registered/unavailable rather than deleting Library/Progress state.

### 4.19.9 Restored Assets and Bindings Remain Known but Not Presumed Present

Backup restores canonical/source/Asset associations because they carry durable user and reconciliation value.

However:

```text
restored Asset locator/evidence
!= PRESENT observation on destination device
```

Default semantics:

- `AssetId`/revision/binding lineage may be restored;
- current availability is `UNCONFIRMED/LAST_KNOWN` until real observation occurs;
- restoration itself cannot create authoritative `MISSING` state merely because media is not immediately accessible;
- after root reauthorization, a fresh Q-SCN-compliant scan supplies positive/negative authority;
- strong hashes/fingerprints restored from backup are evidence caches and must be tied to the prior revision/provenance; they do not prove a newly encountered file is the same Asset without normal Q-ID/Q-REC checks.

This preserves the critical rule:

```text
backup restore without media access
!= mass deletion
!= mass missing
```

### 4.19.10 Restore Does Not Resume Operational Runtime State

Backup/import does not resurrect in-flight execution.

On restore:

- old `RUNNING` ScanRun authority is not resumed as authoritative work;
- old WorkManager/Worker IDs are not restored as domain jobs;
- stale operation leases/checkpoints that exist only for runtime recovery are excluded/reset;
- a new rescan/maintenance request, if needed, creates fresh Q-RUN/Q-SCN authority;
- pending destructive commands are never replayed merely because they existed in a backup.

Historical run diagnostics may be exported later only if a real user/support use case exists; they are not required V1 portable state.

### 4.19.11 Restore Mode — Replace, Not General Merge, in V1

V1 explicit backup restore uses **replace-the-app-owned-state** semantics, not arbitrary merge.

Reason:

```text
Backup A canonical IDs
+ Existing installation B canonical IDs
+ Library suppression/history/progress/bindings
→ no safe generic merge without a new conflict/reconciliation model
```

Rules:

- restore into a non-empty installation must be an explicit replacement operation;
- implementation should preserve/offer a pre-restore safety snapshot when practical, but exact UX is technical/product design;
- V1 does not invent automatic cross-backup ID deduplication or history/progress merge policy;
- future “merge/import selected state” is a separate question/versioned feature.

Android system restore on install naturally fits replacement semantics because it restores before ordinary app use.

### 4.19.12 Atomic Restore / Staging Rule

Explicit restore must not mutate live durable state incrementally while the archive is still unverified.

Conceptual flow:

```text
open backup
  ↓
validate container/integrity/version
  ↓
decode into backup model
  ↓
migrate backup model to supported current logical version
  ↓
validate invariants/references
  ↓
stage replacement state
  ↓
atomic/transactional commit or controlled database swap
  ↓
reset runtime-only authority/caches
  ↓
start in restored-but-storage-unverified state
```

Rules:

- malformed/truncated/incompatible backup fails before destructive live replacement;
- failure during staging leaves the previous live state usable;
- canonical IDs and internal referential integrity are validated before commit;
- external root/media access is not required for the logical restore transaction to succeed;
- caches/derived projections can be regenerated after commit;
- exact Room transaction/temp-database/swap strategy is downstream persistence design.

### 4.19.13 Backup Consistency Rule

Backup must represent a coherent app-owned snapshot, but it does not need a filesystem snapshot of user media.

For explicit product backup:

- backup reads a transactionally/otherwise consistently captured logical state boundary;
- long-running export may stream after creating a stable snapshot/checkpoint rather than hold the live database locked for the whole archive write;
- concurrent Progress/Library changes must be either included according to a clear snapshot point or left for the next backup, not produce cross-table torn state;
- media files can change during backup because they are external evidence, not payload authority;
- exact snapshot mechanism is decided with persistence/benchmark work.

### 4.19.14 Backup Versioning vs Room Schema Migration

There are two independent compatibility paths:

```text
Android platform restore of private database file
→ normal Room schema migration

V1 explicit BackupArchive restore
→ backup-format migration
→ current logical import model
→ current persistence schema
```

Rules:

- never equate `backupFormatVersion` with Room `schemaVersion`;
- every released backup format that remains supported needs migration tests/fixtures;
- current app may support a defined range of older backup versions;
- restoring a backup created by a newer incompatible app is rejected rather than best-effort partial import;
- core user/canonical database must not use destructive Room fallback as normal restore migration behavior;
- if a platform-restored raw DB cannot migrate safely, app must surface/recover explicitly rather than silently recreate an empty library.

### 4.19.15 Platform Auto Backup / D2D Policy

Android platform backup is **supplemental** and must use explicit classification rules.

Foundation-level rules:

- do not accept default blanket backup accidentally;
- define `dataExtractionRules` (and legacy rules where supported device range requires them) intentionally;
- exclude caches, runtime-operation state, credentials/device-bound encrypted state and anything whose restore would be unsafe;
- cloud Auto Backup's 25 MB quota means full canonical DB inclusion cannot be assumed safe for arbitrary library size;
- D2D may use a different inclusion policy from cloud backup because Android exposes separate rules;
- exact decision about whether the canonical Room DB participates in cloud backup, D2D only, or neither is deferred to the pre-implementation backup technical spec after representative DB-size/security measurements;
- regardless of platform file inclusion, restored root/URI/access state still obeys this Q-BACK revalidation model.

The V1 user-controlled BackupArchive remains the guaranteed portable recovery mechanism even if platform backup is unavailable, disabled, quota-exceeded or OEM-dependent.

### 4.19.16 Secret / Credential Policy

Backup scope follows least privilege.

Default rules:

- session cookies, auth bearer tokens and provider credentials are excluded from user-visible backup unless explicitly opted into a future secure credential-export design;
- Android Keystore keys are never treated as portable backup payload;
- device-bound encrypted preference/database material is excluded from platform backup unless recovery of its key is demonstrably supported;
- metadata/provider configuration that is non-secret may be backed up; secrets are re-entered/re-authenticated after restore;
- future V2/V3 account/tracking/plugin credentials require an explicit backup/security decision and must not inherit “include all settings” automatically.

Backup archive encryption/password UX is **deferred**. Until such a feature is explicitly designed, a user-exported backup file must not be described as cryptographically confidential merely because Android app-private data was secure before export.

### 4.19.17 Restore Completion Is Not Storage Reconciliation Completion

Restore has two distinct milestones:

```text
LOGICAL_RESTORE_COMPLETE
= app-owned durable state validated + committed

STORAGE_REATTACHMENT_COMPLETE
= required roots reauthorized + fresh observation/reconciliation performed
```

The app may be usable in between:

- Library metadata/progress/history remain visible offline;
- local content whose storage is not reauthorized is unavailable but not deleted;
- user can reattach roots one by one;
- fresh scans update availability under Q-SCN/Q-REC without replacing canonical state blindly.

This avoids making restore success depend on external media being physically present at import time.

### 4.19.18 Rejected Alternatives

#### Alternative A — Public backup is a raw Room database file

Rejected because it couples portability to persistence schema, makes selective exclusion/security difficult and turns database migration details into the public backup contract.

#### Alternative B — Copy the whole app data directory

Rejected because caches/runtime state/device-specific files can be included accidentally and cross-version/device compatibility becomes implicit and brittle.

#### Alternative C — Android Auto Backup is sufficient as the only V1 backup

Rejected because cloud backup is quota-limited, platform/OEM/user-setting dependent and not a user-accessible portable archive contract for a potentially large library.

#### Alternative D — Include local media files in the default backup archive

Rejected because V1 backup is app-state recovery, not full media duplication. User media can be enormous and already has independent ownership/storage lifecycle.

#### Alternative E — Restored SAF URI/grant immediately means root ACCESSIBLE

Rejected because URI/access capabilities are platform-owned, may be invalid on the destination device and Android explicitly warns restored URIs can be unstable.

#### Alternative F — Missing media immediately after restore means Asset MISSING/deleted

Rejected because restore has not yet produced authoritative scan coverage. Q-SCN/Q-REC still control negative evidence.

#### Alternative G — Generic merge backup into existing installation

Rejected for V1 because canonical IDs, history/progress, library suppression, bindings and metadata overrides need a dedicated conflict model. Replace semantics are deterministic and reversible with a pre-restore safety backup.

#### Alternative H — Restore incomplete archive directly into live DB as records are parsed

Rejected because corruption/version error can leave half-restored state. Validate/migrate/stage before commit.

#### Alternative I — Back up every setting including credentials/device keys

Rejected because some secrets are non-portable and keystore-backed ciphertext may be undecryptable after restore.

#### Alternative J — Resume old ScanRun/WorkRequest from backup

Rejected because scheduler/runtime state is not domain truth and stale coverage must never regain negative authority.

### 4.19.19 Downstream Impact

Q-BACK-001 unlocks deep V1 persistence/module/API design with these assumptions:

```text
V1 product backup = versioned logical app-state archive.
Raw Room schema != public backup format.
Canonical IDs/user state are portable.
External media bytes are not part of default state backup.
Root registration intent is portable; Android access grant is not.
Restored Asset availability starts unconfirmed until fresh observation.
Restore does not create MISSING/deletion evidence.
Restore replaces app-owned state in V1; generic merge is deferred.
Restore validates/migrates/stages before live commit.
Backup-format migration and Room migration are separate concerns.
Operational scheduler/ScanRun authority is not portable.
Platform Auto Backup/D2D is supplemental and explicitly classified.
Secrets/device-bound keys are excluded by default.
```

Persistence design may now choose tables/foreign keys/transactions knowing which records need portable semantics and which operational/cache records can be reconstructed.

### 4.19.20 Verification Ideas

Future implementation must prove at least:

1. Backup/restore preserves `MediaId`/`UnitId` and Library/Progress/History relations.
2. Explicit user removal/suppression survives backup so rescan does not silently re-add a removed Media.
3. `libraryAddedAt` membership-epoch semantics survive restore.
4. Typed Progress/completion restores even when no local media is currently accessible.
5. Metadata user overrides, including explicit empty, survive restore and still outrank automated refresh.
6. A restored StorageRoot does not claim current SAF access until actual reauthorization/revalidation succeeds.
7. Restoring on a new device with different tree URI can reattach to existing RootId/canonical state through explicit flow + reconciliation evidence.
8. Restore with no media present creates no mass `MISSING`/`REMOVED` transitions.
9. After reauthorization, fresh authoritative scan can restore/preserve AssetIds when Q-ID/Q-REC evidence supports continuity.
10. Old locator/hashes remain evidence only and cannot bypass conflict/ambiguity rules.
11. Backup created on an older supported backup format migrates into the current app without changing canonical identity.
12. Newer unsupported backup format is rejected before mutating live state.
13. Corrupt/truncated archive leaves existing installation unchanged.
14. Restore to a non-empty installation uses explicit replacement semantics and does not silently merge histories/IDs.
15. WorkManager IDs, RUNNING ScanRuns and stale negative coverage are not resurrected by restore.
16. First launch after Android platform DB restore runs Room migrations and does not use destructive fallback for core state.
17. Platform-restored invalid URI does not crash startup or delete library state.
18. Cache/thumbnails may be absent after restore and regenerate without affecting canonical/user state.
19. Backup archive contains no Android Keystore key material and default backup excludes sensitive auth/session tokens.
20. A library database larger than Android cloud Auto Backup quota still has a working explicit user backup path.
21. Backup taken while Progress changes produces a coherent snapshot rather than torn cross-table state.
22. Restore can complete logically while one or more roots remain unavailable/awaiting user reauthorization.

### 4.19.21 Remaining Risks / Deferred Details

Q-BACK-001 intentionally does **not** decide:

- exact archive/container format (`zip`, protobuf, CBOR, JSON envelope, etc.);
- exact compression method;
- exact integrity checksum/hash layout;
- backup file extension;
- optional password/encryption UX and key derivation;
- exact user-facing pre-restore safety-backup flow;
- exact staging implementation (single Room transaction vs temporary DB + swap);
- exact export snapshot/checkpoint mechanism;
- exact Auto Backup cloud/D2D include/exclude file matrix until representative DB-size/security measurements;
- exact automatic local backup cadence/retention count;
- exact WorkManager/foreground runtime for scheduled backup/export;
- selective restore UI;
- merge/import-from-another-install semantics;
- backup of future downloaded content;
- V2/V3 credentials/account/plugin/tracking/sync backup policy;
- cloud backup service owned by the app.

None of these deferred items may redefine canonical identity, treat locators/grants as portable authority, silently drop user state, or bypass the staged/validated restore rules above.

### 4.19.22 ADR Requirement

No separate ADR is required for the Q-BACK semantic boundary itself.

Create an ADR before adopting a choice that materially changes portability/security guarantees, especially:

- raw database as a public long-term backup format;
- encrypted/password-protected backup format and key management;
- cross-install merge semantics;
- backing up/restoring external media/download payloads;
- platform cloud backup of a large/sensitive canonical database as a required recovery mechanism;
- a schema/snapshot architecture that cannot atomically reject a failed restore.

### 4.19.23 Research References

Primary Android / Jetpack sources:

- Android data backup overview — Auto Backup options, 25 MB limit, warning against restoring unstable URIs: https://developer.android.com/identity/data/backup
- Android Auto Backup — included app-private files/databases, restore schedule, rules and quota: https://developer.android.com/identity/data/autobackup
- Android 12 backup/restore behavior and `dataExtractionRules`: https://developer.android.com/about/versions/12/behavior-changes-12
- Android backup security recommendations: https://developer.android.com/privacy-and-security/risks/backup-best-practices
- SAF document access / persistable URI permissions and their limits: https://developer.android.com/training/data-storage/shared/documents-files
- Android shared-storage ownership: https://developer.android.com/training/data-storage/shared
- Room database migration guidance/testing: https://developer.android.com/training/data-storage/room/migrating-db-versions
- DataStore backup/restore and rule classification: https://developer.android.com/topic/libraries/architecture/datastore
- AndroidX `EncryptedSharedPreferences` warning against backup when key may not restore: https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences

Mature project evidence:

- Mihon backup/restore scope: https://mihon.app/docs/guides/backups
- AntennaPod backup/export fidelity: https://antennapod.org/documentation/general/backup
- AnkiDroid backup/export behavior: https://docs.ankidroid.org/
- Kodi backup — app data separated from external media and version/platform cautions: https://kodi.wiki/view/Backup
- calibre export/import all data and location rebinding: https://manual.calibre-ebook.com/faq.html
- Jellyfin backup/restore consistency and version boundaries: https://jellyfin.org/docs/general/administration/backup-and-restore/

Research note: Android platform contracts have authority for Auto Backup/SAF behavior. Mature projects are used to compare portable-state/content boundaries and recovery workflows; their raw formats and server/reader-specific assumptions are not copied into this project's architecture.

### 4.19.24 Stage F Closure Summary

Stage F now has a coherent provisional Android execution + portability baseline:

```text
Q-RUN-001
runtime/scheduler ownership
→ restartable/idempotent persistent execution
→ scheduler state never becomes domain truth

Q-BACK-001
portable app-owned state
→ versioned logical backup
→ validated/staged replacement restore
→ storage access reauthorization/reconciliation
```

Critical invariants:

```text
WorkRequestId != ScanRunId
backupFormatVersion != RoomSchemaVersion
backup file != live database authority
restored URI != current access authority
restored asset != currently PRESENT proof
restore without media != MISSING/deletion evidence
external media bytes != default app-state backup
platform Auto Backup != sole V1 recovery contract
secret/device-bound key != default portable state
```

`Q-BACK-001` is **PROVISIONAL**.

**Stage F status:** `PROVISIONAL BASELINE / COMPLETE`

**Stages A–F status:** `PROVISIONAL BASELINE / COMPLETE`

**Historical Stage F handoff:** deep persistence schema + module/API boundary design was next. R4.14 completed `Q-PER-001`, `Q-MOD-001` and `Q-API-001` at PROVISIONAL level; R4.15 subsequently completed `Q-BOOT-001`. R4.16 has generated the skeleton; current work is executable bootstrap verification (official wrapper + JDK 17 + SDK 37 + Gradle/Android/CI evidence).


## 4.20 Audit / Development Loop

Trong giai đoạn Decision Queue còn `OPEN`, V1 dùng vòng lặp sau thay vì audit toàn bộ rồi mới implement một lần:

```text
Select next OPEN question from Decision Queue
        ↓
Research + examples + failure cases
        ↓
Choose provisional/locked semantics
        ↓
Update this baseline / ADR if needed
        ↓
Self-review downstream contradictions
        ↓
Design only the now-unblocked subsystem slice
        ↓
Implement + test + measure
        ↓
Feed implementation evidence back into next audit question
```

Sau `Q-BACK-001`, foundation semantic queue không còn question `OPEN`. R4.14 completed the first downstream design tranche (`Q-PER-001`, `Q-MOD-001`, `Q-API-001`) and R4.15 completed `Q-BOOT-001`. Current flow is:

```text
R4.16 skeleton generated + static gates verified
        ↓
executable bootstrap verification on JDK 17 + SDK 37
        ↓
first vertical-slice implementation
        ↓
tests + process-death/storage/performance evidence
        ↓
contradiction found?
   ├── no  → continue breadth by roadmap/gates
   └── yes → reopen affected Question ID + impact review
```

Rule vẫn giữ: **không deep-design production schema/API của một concept đang bị question `OPEN` trực tiếp block**. Việc queue hiện complete không biến mọi provisional choice thành immutable; implementation evidence vẫn có thể reopen đúng question theo Agent Rules.

---

## 4.21 Downstream Design Ledger — Persistence / Module / API

Sau khi `Q-BACK-001` đóng Stage F, foundation không còn question semantic `OPEN`. Theo continuation protocol, work chuyển sang downstream design thay vì mở lại research breadth cũ. R4.14 tạo một ledger riêng để phân biệt **semantic foundation questions** với **production design decisions** đã được unlock.

| ID | Design question | Why it matters | Status |
|---|---|---|---|
| `Q-PER-001` | App-owned durable state được map vào Room/DataStore/files thế nào; relational schema, transaction, migration, corruption và backup boundary ra sao? | Đây là production persistence baseline; sai ở đây sẽ làm identity/progress/reconciliation khó migrate. | PROVISIONAL |
| `Q-MOD-001` | Initial Gradle/module dependency graph nào đủ enforce boundaries mà không over-modularize? | Cần trước khi feature count tăng và trước khi implementation details leak xuyên project. | PROVISIONAL |
| `Q-API-001` | Public/internal API boundaries, ports, transaction ownership, errors và composition root hoạt động thế nào? | Tránh Room/SAF/Media3/provider DTO trở thành de facto domain API. | PROVISIONAL |
| `Q-BOOT-001` | Exact SDK/toolchain/version catalog/convention plugins/minSdk baseline nào được pin cho bootstrap? | Cần để tạo project/build CI thật với compatibility matrix reproducible. | PROVISIONAL |

`Q-PER-001 → Q-MOD-001 → Q-API-001` được hoàn tất trong R4.14 ở mức **PROVISIONAL**. R4.15 hoàn tất `Q-BOOT-001` ở mức **PROVISIONAL**. DI **strategy** vẫn là constructor injection + app composition root; bootstrap không mở DI framework như blocker vì chưa có implementation evidence cho thấy framework mang value đủ lớn.

## 4.22 Decision Record — Q-PER-001: Persistence Ownership / Relational Schema / Migration

**Status:** `PROVISIONAL`

### 4.22.1 Problem Statement

Stages A–F đã khóa ownership semantics nhưng chưa biến chúng thành một production persistence shape. Persistence design phải đồng thời giữ các invariant sau:

```text
canonical identity is app-owned
representation/location/evidence are not identity
partial scan absence is not deletion
progress/history/library are user state above Asset availability
runtime/scheduler state is not domain truth
backup format is not Room schema
```

Nếu schema mirror filesystem/provider DTO hoặc dồn mọi relation vào JSON blobs, reconciliation/migration/query integrity sẽ yếu. Nếu normalize quá mức hoặc materialize mọi projection/candidate, V1 lại over-engineer trước vertical slice.

Q-PER-001 phải quyết tối thiểu:

1. Một hay nhiều canonical relational DB?
2. State nào là durable truth, operational durable state, derived cache hay external authority?
3. `ConsumptionTargetRef` được FK hóa thế nào mà không tạo fake canonical entity?
4. Progress typed anchors, SourceBinding, Asset lineage, root/scan semantics và metadata provenance được persist ở đâu?
5. Transaction nào phải atomic và transaction nào **không được** kéo dài cả scan?
6. FK/delete behavior nào bảo vệ user state khỏi destructive cascade?
7. Migration/corruption policy nào giữ user data qua app versions?
8. Logical backup map sang schema này thế nào mà không publicize Room internals?

### 4.22.2 Primary Android / SQLite Findings

#### Room is relational persistence, not a domain-object graph serializer

Room uses SQLite relationships and deliberately does not allow entities to hold direct object references as ORM-owned graphs. Relationships are queried explicitly through joins/intermediate projections/multimaps. This is useful for this project because domain aggregate/read models can differ from storage entities instead of forcing `Media`/`Asset`/`Progress` into one recursive persisted object.

**Project inference:** Room entities/DAO projections remain persistence implementation types; repositories map them into domain/read models.

#### Foreign keys can enforce app-owned referential integrity

Room/SQLite foreign keys ensure child relations reference valid parent keys, and child FK columns should be indexed to avoid expensive full scans on parent modification.

**Project inference:** use FK constraints for app-owned relations such as Unit→Media, Binding→Target/Source, Asset→Binding and Progress/History→ConsumptionTarget bridge. Do not use FK semantics to pretend external URI/provider availability is database-owned truth.

#### Transactions are useful for bounded consistency, not giant scan lifetimes

Room transaction methods execute bounded multi-statement work atomically; large/relation queries can also use transactions for a consistent result. Room serializes transactions, so a full 20k-item scan in one transaction would create contention and process-death risk.

**Project inference:** scan positives commit incrementally in short batches; negative reconciliation happens in a short guarded finalization transaction, matching Q-SCN/Q-RUN.

#### Migration history must be explicit and tested

Android guidance requires schema export for reliable migration generation/testing, supports auto migrations for simple changes and manual migrations for complex transforms, and recommends testing both individual and complete migration paths. `fallbackToDestructiveMigration()` permanently deletes database content when a path is missing.

**Project inference:** canonical V1 database does **not** use destructive fallback as normal production recovery. Schema history is version-controlled and every supported upgrade path is tested with data assertions, not just schema-open success.

### 4.22.3 Mature Project / Library Evidence

Research deliberately spans Android readers, podcast apps, book managers and media servers rather than copying one schema family.

- **Mihon** keeps persistent manga/chapter/read-state data in an explicit SQL schema and separates domain repository/interactor code from the SQL implementation. Its chapter rows demonstrate that source-native reference, canonical-ish logical record and reading state can coexist without making runtime page objects persistent truth.
- **AntennaPod** separates `:storage:database`, `:storage:preferences` and import/export concerns, and also uses service-interface/service boundaries for runtime implementations. This supports keeping relational truth, preferences and portable export responsibilities distinct.
- **calibre** uses an app/database-owned logical book ID while formats are attached representations. The same logical item can have multiple formats, reinforcing Media/Unit identity above Asset/format rows.
- **Komga** has a long SQLite migration history and explicit schema validation/migration tooling. Its operational experience shows that real media libraries accumulate many schema changes, indexes and maintenance migrations over time; migration discipline is not optional polish.
- **Jellyfin**'s recent database migration failures around uniqueness/foreign-key/data transforms are useful negative evidence: schema constraints and migration code can block startup when historical data violates new assumptions. Migrations therefore need data-shape tests and preconditions, not only generated DDL.

These projects are evidence for failure modes and separation of concerns. Their exact tables, server assumptions or reader-specific identity models are **not** copied.

### 4.22.4 Decision — One Canonical Room Database, Multiple Persistence Mechanisms

V1 starts with **one canonical Room/SQLite database** for app-owned relational truth plus operational durable records that need atomic consistency with that truth.

```text
Room / SQLite
├── canonical identity + relations
├── local source/root/asset/reconciliation state
├── progress/history/library
├── metadata mappings/provenance/overrides
└── scan operational journal needed for restart-safe finalization

DataStore (or equivalent)
└── small user/application preferences only

App-owned files
├── thumbnails / image caches
├── parser/runtime caches
├── exported backups
├── large generated artifacts
└── future managed download payloads where policy allows

External storage/providers
└── authority for whether external media/resources are actually accessible now
```

Rationale for one canonical DB in V1:

- cross-domain writes such as reconciliation + binding + Library admission need real transactions;
- backup exporter can read one relational snapshot while still filtering portable vs operational rows;
- splitting canonical state into multiple DBs would introduce distributed transaction/recovery complexity without a V1 requirement;
- operational scan rows may share the DB because correctness benefits from transaction boundaries, but their **backup classification differs** from canonical/user state.

A future rebuildable search/cache database may be split out if measured size/write patterns justify it. That is an optimization, not V1 truth ownership.

### 4.22.5 Persistence State Matrix

#### A. Portable canonical / user truth

Must survive process death, normal app upgrades and V1 logical backup/restore:

- `Media`, optional `MediaGrouping`, `MediaUnit` app IDs and canonical relations;
- portable `ConsumptionTargetRef` meaning (`MediaTarget(MediaId)` / `UnitTarget(UnitId)`);
- Library membership/suppression intent and current membership epoch timestamp;
- typed current Progress + explicit completion;
- History entries;
- Sources/configuration that are portable and non-secret;
- SourceBindings with reconstructable source-native references where portable;
- user metadata overrides, including explicit-empty state;
- metadata mappings/provenance with durable value;
- external IDs/mappings;
- root registration intent/hints that Q-BACK classifies as portable metadata, **not restored access authority**;
- selected Asset identity/evidence useful for reconciliation, provided restore reclassifies availability as unverified.

#### B. Durable device-local / operational state

May survive process death/retry on one install but is **not portable authority** and is excluded/reset during logical restore unless explicitly transformed:

- `ScanRun` / scope / coverage / finalization journal;
- run-local seen/observation rows used for guarded negative reconciliation;
- WorkManager linkage/telemetry hints;
- current access-check diagnostics;
- current locator preference/alias evidence that is device/provider specific;
- cached MediaStore generation/version state;
- stale-run/supersession bookkeeping;
- retry/backoff diagnostics.

#### C. Derived / rebuildable state

Not authoritative portable truth:

- Continue Watching / Continue Reading projections;
- Recently Added query result (derived from `libraryAddedAt` semantics);
- parent/series aggregate progress;
- effective current availability projection from root access + Asset presence + binding state;
- search FTS/index tables if introduced;
- thumbnails, page previews, artwork transform caches;
- parser-derived transient candidates that have not acquired durable reconciliation value;
- denormalized counts used purely for performance and safe to rebuild.

#### D. Runtime-only state

Never stored as canonical truth:

- Media3 `Player`, `MediaItem`, session/controller objects;
- Readium/runtime Publication/Navigator objects;
- decoded bitmaps/page render objects;
- expiring URLs/cookies/tokens/live session IDs;
- coroutine jobs/flows/locks;
- `ResolvedContent` instances;
- Android `WorkInfo` as domain state.

### 4.22.6 Provisional Relational Schema Families

Names below are **logical production table names**, not a promise of final Kotlin class spelling. Column spellings/storage codecs may change without reopening the decision as long as ownership and constraints remain equivalent.

#### Canonical identity tables

```text
media
├── media_id PK (app-owned)
├── media_kind
├── canonical structural facts
└── lifecycle bookkeeping that is truly canonical

media_grouping
├── grouping_id PK
├── media_id FK → media
├── grouping_kind
├── ordering/label facts
└── no progress/source ownership

media_unit
├── unit_id PK
├── media_id FK → media
├── grouping_id FK? → media_grouping
├── unit_kind / canonical coordinate facts
└── stable independently consumable identity
```

`MediaGrouping` stays optional. Unit identity does not depend on grouping identity.

#### Persistence-only `ConsumptionTargetRef` bridge

Progress/History/SourceBinding need one FK-able target while Q-DOM-002 explicitly allows both Media and Unit targets.

R4.14 therefore permits a **persistence-only target bridge**:

```text
consumption_target
├── target_row_id PK              // persistence surrogate only
├── target_kind                   // MEDIA | UNIT
├── media_id FK? UNIQUE → media
└── unit_id  FK? UNIQUE → media_unit
```

Invariant: exactly one canonical referent is represented per row. Production DDL/DAO layer must enforce this invariant through schema constraint where supported plus creation APIs/tests.

Critical guardrail:

```text
target_row_id
!= new canonical identity
!= navigation/public ID
!= backup logical identity
```

Domain/API/backup still use tagged `MediaId` or `UnitId`. The bridge is a relational implementation detail that gives Progress/History/Binding real referential integrity without polymorphic `ownerType + ownerId` strings leaking across the schema.

#### Source / storage / representation tables

```text
source
├── source_id PK
├── source_kind
├── non-secret configuration identity
├── enabled/config state
└── no expiring session token as truth

source_binding
├── binding_id PK
├── target_row_id FK → consumption_target
├── source_id FK → source
├── source_native_reference? / reconstructable reference
├── mapping/provenance fields
└── association lifecycle state

storage_root
├── root_id PK
├── registration/enabled state
├── access strategy kind
├── serialized current access descriptor/hint
├── last-known access observation/diagnostic
├── configuration generation
└── no URI/grant as RootId

asset
├── asset_id PK
├── binding_id FK → source_binding
├── representation kind/media facts
├── current revision token/generation
├── presence/lifecycle fields under Q-REC
└── last useful observation provenance

asset_locator
├── locator_id PK
├── asset_id FK → asset
├── root_id FK? → storage_root
├── locator kind + scoped serialized locator
├── current/stale evidence status
├── provider/root provenance
└── timestamps/generation evidence

asset_fingerprint
├── fingerprint_id PK
├── asset_id FK → asset
├── revision token/context
├── fingerprint kind/value
└── provenance/validity evidence
```

`asset_locator` exists because one representation lineage may retain a current locator plus limited stale aliases/evidence, and provider aliasing can mean locator cardinality is not exactly one. Alias retention remains a maintenance policy, not identity.

`AssetRevision` remains semantic state rather than a mandatory standalone canonical entity. V1 may keep a current revision token on `asset` plus revision-scoped fingerprints/context. If implementation later proves that revision history must be queried independently, a child revision table can be introduced by migration without changing canonical ownership.

#### Progress tables

```text
progress_state
├── target_row_id PK/FK → consumption_target
├── completion state
├── last-used binding_id? / asset_id? context
├── asset revision context?
├── updated_at / state revision metadata
└── exactly one current durable Progress per target

video_resume_anchor
├── target_row_id PK/FK → progress_state
├── position_ms
└── duration_snapshot_ms?

image_resume_anchor
├── target_row_id PK/FK → progress_state
├── page_ordinal
└── page_count_snapshot?

publication_resume_anchor
├── target_row_id PK/FK → progress_state
├── structured locator payload + payload version
└── normalized progression? as projection/fallback evidence
```

Only the anchor table compatible with the target's consumption family may exist. This keeps typed semantics explicit and avoids one generic percentage/blob being treated as universal truth. Publication locator serialization is versioned because reader/format structures evolve.

#### History and Library tables

```text
history_entry
├── history_id PK
├── target_row_id FK → consumption_target
├── meaningful_activity_started_at
├── last_meaningful_activity_at
├── ended_at?
└── optional minimal session summary fields

library_entry
├── media_id PK/FK → media
├── membership state (active/suppressed semantics)
├── library_added_at?  // current active membership epoch
└── durable user/app intent metadata
```

A history row may be opened/updated only after meaningful activity threshold is crossed; stale open rows after process death may be safely closed/repaired from durable last-activity data. Exact threshold/timeout remains product/runtime tuning, not schema identity.

#### Scan / reconciliation operational tables

```text
scan_run
├── run_id PK (app-owned domain run id)
├── trigger/provenance
├── started_at / terminal outcome
├── cancellation/supersession markers
└── no WorkRequestId as authority

scan_scope
├── scope_id PK
├── run_id FK → scan_run
├── root_id FK? → storage_root
├── declared scope/mode/filter/config generation
├── coverage/finalization state
└── authoritative-negative eligibility fields

scan_seen_asset   // operational/run-local journal, name provisional
├── run_id/scope_id FK
├── asset_id FK? / observation candidate key
└── positive observation marker/provenance
```

Run-local observation rows may be compacted after authoritative finalization/recovery windows. They are not History and are not part of user backup.

#### Metadata / mapping tables

V1 needs explainable Media-level metadata enrichment without creating a universal EAV engine for every possible node.

```text
media_external_id
├── media_id FK → media
├── namespace/provider
├── external id
└── provenance/authority

unit_external_id               // materialize only when a V1 unit mapping use case needs it
├── unit_id FK → media_unit
└── namespace/id/provenance

media_metadata_candidate
├── candidate_id PK
├── media_id FK → media
├── field_key
├── source category / source reference
├── typed/serialized value
├── observed/refreshed_at
└── provenance/authority facts

media_metadata_override
├── media_id FK → media
├── field_key
├── override mode: VALUE | EXPLICIT_EMPTY
├── typed/serialized value?
└── updated_at
```

Effective metadata is computed by deterministic policy from candidates + overrides. It is not required to persist a second duplicate “effective row” unless profiling proves materialization worthwhile; if materialized later, it remains rebuildable projection.

Grouping/Unit field-level candidate tables are **not pre-created** merely for symmetry. Add them by migration when an actual V1 metadata/mapping use case needs durable provenance at those scopes.

### 4.22.7 Referential / Delete Rules

Default relational policy is **protective**, not cascade-heavy.

- All app-owned FK child columns that participate in common joins/deletes are indexed.
- `MediaUnit → Media`, Grouping→Media and target bridge relations use FK constraints.
- Progress/History/Library never FK to Asset as their owner; Asset loss therefore cannot cascade-delete user state.
- `SourceBinding → Source/Target` and `Asset → Binding` are constrained, but normal unavailability/removal uses lifecycle state rather than physical row deletion.
- Hard deleting `StorageRoot` is not a normal “disable root” operation. Root unregister/disable preserves relevant Assets/evidence per Q-REC.
- `CASCADE` is allowed only for **true owned implementation children** whose meaning cannot exist independently, e.g. deleting an explicitly purged Progress row may delete its typed anchor child; deleting an explicitly purged scan run may delete its run-local scope/observation rows.
- Canonical Media/Unit/User state does not disappear via Asset/locator/root cascade.
- Any explicit “purge canonical item” operation is a separate domain transaction with user-facing semantics; it is not triggered by scan absence.

### 4.22.8 Transaction Boundaries

The DB exposes **semantic transactions**, not a generic transaction lambda to arbitrary higher modules.

Required bounded atomic operations include:

1. **Register/reauthorize root** — registration + descriptor/config generation update.
2. **Commit positive observation batch** — locator/evidence + Asset/Binding/canonical recognition changes that must agree.
3. **Create/reuse canonical identity during reconciliation** — Media/Unit/target bridge + binding + initial Asset + auto-admission Library intent when applicable.
4. **Guarded negative finalization** — re-check run/scope/config generation/supersession/coverage, then mark eligible Assets `MISSING` or finalize move candidates.
5. **Checkpoint Progress** — `progress_state` + exactly one typed anchor + resume-context update.
6. **Append/update meaningful History session summary** — idempotent session write.
7. **Apply metadata override/rematch result** — mapping/candidate/override changes remain internally consistent.
8. **Logical restore commit** — only after archive validation/migration/staging succeeds; exact temp-DB-vs-transaction technique remains a Q-BACK implementation choice.

Explicit non-transaction rule:

```text
full recursive scan lifecycle
!= one giant Room transaction
```

Large scans persist positives/checkpoints incrementally. Only authority-sensitive finalization is short and atomic.

### 4.22.9 Migration Strategy Baseline

R4.14 locks the following for the canonical database:

- `RoomSchemaVersion` is an implementation compatibility number; it is **not** `backupFormatVersion`.
- Export generated Room schema history and commit it to version control.
- Every released schema transition has an explicit auto/manual migration path or an explicitly documented unsupported upgrade boundary before release.
- Use auto migration only for transformations whose semantics are actually mechanical; table split/merge, identity changes, backfills, provenance introduction and user-state transforms use reviewed manual migrations.
- Run migration tests from representative old versions to current, plus an **all-migrations** path.
- Migration tests assert both resulting schema **and preserved data/invariants** (IDs, Library suppression, Progress anchor/completion, History, mappings).
- Production canonical DB does **not** call broad `fallbackToDestructiveMigration()` as a normal missing-path policy.
- Destructive recreation is acceptable only for explicitly classified rebuildable cache/index databases or test fixtures, not canonical/user truth.
- Migration code must not call provider/network/storage APIs to “repair” schema; migration is deterministic over app-owned persisted state. External reconciliation happens after successful open under normal runtime rules.
- Large/risky data migrations should define interruption/failure behavior and may use preflight backup/staging when justified; exact per-version mechanism is ADR/implementation work.

### 4.22.10 Database Corruption / Open Failure Policy

Canonical DB failure is **not** silently treated as “empty library”.

Default behavior:

```text
open/migration/integrity failure
→ fail closed for destructive writes
→ expose typed diagnostic/recovery state
→ preserve original DB bytes when possible
→ offer validated backup restore / support export / explicit rebuild paths
```

A re-scan of external media can reconstruct some Media/Asset facts but cannot reconstruct all Library suppression, Progress, History, user overrides or manual mappings. Therefore automatic delete-and-reindex is not equivalent recovery and is not the default.

Exact SQLite integrity-check cadence, salvage tooling and support UX remain implementation/maintenance decisions. They may not bypass Q-BACK or destructive-migration rules.

### 4.22.11 Backup / Restore Mapping

Logical backup serializes **domain meaning**, not table surrogates.

Portable representation rules:

- Encode target references as tagged `MediaId`/`UnitId`, not `target_row_id`.
- Reconstruct target-bridge rows during restore staging.
- Preserve app-owned canonical IDs, Library/Progress/History/mappings/overrides according to Q-BACK.
- Serialize root/locator/Asset evidence only with explicit portable-hint classification; restored rows begin unverified/unconfirmed.
- Exclude ScanRun/scope/seen journal, WorkManager linkage, FTS/cache tables and current access authority.
- Backup schema migration is independent from Room migration; exporter/importer map through a versioned logical model.

### 4.22.12 Query / Projection Policy

Persistence schema is normalized for correctness; UI does **not** assemble a dozen tables itself.

The data implementation may expose optimized read models/SQL views/queries such as:

- Library card rows.
- Continue Watching/Reading rows.
- Media detail summary.
- Unit/chapter/episode list.
- Current source/availability summary.
- Recently Added.

These are query projections, not new canonical authorities. A denormalized/materialized projection must have a clear rebuild rule and benchmark evidence before becoming durable complexity.

### 4.22.13 Rejected Alternatives

#### Alternative A — Raw Room database is the public backup format

Rejected by Q-BACK and migration independence. It couples portable recovery to table internals and device-local operational state.

#### Alternative B — Separate DB per subsystem from day one

Rejected for V1 because reconciliation/progress/library/source relations need cross-domain atomicity and no measured scaling need justifies distributed consistency complexity.

#### Alternative C — One giant `media` table with nullable fields for everything

Rejected because identity, representation, root/access, progress/history and metadata authority have different lifecycles and would reintroduce evidence-as-identity coupling.

#### Alternative D — Generic polymorphic `owner_type + owner_id` for all relations

Rejected as the default because SQLite cannot FK that pair to different parent tables. `ConsumptionTargetRef` gets a persistence-only bridge where the domain already has a real union concept; other scopes use explicit tables until a real polymorphic domain concept exists.

#### Alternative E — JSON-serialize all domain aggregates into Room blobs

Rejected because referential integrity, targeted migration, queries, reconciliation and provenance inspection would become opaque. Structured payloads remain appropriate for bounded extensible data such as a publication locator, not the whole domain.

#### Alternative F — Immediate hard-delete of missing assets/roots

Rejected by Q-REC/Q-SCN/Q-LIB. Normal scan/revoke/unregister paths update observation/lifecycle state instead.

#### Alternative G — Destructive migration fallback for canonical DB

Rejected because local files cannot reconstruct all user-owned state.

### 4.22.14 Verification Requirements

Before persistence Gate G can be considered implemented, tests must prove at least:

1. Media/Unit IDs survive normal schema migrations.
2. Persistence target bridge round-trips `MediaTarget` and `UnitTarget` without exposing a third domain ID.
3. Progress has at most one typed anchor compatible with its consumption family.
4. Asset/root deletion paths cannot cascade-delete Library/Progress/History.
5. Partial scan positives can commit without granting negative authority.
6. Guarded finalization rejects stale/superseded/incomplete scope.
7. Move/copy/revision cases preserve/recreate the correct Asset/Binding identity according to Q-REC.
8. Library suppression survives rescan and migration.
9. User metadata explicit-empty survives refresh/rematch and migration.
10. Migration tests validate data from every supported released schema lineage.
11. Missing migration path does not silently wipe canonical DB.
12. Logical backup excludes operational scan state and can rebuild persistence target bridge on restore.
13. Restored roots/assets are not marked currently accessible/present merely because rows were restored.
14. Derived Library/Continue/Recently Added queries can rebuild solely from canonical/user state.

### 4.22.15 Downstream Impact

`Q-PER-001` provides a production persistence baseline sufficient to design repositories/ports and the first local vertical slice without guessing ownership.

It does **not** lock:

- final Kotlin class/entity/DAO names;
- exact SQL column spellings;
- UUID vs another app-owned ID encoding;
- exact Room release/version;
- exact publication locator codec;
- hash algorithm/fingerprint thresholds;
- exact FTS/search implementation;
- corruption salvage UX/tooling;
- backup container/staging implementation already deferred by Q-BACK.

Changes to those details do not reopen Q-PER unless they violate ownership, transaction, migration or portability invariants.

### 4.22.16 Research References

Primary Android / Jetpack:

- Room relationships / explicit relational mapping: https://developer.android.com/training/data-storage/room/relationships
- Room foreign keys: https://developer.android.com/reference/androidx/room/ForeignKey
- Room transactions: https://developer.android.com/reference/androidx/room/Transaction
- Room migration guidance/schema export/testing: https://developer.android.com/training/data-storage/room/migrating-db-versions
- MigrationTestHelper: https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper

Mature project/library evidence:

- Mihon SQLDelight chapter schema: https://github.com/mihonapp/mihon/blob/main/data/src/main/sqldelight/tachiyomi/data/chapters.sq
- Mihon modules: https://github.com/mihonapp/mihon/blob/main/settings.gradle.kts
- AntennaPod architecture/modules: https://github.com/AntennaPod/AntennaPod/blob/develop/AGENTS.md
- calibre database API / app-owned book identity + formats: https://manual.calibre-ebook.com/db_api.html
- Komga project/database migration evidence: https://github.com/gotson/komga
- Jellyfin database migration setup: https://github.com/jellyfin/jellyfin/blob/master/src/Jellyfin.Database/readme.md
- Jellyfin migration failure examples used as negative evidence: https://github.com/jellyfin/jellyfin/issues/14423 and https://github.com/jellyfin/jellyfin/issues/17830

## 4.23 Decision Record — Q-MOD-001: Initial Module / Dependency Graph

**Status:** `PROVISIONAL`

### 4.23.1 Problem Statement

The project now needs Gradle boundaries strong enough to prevent persistence/storage/player internals leaking into features, while avoiding a module-per-class or `api/impl` pair for every screen before the codebase earns that cost.

The module graph must support:

- Android-free domain tests where practical;
- Room/SAF/Media3/Compose ownership isolation;
- future V2 online providers without replacing Player/Reader/UI contracts;
- separate scanner/reconciliation runtime from source resolution/playback hot path;
- app-level composition and navigation;
- benchmark/test infrastructure;
- future split triggers without forcing them now.

### 4.23.2 Research Findings

Official Android modularization guidance emphasizes high cohesion/low coupling, minimal public surfaces and `internal`/`implementation` visibility. It explicitly warns against both too-fine and too-coarse module granularity. It also describes dependency inversion via abstraction modules when interchangeability/test isolation justifies a separate API.

**Now in Android** demonstrates a heavily modularized Compose app with feature/core modules and repository/data boundaries, but its exact graph is a sample, not a universal target.

**AntennaPod** is particularly relevant because it has long-lived media playback, storage, import/export and service-interface/service module splits; it uses API/implementation separation where runtime substitution/lifecycle value is real rather than for every class.

**Mihon** currently has moderate coarse modules such as `:data`, `:domain`, `:source-api`, `:source-local`, presentation modules and app. This is useful evidence that a reader app can keep source contracts separate while avoiding hundreds of tiny Gradle modules.

### 4.23.3 Decision — Moderate Initial Graph

V1 starts with the following **initial** graph. This is not declared the eternal/final graph; it is the smallest reviewed graph that enforces the already-locked boundaries.

```text
:app                         Android application / composition / root navigation

:core:model                  pure Kotlin IDs + stable value/domain model primitives
:core:domain                 pure Kotlin policies, operations, repository/port contracts
:core:designsystem           Compose theme/components shared by actual features

:data                        Room database, DAOs, migrations, repository implementations
:storage:local               SAF/MediaStore/access adapters; no canonical policy ownership
:ingestion:local             scan orchestration + parser/classifier adapters using domain ports

:source:api                  stable source-resolution contracts + ResolvedContent shapes
:source:local                Local SourceBinding/Asset resolution implementation

:playback:api                playback-facing app contract
:playback:media3             Media3 session/service/player implementation

:reader:image                CBZ/image-sequence runtime + viewer implementation boundary
:reader:publication          EPUB/publication runtime adapter/reader boundary

:feature:library             Library/home/detail/search-for-local V1 UI flow as it emerges
:feature:settings            settings/root/backup entry UI as needed

:benchmark                   macrobenchmark/baseline-profile/release-like performance harness
:testing                     shared fixtures/fakes/test utilities when reuse appears
```

Notes:

- `:testing` is created only when multiple modules genuinely share fixtures/fakes; until then tests live with owners.
- `:benchmark` may be one or more Android test/baseline-profile modules depending current tooling, but the boundary is reserved from bootstrap because performance is a foundation gate.
- UI feature granularity may later split `library` into detail/search/etc. only when ownership/build/navigation complexity justifies it. Do not pre-create a module for every screen.

### 4.23.4 Dependency Direction

Provisional allowed dependency shape:

```text
:core:model
    ↑
:core:domain         :source:api        :playback:api
    ↑   ↑                ↑                  ↑
    │   ├──────┐         │                  │
    │          │         │                  │
:data      :storage:local │                  │
    │          │         │                  │
    └──── app wiring ─────┼──────────────────┘
               ↑         │
        :ingestion:local  :source:local
                          │
                     :playback:media3

:reader:image / :reader:publication
    depend on stable model/domain/source contracts,
    never on Room/SAF provider DTO internals.

:feature:*
    depend on :core:model/:core:domain/:core:designsystem
    and explicit stable API modules only when the feature actually uses them.

:app
    depends on feature modules + concrete implementation modules
    and wires them together.
```

The diagram is conceptual, not Gradle syntax. Exact dependency declarations follow these rules:

1. `:core:model` depends on no Android framework and no project implementation module.
2. `:core:domain` may depend on `:core:model`; it owns business policy/ports, not Room/ContentResolver/Media3.
3. `:data` depends on core model/domain and implements persistence ports; higher modules never import Room entities/DAOs.
4. `:storage:local` implements Android storage/access ports; it does not decide Media identity or Library membership.
5. `:ingestion:local` orchestrates discovery/classification/parser adapters through domain contracts; it does not become the database or Source resolver.
6. `:source:api` is small because V2/V3 will depend on its stability; it does not expose provider implementation DTOs.
7. `:source:local` implements source resolution and may use injected local asset-access ports; scanner logic is not pulled into playback resolution.
8. `:playback:media3` depends on playback/source contracts and owns Media3 specifics.
9. Readers consume resolved content and report domain progress events; they do not import `:data` or scanner internals.
10. Feature modules do not depend on another feature's implementation. Cross-feature navigation is assembled in `:app` using stable IDs/callback/contracts.

### 4.23.5 Why `:data` Is One Module Initially

R4.14 deliberately does **not** start with `:data:library`, `:data:progress`, `:data:scan`, `:data:metadata`, etc.

Reasons:

- one Room DB owns cross-domain transactions;
- V1 team/project scale has not yet shown independent build ownership value for each repository;
- premature split can move circular dependencies into Gradle configuration instead of solving them;
- Kotlin `internal` + package ownership + architecture tests can hide DAO/entity internals inside one module.

Split trigger later:

- data module becomes a build bottleneck;
- independent capability implementation has a stable API and separate owner/tests;
- implementation details are repeatedly leaking despite visibility rules;
- a capability gains multiple interchangeable implementations;
- cycle pressure proves current cohesion assumption false.

If split occurs, contracts stay in `:core:domain` or a justified capability API module; do not expose Room to solve the split.

### 4.23.6 Why Source / Playback Have Explicit API Modules

These two boundaries justify API modules earlier than most areas:

- V2 adds online/downloaded source implementations behind source resolution;
- V3 may eventually expose source contracts to plugin adapters;
- playback implementation may change Media3/session details without source/UI rewrite;
- fake implementations are valuable for reader/player/integration tests;
- both boundaries are consumed by multiple independent modules.

This is a real interchangeability/use-case boundary, not ceremony.

### 4.23.7 Composition / DI Strategy

Framework remains intentionally deferred, but **composition strategy is now decided**:

```text
constructor injection by default
        ↓
app-owned composition root
        ↓
concrete implementations supplied to contract consumers
```

Rules:

- no global service locator;
- no static mutable dependency registry;
- Android process-wide singletons only for objects whose lifetime truly is process-wide (e.g. canonical DB instance), not because DI makes it easy;
- service/player lifetime follows Android component ownership, not DI singleton convenience;
- test source sets can replace implementations/fakes at composition boundaries;
- choosing Hilt/Koin/manual/generated DI later must not change domain/module dependency direction.

### 4.23.8 Build Boundary Rules

- Prefer pure Kotlin/JVM modules for `core:model` and `core:domain` when no Android type is required.
- Android library modules exist only where framework/resources/lifecycle/service APIs are real dependencies.
- Prefer Gradle `implementation` dependencies; use `api` only when a type is intentionally part of the consumer-facing contract.
- Shared build convention follows the version-catalog/included-`build-logic` baseline defined by Q-BOOT-001 rather than being repeated per module.
- Public project APIs should be small; everything else `internal`/`private` by default.
- Add architecture/dependency verification before feature count makes accidental edges expensive.

### 4.23.9 Rejected Alternatives

#### Alternative A — Single `:app` monolith for V1

Rejected because Room/SAF/Media3/source boundaries are already known long-lived seams and V2 requires implementation interchange without feature rewrite.

#### Alternative B — Module per layer per feature from day one

Rejected because it multiplies Gradle/build/DI ceremony before collaboration/build-time evidence justifies it.

#### Alternative C — `api`/`impl` pair for every feature immediately

Rejected. Use it where consumers need a stable abstraction or alternate implementation. Root app navigation already prevents feature→feature implementation dependency for initial V1.

#### Alternative D — One giant `:core` module

Rejected because model/domain/design-system have different platform/dependency/lifecycle characteristics and would expose too much transitive surface.

#### Alternative E — Put scanner and source resolver in the same hot-path module by default

Rejected because ingestion is restartable bulk work while source resolution is latency-sensitive consumption preparation. Their runtime/dependency pressures differ even though both deal with local content.

### 4.23.10 Verification Requirements

1. `:core:model` and `:core:domain` compile/test without Android framework dependency unless a future explicit decision changes it.
2. Feature modules cannot import Room entities/DAOs, SAF adapter classes or Media3 implementation types.
3. Playback/reader tests can consume fake `ResolvedContent`/contracts without constructing scanner/provider/database internals.
4. App module is the only place required to know the full set of concrete top-level implementations.
5. No feature implementation imports another feature implementation.
6. Dependency graph has no cycles.
7. Architecture verification fails when forbidden implementation dependency is introduced.
8. Removing/replacing a concrete source implementation does not require changing feature/player/reader contracts.

### 4.23.11 Research References

Primary Android:

- Guide to Android app modularization: https://developer.android.com/topic/modularization
- Common modularization patterns / high cohesion / low coupling / data modules / dependency inversion: https://developer.android.com/topic/modularization/patterns
- Navigation modularization guidance (API/implementation split when navigation boundaries warrant it): https://developer.android.com/guide/navigation/navigation-3/modularize

Reference projects:

- Now in Android architecture: https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md
- Now in Android project/modules: https://github.com/android/nowinandroid
- AntennaPod modules/service-interface patterns: https://github.com/AntennaPod/AntennaPod/blob/develop/AGENTS.md
- Mihon module graph: https://github.com/mihonapp/mihon/blob/main/settings.gradle.kts

## 4.24 Decision Record — Q-API-001: Public / Internal Contracts and Boundary Ownership

**Status:** `PROVISIONAL`

### 4.24.1 Problem Statement

A correct module graph can still fail if public types leak implementation details. R4.14 therefore locks what may cross boundaries before the first vertical slice creates accidental APIs around Room entities, Android URIs, `DocumentFile`, WorkManager, Media3 or reader-engine objects.

### 4.24.2 Decision — API Surface Follows Domain Meaning

Public cross-module APIs expose:

- app-owned IDs/value objects;
- stable domain/read models;
- intent-based query/command ports;
- source-resolution contracts;
- playback/reader control contracts where multiple implementations/owners need them;
- typed failures with business/runtime meaning.

They do **not** expose:

- Room `@Entity`, DAO, `@Relation` projection or SQLite cursor;
- `WorkRequest`, `WorkInfo`, Worker classes as domain run state;
- SAF `DocumentFile`/provider cursor/document IDs as canonical types;
- MediaStore row IDs as app identity;
- Media3 `Player`/`MediaItem` above playback implementation boundary;
- Readium `Publication`/Navigator above publication runtime boundary;
- provider DTOs or expiring URL/session data as SourceBinding truth;
- mutable database transaction handles.

### 4.24.3 Repository / Port Shape

R4.14 keeps the foundation rule: **no generic repository abstraction merely for CRUD syntax**.

Ports are named by intent/capability. Representative examples (names provisional):

```text
LibraryQueries
ProgressStore
HistoryStore
CanonicalMediaStore
SourceBindingStore
StorageRootStore
ScanJournal
ReconciliationStore
MetadataStore
```

A capability may combine read/write methods where cohesion is real. Do not manufacture one interface per DAO method or one giant `AppRepository`.

Simple queries may be called directly by a state holder through a stable query/repository contract when no domain policy is added. A use case/domain operation exists when it owns policy, multi-port orchestration, transaction semantics or reusable transformation.

### 4.24.4 Semantic Transaction APIs

Higher layers never receive `RoomDatabase` or a generic `withTransaction { arbitrary DAO code }` escape hatch.

The data implementation owns transaction composition and exposes semantic operations such as:

```text
commitObservationBatch(...)
finalizeAuthoritativeScan(...)
checkpointProgress(...)
applyLibraryIntent(...)
applyMetadataOverride(...)
commitRestore(...)
```

Exact names/signatures are implementation design. The rule is that the **meaning** of an atomic operation crosses the boundary, not the persistence mechanism.

### 4.24.5 Domain / Persistence Mapping

Room entity != domain entity.

Mapping rules:

- DAOs return internal persistence rows/projections.
- Repository implementations map to domain/read models before crossing module boundary.
- Domain invariants are validated again at command boundaries where untrusted/stale persisted data could violate assumptions after migration/corruption.
- Query-specific read models are allowed; UI does not need the full canonical aggregate for every card/list.
- Persistence surrogate keys such as `target_row_id` never appear in navigation, backup or public feature APIs.

### 4.24.6 Storage Boundary API

Storage adapters emit observations/capabilities, not canonical decisions.

Conceptually:

```text
Storage observation
├── root reference
├── scoped locator descriptor
├── media/document facts
├── provider capabilities
├── loading/completeness evidence
└── typed access failure
```

Identity/Reconciliation consumes these observations through domain operations. Storage code does not decide “this is MediaId X” or “delete this LibraryEntry”.

Pure Kotlin contracts avoid Android framework types where practical. Android-specific descriptors can remain implementation-internal and be serialized/adapted into app-owned boundary values.

### 4.24.7 Source / ResolvedContent API

`source:api` preserves Q-SRC separation:

```text
canonical target
→ select SourceBinding
→ Source resolver
→ typed ResolvedContent
→ playback/reader adapter
```

Resolved runtime descriptors may include ephemeral access data but remain short-lived values; they are not persisted or passed through navigation.

Cross-module resolved families remain conceptually:

- ResolvedVideo
- ResolvedImageSequence
- ResolvedPublication

Exact Kotlin sealed/interface hierarchy is deferred to the first consumption slice, but it must not contain provider/database entity types.

### 4.24.8 Error Boundary

Implementation exceptions are translated at the owning boundary into typed failures sufficient for orchestration/UI policy.

Minimum categories remain consistent with previous foundation decisions:

- access/permission lost;
- unavailable/offline;
- not found;
- auth required where future providers apply;
- unsupported representation;
- transient runtime/network/provider failure;
- persistent data/migration/corruption failure where relevant;
- cancelled/superseded for long-running operations.

UI may render error descriptions but must not parse exception strings to infer business state.

### 4.24.9 Navigation / Feature Contracts

- Navigation arguments are stable app IDs + small scalar state only.
- Do not pass Room/domain graph objects between screens.
- Feature A does not import Feature B implementation just to navigate.
- `:app` owns root destination wiring. If navigation contracts become broadly shared and stable, a small feature/navigation API module can be introduced then; not pre-created for every screen.
- Returning results/actions between features uses explicit callbacks/result contracts or shared domain state, not implementation backreferences.

### 4.24.10 Composition Root

`:app` owns top-level wiring:

```text
Room/Data implementation
Storage adapters
Ingestion runtime
Local Source resolver
Playback Media3 implementation
Reader implementations
Feature state holders / navigation factories
WorkManager worker factories if framework needs them
```

The app module may know concrete implementation types; feature/domain API modules should not.

Android-owned long-lived components still own their runtime objects:

- `MediaSessionService` owns session/player lifetime;
- Worker owns one execution attempt, not ScanRun truth;
- ViewModel owns screen/session orchestration, not persistence.

DI tooling must respect these lifetimes rather than flattening everything into application singleton scope.

### 4.24.11 API Evolution Rules

Before V2/V3:

- internal APIs can evolve aggressively while V1 validates semantics;
- source/playback public project contracts change only through impact review once more than one implementation/consumer depends on them;
- no plugin/public ABI promise exists in V1;
- provider/plugin-facing contracts are not frozen from the first local implementation;
- when V2 proves multiple built-in implementations, stable parts can graduate toward V3 plugin API.

### 4.24.12 Rejected Alternatives

- **Expose DAOs to features** — rejected; couples UI to schema and transaction mechanics.
- **Repository returns Room entities because fields look identical** — rejected; makes schema migration a domain/API breaking change.
- **Pass `Uri`/DocumentFile everywhere** — rejected as canonical/public identity leakage; Android-specific runtime adapters may still use `Uri` internally.
- **Generic `Result<Throwable>` with UI string parsing** — rejected; failure semantics need typed categories.
- **Global event bus/service locator for cross-module actions** — rejected; hides ownership/dependencies and complicates tests.
- **Freeze provider/plugin API in V1** — rejected; contracts must be proven by built-in local + V2 implementations first.

### 4.24.13 Verification Requirements

1. Compile-time/module tests prove feature code cannot reference Room DAO/entity packages.
2. No public domain/source/playback contract exposes Android scheduler/database/provider implementation objects.
3. Replacing Room repository implementation with an in-memory fake requires no feature API change.
4. Replacing Local Source resolution with a fake/provider-style resolver requires no Player/Reader API change.
5. Typed failures reach UI without exception-string business parsing.
6. `target_row_id` and other persistence surrogates never leave `:data`.
7. Navigation state survives process recreation using IDs, not object graphs.
8. Cross-repository atomic policy is implemented behind semantic transaction APIs.

### 4.24.14 Stage G/H Closure Summary

R4.14 now provides:

```text
Stages A–F
semantic/runtime/backup foundation
        ↓
Stage G — Q-PER-001
persistence ownership + relational schema + transaction + migration baseline
        ↓
Stage H — Q-MOD-001 + Q-API-001
initial module graph + public/internal contract + composition baseline
        ↓
COMPLETED: Q-BOOT-001
exact SDK/toolchain/minSdk/version-catalog/convention-plugin bootstrap baseline
        ↓
NEXT: project skeleton + quality/security baseline
        ↓
first local vertical slice
```

**Stage G status:** `PROVISIONAL BASELINE / COMPLETE`

**Stage H status:** `PROVISIONAL BASELINE / COMPLETE`

A prior Stage A–H question is reopened only if schema/module/API/toolchain design or later implementation evidence creates a real contradiction. R4.15 has completed bootstrap design; next work implements the skeleton and quality/security gates rather than re-auditing settled semantics.

### 4.24.15 Research References

- Android modularization: https://developer.android.com/topic/modularization
- Android modularization patterns / dependency inversion / minimal public APIs: https://developer.android.com/topic/modularization/patterns
- Android navigation modularization: https://developer.android.com/guide/navigation/navigation-3/modularize
- Now in Android architecture: https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md
- AntennaPod module architecture: https://github.com/AntennaPod/AntennaPod/blob/develop/AGENTS.md
- Mihon module graph/source separation: https://github.com/mihonapp/mihon/blob/main/settings.gradle.kts


## 4.25 Decision Record — Q-BOOT-001: Project / Toolchain / SDK Bootstrap Baseline

**Status:** `PROVISIONAL`

Q-BOOT-001 closes the exact build/toolchain decision that R4.14 intentionally left open. The goal is not to chase the newest independent version of every tool; the goal is to choose a **mutually supported, reproducible compatibility intersection** that can build the initial multi-module project, compile current stable Compose, target the current Android platform, and remain easy to upgrade through measured changes.

### 4.25.1 Problem Statement

The project now has enough domain, persistence, module and API semantics to create a real Android project, but a reproducible bootstrap still needs exact answers for:

1. Gradle Wrapper, AGP, Kotlin/KGP and JDK versions.
2. Compose compiler/plugin relationship and Compose dependency alignment.
3. `compileSdk`, `targetSdk` and `minSdk`.
4. Android built-in Kotlin versus the legacy `kotlin-android` plugin.
5. version catalog, build-logic and repository ownership.
6. build types, release signing and R8 baseline.
7. package/namespace conventions and CI build authority.

A bad bootstrap can create avoidable migration work before feature code exists. In particular, independently selecting the newest AGP, newest Kotlin and newest Gradle can produce a combination that is individually stable but outside the published compatibility matrix.

### 4.25.2 Primary Compatibility Findings

**Observed fact — Kotlin:** Kotlin `2.4.20` is the current stable 2.4 line release (2026-09-07). JetBrains documents KGP `2.4.20` as fully supported with Gradle `7.6.3–9.7.0` and AGP `8.5.2–9.3.1`.

**Observed fact — AGP:** AGP `9.2.x` supports API 37 and uses Gradle `9.4.1` as its minimum/default pair. AGP `9.2.1` is the patch release that fixes the published `RecordTag` regression from 9.2.0.

**Observed fact — newer AGP trade-off:** AGP `9.4.0` is newer and requires Gradle `9.6.0`, but it is beyond the currently published KGP `2.4.20` fully-supported AGP range. AGP `9.3.1` is inside that Kotlin range, but the 9.3 line has a documented JDK-17 lint failure fixed in `9.3.2`; `9.3.2` is itself outside the published KGP 2.4.20 maximum. The bootstrap therefore does not gain enough value from the newer AGP line to justify beginning outside the clean published compatibility intersection.

**Observed fact — built-in Kotlin:** AGP 9 enables built-in Kotlin by default. Android documentation says Android modules no longer need `org.jetbrains.kotlin.android`; a higher KGP can be placed on the build classpath when the project needs a newer Kotlin than AGP's bundled dependency.

**Observed fact — Compose:** Kotlin 2.x Compose Compiler is shipped from the Kotlin repository and the Compose compiler Gradle plugin should use the same version as Kotlin. Stable Compose `1.12.x` requires `compileSdk 37` and AGP 9; the current Compose setup guidance uses BOM `2026.08.00`.

**Observed fact — AndroidX / Media3 minimum:** current AndroidX defaults new library releases to `minSdk 23`, and Media3 moved its own minimum to API 23 in 1.9.0. Current Media3 stable is `1.11.1`.

**Observed fact — platform target:** Android 17 / API 37 was released in June 2026. Google Play currently requires new standard Android apps/updates to target API 36 or higher; target 37 therefore satisfies the current Play floor while exercising current Android 17 target behavior from the start.

### 4.25.3 Project Decision — Exact Bootstrap Matrix

The initial project bootstrap uses:

| Item | Baseline |
|---|---|
| Gradle Wrapper | **9.4.1** |
| Android Gradle Plugin | **9.2.1** |
| Build JDK / Java toolchain | **JDK 17** |
| Kotlin / KGP | **2.4.20** |
| Compose Compiler Gradle plugin | **2.4.20** — same version as Kotlin |
| Compose dependency alignment | **Compose BOM 2026.08.00** |
| `compileSdk` | **37** |
| `targetSdk` | **37** |
| `minSdk` | **23** |
| KSP when first required | **2.3.12** baseline; pin in catalog and upgrade only with build/test evidence |

This matrix is **PROVISIONAL**, not eternal. Upgrade rule:

```text
change one compatibility axis intentionally
        ↓
read official compatibility/release notes
        ↓
update version catalog/wrapper together where required
        ↓
run build + lint + tests + release-like build
        ↓
only then accept the toolchain ratchet
```

Do not use dynamic `+`, `latest.release`, floating BOM aliases or IDE-only version state.

### 4.25.4 Kotlin / Compose Bootstrap Rules

Android modules:

- use AGP 9 built-in Kotlin;
- **do not apply** `org.jetbrains.kotlin.android`;
- explicitly put KGP `2.4.20` on the top-level/build-logic classpath so Android built-in Kotlin, pure JVM Kotlin modules and compiler plugins resolve a single intended Kotlin generation;
- apply `org.jetbrains.kotlin.plugin.compose` `2.4.20` only to modules that actually use Compose;
- do not use legacy `kotlinCompilerExtensionVersion` Compose setup;
- migrate annotation processing to KSP when a processor supports it; do not introduce `kapt` into a new baseline without an explicit compatibility reason.

Pure Kotlin/JVM modules such as `:core:model` / `:core:domain` may apply `org.jetbrains.kotlin.jvm` `2.4.20` and remain Android-framework-free.

Java/Kotlin compilation baseline:

- Java toolchain 17;
- Java source/target compatibility 17 where applicable;
- Kotlin JVM target aligned to 17;
- Android core-library desugaring is enabled only when a used Java API actually requires it; language level alone is not a reason to add unrelated runtime dependencies.

### 4.25.5 SDK Decision — Why API 23 / 37 / 37

#### `minSdk = 23`

Chosen because:

- it aligns with the current AndroidX default floor;
- current Media3 already requires API 23;
- it avoids carrying an API 21/22 compatibility tax that the modern AndroidX/media stack has itself dropped;
- it still covers the large majority of active Play devices according to AndroidX's published rationale.

Publication/EPUB engine choice remains deferred. If the selected engine later requires `minSdk > 23`, that is an explicit impact review; do not silently raise the app floor through a transitive dependency.

#### `compileSdk = 37`

Required by the current stable Compose 1.12 line and supported by AGP 9.2.

#### `targetSdk = 37`

The app is pre-release and its architecture already explicitly owns MediaSession/background playback, process death, large-screen adaptation and current runtime behavior. It is preferable to validate Android 17 target behavior during V1 construction rather than postpone it until release migration. If a target-37 platform behavior exposes a real architecture contradiction, reopen the affected foundation decision rather than lowering targetSdk to hide it.

### 4.25.6 Dependency / Repository Ownership

Use one root catalog:

```text
gradle/libs.versions.toml
```

It owns external library/plugin aliases and versions used by the main build. Rules:

- one canonical version entry per dependency family where practical;
- stable channel by default;
- alpha/beta/RC requires an explicit reason in the consuming slice;
- no dynamic versions;
- no duplicate hard-coded versions scattered across module build files;
- Compose libraries are aligned by BOM rather than manually pinning every Compose artifact.

Repositories are declared centrally in `settings.gradle.kts`; project modules do not add arbitrary repositories. Prefer `google()`, `mavenCentral()` and `gradlePluginPortal()` only where their artifact class requires them. Additional repositories require explicit review.

### 4.25.7 Convention Plugin / Build-Logic Baseline

Use a dedicated included build:

```text
build-logic/
```

rather than broad `allprojects {}` / `subprojects {}` mutation. Initial convention plugins should stay small and capability-oriented, for example:

```text
universalmedia.kotlin.jvm
universalmedia.android.application
universalmedia.android.library
universalmedia.android.compose
```

Do not create one convention plugin per product feature. A convention plugin configures build capability/common defaults; feature/module dependency intent remains visible in that module's own build file.

The included `build-logic` build may compile against the required AGP/KGP APIs, but it must use public Gradle/AGP APIs and must not depend on internal implementation packages.

### 4.25.8 Gradle Wrapper / Supply-Chain Rules

The repository commits:

- `gradlew` / `gradlew.bat`;
- wrapper JAR;
- `gradle-wrapper.properties` pinned to Gradle `9.4.1` `-bin` distribution;
- `distributionSha256Sum` for the selected Gradle distribution.

Wrapper JAR/distribution integrity is verified during bootstrap/CI setup. CI invokes the wrapper, never a machine-global `gradle` executable.

Dependency verification/lock metadata may be added by the quality/security bootstrap gate; Q-BOOT only requires the wrapper and version inputs themselves to be deterministic.

### 4.25.9 Package / Namespace Convention

Bootstrap uses the provisional technical root:

```text
app.universalmedia
```

Rules:

- `:app` applicationId baseline: `app.universalmedia`;
- debug applicationId suffix: `.debug`;
- Android library namespace follows module ownership, e.g. `app.universalmedia.storage.local`, `app.universalmedia.feature.library`;
- pure Kotlin packages follow the same logical root where useful but package layout must not be forced to mirror Gradle modules 1:1.

`applicationId` becomes effectively permanent once an externally distributed product identity depends on it. If branding/domain ownership requires another final ID, change it **before the first public/persistent distribution channel** and perform an explicit impact review. Do not silently rename after backups, deep links, provider authorities or published installs depend on it.

### 4.25.10 Build Types / Signing / R8

Baseline build types:

- `debug` — debuggable, debug signing, no minification; development only.
- `release` — non-debuggable, R8 code shrinking/optimization and resource shrinking enabled from the beginning; no release key committed to source control.
- `benchmark` — added when the benchmark module is wired; release-like/non-debuggable and never treated as a distributable signing identity.

Signing rules:

- debug keystore is only for local/debug/benchmark mechanics;
- release signing material comes from developer/CI secret configuration and is never committed;
- unsigned release assembly may be used for CI compile/shrinker validation when distribution signing is unavailable;
- distribution tasks must fail clearly if required release signing material is absent.

Do not postpone R8 until the end of V1; release-like builds must exist early enough to expose reflection/serialization/keep-rule problems.

### 4.25.11 CI Build Authority

IDE sync is useful but is not build authority. The wrapper/CLI build is canonical.

Bootstrap CI must at minimum prove on a clean checkout:

```text
./gradlew --no-daemon --stacktrace test lint
./gradlew --no-daemon --stacktrace :app:assembleDebug :app:assembleRelease
```

As Android/instrumentation modules become real, add a separate device/emulator lane rather than turning every fast PR gate into a device gate.

CI and local developer builds must use the same Gradle wrapper and JDK-17 toolchain requirement. Android Studio may use its bundled JBR for the IDE, but Gradle's selected JVM/toolchain must remain reproducible and compatible with the pinned baseline.

### 4.25.12 Bootstrap Dependency Candidates — Evidence, Not Global Lock-In

Current stable versions relevant to the first slices include:

- Room `2.8.5`;
- Media3 `1.11.1`;
- WorkManager `2.11.2`;
- Activity `1.13.0`;
- Lifecycle `2.11.0`;
- Benchmark `1.5.0`.

These are **not** all forced into the skeleton. Add only libraries required by an implemented slice, pin them in the version catalog, and run the same compatibility/test gate. The bootstrap must remain minimal rather than preloading the entire roadmap dependency graph.

### 4.25.13 Rejected Alternatives

- **AGP 9.4.0 simply because it is newest** — rejected for bootstrap because it is currently outside KGP 2.4.20's published fully-supported AGP range.
- **AGP 9.3.1 + JDK 17** — rejected for this baseline because the 9.3 line has a published JDK-17 lint failure fixed in 9.3.2, while 9.3.2 currently exceeds the published KGP 2.4.20 maximum.
- **AGP 8.x** — rejected; stable Compose 1.12 requires AGP 9/compileSdk 37 and the project would immediately need another major build migration.
- **`minSdk 21` for theoretical reach** — rejected because current AndroidX/Media3 have moved the practical modern floor to API 23.
- **`targetSdk 36` while compiling 37 only to reduce behavior work** — rejected for a pre-release app; hiding current target behavior now merely defers integration risk.
- **Apply `kotlin-android` everywhere** — rejected because AGP 9 built-in Kotlin makes that legacy path unnecessary/incompatible with the new default DSL.
- **Use `buildSrc` plus root `subprojects {}` configuration for convenience** — rejected as default for this multi-module baseline; included `build-logic` + explicit convention plugins keeps ownership clearer and scales better.
- **Pin every future V1 dependency now** — rejected; it creates stale dependency decisions before the consuming slice exists.

### 4.25.14 Verification Requirements

Q-BOOT-001 is considered implemented only when the project skeleton proves:

1. Gradle wrapper `9.4.1` runs under JDK 17 from CLI on the supported developer/CI platforms.
2. AGP `9.2.1` Android modules and KGP `2.4.20` JVM modules coexist without applying `kotlin-android` to Android modules.
3. A Compose module compiles with Compose Compiler plugin `2.4.20`, BOM `2026.08.00`, `compileSdk 37`, target 37 and min 23.
4. Debug and release-like variants assemble from a clean checkout.
5. `lint` and JVM tests run from the wrapper.
6. No module adds an undeclared repository or floating dependency version.
7. Convention plugins use public APIs and do not introduce project-to-project cycles.
8. Release signing secrets are absent from source control.
9. A future API-23 instrumentation smoke lane and API-37 lane are reserved in the test strategy; actual device wiring is a quality-gate implementation task.
10. Upgrading one pinned toolchain component requires an explicit compatibility/test diff rather than an IDE prompt being accepted blindly.

### 4.25.15 Deferred / Follow-Up Decisions

Q-BOOT-001 intentionally does **not** choose:

- a DI framework — initial bootstrap uses constructor/manual composition from Q-MOD/Q-API; add a framework only when wiring evidence justifies it;
- EPUB engine;
- image loader/cache;
- archive implementation;
- metadata provider;
- final benchmark numeric budgets;
- final public brand/package if product branding changes before distribution;
- every first-slice AndroidX/library version beyond the bootstrap/toolchain set.

No remaining toolchain ambiguity blocks creating the project skeleton.

### 4.25.16 Closure / Next Work

```text
Stages A–F — semantic/runtime/backup baseline
        ↓
Stage G — persistence baseline
        ↓
Stage H — module/API baseline
        ↓
Q-BOOT-001 — exact build/SDK/toolchain baseline
        ↓
HISTORICAL NEXT — completed structurally in R4.16
project skeleton + quality/security bootstrap gates
        ↓
CURRENT NEXT
executable bootstrap verification on JDK 17 + SDK 37
        ↓
first local vertical slice
```

`Q-COMP-001` is **not opened as a blocker**: manual constructor injection/app composition root is sufficient for the initial skeleton and first vertical slice. A DI-framework decision is reopened only if measured wiring/test-replacement complexity creates real value for it.

**Q-BOOT-001 status:** `PROVISIONAL / COMPLETE FOR PROJECT SKELETON`

### 4.25.17 Research References

Primary Android / Jetpack / Gradle / Kotlin references:

- AGP 9.2 release notes / API 37 / Gradle 9.4.1 / JDK 17: https://developer.android.com/build/releases/agp-9-2-0-release-notes
- AGP 9.4 release notes / current newer line: https://developer.android.com/build/releases/agp-9-4-0-release-notes
- AGP built-in Kotlin + higher KGP classpath override: https://developer.android.com/build/releases/agp-9-0-0-release-notes
- Built-in Kotlin migration guidance: https://developer.android.com/build/migrate-to-built-in-kotlin
- Kotlin 2.4.20 release + KGP/Gradle/AGP compatibility: https://kotlinlang.org/docs/releases.html and https://kotlinlang.org/docs/gradle-configure-project.html
- Compose compiler migration / same-version Kotlin plugin rule: https://kotlinlang.org/docs/compose-compiler-migration-guide.html
- Compose compiler/dependency setup + BOM + compileSdk requirement: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Compose stable releases: https://developer.android.com/jetpack/androidx/releases/compose
- AndroidX minSdk baseline: https://developer.android.com/jetpack/androidx/versions
- Media3 release notes / minSdk 23: https://developer.android.com/jetpack/androidx/releases/media3
- Android 17 SDK / target 37: https://developer.android.com/about/versions/17/setup-sdk
- Android 17 release timeline: https://developer.android.com/blog/topics/android-17
- Google Play target API requirement: https://developer.android.com/google/play/requirements/target-sdk
- JDK/toolchain guidance: https://developer.android.com/build/jdks
- Gradle Wrapper and checksum verification: https://docs.gradle.org/current/userguide/gradle_wrapper.html
- Gradle Version Catalogs: https://docs.gradle.org/current/userguide/version_catalogs.html
- Gradle Convention Plugins / included build pattern: https://docs.gradle.org/current/userguide/implementing_gradle_plugins_convention.html
- KSP latest release / KSP2 baseline: https://github.com/google/ksp/releases and https://kotlinlang.org/docs/ksp-quickstart.html

Current stable dependency evidence used only for bootstrap planning:

- Room: https://developer.android.com/jetpack/androidx/releases/room
- WorkManager: https://developer.android.com/jetpack/androidx/releases/work
- Activity: https://developer.android.com/jetpack/androidx/releases/activity
- Lifecycle: https://developer.android.com/jetpack/androidx/releases/lifecycle
- Benchmark/AndroidX versions: https://developer.android.com/jetpack/androidx/versions


## 4.26 Bootstrap Implementation Evidence Record — Phase-0 Skeleton

**Status:** `STRUCTURALLY VERIFIED / EXECUTABLE BUILD EVIDENCE PENDING`

R4.16 records implementation evidence for the downstream work opened by `Q-BOOT-001`. This record does **not** create a new architecture semantic decision and does not upgrade provisional Stages A–I to LOCKED. It proves only what the generated skeleton and static checks actually demonstrate.

### 4.26.1 Generated Bootstrap Surface

The Phase-0 artifact contains:

```text
:app
:core:model
:core:domain
:core:designsystem
:data
:storage:local
:ingestion:local
:source:api
:source:local
:playback:api
:playback:media3
:reader:image
:reader:publication
:feature:library
:feature:settings
:benchmark
```

with:

- root version catalog and central repository ownership;
- included `build-logic` convention plugins for JVM, Android application/library and Compose capability;
- AGP 9 built-in Kotlin for Android modules;
- minimal Compose launch surface only, with no scanner/database/provider/player/reader feature breadth;
- JVM smoke harnesses and an Android Compose launch smoke test;
- release-like non-debuggable/minified benchmark target plus separate `com.android.test` Macrobenchmark module;
- `verifyArchitecture`, `verifySecurityBaseline`, `verifyFast` and `verifyRelease` task wiring;
- Bash + PowerShell developer scripts;
- CI workflow with JDK 17, SDK 37, fast/release gates and API-23/API-37 instrumentation lanes;
- URI/file threat model, exported-component policy and logging/secrets policy.

### 4.26.2 Self-Review Corrections

Two correctness defects were found and fixed before handoff:

1. **Built-in Kotlin configuration:** Android convention plugins originally configured `KotlinCompilationTask` directly. Under AGP 9 built-in Kotlin, the supported migration path is `kotlin { compilerOptions { ... } }`; Android `jvmTarget` remains aligned through Java `compileOptions.targetCompatibility = 17`. The convention plugins now follow that model.
2. **Architecture verifier false-PASS risk:** the initial regex for `project(":...")` dependencies was double-escaped inside a raw Kotlin string. It could therefore observe an empty dependency set and pass forbidden edges silently. The regex now correctly matches declared project dependencies, and an independent static scan reproduced the expected graph with no forbidden edges.

The security verifier was also hardened to scan **all main manifests**, not only `:app`, while allowing exactly one `android:exported="true"` component: launcher `MainActivity`.

### 4.26.3 Evidence Collected in the Generation Environment

Fresh checks completed successfully:

- security baseline Bash verifier;
- architecture dependency allow-list scan;
- XML parsing for manifests/resources/lint config;
- TOML parse for version catalog;
- GitHub Actions YAML parse;
- Bash syntax validation;
- placeholder scan (`TODO` / `TBD` / `FIXME`) outside documentation;
- dynamic/floating dependency scan;
- forbidden `org.jetbrains.kotlin.android` / `kotlin-android` scan outside docs;
- forbidden broad-storage/`INTERNET` manifest permission scan;
- basic hard-coded secret-assignment scan.

Gradle 9.4.1 distribution and wrapper SHA-256 pins were cross-checked against the published Gradle checksum reference. The repository intentionally does not fabricate a `gradle-wrapper.jar`; checksum-verifying bootstrap scripts materialize the official wrapper on a networked JDK-17 machine.

### 4.26.4 Evidence Not Yet Proven

The generation environment has Java 21 only, no JDK 17, no Android SDK, no system Gradle and cannot fetch/materialize the official wrapper binary. Therefore the following remain **PENDING**, not PASS:

1. `./gradlew help` / Gradle sync on JDK 17.
2. `./gradlew verifyFast --no-daemon`.
3. JVM JUnit execution.
4. Android Lint execution.
5. `:app:assembleDebug` and `:app:assembleRelease`.
6. `:benchmark:assembleBenchmark`.
7. API-23/API-37 `connectedDebugAndroidTest`.
8. GitHub Actions clean-checkout execution.
9. Macrobenchmark device execution and any numeric performance evidence.

No later feature implementation may cite these pending items as green until fresh output exists.

### 4.26.5 Next Execution Gate

On a JDK-17 + Android-SDK-37 environment:

```text
materialize + verify official Gradle 9.4.1 wrapper
        ↓
./gradlew help --no-daemon
        ↓
./gradlew verifyFast --no-daemon
        ↓
./gradlew :app:connectedDebugAndroidTest --no-daemon
        ↓
./gradlew verifyRelease --no-daemon
        ↓
CI clean-checkout evidence
        ↓
only then begin first local vertical slice
```

If any executable evidence contradicts Q-BOOT/Q-MOD/Q-API assumptions, reopen the affected question before expanding feature breadth.

---

# 5. Three-Version Product Roadmap

Roadmap có đúng ba version sản phẩm lớn:

```text
V1 — Local-first Core
        ↓
V2 — Online & Unified Sources
        ↓
V3 — Platform & Ecosystem
```

Mỗi version phải **chứng minh một lớp kiến trúc** trước khi lớp tiếp theo được mở.

Pre-development setup ở phần 6 là Phase 0 engineering, không phải product version thứ tư.

---

## 5.1 V1 — Local-first Core

### Goal

Xây dựng một ứng dụng media local hoàn chỉnh có thể hoạt động offline và chứng minh core architecture.

V1 phải chứng minh:

- Canonical Media hoạt động.
- Stable internal identity hoạt động.
- Media Unit hoạt động.
- Local Source abstraction hoạt động.
- Ingestion/reconciliation đủ tin cậy.
- Player/Reader độc lập source implementation.
- Progress persist/restore chính xác.
- Local media trở thành library thật sự.
- Metadata enrich library mà không trở thành canonical identity.
- Process restart không làm mất durable user state.

### Functional Scope

#### Core

- Canonical Media.
- Media Unit.
- Identity Core.
- Source abstraction.
- Progress.
- Persistence.
- Library model.

#### Local Media

- Android Storage Access Framework where applicable.
- Folder/document-tree registration.
- Persisted access grants where supported.
- Local scanner/ingestion.
- File/document type detection.
- Grouping.
- Add/delete/rename/move reconciliation where observable.

#### Video

- MKV.
- MP4.
- WebM.
- Basic Media3-based playback boundary.
- Resume playback.
- Basic track selection.

#### Manga / Comic

- CBZ.
- ZIP image archive.
- Image folder.
- JPEG / PNG / WebP.
- Reader.
- Resume position.

#### Novel / Publication

- EPUB 2.
- EPUB 3.
- Publication reader.
- Locator-based resume.

#### Library

- Video/Anime library representation.
- Manga/Comic library representation.
- Novel library representation.
- Continue Watching.
- Continue Reading.
- Recently Added.
- Basic History.

#### Metadata

Metadata trong V1 là **optional enrichment**, không phải dependency để local core hoạt động.

- Metadata search khi provider/network available.
- Media details.
- Local media matching.
- Manual matching.
- Rematching.
- Metadata override.
- Graceful offline/provider-failure behavior.

#### User Data

- Basic settings.
- Local backup of app-owned state/configuration.
- Local restore of app-owned state/configuration.

V1 backup không mặc định sao chép toàn bộ user media binaries và không thể giả định Android storage grants được restore; storage roots có thể cần user re-authorization.

### Explicitly Excluded from V1

- Online streaming providers.
- Online manga chapters.
- Online novel providers.
- Dynamic plugins.
- Tracking services.
- Cloud sync.
- Multi-device sync.
- Online downloads.
- Recommendation engine.
- Advanced discovery.
- Social features.
- Advanced player features.
- Advanced reader features.

### V1 Critical Success Flow

```text
Grant Folder / Document Tree
        ↓
Discover Assets
        ↓
Classify / Parse / Group
        ↓
Identify / Reconcile
        ↓
Create or Reuse Canonical Media + optional Unit(s) when subdivision semantics exist
        ↓
Persist Internal State
        ↓
Optional Metadata Match
        ↓
Library
        ↓
Play / Read
        ↓
Persist Progress
        ↓
App Process Ends / Device Restarts
        ↓
Open App
        ↓
Resume Correctly
```

### Gate V1 → V2

Chỉ chuyển sang V2 khi:

- Local library ổn định trên representative datasets.
- Re-scan không tạo duplicate bất thường.
- Rename/move/delete reconciliation có semantics rõ.
- Resume hoạt động chính xác cho video/manga/publication.
- Player/Reader độc lập source implementation.
- Canonical Media/Unit không cần redesign lớn cho use case V1.
- Metadata không thay canonical identity.
- Durable state sống qua process death/restart.
- Storage permission loss/revocation có failure behavior rõ.
- Performance gates cho critical V1 journeys không có regression nghiêm trọng.
- Backup/restore không phá identity/mapping/progress.

Nếu gate này chưa đạt thì không mở online architecture.

---

## 5.2 V2 — Online & Unified Sources

### Goal

Biến local-first media app thành universal media client bằng cách **mở rộng core V1**, không dựng một online subsystem song song.

Online content phải đi qua cùng:

- Canonical Media.
- Canonical Media Unit.
- Progress.
- Library.
- Consumption boundaries.

### Functional Scope

#### Content Provider API

- Stable built-in content-provider contract.
- Built-in video provider.
- Built-in manga provider.
- Optional publication provider.

#### Provider Data

- Provider media results.
- Provider units.
- Streams.
- Pages.
- Publication resources.

Provider models ở adapter boundary, không trở thành canonical models.

#### Resolver

- Metadata → Provider candidate matching.
- Candidate list.
- Confidence/evidence where useful.
- User confirmation.
- Mapping persistence.
- Rematch.

#### Unified Sources

Một Canonical Consumption Target có thể có:

```text
Local
Downloaded
Provider A
Provider B
```

#### Source Selection

- Manual source selection.
- Prefer Local.
- Prefer Downloaded.
- Preferred Provider.
- Ask Every Time.
- Automatic source selection after deterministic policies are defined.

#### Online Playback / Reading

- Online video playback.
- Online manga reading.
- Online publication reading nếu provider supports it.

#### Unified Search

Search có thể aggregate các result types có provenance rõ từ:

- Local library.
- Metadata provider.
- Content provider.

Không silently merge ambiguous results.

#### Download

- Download queue.
- Pause / Resume.
- Offline playback.
- Offline reading.
- Storage management.
- Downloaded asset registered as a source availability.

#### Unified Progress

Switching source không tạo progress namespace mới nếu các source mapping tới cùng canonical consumption target.

### V2 Critical Success Flow

```text
Canonical Consumption Target
        ↓
Resolver / Mapping
        ↓
Available Sources
        ↓
Source Policy / User Selection
        ↓
Resolved Asset
        ↓
Player / Reader
        ↓
Unified Progress
```

### Gate V2 → V3

Chỉ chuyển sang V3 khi:

- Provider API đã ổn định qua nhiều built-in implementations/use cases.
- Resolver hoạt động và có manual correction path.
- Mapping persistence/rematch hoạt động.
- Local và online thực sự dùng cùng canonical model.
- Source switching hoạt động.
- Unified Progress hoạt động.
- Download được coi như source bình thường.
- Provider implementation có thể đổi mà UI/Player/Reader không cần biết chi tiết.
- Provider failures/network failures không làm corrupt canonical/local state.
- Security boundaries cho provider/network access đã rõ.

Nếu provider contracts còn thay đổi mạnh thì chưa xây dynamic plugin ABI/API.

---

## 5.3 V3 — Platform & Ecosystem

### Goal

Biến provider architecture đã được kiểm chứng thành platform mở rộng được mà vẫn bảo vệ core identity, security và app stability.

### Functional Scope

#### Plugin Platform

- Plugin manifest.
- Plugin API version.
- Compatibility system.
- Capability system.
- Allowed hosts.
- Plugin permissions.
- Plugin manager.
- Install/update/remove lifecycle.
- Failure isolation strategy.

#### Plugin Types

- Metadata Plugin.
- Content Plugin.
- Tracking Plugin.

Future candidates:

- Subtitle Plugin.
- Recommendation Plugin.
- Sync Plugin.

#### Tracking

- Authentication.
- Import library.
- Progress sync.
- Status sync.
- Score sync.
- Mapping.
- Conflict handling.

#### Discovery

- Trending.
- Popular.
- Seasonal.
- Upcoming.
- Top Rated.
- Filters.
- Provider discovery capabilities.

#### Sync

- Multi-device sync.
- Cloud sync.
- Self-hosted sync.
- Peer sync.
- Conflict/reconciliation policy.

#### Advanced Video

- Picture in Picture.
- Casting.
- Intro / Outro skip.
- Advanced subtitle styling.
- External subtitles.
- Decoder selection.

#### Advanced Manga

- Dual page.
- Spread detection.
- Webtoon crop.
- Image filters.
- Panel navigation.

#### Advanced Novel

- Highlight.
- Bookmark.
- Notes.
- Dictionary.
- Translation.
- Text-to-speech.
- Reading statistics.

#### Advanced Library

- Custom lists.
- Tags.
- Smart collections.
- Statistics.
- Ratings.
- Favorites.
- Rewatch / reread count.

### V3 Guardrails

- Plugin code/data không được bypass canonical model.
- Plugin permissions phải explicit và least-privilege.
- Network host access phải declarative/controlled.
- Plugin failure không được làm corrupt app database.
- Compatibility/version negotiation phải explicit.
- Tracking/sync conflicts không được silently overwrite local user state without policy.
---

# 6. Pre-Development Setup — Required Before Feature Coding

Phần này là **Phase 0 engineering setup**. Nó phải hoàn thành đủ trước khi implementation V1 mở rộng.

Không cần hoàn thành mọi class hay schema, nhưng ownership, boundaries và project rules phải đủ rõ để tránh rewrite kiến trúc ngay trong những sprint đầu tiên.

## 6.1 Setup Gate A — Product & Domain Decisions

Gate A được điều khiển bởi **V1 Decision Queue** ở phần 4. Không dùng một checklist mơ hồ kiểu “TBD”; mỗi ambiguity phải có question ID, status và closure evidence.

Must-resolve before production persistence/API design:

- `Q-DOM-001..003` — canonical media/grouping/unit shape.
- `Q-SRC-001` — Source/Binding/Asset semantics.
- `Q-STO-001` — StorageRoot/access boundary.
- `Q-ID-001`, `Q-REC-001`, `Q-SCN-001` — identity evidence + reconciliation + scan completeness.
- `Q-PROG-001`, `Q-PROG-002`, `Q-HIST-001`, `Q-LIB-001` — typed progress, resume portability and user-state semantics.
- `Q-META-001` — metadata precedence/provenance/user override (now PROVISIONAL).

`Q-RUN-001` and `Q-BACK-001` are now PROVISIONAL; Stage F is complete and corresponding runtime/backup technical design may proceed under their invariants.

Không tạo Room production schema chính thức hoặc public source/consumption API khi question trực tiếp block nó vẫn `OPEN`.

## 6.2 Setup Gate B — Android Runtime Model

Phải xác định **ai sở hữu state/work trong Android runtime**.

### Process reality

Android process lifetime không do app trực tiếp kiểm soát. Durable user/app state không được phụ thuộc vào việc process còn sống.

### Ownership model

```text
Application process
├── Activity / Navigation host
│    └── Compose UI
│         └── screen state holder / ViewModel
├── Playback Service
│    └── MediaSession + Player ownership
├── WorkManager
│    └── reliable persistent jobs
└── Repositories / DB / storage adapters
```

### State lifetime classes

#### Ephemeral rendering state

Examples:

- Animation state.
- Temporary UI expansion.
- Current gesture state.

Owner:

- Compose/state holder close to UI.

#### Restorable lightweight UI/navigation state

Examples:

- selected tab.
- query text worth restoring.
- navigation argument.

Owner/tools:

- `rememberSaveable`, SavedStateHandle or navigation state as appropriate.

#### Screen/session state

Examples:

- loaded media detail state.
- transient filter state.
- screen orchestration state.

Owner:

- ViewModel/state holder.

Must be reconstructible after process death from durable state + arguments when required.

#### Durable app/user state

Examples:

- library membership.
- canonical identity.
- mappings.
- progress.
- history.
- registered roots.
- user settings.

Owner:

- persistence/storage layer.

`ViewModel != persistence`.

#### Long-running runtime state

Examples:

- active playback.
- persistent scan/download job.

Owner:

- Android component appropriate to the requirement, not arbitrary screen scope.

## 6.3 Setup Gate C — Async & Background Execution Model

`Q-RUN-001` is now **PROVISIONAL** and defines the runtime ownership rule. Android scheduler state is orchestration state, not domain truth.

### In-process bounded work

Use coroutine/structured concurrency when work is bounded and losing the current execution with its owning scope/process is acceptable because it can be reconstructed or retriggered safely.

Examples:

- DB query.
- small parsing/metadata extraction.
- screen request.
- image decode coordination.
- small targeted observation that does not require persistent continuation and still obeys `ScanRun` semantics if it publishes durable scan evidence.

Rules:

- do not use `ViewModel` scope as durability;
- no performance-critical I/O/CPU work on the main thread;
- cancellation must propagate through suspend points and platform cancellation primitives where available.

### Reliable persistent / deferrable work

Use WorkManager when the **request to perform work must outlive a screen/process and may be delayed/retried by Android**.

V1 default candidates:

- root/full library scan and reconciliation trigger;
- scheduled rescan/maintenance;
- persistent metadata refresh batches when introduced;
- backup/export orchestration under the completed `Q-BACK-001` semantics.

Critical rules from Q-RUN-001:

- `WorkRequestId`, unique work name, `runAttemptCount` and Worker instance are **not** `ScanRunId` or domain authority;
- WorkManager input/progress carries small orchestration references/telemetry, not the authoritative scan dataset;
- persistent correctness lives in app-owned DB/state and restartable operations;
- Worker retry/recreation may create a fresh `ScanRun` attempt rather than reviving old negative authority;
- ordinary full/root scans should be serialized/coalesced per overlapping authority scope so two runs cannot race to publish negative evidence.

### User-visible continuous work

Purpose-built continuous runtimes remain separate:

- playback → Media3 `MediaSessionService`/session ownership;
- future download transfer → purpose-built download runtime chosen for that slice;
- long user-visible import/export/transfer may need a purpose-built foreground/JobScheduler path depending current Android rules.

A foreground service is **not** the default generic scanner. Android 15 imposes time limits on some foreground-service types, and Android 16 applies runtime quota behavior to background jobs including WorkManager. Large scans therefore must be designed to stop/restart safely rather than depending on one immortal process.

### Cancellation / retry / idempotency

Every persistent operation must define:

- cooperative cancellation points;
- durable cancellation/supersession semantics where required;
- transient vs terminal failure classification;
- retry/backoff policy for retryable failures only;
- idempotent/restart-safe durable writes;
- batch/transaction boundaries;
- process-death recovery;
- stale-run recovery and authority checks before finalization.

`onStopped()`/service callbacks are cleanup hints, **not correctness hooks**: abrupt process termination may provide no callback. Recovery must be derivable from durable state.

For scans, positive observations may commit incrementally, but negative reconciliation is allowed only in a short guarded finalization step that re-checks current scope/config generation, cancellation/supersession and Q-SCN coverage authority.

## 6.4 Setup Gate D — Storage Model

V1 local media phụ thuộc mạnh vào Android storage semantics.

Phải design trước:

- Storage Access Framework usage.
- MediaStore vs SAF responsibility where relevant.
- `ACTION_OPEN_DOCUMENT_TREE` flow where appropriate.
- Persistable URI permission handling.
- Permission loss/revocation behavior.
- Removable storage behavior.
- Provider/document capability differences.
- Asset unavailable state.
- Rename/move detection limitations.
- Large tree scan strategy.
- Whether any broad storage permission is truly necessary; do not assume it by default.

Rules:

- Raw filesystem path không phải canonical identity.
- URI/location không phải canonical Media ID.
- Persisted access grant không được giả định tồn tại mãi.
- Backup/restore không được giả định có thể khôi phục Android URI permission; user có thể phải re-authorize storage roots.
- UI chỉ expose operation mà underlying document/provider supports.

Conceptual separation:

```text
Canonical identity
      ≠
Asset location
      ≠
Android access permission
```

## 6.5 Setup Gate E — Playback Architecture

V1 phải chọn Media3-based playback boundary trước khi UI player phát triển sâu.

Target ownership for background-capable playback:

```text
Compose Player UI
       ↓
MediaController
       ↓
MediaSession
       ↓
Player
       ↓
MediaSessionService
```

Rules:

- Player instance không mặc định thuộc Screen/ViewModel nếu background playback là product direction.
- UI không resolve provider/local source trực tiếp.
- Playback engine nhận resolved playable input.
- Playback progress persistence tách khỏi provider identity.
- Audio focus/session/notification responsibilities thuộc playback boundary phù hợp.

## 6.6 Setup Gate F — Reader Runtime Boundaries

Manga và Publication Reader phải có ownership tương tự về mặt nguyên tắc:

```text
UI controls
    ↓
Reader state holder
    ↓
Reader/Publication engine
    ↓
Resolved content
```

Không để Reader tự trở thành source resolver hoặc canonical progress repository.

Phải định nghĩa sớm:

- page/image cache ownership.
- decode cancellation.
- memory pressure behavior.
- prefetch window.
- locator/page progress persistence frequency.
- recovery after asset disappears.

## 6.7 Setup Gate G — Persistence Strategy

**R4.14 status: PROVISIONAL BASELINE / DESIGN COMPLETE.**

`Q-PER-001` in section 4.22 is the canonical downstream persistence design. It locks the ownership/state matrix, one-canonical-Room-DB baseline, persistence-only `ConsumptionTargetRef` bridge, provisional relational table families, bounded semantic transactions, protective FK/delete rules, migration testing/non-destructive policy, corruption behavior and logical-backup mapping.

Implementation rules:

- Room/SQLite owns app relational truth; DataStore owns small preferences; files own large cache/artifact/export payloads.
- Domain models and Room entities are separate; DAOs/entities remain internal to `:data`.
- Scan positives may persist incrementally; negative reconciliation only occurs in guarded finalization transactions.
- Canonical DB has exported schema history and tested migration paths; broad destructive fallback is prohibited for canonical/user truth.
- Runtime/scheduler/cache state can coexist in the DB only with explicit non-portable/rebuildable classification.
- Exact entity/DAO names, column spellings, codec choices and index tuning are implementation details unless they change semantic ownership.

## 6.8 Setup Gate H — Architecture & Module Boundaries

**R4.14 status: PROVISIONAL BASELINE / DESIGN COMPLETE.**

`Q-MOD-001` and `Q-API-001` in sections 4.23–4.24 define the initial graph and API rules. Baseline modules are:

```text
:app
:core:model
:core:domain
:core:designsystem
:data
:storage:local
:ingestion:local
:source:api
:source:local
:playback:api
:playback:media3
:reader:image
:reader:publication
:feature:library
:feature:settings
:benchmark
:testing              // only when shared test value exists
```

Module/API rules:

- Pure Kotlin where framework dependencies are unnecessary.
- `:app` is the composition/root-navigation owner and may know concrete implementations.
- Constructor injection is the default strategy; the DI framework itself remains deferred to bootstrap.
- Features depend on stable model/domain/API contracts, not Room/SAF/Media3 implementation types.
- No feature implementation depends on another feature implementation.
- Source/playback get explicit API modules because future implementation interchange is already a roadmap requirement.
- `:data` remains one module initially because one canonical DB owns cross-domain transactions; split only on measured build/ownership/coupling pressure.
- Prefer `implementation`; expose as little as possible; add architecture/dependency verification before graph growth.

The graph is **initial/provisional**, not a claim that the final V3 module graph is frozen.

## 6.9 Setup Gate I — UI Architecture

Jetpack Compose là UI toolkit direction.

UI architecture phải theo unidirectional data flow ở mức phù hợp:

```text
State
 ↓
UI
 ↓ user event
State holder / action
 ↓
Domain/Data
 ↓
new State
```

Rules:

- UI renders state.
- UI emits events/actions.
- Composable không truy cập DB/network/storage/provider trực tiếp.
- Screen state holder không giữ Android resources lâu hơn lifecycle cần thiết.
- Navigation arguments chứa identity nhỏ/stable, không truyền graph object lớn giữa screens.
- Design system được hình thành theo component reuse thực tế, không dựng framework UI khổng lồ trước khi có screens.

## 6.10 Setup Gate J — Build Baseline

`Q-BOOT-001` is now **PROVISIONAL / COMPLETE FOR PROJECT SKELETON**. Bootstrap baseline:

- JDK / Java toolchain: **17**.
- Gradle Wrapper: **9.4.1**, wrapper distribution checksum pinned.
- Android Gradle Plugin: **9.2.1**.
- Kotlin / KGP: **2.4.20**.
- Compose Compiler Gradle plugin: **2.4.20**.
- Compose alignment: **BOM 2026.08.00**.
- `compileSdk = 37`.
- `targetSdk = 37`.
- `minSdk = 23`.
- AGP built-in Kotlin for Android modules; no `kotlin-android` plugin.
- root `gradle/libs.versions.toml` + included `build-logic/` convention plugins.
- provisional namespace/applicationId root: `app.universalmedia` (must be reviewed before first public distribution if branding changes).
- `debug` + `release` baseline; benchmark release-like type when benchmark module is wired.
- release-like R8/resource shrinking enabled early; release signing secrets never committed.
- wrapper/CLI is CI build authority.

### Android SDK baseline

As of **2026-09**, Google Play requires standard new apps and app updates to target **Android 16 / API 36 or higher**. Android 17/API 37 has been released; this pre-release project intentionally targets 37 so current runtime/media/large-screen behavior is validated during V1 rather than deferred. Stable Compose 1.12 also requires `compileSdk 37`.

### Upgrade policy

Toolchain versions are pinned, not frozen forever. Upgrade one compatibility axis intentionally, read official release/compatibility notes, run build + lint + tests + release-like build, then ratchet the baseline. Do not accept IDE upgrade prompts as architecture decisions.

Only dependencies required by an implemented slice are added to the catalog; the roadmap does not preload every V1 library.

## 6.11 Setup Gate K — Dependency Injection & Composition

DI framework là implementation choice, không phải domain architecture.

Trước code phải thống nhất:

- composition root nằm ở đâu.
- object lifetime/scopes.
- singleton nào thực sự process-wide.
- service/player ownership.
- repository implementation wiring.
- test replacement strategy.

Tránh service locator toàn cục và static mutable singleton state.

Framework cụ thể chỉ được chọn sau khi lifetime requirements rõ.

## 6.12 Setup Gate L — Testing Strategy

Testing topology phải được chuẩn bị cùng architecture, không để đến cuối V1.

### Fast JVM tests

- Domain invariants.
- Identity/reconciliation rules.
- Filename parsing/grouping.
- Progress calculations.
- Source policy.

### Persistence tests

- DAO behavior.
- Transaction behavior.
- Migration.
- Mapping integrity.
- Backup/restore transformations.

### Android/instrumentation tests

- SAF/document access.
- Room integration where Android runtime matters.
- lifecycle/process restoration scenarios.
- Media3/session integration.
- reader integration.

### End-to-end critical flows

- Register folder → scan → library → consume → resume.
- Re-scan same content without duplicate.
- Move/rename/delete reconciliation.
- Permission lost → understandable degraded state.
- Process death/restart → restore durable state.

### Test data

Maintain representative fixtures for:

- clean filenames.
- messy filenames.
- season/volume structures.
- duplicate files.
- moved files.
- large libraries.
- malformed archives/publications.

## 6.13 Setup Gate M — Performance Strategy

Performance là architecture requirement.

Không dùng debug build để kết luận production performance.

### Critical User Journeys to benchmark

- Cold launch.
- Warm launch where useful.
- Open library.
- Scroll large media grid.
- Initial scan.
- Incremental/re-scan.
- Open detail.
- Start video playback.
- Seek/resume playback.
- Open CBZ / turn pages.
- Open EPUB / restore locator.

### Required tools/approach

- Macrobenchmark for startup/large UI journeys.
- Frame timing/jank metrics where relevant.
- Baseline Profile when critical journeys stabilize.
- Release-like benchmark variant: non-debuggable and representative of production optimization.
- Trace evidence for regressions instead of subjective “feels smooth”.

### Performance budgets

Exact numeric budgets are not fixed in this roadmap.

They must be established from representative devices/datasets before feature volume grows, then ratcheted instead of guessed.

## 6.14 Setup Gate N — Security & Privacy Baseline

Phải review trước:

- exported Android components.
- URI permission handling.
- untrusted file/archive parsing.
- Web/network content handling.
- secrets/tokens for future providers/tracking.
- backup contents.
- logging of user paths/URLs/tokens.
- future plugin code/network permissions.
- future app-store/distribution policy implications of dynamic code loading.

Rules:

- Treat external media/files/providers as untrusted input.
- Do not log secrets/auth tokens.
- Do not trust archive paths blindly.
- Dynamic plugin security is mandatory scope of V3, not an afterthought.

## 6.15 Setup Gate O — Observability & Failure Model

Long-running/media operations cần failure semantics rõ.

Define:

- typed/domain-meaningful failures where useful.
- user-recoverable vs retryable vs terminal failures.
- structured logging tags/categories.
- scan/download/playback diagnostic context.
- no parsing exception strings in UI for business decisions.

Debug diagnostics phải có thể bật/tắt và không trở thành production domain API.

## 6.16 Setup Gate P — Engineering Quality Gates

Project bootstrap phải có tối thiểu:

- formatter/linter policy.
- static analysis policy.
- unit test command.
- Android test command strategy.
- release-like build command.
- dependency/version catalog.
- CI baseline.
- architecture/dependency verification when module graph becomes non-trivial.

Quality gate phải nhanh đủ để chạy thường xuyên; deep device/performance gates có thể tách riêng.

## 6.17 Setup Gate Q — Decision Records

Dùng ADR cho decisions có cost thay đổi lớn, ví dụ:

- minSdk.
- module graph.
- DI framework.
- EPUB engine/library.
- Media3 service ownership.
- Room schema strategy.
- scanner work execution model.
- plugin runtime model.

Không dùng ADR cho mọi naming/refactor nhỏ.

---

# 7. Development Dependency Order

Audit pass 1 tách **semantic design order** khỏi **implementation dependency order**. Dùng một thứ tự duy nhất cho cả hai sẽ khiến ta hoặc thiết kế database quá sớm, hoặc trì hoãn feedback implementation quá lâu.

## 7.1 Semantic Decision Order

Trước khi subsystem tương ứng được deep-design:

```text
Canonical Media / Grouping / Unit semantics
      ↓
Source / Binding / Asset semantics
      ↓
StorageRoot + access semantics
      ↓
Identity evidence + Reconciliation semantics
      ↓
Scan completeness / Availability semantics
      ↓
Progress + History semantics
      ↓
Library membership semantics
      ↓
Metadata precedence / override semantics
      ↓
Android runtime ownership for long-running operations
      ↓
Persistence state matrix + schema design (`Q-PER-001`)
      ↓
Module/API boundary design (`Q-MOD-001`, `Q-API-001`)
      ↓
Toolchain/project bootstrap (`Q-BOOT-001`)
```

Question IDs và exact order được quản lý ở phần 4.

## 7.2 V1 Implementation Dependency Order

Khi các semantics trực tiếp block slice đã đủ `PROVISIONAL/LOCKED`, implementation đi theo:

```text
Domain types + invariants
      ↓
Persistence contracts / minimal schema
      ↓
Storage access adapter
      ↓
Ingestion + reconciliation
      ↓
Source binding/resolution
      ↓
Consumption boundary
      ↓
Progress persistence
      ↓
Library projections/queries
      ↓
Optional metadata enrichment
      ↓
Backup + V1 hardening/performance
```

Không cần implement toàn bộ subsystem breadth trước vertical slice. Chỉ implement phần contract cần để chứng minh next critical flow, nhưng không bypass unresolved semantic blockers.

## 7.3 V2/V3 Dependency Direction

Sau khi V1 gate đạt:

```text
Content Provider API
      ↓
Provider Adapters
      ↓
Resolver / Mapping
      ↓
Unified Source Selection
      ↓
Online Consumption
      ↓
Download as Source
      ↓
Unified Search / V2 hardening
```

Sau khi V2 gate đạt:

```text
Plugin Contract Hardening
      ↓
Plugin Runtime + Security
      ↓
Tracking
      ↓
Discovery
      ↓
Sync
      ↓
Advanced Features
```

Không phát triển feature phía dưới bằng cách bypass layer/boundary phía trên.

---

# 8. Development Priority

Ưu tiên theo rủi ro kiến trúc và product value:

```text
1. Correct domain semantics
2. Stable internal identity
3. Android runtime/state ownership
4. Stable persistence
5. Local storage + ingestion reliability
6. Playback/reading reliability
7. Progress correctness
8. Library correctness
9. Metadata integration
10. Performance + process-death hardening
11. Provider abstraction
12. Resolver/mapping
13. Unified sources
14. Download
15. Plugin platform
16. Tracking
17. Discovery
18. Sync
19. Advanced features
```

Không ưu tiên animation, recommendation hoặc social feature trước core reliability.

---

# 9. Version Definition of Done

## 9.1 V1 Done Means

V1 không chỉ là “feature đã hiển thị”. Nó phải chứng minh:

- Local library is trustworthy.
- Identity remains stable across normal rescans/restarts.
- Durable progress restores correctly.
- Player/reader boundaries do not depend on local implementation details.
- Process death does not reveal hidden in-memory truth dependencies.
- Storage access loss has predictable behavior.
- Migration/backup path exists for durable user state.
- Critical performance journeys have measured baseline.
- Architecture does not require known redesign to introduce V2 sources.

## 9.2 V2 Done Means

- Built-in provider contracts support multiple real source types.
- Mapping is correctable and persistent.
- Local/download/online converge at source-resolution boundary.
- Unified progress works across mapped sources.
- Network/provider failure does not corrupt local canonical state.
- Provider replacement does not force UI/player/reader rewrite.
- Contracts are stable enough to expose to plugins.

## 9.3 V3 Done Means

- Plugin lifecycle/versioning/security are explicit.
- Ecosystem features do not bypass core identity/progress/library rules.
- Sync/tracking conflict policies are explicit and reversible where possible.
- Plugin failure can be diagnosed and contained.
- Advanced features extend stable contracts rather than adding parallel architectures.

---

# 10. Known High-Risk Areas

Các khu vực sau phải được coi là architecture risk, không phải “implementation detail nhỏ”:

## Identity & Reconciliation

- Ambiguous filenames.
- Duplicate copies.
- Rename/move across document trees.
- Metadata mismatch.
- Provider mismatch.

## Android Storage

- Revoked URI permissions.
- Removable storage.
- Providers with different supported operations.
- Large directory trees.
- Non-file-backed document URIs.

## Process & Background Work

- Process death during scan.
- Process death during playback handoff.
- Retry/idempotency.
- User cancellation.
- battery/background restrictions.

## Consumption

- Large images/archive memory pressure.
- malformed CBZ/EPUB.
- expiring online streams.
- player/session lifecycle.

## Persistence

- schema migration.
- partially completed reconciliation.
- mapping consistency.
- backup/restore across app versions.

## Backup / Restore

- corrupt/truncated or unsupported backup archive;
- restore across backup-format/app/database versions;
- stale URI/root hints being mistaken for current access;
- non-empty-install restore conflict / accidental merge;
- secret/device-bound encrypted state leakage or undecryptable restore;
- torn backup snapshot while user state is changing.

## Future Plugins

- untrusted code/data.
- API compatibility.
- permissions/network hosts.
- crash/failure isolation.

---

# 11. Explicit Deferred Decisions

Các quyết định sau **không được đoán trong roadmap**. Chúng phải được research/benchmark và chốt bằng technical spec/ADR trước khi dependency tương ứng được implementation sâu:

- Dependency injection framework.
- EPUB rendering engine/library.
- Image loading/cache library.
- Exact archive reader implementation.
- Exact metadata provider(s) used in V1.
- Exact optimization/supplemental scope for MediaStore versus SAF traversal after performance evidence; foundation direction is already SAF-primary / MediaStore-supplemental.
- Exact V2 content providers.
- Plugin execution technology/runtime.
- Cloud/sync backend.
- Numeric performance budgets.
- Exact V1 backup archive serialization/container, optional encryption/password UX and retention cadence under Q-BACK-001 semantics.
- Exact Android Auto Backup cloud/D2D inclusion matrix after representative DB-size/security measurements.
- Final multi-module graph.

Deferring these decisions is intentional, not missing planning.

---

# 12. Pre-Implementation Exit Checklist

Feature implementation V1 chỉ bắt đầu sau khi checklist foundation đạt mức đủ dùng.

## Product / Domain

- [ ] V1 scope accepted.
- [x] `Q-DOM-001..003` are PROVISIONAL or LOCKED.
- [x] `Q-SRC-001` and `Q-STO-001` are PROVISIONAL or LOCKED.
- [x] `Q-ID-001`, `Q-REC-001`, `Q-SCN-001` are PROVISIONAL or LOCKED.
- [x] `Q-PROG-001`, `Q-PROG-002`, `Q-HIST-001`, `Q-LIB-001` are PROVISIONAL or LOCKED.
- [x] `Q-META-001` is PROVISIONAL or LOCKED with explicit precedence/provenance rule.
- [x] `Q-RUN-001`, `Q-BACK-001` are PROVISIONAL or LOCKED with explicit execution/backup portability rules.
- [x] No production schema/API depends on an OPEN blocking question.

## Android Runtime

- [x] `Q-RUN-001` is PROVISIONAL or LOCKED.
- [x] Process-death/state ownership model documented.
- [x] Playback service ownership decided.
- [x] WorkManager vs coroutine decision rules documented.
- [x] Persistent work is restartable/idempotent and does not treat Worker state as domain truth.
- [x] Cancellation/retry/supersession/finalization rules documented.
- [x] Storage access model documented.

## Data

- [x] Persistence responsibilities agreed (`Q-PER-001`).
- [x] Domain vs persistence model separation agreed (`Q-PER-001` / `Q-API-001`).
- [x] Migration strategy baseline defined (`Q-PER-001`).
- [x] Backup boundary baseline defined (`Q-BACK-001` PROVISIONAL).
- [x] Initial relational schema families + transaction/FK rules reviewed.

## Project Structure

- [x] Initial module/dependency graph reviewed (`Q-MOD-001`).
- [x] Composition/DI strategy selected: constructor injection + app composition root; framework remains deferred.
- [x] Build toolchain pinned (`Q-BOOT-001`: Gradle 9.4.1 / AGP 9.2.1 / JDK 17 / Kotlin 2.4.20 / Compose Compiler 2.4.20).
- [x] targetSdk/compileSdk/minSdk decisions recorded exactly (`Q-BOOT-001`: 37 / 37 / 23).

## Quality

- [ ] Unit-test baseline works.
- [ ] Android-test baseline works.
- [ ] Static analysis/formatting works.
- [ ] CI baseline works.
- [x] Benchmark module/variant wiring implemented and statically reviewed; device execution/numeric evidence remain pending.
- [x] Critical V1 performance journeys listed.

## Security

- [x] URI/file input threat review completed and documented in the Phase-0 skeleton.
- [x] exported-component baseline reviewed; static verifier enforces exactly one exported launcher component across main manifests.
- [x] logging/secrets policy documented; production logs must not contain secrets/tokens/full user paths/full content URIs.

---

# 13. Immediate Next Work

R4.16 has generated the Phase-0 skeleton opened by `Q-BOOT-001` and collected structural/static evidence. Do not reopen Stages A–I without contradiction evidence, and do not treat unexecuted Gradle/Android gates as PASS.

Current sequence:

```text
COMPLETED PROVISIONAL — STAGES A–I
Foundation semantics → persistence → module/API → toolchain baseline
        ↓
GENERATED + STATICALLY VERIFIED — PHASE-0 SKELETON
module graph / build-logic / launch smoke surface / benchmark boundary /
security docs + verifier / CI definition
        ↓
NEXT EXECUTION GATE
official wrapper on JDK 17 + SDK 37
→ Gradle help/sync
→ verifyFast
→ Android smoke test
→ verifyRelease
→ CI clean-checkout evidence
        ↓
First local vertical slice
```

No DI-framework question blocks the skeleton or first slice: manual constructor injection/app composition root remains the default until implementation evidence demonstrates real framework value.

The next session must prioritize **executable bootstrap evidence**, not add more product modules or feature breadth. Unit-test, Android-test, lint/format, assemble and CI checklist entries stay open until their commands actually pass. Security documentation/static manifest gates and benchmark wiring are now implemented; Macrobenchmark measurements remain a later device-performance gate.

## 13.1 First Implementation Trigger

Bootstrap tooling/project skeleton có thể chuẩn bị độc lập ở mức build/CI tối thiểu, nhưng **feature/domain implementation không mở rộng** cho đến khi các blocking semantics của vertical slice đã `PROVISIONAL/LOCKED`.

Vertical slice đầu tiên vẫn nên nhỏ:

```text
Register one local root
        ↓
Discover one simple supported media shape
        ↓
Create/reuse stable canonical Media + optional Unit when subdivision semantics exist
        ↓
Persist
        ↓
Show in Library
        ↓
Consume
        ↓
Persist progress
        ↓
Kill/restart process
        ↓
Restore correctly
```

Slice đầu tiên là validation của decisions, không phải lý do để bypass unresolved questions. Nếu implementation evidence contradicts a provisional decision, quay lại Decision Queue và sửa foundation trước khi mở breadth.

---

# 14. Research Basis for Pre-Development Decisions

Các setup rules trong tài liệu này được định hướng bởi official Android guidance và các architecture patterns phù hợp với project media.

## Android architecture / UI state

- Android app architecture guidance: UI should render application state and follow clear data-flow boundaries.
- Domain/use-case layer is useful when it adds real business logic/orchestration; it should not become mandatory wrapper ceremony.
- ViewModel/state holders are not durable persistence.

References:

- https://developer.android.com/topic/architecture
- https://developer.android.com/topic/architecture/ui-layer
- https://developer.android.com/guide/components/activities/process-lifecycle
- https://developer.android.com/guide/components/activities/activity-lifecycle

## Background work

- Coroutines fit bounded in-process work whose current execution may end with its owning scope/process.
- WorkManager is the recommended persistent-work scheduler when a request must survive leaving the screen/process and may be rescheduled after constraints/restart/reboot; a Worker execution itself is still stoppable/recreated and therefore must be restart-safe.
- WorkManager unique work helps serialize/coalesce orchestration but does not replace app-owned run authority.
- Long-running WorkManager uses foreground-service machinery and remains subject to evolving Android quotas/restrictions; direct foreground services are reserved for explicit user-visible continuous requirements.
- Android 15 adds timeout constraints for `dataSync`/`mediaProcessing` foreground services; Android 16 applies job runtime quotas to jobs including WorkManager.

References:

- https://developer.android.com/develop/background-work/background-tasks/persistent
- https://developer.android.com/reference/androidx/work/WorkManager
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
- https://developer.android.com/develop/background-work/services/fgs/timeout
- https://developer.android.com/about/versions/16/behavior-changes-all

## Backup / restore

- V1 product backup is a user-controlled versioned logical app-state archive; Android Auto Backup/D2D is supplemental.
- Auto Backup is file-oriented and cloud quota-limited (25 MB), so full library recovery cannot depend on it.
- Android explicitly warns that restored URIs can be unstable; storage registrations/locators restore as hints and must be reauthorized/revalidated.
- Android 12+ provides separate cloud-backup and device-transfer extraction rules; backup classification must be explicit.
- Room schema migration and explicit backup-format migration are separate compatibility boundaries.
- Sensitive/device-bound encrypted data must be excluded unless a secure recovery design proves portability.

References:

- https://developer.android.com/identity/data/backup
- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/about/versions/12/behavior-changes-12
- https://developer.android.com/privacy-and-security/risks/backup-best-practices
- https://developer.android.com/training/data-storage/room/migrating-db-versions
- https://developer.android.com/training/data-storage/shared/documents-files

## Android storage

- Storage Access Framework/document-tree access is user-controlled.
- Persistable URI grants are required when access must survive restart where supported.
- Provider capabilities can differ.

Reference:

- https://developer.android.com/training/data-storage/shared/documents-files

## Playback

- Media3 supports separating player/session ownership from Activity/UI through `MediaSessionService` for background playback.

Reference:

- https://developer.android.com/media/media3/session/background-playback

## Modularization

- Prefer high cohesion and low coupling.
- Module boundaries should follow responsibilities, not arbitrary layer count.

Reference:

- https://developer.android.com/topic/modularization/patterns

## Performance

- Macrobenchmark is appropriate for startup and larger UI journeys.
- Benchmark release-like/non-debuggable builds.
- Baseline Profiles should follow stable critical user journeys and be measured, not assumed.

References:

- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- https://developer.android.com/topic/performance/baselineprofiles/overview
- https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile

## Toolchain / bootstrap compatibility

- Q-BOOT-001 uses a mutually supported compatibility intersection rather than independently selecting the newest AGP/Kotlin/Gradle releases.
- Baseline: Gradle 9.4.1, AGP 9.2.1, JDK 17, Kotlin/KGP 2.4.20, Compose Compiler 2.4.20, Compose BOM 2026.08.00.
- Android modules use AGP built-in Kotlin; pure JVM modules use Kotlin JVM plugin.
- Current AndroidX/Media3 direction supports a practical `minSdk 23`; stable Compose 1.12 requires `compileSdk 37`.
- Gradle Wrapper checksum + version catalog + included convention-plugin build are part of reproducible bootstrap.

References:

- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://developer.android.com/build/releases/agp-9-0-0-release-notes
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://kotlinlang.org/docs/gradle-configure-project.html
- https://kotlinlang.org/docs/compose-compiler-migration-guide.html
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- https://developer.android.com/jetpack/androidx/versions
- https://developer.android.com/jetpack/androidx/releases/media3
- https://docs.gradle.org/current/userguide/gradle_wrapper.html
- https://docs.gradle.org/current/userguide/version_catalogs.html
- https://docs.gradle.org/current/userguide/implementing_gradle_plugins_convention.html

## Google Play target API baseline

As of 31 August 2026, standard new apps and updates submitted to Google Play must target Android 16 / API 36 or higher.

Reference:

- https://developer.android.com/google/play/requirements/target-sdk

---

# 15. Final Project Rules Summary

```text
Canonical identity belongs to the app.
Provider identity is mapping/evidence.
Asset location is not media identity.
StorageRoot/access grant is not media identity.
Evidence is not identity.
Partial-scan absence is not deletion.
Only finalized authoritative comparable scan coverage can produce absence evidence.
Incremental/non-snapshot success is not blanket absence authority.
Unavailable is not automatically removed.
Progress belongs to canonical media/unit.
Typed resume anchor and completion are separate durable semantics.
Progress values are not monotonic version clocks.
Local/download/online are source variants, not parallel apps.
UI renders state; it does not own domain truth.
ViewModel is not persistence.
Long-running work must have correct Android ownership.
Scheduler identity/state is not domain run identity/state.
Persistent work is restartable/idempotent; process death cannot be a correctness event.
Negative scan reconciliation occurs only in guarded authoritative finalization, never merely because a Worker stopped.
Foreground service is an explicit user-visible runtime choice, not a generic durability mechanism.
Player/Reader consume resolved content, not provider/database objects.
Database stores normalized internal state; external sources remain authorities for external availability.
History is append-oriented meaningful consumption activity, not merely Progress.updatedAt or every checkpoint.
Library membership is user/app state, not a filesystem mirror.
Explicit user metadata override outranks automated refresh/rematch until the user resets it.
Declared local metadata is not filename/path inference; effective metadata keeps provenance.
Metadata enrichment does not own canonical identity, Library, Progress/History or Asset technical truth.
Use cases exist for real policy/orchestration, not ceremony.
Persistence surrogate target rows are implementation detail; domain/backup identity remains MediaId/UnitId.
Room entities/DAOs and Android storage/player implementation types do not cross public feature/domain boundaries.
Canonical Room DB uses explicit tested migrations; missing migration paths do not silently destructive-reset user truth.
App composition root wires concrete implementations; constructor injection is default while DI framework remains a bootstrap choice.
Open questions block the downstream design/implementation that depends on them.
Performance/testing/security are foundation requirements, not final polish.
Bootstrap toolchain is pinned as a tested compatibility matrix; IDE/latest-version prompts do not silently change build authority.
Android modules use AGP built-in Kotlin; Compose compiler version follows Kotlin; wrapper/version catalog/build-logic own reproducible build inputs.
Provider contracts must stabilize before dynamic plugins.
V1 proves local core.
V2 proves unified sources.
V3 opens the ecosystem.
```

This document is the baseline for all subsequent V1 technical specifications and implementation decisions. R4.16 additionally records Phase-0 skeleton/static evidence and keeps executable Gradle/Android gates explicitly pending until fresh JDK-17/SDK-37 output exists.
