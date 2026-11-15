cmake_minimum_required(VERSION 3.22.1)
project(galaxya56 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(GALAXYA56_VULKAN "Build llama.cpp Vulkan backend (needs glslc + host compiler)" OFF)
if(GALAXYA56_VULKAN)
    set(GGML_VULKAN ON CACHE BOOL "" FORCE)
    if(DEFINED CMAKE_ANDROID_NDK)
        if(CMAKE_HOST_WIN32)
            set(_vkBinfmt "windows-x86_64")
        elseif(CMAKE_HOST_APPLE)
            set(_vkBinfmt "macos-arm64")
        else()
            set(_vkBinfmt "linux-x86_64")
        endif()
        set(Vulkan_GLSLC_EXECUTABLE "${CMAKE_ANDROID_NDK}/shader-tools/${_vkBinfmt}/glslc${CMAKE_EXECUTABLE_SUFFIX}"
            CACHE FILEPATH "" FORCE)
        set(Vulkan_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/vulkan-headers"
            CACHE PATH "" FORCE)
        set(Vulkan_LIBRARY "${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/${_vkBinfmt}/sysroot/usr/lib/aarch64-linux-android/28/libvulkan.so"
            CACHE FILEPATH "" FORCE)
        message(STATUS "Vulkan: glslc=${Vulkan_GLSLC_EXECUTABLE}")
    endif()
endif()

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
set(GGML_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)

add_subdirectory(third_party/llama.cpp EXCLUDE_FROM_ALL)

# ---------------------------------------------------------------------------
# App native library: hardware detector + llama engine + JNI bridge
# ---------------------------------------------------------------------------
set(GALAXYA56_SOURCES
    hardware_detector.cpp
    llama_engine.cpp
    jni_bridge.cpp
    npu_probe.cpp
)
if(GALAXYA56_VULKAN)
    list(APPEND GALAXYA56_SOURCES vulkan_android_compat.cpp)
endif()
add_library(galaxya56 SHARED ${GALAXYA56_SOURCES})

target_include_directories(galaxya56 PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp/ggml/include
)
if(GALAXYA56_VULKAN)
    target_include_directories(galaxya56 PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/vulkan-headers)
    target_compile_definitions(galaxya56 PRIVATE GALAXYA56_HAS_VULKAN=1)
endif()

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
