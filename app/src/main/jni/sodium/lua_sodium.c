/*
 * lua_sodium.c - Lua bindings for libsodium (1.0.22)
 * Requires: require "sodium"
 */

#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>
#include <string.h>
#include <stdlib.h>

#include <sodium.h>

static int l_sodium_version(lua_State *L) {
    lua_pushstring(L, sodium_version_string());
    return 1;
}

static int l_sodium_randombytes(lua_State *L) {
    size_t n = (size_t) luaL_checkinteger(L, 1);
    unsigned char *buf = (unsigned char *) malloc(n ? n : 1);
    if (buf == NULL) return luaL_error(L, "out of memory");
    randombytes_buf(buf, n);
    lua_pushlstring(L, (const char *) buf, n);
    free(buf);
    return 1;
}

static int l_sodium_bin2hex(lua_State *L) {
    size_t len;
    const char *bin = luaL_checklstring(L, 1, &len);
    char *hex = malloc(len * 2 + 1);
    if (hex == NULL) return luaL_error(L, "out of memory");
    sodium_bin2hex(hex, len * 2 + 1, (const unsigned char *) bin, len);
    lua_pushstring(L, hex);
    free(hex);
    return 1;
}

static int l_sodium_hex2bin(lua_State *L) {
    size_t len;
    const char *hex = luaL_checklstring(L, 1, &len);
    const char *ignore = luaL_optstring(L, 2, NULL);
    unsigned char *bin = malloc(len / 2 + 1);
    if (bin == NULL) return luaL_error(L, "out of memory");
    size_t bin_len = 0;
    if (sodium_hex2bin(bin, len / 2 + 1, hex, len, ignore, &bin_len, NULL) != 0) {
        free(bin);
        return luaL_error(L, "invalid hex string");
    }
    lua_pushlstring(L, (const char *) bin, bin_len);
    free(bin);
    return 1;
}

static int l_sodium_bin2base64(lua_State *L) {
    size_t len;
    const char *bin = luaL_checklstring(L, 1, &len);
    int variant = (int) luaL_optinteger(L, 2, sodium_base64_VARIANT_ORIGINAL);
    size_t out_len = sodium_base64_encoded_len(len, variant);
    char *b64 = malloc(out_len);
    if (b64 == NULL) return luaL_error(L, "out of memory");
    sodium_bin2base64(b64, out_len, (const unsigned char *) bin, len, variant);
    lua_pushstring(L, b64);
    free(b64);
    return 1;
}

static int l_sodium_base642bin(lua_State *L) {
    size_t len;
    const char *b64 = luaL_checklstring(L, 1, &len);
    int variant = (int) luaL_optinteger(L, 2, sodium_base64_VARIANT_ORIGINAL);
    const char *ignore = luaL_optstring(L, 3, NULL);
    unsigned char *bin = malloc(len);
    if (bin == NULL) return luaL_error(L, "out of memory");
    size_t bin_len = 0;
    if (sodium_base642bin(bin, len, b64, len, ignore, &bin_len, NULL, variant) != 0) {
        free(bin);
        return luaL_error(L, "invalid base64 string");
    }
    lua_pushlstring(L, (const char *) bin, bin_len);
    free(bin);
    return 1;
}

static int l_sodium_generichash(lua_State *L) {
    size_t msg_len;
    const char *msg = luaL_checklstring(L, 1, &msg_len);
    size_t out_len = (size_t) luaL_optinteger(L, 2, crypto_generichash_BYTES);
    const char *key = NULL;
    size_t key_len = 0;
    if (!lua_isnoneornil(L, 3)) key = luaL_checklstring(L, 3, &key_len);
    if (out_len < crypto_generichash_BYTES_MIN || out_len > crypto_generichash_BYTES_MAX)
        return luaL_error(L, "invalid output length");
    unsigned char *out = malloc(out_len);
    if (out == NULL) return luaL_error(L, "out of memory");
    if (crypto_generichash(out, out_len, (const unsigned char *) msg, msg_len,
                           (const unsigned char *) key, key_len) != 0) {
        free(out);
        return luaL_error(L, "generichash failed");
    }
    lua_pushlstring(L, (const char *) out, out_len);
    free(out);
    return 1;
}

