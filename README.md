# WaifuVerse

A native Android (Kotlin + Jetpack Compose) voice companion app. Talk to anime
companions in any language through the **Gemini Live API**, with a lip-synced
character on screen, editable personalities, conversation memory and history.
Everything the app creates lives inside its own sandbox folder in a SQLite
database.

Target device: Xiaomi Redmi Note 8 Pro (1080 × 2340, 19.5:9), portrait only.
Minimum Android 8.0 (API 26), target API 35. Package: `com.vislupus.waifuverse`.

---

## Quick start

1. Install **Android Studio** (it includes the Android SDK and `adb`) and enable
   *USB debugging* on the phone.
2. Clone the repo and open the folder in Android Studio once (it downloads the
   Gradle wrapper and dependencies), **or** just run the commands below.
3. In the app: *Settings → Gemini API key* (get one at
   https://aistudio.google.com/apikey).

### One-word commands

| Command        | What it does                                                      |
|----------------|-------------------------------------------------------------------|
| `make dev`     | build + install on the connected phone + launch + live logs       |
| `make run`     | build + install + launch                                          |
| `make build`   | compile a debug APK → `app/build/outputs/apk/debug/app-debug.apk` |
| `make release` | compile a release APK (signed with the debug key so it installs)  |
| `make watch`   | rebuild + reinstall + relaunch automatically on every file change |
| `make logs`    | show the app's log output                                         |
| `make preview` | open the project in Android Studio for Compose previews           |
| `make sprites` | regenerate character sprites from `tools/raw_sprites`             |
| `make icons`   | regenerate the Lucide icon file from `tools/lucide_svg`           |

The same scripts exist as `scripts/*.sh` (macOS/Linux/Git Bash) and
`scripts/*.ps1` (Windows PowerShell), e.g. `./scripts/dev.sh` or
`.\scripts\dev.ps1`. Plain Gradle works too: `./gradlew installDebug`.

### Hot reload / previews

* **Live Edit** (true hot reload): in Android Studio run the app once
  (▶ or `make run`), then every edit to a Composable is pushed to the phone
  automatically. Enable it under *Settings → Editor → Live Edit* if it is off.
* **Compose previews** (no phone needed): open
  `app/src/main/java/com/vislupus/waifuverse/ui/Previews.kt` and click the
  *Split* / *Design* tab. Each screen has a preview in light/dark and in
  English/Bulgarian.
* **From the terminal**: `make watch` rebuilds and reinstalls on every change.

---

## Where to change things (the config files)

You should never need to dig through the code. Everything that controls
behaviour, looks, texts, characters and models is in
`app/src/main/java/com/vislupus/waifuverse/config/`:

| File                   | Controls                                                                                  |
|------------------------|-------------------------------------------------------------------------------------------|
| `AppConfig.kt`         | behaviour: defaults, storage paths, memory limits, lip-sync thresholds, animation, timeouts |
| `ThemeConfig.kt`       | accent colours, light/dark palettes, font sizes, radii, spacing, button sizes             |
| `TextsConfig.kt`       | every UI string, in English and Bulgarian (add a language here)                           |
| `CharactersConfig.kt`  | the waifus (name, title, voice, background, accent, personality), prompt template, rooms  |
| `ModelConfig.kt`       | Gemini model chain + fallbacks, voices, generation defaults, audio settings               |

Assets:

* `app/src/main/assets/characters/<id>/` – `body.webp`, `mouth_0..3.webp`,
  `sprite.json` (generated, see below)
* `app/src/main/assets/backgrounds/<id>.webp` – rooms
* `tools/raw_sprites/` – the original frames the generator starts from

User-changeable settings (theme, accent, font size, language, API key, model,
audio mode, memory default) are on the Settings page and stored in the
database. Their defaults come from the config files.

---

## Characters that do not jitter

The original mouth frames were generated as separate images and are shifted by
a few pixels relative to each other, so swapping whole frames made the
character "wobble". `tools/prepare_sprites.py` fixes this once, offline:

1. `mouth_0` becomes the static **body** image.
2. Every other frame is aligned to the body (best integer shift).
3. The mouth rectangle is detected as the only *solid* region that still
   differs after alignment (thin edge lines are eroded away).
4. That rectangle is cut from every aligned frame, its edges are feathered
   into the body, and saved as a small **mouth patch**.

At runtime the app draws the body once and only swaps the small patch, so
nothing except the mouth can move. The idle "breathing" animation moves body
and mouth together.

To add a character: put `mouth_0..3.webp` (transparent background, identical
size, closed → wide open) in `tools/raw_sprites/characters/<id>/`, run
`make sprites`, then add an entry in `CharactersConfig.kt`.

Requires `pip install pillow numpy`.

---

## Voice pipeline

* Microphone → PCM16 16 kHz → Gemini Live websocket (`realtimeInput.audio`)
* Gemini → PCM16 24 kHz → `AudioTrack`; the loudness of what is *currently
  heard* drives the mouth frame (lip sync stays in sync with playback, not with
  network arrival)
* Models are tried in the order of `ModelConfig.MODEL_CHAIN`
  (`gemini-3.8-live` → `gemini-3.1-flash-live-preview` → …). The first entry
  can be overridden from Settings. If a model is rejected the next one is used
  automatically and a notice appears in the chat.
* Interruptions: when you talk over her, playback is flushed instantly.
* Session resumption: when Gemini announces the session is ending
  (`goAway`), the app reconnects transparently with the resumption handle.
* Audio mode (Settings): **Voice call** uses the phone-call audio path with
  hardware echo cancellation (recommended hands-free); **Media** gives the
  highest-fidelity playback and can be combined with *mute mic while she
  speaks*.

## Memory and history

* Each chat is a *conversation*. **Remembered** conversations (bookmark icon on
  the stage) are injected as memory into every future session with the same
  companion (`AppConfig.MEMORY_MAX_MESSAGES` / `MEMORY_MAX_CHARS`).
* **Temporary** conversations are kept in History but never used as memory.
* Open any conversation from History to continue it — its transcript is
  re-sent as context.

## Storage

Everything is in the app's private files directory:
`/data/user/0/com.vislupus.waifuverse/files/data/waifuverse.db`
(SQLite: `settings`, `waifus`, `conversations`, `messages`). No external
storage is used. Uninstalling the app removes all data.

## Project layout

```
app/src/main/java/com/vislupus/waifuverse/
├─ config/        ← all tunables, texts, characters, models (edit these)
├─ data/          ← SQLite (AppDatabase), models, repository
├─ live/          ← Gemini Live websocket, audio in/out, session manager
├─ sprite/        ← sprite + background loader
├─ ui/
│  ├─ theme/      ← Material 3 theme built from ThemeConfig
│  ├─ icons/      ← Lucide icons as ImageVectors (generated)
│  ├─ components/ ← shared widgets, the character stage
│  ├─ screens/    ← Chat, Persona, History, Settings
│  ├─ AppRoot.kt  ← bottom navigation
│  └─ Previews.kt ← Android Studio previews
├─ MainViewModel.kt
├─ MainActivity.kt
└─ WaifuVerseApp.kt
tools/            ← prepare_sprites.py, lucide_to_kotlin.py, raw assets
scripts/          ← dev/run/build/watch/logs/preview scripts
```

## Icons

Icons are from [Lucide](https://lucide.dev) (ISC license), converted to
Compose `ImageVector`s by `tools/lucide_to_kotlin.py` so the app has no icon
dependency. To add an icon, drop its SVG into `tools/lucide_svg/` and run
`make icons`; it becomes `Lucide.<Name>`.
