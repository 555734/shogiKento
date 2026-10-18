// In-process transport for the pinned, otherwise unmodified GPL-3.0 engine.
// iOS does not permit launching the Android USI executable as a child process.
#include "BKEngine.h"
#include "bitboard.h"
#include "position.h"
#include "movegen.h"
#include "usi.h"
#include "misc.h"
#include <condition_variable>
#include <deque>
#include <mutex>
#include <thread>
#include <iostream>
#include <cstring>
#include <cstdlib>
using namespace YaneuraOu;
namespace {
std::once_flag tables, started;
void initialize() { std::call_once(tables, [] { Bitboards::init(); Position::init(); }); }
char *copy(const std::string& s) { auto p=(char*)std::malloc(s.size()+1); if(p) std::memcpy(p,s.c_str(),s.size()+1); return p; }
struct Input : std::streambuf {
 std::mutex lock; std::condition_variable changed; std::deque<std::string> commands; std::string current;
 int_type underflow() override { std::unique_lock<std::mutex> hold(lock); changed.wait(hold,[&]{return !commands.empty();}); current=std::move(commands.front());commands.pop_front();setg(current.data(),current.data(),current.data()+current.size());return traits_type::to_int_type(*gptr()); }
 void send(std::string s) { std::lock_guard<std::mutex> hold(lock);commands.push_back(s+"\n");changed.notify_one(); }
};
struct Output : std::streambuf {
 std::mutex lock;std::condition_variable changed;std::deque<std::string> lines;std::string current;
 std::streamsize xsputn(const char *s,std::streamsize n) override { std::lock_guard<std::mutex> hold(lock);for(std::streamsize i=0;i<n;i++){if(s[i]=='\n'){lines.push_back(std::move(current));current.clear();changed.notify_one();}else if(s[i]!='\r')current+=s[i];}return n; }
 int_type overflow(int_type c) override {if(!traits_type::eq_int_type(c,traits_type::eof())){char ch=traits_type::to_char_type(c);xsputn(&ch,1);}return traits_type::not_eof(c);}
 int sync() override {return 0;}
};
// Lifetime intentionally matches the application; no stream-buffer use after destruction.
Input& input(){static auto p=new Input;return *p;} Output& output(){static auto p=new Output;return *p;}
}
extern "C" void BKStart(){std::call_once(started,[]{initialize();std::ios::sync_with_stdio(false);std::cin.rdbuf(&input());std::cout.rdbuf(&output());static char name[]="BeniShogi";static char *argv[]={name,nullptr};CommandLine::g.set_arg(1,argv);std::thread([]{run_engine_entry();}).detach();});}
extern "C" void BKSend(const char *s){BKStart();if(s)input().send(s);}
extern "C" char *BKRead(int ms){auto& o=output();std::unique_lock<std::mutex> hold(o.lock);if(!o.changed.wait_for(hold,std::chrono::milliseconds(ms),[&]{return !o.lines.empty();}))return nullptr;auto s=std::move(o.lines.front());o.lines.pop_front();return copy(s);}
extern "C" char *BKLegal(const char *s){initialize();if(!s)return nullptr;Position p;StateInfo state;if(p.set(s,&state))return nullptr;std::string result;for(auto m:MoveList<LEGAL_ALL>(p)){if(!result.empty())result+=' ';result+=USIEngine::move(m);}return copy(result);}
extern "C" char *BKApply(const char *s,const char *move){initialize();if(!s||!move)return nullptr;Position p;StateInfo state,next;if(p.set(s,&state))return nullptr;for(auto m:MoveList<LEGAL_ALL>(p))if(USIEngine::move(m)==move){p.do_move(m,next);return copy(p.sfen());}return nullptr;}
extern "C" int BKChecked(const char *s){initialize();if(!s)return -1;Position p;StateInfo state;if(p.set(s,&state))return -1;return p.in_check()?1:0;}
extern "C" void BKFree(char *s){std::free(s);}
