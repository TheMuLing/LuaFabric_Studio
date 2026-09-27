cmake_minimum_required(VERSION 3.31.6)

# mbedTLS 2.28.9 LTS 静态库（官方 tarball library/ 子集）
file(GLOB MBEDTLS_SRCS ${CMAKE_CURRENT_SOURCE_DIR}/library/*.c)

add_library(mbedtls STATIC ${MBEDTLS_SRCS})

target_include_directories(mbedtls PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/library
        )

target_compile_options(mbedtls PRIVATE
        -fvisibility=default
        -Wall
        )

set_target_properties(mbedtls PROPERTIES C_STANDARD 99)
