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
