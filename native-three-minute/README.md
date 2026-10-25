## v1.15.0 コーヒー応援の買い切り購入

広告除去・対局AI Lv.10解放・購入復元と、2回目以降の起動案内／非表示設定を追加しました。商品IDは `coffee_support_unlock`、日本の設定予定価格は200円です。ストア商品は未登録で、通常起動では準備中です。ストアでの登録と検証公開鍵の設定など、本番準備と検証範囲は [`docs/coffee-purchase.md`](../docs/coffee-purchase.md) を参照してください。

## v1.14.0 テスト広告版

Googleの公式テスト広告をホーム・対局・検討・過去の棋譜の末尾へ追加しました。検討の盤面・駒の寸法を保ち、広告へはスクロールして到達します。広告は本番配信されず、収益は発生しません。詳細は [`docs/test-ads.md`](../docs/test-ads.md) を参照してください。

## v1.13.0（20）：対局設定と棋譜再生SE

「対局設定」でAIの強さLv.1〜10、双方の持ち時間（1・3・5・10・15・30分の切れ負け）、あなたの先手／後手を指定できます。初期値は3分・先手・Lv.8です。後手では盤面を反転し、CPUが先に指します。設定は再起動後も保持し、棋譜にも時間・レベル・先後の名前を記録します。

設定画面では時計と探索を停止します。キャンセルでは現在の対局を保持し、異なる設定の適用では現在の棋譜を保存して新しい盤面へ切り替えます。新しい対局は「対局開始」で開始します。

同じやねうら王＋つようら王11microを使い、Lv.1〜10のノード上限は100 / 300 / 800 / 2,000 / 5,000 / 10,000 / 20,000 / 50,000 / 100,000 / 250,000、探索時間上限は50 / 75 / 100 / 150 / 250 / 400 / 600 / 750 / 1,200 / 2,000msです。段級位やレーティングの保証値ではありません。検討の継続解析は対局レベルの影響を受けません。

検討での手順・グラフ・前後・初期／最終の局面移動にもSEを付けます。同じ局面、無効な移動、背景では鳴らさず、SE OFF・音量0を守ります。再生で振動は追加しません。

# Shogi Kento v1.12.0

参照画像に合わせて赤と金のホームへ変更しました。大きな検討ボタン、対局・貼り付け・過去の棋譜、小さな設定ボタンを配置。効果音の音量・SE ON/OFF・着手時の振動を調整して保存できます。詳細と生成素材・プロンプトは [HOME_DESIGN.md](HOME_DESIGN.md)。versionCode 19。

## v1.11.0 の変更（履歴）

ホームの「過去の棋譜」から、コピーして取り込んだ棋譜とアプリ内の対局を一覧で開けます。端末内へ自動保存し、最終局面・全手順の移動・AI検討を再利用できます。対局途中、投了、時間切れ、やり直し前の棋譜も保持します。詳細は [RECORD_ARCHIVE.md](RECORD_ARCHIVE.md)。versionCode 18。

## v1.10.1 の変更（履歴）

選択した駒の合法な移動先を明るい金色、選択元を青色で表示します。移動できないマスは選択解除、別の自分の駒は選択切り替え。持ち駒・王手・ピン・成りにも対応します。検討の標準操作は合法手のみとなり、従来の自由配置は解析欄左上の操作メニューから切り替えられます。棋譜取り込み・全手順移動・継続解析は維持。詳細は [LEGAL_SELECTION.md](LEGAL_SELECTION.md)。versionCode 17。

## v1.9.0 の変更（履歴）

開発リポジトリ: https://github.com/555734/shogiKento

検討画面から「モード」「初期配置」「一手戻す」を除去し、空いた上部を詰めています。画面を戻るにはAndroidの戻る操作を使用します。対局画面の操作は維持します。

評価グラフをタップすると最も近い記録手数の盤面へ移動します。盤上の駒、成り状態、両者の持ち駒、解析側を復元します。先の履歴は残るため前後に移動できます。過去の局面で新しい手を指すと、その先の履歴を置き換えます。自由配置モードのため、記録の1手は自由編集の1操作です。最大300操作前まで保持します。

