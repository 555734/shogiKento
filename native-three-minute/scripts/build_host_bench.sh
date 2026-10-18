#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p host-bench
for variant in full light; do
 root="third_party/sunfish-$variant"
 mapfile -t sources < <(find "$root/src/core" "$root/src/search" "$root/src/common" "$root/src/logger" -name '*.cpp')
 flags=();if [ "$variant" = light ]; then flags+=(-DSUNFISH_LIGHT=1);fi
 g++ "${flags[@]}" -std=c++11 -O2 -DNDEBUG -pthread -fno-rtti -DHOST_BENCH -I"$root/src" app/src/main/cpp/sunfish_bridge.cpp "${sources[@]}" -o "host-bench/$variant"
done
