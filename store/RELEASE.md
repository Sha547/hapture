# Releasing to Google Play

## One-time
1. Create the upload key (choose your own password; keep the file and password backed up OUTSIDE this repo):
   `keytool -genkeypair -v -keystore ~/motionlab-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`
2. Create `keystore.properties` in the repo root (git-ignored):
   ```
   storeFile=/home/YOU/motionlab-upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
3. In Play Console: create the app, enable Play App Signing (default), fill the store listing from LISTING.md, upload the assets, paste the privacy policy URL: https://sha547.github.io/hapture/privacy/ (source: docs/privacy.md).

## Data safety form (matches docs/privacy.md)
- Does the app collect or share user data? **No.**
- Data encrypted in transit / deletion request: not applicable (nothing collected).
- Permissions declared: VIBRATE, INTERNET (local live-sync server only).

## Each release
1. Bump `versionCode` (must increase every upload) and `versionName` in `app/build.gradle.kts`.
2. `./gradlew :app:bundleRelease` -> `app/build/outputs/bundle/release/app-release.aab`
3. Upload the .aab to a testing track first (internal, then closed), then production.
4. Before uploading, run: `./scripts/check-generated-kotlin.sh` and the unit + on-device tests.

## Check in Play Console before you start
- The current minimum target API level for new apps (this build targets 35; it may need 36).
- Testing requirements for new personal developer accounts (a closed test with a minimum number of testers for a minimum number of days may be required before production).
