# v1.9.1 approved vermilion lacquer (2026-10-18)

The user selected red/wood revision 03, **vermilion lacquer**. `design/generated/red_lacquer_approved.png` records the approved mockup. Two production-only textures were created with built-in image_gen; exact prompts are in `design/generated/prompts_v191.json`.

- `generated/background_red_lacquer_v191.webp`: uninterrupted vermilion lacquer with restrained reflected light, replacing the displayed red/gold patterned background in home, match and study.
- `generated/board_v191.webp`: honey-amber wood without the former mountain/temple illustration or baked grid. Board geometry is unchanged; native Canvas still draws the exact 9×9 grid and stars.
- Lossless WebP conversion preserves decoded source pixels; dimensions and PNG/WebP SHA-256 hashes are in adjacent JSON files. Source PNGs are in `design/generated/`.
- Existing golden piece atlas, player panels, controls and all 37 original PNG bytes are retained. Runtime uses the new textures. The unused v1.5 generated atlas and v1.8.4 generated board (with metadata) are preserved byte-for-byte under `design/generated/legacy/`, included in the source ZIP instead of the APK to keep the existing 25 MB package budget. The original red/gold background remains packaged with the original artwork.
- This release retains v1.9.0 continuous native analysis and graph position navigation. Build identity: 1.9.1 (15).

# v1.8.4 reference design (2026-10-18; historical)

The built-in image_gen tool created the golden piece atlas and amber board in response to the user-provided 6386.jpg reference. Exact prompts are in `design/generated/prompts_v184.json`.

- `app/src/main/assets/generated/pieces_v184.png`: 1254×1254, original generated RGBA PNG, 15 sprites on a regular 4×4 atlas. Correct black 歩 香 桂 銀 金 角 飛 玉 王 and red と 杏 圭 全 馬 龍. Runtime crops discard transparent gutters and normalize all visible body heights without changing sprite aspect ratios. Crops and SHA-256 are in the adjacent JSON.
- `app/src/main/assets/generated/board_v184.webp`: 1226×1283, lossless WebP, pixel-identical to `design/generated/board_v184.png`. The original generated PNG is included in the source ZIP. Native Canvas draws the exact 9×9 grid and star points over this generated wood/frame bitmap; source aspect ratio is maintained.
- Compact dark player/analysis panels use the existing generated original UI tray wood crop with uniform scaling and horizontal tiling; neutral tint follows the reference. Native text shows changing clocks, counts and evaluations. Blue/red percentages appear above a slim bar; graph is cyan on slate.
- Original red/gold background is unchanged. All 37 original PNGs retain their original bytes in the APK; original source had 38 files including its README. Previous generated `pieces_v15.png` and its JSON are also retained in the APK and source, but are not displayed.
- No reference screenshot, avatar or sample midgame board is embedded. Normal initial shogi placement and every existing game/study feature remain.

# v1.5 generated tall shogi pieces

Final project asset: app/src/main/assets/generated/pieces_v15.png (RGBA PNG, 1265×1243), plus coordinate/hash metadata pieces_v15.json. Created with the built-in image_gen tool. Originals were preserved byte-for-byte; the new atlas is a separate added file. Native Android crops sprites at runtime and scales each uniformly, without changing their aspect ratio. Body heights are normalized across all types, directions, ranks and trays. Black normal glyphs and red promoted glyphs are baked into the PNG. The blank sixteenth tile is retained but is not drawn in gameplay.

Reference inputs: original/pieces/normal/fu.png and original/sheets/normal_pieces_grid.png, both provided by the user. Shogi Wars screenshots were used only to compare cell/piece proportions, not as art to copy or redistribute.

## Prompt 1

Production transparent PNG sprite atlas for a native Japanese shogi game. Exactly 4 columns by 4 rows. One upright pentagonal wooden shogi piece per tile, straight-on orthographic view, warm honey-gold wood, gold bevel, natural vertical grain, large confident Japanese brush lettering. Identical tall slender silhouettes, target height/width 1.22. Generous transparent gutters, no labels or grid. Row 1 black 歩 香 桂 銀; row 2 black 金 角 飛 玉; row 3 black 王, red と 成香 成桂; row 4 red 成銀 馬 龍, then blank. Two-character promoted labels stacked vertically. Actual alpha transparency, no opaque background. Input references provide material and lettering style; replace the squat silhouettes.

## Final edit prompt

Use case: precise-object-edit. Edit this production transparent shogi sprite atlas. KEEP the exact 4x4 arrangement, all characters/order, wood grain and colors, gold bevel, straight-on lighting, real transparent background. Change ONLY: (1) make ALL sixteen wooden bodies uniformly more slender and tall, reducing their width by about 8 percent while keeping height the same, so each body height is about 1.24 times its maximum width; identical silhouettes for all. Do not widen text beyond the new bodies. (2) CRITICAL Japanese correction: row TWO column FOUR must clearly be the character 玉 (a king with a distinct DOT at lower right), NOT 王. Row THREE column ONE remains 王 with NO dot. All other writing stays unchanged and exactly correct: row1 歩 香 桂 銀, row2 金 角 飛 玉, row3 王 と 成香 成桂, row4 成銀 馬 龍 blank. Normal black characters, promoted crimson red. Generous transparent gutters so no piece touches another. No new labels, panels, grid, extra text, or opaque background. Maintain straight sides and sharp 5-sided tall shogi silhouette, large bold clear calligraphy. Preserve original alpha transparency.

## Measurement and integration

Measured body rectangles are recorded rather than assuming exact equal tiles. The final measured height/width is 1.319–1.390 (the generator did not output the requested 1.24 exactly). These actual ratios are preserved. We visually checked all fifteen used glyphs, including 玉 with dot / 王 without dot and red promotion labels. No reference-game assets are packaged.

