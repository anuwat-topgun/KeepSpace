# KeepSpace — สถานะงานและสิ่งที่เหลือ

อัปเดตล่าสุด: 28 ก.ย. 2026 · v1.1 Cloud Backup implementation ใน repo พร้อมแล้ว; เหลือ OAuth/physical-device/Store-account gates
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

การยืนยัน: iOS 83 tests / 27 suites ผ่าน; integration probe บน iPhone 17 simulator เทียบรูปนิ่ง 115 ไฟล์กับ KVC เดิม ตรง 115/115 (0 mismatch); ไม่มี `TMPPROBE` เหลือใน source
ข้อแลกเปลี่ยนที่ยอมรับ: สแกนครั้งแรกของคลัง 10,000 รูปช้าขึ้นราว 1–2 นาที (อ่านทุกไฟล์หนึ่งรอบ) แต่รอบถัดไปใช้ cache และได้ตัวเลขที่แม่นพร้อม hash ของรูปใน pass เดียว

---

## 1. สรุปสถานะการทดสอบ

| แพลตฟอร์ม | Unit tests | สถานะ | ทดสอบบนอุปกรณ์ |
|---|---|---|---|
| iOS (SwiftUI, iOS 17+) | 83 tests / 27 suites + 1 UI smoke test | ✅ ผ่านทั้งหมด | iPhone 17 + iPad Pro simulator |
| Android (Compose, API 26+) | 76 tests (JUnit, JVM) + UI test compile + Thai OCR spike (instrumented) | ✅ ผ่านทั้งหมด | `keepspace_tablet` emulator (Pixel Tablet, API 36) |

คำสั่งรันเทส: ดู `runbook.md` หัวข้อ 4

ทดสอบบนอุปกรณ์จำลองล่าสุด (27–28 ก.ย.):
- Exact duplicates: emulator เจอ 3 สำเนา, iOS simulator เจอ 1 สำเนา
- ใบเสร็จ/สลิปไทย+อังกฤษบน Android: 7/7 ถูก (ชื่อร้าน, ยอด, วันที่); แชท/ช้อปปิ้งไทยจัดหมวดถูก
- Memories: ทริป 2 วัน ห่างบ้าน 580 กม. + อีเวนต์ ตรวจพบทั้งสองแพลตฟอร์ม
- Release build Android (R8) ผ่าน

ข้อจำกัดของการทดสอบที่ผ่านมา:
- มี unit test ของ logic (grouping, scoring, OCR classification, receipt extraction, rules, templates, events) + smoke UI test สองแพลตฟอร์มสำหรับ demo navigation; flow ลบผ่าน system confirmation ยังต้องทดสอบจริง
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
- [x] About: ลิงก์ privacy policy / terms แบบ public (`legal/` บน GitHub)
- [ ] Privacy & Security: ปุ่มล้างข้อมูลใบเสร็จที่บันทึกไว้ (ตอนนี้ปุ่มล้าง cache ครอบคลุมทั้งหมดที่ derive จากคลังรูปแล้ว; กฎ Storage Rules ยังเก็บแยก)

#### 🟡 Memories — ต่อยอด
- [ ] Android: เพิ่ม scene labeling (ML Kit Image Labeling bundled) เพื่อให้ชื่อธีม (Birthday Party, Beach Trip) เหมือน iOS
- [ ] ให้ผู้ใช้เปลี่ยนชื่อทริป/อีเวนต์ และปิดการป้องกันรายอีเวนต์ได้ (เก็บ override ในเครื่อง)
- [ ] ยืนยันเกณฑ์ (6 ชม., 80 กม., 12/20/30 รูป) กับคลังรูปจริงหลายแบบ แล้วปรับ
- [ ] (ทางเลือก) ชื่อสถานที่: ต้องใช้ reverse geocoding ซึ่งส่งพิกัดไป Apple/Google — ต้องตัดสินใจเรื่อง privacy ก่อน ถ้าทำให้เป็น opt-in

### 3.2 v1.1 — Manual cloud backup 🟡

งานใน repo:
- [x] OAuth Authorization Code + PKCE ไม่มี client secret; iOS token อยู่ Keychain, Android OneDrive token เข้ารหัสด้วย Android Keystore
- [x] Android Google Drive ใช้ Google Identity `AuthorizationClient` ตามข้อกำหนดปัจจุบัน (ไม่ใช้ custom-scheme browser OAuth ที่ Google เลิกรองรับ)
- [x] Scope ต่ำสุด: Drive `drive.file`; OneDrive `Files.ReadWrite.AppFolder`; disconnect/revoke Google token; ไม่มี KeepSpace backend
- [x] Persistent queue แยกจาก analysis cache: waiting → uploading → verifying → backed up / failed / cancelled, retry/backoff, resume หลังเปิดแอปใหม่
- [x] Android WorkManager + Wi-Fi constraint; iOS persistent Application Support queue + no-cellular URLSession และ resume ตอน active
- [x] Drive resumable upload + KeepSpace folder; OneDrive app-folder upload; คืน/ตรวจ remote file ID
- [x] Preferences: rename, Wi-Fi only, keep local copies (บังคับเปิดใน v1.1); เลือก Photos/Screenshots/Receipts และโฟลเดอร์ปลายทาง
- [x] Cloud Overview + Back Up Now + progress/error/retry/cancel UI ทั้งสองแพลตฟอร์ม; เปิด entry point ใน Settings/Library
- [x] privacy/terms/store copy/review notes/release checklist อัปเดตสำหรับ v1.1; version `1.1.0 (2)`

