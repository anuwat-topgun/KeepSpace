# KeepSpace — สถานะงานและสิ่งที่เหลือ

อัปเดตล่าสุด: 27 ก.ย. 2026 (commit `4a225ea`)
อ้างอิงสเปก: `01_Product_Technical_Spec_v1_to_v1_3.md`, `02_Design_Handoff_v1_to_v1_3.md`

สัญลักษณ์: ✅ เสร็จและผ่านเทส · 🟡 ทำแล้วบางส่วน / ยังต้องยืนยัน · ⬜ ยังไม่เริ่ม

---

## 1. สรุปสถานะการทดสอบ

| แพลตฟอร์ม | Unit tests | สถานะ | ทดสอบบนอุปกรณ์ |
|---|---|---|---|
| iOS (SwiftUI, iOS 17+) | 53 tests / 19 suites (Swift Testing) | ✅ ผ่านทั้งหมด | iPhone 17 + iPad Pro simulator |
| Android (Compose, API 26+) | 53 tests (JUnit, JVM) | ✅ ผ่านทั้งหมด | `keepspace_tablet` emulator (Pixel Tablet, API 36) |

คำสั่งรันเทส:
```bash
cd ios && xcodegen && xcodebuild test -project SmartStorage.xcodeproj -scheme SmartStorage -destination 'platform=iOS Simulator,name=iPhone 17'
cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug
```

ข้อจำกัดของการทดสอบที่ผ่านมา:
- เทสทั้งหมดเป็น unit test ของ logic ล้วน (grouping, scoring, OCR classification, receipt extraction, rules, templates, events) — **ยังไม่มี UI test อัตโนมัติ**
- หน้าจอตรวจด้วยมือบน simulator/emulator โดยใช้รูปทดสอบที่สร้างขึ้นเอง (PIL) ไม่ใช่คลังรูปจริง
- **iOS Simulator ใช้ Vision ได้ไม่เต็มที่** (feature print และ scene classifier ให้ค่าเสื่อม) → ความแม่นของ Similar Photos, ชื่อธีมของอีเวนต์ และการหาใบเสร็จภาษาไทยจาก classifier ต้องยืนยันบน iPhone จริง
- ยังไม่เคยรันบน iPhone / Android เครื่องจริง

---

## 2. Feature ที่ทำเสร็จแล้ว

### พื้นฐาน (Foundation)
| งาน | iOS | Android | หมายเหตุ |
|---|---|---|---|
| โครงแอป + navigation รองรับ phone/tablet | ✅ | ✅ | iOS: TabView / NavigationSplitView (iPad sidebar) · Android: NavigationSuiteScaffold (bar/rail/drawer) |
| Design system (สี, ตัวอักษร, การ์ด, ปุ่ม, badge, dark mode) | ✅ | ✅ | ตาม design handoff |
| App Store Connect: app "Keep space. Keep what matters." | ✅ | — | ASC app 6816584493, bundle `com.keepspace.app`, SKU keepspace-ios-001 |
| เชื่อม Photo Library / MediaStore + ขอสิทธิ์ | ✅ | ✅ | รองรับ Limited access (iOS) / Selected photos (Android 14) |
| On-device AI (Vision / ML Kit) | ✅ | ✅ | ไม่มี API ภายนอก; ML Kit แบบ bundled |
| Local database cache + incremental scan | ✅ | ✅ | SwiftData / Room (Caches, no_backup) มี version ต่อ analyzer |

