# Unplug — by Janak

A minimal, futuristic, black-and-white **home screen launcher** for Android with a built-in, fully offline focus mode and digital-wellbeing tools.
Start a session (or let a schedule start it) → everything is locked except **Phone**, **WhatsApp** and the apps you allow, until it ends.

## The launcher

| Gesture | What happens |
|---|---|
| Swipe **up** (or tap ︿) | App drawer slides up: search, A–Z sections, letter scrubber on the right |
| Swipe **down** on home | Notification shade (needs the Unplug blocker turned on) |
| Pull the drawer list down from the top / drag its handle | Drawer springs closed |
| Tap an app | Opens it, animating out of the row you tapped |
| Long-press an app | Pin to home / Remove, App info, Uninstall |
| Long-press empty home, or ⚙ in the drawer | Launcher settings, Your stats, Choose home app, Focus blocker |
| Tap the clock | Alarms |
| Centre dock button ⏻ | Start a focus session |
| Home / Back | Close whatever is open; never leaves the launcher |

On first launch, a 4-step welcome walks through: set as home screen → turn on the blocker → optional extras.

## Focus mode

- Sessions last up to **8 hours** (presets 15m, 30m, 1h, 2h, 4h, 8h), started by hand or by a **schedule** (e.g. 22:00 → 06:00 every night).
- The home screen becomes a countdown ring. Only Phone, WhatsApp and your allowed apps open. The drawer is gone.
- Other apps are sent back home, including when opened from notifications or recents. Settings and the app installer are always blocked.
- **Power menu locked:** power off / restart is closed the moment it opens, unless the battery is below your threshold (default 5%, not charging). A hardware force-restart (holding power for about 10 seconds) can't be blocked by any app; after one, focus resumes.
- **Notifications silenced:** other apps' notifications are snoozed until the session ends (they come back afterwards). Needs notification access.
- **SOS:** the SOS pill on the focus screen dials your emergency number (default 112, changeable) or opens the phone's emergency dialer.
- **Emergency unlock** (off by default, enable in settings): hold **both volume keys for 5 seconds** → enter your password → the session ends. Time already focused still counts.
  - Default password: **`unplug@#2026`**. Change it in Launcher settings → Focus → Unlock password (needs the current one).
  - Custom passwords are stored only as a salted PBKDF2 hash. Three wrong tries → 30 s wait, growing each time.
  - If the blocker was already on before this update, switch it off and on once so Android grants the volume-key permission.
- **Uninstall protection** (optional): Unplug becomes a device admin with no powers except that it can't be uninstalled until you turn the protection off. Settings is blocked during focus, so it can't be turned off mid-session.

## Wellbeing (any time, not just during focus)

| Feature | Settings you can change |
|---|---|
| **Daily limits**: an app closes for the rest of the day once its limit is used | Per-app limit, 5 min – 8 h (needs usage access) |
| **Pause before opening**: a breathing screen before chosen apps open | Which apps · pause length 5/10/15/30 s · don't ask again for 1/5/15/30 min |
| **Hidden apps**: removed from drawer and search | Which apps |
| **Stats**: current/best streak, last-7-days chart, day list, totals | Daily focus goal 15m – 4h (a day "counts" when it's met) |

## Launcher settings

| Section | Options |
|---|---|
| Appearance | Theme (Auto / Dark / Light) · Font (Modern, Condensed, Serif, Mono, Script, + Space if bundled) · App icons (Mono / Color / Off) · Home text size · Hidden apps |
| Watch | Face (Ring, Analog, Digital, Orbit) with live preview · Weight · Hour format · Show seconds / date / battery |
| Focus | Allowed apps · Schedules · Lock power menu + low-battery threshold · Silence notifications · SOS number · Emergency unlock · Unlock password · Uninstall protection |
| Wellbeing | Daily limits · Pause before opening · Pause length · Grace period · Daily goal · Your stats |
| General | Haptic feedback · Home app · Phone settings · Show welcome again |
| About | unplug. by Janak, version, offline statement |

Settings can't be opened during a session, so nothing can be loosened mid-session.

## Widget

For other launchers or a second home screen: a black **Unplug widget** shows the live focus countdown (it ticks by itself), or today's focused time with "Tap to focus".

## Optional font: Space Grotesk

Unplug can't download anything, so the font ships inside the app. On your build computer, put `SpaceGrotesk.ttf` in `app/src/main/assets/fonts/` (instructions in the README there). The **Space** option appears automatically. Without the file, it's hidden.

## Code map

| File | What |
|---|---|
| `MainActivity.kt` | The HOME activity |
| `Launcher.kt` | Home screen, dock, drawer physics, menus, overlays wiring |
| `Drawer.kt` | App drawer, search, letter scrubber |
| `FocusSheet.kt` | Duration picker + blocker setup |
| `Overlays.kt` | SOS, emergency unlock, pause screen, stats + weekly chart |
| `Onboarding.kt` | First-launch walkthrough |
| `Glyphs.kt`, `Clocks.kt` | Tick ring, analog, digital and orbit faces |
| `Settings.kt`, `SettingsScreen.kt`, `Fields.kt` | Settings model/storage, settings pages, form controls |
| `Theme.kt` | Light/dark palette, fonts, buttons, switch, press animation |
| `Apps.kt` | Installed apps + icon cache, pins, default-home check, actions |
| `FocusSession.kt` | Manual + scheduled sessions, early end |
| `BlockerService.kt`, `AllowList.kt` | Blocking, power menu, volume-key unlock, limits, pauses |
| `NotificationSilencer.kt` | Snoozes notifications during focus |
| `Wellbeing.kt` | Stats, usage tracking, passes, uninstall protection |
| `UnlockPassword.kt` | Emergency-unlock password (hashed) |
| `FocusWidget.kt` | Home-screen widget |

## Build & install

1. Install **Android Studio**. **File → Open** → this `Unplug` folder, then let Gradle sync.
2. Connect your phone with USB debugging on and press **Run ▶**.
3. Follow the welcome walkthrough: set as home screen, turn on the blocker, optional extras.
   If the Accessibility switch is greyed out (sideloaded apps on Android 13+): **Settings → Apps → Unplug → ⋮ → Allow restricted settings**.

To switch back to your old launcher: long-press empty home → **Choose home app**.

## Publishing on the Play Store

- Build a signed bundle: **Build → Generate Signed App Bundle**, then upload it in Play Console.
- Expect a close review. Declarations needed in Play Console:
  - **Accessibility API**: blocks distracting apps during user-started focus sessions, applies user-set app limits.
  - **Usage access** (`PACKAGE_USAGE_STATS`): daily app limits.
  - **Device admin**: only for user-enabled uninstall protection; no policies are used.
- Publish a one-page privacy policy: no data collected, no internet permission.
- Installs from Play don't hit the "restricted settings" lock.

Works on Android 8.0+ (API 26). **Offline:** the manifest removes `INTERNET`; everything is stored on the phone.
