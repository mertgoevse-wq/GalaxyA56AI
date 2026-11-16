# Externe Werkzeuge: Stitch und Reverse-Engineer-Anything

Stand: 16. November 2026.

## Google Stitch / Google-Service-Hub

Geprüfte Repositories aus dem GitHub-Profil:

- `google-services-hub` (Stitch, Veo, Imagen, MusicFX)
- `design-skill-library` (lokale Design-Skills)
- `claude-media-bridge`

**Ergebnis:** Der Stitch-Aufruf ist ohne Google-OAuth/Google-AI-Pro-Konto nicht
ausführbar. Das Hub-Repository erwartet Provider-Login und Zugangsdaten; im Projekt
liegen keine Google-Credentials, und es wurden keine erfunden. Ein direkter
Stitch-API-Aufruf ist damit **blockiert**, nicht „integriert“.

Die App-Überarbeitung im Field-Lab-Stil wurde deshalb mit dem lokalen
`DESIGN.md`/`design-skill-library`-Satz umgesetzt. Das ist eine lokale
Design-System-Anwendung, **kein** Nachweis eines Stitch-API-Laufs.

## Reverse-Engineer-Anything (REA)

REA wurde aus `morluto/rea` lokal aufgebaut. Für die Android-JNI-Analyse war ein
gepinntes JADX-JAR nötig:

```text
.tools/rea-jadx/jadx-headless-mcp-0.7.1-all.jar
REA_JADX_MCP_JAR=.tools/rea-jadx/jadx-headless-mcp-0.7.1-all.jar
```

Der `inspect-android-jni-bindings`-Lauf auf `app-debug.apk` hat die nativen
Deklarationen den exportierten Symbolen in `libgalaxya56.so` zugeordnet, darunter:

```text
nativeImageGenerateVideo  → Java_com_galaxya56_ai_inference_LocalInference_nativeImageGenerateVideo
nativeEnnUpscale          → Java_com_galaxya56_ai_inference_LocalInference_nativeEnnUpscale
nativeImageLoad           → Java_com_galaxya56_ai_inference_LocalInference_nativeImageLoad
nativeGenerate            → Java_com_galaxya56_ai_inference_LocalInference_nativeGenerate
```

Evidence-ID des Laufs:

```text
ev_d3887b4843df37f07e0f6033f3a4e55e3c6ff345697c23a0fbfc9365bdc4d572
```

**Grenze:** REA arbeitet hier statisch. Es beweist die JNI-Bindung und die
Export-Symbole, nicht jede Laufzeitverzweigung. Die tatsächliche NPU-Ausführung wurde
separat über den ENN-Lauf (`ENN run rc=success`, 202 ms) belegt. Ein vollständiges
„NPU-Backend für beliebige GPU-Modelle“ lässt sich daraus nicht ableiten: Exynos 1580
führt nur vorcompilierte ENN-Container aus; für SD/LLM-Graphen existiert kein
öffentlicher Compiler-Pfad.
