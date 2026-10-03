# KeepSpace — Runbook

คู่มือปฏิบัติงานสำหรับนักพัฒนา: ตั้งเครื่อง, build, รัน, ทดสอบ, ใส่ข้อมูลทดสอบ, ดู log และแก้ปัญหาที่เจอบ่อย
สถานะงานและสิ่งที่เหลือ: [`todo.md`](todo.md) · ส่งต่องาน/การตัดสินใจ/กับดัก: [`handoff.md`](handoff.md) · สถาปัตยกรรมและรายละเอียดฟีเจอร์: [`README.md`](README.md)

อัปเดตล่าสุด: 28 ก.ย. 2026 (privacy manifest และ public-API file sizing เสร็จแล้ว)

---

## 1. ภาพรวม

| | iOS | Android |
|---|---|---|
| โฟลเดอร์ | `ios/` | `android/` |
| UI | SwiftUI (iOS 17+, iPhone + iPad) | Jetpack Compose (API 26+, phone + tablet) |
| ภาษา / build | Swift 6, XcodeGen (`ios/project.yml`) | Kotlin 2.1, Gradle 8.11.1 (wrapper), AGP 8.10.1 |
| App ID | `com.keepspace.app` | `com.keepspace.app` (namespace ในโค้ด `com.smartstorage.cleaner`) |
| AI ในเครื่อง | Vision, CryptoKit | ML Kit (bundled), Tesseract (ไทย), Media3 |
| Cache | SwiftData ใน `Caches/analysis.store` | Room ใน `noBackupFilesDir/analysis.db` |
| Unit tests | `ios/SmartStorageTests` (Swift Testing) | `android/app/src/test` (JUnit, JVM) |

กฎที่ห้ามละเมิด (จากสเปก): AI/ข้อความ OCR อยู่ในเครื่อง; สื่อออกจากเครื่องได้เฉพาะ Cloud Backup ที่ผู้ใช้สั่งและไปตรงบัญชี Drive/OneDrive ของผู้ใช้, การลบทุกครั้งต้องผ่านหน้ายืนยันของระบบ, log ห้ามเก็บชื่อไฟล์ token ข้อความ หรือข้อมูลส่วนตัว

---

## 2. ตั้งเครื่อง dev (macOS, Apple Silicon)

### iOS
```bash
brew install xcodegen
```
- ต้องมี Xcode (ทดสอบกับ Xcode 27) + iOS Simulator runtime
- `ios/*.xcodeproj` ไม่อยู่ใน git → **ต้องรัน `xcodegen` ทุกครั้งหลัง clone หรือเพิ่ม/ลบไฟล์**

