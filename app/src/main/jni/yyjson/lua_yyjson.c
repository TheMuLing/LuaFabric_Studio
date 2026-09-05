/*
** yyjson Lua 绑定模块
**
** 提供高性能 JSON 解析、序列化和操作功能
**
** 依赖：yyjson 库
**
**By DifierLine 2026/01/21
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>
#include "yyjson.h"

#define MODNAME "yyjson ModByDifierLine"
#define VERSION "2026/01/21"

static int is_array_table(lua_State *L, int idx, int *len) {
    // 将索引转换为绝对索引，避免栈操作影响索引
    idx = lua_absindex(L, idx);

    int n = luaL_len(L, idx);
    *len = n;
    if (n == 0) {
        // 检查是否存在非数字键
        int has_non_number_key = 0;
        lua_pushnil(L);
        while (lua_next(L, idx) != 0) {
            int key_type = lua_type(L, -2);
            if (key_type != LUA_TNUMBER) {
                has_non_number_key = 1;
            }
            lua_pop(L, 1);
        }
        return !has_non_number_key;
    }
    for (int i = 1; i <= n; i++) {
        lua_geti(L, idx, i);
        int is_nil = lua_isnil(L, -1);
        lua_pop(L, 1);
        if (is_nil) {
            return 0;
        }
    }
    int has_non_number_key = 0;
    lua_pushnil(L);
    while (lua_next(L, idx) != 0) {
        int key_type = lua_type(L, -2);
        if (key_type != LUA_TNUMBER) {
            has_non_number_key = 1;
        }
        lua_pop(L, 1);
    }
    return !has_non_number_key;
}

static yyjson_mut_val *lua_to_yyjson(lua_State *L, int idx, yyjson_mut_doc *doc) {
    // 将索引转换为绝对索引，避免栈操作影响索引
    idx = lua_absindex(L, idx);

    int type = lua_type(L, idx);

    switch (type) {
        case LUA_TNIL:
            return yyjson_mut_null(doc);
        case LUA_TBOOLEAN:
            return yyjson_mut_bool(doc, lua_toboolean(L, idx));
        case LUA_TNUMBER: {
            if (lua_isinteger(L, idx)) {
                return yyjson_mut_int(doc, lua_tointeger(L, idx));
            }
            return yyjson_mut_real(doc, lua_tonumber(L, idx));
        }
        case LUA_TSTRING: {
            size_t len;
            const char *str = lua_tolstring(L, idx, &len);
            return yyjson_mut_strn(doc, str, len);
        }
        case LUA_TTABLE: {
            int arr_len;
            if (is_array_table(L, idx, &arr_len)) {
                yyjson_mut_val *arr = yyjson_mut_arr(doc);
                for (int i = 1; i <= arr_len; i++) {
                    lua_geti(L, idx, i);
                    yyjson_mut_val *elem = lua_to_yyjson(L, -1, doc);
                    yyjson_mut_arr_append(arr, elem);
                    lua_pop(L, 1);
                }
                return arr;
            } else {
                yyjson_mut_val *obj = yyjson_mut_obj(doc);
                lua_pushnil(L);
                while (lua_next(L, idx) != 0) {
                    if (lua_type(L, -2) == LUA_TSTRING) {
                        size_t klen;
                        const char *k = lua_tolstring(L, -2, &klen);
                        yyjson_mut_val *v = lua_to_yyjson(L, -1, doc);
                        yyjson_mut_val *key = yyjson_mut_strn(doc, k, klen);
                        yyjson_mut_obj_add(obj, key, v);
                    } else if (lua_type(L, -2) == LUA_TNUMBER) {
                        // 处理数字键，转换为字符串键
                        size_t klen;
                        const char *k = lua_tolstring(L, -2, &klen);
                        yyjson_mut_val *v = lua_to_yyjson(L, -1, doc);
                        yyjson_mut_val *key = yyjson_mut_strn(doc, k, klen);
                        yyjson_mut_obj_add(obj, key, v);
                    }
                    lua_pop(L, 1);
                }
                return obj;
            }
        }
        default:
            return yyjson_mut_null(doc);
    }
}

static void yyjson_to_lua(lua_State *L, yyjson_val *val) {
    switch (yyjson_get_type(val)) {
        case YYJSON_TYPE_NULL:
            lua_pushnil(L);
            break;
        case YYJSON_TYPE_BOOL:
            lua_pushboolean(L, yyjson_get_bool(val));
            break;
        case YYJSON_TYPE_NUM:
            if (yyjson_is_int(val)) {
                lua_pushinteger(L, yyjson_get_int(val));
            } else if (yyjson_is_uint(val)) {
                lua_pushinteger(L, yyjson_get_uint(val));
            } else {
                lua_pushnumber(L, yyjson_get_real(val));
            }
            break;
        case YYJSON_TYPE_STR: {
            const char *str = yyjson_get_str(val);
            size_t len = yyjson_get_len(val);
            lua_pushlstring(L, str, len);
            break;
        }
        case YYJSON_TYPE_ARR: {
            size_t idx, max;
            yyjson_val *elem;
            lua_newtable(L);
            yyjson_arr_foreach(val, idx, max, elem) {
                yyjson_to_lua(L, elem);
                lua_seti(L, -2, idx + 1);
            }
            break;
        }
        case YYJSON_TYPE_OBJ: {
            size_t idx, max;
            yyjson_val *key, *elem;
            lua_newtable(L);
            yyjson_obj_foreach(val, idx, max, key, elem) {
                const char *k = yyjson_get_str(key);
                size_t klen = yyjson_get_len(key);
                lua_pushlstring(L, k, klen);
                yyjson_to_lua(L, elem);
                lua_settable(L, -3);
            }
            break;
        }
        default:
            lua_pushnil(L);
            break;
    }
}

static void yyjson_mut_to_lua(lua_State *L, yyjson_mut_val *val) {
    switch (yyjson_mut_get_type(val)) {
        case YYJSON_TYPE_NULL:
            lua_pushnil(L);
            break;
        case YYJSON_TYPE_BOOL:
            lua_pushboolean(L, yyjson_mut_get_bool(val));
            break;
        case YYJSON_TYPE_NUM:
            if (yyjson_mut_is_int(val)) {
                lua_pushinteger(L, yyjson_mut_get_int(val));
            } else if (yyjson_mut_is_uint(val)) {
                lua_pushinteger(L, yyjson_mut_get_uint(val));
            } else {
                lua_pushnumber(L, yyjson_mut_get_real(val));
            }
            break;
        case YYJSON_TYPE_STR: {
            const char *str = yyjson_mut_get_str(val);
            size_t len = yyjson_mut_get_len(val);
            lua_pushlstring(L, str, len);
            break;
        }
        case YYJSON_TYPE_ARR: {
            size_t idx, max;
            yyjson_mut_val *elem;
            lua_newtable(L);
            yyjson_mut_arr_foreach(val, idx, max, elem) {
                yyjson_mut_to_lua(L, elem);
                lua_seti(L, -2, idx + 1);
            }
            break;
        }
        case YYJSON_TYPE_OBJ: {
            size_t idx, max;
            yyjson_mut_val *key, *elem;
            lua_newtable(L);
            yyjson_mut_obj_foreach(val, idx, max, key, elem) {
                const char *k = yyjson_mut_get_str(key);
                size_t klen = yyjson_mut_get_len(key);
                lua_pushlstring(L, k, klen);
                yyjson_mut_to_lua(L, elem);
                lua_settable(L, -3);
            }
            break;
        }
        default:
            lua_pushnil(L);
            break;
    }
}

static int push_yyjson_val(lua_State *L, yyjson_val *val) {
    if (!val) {
        lua_pushnil(L);
        return 1;
    }
    yyjson_to_lua(L, val);
    return 1;
}

static int push_yyjson_mut_val(lua_State *L, yyjson_mut_val *val) {
    if (!val) {
        lua_pushnil(L);
        return 1;
    }
    yyjson_mut_to_lua(L, val);
    return 1;
}

static int lua_yyjson_read(lua_State *L) {
    const char *json_str;
    size_t len;
    yyjson_read_err err;
    yyjson_doc *doc;

    json_str = luaL_checklstring(L, 1, &len);
    if (!json_str) {
        return luaL_error(L, "参数必须是字符串");
    }

    doc = yyjson_read_opts((char *) json_str, len, YYJSON_READ_INSITU, NULL, &err);
    if (!doc) {
        lua_pushnil(L);
        lua_pushstring(L, err.msg);
        return 2;
    }

    lua_pushlightuserdata(L, doc);
    return 1;
}

static int lua_yyjson_decode(lua_State *L) {
    const char *json_str;
    size_t len;
    yyjson_read_err err;
    yyjson_doc *doc;
    yyjson_val *root;

    json_str = luaL_checklstring(L, 1, &len);
    if (!json_str) {
        return luaL_error(L, "参数必须是字符串");
    }

    doc = yyjson_read_opts((char *) json_str, len, YYJSON_READ_INSITU, NULL, &err);
    if (!doc) {
        lua_pushnil(L);
        lua_pushstring(L, err.msg);
        return 2;
    }

    root = yyjson_doc_get_root(doc);
    lua_newtable(L);
    yyjson_to_lua(L, root);

    yyjson_doc_free(doc);
    return 1;
}

static int lua_yyjson_encode(lua_State *L) {
    yyjson_write_err err;
    yyjson_mut_doc *doc;
    yyjson_mut_val *root;
    const char *result;
    size_t len;

    if (!lua_istable(L, 1)) {
        return luaL_error(L, "参数必须是表");
    }

    doc = yyjson_mut_doc_new(NULL);
    if (!doc) {
        lua_pushnil(L);
        lua_pushstring(L, "创建文档失败");
        return 2;
    }

    root = lua_to_yyjson(L, 1, doc);
    if (!root) {
        yyjson_mut_doc_free(doc);
        lua_pushnil(L);
        lua_pushstring(L, "转换 Lua 表失败");
        return 2;
    }

    yyjson_mut_doc_set_root(doc, root);
    result = yyjson_mut_write_opts(doc, YYJSON_WRITE_PRETTY, NULL, &len, &err);

    if (!result) {
        yyjson_mut_doc_free(doc);
        lua_pushnil(L);
        lua_pushstring(L, err.msg);
        return 2;
    }

    lua_pushlstring(L, result, len);
    free((void *) result);
    yyjson_mut_doc_free(doc);

    return 1;
}

static int lua_yyjson_doc_encode(lua_State *L) {
    /*
     * 将 yyjson 文档编码为 JSON 字符串
     * 参数: 1. yyjson 文档对象
     * 返回值: 1. JSON 字符串
     * 用法: local json_str = yyjson.encode_doc(doc)
     */
    yyjson_write_err err;
    const char *result;
    size_t len;

    // 检查第一个参数是否是可变文档
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    if (!doc) {
        return luaL_error(L, "参数必须是可变文档");
    }

    // 编码文档为 JSON 字符串
    result = yyjson_mut_write_opts(doc, YYJSON_WRITE_PRETTY, NULL, &len, &err);

    if (!result) {
        lua_pushnil(L);
        lua_pushstring(L, err.msg);
        return 2;
    }

    lua_pushlstring(L, result, len);
    free((void *) result);

    return 1;
}

