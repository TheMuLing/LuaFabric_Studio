cmake_minimum_required(VERSION 3.31.6)

# 自包含 yyjson Lua 绑定（参考 LuaForge-Studio 实现）。
# 无外部 Lua 模块依赖：Lua 侧 require('yyjson') 直接解析到本库的
# luaopen_yyjson，避免 mah0x211/lua-yyjson 在运行期 require('errno') 等
# 子模块导致缺失的问题。luaopen_yyjson 由父级 add_and_optimize_library 导出。

add_library(yyjson SHARED
        lua_yyjson.c
        yyjson.c
)

target_include_directories(yyjson PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(yyjson
        log
        lua
        m
)

target_compile_options(yyjson PRIVATE
        -DANDROID
        -std=gnu11
        -Wall
)