### Android (ไม่ต้องใช้ Android Studio)
```bash
brew install openjdk@17
```
```bash
brew install --cask android-commandlinetools
```
- ใช้ `openjdk@17` (cask `temurin@17` ต้องใช้ sudo และติดตั้งไม่ผ่าน)
- ตั้ง JAVA_HOME ทุกครั้งที่รัน Gradle:
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```
- SDK อยู่ที่ `/opt/homebrew/share/android-commandlinetools` — สร้าง `android/local.properties` (ไม่อยู่ใน git):
```bash
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > android/local.properties
```
- ติดตั้ง SDK + emulator image และสร้าง AVD `keepspace_tablet`:
```bash
sdkmanager "platform-tools" "emulator" "platforms;android-36" "build-tools;36.0.0" "system-images;android-36;google_apis;arm64-v8a"
```
```bash
avdmanager create avd -n keepspace_tablet -k "system-images;android-36;google_apis;arm64-v8a" -d pixel_tablet
```
- เครื่องมือที่ใช้บ่อย (ใส่ใน PATH หรือเรียกเต็ม path):
  - `adb` = `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`
  - `emulator` = `/opt/homebrew/share/android-commandlinetools/emulator/emulator`

---

## 3. Build และรันแอป

### iOS (Simulator)
```bash
cd ios && xcodegen && xcodebuild -project SmartStorage.xcodeproj -scheme SmartStorage -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build/dd build
```
```bash
xcrun simctl boot "iPhone 17"
```
```bash
xcrun simctl install "iPhone 17" ios/build/dd/Build/Products/Debug-iphonesimulator/SmartStorage.app
```
```bash
xcrun simctl privacy "iPhone 17" grant photos com.keepspace.app
```
```bash
xcrun simctl launch "iPhone 17" com.keepspace.app -hasCompletedOnboarding YES -debugTab library -debugRoute memories
```

Launch arguments (เฉพาะ Debug build, `Shell/DebugLaunch.swift`):

| Argument | ค่า |
|---|---|
| `-hasCompletedOnboarding YES` | ข้าม onboarding |
| `-demoData YES` | ใช้ข้อมูลตัวอย่างตาม mockup (ไม่อ่านคลังรูป) |
| `-debugTab` | `home` `clean` `library` `insights` `settings` |
| `-debugRoute` | `photoAccess` `notifications` `aiTaste` `privacy` `about` `cleanupPlan` `similarPhotos` `bestShot:<id>` (`bestShot:first`) `screenshots` `videos` `memories` `memory:<id>` (`memory:first`) `review:<kind>` `storageRules` `newRule` `receipts` `receiptFiling:<id>` `cloudOverview` `manualBackup` `backupVerification` |
| `review:<kind>` | `duplicates` `similar` `blurry` `oldScreenshots` `oldRecordings` `largeVideos` `expired` `screenshots.<kind>` (เช่น `screenshots.receipts`) |

iPad: เปลี่ยน destination เป็น `name=iPad Pro 13-inch (M5)` (หรือรุ่นที่มีใน `xcrun simctl list devices`)
หมุนจอ simulator จาก CLI ไม่ได้ — กด ⌘→ ในหน้าต่าง Simulator เอง

### Android (Emulator)
```bash
cd android && ./gradlew assembleDebug
```
```bash
emulator -avd keepspace_tablet -no-snapshot-save -no-audio -no-boot-anim &
```
```bash
adb wait-for-device && adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```
```bash
adb shell am start -n com.keepspace.app/com.smartstorage.cleaner.MainActivity
```

- **build ครั้งแรกต้องต่อเน็ต**: task `fetchTessdata` ดาวน์โหลดโมเดล Tesseract (tessdata_fast tag 4.1.0: `tha`, `eng` ~5 MB) ไปที่ `app/build/generated/tessdata` และตรวจ SHA-256 — ถ้า checksum ไม่ตรง build จะล้ม (อย่าแก้ checksum โดยไม่ตรวจที่มาของไฟล์)
- ให้สิทธิ์โดยไม่ต้องกด dialog:
```bash
for p in READ_MEDIA_IMAGES READ_MEDIA_VIDEO ACCESS_MEDIA_LOCATION; do adb shell pm grant com.keepspace.app android.permission.$p; done
```
- ล้างข้อมูลแอป (cache, กฎ, สถานะ onboarding) — หลังจากนี้ onboarding จะกลับมา ต้องกด **Continue** ก่อนแอปจะเริ่มสแกน:
```bash
adb shell pm clear com.keepspace.app
```
- Android ไม่มี debug launch arguments — นำทางด้วย `adb shell uiautomator dump` + `adb shell input tap x y`
  (node ที่อยู่นอกจอจะไม่อยู่ใน dump ต้อง scroll ก่อน)
- Release App Bundle (R8): `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
- Release machine setup (29 ก.ย. 2026): upload keystore อยู่ที่ `/Users/topgun/.keepspace/credentials/keepspace-upload.jks`, alias `keepspace-upload`; store/key passwords อยู่ใน macOS Keychain services `com.keepspace.android.upload.keystore.password` และ `com.keepspace.android.upload.key.password`. ห้ามนำรหัสผ่านหรือ keystore เข้า repo.
- Signed v1.1 AAB ถูก build/verify แล้ววันที่ 29 ก.ย. 2026; certificate SHA-1 `26:F7:E4:4A:BA:F2:4B:C4:F1:0A:29:4B:81:4C:3E:94:E8:86:AE:8D`.
- ถ้าไม่ตั้งค่าด้านล่าง bundle จะ unsigned (เหมาะกับ CI artifact เท่านั้น) สำหรับไฟล์ที่จะอัปโหลด Play ให้ตั้ง secret ผ่าน environment โดยห้าม commit keystore/รหัสผ่าน:
```bash
KEYSTORE_FILE=/absolute/path/keepspace-upload.jks \
KEYSTORE_PASSWORD='…' KEY_ALIAS='…' KEY_PASSWORD='…' \
./gradlew bundleRelease
```

