# KeepSpace — Developer Handoff

เอกสารส่งต่องานสำหรับคนที่จะทำต่อ (คนหรือ AI agent): แอปคืออะไร ทำอะไรไปแล้ว ตัดสินใจอะไรไว้เพราะอะไร งานที่ค้างอยู่จุดไหน และกับดักที่เจอมา
ไม่ซ้ำกับเอกสารอื่น — อ่านคู่กัน:

| เอกสาร | ใช้ทำอะไร |
|---|---|
| [`todo.md`](todo.md) | สถานะรายฟีเจอร์ + งานที่เหลือละเอียด (แหล่งความจริงของ "ทำอะไรไปแล้ว/ยังไม่ทำ") |
| [`runbook.md`](runbook.md) | คำสั่ง build / run / test / log / แก้ปัญหา |
| [`README.md`](README.md) | สถาปัตยกรรมและรายละเอียดฟีเจอร์แต่ละตัว |
| `01_Product_Technical_Spec_v1_to_v1_3.md`, `02_Design_Handoff_v1_to_v1_3.md` | สเปกและ design handoff ต้นฉบับจากทีมออกแบบ (ไม่ใช่เอกสารนี้ — ไม่ได้แก้) |
| [`icons/README.md`](icons/README.md) | ที่มาของไอคอนและสคริปต์สร้างไอคอน |

อัปเดตล่าสุด: 30 ก.ย. 2026 · เพิ่ม Library แบบ Photos, cloud status, 25 ภาษา และ Google Sign-In SDK บน iOS แล้ว

---

## 1. แอปคืออะไร (สั้น ๆ)

KeepSpace ("Keep space. Keep what matters.") ช่วยคืนพื้นที่เก็บข้อมูลโทรศัพท์อย่างปลอดภัย โดย **AI ทำงานในเครื่องทั้งหมด** — รูป วิดีโอ และข้อความ OCR ไม่ออกจากเครื่อง
Native สองตัว รองรับ phone + tablet: `ios/` (SwiftUI, iOS 17+) และ `android/` (Jetpack Compose, API 26+) · bundle/app ID `com.keepspace.app`
App Store Connect: app id `6816584493`, SKU `keepspace-ios-001` · repo: `https://github.com/anuwat-topgun/KeepSpace.git` (public) branch `main`

กฎที่ต้องรักษา (จากสเปก §3 และที่ผู้ใช้เน้น):
1. ไม่อัปโหลดสื่อขึ้น backend ของเรา; อัปโหลด cloud (v1.1+) ไปตรงที่ Drive/OneDrive เท่านั้น
2. การลบทุกครั้งต้องผ่านหน้ายืนยันของระบบ; ของที่ลบไปอยู่ถัง 30 วัน; รูปโปรดและรูปใน Memories ไม่ถูกเลือกไว้ล่วงหน้า
3. log ถาวรเก็บแค่ตัวเลข (ห้ามชื่อไฟล์/ข้อความ OCR/พิกัด); ข้อความ OCR ไม่ถูกเก็บ
4. คำแนะนำต้องอธิบายได้ (Safety Score, เหตุผลของ Best Shot)

---

## 2. ทำอะไรไปแล้ว (ภาพรวม — รายละเอียดใน `todo.md`)

v1.0 Core ครบทั้งสองแพลตฟอร์ม: Storage Analysis, Similar Photos + Best Shot, Exact Duplicates, Blurry, Screenshots (OCR แยกหมวด, ไทย/อังกฤษ), Large Videos / Recordings + บีบอัดวิดีโอ,
Memories (ทริป/อีเวนต์), Safety Score, Clean X GB + Cleanup Plan, Insights/Forecast, Weekly Smart Clean (แจ้งเตือนรายสัปดาห์), Personalized AI Taste, Settings (Photo Access / Privacy / Notifications / AI Taste / About), ไอคอนครบทุกขนาด และ localization 25 ภาษาเหมือน IPTV Prime
v1.2: Storage Rules + Rule Builder · v1.3 (ส่วนไม่ใช้ cloud): Receipt Filing (screenshot + ใบเสร็จกระดาษจากรูปกล้อง)

