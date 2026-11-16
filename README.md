# Local AI Studio — Galaxy A56

Lokale KI-App für das **Samsung Galaxy A56 (Exynos 1580)**: Chat- und Coding-Assistent mit
GGUF-Modellen, Modell-Browser für **Hugging Face** und **Civitai** (inkl. LoRA-Adaptern,
NSFW-/Abliterated-Modellen hinter Altersfreigabe), optionaler Gemini-Cloud-Fallback und
einer Oberfläche im Stil von ChatGPT/Claude.

> **Ehrlicher Status (auf SM-A566B verifiziert):** GGUF-Inferenz läuft echt über
> **llama.cpp** auf CPU/NEON **und** über den **Vulkan-Backend auf der Xclipse 540**
> (Qwen2.5-0.5B Q4_K_M: 10,3 tok/s mit 89 GPU-Layern). Die NPU ist über NNAPI **nicht**
> exponiert, aber über Samsungs öffentliche **ENN-Vendor-API** erreichbar: Der
> geräteeigene MIDAS-4×-Upscaler läuft auf der NPU (256→1024 in ~0,2 s). Lokale
> Bildgenerierung läuft über **stable-diffusion.cpp** (SD1.5, CPU/Vulkan). Details unten.

---

## Statusmatrix

| Bereich | Status | Umsetzung |
|---|---|---|
| Chat / Coding / Reasoning (GGUF) | ✅ implementiert | llama.cpp `b6390`, Q4_K_M, Streaming, Stop, Chat-Templates (ChatML, Llama 3, Gemma, Phi) |
| Modell-Downloads | ✅ implementiert | Hugging Face direkt mit Resume (`.part`), Civitai mit API-Token |
| Modell-Suche | ✅ auf Gerät verifiziert | Unified **Entdecken**: HF + Civitai parallel, automatische Suche, Quantisierungs-Badges, LLM/Bild/LoRA, alle Dateiformate, `abliterated`/`uncensored`/NSFW nur hinter 18+-Gate |
| LoRA-Adapter | ✅ implementiert | GGUF-LoRA über `llama_adapter_lora_init` + `llama_set_adapter_lora`, Skalierung 0–1.5 |
| NSFW / Abliterated / Uncensored | ✅ implementiert | 18+-Bestätigung, Toggle, Suche nach `abliterated`, `uncensored`, Civitai-NSFW |
| NPU über ENN (Samsung) | ✅ auf Gerät verifiziert | Öffentliche ENN-API (`libenn_public_api_cpp.so`) dynamisch geladen; MIDAS-4×-NPU-Upscaler 256→1024 in **189–259 ms**, HumanSeg 288×288 in **6 ms**. Keine proprietären Binaries im Repo. |
| NNAPI-Probe | ⚠️ Treiber limitiert | ADD-Graph kompiliert, Ausführung auf `nnapi-reference` endet mit Code 4. Kein NPU-Accelerator über NNAPI auf diesem ROM. |
| GPU-Erkennung | ✅ implementiert | Vulkan-Instanz, „Samsung Xclipse 540“, API 1.3, RT-/IntDot, 4 Compute-CUs |
| GPU-Inferenz (Vulkan) | ✅ auf Gerät verifiziert | llama.cpp `GGML_VULKAN=ON`, GPU-Layer-Offload, 10,3 tok/s (Qwen2.5-0.5B Q4_K_M) |
| Lokale Bildgenerierung (SD 1.5 / SD-Turbo) | ✅ SD1.5, ⚠️ SD-Turbo | `stable-diffusion.cpp` als separate `libstable-diffusion.so`; SD1.5 läuft, SD-Turbo lädt und rechnet, liefert mit diesem GGUF-Export aber nur Rauschen (Zero-Terminal-SNR-Metadaten fehlen). Modellauswahl, CFG, Steps, **VAE-Tiling** (512×512 getestet). |
| ControlNet (SD1.5) | ✅ auf A56 verifiziert | Canny-ControlNet (`control_v11p_sd15_canny.pth`, 1,4 GB) geladen, 256×256/4 Steps/LCM/CFG 1,5 in **25,1 s** auf Vulkan; Control-Bild, Stärke 0,9 und Typ `canny` landen in JSON/PNG. |
| Lokale Musik (Progressive Psytrance) | ✅ auf A56 verifiziert | Lokaler DSP-Renderer `PsytranceEngine`: 44,1 kHz Stereo-WAV, 138 BPM, **4:30** (270 s), 46 MB, Mean −14,3 dB / Peak −3,3 dB, ohne Cloud. Kein neuronales Musikmodell. |
| Lokale Videogenerierung | ⚠️ Katalog/experimentell | AnimateDiff-/Wan-Kandidaten und Downloads vorhanden; in diesem Build keine Video-Runtime und keine MP4-Ausgabe. |
| Bild-Extensions / Tutorials | ✅ implementiert | ControlNet-Karte, Modell-/Bildauswahl, Stärke, Typ-Chips, Canny; 6-seitiges Handbuch mit Checklisten und Werkstatt-Notizen. |
| Speicher, ZRAM & USB-C | ✅ Diagnose/Import | Liest RAM, freien App-Speicher, `/proc/swaps`, ZRAM und Wechselmedien; USB-C/SD-Import per SAF (`OpenDocumentTree`). ZRAM/Swap kann ohne Root nicht aktiviert werden. |
| Sichere Tokens | ✅ gehärtet | AES/GCM im Android Keystore; Klartext-Migration löscht nur nach verifiziertem Rücklesen, bei Keystore-Fehler bleiben alte Werte erhalten. |
| Cloud-Chat + Cloud-Bild | ✅ implementiert | Gemini API-Key (`gemini-2.5-flash`, `-pro`, `gemini-2.5-flash-image`) |