### OAuth / Cloud Backup v1.1

ทำขั้นตอน production registration ใน `store/CLOUD_SETUP.md` ก่อน build สำหรับ Store. iOS ต้อง inject
`KEEP_SPACE_GOOGLE_CLIENT_ID` และ `KEEP_SPACE_MICROSOFT_CLIENT_ID`; Android ต้อง inject Microsoft client ID
และลงทะเบียน package/SHA-1 ของ Google โดยไม่ฝัง Google client ID ใน APK. ถ้าไม่มีค่า หน้า Cloud จะแสดง
`Setup required` แทนการเปิด flow ที่พัง

---

## 4. Tests

### Unit tests
- Monetization (`Monetization/` ↔ `monetization/`): เก็บเฉพาะสถานะ Pro กับตัวนับรายเดือนใน UserDefaults/SharedPreferences คีย์ `monetization.status.v1`, `monetization.usage.v1` (ไม่เก็บชื่อไฟล์); StoreKit 2 (`ProStoreService`) / Play Billing 7.1.1 (`ProBillingService`) ต่อแล้วแต่ยังไม่ได้ลองกับสินค้าจริง; Play Billing ไม่ให้วันหมดอายุบนเครื่อง จึงถือว่ารายการที่ Play ยังแสดงอยู่ใช้ได้ 1 วันแล้วอ่านใหม่ทุกครั้งที่เปิด/resume; Paywall + หน้า Subscription ทำแล้ว (ยังไม่ต่อ gate). ดู paywall แบบ debug: iOS `-debugPaywall YES|<ชื่อ ProFeature>` (ใช้ราคาตัวอย่าง), Android `--ez debugPaywall true --es debugPaywallFocus VideoCompression`; หน้า Subscription: iOS `-debugTab settings -debugRoute subscription`. ทดสอบ iOS ในเครื่อง: เปิดจาก Xcode (scheme ใช้ `ios/StoreKit/KeepSpace.storekit`)
```bash
cd ios && xcodegen && xcodebuild test -project SmartStorage.xcodeproj -scheme SmartStorage -destination 'platform=iOS Simulator,name=iPhone 17'
```
```bash
cd android && ./gradlew testDebugUnitTest
```
- สถานะล่าสุด: iOS 124 unit tests / 33 suites + 1 UI smoke test และ Android 119 unit tests ผ่านทั้งหมด
- ผล Android: `android/app/build/test-results/testDebugUnitTest/*.xml` · report HTML: `android/app/build/reports/tests/testDebugUnitTest/index.html`
- ถ้า `xcodebuild test` ค้างนาน (มัก hang ที่ `simctl diagnose` เมื่อ test crash หรือ simulator ยังไม่ boot) ให้แยกเป็น 2 ขั้น:
```bash
xcodebuild build-for-testing -project SmartStorage.xcodeproj -scheme SmartStorage -destination 'platform=iOS Simulator,name=iPhone 17'
```
```bash
xcrun simctl boot "iPhone 17"; xcodebuild test-without-building -project SmartStorage.xcodeproj -scheme SmartStorage -destination 'platform=iOS Simulator,name=iPhone 17'
```
- Swift Testing: อย่าใช้ `try!` กับ `#require` (ถ้าไม่ผ่านจะ crash ทั้ง test run) → ให้ test เป็น `throws` แล้วใช้ `try #require`

### Thai OCR spike (instrumented, Android)
เทียบ ML Kit กับ Tesseract บนรูปไทย ผลอยู่ที่ `android/app/src/androidTest/THAI_OCR_SPIKE.md`
```bash
android/app/src/androidTest/fetch_tessdata.sh
```
```bash
cd android && ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartstorage.cleaner.spike.ThaiOcrSpikeTest
```
```bash
adb logcat -d -v raw -s ThaiOcrSpike
```

