# KeepSpace 1.1 Cloud Backup — production setup

The app contains no OAuth client secret and no KeepSpace backend. Client IDs are injected at build time and must never be replaced with a user's password, access token, or refresh token.

## Google Drive

1. Use the Google Cloud project `keepspace-production`, enable Google Drive API, and complete Branding/Audience/Data Access for an external production app.
2. Configure the application home page as `https://keepspace.itston.com/`, privacy policy as `https://keepspace.itston.com/privacy/`, and terms of service as `https://keepspace.itston.com/terms/`. Request only `openid`, `email`, `profile`, and `https://www.googleapis.com/auth/drive.file`.
3. The iOS OAuth client `KeepSpace iOS Production` is registered for bundle `com.keepspace.app` and Apple Team ID `82MGZU2HH4`. The checked-in iOS build setting `KEEP_SPACE_GOOGLE_CLIENT_ID` contains its public client ID. iOS uses the official Google Sign-In SDK and registers the reversed client-ID URL scheme `com.googleusercontent.apps.704189605605-aaosn5etn4m6peil1dttaffdb0c20jtk` for the OAuth callback.
4. Android OAuth clients registered for package `com.keepspace.app`: `KeepSpace Android Debug`, SHA-1 `03:6B:A4:52:7B:D1:3A:84:17:FC:6E:24:D8:40:E2:12:7A:3F:E7:62`, client ID `704189605605-5vmvsgan8dsg8g72bqeao117vbkov9q9.apps.googleusercontent.com`; `KeepSpace Android Release`, SHA-1 `26:F7:E4:4A:BA:F2:4B:C4:F1:0A:29:4B:81:4C:3E:94:E8:86:AE:8D`, client ID `704189605605-cidhgv8ejm0uj6s9v1rec9e6sbpau0us.apps.googleusercontent.com`; and `KeepSpace Android Play App Signing`, SHA-1 `A4:B3:55:E3:14:A9:92:4A:45:61:1E:D2:21:4F:E4:C5:8E:2A:40:4C`, client ID `704189605605-b3vgj66l8t4led362u65sptmvfu227d1.apps.googleusercontent.com`. Android uses Google Identity `AuthorizationClient`; no Android client ID is embedded in the APK.
5. Publish/verify the consent screen and, after the Play listing exists, verify Android app ownership.

## Microsoft OneDrive

1. Use a Microsoft Entra app registration whose supported account type is **Personal Microsoft accounts only**.
2. The production registration is `KeepSpace OneDrive Production`, client ID `3f3a9ce1-efd8-4c61-bd72-5fcce0aa47f6`. It enables public client flows and has delegated Microsoft Graph permission `Files.ReadWrite.AppFolder` (along with the OpenID scopes requested at authorization time). Both apps use the Microsoft `consumers` authority so work/school accounts cannot sign in.
3. The iOS redirect URI is `msauth.com.keepspace.app://auth`. The public client ID is checked into the iOS build settings and may be overridden with `KEEP_SPACE_MICROSOFT_CLIENT_ID`.
4. The Android PKCE redirect is `keepspace://oauth2redirect`. The same public client ID is the Gradle default and may be overridden by Gradle property or environment variable `KEEP_SPACE_MICROSOFT_CLIENT_ID`.
5. Configure the Microsoft app branding home page as `https://keepspace.itston.com/`, privacy policy as `https://keepspace.itston.com/privacy/`, terms of service as `https://keepspace.itston.com/terms/`, and support page as `https://keepspace.itston.com/support/`.
6. The publisher domain is `keepspace.itston.com`. Microsoft Partner Center publisher verification is not a v1.1 release gate because this registration accepts personal Microsoft accounts only; revisit it before adding organizational accounts.
7. Do not add a client secret. KeepSpace is a public native client.

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