**มีแล้วใน v1.1:** Google Drive/OneDrive auth, secure token storage, persistent upload queue, direct upload + remote-ID verification, Cloud/Back Up Now UI, retry/cancel/Wi-Fi-only, production OAuth clients ฝั่ง iOS/Microsoft, Google Android debug/release clients และ release/privacy docs. iOS ใช้ Google Sign-In SDK + reversed client-ID callback; OneDrive จำกัดเฉพาะบัญชี Microsoft ส่วนบุคคลทั้งใน Entra และ OAuth authority. Library แบบ Photos แสดง All/Photos/Videos/Backed Up, badge สถานะ cloud, preview รูปแบบ aspect-fit, ปัดซ้าย/ขวา, ปัดลงเพื่อปิด และเล่นวิดีโอ/fullscreen ได้ทั้งสองแพลตฟอร์ม. Select All เลือกชุดที่มองเห็นจริงและตัด ID เก่า/รายการที่ต้องเก็บออกแล้ว. Android upload keystore ถูกสร้างนอก repo, เก็บรหัสผ่านใน macOS Keychain และ build signed AAB ผ่านแล้ว. **ยังไม่มี:** Google Play App Signing OAuth client, end-to-end cloud sign-off ทุก provider/แพลตฟอร์ม, การเชื่อมกฎเข้าคิวอัปโหลด, safe delete (v1.3), Subscription/Monetization, Analytics

Tests baseline ก่อน commit ชุดนี้: iOS 86 unit tests และ Android 77 unit tests + `assembleDebug` ผ่าน; i18n validator ผ่าน 25 locales × 390 keys; iOS มี UI smoke test ครอบเส้นทางหลักและหน้า Cloud, Android UI test compile ผ่าน. Signed iOS build ด้วย Team `Anuwat Palasak` (`82MGZU2HH4`) ติดตั้งและเปิดบน iPhone 100 Pro Max แล้ว. **ยังไม่ยืนยัน connect/upload จริงครบ Google Drive และ OneDrive บน production-signed builds**

---

## 3. งาน App Store risk ที่ปิดแล้ว

### Privacy manifest

`ios/SmartStorage/Resources/PrivacyInfo.xcprivacy` อยู่ใน bundle และผ่าน `plutil -lint` แล้ว (commit `834cb18`):
- UserDefaults → `CA92.1`
- disk space → `85F4.1`
- ไม่เก็บข้อมูลและไม่ track

ต้อง audit และอัปเดต manifest ทุกครั้งที่เพิ่ม Required Reason API หรือ SDK ใหม่ ดูคำสั่งใน `runbook.md` หัวข้อ 11

### Public-API file sizing

- ไม่มี `PHAssetResource.value(forKey: "fileSize")` เหลือใน source
- รูป local ถูก stream ผ่าน `PHAssetResourceManager` หนึ่งรอบเพื่อนับ byte และ SHA-256 พร้อมกัน
- วิดีโอวัดผ่าน `AVAssetTrack.totalSampleDataLength`; อ่านไฟล์เต็มเฉพาะ duplicate candidates
- iCloud-only originals ไม่ถูกดาวน์โหลดและใช้ `SizeEstimator`; รูปที่แก้ไขวัดขนาดได้แต่ไม่ hash
- ขนาด/hash cache อยู่ใน `HashRecord` และ invalidated ด้วย `modifiedAt`
- delete/compress รับขนาดจาก `LibraryStore`; output ของ compression ใช้ `URLResourceValues.fileSizeKey`
- integration probe บน iPhone 17 simulator: รูปนิ่ง 115/115 ตรงกับ KVC เดิม, 0 mismatch; ลบ `TMPPROBE` แล้ว
- iOS unit tests ปัจจุบัน 86 tests ผ่าน รวม test ของ estimator, measurement plan, optional hash และ Cloud queue state

Android ไม่ต้องเปลี่ยน เพราะใช้ `MediaStore.SIZE` ซึ่งเป็น public API อยู่แล้ว

---

## 4. ภาพรวมสถาปัตยกรรม

