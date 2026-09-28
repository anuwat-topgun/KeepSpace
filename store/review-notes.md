# Store review notes — KeepSpace 1.1

## Apple App Review

KeepSpace analyzes the user's photo library entirely on-device and has no ads, analytics, or developer-operated backend. Optional Cloud Backup uses OAuth and sends only user-selected files directly to Google Drive or OneDrive. Grant full or limited photo access during onboarding. All deletion actions use the iOS system confirmation and send items to Recently Deleted.

Suggested review path:

1. Complete onboarding and grant photo access.
2. Open Home to allow the local scan to finish.
3. Open Clean and choose a target to see the Safety Score and cleanup plan.
4. Open Library for Similar Photos, Screenshots, Videos, and Memories.
5. Open Settings > Cloud, connect a supplied review Google/Microsoft account, then use Back Up Now to select Photos, Screenshots, or Receipts and a destination folder.
6. Open Settings > Privacy & Security to inspect the local-processing explanation and clear the regenerable cache.

The reviewer may need a library containing duplicates, screenshots, and a video to exercise all features. There is no demo-mode control in the production UI. Add temporary provider review credentials in App Store Connect review notes; never commit them to this repository.

## Google Play review

KeepSpace requires photo/video access for its core on-device library-cleanup function. Selected-photo access is supported on Android 14+. `ACCESS_MEDIA_LOCATION` is used only to group trips and events on-device. Notification permission is requested only if the reviewer enables Weekly Smart Clean under Settings > Notifications.

All deletion uses Android's system trash/confirmation flow. Cloud jobs use WorkManager, honor the Wi-Fi-only preference, retry transient failures, and always keep the local original in v1.1. The release is an Android App Bundle so Play serves only the native libraries needed by the device.
