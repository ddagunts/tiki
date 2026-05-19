# F-Droid submission

This is the runbook for getting tiki into the official F-Droid repository.

## Why this is plausibly accepted

F-Droid only accepts apps that build entirely from FOSS source on a clean
buildserver, with no proprietary dependencies and no anti-features beyond what's
declared. tiki clears all of those:

- **License:** Apache-2.0 (`LICENSE`).
- **No proprietary deps:** AndroidX + Compose + Coroutines + Tink + protobuf-lite.
  No Google Play Services, no Firebase, no Cast SDK, no analytics, no crash
  reporting.
- **No `INTERNET` permission** in the manifest. The app cannot phone home even if
  a future bug tried to.
- **No location permission.** BLE scan uses `usesPermissionFlags="neverForLocation"`.
- **Pinned versions** via `gradle/libs.versions.toml`. No `+` or dynamic ranges.
- **Release signing is conditional.** If `keystore.properties` is present (local
  dev) or `TKEY_KEYSTORE_*` env vars are set (CI), `assembleRelease` produces a
  signed APK for GitHub Releases / sideload. If neither is present — which is
  the state F-Droid's buildserver sees, because `keystore.properties` is
  gitignored — `assembleRelease` produces an unsigned APK and F-Droid signs it
  with the F-Droid key after their reproducible build. The two distribution
  channels therefore have different signing certs, which is expected.

## Before tagging a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add a changelog entry at `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Verify a clean release build:
   ```sh
   JAVA_HOME=~/Downloads/android-studio-panda4-patch1-linux/android-studio/jbr \
     ./gradlew clean :app:assembleRelease
   ```
4. Tag with a `v`-prefix that the recipe's `UpdateCheckMode: Tags ^v.*` will match:
   ```sh
   git tag -s v0.1.0 -m 'v0.1.0'
   git push origin v0.1.0
   ```
   Signed tags are not required by F-Droid, but they let third parties verify
   the tag came from you.

## Submitting to fdroiddata

1. Fork https://gitlab.com/fdroid/fdroiddata.
2. Copy `docs/fdroid/com.tkey.yml` from this repo to `metadata/com.tkey.yml` in
   the fork.
3. Place a 512×512 PNG icon at `metadata/com.tkey/en-US/icon.png` if the
   in-repo fastlane icon is not picked up automatically.
4. Lint locally if you have `fdroidserver` installed:
   ```sh
   fdroid lint com.tkey
   fdroid readmeta
   fdroid rewritemeta com.tkey
   ```
5. Test-build locally (slow — downloads the buildserver VM):
   ```sh
   fdroid build --verbose --on-server --no-tarball com.tkey:1
   ```
6. Open a merge request against fdroiddata `master`. Title: `New app: com.tkey`.
   Link this repo and the v0.1.0 tag. A maintainer will review.

## Known buildserver friction

- **JDK 21:** AGP 9.2.1 requires JDK 21. The fdroiddata recipe in
  `com.tkey.yml` pulls Temurin 21 in a `sudo:` block. If F-Droid's buildserver
  image is updated to bundle 21, drop that block.
- **compileSdk 36 / AGP 9.2.1:** Bleeding-edge. If the buildserver's
  `android-sdk` package lacks platform 36 or the build-tools needed by AGP 9.2,
  the build will fail at `configure`. The fix is on F-Droid's side (their
  `srvlib` images get bumped roughly quarterly) — if you see this, raise it in
  the fdroiddata MR and they will rebase onto a newer buildserver image.
- **Gradle 9.5.1:** The wrapper at `gradle/wrapper/gradle-wrapper.properties`
  pins this; F-Droid's buildserver downloads it on demand. No action needed.

## Anti-Features

Plan to declare **none**. Specifically:
- No `Tracking`, `NonFreeNet`, `NonFreeAdd`, `NonFreeDep`, `NonFreeAssets`,
  `UpstreamNonFree`, `KnownVuln`, `Ads`, or `DisabledAlgorithm` applies.
- The trademark "Tesla" is referenced in the description but the app does not
  ship Tesla branding or assets and is documented as unaffiliated. F-Droid has
  not historically flagged independent-protocol clients (e.g. Mastodon /
  Twitter / Reddit clients) for trademark reasons.

## After acceptance

- New tags will be auto-picked up by `UpdateCheckMode: Tags ^v.*` — no fdroiddata
  PR needed per release, only when `Builds:` needs new entries or the recipe
  itself changes.
- Each release should have a matching `changelogs/<versionCode>.txt`; F-Droid
  surfaces this as the "What's New" on the app page.
- Screenshots in `fastlane/metadata/android/en-US/images/phoneScreenshots/`
  are auto-ingested; update them when the UI changes meaningfully.

## Files this prep added

```
fastlane/metadata/android/en-US/
  title.txt
  short_description.txt
  full_description.txt
  changelogs/1.txt
  images/icon.png                  (512×512, from docs/screenshots/logo.png)
  images/phoneScreenshots/         (six 1080×2400 PNGs, ordered)
gradlew, gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties   (gradle-9.5.1-bin)
docs/fdroid/com.tkey.yml           (sample recipe to copy into fdroiddata)
docs/fdroid/FDROID.md              (this file)
```