### ข้อจำกัดของการทดสอบ
- iOS Simulator: Vision feature print และ scene classifier ให้ค่าเสื่อม (ทุกรูปเป็น `night_sky`) และอ่าน barcode ไม่ได้ →
  Similar Photos, ชื่อธีมอีเวนต์, การหาใบเสร็จไทยด้วย classifier ต้องยืนยันบน iPhone จริง
  (ทดสอบ logic Vision บน Mac ได้ด้วย `swift` script เล็ก ๆ — Mac ให้ค่าจริง)
- มี smoke UI test สำหรับเปิด demo data และนำทาง Home → Clean → Library → Settings (`SmartStorageUITests/StoreSmokeTests.swift`, `StoreSmokeTest.kt`) แต่ flow ลบผ่าน system confirmation ยังต้องทดสอบบนเครื่องจริง
- ยังไม่เคยรันบนเครื่องจริง

---

## 4.5 ไอคอนแอป

ไอคอนทั้งหมดสร้างจาก `icons/source/KeepSpace_icon_source_1254.png` ด้วยสคริปต์เดียว (ต้องมี Pillow):
```bash
python3 icons/build_icons.py
```
สคริปต์เขียนตรงเข้า `ios/.../AppIcon.appiconset`, `android/app/src/main/res/mipmap-*` และ `icons/build/ic_launcher-playstore.png`
(เหตุผลที่ไม่ใช้ไฟล์ export ตรง ๆ: ต้นฉบับมีพื้นหลังดำทึบ — ดู `icons/README.md`) ถ้าเปลี่ยนภาพต้นฉบับ ให้รันสคริปต์แล้ว commit ไฟล์ที่เปลี่ยน

- launcher ของ Android แคชไอคอนเก่า: หลังติดตั้งใหม่ถ้ายังเห็นไอคอนเดิม ให้ `adb shell am force-stop com.google.android.apps.nexuslauncher` แล้วกด Home
- iOS simulator: ติดตั้งใหม่ (uninstall → install) จึงจะเห็นไอคอนใหม่

---

## 5. ข้อมูลทดสอบ (รูป/วิดีโอ)

- รูปทดสอบ OCR ไทย/ใบเสร็จอยู่ใน git: `android/app/src/androidTest/assets/ocr/` (screenshot ไทย, สลิป, ใบเสร็จกระดาษ)
- สร้างรูปใหม่ด้วย Python PIL — ข้อควรระวัง:
  - PIL ไม่มี Thai shaping (raqm) → ใช้ฟอนต์ `Ayuthaya.ttf`; `Thonburi` จะได้วรรณยุกต์เป็นวงกลมจุด
  - ใส่ EXIF `DateTimeOriginal` **และ** `OffsetTimeOriginal` (0x9011) — ไม่งั้น Android ไม่เติม `DATE_TAKEN`
  - GPS ใส่เป็น `Fraction` (ไม่ใช่ tuple) ใน IFD 0x8825
  - รูปแต่ละรูปต้องต่างกันพอ ไม่งั้นจะถูกจัดเป็น Similar

### ใส่รูปเข้า iOS Simulator
```bash
xcrun simctl addmedia "iPhone 17" path/to/*.jpg
```
(ใส่ไฟล์เดิมซ้ำ = ได้ exact duplicate สำหรับทดสอบ)

### ใส่รูปเข้า Android Emulator
```bash
adb push photo.jpg /sdcard/DCIM/Camera/
```
```bash
adb push shot.png /sdcard/Pictures/Screenshots/
```
```bash
adb shell content call --uri content://media --method scan_volume --arg external_primary
```
- ไฟล์ใน `Pictures/Screenshots/` = screenshot, `DCIM/Camera/` = รูปกล้อง
- ตรวจว่า MediaStore เห็นแล้ว:
```bash
adb shell content query --uri content://media/external/images/media --projection _id:_display_name:relative_path:datetaken
```
- สร้าง exact duplicate: `adb shell cp /sdcard/DCIM/Camera/a.jpg /sdcard/DCIM/Camera/a_copy.jpg` แล้ว scan ใหม่

