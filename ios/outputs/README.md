# 紅将棋 iOS build

`BeniShogi-unsigned.ipa` / `BeniShogi-unsigned.xcarchive.zip` は端末向けarm64ビルドです。Appleの配布証明書・プロビジョニングによる署名は含みません。そのままiPhoneへインストールする配布物やTestFlight公開ではありません。

`BeniShogi-simulator.app.zip` はSimulator用です。実機へはインストールできません。

`BeniShogi-complete-source.zip` は固定したGPL-3.0エンジン、MIT評価関数・ライセンス、iOSアプリ・ブリッジ・ビルド手順を含む対応ソースです。

ビルドしたソースSHA、各成果物の状態は `build-info.json`、実行結果はActionsとテストログ・xcresultで確認してください。失敗したrunの成果物を検証済みとして扱わないでください。
