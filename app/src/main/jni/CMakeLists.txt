cmake_minimum_required(VERSION 3.31.6)
project(lua-libraries)

# 基础配置
set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)

# 构建类型检测和优化配置
# 检查是否从Gradle传递了构建类型
if (NOT DEFINED ANDROID_BUILD_TYPE)
    set(ANDROID_BUILD_TYPE "release")
endif ()

message(STATUS "Android Build Type: ${ANDROID_BUILD_TYPE}")
message(STATUS "Android ABI: ${CMAKE_ANDROID_ARCH_ABI}")

# 基础优化标志（所有平台）
set(BASE_C_FLAGS "")
set(BASE_CXX_FLAGS "")
set(BASE_LINKER_FLAGS "")

# Android 特定配置
if (ANDROID)
    message(STATUS "Building for Android platform")

    # 根据构建类型设置优化级别
    if (ANDROID_BUILD_TYPE STREQUAL "release")
        message(STATUS "Release build - maximum optimization")
        set(BASE_C_FLAGS "-O3 -DNDEBUG -fomit-frame-pointer")
        set(BASE_CXX_FLAGS "-O3 -DNDEBUG -fomit-frame-pointer")
        set(BASE_LINKER_FLAGS "-Wl,--gc-sections -Wl,--as-needed")

        # Release版本启用LTO
        if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
            set(BASE_C_FLAGS "${BASE_C_FLAGS} -flto=thin")
            set(BASE_CXX_FLAGS "${BASE_CXX_FLAGS} -flto=thin")
            set(BASE_LINKER_FLAGS "${BASE_LINKER_FLAGS} -flto=thin")
        endif ()

    elseif (ANDROID_BUILD_TYPE STREQUAL "debug")
        message(STATUS "Debug build - debugging enabled")
        set(BASE_C_FLAGS "-O1 -g -DDEBUG")
        set(BASE_CXX_FLAGS "-O1 -g -DDEBUG")
        set(BASE_LINKER_FLAGS "")

    else ()
        message(WARNING "Unknown build type: ${ANDROID_BUILD_TYPE}, defaulting to release")
        set(BASE_C_FLAGS "-O3 -DNDEBUG -fomit-frame-pointer")
        set(BASE_CXX_FLAGS "-O3 -DNDEBUG -fomit-frame-pointer")
        set(BASE_LINKER_FLAGS "-Wl,--gc-sections -Wl,--as-needed")
    endif ()

    # 所有Android构建共用的标志
    set(BASE_C_FLAGS "${BASE_C_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden")
    set(BASE_CXX_FLAGS "${BASE_CXX_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden -fexceptions -frtti")
    set(BASE_LINKER_FLAGS "${BASE_LINKER_FLAGS} -Wl,--exclude-libs,ALL")

    # 检查 NDK 版本是否支持 16KB 页面
    if (NOT CMAKE_ANDROID_NDK_VERSION VERSION_LESS 27)
        message(STATUS "NDK 27+ detected, enabling 16KB page support")
        # 添加 16KB 页面对齐标志 - 修复：移除多余的 }
        set(BASE_LINKER_FLAGS "${BASE_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
        # 定义宏以便代码中检测
        add_definitions(-DANDROID_SUPPORT_16KB_PAGES)
    else ()
        message(STATUS "NDK < 27, using default 4KB page alignment")
        set(BASE_LINKER_FLAGS "${BASE_LINKER_FLAGS} -Wl,-z,max-page-size=4096 -Wl,-z,common-page-size=4096")
    endif ()

    # 架构特定优化
    if (CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
        message(STATUS "Optimizing for ARM64")
        set(BASE_C_FLAGS "${BASE_C_FLAGS} -march=armv8-a+crc")
        set(BASE_CXX_FLAGS "${BASE_CXX_FLAGS} -march=armv8-a+crc")
    elseif (CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
        message(STATUS "Optimizing for ARMv7a with NEON")
        set(BASE_C_FLAGS "${BASE_C_FLAGS} -march=armv7-a -mfpu=neon")
        set(BASE_CXX_FLAGS "${BASE_CXX_FLAGS} -march=armv7-a -mfpu=neon")
    endif ()
else ()
    # 非Android平台的默认优化
    message(STATUS "Non-Android platform build")
    set(BASE_C_FLAGS "-O2")
    set(BASE_CXX_FLAGS "-O2")
endif ()

# 应用全局标志
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${BASE_C_FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${BASE_CXX_FLAGS}")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${BASE_LINKER_FLAGS}")

# Lua 库配置
set(LUA_INCLUDE_DIR ${CMAKE_SOURCE_DIR}/lua)
include_directories(${LUA_INCLUDE_DIR})

# 智能添加子目录函数（所有模块统一处理）
function(add_and_optimize_library dir)
    if (EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/${dir})
        message(STATUS "Adding library: ${dir}")
        add_subdirectory(${dir})

        # 尝试获取目标名称
        set(TARGET_NAME "")
        if (TARGET ${dir})
            set(TARGET_NAME ${dir})
        elseif (TARGET ${dir}_lib)
            set(TARGET_NAME ${dir}_lib)
        elseif (TARGET lib${dir})
            set(TARGET_NAME lib${dir})
        endif ()

        # 应用目标优化
        if (NOT TARGET_NAME STREQUAL "")
            # 所有模块保持符号可见
            target_compile_options(${TARGET_NAME} PRIVATE
                    -fvisibility=default
            )

            # 所有模块都尝试导出 luaopen_xxx 函数
            if (ANDROID)
                target_link_options(${TARGET_NAME} PRIVATE
                        "-Wl,--export-dynamic-symbol=luaopen_${dir}"
                )
            endif ()

            message(STATUS "  -> Module: ${TARGET_NAME}, exporting luaopen_${dir}")

            # 通用的优化选项（所有目标）
            target_compile_options(${TARGET_NAME} PRIVATE
                    -ffunction-sections
                    -fdata-sections
            )
        else ()
            message(WARNING "Could not find target for directory: ${dir}")
        endif ()
    else ()
        message(STATUS "Skipping missing directory: ${dir}")
    endif ()
endfunction()

# 添加所有库
add_and_optimize_library(lua)
add_and_optimize_library(aes)
add_and_optimize_library(bson)
add_and_optimize_library(canvas)
add_and_optimize_library(cjson)
add_and_optimize_library(json)
add_and_optimize_library(lfs)
add_and_optimize_library(luajava)
add_and_optimize_library(luaparser)
add_and_optimize_library(socket)
add_and_optimize_library(ffi)
add_and_optimize_library(sensor)
add_and_optimize_library(sodium)
add_and_optimize_library(mmkv)
add_and_optimize_library(zstd)
add_and_optimize_library(gl)
add_and_optimize_library(luv)
add_and_optimize_library(rawio)
add_and_optimize_library(regex)
add_and_optimize_library(zlib)
add_and_optimize_library(zip)
add_and_optimize_library(unix)
add_and_optimize_library(xml)
add_and_optimize_library(time)
add_and_optimize_library(tcc)
add_and_optimize_library(decrypt)
add_and_optimize_library(lanes_core)