**Logic ที่เป็น pure มีคู่กันสองภาษา** (เปลี่ยนฝั่งหนึ่งต้องเปลี่ยนอีกฝั่ง + test ทั้งสอง):
`SimilarityGrouper`, `BestShotScorer` + `TasteModel`, `LibraryReportBuilder`, `CachePlanner`, `ScreenshotClassifier`/`DateExtractor`, `ReceiptExtractor`, `TextLayout`, `ThaiText`, `FilingRules` (template/rule), `EventGrouper`, `DuplicateFinder`, `SafetyLevel` (ใน `ReviewModels`), `WeeklyClean` (schedule/message)
ที่ต่างกันโดยตั้งใจ: iOS ใช้ Vision (feature print, classifier, OCR ไทย) / Android ใช้ dHash + ML Kit + Tesseract (ไทย); iOS ยังมี `SizeEstimator`/การวัดขนาดแบบอ่านไฟล์ (Android ไม่ต้อง)

**Flow การสแกน** (`LibraryStore.runScan` ทั้งสองฝั่ง): โหลดรายการทั้งคลัง → แบ่งงานตาม cache (ใช้ผลเก่าจนกว่า `modifiedAt` เปลี่ยน) → [ขั้น hash/วัดขนาด] → วิเคราะห์รูป (similar/blurry/faces/scene/text lines) → อ่านข้อความ screenshot และรูปที่น่าจะเป็นใบเสร็จ → `LibraryReportBuilder.build` สร้างทุกหน้าจอจากผลลัพธ์ · ทุกขั้น publish ผลบางส่วนทันทีเพื่อให้ UI ไม่ว่าง
**Cache:** SwiftData (`Caches/analysis.store`) / Room (`noBackupFilesDir/analysis.db`) — เป็นข้อมูลที่ derive ได้ล้างได้ (มีปุ่มล้างใน Settings → Privacy) · ข้อมูลที่ห้ามหาย (กฎ Storage Rules, AI Taste, ตั้งค่าแจ้งเตือน) อยู่ใน UserDefaults / SharedPreferences · ข้อมูล v1.1 (บัญชี cloud, คิวอัปโหลด และ `sourceModifiedAt` สำหรับผูกสถานะกับ asset version ปัจจุบัน) แยก store ไม่อยู่ใน cache ที่ล้างได้ · Android ฟัง SharedPreferences ของ WorkManager แบบ live เพื่อสะท้อน progress ใน UI · ตาราง version ที่ต้อง bump อยู่ใน `runbook.md` หัวข้อ 7
**UI:** iOS `TabView`/`NavigationSplitView`; Android `NavigationSuiteScaffold`; design system มีชุดเดียวกันทั้งสองฝั่ง (`DesignSystem/` และ `ui/components`, `ui/theme`). Library grid/filter/viewer ทำคู่กันทั้งสองแพลตฟอร์ม และข้อความ UI ใช้ shared 25-locale catalogue ใน `i18n/`

---

## 5. การตัดสินใจสำคัญและเหตุผล

