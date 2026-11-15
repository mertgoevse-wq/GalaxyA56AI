# Local AI Studio — Galaxy A56

Lokale KI-App für das **Samsung Galaxy A56 (Exynos 1580)**: Chat- und Coding-Assistent mit
GGUF-Modellen, Modell-Browser für **Hugging Face** und **Civitai** (inkl. LoRA-Adaptern,
NSFW-/Abliterated-Modellen hinter Altersfreigabe), optionaler Gemini-Cloud-Fallback und
einer Oberfläche im Stil von ChatGPT/Claude.

> **Ehrlicher Status (auf SM-A566B verifiziert):** GGUF-Inferenz läuft echt über
> **llama.cpp** auf CPU/NEON **und** über den **Vulkan-Backend auf der Xclipse 540**
> (Qwen2.5-0.5B Q4_K_M: 10,3 tok/s mit 89 GPU-Layern). Die NPU ist hardwaremäßig vorhanden,
> wird von diesem Android-ROM aber **nicht als NNAPI-Accelerator exponiert** – NNAPI zeigt
> nur `nnapi-reference` (CPU). Lokale Bildgenerierung ist weiterhin **nicht implementiert**;
> Bilder laufen über die optionale Gemini-API. Details unten.

---

## Statusmatrix

| Bereich | Status | Umsetzung |
|---|---|---|
| Chat / Coding / Reasoning (GGUF) | ✅ implementiert | llama.cpp `b6390`, Q4_K_M, Streaming, Stop, Chat-Templates (ChatML, Llama 3, Gemma, Phi) |
| Modell-Downloads | ✅ implementiert | Hugging Face direkt mit Resume (`.part`), Civitai mit API-Token |
| Modell-Suche | ✅ implementiert | HF-API (`filter=gguf|lora`), Civitai-API (`types=Checkpoint,LORA`), NSFW-Filter |
| LoRA-Adapter | ✅ implementiert | GGUF-LoRA über `llama_adapter_lora_init` + `llama_set_adapter_lora`, Skalierung 0–1.5 |
| NSFW / Abliterated / Uncensored | ✅ implementiert | 18+-Bestätigung, Toggle, Suche nach `abliterated`, `uncensored`, Civitai-NSFW |
| NPU-Erkennung + NNAPI-Probe | ⚠️ implementiert, Treiber limitiert | NNAPI-Geräteabfrage; ADD-Graph kompiliert, Ausführung auf `nnapi-reference` endet mit Code 4. Kein NPU-Accelerator über NNAPI auf diesem ROM. |
| GPU-Erkennung | ✅ implementiert | Vulkan-Instanz, „Samsung Xclipse 540“, API 1.3, RT-/IntDot, 4 Compute-CUs |
| GPU-Inferenz (Vulkan) | ✅ auf Gerät verifiziert | llama.cpp `GGML_VULKAN=ON`, GPU-Layer-Offload, 10,3 tok/s (Qwen2.5-0.5B Q4_K_M) |
| Lokale Bildgenerierung (SD 1.5) | ❌ offen | Katalog + Download vorhanden, Runtime fehlt |
| Lokale Video-/Musikgenerierung | ❌ offen | Katalog + Download vorhanden, Runtime fehlt |
| Cloud-Chat + Cloud-Bild | ✅ implementiert | Gemini API-Key (`gemini-2.5-flash`, `-pro`, `gemini-2.5-flash-image`) |

Auf dem **Galaxy A56 (SM-A566B, Android 17)** installiert und getestet:

- Hardware-Probe: 8 Kerne (4P + 4E), 7.416 MB RAM, Xclipse 540 mit Vulkan 1.3.
- CPU-Inferenz: Qwen2.5-0.5B Q4_K_M, 7 Threads → **10,7 tok/s**.
- Vulkan-Inferenz: 89 GPU-Layer → **10,3 tok/s**, Backend „CPU+Vulkan Samsung Xclipse 540“.
- APK: 70,9 MB (Vulkan-Build), `libgalaxya56.so` 34,4 MB inkl. SPIR-V-Shader;
  CPU-Build 62,3 MB. Beide Builds kompilieren.

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
├── llama_engine.cpp           llama.cpp, Streaming, Cancel, LoRA, GPU-Layer
├── vulkan_android_compat.cpp  vkResetQueryPool für API < 33
└── jni_bridge.cpp             11 JNI-Methoden, JSON-Transport
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

1. NPU: Samsung **ENN SDK** statt NNAPI evaluieren (nicht öffentlich im NDK); alternativ
   LiteRT + Samsung-Delegate, falls verfügbar. NNAPI-Referenz-Treiber unterstützt den
   Probe-Graphen nicht (Code 4).
2. Diffusion-Runtime: stable-diffusion.cpp (SD 1.5, Q8_0) als JNI-Modul; Modelldownload
   und 512×512-Pipeline auf 7,4 GB RAM testen.
3. Audio-Runtime: MusicGen / Stable Audio Open Small, Whisper-ggml, TTS.
4. Benchmark-Suite: Prompt-Länge, Threads, GPU-Layer und Thermik systematisch messen.
5. Vulkan auf weiteren Geräten testen (Xclipse 540 ist verifiziert, Adreno/Mali offen).

---

## Lizenz

MIT — siehe [`LICENSE`](LICENSE). llama.cpp steht unter MIT, siehe
`app/src/main/cpp/third_party/llama.cpp/LICENSE`.
