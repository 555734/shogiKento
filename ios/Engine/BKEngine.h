#pragma once
#ifdef __cplusplus
extern "C" {
#endif
void BKStart(void);
void BKSend(const char *command);
char *BKRead(int timeout_ms);
char *BKLegal(const char *sfen);
char *BKApply(const char *sfen, const char *move);
int BKChecked(const char *sfen);
void BKFree(char *text);
#ifdef __cplusplus
}
#endif