static int lua_yyjson_encode_compact(lua_State *L) {
    yyjson_write_err err;
    yyjson_mut_doc *doc;
    yyjson_mut_val *root;
    const char *result;
    size_t len;

    if (!lua_istable(L, 1)) {
        return luaL_error(L, "参数必须是表");
    }

    doc = yyjson_mut_doc_new(NULL);
    if (!doc) {
        lua_pushnil(L);
        lua_pushstring(L, "创建文档失败");
        return 2;
    }

    root = lua_to_yyjson(L, 1, doc);
    if (!root) {
        yyjson_mut_doc_free(doc);
        lua_pushnil(L);
        lua_pushstring(L, "转换 Lua 表失败");
        return 2;
    }

    yyjson_mut_doc_set_root(doc, root);
    result = yyjson_mut_write_opts(doc, 0, NULL, &len, &err);

    if (!result) {
        yyjson_mut_doc_free(doc);
        lua_pushnil(L);
        lua_pushstring(L, err.msg);
        return 2;
    }

    lua_pushlstring(L, result, len);
    free((void *) result);
    yyjson_mut_doc_free(doc);

    return 1;
}

static int lua_yyjson_read_file(lua_State *L) {
    const char *path;
    FILE *fp;
    char *buffer;
    size_t size, read_size;
    yyjson_read_err err;
    yyjson_doc *doc;
    yyjson_val *root;

    path = luaL_checkstring(L, 1);
    if (!path) {
        return luaL_error(L, "路径必须是字符串");
    }

    fp = fopen(path, "rb");
    if (!fp) {
        lua_pushnil(L);
        lua_pushstring(L, "无法打开文件");
        return 2;
    }

    fseek(fp, 0, SEEK_END);
    size = ftell(fp);
    fseek(fp, 0, SEEK_SET);

    if (size == 0) {
        fclose(fp);
        lua_newtable(L);
        return 1;
    }

    buffer = (char *) malloc(size + 1);
    if (!buffer) {
        fclose(fp);
        lua_pushnil(L);
        lua_pushstring(L, "内存分配失败");
        return 2;
    }

    read_size = fread(buffer, 1, size, fp);
    buffer[read_size] = '\0';
    fclose(fp);

    if (read_size != size) {
        free(buffer);
        lua_pushnil(L);
        lua_pushstring(L, "读取文件失败");
        return 2;
    }

    doc = yyjson_read_opts(buffer, read_size, YYJSON_READ_INSITU, NULL, &err);
    free(buffer);

    if (!doc) {
        lua_pushnil(L);
        lua_pushstring(L, err.msg);
        return 2;
    }

    root = yyjson_doc_get_root(doc);
    lua_newtable(L);
    yyjson_to_lua(L, root);

    yyjson_doc_free(doc);
    return 1;
}

static int lua_yyjson_write_file(lua_State *L) {
    const char *path;
    FILE *fp;
    yyjson_write_err err;
    yyjson_mut_doc *doc;
    yyjson_mut_val *root;
    const char *result;
    size_t len;

    if (!lua_istable(L, 1)) {
        return luaL_error(L, "参数必须是表");
    }

    path = luaL_checkstring(L, 2);
    if (!path) {
        return luaL_error(L, "路径必须是字符串");
    }

    doc = yyjson_mut_doc_new(NULL);
    if (!doc) {
        lua_pushnil(L);
        lua_pushstring(L, "创建文档失败");
        return 2;
    }

    root = lua_to_yyjson(L, 1, doc);
    if (!root) {
        yyjson_mut_doc_free(doc);
        lua_pushnil(L);
        lua_pushstring(L, "转换 Lua 表失败");
        return 2;
    }

    yyjson_mut_doc_set_root(doc, root);

    int pretty = lua_gettop(L) >= 3 && lua_isboolean(L, 3) && lua_toboolean(L, 3);
    yyjson_write_flag flag = pretty ? YYJSON_WRITE_PRETTY : 0;

    result = yyjson_mut_write_opts(doc, flag, NULL, &len, &err);

    if (!result) {
        yyjson_mut_doc_free(doc);
        lua_pushnil(L);
        lua_pushstring(L, err.msg);
        return 2;
    }

    fp = fopen(path, "wb");
    if (!fp) {
        free((void *) result);
        yyjson_mut_doc_free(doc);
        lua_pushnil(L);
        lua_pushstring(L, "无法创建文件");
        return 2;
    }

    fwrite(result, 1, len, fp);
    fclose(fp);

    free((void *) result);
    yyjson_mut_doc_free(doc);

    lua_pushboolean(L, 1);
    return 1;
}

