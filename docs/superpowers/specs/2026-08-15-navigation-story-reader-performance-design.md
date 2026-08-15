# Navigation, Story, and Reader Performance Design

## Goal

Remove avoidable UI reload and navigation jank in the three highest-impact runtime paths without changing the product's visible information architecture.

## Wave 1: retained top-level navigation

Discover, Home, and Library each own a persistent Navigation 3 back stack. Switching the floating top-level destination changes the active stack instead of clearing navigation state. Each stack is decorated independently with saveable-state and ViewModel-store ownership so inactive tabs retain screen and ViewModel state without sharing stores with equal route keys in another tab.

Only the start stack plus the active non-start stack are rendered. The remaining stack state stays retained. Back pops the active nested route first; at a non-start top-level root it returns to Home. Top-level ViewModels that can remain retained must use demand-driven `WhileSubscribed` collection rather than `SharingStarted.Eagerly`.

## Wave 2: Story lazy workloads and aggregate downloads

`StoryViewModel` owns only always-visible Story data plus a one-shot ordered readable-target snapshot used by the hero. `MappingViewModel` is created/collected only while Sources is selected. `ChapterListViewModel` is created/collected only while Chapters is selected and uses `WhileSubscribed` state sharing.

`DownloadViewModel` remains available for hero download commands but performs no database observation until Chapters needs statuses. While Chapters collects `statuses`, it maps one `observeAll()` stream by release ID; leaving Chapters cancels that UI collection. Per-release `watch()` observers are removed.

## Wave 3: one Reader session across chapter changes

Previous/Next chapter actions call `ReaderViewModel.openChapter()` instead of navigating to another Reader route. The current chapter ID is stored in `SavedStateHandle`; switching chapter flushes pending progress, clears the prior explicit release selection, and reloads the new chapter inside the existing Reader ViewModel.

Reader chapter graph materialization groups releases by canonical chapter ID once before mapping chapters. It must not scan the full releases list separately for every chapter.

## Non-goals

This change does not add macrobenchmarks, Baseline Profiles, Search filter caching, Discover flow sharing, DAO projection redesign, or backdrop-blur changes. Those remain Wave 4 work.

## Verification

Behavior is guarded by navigation, Story/download, and Reader unit/instrumentation tests plus a repository static performance-lifecycle contract. Existing UI token/shared-component policies, Roborazzi, Detekt, lint, architecture verification, and Room schema stability remain required.
