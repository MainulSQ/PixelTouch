# PixelTouch

PixelTouch is an AssistiveTouch-style quick-control app for Android, now built
with Flutter for the interface and BLoC for state management.

## Architecture

```text
lib/
  features/home/
    application/    BLoC state and actions
    data/           Flutter-to-Android gateway
    presentation/   Flutter screens
android/app/
  src/main/kotlin/  Overlay, quick tiles, screenshots and device controls
```

Flutter owns the app UI. The Android host retains capabilities Flutter cannot
provide directly: the overlay service, accessibility screenshot service, boot
receiver, Quick Settings tiles, torch, connectivity settings, and updater.

## Development

```bash
flutter pub get
flutter run
flutter build apk --debug
```

The release workflow runs after each push to `main` and publishes the debug
APK as a GitHub release. Increase `version` in `pubspec.yaml` for every update.
