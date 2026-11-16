# Modellkatalog für das Galaxy A56

Alle Direkt-URLs wurden beim Build mit HTTP 200 geprüft. Größen sind Dateigrößen, keine
RAM-Prognosen. Faustregel A56 (8 GB): Modell + ~1.5 GB Kontext/KV-Cache + 1.5 GB Android.

## Text & Coding (GGUF, llama.cpp)

| Modell | Parameter | Quant | Größe | RAM | Link |
|---|---|---|---|---|---|
| Llama 3.2 1B Instruct | 1B | Q4_K_M | 808 MB | 4 GB | [bartowski](https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF) |
| Llama 3.2 3B Instruct | 3B | Q4_K_M | 2.0 GB | 6 GB | [bartowski](https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF) |
| Qwen2.5 Coder 1.5B | 1.5B | Q4_K_M | 1.1 GB | 4 GB | [Qwen](https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF) |
| Qwen2.5 Coder 3B | 3B | Q4_K_M | 1.9 GB | 6 GB | [Qwen](https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF) |
| Qwen2.5 1.5B Instruct | 1.5B | Q4_K_M | 1.1 GB | 4 GB | [bartowski](https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF) |
| Gemma 3 1B IT | 1B | Q4_K_M | 806 MB | 4 GB | [bartowski](https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF) |
| SmolLM2 1.7B Instruct | 1.7B | Q4_K_M | 1.05 GB | 4 GB | [bartowski](https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF) |
| DeepSeek-R1-Distill-Qwen 1.5B | 1.5B | Q4_K_M | 1.1 GB | 4 GB | [bartowski](https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF) |

**Chat-Templates** werden anhand des Dateinamens gewählt: `qwen`/`smollm`/`deepseek` →
ChatML, `llama-3` → Llama 3, `gemma` → Gemma, `phi` → Phi. Andere Familien laufen über
ChatML als Fallback.

## Abliterated / Uncensored (lokal, ohne Cloud-Zensur)

Suche in der App (Modelle → Hugging Face) nach:

- `abliterated` · `uncensored` · `heretic` · `dolphin` · `NSFW` · `roleplay`

Typische Quellen (jeweils GGUF-Varianten prüfen):

- <https://huggingface.co/models?search=abliterated%20gguf>
- <https://huggingface.co/models?search=uncensored%20gguf>
- <https://huggingface.co/models?search=dolphin%20gguf>
- <https://huggingface.co/models?search=nsfw%20roleplay%20gguf>

Hinweis: Abliterated-Modelle entfernen die eingebaute Verweigerungshaltung, nicht die
Verantwortung. Illegale Inhalte bleiben ausgeschlossen. Die App zeigt NSFW-Modelle erst
nach der 18+-Bestätigung in den Optionen.

## Bildmodelle (Katalog + Download; Runtime offen)

| Modell | Basis | Größe | Link |
|---|---|---|---|
| Stable Diffusion 1.5 | SD 1.5 | ~4 GB | [HF](https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-v1-5) |
| DreamShaper 8 | SD 1.5 | ~2.1 GB | [Civitai 4384](https://civitai.com/models/4384) |
| Realistic Vision V6.0 | SD 1.5 | ~2.1 GB | [Civitai 4201](https://civitai.com/models/4201) |
| SDXL Turbo | SDXL | ~6.9 GB | [HF](https://huggingface.co/stabilityai/sdxl-turbo) |
| Flux.1 Schnell | Flux | ~23 GB | nur 12-GB-Geräte, nicht empfohlen |

Für 8 GB RAM ist SD 1.5 (512×512, 20–30 Schritte) der einzige realistische Kandidat.
SDXL/Flux sprengen den Speicher.

## Video (Runtime in diesem Build noch nicht verdrahtet)

| Modell | Typ | Größe | Link |
|---|---|---|---|
| AnimateDiff Motion Adapter v1.5.2 | SD-1.5-Erweiterung, 16 Frames | ~1.7 GB | [HF](https://huggingface.co/guoyww/animatediff) |
| Stable Video Diffusion XT | Image-to-Video | ~9.6 GB | [HF](https://huggingface.co/stabilityai/stable-video-diffusion-img2vid-xt) |

## Musik & Audio (neuronale Runtime offen; lokaler Psytrance-DSP läuft)

| Modell | Aufgabe | Größe | Link |
|---|---|---|---|
| MusicGen Small | Text → Musik, 30 s | ~1.2 GB | [HF](https://huggingface.co/facebook/musicgen-small) |
| Stable Audio Open Small | Text → Loops/SFX, 11 s | ~1.4 GB | [HF](https://huggingface.co/stabilityai/stable-audio-open-small) |
| Whisper Tiny (ggml) | ASR | 75 MB | [HF](https://huggingface.co/ggerganov/whisper.cpp) |
| Bark Small | TTS + SFX | ~400 MB | [HF](https://huggingface.co/suno/bark) |
| MeloTTS | TTS mehrsprachig | ~200 MB | [HF](https://huggingface.co/myshell-ai/MeloTTS) |

## LoRA-Adapter

- **GGUF-LoRA** für llama.cpp: in der App unter Modelle → LoRA suchen, herunterladen,
  in den Optionen mit Stärke anwenden. Passt nur, wenn Basisarchitektur übereinstimmt.
- **safetensors-LoRA** für SD 1.5: über Civitai suchen (Typ „LORA“), Download mit
  Token, dann im Studio als Bild-LoRA wählen. LCM ist als 4-Step-Pfad verifiziert.
- **ControlNet**: SD1.5-ControlNet (Canny/Depth/OpenPose/Scribble) im Studio laden,
  Control-Bild wählen und Stärke setzen; Canny ist auf dem A56 getestet.
- Civitai-Beispiele: [Detail Tweaker](https://civitai.com/models/122359),
  [Add More Details](https://civitai.com/models/82098),
  [LCM LoRA](https://civitai.com/models/257749).

## Tokens

| Zweck | Wo erstellen | Wo eintragen |
|---|---|---|
| Gated/private HF-Modelle | <https://huggingface.co/settings/tokens> | Optionen → Konten |
| Civitai-Downloads/NSFW | <https://civitai.com/user/account> | Optionen → Konten |
| Gemini Cloud | <https://aistudio.google.com/apikey> | Optionen → Cloud |

## Rechtliches

- Modelllizenzen unterscheiden sich (Llama-Community-Lizenz, Gemma Terms, Apache-2.0,
  Civitai-Modellrechte). Vor kommerzieller Nutzung prüfen.
- NSFW-Inhalte nur für Volljährige; keine Darstellungen realer Personen ohne Einwilligung,
  keine Minderjährigen, kein Missbrauchsmaterial.
