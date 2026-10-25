# Shogi Kento

Native Android and iOS shogi with offline TsuyouraOu11micro analysis.

1.17.0（26）では、検討の手順を分岐ごとに保存します。「手順・分岐」から元の手順や別の枝へ戻り、その先も再生できます。現在のツリーはアプリ再起動後も復元します。[操作と検証](docs/study-variations.md)。

- Android: [native-three-minute](native-three-minute/README.md)（完全無料・登録不要のオンライン対戦：公開NostrリレーでマッチングしP2P UDPで直結、つながらない時はリレー経由。[仕組み](native-three-minute/README.md#オンライン対戦p2pサーバー不要)）
- iOS: [SwiftUI app, embedded engine and Actions](ios/README.md)

This repository contains the verified v1.8.4 artwork and source. Engine: GPL v3; evaluation parameters: MIT. The pinned engine source and full corresponding source archive are prepared by `native-three-minute/scripts/prepare_yaneura.py`. Prebuilt engine cache provenance and SHA-256 are in `native-three-minute/native-cache/manifest.json`.
