# KeepSpace 1.1 Cloud Backup — production setup

The app contains no OAuth client secret and no KeepSpace backend. Client IDs are injected at build time and must never be replaced with a user's password, access token, or refresh token.

## Google Drive

1. Create a Google Cloud project, enable Google Drive API, and complete Branding/Audience/Data Access for an external production app.
2. Add the public privacy and terms URLs from `store/ios/en-US/` and request only `openid`, `email`, `profile`, and `https://www.googleapis.com/auth/drive.file`.
3. Create an iOS OAuth client for bundle `com.keepspace.app`. Register `com.keepspace.app:/oauth2redirect` for the native authorization flow and set the iOS build setting `KEEP_SPACE_GOOGLE_CLIENT_ID` to its client ID.
4. Create separate Android OAuth clients for package `com.keepspace.app` and each SHA-1 that can sign the app: debug, upload/release, and Google Play App Signing. Android uses Google Identity `AuthorizationClient`; no Android client ID is embedded in the APK.
5. Publish/verify the consent screen and, after the Play listing exists, verify Android app ownership.

## Microsoft OneDrive

1. Create one Microsoft Entra app registration that supports personal Microsoft accounts and work/school accounts.
2. Enable public client flows and delegated scopes `openid`, `profile`, `email`, `offline_access`, and `Files.ReadWrite.AppFolder`.
3. Register iOS redirect URI `msauth.com.keepspace.app://auth`. Set `KEEP_SPACE_MICROSOFT_CLIENT_ID` in the iOS release build settings.
4. For the current standards-based Android PKCE flow, add the mobile/desktop custom redirect `keepspace:/oauth2redirect`. Set Gradle property or environment variable `KEEP_SPACE_MICROSOFT_CLIENT_ID` for the release build.
5. Do not add a client secret. KeepSpace is a public native client.

## Build examples

```bash
cd ios
xcodegen
xcodebuild archive -project SmartStorage.xcodeproj -scheme SmartStorage \
  KEEP_SPACE_GOOGLE_CLIENT_ID='…apps.googleusercontent.com' \
  KEEP_SPACE_MICROSOFT_CLIENT_ID='00000000-0000-0000-0000-000000000000'
```

```bash
cd android
KEEP_SPACE_MICROSOFT_CLIENT_ID='00000000-0000-0000-0000-000000000000' \
KEYSTORE_FILE='/absolute/path/keepspace-upload.jks' \
KEYSTORE_PASSWORD='…' KEY_ALIAS='…' KEY_PASSWORD='…' \
./gradlew bundleRelease
```

## Release verification

- Confirm the consent screen names KeepSpace and links to the production home/privacy/terms pages.
- Test both providers on physical iOS and Android devices with production-signed builds.
- Upload one photo, one screenshot, and one receipt to a non-default folder; verify the remote byte size and in-app `Backed up` state.
- Test access-token expiry/reconnect, revoked consent, quota full, offline/retry, Wi-Fi-only, app kill/relaunch, cancellation, and disconnect.
- Confirm every v1.1 upload retains the local original and that KeepSpace has no server-side access or logs containing file names/tokens.
- Put temporary reviewer credentials only in the private App Store Connect/Play Console review fields, then rotate them after review.
