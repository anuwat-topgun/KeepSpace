# KeepSpace — สถานะงานและสิ่งที่เหลือ

อัปเดตล่าสุด: 30 ก.ย. 2026 · Library/preview/cloud status/localization พร้อมแล้ว; เหลือ production OAuth, physical-device และ Store submission gates
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

การยืนยัน: iOS unit tests ปัจจุบัน 86 tests ผ่าน; integration probe บน iPhone 17 simulator เทียบรูปนิ่ง 115 ไฟล์กับ KVC เดิม ตรง 115/115 (0 mismatch); ไม่มี `TMPPROBE` เหลือใน source
ข้อแลกเปลี่ยนที่ยอมรับ: สแกนครั้งแรกของคลัง 10,000 รูปช้าขึ้นราว 1–2 นาที (อ่านทุกไฟล์หนึ่งรอบ) แต่รอบถัดไปใช้ cache และได้ตัวเลขที่แม่นพร้อม hash ของรูปใน pass เดียว

---

## 1. สรุปสถานะการทดสอบ

| แพลตฟอร์ม | Unit tests | สถานะ | ทดสอบบนอุปกรณ์ |
|---|---|---|---|
| iOS (SwiftUI, iOS 17+) | 86 unit tests + 1 UI smoke test | ✅ ผ่านทั้งหมด | iPhone 17 + iPad Pro simulator; signed build ติดตั้ง/เปิดบน iPhone 100 Pro Max |
| Android (Compose, API 26+) | 77 tests (JUnit, JVM) + UI test compile + Thai OCR spike (instrumented) | ✅ ผ่านทั้งหมด | `keepspace_tablet` emulator (Pixel Tablet, API 36) |

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
- iPhone จริงยืนยัน build/install/launch แล้ว แต่ยังไม่ได้ sign-off คลังใหญ่, cloud end-to-end, permission/delete และ background/resume; Android ยังไม่ทดสอบบนเครื่องจริง

---

## 2. Feature ที่ทำเสร็จแล้ว

### พื้นฐาน (Foundation)
| งาน | iOS | Android | หมายเหตุ |
|---|---|---|---|
| โครงแอป + navigation รองรับ phone/tablet | ✅ | ✅ | iOS: TabView / NavigationSplitView (iPad sidebar) · Android: NavigationSuiteScaffold (bar/rail/drawer) |
| Design system (สี, ตัวอักษร, การ์ด, ปุ่ม, badge, dark mode) | ✅ | ✅ | ตาม design handoff |
| App Store Connect: app "KeepSpace: Storage Cleaner" (ชื่อ "KeepSpace" เฉย ๆ ถูกใช้แล้ว) | ✅ | — | ASC app 6816584493, bundle `com.keepspace.app`, SKU keepspace-ios-001 |
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
| Library แบบ Photos + cloud status | ✅ | ✅ | กรอง All/Photos/Videos/Backed Up, badge ต่อ asset, รูป portrait แสดงเต็มแบบ aspect-fit, ปัดซ้าย/ขวาและปัดลงปิด; วิดีโอเล่นและเข้า fullscreen ด้วย double tap/ไอคอนได้ |
| Select All ในหน้า Review | ✅ | ✅ | เลือกเฉพาะรายการ selectable ที่แสดงอยู่, ล้าง stale IDs และไม่เลือกรายการ keeper/protected |

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
- [x] About: ลิงก์ privacy policy / terms แบบ public บน `https://keepspace.itston.com/`
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
- [x] iOS Google Drive ใช้ Google Sign-In SDK, production client ID และ reversed client-ID callback URL scheme
- [x] Scope ต่ำสุด: Drive `drive.file`; OneDrive `Files.ReadWrite.AppFolder`; disconnect/revoke Google token; ไม่มี KeepSpace backend
- [x] Persistent queue แยกจาก analysis cache: waiting → uploading → verifying → backed up / failed / cancelled, retry/backoff, resume หลังเปิดแอปใหม่
- [x] Android WorkManager + Wi-Fi constraint; iOS persistent Application Support queue + no-cellular URLSession และ resume ตอน active
- [x] Drive resumable upload + KeepSpace folder; OneDrive app-folder upload; คืน/ตรวจ remote file ID
- [x] Preferences: rename, Wi-Fi only, keep local copies (บังคับเปิดใน v1.1); เลือก Photos/Screenshots/Receipts และโฟลเดอร์ปลายทาง
- [x] Cloud Overview + Back Up Now + progress/error/retry/cancel UI ทั้งสองแพลตฟอร์ม; เปิด entry point ใน Settings/Library
- [x] ผูกสถานะ backup กับ `assetID + sourceModifiedAt` เพื่อไม่แสดง badge ว่า backed up หลังไฟล์ต้นฉบับเปลี่ยน; Android รับ progress จาก WorkManager แบบ live
- [x] privacy/terms/store copy/review notes/release checklist อัปเดตสำหรับ v1.1; version `1.1.0 (2)`

