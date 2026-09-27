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

Android: open the `android/` folder in Android Studio (it provisions the JDK, SDK and Gradle wrapper).
