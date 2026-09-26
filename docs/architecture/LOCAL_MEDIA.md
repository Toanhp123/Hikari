# Android local media walking skeleton

## Scope and boundaries

Implemented flow: restore or choose one Android folder, scan its SAF document tree,
classify source items, show results, then open video, image pages, or UTF-8 text. The
selected root is persisted; scan results themselves exist only in memory. Explicit
Library snapshots and reader progress are now persisted separately; see
[USER_STATE](USER_STATE.md) for canonical behavior and resume limits.

- `domain/media/media.dart`: pure Dart `Media`, `MediaType`, opaque value `SourceId`,
  and `SourceMediaRef`. New source identifiers do not require changing an enum. `itemId`
  is opaque to domain; there is no canonical `MediaId`.
- `infrastructure/local_media/`: platform-channel access, document-entry DTO,
  deterministic classification and filename ordering. Kotlin `LocalMediaChannel`
  owns Android document APIs. Queries and reads run on a worker, not Android's UI thread.
- `infrastructure/playback/`: media-kit controller, video surface, errors and lifecycle.
- `features/`: list, player page, manga and text presentation. Constructor callbacks
  and an injected playback widget keep concrete infrastructure out of features.
- `app/`: composition and ordinary Flutter navigation. No application use-case layer
  is needed for these independent operations.

The existing architecture guard is unchanged.

## Discovery and classification

The picker returns a tree document URI and display name. The Dart adapter enumerates
children iteratively, preserves each direct parent, and classifies in an isolate.
The selected root can itself be a manga directory.

| Source item | Domain type | Title |
| --- | --- | --- |
| `.mp4`, `.mkv`, `.webm`, `.m4v` file | Anime | Filename without final extension |
| Directory with direct `.jpg`, `.jpeg`, `.png`, `.webp` children | Manga | Directory display name |
| `.txt`, `.md` file | Light Novel | Filename without final extension |

Extensions are case-insensitive. Images never become individual media items.
A parent containing only a nested manga directory is not itself manga. Mixed-content
folders still emit their video/text files. Natural numeric ordering uses digit-run
length and lexical comparison, avoiding integer overflow; raw name/identifier breaks ties.

This is deliberately an extension heuristic: ordinary videos are labeled Anime and
ordinary text is labeled Light Novel. No content sniffing, metadata, hashing, image
decoding, or video reads occur during discovery.

## Android storage choice

A small native MethodChannel adapter uses `ACTION_OPEN_DOCUMENT_TREE`,
`DocumentsContract` queries, `takePersistableUriPermission` with the granted read flag,
and bounded `ContentResolver.openInputStream` reads. It never requests broad storage
permissions or converts content URIs into filesystem paths. Streams/cursors close with
Kotlin `use`. Picker cancellation returns null and preserves existing presentation results.

The MVP stores exactly one selected tree URI in Android `SharedPreferences`. On startup,
the app restores that locator, verifies its persisted read grant, resolves the root document,
and scans it automatically. Choosing another folder replaces the stored root and releases
the previous persisted read grant. Scan results are transient, not persisted as a
library/database. Only explicit Library snapshots are persisted. Old snapshots can
remain visible after switching roots but become unavailable when their grant is
released; users can still remove them. Multi-root persistent access remains deferred.
If the stored grant is missing or a query establishes that the root no longer exists, the
stored selection is cleared. Transient provider/query failures keep the selection so retry can
work without reopening the picker. The UI never retries automatically.

Compared with `saf` 2.1.2 (MIT; Flutter >=3.10, Dart >=3, Android >=21), native code
covers only the four operations currently consumed. `saf` offers useful walk/stream/FD
APIs and recent AGP 9 fixes, but its broader surface is unnecessary here. Neither choice
bypasses Android's restrictions on storage roots, Download, Android/data or Android/obb.
Moved, removed, revoked or unavailable documents produce errors and allow reselection.
Providers reporting an unfinished listing fail explicitly rather than returning a false
complete library. Large/remote providers can still take time; no background scheduler exists.
Folder-picker cancellation is supported; cancellation of a recursive scan already in progress
is not implemented.

