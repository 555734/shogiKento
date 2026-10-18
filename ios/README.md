# 紅将棋 iOS（1.12.0 / 19）

SwiftUI / iOS 16以上。Android版と同じ赤・金のホーム、木の盤面と駒、元の着手音、やねうら王9.80git＋つようら王11microを使います。対局・合法手の色表示・選択解除と切替・成り・持ち駒・3分切れ負け・投了・千日手、検討の全手順移動・評価グラフ・継続AI解析、平手KIF取り込み、端末内の過去の棋譜、音量・SE・振動設定に対応します。

## iOSへの移植

- 将棋の合法手判定と局面更新は、固定した同じエンジンのPosition / LEGAL_ALLを使用します。Swift側に別の簡易ルールや弱い代替AIは作りません。AIはiOSで外部プロセスを起動せず、C++静的ライブラリをアプリへリンクし、バックグラウンドスレッドとメモリー内USIストリームで呼び出します。端末・Simulatorを別々にコンパイルします。
- 対局CPUは最大750ms、検討はgo infinite / MultiPV最大3候補です。画面変更・局面変更・アプリが非アクティブになった時に停止し、古い探索結果を破棄します。対局時計も背景で停止します。詰みでは終局評価を表示します。
- KIFは提示されたShogi Questの79手に対応し、全角数字・空白・同・成・不成・打・移動元・時間欄を処理します。全80局面は独立したpython-shogiパーサーの結果と照合します。
- 承認した取り込みと対局をApplication Supportへ原子的に保存します。アーカイブは検討用の複製と独立し、進行中の対局も残します。iCloudバックアップ対象から除外し、外部ストレージ権限・通信・端末間同期を使いません。アプリ削除で棋譜も削除されます。
- 音量・SE・振動をUserDefaultsへ保存します。iOSの消音・音量・ハプティクス設定にも従います。実機での音・振動の体感はSimulatorのAPI検証とは区別します。

## クリップボードの相違

iOS版はホームの「棋譜を貼り付け」→OS標準PasteButton→「棋譜を読み込む」で取り込みます。任意のクリップボード本文を起動・復帰のたびに自動読み取りしません。KIFかどうかは本文を読むまで判定できず、iOSの許可要求を無関係なコピーにも出してしまうためです。貼り付け後、読み込みと既存の検討を置き換える確認を行います。キャンセル・失敗では既存の検討と保存済み棋譜を維持します。明示的に再度貼り付けた時は再確認できます。

[Apple PasteButton](https://developer.apple.com/documentation/swiftui/pastebutton) / [UIPasteControl](https://developer.apple.com/documentation/uikit/uipastecontrol)

## ビルド

`.github/workflows/ios.yml` をworkflow_dispatchで実行します。macOS / Xcode / Python 3.12 / XcodeGenを使用し、Appleの署名情報を要求せずに端末向けarm64のRelease ArchiveとSimulator版を作ります。

```sh
python -m pip install py7zr==1.1.4 python-shogi==1.1.1 Pillow==11.3.0
python native-three-minute/scripts/prepare_yaneura.py
python ios/scripts/prepare.py
python ios/scripts/build_engine.py --sdk iphoneos
python ios/scripts/build_engine.py --sdk iphonesimulator
cd ios
xcodegen generate
xcodebuild -project BeniShogi.xcodeproj -scheme BeniShogi -configuration Release -destination 'generic/platform=iOS' -archivePath build/BeniShogi.xcarchive CODE_SIGNING_ALLOWED=NO archive
```

ActionsはSimulatorで実NNUE・合法手・KIF全80局面・保存・時計を単体テストし、ホーム・設定保持・実CPU応手・標準貼り付け・最終詰み・解析・手順移動・再起動後の一覧をUIテストします。実行成否は各runで確認してください。初回追加時のコードだけで全項目が検証済みとは扱いません。

## 成果物と署名の境界

- `BeniShogi-unsigned.ipa` / `BeniShogi-unsigned.xcarchive.zip`：端末向けarm64のビルド。Appleの配布証明書・プロビジョニングによる署名はありません。iPhoneへそのままインストールできる配布IPA・TestFlight公開ではありません。
- `BeniShogi-simulator.app.zip`：Simulator専用。実機にはインストールできません。
- `BeniShogi-complete-source.zip`：対応するGPLエンジンの固定ソース、MITモデル・ライセンス、iOSソースとブリッジ、素材・ビルド手順。
- Actions Artifact：上記に加えてxcresult・テスト添付画像・ビルドログ。永続ダウンロードは`build/ios-<run ID>`ブランチの`ios/outputs`。

実機配布やApp Store Connectへの提出には、別途Apple Developerのチーム・署名・プロビジョニングの設定が必要です。このワークフローはストア提出・TestFlightアップロードを行いません。

[Apple: Distributing your app](https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases)

## ライセンス

静的リンクするエンジンはGPL-3.0です。対応するエンジンとブリッジを含むiOS版のソースをGPL-3.0で提供します。つようら王11microの評価関数はMITで、権利表記をアプリと対応ソースへ同梱します。既存の画像・元の効果音・生成素材はこのプロジェクト用の素材です。エンジンのピンとSHA256はAndroid側のprepare_yaneura.py、iOSの実コンパイルフラグとソース一覧は各ビルドのengine/*/build.jsonで確認できます。
