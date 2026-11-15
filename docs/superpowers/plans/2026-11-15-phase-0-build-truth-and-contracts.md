# Phase 0 Build Truth and Contracts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the prototype into a reproducible native Android project that builds a debug APK, exposes tested capability/planning contracts, and never reports placeholder work as successful inference.

**Architecture:** Keep one native Kotlin/Compose application module and one narrow JNI library. First restore Gradle/Kotlin/C++ build truth, then replace drifted model/backend booleans with pure Kotlin compatibility and execution-plan domain types that are testable without a device. The Phase 0 engine is explicitly unavailable until a real runtime lands; the UI shows that state instead of fake output.

**Tech Stack:** JDK 17, Gradle Wrapper 8.7, Android Gradle Plugin 8.5.x, Kotlin 1.9.23, Jetpack Compose Material 3, kotlinx.serialization, JUnit 4, C++20/CMake 3.22.1, Android NDK 27.2.12479018

**Spec:** `docs/superpowers/specs/2026-11-15-galaxy-a56-local-ai-studio-design.md`

## Global Constraints

- Tests are written first and observed failing before each behavior implementation.
- Build and target the locally installed Android API 35 baseline in Phase 0; later API behavior is runtime-gated.
- `minSdk` remains 28; Java/JVM target remains 17; APK ABI remains `arm64-v8a`.
- NPU → Vulkan → CPU is preference order only after exact eligibility checks.
- Unknown hardware remains unknown; do not synthesize GPU identity, TOPS, modality support, or benchmarks.
- No fake model path, empty graph, empty shader, or deterministic placeholder output may report inference success.
- No model weights, APKs, signing material, credentials, `local.properties`, or generated build directories enter Git.
- Every code change is followed by formatting, lint/static checks, type/build checks, and tests appropriate to the current green baseline.
- Do not push or rewrite the existing Expo repository until repository identity, license, and secret checks are complete.

## Review Focus

- Missing/corrupt model artifacts must produce a typed failure and no success event; pinned in Task 5.
- A requested NPU route with no qualified accelerator must fall through to eligible Vulkan/CPU with recorded reasons; pinned in Task 4.
- No eligible backend must yield a deterministic `NoEligibleBackend` result, not an exception or fake CPU result; pinned in Task 4.
- Unknown hardware fields must render as unavailable/unknown without invented values; pinned in Task 6.
- Repeated initialize/shutdown and native library absence must be safe and visible; pinned in Task 5.

---

## Planned file structure

### Build and repository

- `settings.gradle.kts` — plugin/dependency repositories and the single app module.
- `build.gradle.kts` — root plugin versions and clean task only.
- `app/build.gradle.kts` — Android/Compose/native configuration and a minimal justified dependency set.
- `gradle/wrapper/*`, `gradlew`, `gradlew.bat` — complete Gradle 8.7 wrapper.
- `gradle/libs.versions.toml` — one source of dependency/plugin versions.
- `.github/workflows/android.yml` — truthful format/lint/test/debug-build CI.
- `.gitignore` — generated/native/model/signing/credential exclusions.

### Domain and engine boundary

- `app/src/main/java/com/galaxya56/ai/domain/BackendKind.kt` — backend enum and ordered preference.
- `app/src/main/java/com/galaxya56/ai/domain/ModelContracts.kt` — modality, artifact, manifest, quality profile.
- `app/src/main/java/com/galaxya56/ai/domain/Compatibility.kt` — evidence, reason codes, reports, execution plans.
- `app/src/main/java/com/galaxya56/ai/inference/CompatibilityPlanner.kt` — pure deterministic route planner.
- `app/src/main/java/com/galaxya56/ai/inference/InferenceClient.kt` — typed job-facing interface.
- `app/src/main/java/com/galaxya56/ai/inference/UnavailableInferenceClient.kt` — explicit Phase 0 implementation.
- `app/src/main/java/com/galaxya56/ai/inference/NativeBridge.kt` — minimal library-loading and probe boundary.
- `app/src/main/cpp/CMakeLists.txt`, `app/src/main/cpp/jni/native_bridge.cpp` — one compileable JNI smoke library; legacy stub backends are not linked.

