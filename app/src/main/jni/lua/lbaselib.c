/*
** $Id: lbaselib.c $
** Basic library
** See Copyright Notice in lua.h
*/

#define lbaselib_c
#define LUA_LIB

#include "lprefix.h"


#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "lua.h"

#include "lauxlib.h"
#include "lualib.h"
#include "llimits.h"

#ifdef __ANDROID__

#include <android/log.h>

#define LOG_TAG "lua"
#define LOGD(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#endif

/* 声明libc库的初始化函数 */
extern int luaopen_libc(lua_State *L);

static int luaB_print(lua_State *L) {
    int n = lua_gettop(L);  /* number of arguments */
    int i;
    for (i = 1; i <= n; i++) {  /* for each argument */
        size_t l;
        const char *s = luaL_tolstring(L, i, &l);  /* convert it to string */
        if (i > 1)  /* not the first element? */
            lua_writestring("\t", 1);  /* add a tab before it */
        lua_writestring(s, l);  /* print it */
#ifdef __ANDROID__
        LOGD("%s", s);
#endif
        lua_pop(L, 1);  /* pop result */
    }
    lua_writeline();
    return 0;
}


/*
** Creates a warning with all given arguments.
** Check first for errors; otherwise an error may interrupt
** the composition of a warning, leaving it unfinished.
*/
static int luaB_warn(lua_State *L) {
    int n = lua_gettop(L);  /* number of arguments */
    int i;
    luaL_checkstring(L, 1);  /* at least one argument */
    for (i = 2; i <= n; i++)
        luaL_checkstring(L, i);  /* make sure all arguments are strings */
    for (i = 1; i < n; i++)  /* compose warning */
        lua_warning(L, lua_tostring(L, i), 1);
    lua_warning(L, lua_tostring(L, n), 0);  /* close warning */
    return 0;
}


#define SPACECHARS    " \f\n\r\t\v"

static const char *b_str2int(const char *s, unsigned base, lua_Integer *pn) {
    lua_Unsigned n = 0;
    int neg = 0;
    s += strspn(s, SPACECHARS);  /* skip initial spaces */
    if (*s == '-') {
        s++;
        neg = 1;
    }  /* handle sign */
    else if (*s == '+') s++;
    if (!isalnum(cast_uchar(*s)))  /* no digit? */
        return NULL;
    do {
        unsigned digit = cast_uint(isdigit(cast_uchar(*s))
                                   ? *s - '0'
                                   : (toupper(cast_uchar(*s)) - 'A') + 10);
        if (digit >= base) return NULL;  /* invalid numeral */
        n = n * base + digit;
        s++;
    } while (isalnum(cast_uchar(*s)));
    s += strspn(s, SPACECHARS);  /* skip trailing spaces */
    *pn = (lua_Integer) ((neg) ? (0u - n) : n);
    return s;
}


static int luaB_tonumber(lua_State *L) {
    if (lua_isnoneornil(L, 2)) {  /* standard conversion? */
        if (lua_type(L, 1) == LUA_TNUMBER) {  /* already a number? */
            lua_settop(L, 1);  /* yes; return it */
            return 1;
        } else {
            size_t l;
            const char *s = lua_tolstring(L, 1, &l);
            if (s != NULL && lua_stringtonumber(L, s) == l + 1)
                return 1;  /* successful conversion to number */
            /* else not a number */
            luaL_checkany(L, 1);  /* (but there must be some parameter) */
        }
    } else {
        size_t l;
        const char *s;
        lua_Integer n = 0;  /* to avoid warnings */
        lua_Integer base = luaL_checkinteger(L, 2);
        luaL_checktype(L, 1, LUA_TSTRING);  /* no numbers as strings */
        s = lua_tolstring(L, 1, &l);
        luaL_argcheck(L, 2 <= base && base <= 36, 2, "base out of range");
        if (b_str2int(s, cast_uint(base), &n) == s + l) {
            lua_pushinteger(L, n);
            return 1;
        }  /* else not a number */
    }  /* else not a number */
    luaL_pushfail(L);  /* not a number */
    return 1;
}


static int luaB_tointeger(lua_State *L) {
    if (lua_type(L, 1) == LUA_TNUMBER) {
        if (lua_isinteger(L, 1)) {
            lua_settop(L, 1);
            return 1;
        } else {
            lua_Number n = lua_tonumber(L, 1);
            lua_pushinteger(L, (lua_Integer) n);
            return 1;
        }
    } else {
        size_t l;
        const char *s = luaL_tolstring(L, 1, &l);
        if (s != NULL && lua_stringtonumber(L, s) == l + 1) {
            lua_Number n = lua_tonumber(L, 1);
            lua_pushinteger(L, (lua_Integer) n);
            return 1;
        }
    }
    lua_pushnil(L);
    return 1;
}


static int luaB_error(lua_State *L) {
    int level = (int) luaL_optinteger(L, 2, 1);
    lua_settop(L, 1);
    if (lua_type(L, 1) == LUA_TSTRING && level > 0) {
        luaL_where(L, level);   /* add extra information */
        lua_pushvalue(L, 1);
        lua_concat(L, 2);
    }
    return lua_error(L);
}


