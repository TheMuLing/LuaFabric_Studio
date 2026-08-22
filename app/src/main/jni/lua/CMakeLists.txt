cmake_minimum_required(VERSION 3.31.6)

set(LUA_SRCS
        lapi.c
        lauxlib.c
        lbaselib.c
        lbitlib.c
        lboolib.c
        lcode.c
        lcorolib.c
        lctype.c
        ldblib.c
        ldebug.c
        ldo.c
        ldump.c
        lfunc.c
        lgc.c
        linit.c
        liolib.c
        llex.c
        llibc.c
        lmathlib.c
        lmem.c
        loadlib.c
        lobject.c
        lopcodes.c
        loslib.c
        lparser.c
        lptrlib.c
        lstate.c
        lstring.c
        lstrlib.c
        ltable.c
        ltablib.c
        ltm.c
        ltranslator.c
        lundump.c
        lutf8lib.c
        lvm.c
        lvmlib.c
        lzio.c
        ludatalib.c
        json_parser.c
)

# 创建静态库
add_library(lua STATIC ${LUA_SRCS})

# 链接 zlib
find_library(ZLIB_LIBRARY z)
target_link_libraries(lua log dl m ${ZLIB_LIBRARY})

# 编译选项
target_compile_options(lua PRIVATE
        -DANDROID -Wall -O2
        -DLUA_COMPAT_5_2
        -DLUA_COMPAT_5_1
        -DLUA_COMPAT_MAXN
        -DLUA_USE_LINUX
        -DLUA_PROTECT_FLAGS=0x03
        -DLUA_PROTECT_KEY=0x89ABCDEF
)

# 导出头文件目录
target_include_directories(lua PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})