### v1.0 — Core MVP
| Feature (สเปก §5.2) | iOS | Android | หมายเหตุ |
|---|---|---|---|
| Storage Analysis (Home dashboard) | ✅ | ✅ | พื้นที่เครื่องจริง + ขนาดตามหมวด |
| Similar Photo Groups | ✅ | ✅ | iOS: Vision feature print · Android: dHash — iOS ต้องยืนยันความแม่นบนเครื่องจริง |
| AI Best Shot + เหตุผล | ✅ | ✅ | คะแนนความคมชัด, ใบหน้า/ตาเปิด, แสง |
| Clean X GB (เลือกเป้าหมาย + Cleanup Plan) | ✅ | ✅ | |
| Screenshot semantic grouping (OCR) | ✅ | ✅ | Shopping/Receipts/Chats/QR/Tickets/Other, ตั๋วหมดอายุ — **Android อ่านภาษาไทยไม่ได้** (ML Kit Latin เท่านั้น) |
| Large Videos / Screen Recordings | ✅ | ✅ | |
| Blurry / Bad Shot Detection | ✅ | ✅ | Laplacian variance |
| Protected Memories + Event/Trip Grouping | ✅ | ✅ | ใหม่ล่าสุด — ทริป/อีเวนต์, ระยะห่างจากบ้าน, หน้า detail, รูปเบลอในความทรงจำไม่ถูกเลือกไว้ล่วงหน้า |
| Review ก่อนลบ + ลบผ่าน system confirmation | ✅ | ✅ | ลงถัง Recently Deleted / Trash เท่านั้น; favorites ไม่ถูกเลือกไว้ |
| Video Compression | ✅ | ✅ | iOS: AVAssetExportSession HEVC · Android: Media3 Transformer (คง DATE_TAKEN) |
| Storage Forecast (Insights) | ✅ | ✅ | คาดการณ์จากอัตราเพิ่มของสื่อ |
| Onboarding + privacy promise | ✅ | ✅ | |

### v1.2 — Rules engine
| Feature | iOS | Android | หมายเหตุ |
|---|---|---|---|
| Storage Rules list (mockup 14) | ✅ | ✅ | เปิด/ปิด, แก้ไข, ลบ (iOS กดค้าง) |
| Rule Builder (mockup 15) | ✅ | ✅ | trigger, provider, template + variable chips, live preview, validation, ยืนยัน "ลบหลัง 30 วัน" |
| Template resolver ({YEAR} {MONTH} {DAY} {DATE} {MERCHANT} {AMOUNT} {CATEGORY} {EVENT} {MEDIA_TYPE} {ORIGINAL_NAME} {INDEX}) | ✅ | ✅ | ตัดอักขระที่ Drive/OneDrive ไม่รับ |
| เก็บกฎในเครื่อง | ✅ | ✅ | UserDefaults / SharedPreferences (JSON) |

### v1.3 — Semantic filing (ส่วนที่ไม่ต้องใช้ cloud)
| Feature | iOS | Android | หมายเหตุ |
|---|---|---|---|
| Receipt extraction (merchant, date, amount, currency, category) | ✅ | 🟡 | Android อ่านชื่อร้านภาษาไทยไม่ได้ (แสดง "Unknown merchant") |
| ใบเสร็จจาก screenshot | ✅ | ✅ | รองรับวันที่ไทย/พ.ศ., สลิปโอนเงิน |
| ใบเสร็จกระดาษจากรูปกล้อง | ✅ | ✅ | คัด candidate → OCR 2048px + แก้เอียง/EXIF, สร้างแถวใหม่ (TextLayout) |
| Receipt Filing screen (mockup 16) | ✅ | ✅ | แสดงข้อมูลที่อ่านได้ + กฎที่ match + path ปลายทาง |

---

## 3. สิ่งที่ยังไม่ได้ทำ (รายละเอียด)

### 3.1 v1.0 — ส่วนที่ยังขาด

#### ⬜ Exact Duplicate Detection (สเปก §5.2)
ตอนนี้มีแค่ "Similar" (ภาพคล้าย) ยังไม่มีการหาไฟล์ซ้ำเป๊ะ
- [ ] คำนวณ hash ของไฟล์ (SHA-256 ของ original, หรือ size + hash ของ 64KB แรก/สุดท้ายเพื่อความเร็ว) เก็บใน cache
- [ ] iOS: อ่าน data ผ่าน `PHAssetResourceManager` (ข้ามไฟล์ iCloud-only) · Android: `ContentResolver.openInputStream`
- [ ] จัดกลุ่มตาม hash; เก็บตัวที่เก่าที่สุด/favorite, ที่เหลือ preselected (ปลอดภัยที่สุดเพราะเหมือนกันทุก byte)
- [ ] เพิ่มหมวด "Exact Duplicates" ใน Cleanup Plan + Review
- [ ] Unit tests ทั้งสองแพลตฟอร์ม

