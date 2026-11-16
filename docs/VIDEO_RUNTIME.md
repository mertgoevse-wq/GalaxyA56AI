# Lokale Video-Runtime auf dem Galaxy A56

Stand: 16. November 2026, SM-A566B (8 GB RAM, Exynos 1580, Xclipse 540).

## Was verifiziert läuft

Die App erzeugt kurze Clips vollständig lokal:

```text
SD1.5-Checkpoint + AnimateDiff-Motion-Modul
  → stable-diffusion.cpp (Vulkan0)
  → 8 PNG-Frames (HWC, 256×256)
  → MediaCodec H.264
  → MediaMuxer MP4
```

Getesteter Lauf:

| Parameter | Wert |
|---|---|
| Basis | `DreamShaper8_LCM_q4_0.gguf` |
| Motion-Modul | `mm_sd_v15_v2.ckpt`, 1,6 GB |
| Auflösung | 256×256 |
| Frames | 8 |
| Sampler / Steps | LCM / 4 |
| CFG | 1,5 |
| Seed | 424242 |
| Backend | Vulkan0 (Xclipse 540) |
| Dauer | **233,3 s** (zweiter Lauf **339,4 s** inkl. Laden/VAE) |
| Ausgabe | 8 PNG-Frames + `clip_424242.mp4` |

`ffprobe` für das MP4:

```text
Video: h264, yuv420p, 256x256, 8 fps
Dauer: 1,000 s
Frames: 8
Größe: 29.368 Bytes
```

Die Frame-Extraktion berücksichtigt das Layout von `sd::Image`: **HWC-interleaved,
frame-major**. Der frühere CWH-Index erzeugte Rauschen; der aktuelle Index ist
`t*C*W*H + (y*W+x)*C + c`. Ergebnis sind zusammenhängende, wenn auch bei 4 LCM-Steps
abstrakte Frames, keine Noise-Bilder.

## Die 8-GB-Grenze

| Einstellung | Ergebnis |
|---|---|
| 8 Frames, 256², 4 Steps | ✅ erfolgreich, 233–339 s, MP4 gültig |
| 16 Frames, 256², gleicher Graph | ❌ nach Motion-Load GPU-Stall; Prozess später vom Android-Low-Memory-Killer beendet (`LOW_MEMORY`, `reason=3`) |
| Wan 2.1 T2V 1.3B | nicht getestet; zusätzlicher Textencoder/VAE und höherer Speicherbedarf |
| SVD XT | nicht getestet; ~9,6 GB Modellgröße |

**Empfehlung:** 8 Frames bei 256² sind der belastbare Default auf 8 GB. 16 Frames
werden in der UI angeboten, aber nicht als zuverlässig markiert. Ein Sliding-Window-
oder Chunking-Ansatz wäre nötig, um den Peak zu senken; das ist in diesem Build nicht
implementiert und wird hier nicht als funktionierend behauptet.

## Reproduktion

Modelle liegen geräteweit unter:

```text
/sdcard/Android/data/com.galaxya56.ai/files/models/mm_sd_v15_v2.ckpt
/sdcard/Android/data/com.galaxya56.ai/files/models/DreamShaper8_LCM_q4_0.gguf
```

Debug-Intent (Prompt ohne Leerzeichen, damit `am start` ihn nicht zerlegt):

```bash
adb shell am start -n com.galaxya56.ai/.ui.MainActivity \
  --es video_test neon \
  --es video_motion /sdcard/Android/data/com.galaxya56.ai/files/models/mm_sd_v15_v2.ckpt \
  --ei video_frames 8 \
  --ei video_steps 4 \
  --es video_sampler lcm
```

Die App schreibt Frames und MP4 in den app-privaten `files/video/`-Bereich; der
Video-Tab zeigt die letzte Ausgabe als `VideoView`. Relevante Logzeilen:

```text
AnimateDiff dispatch: 8 frames, 256x256
SD video done seed=424242 frames=8 fps=8 ms=233288 file=...
```

## Implementierung

- `app/src/main/cpp/image_engine.cpp/.h`: `VideoGenParams`, Motion-Load,
  `sd_generate_video`, Frame-Export.
- `app/src/main/cpp/jni_bridge.cpp`: `nativeImageLoad(..., motionModulePath)`,
  `nativeImageGenerateVideo`.
- `app/src/main/java/com/galaxya56/ai/inference/LocalInference.kt`:
  `VideoGenerateResult`, JNI-Fassade.
- `app/src/main/java/com/galaxya56/ai/data/Mp4Encoder.kt`: MediaCodec/MediaMuxer,
  flexibles NV12/OGLES-Pfad, EOS-Drain ohne Verlust des letzten Frames.
- `app/src/main/java/com/galaxya56/ai/ui/StudioScreen.kt`: Video-Tab, Motion-Picker,
  Frames/Steps/Sampler, `VideoView`.
- `app/src/main/java/com/galaxya56/ai/data/SettingsStore.kt`: persistierte
  Video-Parameter und `npuAutoUpscale`.

## Qualitätsgrenze

Der Lauf beweist die Runtime, nicht die visuelle Qualität. Bei 4 LCM-Steps und dem
kurzen Prompt `neon` sind die Frames abstrakt. Mehr Steps, ein konkreter Prompt und
ein auf AnimateDiff abgestimmtes SD1.5-Modell verbessern das Ergebnis, kosten aber
linear Zeit. Das ist eine Qualitäts-, keine Runtime-Frage.