---

## 6. Log และการ debug

| | คำสั่ง | สิ่งที่ log |
|---|---|---|
| iOS | `xcrun simctl spawn "iPhone 17" log show --last 5m --predicate 'subsystem == "com.keepspace.app"' --info --style compact` | `scan:` จำนวนรายการ, cache hit, จำนวนที่ต้องวิเคราะห์/hash/อ่าน |
| Android | `adb logcat -s KeepSpaceScan KeepSpaceOcr` | เหมือน iOS + สถานะ Tesseract |

- ถ้าเพิ่ม log ชั่วคราวเพื่อดีบัก ให้ใช้ tag/ข้อความที่ค้นง่าย (เช่น `TMPPROBE`) และ **ลบก่อน commit** (`grep -rn TMPPROBE ios android` ต้องว่าง)
- ห้าม log ชื่อไฟล์ ข้อความ OCR หรือพิกัด ใน log ถาวร

---

## 7. Cache และ version

ผลวิเคราะห์ถูก cache ต่อ asset (ใช้ใหม่จนกว่าไฟล์ถูกแก้ `modifiedAt`) — ถ้าเปลี่ยน logic ต้อง bump version ให้ถูกตัว:

| เปลี่ยนอะไร | iOS | Android | ผล |
|---|---|---|---|
| ผลของ ImageAnalyzer (feature, metric ใหม่) | `analyzerVersion` (ตอนนี้ 2) | `ANALYZER_VERSION` (ตอนนี้ 3) | วิเคราะห์รูปทั้งหมดใหม่ |
| OCR/classifier/receipt extraction | `screenshotReaderVersion` (5) | `SCREENSHOT_READER_VERSION` (5) | อ่าน screenshot + รูปใบเสร็จใหม่ |
| schema ของ cache | SwiftData lightweight migration (property ใหม่ต้องมีค่า default) | Room `version` (ตอนนี้ 6) + destructive migration | Android ล้าง cache ทั้งหมด |
| โมเดล Tesseract | — | `fetchTessdata` checksums + `ThaiOcr.MODELS_VERSION` | copy โมเดลใหม่เข้า `noBackupFilesDir/tesseract` |

- `HashRecord` (iOS) / `CachedHash`: เก็บ assetID, modifiedAt, bytes, sha256; `sha256` เป็น optional สำหรับวิดีโอที่วัดขนาดแล้วแต่ยังไม่ได้อ่านและรูปที่ถูกแก้ โดย record เดียวกันเก็บผลวัดขนาดและ hash เพื่อลด I/O
- ข้อมูลที่ต้องไม่หาย อยู่นอก cache: กฎ Storage Rules (`storageRules.v1`), AI Taste (`taste.enabled`, `taste.v1` / `taste.*`), การแจ้งเตือน (`weeklyClean.*`) — iOS `UserDefaults` · Android SharedPreferences `keepspace`
- ข้อมูล v1.1+ (บัญชี cloud, คิวอัปโหลด) **ห้าม** ใส่ใน cache ที่ล้างได้ — ต้องเป็น store แยก
- ล้าง cache: iOS ลบแอป (`xcrun simctl uninstall "iPhone 17" com.keepspace.app`) · Android `adb shell pm clear com.keepspace.app`

---

## 8. ปัญหาที่เจอบ่อย

