# Store review notes — KeepSpace 1.1 (with KeepSpace Pro)

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

## In-app purchases (KeepSpace Pro) — both stores

KeepSpace is free to use and Pro is optional. There is no account and no server: Pro status is read from the store on the device. Products (same IDs on both stores):

| Product | Type | Notes |
|---|---|---|
| `com.keepspace.app.pro.annual` | Auto-renewing subscription | 7-day free trial for new subscribers; preselected on the purchase screen |
| `com.keepspace.app.pro.monthly` | Auto-renewing subscription | Same subscription group (iOS) |
| `com.keepspace.app.pro.lifetime` | Non-consumable (iOS) / one-time product (Android) | One purchase, no renewal |

Where to find it: **Settings > Subscription** (shows the plan, this month's free allowance, **Upgrade to Pro**, **Restore Purchases**, and **Manage Subscription** when subscribed). The purchase screen also opens when you tap something that needs Pro. It is never shown at launch, during onboarding, or while a scan runs.

What is Free: scanning, every category and size, Safety Score, Best Shot, protected memories, Weekly Smart Clean, confirmation before every deletion, about 1 GB of confirmed deletions per month, about 100 backup files per month with one cloud account, and one storage rule. What Pro adds: unlimited cleanup, video compression, opening screenshot categories, Receipt Filing, AI Taste, unlimited backup and rules, and several cloud accounts.

To test: use a sandbox Apple ID (iOS) or a Play license tester account (Android). Buy any plan, confirm Pro unlocks, then use **Restore Purchases** on a fresh install. Prices, currency, trial length and trial eligibility shown on the purchase screen come from the store, not from the app. The subscription terms (auto-renewal, how to cancel) and links to the Terms of Use and Privacy Policy are on the purchase screen, which also has **Restore**.

To see the free limit: in Review, select more than about 1 GB of items and tap Delete; a sheet offers "Delete (safest first)" for what fits, or the purchase screen. Pro never deletes or hides existing user data when it ends.

## Google Play review

KeepSpace requires photo/video access for its core on-device library-cleanup function. Selected-photo access is supported on Android 14+. `ACCESS_MEDIA_LOCATION` is used only to group trips and events on-device. Notification permission is requested only if the reviewer enables Weekly Smart Clean under Settings > Notifications.

All deletion uses Android's system trash/confirmation flow. Cloud jobs use WorkManager, honor the Wi-Fi-only preference, retry transient failures, and always keep the local original in v1.1. The release is an Android App Bundle so Play serves only the native libraries needed by the device.
