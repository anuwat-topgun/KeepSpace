# KeepSpace — สถานะงานและสิ่งที่เหลือ

อัปเดตล่าสุด: 28 ก.ย. 2026 · ปิดความเสี่ยง App Store เรื่อง privacy manifest และ private KVC แล้ว
อ้างอิงสเปก: `01_Product_Technical_Spec_v1_to_v1_3.md`, `02_Design_Handoff_v1_to_v1_3.md`
วิธี build / run / test / แก้ปัญหา: ดู [`runbook.md`](runbook.md) · ส่งต่องาน/การตัดสินใจ/กับดัก: ดู [`handoff.md`](handoff.md)

สัญลักษณ์: ✅ เสร็จและผ่านเทส · 🟡 ทำแล้วบางส่วน / ยังต้องยืนยัน · ⬜ ยังไม่เริ่ม

---

## 0. งาน App Store risk ที่เพิ่งปิด

เป้าหมาย: ปิดความเสี่ยงตอนส่ง App Store 2 ข้อ (รายละเอียดและขั้นตอนต่อ: `handoff.md` หัวข้อ 3)

| งาน | สถานะ | หมายเหตุ |
|---|---|---|
| iOS privacy manifest (`PrivacyInfo.xcprivacy`) | ✅ | ประกาศ UserDefaults `CA92.1` + disk space `85F4.1`; ไม่เก็บข้อมูล/ไม่ track; lint ผ่าน + อยู่ใน bundle · commit `834cb18` |
| iOS เลิกใช้ `value(forKey: "fileSize")` | ✅ | รูปอ่าน byte + SHA-256 รอบเดียวผ่าน `PHAssetResourceManager`; วิดีโอใช้ `totalSampleDataLength`; compression/delete รับขนาดที่วัดแล้วจาก store; `rg value(forKey:)` ว่าง |

การยืนยัน: iOS 81 tests / 26 suites ผ่าน; integration probe บน iPhone 17 simulator เทียบรูปนิ่ง 115 ไฟล์กับ KVC เดิม ตรง 115/115 (0 mismatch); ไม่มี `TMPPROBE` เหลือใน source
ข้อแลกเปลี่ยนที่ยอมรับ: สแกนครั้งแรกของคลัง 10,000 รูปช้าขึ้นราว 1–2 นาที (อ่านทุกไฟล์หนึ่งรอบ) แต่รอบถัดไปใช้ cache และได้ตัวเลขที่แม่นพร้อม hash ของรูปใน pass เดียว

---

## 1. สรุปสถานะการทดสอบ

| แพลตฟอร์ม | Unit tests | สถานะ | ทดสอบบนอุปกรณ์ |
|---|---|---|---|
| iOS (SwiftUI, iOS 17+) | 81 tests / 26 suites (Swift Testing) | ✅ ผ่านทั้งหมด | iPhone 17 + iPad Pro simulator |
| Android (Compose, API 26+) | 74 tests (JUnit, JVM) + Thai OCR spike (instrumented) | ✅ ผ่านทั้งหมด | `keepspace_tablet` emulator (Pixel Tablet, API 36) |

คำสั่งรันเทส: ดู `runbook.md` หัวข้อ 4

ทดสอบบนอุปกรณ์จำลองล่าสุด (27–28 ก.ย.):
- Exact duplicates: emulator เจอ 3 สำเนา, iOS simulator เจอ 1 สำเนา
- ใบเสร็จ/สลิปไทย+อังกฤษบน Android: 7/7 ถูก (ชื่อร้าน, ยอด, วันที่); แชท/ช้อปปิ้งไทยจัดหมวดถูก
- Memories: ทริป 2 วัน ห่างบ้าน 580 กม. + อีเวนต์ ตรวจพบทั้งสองแพลตฟอร์ม
- Release build Android (R8) ผ่าน

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
| OCR ภาษาไทย | ✅ (Vision) | ✅ | Android: ML Kit + Tesseract tha+eng เฉพาะเมื่อจำเป็น (`ThaiOcr`), โมเดลดาวน์โหลดตอน build + ตรวจ SHA-256 |
| Local database cache + incremental scan | ✅ | ✅ | SwiftData / Room (Caches, no_backup) มี version ต่อ analyzer |

