# Store privacy declarations for KeepSpace 1.1

Last reviewed: September 28, 2026

These answers describe the code in this repository at version 1.1, including optional direct-to-provider Cloud Backup.

## Apple App Privacy

- **Does this app collect data?** Yes, for the optional Cloud Backup feature.
- **Data used to track you:** None.
- **Data linked to you:**
  - **User Content → Photos or Videos** — App Functionality; optional; the selected image is retained in the user's Google Drive or OneDrive account.
  - **Contact Info → Email Address** — App Functionality; optional; used by the connected provider for OAuth/account display.
  - **Identifiers → User ID** — App Functionality; optional; used by the connected provider to authorize the user's cloud account.
- **Data not linked to you:** None.
- **Privacy Policy URL:** `https://github.com/anuwat-topgun/KeepSpace/blob/main/legal/privacy-policy.md`

Reasoning: KeepSpace does not receive data and has no backend, and AI data remains on-device. However, Apple's privacy-label guidance says to disclose the specific media type when an app enables uploads. These conservative answers cover the optional OAuth connection and user-initiated image upload even though the developer cannot access the provider account. Do not declare precise location, OCR text, face data, analytics, advertising, or tracking because none leaves the device through KeepSpace.

## Google Play Data safety

- **Does the app collect or share any required user data types?** No.
- **Is all user data encrypted in transit?** Yes. OAuth and uploads use provider HTTPS endpoints.
- **Can users request deletion?** No account or server-side collection exists. Users clear the analysis cache in the app, clear app storage, or uninstall the app.
- **Security practices:** No data is sold or used for advertising, tracking, or analytics.

Cloud Backup is a prominent, user-initiated transfer to the user's own external cloud account; KeepSpace never receives or later accesses the file. Google Play's current Data safety guidance explicitly says this external-drive case does not need to be declared as collection; the user-initiated-action exception also means it is not declared as sharing. Reconfirm in Play Console at submission time.

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
