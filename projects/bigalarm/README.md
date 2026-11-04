# Big Alarm

A standalone alarm app for the Samsung Galaxy Watch5 (SM-R910, One UI 8 Watch / Wear OS 6) and any
other Wear OS 4+ watch. Built because the stock alarm app's names are too small to read without
glasses and it stops at 15 alarms.

## What it does

- **Big alarm names.** A text-size setting (1x to 3x, on top of the system font size) applies to the
  alarm name in the list, in the editor and on the ringing screen. The ringing screen shows the name
  in the biggest size with huge Dismiss and Snooze buttons.
- **Unlimited alarms.** Alarms live in a local SQLite database (Room). There is no cap.
- **Custom repeat rules.** Once, once on a specific date, every day, chosen days of the week, or every
  N days counted from a start date. Rules are computed in pure Kotlin and covered by unit tests.
- **Reliable firing.** Alarms are registered with `AlarmManager.setAlarmClock`, the highest-priority
  alarm type Android has. They are re-registered after reboot, time or time-zone changes and app
  updates, and every time the app is opened.
- **Ringing.** A foreground service vibrates in a loop, optionally plays the watch's alarm ringtone
  (or synthesised beeps if the watch has none), wakes the screen, and posts a high-priority
  notification with Dismiss and Snooze actions. Two alarms firing at the same minute queue up
  instead of overwriting each other. After a configurable time the alarm gives up and posts a
  "Missed" notification.
- **Fast entry.** Names are typed or dictated with the watch's own input method. Names already used
  by other alarms are offered as one-tap choices. Any alarm can be duplicated.
- Standalone: no phone, no account, no store, no network.

## Status: builds, not yet run on a watch

Verified so far:

- `./gradlew :app:assembleDebug` succeeds with AGP 9.5.0, Gradle 9.6.0, the Kotlin 2.2.10 that AGP
  bundles, KSP 2.3.13 and compileSdk 37, with no Kotlin warnings. It produces
  `app/build/outputs/apk/debug/app-debug.apk`.
- The pure-Kotlin core (`core/` package: model, schedule computation, formatting) has 20 unit tests,
  all passing via `./gradlew :app:testDebugUnitTest`.
- Every library version in `gradle/libs.versions.toml` was checked against the official release
  pages on 4 Nov 2026.

Not verified: behaviour on the watch itself. Ringing, wake-up, notification actions and the Wear
input method all need a real device, so the first install is the real test. If your Android Studio
ships a different AGP, let the **AGP Upgrade Assistant** adjust `gradle/libs.versions.toml`; keep
the `kotlin` entry equal to the Kotlin Gradle plugin version that AGP release bundles (readable from
the AGP POM on maven.google.com).

## Requirements (Linux)

