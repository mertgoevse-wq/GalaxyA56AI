# Exynos 1580 auf dem Galaxy A56: NPU, GPU, CPU und was wirklich beschleunigt

Stand: 16. November 2026 · Gerät: **SM-A566B**, Android 17 (API 37), App `com.galaxya56.ai`

Diese Notiz trennt drei Dinge, die in Marketingtexten oft vermischt werden:

1. **Was der Chip kann** (TOPS, MACs, Cache, Bandbreite).
2. **Was das Android-Gerät der App zugänglich macht** (Treiber, HALs, NNAPI, ENN).
3. **Was diese App tatsächlich ausführt und gemessen hat** (nur das zählt für eine Beschleunigungsaussage).

---

## 1. Hardware: Exynos 1580 (S5E8855)

| Baustein | Spezifikation | Relevanz für KI |
|---|---|---|
| Fertigung | 4 nm FinFET/EUV | Effizienz, Thermik |
| CPU | 1× Cortex-A720 @ 2,9 GHz, 3× A720 @ 2,6 GHz, 4× A520 @ 1,95 GHz (Armv9, tri-cluster) | Tokenisierung, Sampling, kleine Ops, Fallback |
| GPU | Samsung **Xclipse 540**, AMD RDNA 3, 2 WGP, 256 Shader, ~1,3 GHz | Vulkan/OpenCL-Matrizen, Diffusion, LLM-Layer |
| NPU | **14,7 TOPS** (INT8-Marketingwert), **6K MAC**, **2 MB** NPU-Cache | CNN-Inferenz, Upscaling, keine freie Programmierbarkeit |
| RAM | LPDDR5, 2 Kanäle, 6400 MT/s, **25,6 GB/s** | Modellgewichte, KV-Cache, Bandbreitenlimit |
| Speicher | UFS 3.1 | mmap, Modell-Streaming, Swap-Datei |
| Video | 4K60 Encode/Decode (H.264/HEVC/VP9/VP8) | MP4-Ausgabe, Kamera-Pipelines |

Das Gerät meldet **7.594.828 kB MemTotal** (≈ 7,24 GiB sichtbar), also die 8-GB-Variante. Die NPU ist kein frei programmierbarer Co-Prozessor: Sie führt über Samsung ENN/LiteCore kompilierte Container aus, nicht beliebige GGUF- oder Diffusionsgraphen.