| เรื่อง | ตัดสินใจ | เหตุผล |
|---|---|---|
| Stack | Native SwiftUI + Compose (ไม่ใช้ cross-platform) | ผู้ใช้เลือก; ต้องใช้ Vision / PhotoKit / MediaStore ลึก |
| ลบไฟล์ | ผ่าน system confirmation เสมอ, ลงถังขยะ | กฎความปลอดภัยของสเปก; ไม่มีการลบถาวรจากแอป |
| OCR ไทยบน Android | ML Kit ก่อน แล้วเรียก Tesseract (tha+eng) เฉพาะกรณีจำเป็น | ML Kit ไม่รองรับไทย; spike วัดแล้ว 21/25 vs 13/25 (`android/app/src/androidTest/THAI_OCR_SPIKE.md`); โมเดลโหลดตอน build + ตรวจ SHA-256 |
| ใบเสร็จกระดาษ | คัด candidate ก่อน (จำนวนบรรทัดข้อความ/คะแนน classifier) แล้วอ่าน 2048px + แก้เอียง (`TextLayout`) | อ่านทุกรูปช้าเกินไป; OCR คืนคอลัมน์ชื่อรายการกับยอดแยกกัน ต้องประกอบแถวใหม่ตามตำแหน่ง |
| Memories | ทริป = หลายวันห่างบ้าน ≥ 80 กม.; บ้าน = เซลล์ ~25 กม. ที่มีรูปในจำนวน "วัน" มากสุด; ไม่ค้นชื่อสถานที่ออนไลน์ | ทริปที่ถ่ายเยอะไม่ควรถูกนับเป็นบ้าน; ห้ามส่งพิกัดออกนอกเครื่อง |
| Weekly Smart Clean | opt-in, ข้อความอ้างผลสแกนล่าสุด, **ไม่สแกนเบื้องหลัง** | แบตเตอรี่และยืนยันความน่าเชื่อถือบน simulator ไม่ได้; หน้าจอบอกผู้ใช้ตรง ๆ |
| AI Taste | Bradley–Terry เล็ก ๆ ปรับน้ำหนัก 3 ตัว, เรียนเฉพาะเมื่อยืนยันลบ, เชื่อถือเต็มที่ 20 ครั้ง, เก็บแค่ตัวเลข | ไม่เก็บรูป/id; กันน้ำหนักเพี้ยน; เปิด/ปิด/รีเซ็ตได้ |
| ไอคอน | สร้างใหม่จากต้นฉบับด้วย `icons/build_icons.py` (ไม่ copy ไฟล์ export ตรง ๆ) | ต้นฉบับมีพื้นดำทึบ → iOS เห็นมุมดำใต้ mask, Android เห็นมุมดำ/วงกลมซีด; ทำ adaptive icon ให้เพราะ minSdk 26 |
| Cloud (v1.1) | direct-to-provider, no backend; Google Android ใช้ AuthorizationClient, Google iOS ใช้ Google Sign-In SDK, OneDrive ใช้ OAuth PKCE; Drive `drive.file`, OneDrive app folder; OneDrive personal accounts only | โค้ดและ client defaults เสร็จ; ยังต้องสร้าง Google Play App Signing client, ยืนยัน Google consent/app ownership ด้วยบัญชีโครงการที่ถูกต้อง และทดสอบ connect/upload/relaunch จริงก่อนส่ง |

---

## 6. ความเสี่ยงและข้อจำกัดที่ยังเปิดอยู่

- **ติดตั้ง/เปิดบน iPhone จริงแล้ว แต่ยังไม่ผ่าน physical-device sign-off เต็มชุด** — ยังต้องวัดคลัง 10k+, ความแม่น Similar Photos/อีเวนต์/ใบเสร็จไทย, permission/delete flow, background/resume และ cloud upload จริง; Vision บน Simulator ให้ค่าเสื่อม (ทุกรูปเป็น `night_sky`)
- รูปทดสอบทั้งหมดเป็นภาพสังเคราะห์ (Python PIL) ไม่ใช่คลังรูปจริง
- Android: ชื่อไทยที่ Tesseract อ่านอาจขาดวรรณยุกต์ ("รานกาแฟ") — จัดหมวดไม่กระทบแล้ว แต่ชื่อโฟลเดอร์เพี้ยน
- Android ยังไม่มี scene labeling → อีเวนต์ตั้งชื่อตามวันที่เท่านั้น
- ขนาดแอป Android: Tesseract เพิ่ม ~7.5 MB native + 5.2 MB โมเดลต่อเครื่อง (APK รวมทุก ABI ~136 MB → ต้องปล่อยเป็น App Bundle)
- release baseline ใน repo ทำแล้ว: privacy policy/terms URL, launch screen, Android themed icon, API 36, signing hook, Store copy/privacy drafts, smoke UI tests และ CI
- Google Cloud Console ที่เปิดล่าสุดเป็นบัญชี Google อื่น ไม่ใช่บัญชี ITSTON.COM ที่ควรเป็นเจ้าของ/จัดการโครงการ `keepspace-production`; ต้องสลับบัญชีที่ถูกต้องก่อนยืนยัน consent screen, app ownership และทดสอบ Drive production OAuth
- ยังต้องใช้เจ้าของบัญชี/เครื่องจริง: เปิด Play App Signing + เพิ่ม SHA-1 client, อัปโหลด TestFlight/Play closed testing, กรอก Store forms/screenshots และรัน physical-device gates ใน `store/RELEASE_CHECKLIST.md`

