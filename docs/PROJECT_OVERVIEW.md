# Hikari — Project Overview

> Status: **Living project specification**
>
> Tài liệu này là mô tả cấp cao của dự án Hikari. Mục tiêu là để bất kỳ ai quay lại dự án sau một thời gian đều có thể nhanh chóng hiểu:
>
> - Hikari là gì;
> - Hikari giải quyết vấn đề gì;
> - sản phẩm dự kiến có những nhóm chức năng nào;
> - những gì đã được quyết định;
> - những gì vẫn còn mở để nghiên cứu;
> - thứ tự phát triển tổng quát.
>
> Khi định hướng sản phẩm thay đổi, cập nhật tài liệu này cùng với thay đổi đó.

---

## 1. Hikari là gì?

Hikari là một ứng dụng media hub đa nền tảng, tập trung vào trải nghiệm xem và đọc nội dung trong một giao diện thống nhất.

Các nhóm nội dung mục tiêu:

```text
Movies
TV Series
Anime
Manga / Comics / Webtoon
Light Novels / Web Novels
```

Hikari không được định nghĩa là "một app xem anime", "một manga reader" hay "một movie player" riêng lẻ.

Ý tưởng trung tâm là:

```text
một thư viện
một lịch sử
một hệ thống tiến độ
một tìm kiếm thống nhất
một trải nghiệm UI thống nhất
```

cho nhiều loại nội dung khác nhau.

---

## 2. Mục tiêu sản phẩm

Hikari hướng tới một ứng dụng có trải nghiệm sử dụng gần với các media app hiện đại nhưng vẫn giữ khả năng mở rộng kiểu các ứng dụng reader/player mã nguồn mở.

Các mục tiêu chính:

1. Giao diện sạch, nhanh và nhất quán.
2. Xem và đọc nhiều loại nội dung trong cùng một app.
3. Lưu lịch sử và tiến độ xem/đọc thống nhất.
4. Có library cá nhân.
5. Có khả năng hoạt động offline ở những khu vực phù hợp.
6. Có kiến trúc source/provider đủ tách biệt để thay đổi nguồn mà không phá core.
7. Có khả năng mở rộng sang plugin/extension trong tương lai.
8. Không phụ thuộc chặt vào một backend hoặc một nguồn nội dung duy nhất.
9. Giữ phần domain và business logic độc lập tối đa với UI framework và platform.
10. Tránh kiến trúc phải đập đi xây lại mỗi khi thêm một nhóm nội dung hoặc platform mới.

---

## 3. Platform mục tiêu

### Đã quyết định

```text
Android
Windows
iOS
```

Android là platform ưu tiên trong giai đoạn đầu.

Windows là desktop target đầu tiên.

iOS được giữ compatibility từ sớm nhưng việc build và test thực tế cần macOS + Xcode.

### Chưa ưu tiên

```text
Web
Linux
macOS
tvOS
```

Android TV có thể được hỗ trợ về sau nhưng vẫn thuộc platform Android.

---

## 4. Công nghệ nền tảng

### Đã quyết định

Framework chính:

```text
Flutter + Dart
```

Flutter chịu trách nhiệm chính cho:

- UI;
- navigation;
- state presentation;
- phần lớn business/application code;
- manga reader;
- novel reader;
- orchestration của media player;
- cross-platform behavior.

Native code được phép sử dụng khi cần.

Android native:

```text
Kotlin
```

có thể được dùng cho:

- Android-specific APIs;
- background media;
- Picture-in-Picture;
- Android TV;
- notification/media session;
- download/service integration;
- platform capability mà Flutter package không đáp ứng tốt.

Nguyên tắc:

> Flutter là framework chính, native code là platform layer chứ không phải nơi chứa business logic của Hikari.

---

## 5. Các nhóm chức năng sản phẩm

Các mục dưới đây mô tả capability mong muốn của sản phẩm. Không đồng nghĩa tất cả phải có trong phiên bản đầu tiên.

### 5.1 Home

Mục tiêu:

- nội dung đang xem/đọc dở;
- nội dung mới hoặc nổi bật;
- cập nhật gần đây;
- các hàng nội dung theo loại;
- shortcut tới library và search.

Ví dụ:

```text
Continue Watching
Continue Reading
Trending
Recently Updated
Movies
Anime
Manga
Novels
```

