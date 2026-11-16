# Design System: GalaxyA56AI Field Lab

**Project ID:** local build — Google Stitch MCP is not configured in this workspace.
This document was produced with the installed Stitch `design-md` and `taste-design` skills and maps the actual Compose theme in `ui/theme/Theme.kt`.

## 1. Visual Theme & Atmosphere

A **darkroom field lab**: graphite-green surfaces, one sharp chartreuse signal colour, serif headlines for the editorial/creative side, and monospace metadata for the instrument side. The interface should feel like a piece of measurement equipment that happens to make images and music — calm, dense, tactile, and honest about limits.

Density: **7/10** (cockpit-adjacent, but with breathing room around the hero).
Variance: **5/10** (left-aligned editorial headers, asymmetric status rows, no centred card grid).
Motion: **3/10** (restrained; progress and state changes, no decorative loops).
Temperature: **cool neutral green**, not blue-purple, not pure black.

The visual thesis is: **the A56's real constraints are part of the product**. NPU reach, RAM, thermals, and fallback state are shown as first-class information, not hidden in a settings grave.

## 2. Color Palette & Roles

- **Darkroom Ink** (`#0D100E`) — app background; near-black with a green cast, never `#000000`.
- **Workbench Graphite** (`#151916`) — primary surface, navigation, sheets.
- **Raised Graphite** (`#1C211D`) — secondary cards and grouped controls.
- **Instrument Panel** (`#262D27`) — highest surface, chips, inline code fields.
- **Hairline Moss** (`#354038`) — 1 px borders, dividers, input outlines.
- **Warm Paper** (`#F0EEE4`) — primary text; slightly warm to avoid a clinical white.
- **Muted Sage** (`#A2ADA4`) — secondary text, captions, metadata.
- **Signal Chartreuse** (`#D8F26B`) — the single accent: primary actions, active tabs, NPU/success states, focus.
- **Signal Wash** (`#2E3A1D`) — tinted accent container; selected rows and active chips.
- **Signal Ink** (`#F2FFC4`) — text on the accent wash.
- **Amber Alert** (`#E6A56F`) — warnings, thermal/experimental states.
- **Coral Failure** (`#FF8A7A`) — errors and destructive outcomes.

Rules: exactly one saturated accent. No purple/blue neon, no gradient text, no glow shadows. Warning and error colours are semantic, not decorative.

## 3. Typography Rules

- **Display / hero:** system serif (`FontFamily.Serif`) at 32 sp, normal weight, −0.4 sp tracking, 36 sp leading. Used only for the Studio's editorial headline ("Bilder aus Gedanken."). This is a deliberate creative-studio exception to the dashboard-serif ban; it is not used for dense settings tables.
- **Section headline:** serif 24 sp, normal, −0.2 sp tracking. One per screen, left-aligned.
- **Titles:** sans-serif semibold 19/16 sp. Clear hierarchy through weight, not size inflation.
- **Body:** sans-serif 15 sp / 23 sp leading; secondary body 14 sp / 21 sp. Max readable width on tablets; phone content uses full width with 16 dp gutters.
- **Metadata / labels:** monospace 10.5 sp, 0.2 sp tracking. Used for seeds, hashes, backend names, timing, model file sizes, and status. Monospace is the "instrument readout" voice.
- **Banned:** Inter, generic Times/Georgia-style body text, all-caps eyebrow labels, oversized 60 sp marketing headlines, gradient-filled type.

## 4. Component Stylings

- **Buttons:** primary is a chartreuse fill with near-black label (`#1A2108`), gently rounded (Material default, ~20 dp full pill for mode tabs). Secondary is a hairline outline on graphite. No glow. Press state is a tactile darkening, not a scale bounce.
- **Mode tabs:** pill-shaped, 7 dp vertical / 14 dp horizontal padding, accent fill when active, graphite when idle. They sit in a single horizontal row with a text-only "Handbuch" action pushed to the end.
- **Cards:** 14–16 dp softly rounded corners, `Workbench Graphite` or `Raised Graphite` fill, 1 px moss border only where separation is needed. No drop shadows; depth comes from surface value and hairlines. High-density lists use dividers or spacing instead of stacked cards.
- **Inputs:** filled dark fields with a 1 px moss outline; chartreuse focus border. Labels sit above the value (Material floating label is acceptable here). Helper text is monospace and muted.
- **Progress:** linear chartreuse bar with a percentage/step label; no indeterminate spinner as the only feedback for long jobs.
- **Status chips:** small rounded rectangles; active/ready uses signal wash + chartreuse text, warnings use amber, errors use coral. Never rely on colour alone; pair with a word.
- **Images:** preview cards crop to 154 dp height with the top corners rounded to match the card. Generated images use a 16 dp rounded surface and fill width. Every remote preview degrades to a textual "Vorschau nicht verfügbar" state.
- **Empty states:** a short, specific sentence plus the next action; no generic "No data" and no fake metrics.

## 5. Layout Principles

- Phone-first single column, 16 dp outer gutters, 8/12/14/20 dp vertical rhythm.
- Editorial header is **left-aligned**, never centred. A single hero per screen.
- Status rows are asymmetric: label on the left, real value on the right, monospace for numbers.
- No overlapping text or images. Every element owns its spatial zone.
- Maximum one primary CTA per section. Secondary actions are text buttons or outlines.
- Dense sections (Settings, Models) may use compact cards, but never a 3-equal-card feature row.
- Touch targets ≥ 44 dp; sliders have adjacent numeric readouts so the value is legible without dragging.
- Long lists are `LazyColumn`/`LazyRow`; no nested vertical scrolling inside a scrollable parent for large data sets.

## 6. Motion & Interaction

- State transitions use short fades and progress updates, not perpetual motion.
- Long-running jobs expose real stages: load → sample step → decode → save. The UI must never pretend work is instantaneous.
- Error and fallback transitions are logged and surfaced in the status line (e.g. Vulkan → CPU retry).
- Animations are limited to `alpha`, `transform`, and progress values; no layout thrash.

## 7. Anti-Patterns (Banned)

- No emojis in product UI.
- No generic AI-purple gradients, neon glows, or "magic" sparkle icons.
- No pure black `#000000`; use Darkroom Ink.
- No invented benchmark numbers, fake uptime, or decorative "metrics" cards.
- No 3-column equal feature grids.
- No overlapping headline/image collages.
- No fake sample images or broken Unsplash links; real Civitai/HF previews or an explicit unavailable state.
- No invisible failure: every fallback, missing model, or unsupported NPU path gets a plain-language message.
- No all-caps micro-labels; use monospace sentence case.
- No "Elevate / Unleash / Next-Gen" copy. Prefer concrete verbs: "erzeugen", "laden", "prüfen", "verwerfen".

## 8. Implementation Notes

- Tokens live in `app/src/main/java/com/galaxya56/ai/ui/theme/Theme.kt`.
- The theme is dark-only by design; a light mode is not part of this product direction.
- Stitch MCP (`create_project`, `generate_screen_from_text`, `get_screen`) needs an API key and is not available in this workspace. The installed skills were used as the design brief and review rubric; the Compose implementation is the source of truth.