Auf dem **Galaxy A56 (SM-A566B, Android 17)** installiert und getestet:

- Hardware-Probe: 8 Kerne (4P + 4E), 7.416 MB RAM, Xclipse 540 mit Vulkan 1.3.
- CPU-Inferenz: Qwen2.5-0.5B Q4_K_M, 7 Threads → **10,7 tok/s**.
- Vulkan-Inferenz: 89 GPU-Layer → **10,3 tok/s**, Backend „CPU+Vulkan Samsung Xclipse 540“.
- ENN/NPU: MIDAS-4×-Upscaler 256×256→1024×1024 in **189–259 ms**, HumanSeg 288×288 in **6 ms**.
- APK: 79 MB (aktueller Debug-Build inkl. SD-Runtime + ENN); frühere Builds: Vulkan 70,9 MB,
  CPU 62,3 MB.
- **512×512 / SD1.5 Q4_0 / Vulkan**, 8 Steps, CFG 7: **107,1 s** (4 Steps: 85,8 s);
  Peak PSS **3,58 GiB**, Peak GL/Vulkan **3,38 GiB**, `VmHWM` **1,67 GiB**.
- **VAE-Tiling** bei 512×512/8 Steps: **122,4 s**, Peak GL **2,44 GiB** statt 3,38 GiB;
  Peak PSS 3,45 GiB. Thermik: Thermal-Status 2–3, AP bis **52,4 °C**, Skin bis **45,2 °C**.
- **SD-Turbo Q8_0**, 512×512, CFG 1, erzwungene v-Prediction: 1 Step **29,9 s**, 4 Steps **45,2 s**.
  Die Ausgabe war in beiden Fällen nur Rauschen/Brei; das ist **kein gültiger Qualitätsvergleich**.
  Ursache: Der GGUF-Export enthält weder `alphas_cumprod` (Zero-Terminal-SNR) noch `v_pred`-Markierungen.
- **SD1.5 Q4_0** gleicher 4-Step-Lauf mit CFG 1: **44,9 s**, aber sehr weich – CFG 7 bleibt der bessere Default.
- Chat-UI auf Gerät getestet: Qwen2.5-0.5B Q4_K_M, **12,1 tok/s** Streaming-Antwort.

---

## Funktionen der App

