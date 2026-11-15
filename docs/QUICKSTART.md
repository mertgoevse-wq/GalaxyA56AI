# Quick Start

## 1. APK installieren

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Oder die fertige `app-debug.apk` direkt auf das A56 kopieren und dort öffnen
(Installation aus unbekannten Quellen erlauben).

## 2. Modell laden

1. **Modelle → Katalog**: z. B. „Qwen2.5 Coder 1.5B“ (~1.1 GB) oder
   „Llama 3.2 1B“ (~808 MB) antippen → Download.
2. Alternativ **Hugging Face**: Suchbegriff eingeben (`gguf`, `abliterated`, `dolphin`, …),
   Treffer öffnen, Datei wählen (`Q4_K_M` empfohlen).
3. **Civitai**: für Checkpoints/LoRAs; für Downloads einen API-Token in
   **Optionen → Konten** eintragen.

## 3. Chatten

1. **Chat** öffnen, unten auf den Modell-Chip tippen und das installierte GGUF wählen.
2. System-Prompt über den zweiten Chip wählen (`Coding`, `App-Builder`, `Direkt …`).
3. Nachricht senden. Tokens streamen; darunter stehen Tokens, tok/s und Prompt-Zeit.
4. **Stop** bricht die Generierung sofort ab.

## 4. Cloud (optional)

- API-Key von <https://aistudio.google.com/apikey> holen.
- **Optionen → Cloud**: Key einfügen, `gemini-2.5-flash` wählen, Schalter aktivieren.
- Danach im Chat den Modell-Chip → „Gemini Cloud“.
- **Studio → Bild**: Prompt eingeben → „Bild generieren“ (nutzt `gemini-2.5-flash-image`).

## 5. LoRA anwenden

1. **Modelle → LoRA**, nach einem GGUF-Adapter suchen und laden.
2. Sicherstellen, dass das Basismodell geladen ist.
3. **Optionen → LoRA-Adapter**: Stärke einstellen, „Anwenden“.
4. „Alle LoRAs entfernen“ setzt zurück.

## 6. Tuning

| Einstellung | Wirkung |
|---|---|
| Kontext 2048 | schneller, weniger RAM |
| Kontext 8192 | längere Chats, mehr RAM/thermische Last |
| Threads 4 | guter Kompromiss auf 10 Kernen |
| Temperatur 0.2 | deterministisch (Code) |
| Temperatur 0.9 | kreativer (Texte) |
| max. Tokens | begrenzt Antwortlänge pro Turn |

## Fehlersuche

```bash
# Logs der nativen Engine
adb logcat -s A56AI-LLM A56AI-HW A56AI-JNI

# Installierte Modelle
adb shell ls /sdcard/Android/data/com.galaxya56.ai/files/models

# Absturzbericht
adb logcat -b crash
```

- **„Kein lokales Modell installiert“** → Datei fehlt oder Download unvollständig
  (`.part`). Erneut laden.
- **Laden schlägt fehl** → Format prüfen (GGUF v2/v3), RAM freimachen, kleineres Quant.
- **Sehr langsam** → Kontext/Threads senken, Thermik prüfen, 1B statt 3B nutzen.
- **Civitai 401/403** → Token fehlt oder hat keine Download-Rechte.
