#!/usr/bin/env bash
# Checks the iOS online core (ios/Sources/Online) against the Android implementation on the host:
# BIP-340 vectors and NIP-01 ids, then complete games between the Swift code and the Java client
# through a local relay (direct UDP, relay only, rooms, repetition, resignation, rematch, leaving).
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
android="$root/native-three-minute"
classes="$root/ios/HostTests/.build/java"
rm -rf "$classes" && mkdir -p "$classes"
javac -encoding UTF-8 -implicit:class -sourcepath "$android/app/src/main/java" -d "$classes" \
  "$android/tests/relay/LocalRelay.java" "$android"/tests/interop/*.java
swift build -c release --package-path "$root/ios/HostTests"
check="$root/ios/HostTests/.build/release/HostCheck"
"$check" "$android/tests/bip340.csv"
"$check" interop "$classes"