- A 64-bit x86 Linux with glibc 2.31 or newer. Android Studio does not support ARM Linux.
- [Android Studio](https://developer.android.com/studio) (Rabbit 2 or newer). It bundles a JDK and
  installs the SDK platform 37 and build tools on first sync. Everything is free and the SDK and
  Jetpack libraries are Apache 2.0.
- `adb` (comes with the SDK platform-tools; Android Studio installs it under
  `~/Android/Sdk/platform-tools`).
- Disk: roughly 10 GB for Studio, SDK and Gradle caches.

Command-line only is also possible: install `cmdline-tools`, run
`sdkmanager "platforms;android-37" "build-tools;36.0.0" "platform-tools"`, export `ANDROID_HOME`,
and use `./gradlew` as below.

## Build

Open `projects/bigalarm` in Android Studio and let it sync, or from a terminal:

```bash
cd projects/bigalarm
./gradlew :app:assembleDebug
```

Run the core unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

The debug APK ends up at `app/build/outputs/apk/debug/app-debug.apk`.

## Put it on the watch

Wear OS 3+ dropped Bluetooth debugging and the Watch5 charges wirelessly, so the route is Wi-Fi.
The watch and the Linux box must be on the same network.

1. On the watch: **Settings > About watch > Software** and tap **Software version** seven times
   to unlock Developer options.
2. **Settings > Developer options > ADB debugging** on, then **Wireless debugging** on.
3. Still under Wireless debugging, tap **Pair new device**. The watch shows a pairing code and an
   `IP:port`.
4. On the computer:

```bash
adb pair 192.168.1.50:41234
```

   Enter the code when prompted. Then connect using the second `IP:port` shown on the main
   Wireless debugging screen (a different port):

```bash
adb connect 192.168.1.50:37891
```

5. Install:

```bash
cd projects/bigalarm
./gradlew :app:installDebug
```

   or, with an APK already built, `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Android Studio's device drop-down also lists the watch once `adb connect` has succeeded, so you can
use the Run button instead.

On first launch the app asks for notification permission. Allow it, otherwise the ringing
notification (and its Dismiss / Snooze buttons) cannot be shown.

## Optional: make the ringing screen pop up by itself

Google's docs state that full-screen-intent notifications are not supported on Wear OS, and Android
blocks apps from opening a screen from the background. Out of the box, the watch therefore buzzes,
wakes the screen and shows a notification; tapping it opens the big-text ringing screen with
Dismiss and Snooze.

Android does allow the launch when the app holds the "draw over other apps" permission. Wear OS has
no settings page for it, but it can be granted once over adb:

```bash
adb shell appops set com.akvalentine.bigalarm SYSTEM_ALERT_WINDOW allow
```

The Settings screen in the app reports whether this is on. Revoke with `... SYSTEM_ALERT_WINDOW
default`. This is standard Android behaviour but it has not been confirmed on One UI 8 Watch, so
treat it as an experiment.

## How it works

```
AlarmManager.setAlarmClock ──► AlarmReceiver ──► AlarmRingService (foreground, type systemExempted)
                                                     ├─ Ringer: vibration loop, ringtone or tones
                                                     ├─ wake lock, screen on
                                                     ├─ notification: Dismiss / Snooze actions
                                                     └─ RingingState ──► AlarmRingActivity (big text)
Room (alarms.db) ◄──► AlarmRepository ◄──► AlarmActions ──► AlarmScheduler.rescheduleAll()
```

- `core/` is pure Kotlin: `Alarm`, `Repeat`, `Schedule.nextTrigger()`, `Format`. Unit-tested.
- Build: AGP 9 built-in Kotlin (no `kotlin-android` plugin), Compose compiler plugin matched to the
  bundled Kotlin version, KSP 2.3.x for Room (older KSP releases are incompatible with built-in
  Kotlin's source-set rules).
- `data/` is Room plus a SharedPreferences-backed `Settings`.
- `alarm/` is scheduling and ringing. Every write goes through `AlarmActions`, which re-registers
  all system alarms afterwards, so the database and AlarmManager never drift apart.
- `ui/` is Compose for Wear OS Material 3 with swipe-to-dismiss navigation. Editing happens on an
  in-memory draft (`EditDraft`) and is written only on Save.

Permissions: `USE_EXACT_ALARM` (granted automatically to alarm-clock apps on API 33+),
`POST_NOTIFICATIONS`, `VIBRATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, foreground-service
permissions, and the optional `SYSTEM_ALERT_WINDOW`. No network permission.

## Known limitations and ideas

- The ringing screen only opens by itself with the adb step above.
- No phone companion yet. A phone app for editing alarms with a real keyboard would use the Wear
  Data Layer API; the standalone design leaves room for it.
- No tile or watch-face complication yet. A tile showing the next alarm would be a natural addition.
- The notification text uses the system font size. Only the app's own screens get the big text.

## References

- Wear OS: create your first app, run on a physical watch:
  <https://developer.android.com/training/wearables/get-started/creating>
- Debug Wear OS over Wi-Fi:
  <https://developer.android.com/training/wearables/get-started/debug-wifi>
- Alarms, exact alarms and the `USE_EXACT_ALARM` permission:
  <https://developer.android.com/develop/background-work/services/alarms/schedule>
- Foreground service types (`systemExempted` allowed for exact-alarm apps):
  <https://developer.android.com/develop/background-work/services/fg-service-types>
- Background activity launch restrictions and exemptions:
  <https://developer.android.com/guide/components/activities/background-starts>
- Notifications on Wear OS (full-screen intents unsupported):
  <https://developer.android.com/training/wearables/notifications/creating>
- Compose for Wear OS releases: <https://developer.android.com/jetpack/androidx/releases/wear-compose>
- Android Gradle plugin 9 built-in Kotlin: <https://developer.android.com/build/migrate-to-built-in-kotlin>
- Text input on Wear OS, `RemoteInputIntentHelper`:
  <https://developer.android.com/reference/androidx/wear/input/RemoteInputIntentHelper>