#### ⬜ Safety Score (สเปก §5.2)
- [ ] นิยามคะแนนต่อรายการ/ต่อแผน เช่น exact duplicate = สูงสุด, extra shot ในกลุ่ม similar = สูง, blurry = กลาง, วิดีโอส่วนตัว = ต่ำ, อยู่ในความทรงจำ/favorite = ห้าม
- [ ] แสดงใน Cleanup Plan และ Review ("ปลอดภัยมาก / ควรดูก่อน") พร้อมเหตุผลที่อธิบายได้ (กฎข้อ 5 ของสเปก)
- [ ] ใช้คะแนนเรียงลำดับตอนเลือกให้ถึงเป้า Clean X GB

#### 🟡 Weekly Smart Clean
มีปุ่ม "Run Weekly Smart Clean" และแถว Notifications ใน Settings แต่ยังไม่มีระบบจริง
- [ ] Local notification รายสัปดาห์ (iOS `UNUserNotificationCenter` · Android `WorkManager` + notification permission API 33+)
- [ ] สแกนเบื้องหลังแบบเบา (iOS `BGProcessingTask` · Android `WorkManager` constraints: charging/idle)
- [ ] สรุป "สัปดาห์นี้เพิ่ม X รูป, ลบได้ Y GB" → เปิดแอปไปหน้า Cleanup Plan
- [ ] หน้า Settings > Notifications (เปิด/ปิด, เลือกวัน/เวลา)

#### ⬜ Personalized AI Taste
- [ ] เก็บสัญญาณในเครื่อง: รูปที่ผู้ใช้เก็บ/ลบใน Best Shot และ Review (ไม่ส่งออกนอกเครื่อง)
- [ ] ปรับน้ำหนัก BestShotScorer (เช่น ชอบรูปคมกว่า vs ชอบรูปที่มีคนยิ้ม) แบบง่าย เช่น online logistic weights
- [ ] ปุ่ม reset ใน Settings

#### 🟡 Settings — หน้าที่ยังเป็น placeholder
- [ ] Notifications (ผูกกับ Weekly Smart Clean)
- [ ] Subscription (ดูข้อ 3.5)
- [ ] Privacy & Security: อธิบายว่าอะไรอยู่ในเครื่อง, ปุ่มล้าง cache การวิเคราะห์, ล้างข้อมูลใบเสร็จ
- [ ] About: เวอร์ชัน, ลิงก์ privacy policy / terms, open-source licenses
- [ ] Photo Access: ปุ่มเปิด system settings / เลือกรูปเพิ่ม (limited access)

#### 🟡 Memories — ต่อยอด
- [ ] Android: เพิ่ม scene labeling (ML Kit Image Labeling bundled) เพื่อให้ชื่อธีม (Birthday Party, Beach Trip) เหมือน iOS
- [ ] ให้ผู้ใช้เปลี่ยนชื่อทริป/อีเวนต์ และปิดการป้องกันรายอีเวนต์ได้ (เก็บ override ในเครื่อง)
- [ ] ยืนยันเกณฑ์ (6 ชม., 80 กม., 12/20/30 รูป) กับคลังรูปจริงหลายแบบ แล้วปรับ
- [ ] (ทางเลือก) ชื่อสถานที่: ต้องใช้ reverse geocoding ซึ่งส่งพิกัดไป Apple/Google — ต้องตัดสินใจเรื่อง privacy ก่อน ถ้าทำให้เป็น opt-in

### 3.2 v1.1 — Manual cloud backup (ยังไม่เริ่ม) ⬜

**สิ่งที่ต้องได้จากเจ้าของแอปก่อนเริ่ม** — OAuth client ของ KeepSpace เอง (ไม่ใช่ของผู้ใช้/ของกลาง):
- [ ] Google Cloud Console: สร้างโปรเจกต์ KeepSpace, เปิด Google Drive API, OAuth consent screen (external, scope `drive.file`), iOS client ID (bundle `com.keepspace.app`), Android client ID (package `com.keepspace.app` + SHA-1 ของ debug และ release keystore)
- [ ] Microsoft Entra ID: app registration (personal + work accounts), platform iOS (bundle ID) + Android (package + signature hash), redirect URIs, delegated permissions `Files.ReadWrite` (หรือ `Files.ReadWrite.AppFolder`) + `offline_access` + `User.Read`
- [ ] ถ้าจะใช้ scope ที่กว้างกว่า `drive.file` ต้องผ่าน Google verification (ใช้เวลาหลายสัปดาห์) → แนะนำเริ่มที่ `drive.file`

