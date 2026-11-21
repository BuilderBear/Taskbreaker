# AGENTS.md — Taskbreaker

Instructions for AI coding agents (Claude, Codex, Copilot, Cursor, etc.) working in this project.
Read this whole file before making changes, and keep it up to date when the stack, commands or conventions change.

## What this project is

Taskbreaker is a personal **native Android app** for daily life management. It brings several small tools together in one place:

| Tool | Status | Notes |
|---|---|---|
| **Focus timer** | Built (Android) | Visual countdown dial. Its own page. |
| **Breakdown** (task breaker) | Built (Android) | Breaks a big or stuck task into small first steps, using Claude. Its own page. |
| **Medication reminders** | Not started | Morning and evening reminders. A dose counts as taken only when the owner taps a physical NFC sticker with their phone. |
| **Visual planner** | Not started | Calendar view in the style of Google Calendar, ideally synced with Google Calendar. |

Out of scope for now: finding misplaced items ("where's my stuff").

## Who you're working with

- The owner is a senior mechanical engineer who is **new to software and networking** and is learning as they go.
- Explain what you changed and why in plain language. Define jargon the first time you use it. Engineering analogies are welcome.
- Prefer simple, readable code over clever code. Add short comments that explain *why* something is done.
- When there are several reasonable approaches, briefly lay out the tradeoffs and recommend one. Don't silently pick a complex one.

## Project layout

```
android/                      The Android app (open this folder in Android Studio)
  app/build.gradle.kts        App settings and library list (dependencies)
  app/src/main/AndroidManifest.xml   Permissions and app components
  app/src/main/java/com/builderbear/taskbreaker/
    MainActivity.kt           App container: top bar, bottom tabs, switches pages
    ui/Theme.kt               ALL colors and shapes (TbColors)
    ui/Components.kt          Shared buttons, cards, headings, text-field colors
    timer/                    Focus timer: screen, ViewModel, system alarm + notification
    breakdown/                Breakdown: screen, ViewModel, Step data
    ai/ClaudeClient.kt        The Claude API call and its prompt
    settings/                 Settings screen and API-key storage
web/taskbreaker.html          The original single-file web prototype, kept for reference
```

## How the Android app works

- **Language and UI:** Kotlin with Jetpack Compose (screens are written as Kotlin functions marked `@Composable`).
- **Pages:** three bottom tabs (Timer, Breakdown, Settings), switched by the `page` variable in `MainActivity.kt`. No navigation library.
- **ViewModels** (`TimerViewModel`, `BreakdownViewModel`) hold each page's data and logic. Screens only display state and call ViewModel functions.
- **Saved data:** Android `SharedPreferences` (small private key-value files on the phone), one file per feature: `timer`, `breakdown` (steps stored as JSON text) and `settings` (the API key). Nothing is sent anywhere, except the Claude call below.
- **Timer reliability:** a running timer stores its finish time, not a tick count. When it starts, `TimerAlarm` also schedules a system alarm (`AlarmManager.setAlarmClock`), so the "time's up" notification fires even if the app is closed or the phone is asleep. If notifications are off or only an inexact alarm is allowed, the timer screen shows a magenta warning. Alarms are lost if the phone reboots. That's fine for the timer, but medication reminders must handle it.
- **Breakdown AI:** `ClaudeClient` calls the Claude API with the official Anthropic Java SDK (`com.anthropic:anthropic-java`), model `claude-opus-5-5` at low effort. The owner types their own API key into Settings. It's stored only on the phone and kept out of backups (`allowBackup="false"`). Only the task text is sent. With no key, or on any error, the screen shows a plain message and adding steps by hand still works.

## Target platform

- Main device: **Android phone**. `minSdk` 26 (Android 8.0), `targetSdk` 36, `compileSdk` 37 (required by current AndroidX libraries).
- NFC (for medication): use Android's native NFC APIs. Always provide a fallback, such as a manual "I took it" button.
- Long term, any server parts may be self-hosted on a home server (mini PC, possibly behind Cloudflare). Avoid choices that lock the app to one hosting provider.