static int luaB_getmetatable(lua_State *L) {
    luaL_checkany(L, 1);
    if (!lua_getmetatable(L, 1)) {
        lua_pushnil(L);
        return 1;  /* no metatable */
    }
    luaL_getmetafield(L, 1, "__metatable");
    return 1;  /* returns either __metatable field (if present) or metatable */
}


static int luaB_setmetatable(lua_State *L) {
    int t = lua_type(L, 2);
    luaL_checktype(L, 1, LUA_TTABLE);
    luaL_argexpected(L, t == LUA_TNIL || t == LUA_TTABLE, 2, "nil or table");

    if (l_unlikely(luaL_getmetafield(L, 1, "__metatable") != LUA_TNIL))
        return luaL_error(L, "cannot change a protected metatable");
    lua_settop(L, 2);
    lua_setmetatable(L, 1);
    return 1;
}


static int luaB_rawequal(lua_State *L) {
    luaL_checkany(L, 1);
    luaL_checkany(L, 2);
    lua_pushboolean(L, lua_rawequal(L, 1, 2));
    return 1;
}


static int luaB_rawlen(lua_State *L) {
    int t = lua_type(L, 1);
    luaL_argexpected(L, t == LUA_TTABLE || t == LUA_TSTRING, 1,
                     "table or string");
    lua_pushinteger(L, l_castU2S(lua_rawlen(L, 1)));
    return 1;
}


static int luaB_rawget(lua_State *L) {
    luaL_checktype(L, 1, LUA_TTABLE);
    luaL_checkany(L, 2);
    lua_settop(L, 2);
    lua_rawget(L, 1);
    return 1;
}

static int luaB_rawset(lua_State *L) {
    luaL_checktype(L, 1, LUA_TTABLE);
    luaL_checkany(L, 2);
    luaL_checkany(L, 3);
    lua_settop(L, 3);
    lua_rawset(L, 1);
    return 1;
}


static int pushmode(lua_State *L, int oldmode) {
    if (oldmode == -1)
        luaL_pushfail(L);  /* invalid call to 'lua_gc' */
    else
        lua_pushstring(L, (oldmode == LUA_GCINC) ? "incremental"
                                                 : "generational");
    return 1;
}


/*
** check whether call to 'lua_gc' was valid (not inside a finalizer)
*/
#define checkvalres(res) { if (res == -1) break; }

static int luaB_collectgarbage(lua_State *L) {
    static const char *const opts[] = {"stop", "restart", "collect",
                                       "count", "step", "setpause", "setstepmul",
                                       "isrunning", "generational", "incremental", "param", NULL};
    static const int optsnum[] = {LUA_GCSTOP, LUA_GCRESTART, LUA_GCCOLLECT,
                                  LUA_GCCOUNT, LUA_GCSTEP, LUA_GCSETPAUSE, LUA_GCSETSTEPMUL,
                                  LUA_GCISRUNNING, LUA_GCGEN, LUA_GCINC, LUA_GCPARAM};
    int o = optsnum[luaL_checkoption(L, 1, "collect", opts)];
    switch (o) {
        case LUA_GCCOUNT: {
            int k = lua_gc(L, o);
            int b = lua_gc(L, LUA_GCCOUNTB);
            checkvalres(k);
            lua_pushnumber(L, (lua_Number) k + ((lua_Number) b / 1024));
            return 1;
        }
        case LUA_GCSTEP: {
            lua_Integer n = luaL_optinteger(L, 2, 0);
            int res = lua_gc(L, o, cast_sizet(n));
            checkvalres(res);
            lua_pushboolean(L, res);
            return 1;
        }
        case LUA_GCSETPAUSE:
        case LUA_GCSETSTEPMUL: {
            int p = (int) luaL_optinteger(L, 2, 0);
            int previous = lua_gc(L, o, p);
            checkvalres(previous);
            lua_pushinteger(L, previous);
            return 1;
        }
        case LUA_GCISRUNNING: {
            int res = lua_gc(L, o);
            checkvalres(res);
            lua_pushboolean(L, res);
            return 1;
        }
        case LUA_GCGEN: {
            int minormul = (int) luaL_optinteger(L, 2, 0);
            int majormul = (int) luaL_optinteger(L, 3, 0);
            return pushmode(L, lua_gc(L, o, minormul, majormul));
        }
        case LUA_GCINC: {
            int pause = (int) luaL_optinteger(L, 2, 0);
            int stepmul = (int) luaL_optinteger(L, 3, 0);
            int stepsize = (int) luaL_optinteger(L, 4, 0);
            return pushmode(L, lua_gc(L, o, pause, stepmul, stepsize));
        }
        case LUA_GCPARAM: {
            static const char *const params[] = {
                    "minormul", "majorminor", "minormajor",
                    "pause", "stepmul", "stepsize", NULL};
            static const char pnum[] = {
                    LUA_GCPMINORMUL, LUA_GCPMAJORMINOR, LUA_GCPMINORMAJOR,
                    LUA_GCPPAUSE, LUA_GCPSTEPMUL, LUA_GCPSTEPSIZE};
            int p = pnum[luaL_checkoption(L, 2, NULL, params)];
            lua_Integer value = luaL_optinteger(L, 3, -1);
            lua_pushinteger(L, lua_gc(L, o, p, (int) value));
            return 1;
        }
        default: {
            int res = lua_gc(L, o);
            checkvalres(res);
            lua_pushinteger(L, res);
            return 1;
        }
    }
    luaL_pushfail(L);  /* invalid call (inside a finalizer) */
    return 1;
}


