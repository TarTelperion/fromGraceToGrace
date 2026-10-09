# Steady

Steady is a local-only Android sobriety tracker. The first version is designed for a person or a small trusted recovery group and deliberately avoids accounts, cloud storage, advertising, analytics, and paid services.

## Included in the first version

- A large count-up display based on the current recovery start
- A date-and-time correction flow for entering the real current recovery start
- A gentle “begin a new start” flow with an optional private note
- On-device history of prior recovery intervals
- A calm local encouragement feed
- A daily recovery-relevant scripture from the free, keyless Open Scripture API
- On-device caching so the last successful daily scripture remains available offline
- Importing selected text or links through Android’s Share menu
- An official link to the Church Addiction Recovery Program guide
- Optional local daily notifications with private lock-screen wording
- Eight selectable sobriety milestones from one hour through one year
- Animated progress around the center circle for the current milestone
- Optional local milestone notifications that advance to the next selected goal
- Five selectable color themes, plus system light and dark mode
- Restrained motion

## Privacy model

- The application requests internet access only to retrieve the daily scripture. Recovery records, notes, goals, and settings are never uploaded.
- Recovery data is stored in an app-private SQLite database.
- Preferences are stored locally with Android DataStore.
- Android backup and device transfer are disabled for all app data.
- Notifications never include a sobriety count, relapse note, addiction name, or recovery status.

Uninstalling the app permanently removes its data. Export and encrypted transfer are intentionally deferred until they can be designed without weakening privacy.

## Opening the project

Install the current stable Android Studio with Android SDK 37 and JDK 17. Open this folder, allow Gradle to sync, and run the `app` configuration on an Android 7.0 or newer emulator or phone. Samsung Galaxy hardware should be included in device testing before wider distribution.

## Working name and package

“Steady” and `org.recovery.steady` are placeholders. Choose a final product name and permanent application ID before distributing builds, because changing the application ID later creates a separate installed app.
