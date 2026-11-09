#!/bin/bash
set -e

SYSROOT="/opt/android-ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
LIBUNWIND="/opt/android-ndk/toolchains/llvm/prebuilt/linux-x86_64/lib64/clang/14.0.7/lib/linux/aarch64/libunwind.a"
cp -f "$LIBUNWIND" "$SYSROOT/usr/lib/aarch64-linux-android/libunwind.a"

CPP_DIR="/workspace/frame-forge/app/src/main/cpp"
CLANGXX="/opt/llvm-12/bin/clang++"
LLD="/opt/llvm-12/bin/ld.lld"
OBJ_DIR="/tmp/frameforge_obj"
rm -rf "$OBJ_DIR"
mkdir -p "$OBJ_DIR"

echo "=== 1. Compiling FrameForge C++ sources for Android arm64-v8a ==="
CXXFLAGS="-O3 -fPIC -std=c++17 --target=aarch64-linux-android26 --sysroot=$SYSROOT -ffunction-sections -fdata-sections -I$CPP_DIR/ncnn_install/include -I$CPP_DIR"

SOURCES=("real_esrgan_engine.cpp" "rife_engine.cpp" "tile_engine.cpp" "color_convert.cpp" "quality_metrics.cpp" "video_restoration.cpp" "jni_bridge.cpp")

for src in "${SOURCES[@]}"; do
    echo "Compiling $src..."
    $CLANGXX $CXXFLAGS -c "$CPP_DIR/$src" -o "$OBJ_DIR/${src%.cpp}.o"
done

echo "=== 2. Linking libframeforge.so for Android Bionic ==="
OUT_LIB="/workspace/frame-forge/app/src/main/jniLibs/arm64-v8a/libframeforge.so"
mkdir -p "$(dirname "$OUT_LIB")"

$CLANGXX -shared -fPIC \
  --target=aarch64-linux-android26 \
  --sysroot="$SYSROOT" \
  -fuse-ld="$LLD" \
  -static-libstdc++ \
  -Wl,--gc-sections \
  -Wl,-soname,libframeforge.so \
  -o "$OUT_LIB" \
  "$OBJ_DIR"/*.o \
  "$CPP_DIR/ncnn_install/lib/libncnn.a" \
  "$LIBUNWIND" \
  -landroid -llog -lm -ldl -lz

/usr/bin/aarch64-linux-gnu-strip --strip-unneeded "$OUT_LIB"

echo "=== 3. Verification ==="
readelf -d "$OUT_LIB" | grep NEEDED
ls -lh "$OUT_LIB"
echo "Done!"
