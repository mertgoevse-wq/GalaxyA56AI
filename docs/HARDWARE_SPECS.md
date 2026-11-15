# Galaxy A56 (Exynos 1580) — Hardware & Inferenzpfade

## SoC

| Komponente | Daten |
|---|---|
| SoC | Samsung Exynos 1580 (`s5e8855`), 4 nm |
| CPU | 10 Kerne: 4× Cortex-A78 + 6× Cortex-A55 |
| GPU | Samsung Xclipse 540 (RDNA 3, 8 CUs, Ray Tracing, Integer Dot Product) |
| NPU | Samsung NPU, ~35 TOPS INT8 / ~17 TOPS FP16 / ~35 TOPS FP8 (Herstellerangaben) |
| RAM | 8 GB oder 12 GB LPDDR5 |
| Speicher | 128/256 GB UFS |

## Was die App tatsächlich erkennt

`app/src/main/cpp/hardware_detector.cpp` erzeugt JSON aus:

- **CPU**: `/proc/cpuinfo` (MIDR-Partnummern für A78/A55), `sysconf`, `cpufreq`-Maximaltakt.
- **GPU**: Vulkan-Instanz + `vkEnumeratePhysicalDevices` → Name (z. B. „Xclipse 540“),
  Vendor-ID, API-Version, Extension-Liste (Ray Tracing, Integer Dot Product).
- **NPU**: `dlopen("libneuralnetworks.so")` und `ANeuralNetworks_getDeviceCount/getDevice/
  Device_getName/getType`; Namen und Gerätetyp (CPU/GPU/Accelerator) landen in der UI.
- **TOPS**: Tabelle für `s5e8855` (1580) und `s5e8845` (1480) plus generischer Fallback.
- **RAM/Thermik**: `/proc/meminfo`, `sysinfo`, `/sys/class/thermal/thermal_zone*`.
- **Android**: `ro.build.version.*`, `ro.product.model`, `ro.soc.model`.

## Inferenzpfade

| Pfad | Status | Bibliothek |
|---|---|---|
| CPU/NEON | ✅ läuft | vendored llama.cpp b6390, Q4_K_M |
| GPU/Vulkan | ⚠️ nur Erkennung | Vulkan-Loader; keine Compute-Kernel eingebunden |
| NPU/NNAPI | ⚠️ nur Erkennung | NNAPI-Geräteabfrage; keine Graph-Ausführung |
| Cloud | ✅ optional | Gemini API (Chat + Bild) |

### Warum kein Vulkan-LLM in diesem Build?

llama.cpp kann mit `GGML_VULKAN=ON` gebaut werden, generiert die Shader aber zur
Build-Zeit mit `glslc` (Shader-Toolchain). Auf dem Build-Host ist das nicht eingerichtet;
ohne diese Shader bricht der Vulkan-Backend-Build ab. Der nächste Schritt ist ein
CI-Build mit Vulkan-SDK oder das Einbinden vorkompilierter Shader.

### Warum keine NPU-LLMs?

NNAPI führt Operator-Graphen aus TFLite/LiteRT aus, keine GGUF-Transformer. Für die NPU
muss ein Modell (z. B. Gemma 3 1B) nach LiteRT konvertiert, INT8-kalibriert und mit dem
Samsung-Delegate geladen werden. Das ist ein eigener Arbeitsstrang.

## Speicherbudget (8 GB)

| Modellklasse | Datei | Kontext/KV | Praxis |
|---|---|---|---|
| 1B Q4_K_M | ~0.8 GB | 0.3–0.6 GB | angenehm, ~10–25 tok/s (Schätzung) |
| 1.5–2B Q4_K_M | ~1.1 GB | 0.5–0.9 GB | guter Kompromiss |
| 3B Q4_K_M | ~2.0 GB | 0.8–1.5 GB | braucht 6 GB frei, thermisch kritisch |
| 7–8B Q4_K_M | ~4.7 GB | 1.5–2.5 GB | technisch möglich, sehr langsam, OOM-Risiko |

Kontextgröße, Threads und `maxTokens` sind in der App einstellbar. Bei Thermik > 90 %
des Schwellenwerts zeigt die Hardware-Karte eine Warnung.