static int lua_yyjson_validate(lua_State *L) {
    const char *json_str;
    size_t len;
    yyjson_read_err err;
    yyjson_doc *doc;

    json_str = luaL_checklstring(L, 1, &len);
    if (!json_str) {
        return luaL_error(L, "参数必须是字符串");
    }

    doc = yyjson_read_opts((char *) json_str, len, YYJSON_READ_INSITU, NULL, &err);
    if (!doc) {
        lua_pushnil(L);
        lua_pushstring(L, err.msg);
        return 2;
    }

    yyjson_doc_free(doc);
    lua_pushboolean(L, 1);
    return 1;
}

static int lua_yyjson_is_null(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_is_null(val));
    return 1;
}

static int lua_yyjson_is_bool(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_is_bool(val));
    return 1;
}

static int lua_yyjson_is_true(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_is_true(val));
    return 1;
}

static int lua_yyjson_is_false(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_is_false(val));
    return 1;
}

static int lua_yyjson_is_int(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_is_int(val));
    return 1;
}

static int lua_yyjson_is_uint(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_is_uint(val));
    return 1;
}

static int lua_yyjson_is_real(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_is_real(val));
    return 1;
}

static int lua_yyjson_is_num(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_is_num(val));
    return 1;
}

static int lua_yyjson_is_str(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_is_str(val));
    return 1;
}

static int lua_yyjson_is_arr(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_is_arr(val));
    return 1;
}

static int lua_yyjson_is_obj(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_is_obj(val));
    return 1;
}

static int lua_yyjson_is_ctn(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_is_ctn(val));
    return 1;
}

static int lua_yyjson_get_type(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushstring(L, yyjson_get_type_desc(val));
    return 1;
}

static int lua_yyjson_get_tag(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushinteger(L, yyjson_get_tag(val));
    return 1;
}

static int lua_yyjson_get_bool(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushboolean(L, yyjson_get_bool(val));
    return 1;
}

static int lua_yyjson_get_int(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushinteger(L, yyjson_get_int(val));
    return 1;
}

static int lua_yyjson_get_uint(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushinteger(L, yyjson_get_uint(val));
    return 1;
}

static int lua_yyjson_get_sint(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushinteger(L, yyjson_get_sint(val));
    return 1;
}

static int lua_yyjson_get_real(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushnumber(L, yyjson_get_real(val));
    return 1;
}

static int lua_yyjson_get_num(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushnumber(L, yyjson_get_num(val));
    return 1;
}

static int lua_yyjson_get_str(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushstring(L, yyjson_get_str(val));
    return 1;
}

static int lua_yyjson_get_len(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 值");
    }
    lua_pushinteger(L, yyjson_get_len(val));
    return 1;
}

static int lua_yyjson_arr_size(lua_State *L) {
    yyjson_val *arr = (yyjson_val *) lua_touserdata(L, 1);
    if (!arr || !yyjson_is_arr(arr)) {
        return luaL_error(L, "参数必须是数组");
    }
    lua_pushinteger(L, yyjson_arr_size(arr));
    return 1;
}

static int lua_yyjson_arr_get(lua_State *L) {
    yyjson_val *arr = (yyjson_val *) lua_touserdata(L, 1);
    int idx = luaL_checkinteger(L, 2);
    if (!arr || !yyjson_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    return push_yyjson_val(L, yyjson_arr_get(arr, idx));
}

static int lua_yyjson_arr_get_first(lua_State *L) {
    yyjson_val *arr = (yyjson_val *) lua_touserdata(L, 1);
    if (!arr || !yyjson_is_arr(arr)) {
        return luaL_error(L, "参数必须是数组");
    }
    return push_yyjson_val(L, yyjson_arr_get_first(arr));
}

static int lua_yyjson_arr_get_last(lua_State *L) {
    yyjson_val *arr = (yyjson_val *) lua_touserdata(L, 1);
    if (!arr || !yyjson_is_arr(arr)) {
        return luaL_error(L, "参数必须是数组");
    }
    return push_yyjson_val(L, yyjson_arr_get_last(arr));
}

static int lua_yyjson_obj_size(lua_State *L) {
    yyjson_val *obj = (yyjson_val *) lua_touserdata(L, 1);
    if (!obj || !yyjson_is_obj(obj)) {
        return luaL_error(L, "参数必须是对象");
    }
    lua_pushinteger(L, yyjson_obj_size(obj));
    return 1;
}

static int lua_yyjson_obj_get(lua_State *L) {
    yyjson_val *obj = (yyjson_val *) lua_touserdata(L, 1);
    const char *key = luaL_checkstring(L, 2);
    if (!obj || !yyjson_is_obj(obj)) {
        return luaL_error(L, "第一个参数必须是对象");
    }
    return push_yyjson_val(L, yyjson_obj_get(obj, key));
}

static int lua_yyjson_obj_getn(lua_State *L) {
    yyjson_val *obj = (yyjson_val *) lua_touserdata(L, 1);
    size_t len;
    const char *key = luaL_checklstring(L, 2, &len);
    if (!obj || !yyjson_is_obj(obj)) {
        return luaL_error(L, "第一个参数必须是对象");
    }
    return push_yyjson_val(L, yyjson_obj_getn(obj, key, len));
}

static int lua_yyjson_doc_get_root(lua_State *L) {
    yyjson_doc *doc = (yyjson_doc *) lua_touserdata(L, 1);
    if (!doc) {
        return luaL_error(L, "参数必须是 yyjson 文档");
    }
    return push_yyjson_val(L, yyjson_doc_get_root(doc));
}

static int lua_yyjson_doc_get_read_size(lua_State *L) {
    yyjson_doc *doc = (yyjson_doc *) lua_touserdata(L, 1);
    if (!doc) {
        return luaL_error(L, "参数必须是 yyjson 文档");
    }
    lua_pushinteger(L, yyjson_doc_get_read_size(doc));
    return 1;
}

static int lua_yyjson_doc_get_val_count(lua_State *L) {
    yyjson_doc *doc = (yyjson_doc *) lua_touserdata(L, 1);
    if (!doc) {
        return luaL_error(L, "参数必须是 yyjson 文档");
    }
    lua_pushinteger(L, yyjson_doc_get_val_count(doc));
    return 1;
}

static int lua_yyjson_ptr_get(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 值");
    }
    return push_yyjson_val(L, yyjson_ptr_get(val, path));
}

static int lua_yyjson_ptr_getn(lua_State *L) {
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 1);
    size_t len;
    const char *path = luaL_checklstring(L, 2, &len);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 值");
    }
    return push_yyjson_val(L, yyjson_ptr_getn(val, path, len));
}

static int lua_yyjson_doc_ptr_get(lua_State *L) {
    yyjson_doc *doc = (yyjson_doc *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是 yyjson 文档");
    }
    return push_yyjson_val(L, yyjson_doc_ptr_get(doc, path));
}

static int lua_yyjson_doc_ptr_getn(lua_State *L) {
    yyjson_doc *doc = (yyjson_doc *) lua_touserdata(L, 1);
    size_t len;
    const char *path = luaL_checklstring(L, 2, &len);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是 yyjson 文档");
    }
    return push_yyjson_val(L, yyjson_doc_ptr_getn(doc, path, len));
}

static int lua_yyjson_mut_doc_new(lua_State *L) {
    yyjson_mut_doc *doc = yyjson_mut_doc_new(NULL);
    if (!doc) {
        lua_pushnil(L);
        lua_pushstring(L, "创建可变文档失败");
        return 2;
    }
    lua_pushlightuserdata(L, doc);
    return 1;
}

static int lua_yyjson_mut_doc_free(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    if (doc) {
        yyjson_mut_doc_free(doc);
    }
    return 0;
}