งานพัฒนา:
- [ ] **Auth**: Google Sign-In SDK / Credential Manager (Android) + MSAL (iOS/Android), PKCE, ไม่มี client secret
- [ ] เก็บ token ใน Keychain / Android Keystore (สเปก §9.2), refresh token, disconnect + revoke
- [ ] **Data model** (สเปก §6.9): `cloud_provider_connection`, `cloud_upload_item`, `upload_preferences` ลงใน SwiftData / Room (แยกจาก cache เพราะเป็นข้อมูลที่ต้องไม่หาย — ห้ามอยู่ใน Caches/no_backup แบบ destructive migration)
- [ ] **Provider adapters**: Google Drive (resumable upload, สร้างโฟลเดอร์ตาม path, คืน file ID) · OneDrive (Graph upload session สำหรับไฟล์ >4MB, `conflictBehavior=rename`)
- [ ] **Upload queue**: สถานะ waiting → uploading → verifying → backed up / failed / cancelled (สเปก §6.6), retry + backoff, ทำงานต่อหลังแอปปิด (iOS background `URLSession` · Android `WorkManager` foreground service)
- [ ] Upload settings: Wi-Fi only, keep local copies, rename automatically, compress videos before upload
- [ ] **หน้าจอ**: Cloud Sync Overview (mockup 12), Back Up Now / Manual Backup (mockup 13), เลือกโฟลเดอร์ปลายทาง, สถานะ sync รายไฟล์ — ตอนนี้ route มีแล้วแต่เป็น PlaceholderScreen
- [ ] อัปเดตข้อความ privacy เป็นแบบ v1.1+ (สเปก §9.1)
- [ ] Error handling: token หมดอายุ, quota เต็ม, ไม่มีเน็ต, ไฟล์ถูกลบระหว่างรอคิว
- [ ] Tests: queue state machine, path/folder resolution, retry logic (mock adapter)
- [ ] Acceptance (สเปก §14): Google Drive connect / OneDrive connect / manual backup / upload state UI

### 3.3 v1.2 — ส่วนที่ต้องมี cloud ก่อน ⬜
- [ ] เชื่อม Rules engine เข้ากับ upload queue (สเปก §7.9): ตรวจพบ → จัดประเภท → หา rule → resolve path → เข้าคิว → รอ verify → ทำ after-upload policy
- [ ] `destination_folder_id` จริงจาก provider แทนการใช้ path อย่างเดียว
- [ ] `storage_rule_match_log` (สเปก §7.10) เพื่อแสดงประวัติ/ดีบัก
- [ ] Trigger "Event / Trip" (ใช้ EventGrouper ที่ทำแล้ว + ตัวแปร {EVENT})
- [ ] หน้าเลือกโฟลเดอร์ปลายทางใน Rule Builder (browse โฟลเดอร์ใน Drive/OneDrive)
- [ ] การรันกฎอัตโนมัติเมื่อมีรูปใหม่ (PHPhotoLibraryChangeObserver · Android ContentObserver/WorkManager)

### 3.4 v1.3 — Backup verification + safe delete ⬜
- [ ] Verification pipeline (สเปก §8.5–8.6): upload started → uploaded → provider acknowledged → verified (ตรวจ file ID, ขนาด, checksum ถ้า provider ให้: Drive `md5Checksum` / OneDrive `quickXorHash`) → cleanup eligible
- [ ] Data model: `cloud_backup_verification`, `semantic_document_analysis` (ข้อความ OCR เก็บในเครื่องเป็น optional เท่านั้น)
- [ ] เงื่อนไขก่อนเสนอให้ลบ: upload สำเร็จ + มี provider file ID + รู้ path + สถานะ verified + ไฟล์ในเครื่องยังอยู่ — **ห้ามลบทันทีหลังอัปโหลด**
- [ ] After-upload policy: Suggest deletion / Keep / Suggest after 30 days (นับจากเวลา verified)
- [ ] หน้า Backup Verification + Safe Delete (mockup 17) — ตอนนี้เป็น placeholder
- [ ] Badge "Cloud verified" ในรายการรูป/ใบเสร็จ
- [ ] ปุ่ม "Connect Google Drive" ในหน้า Receipt Filing ให้ทำงานจริง
- [ ] Acceptance (สเปก §14): uploaded files become verified / cleanup suggestion appears only after verification

