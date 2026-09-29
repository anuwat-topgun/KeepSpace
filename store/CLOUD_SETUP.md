# KeepSpace 1.1 Cloud Backup — production setup

The app contains no OAuth client secret and no KeepSpace backend. Client IDs are injected at build time and must never be replaced with a user's password, access token, or refresh token.

## Google Drive

1. Use the Google Cloud project `keepspace-production`, enable Google Drive API, and complete Branding/Audience/Data Access for an external production app.
2. Add the public privacy and terms URLs from `store/ios/en-US/` and request only `openid`, `email`, `profile`, and `https://www.googleapis.com/auth/drive.file`.
3. The iOS OAuth client `KeepSpace iOS Production` is registered for bundle `com.keepspace.app`. The checked-in iOS build setting `KEEP_SPACE_GOOGLE_CLIENT_ID` contains its public client ID and the native authorization flow redirects to `com.keepspace.app:/oauth2redirect`.
4. Create separate Android OAuth clients for package `com.keepspace.app` and each SHA-1 that can sign the app: debug, upload/release, and Google Play App Signing. Android uses Google Identity `AuthorizationClient`; no Android client ID is embedded in the APK.
5. Publish/verify the consent screen and, after the Play listing exists, verify Android app ownership.

## Microsoft OneDrive

1. Create one Microsoft Entra app registration that supports personal Microsoft accounts and work/school accounts.
2. The production registration is `KeepSpace OneDrive Production`, client ID `3f3a9ce1-efd8-4c61-bd72-5fcce0aa47f6`. It supports organizational directories plus personal Microsoft accounts, enables public client flows, and has delegated Microsoft Graph permission `Files.ReadWrite.AppFolder` (along with the OpenID scopes requested at authorization time).
3. The iOS redirect URI is `msauth.com.keepspace.app://auth`. The public client ID is checked into the iOS build settings and may be overridden with `KEEP_SPACE_MICROSOFT_CLIENT_ID`.
4. The Android PKCE redirect is `keepspace://oauth2redirect`. The same public client ID is the Gradle default and may be overridden by Gradle property or environment variable `KEEP_SPACE_MICROSOFT_CLIENT_ID`.
5. Complete Microsoft publisher verification before public release. The Entra portal currently marks the registration as an unverified publisher and warns that end users cannot consent to newly registered multitenant apps until the publisher is verified. This requires a verified custom domain and the corresponding Microsoft Partner Center publisher identity.
6. Do not add a client secret. KeepSpace is a public native client.

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