static int lua_yyjson_mut_doc_set_root(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    yyjson_mut_val *root = (yyjson_mut_val *) lua_touserdata(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    yyjson_mut_doc_set_root(doc, root);
    return 0;
}

static int lua_yyjson_mut_doc_get_root(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    if (!doc) {
        return luaL_error(L, "参数必须是可变文档");
    }
    yyjson_mut_val *root = yyjson_mut_doc_get_root(doc);
    if (!root) {
        lua_pushnil(L);
        return 1;
    }
    lua_pushlightuserdata(L, root);
    return 1;
}

static int lua_yyjson_mut_doc_mut_copy(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    if (!doc) {
        return luaL_error(L, "参数必须是可变文档");
    }
    yyjson_mut_doc *copy = yyjson_mut_doc_mut_copy(doc, NULL);
    if (!copy) {
        lua_pushnil(L);
        lua_pushstring(L, "复制文档失败");
        return 2;
    }
    lua_pushlightuserdata(L, copy);
    return 1;
}

static int lua_yyjson_mut_doc_imut_copy(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    if (!doc) {
        return luaL_error(L, "参数必须是可变文档");
    }
    yyjson_doc *copy = yyjson_mut_doc_imut_copy(doc, NULL);
    if (!copy) {
        lua_pushnil(L);
        lua_pushstring(L, "复制文档失败");
        return 2;
    }
    lua_pushlightuserdata(L, copy);
    return 1;
}

static int lua_yyjson_mut_is_null(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_is_null(val));
    return 1;
}

static int lua_yyjson_mut_is_bool(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_is_bool(val));
    return 1;
}

static int lua_yyjson_mut_is_true(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_is_true(val));
    return 1;
}

static int lua_yyjson_mut_is_false(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_is_false(val));
    return 1;
}

static int lua_yyjson_mut_is_int(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_is_int(val));
    return 1;
}

static int lua_yyjson_mut_is_uint(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_is_uint(val));
    return 1;
}

static int lua_yyjson_mut_is_real(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_is_real(val));
    return 1;
}

static int lua_yyjson_mut_is_num(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_is_num(val));
    return 1;
}

static int lua_yyjson_mut_is_str(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_is_str(val));
    return 1;
}

static int lua_yyjson_mut_is_arr(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_is_arr(val));
    return 1;
}

static int lua_yyjson_mut_is_obj(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_is_obj(val));
    return 1;
}

static int lua_yyjson_mut_is_ctn(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_is_ctn(val));
    return 1;
}

static int lua_yyjson_mut_get_type(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushstring(L, yyjson_mut_get_type_desc(val));
    return 1;
}

static int lua_yyjson_mut_get_tag(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushinteger(L, yyjson_mut_get_tag(val));
    return 1;
}

static int lua_yyjson_mut_get_bool(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_get_bool(val));
    return 1;
}

static int lua_yyjson_mut_get_int(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushinteger(L, yyjson_mut_get_int(val));
    return 1;
}

static int lua_yyjson_mut_get_uint(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushinteger(L, yyjson_mut_get_uint(val));
    return 1;
}

static int lua_yyjson_mut_get_sint(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushinteger(L, yyjson_mut_get_sint(val));
    return 1;
}

static int lua_yyjson_mut_get_real(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushnumber(L, yyjson_mut_get_real(val));
    return 1;
}

static int lua_yyjson_mut_get_num(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushnumber(L, yyjson_mut_get_num(val));
    return 1;
}

static int lua_yyjson_mut_get_str(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushstring(L, yyjson_mut_get_str(val));
    return 1;
}

static int lua_yyjson_mut_get_len(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushinteger(L, yyjson_mut_get_len(val));
    return 1;
}

static int lua_yyjson_mut_arr_size(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "参数必须是数组");
    }
    lua_pushinteger(L, yyjson_mut_arr_size(arr));
    return 1;
}

static int lua_yyjson_mut_arr_get(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    int idx = luaL_checkinteger(L, 2);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    return push_yyjson_mut_val(L, yyjson_mut_arr_get(arr, idx));
}

static int lua_yyjson_mut_arr_get_first(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "参数必须是数组");
    }
    return push_yyjson_mut_val(L, yyjson_mut_arr_get_first(arr));
}

static int lua_yyjson_mut_arr_get_last(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "参数必须是数组");
    }
    return push_yyjson_mut_val(L, yyjson_mut_arr_get_last(arr));
}

static int lua_yyjson_mut_arr_append(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 2);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    lua_pushboolean(L, yyjson_mut_arr_append(arr, val));
    return 1;
}

static int lua_yyjson_mut_arr_insert(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    int idx = luaL_checkinteger(L, 2);
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 3);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    lua_pushboolean(L, yyjson_mut_arr_insert(arr, val, idx));
    return 1;
}

static int lua_yyjson_mut_arr_prepend(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 2);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    lua_pushboolean(L, yyjson_mut_arr_prepend(arr, val));
    return 1;
}

static int lua_yyjson_mut_arr_replace(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    int idx = luaL_checkinteger(L, 2);
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 3);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    return push_yyjson_mut_val(L, yyjson_mut_arr_replace(arr, idx, val));
}

static int lua_yyjson_mut_arr_remove(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    int idx = luaL_checkinteger(L, 2);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    return push_yyjson_mut_val(L, yyjson_mut_arr_remove(arr, idx));
}

static int lua_yyjson_mut_arr_remove_first(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "参数必须是数组");
    }
    return push_yyjson_mut_val(L, yyjson_mut_arr_remove_first(arr));
}

static int lua_yyjson_mut_arr_remove_last(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "参数必须是数组");
    }
    return push_yyjson_mut_val(L, yyjson_mut_arr_remove_last(arr));
}

static int lua_yyjson_mut_arr_clear(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "参数必须是数组");
    }
    lua_pushboolean(L, yyjson_mut_arr_clear(arr));
    return 1;
}

static int lua_yyjson_mut_arr_add_null(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 2);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    lua_pushboolean(L, yyjson_mut_arr_add_null(doc, arr));
    return 1;
}

static int lua_yyjson_mut_arr_add_true(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 2);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    lua_pushboolean(L, yyjson_mut_arr_add_true(doc, arr));
    return 1;
}

static int lua_yyjson_mut_arr_add_false(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 2);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    lua_pushboolean(L, yyjson_mut_arr_add_false(doc, arr));
    return 1;
}

static int lua_yyjson_mut_arr_add_bool(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 2);
    bool val = lua_toboolean(L, 3);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    lua_pushboolean(L, yyjson_mut_arr_add_bool(doc, arr, val));
    return 1;
}

static int lua_yyjson_mut_arr_add_int(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 2);
    int64_t val = luaL_checkinteger(L, 3);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    lua_pushboolean(L, yyjson_mut_arr_add_sint(doc, arr, val));
    return 1;
}

static int lua_yyjson_mut_arr_add_uint(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 2);
    uint64_t val = luaL_checkinteger(L, 3);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    lua_pushboolean(L, yyjson_mut_arr_add_uint(doc, arr, val));
    return 1;
}

static int lua_yyjson_mut_arr_add_real(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 2);
    double val = luaL_checknumber(L, 3);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    lua_pushboolean(L, yyjson_mut_arr_add_real(doc, arr, val));
    return 1;
}

static int lua_yyjson_mut_arr_add_str(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 2);
    const char *val = luaL_checkstring(L, 3);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    lua_pushboolean(L, yyjson_mut_arr_add_str(doc, arr, val));
    return 1;
}

static int lua_yyjson_mut_arr_add_obj(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 2);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    yyjson_mut_val *obj = yyjson_mut_arr_add_obj(doc, arr);
    lua_pushboolean(L, obj != NULL);
    return 1;
}

static int lua_yyjson_mut_arr_add_arr(lua_State *L) {
    yyjson_mut_val *arr = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 2);
    if (!arr || !yyjson_mut_is_arr(arr)) {
        return luaL_error(L, "第一个参数必须是数组");
    }
    yyjson_mut_val *val = yyjson_mut_arr_add_arr(doc, arr);
    lua_pushboolean(L, val != NULL);
    return 1;
}

static int lua_yyjson_mut_obj_size(lua_State *L) {
    yyjson_mut_val *obj = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!obj || !yyjson_mut_is_obj(obj)) {
        return luaL_error(L, "参数必须是对象");
    }
    lua_pushinteger(L, yyjson_mut_obj_size(obj));
    return 1;
}

static int lua_yyjson_mut_obj_get(lua_State *L) {
    yyjson_mut_val *obj = (yyjson_mut_val *) lua_touserdata(L, 1);
    const char *key = luaL_checkstring(L, 2);
    if (!obj || !yyjson_mut_is_obj(obj)) {
        return luaL_error(L, "第一个参数必须是对象");
    }
    return push_yyjson_mut_val(L, yyjson_mut_obj_get(obj, key));
}

