# AGENTS.md — Launchpad

Instructions for AI coding agents (Claude, Codex, Copilot, Cursor, etc.) working in this project.
Read this whole file before making changes, and keep it up to date when the stack, commands or conventions change.

## What this project is

Launchpad is a personal web app for daily life management. It brings several small tools together in one place:

| Tool | Status | Notes |
|---|---|---|
| **Focus timer** | Prototype built | Visual countdown dial. Its own page. |
| **Task breaker** | Prototype built | Breaks a big or stuck task into small first steps. Its own page. |
| **Medication reminders** | Not started | Morning and evening reminders. A dose counts as taken only when the owner taps a physical NFC sticker with their phone. |
| **Visual planner** | Not started | Calendar view in the style of Google Calendar, ideally synced with Google Calendar. |

Out of scope for now: finding misplaced items ("where's my stuff").

## Who you're working with

- The owner is a senior mechanical engineer who is **new to software and networking** and is learning as they go.
- Explain what you changed and why in plain language. Define jargon the first time you use it. Engineering analogies are welcome.
- Prefer simple, readable code over clever code. Add short comments that explain *why* something is done.
- When there are several reasonable approaches, briefly lay out the tradeoffs and recommend one. Don't silently pick a complex one.

## Current state of the code

Everything lives in one file today: **`launchpad.html`** (plain HTML, CSS and JavaScript, no build step, no dependencies).

- **Pages:** hash routes inside the one file. `#timer` shows the Focus timer, `#breakdown` shows the Task breaker. The `route()` function shows and hides the two `<section class="view">` blocks.
- **Saved data:** browser `localStorage`, under the keys `lp.timer` (last timer length) and `lp.steps` (the current task and its steps). Access always goes through the `store` helper, which fails quietly if storage is blocked. Nothing leaves the device.
- **Timer:** counts against a fixed end time (`endAt`) instead of counting ticks, so it stays accurate if the phone throttles the page. When time runs out it plays a chime (Web Audio) and vibrates.
- **Task breaker AI:** the "Break it down" button calls `window.claude.use('sample')`. That API only exists when the page is hosted as a claude.ai artifact. Opened anywhere else, the page shows a clear "not available" message and the owner can still add steps by hand. **If the app moves to self-hosting, this needs a replacement** (for example a small backend that calls an AI API with a key kept in `.env`). Ask the owner before choosing one.
- **Fonts:** Chakra Petch and Rajdhani from Google Fonts.

When the app grows past this single file (medication, planner), propose a folder structure first and ask before changing it.

## Target platform

- Main device: **Android phone** (Chrome). Design mobile-first, then check it works on desktop.
- NFC: use the **Web NFC API** (`NDEFReader`), which works in Chrome on Android only, and only on a secure page (`https://` or `localhost`). Always provide a fallback, such as a manual "I took it" button.
- Long term it may be self-hosted on a home server (mini PC, possibly behind Cloudflare). Avoid choices that lock the app to one hosting provider.

## Design / look and feel

- **Cyberpunk HUD theme:** black and graphite backgrounds, glowing **lime-green accents** on buttons, highlights and active states, one hot magenta for alerts. Angled (clipped) corners on buttons and panels, faint grid and scanline overlay.
- All colors are CSS variables in the `:root` block at the top of `launchpad.html`. Use those variables; don't hard-code new colors. Current tokens:
  - `--bg: #07080a`, `--panel: #111316`, `--panel2: #181b1f`, `--line: #2a2f35` (borders)
  - `--ink: #e8ece6` (text), `--muted: #8a929a` (secondary text)
  - `--lime: #c6ff00` (accent), `--lime2: #8fbf00` (dim accent), `--hot: #ff2e88` (alerts)
  - `--glow`: the lime glow shadow
- Keep text readable: aim for WCAG AA contrast. Tap targets at least 44×44 px.
- Respect `prefers-reduced-motion` (the timer already does).
- Each tool gets its **own page/route**.

## Project location

- Local path: `C:\Users\natha\Documents\Projects\Launchpad` (Windows).
- Give terminal commands for **Windows PowerShell** unless told otherwise.

## Tech stack

- Frontend: plain HTML, CSS and JavaScript in `launchpad.html`. No framework yet. **Ask before adding one.**
- Backend / data storage: none yet. Data is in the browser's `localStorage`. (Firebase was mentioned as an option; not decided.)
- Hosting: none yet. Candidates are a local home server and Cloudflare.

## Commands

- Install dependencies: none needed.
- Run locally: double-click `launchpad.html` to open it in a browser. For phone testing or Web NFC you'll need to serve it over `https://` or `localhost`. Ask before adding a local server tool.
- Run tests: no automated tests yet. Test by hand in Chrome on desktop and on the Android phone.

## Rules for agents

1. **Ask before** adding a new dependency, framework or paid service, or changing the project structure.
2. **Never commit secrets.** API keys, Google credentials and Firebase config secrets go in a `.env` file that is listed in `.gitignore`.
3. **Make small, focused changes.** One feature or fix at a time, so each one is easy to review and undo.
4. **Reminders must be reliable.** Missed medication reminders are the worst failure mode.
   - Handle errors visibly, never silently. If notifications or NFC aren't available, tell the user clearly on screen.
   - A plain web page can't fire a reminder once its tab is closed or the phone is asleep. Reliable reminders will need something like a service worker with push notifications, or a backup outside the browser. Explain this tradeoff to the owner before building reminders.
5. **Privacy:** medication data is personal. Store as little as needed and don't send it to third-party services (including AI APIs) without asking.
6. **Don't break the fallbacks.** Every feature that depends on a special browser API (NFC, notifications, AI, audio, vibration) must still leave the owner a working manual path.
7. After each change, summarize: what changed, which files were touched, how to test it, and anything the owner should learn from it.
