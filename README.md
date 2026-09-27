# Smart Storage Cleaner — Complete Dev Handoff (v1 to v1.3)

This package contains the complete delivery handoff for Smart Storage Cleaner from **v1.0 through v1.3**.

## Included documents

- `01_Product_Technical_Spec_v1_to_v1_3.md`
  - full product and technical specification for v1.0, v1.1, v1.2, and v1.3
  - includes scope, architecture, rules engine, cloud sync, semantic filing, and safe delete requirements

- `02_Design_Handoff_v1_to_v1_3.md`
  - visual and UX handoff for all designed screens
  - includes shared design system guidance and screen-by-screen notes

## Included design assets

Folder: `screenshots/`

### v1.0
1. `01_Onboarding_Privacy.png`
2. `02_Home_Dashboard.png`
3. `03_Clean_Select_Target.png`
4. `04_Cleanup_Plan.png`
5. `05_Similar_Photos_Groups.png`
6. `06_Best_Shot_Review.png`
7. `07_Insights_Storage_Forecast.png`
8. `08_Videos_Compression.png`
9. `09_Settings_Privacy_OnDeviceAI.png`
10. `10_Memories_Protected.png`
11. `11_Screenshots_Semantic_Cleanup.png`

### v1.1
12. `12_Cloud_Sync_Overview_v1_1.png`
13. `13_Manual_Backup_Receipts_v1_1.png`

### v1.2
14. `14_Storage_Rules_v1_2.png`
15. `15_New_Rule_Builder_v1_2.png`

### v1.3
16. `16_Receipt_Filing_v1_3.png`
17. `17_Backup_Verification_Safe_Delete_v1_3.png`

## Recommended dev flow

1. Read the product / technical spec first.
2. Read the design handoff second.
3. Build the shared design system and app shell.
4. Implement v1.0 first.
5. Add v1.1 cloud sync flows.
6. Add v1.2 rules engine and rule-builder UI.
7. Add v1.3 receipt filing and backup verification flows.

## Important product constraint

The app must remain **on-device AI first**.
Media files should never be uploaded to our own backend.
Uploads go directly from the device to Google Drive or OneDrive.

## Codebase

Native apps, one per platform, both supporting phone and tablet:

| Folder | Stack | Min OS |
|---|---|---|
| `ios/` | SwiftUI, generated with [XcodeGen](https://github.com/yonaskolb/XcodeGen) | iOS / iPadOS 17 |
| `android/` | Kotlin + Jetpack Compose (Material 3 adaptive) | Android 8.0 (API 26) |

Bundle / application ID: `com.keepspace.app` (App Store name: "Keep space. Keep what matters.")

### Structure (same on both platforms)

- `DesignSystem` / `ui/theme` + `ui/components` — color tokens, type scale, cards, buttons, badges, rows, layout helpers
- `Shell` / `ui/shell` — adaptive app shell and navigation (one back stack per tab)
- `Features` / `ui/feature` — one folder per screen
- `Model` / `model` — data models (mock data for now)

### Adaptive layout

| Window | iOS | Android |
|---|---|---|
| Phone | Bottom tab bar | Bottom navigation bar |
| Phone landscape / foldable | Bottom tab bar | Navigation rail |
| Tablet | Sidebar (`NavigationSplitView`) | Permanent drawer (`NavigationSuiteScaffold`) |

Content is capped at 680pt/dp (single column) or 960pt/dp (grids) so cards never stretch edge to edge;
grids add a column only when each column stays at least 320pt/dp wide.

### Build

iOS:

```bash
cd ios && xcodegen generate && open SmartStorage.xcodeproj
```

Android: open the `android/` folder in Android Studio, or build from the terminal with JDK 17
(`brew install openjdk@17`) and an SDK containing `platforms;android-35` + `build-tools;35.0.0`:

```bash
cd android && echo "sdk.dir=$ANDROID_HOME" > local.properties
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

### Media engine (on-device AI)

Everything runs on the device; nothing is uploaded.

| | iOS | Android |
|---|---|---|
| Library | PhotoKit (`LibraryEngine`) | MediaStore (`MediaStoreSource`) |
| Similar photos | Vision feature prints, Euclidean distance ≤ 0.55 | 64-bit dHash, Hamming distance ≤ 12 |
| Blur | Laplacian variance on a 256 px grayscale thumbnail (< 60 = blurry) | same |
| Best shot | sharpness + exposure + Vision face-capture quality | sharpness + exposure + ML Kit eyes-open (bundled model) |
| Scene titles | Vision `VNClassifyImageRequest` | — (date titles) |

Photos are grouped only when taken within 2 minutes of each other *and* they look alike.

**Incremental scanning.** Analysis results are cached per asset (SwiftData on iOS, Room on Android),
keyed by asset ID + modification time + `analyzerVersion`. A scan shows cached results immediately,
analyzes only new or edited photos (saving every 25), and drops entries for deleted photos.
The cache lives in `Caches/` (iOS) and `no_backup/` (Android): it is regenerable and is never backed up.
Bump `analyzerVersion` / `ANALYZER_VERSION` whenever analyzer output changes. Scan logs contain counts only.
The pure logic (grouping, scoring, report/plan/forecast) is unit-tested on both platforms:

```bash
cd ios && xcodebuild test -scheme SmartStorage -destination 'platform=iOS Simulator,name=iPhone 17'
```

```bash
cd android && ./gradlew testDebugUnitTest
```

Notes:
- **iOS Simulator:** Vision models can't use the GPU/Neural Engine there and return near-identical
  feature prints, so similarity grouping is disabled automatically (blur, sizes and forecast still work).
  Calibrate the similarity threshold on a real iPhone.
- **Demo data:** launch with `-demoData YES` (iOS) or `--ez demoData true` (Android) to show the mockup data set.

### Screenshot categories (on-device OCR)

Screenshots are read on the device and sorted into Shopping, Receipts, Chats, QR Codes, Tickets and Other;
tickets whose date has passed become **Expired Tickets**.

| | iOS | Android |
|---|---|---|
| Text | Vision `VNRecognizeTextRequest` (Thai + English) | ML Kit Text Recognition (bundled, **Latin only**) |
| Codes | Vision barcodes (QR, Aztec, PDF417) | ML Kit Barcode Scanning (same formats) |

- The recognised text is used only to classify and is **never stored or uploaded**; the cache keeps the category,
  a ticket's date and route (e.g. `BKK → HND`).
- `ScreenshotClassifier` (keywords in English and Thai, prices, chat timestamps, QR presence) and `DateExtractor`
  (ISO, day-first numeric, `12 SEP 2026`, `Sep 12, 2026`, Buddhist-era years) are pure and unit-tested.
- Receipts and upcoming tickets are never part of "Old Screenshots"; category browsing starts with nothing selected.
- Bump `screenshotReaderVersion` / `SCREENSHOT_READER_VERSION` when classification changes: screenshots are re-read,
  photos keep their cached analysis.
- Known limits: the iOS Simulator can't run Vision's barcode model (works on devices and on macOS); Android can't
  read Thai words, so Thai receipts are recognised from their amounts and Thai transfer slips fall back to QR Codes.

### Receipt Filing (v1.3, extraction + rules)

Receipts found in screenshots get their details read on the device and a filing destination from the rules engine:

- `ReceiptExtractor`: merchant (header line, or recipient on transfer slips), date (incl. Thai "27 ก.ย. 69"),
  total (labelled total beats subtotal; else the largest amount), currency, spending category.
- `TemplateResolver` (spec §7.6–7.7): `{YEAR} {MONTH} {DAY} {DATE} {MERCHANT} {AMOUNT} {CATEGORY} {EVENT}
  {MEDIA_TYPE} {ORIGINAL_NAME} {INDEX}`, with characters Drive/OneDrive reject removed and short merchant names
  ("Central Department Store" → `Central`, honorifics dropped).
- `StorageRule.defaults` are the spec's example rules until the rule builder (v1.2 UI) exists.
- Extracted details are stored on the device only. **Uploading needs v1.1 cloud accounts**; until then the
  Receipt Filing screen says so and offers to connect.
- Paper receipts photographed with the camera aren't read yet (screenshots only).

### Deleting and compressing (safety model)

- Nothing is removed without the **system confirmation**: PhotoKit's prompt on iOS (→ Recently Deleted, 30 days),
  `MediaStore.createTrashRequest` on Android 11+ (→ Trash, 30 days). Android 8–10 has no system prompt, so the app
  confirms in-app first.
- Review sets never offer a group's keeper or favorites, and large personal videos start unselected.
- Compression replaces a video only if the result is ≥ 20 % and ≥ 5 MB smaller. The new copy keeps the capture date
  (Android writes it into the MP4 header, since MediaStore reads `DATE_TAKEN` from there); on iOS saving the copy and
  deleting the original is one PhotoKit change, so cancelling leaves the library untouched. On Android a declined
  trash request removes the new copy again.
- Confirmation messages say the space returns only after the trash is emptied.
