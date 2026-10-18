# Native shogi v1.8.4 — reference layout

This version adopts the supplied reference's broad golden pieces, thin amber board frame, compact player/clock/hand strips, dark analysis panel, blue/red percentage display and cyan score-history line. The existing red/gold Japanese background is retained byte-for-byte. Promotion selection remains available for both match and study modes.

The board uses 98% of screen width for its grid. Cells follow the new generated board's natural 1283:1226 aspect (~1.047 height/width), and every piece shares a normalized visible height (~97% of cell height), retaining its own raster aspect. All eight study hand types fit beside the player information. Short screens scroll study content instead of shrinking the board.

Generated asset specifications, prompts, lossless conversion and hashes: `GENERATED_ASSETS.md`. CI verifies all original asset hashes, both generated generations, actual native-engine searches, promotion dialogs, compact hand hit areas, clock behavior, real screenshots and APK installation. Physical Motorola g55 testing remains unavailable.

# 紅将棋3分 / Native Android v1.8.0

Java Activity/Canvas＋独立したネイティブUSIプロセス。HTML・WebView・ネット接続不要。CPUはやねうら王9.80git（2026-09-14固定ソース）＋つようら王11micro（2026-10-14公開、MIT評価パラメーター）。nn.bin＋progress.binは合計4,867,714 bytes。miniとの同条件のノード比較と選定根拠はAI_COMPARISON.md。同じmicroで3,000/10,000/50,000ノードを選択でき、初期値50,000、1スレッド、Hash16MiB、上限750ms、先読みなし。旧Suisho5はAPKに含めず、更新時にprivate領域の旧64.2MBコピーも削除します。保存済みの対局・検討局面は維持します。

駒の移動・駒取り・持ち駒の打ち込みが成立した時に、短い木製の着手音を鳴らします。自分・CPU・検討の双方に対応。選択だけ、非合法手、手戻し、背景中は鳴らしません。音は本アプリ用のオリジナル合成WAV（120ms、24kHz mono、5,804 bytes）をSoundPoolで事前読み込みし、端末のメディア音量で再生します。外部音源や追加権限は不要。再生成はscripts/generate_move_sound.py。

双方3分切れ負け。バックグラウンドでは双方の時計を停止し復帰時に手番側だけ再開。合法手・王手放置禁止・駒取り/打ち込み・二歩/行き所のない駒/打ち歩詰め禁止・任意/強制成り・詰み/投了/時間切れ・千日手/連続王手・やり直しを実装。AIの着手もJavaの合法手と照合。

起動時に対局/検討モードを選択します。対局は従来の3分切れ負けCPU戦です。検討は時計・CPUの自動着手・交互手番を止めた自由配置で、先後どちらの駒も任意の空きマス/相手駒へ動かせます。二歩・王手・駒本来の動き等の制約は検討では適用しません。両側の持ち駒の打ち込み、長押しで成り/不成り、300手までの一手戻す、初期配置を用意。対局と検討の盤面・持ち時間・履歴は別々に保存し、モード間の移動は上部「モード」またはAndroidの戻る操作で行います。コールド起動では選択画面へ戻り、背景からの復帰は選択中のモードを維持します。

検討画面の盤下にAI候補を最大3手と各評価値を表示します。初期配置、移動、駒取り、打ち込み、成り変更、一手戻す、リセット、復帰のたびに自動更新。MultiPV=3、1スレッド、設定ノード数（初期50,000）、最大750msで、局面が変わったら前の結果を消して中断します。同じ深さの完了済み探索から異なる候補だけを採用し、全候補をJavaの合法手と照合します。正の値は先手有利、負は後手有利。通常値はUSI cp（歩100）、詰みは「詰み ±手数」、上下限は≥/≤を保持。自由編集後は動かした駒の相手側を解析し、パネル右上「解析：先手/後手」をタップして任意に切替可能です。選択側は保存します。解析は駒を動かさず、時計も使用しません。

自由配置自体は制限しませんが、双方の玉がない、二歩、行き所のない駒、駒数超過、非手番側が王手されているなど通常将棋の解析が成立しない局面には理由を表示し、評価値を捏造しません。王手の局面では王手された側を選んでください。合法手がない局面は詰み/合法手なしを表示します。解析パネルは持ち駒の下。検討のみ盤を上へ移してパネルの領域を確保し、従来の全幅・縦長の盤と駒を維持します。