### v1.0 — Core MVP
| Feature (สเปก §5.2) | iOS | Android | หมายเหตุ |
|---|---|---|---|
| Storage Analysis (Home dashboard) | ✅ | ✅ | พื้นที่เครื่องจริง + ขนาดตามหมวด |
| Settings: Photo Access / Privacy & Security / About | ✅ | ✅ | ล้าง cache การวิเคราะห์ได้; Subscription ยังเป็นแถวเปล่า |
| Weekly Smart Clean (เตือนรายสัปดาห์) | ✅ | ✅ | opt-in, ข้อความจากผลสแกนล่าสุด, ไม่สแกนเบื้องหลัง |
| Safety Score | ✅ | ✅ | ระดับ + คะแนนถ่วงน้ำหนัก, แผนเรียงจากปลอดภัยที่สุดก่อน |
| Exact Duplicate Detection | ✅ | ✅ | hash เฉพาะไฟล์ขนาดชนกัน, เก็บรายการโปรด/เก่าสุด, อยู่อันดับแรกใน Cleanup Plan |
| Similar Photo Groups | ✅ | ✅ | iOS: Vision feature print · Android: dHash — iOS ต้องยืนยันความแม่นบนเครื่องจริง |
| AI Best Shot + เหตุผล | ✅ | ✅ | คะแนนความคมชัด, ใบหน้า/ตาเปิด, แสง |
| Clean X GB (เลือกเป้าหมาย + Cleanup Plan) | ✅ | ✅ | |
| Screenshot semantic grouping (OCR) | ✅ | ✅ | Shopping/Receipts/Chats/QR/Tickets/Other, ตั๋วหมดอายุ — Android อ่านไทยด้วย Tesseract เมื่อจำเป็น |
| Large Videos / Screen Recordings | ✅ | ✅ | |
| Blurry / Bad Shot Detection | ✅ | ✅ | Laplacian variance |
| Protected Memories + Event/Trip Grouping | ✅ | ✅ | ทริป/อีเวนต์, ระยะห่างจากบ้าน, หน้า detail, รูปเบลอในความทรงจำไม่ถูกเลือกไว้ล่วงหน้า |
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
| Receipt extraction (merchant, date, amount, currency, category) | ✅ | ✅ | Android: Tesseract tha+eng — ชื่อไทยอาจขาดวรรณยุกต์บางตัว |
| ใบเสร็จจาก screenshot | ✅ | ✅ | รองรับวันที่ไทย/พ.ศ., สลิปโอนเงิน |
| ใบเสร็จกระดาษจากรูปกล้อง | ✅ | ✅ | คัด candidate → OCR 2048px + แก้เอียง/EXIF, สร้างแถวใหม่ (TextLayout) |
| Receipt Filing screen (mockup 16) | ✅ | ✅ | แสดงข้อมูลที่อ่านได้ + กฎที่ match + path ปลายทาง |

---

## 3. สิ่งที่ยังไม่ได้ทำ (รายละเอียด)

### 3.1 v1.0 — ส่วนที่ยังขาด

#### ✅ Exact Duplicate Detection — เสร็จแล้ว (ดูตารางข้อ 2)

#### ✅ Safety Score — เสร็จแล้ว
- ระดับ Very safe / Safe / Review first ตามชนิดของรายการ, คะแนนถ่วงน้ำหนักตามขนาด (0–100) พร้อมคำอธิบายบรรทัดเดียว, ป้ายในแต่ละแถวของ Cleanup Plan และหน้า Review, แผนเรียงจากปลอดภัยที่สุดก่อน
- ทดสอบ: unit tests ทั้งสองแพลตฟอร์ม + emulator (คะแนน 89/100)

#### ✅ Weekly Smart Clean — เสร็จแล้ว (แบบเตือนจากผลสแกนล่าสุด)
- เปิดเอง (opt-in) ที่ Settings → Notifications: เลือกวัน/เวลา, ขอสิทธิ์แจ้งเตือนตอนเปิดสวิตช์, ปุ่มส่งการแจ้งเตือนทดสอบ, แตะแล้วเปิด Cleanup Plan
- iOS: repeating calendar notification เขียนใหม่หลังสแกนเสร็จทุกครั้ง · Android: WorkManager รายสัปดาห์ อ่านผลสแกนล่าสุดตอนยิง
- ทดสอบ: unit tests (คำนวณช่วงเวลา, ข้อความ) ทั้งสองแพลตฟอร์ม + emulator (สวิตช์ → dialog สิทธิ์ → job ตั้งเวลาถูก 5 วัน 20 ชม. ก่อนวันอาทิตย์ 10:00 → ส่งทดสอบ → แตะแล้วเปิด Cleanup Plan) · iOS ดูหน้าจอบน simulator + unit tests (ยังไม่ได้ยืนยันการยิงแจ้งเตือนจริงบน iOS)
- [ ] **ยังไม่ทำ (ตั้งใจ):** สแกนเบื้องหลังจริง (iOS `BGProcessingTask` / Android worker สแกนเอง) — ตัวเลขในการแจ้งเตือนจึงเท่ากับตอนที่เปิดแอปครั้งล่าสุด; ต้องวัดแบตเตอรี่/ความน่าเชื่อถือบนเครื่องจริงก่อนตัดสินใจ
- [ ] ยืนยันบน iPhone จริง: การยิงแจ้งเตือนตามเวลา, ข้อความหลังสแกน, การแตะเปิด Cleanup Plan (cold launch)
- ไอคอนการแจ้งเตือน Android ยังเป็นไอคอนประกายชั่วคราว (`ic_stat_keepspace`, ต้องเป็นภาพขาวดำ) — ไอคอนแอปจริงทำแล้ว