### 3.5 Monetization (สเปก §11) ⬜
- [ ] ตัดสินใจราคา + สิทธิ์ Free vs Pro
- [ ] iOS StoreKit 2 (subscription group ใน App Store Connect) · Android Play Billing Library
- [ ] Entitlement layer ในแอป + paywall + restore purchases
- [ ] จำกัดฟีเจอร์ Free ตามสเปก (cleanup จำกัด, 1 cloud account, backup จำกัด ฯลฯ)
- [ ] (ถ้าต้องมี backend) server สำหรับ entitlement/feature flags เท่านั้น — ห้ามรับไฟล์สื่อ (สเปก §4.2)

### 3.6 Analytics (สเปก §10) ⬜
- [ ] เลือกเครื่องมือที่เคารพ privacy (หรือทำ endpoint เอง) + ขอความยินยอม
- [ ] Event ที่อนุญาต: connected provider, backup started/completed/failed, rule created/edited/matched, receipt filing reviewed, cleanup suggested
- [ ] ห้ามส่ง: สื่อ, ข้อความ OCR, embedding, ชื่อไฟล์

### 3.7 คุณภาพและการเตรียมปล่อยแอป
- [ ] **ทดสอบบนเครื่องจริง** iPhone + iPad + Android phone + tablet ด้วยคลังรูปใหญ่ (10k+ รูป): เวลาสแกน, หน่วยความจำ, แบตเตอรี่, ความแม่นของ similar/blurry/receipt/events
- [ ] ยืนยัน threshold ของ SimilarityGrouper บน iPhone จริง (simulator ให้ feature print เสื่อม)
- [ ] **Android OCR ภาษาไทย**: ML Kit ไม่มี Thai → พิจารณา Tesseract (tha) แบบ on-device หรือโมเดลอื่น แล้ววัดขนาดแอป/ความเร็ว
- [ ] **ความเสี่ยง App Review**: iOS อ่านขนาดไฟล์ด้วย `PHAssetResource.value(forKey: "fileSize")` (KVC ที่ไม่ใช่ public API) → หาทางเลือก (เช่น ประมาณจาก resource/อ่านผ่าน `PHAssetResourceManager` เฉพาะเมื่อจำเป็น) หรือยอมรับความเสี่ยงอย่างรู้ตัว
- [ ] App icon (ตอนนี้เป็นไอคอนว่าง) + launch screen ทั้งสองแพลตฟอร์ม
- [ ] `PrivacyInfo.xcprivacy` (Required Reason APIs เช่น UserDefaults, file timestamps, disk space)
- [ ] Info.plist: ข้อความขอสิทธิ์ Photos ให้ครบ/สุภาพ; Android: ข้อความอธิบาย ACCESS_MEDIA_LOCATION
- [ ] Localization ภาษาไทย (ตอนนี้ UI เป็นภาษาอังกฤษทั้งหมด)
- [ ] Accessibility: VoiceOver/TalkBack label ครบ, Dynamic Type/font scale ใหญ่, contrast
- [ ] UI tests อัตโนมัติ (XCUITest / Compose UI test) สำหรับ flow หลัก: onboarding → scan → cleanup plan → review → delete
- [ ] iPad landscape: ทดสอบด้วยมือ (หมุน simulator จาก CLI ไม่ได้)
- [ ] Android release: keystore สำหรับ release, R8/ProGuard rules (ML Kit, Room, Media3), Play Console listing, Data safety form, target API ล่าสุด
- [ ] iOS release: signing/provisioning, TestFlight, screenshots สำหรับ store (phone + iPad), App Privacy nutrition label
- [ ] Privacy policy + Terms (จำเป็นสำหรับทั้งสอง store และสำหรับ Google OAuth verification)
- [ ] CI (GitHub Actions): build + unit tests ทั้งสองแพลตฟอร์มทุก PR

---

## 4. ลำดับที่แนะนำ

1. ทดสอบบนเครื่องจริง + ปิดช่องโหว่ v1.0 (Exact Duplicates, Safety Score, Settings placeholders, app icon, privacy manifest) → พร้อมปล่อย v1.0 ตามสเปก §12
2. สร้าง OAuth clients (ข้อ 3.2) → v1.1 manual backup
3. เชื่อม rules เข้ากับ upload queue → v1.2 ครบ
4. Verification + safe delete → v1.3 ครบ
5. Monetization + analytics ก่อนปล่อยจริงบน store
