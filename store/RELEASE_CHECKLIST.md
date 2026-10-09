# KeepSpace 1.1 — release checklist

Last audited: October 9, 2026. This file is the release source of truth.

Passing repository tests increase release confidence, but Apple and Google make the final review decisions. Do not describe approval as guaranteed.

## Verified in this audit

- [x] iOS unit suite: 135 tests in 36 suites passed on iPhone 17 simulator.
- [x] iOS UI suite: 3/3 passed. It covers primary navigation, every Library filter not opening a preview, and opening the second demo photo then paging left and right.
- [x] Android: `testDebugUnitTest`, `assembleDebugAndroidTest`, and `lintRelease` passed.
- [x] Android signed release AAB built with `android/build-signed-release.sh`; `jarsigner` verified it and the upload certificate SHA-1 is `26:F7:E4:4A:BA:F2:4B:C4:F1:0A:29:4B:81:4C:3E:94:E8:86:AE:8D`.
- [x] Localization catalogue: 25 complete locales × 598 keys; placeholder types/order validated. Android resources use numbered format arguments.
- [x] Production legal links used by both apps: `https://keepspace.itston.com/privacy/` and `https://keepspace.itston.com/terms/`.
- [x] Public Home, Privacy, Terms, and Support pages returned HTTP 200 during the release audit.
- [x] iOS archive/build 1.1.0 (4), bundle `com.keepspace.app`, Team `Anuwat Palasak` (`82MGZU2HH4`), launch screen, privacy manifest, and OAuth callback configuration were inspected. Build 4 uploaded successfully to App Store Connect.
- [x] App Store Connect App Privacy is published and the three KeepSpace Pro products are included with the submitted version.
- [x] Play App Signing is enabled. Current app-signing certificate SHA-1 is `A4:B3:55:E3:14:A9:92:4A:45:61:1E:D2:21:4F:E4:C5:8E:2A:40:4C`.
- [x] Play products are active: yearly subscription with trial offer, monthly subscription, and lifetime one-time purchase.
- [x] Play internal-testing release 1.1.0 (4) is active.
- [x] Google OAuth client `KeepSpace Android Play App Signing` exists in `keepspace-production` for package `com.keepspace.app` and the Play App Signing SHA-1.
- [x] Play AI-content, Photos & Videos access, and Advertising ID declarations are complete. The submitted icon and feature graphic are labelled as AI-generated/modified; the app declares full-library photo/video access for its core cleanup/backup workflows and declares that it does not use Advertising ID.
- [x] Closed-testing Alpha changes for 1.1.0 (4), Thailand, and the `IPTV Prime Testers` + `KeepSpace Testers` lists were submitted to Google for review on October 9, 2026.
- [x] Play's automated pre-submission quick check completed without surfacing another blocker; Publishing Overview now shows the 13 changes under review.

## Blocking iOS release gates

- [ ] Current App Review status is **Rejected — Guideline 2.1, Information Needed**. Apple did not report a crash; it requested operating details and a physical-device recording.
- [ ] Record one continuous video on a physical iPhone running the latest public iOS. Follow the storyboard in `store/APPLE_REVIEW_RESPONSE.md`, including launch, Photos permission, scan, deletion confirmation, Library photo/video/share flows, a completed cloud upload, and the full Pro purchase screen.
- [ ] Verify every step against the exact submitted build, attach the recording, paste `store/APPLE_REVIEW_RESPONSE.md` into Resolution Center, and resubmit.
- [ ] Perform a real sandbox purchase/restore test for yearly, monthly, and lifetime products.

## Blocking Android release gates

- [ ] Wait for Google to approve/publish the Alpha changes, then verify at least 12 distinct testers have opted in and keep the test active for at least 14 continuous days before applying for production access. Selecting two email lists does not by itself prove opt-in eligibility.
- [ ] Wait for Play to generate the pre-launch report from the submitted closed-track artifact, then resolve every crash, ANR, accessibility, security, and compatibility finding. Immediately after submission, the report page still showed “upload an artifact”.
- [ ] Test purchases, trial eligibility, cancellation/refund, restore, and upgrade/downgrade with Play license testers.

## Physical-device acceptance gates for both stores

- [ ] Complete Google Drive and OneDrive connect, upload, remote verification, disconnect, and expired-token refresh on the production-signed iOS and Android builds.
- [ ] Verify upload progress, retry/cancel, Wi-Fi-only behavior, process-kill resume, offline recovery, quota-full error, duplicate name handling, and iCloud-only source behavior.
- [ ] On a large real library, verify All/Photos/Videos/Backed Up filters, portrait aspect-fit, photo paging/zoom, video auto-play/fullscreen, sharing, immediate delete behavior, Similar Photos grouping, manual keep of more than one photo, and Keep Recommended.
- [ ] Test limited/full/denied photo permission and the system deletion/trash behavior on physical iPhone, iPad, Android phone, and Android tablet.
- [ ] Measure first scan time, memory, battery, heat, and stability with 10,000+ mixed assets.
- [ ] Native-speaker and legal review of store copy, subscription disclosure, Privacy Policy, and Terms.

## Release decision

- **Repository technical gate:** pass.
- **iOS submission gate:** not ready to resubmit until the physical-device video and reviewer response are attached.
- **Android internal-testing gate:** pass.
- **Android production gate:** not ready; the Alpha submission is under review, the pre-launch report has not generated, production cloud/purchase tests are pending, and the 12-testers/14-days requirement has not started with verified opt-ins.

Confidence can reasonably exceed 85% only after every blocking gate above has recorded evidence. Store approval itself cannot be guaranteed.
