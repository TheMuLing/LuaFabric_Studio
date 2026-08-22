/*
** $Id: linit.c $
** Initialization of libraries for lua.c and other clients
** See Copyright Notice in lua.h
*/

#define linit_c
#define LUA_LIB

/*
** If you embed Lua in your program and need to open the standard
** libraries, call luaL_openlibs in your program. If you need a
** different set of libraries, copy this file to your project and edit
** it to suit your needs.
**
** You can also *preload* libraries, so that a later 'require' can
** open the library, which is already linked to the application.
** For that, do the following code:
**
**  luaL_getsubtable(L, LUA_REGISTRYINDEX, LUA_PRELOAD_TABLE);
**  lua_pushcfunction(L, luaopen_modname);
**  lua_setfield(L, -2, modname);
**  lua_pop(L, 1);  // remove PRELOAD table
*/

#include "lprefix.h"

#include <stddef.h>

#include "lua.h"

#include "lualib.h"
#include "lauxlib.h"
#include "ltranslator.h"

/* 声明libc库的初始化函数 */
int luaopen_libc(lua_State *L);

// clang and ffi libraries

/*
** Embedded class system Lua code
** This provides OOP functionality directly in Lua
*/
static const char *lua_class_system =
        "local _NIL = {}\n"
        "local function class(config)\n"
        "  local cls = config.extends or Object\n"
        "  local name = config.name\n"
        "  return setmetatable(config.static or {}, {\n"
        "    __call = function(t, ...)\n"
        "      local args = {...}\n"
        "      local constructor = function(super, ...)\n"
        "        if table.size(args) == 1 and type(args[1]) == \"table\" and luajava.instanceof(args[1][1], cls) then\n"
        "          super = function(...)\n"
        "            return args[1][1]\n"
        "          end\n"
        "        end\n"
        "        return (config.constructor or function(super, ...)\n"
        "          return super(...)\n"
        "        end)(super, ...)\n"
        "      end\n"
        "      local fields = {}\n"
        "      if config.fields then\n"
        "        for k, v in pairs(config.fields) do\n"
        "          fields[k] = v\n"
        "        end\n"
        "      end\n"
        "      local methods = config.methods or {}\n"
        "      local overrides = config.overrides or {}\n"
        "      local obj = constructor(cls, ...) or cls(...)\n"
        "      local oldmt, mt = getmetatable(obj), {}\n"
        "      for k, v in pairs(oldmt) do\n"
        "        mt[k] = v\n"
        "      end\n"
        "      local ___tostring = mt.__tostring\n"
        "      local function index(self, key, hasParam, ...)\n"
        "        debug.setmetatable(self, oldmt)\n"
        "        local r\n"
        "        if hasParam then\n"
        "          r = self[key](...)\n"
        "        else\n"
        "          r = self[key]\n"
        "        end\n"
        "        debug.setmetatable(self, mt)\n"
        "        return type(r) == \"function\" and function(...)\n"
        "          return index(self, key, true, ...)\n"
        "        end or r\n"
        "      end\n"
        "      mt.__index = function(self, key)\n"
        "        if fields[key] ~= nil then\n"
        "          if fields[key] == _NIL then\n"
        "            return\n"
        "          end\n"
        "          return fields[key]\n"
        "        end\n"
        "        if overrides[key] then\n"
        "          return function(...)\n"
        "            return overrides[key](self, index(self, key), ...)\n"
        "          end\n"
        "        end\n"
        "        if methods[key] then\n"
        "          return function(...)\n"
        "            return methods[key](self, ...)\n"
        "          end\n"
        "        end\n"
        "        return index(self, key)\n"
        "      end\n"
        "      mt.__newindex = function(self, key, val)\n"
        "        if fields[key] ~= nil then\n"
        "          if val == nil then\n"
        "            val = _NIL\n"
        "          end\n"
        "          fields[key] = val\n"
        "          return\n"
        "        end\n"
        "        debug.setmetatable(self, oldmt)[key] = val\n"
        "        debug.setmetatable(self, mt)\n"
        "      end\n"
        "      mt.__tostring = function(self)\n"
        "        local s = ___tostring(self)\n"
        "        s = s:match(\"(@.+)\") or s:match(\"({.+})\")\n"
        "        return (name or tostring(cls):sub(7)) .. s\n"
        "      end\n"
        "      mt.__type = function(self)\n"
        "        return \"userdata\"\n"
        "      end\n"
        "      debug.setmetatable(obj, mt)\n"
        "      if config.init then\n"
        "        config.init(obj)\n"
        "      end\n"
        "      return obj\n"
        "    end,\n"
        "    __index = function(t, key)\n"
        "      return cls[key]\n"
        "    end,\n"
        "    __tostring = function(t)\n"
        "      return name and \"class \" .. name or tostring(cls)\n"
        "    end,\n"
        "    __type = function(t)\n"
        "      return tostring(t)\n"
        "    end\n"
        "  })\n"
        "end\n"
        "\n"
        "_G.newClass = class\n";

