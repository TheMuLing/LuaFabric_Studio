// Lua 绑定：MMKV (v2.4.1) —— 高性能键值存储
// 用法：
//   local mmkv = require "mmkv"
//   mmkv.initialize("/data/data/包名/files/mmkv")   -- 必须先初始化
//   mmkv.set("default", "key", "value")             -- string/boolean/integer/number
//   mmkv.set("default", "key", "value", 3600)       -- 可选过期时间（秒）
//   mmkv.get("default", "key")                      -- 返回存储的值或 nil
//   mmkv.contains("default", "key")
//   mmkv.remove("default", "key")
//   mmkv.clear("default")
//   mmkv.count("default")
//   mmkv.totalSize("default")
//   mmkv.allKeys("default")
//   mmkv.version()

extern "C" {
#include "lua.h"
#include "lauxlib.h"
}

#include "MMKV.h"

#include <string>
#include <string_view>

static MMKV *getMMKVInstance(lua_State *L, int idx) {
    const char *id = luaL_checkstring(L, idx);
    MMKV *mmkv = MMKV::mmkvWithID(std::string(id), MMKVConfig());
    mmkv->enableAutoKeyExpire();
    return mmkv;
}

static int l_mmkv_version(lua_State *L) {
    lua_pushstring(L, MMKV_VERSION);
    return 1;
}

static int l_mmkv_initialize(lua_State *L) {
    const char *rootDir = luaL_checkstring(L, 1);
    try {
        MMKV::initializeMMKV(std::string(rootDir));
        lua_pushboolean(L, true);
    } catch (std::exception &e) {
        lua_pushboolean(L, false);
        lua_pushstring(L, e.what());
        return 2;
    }
    return 1;
}

static int l_mmkv_set(lua_State *L) {
    const char *key = luaL_checkstring(L, 2);
    uint32_t expire = 0;
    if (lua_gettop(L) >= 4 && !lua_isnil(L, 4)) {
        lua_Integer e = luaL_checkinteger(L, 4);
        if (e < 0) return luaL_error(L, "mmkv.set: expire must be >= 0");
        expire = (uint32_t)e;
    }
    try {
        MMKV *mmkv = getMMKVInstance(L, 1);
        bool ok = false;
        std::string_view keyView(key);
        if (lua_isnil(L, 3)) {
            ok = mmkv->removeValueForKey(keyView);
        } else if (lua_isboolean(L, 3)) {
            bool v = lua_toboolean(L, 3);
            ok = expire > 0 ? mmkv->set(v, keyView, expire) : mmkv->set(v, keyView);
        } else if (lua_isinteger(L, 3)) {
            lua_Integer v = lua_tointeger(L, 3);
            ok = expire > 0 ? mmkv->set((int64_t)v, keyView, expire) : mmkv->set((int64_t)v, keyView);
        } else if (lua_isnumber(L, 3)) {
            double v = lua_tonumber(L, 3);
            ok = expire > 0 ? mmkv->set(v, keyView, expire) : mmkv->set(v, keyView);
        } else if (lua_isstring(L, 3)) {
            size_t len = 0;
            const char *s = lua_tolstring(L, 3, &len);
            std::string_view v(s, len);
            ok = expire > 0 ? mmkv->set(v, keyView, expire) : mmkv->set(v, keyView);
        } else {
            return luaL_error(L, "mmkv.set: unsupported value type");
        }
        lua_pushboolean(L, ok);
        return 1;
    } catch (std::exception &e) {
        return luaL_error(L, "mmkv.set: %s", e.what());
    }
}

