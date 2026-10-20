#!/usr/bin/env bash
# Uses approved, app-scoped CI credentials. Never writes signing material to outputs.
set -euo pipefail
cd "$(dirname "$0")/.."
keychain initialize
app-store-connect fetch-signing-files com.presentsoft.shogi3minutes \
  --platform IOS --type IOS_APP_STORE --strict-match-identifier \
  --certificate-key '@env:CERTIFICATE_PRIVATE_KEY' --create
keychain add-certificates
xcode-project use-profiles --project BeniShogi.xcodeproj
xcode-project build-ipa --project BeniShogi.xcodeproj --scheme BeniShogi \
  --config Release --archive-directory build/AppStoreArchive \
  --ipa-directory build/AppStoreIPA --disable-xcpretty \
  --archive-xcargs 'CODE_SIGNING_ALLOWED=YES'
ipa=$(find build/AppStoreIPA -name '*.ipa' -print -quit)
test -n "$ipa"
app-store-connect publish --path "$ipa" --enable-package-validation
cp "$ipa" outputs/AI-Shogi-Kento-AppStore-1.16.0-24.ipa