static int luaB_type(lua_State *L) {
    int t = lua_type(L, 1);
    luaL_argcheck(L, t != LUA_TNONE, 1, "value expected");
    lua_pushstring(L, lua_typename(L, t));
    return 1;
}


static int luaB_next(lua_State *L) {
    luaL_checktype(L, 1, LUA_TTABLE);
    lua_settop(L, 2);  /* create a 2nd argument if there isn't one */
    if (lua_next(L, 1))
        return 2;
    else {
        lua_pushnil(L);
        return 1;
    }
}


static int pairscont(lua_State *L, int status, lua_KContext k) {
    (void) L;
    (void) status;
    (void) k;  /* unused */
    return 3;
}

static int luaB_pairs(lua_State *L) {
    luaL_checkany(L, 1);
    if (luaL_getmetafield(L, 1, "__pairs") == LUA_TNIL) {  /* no metamethod? */
        lua_pushcfunction(L, luaB_next);  /* will return generator, */
        lua_pushvalue(L, 1);  /* state, */
        lua_pushnil(L);  /* and initial value */
    } else {
        lua_pushvalue(L, 1);  /* argument 'self' to metamethod */
        lua_callk(L, 1, 3, 0, pairscont);  /* get 3 values from metamethod */
    }
    return 3;
}


/*
** Traversal function for 'ipairs'
*/
static int ipairsaux(lua_State *L) {
    lua_Integer i = luaL_checkinteger(L, 2);
    i = luaL_intop(+, i, 1);
    lua_pushinteger(L, i);
    return (lua_geti(L, 1, i) == LUA_TNIL) ? 1 : 2;
}


/*
** 'ipairs' function. Returns 'ipairsaux', given "table", 0.
** (The given "table" may not be a table.)
*/
static int luaB_ipairs(lua_State *L) {
    luaL_checkany(L, 1);
    lua_pushcfunction(L, ipairsaux);  /* iteration function */
    lua_pushvalue(L, 1);  /* state */
    lua_pushinteger(L, 0);  /* initial value */
    return 3;
}

static int load_aux(lua_State *L, int status, int envidx) {
    if (l_likely(status == LUA_OK)) {
        if (envidx != 0) {  /* 'env' parameter? */
            lua_pushvalue(L, envidx);  /* environment for loaded function */
            if (!lua_setupvalue(L, -2, 1))  /* set it as 1st upvalue */
                lua_pop(L, 1);  /* remove 'env' if not used by previous call */
        }
        return 1;
    } else {  /* error (message is on top of the stack) */
        luaL_pushfail(L);
        lua_insert(L, -2);  /* put before error message */
        return 2;  /* return fail plus error message */
    }
}


static const char *getMode(lua_State *L, int idx) {
    const char *mode = luaL_optstring(L, idx, "bt");
    if (strchr(mode, 'B') != NULL)  /* Lua code cannot use fixed buffers */
        luaL_argerror(L, idx, "invalid mode");
    return mode;
}


static int luaB_loadfile(lua_State *L) {
    const char *fname = luaL_optstring(L, 1, NULL);
    const char *mode = luaL_optstring(L, 2, NULL);
    int env = (!lua_isnone(L, 3) ? 3 : 0);  /* 'env' index or 0 if no 'env' */
    int status = luaL_loadfilex(L, fname, mode);
    return load_aux(L, status, env);
}


/*
** {======================================================
** Generic Read function
** =======================================================
*/


/*
** reserved slot, above all arguments, to hold a copy of the returned
** string to avoid it being collected while parsed. 'load' has four
** optional arguments (chunk, source name, mode, and environment).
*/
#define RESERVEDSLOT    5


/*
** Reader for generic 'load' function: 'lua_load' uses the
** stack for internal stuff, so the reader cannot change the
** stack top. Instead, it keeps its resulting string in a
** reserved slot inside the stack.
*/
static const char *generic_reader(lua_State *L, void *ud, size_t *size) {
    (void) (ud);  /* not used */
    luaL_checkstack(L, 2, "too many nested functions");
    lua_pushvalue(L, 1);  /* get function */
    lua_call(L, 0, 1);  /* call it */
    if (lua_isnil(L, -1)) {
        lua_pop(L, 1);  /* pop result */
        *size = 0;
        return NULL;
    } else if (l_unlikely(!lua_isstring(L, -1)))
        luaL_error(L, "reader function must return a string");
    lua_replace(L, RESERVEDSLOT);  /* save string in reserved slot */
    return lua_tolstring(L, RESERVEDSLOT, size);
}


static int luaB_load(lua_State *L) {
    int status;
    size_t l;
    const char *s = lua_tolstring(L, 1, &l);
    const char *mode = luaL_optstring(L, 3, "bt");
    int env = (!lua_isnone(L, 4) ? 4 : 0);  /* 'env' index or 0 if no 'env' */
    if (s != NULL) {  /* loading a string? */
        const char *chunkname = luaL_optstring(L, 2, s);
        status = luaL_loadbufferx(L, s, l, chunkname, mode);
    } else {  /* loading from a reader function */
        const char *chunkname = luaL_optstring(L, 2, "=(load)");
        luaL_checktype(L, 1, LUA_TFUNCTION);
        lua_settop(L, RESERVEDSLOT);  /* create reserved slot */
        status = lua_load(L, generic_reader, NULL, chunkname, mode);
    }
    return load_aux(L, status, env);
}