### UI

- `app/src/main/java/com/galaxya56/ai/ui/MainActivity.kt` — edge-to-edge Compose entry point.
- `app/src/main/java/com/galaxya56/ai/ui/WorkbenchApp.kt` — compact adaptive app shell.
- `app/src/main/java/com/galaxya56/ai/ui/work/WorkScreen.kt` — mode selector, honest empty state, composer.
- `app/src/main/java/com/galaxya56/ai/ui/components/ComputeRail.kt` — route stages and reason-coded availability.
- `app/src/main/java/com/galaxya56/ai/ui/theme/*` — static/dynamic light/dark Material 3 tokens.
- Existing broken prototype Kotlin files remain preserved outside the compiled source set until migrated or removed in later reviewed tasks.

### Tests

- `app/src/test/java/com/galaxya56/ai/inference/CompatibilityPlannerTest.kt`.
- `app/src/test/java/com/galaxya56/ai/inference/UnavailableInferenceClientTest.kt`.
- `app/src/test/java/com/galaxya56/ai/domain/ModelManifestTest.kt`.
- `app/src/androidTest/java/com/galaxya56/ai/ui/WorkbenchAppTest.kt`.
- `app/src/androidTest/java/com/galaxya56/ai/inference/NativeBridgeTest.kt`.

---

### Task 1: Reproducible build launcher and repository baseline

**Files:**
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `.gitignore`
- Create: `local.properties` (ignored, local only)

**Interfaces:**
- Consumes: installed JDK 17 and Android SDK path.
- Produces: `./gradlew --version` reporting Gradle 8.7 and an initialized local Git repository on branch `main`.

- [ ] **Step 1: Capture the failing build-launch smoke test**

Run: `./gradlew --version` (or `gradlew.bat --version` on Windows)  
Expected: FAIL because the wrapper scripts/JAR do not exist.

- [ ] **Step 2: Generate the complete Gradle 8.7 wrapper from a temporary verified Gradle distribution**

Use the official `https://services.gradle.org/distributions/gradle-8.7-bin.zip`; do not execute from an untrusted download directory. Set `distributionSha256Sum` to the official archive checksum in `gradle-wrapper.properties`.

- [ ] **Step 3: Add local SDK configuration without committing machine paths**

Write `sdk.dir=...` to ignored `local.properties`; ensure `.gitignore` also excludes `.cxx/`, model artifacts (`*.gguf`, `*.safetensors`, `*.onnx`, `*.tflite`), keystores, and secrets.

- [ ] **Step 4: Verify launcher and initialize Git**

Run: `gradlew.bat --version`  
Expected: PASS, Gradle 8.7 on JVM 17. Initialize Git with `main`, inspect ignored files, then make the first commit only after Task 2 is green.

### Task 2: Correct Gradle Kotlin DSL and establish a buildable source boundary

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `prototype/README.md`
- Move: existing uncompilable app sources to `prototype/app/` only if fixing them in place would mix prototype contracts into the new baseline

**Interfaces:**
- Consumes: complete Gradle wrapper from Task 1.
- Produces: valid Kotlin DSL, one `:app` Android application, Compose compiler aligned to Kotlin, a compiling empty/minimal source set, and dependency declarations only for Phase 0.

- [ ] **Step 1: Run the build configuration as the failing test**

Run: `gradlew.bat :app:tasks --stacktrace --no-daemon`  
Expected: FAIL on Groovy syntax/duplicate plugin declarations or version mismatch.

- [ ] **Step 2: Replace root and settings scripts with valid Kotlin DSL**

Use `RepositoriesMode.FAIL_ON_PROJECT_REPOS` only in settings; remove `allprojects.repositories`; define AGP 8.5.x, Kotlin 1.9.23, serialization, and kapt plugins once.

- [ ] **Step 3: Replace app build script with one Android application configuration**

Use `compileSdk = 35`, `targetSdk = 35`, `minSdk = 28`, Java/Kotlin 17, Compose enabled, arm64 native build, and no undeclared release signing config. Remove speculative ML dependencies until a runtime task needs one. Add `lifecycle-runtime-compose`, Material icons extended only if actually used, serialization, and test dependencies.