---

## 5.2 Search

Search cần có khả năng phát triển từ:

```text
search trong một source
```

đến:

```text
search nhiều source
```

và cuối cùng:

```text
unified search
```

Search result phải sử dụng domain model chung thay vì để UI phụ thuộc trực tiếp vào dữ liệu riêng của từng provider.

---

## 5.3 Media Details

Một màn hình details dùng chung ở cấp domain, sau đó mở rộng theo loại nội dung.

Thông tin có thể bao gồm:

- title;
- alternative titles;
- artwork;
- description;
- genres/tags;
- status;
- year;
- rating;
- metadata;
- available sources;
- episode/chapter list;
- library state;
- user progress.

---

## 5.4 Video

Áp dụng cho:

```text
Movie
TV Series
Anime
```

Capability dự kiến:

- playback;
- play/pause/seek;
- episode navigation;
- quality selection;
- audio tracks;
- subtitles;
- playback speed;
- progress persistence;
- resume;
- fullscreen;
- Picture-in-Picture;
- external player handoff;
- offline/download support ở phase sau;
- casting ở phase sau.

Player không được phụ thuộc trực tiếp vào provider cụ thể.

Provider trả về một dạng dữ liệu chuẩn, ví dụ:

```text
PlayableMedia
Stream
AudioTrack
SubtitleTrack
```

Player chỉ biết phát dữ liệu chuẩn đó.

---

## 5.5 Manga / Comics Reader

Hỗ trợ dự kiến:

```text
Manga
Manhwa
Manhua
Webtoon
Comics
```

Reading modes dự kiến:

- vertical;
- webtoon;
- left-to-right;
- right-to-left;
- single page;
- double page ở phase sau.

Reader cần quan tâm đặc biệt đến:

- image memory;
- preload;
- decoded image cache;
- disk cache;
- restore reading position;
- chapter navigation;
- zoom;
- orientation;
- long webtoon pages.

---

## 5.6 Novel Reader

Hỗ trợ:

```text
Light Novel
Web Novel
local text/ebook formats ở các phase sau
```

Capability dự kiến:

- vertical reading;
- paged reading nếu phù hợp;
- font family;
- font size;
- line height;
- margins;
- themes;
- chapter navigation;
- bookmarks;
- reading position;
- offline chapter cache;
- text-to-speech ở phase sau.

---

## 5.7 Library

Library là nơi người dùng quản lý nội dung muốn theo dõi.

Cần độc lập với provider ở mức có thể.

Capability dự kiến:

- add/remove;
- categories;
- sort;
- filter;
- favorite;
- status;
- last updated;
- current progress.

---

## 5.8 History & Progress

Hikari phải coi progress là một capability cấp core.

Các dạng progress:

```text
Video:
episode + playback position + duration

Manga:
chapter + page/scroll position

Novel:
chapter + reading position
```

Mục tiêu là Home có thể xây dựng "Continue Watching / Continue Reading" từ cùng một hệ thống progress.

---

## 5.9 Sources / Providers

Hikari không nên buộc domain core phụ thuộc vào một nguồn nội dung duy nhất.

Provider có thể cung cấp một hoặc nhiều capability:

```text
Search
Browse
Details
Episodes
Video Streams
Chapters
Manga Pages
Novel Content
```

Một provider không bắt buộc phải hỗ trợ tất cả.

Ví dụ:

```text
Provider A
├── Anime
└── Video Streams

Provider B
├── Manga
└── Manga Pages

Provider C
├── Novel
└── Novel Chapters
```

Provider architecture là một subsystem quan trọng và sẽ được thiết kế riêng khi đến phase tương ứng.

---

## 5.10 Extensions

### Định hướng

Extension/plugin system là mục tiêu dài hạn.

### Chưa chốt

Chưa quyết định chính thức:

- JavaScript runtime nào;
- extension manifest format;
- sandbox model;
- API versioning;
- compatibility với Mangayomi;
- compatibility với Mihon/Aniyomi;
- APK extensions;
- remote extension repositories.

Nguyên tắc hiện tại:

> Core phải có provider contracts đủ sạch để sau này extension runtime chỉ là một cách tạo Provider implementation.

