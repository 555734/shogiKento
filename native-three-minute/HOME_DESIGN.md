# ホームと音・振動の設定（v1.12.0 / 19）

添付された6401.jpgをデザインの参照として、赤い漆・金箔・梅・和柄を使うホームに更新しました。金の筆文字の題字、主ボタンの検討モード、木を感じる暗色の対局・貼り付け・過去の棋譜ボタンを配置します。本文とボタンはAndroid Canvasで描画し、画面サイズごとに独立したタップ範囲を計算します。対局・検討の盤面画像は変更しません。

右上の小さな「設定」は、見た目より広いタップ範囲を持ちます。ネイティブの設定ダイアログで効果音の音量0〜100%、SEのON/OFF、着手時のバイブレーションのON/OFFを変更し、効果音と振動をそれぞれ試せます。変更はアプリ専用SharedPreferencesに即時反映し、端末内に保存します。初期値は従来の音量65%、SE ON、振動 OFFです。音楽は従来どおりありません。

音量はアプリのSoundPoolゲインです。端末のメディア音量を変更しません。SE OFFと音量0では再生要求を出しません。対局の双方の着手でSEを出し、振動は自分の着手と検討の駒移動・駒打ちでだけ要求します。選択・違法手・ホーム・バックグラウンド・CPUの応手には振動を要求しません。標準のperformHapticFeedbackを使い、端末の振動設定を尊重します。振動非対応端末やOSで無効の場合は振動しません。VIBRATE権限は追加しません。

## 素材と生成方法

内蔵image_genツールで参照画像から別々に生成した背景と透明な題字です。以下がプロジェクト内の最終素材です。WebPへ形式変換・圧縮し、題字のアルファを保持しています。ボタン文字を画像に含めず、盤面の既存素材は維持しています。

- `app/src/main/assets/generated/home_background_v112.webp`
- `app/src/main/assets/generated/home_title_v112.webp`
- `app/src/main/assets/generated/home_v112.json`（寸法・SHA256）

### 背景の生成プロンプト

```text
Use case: stylized-concept. Asset type: portrait background bitmap for an Android Japanese shogi game's home menu. Input image is a style/composition reference, NOT an edit target. Create a NEW high quality 9:16 portrait background inspired by the supplied reference: rich vermilion and deep crimson textured lacquer/washi, elegant gold foil brush strokes sweeping across the bottom and corners, a dark red ink enso in the upper third, small red plum blossoms and fine branch entering the top left, subtle gold asanoha geometric lines on the upper right and lower corners, subdued gold seigaiha waves near the bottom. Delicate red petals, restrained gold flecks. Keep the central upper area and the central lower two-thirds quiet, dark red and uncluttered so real native text and buttons can be placed on top. Ornate premium Japanese craftsmanship, warm wood/red/gold palette, textured metallic highlights, no neon. VERY IMPORTANT: background only. Absolutely NO text, NO letters, NO numbers, NO logo, NO buttons, NO rectangles, NO menu panels, NO shogi board, no UI. No watermark. Output portrait 9:16.
```

### 題字の生成プロンプト

```text
Use case: logo-brand. Asset type: transparent raster title wordmark for a Japanese shogi game Android home screen. The attached image is a style reference only. Create ONLY the exact Japanese title text "紅将棋 3分" in one horizontal line. All FIVE characters/symbols must be verbatim: 紅 将 棋 3 分. Elegant expressive Japanese brush calligraphy, strong hand-painted strokes, sculpted gold leaf with warm pale gold highlights, rough ink-like edges, refined premium traditional Japanese craft style as in the reference title. A short graceful metallic gold brush underline trailing to the right. Generous clear space around all sides, entire wordmark visible with no clipping. Wide horizontal composition about 3.5:1. Transparent alpha background; no background panel, no solid background, no checkerboard baked in, no red field, no enso, no decorations, no buttons, no additional text, no watermark. The logo must remain clearly readable when shown at 340 device-independent pixels wide.
```

## 検証

Android instrumentationで400〜960の仮想縦幅のボタン配置、主ボタンの大きさ、ボタンの重なりと下端、設定のタップ範囲、透明題字、OS経由のスライダーとスイッチ操作、SE試聴と実SoundPoolゲイン、音量0とSE OFFの再生抑止、Activity再生成後の設定保持を確認します。実際の着手とネイティブCPU応手を使い、ユーザー操作だけでの振動要求、選択・違法手・CPU・ホームでの抑止も確認します。証跡は `outputs/home_settings.txt`、画面41〜42です。物理端末での音量・振動の体感は、エミュレーターの再生・振動API確認とは区別します。