Quellen: [Samsung Exynos 1580](https://semiconductor.samsung.com/processor/mobile-processor/exynos-1580), [GSMArena-Vorstellung](https://www.gsmarena.com/exynos_1580_unveiled_with_cortexa720_cores_double_the_gpu_hardware-news-65051.php), [HMC-Spezifikation](https://hmc-tech.com/cpus/samsung-exynos-1580).

---

## 2. Was auf dem A56 tatsächlich sichtbar ist

Per `adb shell` geprüft:

```text
ro.hardware            = s5e8855
ro.board.platform      = erd8855
ro.product.model       = SM-A566B
ro.build.version.sdk   = 37
MemTotal               = 7594828 kB
/dev/npu0_throughput, /dev/npu1_throughput, /dev/npu_throughput
/vendor/lib64/libenn_engine.so, libenn_public_api_cpp.so,
libenn_user_driver_cpu.so, libenn_user_driver_gpu.so,
libenn_user_driver_unified.so
vendor.samsung_slsi.hardware.enn_aidl-V1-ndk.so
```

- **ENN ist vorhanden** und über die öffentliche Vendor-Library `libenn_public_api_cpp.so` nutzbar. Die App deklariert diese Library in `AndroidManifest.xml` als optionale `uses-native-library` und lädt sie zur Laufzeit; es werden **keine Samsung-Binaries mitgeliefert**.
- **NNAPI ist auf diesem ROM nicht als NPU-Accelerator exponiert.** Die App findet nur den Referenz-Treiber; der ADD-Graph endet mit Fehlercode 4. NNAPI ist damit kein Weg zu Diffusion/LLM auf der NPU.
- Die NPU-Treiberknoten und die ENN-HAL sind vorhanden, aber der öffentliche **Modellcompiler** ist der Engpass, nicht die Recheneinheit.

### Verifizierter NPU-Pfad: MIDAS 4× Upscale

Die App nutzt das geräteeigene Modell

```text
/vendor/etc/midas/SRIBMidas_aiUPSCALER_4X_V210_FP16_rose.nnc
```

über ENN. Ergebnis auf dem A56: **256×256 → 1024×1024** in **189–259 ms** (ENN-Benchmark, siehe README). Das ist der einzige in diesem Build nachweislich NPU-beschleunigte Pfad. Er beschleunigt **Nachbearbeitung**, nicht die Diffusion selbst.

---

## 3. Software-Spezifikationen und harte Grenzen

### Samsung ENN / AI LiteCore

ENN ist Samsungs eigener NPU-/DSP-/GPU-/CPU-Runtime-Stack. Modelle werden als **NNC** (Neural Network Container) kompiliert. Die öffentliche LiteCore-Dokumentation beschreibt Konvertierung aus LiteRT/Torch/ORT, Graph-Optimierung und NPU-Codegenerierung. Der Compiler ist nicht Teil dieses Repos und nicht als Android-App-Bibliothek verfügbar.

Quelle: [Exynos AI LiteCore Documentation](https://soc-developer.semiconductor.samsung.com/global/development/ai-litecore/document/documentation).

### LiteRT mit Samsung-NPU

Google dokumentiert die Samsung-Exynos-Anbindung über die **LiteRT CompiledModel API** (AOT und JIT). Die offizielle Liste der unterstützten SoCs ist jedoch eng:

- Exynos **2500** (E9955)
- Exynos **2600** (E9965)

Der **Exynos 1580 ist dort (Stand November 2026) nicht gelistet**. Ein `.tflite`-Modell mit Samsung-NPU-Delegate ist auf dem A56 daher kein belastbarer Pfad, auch wenn die ENN-HAL vorhanden ist.

Quelle: [LiteRT: Samsung NPU (Exynos AI LiteCore)](https://developers.google.com/edge/litert/next/samsung).

### ExecuTorch mit Samsung-Backend

Das ExecuTorch-Samsung-Backend nutzt LiteCore und unterstützt FP16, int8/uint8 und int16/uint16. Auch hier gilt laut Dokumentation:

> Currently, the Samsung Exynos backend is supported only for devices with the following chipsets: Exynos 2500, Exynos 2600.

Quelle: [ExecuTorch Samsung Exynos Backend](https://docs.pytorch.org/executorch/stable/backends/samsung/samsung-overview.html).

**Konsequenz:** Ein generisches `model.pte` oder `.tflite` „einfach auf die NPU schieben“ funktioniert auf dem A56 nicht. Alles andere wäre eine unbelegte Behauptung.

---

## 4. Beschleunigungsmatrix

| Workload | CPU | GPU (Xclipse 540) | NPU (ENN) | Status auf A56 |
|---|---|---|---|---|
| LLM (GGUF, llama.cpp) | ✅ NEON, 4–7 Threads | ✅ Vulkan-Layer-Offload | ❌ kein GGUF-Backend | CPU **10–12 tok/s**, Vulkan **10,3 tok/s** (Qwen2.5-0.5B Q4_K_M) |
| SD1.5 / LCM Diffusion | ✅ Fallback | ✅ Vulkan/stable-diffusion.cpp | ❌ kein UNet-NNC | **76–80 s** LCM 4 Steps; **107–124 s** SD1.5 8 Steps (512²) |
| ControlNet (SD1.5) | ✅ Canny/Preprocess | ✅ ControlNet-Graph auf Vulkan | ❌ | **25,1 s** bei 256², 4 Steps, inkl. 1,4-GB-Modellladung |
| 4× Upscale | ✅ möglich, langsam | ✅ möglich | ✅ **MIDAS über ENN** | **189–259 ms** für 256→1024 |
| Musik (DSP) | ✅ PsytranceEngine | – | – | **270 s** WAV, 44,1 kHz Stereo, in wenigen Sekunden gerendert |
| Neuronale Musik (MusicGen/ACE-Step) | ❌ zu groß/langsam | ⚠️ theoretisch | ❌ kein Runtime-Container | Katalog/Download, keine Runtime |
| Video (AnimateDiff/Wan) | ❌ zu langsam | ⚠️ experimentell | ❌ | Katalog/Download, keine Runtime in diesem Build |

Die Tabelle ist bewusst konservativ: „theoretisch“ bedeutet nicht „in der App verfügbar“.

---

## 5. Wie CPU, GPU und NPU zusammenspielen (und wie nicht)

### GPU beschleunigt CPU-Workloads

- **llama.cpp** legt ausgewählte Transformer-Layer als Vulkan-Compute-Shader auf die Xclipse 540. Die CPU behält Tokenisierung, KV-Cache-Verwaltung, Sampling und die nicht offloaded Layer. Auf dem A56 ist der Gewinn bei 0,5B klein (10,3 vs. 10,7 tok/s), weil der Flaschenhals Speicherbandbreite und Kernel-Overhead ist, nicht reine Rechenleistung.
- **stable-diffusion.cpp** führt UNet, VAE und ControlNet auf Vulkan aus. Die CPU liefert Prompt-Encoding, Tensor-Aufbau, Canny-Vorverarbeitung (falls aktiv) und die Datei-/Metadatenlogik.
- Der **Vulkan-Fallback** ist echt: schlägt das Laden fehl, wird CPU versucht. Schlägt die **Generierung** fehl, lädt die App einmal mit `gpuLayers=0` neu und wiederholt den Job; Teilausgaben werden vorher verworfen, damit nichts doppelt erscheint.

### CPU beschleunigt GPU-Workloads

- Prompt-/Tokenisierung, Sampling, Bilddekodierung, PNG-Metadaten und UI bleiben auf der CPU.
- Bei SD ist die CPU für Tensor-Layout und Kontrollfluss zuständig; die GPU rechnet die großen Matrizen.
- **Thread-Zahl** ist ein echter Regler: 4 Threads sind auf dem A56 ein guter Kompromiss; mehr Threads erhöhen thermischen Druck und bringen bei den kleinen Modellen kaum etwas.

### NPU beschleunigt GPU-/CPU-Pipelines

Auf dem A56 ist das nur als **Pipeline-Stufe** möglich, nicht als Co-Prozessor für denselben Graphen:

```text
Text → CPU (Tokenisierung/Prompt)
     → GPU (SD-UNet/VAE, Vulkan)
     → CPU (PNG/Metadaten)
     → NPU (MIDAS 4× Upscale, ENN)
```

Die NPU wartet auf ein fertiges Bild; sie kann die Diffusion nicht „mitrechnen“. Das ist kein App-Bug, sondern eine Folge des fehlenden öffentlichen NNC-Compilers für SD-/LLM-Graphen auf dem 1580.

### Reziprok: GPU-Modell mit CPU-Unterstützung

Ein Modell kann auf der GPU laufen und von der CPU unterstützt werden, wenn die Runtime Layer-Splitting beherrscht:

- **llama.cpp:** `n_gpu_layers` legt fest, wie viele Layer auf Vulkan laufen; der Rest bleibt CPU. Das ist der praktische „GPU + CPU“-Modus.
- **stable-diffusion.cpp:** GPU/CPU-Backend pro Kontext; bei Fehlern wird der gesamte Kontext neu geladen. Ein echter layerweiser SD-Mix ist in dieser App nicht implementiert.
- **NPU + GPU gleichzeitig** für denselben Graph: nicht verfügbar. Die einzige sinnvolle Kopplung ist sequenziell (GPU → NPU-Upscale).

---

## 6. Methoden, die auf dem A56 wirklich helfen

1. **Quantisierung**: Q4_0/Q4_K_M für LLMs, Q4_0 für SD1.5. Das senkt Bandbreite und Dateigröße; 25,6 GB/s sind der harte Deckel.
2. **Weniger Steps**: LCM-LoRA + 4 Steps statt 20–30. Das ist der größte SD-Gewinn (76–80 s statt 124 s bei 512²).
3. **Kleinere Auflösung**: 256²/384² generieren und anschließend NPU-4×-Upscale. Das verlagert Arbeit auf den einzigen schnellen NPU-Pfad.
4. **VAE-Tiling**: senkt den Vulkan-Spitzenverbrauch (2,44 statt 3,38 GiB GL), kostet Zeit.
5. **mmap + sequenzielles Laden**: immer nur ein großes Modell gleichzeitig; vor dem Wechsel `imageUnload()`/`unload()`. Das ist aktives „nur benötigte Bereiche laden“ auf Dateisystem-Ebene.
6. **Thermik**: nach mehreren 512er-Läufen drosselt das Gerät. Kühlpausen und 256er-Läufe sind keine Kosmetik.
7. **NPU nur für kompatible Container**: MIDAS/HumanSeg `.nnc`; keine GGUF-/Safetensors-Hoffnungen.
8. **ZRAM/Swap**: Das ROM verwaltet ZRAM; eine App ohne Root kann es weder aktivieren noch vergrößern. `/proc/swaps` und `/sys/block/zram0/disksize` werden nur gelesen. USB-C-Speicher ist für Modelle nutzbar (SAF-Import), aber nicht als schneller RAM-Ersatz.

---

## 7. Was für echte NPU-Diffusion nötig wäre

- Ein öffentlicher **NNC-Compiler**, der die Ziel-NPU (NCP 26 auf dem 1580) unterstützt, plus ein UNet-/Transformer-Container mit den nötigen Operatoren.
- Oder ein offizieller **LiteRT-/ExecuTorch-Support für Exynos 1580** mit einem Diffusions-/LLM-Backend. Beides ist Stand November 2026 nicht verfügbar.
- Solange das fehlt, ist die ehrliche Architektur: **GPU für Diffusion/LLM, CPU als Fallback und Orchestrator, NPU für CNN-Nachbearbeitung**.

---

## 8. Quellen

- Samsung Semiconductor: [Exynos 1580 Produktseite](https://semiconductor.samsung.com/processor/mobile-processor/exynos-1580)
- Samsung Exynos Developer Society: [AI LiteCore Dokumentation](https://soc-developer.semiconductor.samsung.com/global/development/ai-litecore/document/documentation)
- Google AI Edge: [Samsung NPU with LiteRT](https://developers.google.com/edge/litert/next/samsung)
- PyTorch: [ExecuTorch Samsung Exynos Backend](https://docs.pytorch.org/executorch/stable/backends/samsung/samsung-overview.html)
- GSMArena: [Exynos 1580 unveiled](https://www.gsmarena.com/exynos_1580_unveiled_with_cortexa720_cores_double_the_gpu_hardware-news-65051.php)
- HMC: [Exynos 1580 Full Specs](https://hmc-tech.com/cpus/samsung-exynos-1580)
- Projektinterne Messungen: README, `A56AI-*`-Logs, `docs/MODELS.md`
