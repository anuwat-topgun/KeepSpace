# Smart Storage Cleaner — Product & Technical Specification (v1 to v1.3)

**Document status:** Delivery handoff  
**Product:** Smart Storage Cleaner  
**Versions covered:** MVP v1, v1.1, v1.2, v1.3  
**Platforms:** iOS-first, Android parity planned  
**Core strategy:** On-device AI first, privacy-first, premium consumer utility

---

# 1. Product summary

Smart Storage Cleaner is an **AI storage assistant** that helps users:

1. recover space safely
2. protect meaningful memories
3. organize media automatically
4. back up selected content to the cloud
5. clean local files only after safe verification

The product should not feel like a generic cleaner app.
It should feel like a **built-in intelligent storage system app**.

Core promise:

> Free up space, keep what matters.

Extended promise from v1.1 onward:

> AI processing stays on your device. Files are uploaded only to the destinations you choose.

---

# 2. Version roadmap overview

| Version | Focus | Main value |
|---|---|---|
| v1.0 | Core on-device AI cleaner | Recover space safely with on-device analysis |
| v1.1 | Manual cloud backup | Send selected categories to Google Drive / OneDrive |
| v1.2 | Rules engine | Automatically route files to chosen folders |
| v1.3 | Semantic filing + safe delete after backup | Extract meaning, match rules, verify uploads, then suggest cleanup |

---

# 3. Non-negotiable product rules

1. Original photos and videos must not be uploaded to our server.
2. AI classification and media understanding remain on-device.
3. Cloud uploads go directly from device to provider.
4. The backend must not proxy media files.
5. Cleanup recommendations must remain explainable.
6. Important memories must default to protection.
7. Local deletion should never happen before upload verification.
8. Privacy and trust are more important than aggressive automation.

---

# 4. Shared architecture principles

## 4.1 App-side architecture

```text
App UI
  ├── Home / Clean / Library / Insights / Settings
  ├── Cloud / Rules / Backup flows
  ├── Media Library Layer
  ├── On-device AI Engine
  ├── Cleanup Planner
  ├── Rules Engine
  ├── Sync Queue
  ├── Google Drive Adapter
  ├── OneDrive Adapter
  ├── Local Database
  ├── Analytics Layer
  └── Subscription Layer
```

## 4.2 Backend responsibilities

Allowed backend responsibilities:
- subscription / entitlement
- feature flags
- remote config
- app configuration
- analytics ingestion
- model metadata

Forbidden backend responsibilities:
- storing user photos
- storing user videos
- storing OCR content by default
- proxying uploads to Drive or OneDrive
- storing image embeddings with user identity

---

# 5. v1.0 — Core MVP

## 5.1 Objective

Help users recover storage safely using entirely on-device AI.

## 5.2 Core feature list

- Storage Analysis
- Exact Duplicate Detection
- Similar Photo Groups
- AI Best Shot + explanation
- Clean X GB
- Screenshot detection and semantic grouping
- Large Videos
- Screen Recordings
- Blurry / Bad Shot Detection
- Safety Score
- Protected Memories
- Event / Trip Grouping
- Video Compression
- Storage Forecast
- Weekly Smart Clean
- Personalized AI Taste
- Incremental scanning
- Local database caching
- No external AI API dependency

## 5.3 User value

- knows why the phone is full
- can request a cleanup target such as 10 GB
- can review safe suggestions
- can protect meaningful memories
- can compress instead of delete

## 5.4 Key UX flows

- onboarding and privacy promise
- home dashboard
- cleanup target selection
- cleanup plan
- similar photos review
- best shot explanation
- screenshots cleanup
- videos compression
- insights and forecast
- settings and privacy

## 5.5 Engineering notes

- use Vision / Core ML on iOS
- use ML Kit / TFLite / ONNX Runtime on Android
- keep scanning incremental
- cache embeddings, classification, and scan states locally

---

# 6. v1.1 — Manual cloud backup

## 6.1 Objective

Allow users to manually back up selected categories to cloud destinations they choose.

## 6.2 Scope

Cloud providers:
- Google Drive
- OneDrive

Supported use cases:
- back up selected photos
- back up screenshots
- back up receipts
- choose destination folder
- upload directly to provider
- show upload progress and status
- choose upload preferences

## 6.3 Out of scope for v1.1

- automatic routing rules
- semantic auto filing
- safe delete after backup automation
- cross-provider fallback logic
- advanced conflict resolution

## 6.4 Core screens

- Cloud Sync Overview
- Back Up Now / Manual Backup
- folder selection flow
- sync status details