Release gates ที่ต้องใช้เจ้าของบัญชี/เครื่องจริง (รายละเอียด `store/CLOUD_SETUP.md`):
- [x] Production website: Home, Privacy, Terms, Support และ health check เปิดผ่าน `https://keepspace.itston.com/`
- [ ] Google Cloud: iOS client และ Android debug/release clients สร้างแล้ว; ยังต้องเข้าสู่ `keepspace-production` ด้วยบัญชี ITSTON.COM ที่ถูกต้องเพื่อยืนยัน consent/app ownership, สร้าง client จาก Play App Signing SHA-1 และทดสอบ Drive API บนเครื่องจริง (session ล่าสุดเป็นบัญชี Google อื่น)
- [x] Microsoft Entra: app registration รองรับเฉพาะ Personal Microsoft accounts, public client, redirect URIs, scope `Files.ReadWrite.AppFolder`; ฝัง public client ID ใน build defaults และยัง override ผ่าน environment ได้
- [x] OneDrive personal-only hardening: Entra audience เป็น Personal Microsoft accounts และ iOS/Android ใช้ OAuth authority `consumers`; Microsoft Partner Center publisher verification ไม่เป็น release gate จนกว่าจะเพิ่มบัญชีองค์กร
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

### 3.5 Monetization (สเปก §11) 🟡 implement เสร็จทั้งสองแพลตฟอร์ม · รอส่งร้านค้าและทดสอบซื้อจริง

ผู้ใช้อนุมัติแนวทางนี้แล้ว (3 ต.ค. 2026) ราคาเป็นราคาตั้งต้นจากการประเมิน **ยังไม่ได้ตรวจราคาคู่แข่งหรือวัด conversion จริง** → ต้องวัดผลหลังเปิดแล้วปรับ
ดีไซน์หน้าจอ: `store/PAYWALL_DESIGN.md` + mockup `store/paywall/` (`store/paywall/paywall-mockup.html`, PNG ใน `store/paywall/*.png`)

#### แพ็กเกจและฟีเจอร์ (ที่ตัดสินใจแล้ว)
| ฟีเจอร์ | Free | Pro |
|---|---|---|
| สแกนทั้งคลัง, Storage Analysis, เห็นขนาดที่เคลียร์ได้ทุกหมวด | ✅ ไม่จำกัด | ✅ |
| Safety Score, Memories ที่ปกป้องไว้, ยืนยันก่อนลบ, ถังขยะ 30 วัน | ✅ **ห้ามเอามาขาย** | ✅ |
| Exact Duplicates, Similar Photos, Blurry | ✅ ลบได้ตามโควตา | ✅ ไม่จำกัด |
| Best Shot (รูปที่ AI แนะนำ) | ✅ (ต่างจากสเปก — ถ้าซ่อนไว้ กลุ่มรูปคล้ายใช้ไม่ได้) | ✅ + ลบทั้งหมวดในครั้งเดียว |
| Storage Forecast, Weekly Smart Clean, Photo Access/Privacy | ✅ (Forecast ต่างจากสเปก — ต้นทุนต่ำ ช่วยให้กลับมาเปิดแอป) | ✅ |
| Backup ไป Drive/OneDrive แบบมือ | ✅ 1 บัญชี, จำกัดจำนวนต่อเดือน | ✅ ทุกบัญชี, ไม่จำกัด |
| Storage Rules + Rule Builder | ✅ 1 กฎ | ✅ ไม่จำกัด |
| บีบอัดวิดีโอ | — | ✅ |
| Screenshot แยกหมวดด้วย OCR (ใบเสร็จ/ตั๋ว/แชท/ช้อปปิ้ง, ไทย/อังกฤษ) | ดูจำนวน+ขนาดต่อหมวด | ✅ เปิดดู/จัดการรายหมวด |
| Receipt Filing (screenshot + ใบเสร็จกระดาษ) | — | ✅ |
| AI Taste (เรียนรู้รสนิยม) | — | ✅ |