static int lua_yyjson_mut_obj_getn(lua_State *L) {
    yyjson_mut_val *obj = (yyjson_mut_val *) lua_touserdata(L, 1);
    size_t len;
    const char *key = luaL_checklstring(L, 2, &len);
    if (!obj || !yyjson_mut_is_obj(obj)) {
        return luaL_error(L, "第一个参数必须是对象");
    }
    return push_yyjson_mut_val(L, yyjson_mut_obj_getn(obj, key, len));
}

static int lua_yyjson_mut_obj_add(lua_State *L) {
    yyjson_mut_val *obj = (yyjson_mut_val *) lua_touserdata(L, 1);
    const char *key = luaL_checkstring(L, 2);
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 3);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 4);
    if (!obj || !yyjson_mut_is_obj(obj)) {
        return luaL_error(L, "第一个参数必须是对象");
    }
    if (!doc) {
        return luaL_error(L, "第四个参数必须是可变文档");
    }
    yyjson_mut_val *key_val = yyjson_mut_str(doc, key);
    lua_pushboolean(L, yyjson_mut_obj_add(obj, key_val, val));
    return 1;
}

static int lua_yyjson_mut_obj_addn(lua_State *L) {
    yyjson_mut_val *obj = (yyjson_mut_val *) lua_touserdata(L, 1);
    size_t klen;
    const char *key = luaL_checklstring(L, 2, &klen);
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 3);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 4);
    if (!obj || !yyjson_mut_is_obj(obj)) {
        return luaL_error(L, "第一个参数必须是对象");
    }
    if (!doc) {
        return luaL_error(L, "第四个参数必须是可变文档");
    }
    yyjson_mut_val *key_val = yyjson_mut_strn(doc, key, klen);
    lua_pushboolean(L, yyjson_mut_obj_add(obj, key_val, val));
    return 1;
}

static int lua_yyjson_mut_obj_remove(lua_State *L) {
    yyjson_mut_val *obj = (yyjson_mut_val *) lua_touserdata(L, 1);
    const char *key = luaL_checkstring(L, 2);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 3);
    if (!obj || !yyjson_mut_is_obj(obj)) {
        return luaL_error(L, "第一个参数必须是对象");
    }
    if (!doc) {
        return luaL_error(L, "第三个参数必须是可变文档");
    }
    yyjson_mut_val *key_val = yyjson_mut_str(doc, key);
    return push_yyjson_mut_val(L, yyjson_mut_obj_remove(obj, key_val));
}

static int lua_yyjson_mut_obj_removen(lua_State *L) {
    yyjson_mut_val *obj = (yyjson_mut_val *) lua_touserdata(L, 1);
    size_t len;
    const char *key = luaL_checklstring(L, 2, &len);
    if (!obj || !yyjson_mut_is_obj(obj)) {
        return luaL_error(L, "第一个参数必须是对象");
    }
    return push_yyjson_mut_val(L, yyjson_mut_obj_remove_keyn(obj, key, len));
}

static int lua_yyjson_mut_obj_clear(lua_State *L) {
    yyjson_mut_val *obj = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!obj || !yyjson_mut_is_obj(obj)) {
        return luaL_error(L, "参数必须是对象");
    }
    lua_pushboolean(L, yyjson_mut_obj_clear(obj));
    return 1;
}

static int lua_yyjson_mut_obj_replace(lua_State *L) {
    yyjson_mut_val *obj = (yyjson_mut_val *) lua_touserdata(L, 1);
    const char *key = luaL_checkstring(L, 2);
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 3);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 4);
    if (!obj || !yyjson_mut_is_obj(obj)) {
        return luaL_error(L, "第一个参数必须是对象");
    }
    if (!doc) {
        return luaL_error(L, "第四个参数必须是可变文档");
    }
    yyjson_mut_val *key_val = yyjson_mut_str(doc, key);
    lua_pushboolean(L, yyjson_mut_obj_replace(obj, key_val, val));
    return 1;
}

static int lua_yyjson_mut_set_null(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_set_null(val));
    return 1;
}

static int lua_yyjson_mut_set_bool(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    bool b = lua_toboolean(L, 2);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_set_bool(val, b));
    return 1;
}

static int lua_yyjson_mut_set_int(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    int64_t i = luaL_checkinteger(L, 2);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_set_int(val, i));
    return 1;
}

static int lua_yyjson_mut_set_uint(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    uint64_t i = luaL_checkinteger(L, 2);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_set_uint(val, i));
    return 1;
}

static int lua_yyjson_mut_set_real(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    double d = luaL_checknumber(L, 2);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_set_real(val, d));
    return 1;
}

static int lua_yyjson_mut_set_str(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    const char *s = luaL_checkstring(L, 2);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_set_str(val, s));
    return 1;
}

static int lua_yyjson_mut_set_arr(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_set_arr(val));
    return 1;
}

static int lua_yyjson_mut_set_obj(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_set_obj(val));
    return 1;
}

static int lua_yyjson_mut_ptr_get(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    return push_yyjson_mut_val(L, yyjson_mut_ptr_get(val, path));
}

static int lua_yyjson_mut_ptr_getn(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    size_t len;
    const char *path = luaL_checklstring(L, 2, &len);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    return push_yyjson_mut_val(L, yyjson_mut_ptr_getn(val, path, len));
}

static int lua_yyjson_mut_doc_ptr_get(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_mut_doc_ptr_get(doc, path));
}

static int lua_yyjson_mut_doc_ptr_getn(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    size_t len;
    const char *path = luaL_checklstring(L, 2, &len);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_mut_doc_ptr_getn(doc, path, len));
}

static int lua_yyjson_mut_ptr_set(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    yyjson_mut_val *new_val = (yyjson_mut_val *) lua_touserdata(L, 3);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 4);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    if (!doc) {
        return luaL_error(L, "第四个参数必须是可变文档");
    }
    lua_pushboolean(L, yyjson_mut_ptr_set(val, path, new_val, doc));
    return 1;
}

static int lua_yyjson_mut_ptr_setn(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    size_t len;
    const char *path = luaL_checklstring(L, 2, &len);
    yyjson_mut_val *new_val = (yyjson_mut_val *) lua_touserdata(L, 3);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 4);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    if (!doc) {
        return luaL_error(L, "第四个参数必须是可变文档");
    }
    lua_pushboolean(L, yyjson_mut_ptr_setn(val, path, len, new_val, doc));
    return 1;
}

static int lua_yyjson_mut_doc_ptr_set(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    yyjson_mut_val *new_val = (yyjson_mut_val *) lua_touserdata(L, 3);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    lua_pushboolean(L, yyjson_mut_doc_ptr_set(doc, path, new_val));
    return 1;
}

static int lua_yyjson_mut_doc_ptr_setn(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    size_t len;
    const char *path = luaL_checklstring(L, 2, &len);
    yyjson_mut_val *new_val = (yyjson_mut_val *) lua_touserdata(L, 3);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    lua_pushboolean(L, yyjson_mut_doc_ptr_setn(doc, path, len, new_val));
    return 1;
}

static int lua_yyjson_mut_ptr_add(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    yyjson_mut_val *new_val = (yyjson_mut_val *) lua_touserdata(L, 3);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 4);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    if (!doc) {
        return luaL_error(L, "第四个参数必须是可变文档");
    }
    lua_pushboolean(L, yyjson_mut_ptr_add(val, path, new_val, doc));
    return 1;
}

static int lua_yyjson_mut_ptr_addn(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    size_t len;
    const char *path = luaL_checklstring(L, 2, &len);
    yyjson_mut_val *new_val = (yyjson_mut_val *) lua_touserdata(L, 3);
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 4);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    if (!doc) {
        return luaL_error(L, "第四个参数必须是可变文档");
    }
    lua_pushboolean(L, yyjson_mut_ptr_addn(val, path, len, new_val, doc));
    return 1;
}

