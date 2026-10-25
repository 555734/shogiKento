## オンライン対戦（P2P・サーバー不要・Android版と対戦可）

ホームの「オンライン対戦」から「ランダム対戦」「ルームを作る（6桁コード）」「ルームに参加」で、人と3分切れ負けで対局できます。仕組みはAndroid版と同じで、メッセージ形式・暗号・時計の規則まで一致させているため、iPhoneとAndroidの利用者が同じロビーでマッチングし、そのまま対局できます。登録・課金・運用サーバーは不要です（仕組みの詳細は[Android版の説明](../native-three-minute/README.md#オンライン対戦p2pサーバー不要)）。

- 実装は `Sources/Online/`（Swift）: NIP-01のJSON直列化、secp256k1/BIP-340署名、公開Nostrリレー8か所への同時接続（URLSessionWebSocketTask・TLS）、デュアルスタックUDPとSTUN、P-256 ECDH＋HKDF＋AES-GCM（CryptoKit）で暗号化したP2Pリンクとリレー経由の自動切替、募集→申込→承諾→確定のマッチング、遅延補正付きの時計。合法手・千日手・連続王手の千日手・詰みの判定は内蔵エンジン（`EngineRules`）で行い、相手の指し手も全て自分側で検証します。
- iOSはLAN内のアドレスへ送信すると「ローカルネットワーク」の許可を求めるため、オンライン対戦はLANアドレスを使いません（同じWi-Fiの相手とも公開アドレス・IPv6・リレーで接続します）。許可ダイアログは出ません。
- アカウントとレーティング: Android版と同じ仕組みです（[説明](../native-three-minute/README.md#アカウントとレーティング)）。ロビー上部のカードから「アカウント」画面を開き、名前の変更、レートの推移グラフ、最近のレート戦、公開プロフィールのランキング、プロフィール公開の切替、引き継ぎコード（nsec）の表示と復元ができます。秘密鍵はKeychainに保存します。ランダム対戦がレート戦（Elo・初期1500）、ルームはレート変動なし。対局中は相手の名前・レート・対戦成績を、終局後にレートの変化を表示します。iPhoneとAndroidの間でも、署名付きカードの検証と同じ計算式でレートが一致します。
- オンライン対局中はアプリを離れても時計は止まりません。iOSはバックグラウンドで通信を止めるため、離れている間に時間切れになることがあります。退出は投了扱いです。対局の盤の下には対局モードと同じ広告枠（match）を表示し、コーヒー応援の案内はオンライン画面では出しません。
- `Info.plist` の `NSAllowsLocalNetworking` は、テストで127.0.0.1のリレー（ws://）へ接続するためです。公開リレーには常にwss://で接続します。
- 通信内容（指し手）はAES-GCMで暗号化します（CryptoKitの標準アルゴリズム）。App Store Connectの輸出コンプライアンス（`ITSAppUsesNonExemptEncryption`）の申告内容は、提出前に確認してください。

検証:
- `scripts/online_interop.sh`（macOS・Linux）: `HostTests/` のSwiftパッケージが `Sources/Online` をそのままビルドし、BIP-340公式ベクター19件とNIP-01のID、仮想時間での時計・時間切れ・猶予・不正手・再戦・千日手、30%のUDP損失下での順序保証・改ざん/リプレイ拒否・UDP断からの復帰を確認したうえで、Android版のJavaクライアント（`native-three-minute/tests/interop/NetplayBot.java`）とローカルリレー越しに完全な対局を行います: ランダム対戦（ループバックUDP直結）40手・投了・先後を入れ替えた再戦・退出、ネットワーク越しの千日手（両端末で「千日手・引き分け」）、UDP遮断時のリレーのみの対局、両方向のルーム対戦、iPhone同士の対局。Swift側のルールにはAndroidのルール（`RulesOracle.java`）を使い、毎手、両端末の局面（SFEN）が一致することを確認します。アカウントカードの相互検証、両端末でのレート更新の一致（投了・相手の退出）、各局でのカード更新、一方のOSが公開したプロフィールを他方が読めること、レート差の大きい相手とは最初は組まれないことも確認します。Linux上の実測: 直結の指し手到達が中央値4〜7ms、ローカルリレーのみで43〜60ms（510チェック）。
- iOS Simulator（GitHub Actions）: CIがMac上でローカルリレーとAndroid版の自動対局ボットを起動し、`OnlineTests` がアプリのネットワークコードと内蔵エンジンのルールでボットと最大30手の対局・再戦を行い、Androidの指し手が全てiOSのルールでも合法であること、Androidのアカウントカードの検証とレート戦の結果を確認します。Keychainへの保存、途中終了の負け扱い、結果の一回だけの確定、不成立の扱いも単体テストで確認します。`FlowTests.testOnlineMatchAgainstAndroidBot` はUI操作でアカウント名の変更→ランダム対戦→着手→投了→レート変化の表示までを行い、スクリーンショット20〜23を保存します。実機同士・キャリア回線間の直結率と遅延は未検証です。

## v1.15.0 コーヒー応援の買い切り購入

広告除去・対局AI Lv.10解放・購入復元と、2回目以降の起動案内／非表示設定を追加しました。商品IDは `coffee_support_unlock`、日本の設定予定価格は200円です。ストア商品は未登録で、通常起動では準備中です。ストアでの登録と検証公開鍵の設定など、本番準備と検証範囲は [`docs/coffee-purchase.md`](../docs/coffee-purchase.md) を参照してください。

## v1.14.0 テスト広告版

Googleの公式テスト広告をホーム・対局・検討・過去の棋譜の末尾へ追加しました。検討の盤面・駒の寸法を保ち、広告へはスクロールして到達します。広告は本番配信されず、収益は発生しません。詳細は [`docs/test-ads.md`](../docs/test-ads.md) を参照してください。

# 紅将棋 iOS（1.13.0 / 20）

SwiftUI / iOS 16以上。Android版と同じ赤・金のホーム、木の盤面と駒、元の着手音、やねうら王9.80git＋つようら王11microを使います。対局・合法手の色表示・選択解除と切替・成り・持ち駒・1・3・5・10・15・30分の切れ負け・投了・千日手、検討の全手順移動・評価グラフ・継続AI解析、平手KIF取り込み、端末内の過去の棋譜、音量・SE・振動設定に対応します。

## iOSへの移植

- 将棋の合法手判定と局面更新は、固定した同じエンジンのPosition / LEGAL_ALLを使用します。Swift側に別の簡易ルールや弱い代替AIは作りません。AIはiOSで外部プロセスを起動せず、C++静的ライブラリをアプリへリンクし、バックグラウンドスレッドとメモリー内USIストリームで呼び出します。端末・Simulatorを別々にコンパイルします。
- 対局設定であなたの先手／後手とAIの強さLv.1〜10を指定します。後手では盤面を反転し、CPUが初手を指します。AIの探索量は100〜250,000ノード、最大時間は50〜2,000ms、検討はgo infinite / MultiPV最大3候補です。画面変更・局面変更・アプリが非アクティブになった時に停止し、古い探索結果を破棄します。対局時計も背景で停止します。詰みでは終局評価を表示します。
- KIFは提示されたShogi Questの79手に対応し、全角数字・空白・同・成・不成・打・移動元・時間欄を処理します。全80局面は独立したpython-shogiパーサーの結果と照合します。
- 承認した取り込みと対局をApplication Supportへ原子的に保存します。アーカイブは検討用の複製と独立し、進行中の対局も残します。iCloudバックアップ対象から除外し、外部ストレージ権限・通信・端末間同期を使いません。アプリ削除で棋譜も削除されます。
- 対局設定を現在の棋譜と次回用のUserDefaultsへ保存します。変更を適用すると、対局中の棋譜を保存して新しい盤面にします。キャンセルでは現在の対局を保持します。設定画面では時計を止めます。
- 検討の前後移動・初期／最終・手順一覧・グラフからの局面移動でもSEを鳴らします。同じ局面・移動失敗・背景では鳴らさず、音量0とSE OFFを守ります。再生では振動を追加しません。
- 音量・SE・振動をUserDefaultsへ保存します。iOSの消音・音量・ハプティクス設定にも従います。実機での音・振動の体感はSimulatorのAPI検証とは区別します。

## クリップボードの相違

iOS版はホームの「棋譜を貼り付け」→OS標準PasteButton→「棋譜を読み込む」で取り込みます。任意のクリップボード本文を起動・復帰のたびに自動読み取りしません。KIFかどうかは本文を読むまで判定できず、iOSの許可要求を無関係なコピーにも出してしまうためです。貼り付け後、読み込みと既存の検討を置き換える確認を行います。キャンセル・失敗では既存の検討と保存済み棋譜を維持します。明示的に再度貼り付けた時は再確認できます。

[Apple PasteButton](https://developer.apple.com/documentation/swiftui/pastebutton) / [UIPasteControl](https://developer.apple.com/documentation/uikit/uipastecontrol)

## ビルド

初回追加はドラフトPRのチェックとして`.github/workflows/ios.yml`を実行します。ワークフローが既定ブランチへ入った後はworkflow_dispatchでも実行できます。macOS / Xcode / Python 3.12 / XcodeGenを使用し、Appleの署名情報を要求せずに端末向けarm64のReleaseビルドとSimulator版を作ります。

```sh
python -m pip install py7zr==1.1.4 python-shogi==1.1.1 Pillow==11.3.0
python native-three-minute/scripts/prepare_yaneura.py
python ios/scripts/prepare.py
python ios/scripts/build_engine.py --sdk iphoneos
python ios/scripts/build_engine.py --sdk iphonesimulator
cd ios
xcodegen generate
xcodebuild -project BeniShogi.xcodeproj -scheme BeniShogi -configuration Release -destination 'generic/platform=iOS' -derivedDataPath build/DeviceData CODE_SIGNING_ALLOWED=NO build
```

ActionsはSimulatorで実NNUE・合法手・KIF全80局面・保存・時計を単体テストし、ホーム・設定保持・実CPU応手・標準貼り付け・最終詰み・解析・手順移動・再起動後の一覧をUIテストします。実行成否は各runで確認してください。初回追加時のコードだけで全項目が検証済みとは扱いません。

## 成果物と署名の境界

- `BeniShogi-unsigned.ipa`：端末向けarm64のビルド。Appleの配布証明書・プロビジョニングによる署名はありません。iPhoneへそのままインストールできる配布IPA・TestFlight公開ではありません。
- `BeniShogi-simulator.app.zip`：Simulator専用。実機にはインストールできません。
- `BeniShogi-complete-source.zip`：対応するGPLエンジンの固定ソース、MITモデル・ライセンス、iOSソースとブリッジ、素材・ビルド手順。
- Actions Artifact：上記に加えてxcresult・テスト添付画像・ビルドログ。永続ダウンロードは`build/ios-<run ID>`ブランチの`ios/outputs`。

実機配布やApp Store Connectへの提出には、別途Apple Developerのチーム・署名・プロビジョニングの設定が必要です。このワークフローはストア提出・TestFlightアップロードを行いません。

[Apple: Distributing your app](https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases)

## ライセンス

静的リンクするエンジンはGPL-3.0です。対応するエンジンとブリッジを含むiOS版のソースをGPL-3.0で提供します。つようら王11microの評価関数はMITで、権利表記をアプリと対応ソースへ同梱します。既存の画像・元の効果音・生成素材はこのプロジェクト用の素材です。エンジンのピンとSHA256はAndroid側のprepare_yaneura.py、iOSの実コンパイルフラグとソース一覧は各ビルドのengine/*/build.jsonで確認できます。
