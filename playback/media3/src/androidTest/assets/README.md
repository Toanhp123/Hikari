# Playback fixture

`fixture.mp4` is a generated 30-second silent blue 640x360 H.264 video, 10 fps,
yuv420p, MP4 fast-start. It contains no third-party content or user data.

Reproduce with FFmpeg:

```sh
ffmpeg -f lavfi -i color=c=blue:s=640x360:r=10 -t 30 -c:v libx264 -pix_fmt yuv420p -movflags +faststart fixture.mp4
```

The original 64x64 fixture failed output-port configuration on the Redmi Note 9S
Qualcomm AVC decoder despite advertised format support. Use a conventional video
size for the service-lifecycle acceptance test. Host decoding alone does not prove
device decoder compatibility; connected acceptance remains required.

## Task 11 reuse

The same self-owned `fixture.mp4` is reused by `scripts/task11-process-death.{sh,ps1}`.
The harness copies it with `adb push` to `/sdcard/Download/HikariTask11/task11-fixture.mp4`
before Phase A so the user can grant the folder through the real DocumentsUI/SAF picker.
The app never downloads the fixture and no production storage bypass is introduced.