static int lua_yyjson_mut_doc_ptr_add(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    yyjson_mut_val *new_val = (yyjson_mut_val *) lua_touserdata(L, 3);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    lua_pushboolean(L, yyjson_mut_doc_ptr_add(doc, path, new_val));
    return 1;
}

static int lua_yyjson_mut_doc_ptr_addn(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    size_t len;
    const char *path = luaL_checklstring(L, 2, &len);
    yyjson_mut_val *new_val = (yyjson_mut_val *) lua_touserdata(L, 3);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    lua_pushboolean(L, yyjson_mut_doc_ptr_addn(doc, path, len, new_val));
    return 1;
}

static int lua_yyjson_mut_ptr_remove(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    return push_yyjson_mut_val(L, yyjson_mut_ptr_remove(val, path));
}

static int lua_yyjson_mut_ptr_removen(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    size_t len;
    const char *path = luaL_checklstring(L, 2, &len);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    return push_yyjson_mut_val(L, yyjson_mut_ptr_removen(val, path, len));
}

static int lua_yyjson_mut_doc_ptr_remove(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_mut_doc_ptr_remove(doc, path));
}

static int lua_yyjson_mut_doc_ptr_removen(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    size_t len;
    const char *path = luaL_checklstring(L, 2, &len);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_mut_doc_ptr_removen(doc, path, len));
}

static int lua_yyjson_mut_ptr_replace(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    yyjson_mut_val *new_val = (yyjson_mut_val *) lua_touserdata(L, 3);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    return push_yyjson_mut_val(L, yyjson_mut_ptr_replace(val, path, new_val));
}

static int lua_yyjson_mut_ptr_replacen(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    size_t len;
    const char *path = luaL_checklstring(L, 2, &len);
    yyjson_mut_val *new_val = (yyjson_mut_val *) lua_touserdata(L, 3);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    return push_yyjson_mut_val(L, yyjson_mut_ptr_replacen(val, path, len, new_val));
}

static int lua_yyjson_mut_doc_ptr_replace(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    yyjson_mut_val *new_val = (yyjson_mut_val *) lua_touserdata(L, 3);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_mut_doc_ptr_replace(doc, path, new_val));
}

static int lua_yyjson_mut_doc_ptr_replacen(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    size_t len;
    const char *path = luaL_checklstring(L, 2, &len);
    yyjson_mut_val *new_val = (yyjson_mut_val *) lua_touserdata(L, 3);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_mut_doc_ptr_replacen(doc, path, len, new_val));
}

static int lua_yyjson_mut_val_write(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_write_err err;
    size_t len;
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    const char *result = yyjson_mut_val_write_opts(val, YYJSON_WRITE_PRETTY, NULL, &len, &err);
    if (!result) {
        lua_pushnil(L);
        lua_pushstring(L, err.msg);
        return 2;
    }
    lua_pushlstring(L, result, len);
    free((void *) result);
    return 1;
}

static int lua_yyjson_mut_val_write_compact(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_write_err err;
    size_t len;
    if (!val) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    const char *result = yyjson_mut_val_write_opts(val, 0, NULL, &len, &err);
    if (!result) {
        lua_pushnil(L);
        lua_pushstring(L, err.msg);
        return 2;
    }
    lua_pushlstring(L, result, len);
    free((void *) result);
    return 1;
}

static int lua_yyjson_mut_val_write_file(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    const char *path = luaL_checkstring(L, 2);
    yyjson_write_err err;
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    int pretty = lua_gettop(L) >= 3 && lua_isboolean(L, 3) && lua_toboolean(L, 3);
    yyjson_write_flag flag = pretty ? YYJSON_WRITE_PRETTY : 0;
    lua_pushboolean(L, yyjson_mut_val_write_file(path, val, flag, NULL, &err));
    return 1;
}

static int lua_yyjson_val_mut_copy(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    yyjson_val *val = (yyjson_val *) lua_touserdata(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_val_mut_copy(doc, val));
}

static int lua_yyjson_mut_val_mut_copy(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_mut_val_mut_copy(doc, val));
}

