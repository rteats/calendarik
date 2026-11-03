# Календарик

Android calendar for two people. The current phase is **local-only**: create, edit and delete personal and
joint events, add unlimited red/pink hearts to each day, choose one mood per
person per day, show daily events and reactions in an Android widget, and
check/install signed updates through GitHub Releases.

**Two-person encrypted sync is now implemented** using Firebase Authentication,
a QR invitation, Android Keystore, AES-GCM encryption, Firestore snapshots, and
WorkManager. Follow [Firebase setup](docs/FIREBASE_SETUP.md) to whitelist the
two account UIDs and deploy Security Rules before pairing. The local profile
switch remains for offline/demo use only; a paired account has a locked role.

## Build

- Java 17, Gradle 9.6, Android SDK 36
- Firebase SDK dependencies and public Android client configuration included
- Run: \`gradle :app:assembleDebug\` (debug package is \`com.rteats.calendarik.debug\`)
- Pull requests: Python policy checks, Android lint and JVM tests; no APK build
- Main: APK build only. With signing secrets, main publishes a single rolling
  \`latest\` GitHub Release for in-app updates; without them, a disposable debug
  artifact is published in Actions **but is not safe to keep data in**.

## Release APK size

The signed APK built by `main` uses **R8 code shrinking and optimization**
and Android **resource shrinking** (the same production approach as MPEI Lite).
Normal debug builds are not shrunk. To reproduce the optimized release locally,
run `gradle :app:assembleRelease -POPTIMIZE_RELEASE=true`; a local build
without the required signing secrets is not a replacement for the published
APK.

The GitHub Actions build summary compares the new APK size with the previous
`latest` release asset. PR checks also run the R8 shrinking task **without
packaging an APK**, in addition to lint and unit tests. Do not add broad
`-keep` rules or disable the shrinkers to silence R8 failures: narrow
preservation rules must cover only code accessed by reflection.

Do not change the application ID, release signing key, or Room migrations
as a size optimization. ABI splits are intentionally not used because
in-app updates currently download **one universal APK**.

## Signing before your first real installation

**Never commit an APK signing private key.** Generate a stable PKCS#12 file on
your own computer, keep an offline backup, then set the following GitHub Actions
repository secrets:

- \`CALENDARIK_KEYSTORE_BASE64\`: base64 encoding of the .p12 file (single line)
- \`CALENDARIK_STORE_PASSWORD\`
- \`CALENDARIK_KEY_ALIAS\`
- \`CALENDARIK_KEY_PASSWORD\`

Example key creation:

\`\`\`bash
keytool -genkeypair -v -storetype PKCS12 -keystore calendarik-release.p12 \
  -alias calendarik -keyalg RSA -keysize 3072 -validity 10000
\`\`\`

Convert the file to a single line using \`base64 -w0 calendarik-release.p12\`
on Linux or \`[Convert]::ToBase64String([IO.File]::ReadAllBytes("calendarik-release.p12"))\`
in PowerShell. Add the four secrets under repository Settings →
Secrets and variables → Actions. A new push to main or manual workflow launch
will then produce a consistently signed release.

The signed release uses \`com.rteats.calendarik\`. Keep **both application ID and
signing key constant** across all later releases, or Android cannot update
in-place. Do not install the preview debug APK if you intend to keep its data.

## Data safety

- Room is the local source of truth.
- Room v2 adds `daily_reactions` with an explicit additive migration from v1, preserving old events and tombstones.
- Hearts are 64-bit SQLite counters, incremented transactionally; mood replaces the previous value.
- Events have immutable UUIDs, versioned payloads, author masks and revisions.
- Deletes create tombstones rather than removing data.
- The database exports a versioned schema.
- Destructive fallback migrations are forbidden.
- Each schema change must include an explicit migration, schema snapshot and
  upgrade tests from old versions before a production release.
- An incompatible database upgrade must **fail safely**, not erase the data.
- Android automatic cloud backups are off until encrypted key-aware backup is
  implemented; export/import is not yet available.

See [DATA_COMPATIBILITY.md](docs/DATA_COMPATIBILITY.md) and
[SYNC_DESIGN.md](docs/SYNC_DESIGN.md).

## Notes

All UI dates use the device time zone. Release names use Moscow time
(\`YYYY.MM.DD.N\`). The increasing Android \`versionCode\` is separate from
the calendar date counter. The uploaded Firebase Android client config is committed to the repository;
it contains public project identifiers, not a privileged service account or
content-encryption key. The backend remains **locked** until real user
authentication, pair access rules and end-to-end encryption are finished.
See [FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md).


Local database v3 is additive and does not delete old Room v1/v2 data.
Encryption keys are NOT backed up: do not delete both devices before key export is available.
