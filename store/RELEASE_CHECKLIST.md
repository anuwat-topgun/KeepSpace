# KeepSpace 1.1 release checklist

Repository release baseline updated September 28, 2026.

## Automated and repository checks

- [x] `cd ios && xcodegen && xcodebuild test -project SmartStorage.xcodeproj -scheme SmartStorage -destination 'platform=iOS Simulator,name=iPhone 17'` — 83 unit tests + 1 UI smoke test passed on September 28, 2026
- [x] OneDrive personal-account authority regression suite — 3 Cloud Backup tests passed on iPhone 17 simulator on September 29, 2026
- [ ] iPhone and iPad simulator smoke test, including launch screen, onboarding, limited/full access, dark mode, large text, landscape on iPad, and every primary tab.
- [x] `cd android && ./gradlew testDebugUnitTest assembleDebugAndroidTest lintRelease bundleRelease` — 76 unit tests, UI-test APK compile, lint, and unsigned release bundle passed on September 28, 2026
- [x] Android `testDebugUnitTest` passed again after the OneDrive `consumers` authority change on September 29, 2026
- [ ] Android 16 phone and tablet emulator smoke test, including predictive back, edge-to-edge insets, selected photos, themed icon, and notification permission.
- [ ] Inspect the iOS archive for `PrivacyInfo.xcprivacy`, launch storyboard, version `1.1.0 (2)`, bundle ID `com.keepspace.app`, OAuth callback URL, and distribution signing.
- [ ] Inspect the signed AAB with Play Console pre-launch report; verify version `1.1.0 (2)`, target API 36, package `com.keepspace.app`, OAuth redirect activity, and Play App Signing.

## Required owner/account actions

- [ ] Create and securely back up an Android upload keystore. Do not commit it. Build with `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` environment variables.
- [ ] Register/enroll the App Store distribution certificate and provisioning through the Apple Developer account; archive and upload build 1 to TestFlight.
- [ ] Enable Play App Signing and upload the signed `app-release.aab` to an internal testing track.
- [ ] Accept current Apple agreements, tax/banking details if applicable, age rating, export-compliance answer, content rights, and availability.
- [ ] Complete Google Play app access, ads, content rating, target audience, news, health, financial-features, government-app, and data-safety forms.
- [ ] Enter the privacy/support URLs and listing copy from this directory.
- [ ] Upload final screenshots: at least one required iPhone size plus iPad because the binary supports iPad; phone and tablet assets for Google Play.
- [ ] Complete `store/CLOUD_SETUP.md`: Google Android clients and SHA-1/signature hashes, provider consent/branding, least-privilege scopes, and production-client physical-device validation. Microsoft and Google iOS production clients are configured.
- [ ] Put temporary Google/Microsoft reviewer credentials in the private Store review field; never commit passwords or tokens.

## Physical-device release gates

- [ ] iPhone and iPad on iOS 17 and latest iOS: real library with at least 10,000 mixed assets, iCloud-only items, edited Live Photos, favorites, screenshots, and large videos.
- [ ] Calibrate and sign off iOS Similar Photos threshold on multiple real libraries; verify false-positive rate before submission.
- [ ] Validate Thai paper receipts and bank-transfer slips captured by a real iPhone; verify merchant, amount, date, and classification.
- [ ] Android phone and tablet on API 26/29/33/34/36: full, denied, and selected-photo permissions; MediaStore trash; compression; process death; rotation/multi-window.
- [ ] Measure first scan time, peak memory, battery, heat, and free-space behavior on lower-memory devices and with 10,000+ assets.
- [ ] Verify scheduled notification delivery and cold-launch deep link on a real iPhone and Android device.
- [ ] On iOS and Android physical devices, test connect/disconnect and expired-token refresh for both providers; verify a photo, screenshot, and receipt in the chosen folder.
- [ ] Kill and relaunch during upload; verify the persistent queue resumes/retries, Wi-Fi-only blocks cellular, cancel works, failures are actionable, and no local original is removed.
- [ ] Test provider errors: revoked grant, full quota, duplicate filename, offline/poor network, iCloud-only source, and a large image.

## Release decision

Do not submit production build 2 until every physical-device and OAuth gate above has an owner and recorded result. TestFlight and Play internal testing are the intended first distribution channels.
