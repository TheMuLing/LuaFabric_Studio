cmake_minimum_required(VERSION 3.31.6)

# 云居 DAU 上报专用客户端（VPN/WLAN 门控 + mbedTLS HTTPS POST）
add_library(yunju SHARED yunju.c)

target_include_directories(yunju PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/../mbedtls/include
        )

target_compile_options(yunju PRIVATE
        -fvisibility=default
        -Wall
        )

set_target_properties(yunju PROPERTIES C_STANDARD 99)

target_link_libraries(yunju
        mbedtls
        log
        )
