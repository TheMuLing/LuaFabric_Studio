// Lua 绑定：zstd —— Zstandard 压缩库
// 用法：
//   local zstd = require "zstd"
//   local packed = zstd.compress(data, 3)       -- data: string, 可选压缩级别(默认3)
//   local original = zstd.decompress(packed)    -- 解压
//   zstd.compressBound(len)                     -- 压缩后最大尺寸
//   zstd.decompressedSize(packed)               -- 解压后原始尺寸（-1=未知,-2=错误）
//   zstd.minCLevel() / zstd.maxCLevel() / zstd.defaultCLevel()
//   zstd.version()

#include <string.h>

#include "lua.h"
#include "lauxlib.h"

#include "zstd.h"

static int l_zstd_version(lua_State *L) {
    lua_pushstring(L, ZSTD_VERSION_STRING);
    return 1;
}

static int l_zstd_compress(lua_State *L) {
    size_t srcLen = 0;
    const char *src = luaL_checklstring(L, 1, &srcLen);
    int level = 3;
    if (lua_gettop(L) >= 2 && !lua_isnil(L, 2)) {
        level = (int)luaL_checkinteger(L, 2);
    }
    size_t bound = ZSTD_compressBound(srcLen);
    char *dst = (char *)lua_newuserdata(L, bound);
    size_t dstLen = ZSTD_compress(dst, bound, src, srcLen, level);
    if (ZSTD_isError(dstLen)) {
        lua_pushnil(L);
        lua_pushstring(L, ZSTD_getErrorName(dstLen));
        return 2;
    }
    lua_pushlstring(L, dst, dstLen);
    return 1;
}

static int l_zstd_decompress(lua_State *L) {
    size_t srcLen = 0;
    const char *src = luaL_checklstring(L, 1, &srcLen);
    unsigned long long frameSize = ZSTD_getFrameContentSize(src, srcLen);
    if (frameSize == ZSTD_CONTENTSIZE_ERROR) {
        lua_pushnil(L);
        lua_pushstring(L, "not a valid zstd frame");
        return 2;
    }
    if (frameSize == ZSTD_CONTENTSIZE_UNKNOWN) {
        lua_pushnil(L);
        lua_pushstring(L, "frame content size unknown, use decompressedSize check first");
        return 2;
    }
    char *dst = (char *)lua_newuserdata(L, (size_t)frameSize);
    size_t dstLen = ZSTD_decompress(dst, (size_t)frameSize, src, srcLen);
    if (ZSTD_isError(dstLen)) {
        lua_pushnil(L);
        lua_pushstring(L, ZSTD_getErrorName(dstLen));
        return 2;
    }
    lua_pushlstring(L, dst, dstLen);
    return 1;
}

static int l_zstd_compressBound(lua_State *L) {
    lua_pushinteger(L, (lua_Integer)ZSTD_compressBound((size_t)luaL_checkinteger(L, 1)));
    return 1;
}

static int l_zstd_decompressedSize(lua_State *L) {
    size_t srcLen = 0;
    const char *src = luaL_checklstring(L, 1, &srcLen);
    unsigned long long frameSize = ZSTD_getFrameContentSize(src, srcLen);
    if (frameSize == ZSTD_CONTENTSIZE_ERROR || frameSize == ZSTD_CONTENTSIZE_UNKNOWN) {
        lua_pushinteger(L, -1);
    } else {
        lua_pushinteger(L, (lua_Integer)frameSize);
    }
    return 1;
}

static int l_zstd_minCLevel(lua_State *L) {
    lua_pushinteger(L, ZSTD_minCLevel());
    return 1;
}

static int l_zstd_maxCLevel(lua_State *L) {
    lua_pushinteger(L, ZSTD_maxCLevel());
    return 1;
}

static int l_zstd_defaultCLevel(lua_State *L) {
    lua_pushinteger(L, ZSTD_defaultCLevel());
    return 1;
}

static int l_zstd_compressStream(lua_State *L) {
    size_t srcLen = 0;
    const char *src = luaL_checklstring(L, 1, &srcLen);
    int level = 3;
    if (lua_gettop(L) >= 2 && !lua_isnil(L, 2)) {
        level = (int)luaL_checkinteger(L, 2);
    }
    ZSTD_CStream *cstream = ZSTD_createCStream();
    if (!cstream) {
        lua_pushnil(L);
        lua_pushstring(L, "createCStream failed");
        return 2;
    }
    size_t r = ZSTD_initCStream(cstream, level);
    if (ZSTD_isError(r)) {
        ZSTD_freeCStream(cstream);
        lua_pushnil(L);
        lua_pushstring(L, ZSTD_getErrorName(r));
        return 2;
    }
    size_t bound = ZSTD_compressBound(srcLen);
    char *dst = (char *)lua_newuserdata(L, bound);
    ZSTD_outBuffer out = {dst, bound, 0};
    ZSTD_inBuffer in = {src, srcLen, 0};
    size_t remaining = ZSTD_compressStream2(cstream, &out, &in, ZSTD_e_end);
    size_t total = out.pos;
    ZSTD_freeCStream(cstream);
    if (ZSTD_isError(remaining)) {
        lua_pushnil(L);
        lua_pushstring(L, ZSTD_getErrorName(remaining));
        return 2;
    }
    lua_pushlstring(L, dst, total);
    return 1;
}

static int l_zstd_decompressStream(lua_State *L) {
    size_t srcLen = 0;
    const char *src = luaL_checklstring(L, 1, &srcLen);
    unsigned long long frameSize = ZSTD_getFrameContentSize(src, srcLen);
    if (frameSize == ZSTD_CONTENTSIZE_ERROR || frameSize == ZSTD_CONTENTSIZE_UNKNOWN) {
        lua_pushnil(L);
        lua_pushstring(L, "cannot determine content size");
        return 2;
    }
    ZSTD_DStream *dstream = ZSTD_createDStream();
    if (!dstream) {
        lua_pushnil(L);
        lua_pushstring(L, "createDStream failed");
        return 2;
    }
    ZSTD_initDStream(dstream);
    char *dst = (char *)lua_newuserdata(L, (size_t)frameSize);
    ZSTD_outBuffer out = {dst, (size_t)frameSize, 0};
    ZSTD_inBuffer in = {src, srcLen, 0};
    size_t r = ZSTD_decompressStream(dstream, &out, &in);
    size_t total = out.pos;
    ZSTD_freeDStream(dstream);
    if (ZSTD_isError(r)) {
        lua_pushnil(L);
        lua_pushstring(L, ZSTD_getErrorName(r));
        return 2;
    }
    lua_pushlstring(L, dst, total);
    return 1;
}

static const luaL_Reg zstdLib[] = {
    {"version", l_zstd_version},
    {"compress", l_zstd_compress},
    {"decompress", l_zstd_decompress},
    {"compressBound", l_zstd_compressBound},
    {"decompressedSize", l_zstd_decompressedSize},
    {"minCLevel", l_zstd_minCLevel},
    {"maxCLevel", l_zstd_maxCLevel},
    {"defaultCLevel", l_zstd_defaultCLevel},
    {"compressStream", l_zstd_compressStream},
    {"decompressStream", l_zstd_decompressStream},
    {NULL, NULL}
};

int luaopen_zstd(lua_State *L) {
    luaL_newlib(L, zstdLib);
    return 1;
}