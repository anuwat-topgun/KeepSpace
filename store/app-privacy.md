# Store privacy declarations for KeepSpace 1.1

Last reviewed: September 28, 2026

These answers describe the code in this repository at version 1.1, including optional direct-to-provider Cloud Backup.

## Apple App Privacy

- **Does this app collect data?** No.
- **Data used to track you:** None.
- **Data linked to you:** None.
- **Data not linked to you:** None.
- **Privacy Policy URL:** `https://github.com/anuwat-topgun/KeepSpace/blob/main/legal/privacy-policy.md`

Reasoning: KeepSpace does not receive data and has no backend. AI data remains on-device. A file is sent only when the user explicitly starts a backup, directly to the user's own Google Drive or OneDrive account. Reconfirm this answer in App Store Connect against Apple's current definition of “collect”; direct user-requested transfer to a third party is not developer collection.

## Google Play Data safety

- **Does the app collect or share any required user data types?** No.
- **Is all user data encrypted in transit?** Yes. OAuth and uploads use provider HTTPS endpoints.
- **Can users request deletion?** No account or server-side collection exists. Users clear the analysis cache in the app, clear app storage, or uninstall the app.
- **Security practices:** No data is sold or used for advertising, tracking, or analytics.

Cloud Backup is a prominent, user-initiated transfer to the user's own external cloud account; KeepSpace never receives the file. Under the current Google Play user-initiated-action exception this is not declared as developer collection or sharing. Reconfirm in Play Console at submission time.

## Permissions reviewers should expect

| Permission | Platform | Purpose |
|---|---|---|
| Photo library / photos and videos | iOS, Android | On-device scan, preview, user-confirmed cleanup/compression, and explicit direct cloud backup |
| Internet/network state | Android | OAuth, direct provider upload, retry, and Wi-Fi-only enforcement |
| Selected photos / limited library | iOS, Android 14+ | Let users constrain which media the app can access |
| Photo media location | Android | On-device trip/event grouping; never sent to a geocoder or server |
| Notifications | iOS, Android 13+ | Optional local Weekly Smart Clean reminder; requested only when enabled |

## Content and account declarations

- No KeepSpace account creation. Optional Google/Microsoft OAuth connection is used only for direct cloud backup.
- No user-generated content sharing, social features, ads, purchases, gambling, health claims, or location tracking.
- The app reads user-owned media but does not expose it to other users.
- Encryption export: the app uses standard HTTPS plus Keychain/Android Keystore and CryptoKit hashing; confirm the final App Store export-compliance answer against the signed binary.
