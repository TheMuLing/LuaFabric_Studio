cmake_minimum_required(VERSION 3.31.6)

add_library(sodium SHARED
        lua_sodium.c
)

target_link_libraries(sodium
        lua
        log
        m
        ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/libsodium.a
)

target_include_directories(sodium PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_SOURCE_DIR}/lua
)

target_compile_options(sodium PRIVATE
        -fPIC
        -Wall
        -fvisibility=default
)