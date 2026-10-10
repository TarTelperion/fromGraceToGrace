# From Grace to Grace

From Grace to Grace is a simple, private Android recovery companion inspired by the principle of continuing “from grace to grace.” It is intended for personal use or a small trusted recovery group.

The app has no account, advertising, analytics, subscription, or paid service. Recovery progress and notes stay on the device. Internet access is used only to download optional scripture and motivational content.

> From Grace to Grace is a personal reflection and habit-support tool. It is not medical care, professional counseling, or an emergency service.

## Current version

- Version name: `0.3.0`
- Application ID: `org.recovery.steady`
- Minimum Android version: Android 7.0 / API 24
- Target Android version: API 36
- Compile SDK: API 37

The current build is suitable for personal phone testing. A permanent application ID and release-signing key should be chosen before wider distribution.

## Features

### Recovery tracking

- First-run choice to start now or enter the actual beginning date and time
- Large count-up circle based on complete 24-hour periods
- Goal ring that fills as the next selected milestone approaches
- Eight available milestones: 1 hour, 1 day, 3 days, 1 week, 1 month, 3 months, 6 months, and 1 year
- Adjustable goal selection with at least one goal always active
- Date-and-time correction without creating a lapse record
- Gentle “begin a new start” flow with an optional private note
- On-device history of previous intervals and notes

### Encouragement

- Daily recovery-relevant scripture
- Two rotating, curated gospel-centered passages in the daily feed
- Up to three rotating motivational quotes from the weekly online cache
- New daily selections after local midnight
- Official link to the Church Addiction Recovery Program guide
- Android Share-menu import for passages and links
- Duplicate protection and deletion for saved passages

### Notifications and appearance

- Optional daily reminder with private lock-screen wording
- Optional milestone encouragement when a selected goal is reached
- Reminder scheduling that recalculates against the phone’s local time
- Five selectable accent colors
- System-controlled light and dark mode
- Minimal animation

Android schedules these notifications as background work. Battery-saving features—especially Samsung battery management—may delay them. Notification timing should be tested on the physical phone before relying on it.

## Content sources

- Scripture is requested daily from the free, keyless [Open Scripture API](https://openscriptureapi.org/).
- Motivational quotes are requested from [ZenQuotes](https://zenquotes.io/) no more than once every seven days after a successful refresh.
- Downloaded content is cached locally so the feed remains useful offline.
- Recovery data is never sent to either content provider.
- Internet quotes pass through a basic recovery-sensitive word filter, but they are not official Church content or individually reviewed.

If an online quote refresh is unavailable, the curated gospel-centered passages remain available.

## Privacy and local storage

- Recovery progress, lapse notes, goals, reminders, theme choices, and saved passages remain in private app storage.
- Recovery data is stored in SQLite.
- Settings and cached content are stored with Android DataStore.
- Automatic Android cloud backup and device transfer are disabled.
- Notifications do not display the sobriety count, lapse notes, addiction name, or recovery status.

Uninstalling the app removes its private data. Export a manual backup first if the history needs to be restored later.

### Manual backups

Open **Settings → Private backup** to export or restore a backup.

The JSON backup includes:

- Current recovery start
- Lapse history and notes
- Goals and notification preferences
- Reminder time
- Theme choice
- Saved passages

Backup files are not encrypted. Save them only in a private location and avoid sending them through untrusted services. Restoring a backup replaces the corresponding local data after an explicit confirmation.

## Open and run in Android Studio

1. Install the current stable Android Studio.
2. Open the project folder at `/Users/jackjones/Desktop/Steady`.
3. Allow Android Studio to finish Gradle synchronization and install any requested Android SDK components.
4. Select the `app` run configuration.
5. Choose an Android 7.0 or newer emulator or a connected Android phone.
6. Press **Run**.

For a Samsung phone, enable Developer options and USB debugging, connect the phone, approve the computer when prompted, and select the phone from Android Studio’s device menu.

The generated debug APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Verify the project

From the project directory, the complete local check is:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The current version passes the unit tests, Android lint, debug build, emulator installation, backup/restore test, duplicate-share test, and basic runtime smoke test. Remaining lint notices concern available Android tooling or dependency updates rather than app errors.

## Technology

- Kotlin
- Jetpack Compose and Material 3
- SQLite
- Android DataStore
- Android WorkManager
- Java 17

No cloud database or custom server is required.

## Distribution note

Debug builds are appropriate for personal testing but are not Play Store releases. Before sharing broadly:

1. Choose and retain a permanent application ID.
2. Create and securely preserve a release-signing key.
3. Test notifications and battery behavior on physical Samsung hardware.
4. Review privacy wording and all bundled or externally sourced content.
5. Build and test a signed release APK or Android App Bundle.