## 6.5 Functional requirements

### Provider connection
- user can connect Google Drive
- user can connect OneDrive
- app shows connection status
- app can disconnect account

### Manual backup
- user can select category scope
- user can choose destination folder
- user can start upload manually
- user can see upload progress and queue state

### Upload settings
- rename files automatically toggle
- Wi-Fi only toggle
- keep local copies toggle
- compress videos before upload toggle for video flows

## 6.6 Suggested local upload states

- waiting
- uploading
- verifying
- backed up
- failed
- cancelled

## 6.7 Permissions and auth

### Google Drive
Use OAuth 2.0 and keep scopes as minimal as possible.
Prefer folder-based or app-folder-compatible flows first where product experience permits.

### OneDrive
Use Microsoft Graph with OAuth.
Prefer least privilege, but allow broader file permissions only when required by folder-selection UX.

## 6.8 Technical architecture for uploads

```text
Device
  ├── Rules not yet required
  ├── Manual backup selection
  ├── Upload queue
  ├── Google Drive adapter
  └── OneDrive adapter

Uploads flow directly from device → provider
```

## 6.9 Data model additions

### cloud_provider_connection
- id
- provider_type
- account_name
- account_identifier
- connected_at
- token_reference
- status

### cloud_upload_item
- id
- asset_id
- provider_type
- destination_folder_id
- destination_path_display
- filename
- upload_status
- provider_file_id
- bytes_uploaded
- total_bytes
- created_at
- updated_at

### upload_preferences
- wifi_only
- keep_local_copies
- rename_files_automatically
- compress_before_upload

---

# 7. v1.2 — Rules engine and auto filing

## 7.1 Objective

Allow users to define automation rules so specific content types are stored in specific destinations and folders automatically.

## 7.2 Scope

- rules list
- rule builder
- provider selection
- folder template support
- post-upload action preference
- on-device classification feeding rule matching

## 7.3 User examples

- Photos → Google Drive → /Photos/{YEAR}/{MONTH}/
- Screenshots → OneDrive → /Pictures/Screenshots/{YEAR}/{MONTH}/
- Receipts → Google Drive → /Receipts/{YEAR}/{MONTH}/{MERCHANT}/
- Large Videos → OneDrive → /Videos/Compressed/

## 7.4 Rule model

Each rule contains:
- trigger condition
- destination provider
- destination folder template
- filename template
- post-upload action policy
- enabled state

## 7.5 Initial supported trigger types

- Photo
- Screenshot
- Receipt
- Large Video
- Screen Recording
- Favorites
- Event / Trip (future-ready)

## 7.6 Folder template variables

Support at least:
- {YEAR}
- {MONTH}
- {DAY}
- {CATEGORY}
- {MERCHANT}
- {EVENT}
- {MEDIA_TYPE}
- {DATE}
- {AMOUNT}

## 7.7 File naming template variables

Support at least:
- {DATE}
- {MERCHANT}
- {AMOUNT}
- {ORIGINAL_NAME}
- {CATEGORY}
- {INDEX}

## 7.8 After-upload actions

- Keep on device
- Suggest deletion
- Delete after 30 days (must be explicit and conservative)

## 7.9 Rules engine behavior

```text
Asset selected or detected
  ↓
On-device classification
  ↓
Find matching rule
  ↓
Resolve destination path and file name
  ↓
Queue upload
  ↓
Wait for verification
  ↓
Apply post-upload policy
```

## 7.10 Data model additions

### storage_rule
- id
- name
- trigger_type
- provider_type
- destination_folder_id
- destination_path_template
- file_name_template
- after_upload_action
- enabled
- created_at
- updated_at

### storage_rule_match_log
- id
- rule_id
- asset_id
- match_confidence
- resolved_path
- resolved_filename
- status

---

# 8. v1.3 — Semantic filing and safe delete after backup

## 8.1 Objective

Turn backup from simple file upload into intelligent filing and trustworthy cleanup.

## 8.2 Scope

- semantic extraction for receipts
- rule matching based on extracted metadata
- suggested folder routing from extracted metadata
- upload verification
- safe local cleanup suggestion after backup
- cloud verified badge / status

## 8.3 Primary use case

Receipt photo or screenshot:
1. detect receipt on device
2. run OCR on device
3. extract merchant, date, amount, category
4. match storage rule
5. generate destination path and filename
6. upload directly to provider
7. verify upload success
8. suggest local cleanup if configured

## 8.4 Supported semantic extraction for v1.3

