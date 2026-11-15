# Local AI Studio — Galaxy A56

Lokale KI-App für das **Samsung Galaxy A56 (Exynos 1580)**: Chat- und Coding-Assistent mit
GGUF-Modellen, Modell-Browser für **Hugging Face** und **Civitai** (inkl. LoRA-Adaptern,
NSFW-/Abliterated-Modellen hinter Altersfreigabe), optionaler Gemini-Cloud-Fallback und
einer Oberfläche im Stil von ChatGPT/Claude.

> **Ehrlicher Status:** Text-/Coding-Inferenz läuft echt über vendored **llama.cpp** auf
> CPU/NEON. NPU und GPU werden vollständig erkannt, führen in diesem Build aber noch keine
> Transformer-Inferenz aus. Bildgenerierung läuft über die optionale Gemini-API; lokale
> Diffusions-, Video- und Musik-Runtimes sind vorbereitet, aber nicht implementiert.
> Details in der Statusmatrix unten.

---

## Statusmatrix

| Bereich | Status | Umsetzung |
|---|---|---|
| Chat / Coding / Reasoning (GGUF) | ✅ implementiert | llama.cpp `b6390`, Q4_K_M, Streaming, Stop, Chat-Templates (ChatML, Llama 3, Gemma, Phi) |
| Modell-Downloads | ✅ implementiert | Hugging Face direkt mit Resume (`.part`), Civitai mit API-Token |
| Modell-Suche | ✅ implementiert | HF-API (`filter=gguf|lora`), Civitai-API (`types=Checkpoint,LORA`), NSFW-Filter |
| LoRA-Adapter | ✅ implementiert | GGUF-LoRA über `llama_adapter_lora_init` + `llama_set_adapter_lora`, Skalierung 0–1.5 |
| NSFW / Abliterated / Uncensored | ✅ implementiert | 18+-Bestätigung, Toggle, Suche nach `abliterated`, `uncensored`, Civitai-NSFW |
| NPU-Erkennung | ✅ implementiert | NNAPI-Geräte via `libneuralnetworks.so`, Namen/Typ, Exynos-TOPS-Tabelle |
| GPU-Erkennung | ✅ implementiert | Vulkan-Instanz, Device-Name (Xclipse 540), API-Version, RT-/IntDot-Extensions |
| NPU-Inferenz (TFLite/NNAPI) | ❌ offen | Graph-Konvertierung + Delegate-Anbindung fehlen |
| Vulkan-Compute-Shader | ❌ offen | MatMul/Conv2D/Attention-Kernel fehlen |
| Lokale Bildgenerierung (SD 1.5) | ❌ offen | Katalog + Download vorhanden, Runtime fehlt |
| Lokale Video-/Musikgenerierung | ❌ offen | Katalog + Download vorhanden, Runtime fehlt |
| Cloud-Chat + Cloud-Bild | ✅ implementiert | Gemini API-Key (`gemini-2.5-flash`, `-pro`, `gemini-2.5-flash-image`) |

Der APK-Build selbst wurde verifiziert (58 MB, `libgalaxya56.so` 7 MB, 9 JNI-Exporte,
`libc++_shared.so` korrekt gebunden). **Auf einem physischen Gerät wurde nicht getestet**,
weil beim Build kein Gerät per `adb` verbunden war.

---

## Funktionen der App

- **Chat-Oberfläche** wie ChatGPT/Claude: Verlauf-Schublade, Streaming, Codeblöcke mit
  Copy-Button, Stop, Modell- und System-Prompt-Auswahl, Token-/tok-s-Anzeige.
- **Coding-Modus** über Qwen2.5-Coder (1.5B/3B) mit passendem System-Prompt.
- **Studio**: Bild-, Video- und Musik-Tab mit Prompt-Feldern, Modellkatalog und
  Cloud-Bildgenerierung.
- **Modelle**: installierte Dateien, Katalog, Hugging-Face-Suche, Civitai-Suche,
  LoRA-Tab, Fortschritt/Abbrechen/Löschen.
- **Optionen**: Hardware-Karte, Backend-Reihenfolge (NPU → GPU → CPU), Kontext, Threads,
  Temperatur, Top-P, max. Tokens, System-Prompt-Bibliothek, Cloud-Key, HF-/Civitai-Token,
  NSFW-Freigabe, LoRA-Stärke.
- **Hardware-Probe** (nativ): CPU-Kerne (Performance/Efficiency über MIDR), Max-Takt,
  RAM, Thermalzonen, Vulkan-GPU, NNAPI-NPU, SoC-TOPS.

---

## Build

Voraussetzungen:

- JDK 17
- Android SDK mit **Platform 36**, **Build-Tools 36.0.0**, **NDK 27.2.12479018**, **CMake 3.22.1**
- Windows, Linux oder macOS; Internet für den ersten Gradle-Lauf

```bash
# SDK-Pfad (Windows, Forward-Slashes vermeiden AGP-Path-Bugs)
echo "sdk.dir=C:/Users/<user>/Android/Sdk" > local.properties

./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Release (unsigniert, eigenes Keystore nötig):

```bash
./gradlew assembleRelease
```

Technik: Gradle 8.11.1 (Wrapper), AGP 8.9.2, Kotlin 2.0.21, Compose BOM 2025.01.00,
`compileSdk`/`targetSdk` 36, `minSdk` 28, nur `arm64-v8a`.

### Warum llama.cpp vendored ist

`app/src/main/cpp/third_party/llama.cpp` liegt als Quellcode bei (MIT), damit der Build
ohne Submodule und ohne Netzwerk reproduzierbar ist. Es werden nur `src/`, `ggml/`,
`include/`, `cmake/`, Lizenz und README benötigt; Tests, Beispiele, Tools und
Beispiel-Vokabulare sind entfernt.

---

## Architektur

```
MainActivity
└── AppRoot (Drawer + Bottom-Nav)
    ├── ChatScreen ── AppViewModel ── PromptBuilder
    ├── StudioScreen ─┤                ├── LocalInference (JNI)
    ├── ModelsScreen ─┤                ├── GeminiClient (optional)
    └── SettingsScreen┘                └── ModelStore (HF/Civitai, Resume)
                                            │
Native (C++17, arm64-v8a)                    │
├── hardware_detector.cpp  Vulkan / NNAPI / procfs / properties
├── llama_engine.cpp       llama.cpp, Streaming, Cancel, LoRA
└── jni_bridge.cpp         9 JNI-Methoden, JSON-Transport
```

Datenfluss Chat: `AppViewModel.send()` → Prompt mit Chat-Template → `nativeGenerate`
(blockierend auf `Dispatchers.IO`) → Token-Callback → Compose-State → Streaming-Bubble.

---

## Modellquellen

Kuratierte, geprüfte Liste in [`docs/MODELS.md`](docs/MODELS.md). In der App lassen sich
zusätzlich beliebige GGUF- und LoRA-Dateien über die Suche laden.

- **Hugging Face**: direkte `resolve/main`-URLs, optionaler Token für gated/private Repos.
- **Civitai**: Checkpoints/LoRAs, Download-Token nötig; NSFW nur nach 18+-Bestätigung.
- **Abliterated/Uncensored**: über Suchbegriffe auffindbar, keine Cloud-Umgehung.

> Für den lokalen Betrieb auf 8 GB RAM: 1B–3B-Modelle in Q4_K_M. 3B belegt ~2 GB Datei
> plus ~1–2 GB Kontext/KV-Cache. 7B/8B sind auf dem A56 technisch möglich, aber langsam
> und RAM-kritisch.

---

## Sicherheit & Credentials

- Es werden **keine Kontopasswörter** gespeichert oder verwendet. Cloud-Zugriff läuft
  ausschließlich über einen **Google-AI-Studio-API-Key**.
- Ein Google-AI-Pro-Abo lässt sich nicht per E-Mail/Passwort in Dritt-Apps nutzen.
  Key erstellen: <https://aistudio.google.com/apikey>.
- API-Keys und HF-/Civitai-Tokens liegen in `SharedPreferences` (nicht verschlüsselt).
  Für maximale Sicherheit nur eigene Geräte nutzen und Tokens widerrufen, wenn nötig.
- Modell-Downloads laufen über HTTPS; die App hat keine Telemetrie.

---

## Offene Arbeiten

1. NPU-Pfad: TFLite-Modelle nach NNAPI konvertieren, Samsung-Delegate binden, INT8-Kalibrierung.
2. Vulkan-Compute: Shader für MatMul, Conv2D, Attention, GELU/SiLU; Vulkan-Backend in
   llama.cpp für Adreno/Xclipse aktivieren (benötigt `glslc` im Build).
3. Diffusion-Runtime: SD-1.5-Pipeline (UNet/VAE/CLIP) in C++ oder ONNX Runtime Mobile.
4. Audio-Runtime: MusicGen / Stable Audio Open Small, Whisper-ggml, TTS.
5. Gerätetests: Ladezeiten, tok/s, Thermik, Speicherverbrauch auf echter Hardware messen.

---

## Lizenz

MIT — siehe [`LICENSE`](LICENSE). llama.cpp steht unter MIT, siehe
`app/src/main/cpp/third_party/llama.cpp/LICENSE`.