- **Chat-Oberfläche** mit lokalem Hero, Verlauf-Schublade, Streaming, Codeblöcken,
  Copy-Button, Stop, Modell-/System-Prompt-Auswahl, Token-/tok-s-Anzeige und
  lokalem/Cloud-Modellstatus.
- **Coding-Modus** über Qwen2.5-Coder (1.5B/3B) mit passendem System-Prompt.
- **Studio**: Bild-, Video- und Musik-Tab mit Prompt-Feldern, Modellkatalog,
  lokaler SD1.5-Generierung (SD-Turbo lädt, Qualität ist mit dem getesteten Export noch unbrauchbar),
  auswählbarem Checkpoint, CFG/Steps/VAE-Tiling, **ControlNet**, **NPU-4×-Upscale**,
  **Progressive-Psytrance-Renderer** (4:30, 44,1 kHz, Wiedergabe) und Cloud-Bildgenerierung.
- **Extensions-Tab**: ControlNet, LCM-LoRA, IP-Adapter, ADetailer, AnimateDiff, Wan,
  MusicGen/ACE-Step, Whisper und LLM-LoRAs mit ehrlichem Status (integriert / kompatibel /
  experimentell / geplant) und Quellenlinks.
- **Handbuch**: sechs interaktive Seiten zu Steps, CFG, Sampler, ControlNet, NPU und
  Speicher; Checklisten, Fortschritt und Werkstatt-Notizen.
- **Civitai-Vorschauen**: Beispielbilder direkt in Treffer-Karten und Detail-Sheets,
  mit speicherschonendem Downsampling; 18+-Inhalte bleiben hinter dem Gate.
- **Modelle → Entdecken**: automatische, gemeinsame Suche auf Hugging Face und Civitai;
  Filter für LLM, Bildmodelle und LoRAs; Quantisierungen (Q2–Q8, FP16 …) direkt an der
  Karte; alle Dateien inklusive LoRA; `abliterated`, `uncensored`, `unliberated` und NSFW
  werden nicht ausgeblendet — nur die 18+-Bestätigung schützt den Zugang.
- **Modelle → LoRA**: Bild- und Sprach-LoRAs, Download nach `loras/`, Anwenden auf GGUF.
- **Optionen**: Hardware-Karte, Backend-Reihenfolge (NPU → GPU → CPU), ENN-/NPU-Test,
  Kontext, Threads, Temperatur, Top-P, max. Tokens, System-Prompt-Bibliothek, Cloud-Key,
  HF-/Civitai-Token, NSFW-Freigabe, LoRA-Stärke, **Speicher-/ZRAM-Diagnose und USB-C-Import**.
- **Design**: eigenes „Field Lab“-Theme (Graphitgrün, Signal-Chartreuse, Serif-Hero,
  Monospace-Metadaten); Designsystem in [`DESIGN.md`](DESIGN.md).
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

./gradlew assembleDebug          # CPU/NEON
./gradlew assembleDebug -Pvulkan=true   # + Vulkan (Host-Compiler nötig)
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Vulkan-Build (Host-Compiler)

llama.cpp generiert die Vulkan-Shader zur Build-Zeit mit `glslc` und einem
**Host-C++-Compiler**. Ohne MSVC/LLVM kann ein portables Zig als Shim verwendet werden:

```bash
# einmalig: .tools/zig-windows-x86_64-0.13.0/zig.exe + .tools/hostbin/clang(.++).exe
export PATH="$PWD/.tools/hostbin:$ANDROID_HOME/cmake/3.22.1/bin:$PATH"
./gradlew assembleDebug -Pvulkan=true
```