static int l_sodium_secretbox_easy(lua_State *L) {
    size_t msg_len, nonce_len, key_len;
    const char *msg = luaL_checklstring(L, 1, &msg_len);
    const char *nonce = luaL_checklstring(L, 2, &nonce_len);
    const char *key = luaL_checklstring(L, 3, &key_len);
    if (nonce_len != crypto_secretbox_NONCEBYTES || key_len != crypto_secretbox_KEYBYTES)
        return luaL_error(L, "nonce must be %d bytes, key must be %d bytes",
                          crypto_secretbox_NONCEBYTES, crypto_secretbox_KEYBYTES);
    unsigned char *ct = malloc(msg_len + crypto_secretbox_MACBYTES);
    if (ct == NULL) return luaL_error(L, "out of memory");
    if (crypto_secretbox_easy(ct, (const unsigned char *) msg, msg_len,
                              (const unsigned char *) nonce, (const unsigned char *) key) != 0) {
        free(ct);
        return luaL_error(L, "secretbox failed");
    }
    lua_pushlstring(L, (const char *) ct, msg_len + crypto_secretbox_MACBYTES);
    free(ct);
    return 1;
}

static int l_sodium_secretbox_open_easy(lua_State *L) {
    size_t ct_len, nonce_len, key_len;
    const char *ct = luaL_checklstring(L, 1, &ct_len);
    const char *nonce = luaL_checklstring(L, 2, &nonce_len);
    const char *key = luaL_checklstring(L, 3, &key_len);
    if (nonce_len != crypto_secretbox_NONCEBYTES || key_len != crypto_secretbox_KEYBYTES)
        return luaL_error(L, "nonce must be %d bytes, key must be %d bytes",
                          crypto_secretbox_NONCEBYTES, crypto_secretbox_KEYBYTES);
    if (ct_len < crypto_secretbox_MACBYTES) return luaL_error(L, "ciphertext too short");
    unsigned char *pt = malloc(ct_len);
    if (pt == NULL) return luaL_error(L, "out of memory");
    if (crypto_secretbox_open_easy(pt, (const unsigned char *) ct, ct_len,
                                   (const unsigned char *) nonce, (const unsigned char *) key) != 0) {
        free(pt);
        return luaL_error(L, "decryption failed (bad key/nonce/tampered)");
    }
    lua_pushlstring(L, (const char *) pt, ct_len - crypto_secretbox_MACBYTES);
    free(pt);
    return 1;
}

static int l_sodium_sign_detached(lua_State *L) {
    size_t msg_len, sk_len;
    const char *msg = luaL_checklstring(L, 1, &msg_len);
    const char *sk = luaL_checklstring(L, 2, &sk_len);
    if (sk_len != crypto_sign_SECRETKEYBYTES)
        return luaL_error(L, "secret key must be %d bytes", crypto_sign_SECRETKEYBYTES);
    unsigned char *sig = malloc(crypto_sign_BYTES);
    if (sig == NULL) return luaL_error(L, "out of memory");
    if (crypto_sign_detached(sig, NULL, (const unsigned char *) msg, msg_len,
                             (const unsigned char *) sk) != 0) {
        free(sig);
        return luaL_error(L, "sign failed");
    }
    lua_pushlstring(L, (const char *) sig, crypto_sign_BYTES);
    free(sig);
    return 1;
}

static int l_sodium_sign_verify_detached(lua_State *L) {
    size_t sig_len, msg_len, pk_len;
    const char *sig = luaL_checklstring(L, 1, &sig_len);
    const char *msg = luaL_checklstring(L, 2, &msg_len);
    const char *pk = luaL_checklstring(L, 3, &pk_len);
    if (sig_len != crypto_sign_BYTES || pk_len != crypto_sign_PUBLICKEYBYTES)
        return luaL_error(L, "signature must be %d bytes, public key must be %d bytes",
                          crypto_sign_BYTES, crypto_sign_PUBLICKEYBYTES);
    lua_pushboolean(L, crypto_sign_verify_detached((const unsigned char *) sig,
                                                   (const unsigned char *) msg, msg_len,
                                                   (const unsigned char *) pk) == 0);
    return 1;
}