References:
- [Android SAF guide](https://developer.android.com/guide/topics/providers/document-provider)
- [Android document/tree access and restrictions](https://developer.android.com/training/data-storage/shared/documents-files)
- [saf package/API](https://pub.dev/packages/saf)
- [saf AGP 9 compatibility](https://github.com/jvoltci/saf/issues/45)

## Playback and readers

Video uses `media_kit` 1.2.6, `media_kit_video` 2.0.1 and `media_kit_libs_video` 1.0.7.
App bootstrap initializes media-kit once before any Player construction. One concrete
app-owned LocalVideoSession per Flutter app/engine lazily constructs one Player and
VideoController at first playback and reuses them across routes. Routes await frozen
progress persistence and stop/unload, never native Player disposal. Only owner shutdown
cancels subscriptions/timer and disposes the Player; VideoController cleanup belongs to
that Player. LocalVideo displays the shared controller and errors only. Native commands
are serialized; leaving foreground flushes and pauses without closing the session.
No whole-video cache copy is made. Progress/reset semantics live in [USER_STATE](USER_STATE.md).
Installed media-kit 1.2.6 `media_native.dart` normalizes Android `content://` to
`fd://`; its Android provider owns/caches the descriptor, and `Media` finalization
closes it after release. The player retains its playlist while playing. Hikari does
not close engine-owned descriptors independently.

All three Dart packages are MIT, but that does **not** license bundled native codecs
as MIT. Android native package 1.3.8 downloads libmpv build v1.1.7; its default build
uses LGPLv3-or-later FFmpeg components and separate third-party licenses. Before
redistributing binaries, audit/provide the corresponding notices and source/relinking
materials required by those licenses. This development APK is not a release-compliance
claim. See [native build sources](https://github.com/media-kit/libmpv-android-video-build)
and [mpv copyright](https://github.com/mpv-player/mpv/blob/master/Copyright).

Manga enumerates direct page references when opened and reads one page at a time.
Previous/next buttons remain available after a failed page. InteractiveViewer supports
pinch/zoom. Encoded reads are capped at 32 MiB and decoded images fit within 2048×4096;
previous images are evicted on navigation/disposal. Very long webtoon pages lose detail
at this ceiling; tiled decoding is not part of this slice.

Text reads are capped at 4 MiB and decoded as UTF-8 with malformed sequences replaced.
Markdown displays as plain text. The reader scrolls, constrains line width and supports
selection. Limits fail explicitly rather than truncating content. Scan metadata is capped
at 50,000 entries; choose a smaller tree if exceeded.

Non-Android platforms display an unsupported local-scan message. Bootstrap initializes
media-kit, but no native Player is constructed until playback is requested.
Windows/iOS scanning is not implemented. Remote search remains available on these
platforms; its independent capability-based route is documented in
[REMOTE_MANGA](REMOTE_MANGA.md). Local manga still opens folder pages directly,
without a chapter-selection screen.

## Deferred

CBZ/ZIP, CBR/RAR, EPUB, PDF; series/season/chapter parsing; canonical identity,
hashing/deduplication, enrichment, covers/thumbnails; rename recovery, watchers,
history sessions, downloads, remote providers and generic source/engine frameworks.
Progress/Library persistence and minimal page/text source capabilities are implemented
in [USER_STATE](USER_STATE.md); canonical identity remains deferred.

## Device verification checklist

The user verified the original local-media walking skeleton on a real Android device.
The new persistence/resume slice has not been verified on a device. Automated tests do
not establish real SAF provider behavior, codec support, sound output, seeking or native
lifecycle safety.
Use user-created/legal fixtures (not checked into this repository):

```text
HikariTest/
  anime/test.mp4
  manga/My Manga/1.jpg
  manga/My Manga/2.jpg
  manga/My Manga/10.jpg
  novel/sample.txt
```

1. Run `fvm flutter run -d <android-device>` and choose HikariTest using the system picker.
2. Confirm `test` / Anime, `My Manga` / Manga, `sample` / Light Novel.
3. Fully stop/restart the app and confirm the same root restores and rescans without opening
   the picker again.
4. Play video; verify sound, pause/resume, seek, rotate and background. From both Local
   Media and Library, seek to a non-zero position, exit with AppBar Back, reopen and
   confirm resume; repeat using Android edge/system back with predictive back enabled.
   Try rapid double-back and cancellation of a gesture; confirm no double exit or reset
   to zero. Complete playback, reopen, and verify completion/replay behavior.
   Stress repeatedly: video A → leave → manga → leave → A → leave → B → leave.
   Check both back paths, background transitions and leaving during loading. Confirm each
   video's latest meaningful progress resumes independently. This ownership rebuild has
   deterministic fake-driver coverage, not physical-device verification.
5. Read manga pages in 1, 2, 10 order, zoom, navigate back, and leave/reopen the reader.
   Complete manga, reopen at page zero and leave untouched: completion must remain.
   Navigate to an earlier nonfinal page: progress becomes active; finish again.
6. Read/scroll text; repeat with malformed UTF-8, Markdown and an empty text file.
7. Cancel folder reselection and confirm previous results remain. Then select a different root
   and confirm it replaces the old selection and releases its persisted grant. Save an
   item from the old root to Library first; confirm its snapshot remains visible, opening
   reports unavailable access, and removal still works. Restart: only the new root rescans;
   unsaved old scan results do not return. Multi-root access is not supported.
8. Test empty folder, nested/mixed folders, corrupt images/video, missing/revoked documents,
   and files exceeding reader size limits. Error pages must support retry/back/reselection.
9. Repeat on an Android 11+ device with scoped-storage restrictions and TalkBack enabled.

On Windows setups where the Pub cache and project are on different drives, Kotlin incremental
compilation can fail while relativizing cache paths. The repository no longer disables Kotlin
incremental compilation globally. On an affected machine, add this line to the user-local
`%USERPROFILE%\.gradle\gradle.properties`, run the build, then remove the line when it is no
longer needed:

```properties
kotlin.incremental=false
```

Then run `fvm flutter build apk --debug` normally. This is a machine-local workaround, not a
repository default; while present it applies to Gradle builds for that user.
