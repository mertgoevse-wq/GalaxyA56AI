cmake_minimum_required(VERSION 3.22.1)
project(galaxya56 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# llama.cpp (vendored, MIT) — real GGUF inference on ARM64 CPU (NEON)
# ---------------------------------------------------------------------------
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN          OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)

add_subdirectory(third_party/llama.cpp EXCLUDE_FROM_ALL)

# ---------------------------------------------------------------------------
# App native library: hardware detector + llama engine + JNI bridge
# ---------------------------------------------------------------------------
add_library(galaxya56 SHARED
    hardware_detector.cpp
    llama_engine.cpp
    jni_bridge.cpp
)

target_include_directories(galaxya56 PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp/ggml/include
)

find_library(log-lib log)
find_library(vulkan-lib vulkan)
find_library(android-lib android)

target_link_libraries(galaxya56
    llama
    ${log-lib}
    ${android-lib}
    ${vulkan-lib}
    c++_shared
    dl
)

target_compile_options(galaxya56 PRIVATE -O3 -fexceptions -frtti -Wall -Wextra -Wno-unused-parameter)
target_compile_definitions(galaxya56 PRIVATE GALAXYA56_LLAMA=1)
