cmake_minimum_required(VERSION 3.20)
project(abug_desktop_native LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Find the host platform's ncnn, never the Android library bundled under app/src/main.
find_package(ncnn CONFIG REQUIRED)
find_package(OpenMP QUIET COMPONENTS CXX)

# Homebrew's ncnn target may expose include/ncnn, while this project uses
# <ncnn/net.h> and <ncnn/layer.h>. Add the parent include directory explicitly.
# Never use the bundled Android NCNN headers/library for the Mac executable.
get_filename_component(ABUG_NCNN_PREFIX "${ncnn_DIR}/../../.." ABSOLUTE)
find_path(ABUG_NCNN_HEADER_ROOT
    NAMES ncnn/net.h
    HINTS "${ABUG_NCNN_PREFIX}/include"
          "/opt/homebrew/opt/ncnn/include"
          "/usr/local/opt/ncnn/include"
          "$ENV{HOMEBREW_PREFIX}/include"
    NO_DEFAULT_PATH)
if(NOT ABUG_NCNN_HEADER_ROOT)
    message(FATAL_ERROR "macOS ncnn/net.h not found: inspect brew list ncnn and ncnn_DIR=${ncnn_DIR}")
endif()
message(STATUS "ncnn headers: ${ABUG_NCNN_HEADER_ROOT}")

set(ABUG_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/..")
set(ABUG_CPP "${ABUG_ROOT}/app/src/main/cpp")
add_executable(abug-cli-macos
    abug_cli.cpp
    "${ABUG_CPP}/real_esrgan_engine.cpp"
    "${ABUG_CPP}/rife_engine.cpp"
    "${ABUG_CPP}/tile_engine.cpp"
    "${ABUG_CPP}/color_convert.cpp"
    "${ABUG_CPP}/quality_metrics.cpp"
    "${ABUG_CPP}/video_restoration.cpp")
target_include_directories(abug-cli-macos PRIVATE "${ABUG_ROOT}" "${ABUG_CPP}" "${ABUG_NCNN_HEADER_ROOT}")
target_link_libraries(abug-cli-macos PRIVATE ncnn)
if(OpenMP_CXX_FOUND)
    target_link_libraries(abug-cli-macos PRIVATE OpenMP::OpenMP_CXX)
endif()
set_target_properties(abug-cli-macos PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}")