---

## 7. กับดักของสภาพแวดล้อม (เจอมาจริง)

| อาการ | แก้ |
|---|---|
| เครื่องมือ Bash/Edit/Write ของ agent ตอบ "auto mode classifier gave no verdict" | ชั่วคราวฝั่งเซิร์ฟเวอร์ — ลองซ้ำครั้งเดียว ถ้ายังไม่ผ่านให้หยุดรอ ห้ามยิงซ้ำถี่ ๆ (ครบ 10 ครั้งติดกันเทิร์นถูกตัด); อ่านไฟล์ยังทำได้ (Read, อ่าน `.git/logs/HEAD` แทน `git log`) |
| `xcodebuild test` ค้าง | แยกเป็น `build-for-testing` แล้วบูต simulator แล้ว `test-without-building` (ดู runbook หัวข้อ 4) |
| ตัวเลือกกดหน้าจอ simulator ผ่านเครื่องมือ agent ไม่ติดกับปุ่ม SwiftUI | พิกัดเป็นหน่วย point (screenshot ย่อขนาดแล้ว); ถ้ายังไม่ติด ยืนยันตรรกะด้วย unit test แทน + เปิดหน้าด้วย `-debugRoute` |
| scratchpad (รูปทดสอบ, สคริปต์ adb) ถูกล้างเมื่อเปลี่ยนเซสชัน | รูปทดสอบ OCR ไทยอยู่ใน git ที่ `android/app/src/androidTest/assets/ocr/`; สคริปต์ tap ของ adb เขียนใหม่ได้ (ใช้ `uiautomator dump`; `&` แสดงเป็น `&amp;`) |
| ไม่มี numpy/scipy | สคริปต์รูปภาพใช้ Pillow ล้วน (ดู `icons/build_icons.py`) |

---

## 8. ลำดับที่แนะนำต่อจากนี้

1. สลับ Google Cloud เป็นบัญชี ITSTON.COM ที่ดูแล `keepspace-production`; ยืนยัน consent/app ownership และสร้าง Android OAuth client จาก Play App Signing SHA-1 ตาม `store/CLOUD_SETUP.md`
2. อัปโหลด `1.1.0 (2)` เข้า TestFlight และ Play closed testing แล้วทดสอบ connect/upload/verify/relaunch ของ Google Drive + OneDrive บน production-signed builds
3. ทดสอบคลังจริง 10k+ บน iPhone/iPad/Android phone/tablet รวม Library viewer, Select All, permission/delete, performance, battery และ accessibility
4. เชื่อมกฎเข้าคิวอัปโหลด (v1.2) → backup verification + safe delete (v1.3)
5. Monetization + analytics ก่อนปล่อยจริง

---

## ภาคผนวก — บันทึกการออกแบบ file sizing

**สถานะ: ทำเสร็จและทดสอบแล้ว** — โค้ดด้านล่างเป็นร่างเดิมเพื่อเก็บเหตุผลการออกแบบเท่านั้น; source ปัจจุบันใน `LibraryEngine.swift` เป็นแหล่งความจริง ห้ามคัดลอกร่างนี้ทับ implementation

