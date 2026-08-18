cmake_minimum_required(VERSION 3.31.6)

# zstd 静态库（lib 源码精简版，不含 dictBuilder/legacy）
# 版本由 G:\Desktop\zstd 仓库同步

set(ZSTD_SRCS
        lua_zstd.c
        common/entropy_common.c
        common/error_private.c
        common/fse_decompress.c
        common/debug.c
        common/xxhash.c
        common/pool.c
        common/threading.c
        common/zstd_common.c
        compress/hist.c
        compress/fse_compress.c
        compress/huf_compress.c
        compress/zstd_compress.c
        compress/zstd_compress_literals.c
        compress/zstd_compress_sequences.c
        compress/zstd_compress_superblock.c
        compress/zstd_double_fast.c
        compress/zstd_fast.c
        compress/zstd_lazy.c
        compress/zstd_ldm.c
        compress/zstd_opt.c
        compress/zstd_preSplit.c
        compress/zstdmt_compress.c
        decompress/huf_decompress.c
        decompress/zstd_ddict.c
        decompress/zstd_decompress.c
        decompress/zstd_decompress_block.c
        )

add_library(zstd SHARED ${ZSTD_SRCS})

target_include_directories(zstd PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/common
        ${CMAKE_CURRENT_SOURCE_DIR}/compress
        ${CMAKE_CURRENT_SOURCE_DIR}/decompress
        )

target_compile_options(zstd PRIVATE
        -fvisibility=default
        -Wall
        )

set_target_properties(zstd PROPERTIES C_STANDARD 11)

target_link_libraries(zstd
        lua
        z
        )