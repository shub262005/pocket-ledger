# Pocket Ledger

**Small spends. Clear friendships.**

A simple, offline Android app for the everyday money you owe friends—and the money they owe you. Track chips, milk, shared dinners, and other small expenses without a spreadsheet or an account.

[![Android build](https://github.com/shub262005/pocket-ledger/actions/workflows/android.yml/badge.svg)](https://github.com/shub262005/pocket-ledger/actions/workflows/android.yml)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?logo=kotlin&logoColor=white)
![Material 3](https://img.shields.io/badge/UI-Material%203-245C43)

## Why Pocket Ledger?

You bought snacks for a friend. They paid for lunch yesterday. Pocket Ledger keeps both sides in one place, so you can see what is outstanding and settle up when it suits you.

Native Android screens use Jetpack Compose and Material 3, with a sage-green palette, rounded cards, and light and dark themes. Your ledger lives locally in SQLite. There is no login, advertising, analytics, or internet permission.

## Features

| Feature | What it does |
| --- | --- |
| Balance overview | See what you owe, what you are owed, and your overall net balance. |
| Everyday expenses | Record a person, item, amount, direction, category, date, and optional note. |
| Categories | Snacks, dairy, restaurants, groceries, travel, shopping, and other. |
| People view | See each friend's net balance and expense history. |
| Repayments | Record partial repayments or fully settle an expense. Settled entries stay in history. |
| Search and filters | Search names, items, categories, and notes; filter open, owed, owing, and settled entries. |
| Equal-share calculator | Divide a bill by the participant count and record one person's share. |
| Editing | Update expenses or delete them after confirmation. |
| Appearance | Choose light, dark, or system theme. |
| Currency | INR by default, with USD, EUR, and GBP display options. |
| Backup and restore | Export JSON using Android's file picker and merge a previous backup by expense ID. |

## Using the app

1. Tap **Add expense** and choose **They owe me** or **I owe them**.
2. Enter the person's name, item, and amount. Choose a category and date, then save.
3. Tap an expense to record a repayment, settle it, edit it, or delete it.
4. Open **People** to review your history with one friend.
5. Use **Settings → Export backup** before uninstalling or changing phones.

For a shared bill, enable **Split a bill equally** and enter the total and participant count, including you. Save a separate entry for each other person's share.

## Build and install

### Requirements

- Android Studio with support for Android Gradle Plugin **9.1.1**, or Android command-line tools.
- JDK **17 or 21**.
- Android SDK Platform **37.0** and Build Tools **36.0.0**.
- An Android **8.0 / API 26** or newer device or emulator.

The Gradle **9.3.1** wrapper is included; a separate Gradle installation is unnecessary. Compile SDK is 37.0 and target SDK is 36.

### Android Studio

```sh
git clone https://github.com/shub262005/pocket-ledger.git
cd pocket-ledger
```

Open the folder in Android Studio, let Gradle sync, and run the **app** configuration. Android Studio normally creates `local.properties` with your SDK location.

### Command line

Install SDK packages if needed:

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0'
```

Set `ANDROID_HOME` to your SDK directory, or create `local.properties` with `sdk.dir` pointing to it. This file is ignored by Git. In Windows properties files, escape the drive-letter colon, for example `sdk.dir=C\:/Users/you/AppData/Local/Android/Sdk`.

**Windows / PowerShell**

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

**macOS / Linux**

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Transfer it to your phone and open it to install, allowing installation from that source if Android prompts you. With USB debugging enabled and a device connected, you can also use:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

This is a debug build for development and personal testing. Publishing requires your own release signing configuration. Keep signing keys outside version control.

Internet access is needed to download build dependencies initially. The installed app works offline.

### GitHub Actions

The [Android build workflow](https://github.com/shub262005/pocket-ledger/actions/workflows/android.yml) builds the APK, runs unit tests, and checks Android lint on pushes to `main` and pull requests. Successful runs provide a **pocket-ledger-debug** APK artifact and test/lint reports under **Artifacts**. Downloading workflow artifacts generally requires signing in to GitHub.

## Data and privacy

- Expenses live in the app's private SQLite database; settings use local SharedPreferences.
- The app does not request internet or contacts access.
- Automatic Android cloud backup and device-transfer backup are excluded by configuration. Use explicit export/restore to move your ledger.
- **Uninstalling removes local data. Export a backup first.**
- Backups are plain JSON containing names, amounts, and notes. They are not encrypted; store them somewhere private.

Choose **Settings → Restore backup** to import a Pocket Ledger JSON file. Imports are validated before writing and merged in a database transaction. Matching expense IDs are replaced, including repayment totals; unrelated entries remain. Export your current ledger before restoring if you need to preserve a copy.

## Scope and limitations

- One display currency applies to the whole ledger. Changing it **does not convert amounts**.
- Repayments are stored as a cumulative total per expense, rather than a dated payment history.
- People are derived from expense names. Use consistent names for the same person; entering an existing name ignores letter case.
- Equal splitting saves one person's share at a time and rounds to the nearest minor unit. It does not distribute rounding remainders across a group.
- No account sync, automatic payment collection, or bank integration.

## Project structure

```text
app/
  src/main/
    java/com/pocketledger/app/
      MainActivity.kt    # Overview, people, settings, theme
      Dialogs.kt         # Expense editor and repayment details
      Ledger.kt          # SQLite, backup/restore, money helpers
    res/                 # Theme and launcher resources
    AndroidManifest.xml
  src/test/java/com/pocketledger/app/
    MoneyTest.kt         # Exact amounts, splitting, balances
gradle/wrapper/          # Gradle wrapper
.github/workflows/      # Build, tests, lint, APK artifacts
```

Built with **Kotlin**, **Jetpack Compose**, **Material 3**, **SQLiteOpenHelper**, and Android's **Storage Access Framework**. Money uses integer minor units rather than floating-point arithmetic. Compose dependencies are aligned through the [Compose BOM](https://developer.android.com/develop/ui/compose/bom).

## Verification

The initial implementation passed a clean debug build, all **3 unit tests**, Android lint with **0 errors**, and APK signature verification. Lint retains warnings about dependency updates, the target SDK, and Kotlin extension suggestions. Device/emulator UI testing has not yet been performed; build checks do not replace that testing.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development conventions and validation steps. For bugs or ideas, [open an issue](https://github.com/shub262005/pocket-ledger/issues). Use fictional names and amounts in examples and screenshots.