### Receipt entities
- merchant
- date
- amount
- category
- possible currency

### Future extension candidates
- ticket event date
- travel booking date
- QR category
- invoice number
- document type

## 8.5 Safe delete after backup rules

Before the app suggests cleanup, the following must be true:
- upload completed successfully
- provider file ID exists
- provider path is known
- item status marked verified
- local asset still available

Never delete immediately after upload request.

## 8.6 Suggested verification states

- upload started
- uploaded
- provider acknowledged
- verified
- cleanup eligible
- cleanup skipped
- cleanup failed

## 8.7 Data model additions

### semantic_document_analysis
- id
- asset_id
- detected_type
- merchant_name
- detected_date
- detected_amount
- detected_currency
- category
- confidence
- extracted_text_optional_local_only

### cloud_backup_verification
- id
- asset_id
- provider_type
- provider_file_id
- verified_at
- verification_method
- cleanup_eligible
- cleanup_suggested_at

## 8.8 UX principles

- clearly show what was extracted
- clearly show which rule matched
- clearly show where the file will be saved
- clearly show backup success before any cleanup suggestion
- clearly explain that files were verified before cleanup was suggested

---

# 9. Privacy and security

## 9.1 Privacy stance

v1.0 message:
> Your photos never leave your phone.

v1.1+ message:
> AI processing never leaves your device. Files are uploaded only to the cloud destinations you choose.

## 9.2 Security rules

- keep OAuth tokens secure in Keychain / Android Keystore
- do not upload media to our backend
- avoid logging filenames with personal data where possible
- do not upload OCR text by default
- do not store receipt extraction data on backend by default
- communicate provider permissions clearly

---

# 10. Analytics

Allowed analytics examples:
- connected Google Drive
- connected OneDrive
- started manual backup
- backup completed
- backup failed
- rule created
- rule edited
- rule matched
- receipt filing reviewed
- cleanup suggested after backup

Never log:
- raw media
- full OCR content
- image embeddings
- private provider file content

---

# 11. Monetization suggestion by version

## Free tier
- v1.0 core analysis
- limited cleanup
- limited duplicate review
- one connected cloud account in v1.1
- limited manual backups

## Pro tier
- unlimited cleanup
- AI Best Shot
- Smart Screenshot Intelligence
- Video Compression
- Storage Forecast
- multiple cloud providers
- unlimited rules
- auto filing
- dynamic folder templates
- receipt filing
- backup verification + cleanup suggestions

---

# 12. Release recommendation

## v1.0
Ship first to validate:
- activation
- scan completion
- cleanup conversion
- trust and retention

## v1.1
Ship after v1.0 core is stable:
- OAuth connections
- manual backup flows
- upload queue stability
- provider error handling

## v1.2
Ship after v1.1 is stable:
- rule creation
- rule editing
- destination templating
- path resolution correctness

## v1.3
Ship after v1.2 is stable:
- receipt extraction confidence
- file naming quality
- backup verification reliability
- cleanup trust after backup

---

# 13. Developer implementation priorities

## Foundation
- local media engine
- on-device AI engine
- local database
- navigation shell
- shared UI components

## v1.0
- cleanup experiences
- best shot
- screenshots
- videos
- insights

## v1.1
- provider auth
- upload queue
- manual backup UI
- sync status UI

## v1.2
- rules persistence
- rules engine
- template interpolation
- automation UI

## v1.3
- receipt OCR + entity extraction
- matched rule view
- verification pipeline
- cleanup eligibility logic

---

# 14. Acceptance checklist

## v1.0
- [ ] core scanning works
- [ ] cleanup plan works
- [ ] protected memories are safe
- [ ] offline core works

## v1.1
- [ ] Google Drive connect works
- [ ] OneDrive connect works
- [ ] manual backup works
- [ ] upload state UI works

## v1.2
- [ ] create rule works
- [ ] edit rule works
- [ ] rule matching by type works
- [ ] folder templates resolve correctly

## v1.3
- [ ] receipt extraction works reliably enough for MVP
- [ ] matched destination displays correctly
- [ ] uploaded files become verified
- [ ] cleanup suggestion appears only after verification

---

# 15. Final summary

Smart Storage Cleaner evolves across these versions as follows:

- **v1.0** = intelligent cleanup
- **v1.1** = backup destinations
- **v1.2** = storage automation
- **v1.3** = semantic filing + verified cleanup

This roadmap turns the app from a cleaner into a broader **AI-powered storage operating layer** for personal media and lightweight document organization.