Không thiết kế toàn bộ app xoay quanh extension ngay từ phiên bản đầu.

---

## 5.11 Offline / Downloads

Dự kiến cần hỗ trợ:

```text
video downloads
manga chapter downloads
novel chapter downloads
```

Nhưng download engine sẽ không được xây trước domain/data contracts.

Download phải được coi là một subsystem riêng:

```text
Download Job
Queue
Progress
Storage
Retry
Cleanup
Metadata
```

---

## 5.12 Tracking

Có thể tích hợp các dịch vụ metadata/tracking trong tương lai.

Ví dụ:

```text
AniList
MyAnimeList
TMDB
Trakt
Kitsu
SIMKL
```

Tracking không được trở thành nguồn chân lý duy nhất của library nội bộ.

Hikari phải hoạt động được ngay cả khi không đăng nhập tracker.

---

## 6. Trải nghiệm người dùng mục tiêu

Một flow điển hình:

```text
Home
  ↓
Search / Browse
  ↓
Details
  ↓
Watch / Read
  ↓
Progress saved
  ↓
Continue Watching / Reading
```

Library flow:

```text
Details
  ↓
Add to Library
  ↓
Library
  ↓
Resume / Update / Organize
```

Source flow:

```text
User chooses content
      ↓
Hikari resolves provider
      ↓
Provider returns canonical domain data
      ↓
Player / Reader consumes canonical data
```

---

## 7. Nguyên tắc kiến trúc

Các nguyên tắc này quan trọng hơn việc chọn package cụ thể.

Kiến trúc cấp cao đã được chốt trong [`ADR-001`](decisions/ADR-001-hybrid-layered-architecture.md). Hikari dùng cấu trúc hybrid: presentation theo feature, còn domain/application/infrastructure giữ boundary theo trách nhiệm.

```text
lib/
├── app/              # composition root, routing, global app configuration
├── core/             # cross-cutting primitives thật sự dùng chung
├── domain/           # pure Dart business concepts, rules, ports/contracts
├── application/      # workflows/use cases điều phối domain contracts
├── infrastructure/   # DB/HTTP/providers/engines/platform implementations
└── features/         # Flutter UI + presentation state theo feature
```

Đây là **architecture map**, không phải danh sách folder phải tạo ngay. Chỉ tạo boundary khi có code thật cần đến nó.

Dependency direction này được machine-enforce bởi architecture guard trong test suite, được chốt ở [`ADR-003`](decisions/ADR-003-architecture-guardrails.md). Guard kiểm tra `import`, `export`, `part`, relative URI và package URI; `app/` là composition root còn các layer bên trong không được bypass boundary bằng dependency trực tiếp tới implementation bên ngoài.

Dependency direction mục tiêu:

```text
features ─────────> application
   |                    |
   └────────────────────v
                      domain
                        ^
                        |
infrastructure ----------┘

app = composition root / wiring
core = shared primitives có scope thật sự cross-cutting
```

### 7.1 Domain không phụ thuộc framework

Domain core không được biết:

- Flutter widget;
- BuildContext;
- Isar;
- SQLite;
- Dio;
- media_kit;
- Android;
- iOS;
- Windows;
- QuickJS.

Domain chỉ mô tả Hikari hiểu "media" và hành vi của nó như thế nào.

---

### 7.2 Implementation có thể thay, contract phải ổn định

Ví dụ:

```text
DatabaseRepository
        ↑
   implementation
        │
   SQLite / Isar / ...
```

Nếu thay DB, UI và domain không nên phải viết lại.

Tương tự:

```text
PlayerEngine
     ↑
media_kit
platform player
future player
```

---

### 7.3 Provider không được leak vào UI

Không:

```text
Widget -> SomeAnimeWebsiteParser
```

Mà:

```text
Widget
  ↓
Application
  ↓
Provider contract
  ↓
Provider implementation
```

---

### 7.4 Database model không phải domain model

DB schema có thể thay đổi vì index, migration hoặc storage optimization.

Domain model chỉ thay đổi khi bản chất sản phẩm thay đổi.

Hai lớp cần mapper rõ ràng.

---

### 7.5 UI là lớp có thể thay thế

Netflix-like UI là định hướng trải nghiệm, không phải kiến trúc core.

Có thể redesign Home mà không thay provider, database hoặc player engine.

