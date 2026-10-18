from pathlib import Path
import sys
p=Path(sys.argv[1])
h=p/'src/search/Searcher.hpp';s=h.read_text()
s=s.replace('  void clean();','  void clean();\n  void setNodeLimit(uint64_t n) { nodeLimit_ = n; }\n  uint64_t visitedNodes() const { return visitedNodes_.load(); }\n  void setExternalEpoch(std::atomic<uint64_t>* p, uint64_t expected) { externalEpoch_=p; expectedEpoch_=expected; }')
s=s.replace('  bool isInterrupted() const {','  bool isInterrupted() const {\n    if (externalEpoch_ && externalEpoch_->load(std::memory_order_relaxed) != expectedEpoch_) return true;\n    if (nodeLimit_ && visitedNodes_.load(std::memory_order_relaxed) >= nodeLimit_) return true;')
s=s.replace('  std::atomic_bool interrupted_;','  std::atomic_bool interrupted_;\n  std::atomic<uint64_t> visitedNodes_{0};\n  uint64_t nodeLimit_=3000;\n  std::atomic<uint64_t>* externalEpoch_=nullptr;\n  uint64_t expectedEpoch_=0;')
h.write_text(s)
c=p/'src/search/Searcher.cpp';s=c.read_text().replace('  interrupted_ = false;','  interrupted_ = false;\n  visitedNodes_=0;')
for field in ['nodes','quiesNodes']:
 s=s.replace('  tree.info.'+field+'++;','  if (isInterrupted()) return Score::zero();\n  visitedNodes_.fetch_add(1, std::memory_order_relaxed);\n  tree.info.'+field+'++;')
# Preserve the best legal root candidate if a small node budget ends iteration 1.
s=s.replace('if (tree.completedDepth > result_.depth)', 'if (tree.completedDepth > result_.depth || (ti == 0 && result_.move.isNone()))')
c.write_text(s)
# Resource cleanup and explicit include portability fixes for current Android Clang.
for file in (p/'src').rglob('CMakeLists.txt'):
 file.write_text(file.read_text().replace('VERSION 2.8','VERSION 3.10'))

# Search duration must not change when Android wall time is adjusted.
for file in (p/'src/common/time').glob('Timer.hpp'):
 file.write_text(file.read_text().replace('system_clock','steady_clock'))
