# Galaxy A56 Local AI Studio — Product and Architecture Design

**Date:** 2026-11-15  
**Status:** Autonomous implementation baseline  
**Target:** Samsung Galaxy A56 / Android, native Kotlin + Jetpack Compose + C++

## Intent

Build a private, local-first AI workbench for text and coding assistance, image generation, bounded music generation, and experimental short-video generation. The app should feel as clear and conversational as leading chat products while retaining its own visual identity and while telling the truth about which hardware actually executes each pipeline stage.

The requested preference is NPU → GPU → CPU. This is a preference order, not a promise: a backend becomes eligible only after the exact model artifact passes format, operator, compilation, memory, thermal, and known-answer checks on the current device/runtime fingerprint.

## Goals

- Produce a reproducible, testable, installable Android APK.
- Provide one chat-first Compose UI for chat, code, image, video, and music jobs.
- Execute real local models with an observable backend route and graceful fallback.
- Prefer a qualified NPU route, then Vulkan GPU, then CPU.
- Curate compatible Hugging Face and Civitai base models, LoRAs, and runtime extensions with license, size, RAM, format, and qualification metadata.
- Support model bundles, resumable verified downloads, prompt/system-prompt presets, and task harnesses.
- Keep inference offline after model installation; model discovery/download is the only ordinary network use.
- Use runtime capability probes rather than guessed Galaxy A56 marketing specifications.

## Non-goals

- Reimplement a universal tensor compiler, all NNAPI operators, or a bespoke general-purpose Vulkan ML runtime.
- Claim that GGUF or arbitrary SafeTensors/ONNX files run directly on the NPU.
- Promise production-quality multi-minute songs or full diffusion video generation on this phone.
- Copy ChatGPT, Claude, OpenAI, or Anthropic names, logos, exact colors, layouts, or trade dress.
- Bundle model weights into the APK.
- Automate login with pasted account credentials or embed cloud credentials in the app.
- Expose the initial inference service to other apps.

## Anti-goals

The project fails even if it compiles when it:

- reports successful inference from placeholder alphabet output or an unloaded model;
- labels a route NPU/GPU without observed device and exact-artifact evidence;
- silently reduces context, resolution, steps, frames, or duration;
- accepts unverified model files or unsafe pickle checkpoints;
- lets one native crash or memory runaway corrupt persistent models;
- publishes credentials, generated binaries, model files, or misleading performance claims;
- offers controls that appear functional but are TODO/no-op actions.

## Constraints and factual baseline

- Existing project root: `C:\Users\Win11 Pro\Desktop\Local Image Video Code`.
- Existing stack: Kotlin, Jetpack Compose Material 3, Android service/JNI, C++20/CMake.
- Java 17, Android SDK platforms 35/36/37.0, NDK 27.2.12479018, and CMake 3.22.1 are available locally.
- The Gradle wrapper is incomplete and the `.kts` files currently contain Groovy syntax.
- Kotlin declarations, Compose sources, service/AIDL, and JNI/native contracts contain compile blockers.
- Current CPU, NPU, and Vulkan implementations are prototypes/stubs, not model inference.
- NNAPI is deprecated from Android API 35; isolate it behind a replaceable adapter.
- Device hardware documentation conflicts. Runtime probes are authoritative; unknown data stays unknown.
- The existing public GitHub repository `mertgoevse-wq/local-ai-studio` is a separate Expo prototype with one unrelated root commit. Preserve its history rather than overwriting it blindly; the native app will use a dedicated branch or a separate repository only after comparison and secret/license checks.

## Approaches considered

### A. Complete the custom NNAPI translator and Vulkan shader runtime

**Benefits:** maximum theoretical control and a single native stack.  
**Costs:** requires graph import, shape inference, memory planning, hundreds of operators, optimized kernels, numerical validation, and years of maintenance. The current implementations contain empty graphs/shaders.  
**Decision:** rejected.

### B. One universal runtime and one artifact per model

Use a broad runtime such as ONNX Runtime for every modality and expect it to choose providers.

**Benefits:** simpler conceptual dependency graph.  
**Costs:** weak fit for GGUF LLMs and mobile diffusion, large binary/dependency footprint, no guarantee of Samsung NPU access, and a universal artifact cannot express backend-specific quantization/layout/shape needs.  
**Decision:** rejected as the product-wide strategy; an individual pipeline may still use ONNX/LiteRT where justified.

### C. Proven runtime adapters plus backend-specific model bundles