- **ห้ามโฆษณาฟีเจอร์ที่ยังไม่มี:** การเชื่อมกฎเข้าคิวอัปโหลดอัตโนมัติ และ backup verification + safe delete (ข้อ 3.3–3.4) ยังไม่ได้ทำ — ห้ามอยู่ในรายการ Pro บนหน้า paywall/store จนกว่าจะส่งมอบจริง
- โควตา Free (ตัวตั้งต้น ต้องวัดแล้วปรับ): **ลบได้ ~1 GB ต่อเดือน** (นับเฉพาะที่ผู้ใช้ยืนยันลบแล้ว, รีเซ็ตต้นเดือนตามเวลาเครื่อง) · backup **~100 ไฟล์ต่อเดือน** · rules **1 กฎ**
- ต้องไม่ล็อก: การ "ดู" ผลสแกน, การปกป้อง Memories/รูปโปรด, หน้าลบที่มีคำเตือนความปลอดภัย — ล็อกได้เฉพาะ "การกระทำ" ที่เกินโควตาหรือฟีเจอร์ Pro

#### ราคา (ตั้งต้น — ใช้ตารางราคาตามประเทศของ App Store / Play Console ห้าม hardcode ในแอป)
| ตัวเลือก | USD | บาท (ประมาณ) | หมายเหตุ |
|---|---|---|---|
| **รายปี** (ตัวหลัก, เลือกไว้ล่วงหน้า) | $19.99 | ~฿690 | ทดลองฟรี 7 วัน |
| รายเดือน | $2.99 | ~฿99 | |
| ซื้อขาด (lifetime) | $39.99 | ~฿1,390 | non-consumable / one-time product |

- ไม่ทำรายสัปดาห์ (ขัดกับจุดขายเรื่องความไว้ใจ)
- Product IDs ที่เสนอ: `com.keepspace.app.pro.annual`, `.monthly` (อยู่ subscription group เดียวกัน), `.lifetime`
- ต้นทุนหลักคือส่วนแบ่งร้านค้า 15–30% (ไม่มี backend/ไม่มีค่าพื้นที่ cloud) — สมัครโปรแกรมร้านค้ารายเล็กของ Apple/Google เพื่อให้ได้ 15%