#### ✅ Personalized AI Taste — เสร็จแล้ว (จาก Best Shot)
- Best Shot: เลือกรูปอื่นในแถบรูป → ปุ่มเปลี่ยนเป็น "Keep This One" และเก็บรูปที่เลือกจริง (เดิมปุ่มเก็บแต่รูปที่ AI แนะนำเสมอ)
- เรียนรู้ในเครื่อง: โมเดล pairwise logistic (Bradley–Terry) ปรับน้ำหนัก ความคมชัด/ใบหน้า/แสง จากรูปที่ผู้ใช้เก็บเทียบกับรูปอื่นในกลุ่ม, เรียนเฉพาะเมื่อผู้ใช้ยืนยันการลบแล้ว (ยกเลิก = ไม่เรียน), เชื่อถือทีละน้อยจนเต็มที่ 20 ครั้ง, ไม่มีน้ำหนักใดต่ำกว่า 3%
- เก็บแค่ตัวเลข 3 ค่า + จำนวนครั้ง (ไม่เก็บรูป/id/เนื้อหา); รูปโปรด (favorite) ยังชนะเสมอ
- Settings → AI Taste: สวิตช์เปิด/ปิด, แถบแสดงน้ำหนักเทียบค่ามาตรฐาน, ปุ่ม reset (ยืนยันก่อน)
- ทดสอบ: unit tests 8+6 ตัว (การเรียนรู้, ความเชื่อถือทีละน้อย, ไม่เรียนเรื่องใบหน้าถ้ากลุ่มไม่มีหน้า, เปิด/ปิด/reset/บันทึก) + emulator (ยกเลิกไม่เรียน → ยืนยันเรียน 1 ครั้ง → หน้า AI Taste)
- [ ] ยังไม่ทำ: เรียนรู้จากหน้า Review กลุ่ม/หมวด "Similar" ที่ผู้ใช้ติ๊กออกรูปที่ AI เลือกลบ (สัญญาณอ่อนกว่า Best Shot)
- [ ] ยังไม่ทำ: ปัจจัยเพิ่ม (เช่น ขนาดไฟล์, รอยยิ้ม, ฉากที่ชอบ) — ต้องมีข้อมูลจริงก่อนว่าปัจจัยไหนมีผล
- [ ] ยังไม่ได้ทดสอบ: iOS บนหน้าจอจริง (ทดสอบตรรกะด้วย unit tests เท่านั้น) และผลกับคลังรูปจริงหลายกลุ่ม

#### 🟡 Settings
- [x] Photo Access: สถานะ (Full/Limited/Off), ปุ่มขอสิทธิ์ / เลือกรูปเพิ่ม / เปิดหน้า Settings ของระบบ
- [x] Privacy & Security: อธิบายว่าอะไรอยู่ในเครื่อง + ปุ่มล้าง cache การวิเคราะห์ (มี dialog ยืนยัน, สแกนใหม่ทันที)
- [x] About: เวอร์ชัน + ไลเซนส์ open-source (Android)
- [x] Notifications: Weekly Smart Clean (ดูด้านบน)
- [ ] Subscription (ดูข้อ 3.5)
- [ ] About: ลิงก์ privacy policy / terms (ยังไม่มี URL — ต้องเขียนเอกสารก่อน, ดูข้อ 3.7)
- [ ] Privacy & Security: ปุ่มล้างข้อมูลใบเสร็จที่บันทึกไว้ (ตอนนี้ปุ่มล้าง cache ครอบคลุมทั้งหมดที่ derive จากคลังรูปแล้ว; กฎ Storage Rules ยังเก็บแยก)

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
- [x] **Android OCR ภาษาไทย**: ขั้นที่ 1 (spike) เสร็จ — ดู `android/app/src/androidTest/THAI_OCR_SPIKE.md` (Tesseract fast tha+eng + fixes: 21/25 เทียบ ML Kit 13/25)
  - [x] ขั้นที่ 2: Tesseract อยู่ในแอปแล้ว (`ThaiOcr`) เรียกเฉพาะรูปใบเสร็จ/ใบเสร็จ/QR/ข้อความที่ดูเป็นไทยอ่านเพี้ยน, `ThaiText` + unit tests — ทดสอบบน emulator: ใบเสร็จไทย 7/7, แชท/ช้อปปิ้งไทยจัดหมวดถูก
  - [x] จับคู่คีย์เวิร์ดแบบไม่สนวรรณยุกต์ (ทั้งสองแพลตฟอร์ม) + คีย์เวิร์ดแชท/ช้อปปิ้งไทยเพิ่ม
  - [ ] วัดเวลาสแกนครั้งแรกบนมือถือจริงที่มี screenshot ไทยจำนวนมาก (Tesseract ~0.2 วินาทีต่อรูปบน emulator)
  - [ ] ขนาดแอป: ปล่อยเป็น App Bundle (แยก ABI) — Tesseract +7.5 MB native + 5.2 MB models ต่อเครื่อง
  - [ ] ทดสอบกับใบเสร็จ/สลิปจริง (รูปทดสอบตอนนี้เป็นภาพสังเคราะห์)