## Design / look and feel

- **Cyberpunk HUD theme:** black and graphite backgrounds, glowing **lime-green accents** on buttons, highlights and active states, one hot magenta for alerts. Angled (cut) corners on buttons and cards.
- All colors live in `TbColors` in `ui/Theme.kt`. Use those; don't hard-code new colors. Current tokens:
  - `Bg #07080A`, `Panel #111316`, `Panel2 #181B1F`, `Line #2A2F35` (borders), `Field #0B0D0F`
  - `Ink #E8ECE6` (text), `Muted #8A929A` (secondary text), `Faint #5D656C`
  - `Lime #C6FF00` (accent), `Lime2 #8FBF00` (dim accent), `Hot #FF2E88` (alerts)
- Use the shared `PrimaryButton`, `SecondaryButton` and `Panel` from `ui/Components.kt` so everything matches.
- Keep text readable: aim for WCAG AA contrast. Tap targets at least 48×48 dp.
- Each tool gets its **own page**.

## Project location

- Local path: `C:\Users\natha\Documents\Projects\Launchpad` (Windows). The folder still has the old name. The app and the GitHub repo (`BuilderBear/Taskbreaker`) are called Taskbreaker.
- Give terminal commands for **Windows PowerShell** unless told otherwise.

## Tech stack

- App: Kotlin + Jetpack Compose (Material 3), built with Gradle 9.8.2 (the Android build tool) and Android Gradle Plugin 9.5.0, which has Kotlin built in (don't add the `org.jetbrains.kotlin.android` plugin). Compose compiler plugin 2.4.21.
- Libraries: AndroidX core, activity-compose, lifecycle (ViewModel), Compose BOM, Anthropic Java SDK. **Ask before adding more.**
- Backend: none. Data stays on the phone.

## Commands

- Installed on this PC: Android Studio (`C:\Program Files\Android\Android Studio`, its bundled Java 25 is in `jbr\`) and the Android SDK (`%LOCALAPPDATA%\Android\Sdk`). `android\local.properties` points Gradle at the SDK. It's per-machine and git-ignored.
- Build from the terminal (PowerShell, from `android\`). Gradle needs to know where Java is, so set it for the session first:
  - `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`
  - `.\gradlew.bat assembleDebug` → builds `app\build\outputs\apk\debug\app-debug.apk`
- Run on the phone: open the `android/` folder in Android Studio, plug in the phone with USB debugging on (or use an emulator), and press the green **Run** button. Or, from the terminal, `.\gradlew.bat installDebug`.
- Tests: no automated tests yet. Test by hand on the phone.
- Windows gotcha: when calling `.bat` tools (like `sdkmanager.bat`) from PowerShell, arguments containing `;` get split. Run them through `cmd /c "..."` instead.

## Rules for agents

1. **Ask before** adding a new dependency, framework or paid service, or changing the project structure.
2. **Never commit secrets.** API keys and credentials never go in code or in git. The Claude key is entered in the app's Settings. Any build-time secrets go in a `.env` or `local.properties` file, both of which are git-ignored.
3. **Make small, focused changes.** One feature or fix at a time, so each one is easy to review and undo.
4. **Reminders must be reliable.** Missed medication reminders are the worst failure mode.
   - Handle errors visibly, never silently. If notifications, exact alarms or NFC aren't available, tell the user clearly on screen (see the timer's warning banner for the pattern).
   - Use system alarms (`AlarmManager`), not in-app timers, and re-schedule after a reboot (`BOOT_COMPLETED`) before calling reminders done.
5. **Privacy:** medication data is personal. Store as little as needed and don't send it to third-party services (including AI APIs) without asking.
6. **Don't break the fallbacks.** Every feature that depends on something that can be missing or fail (NFC, notifications, AI, network) must still leave the owner a working manual path.
7. After each change, summarize: what changed, which files were touched, how to test it, and anything the owner should learn from it.