検討は `go infinite` による同じ局面の継続解析です。ノード・750msで打ち切らず、同じ深さで揃ったMultiPV最大3候補を最大4Hzで更新します。結果が変われば候補手・先手基準評価値・形勢の割合・現在のグラフ点へ反映し、探索の深さとノード数も表示します。数値や候補手が必ず変わるという意味ではありません。局面変更、解析側変更、履歴移動、画面終了、バックグラウンドでは停止し、古い結果を破棄します。復帰時は表示中の局面を再解析します。違法配置や詰みでは従来通り理由・終局状態を表示します。

画像・背景・対局時計・成り選択・着手音・やねうら王＋つようら王11microはv1.8.4と同じです。検討は1スレッドで探索を続けます。対局CPUの3,000/10,000/50,000ノード設定と最大750msは維持します。

## Build and tests

GitHub Actions: `.github/workflows/native-shogi.yml` at repository root. Java 17 / Gradle 8.9 / Android SDK 35. CI tests rule/editor/history/protocol/analysis behavior, independent python-shogi legality, exact APK ARM64 normal/scalar engines, emulator installation and UI operations. The native cache is local to this repository; the previous private repository is not required. Full pinned GPL engine source, MIT evaluation parameters and licenses are prepared and included in the complete source ZIP. Corresponding engine source and cached binaries are linked by the cache manifest's recipe hash.

# Native shogi v1.10.0 — clipboard KIF study

起動・前面復帰時にコピーされた平手KIFを検出し、確認後に最終局面を検討モードへ取り込めます。既存の検討は置き換え前に確認。全手順を保持し、グラフ・手順一覧・前後／初期／最終ボタンで移動、合法手がある局面では既存のAIによる継続解析を開始します。キャンセルした同じ内容は再表示しません。ホームにOS標準の貼り付け操作用の入力欄も用意しています。対応形式・OSの制約・提示された79手の棋譜の検証方法は [CLIPBOARD_IMPORT.md](CLIPBOARD_IMPORT.md)。versionCode 16。

# Native shogi v1.9.1 — vermilion lacquer (historical)

採用されたデザイン案③「朱漆の艶」を反映。赤い漆の背景と、山水画を除いた温かい木目の盤へ変更しました。金色の駒、全幅の盤、暗い持ち駒・解析パネルは維持。v1.9.0の連続解析・グラフからの局面移動も引き継ぎます。versionCode 15。新しい背景・盤は独立した生成素材として追加し、元素材は保持します。採用案、生成プロンプト、可逆WebPのハッシュは `GENERATED_ASSETS.md` と `design/generated/` に記録。

# Native shogi v1.8.4 — reference layout (historical)

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



## オンライン対戦（P2P・サーバー不要）

ホームの「オンライン対戦」から、「ランダム対戦」「ルームを作る（6桁コード）」「ルームに参加」の3通りで、人と3分切れ負けで対局できます。アカウント登録・サーバー契約・課金は一切不要で、開発者側にも運用サーバーはありません。リレー・STUN・P2Pの通信はオンライン対戦の画面を開いている間だけ行い、検討・CPU対局のAIは従来どおり端末内で動作します。オンライン対局の盤の下には、CPU対局と同じ広告枠（match）を表示します。

仕組み（すべて無料の公開インフラだけを組み合わせています）:

1. **マッチング**: 公開Nostrリレー8か所（日本国内のyabu.meを含む。いずれも無料・認証不要・WebSocket over TLS 443番で、CIの実測で書き込みと配信を確認済み）へ同時に接続し、保存されないエフェメラルイベント（kind 25150）で「募集→申込→承諾→確定」の3段階ハンドシェイクを行います。公開鍵の小さい側だけが申し込む規則なので、同時に何人いても二重マッチしません。待っている側は新しい参加者を見た瞬間に名乗り返すため、周期（3秒）を待たずに成立します。申込・承諾は1.2秒ごとに再送され重複は冪等に処理されるので、リレーで1通失われても約1.5秒の遅れで済みます。イベントはBIP-340署名とIDを受信側でも検証します。ルームは6桁コードのハッシュで分離され、ランダム対戦とは混ざりません。
2. **P2P直結**: 申込/承諾にP-256 ECDH公開鍵と、暗号化したUDP候補（LANのIPv4、グローバルIPv6、Google/CloudflareのSTUNで得た公開アドレス）を同封し、マッチ成立と同時に双方がUDPホールパンチングを始めます。応答した経路のうち最も速いものを使います。
3. **対局通信**: AES-256-GCMで暗号化・改ざん検知・リプレイ拒否。再送制御付きで順序と重複排除を保証します。対称型NATなどで直結できない場合や途中で直結が切れた場合は、同じ暗号パケットを自動的に公開リレー経由でも送り、直結が戻れば自動で直結に戻ります。中継者は内容を読めません。
4. **時計（3分切れ負け）**: 自分の持ち時間は自分の端末で計測します。相手が申告する残り時間は「遅延許容（2秒または2×RTT+0.5秒の大きい方）」の範囲内だけ採用するため、通信遅延で持ち時間が減らない一方、改造クライアントが自分の時計を止めることもできません。相手の時間切れは4秒の猶予後に判定し、送信中の指し手を待ちます。
5. **ルール**: 双方の端末が同じJavaの合法手判定を行い、受信した手も合法手と照合します。不正な手は反則負けとして扱います。投了・時間切れ・千日手・詰み・退出は双方で同じ結果になります。終局後は「再戦」で先後を入れ替えて続けられます。後手番では盤面を180度回転して自分の駒を手前に表示します。

画面上部に経路（「P2P直結 12ms」または「リレー経由 180ms」）を表示し、相手から9秒以上応答がない場合は経過秒数を表示します。ロビーには接続できたリレー数、UDP候補（IPv4/IPv6の数）、STUNから推定したNATの種類を表示します。

注意事項:
- P2Pの性質上、対戦相手には自分のIPアドレスが伝わります（ロビーにも表示）。
- 公開リレーの運営者は暗号化された対局内容を読めませんが、使い捨て鍵での募集・申込のタイミングは見えます。公開鍵基盤がないため、リレー自身による能動的な中間者攻撃までは防げません。
- 公開リレーはボランティア運営でSLAはありません。8か所のうち1か所でも動けばマッチングとリレー経由の対局ができます。リレーが「rate-limit」で拒否したら書き込みを指数的に休止し（30秒〜10分）、Web of Trust・認証・PoW・有料などのポリシーで拒否したらそのセッション中は書き込みません（受信は継続）。リレーへの送信は直結できない間だけに絞り、候補の再送は間隔を伸ばし、keepaliveは無通信時だけ送ります。リレー一覧は`NostrRelayPool.DEFAULT_RELAYS`、STUNは`UdpEndpoint.DEFAULT_STUN`。
- オンライン対局中はアプリを離れても時計は止まりません。退出は投了扱いです。レーティングや棋譜サーバーはありません。
- 端末の時計が大きくずれているとリレーがイベントを拒否することがあります（リレーのDateヘッダーが得られれば自動補正）。

### アカウントとレーティング

