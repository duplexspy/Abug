#include <cstdio>
#include <cstdarg>
#include <cstddef>
#include <cstdint>

extern "C" {
int __android_log_print(int prio, const char* tag, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vfprintf(stderr, fmt, args);
    fprintf(stderr, "\n");
    va_end(args);
    return 0;
}
int AAsset_read(void*, void*, size_t) { return 0; }
void* AAssetManager_open(void*, const char*, int) { return nullptr; }
void AAsset_close(void*) {}
size_t AAsset_getLength(void*) { return 0; }
int64_t AAsset_seek(void*, int64_t, int) { return 0; }
const void* AAsset_getBuffer(void*) { return nullptr; }
int64_t AAsset_getRemainingLength64(void*) { return 0; }
int __system_property_get(const char*, char*) { return 0; }
}
