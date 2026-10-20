# AI将棋検討: store preparation evidence

Status checked on 2026-10-20 (Japan time). Package/bundle: `com.presentsoft.shogi3minutes`.

## Uploaded binaries

- Android 1.15.0 (23), target SDK 36, signed APK/AAB: [download](https://github.com/555734/shogiKento/releases/tag/v1.15.0-android-test-23).
- Android source: `9f9aa35d811cdd62e8bd5dba6e65bad07b6d2a62`. [CI and signing verification passed](https://github.com/555734/shogiKento/actions/runs/39363655253). AAB accepted into the Google Play internal-test draft. No testers or rollout have been configured.
- iOS 1.15.0 (23), Xcode 26.3: [signed upload succeeded](https://github.com/555734/Kiki-public/actions/runs/39367783753). Source: `e4d7616` (the full revision is recorded in that run's `shogi_commit` input). Apple build `7f022670-5d16-4941-bf36-27cdb3319385` reached `VALID` and was selected for version 1.15.0.
- The dedicated workflow in `555734/Kiki-public` uses the existing signing configuration inside its runner, following explicit user approval. It builds only this Shogi app. It does not export credentials or change the Kiki app.

## Store assets and declarations

- Google Play: Japanese listing, 512 px icon, 1024×500 feature graphic and five actual Android screenshots saved. IARC questionnaire saved, with in-app purchases declared.
- App Store: four screenshots each for medium iPhone, large iPhone, and 13-inch iPad. Required-size capture [passed](https://github.com/555734/shogiKento/actions/runs/39366311136). Required-size assets reached `PREPARE_FOR_SUBMISSION`, with placements `ACTIVE`.
- Apple privacy labels published after explicit approval: approximate location, device ID, product interaction, advertising data, crash and performance data. No cross-app tracking is requested by this build.
- Apple non-consumable `coffee_support_unlock`, Japan ¥200: review image processed `COMPLETE`; product reached `READY_TO_SUBMIT`. The private review notes explicitly identify the screenshot as a local UI-test capture, not a completed purchase.
- Google Data Safety answers are saved as a draft. Finalization depends on target-audience setup, which in turn requires complete reviewer-access instructions for the paid features.

## Verification boundaries and remaining work

- Actual iOS application build, normal UI flows and fresh iPhone/iPad screenshot flows passed in [run 39365244309](https://github.com/555734/shogiKento/actions/runs/39365244309).
- **The full iOS test run failed:** `CoffeeTests.testStoreKitPurchaseRestoreRefundAndLevelGate` timed out after 180 seconds at the StoreKit 2 purchase path. The cause remains unresolved. Fixture-based UI purchase/restore tests are not evidence that StoreKit checkout or restoration works.
- Validate purchase, restart, restore and refund in Google Play testing and Apple Sandbox/TestFlight on actual devices before review submission. No real-device checkout has been certified.
- Google Play product creation did not complete through the Console. The explicitly approved existing service account returned HTTP 403 for this app. Any additional app-scoped permission must be separately approved, and any temporary grant removed after product registration.
- Provide legitimate reviewer access to the paid level/advertisement removal before completing Play app-access, audience and Data Safety declarations. Do not declare every feature unrestricted while payment is required.
- Ads still use Google's official sample IDs. Revenue-producing AdMob units and regional consent configuration remain separate release work. Child-directed treatment and G-rated ad limits are enabled.
- Neither store version has been submitted for public review or released to production. A successful upload does not establish purchase acceptance, review approval or availability in every jurisdiction.

Website: [developer site](https://555734.github.io/), [privacy](https://555734.github.io/privacy.html), [support](https://555734.github.io/support.html), [app-ads.txt](https://555734.github.io/app-ads.txt).

Keep signing keys, API credentials, local store reports and private reviewer contacts out of Git. Local operational evidence is under the ignored `ios/build/store-registration/` directory.
