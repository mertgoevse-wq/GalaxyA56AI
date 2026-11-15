# Galaxy A56 (Exynos 1580) — gemessene Hardware & Inferenzpfade

Alle Werte in diesem Dokument wurden am 15.11.2026 direkt auf dem Gerät
(`SM-A566B`, `a56x`, `s5e8855`, Android 17 / API 37) per `adb` und über die native
Hardware-Probe der App erfasst. Herstellerangaben sind als solche markiert.

## SoC

| Komponente | Gemessen / Hersteller |
|---|---|
| SoC | Samsung Exynos 1580 (`s5e8855`, `erd8855`), 4 nm |
| CPU | 8 Kerne: 4× Cortex-A720 (Performance) + 4× Cortex-A520 (Effizienz) |
| CPU-Takt | A720 bis 2,60 GHz laut `cpufreq` (Probe meldet 2,91 GHz Maximalwert), A520 bis 1,95 GHz |
| GPU | Samsung Xclipse 540 (RDNA-basiert), Vulkan 1.3, Treiber `25.6.3` |
| GPU-Compute | `VK_AMD_shader_core_properties2`: **4 aktive CUs**, Wavefront 64 |
| NPU | Samsung NPU, **~35 TOPS INT8 / ~17 TOPS FP16** (Herstellerangabe) |
| RAM | **7.416 MB** sichtbar, davon ~2,4 GB frei; 12 GB Swap |
| Speicher | 128/256 GB UFS |

Die frühere Annahme „10 Kerne (4× A78 + 6× A55)“ war falsch und ist korrigiert.

## Was die App tatsächlich erkennt

`app/src/main/cpp/hardware_detector.cpp` erzeugt JSON aus:

- **CPU**: `/proc/cpuinfo` (MIDR: `0xd81` = A720, `0xd80` = A520), `sysconf`,
  `cpufreq/cpuinfo_max_freq`.
- **GPU**: Vulkan-Instanz + `vkEnumeratePhysicalDevices` → „Samsung Xclipse 540“,
  API-Version, Ray-Tracing-/Integer-Dot-Extensions.
- **NPU**: `dlopen("libneuralnetworks.so")` + NNAPI-Geräteabfrage.
- **TOPS**: Tabelle für `s5e8855` (1580) und `s5e8845` (1480).
- **RAM**: `/proc/meminfo`; **Thermik**: `/sys/class/thermal/*` (auf Android 17 durch
  SELinux gesperrt, UI zeigt deshalb „Sensor nicht lesbar“).

### NNAPI-Befund (wichtig)

Auf dem A56 meldet NNAPI **genau ein Gerät**:

```
nnapi-reference · Typ cpu · Feature-Level 1000008
```

Es gibt **keinen** `accelerator`-Eintrag und keinen Samsung-NPU-Treiber über NNAPI.
Die NPU-Hardware ist vorhanden, wird von Android aber nicht als NNAPI-Accelerator
exponiert. Samsung stellt seine NPU stattdessen über das **ENN SDK** bereit, das nicht
Teil des öffentlichen Android-NDK ist.

Der native NNAPI-Graph (FLOAT32-ADD mit Fused-Activation) wird erfolgreich erstellt und
kompiliert, die Ausführung auf dem Referenz-Treiber endet jedoch mit
`ANEURALNETWORKS_BAD_DATA` (Code 4). Das ist ein Treiber-/Referenz-Limit, kein
NPU-Zugriff. Die App zeigt diesen Befund inklusive Geräteliste in den Optionen an.

## Inferenzpfade (verifiziert)

| Pfad | Status | Messung |
|---|---|---|
| CPU/NEON | ✅ echte Inferenz | Qwen2.5-0.5B Q4_K_M, 7 Threads: **10,7 tok/s**, 18,1 s Prompt (4096 Kontext) |
| GPU/Vulkan | ✅ echte Inferenz | gleiches Modell, 89 GPU-Layer: **10,3 tok/s**, 18,5 s Prompt, Backend „CPU+Vulkan Samsung Xclipse 540“ |
| NPU/NNAPI | ⚠️ Probe vorhanden | Modell kompiliert; Ausführung nur auf `nnapi-reference` (CPU), Code 4; kein NPU-Device |
| Cloud | ✅ optional | Gemini API (Chat + Bild) |

Gemessen mit 4 Threads: 1,2 tok/s. Die Thread-Zahl ist der größte Hebel; 7 Threads
nutzen die A720-Cluster. Prompt-Verarbeitung bleibt bei 4096 Kontext der Flaschenhals.

## Vulkan-Build

`GGML_VULKAN=ON` wird über `-Pvulkan=true` aktiviert. Voraussetzungen auf dem Build-Host:

1. NDK-`glslc` (in `ndk/27.2.12479018/shader-tools/windows-x86_64/glslc.exe`).
2. Ein **Host-C/C++-Compiler** für `vulkan-shaders-gen`. Ohne MSVC/LLVM kann ein
   portables Zig als Shim dienen (`.tools/hostbin/clang.exe` → `zig cc`).
3. Khronos-`vulkan.hpp` (liegt unter `app/src/main/cpp/third_party/vulkan-headers`).

Der Build übergibt `HOST_C_COMPILER`/`HOST_CXX_COMPILER` automatisch, wenn
`.tools/hostbin/clang.exe` existiert. Zusätzlich ist der Pfad zum SDK-`ninja` nötig:

```bash
export PATH="$PWD/.tools/hostbin:$ANDROID_HOME/cmake/3.22.1/bin:$PATH"
./gradlew assembleDebug -Pvulkan=true
```

Der Shader-Generator läuft als Windows-Host-EXE und erzeugt die SPIR-V-Header. Die
Android-API-28-Stub-library exportiert `vkResetQueryPool` nicht; dafür liegt
`vulkan_android_compat.cpp` bei (Laufzeit-Auflösung über `vkGetDeviceProcAddr`).

## Speicherbudget (7,4 GB sichtbar)

| Modellklasse | Datei | Kontext/KV | Praxis |
|---|---|---|---|
| 0,5B Q4_K_M | 0,38 GB | 0,3–0,5 GB | getestet, 10–11 tok/s |
| 1B Q4_K_M | ~0,8 GB | 0,3–0,6 GB | angenehm, ~8–12 tok/s (Erwartung) |
| 1,5–2B Q4_K_M | ~1,1 GB | 0,5–0,9 GB | guter Kompromiss |
| 3B Q4_K_M | ~2,0 GB | 0,8–1,5 GB | braucht >5 GB frei, thermisch kritisch |
| 7–8B Q4_K_M | ~4,7 GB | 1,5–2,5 GB | technisch möglich, sehr langsam, OOM-Risiko |

Kontextgröße, Threads, GPU-Layer und `maxTokens` sind in den Optionen einstellbar.