- [x] **ความเสี่ยง App Review**: เลิกใช้ `PHAssetResource.value(forKey: "fileSize")` แล้ว — ใช้ public API ล้วนและยืนยัน 115/115 ไฟล์บน simulator
- [x] App icon ครบทุกขนาด ทั้ง iOS (18 ไฟล์) และ Android (legacy + round + adaptive ทุก density + Play Store 512) — สร้างด้วย `icons/build_icons.py`
  - [ ] iOS 18 dark/tinted icon และ Android 13 themed (monochrome) icon — ต้องออกแบบภาพเพิ่ม
  - [ ] ไอคอนแจ้งเตือน Android แบบขาวดำที่ออกแบบจริง (ตอนนี้เป็นประกายชั่วคราว)
- [ ] Launch screen ทั้งสองแพลตฟอร์ม (ตอนนี้ใช้ค่า default เปล่า)
- [x] `PrivacyInfo.xcprivacy` (Required Reason APIs: UserDefaults `CA92.1`, disk space `85F4.1`; ไม่เก็บข้อมูล/ไม่ track) — commit `834cb18`
  - [ ] ตอนส่ง App Store: ตอบแบบสอบถาม App Privacy เป็น "Data Not Collected" ให้ตรงกับ manifest; ถ้าเพิ่ม API กลุ่ม required-reason ใหม่ ต้องอัปเดต manifest (runbook หัวข้อ 11)
  - [ ] Android: กรอก Data safety form (ไม่เก็บ/ไม่แชร์ข้อมูล; ใช้ READ_MEDIA_*, ACCESS_MEDIA_LOCATION, POST_NOTIFICATIONS)
- [ ] Info.plist: ข้อความขอสิทธิ์ Photos ให้ครบ/สุภาพ; Android: ข้อความอธิบาย ACCESS_MEDIA_LOCATION
- [ ] Localization ภาษาไทย (ตอนนี้ UI เป็นภาษาอังกฤษทั้งหมด)
- [ ] Accessibility: VoiceOver/TalkBack label ครบ, Dynamic Type/font scale ใหญ่, contrast
- [ ] UI tests อัตโนมัติ (XCUITest / Compose UI test) สำหรับ flow หลัก: onboarding → scan → cleanup plan → review → delete
- [ ] iPad landscape: ทดสอบด้วยมือ (หมุน simulator จาก CLI ไม่ได้)
- [ ] Android release: keystore สำหรับ release, Play Console listing, Data safety form, target API ล่าสุด, ทดสอบ release build บนเครื่องจริง (R8 build ผ่านแล้ว + keep rules ของ Tesseract; ยังไม่ได้รันแอป release)
- [ ] iOS release: signing/provisioning, TestFlight, screenshots สำหรับ store (phone + iPad), App Privacy nutrition label
- [ ] Privacy policy + Terms (จำเป็นสำหรับทั้งสอง store และสำหรับ Google OAuth verification)
- [ ] CI (GitHub Actions): build + unit tests ทั้งสองแพลตฟอร์มทุก PR

---

## 4. ลำดับที่แนะนำ

1. ปิดช่องโหว่ v1.0 ที่เหลือ (launch screen, privacy policy/terms, แถว Subscription) + ทดสอบบนเครื่องจริง → พร้อมปล่อย v1.0 ตามสเปก §12
2. สร้าง OAuth clients (ข้อ 3.2) → v1.1 manual backup
3. เชื่อม rules เข้ากับ upload queue → v1.2 ครบ
4. Verification + safe delete → v1.3 ครบ
5. Monetization + analytics ก่อนปล่อยจริงบน store
