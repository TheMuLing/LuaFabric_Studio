cmake_minimum_required(VERSION 3.31.6)

set(AES_SRCS
        micro_aes.c
        lua_micro_aes.c
)

add_library(aes SHARED ${AES_SRCS})

# 链接必要的库
target_link_libraries(aes
        lua
        log
        m
)

# 包含目录
target_include_directories(aes PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_SOURCE_DIR}/lua
)

# 编译选项
target_compile_options(aes PRIVATE
        -fPIC
        -Wall
        -Werror
        -fvisibility=default
)

target_compile_definitions(aes PRIVATE AES___=256)