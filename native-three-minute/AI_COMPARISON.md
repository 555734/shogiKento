# v1.6 AI selection: TsuyouraOu11micro

Official sources:
- https://yaneurao.fanbox.cc/posts/12761272 (public release, 2026-10-14)
- https://yaneuraou.yaneu.com/2026/10/14/tsuyou-11-mini-micro-released-under-mit-license/
- https://github.com/yaneurao/YaneuraOu/tree/c1b80eaa09fe13d5f12b1599d1ae4d53c224de30

## Size and choice

| Model | Architecture | nn.bin bytes | progress.bin bytes | Total parameter bytes |
|---|---|---:|---:|---:|
| Previous Suisho5 | HalfKP256 | 64,217,066 | 0 | 64,217,066 |
| TsuyouraOu11mini | SFNN_ka2_4096_8_64_progress8 | 7,711,372 | 1,003,104 | 8,714,476 |
| TsuyouraOu11micro | SFNN_ka2_2048_8_64_progress8 | 3,864,610 | 1,003,104 | 4,867,714 |

The app bundles micro only: about 92.4% fewer raw parameter bytes than Suisho5, and about 44.1% fewer than mini. Both nn.bin AND progress.bin are required. The official release says micro and mini both exceed Suisho5 by about R150–200; this is the publisher's result, not a rating measured for this app. Mini emphasizes evaluation quality, micro emphasizes NPS. Lowering nodes also reduces work for either model, so the app keeps the 3,000/10,000/50,000 node choices (default 50,000), one thread, Hash16MiB, no pondering and a cooperative 750ms cap.

We choose micro because the goal is a smaller APK and lighter mobile computation. Both models were tested at the SAME node budgets. Fixed-node speed is not playing strength, and a host result is not a Motorola g55 measurement.

## Actual host comparison

Host x86_64 SSE2, one thread, Hash16MiB, ten identical positions per model and node budget, 750ms cap, no book. All 60 returned moves matched python-shogi legal moves. Raw results are in validation/tsuyou_host_comparison.tsv and .json.

| Model | Node limit | Median ms | Max ms |
|---|---:|---:|---:|
| micro | 3000 | 7.90 | 9.16 |
| micro | 10000 | 26.06 | 32.88 |
| micro | 50000 | 136.65 | 177.99 |
| mini | 3000 | 14.29 | 20.93 |
| mini | 10000 | 46.14 | 65.23 |
| mini | 50000 | 245.44 | 346.85 |

## Engine compatibility and rights

The shipped engine is pinned YaneuraOu 9.80git, commit c1b80eaa09fe13d5f12b1599d1ae4d53c224de30 (2026-09-14), built specifically for SFNN_ka2_2048_8_64_progress8. It is not the Windows V9.90 binary linked in the release article. The public source supports this architecture and successfully loads both the model and progress parameters. Android builds use baseline ARMv8 NEON and x86_64 SSE2; no AVX2 or dot-product extension is required on the phone.

YaneuraOu is GPL v3; the complete pinned engine source, architecture generator, build configuration and LICENSE are distributed. The publisher explicitly released nn.bin and progress.bin under MIT, including redistribution and commercial use. Their SHA-256, download URL, release date, parameter architecture and FV_SCALE24 are pinned in app/src/main/assets/ai/provenance.json. The MIT notice and author attribution are included in APK and source. The original tag.json/eval_options.txt are retained; the upstream banner PNG is not used or redistributed.

At first use, both parameter files and metadata are verified and copied to private storage. Incorrect files (including same-size corruption) are repaired. After a verified installation, old suisho5/full/light parameter copies are removed. Match/study saves are preserved. No runtime model download is needed.

Motorola g55 physical-device speed, heat and battery remain unmeasured. Android emulator measurements are separately recorded by CI. No claim about playing strength under the app's node/time caps is inferred from the publisher's rating.