- [ ] **Step 4: Preserve rather than silently discard prototype code**

If broad compile repair is not a focused Phase 0 change, move the original prototype tree intact to `prototype/app/` and document why it is excluded. Create a minimal `app/src/main` source tree. Verify no model files or generated dependencies such as orphaned `node_modules` are staged.

- [ ] **Step 5: Verify project configuration**

Run: `gradlew.bat :app:tasks :app:dependencies --no-daemon`  
Expected: PASS with no repository-mode or Kotlin script errors.

- [ ] **Step 6: Commit build baseline**

Commit: `build: restore reproducible Android build configuration` with required attribution.

### Task 3: Model bundle domain contracts and validation

**Files:**
- Create: `app/src/main/java/com/galaxya56/ai/domain/BackendKind.kt`
- Create: `app/src/main/java/com/galaxya56/ai/domain/ModelContracts.kt`
- Create: `app/src/main/java/com/galaxya56/ai/domain/Compatibility.kt`
- Test: `app/src/test/java/com/galaxya56/ai/domain/ModelManifestTest.kt`

**Interfaces:**
- Produces: `BackendKind`, `Modality`, `ArtifactFormat`, `QualificationStatus`, `ModelArtifact`, `ModelBundleManifest`, `ManifestValidationResult`, `CompatibilityFailure`, `BackendCandidate`, `CompatibilityReport`, `ExecutionPlan`.

- [ ] **Step 1: Write failing manifest tests**

Test names/assertions:
- `rejectsArtifactWithBlankSha256`: blank/non-64-hex SHA-256 returns `Invalid("artifact.sha256")`.
- `rejectsUnsafeCheckpointFormat`: `.ckpt`/pickle artifact returns `Invalid("artifact.format")`.
- `acceptsBackendSpecificSiblingArtifacts`: a GGUF CPU/Vulkan artifact plus TFLite NPU artifact validates.
- `experimentalEntryIsNotA56Verified`: `EXPERIMENTAL.isVerifiedOnA56` is false.

- [ ] **Step 2: Verify RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests '*ModelManifestTest'`  
Expected: FAIL because contracts/validation do not exist.

- [ ] **Step 3: Implement immutable serializable contracts and pure validation**

Keep URLs as strings in the domain layer; require IDs, versions, license, positive byte lengths, exact SHA-256 syntax, safe formats, and at least one backend/runtime declaration.

- [ ] **Step 4: Verify GREEN and format/lint**

Run: `gradlew.bat :app:testDebugUnitTest :app:lintDebug`  
Expected: PASS.

- [ ] **Step 5: Commit**

Commit: `feat(domain): add verified model bundle contracts` with required attribution.

### Task 4: Capability-based route planner

**Files:**
- Create: `app/src/main/java/com/galaxya56/ai/inference/CompatibilityPlanner.kt`
- Test: `app/src/test/java/com/galaxya56/ai/inference/CompatibilityPlannerTest.kt`

**Interfaces:**
- Consumes: `BackendCandidate` and `CompatibilityFailure` from Task 3.
- Produces: `fun plan(stages: List<StageCompatibility>): PlanResult`, where each stage selects the first eligible route in `NPU, VULKAN, CPU` order and retains every rejection reason.

- [ ] **Step 1: Write failing planner tests**

Test names/assertions:
- `selectsNpuWhenExactCandidateIsEligible` → NPU.
- `fallsBackToVulkanAndRecordsNpuReason` → Vulkan plus `UNSUPPORTED_OP` trail.
- `fallsBackToCpuWhenNpuAndVulkanAreIneligible` → CPU plus both reason trails.
- `returnsNoEligibleBackendWithoutInventingCpuSuccess` → typed failure and no plan.
- `keepsStageSpecificRoutes` → encoder may select NPU while decoder selects Vulkan.

- [ ] **Step 2: Verify RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests '*CompatibilityPlannerTest'`  
Expected: FAIL because planner is missing.

- [ ] **Step 3: Implement the deterministic pure planner**

No hardware APIs or global state. Preserve requested quality; quality adjustment requires a distinct explicit result and is not part of automatic fallback.