#### งานที่ต้องทำ (implement)
- [ ] **ผู้ใช้ต้องทำก่อน:** ตั้ง subscription group + 3 products ใน App Store Connect และ Play Console, ข้อตกลง Paid Apps/ข้อมูลภาษี-ธนาคาร, สมัคร Small Business Program, ตอบ App Privacy/Data safety ให้ตรงกับที่แอปเก็บ (ไม่เก็บข้อมูล — IAP ไม่เปลี่ยนคำตอบ)
  - [x] **App Store Connect ทำแล้ว (8 ต.ค. 2026):** กลุ่ม KeepSpace Pro + Yearly ($19.99, trial 1 สัปดาห์), Monthly ($2.99), Lifetime ($39.99 non-consumable), เปิด Family Sharing, Paid Apps Agreement/ธนาคาร/ภาษี Active — อัปโหลด build 4 (1.1.0) ขึ้น TestFlight แล้ว + กลุ่ม Internal Testing + screenshot รีวิวของทั้ง 3 product แล้ว; หน้าเวอร์ชัน iOS: promo/description/keywords/support URL/copyright/screenshots iPhone+iPad กรอกแล้ว (release แบบ manual) — Privacy Policy URL + App Privacy (กรอกแล้ว ยังไม่กด Publish — ให้เจ้าของกด) + Age Rating 4+ + Content Rights (ไม่มีเนื้อหาบุคคลที่สาม) + subtitle + หมวด Utilities/Photo&Video บันทึกแล้ว (9 ต.ค. 2026) — ข้อมูลติดต่อรีวิว + review notes + ราคาแอป Free บันทึกแล้ว, สินค้าทั้ง 3 ตัวอยู่ในการส่งฉบับร่างแล้ว (ยังไม่ส่งตรวจ) — **เหลือ: เจ้าของกด Publish App Privacy (ASC ไม่ให้เพิ่ม 1.1.0 เข้าฉบับร่างจนกว่าจะ publish),  ผูก 3 products กับ 1.1.0 แล้ว Add for Review, ทดสอบซื้อ/restore ใน sandbox** (ส่งตรวจ iOS แล้วเมื่อ 9 ต.ค. 2026 — 1.1.0 + สินค้า 3 ตัว รอ Apple ตอบ)
  - [~] **Play Console (9 ต.ค. 2026, บัญชี ITSTON.COM):** AAB 4 (1.1.0, Billing 8.0.0) อยู่ใน Internal testing แล้ว (ผู้ทดสอบ KeepSpace Testers 2 คน); สร้าง Pro Yearly ($19.99) กับ Pro Monthly ($2.99) เป็นฉบับร่างแล้ว — **Data safety/กลุ่มเป้าหมาย/Login details บันทึกแล้ว; store listing (ข้อความ, ไอคอน, feature graphic, ภาพมือถือ 6 ภาพ) บันทึกเป็นฉบับร่างแล้ว (รูปที่ `store/android/screenshots/phone/`, `store/android/graphics/`); ยังไม่มีภาพแท็บเล็ต — เหลือ: เปิดใช้งาน base plan ทั้งสอง, ข้อเสนอทดลองฟรี 7 วันของ Yearly, สินค้าครั้งเดียว Lifetime ($39.99), Data safety/กลุ่มเป้าหมาย/Login details/store listing**
