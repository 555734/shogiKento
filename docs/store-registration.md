# AI将棋検討: store preparation evidence

Status checked on 2026-10-20 (Japan time). Package/bundle: `com.presentsoft.shogi3minutes`.

## Uploaded binaries

- Android 1.15.0 (23), target SDK 36, signed APK/AAB: [download](https://github.com/555734/shogiKento/releases/tag/v1.15.0-android-test-23).
- Android source: `9f9aa35d811cdd62e8bd5dba6e65bad07b6d2a62`. [CI and signing verification passed](https://github.com/555734/shogiKento/actions/runs/39363655253). Google Play published internal-test release 23 (1.15.0) on October 20 at 17:36 JST. A dedicated list containing only the owner's account is selected; the track reports active. [Internal test opt-in](https://play.google.com/apps/internaltest/4700885711211443340) requires that eligible account. This is not a production release, and device installation/purchases have not been verified.
- iOS 1.15.0 (23), Xcode 26.3: [signed upload succeeded](https://github.com/555734/Kiki-public/actions/runs/39367783753). Source: `e4d7616` (the full revision is recorded in that run's `shogi_commit` input). Apple build `7f022670-5d16-4941-bf36-27cdb3319385` reached `VALID` and was selected for version 1.15.0.
- The dedicated workflow in `555734/Kiki-public` uses the existing signing configuration inside its runner, following explicit user approval. It builds only this Shogi app. It does not export credentials or change the Kiki app.

## Store assets and declarations

- Google Play: Japanese listing, 512 px icon, 1024×500 feature graphic and five actual Android screenshots saved. IARC questionnaire saved, with in-app purchases declared.
- Google Play audience includes all six age groups, following the owner's explicit child-compliance declaration approval. Optional Teacher Approved enrollment is off. The advertising-ID declaration is "not used": source removes `com.google.android.gms.permission.AD_ID`, the signed APK's permission dump confirms its absence, and all ad requests are child-directed. AdServices SDK permissions are still present in the merged APK; this is not a claim that the SDK collects no device data.
- App Store: four screenshots each for medium iPhone, large iPhone, and 13-inch iPad. Required-size capture [passed](https://github.com/555734/shogiKento/actions/runs/39366311136). Required-size assets reached `PREPARE_FOR_SUBMISSION`, with placements `ACTIVE`.
- Apple privacy labels published after explicit approval: approximate location, device ID, product interaction, advertising data, crash and performance data. No cross-app tracking is requested by this build.
- Apple non-consumable `coffee_support_unlock`, Japan ¥200: review image processed `COMPLETE`; product reached `READY_TO_SUBMIT`. The private review notes explicitly identify the screenshot as a local UI-test capture, not a completed purchase.
- Google Play `coffee_support_unlock`, purchase option `permanent`: created through Console after the internal release was saved. API readback verified `ACTIVE`, Japan JPY 200, and 174 available regions (`regionsVersion` 2026/01). This verifies product configuration, not checkout or entitlement restoration.
- The explicitly approved temporary service-account app grant was removed after verification. Its original access to the existing Taiman Akinator app remains at 13 permissions; no account-wide permissions were added.
- With explicit approval of Google's promotion terms, 20 single-use reviewer codes were generated for the paid product, scheduled October 21 through December 31. Five private reviewer instruction sets explain free redemption and restoration; optional partner experience-testing sharing is disabled. Keep codes out of Git and public reports. The redemption flow still needs device validation before public review.
- Google Data Safety answers are saved for review: six SDK data types (approximate location, app interactions, device/other IDs, crash logs, diagnostics, other performance data), collection/sharing disclosed, transport encryption, no app account creation, and no developer-operated remote deletion request. No optional marketing badge is claimed. The Console confirms the changes are saved; this is not approval or publication.
- The app-content overview now has no outstanding declarations. Store review and production release remain unsent.

## Verification boundaries and remaining work

- Actual iOS application build, normal UI flows and fresh iPhone/iPad screenshot flows passed in [run 39365244309](https://github.com/555734/shogiKento/actions/runs/39365244309).
- **The full iOS test run failed:** `CoffeeTests.testStoreKitPurchaseRestoreRefundAndLevelGate` timed out after 180 seconds at the StoreKit 2 purchase path. The cause remains unresolved. Fixture-based UI purchase/restore tests are not evidence that StoreKit checkout or restoration works.
- Validate purchase, restart, restore and refund in Google Play testing and Apple Sandbox/TestFlight on actual devices before review submission. No real-device checkout has been certified.
- Google Play API write attempts returned HTTP 403 with the minimal approved app permissions. Product registration instead completed through the owner Console session; no broader service-account grant was added.
- Reviewer access is declared as restricted, with the official free-code instructions rather than an unrestricted-access declaration. Confirm the codes' active period before submission and replenish consumed codes if needed.
- Ads still use Google's official sample IDs. Revenue-producing AdMob units and regional consent configuration remain separate release work. Child-directed treatment and G-rated ad limits are enabled.
- Neither store version has been submitted for public review or released to production. A successful upload does not establish purchase acceptance, review approval or availability in every jurisdiction.

Website: [developer site](https://555734.github.io/), [privacy](https://555734.github.io/privacy.html), [support](https://555734.github.io/support.html), [app-ads.txt](https://555734.github.io/app-ads.txt).

Keep signing keys, API credentials, local store reports and private reviewer contacts out of Git. Local operational evidence is under the ignored `ios/build/store-registration/` directory.
