cmake_minimum_required(VERSION 3.31.6)

# MMKV Core (v2.4.1) + Lua 绑定
# 源文件与官方 Core/CMakeLists.txt 保持一致（去除 Windows/MSVC 分支）

enable_language(CXX)
enable_language(ASM)

# NDK toolchain 自动追加的 -Wl,--no-undefined 与 C++ 对象 + 静态库组合触发
# LLD 误报 undefined symbol，此 target 在 add_library 前将其移除
string(REPLACE "-Wl,--no-undefined-version" "" CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}")
string(REPLACE "-Wl,--no-undefined" "" CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}")

set(MMKV_SRCS
        lua_mmkv.cpp
        MMKV.cpp
        MMKV_Android.cpp
        MMKV_IO.cpp
        MMKV_OSX.cpp
        MMKVLog.cpp
        MMKVLog_Android.cpp
        CodedInputData.cpp
        CodedInputData_OSX.cpp
        CodedInputDataCrypt.cpp
        CodedInputDataCrypt_OSX.cpp
        CodedOutputData.cpp
        KeyValueHolder.cpp
        PBUtility.cpp
        MiniPBCoder.cpp
        MiniPBCoder_OSX.cpp
        MMBuffer.cpp
        InterProcessLock.cpp
        InterProcessLock_Android.cpp
        InterProcessLock_Win32.cpp
        MemoryFile.cpp
        MemoryFile_Android.cpp
        MemoryFile_Linux.cpp
        MemoryFile_Win32.cpp
        MemoryFile_OSX.cpp
        ThreadLock.cpp
        ThreadLock_Win32.cpp
        aes/AESCrypt.cpp
        aes/openssl/openssl_aes_core.cpp
        aes/openssl/openssl_cfb128.cpp
        aes/openssl/openssl_md5_dgst.cpp
        aes/openssl/openssl_md5_one.cpp
        crc32/crc32_armv8.cpp
        crc32/zlib/crc32.cpp
        )

add_library(mmkv SHARED ${MMKV_SRCS})

if (CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    target_sources(mmkv PRIVATE aes/openssl/openssl_aesv8-armx.S)
    set_source_files_properties(aes/openssl/openssl_aesv8-armx.S PROPERTIES
            COMPILE_OPTIONS "-march=armv8-a+crypto")
endif ()

target_include_directories(mmkv PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_compile_definitions(mmkv PRIVATE MMKV_ANDROID)

set_target_properties(mmkv PROPERTIES
        CXX_STANDARD 20
        CXX_STANDARD_REQUIRED ON
        POSITION_INDEPENDENT_CODE ON
        )

target_compile_options(mmkv PRIVATE
        -fexceptions
        -frtti
        -fno-lto
        -fvisibility=default
        )

target_link_options(mmkv PRIVATE -fno-lto)

target_link_libraries(mmkv
        lua
        z
        )

find_library(ZLIB_LIBRARY z)
if (NOT ZLIB_LIBRARY)
    target_compile_definitions(mmkv PRIVATE MMKV_EMBED_ZLIB=1)
endif ()