static int lua_yyjson_doc_mut_copy(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    yyjson_doc *src = (yyjson_doc *) lua_touserdata(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    yyjson_mut_doc *copy = yyjson_doc_mut_copy(src, NULL);
    if (!copy) {
        lua_pushnil(L);
        lua_pushstring(L, "复制文档失败");
        return 2;
    }
    lua_pushlightuserdata(L, copy);
    return 1;
}

static int lua_yyjson_new_null(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    if (!doc) {
        return luaL_error(L, "参数必须是可变文档");
    }
    yyjson_mut_val *val = yyjson_mut_null(doc);
    if (!val) {
        lua_pushnil(L);
        return 1;
    }
    lua_pushlightuserdata(L, val);
    return 1;
}

static int lua_yyjson_new_true(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    if (!doc) {
        return luaL_error(L, "参数必须是可变文档");
    }
    yyjson_mut_val *val = yyjson_mut_true(doc);
    if (!val) {
        lua_pushnil(L);
        return 1;
    }
    lua_pushlightuserdata(L, val);
    return 1;
}

static int lua_yyjson_new_false(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    if (!doc) {
        return luaL_error(L, "参数必须是可变文档");
    }
    yyjson_mut_val *val = yyjson_mut_false(doc);
    if (!val) {
        lua_pushnil(L);
        return 1;
    }
    lua_pushlightuserdata(L, val);
    return 1;
}

static int lua_yyjson_new_bool(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    bool b = lua_toboolean(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    yyjson_mut_val *val = yyjson_mut_bool(doc, b);
    if (!val) {
        lua_pushnil(L);
        return 1;
    }
    lua_pushlightuserdata(L, val);
    return 1;
}

static int lua_yyjson_new_int(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    int64_t i = luaL_checkinteger(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    yyjson_mut_val *val = yyjson_mut_int(doc, i);
    if (!val) {
        lua_pushnil(L);
        return 1;
    }
    lua_pushlightuserdata(L, val);
    return 1;
}

static int lua_yyjson_new_uint(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    uint64_t i = luaL_checkinteger(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    yyjson_mut_val *val = yyjson_mut_uint(doc, i);
    if (!val) {
        lua_pushnil(L);
        return 1;
    }
    lua_pushlightuserdata(L, val);
    return 1;
}

static int lua_yyjson_new_real(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    double d = luaL_checknumber(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    yyjson_mut_val *val = yyjson_mut_real(doc, d);
    if (!val) {
        lua_pushnil(L);
        return 1;
    }
    lua_pushlightuserdata(L, val);
    return 1;
}

static int lua_yyjson_new_str(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    const char *s = luaL_checkstring(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    yyjson_mut_val *val = yyjson_mut_str(doc, s);
    if (!val) {
        lua_pushnil(L);
        return 1;
    }
    lua_pushlightuserdata(L, val);
    return 1;
}

static int lua_yyjson_new_strn(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    size_t len;
    const char *s = luaL_checklstring(L, 2, &len);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    yyjson_mut_val *val = yyjson_mut_strn(doc, s, len);
    if (!val) {
        lua_pushnil(L);
        return 1;
    }
    lua_pushlightuserdata(L, val);
    return 1;
}

static int lua_yyjson_new_arr(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    if (!doc) {
        return luaL_error(L, "参数必须是可变文档");
    }
    yyjson_mut_val *arr = yyjson_mut_arr(doc);
    if (!arr) {
        lua_pushnil(L);
        return 1;
    }
    lua_pushlightuserdata(L, arr);
    return 1;
}

static int lua_yyjson_new_obj(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    if (!doc) {
        return luaL_error(L, "参数必须是可变文档");
    }
    yyjson_mut_val *obj = yyjson_mut_obj(doc);
    if (!obj) {
        lua_pushnil(L);
        return 1;
    }
    lua_pushlightuserdata(L, obj);
    return 1;
}

static int lua_yyjson_new_arr_with_bool(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    bool b = lua_toboolean(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_mut_arr_with_bool(doc, &b, 1));
}

static int lua_yyjson_new_arr_with_int(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    int64_t i = luaL_checkinteger(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_mut_arr_with_sint(doc, &i, 1));
}

static int lua_yyjson_new_arr_with_uint(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    uint64_t i = luaL_checkinteger(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_mut_arr_with_uint(doc, &i, 1));
}

static int lua_yyjson_new_arr_with_real(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    double d = luaL_checknumber(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_mut_arr_with_real(doc, &d, 1));
}

static int lua_yyjson_new_arr_with_str(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    const char *s = luaL_checkstring(L, 2);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    return push_yyjson_mut_val(L, yyjson_mut_arr_with_str(doc, &s, 1));
}

static int lua_yyjson_new_obj_with_str(lua_State *L) {
    yyjson_mut_doc *doc = (yyjson_mut_doc *) lua_touserdata(L, 1);
    const char *key = luaL_checkstring(L, 2);
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 3);
    if (!doc) {
        return luaL_error(L, "第一个参数必须是可变文档");
    }
    yyjson_mut_val *obj = yyjson_mut_obj(doc);
    yyjson_mut_val *key_val = yyjson_mut_str(doc, key);
    yyjson_mut_obj_add(obj, key_val, val);
    return push_yyjson_mut_val(L, obj);
}

static int lua_yyjson_mut_equals(lua_State *L) {
    yyjson_mut_val *lhs = (yyjson_mut_val *) lua_touserdata(L, 1);
    yyjson_mut_val *rhs = (yyjson_mut_val *) lua_touserdata(L, 2);
    if (!lhs || !rhs) {
        return luaL_error(L, "参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_equals(lhs, rhs));
    return 1;
}

static int lua_yyjson_mut_equals_str(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    const char *s = luaL_checkstring(L, 2);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_equals_str(val, s));
    return 1;
}

static int lua_yyjson_mut_equals_strn(lua_State *L) {
    yyjson_mut_val *val = (yyjson_mut_val *) lua_touserdata(L, 1);
    size_t len;
    const char *s = luaL_checklstring(L, 2, &len);
    if (!val) {
        return luaL_error(L, "第一个参数必须是 yyjson 可变值");
    }
    lua_pushboolean(L, yyjson_mut_equals_strn(val, s, len));
    return 1;
}

static int lua_yyjson_create_and_build_json(lua_State *L) {
    /*
     * 创建并构建 JSON 对象
     * 参数: 无
     * 返回值: 1. 可变文档对象
     * 用法: local doc = yyjson.create()
     *       local root = yyjson.mut_get_root(doc)
     *       yyjson.mut_obj_add(root, "key", value, doc)
     *       local json_str = yyjson.encode(doc)
     */
    yyjson_mut_doc *doc = yyjson_mut_doc_new(NULL);
    if (!doc) {
        lua_pushnil(L);
        lua_pushstring(L, "创建可变文档失败");
        return 2;
    }

    // 自动创建一个空对象作为根对象
    yyjson_mut_val *root = yyjson_mut_obj(doc);
    if (!root) {
        yyjson_mut_doc_free(doc);
        lua_pushnil(L);
        lua_pushstring(L, "创建根对象失败");
        return 2;
    }
    yyjson_mut_doc_set_root(doc, root);

    lua_pushlightuserdata(L, doc);
    return 1;
}

static int lua_yyjson_parse_and_process_json(lua_State *L) {
    /*
     * 解析并处理 JSON 字符串
     * 参数: 1. JSON 字符串
     * 返回值: 1. 可变文档对象
     * 用法: local doc = yyjson.parse_and_process_json('{"name": "test", "age": 18}')
     *       local root = yyjson.mut_doc_get_root(doc)
     *       local name = yyjson.mut_ptr_get(root, ".name")
     */
    const char *json_str = luaL_checkstring(L, 1);
    if (!json_str) {
        return luaL_error(L, "参数必须是字符串");
    }

    // 首先解析为不可变文档
    yyjson_read_err err;
    yyjson_doc *immutable_doc = yyjson_read_opts((char *) json_str, strlen(json_str),
                                                 YYJSON_READ_INSITU, NULL, &err);
    if (!immutable_doc) {
        lua_pushnil(L);
        lua_pushstring(L, err.msg);
        return 2;
    }

    // 转换为可变文档
    yyjson_mut_doc *mutable_doc = yyjson_doc_mut_copy(immutable_doc, NULL);
    yyjson_doc_free(immutable_doc);

    if (!mutable_doc) {
        lua_pushnil(L);
        lua_pushstring(L, "转换为可变文档失败");
        return 2;
    }

    lua_pushlightuserdata(L, mutable_doc);
    return 1;
}

static const luaL_Reg funcs[] = {
        {"read",                  lua_yyjson_read},
        {"decode",                lua_yyjson_decode},
        {"encode",                lua_yyjson_encode},
        {"encode_doc",            lua_yyjson_doc_encode},
        {"encodeCompact",         lua_yyjson_encode_compact},
        {"read_file",             lua_yyjson_read_file},
        {"write_file",            lua_yyjson_write_file},
        {"validate",              lua_yyjson_validate},
        {"is_null",               lua_yyjson_is_null},
        {"is_bool",               lua_yyjson_is_bool},
        {"is_true",               lua_yyjson_is_true},
        {"is_false",              lua_yyjson_is_false},
        {"is_int",                lua_yyjson_is_int},
        {"is_uint",               lua_yyjson_is_uint},
        {"is_real",               lua_yyjson_is_real},
        {"is_num",                lua_yyjson_is_num},
        {"is_str",                lua_yyjson_is_str},
        {"is_arr",                lua_yyjson_is_arr},
        {"is_obj",                lua_yyjson_is_obj},
        {"is_ctn",                lua_yyjson_is_ctn},
        {"get_type",              lua_yyjson_get_type},
        {"get_tag",               lua_yyjson_get_tag},
        {"get_bool",              lua_yyjson_get_bool},
        {"get_int",               lua_yyjson_get_int},
        {"get_uint",              lua_yyjson_get_uint},
        {"get_sint",              lua_yyjson_get_sint},
        {"get_real",              lua_yyjson_get_real},
        {"get_num",               lua_yyjson_get_num},
        {"get_str",               lua_yyjson_get_str},
        {"get_len",               lua_yyjson_get_len},
        {"arr_size",              lua_yyjson_arr_size},
        {"arr_get",               lua_yyjson_arr_get},
        {"arr_get_first",         lua_yyjson_arr_get_first},
        {"arr_get_last",          lua_yyjson_arr_get_last},
        {"obj_size",              lua_yyjson_obj_size},
        {"obj_get",               lua_yyjson_obj_get},
        {"obj_getn",              lua_yyjson_obj_getn},
        {"doc_get_root",          lua_yyjson_doc_get_root},
        {"doc_get_read_size",     lua_yyjson_doc_get_read_size},
        {"doc_get_val_count",     lua_yyjson_doc_get_val_count},
        {"ptr_get",               lua_yyjson_ptr_get},
        {"ptr_getn",              lua_yyjson_ptr_getn},
        {"doc_ptr_get",           lua_yyjson_doc_ptr_get},
        {"doc_ptr_getn",          lua_yyjson_doc_ptr_getn},
        {"mut_new",               lua_yyjson_mut_doc_new},
        {"mut_free",              lua_yyjson_mut_doc_free},
        {"mut_set_root",          lua_yyjson_mut_doc_set_root},
        {"mut_get_root",          lua_yyjson_mut_doc_get_root},
        {"mut_copy",              lua_yyjson_mut_doc_mut_copy},
        {"mut_imut_copy",         lua_yyjson_mut_doc_imut_copy},
        {"mut_is_null",           lua_yyjson_mut_is_null},
        {"mut_is_bool",           lua_yyjson_mut_is_bool},
        {"mut_is_true",           lua_yyjson_mut_is_true},
        {"mut_is_false",          lua_yyjson_mut_is_false},
        {"mut_is_int",            lua_yyjson_mut_is_int},
        {"mut_is_uint",           lua_yyjson_mut_is_uint},
        {"mut_is_real",           lua_yyjson_mut_is_real},
        {"mut_is_num",            lua_yyjson_mut_is_num},
        {"mut_is_str",            lua_yyjson_mut_is_str},
        {"mut_is_arr",            lua_yyjson_mut_is_arr},
        {"mut_is_obj",            lua_yyjson_mut_is_obj},
        {"mut_is_ctn",            lua_yyjson_mut_is_ctn},
        {"mut_get_type",          lua_yyjson_mut_get_type},
        {"mut_get_tag",           lua_yyjson_mut_get_tag},
        {"mut_get_bool",          lua_yyjson_mut_get_bool},
        {"mut_get_int",           lua_yyjson_mut_get_int},
        {"mut_get_uint",          lua_yyjson_mut_get_uint},
        {"mut_get_sint",          lua_yyjson_mut_get_sint},
        {"mut_get_real",          lua_yyjson_mut_get_real},
        {"mut_get_num",           lua_yyjson_mut_get_num},
        {"mut_get_str",           lua_yyjson_mut_get_str},
        {"mut_get_len",           lua_yyjson_mut_get_len},
        {"mut_arr_size",          lua_yyjson_mut_arr_size},
        {"mut_arr_get",           lua_yyjson_mut_arr_get},
        {"mut_arr_get_first",     lua_yyjson_mut_arr_get_first},
        {"mut_arr_get_last",      lua_yyjson_mut_arr_get_last},
        {"mut_arr_append",        lua_yyjson_mut_arr_append},
        {"mut_arr_insert",        lua_yyjson_mut_arr_insert},
        {"mut_arr_prepend",       lua_yyjson_mut_arr_prepend},
        {"mut_arr_replace",       lua_yyjson_mut_arr_replace},
        {"mut_arr_remove",        lua_yyjson_mut_arr_remove},
        {"mut_arr_remove_first",  lua_yyjson_mut_arr_remove_first},
        {"mut_arr_remove_last",   lua_yyjson_mut_arr_remove_last},
        {"mut_arr_clear",         lua_yyjson_mut_arr_clear},
        {"mut_arr_add_null",      lua_yyjson_mut_arr_add_null},
        {"mut_arr_add_true",      lua_yyjson_mut_arr_add_true},
        {"mut_arr_add_false",     lua_yyjson_mut_arr_add_false},
        {"mut_arr_add_bool",      lua_yyjson_mut_arr_add_bool},
        {"mut_arr_add_int",       lua_yyjson_mut_arr_add_int},
        {"mut_arr_add_uint",      lua_yyjson_mut_arr_add_uint},
        {"mut_arr_add_real",      lua_yyjson_mut_arr_add_real},
        {"mut_arr_add_str",       lua_yyjson_mut_arr_add_str},
        {"mut_arr_add_obj",       lua_yyjson_mut_arr_add_obj},
        {"mut_arr_add_arr",       lua_yyjson_mut_arr_add_arr},
        {"mut_obj_size",          lua_yyjson_mut_obj_size},
        {"mut_obj_get",           lua_yyjson_mut_obj_get},
        {"mut_obj_getn",          lua_yyjson_mut_obj_getn},
        {"mut_obj_add",           lua_yyjson_mut_obj_add},
        {"mut_obj_addn",          lua_yyjson_mut_obj_addn},
        {"mut_obj_remove",        lua_yyjson_mut_obj_remove},
        {"mut_obj_removen",       lua_yyjson_mut_obj_removen},
        {"mut_obj_clear",         lua_yyjson_mut_obj_clear},
        {"mut_obj_replace",       lua_yyjson_mut_obj_replace},
        {"mut_set_null",          lua_yyjson_mut_set_null},
        {"mut_set_bool",          lua_yyjson_mut_set_bool},
        {"mut_set_int",           lua_yyjson_mut_set_int},
        {"mut_set_uint",          lua_yyjson_mut_set_uint},
        {"mut_set_real",          lua_yyjson_mut_set_real},
        {"mut_set_str",           lua_yyjson_mut_set_str},
        {"mut_set_arr",           lua_yyjson_mut_set_arr},
        {"mut_set_obj",           lua_yyjson_mut_set_obj},
        {"mut_ptr_get",           lua_yyjson_mut_ptr_get},
        {"mut_ptr_getn",          lua_yyjson_mut_ptr_getn},
        {"mut_doc_ptr_get",       lua_yyjson_mut_doc_ptr_get},
        {"mut_doc_ptr_getn",      lua_yyjson_mut_doc_ptr_getn},
        {"mut_ptr_set",           lua_yyjson_mut_ptr_set},
        {"mut_ptr_setn",          lua_yyjson_mut_ptr_setn},
        {"mut_doc_ptr_set",       lua_yyjson_mut_doc_ptr_set},
        {"mut_doc_ptr_setn",      lua_yyjson_mut_doc_ptr_setn},
        {"mut_ptr_add",           lua_yyjson_mut_ptr_add},
        {"mut_ptr_addn",          lua_yyjson_mut_ptr_addn},
        {"mut_doc_ptr_add",       lua_yyjson_mut_doc_ptr_add},
        {"mut_doc_ptr_addn",      lua_yyjson_mut_doc_ptr_addn},
        {"mut_ptr_remove",        lua_yyjson_mut_ptr_remove},
        {"mut_ptr_removen",       lua_yyjson_mut_ptr_removen},
        {"mut_doc_ptr_remove",    lua_yyjson_mut_doc_ptr_remove},
        {"mut_doc_ptr_removen",   lua_yyjson_mut_doc_ptr_removen},
        {"mut_ptr_replace",       lua_yyjson_mut_ptr_replace},
        {"mut_ptr_replacen",      lua_yyjson_mut_ptr_replacen},
        {"mut_doc_ptr_replace",   lua_yyjson_mut_doc_ptr_replace},
        {"mut_doc_ptr_replacen",  lua_yyjson_mut_doc_ptr_replacen},
        {"mut_val_write",         lua_yyjson_mut_val_write},
        {"mut_val_write_compact", lua_yyjson_mut_val_write_compact},
        {"mut_val_write_file",    lua_yyjson_mut_val_write_file},
        {"val_mut_copy",          lua_yyjson_val_mut_copy},
        {"mut_val_mut_copy",      lua_yyjson_mut_val_mut_copy},
        {"doc_mut_copy",          lua_yyjson_doc_mut_copy},
        {"new_null",              lua_yyjson_new_null},
        {"new_true",              lua_yyjson_new_true},
        {"new_false",             lua_yyjson_new_false},
        {"new_bool",              lua_yyjson_new_bool},
        {"new_int",               lua_yyjson_new_int},
        {"new_uint",              lua_yyjson_new_uint},
        {"new_real",              lua_yyjson_new_real},
        {"new_str",               lua_yyjson_new_str},
        {"new_strn",              lua_yyjson_new_strn},
        {"new_arr",               lua_yyjson_new_arr},
        {"new_obj",               lua_yyjson_new_obj},
        {"new_arr_with_bool",     lua_yyjson_new_arr_with_bool},
        {"new_arr_with_int",      lua_yyjson_new_arr_with_int},
        {"new_arr_with_uint",     lua_yyjson_new_arr_with_uint},
        {"new_arr_with_real",     lua_yyjson_new_arr_with_real},
        {"new_arr_with_str",      lua_yyjson_new_arr_with_str},
        {"new_obj_with_str",      lua_yyjson_new_obj_with_str},
        {"mut_equals",            lua_yyjson_mut_equals},
        {"mut_equals_str",        lua_yyjson_mut_equals_str},
        {"mut_equals_strn",       lua_yyjson_mut_equals_strn},
        {"create",                lua_yyjson_create_and_build_json},
        {"parse",                 lua_yyjson_parse_and_process_json},
        {NULL, NULL}
};


int luaopen_yyjson(lua_State *L) {
#if LUA_VERSION_NUM < 502
    luaL_register(L, MODNAME, funcs);
#else
    luaL_newlib(L, funcs);
#endif

    lua_pushliteral(L, VERSION);
    lua_setfield(L, -2, "_VERSION");

    lua_pushliteral(L, MODNAME);
    lua_setfield(L, -2, "_NAME");

    lua_pushlightuserdata(L, NULL);
    lua_setfield(L, -2, "null");

    return 1;
}