Use a small set of established runtimes: llama.cpp/GGML for text/code CPU+Vulkan, a validated mobile diffusion runtime for compact image generation, and LiteRT/TFLite or native NNAPI only for separately converted, statically qualified NPU artifacts. Add a narrow audio runtime only for the chosen music model.

**Benefits:** shortest path to real inference, honest preflight, testable fallback, independent pipeline evolution.  
**Costs:** backend-specific conversion and possibly multiple artifacts per model; upstream native integrations must be maintained.  
**Decision:** selected.

## System architecture

```text
Compose UI
  └─ InferenceClient (submit / observe / cancel / compatibility)
      └─ private bound InferenceService
          ├─ JobCoordinator (priority, lifecycle, cancellation)
          ├─ ResourceGovernor (RAM, thermal, battery, quality policy)
          ├─ ModelStore (manifest, download, hash, atomic install)
          ├─ CapabilityRegistry (observed device/runtime facts)
          ├─ CompatibilityPlanner (stage plans and fallback trail)
          └─ PipelineFactory
              ├─ Text/Code: tokenizer → prefill → decode → sampling
              ├─ Image: text encoder → denoiser → VAE
              ├─ Music: text encoder → token/latent generator → codec
              └─ Video: conditioning → temporal denoiser → VAE → MediaCodec
                    └─ StageExecutor
                        1. qualified NPU adapter
                        2. qualified Vulkan adapter
                        3. CPU adapter
                            └─ narrow handle-based JNI/native runtime ABI
```

Start the service in-process for the build baseline. Move it to a private `:inference` process after the CPU runtime is stable, preserving the same client API. Large images/audio/video/tensors travel through `ParcelFileDescriptor`, app-private files, direct buffers, or `SharedMemory`, never token-by-token JSON or large Binder payloads.

## Core contracts

### Capability facts

Facts include source and confidence. Do not synthesize TOPS, GPU names, modality support, or operator sets from a device name.

- `BackendKind`: `NPU`, `VULKAN`, `CPU`.
- `CapabilityEvidence`: backend, device/runtime fingerprint, observed features, probe timestamp, provenance.
- `CompatibilityFailure`: `NO_ACCELERATOR_DEVICE`, `FORMAT_MISMATCH`, `UNSUPPORTED_OP`, `DYNAMIC_SHAPE_UNSUPPORTED`, `COMPILE_FAILED`, `KNOWN_ANSWER_FAILED`, `RAM_BUDGET_EXCEEDED`, `THERMAL_BLOCKED`, `CORRUPT_ARTIFACT`, `LICENSE_NOT_ACCEPTED`.
- `CompatibilityReport`: eligible stages/routes, failure reasons, estimated peak memory, quality profile.
- `ExecutionPlan`: immutable stage plans and ordered candidate routes.

### Job API

- Submit a typed `JobSpec` and receive `JobId`.
- Observe bounded `JobEvent` batches as a Flow.
- Cancel by ID; cancellation is never classified as backend failure.
- Events expose planned and actual routes, progress, quality adjustments, warnings, output descriptors, and terminal errors.
- No model/input validation error triggers backend fallback.

### Fallback semantics

1. Preflight all candidates before expensive allocation.
2. Rank only eligible candidates in NPU → Vulkan → CPU order.
3. Record skipped and failed candidates in the result.
4. Retry at most once on the next backend before output begins.
5. Do not silently restart text after tokens were emitted.
6. Migrate image state only at declared portable scheduler boundaries.
7. Switch video/music only between independently committed chunks.
8. Persist a temporary model/backend/driver denylist after compile, device-loss, or known-answer failure.

## Model bundles and catalog

A catalog entry is metadata; installed inference uses a versioned bundle manifest. A bundle may include sibling artifacts for different runtimes/backends.

Required manifest data:

- source provider/repository/version/revision and license;
- model ID, modality, task, architecture, tokenizer/codec IDs;
- artifact URL, exact length, SHA-256, safe format, runtime ABI/version;
- quantization/dtype, shape buckets, required operators/extensions;
- stage graph, estimated weights/activations/KV or latent peak memory;
- backend eligibility claims and qualification status;
- known-answer fixture hash/tolerance;
- LoRA base-model hash compatibility and merge/runtime mode;
- quality/fallback equivalence and known limitations.