Release gates ที่ต้องใช้เจ้าของบัญชี/เครื่องจริง (รายละเอียด `store/CLOUD_SETUP.md`):
- [ ] Google Cloud: เปิด Drive API, ทำ consent screen, iOS client ID, Android clients สำหรับ debug/release/Play SHA-1, app ownership/verification
- [x] Microsoft Entra: app registration personal+work, public client, redirect URIs, scope `Files.ReadWrite.AppFolder`; ฝัง public client ID ใน build defaults และยัง override ผ่าน environment ได้
- [ ] Microsoft publisher verification: เพิ่ม custom domain ที่ยืนยัน DNS แล้วและเชื่อม Microsoft Partner Center; ตอนนี้ Entra แสดง Unverified publisher และเตือนว่าผู้ใช้ทั่วไปยัง consent ให้แอป multitenant ใหม่ไม่ได้
- [ ] ทดสอบ connect/upload/refresh/revoke จริงทั้ง 2 providers บน iOS/Android production-signed build; quota/offline/kill-resume/Wi-Fi-only
- [ ] Android Google token มีอายุประมาณ 1 ชม.; หากคิวข้ามอายุ แอปแจ้งให้ reconnect แล้ว retry (แนวทางทางการไม่แนะนำเก็บ refresh token บนอุปกรณ์)
- [ ] iOS ทำงานต่อเมื่อแอปกลับ active; ถ้าต้องการอัปโหลดต่อขณะถูก suspend เป็นเวลานาน ให้ย้าย data PUT ไป background `URLSession` หลังวัดบนเครื่องจริง

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
  - [x] ขนาดแอป: release เป็น App Bundle (แยก ABI) — Tesseract +7.5 MB native + 5.2 MB models ต่อเครื่อง
  - [ ] ทดสอบกับใบเสร็จ/สลิปจริง (รูปทดสอบตอนนี้เป็นภาพสังเคราะห์)
- [x] **ความเสี่ยง App Review**: เลิกใช้ `PHAssetResource.value(forKey: "fileSize")` แล้ว — ใช้ public API ล้วนและยืนยัน 115/115 ไฟล์บน simulator
- [x] App icon ครบทุกขนาด ทั้ง iOS (18 ไฟล์) และ Android (legacy + round + adaptive ทุก density + Play Store 512) — สร้างด้วย `icons/build_icons.py`
  - [ ] iOS 18 dark/tinted icon — optional, ยังไม่ได้ทำ
  - [x] Android 13 themed (monochrome) icon
  - [ ] ไอคอนแจ้งเตือน Android แบบขาวดำที่ออกแบบจริง (ตอนนี้เป็นประกายชั่วคราว)
- [x] Launch screen ทั้งสองแพลตฟอร์ม (iOS storyboard + Android pre-31/Android 12+ native splash)
- [x] `PrivacyInfo.xcprivacy` (Required Reason APIs: UserDefaults `CA92.1`, disk space `85F4.1`; ไม่เก็บข้อมูล/ไม่ track) — commit `834cb18`
  - [x] เตรียมคำตอบ App Privacy เป็น "Data Not Collected" ใน `store/app-privacy.md`; [ ] เจ้าของบัญชีกดส่งคำตอบใน App Store Connect
  - [x] เตรียมคำตอบ Android Data safety ใน `store/app-privacy.md`; [ ] เจ้าของบัญชีกดส่งใน Play Console
- [x] Info.plist: ข้อความขอสิทธิ์ Photos/Add Photos ชัดเจน; onboarding Android/iOS อธิบายก่อนเปิด permission dialog และหน้า Photo Access อธิบาย location/on-device
- [ ] Localization ภาษาไทย (ตอนนี้ UI เป็นภาษาอังกฤษทั้งหมด)
- [ ] Accessibility: VoiceOver/TalkBack label ครบ, Dynamic Type/font scale ใหญ่, contrast
- [x] Smoke UI tests (XCUITest / Compose UI test): demo launch + Home → Clean → Library → Settings
- [ ] UI test ที่ใช้คลังรูปจริงสำหรับ onboarding → permission → scan → cleanup plan → review → system delete confirmation
- [ ] iPad landscape: ทดสอบด้วยมือ (หมุน simulator จาก CLI ไม่ได้)
- [x] Android repo release: target API 36 + AGP 8.10.1, env-based signing hook, Play listing/Data safety draft, R8 App Bundle
  - [ ] เจ้าของแอป: สร้าง/สำรอง upload keystore, เปิด Play App Signing, อัปโหลด Internal Testing, กรอก Console forms และทดสอบ release บนเครื่องจริง
- [x] iOS repo release: listing/App Privacy/review-note draft, launch screen, privacy manifest, version/build พร้อม archive
  - [ ] เจ้าของแอป: distribution signing/provisioning, TestFlight, screenshots phone+iPad และกดส่ง App Privacy
- [x] Privacy policy + Terms (public GitHub URLs; ลิงก์ใน About ทั้งสองแพลตฟอร์ม)
- [x] CI (`.github/workflows/ci.yml`): Android unit/UI-test compile/lint/AAB + iOS unit/UI tests ทุก PR

---

## 4. ลำดับที่แนะนำ

1. ทำ `store/CLOUD_SETUP.md` และรัน physical-device/OAuth gates ใน `store/RELEASE_CHECKLIST.md`
2. อัปโหลด build 2 (`1.1.0`) เข้า TestFlight + Play Internal Testing แล้วทดสอบ cloud ด้วย production clients
3. เชื่อม rules เข้ากับ upload queue → v1.2 ครบ
4. Verification + safe delete → v1.3 ครบ
5. Monetization + analytics ก่อนปล่อยจริงบน store