```swift
private func hashFile(_ item: MediaItem) async -> CachedHash? {
    guard let asset = assets[item.id] else { return nil }
    let resources = PHAssetResource.assetResources(for: asset)
    guard !resources.contains(where: { $0.type == .adjustmentData }),
          let resource = Self.primaryResource(in: resources), let read = await stream(resource) else { return nil }
    return CachedHash(assetID: item.id, modifiedAt: item.modifiedAt, bytes: read.bytes, hash: read.sha256)
}

func measure(_ items: [MediaItem],
             progress: @escaping @Sendable (Int, Int) async -> Void,
             onBatch: @escaping @Sendable ([CachedHash]) async -> Void) async -> [CachedHash] {
    var results: [CachedHash] = [], pending: [CachedHash] = []
    var done = 0
    await withTaskGroup(of: CachedHash?.self) { group in
        var iterator = items.makeIterator()
        func enqueue() {
            guard let item = iterator.next() else { return }
            group.addTask { await self.measureOne(item) }
        }
        for _ in 0..<3 { enqueue() }
        while let next = await group.next() {
            if let next { results.append(next); pending.append(next) }
            done += 1
            if done % 20 == 0 || done == items.count { await progress(done, items.count) }
            if pending.count >= 50 { await onBatch(pending); pending.removeAll() }
            enqueue()
        }
    }
    if !pending.isEmpty { await onBatch(pending) }
    return results
}

private func measureOne(_ item: MediaItem) async -> CachedHash? {
    guard let asset = assets[item.id] else { return nil }
    if item.isVideo {
        guard let bytes = await videoBytes(of: asset) else { return nil }
        return CachedHash(assetID: item.id, modifiedAt: item.modifiedAt, bytes: bytes, hash: nil)
    }
    let resources = PHAssetResource.assetResources(for: asset)
    guard let resource = Self.primaryResource(in: resources), let read = await stream(resource) else { return nil }
    let edited = resources.contains { $0.type == .adjustmentData }   // an edited photo never counts as a duplicate
    return CachedHash(assetID: item.id, modifiedAt: item.modifiedAt, bytes: read.bytes, hash: edited ? nil : read.sha256)
}

/// Exact byte count + SHA-256 of an original, read once; nil when it can't be read locally (iCloud-only).
private func stream(_ resource: PHAssetResource) async -> (bytes: Int64, sha256: String)? {
    let options = PHAssetResourceRequestOptions()
    options.isNetworkAccessAllowed = false
    return await withCheckedContinuation { (continuation: CheckedContinuation<(bytes: Int64, sha256: String)?, Never>) in
        let box = StreamBox()
        PHAssetResourceManager.default().requestData(for: resource, options: options) { chunk in
            box.add(chunk)
        } completionHandler: { error in
            continuation.resume(returning: error == nil ? box.result() : nil)
        }
    }
}

private final class StreamBox: @unchecked Sendable {   // PhotoKit delivers chunks serially
    private var hasher = SHA256()
    private var count: Int64 = 0
    func add(_ data: Data) { count += Int64(data.count); hasher.update(data: data) }
    func result() -> (bytes: Int64, sha256: String) {
        (count, hasher.finalize().map { String(format: "%02x", $0) }.joined())
    }
}

/// A video's size from its tracks' data length — no file read. (`totalSampleDataLength` is public API.)
private func videoBytes(of asset: PHAsset) async -> Int64? {
    let options = PHVideoRequestOptions()
    options.isNetworkAccessAllowed = false
    options.version = .original
    options.deliveryMode = .highQualityFormat
    let box: AVAssetBox = await withCheckedContinuation { continuation in
        PHImageManager.default().requestAVAsset(forVideo: asset, options: options) { avAsset, _, _ in
            continuation.resume(returning: AVAssetBox(avAsset))
        }
    }
    guard let avAsset = box.asset, let tracks = try? await avAsset.load(.tracks) else { return nil }
    var total: Int64 = 0
    for track in tracks { total += (try? await track.load(.totalSampleDataLength)) ?? 0 }
    return total > 0 ? total : nil
}

private struct AVAssetBox: @unchecked Sendable {
    let asset: AVAsset?
    init(_ asset: AVAsset?) { self.asset = asset }
}
```

และใน `item(for:)`:

```swift
let fileName = PHAssetResource.assetResources(for: asset).first?.originalFilename
// ... MediaItem(..., bytes: SizeEstimator.estimate(isVideo: asset.mediaType == .video, pixelWidth: asset.pixelWidth,
//     pixelHeight: asset.pixelHeight, duration: asset.duration, fileName: fileName), ..., fileName: fileName, ..., isSizeEstimated: true)
```
