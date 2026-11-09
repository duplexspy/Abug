#!/bin/bash
set -e

echo "=== Building abug (🐞) Standalone Native Engine for Laptop/Desktop ==="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CPP_DIR="$ROOT_DIR/app/src/main/cpp"

SYSROOT="/opt/android-ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
LIBUNWIND="/opt/android-ndk/toolchains/llvm/prebuilt/linux-x86_64/lib64/clang/14.0.7/lib/linux/aarch64/libunwind.a"
CLANGXX="/opt/llvm-12/bin/clang++"
LLD="/opt/llvm-12/bin/ld.lld"

if [ -f "$CLANGXX" ] && [ -d "$SYSROOT" ]; then
    echo "Using bundled LLVM toolchain..."
    mkdir -p /tmp/abug_build
    CXXFLAGS="-O3 -std=c++17 --target=aarch64-linux-android26 --sysroot=$SYSROOT -I$ROOT_DIR -I$CPP_DIR -I$CPP_DIR/ncnn_install/include"
    
    $CLANGXX $CXXFLAGS -c "$SCRIPT_DIR/abug_cli.cpp" -o /tmp/abug_build/abug_cli.o
    $CLANGXX $CXXFLAGS -c "$CPP_DIR/real_esrgan_engine.cpp" -o /tmp/abug_build/real_esrgan_engine.o
    $CLANGXX $CXXFLAGS -c "$CPP_DIR/rife_engine.cpp" -o /tmp/abug_build/rife_engine.o
    $CLANGXX $CXXFLAGS -c "$CPP_DIR/tile_engine.cpp" -o /tmp/abug_build/tile_engine.o
    $CLANGXX $CXXFLAGS -c "$CPP_DIR/color_convert.cpp" -o /tmp/abug_build/color_convert.o
    $CLANGXX $CXXFLAGS -c "$CPP_DIR/quality_metrics.cpp" -o /tmp/abug_build/quality_metrics.o
    $CLANGXX $CXXFLAGS -c "$CPP_DIR/video_restoration.cpp" -o /tmp/abug_build/video_restoration.o
    $CLANGXX $CXXFLAGS -c "$SCRIPT_DIR/android_stub.cpp" -o /tmp/abug_build/android_stub.o

    $CLANGXX -O3 --target=aarch64-linux-android26 --sysroot="$SYSROOT" -fuse-ld="$LLD" -static-libstdc++ \
      /tmp/abug_build/*.o \
      "$CPP_DIR/ncnn_install/lib/libncnn.a" \
      "$LIBUNWIND" \
      -llog -lm -ldl -lz \
      -o "$SCRIPT_DIR/abug-cli"
else
    echo "Using host g++ / clang++..."
    g++ -O3 -std=c++17 -fopenmp \
      -I"$ROOT_DIR" \
      -I"$CPP_DIR" \
      -I"$CPP_DIR/ncnn_install/include" \
      "$SCRIPT_DIR/abug_cli.cpp" \
      "$CPP_DIR/real_esrgan_engine.cpp" \
      "$CPP_DIR/rife_engine.cpp" \
      "$CPP_DIR/tile_engine.cpp" \
      "$CPP_DIR/color_convert.cpp" \
      "$CPP_DIR/quality_metrics.cpp" \
      "$CPP_DIR/video_restoration.cpp" \
      "$SCRIPT_DIR/android_stub.cpp" \
      -L"$CPP_DIR/ncnn_install/lib" -lncnn \
      -lpthread -ldl -lm \
      -o "$SCRIPT_DIR/abug-cli"
fi

chmod +x "$SCRIPT_DIR/abug-cli"
echo "=== Successfully built $SCRIPT_DIR/abug-cli ==="
"$SCRIPT_DIR/abug-cli" test