static int l_sodium_sign_keypair(lua_State *L) {
    unsigned char pk[crypto_sign_PUBLICKEYBYTES];
    unsigned char sk[crypto_sign_SECRETKEYBYTES];
    if (crypto_sign_keypair(pk, sk) != 0) return luaL_error(L, "keypair generation failed");
    lua_pushlstring(L, (const char *) pk, crypto_sign_PUBLICKEYBYTES);
    lua_pushlstring(L, (const char *) sk, crypto_sign_SECRETKEYBYTES);
    return 2;
}

static int l_sodium_scalarmult_base(lua_State *L) {
    size_t n_len;
    const char *n = luaL_checklstring(L, 1, &n_len);
    if (n_len != crypto_scalarmult_BYTES)
        return luaL_error(L, "scalar must be %d bytes", crypto_scalarmult_BYTES);
    unsigned char q[crypto_scalarmult_BYTES];
    if (crypto_scalarmult_base(q, (const unsigned char *) n) != 0)
        return luaL_error(L, "scalarmult failed");
    lua_pushlstring(L, (const char *) q, crypto_scalarmult_BYTES);
    return 1;
}

static int l_sodium_pwhash(lua_State *L) {
    size_t pw_len, salt_len;
    const char *pw = luaL_checklstring(L, 1, &pw_len);
    const char *salt = luaL_checklstring(L, 2, &salt_len);
    size_t out_len = (size_t) luaL_optinteger(L, 3, crypto_pwhash_BYTES_MIN);
    unsigned long long ops = (unsigned long long) luaL_optinteger(L, 4, crypto_pwhash_OPSLIMIT_INTERACTIVE);
    size_t mem = (size_t) luaL_optinteger(L, 5, crypto_pwhash_MEMLIMIT_INTERACTIVE);
    if (salt_len != crypto_pwhash_SALTBYTES)
        return luaL_error(L, "salt must be %d bytes", crypto_pwhash_SALTBYTES);
    unsigned char *out = malloc(out_len);
    if (out == NULL) return luaL_error(L, "out of memory");
    if (crypto_pwhash(out, out_len, pw, pw_len, (const unsigned char *) salt,
                      ops, mem, crypto_pwhash_ALG_DEFAULT) != 0) {
        free(out);
        return luaL_error(L, "pwhash failed (memlimit too low?)");
    }
    lua_pushlstring(L, (const char *) out, out_len);
    free(out);
    return 1;
}

static const luaL_Reg sodium_lib[] = {
    {"version",            l_sodium_version},
    {"randombytes",        l_sodium_randombytes},
    {"bin2hex",            l_sodium_bin2hex},
    {"hex2bin",            l_sodium_hex2bin},
    {"bin2base64",         l_sodium_bin2base64},
    {"base642bin",         l_sodium_base642bin},
    {"generichash",        l_sodium_generichash},
    {"secretbox_easy",     l_sodium_secretbox_easy},
    {"secretbox_open_easy", l_sodium_secretbox_open_easy},
    {"sign_detached",      l_sodium_sign_detached},
    {"sign_verify_detached", l_sodium_sign_verify_detached},
    {"sign_keypair",       l_sodium_sign_keypair},
    {"scalarmult_base",    l_sodium_scalarmult_base},
    {"pwhash",             l_sodium_pwhash},
    {NULL, NULL}
};

LUAMOD_API int luaopen_sodium(lua_State *L) {
    if (sodium_init() < 0) return luaL_error(L, "sodium_init failed");
    luaL_newlib(L, sodium_lib);
    lua_pushinteger(L, crypto_secretbox_KEYBYTES);  lua_setfield(L, -2, "KEYBYTES");
    lua_pushinteger(L, crypto_secretbox_NONCEBYTES); lua_setfield(L, -2, "NONCEBYTES");
    lua_pushinteger(L, crypto_sign_PUBLICKEYBYTES); lua_setfield(L, -2, "SIGN_PUBLICKEYBYTES");
    lua_pushinteger(L, crypto_sign_SECRETKEYBYTES); lua_setfield(L, -2, "SIGN_SECRETKEYBYTES");
    lua_pushinteger(L, crypto_pwhash_SALTBYTES);    lua_setfield(L, -2, "PWHASH_SALTBYTES");
    lua_pushinteger(L, sodium_base64_VARIANT_ORIGINAL);  lua_setfield(L, -2, "B64_ORIGINAL");
    lua_pushinteger(L, sodium_base64_VARIANT_URLSAFE_NO_PADDING); lua_setfield(L, -2, "B64_URLSAFE");
    return 1;
}