- [~] Entitlement layer — **pure logic + `MonetizationStore` เสร็จทั้งสองแพลตฟอร์ม พร้อม test (cache สิทธิ์ + grace 3 วัน)**; **ตัวต่อ StoreKit 2 (`ProStoreService`) และ Play Billing (`ProBillingService`) เขียนแล้ว เรียก `applyPurchases` ตอนเปิดแอป/กลับมา foreground** — ยังไม่ได้ทดสอบกับสินค้าจริง (ต้องสร้าง product ใน App Store Connect / Play Console ก่อน; iOS ทดสอบในเครื่องได้ด้วย `ios/StoreKit/KeepSpace.storekit` ผ่าน Xcode scheme) (ไม่มี backend, ไม่มีบัญชีผู้ใช้): iOS StoreKit 2 `Transaction.currentEntitlements` + `Transaction.updates` · Android Play Billing `queryPurchasesAsync` + acknowledge; cache สิทธิ์ในเครื่องให้ใช้ออฟไลน์ได้ (grace) ; `Entitlement` เป็น pure logic ใช้ร่วมกับ test
- [x] `UsageLimits` — เสร็จ: ledger รายเดือน, gate ลบแบบ partial (ปลอดภัยสุดก่อน), โควตา backup/กฎ/บัญชี cloud; ต่อเข้า flow ลบแล้ว ยังไม่ต่อ backup (pure, มีคู่ Swift/Kotlin + unit test): โควตาลบต่อเดือน, โควตา backup, จำนวนกฎ — เก็บเป็นตัวเลขใน UserDefaults/SharedPreferences (ไม่เก็บรายการไฟล์)
- [~] Paywall ตาม `store/PAYWALL_DESIGN.md` — **เสร็จแล้ว: full paywall + หน้า/แถว Subscription ใน Settings ทั้งสองแพลตฟอร์ม**; **quota gate sheet + ป้าย PRO เสร็จแล้ว** — ราคา/ทดลองฟรีดึงจาก store เท่านั้น
- [x] ตรวจ Android ด้วยตาบน emulator โทรศัพท์ (Settings/Subscription/Paywall/AI Taste/Cloud/Back Up Now/Videos+Compress explainer/Quota sheet/Screenshots/Similar Photos/Best Shot/Memories/Insights/Clean/About) — พบและแก้บั๊กเดิม: การ์ดที่มีหลายลูกวางทับกัน (แก้ที่ `SmartCard`)
- [x] ผูก gate เข้าจุดต่าง ๆ ครบ — **ปุ่มลบ (Review ทุกหมวด/กลุ่ม + Best Shot; Cleanup Plan ผ่านหน้า Review; นับเฉพาะที่ลบสำเร็จผ่าน `onDeleted`), บีบอัดวิดีโอ (explainer), หมวด Screenshot (เปิดเนื้อหาเป็น Pro, นับ/ขนาดยังเห็น), Receipt Filing (ProGate ที่ปลายทาง), AI Taste (explainer ถ้ายังไม่เคยเรียนรู้; Free หยุดเรียนรู้แต่เก็บ/รีเซ็ตสิ่งที่เรียนได้), กฎที่ 2, cloud account ที่ 2, โควตา backup (ตัดให้พอดี + ปุ่ม Unlock) — ป้าย PRO ติดที่แถว/ปุ่ม** (ไม่ซ่อน/เบลอเนื้อหา). ทางเข้า Receipts (Receipt Filing) และ Storage Rules เปิดแล้วที่เมนู ⋯ ในแท็บ Library ทั้งสองแพลตฟอร์ม และแถว Storage Rules ใน Settings; Receipt Filing ล็อกที่ปลายทาง (ProGate). ปรับข้อความไม่ให้อวดการ file อัตโนมัติที่ยังไม่มี (ข้อ 3.3 ยังไม่ทำ): หัวหน้า Storage Rules และ benefit ใน paywall
- [x] Restore Purchases (ปุ่มในหน้า paywall + Settings), จัดการ Subscription (ลิงก์ไปหน้าจัดการของระบบ), ข้อความ auto-renew/ยกเลิก — ทำแล้ว; **ข้อความ fine print แปลด้วยเครื่อง ต้องให้คนตรวจก่อนปล่อยจริง**
- [ ] พฤติกรรมเมื่อ Pro หมดอายุ: **ไม่ลบข้อมูลของผู้ใช้เด็ดขาด** — กฎที่เกิน 1 ถูกปิดไว้ (ไม่ลบ), บัญชี cloud ที่สองยังเชื่อมอยู่แต่หยุดคิวใหม่, ของที่ backup ไปแล้วยังอยู่ในบัญชีผู้ใช้
- [ ] Tests: entitlement state machine (ซื้อ/ทดลอง/หมดอายุ/refund/restore), โควตา (นับเฉพาะที่ลบสำเร็จ, รีเซ็ตต้นเดือน, ตัดรายการให้พอดีโควตาโดยเรียงจากปลอดภัยที่สุดก่อน), ไม่มีทางที่ gate ซ่อนฟีเจอร์ความปลอดภัย; ทดสอบจริงด้วย StoreKit configuration file (iOS) และ Play test tracks/license testers (Android)
- [x] อัปเดต `store/` (listing ทั้งสองร้านมีส่วน KeepSpace Pro + ข้อความต่ออายุอัตโนมัติ + ลิงก์ Terms/Privacy, review notes อธิบายวิธีซื้อ/restore, app-privacy: มี in-app purchases แต่คำตอบ Data collected ไม่เปลี่ยน) และ privacy policy/terms ที่พูดถึง subscription/lifetime/ยกเลิก/restore — เขียนเมื่อ 8 ต.ค. 2026; **เจ้าของ: ต้องคัดลอกข้อความ legal เดียวกันไปไว้ที่เว็บ `keepspace.itston.com` (privacy URL ที่ใส่ใน App Store Connect ชี้ไปที่นั่น) และให้ฝ่ายกฎหมายตรวจ**
- [ ] วัดผลหลังเปิด (เมื่อมี analytics ตามข้อ 3.6 และได้รับความยินยอม): อัตรากดซื้อ, trial→paid, แล้วปรับโควตา/ราคา (Google Play ทดลองหลายราคาได้)
- [x] ~~ถ้าต้องมี backend~~ ตัดสินใจ: **ไม่ต้องมี backend** — สิทธิ์ตรวจกับร้านค้าโดยตรง (สเปก §4.2 อนุญาต backend เฉพาะ entitlement/feature flags ซึ่งเรายังไม่ต้องใช้)

