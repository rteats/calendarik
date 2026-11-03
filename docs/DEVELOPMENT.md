# Switching between Android Studio and GitHub releases

Both paths use application ID `com.rteats.calendarik` and the release certificate
pinned in `build-identity.properties`. Install over the existing app to retain
its database, preferences, login and encryption keys.

## Daily local work

1. Start from current source: `git pull --ff-only` (commit your work first if Git
   reports overlapping changes).
2. Connect the phone using Android Studio's device manager. Use the SDK's
   `platform-tools/adb`, since the old ClockworkMod driver also puts an obsolete
   `adb.exe` on this computer's PATH.
3. Select **app / debug** and press **Run**. Or run `powershell -File scripts/dev.ps1`.

The ignored `signing.properties` contains the four `CALENDARIK_KEYSTORE_FILE`,
`CALENDARIK_STORE_PASSWORD`, `CALENDARIK_KEY_ALIAS`, and `CALENDARIK_KEY_PASSWORD`
values. Use forward slashes for the keystore path. No `VERSION_CODE` entry is
needed. The Gradle build reads the connected phone's version code each time,
including when reusing its configuration cache. Repeated Run builds use the
same code, and the next published release can replace them.

For multiple connected devices, set `ANDROID_SERIAL` to the selected device's
serial before starting Studio, or use `scripts/dev.ps1 -Serial <serial>`.
Without a connected device, builds use the baseline code in
`build-identity.properties`; reconnect before building an APK for installation.

## Publish and switch back

Commit and push to `main`. The Main APK workflow checks migrations, runs Python
and JVM tests and Android lint, builds the optimized APK, verifies the pinned
certificate, and publishes both a permanent build release and `latest`.
Missing signing secrets, an older main commit, a lower release version code,
or a database schema downgrade fail publication.

Install `Calendarik.apk` from the latest release, the signed Actions artifact,
or the app's update menu. Debug builds allow returning to a release with the
same code. The update manifest points to a permanent APK URL and verifies its
checksum, package ID and certificate before opening the Android installer.
After installing a release, the next local Run adopts its version code.

The app must support the database already stored on the phone. If local work
introduces schema 6 while the published app still supports schema 5, publish
the schema 6 migrations before switching back to a release. Builds detect a
newer installed schema from the APK version name (`+dbN`); the app also opens
the database before showing the calendar and displays a recovery message on
failure. Never uninstall or clear storage to resolve a migration error.

## Verification

Use Java 17+ and Android SDK 36. The checked-in Gradle wrapper pins the Gradle
distribution and verifies its SHA-256 checksum.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python -m unittest discover -s scripts -p 'test_*.py'
```

On Windows, Python's time-zone tests require `pip install tzdata`.
Keep exported Room schemas in `app/schemas` and add explicit migration tests
whenever the database version changes. `signing.properties`, private keys and
APK files are ignored by Git.