Status values: `VERIFIED_ON_A56`, `COMPATIBLE_UNVERIFIED`, `EXPERIMENTAL`, `INCOMPATIBLE`. Only the first status may be described as working on the Galaxy A56. Verification is invalidated by relevant OTA, GPU driver, runtime, or artifact changes.

Downloads use `.partial`, correct HTTP range/ETag semantics, length and SHA-256 verification, then atomic rename. Reject pickle `.ckpt` on device. SafeTensors and ONNX are containers/interchange formats, not proof of executability.

### Initial feasibility tiers

- **Text/code production target:** 1B–3B Q4/Q5 GGUF, bounded context, llama.cpp CPU baseline then Vulkan. 7B/8B is opt-in/qualified only.
- **Image production target:** SD 1.5-class compact latent diffusion, 512×512, 4–8 steps, Vulkan-first with tiled VAE. SDXL is constrained; 14 GB Flux is incompatible as an A56 baseline.
- **Music production target:** one compact instrumental loop/phrase or symbolic/MIDI model, approximately 5–10 seconds, fixed quality profile and chunked decode. Broad vocals/full songs remain experimental.
- **Video:** low-resolution, very short clip research track only until device RAM, thermal, wall-time, cancellation, and quality gates pass.
- **NPU first target:** a static-shape encoder/embedding graph, then qualified image/audio subgraphs; not generic GGUF LLM decode.

Hugging Face and Civitai discovery displays only entries with direct downloadable artifacts and parseable license/base-model metadata. Civitai LoRAs are shown only when their base-model family and tensor keys match a locally supported image runtime. Extensions mean signed/versioned app runtime adapters or pipeline presets, not arbitrary executable plugins downloaded from the internet.

## Resource and thermal policy

A job is admitted only if:

`resident weights + peak activations + KV/latent state + output + runtime overhead + safety reserve < dynamic available-memory budget`.

- Use `ActivityManager.MemoryInfo`, trim-memory callbacks, and allocation rechecks.
- One heavy image/video/music job at a time.
- Memory-map immutable weights from app-private descriptors.
- Evict activations/KV before compiled graphs/weight mappings.
- Retry one allocation failure at one lower explicit quality/backend only.
- Use `PowerManager.OnThermalStatusChangedListener`; optional thermal headroom where available.
- Moderate: reduce CPU workers/concurrency. Severe: pause or explicitly propose reduced quality at a safe boundary. Critical+: checkpoint if supported, release resources, terminate resumably.

## UI direction: Local AI Workbench

The UI borrows conversational ergonomics, not visual identity.

### Product structure

```text
Compact phone
┌──────────────────────────────┐
│ Workbench       NPU · GPU · CPU │
│ [Chat][Code][Image][Video][Music]│
│                              │
│ Conversation / generated work│
│ with inline route + progress  │
│                              │
│ ┌ prompt / attachments      ┐│
│ │ model · harness · quality ││
│ └───────────────────────────┘│
│ Work  Models  Library  Settings│
└──────────────────────────────┘

Expanded
┌────────────┬──────────────────────┬──────────────┐
│ navigation │ conversation/work    │ model & route│
│ + history  │                      │ inspector    │
└────────────┴──────────────────────┴──────────────┘
```

### Visual system

- Material 3 components and semantic color roles; optional dynamic color.
- Static seed direction: deep cobalt primary, mineral teal secondary, amber for compute warnings; neutral graphite/porcelain surfaces. Do not use Anthropic clay/cream or OpenAI branding.
- Roboto/Roboto Flex Material type scale. Code uses a bundled/readable monospace only where syntax needs it.
- The memorable element is a slim **Compute Rail** showing each pipeline stage moving through NPU/GPU/CPU with reason-coded fallback. Everything else stays calm and content-first.
- Assistant responses are not placed in decorative chat bubbles by default; user prompts use compact tonal containers. Code blocks, generated media, and job cards have task-appropriate surfaces.
- Adaptive bottom navigation on compact widths; rail/list-detail at larger widths. Edge-to-edge, IME-safe composer, 48 dp targets, TalkBack semantics, high-contrast-safe semantic colors.
- Empty and failure states state what action is possible. Unsupported modes are labeled experimental/unavailable rather than left as no-op buttons.

### Main destinations

- **Work:** chat/code/image/video/music composer, conversation history, prompt/system-prompt/harness picker, route/progress.
- **Models:** curated catalog, compatibility filters, license, disk/RAM requirements, installed bundles, download verification.
- **Library:** conversations, generated outputs, reusable prompts/harnesses, exports.
- **Settings:** privacy, storage, thermal/quality policy, dynamic color, developer diagnostics.