### 3.6 Analytics (สเปก §10) ⬜
- [ ] เลือกเครื่องมือที่เคารพ privacy (หรือทำ endpoint เอง) + ขอความยินยอม
- [ ] Event ที่อนุญาต: connected provider, backup started/completed/failed, rule created/edited/matched, receipt filing reviewed, cleanup suggested
- [ ] ห้ามส่ง: สื่อ, ข้อความ OCR, embedding, ชื่อไฟล์

### 3.7 คุณภาพและการเตรียมปล่อยแอป
- [ ] **ทดสอบบนเครื่องจริง** iPhone + iPad + Android phone + tablet ด้วยคลังรูปใหญ่ (10k+ รูป): เวลาสแกน, หน่วยความจำ, แบตเตอรี่, ความแม่นของ similar/blurry/receipt/events
- [x] iPhone 100 Pro Max (iOS 26.6.2): signed build ด้วย Team `Anuwat Palasak` (`82MGZU2HH4`) build/install/launch สำเร็จ
- [ ] iPhone 100 Pro Max: ทดสอบ Library grid/filter, swipe viewer, portrait aspect-fit, video playback/fullscreen, Select All และสถานะ cloud กับคลังจริง
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
- [x] Localization 25 ภาษาเหมือน IPTV Prime (รวมไทย, Arabic RTL, iOS permission prompts และ Android per-app language config); shared catalogue + generators อยู่ใน `i18n/`
- [ ] Accessibility: VoiceOver/TalkBack label ครบ, Dynamic Type/font scale ใหญ่, contrast
- [x] Smoke UI tests (XCUITest / Compose UI test): demo launch + Home → Clean → Library → Settings
- [ ] UI test ที่ใช้คลังรูปจริงสำหรับ onboarding → permission → scan → cleanup plan → review → system delete confirmation
- [ ] iPad landscape: ทดสอบด้วยมือ (หมุน simulator จาก CLI ไม่ได้)
- [x] Android repo release: target API 36 + AGP 8.10.1, env-based signing hook, upload keystore นอก repo, signed AAB, Play listing/Data safety draft, R8 App Bundle
  - [ ] เจ้าของแอป: เปิด Play App Signing, เพิ่ม signing SHA-1 ใน Google OAuth, อัปโหลด closed testing, กรอก Console forms และทดสอบ release บนเครื่องจริง
- [x] iOS repo release: listing/App Privacy/review-note draft, launch screen, privacy manifest, version/build และ Team `Anuwat Palasak` signing พร้อม archive
  - [ ] เจ้าของแอป: archive/upload TestFlight, screenshots phone+iPad และกดส่ง App Privacy
- [x] Privacy policy + Terms (public GitHub URLs; ลิงก์ใน About ทั้งสองแพลตฟอร์ม)
- [x] CI (`.github/workflows/ci.yml`): Android unit/UI-test compile/lint/AAB + iOS unit/UI tests ทุก PR

---

## 4. ลำดับที่แนะนำ

1. ใช้บัญชี ITSTON.COM จบ Google consent/app ownership และเพิ่ม OAuth client จาก Play App Signing SHA-1 ตาม `store/CLOUD_SETUP.md`
2. อัปโหลด build 2 (`1.1.0`) เข้า TestFlight + Play closed testing แล้วทดสอบ Google Drive/OneDrive upload จริงด้วย production clients
3. รัน physical-device checklist: คลัง 10k+, Library/preview/video/Select All, permission/delete, offline/kill-resume/Wi-Fi-only และ accessibility
4. เชื่อม rules เข้ากับ upload queue → v1.2 ครบ
5. Verification + safe delete → v1.3 ครบ
6. Monetization + analytics ก่อนปล่อยจริงบน store