| อาการ | สาเหตุ / วิธีแก้ |
|---|---|
| Vision: "Failed to create espresso context" บน simulator | ใช้ CPU บน simulator (`ImageAnalyzer.preferCPUOnSimulator`) — ทำไว้แล้ว |
| Similar Photos ว่างบน iOS simulator | feature print เสื่อม แอปปิด grouping อัตโนมัติ — ทดสอบบนเครื่องจริง |
| Gradle: `Unresolved reference: security` ใน `build.gradle.kts` | ใน Kotlin DSL `java` คือ extension ไม่ใช่ package → `import java.security.MessageDigest` ที่หัวไฟล์ |
| Build Android ล้มที่ `fetchTessdata` | ไม่มีเน็ต หรือ checksum ไม่ตรง — ตรวจการเชื่อมต่อ/ที่มาของไฟล์ |
| Android OCR อ่านไม่ออก/ภาพเล็ก | `ContentResolver.loadThumbnail` ถูกจำกัดราว 600–800 px — งานที่ต้องการรายละเอียดใช้ `decodeUpright()` |
| สลิปไทยบน Android ไม่ถูกจัดเป็นใบเสร็จ | สลิปมี QR → ML Kit จัดเป็น QR Codes; ต้องผ่านรอบ Tesseract (`needsThai` รวม QR แล้ว) |
| ไม่มีทริปใน Memories บน Android | ไม่ได้ให้ `ACCESS_MEDIA_LOCATION` หรือรูปไม่มี GPS |
| `uiautomator dump` แสดง `&` เป็น `&amp;` (เช่น "Privacy &amp; Security") | ใช้ข้อความที่ escape แล้วเวลาจับ node ด้วยข้อความ |
| ทดสอบการแจ้งเตือน Android | เปิดสวิตช์ใน Notifications → Allow; ดู job: `adb shell dumpsys jobscheduler \| grep -A22 "androidx.work.systemjobscheduler"` (Minimum latency = เวลาถึงช่วงถัดไป); อ่านข้อความ: `adb shell dumpsys notification --noredact \| grep -A12 "pkg=com.keepspace.app"`; เปิดเงา: `adb shell cmd statusbar expand-notifications` |
| ทดสอบการแจ้งเตือน iOS | เปิดล่วงหน้าด้วย launch arg `-weeklyClean.enabled YES` (ต้องให้สิทธิ์แจ้งเตือนใน simulator ก่อนถึงจะตั้งเวลา); ปุ่ม "Send a Test Notification" ยิงใน 3 วินาที |
| Emulator ช้า/ANR | ปิด iOS simulator ที่ไม่ใช้, restart emulator แล้วให้สิทธิ์ใหม่ |
| สแกนไม่เริ่มหลัง `pm clear` | ยังค้างที่ onboarding — กด Continue |
| `sleep` ยาวใน script ถูกบล็อก (ในเครื่องมือ agent) | ใช้ loop รอเงื่อนไข เช่น `until adb logcat -d -s KeepSpaceScan \| grep -q scan:; do sleep 3; done` |
| Timing ของ spike แกว่ง | emulator ช้าลงเมื่อรันนาน — ดูตัวเลขจาก run ที่เครื่องว่าง |
| เครื่องมือ agent ตอบ "auto mode classifier gave no verdict" | ปัญหาฝั่งเซิร์ฟเวอร์ชั่วคราว ไม่เกี่ยวกับคำสั่ง — ลองซ้ำครั้งเดียว แล้วหยุดรอ (ยิงถี่ ๆ ครบ 10 ครั้งติดกันเทิร์นจะถูกตัด); อ่านไฟล์ได้ตามปกติ (อ่าน `.git/logs/HEAD` แทน `git log`) |
| ไอคอนใหม่ไม่ขึ้นบน simulator / launcher ยังโชว์ไอคอนเก่า | iOS: uninstall → install ใหม่; Android: force-stop launcher แล้วกด Home (ดู หัวข้อ 4.5) |

---

## 9. Git และการ commit

- Remote: `https://github.com/anuwat-topgun/KeepSpace.git` (public) · branch `main`
- ก่อน commit: unit tests ผ่านทั้งสองแพลตฟอร์ม, ไม่มี log ชั่วคราว, อัปเดต README/todo.md ถ้าฟีเจอร์เปลี่ยน
- ไฟล์ที่ไม่อยู่ใน git: `ios/*.xcodeproj`, `android/local.properties`, build outputs, โมเดล Tesseract (`app/build/generated/tessdata`, `androidTest/assets/tess_*`)
- ทุก feature: ทำทั้ง iOS และ Android ให้ logic เหมือนกัน (ไฟล์ pure logic มีคู่กัน เช่น `EventGrouper.swift` ↔ `EventGrouper.kt`) และมี unit test ทั้งสองฝั่ง