提供素材は実際にはPNG37枚＋README等の38ファイル。元PNGのバイトをすべて保持し、asset_manifest.json/asset_inventory.csvにサイズ・透過・用途・ハッシュを記載。背景・盤・時計・持ち駒パネル・ハイライトは元の画像を使用します。v1.5では画像生成した縦長の駒PNGアトラスを追加し、盤上・持ち駒の通常駒と赤文字の成駒をそのアトラスから描きます。generated/pieces_v15.jsonに各駒の切り出し座標・縦横比・ハッシュを記録。元の駒PNGと参考シートは削除しません。生成方法はGENERATED_ASSETS.md。

盤面は横:縦=1:1.20、9×9のマスは縦が横の約1.204倍。元の盤PNGの透明外周と太い枠の一部を除き、木目・盤線・星の画像を分割配置します。1080×2400等の縦画面では実マス領域が画面幅の98%。前版の基準幅400で392×392だったマス領域を392×472へ拡大し、高さ・面積は約20.4%増。全駒共通の本体高さは42.56→51.04（約19.9%増）です。新しい駒本体は縦/横1.319〜1.390で、画像自体を縦横別倍率に伸ばさず同じ高さへ揃えます。時計パネルは元の比率のまま小さくし、上下の持ち駒パネルもコンパクトにします。短い画面では盤面全体を縮小して収め、縦横比は維持します。7種類の画面寸法でマス・駒・時計・ラベルとタップ座標の収まりを検証します。

## ビルド

Java17、Gradle8.9、Android SDK35、NDK27.2.12479018。GitHub Actionsは.github/workflows/native-shogi.yml。arm64-v8a/x86_64を収録。

0. python -m pip install py7zr==1.1.4
1. python scripts/prepare_yaneura.py
2. python scripts/build_yaneura.py --ndk "$ANDROID_HOME/ndk/27.2.12479018"
3. python scripts/prepare_ndk_notices.py "$ANDROID_HOME/ndk/27.2.12479018"
4. base64 -d debug.keystore.b64 > debug.keystore
5. gradle wrapper --gradle-version 8.9
6. ./gradlew assembleDebug assembleDebugAndroidTest

APKはapp/build/outputs/apk/debug/app-debug.apk。公開テスト鍵を同梱（password android、alias androiddebugkey）。前版と同じ署名でversionCode9。本番の秘密鍵ではありません。完全ソースZIPは固定GPLエンジンの全ソース・MIT評価パラメーター2個・権利表記・全素材・音の生成コード・テスト・ビルド手順を含みます。モデルハッシュはapp/src/main/assets/ai/provenance.json。

## 検証

ルール/時計のアサーション、python-shogiとの640局面の合法手集合照合、372件の自由配置テスト、Androidでの40探索、起動時選択・両側の任意移動・捕獲・両側の持ち駒打ち・長押しで成り/不成り・一手戻す・再起動復元・対局との分離・モード切替中のAI停止、通常対局の移動/CPU/成り/持ち駒/時間切れ/復帰を検証します。フレーム描画後の実行画像をoutputs/に保存します。今回の新AIについて、Android上の40探索・合法手・時間/ノード上限・キャンセル・nn.bin/progress.binの読み込み・ハッシュ・旧データの削除を検証します。着手音のSoundPool読み込みと再生受理回数、移動時だけ鳴ること、背景中の停止をAndroid上で確認します。エミュレーターは音声出力を無効にしており、実スピーカーでの聴感・音量・遅延は未検証です。Motorola g55実機の速度・発熱・電池消費も未検証です。

検討解析のUSIパーサー/先手基準の符号/詰み/上下限/同一深さの候補/不正配置をAnalysisTest.javaで検証し、Androidでは実際のmicro候補3手、全候補の合法性、編集各操作・先後切替・再開による更新、旧局面の破棄、両モード7画面寸法を確認します。


## v1.8: 形勢バーと評価値グラフ

検討の主候補の評価を青（先手）・赤（後手）のバーと双方の割合で表示します。「形勢の目安」であり、microやユーザーの実戦勝率を実測・較正した値ではありません。換算は先手cpをvとしてp=1/(1+exp(-v/600))、後手は1-p。通常評価では0.1〜99.9%へ表示を制限し、詰みだけ0/100%とします。小数1桁を先手側で丸め、後手は残りなので合計は常に100.0%。USI上下限は先後換算とともに反転して≥/≤を保持します。換算の参照は、やねうら王公式『機械学習エンジニアのための将棋AI開発入門その1』（2020-05-03）。この定数はmicro実測の推定ではありません。公式『教師生成時のdepth問題に関する考察』（2020-02-06、2023年コメント）でも定数の調整が必要と説明されています。

