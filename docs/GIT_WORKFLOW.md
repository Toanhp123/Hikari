# Hikari Git Workflow

> Status: **Project standard**
>
> Mục tiêu của tài liệu này là giữ lịch sử Git của Hikari dễ đọc, dễ rollback, dễ review và tránh để các thay đổi thử nghiệm làm hỏng nhánh ổn định.

## 1. Nguyên tắc cốt lõi

Hikari sử dụng mô hình:

```text
main
└── dev
    ├── feat/...
    ├── fix/...
    ├── refactor/...
    ├── perf/...
    ├── test/...
    ├── docs/...
    └── chore/...
```

Quy tắc bắt buộc:

- `main` là nhánh ổn định, luôn phải build được.
- `dev` là nhánh tích hợp cho phiên bản đang phát triển.
- Không phát triển tính năng trực tiếp trên `main`.
- Không phát triển tính năng trực tiếp trên `dev`.
- Mỗi mục tiêu độc lập phải được thực hiện trên một branch riêng.
- Mỗi phase nền móng phải có checkpoint commit rõ ràng trước khi chuyển sang phase tiếp theo.
- Mọi thay đổi code hoặc file do ChatGPT thực hiện phải kèm patch/diff để có thể review hoặc áp dụng lại.

## 2. Vai trò của các branch

### `main`

`main` chỉ chứa trạng thái đã được kiểm tra và đủ ổn định để coi là một mốc phát hành hoặc checkpoint lớn.

Không commit trực tiếp vào `main`.

Các thay đổi vào `main` đi từ:

```text
dev -> main
```

Trước khi merge vào `main`:

```bash
flutter analyze
flutter test
flutter build apk --debug
flutter build windows --debug
```

Nếu một platform chưa thể build trên máy hiện tại, phải ghi rõ platform nào chưa được xác minh.

### `dev`

`dev` là nơi tích hợp các feature/fix đã hoàn thành.

Không phát triển trực tiếp trên `dev`, ngoại trừ những thay đổi quản trị cực nhỏ và có chủ đích như sửa typo trong tài liệu workflow.

Luồng thông thường:

```text
feature branch
      ↓
     dev
      ↓
    main
```

### Feature branches

Mỗi branch chỉ nên có một mục tiêu chính.

Ví dụ:

```text
feat/domain-core
feat/persistence
feat/provider-runtime
feat/video-player
feat/manga-reader
feat/novel-reader

fix/android-player-resume
fix/library-progress

refactor/media-model
perf/image-cache
test/domain-models
docs/architecture
chore/update-flutter
```

Không tạo branch quá rộng như:

```text
feat/everything
dev-new
work
test123
```

## 3. Quy ước tên branch

Các prefix được phép:

| Prefix | Dùng cho |
| --- | --- |
| `feat/` | Tính năng hoặc capability mới |
| `fix/` | Sửa lỗi |
| `refactor/` | Tái cấu trúc, không đổi hành vi mong muốn |
| `perf/` | Tối ưu hiệu năng |
| `test/` | Thêm hoặc sửa test |
| `docs/` | Tài liệu |
| `chore/` | Tooling, dependency, cấu hình, housekeeping |
| `ci/` | CI/CD |
| `hotfix/` | Sửa lỗi khẩn cấp từ `main` |

Tên branch:

- viết thường;
- dùng dấu `-`;
- ngắn nhưng mô tả đúng mục tiêu;
- không dùng tên người hoặc số thứ tự vô nghĩa.

Ví dụ tốt:

```text
feat/domain-core
fix/manga-reader-cache
refactor/provider-registry
```

## 4. Quy ước commit

Hikari dùng Conventional Commit ở mức đơn giản:

```text
<type>: <mô tả ngắn>
```

Các type chính:

```text
feat
fix
refactor
perf
test
docs
chore
ci
```

Ví dụ:

```text
feat: add canonical media entity
feat: add source capability model
fix: restore reading progress correctly
refactor: simplify provider registry
perf: bound decoded image cache
test: cover media identifier equality
docs: document git workflow
chore: configure Android build
```

Quy tắc:

- commit phải mô tả một thay đổi logic tương đối độc lập;
- tránh commit kiểu `update`, `changes`, `fix stuff`;
- không nhét nhiều subsystem không liên quan vào một commit;
- không commit generated build output, secret, local cache hoặc file máy cá nhân.

## 5. Bắt đầu một công việc mới

Luôn bắt đầu từ `dev` mới nhất:

```bash
git checkout dev
git pull --ff-only

git checkout -b feat/<ten-cong-viec>
```

Ví dụ:

```bash
git checkout dev
git pull --ff-only
git checkout -b feat/domain-core
```

`--ff-only` giúp tránh vô tình tạo merge commit chỉ vì local branch bị lệch.

## 6. Trong quá trình làm việc

Kiểm tra thường xuyên:

```bash
git status
git diff
```

Trước mỗi commit:

```bash
git diff --check
flutter analyze
```

Nếu thay đổi có test liên quan:

```bash
flutter test
```

Sau đó:

```bash
git add <cac-file-lien-quan>
git commit -m "feat: ..."
```

Ưu tiên `git add <file>` thay vì `git add .` khi thay đổi đang lẫn nhiều việc chưa hoàn tất.

## 7. Đồng bộ branch với `dev`

Trước khi merge feature, cập nhật `dev`:

```bash
git checkout dev
git pull --ff-only

git checkout feat/<ten-cong-viec>
git rebase dev
```

Nếu có conflict:

```bash
# sửa conflict

git add <file-da-sua>
git rebase --continue
```

Nếu muốn hủy rebase:

```bash
git rebase --abort
```

Không dùng `git push --force` trên `main` hoặc `dev`.

Nếu feature branch đã được push và cần cập nhật sau rebase:

```bash
git push --force-with-lease
```

Chỉ dùng `--force-with-lease` cho branch cá nhân/feature của chính công việc đó.

## 8. Merge feature vào `dev`

Trước khi merge:

```bash
flutter analyze
flutter test
```

Nếu thay đổi ảnh hưởng Android hoặc Windows, nên chạy target tương ứng.

Khuyến nghị trên GitHub:

```text
feature branch -> Pull Request -> dev
```

Với feature branch, ưu tiên **Squash and merge** để `dev` giữ lịch sử gọn:

```text
feat: establish domain core
```

thay vì mang theo hàng loạt commit thử nghiệm.

Sau khi merge:

```bash
git checkout dev
git pull --ff-only
git branch -d feat/<ten-cong-viec>
```

Có thể xóa remote branch:

```bash
git push origin --delete feat/<ten-cong-viec>
```

## 9. Promote `dev` lên `main`

Khi một phase hoặc phiên bản đạt trạng thái ổn định:

```text
dev -> Pull Request -> main
```

Không squash toàn bộ lịch sử `dev` thành một commit duy nhất.

Ưu tiên **Create a merge commit** để lưu rõ boundary của một lần promote/release.

Ví dụ:

```text
Merge dev into main for domain foundation
```

Sau merge, tạo tag nếu đây là mốc đáng lưu:

```bash
git tag -a v0.1.0 -m "Hikari foundation"
git push origin v0.1.0
```

Không cần tag cho mọi thay đổi nhỏ.

## 10. Hotfix

Nếu `main` có lỗi cần sửa ngay:

```bash
git checkout main
git pull --ff-only
git checkout -b hotfix/<ten-loi>
```

Sửa và kiểm tra xong, merge hotfix vào `main`.

Sau đó **bắt buộc đưa cùng thay đổi trở lại `dev`** để hai nhánh không phân kỳ:

```text
hotfix -> main
   └──> dev
```

## 11. Quy tắc kiểm tra trước merge

Tối thiểu:

```bash
git diff --check
flutter analyze
flutter test
```

Với thay đổi platform:

```bash
flutter run -d <device>
```

hoặc build:

```bash
flutter build apk --debug
flutter build windows --debug
```

Không merge code đang:

- fail analyzer;
- fail test liên quan;
- không build trên platform bị ảnh hưởng;
- chứa secret/API key;
- chứa code debug tạm thời mà không có lý do giữ lại.

## 12. Patch/diff là một phần của workflow

Mọi thay đổi code hoặc file do ChatGPT thực hiện cho Hikari phải đi kèm patch/diff.

Mục tiêu:

- review chính xác file nào thay đổi;
- có thể áp dụng lại thay đổi;
- dễ rollback;
- tránh chỉnh sửa âm thầm ngoài phạm vi task.

Patch nên được tạo từ trạng thái trước thay đổi sang trạng thái sau thay đổi.

Ví dụ:

```bash
git diff > patches/domain-core.patch
```

Nếu thay đổi chưa được Git track, có thể dùng:

```bash
git diff --no-index <old> <new>
```

Khi ChatGPT cung cấp file đã chỉnh sửa, phải cung cấp cả:

```text
1. file/kết quả mới
2. patch/diff tương ứng
3. tóm tắt thay đổi
4. các kiểm tra đã chạy
```

## 13. Checkpoint theo roadmap Hikari

Các mốc lớn dự kiến:

```text
chore: bootstrap Flutter project
        ↓
feat: establish domain core
        ↓
feat: establish core contracts
        ↓
feat: add persistence foundation
        ↓
feat: add infrastructure foundation
        ↓
feat: add provider engine
        ↓
feat: add media engines
        ↓
feat: add application use cases
        ↓
feat: add UI foundation
```

Mỗi mốc phải tồn tại như một trạng thái build/test được trước khi tiếp tục đặt tầng tiếp theo.

## 14. Những điều không làm

Không:

```text
commit trực tiếp feature vào main
commit trực tiếp feature vào dev
force push main
force push dev
merge code chưa chạy analyzer
trộn nhiều mục tiêu không liên quan vào một branch
đổi kiến trúc lớn mà không có lý do/documentation
commit .env, token, secret, signing key
commit build/, .dart_tool/ hoặc IDE cache
```

## 15. Workflow hằng ngày

Bắt đầu:

```bash
git checkout dev
git pull --ff-only
git checkout -b feat/<task>
```

Làm việc:

```bash
git status
git diff
flutter analyze
flutter test
```

Commit:

```bash
git add <files>
git commit -m "feat: ..."
git push -u origin feat/<task>
```

Hoàn tất:

```text
Pull Request
feature -> dev
Squash and merge
```

Khi một phase ổn định:

```text
Pull Request
dev -> main
Create a merge commit
```

---

Tài liệu này là chuẩn mặc định của Hikari. Nếu workflow cần thay đổi về sau, thay đổi đó phải được cập nhật vào chính tài liệu này trước hoặc cùng lúc với việc áp dụng quy trình mới.
