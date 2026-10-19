#!/usr/bin/env bash
set -euo pipefail
mkdir -p outputs/screenshots
adb shell wm size 1080x2400
adb shell wm density 420
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
python scripts/emulator_preflight.py | tee outputs/emulator_preflight.txt
adb shell am start -W -n com.presentsoft.shogi3minutes/.MainActivity | tee outputs/launch.txt
adb exec-out screencap -p > outputs/screenshots/00_launch.png
adb shell am force-stop com.presentsoft.shogi3minutes
set +e
timeout 420s adb shell am instrument -w com.presentsoft.shogi3minutes.test/com.presentsoft.shogi3minutes.NativeSmokeTest | tee outputs/instrumentation.txt
test_status=${PIPESTATUS[0]}
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/screenshots outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/ai_benchmark.tsv outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/model_installation.txt outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/placement_audio.txt outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/study_analysis.txt outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/evaluation_graph.txt outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/study_recovery.txt outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/native_compatibility.txt outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/promotion_popup.txt outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/continuous_study.txt outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/clipboard_import.txt outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/legal_selection.txt outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/record_archive.txt outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/home_settings.txt outputs/ || true
adb pull /sdcard/Android/data/com.presentsoft.shogi3minutes/files/match_settings.txt outputs/ || true
adb shell dumpsys meminfo com.presentsoft.shogi3minutes > outputs/meminfo.txt
adb logcat -d > outputs/logcat.txt
set -e
test "$test_status" -eq 0
grep -q 'PASS native instrumentation:' outputs/instrumentation.txt

python scripts/verify_android_ai.py | tee outputs/android_ai_verification.txt