static int l_mmkv_get(lua_State *L) {
    const char *key = luaL_checkstring(L, 2);
    try {
        MMKV *mmkv = getMMKVInstance(L, 1);
        std::string_view keyView(key);
        mmkv::MMBuffer data = mmkv->getDataForKey(keyView);
        size_t n = data.length();
        if (n == 0) {
            lua_pushnil(L);
            return 1;
        }
        const uint8_t *p = (const uint8_t *)data.getPtr();

        // bool: single byte 0/1
        if (n == 1 && (p[0] == 0 || p[0] == 1)) {
            lua_pushboolean(L, p[0] != 0);
            return 1;
        }

        // string: [varint length][bytes]
        {
            size_t pos = 0;
            uint64_t len = 0;
            int shift = 0;
            while (pos < n && shift < 64) {
                uint8_t b = p[pos++];
                len |= (uint64_t)(b & 0x7F) << shift;
                if (!(b & 0x80)) break;
                shift += 7;
            }
            if (pos < n && len == n - pos) {
                lua_pushlstring(L, (const char *)p + pos, (size_t)len);
                return 1;
            }
        }

        if (n == 8) {
            double d = 0;
            memcpy(&d, p, 8);
            lua_pushnumber(L, (lua_Number)d);
            return 1;
        }
        if (n == 4) {
            float f = 0;
            memcpy(&f, p, 4);
            lua_pushnumber(L, (lua_Number)f);
            return 1;
        }

        // integer: entire data is a single varint
        {
            size_t pos = 0;
            int shift = 0;
            while (pos < n && shift < 63) {
                uint8_t b = p[pos++];
                if (!(b & 0x80)) break;
                shift += 7;
            }
            if (pos == n) {
                uint64_t v = 0;
                int s = 0;
                for (size_t i = 0; i < n; i++) {
                    v |= (uint64_t)(p[i] & 0x7F) << s;
                    s += 7;
                }
                lua_pushinteger(L, (lua_Integer)(int64_t)v);
                return 1;
            }
        }

        // fallback: raw bytes
        lua_pushlstring(L, (const char *)p, n);
        return 1;
    } catch (std::exception &e) {
        return luaL_error(L, "mmkv.get: %s", e.what());
    }
}

static int l_mmkv_contains(lua_State *L) {
    try {
        MMKV *mmkv = getMMKVInstance(L, 1);
        lua_pushboolean(L, mmkv->containsKey(std::string_view(luaL_checkstring(L, 2))));
        return 1;
    } catch (std::exception &e) {
        return luaL_error(L, "mmkv.contains: %s", e.what());
    }
}

static int l_mmkv_remove(lua_State *L) {
    try {
        MMKV *mmkv = getMMKVInstance(L, 1);
        lua_pushboolean(L, mmkv->removeValueForKey(std::string_view(luaL_checkstring(L, 2))));
        return 1;
    } catch (std::exception &e) {
        return luaL_error(L, "mmkv.remove: %s", e.what());
    }
}

static int l_mmkv_clear(lua_State *L) {
    try {
        MMKV *mmkv = getMMKVInstance(L, 1);
        mmkv->clearAll();
        lua_pushboolean(L, true);
        return 1;
    } catch (std::exception &e) {
        return luaL_error(L, "mmkv.clear: %s", e.what());
    }
}

static int l_mmkv_count(lua_State *L) {
    try {
        MMKV *mmkv = getMMKVInstance(L, 1);
        lua_pushinteger(L, (lua_Integer)mmkv->count());
        return 1;
    } catch (std::exception &e) {
        return luaL_error(L, "mmkv.count: %s", e.what());
    }
}

static int l_mmkv_totalSize(lua_State *L) {
    try {
        MMKV *mmkv = getMMKVInstance(L, 1);
        lua_pushinteger(L, (lua_Integer)mmkv->totalSize());
        return 1;
    } catch (std::exception &e) {
        return luaL_error(L, "mmkv.totalSize: %s", e.what());
    }
}

static int l_mmkv_allKeys(lua_State *L) {
    try {
        MMKV *mmkv = getMMKVInstance(L, 1);
        auto keys = mmkv->allKeys();
        lua_createtable(L, (int)keys.size(), 0);
        int i = 1;
        for (const auto &k : keys) {
            lua_pushlstring(L, k.data(), k.size());
            lua_rawseti(L, -2, i++);
        }
        return 1;
    } catch (std::exception &e) {
        return luaL_error(L, "mmkv.allKeys: %s", e.what());
    }
}

static const luaL_Reg mmkvLib[] = {
    {"version", l_mmkv_version},
    {"initialize", l_mmkv_initialize},
    {"set", l_mmkv_set},
    {"get", l_mmkv_get},
    {"contains", l_mmkv_contains},
    {"remove", l_mmkv_remove},
    {"clear", l_mmkv_clear},
    {"count", l_mmkv_count},
    {"totalSize", l_mmkv_totalSize},
    {"allKeys", l_mmkv_allKeys},
    {nullptr, nullptr}
};

extern "C" int luaopen_mmkv(lua_State *L) {
    luaL_newlib(L, mmkvLib);
    return 1;
}