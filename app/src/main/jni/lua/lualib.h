/*
** $Id: lualib.h $
** Lua standard libraries
** See Copyright Notice in lua.h
*/


#ifndef lualib_h
#define lualib_h

#include "lua.h"


/* version suffix for environment variable names */
#define LUA_VERSUFFIX          "_" LUA_VERSION_MAJOR "_" LUA_VERSION_MINOR


LUAMOD_API int (luaopen_base)(lua_State *L);

#define LUA_COLIBNAME    "coroutine"

LUAMOD_API int (luaopen_coroutine)(lua_State *L);

#define LUA_TABLIBNAME    "table"

LUAMOD_API int (luaopen_table)(lua_State *L);

#define LUA_IOLIBNAME    "io"

LUAMOD_API int (luaopen_io)(lua_State *L);

#define LUA_OSLIBNAME    "os"

LUAMOD_API int (luaopen_os)(lua_State *L);

#define LUA_STRLIBNAME    "string"

LUAMOD_API int (luaopen_string)(lua_State *L);

#define LUA_UTF8LIBNAME    "utf8"

LUAMOD_API int (luaopen_utf8)(lua_State *L);

#define LUA_MATHLIBNAME    "math"

LUAMOD_API int (luaopen_math)(lua_State *L);

#define LUA_DBLIBNAME    "debug"

LUAMOD_API int (luaopen_debug)(lua_State *L);

#define LUA_BITLIBNAME    "bit"

LUAMOD_API int (luaopen_bit)(lua_State *L);

#define LUA_BOOLIBNAME    "bool"

LUAMOD_API int (luaopen_bool)(lua_State *L);

#define LUA_UDATALIBNAME    "userdata"

LUAMOD_API int (luaopen_userdata)(lua_State *L);

#define LUA_VMLIBNAME    "vm"

LUAMOD_API int (luaopen_vm)(lua_State *L);

#define LUA_PTRLIBNAME    "ptr"

LUAMOD_API int (luaopen_ptr)(lua_State *L);

/* translator library */
#define LUA_TRANSLATORLIBNAME    "translator"

LUAMOD_API int (luaopen_translator)(lua_State *L);

#define LUA_LOADLIBNAME    "package"

LUAMOD_API int (luaopen_package)(lua_State *L);


/* open all previous libraries */
LUALIB_API void (luaL_openlibs)(lua_State *L);


#endif