- [ ] **Step 4: Verify GREEN and full unit suite**

Run: `gradlew.bat :app:testDebugUnitTest :app:lintDebug`  
Expected: PASS.

- [ ] **Step 5: Commit**

Commit: `feat(inference): add capability-based backend planning` with required attribution.

### Task 5: Honest unavailable inference client and JNI smoke boundary

**Files:**
- Create: `app/src/main/java/com/galaxya56/ai/inference/InferenceClient.kt`
- Create: `app/src/main/java/com/galaxya56/ai/inference/UnavailableInferenceClient.kt`
- Create: `app/src/main/java/com/galaxya56/ai/inference/NativeBridge.kt`
- Replace: `app/src/main/cpp/CMakeLists.txt`
- Create: `app/src/main/cpp/jni/native_bridge.cpp`
- Test: `app/src/test/java/com/galaxya56/ai/inference/UnavailableInferenceClientTest.kt`
- Test: `app/src/androidTest/java/com/galaxya56/ai/inference/NativeBridgeTest.kt`

**Interfaces:**
- Produces: `InferenceClient.submit(JobSpec): Flow<JobEvent>`, `cancel(JobId)`, `compatibility(...)`; `NativeBridge.probeAbi(): NativeProbeResult`.

- [ ] **Step 1: Write failing JVM tests for unavailable behavior**

Assert missing/corrupt/unimplemented runtime emits `JobRejected(RuntimeUnavailable)` and never `JobCompleted`; repeated cancellation is idempotent.

- [ ] **Step 2: Verify RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests '*UnavailableInferenceClientTest'`  
Expected: FAIL because client contracts do not exist.

- [ ] **Step 3: Implement minimal client contracts and unavailable client**

Use bounded typed events; do not call legacy native backends.

- [ ] **Step 4: Write the failing instrumentation JNI smoke test**

Assert `NativeBridge.probeAbi()` returns the expected ABI schema version and compiled ABI, and that two initialize/probe/release cycles do not crash.

- [ ] **Step 5: Verify RED then implement one JNI library**

Run instrumentation compile first and observe missing native symbol/library failure. Replace CMake linkage with one `galaxy_inference` smoke library; load it in `NativeBridge` companion initialization; return only schema/ABI/runtime facts.

- [ ] **Step 6: Verify GREEN**

Run: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`  
When an emulator/device is available: `gradlew.bat :app:connectedDebugAndroidTest`.

- [ ] **Step 7: Commit**

Commit: `feat(inference): establish safe client and native ABI` with required attribution.

### Task 6: Original Workbench Compose shell and compute disclosure

**Files:**
- Create/replace: `app/src/main/java/com/galaxya56/ai/ui/MainActivity.kt`
- Create: `app/src/main/java/com/galaxya56/ai/ui/WorkbenchApp.kt`
- Create: `app/src/main/java/com/galaxya56/ai/ui/work/WorkScreen.kt`
- Create: `app/src/main/java/com/galaxya56/ai/ui/components/ComputeRail.kt`
- Create/replace: `app/src/main/java/com/galaxya56/ai/ui/theme/Color.kt`
- Create/replace: `app/src/main/java/com/galaxya56/ai/ui/theme/Theme.kt`
- Create/replace: `app/src/main/java/com/galaxya56/ai/ui/theme/Type.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/androidTest/java/com/galaxya56/ai/ui/WorkbenchAppTest.kt`

**Interfaces:**
- Consumes: immutable UI state derived from Task 3–5 contracts.
- Produces: compact phone shell with Work/Models/Library/Settings destinations, Work modes Chat/Code/Image/Video/Music, accessible unavailable states, composer, and compute rail.

- [ ] **Step 1: Write failing Compose tests**

Assert:
- Work is the initial destination.
- All five modes have semantics labels and can be selected.
- Send/generate is disabled when no compatible model/runtime exists.
- Unknown hardware renders `Not measured`, never guessed TOPS/device names.
- Compute rail reads NPU → GPU → CPU and exposes reason text for unavailable stages.
- Core touch targets and content descriptions exist.

- [ ] **Step 2: Verify RED via instrumentation test compilation/run**