---

## 10. การปล่อยแอป

baseline ใน repo พร้อมสำหรับ TestFlight / Play Internal Testing แล้ว: launch screen, permission copy, public privacy policy/terms, Store listing copy, privacy declarations, review notes, API 36, release signing hook, App Bundle, smoke UI tests และ CI อยู่ใน `store/`, `legal/`, `.github/workflows/ci.yml`

production gate ที่ยังต้องใช้เจ้าของบัญชีหรือฮาร์ดแวร์จริง: Android upload keystore + Play App Signing, Apple distribution signing/TestFlight, กรอกแบบฟอร์มใน Store, screenshots และทดสอบเครื่องจริง/คลัง 10k+ ตาม [`store/RELEASE_CHECKLIST.md`](store/RELEASE_CHECKLIST.md)
v1.1 cloud ต้องใช้ OAuth client ของ KeepSpace เอง (Google Cloud Console + Microsoft Entra) — ขั้นตอนอยู่ใน `todo.md` ข้อ 3.2

---

## 11. Privacy manifest (iOS)

ไฟล์: `ios/SmartStorage/Resources/PrivacyInfo.xcprivacy` (xcodegen หยิบให้เอง) — ประกาศ: ไม่เก็บข้อมูล, ไม่ track, และเหตุผลของ required-reason API ที่แอปเรียกเอง:

| Category | Reason | ใช้ที่ไหน |
|---|---|---|
| `NSPrivacyAccessedAPICategoryUserDefaults` | `CA92.1` | ตั้งค่าของแอป: onboarding, Storage Rules, AI Taste, Weekly Smart Clean (`@AppStorage`, `UserDefaults`) |
| `NSPrivacyAccessedAPICategoryDiskSpace` | `85F4.1` | แสดงพื้นที่ว่าง/ทั้งหมดบน Home และ Insights (`LibraryStore.deviceStorage`) |

**ต้องอัปเดตไฟล์นี้เมื่อโค้ดเริ่มเรียก API กลุ่ม required-reason ใหม่** ก่อน commit ให้ audit (คำสั่งละบล็อก):
```bash
grep -rn "UserDefaults\|@AppStorage" ios/SmartStorage
```
```bash
grep -rn "volume.*Key\|systemFreeSize\|systemSize\|AvailableCapacity" ios/SmartStorage
```
```bash
grep -rn "creationDateKey\|contentModificationDate\|fileModificationDate\|NSFileCreationDate\|NSFileModificationDate\|stat(\|getattrlist" ios/SmartStorage
```
```bash
grep -rn "systemUptime\|mach_absolute_time\|clock_gettime\|activeInputModes" ios/SmartStorage
```
- ผลตอนตรวจ (28 ก.ย.): สองคำสั่งแรกมีผลตรงกับตารางข้างบน · คำสั่งที่สามเจอแค่ฟังก์ชัน helper ชื่อ `stat(...)` ใน `MemoryDetailView` (ไม่ใช่ API ของระบบ — false positive) · คำสั่งที่สี่ว่าง
- ไฟล์ผลลัพธ์จาก video compression อ่านขนาดด้วย `URLResourceValues.fileSizeKey`; ไม่มีการอ่าน timestamp
- ถ้าเพิ่มไลบรารีภายนอกบน iOS (ตอนนี้ไม่มี) ต้องตรวจว่ามี manifest ของตัวเองและอัปเดตของเราให้ครอบคลุม
- ตรวจไฟล์: `plutil -lint ios/SmartStorage/Resources/PrivacyInfo.xcprivacy` และ `plutil -p <SmartStorage.app>/PrivacyInfo.xcprivacy` หลัง build
- ถ้าแอปเริ่มส่งข้อมูลออกนอกเครื่อง (เช่น analytics, v1.1 cloud) ต้องแก้ `NSPrivacyCollectedDataTypes` และคำตอบ App Privacy ใน App Store Connect ให้ตรงกัน
