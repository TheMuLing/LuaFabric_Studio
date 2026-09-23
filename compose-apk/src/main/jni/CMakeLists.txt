cmake_minimum_required(VERSION 3.31.6)
project(lua-libraries-compose)

# 基础配置
set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)

# app/src/main/jni 下共享 native 源码（lua + luajava），跨模块各自构建不冲突
set(APP_JNI_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../../app/src/main/jni)
set(LUA_INCLUDE_DIR ${APP_JNI_ROOT}/lua)
include_directories(${LUA_INCLUDE_DIR})

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
        set(BASE_LINKER_FLAGS "${BASE_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
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

# 智能添加子目录函数（兼容绝对路径：目录名 = 目标名）
function(add_and_optimize_library dir)
    if (EXISTS ${dir})
        get_filename_component(_name ${dir} NAME)
        message(STATUS "Adding library: ${_name} (${dir})")
        add_subdirectory(${dir} ${CMAKE_CURRENT_BINARY_DIR}/${_name})

        # 尝试获取目标名称
        set(TARGET_NAME "")
        if (TARGET ${_name})
            set(TARGET_NAME ${_name})
        elseif (TARGET ${_name}_lib)
            set(TARGET_NAME ${_name}_lib)
        elseif (TARGET lib${_name})
            set(TARGET_NAME lib${_name})
        endif ()

        # 应用目标优化
        if (NOT TARGET_NAME STREQUAL "")
            target_compile_options(${TARGET_NAME} PRIVATE
                    -fvisibility=default
            )

            # 导出 luaopen 函数供 require 使用
            if (ANDROID)
                set(_EXPORT_FUNC "luaopen_${_name}")
                target_link_options(${TARGET_NAME} PRIVATE
                        "-Wl,--export-dynamic-symbol=${_EXPORT_FUNC}"
                )
            endif ()

            message(STATUS "  -> Module: ${TARGET_NAME}, exporting ${_EXPORT_FUNC}")

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

# 仅编译 lua + luajava（Compose 产物皮肤：移除其余 16 库）
add_and_optimize_library(${APP_JNI_ROOT}/lua)
add_and_optimize_library(${APP_JNI_ROOT}/luajava)