`app/build.gradle.kts` übergibt `HOST_C_COMPILER`/`HOST_CXX_COMPILER` automatisch, wenn
`.tools/hostbin/clang.exe` vorhanden ist. `ninja` muss im PATH liegen (SDK-CMake-Bin).
Die Khronos-Header liegen unter `app/src/main/cpp/third_party/vulkan-headers`; der
API-28-Kompatibilitäts-Shim für `vkResetQueryPool` unter `vulkan_android_compat.cpp`.

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
├── hardware_detector.cpp      Vulkan / NNAPI / procfs / properties
├── npu_probe.cpp              NNAPI-ADD-Graph + Geräte-/Latenzbericht
├── enn_engine.cpp             Samsung ENN: NPU-Probe, .nnc-Benchmark, MIDAS-4×-Upscale
├── llama_engine.cpp           llama.cpp, Streaming, Cancel, LoRA, GPU-Layer
├── vulkan_android_compat.cpp  vkResetQueryPool für API < 33
└── jni_bridge.cpp             JNI-Methoden, JSON-Transport
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

> Design und NPU-Recherche: [`DESIGN.md`](DESIGN.md) und
> [`docs/NPU_EXYNOS_1580.md`](docs/NPU_EXYNOS_1580.md).

> Für den lokalen Betrieb auf 8 GB RAM: 1B–3B-Modelle in Q4_K_M. 3B belegt ~2 GB Datei
> plus ~1–2 GB Kontext/KV-Cache. 7B/8B sind auf dem A56 technisch möglich, aber langsam
> und RAM-kritisch.

---

## Sicherheit & Credentials

- Es werden **keine Kontopasswörter** gespeichert oder verwendet. Cloud-Zugriff läuft
  ausschließlich über einen **Google-AI-Studio-API-Key**.
- Ein Google-AI-Pro-Abo lässt sich nicht per E-Mail/Passwort in Dritt-Apps nutzen.
  Key erstellen: <https://aistudio.google.com/apikey>.
- API-Keys und HF-/Civitai-Tokens liegen **AES/GCM-verschlüsselt** im Android Keystore
  (`SecureTokenStore`). Die Alt-Migration löscht Klartext nur, wenn der verschlüsselte
  Wert nach dem Schreiben nachweislich lesbar ist; bei einem Keystore-Fehler bleiben
  die alten Werte erhalten. Für maximale Sicherheit nur eigene Geräte nutzen und
  Tokens widerrufen, wenn nötig.
- Modell-Downloads laufen über HTTPS; die App hat keine Telemetrie.
- ENN: Es werden **keine Samsung-Binaries** mitgeliefert. Die App lädt die öffentliche
  Vendor-Lib (`libenn_public_api_cpp.so`) zur Laufzeit und nutzt die bereits auf dem Gerät
  vorhandenen `.nnc`-Modelle (MIDAS/HumanSeg).

---

## Offene Arbeiten

1. **Video-Runtime**: AnimateDiff/Wan sind im Katalog, aber es gibt noch keine lokale
   Frame-/MP4-Pipeline. Nächster sinnvoller Schritt: 8–16 Frames bei 256², danach
   MediaCodec-Encode und Thermikmessung.
2. **Neuronale Musik**: MusicGen Small / ACE-Step brauchen eine eigene Runtime; der
   Psytrance-Renderer ist bewusst DSP und kein neuronaler Ersatz.
3. **NPU-Diffusion**: Der öffentliche ENN-Compiler und LiteRT/ExecuTorch unterstützen
   den Exynos 1580 (Stand November 2026) nicht. Details, Quellen und Messwerte in
   [`docs/NPU_EXYNOS_1580.md`](docs/NPU_EXYNOS_1580.md).
4. **Bild-Runtime**: SDXL/Flux auf 8 GB RAM, automatische Modell-Empfehlung nach freiem
   RAM und eine echte layerweise CPU/GPU-Aufteilung für SD.
5. **Benchmark-Suite**: Prompt-Länge, Threads, GPU-Layer, Steps/CFG, VAE-Tiling und Thermik
   systematisch und mit Kühlpausen messen; die bisherigen Werte stehen oben.
6. **Vulkan** auf weiteren Geräten testen (Xclipse 540 ist verifiziert, Adreno/Mali offen).

---

## Lizenz

MIT — siehe [`LICENSE`](LICENSE). llama.cpp steht unter MIT, siehe
`app/src/main/cpp/third_party/llama.cpp/LICENSE`; stable-diffusion.cpp ebenfalls unter MIT.