## Harnesses and prompts

Harnesses are versioned local templates, not executable arbitrary code. Each declares:

- supported modality/model families;
- system prompt and optional structured-output grammar;
- input slots, file/media constraints, stop rules, sampling defaults;
- tool permissions (initially none for local models unless explicitly implemented);
- evaluation examples and expected output shape;
- safety and resource profile.

Initial presets: General chat, Code review, Explain code, App planner, Image prompt, Storyboard, Instrumental loop. Users can clone/edit/export presets; built-ins remain immutable.

## Security and privacy

- Never store or use the password pasted in chat. It must be rotated by the account owner and protected with 2FA.
- No Google AI Pro automation is required for this local runtime and no cloud account is embedded.
- Validate URL scheme/host policy, redirects, content length, free space, hashes, licenses, manifest schema, and archive traversal.
- No arbitrary native library/plugin loading from downloaded sources.
- Keep service non-exported; use app-private model/output storage and scoped export.
- Run secret/PII/license scans before any public push. Exclude APKs, model weights, local properties, caches, signing material, and generated artifacts.

## Delivery phases and gates

### Phase 0 — Build truth and contracts

Fix wrapper/Gradle/Kotlin/Compose/CMake compilation; remove duplicate/stub-success contracts; load native library deterministically; introduce tested capability/planning domain contracts; produce debug APK.

**Gate:** clean format/lint/unit/build run, installable APK, no fake path can report successful inference.

### Phase 1 — CPU text/code baseline

Integrate pinned llama.cpp, verified GGUF installation, real tokenizer, mmap/KV admission, streaming/cancellation, bounded threads.

**Gate:** golden tiny-model tests, cancellation latency, memory-pressure recovery, device soak.

### Phase 2 — Vulkan text/code

Enable proven runtime Vulkan backend, exact probe and driver fingerprint, pipeline cache, fault-injected fallback.

**Gate:** correctness tolerance and measured A56 benefit over CPU.

### Phase 3 — Conditional NPU

Qualify a static embedding/encoder artifact using exact device enumeration, supported-operation query, device-specific compile, and known-answer test.

**Gate:** proof of accelerator selection, correctness, coverage, and benefit; otherwise report unavailable.

### Phase 4 — Compact image

Add staged SD 1.5-class pipeline, bounded profiles, tiled VAE, output descriptor, stage fallback, compatible LoRA validation.

### Phase 5 — Bounded music and multimodal input

Add one qualified compact music/symbolic model and chunked playback/export; add a compact vision encoder to text where qualified.

### Phase 6 — Short-video research track

Reuse image conditioning, resource governor, chunk scheduler, and hardware media encoding. Promote only after explicit qualification.

### Phase 7 — Catalog, harnesses, qualification, release

Complete curated HF/Civitai metadata, on-device benchmarks, accessibility tests, security/license scan, CI, repository publication, and signed release preparation.

## Testing strategy

- Test-first for every behavior change; observe the failure before implementation.
- JVM unit tests for compatibility planning, backend ordering, resource policy, catalog/manifests, harness validation, reducers/viewmodels.
- Compose UI tests for navigation, mode switching, accessibility labels, disabled/experimental states, route disclosure, composer behavior.
- Native host/Android tests for probes, handle lifetime, malformed dimensions, queue bounds, cancellation races, and backend contract fixtures.
- Instrumented service/JNI tests for process/binder lifecycle and large output descriptors.
- Fault injection for NNAPI compile errors, unsupported ops, Vulkan device loss, CPU OOM, thermal emergency, corrupt model, cancellation.
- Actual Galaxy A56 qualification and soak testing after each runtime phase; emulator tests cannot qualify NPU/GPU support.

## Documentation and claims

README and in-app copy distinguish implemented, experimental, and planned features. Performance numbers are recorded measurements with device RAM variant, OS/build fingerprint, runtime/artifact hash, quality profile, and sample count—never estimates presented as fact.

## Decisions left to implementers

- Select exact pinned upstream runtime commits after license/API/build spikes.
- Select the first compact music model after artifact/license/device feasibility research.
- Choose whether the native app becomes a branch in `local-ai-studio` or a new repository after remote comparison; do not rewrite the Expo branch.
- Choose exact static palette tones with automated contrast checks while preserving the visual direction.
- Choose NPU adapter implementation based on what the retail A56 actually exposes and what can be redistributed legally.