/* }====================================================== */


static int dofilecont(lua_State *L, int d1, lua_KContext d2) {
    (void) d1;
    (void) d2;  /* only to match 'lua_Kfunction' prototype */
    return lua_gettop(L) - 1;
}


static int luaB_dofile(lua_State *L) {
    const char *fname = luaL_optstring(L, 1, NULL);
    lua_settop(L, 1);
    if (l_unlikely(luaL_loadfile(L, fname) != LUA_OK))
        return lua_error(L);
    lua_callk(L, 0, LUA_MULTRET, 0, dofilecont);
    return dofilecont(L, 0, 0);
}


static int luaB_assert(lua_State *L) {
    if (l_likely(lua_toboolean(L, 1)))  /* condition is true? */
        return lua_gettop(L);  /* return all arguments */
    else {  /* error */
        luaL_checkany(L, 1);  /* there must be a condition */
        lua_remove(L, 1);  /* remove it */
        lua_pushliteral(L, "assertion failed!");  /* default message */
        lua_settop(L, 1);  /* leave only message (default if no other one) */
        return luaB_error(L);  /* call 'error' */
    }
}


static int luaB_select(lua_State *L) {
    int n = lua_gettop(L);
    if (lua_type(L, 1) == LUA_TSTRING && *lua_tostring(L, 1) == '#') {
        lua_pushinteger(L, n - 1);
        return 1;
    } else {
        lua_Integer i = luaL_checkinteger(L, 1);
        if (i < 0) i = n + i;
        else if (i > n) i = n;
        luaL_argcheck(L, 1 <= i, 1, "index out of range");
        return n - (int) i;
    }
}


/*
** Continuation function for 'pcall' and 'xpcall'. Both functions
** already pushed a 'true' before doing the call, so in case of success
** 'finishpcall' only has to return everything in the stack minus
** 'extra' values (where 'extra' is exactly the number of items to be
** ignored).
*/
static int finishpcall(lua_State *L, int status, lua_KContext extra) {
    if (l_unlikely(status != LUA_OK && status != LUA_YIELD)) {  /* error? */
        lua_pushboolean(L, 0);  /* first result (false) */
        lua_pushvalue(L, -2);  /* error message */
        return 2;  /* return false, msg */
    } else
        return lua_gettop(L) - (int) extra;  /* return all results */
}


static int luaB_pcall(lua_State *L) {
    int status;
    luaL_checkany(L, 1);
    lua_pushboolean(L, 1);  /* first result if no errors */
    lua_insert(L, 1);  /* put it in place */
    status = lua_pcallk(L, lua_gettop(L) - 2, LUA_MULTRET, 0, 0, finishpcall);
    return finishpcall(L, status, 0);
}


/*
** Do a protected call with error handling. After 'lua_rotate', the
** stack will have <f, err, true, f, [args...]>; so, the function passes
** 2 to 'finishpcall' to skip the 2 first values when returning results.
*/
static int luaB_xpcall(lua_State *L) {
    int status;
    int n = lua_gettop(L);
    luaL_checktype(L, 2, LUA_TFUNCTION);  /* check error function */
    lua_pushboolean(L, 1);  /* first result */
    lua_pushvalue(L, 1);  /* function */
    lua_rotate(L, 3, 2);  /* move them below function's arguments */
    status = lua_pcallk(L, n - 2, LUA_MULTRET, 2, 2, finishpcall);
    return finishpcall(L, status, 2);
}


static int luaB_tostring(lua_State *L) {
    luaL_checkany(L, 1);
    luaL_tolstring(L, 1, NULL);
    return 1;
}


/* compatibility with old module system */
#if defined(LUA_COMPAT_MODULE)
static int findtable (lua_State *L) {
  if (lua_gettop(L)==1){
    lua_pushglobaltable(L);
    lua_insert(L, 1);
  }
  luaL_checktype(L, 1, LUA_TTABLE);
  const char *name = luaL_checklstring(L, 2, 0);
  lua_pushstring(L, luaL_findtable(L, 1, name, 0));
  return 2;
}
#endif