- **アカウント（登録不要）**: 初回にこの端末だけが持つ鍵（secp256k1）を作り、その公開鍵がプレイヤーIDになります。秘密鍵はAndroid Keystoreの鍵で暗号化して保存し、端末のバックアップには含めません。名前（16文字まで）はロビーの「アカウント」から変更できます。機種変更は「引き継ぎコードを表示」（NIP-19のnsec形式）→新しい端末の「引き継ぎコードで復元」で行い、公開プロフィールからレートと成績を戻します。コードを知る人はアカウントを使えるため、画面上で警告します。
- **レーティング**: ランダム対戦がレート戦、ルーム対戦はレート変動なしです。Elo方式で初期1500、K係数は最初の10局（暫定）40、30局まで24、以降16、レート差は800で頭打ち、下限100・上限4000。勝敗はそれぞれの端末が自分の視点で記録し、AndroidとiOSで同じ式・同じ丸め（floor(x+0.5)）を使います。どちらかが2手指す前の投了・退出は「対局不成立」でレートは変わりません。レート戦の途中でアプリを終了・強制終了した場合も、次回起動時に負けとして記録します（退出・切断の扱いは相手側の「相手の退出」勝ちと一致します）。
- **相手の確認**: マッチ直後と再戦の各局の開始時に、名前・レート・局数を含む「アカウントカード」をP2Pの暗号化経路で交換します。カードは対局セッションのIDごとにアカウント鍵でBIP-340署名され、受信側が検証するため、途中での書き換えや別の対局からの使い回しはできません。盤の下に相手の名前・レート・これまでの対戦成績を、終局後に「レート 1500 → 1516（+16）」を表示します。
- **レートの近い相手から**: ランダム対戦の募集にレートを載せ、差が±150以内の相手から組みます。待つ間は3秒ごとに±50ずつ広げるので、人が少なくても待ち続けることはありません。
- **公開プロフィールとランキング**: 名前・レート・成績を、同じ公開Nostrリレーに置き換え可能なイベント（NIP-78、kind 30078）としてアカウント鍵で署名して公開します（アカウント画面でオフにできます）。引き継ぎ時の復元と、アカウント画面の「ランキング」（公開プロフィールのレート順）に使います。
- **限界（正直な注意）**: サーバーがないため、レートは各プレイヤーのアプリが計算した自己申告値です。改造したアプリや複数アカウントでの自作自演は防げません。カードの署名で「対局中の相手が本人の鍵を持ち、送ったレートを改ざんされていない」ことまでは保証します。ランキングは参考値として表示します。

Epic Online Services（EOS）について: EOSも無料ですが、Epic開発者アカウントでの製品登録と、ログイン後にしか入手できないSDK・クライアント認証情報が必要で、このリポジトリやCIからは取得できません。そこで同等の構成（マッチング＋NAT越え＋中継フォールバック）を、登録不要の公開Nostrリレーと公開STUNで実装しました。EOSのリレーを追加経路にする場合は、製品のProductId/SandboxId/DeploymentId/ClientId/ClientSecretとAndroid版SDKを用意すれば、`P2PLink.Relay`に同じ暗号パケットを渡す形で組み込めます。

検証: `tests/NetplayTest.java`（905アサーション、約35秒）がJSONのNIP-01直列化、BIP-340公式テストベクター19件、RFC 5769のSTUN応答、候補アドレスの検証、遅延のある仮想時間での時計・時間切れ・猶予・不正手・再戦、30%のUDP損失下での順序保証、改ざん/リプレイ/別鍵パケットの拒否、UDP断→リレー→直結復帰、5人同時のランダム対戦（二重マッチなし）、待機中の相手への即時名乗り返し、ルームコード、リレー再起動、30%損失・150ms遅延のリレー、rate-limit/ポリシーで拒否するリレーへの書き込み停止、直結UDPのデータがリレー経由の確定より先に届く場合、申込・承諾・確定がリレーで1通失われる場合を、署名を厳格に検証するローカルNostrリレーとループバックUDPで確認します。ループバック直結の指し手到達は中央値約2ms、ローカルリレーのみで約26ms。GitHub Actionsでは実際の公開リレーとSTUNで別ロビーを使った対局と、リレーごとの受付・配信・遅延の調査も行い、結果を`outputs/netplay_live.txt`に記録します（外部サービス依存のため情報扱い）。米国のGitHubランナーでの実測: STUNで公開アドレスを取得（非対称型NAT）、ランダム対戦の成立0.3〜0.5秒、UDPを遮断したリレーのみの対局で1手あたり中央値75〜123ms、リレー単体の配信76〜242ms（日本のyabu.meは米国から242ms）。damusは同一IPからの連続試験でrate-limitの対象になり、offchain.pub・nostr.bitcoiner.socialは数件受け付けた後にWeb of Trustで拒否したため、上記の休止・停止が働くことを確認し、後者2か所は既定から外しました。日本国内の回線同士の数値は未計測です。Androidエミュレーターでは、OSのタッチ操作でランダム対戦・直結確認・着手音・投了・先後反転の再戦・ルーム作成・コード入力での参加・退出を、アプリ内のローカルリレーと2台目のNetplayで検証し、スクリーンショット50〜55を保存します。実機同士・キャリア回線間での直結率と遅延は未検証です。