---

### 7.6 Platform-specific code được cô lập

Ví dụ:

```text
Flutter
  ↓
Platform abstraction
  ↓
Kotlin / Swift / Windows implementation
```

Business logic không được nằm rải rác trong platform folders.

### 7.7 Application là nơi điều phối workflow

`domain/` mô tả business concepts, invariant và contract. `application/` điều phối nhiều domain capability/repository thành một workflow mà UI có thể gọi.

Ví dụ:

```text
Resume media
→ read progress
→ resolve source
→ load episode/chapter
→ choose engine
→ restore position
```

Logic dạng này không được phình trong Widget, state object hoặc repository implementation.

Không bắt buộc tạo một use case pass-through cho mọi thao tác đơn giản. Use case/application service chỉ xuất hiện khi workflow hoặc reuse thực sự cần nó.

### 7.8 Infrastructure chứa implementation details

Các thành phần có thể thay bởi thư viện, OS hoặc external system nằm dưới `infrastructure/`:

```text
persistence
network
provider implementations
media engines
platform adapters
repository implementations
```

Repository implementation không nằm bên trong một data source cụ thể như `local/`, vì một repository có thể điều phối local DB, cache và remote sync cùng lúc.

### 7.9 `core/` không phải thư mục tiện ích chung

`core/` chỉ chứa primitive thực sự không thuộc domain nào và được dùng xuyên boundary, ví dụ một `Result`/error primitive khi nhu cầu thật xuất hiện.

Không tạo các bucket chung kiểu `helpers/`, `managers/`, `misc/` hoặc abstraction dự phòng chỉ để “có kiến trúc”.

### 7.10 Source/provider ưu tiên capability-oriented contracts

Một source có thể cung cấp một hoặc nhiều capability như search, details, episodes, streams, chapters, pages hoặc text content. Domain không mặc định rằng một provider chỉ thuộc đúng một media type.

API capability cụ thể vẫn là quyết định của Domain Core; Foundation không scaffold trước interface chưa được requirement chứng minh.

---

## 8. Thứ tự phát triển tổng quát

Hikari được xây theo thứ tự móng → tầng trên, nhưng một vertical mỏng có thể được kéo lên sớm để kiểm chứng boundary thật.

```text
0. Foundation v1.1
   ├── pinned toolchain
   ├── quality gates / CI
   ├── machine-enforced architecture boundaries
   └── CI supply-chain hardening
          ↓
1. Domain Core
   ├── Media
   ├── Progress
   ├── Library
   └── Source capabilities/contracts
          ↓
2. Persistence Foundation
          ↓
3. Infrastructure Foundation
          ↓
4. Provider / Source Engine
          ↓
5. Media Engines
   ├── Video
   ├── Manga
   └── Novel
          ↓
6. Application Workflows / Use Cases
          ↓
7. UI Foundation
          ↓
8. Feature Verticals
          ↓
9. Extensions / Advanced Platform
          ↓
10. Hardening
```

### Quy tắc

Không đi sâu vào một tầng chưa đến lượt trừ khi cần nghiên cứu để tránh khóa kiến trúc sai.

Mỗi phase chỉ xây **mức tối thiểu đã được requirement hiện tại chứng minh cần thiết**. Không hoàn thiện trước một framework/domain abstraction tổng quát chỉ vì roadmap cho thấy phase đó sẽ tồn tại.

Các foundation phase phải hướng tới một **walking skeleton** có thể chạy end-to-end sớm: một luồng nhỏ dùng mock/local/legal provider đi qua contract thật, persistence thật khi đã cần, application logic và UI tối thiểu. Kết quả của vertical đó được dùng để điều chỉnh boundary trước khi mở rộng subsystem.

`Feature Verticals` là cách kiểm chứng kiến trúc, không phải phần thưởng chỉ được bắt đầu sau khi mọi engine đã hoàn thiện. Có thể kéo một vertical mỏng lên sớm khi cần chứng minh rằng các contract hiện tại thực sự dùng được.

Mỗi phase phải để lại một checkpoint build/test được trước khi sang phase tiếp theo.

---

## 9. Feature verticals dự kiến

Khi core đã đủ, phát triển theo lát hoàn chỉnh thay vì xây toàn bộ subsystem cùng lúc.