推移グラフは先手基準、縦軸±2000（範囲外と詰みは端に表示）、横軸は自由配置の「編集回数」。最大300編集を保存し、最新60編集を表示。各点はその局面で実際に得た主候補の評価だけを使用します。解析前・不正配置は欠損点で、前後を線で結びません。上下限を含む線は破線です。一手戻すとカーソルも戻り、そこで編集すると将来の古い枝を破棄します。モード切替・背景復帰・再起動で履歴を維持し、初期配置でリセット。v1.7以前の保存データは履歴がないだけで引き続き読めます。

盤面の全幅と縦長比率を保ったまま、検討だけ上へ配置して解析欄を拡張。短い画面はネイティブな上下スワイプとスクロールバーで下部グラフへ移動できます。スクロール中に駒は動かず、スクロール後もマス・持ち駒・長押し・解析側切替のタップ座標を補正します。対局モードの画面と3分時計は従来通りです。


## v1.8.1 AI error recovery

AI requests now check native process health before each search, replace failed processes,
and retry protocol failures once. A ready barrier drains stale output before the next
search. Closed processes cannot be reused while waiting for OS termination.
Study errors offer a native details/retry dialog; diagnostics include recent USI output
and process exit status where available. Nodes and 750ms search budgets are unchanged.
CI additionally runs the Android ARM64 bionic/NEON objects using QEMU, and runs the
full native UI/instrumentation suite on Android 15 (API 35). QEMU is not a real phone.
The user's particular device failure is not claimed reproduced unless the logs confirm it.


CI records optional QEMU diagnostics as UNVERIFIED on startup failure. They are not
counted as successful ARM phone tests. The Android 15 emulator has 4 GiB RAM; every
screenshot checks that the game's window owns focus, and real OS-routed touch
events must select and move a piece. Native engine objects may be restored only
from the SHA-pinned prior CI APK while the native build recipe is byte-identical;
the application is rebuilt and all runtime artwork/model checks run again.

Android 16 installation and engine-logic checks ran, but the emulator System UI ANR blocked acceptance of full foreground/UI verification. Final UI verification uses Android 15; actual Motorola/Android 16 operation remains unverified.



## v1.8.2: native crash compatibility

An actual phone reported native engine exit=139 after readyok. Its exact cause has not been reproduced on the available environment. The application now distinguishes native fatal exits (SIGSEGV/SIGILL/SIGABRT/SIGBUS) and automatically retries with a separately compiled scalar engine using the same GPL engine source, MIT micro parameters, MultiPV and search budgets. The compatibility choice persists across app restarts. Ordinary protocol errors, unknown EOF and SIGKILL do not change the backend. Both packaged engines use upstream USE_PTHREADS to reserve 8 MiB per search thread instead of relying on Android's small default. Only one engine process runs at once.

GitHub Actions recompiles all four executables, checks both ARM64 dynamic PIE files extracted from the APK using pinned Android 16 bionic under QEMU (Cortex-A53 and max CPU profiles), and validates three legal candidates per position. Android 15 UI instrumentation also injects an actual SIGSEGV into the child and verifies automatic scalar selection and legal analysis. QEMU is not physical Motorola execution; physical error resolution and performance remain unverified. Percentages remain score-derived guides.



## v1.8.3: promotion choice after moving

In study mode, moving any unpromoted pawn, lance, knight, silver, bishop or rook into, within, or out of that side's promotion zone now automatically opens a native popup with 成る / 成らない / キャンセル. Both sides are editable independently of the turn. The board, captured pieces, graph and placement sound update only after the choice. Move, capture and promotion are one Undo action. Free long-press promotion editing is retained. Drops, gold, king and already-promoted pieces do not prompt. In match mode, optional promotion uses the same popup, mandatory promotion remains automatic, and the clock continues during the choice.

The unchanged native engine binaries are reused from the SHA-pinned successful Actions run 32; the Java app is rebuilt and all APK checks, ARM64 dynamic-engine tests and Android UI tests run again.