Run emulator/device test if available; otherwise compile `connectedDebugAndroidTest` sources and record the environment limitation.

- [ ] **Step 3: Implement the Material 3 shell**

Use semantic color roles, optional Android 12+ dynamic color, static cobalt/mineral/amber fallback, edge-to-edge/IME insets, adaptive navigation, and no copied product branding. User messages may use compact tonal containers; assistant content remains on the content surface.

- [ ] **Step 4: Verify UI and accessibility**

Run unit tests, lint, debug assemble, Compose tests, and manual screenshot review on a compact emulator/device. Correct contrast, clipping, focus order, and text scaling issues.

- [ ] **Step 5: Commit**

Commit: `feat(ui): add local AI workbench shell` with required attribution.

### Task 7: CI truth, documentation, security scan, and debug APK

**Files:**
- Replace: `.github/workflows/ci-cd.yml` with `.github/workflows/android.yml`
- Modify: `README.md`
- Modify: `docs/HARDWARE_SPECS.md`
- Create: `docs/architecture/runtime-qualification.md`
- Create: `docs/architecture/model-bundles.md`
- Create: `LICENSE` only after matching repository/license intent is confirmed

**Interfaces:**
- Produces: documented Phase 0 status, CI commands matching local commands, and `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 1: Treat current CI/local quality commands as the failing acceptance test**

Run the exact proposed CI command sequence locally. Expected initial failures include nonexistent detekt config/tasks, wrong native-test working directory, missing suppression file, release signing, and false API assumptions.

- [ ] **Step 2: Implement truthful CI**

CI runs wrapper validation, unit tests, lint, and debug APK build on JDK 17/API 35/NDK 27.2.12479018. Do not claim instrumentation/native tests run unless they actually do. Pin third-party actions to stable major versions or immutable SHAs per repository policy.

- [ ] **Step 3: Rewrite claims to match implementation**

Document Exynos/hardware facts as runtime-measured or externally sourced; remove estimated benchmark tables and implemented-feature claims for stubs. Explain Phase 0 unavailable inference and roadmap.

- [ ] **Step 4: Run secret/license/dependency checks**

Search tracked candidates for credentials, tokens, PII, local paths, model binaries, APKs, and incompatible licenses. Ensure the pasted password is absent. Review dependency licenses before selecting a public repository license.

- [ ] **Step 5: Full verification and APK inspection**

Run:

```text
gradlew.bat --version
gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk   # when an authorized device/emulator is connected
aapt dump badging app/build/outputs/apk/debug/app-debug.apk
```

Expected: all configured gates pass; APK package is `com.galaxya56.ai`; arm64 native library is present; launcher activity resolves.

- [ ] **Step 6: Independent code review and fixes**

Use Kotlin, C++, security, and general code review agents on the final diff. Reproduce each accepted finding with a failing test before fixing behavior.

- [ ] **Step 7: Commit**

Commit: `chore: verify phase zero Android baseline` with required attribution.

### Task 8: Repository reconciliation and publication

**Files:**
- Git metadata/remotes only; no product file change is required unless README links are added.

**Interfaces:**
- Consumes: green Phase 0 branch and public-release scan from Task 7.
- Produces: preserved remote history, pushed native branch/repository, and traceable commit history.

- [ ] **Step 1: Compare local native baseline with `mertgoevse-wq/local-ai-studio`**

Confirm the remote is an independent Expo prototype and inspect its license/default branch/history. Do not force-push or replace `master`.

- [ ] **Step 2: Choose the least destructive repository mapping**

Default: preserve `local-ai-studio/master` and publish native Android work on a named branch such as `native-android`, unless a separate repository more clearly preserves both products. If creating a public repository, ensure LICENSE and attribution are valid first.

- [ ] **Step 3: Add remote and verify outgoing history/content**

Run `git status`, `git log`, `git diff --check`, ignored-file checks, and a final secret scan. Inspect every outgoing commit and repository visibility.

- [ ] **Step 4: Push under the user's standing autonomous publication request**

Push without force, report exact repository/branch URL and CI status. Do not create a release or upload an APK publicly until signing and release policy are separately ready.