### Vertical A — Video basic

```text
Search
→ Details
→ Episode/Movie
→ Play
→ Save progress
```

### Vertical B — Manga basic

```text
Search
→ Details
→ Chapters
→ Reader
→ Save progress
```

### Vertical C — Novel basic

```text
Search
→ Details
→ Chapters
→ Reader
→ Save progress
```

### Vertical D — Library

```text
Add
→ Library
→ Resume
→ Categories
```

Sau khi các vertical cơ bản chạy ổn mới tăng complexity.

---

## 10. MVP dự kiến

MVP không có nghĩa là "đủ mọi feature".

MVP nên chứng minh được kiến trúc.

Một MVP hợp lý cần có:

- Home đơn giản;
- Search;
- Details;
- ít nhất một provider hợp pháp/demo cho từng capability đang test;
- video playback;
- manga reader;
- novel reader;
- library;
- progress/history;
- persistence;
- Android và Windows chạy được;
- iOS compatibility được giữ ở code/package level nếu có thể.

Không bắt buộc trong MVP:

- plugin marketplace;
- APK extension compatibility;
- Android TV;
- casting;
- watch party;
- cloud sync;
- nhiều tracker;
- sophisticated download manager;
- advanced subtitle translation.

---

## 11. Những thứ chưa được quyết định

Các mục sau phải được nghiên cứu khi đến phase tương ứng:

### Domain

- inheritance hay composition cho Media;
- canonical identity;
- relation giữa metadata item và provider item;
- episode/chapter numbering;
- multi-edition / multi-language model.

### Persistence

- Drift/SQLite;
- Isar;
- schema migration strategy;
- backup format.

### State management

- Riverpod;
- Bloc;
- hoặc lựa chọn khác.

Chưa cần chốt trước UI/Application phase.

### Networking

- Dio;
- package khác;
- request middleware;
- cookies;
- WebView integration.

### Player

Walking skeleton local Android dùng media_kit; xem [LOCAL_MEDIA](architecture/LOCAL_MEDIA.md).
Lựa chọn engine cho các capability nâng cao vẫn chưa chốt.

### Extension runtime

- JavaScript engine;
- security model;
- compatibility adapters.

Quy tắc:

> Không chọn công nghệ chỉ vì một project khác đang dùng nó. Chọn khi requirement của phase đã rõ.

---

## 12. Non-goals hiện tại

Để tránh scope creep, Hikari hiện không đặt mục tiêu:

- tự host kho phim, manga hoặc novel;
- xây CDN/video hosting platform;
- xây social network;
- xây recommendation AI ngay từ đầu;
- hỗ trợ mọi platform ngay phiên bản đầu;
- tương thích toàn bộ extension ecosystem của mọi app từ ngày đầu;
- clone UI chính xác của Netflix/Mihon/Dantotsu.

Các project khác là nguồn tham khảo, không phải specification của Hikari.

---

## 13. Pháp lý và nguồn nội dung

Hikari core nên được thiết kế độc lập với nội dung vi phạm bản quyền.

Khi phát triển và test:

- ưu tiên API chính thức;
- public-domain content;
- local media;
- user-owned content;
- demo/mock providers;
- nguồn mà người dùng có quyền truy cập.

Provider system là cơ chế kỹ thuật, không đồng nghĩa Hikari phân phối hoặc sở hữu nội dung của provider.

---

## 14. Tiêu chí kỹ thuật cấp cao

Hikari nên hướng tới:

### Reliability

- app crash không làm mất library/progress;
- migration DB an toàn;
- source lỗi không làm app lỗi toàn cục;
- player failure có error state rõ ràng.

### Performance

- startup hợp lý;
- scroll mượt;
- manga reader không giữ quá nhiều decoded image trong RAM;
- tránh unnecessary rebuild;
- network/cache có giới hạn.

### Maintainability

- domain độc lập;
- dependency direction rõ ràng;
- subsystem giao tiếp qua contracts;
- feature có test được;
- platform code được cô lập.

### Portability

Một capability không được phụ thuộc Android nếu không thật sự cần.

---

## 15. Thuật ngữ thống nhất

### Media

Một nội dung cấp cao mà người dùng có thể xem hoặc đọc.

Ví dụ:

```text
Movie
Anime Series
Manga
Novel
```