/*
** these libs are loaded by lua.c and are readily available to any Lua
** program
*/
/*
** Standard Libraries. (Must be listed in the same ORDER of their
** respective constants LUA_<libname>K.)
** Note: Custom libraries are added after standard libraries with consecutive masks.
*/
static const luaL_Reg stdlibs[] = {
        {LUA_GNAME,        luaopen_base},
        {LUA_LOADLIBNAME,  luaopen_package},
        {LUA_COLIBNAME,    luaopen_coroutine},
        {LUA_DBLIBNAME,    luaopen_debug},
        {LUA_IOLIBNAME,    luaopen_io},
        {LUA_MATHLIBNAME,  luaopen_math},
        {LUA_OSLIBNAME,    luaopen_os},
        {LUA_STRLIBNAME,   luaopen_string},
        {LUA_TABLIBNAME,   luaopen_table},
        {LUA_UTF8LIBNAME,  luaopen_utf8},
        {LUA_BOOLIBNAME,   luaopen_bool},
        {LUA_UDATALIBNAME, luaopen_userdata},
        {LUA_VMLIBNAME,    luaopen_vm},
        {LUA_BITLIBNAME,   luaopen_bit},
        {LUA_PTRLIBNAME,   luaopen_ptr},
        {"translator",     luaopen_translator},
        {"libc",           luaopen_libc},
        {NULL, NULL}
};

/*
** require and preload selected standard libraries
*/
LUALIB_API void luaL_openselectedlibs(lua_State *L, int load, int preload) {
    int mask;
    const luaL_Reg *lib;
    luaL_getsubtable(L, LUA_REGISTRYINDEX, LUA_PRELOAD_TABLE);
    for (lib = stdlibs, mask = 1; lib->name != NULL; lib++, mask <<= 1) {
        if (load & mask) {  /* selected? */
            luaL_requiref(L, lib->name, lib->func, 1);  /* require library */
            lua_pop(L, 1);  /* remove result from the stack */
        } else if (preload & mask) {  /* selected? */
            lua_pushcfunction(L, lib->func);
            lua_setfield(L, -2, lib->name);  /* add library to PRELOAD table */
        }
    }
    lua_pop(L, 1);  /* remove PRELOAD table */
}

/*
** All standard libraries
*/
static const luaL_Reg loadedlibs[] = {
        {LUA_GNAME,        luaopen_base},
        {LUA_LOADLIBNAME,  luaopen_package},
        {LUA_COLIBNAME,    luaopen_coroutine},
        {LUA_TABLIBNAME,   luaopen_table},
        {LUA_IOLIBNAME,    luaopen_io},
        {LUA_OSLIBNAME,    luaopen_os},
        {LUA_STRLIBNAME,   luaopen_string},
        {LUA_UTF8LIBNAME,  luaopen_utf8},
        {LUA_MATHLIBNAME,  luaopen_math},
        {LUA_BOOLIBNAME,   luaopen_bool},
        {LUA_UDATALIBNAME, luaopen_userdata},
        {LUA_VMLIBNAME,    luaopen_vm},
        {LUA_DBLIBNAME,    luaopen_debug},
        {LUA_BITLIBNAME,   luaopen_bit},
        {LUA_PTRLIBNAME,   luaopen_ptr},
        {"translator",     luaopen_translator},
        {"libc",           luaopen_libc},

        {NULL, NULL}
};

LUALIB_API void luaL_openlibs(lua_State *L) {
    const luaL_Reg *lib;
    /* "require" functions from 'loadedlibs' and set results to global table */
    for (lib = loadedlibs; lib->func; lib++) {
        luaL_requiref(L, lib->name, lib->func, 1);
        lua_pop(L, 1);  /* remove lib */
    }

    /* Load and register the class system globally */
    if (luaL_loadstring(L, lua_class_system) == LUA_OK) {
        if (lua_pcall(L, 0, 0, 0) != LUA_OK) {
            /* If loading fails, print error but don't crash */
            const char *error = lua_tostring(L, -1);
            fprintf(stderr, "Class system initialization failed: %s\n", error);
            lua_pop(L, 1);
        }
    } else {
        /* Compilation error - should not happen with static string */
        const char *error = lua_tostring(L, -1);
        fprintf(stderr, "Class system compilation failed: %s\n", error);
        lua_pop(L, 1);
    }
}
