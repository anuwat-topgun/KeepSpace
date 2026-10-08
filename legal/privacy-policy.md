# KeepSpace Privacy Policy

Effective date: October 8, 2026

KeepSpace is an on-device photo and video cleanup and direct cloud-backup app provided by the independent developer of KeepSpace ("KeepSpace," "we," "us," or "our"). This policy explains how version 1.1 of the KeepSpace iOS and Android apps handles information.

## Summary

KeepSpace does not collect, sell, or use personal data for tracking or advertising. Photo analysis, duplicate detection, optical character recognition, recommendations, and storage calculations run on your device. If you explicitly start Cloud Backup, selected files are transmitted over HTTPS directly from your device to your own Google Drive or Microsoft OneDrive account. KeepSpace has no developer-operated media backend, analytics, advertising, or third-party tracking. If you buy KeepSpace Pro, the purchase is handled entirely by Apple or Google Play; KeepSpace does not receive your payment details or identity.

## Information processed on your device

With your permission, KeepSpace can access the photos and videos you select, or your full media library if you grant full access. The app may process image features, file hashes, file size, capture date, favorite status, coarse photo location metadata, recognized text, faces, and video metadata to provide cleanup suggestions, group memories, and compress videos.

This processing happens on your device. Recognized text and face data are not retained. Regenerable analysis results, such as categories, similarity features, hashes, storage measurements, and receipt fields, may be cached locally so later scans are faster. Your preferences, cleanup rules, reminder settings, Pro status, and monthly usage counters are also stored locally.

KeepSpace does not upload your media or analysis results to a KeepSpace server. Cloud Backup is optional and user initiated. OAuth access and refresh tokens are stored in the iOS Keychain or encrypted with Android Keystore; the upload queue and preferences remain on your device.

## KeepSpace Pro purchases

Subscriptions and the lifetime purchase are processed by Apple (iOS) or Google Play (Android). KeepSpace does not see your card or payment details, name, or store account. To know whether Pro is active, the app asks the store on your device which purchases your store account owns. It keeps only the result (for example, "Pro, yearly, renews on a given date") and a few counters on your device: how much cleanup and backup you used in the current calendar month. These counters are numbers only; they contain no file names, photo content, or identifiers. Nothing about your purchases or usage is sent to a KeepSpace server, because there is none. Apple and Google handle store transactions under their own privacy policies.

## Optional Cloud Backup

When you connect Google Drive or OneDrive, the provider processes your sign-in and grants KeepSpace a limited authorization token. KeepSpace uses Google Drive's per-app file permission (`drive.file`) or Microsoft OneDrive's app-folder permission (`Files.ReadWrite.AppFolder`). Selected photos, screenshots, or receipt images go directly from the app to the chosen provider. The developer cannot view or retrieve those files. Google or Microsoft handles the files under its own terms and privacy policy.

You can disconnect a provider in **Settings > Cloud**. Disconnecting removes local credentials but does not delete files already copied to your cloud account. You can delete those files with the provider's app or website. Version 1.1 always keeps the local original and never deletes media after backup.

## Permissions

- **Photos and videos:** used to scan media you allow, display recommendations, save a compressed copy you request, and ask the operating system to move items you confirm to Recently Deleted or Trash.
- **Photo location metadata:** used only on the device to distinguish trips from home-area events. It is not used for tracking or sent to a geocoding service.
- **Notifications:** optional and requested only if you enable the Weekly Smart Clean reminder. The reminder is generated locally from the most recent scan.

You can change permissions in iOS or Android Settings. Limited or selected-photo access is supported. KeepSpace asks the operating system for confirmation before deleting media.

## Service providers and app stores

KeepSpace uses Apple system frameworks on iOS and bundled Android libraries including AndroidX, AppAuth, ML Kit, Tesseract, Room, and Media3. Google and Microsoft process authentication and user-requested cloud files only when you connect or use their services. Apple and Google may independently process information when you download the app, buy or restore KeepSpace Pro, or use their operating-system services; their privacy policies govern that activity.

## Retention and deletion

KeepSpace keeps local settings until you change them, clear them, or uninstall the app. You can clear the regenerable analysis cache from **Settings > Privacy & Security > Clear Cache**. Uninstalling KeepSpace removes its local app data. Media moved to Recently Deleted or Trash remains subject to the retention and recovery controls of iOS or Android.

Because KeepSpace does not create an account or collect data on its servers, there is no KeepSpace server-side personal data to request or delete. Purchase records are held by Apple or Google; manage or request deletion of them with your store account. Cloud copies are controlled through your Google or Microsoft account.

## Children

KeepSpace is a general-audience utility and is not directed to children under 13. We do not knowingly collect personal information from children or anyone else.

## Changes to this policy

If a future version changes how information is handled, we will update this policy and the applicable App Store or Google Play privacy disclosures before that version is released. The effective date above identifies the current policy.

## Contact

Privacy questions can be submitted through the [KeepSpace support page](https://keepspace.itston.com/support/). Do not include private photos, recognized text, precise locations, credentials, or other sensitive information in a public issue.
