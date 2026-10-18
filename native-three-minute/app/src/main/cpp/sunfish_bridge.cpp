#include "core/util/CoreUtil.hpp"
#include "core/record/SfenParser.hpp"
#include "search/util/SearchUtil.hpp"
#include "search/eval/Evaluator.hpp"
#include "search/Searcher.hpp"
#include <atomic>
#include <chrono>
#include <memory>
#include <sstream>
#include <string>
#include <mutex>
#include <sys/resource.h>
#ifdef HOST_BENCH
#include <iostream>
#else
#include <jni.h>
#endif
using namespace sunfish;
using Clock=std::chrono::steady_clock;
static std::shared_ptr<Evaluator> evaluator;
static std::unique_ptr<Searcher> searcher;
static std::atomic<uint64_t> cancelEpoch{0};
static std::once_flag once;
class QuietHandler : public SearchHandler {
 void onStart(const Searcher&) override {}
#ifdef SUNFISH_LIGHT
 void onUpdatePV(const Searcher&,const PV&,float,int,Score) override {}
#else
 void onUpdatePV(const Searcher&,const PV&,float,int,Score,int) override {}
#endif
 void onFailLow(const Searcher&,const PV&,float,int,Score) override {}
 void onFailHigh(const Searcher&,const PV&,float,int,Score) override {}
 void onIterateEnd(const Searcher&,float,int) override {}
};
static QuietHandler quiet;
static double initMs;
static double ms(Clock::time_point t){return std::chrono::duration<double,std::milli>(Clock::now()-t).count();}
static bool initialize(const std::string& path){
 auto start=Clock::now();std::call_once(once,[]{CoreUtil::initialize();SearchUtil::initialize();});
 searcher.reset();evaluator.reset();evaluator=std::make_shared<Evaluator>(Evaluator::InitType::Zero);
 if(!load(path.c_str(),*evaluator)){evaluator.reset();return false;}
 searcher.reset(new Searcher(evaluator));searcher->setHandler(&quiet);searcher->ttResizeMB(16);searcher->clean();initMs=ms(start);return true;
}
static std::string choose(const std::string& sfen,uint64_t nodes,int millis,uint64_t expectedEpoch=0){
 if(!searcher)return "error|not initialized";
 Position pos;if(!SfenParser::parsePosition(sfen,pos))return "error|bad SFEN";
 searcher->setExternalEpoch(&cancelEpoch,expectedEpoch);auto config=searcher->getConfig();config.numberOfThreads=1;config.multiPV=1;
 config.maximumTimeMs=millis;config.optimumTimeMs=SearchConfig::InfinityTime;
 searcher->setConfig(config);searcher->setNodeLimit(nodes);auto start=Clock::now();searcher->idsearch(pos,64*Searcher::Depth1Ply);
 auto& r=searcher->getResult();std::ostringstream out;
 out<<(r.move.isNone()?"none":r.move.toStringSFEN())<<"|"<<searcher->visitedNodes()<<"|"<<ms(start)<<"|"<<r.depth;
 return out.str();
}
#ifdef HOST_BENCH
int main(int argc,char** argv){
 if(argc<4||!initialize(argv[1]))return 2;
 std::string line;while(std::getline(std::cin,line)){
  searcher->clean();auto result=choose(line,std::stoull(argv[2]),std::stoi(argv[3]));struct rusage usage{};getrusage(RUSAGE_SELF,&usage);
  std::cout<<initMs<<"|"<<usage.ru_maxrss<<"|"<<result<<std::endl;
 }
}
#else
#ifdef SUNFISH_LIGHT
#define JNI_FN(name) Java_com_presentsoft_shogi3minutes_NativeLight_##name
#else
#define JNI_FN(name) Java_com_presentsoft_shogi3minutes_NativeFull_##name
#endif
static std::string utf(JNIEnv* e,jstring s){const char* p=e->GetStringUTFChars(s,nullptr);std::string result(p);e->ReleaseStringUTFChars(s,p);return result;}
extern "C" JNIEXPORT jboolean JNICALL JNI_FN(initialize)(JNIEnv* e,jclass,jstring path){return initialize(utf(e,path));}
extern "C" JNIEXPORT jstring JNICALL JNI_FN(search)(JNIEnv* e,jclass,jstring sfen,jint nodes,jint millis,jlong epoch){return e->NewStringUTF(choose(utf(e,sfen),nodes,millis,static_cast<uint64_t>(epoch)).c_str());}
extern "C" JNIEXPORT jlong JNICALL JNI_FN(epoch)(JNIEnv*,jclass){return static_cast<jlong>(cancelEpoch.load());}
extern "C" JNIEXPORT void JNICALL JNI_FN(stop)(JNIEnv*,jclass){cancelEpoch.fetch_add(1);}
extern "C" JNIEXPORT void JNICALL JNI_FN(close)(JNIEnv*,jclass){searcher.reset();evaluator.reset();}
#endif
