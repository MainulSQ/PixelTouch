# PixelTouch — AssistiveTouch-style quick toggles for Pixel 7 Pro

A small Android Studio project (Kotlin) that draws a draggable floating
bubble, like AssistiveTouch on iPhone / Samsung, which expands into a quick
menu for: **Wi‑Fi, Mobile data, Sound mode, Hotspot, Battery saver**. It also
ships five matching **Quick Settings tiles** as an alternative, bubble-free
way to reach the same toggles from the notification shade.

## Before you build: what Android actually allows

Google has locked down most of these controls for regular (non-system) apps
over the last few Android releases, so it's worth knowing exactly what you're
getting before you install this on your Pixel 7 Pro:

| Control | Behaviour in this app | Why |
|---|---|---|
| **Sound mode** (Normal → Vibrate → Silent) | True one-tap toggle, no screen change | Allowed via `AudioManager`, once you grant "Do Not Disturb access" (a one-time permission) |
| **Hotspot** | True one-tap on/off with a fixed name & password | Uses `WifiManager.startLocalOnlyHotspot()` with a custom `SoftApConfiguration` (Android 11+). Needs Location permission — that's an Android platform requirement for any Wi‑Fi AP API, not something specific to this app |
| **Wi‑Fi** | Opens a small inline switch panel (one extra tap) | Since Android 10, apps can no longer flip Wi‑Fi on/off directly — only system/carrier apps can. This is the closest compliant alternative Google provides |
| **Mobile data** | Opens the same inline panel (one extra tap) | There has never been a public API for a regular app to toggle mobile data |
| **Battery saver** | Opens the Battery Saver settings screen (one extra tap) **by default** — becomes a true instant toggle if you run the optional one-time `adb` command below | `PowerManager` has no public setter; writing `Settings.Global` directly needs the `WRITE_SECURE_SETTINGS` permission, which the Play Store won't grant automatically but `adb` can, since it's your own device |

None of this is a limitation of the app's code — it's how Android is
designed since Android 10/11, to stop apps from silently messing with your
connectivity or battery settings in the background. The two "one extra tap"
items still save you several taps versus digging through Settings, and
Wi-Fi/mobile data land you on a switch, not a search.

## Project layout

```
PixelTouch/
  app/src/main/java/com/shihan/pixeltouch/
    MainActivity.kt          – onboarding screen: permissions checklist, start/stop
    OverlayService.kt        – the floating bubble + expandable menu
    BootReceiver.kt          – restarts the bubble after reboot if it was running
    toggles/                 – one helper object per control (Wifi, MobileData, Sound, Hotspot, BatterySaver)
    tiles/                   – 5 TileService classes for the Quick Settings shade
  app/src/main/res/          – layouts, vector icons, colors, strings
```

## Build & install on your Pixel 7 Pro

1. Install **Android Studio** (Koala/2024.1 or newer) if you don't have it.
2. `File → Open`, select the `PixelTouch` folder. Let Gradle sync — Android
   Studio will offer to generate the Gradle wrapper automatically the first
   time (this project intentionally ships without a pre-built wrapper jar to
   keep the download small; just accept the prompt, or click the "Sync
   Project with Gradle Files" elephant icon).
3. On the Pixel 7 Pro: **Settings → About phone → tap "Build number" 7
   times** to enable Developer options, then **Settings → System →
   Developer options → USB debugging → on**.
4. Connect the phone by USB, allow the debugging prompt on the phone, select
   it as the run target in Android Studio, and press **Run ▶**.
5. On first launch, PixelTouch shows a checklist. Grant, in order:
   - **Display over other apps** (required — this is what lets the bubble float over everything)
   - **Do Not Disturb access** (for the sound toggle)
   - **Location** (for instant hotspot on/off — optional, falls back to opening Settings if skipped)
   - **Notifications** (Android 13+, keeps the background service alive)
6. Tap **Start floating bubble**. A small dot appears — drag it anywhere,
   tap it to open the 5-icon menu, tap an icon to trigger that toggle, tap
   again (or the ✕) to collapse it.

## Optional: instant Battery Saver toggle via adb

By default the Battery Saver icon opens the settings screen (one extra tap).
To make it a true instant toggle, run this once from a computer with `adb`
installed, while the phone is connected with USB debugging on:

```
adb shell pm grant com.shihan.pixeltouch android.permission.WRITE_SECURE_SETTINGS
```

This is a standard, documented Android mechanism (the same one several
open-source "battery saver shortcut" apps use) — it only works because you
are granting it to your own device via your own adb session, and only
affects this one app. If you ever uninstall/reinstall the app, you'll need
to run the command again.

## Using the Quick Settings tiles instead of (or alongside) the bubble

Swipe down twice from the top of the screen → tap the pencil/edit icon →
scroll down to find **Wi‑Fi, Mobile data, Sound mode, Hotspot, Battery
saver** under PixelTouch → drag them into your active tiles. These work
independently of the floating bubble and don't need the overlay permission.

## Customizing

- **Hotspot name/password**: change the `"PixelTouch-Hotspot"` / `"touch1234"`
  strings in `OverlayService.kt` and `tiles/HotspotTileService.kt`.
- **Bubble color / icons**: `res/drawable/bg_bubble_circle.xml` and the
  `ic_*.xml` vector icons — swap in Android Studio's built-in Vector Asset
  tool (`res → New → Vector Asset`) for polished Material icons if you'd
  like something more refined than the simple hand-drawn glyphs included
  here.
- **Menu position**: `showMenu()` in `OverlayService.kt` positions the panel
  160px below the bubble; adjust to taste.
- **Auto-start on boot**: already wired up (`BootReceiver`) — it only
  restarts the bubble if it was running when you last stopped it or
  rebooted, and only if the overlay permission is still granted.

## Known limitations (by design, not a bug)

- The bubble is a foreground service with a persistent low-priority
  notification ("PixelTouch is running") — Android requires this so the
  system doesn't kill the overlay in the background. Tap **Stop** on the
  notification or in the app to remove it.
- Wi‑Fi/mobile data/hotspot rely on Android APIs that can shift between OS
  versions; if a future Pixel feature-drop update changes behavior, the
  panel/settings-screen fallbacks are what keep this working without needing
  root.
- This app is for your own personal device via sideloading (Android Studio
  or a debug APK) — it uses `WRITE_SECURE_SETTINGS` and other
  permissions that Google Play would not allow for a public listing.