### Provider / Source

Thành phần biết cách tìm và lấy nội dung từ một data source cụ thể.

### Engine

Subsystem xử lý một loại chức năng kỹ thuật lớn.

Ví dụ:

```text
VideoEngine
MangaReaderEngine
NovelReaderEngine
DownloadEngine
ProviderEngine
```

### Domain Model

Representation chuẩn bên trong Hikari, độc lập với API/DB/UI.

### DTO

Dữ liệu dùng để giao tiếp với external API/provider/storage.

### Progress

Trạng thái hiện tại của người dùng trong một media item.

### Library

Danh sách nội dung người dùng chủ động lưu/theo dõi.

---

## 16. Trạng thái hiện tại

### Hoàn thành

- Flutter SDK/toolchain setup;
- Foundation v1.1 baseline (FVM pin, quality gates, architecture guardrails, smoke test, CI);
- architecture direction (`app/core/domain/application/infrastructure/features`);
- Windows toolchain;
- Android toolchain;
- Android build/run trên thiết bị thật;
- Windows build/run;
- Git workflow standard;
- Claude Code project instructions and skill-routing policy.

### Walking skeleton đã có code

- Android local folder scan qua SAF, phân loại Anime/Manga/Light Novel;
- một local root được nhớ qua restart bằng persisted SAF grant + Android-native selection state;
- model Media + source reference tối thiểu với `SourceId` opaque, chưa có canonical identity;
- video playback bằng media_kit, image reader và UTF-8 text reader;
- cần xác minh end-to-end trên thiết bị Android với bộ file thật; chi tiết boundary,
  giới hạn và checklist tại [LOCAL_MEDIA](architecture/LOCAL_MEDIA.md).

### Tiếp theo

Xác minh local-media vertical trên thiết bị, rồi mở rộng domain theo nhu cầu thực tế:
canonical identity, Progress, Library và source capabilities chưa được slice này giải quyết.

### Chưa bắt đầu

- library/progress persistence;
- provider engine tổng quát;
- production player/readers;
- application layer;
- production UI;
- extension runtime.

---

## 17. Nguyên tắc thảo luận trong tương lai

Khi bàn một thay đổi mới, trước tiên xác định nó thuộc lớp nào.

Ví dụ:

```text
"Thêm hỗ trợ 10-bit video"
→ Video Engine

"Thêm manga vào Favorites"
→ Domain + Library use case

"Đổi Isar thành SQLite"
→ Persistence

"Đổi layout Home"
→ UI

"Thêm JS extension"
→ Provider/Extension Engine
```

Sau đó chỉ nghiên cứu sâu phần liên quan.

Không mở lại toàn bộ quyết định kiến trúc nếu thay đổi không tác động đến tầng đó.

---

## 18. Tài liệu liên quan

```text
Hikari/
├── CLAUDE.md                 # agent routing + Definition of Done
└── docs/
    ├── README.md             # documentation index/routing
    ├── PROJECT_OVERVIEW.md   # product source of truth
    ├── GIT_WORKFLOW.md       # Git/process source of truth
    └── decisions/
        ├── ADR-001-hybrid-layered-architecture.md
        ├── ADR-002-foundation-v1.md
        └── ADR-003-architecture-guardrails.md
```

Trong tương lai có thể bổ sung `docs/architecture/` cho thiết kế subsystem và `docs/roadmap/` khi cần theo dõi execution chi tiết. Không tạo trước tài liệu chưa có nội dung thực tế chỉ để đủ cấu trúc.

---

## 19. Quy tắc cập nhật tài liệu này

Cập nhật `PROJECT_OVERVIEW.md` khi:

- thêm hoặc bỏ một nhóm sản phẩm lớn;
- thay đổi platform mục tiêu;
- thay đổi nguyên tắc kiến trúc cấp cao;
- thay đổi roadmap cấp phase;
- một mục "chưa chốt" được quyết định chính thức.

Các quyết định kỹ thuật cụ thể hơn nên đi vào ADR hoặc tài liệu subsystem tương ứng thay vì làm file này ngày càng chi tiết.

---

Hikari ưu tiên **móng ổn định, boundary rõ ràng và phát triển theo từng phase**, thay vì cố xây mọi tính năng ngay từ đầu.