/* base64 encoding support */
static const char b64chars[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static void
base64_encode(lua_State *L, const char *input, size_t in_len, char **output, size_t *out_len) {
    size_t i, j;
    *out_len = ((in_len + 2) / 3) * 4;
    *output = (char *) lua_newuserdatauv(L, *out_len + 1, 0);

    for (i = 0, j = 0; i < in_len; i += 3, j += 4) {
        uint32_t val = 0;
        int k, count = 0;
        for (k = 0; k < 3 && (i + k) < in_len; k++) {
            val = (val << 8) | (unsigned char) input[i + k];
            count++;
        }

        // 计算每个base64字符
        // 注意：位移计算应基于实际的count值
        switch (count) {
            case 3:
                (*output)[j] = b64chars[(val >> 18) & 0x3F];
                (*output)[j + 1] = b64chars[(val >> 12) & 0x3F];
                (*output)[j + 2] = b64chars[(val >> 6) & 0x3F];
                (*output)[j + 3] = b64chars[val & 0x3F];
                break;
            case 2:
                (*output)[j] = b64chars[(val >> 10) & 0x3F];
                (*output)[j + 1] = b64chars[(val >> 4) & 0x3F];
                (*output)[j + 2] = b64chars[((val << 2) & 0x3F)];
                (*output)[j + 3] = '=';
                break;
            case 1:
                (*output)[j] = b64chars[(val >> 2) & 0x3F];
                (*output)[j + 1] = b64chars[((val << 4) & 0x3F)];
                (*output)[j + 2] = '=';
                (*output)[j + 3] = '=';
                break;
        }
    }

    (*output)[*out_len] = '\0';
}

/* simple XOR encryption */
static void xor_encrypt(const char *input, size_t in_len, char *output, char key) {
    size_t i;
    for (i = 0; i < in_len; i++) {
        output[i] = input[i] ^ key;
    }
}

/* 递归格式化表为字符串 */
/* 辅助结构：已访问表记录 */
typedef struct {
    lua_Integer count;
    void **tables;
} VisitedTables;

/* 检查表是否已访问过 */
static int is_table_visited(lua_State *L, VisitedTables *vt, int table_idx) {
    if (vt->count == 0) {
        return 0;
    }

    /* 获取表的地址 */
    lua_pushvalue(L, table_idx);
    void *table_addr = lua_touserdata(L, -1);
    lua_pop(L, 1);

    /* 检查是否已访问 */
    for (lua_Integer i = 0; i < vt->count; i++) {
        if (vt->tables[i] == table_addr) {
            return 1;
        }
    }

    /* 添加到已访问表列表 */
    vt->tables = (void **) realloc(vt->tables, sizeof(void *) * (vt->count + 1));
    if (vt->tables == NULL) {
        return 1;  /* 内存分配失败，视为已访问 */
    }
    vt->tables[vt->count++] = table_addr;

    return 0;
}

/* 重置已访问表记录 */
static void reset_visited_tables(VisitedTables *vt) {
    if (vt->tables != NULL) {
        free(vt->tables);
        vt->tables = NULL;
    }
    vt->count = 0;
}

static void format_table(lua_State *L, int idx, luaL_Buffer *buffer, int indent, int depth,
                         VisitedTables *visited) {
    int i, t;

    /* 将相对索引转换为绝对索引，避免栈操作影响索引 */
    idx = lua_absindex(L, idx);

    /* 检查元表是否有__tostring方法，如果有则直接调用 */
    int has_tostring = 0;
    if (lua_getmetatable(L, idx)) {
        lua_getfield(L, -1, "__tostring");
        if (lua_isfunction(L, -1)) {
            /* 调用__tostring方法 */
            lua_pushvalue(L, idx);
            if (lua_pcall(L, 1, 1, 0) == 0) {
                /* 成功调用，使用返回的字符串 */
                const char *str = lua_tostring(L, -1);
                if (str != NULL && strcmp(str, "table: 0x0") != 0) {
                    luaL_addstring(buffer, str);
                    has_tostring = 1;
                }
            }
            lua_pop(L, 3);  /* 移除__tostring返回值、__tostring函数和元表 */
            if (has_tostring) {
                return;
            }
        }
        lua_pop(L, 2);  /* 移除__tostring函数和元表 */
    }

    lua_pushnil(L);  /* 第一个键 */

    luaL_addstring(buffer, "{");

    /* 避免过度递归，限制最大深度 */
    if (depth > 40) {
        luaL_addstring(buffer, " ... ");
        lua_pop(L, 1);  /* 移除nil */
        luaL_addstring(buffer, "}");
        return;
    }

    while (lua_next(L, idx) != 0) {
        luaL_addstring(buffer, "\n");
        for (i = 0; i < indent + 2; i++) {
            luaL_addchar(buffer, ' ');
        }

        /* 格式化键 */
        /* 保存键的副本，避免luaL_tolstring修改原始键 */
        lua_pushvalue(L, -2);
        t = lua_type(L, -1);
        if (t == LUA_TSTRING) {
            const char *str = lua_tostring(L, -1);
            if (str != NULL && str[0] != '\0' && isalpha((unsigned char) str[0])) {
                luaL_addstring(buffer, str);
            } else {
                /* 使用lua_pushfstring和luaL_addstring替代luaL_addfstring */
                lua_pushfstring(L, "[%s]", str);
                luaL_addvalue(buffer);
            }
        } else if (t == LUA_TNUMBER) {
            /* 使用lua_pushfstring和luaL_addstring替代luaL_addfstring */
            lua_pushfstring(L, "[%d]", (int) lua_tointeger(L, -1));
            luaL_addvalue(buffer);
        } else {
            luaL_addstring(buffer, "[");
            luaL_tolstring(L, -1, NULL);
            luaL_addvalue(buffer);
            luaL_addstring(buffer, "]");
        }
        /* 移除键的副本 */
        lua_pop(L, 1);

        luaL_addstring(buffer, " = ");

        /* 格式化值 */
        t = lua_type(L, -1);
        if (t == LUA_TTABLE) {
            /* 检查循环引用 */
            if (is_table_visited(L, visited, -1)) {
                luaL_addstring(buffer, "<cycle>");
            } else {
                /* 递归格式化子表 */
                format_table(L, -1, buffer, indent + 2, depth + 1, visited);
            }
        } else if (t == LUA_TSTRING) {
            luaL_addstring(buffer, "\"");
            /* 保存字符串的副本，避免后续操作修改它 */
            lua_pushvalue(L, -1);
            luaL_tolstring(L, -1, NULL);
            luaL_addvalue(buffer);
            /* 移除字符串的副本 */
            lua_pop(L, 1);
            luaL_addstring(buffer, "\"");
        } else if (t == LUA_TFUNCTION) {
            /* 特殊处理函数类型，避免可能的闪退 */
            luaL_addstring(buffer, "<function>");
        } else if (t == LUA_TUSERDATA || t == LUA_TLIGHTUSERDATA) {
            /* 特殊处理 userdata，避免调用 __tostring 导致闪退 */
            luaL_addstring(buffer, "<userdata>");
        } else {
            /* 保存值的副本，避免 luaL_tolstring 修改原始值 */
            lua_pushvalue(L, -1);
            luaL_tolstring(L, -1, NULL);
            luaL_addvalue(buffer);
            /* 移除值的副本 */
            lua_pop(L, 1);
        }

        luaL_addstring(buffer, ",");
        lua_pop(L, 1);  /* 移除值，保留键用于下次循环 */
    }

    luaL_addstring(buffer, "\n");
    for (i = 0; i < indent; i++) {
        luaL_addchar(buffer, ' ');
    }
    luaL_addstring(buffer, "}");
}

/* base64解码 */
static void
base64_decode(lua_State *L, const char *input, size_t in_len, char **output, size_t *out_len) {
    static const unsigned char b64map[] = {
            255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
            255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
            255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 62, 255, 255, 255, 63,
            52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 255, 255, 255, 254, 255, 255,
            255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
            15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 255, 255, 255, 255, 255,
            255, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 255, 255, 255, 255, 255,
            255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
            255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
            255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
            255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
            255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
            255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
            255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
            255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255
    };

    size_t i, j;

    // 跳过所有无效字符，只处理有效的base64字符
    size_t valid_chars = 0;
    size_t padding = 0;

    // 第一次遍历：计算有效字符数和填充字符数
    for (i = 0; i < in_len; i++) {
        unsigned char c = input[i];
        unsigned char b = b64map[c];
        if (b != 255) {
            valid_chars++;
            if (b == 254) { // 填充字符 '='
                padding++;
            }
        }
    }

    // 计算输出长度
    *out_len = ((valid_chars / 4) * 3) - padding;
    if (*out_len == 0 && valid_chars > 0) {
        // 特殊处理：当有有效字符但计算出的输出长度为0时，如单个字符加密后的情况
        *out_len = 1;
    }

    *output = (char *) lua_newuserdatauv(L, *out_len, 0);

    uint32_t val = 0;
    int bits = 0;
    j = 0;

    // 第二次遍历：实际解码
    for (i = 0; i < in_len && j < *out_len; i++) {
        unsigned char c = input[i];
        unsigned char b = b64map[c];

        if (b == 255) {
            // 跳过无效字符
            continue;
        }

        if (b == 254) { // 填充字符 '='
            // 对于填充字符，我们继续处理，直到获取足够的位
            val = (val << 6);
            bits += 6;
        } else {
            val = (val << 6) | b;
            bits += 6;
        }

        if (bits >= 8) {
            bits -= 8;
            (*output)[j++] = (val >> bits) & 0xFF;
        }
    }
}

static int luaB_dump(lua_State *L) {
    int t = lua_type(L, 1);

    // 检查是否有第二个参数
    if (lua_gettop(L) == 2 && t == LUA_TSTRING) {
        const char *str = lua_tostring(L, 1);

        if (lua_isboolean(L, 2)) {
            // 第二个参数是布尔值
            int decrypt = lua_toboolean(L, 2);
            if (decrypt) {
                // 解密：base64解码 + XOR解密
                size_t str_len = lua_rawlen(L, 1);

                // base64解码
                char *decoded;
                size_t decoded_len;
                base64_decode(L, str, str_len, &decoded, &decoded_len);

                // XOR解密
                char *result = (char *) lua_newuserdatauv(L, decoded_len, 0);
                xor_encrypt(decoded, decoded_len, result, 0x5A); // 使用相同密钥解密

                // 压入结果
                lua_pushlstring(L, result, decoded_len);
                return 1;
            } else {
                // 传入false，返回nil
                lua_pushnil(L);
                return 1;
            }
        } else if (lua_isfunction(L, 2)) {
            // 第二个参数是函数，调用该函数并传入第一个参数
            lua_pushvalue(L, 2); // 函数
            lua_pushvalue(L, 1); // 第一个参数
            if (lua_pcall(L, 1, 1, 0) != 0) {
                // 调用出错，返回错误信息
                return 1;
            }
            return 1;
        }
    }

    // 处理单个参数的情况
    switch (t) {
        case LUA_TTABLE: {
            // 把表格式化转字符串
            luaL_Buffer buffer;
            luaL_buffinit(L, &buffer);

            /* 初始化已访问表记录 */
            VisitedTables visited;
            visited.count = 0;
            visited.tables = NULL;

            /* 格式化表 */
            format_table(L, 1, &buffer, 0, 0, &visited);

            /* 清理已访问表记录 */
            reset_visited_tables(&visited);

            luaL_pushresult(&buffer);
            return 1;
        }
        case LUA_TSTRING: {
            // 编译成加密文本然后用base64包裹
            size_t str_len;
            const char *str = lua_tolstring(L, 1, &str_len);

            // XOR加密
            char *encrypted = (char *) lua_newuserdatauv(L, str_len, 0);
            xor_encrypt(str, str_len, encrypted, 0x5A); // 使用0x5A作为密钥

            // base64编码
            char *encoded;
            size_t encoded_len;
            base64_encode(L, encrypted, str_len, &encoded, &encoded_len);

            // 压入结果
            lua_pushlstring(L, encoded, encoded_len);
            return 1;
        }
        case LUA_TUSERDATA: {
            // 尝试强制转化为string
            luaL_tolstring(L, 1, NULL);
            return 1;
        }
        default: {
            // 其他类型直接转化为字符串
            luaL_tolstring(L, 1, NULL);
            return 1;
        }
    }
}

// __gc元方法的回调函数
static int defer_gc_callback(lua_State *L) {
    lua_getiuservalue(L, 1, 1);
    lua_call(L, 0, 0);
    return 0;
}

static int luaB_defer(lua_State *L) {
    luaL_checktype(L, 1, LUA_TFUNCTION);

    // 创建一个带有__gc元方法的用户数据
    lua_newuserdatauv(L, 0, 1);
    lua_pushvalue(L, 1);
    lua_setiuservalue(L, -2, 1);

    // 创建元表
    lua_createtable(L, 0, 1);

    // 定义__gc元方法
    lua_pushcfunction(L, defer_gc_callback);
    lua_setfield(L, -2, "__gc");

    // 设置元表
    lua_setmetatable(L, -2);

    return 0;
}

// 模块信息结构体
typedef struct {
    const char *name;  // 模块名
    lua_CFunction init;  // 模块初始化函数
} ModuleInfo;

// 标准库模块列表
static const ModuleInfo modules[] = {
        {LUA_GNAME,        luaopen_base},
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

// 基本库函数列表
static const luaL_Reg env_funcs[] = {
        {"assert",         luaB_assert},
        {"collectgarbage", luaB_collectgarbage},
        {"defer",          luaB_defer},
        {"dofile",         luaB_dofile},
        {"dump",           luaB_dump},
        {"error",          luaB_error},
        {"getmetatable",   luaB_getmetatable},
        {"ipairs",         luaB_ipairs},
        {"loadfile",       luaB_loadfile},
        {"load",           luaB_load},
        {"loadstring",     luaB_load},
        {"next",           luaB_next},
        {"pairs",          luaB_pairs},
        {"pcall",          luaB_pcall},
        {"print",          luaB_print},
        {"warn",           luaB_warn},
        {"rawequal",       luaB_rawequal},
        {"rawlen",         luaB_rawlen},
        {"rawget",         luaB_rawget},
        {"rawset",         luaB_rawset},
        {"select",         luaB_select},
        {"setmetatable",   luaB_setmetatable},
        {"tonumber",       luaB_tonumber},
        {"tointeger",      luaB_tointeger},
        {"tostring",       luaB_tostring},
        {"type",           luaB_type},
        {"xpcall",         luaB_xpcall},
        {NULL, NULL}
};

// C函数包装器的调用元方法
static int cfunction_wrapper_call(lua_State *L) {
    // 获取存储的C函数指针
    lua_CFunction f = (lua_CFunction) lua_touserdata(L, lua_upvalueindex(1));

    // 调用C函数，传递所有参数
    return f(L);
}

// 保护函数表的元方法
static int protected_table_newindex(lua_State *L) {
    return luaL_error(L, "cannot modify protected function table");
}

static int luaB_getfenv(lua_State *L) {
    if (lua_isnoneornil(L, 1)) {
        // 没有参数，返回当前函数的环境
        lua_getglobal(L, "_ENV");
        return 1;
    } else {
        // 有参数，返回指定函数或线程的环境
        int type = lua_type(L, 1);
        if (type == LUA_TFUNCTION || type == LUA_TTHREAD) {
            // 获取函数或线程的环境
            lua_getuservalue(L, 1);
            if (lua_isnil(L, -1)) {
                // 如果没有环境，返回全局环境
                lua_pop(L, 1);
                lua_getglobal(L, "_ENV");
            }
            return 1;
        } else if (type == LUA_TNUMBER) {
            // 旧版Lua 5.1中，getfenv(1)获取当前函数的环境
            // 这里模拟这个行为
            lua_getglobal(L, "_ENV");
            return 1;
        } else {
            return luaL_error(L,
                              "bad argument #1 to 'getfenv' (function, thread or number expected)");
        }
    }
}

static int luaB_setfenv(lua_State *L) {
    int type = lua_type(L, 1);
    luaL_checktype(L, 2, LUA_TTABLE);

    if (type == LUA_TFUNCTION || type == LUA_TTHREAD) {
        // 设置函数或线程的环境
        lua_setuservalue(L, 1);
        lua_pushvalue(L, 2);  // 返回新的环境
        return 1;
    } else if (type == LUA_TNUMBER) {
        // 旧版Lua 5.1中，setfenv(1, table)设置当前函数的环境
        // 这里模拟这个行为，实际上是修改全局_ENV
        lua_setglobal(L, "_ENV");
        lua_pushvalue(L, 2);  // 返回新的环境
        return 1;
    } else {
        return luaL_error(L, "bad argument #1 to 'setfenv' (function, thread or number expected)");
    }
}

static int luaB_getenv_original(lua_State *L) {
    if (lua_isnoneornil(L, 1)) {
        // 没有参数，返回整个函数表
        lua_createtable(L, 0, 50);  // 创建一个足够大的表

        // 遍历所有基本库函数
        for (int i = 0; env_funcs[i].name != NULL; i++) {
            if (env_funcs[i].func != NULL) {
                // 直接将C函数作为值设置到表中
                lua_pushcfunction(L, env_funcs[i].func);
                lua_setfield(L, -2, env_funcs[i].name);  // 将函数放入表中
            }
        }

        // 遍历所有模块
        for (int i = 0; modules[i].name != NULL; i++) {
            if (modules[i].init != NULL && strcmp(modules[i].name, LUA_GNAME) != 0) {
                // 初始化模块
                modules[i].init(L);
                // 将模块设置到函数表中
                lua_setfield(L, -2, modules[i].name);
            }
        }

        // 设置元表保护
        lua_createtable(L, 0, 1);  // 创建元表
        lua_pushcfunction(L, protected_table_newindex);  // __newindex元方法
        lua_setfield(L, -2, "__newindex");
        lua_pushliteral(L, "protected table");  // 设置__metatable，防止获取元表
        lua_setfield(L, -2, "__metatable");
        lua_setmetatable(L, -2);  // 设置表的元表

        return 1;
    } else {
        // 有参数，返回单个函数或模块
        const char *funcname = luaL_checkstring(L, 1);

        // 1. 直接查找基本库函数
        for (int i = 0; env_funcs[i].name != NULL; i++) {
            if (strcmp(env_funcs[i].name, funcname) == 0 && env_funcs[i].func != NULL) {
                // 直接返回C函数
                lua_pushcfunction(L, env_funcs[i].func);
                return 1;
            }
        }

        // 2. 直接查找模块
        for (int i = 0; modules[i].name != NULL; i++) {
            if (strcmp(modules[i].name, funcname) == 0 && modules[i].init != NULL) {
                // 初始化模块并返回
                modules[i].init(L);
                return 1;
            }
        }

        // 3. 如果没找到，返回nil
        lua_pushnil(L);
        return 1;
    }
}

static const luaL_Reg base_funcs[] = {
        {"assert", luaB_assert},
        {"collectgarbage", luaB_collectgarbage},
        {"defer", luaB_defer},
        {"dofile", luaB_dofile},
        {"dump", luaB_dump},
        {"error", luaB_error},
#if defined(LUA_COMPAT_MODULE)
        {"findtable", findtable},
#endif
        {"getenv", luaB_getenv_original},
        {"getfenv", luaB_getfenv},
        {"getmetatable", luaB_getmetatable},
        {"ipairs", luaB_ipairs},
        {"loadfile", luaB_loadfile},
        {"load", luaB_load},
        {"loadstring", luaB_load},
        {"next", luaB_next},
        {"pairs", luaB_pairs},
        {"pcall", luaB_pcall},
        {"print", luaB_print},
        {"warn", luaB_warn},
        {"rawequal", luaB_rawequal},
        {"rawlen", luaB_rawlen},
        {"rawget", luaB_rawget},
        {"rawset", luaB_rawset},
        {"select", luaB_select},
        {"setfenv", luaB_setfenv},
        {"setmetatable", luaB_setmetatable},
        {"tonumber", luaB_tonumber},
        {"tointeger", luaB_tointeger},
        {"tostring", luaB_tostring},
        {"type", luaB_type},
        {"xpcall", luaB_xpcall},
        /* placeholders */
        {LUA_GNAME, NULL},
        {"_VERSION", NULL},
        {NULL, NULL}
};


static int protect_global(lua_State *L) {
    const char *name = lua_tostring(L, 2);

    // 检查是否为受保护的函数名
    if (strcmp(name, "getenv") == 0) {
        return luaL_error(L, "cannot modify protected function '%s'", name);
    }

    // 允许修改其他全局变量
    lua_rawset(L, 1);
    return 0;
}

LUAMOD_API int luaopen_base(lua_State *L) {
    /* open lib into global table */
    lua_pushglobaltable(L);
    luaL_setfuncs(L, base_funcs, 0);
    /* set global _G */
    lua_pushvalue(L, -1);
    lua_setfield(L, -2, LUA_GNAME);
    /* set global _VERSION */
    lua_pushliteral(L, LUA_VERSION);
    lua_setfield(L, -2, "_VERSION");

    /* 设置全局表元表，保护核心函数不被修改 */
    lua_createtable(L, 0, 1);  /* 创建元表 */
    lua_pushcfunction(L, protect_global);  /* __newindex元方法 */
    lua_setfield(L, -2, "__newindex");

    /* 允许用户修改全局表的元表，但保留__newindex保护 */
    lua_setmetatable(L, -2);  /* 设置全局表元表 */

    return 1;
}

