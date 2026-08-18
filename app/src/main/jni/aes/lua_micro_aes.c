/*
 * lua_micro_aes.c - Lua bindings for μAES library
 * Provides full access to all AES modes implemented in micro_aes.c
 */

#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>
#include <string.h>
#include <stdlib.h>

/* Include the μAES header (must be in the same directory) */
#include "micro_aes.h"

#define BLOCKSIZE 16

/* -------------------- Helper functions -------------------- */

/* Check that key length matches AES_KEYLENGTH */
static void check_key(lua_State *L, int idx) {
    size_t len;
    luaL_checklstring(L, idx, &len);              /* ignore returned pointer */
    if (len != AES_KEYLENGTH) {
        luaL_error(L, "key must be exactly %d bytes (got %zu)", AES_KEYLENGTH, len);
    }
}

/* Retrieve binary data as uint8_t* and its length */
static const uint8_t *get_data(lua_State *L, int idx, size_t *len) {
    return (const uint8_t *) luaL_checklstring(L, idx, len);
}

/* Push binary data as Lua string */
static void push_data(lua_State *L, const uint8_t *data, size_t len) {
    lua_pushlstring(L, (const char *) data, len);
}

/* Check return code and raise Lua error if not success */
static void check_result(lua_State *L, char ret) {
    if (ret != M_RESULT_SUCCESS) {
        luaL_error(L, "operation failed with code %d", ret);
    }
}

/* Check that IV/nonce length matches expected */
static void check_iv_length(lua_State *L, size_t len, size_t expected) {
    if (len != expected) {
        luaL_error(L, "IV/nonce must be exactly %zu bytes (got %zu)", expected, len);
    }
}

/* Check that data length is a multiple of block size (for ECB/CBC decryption) */
static void check_block_multiple(lua_State *L, size_t len, const char *mode) {
    if (len % BLOCKSIZE != 0) {
        luaL_error(L, "%s: input length must be multiple of %d bytes", mode, BLOCKSIZE);
    }
}

/* -------------------- ECB Mode -------------------- */
#if ECB

static int l_ecb_encrypt(lua_State *L) {
    check_key(L, 1);
    size_t ptext_len;
    const uint8_t *ptext = get_data(L, 2, &ptext_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    size_t crtxt_len = ptext_len;
    if (ptext_len % BLOCKSIZE != 0)
        crtxt_len += BLOCKSIZE - (ptext_len % BLOCKSIZE);

    uint8_t *crtxt = (uint8_t *) malloc(crtxt_len);
    if (!crtxt) return luaL_error(L, "out of memory");
    memcpy(crtxt, ptext, ptext_len);

    AES_ECB_encrypt(key, crtxt, ptext_len, crtxt);
    push_data(L, crtxt, crtxt_len);
    free(crtxt);
    return 1;
}

static int l_ecb_decrypt(lua_State *L) {
    check_key(L, 1);
    size_t crtxt_len;
    const uint8_t *crtxt = get_data(L, 2, &crtxt_len);
    check_block_multiple(L, crtxt_len, "ECB decryption");

    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t *ptext = (uint8_t *) malloc(crtxt_len);
    if (!ptext) return luaL_error(L, "out of memory");
    memcpy(ptext, crtxt, crtxt_len);

    char ret = AES_ECB_decrypt(key, ptext, crtxt_len, ptext);
    if (ret != M_RESULT_SUCCESS) {
        free(ptext);
        luaL_error(L, "ECB decryption failed");
    }
    push_data(L, ptext, crtxt_len);
    free(ptext);
    return 1;
}

#endif /* ECB */

/* -------------------- CBC Mode -------------------- */
#if CBC

static int l_cbc_encrypt(lua_State *L) {
    check_key(L, 1);
    size_t iv_len;
    const uint8_t *iv = get_data(L, 2, &iv_len);
    check_iv_length(L, iv_len, BLOCKSIZE);

    size_t ptext_len;
    const uint8_t *ptext = get_data(L, 3, &ptext_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    size_t crtxt_len = ptext_len;
    if (ptext_len % BLOCKSIZE != 0)
        crtxt_len += BLOCKSIZE - (ptext_len % BLOCKSIZE);

    uint8_t *crtxt = (uint8_t *) malloc(crtxt_len);
    if (!crtxt) return luaL_error(L, "out of memory");
    memcpy(crtxt, ptext, ptext_len);

    char ret = AES_CBC_encrypt(key, iv, crtxt, ptext_len, crtxt);
    check_result(L, ret);
    push_data(L, crtxt, crtxt_len);
    free(crtxt);
    return 1;
}

static int l_cbc_decrypt(lua_State *L) {
    check_key(L, 1);
    size_t iv_len;
    const uint8_t *iv = get_data(L, 2, &iv_len);
    check_iv_length(L, iv_len, BLOCKSIZE);

    size_t crtxt_len;
    const uint8_t *crtxt = get_data(L, 3, &crtxt_len);
    check_block_multiple(L, crtxt_len, "CBC decryption");

    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t *ptext = (uint8_t *) malloc(crtxt_len);
    if (!ptext) return luaL_error(L, "out of memory");
    memcpy(ptext, crtxt, crtxt_len);

    char ret = AES_CBC_decrypt(key, iv, ptext, crtxt_len, ptext);
    if (ret != M_RESULT_SUCCESS) {
        free(ptext);
        luaL_error(L, "CBC decryption failed");
    }
    push_data(L, ptext, crtxt_len);
    free(ptext);
    return 1;
}

#endif /* CBC */

/* -------------------- CFB Mode -------------------- */
#if CFB

static int l_cfb_encrypt(lua_State *L) {
    check_key(L, 1);
    size_t iv_len;
    const uint8_t *iv = get_data(L, 2, &iv_len);
    check_iv_length(L, iv_len, BLOCKSIZE);

    size_t data_len;
    const uint8_t *data = get_data(L, 3, &data_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t *out = (uint8_t *) malloc(data_len);
    if (!out) return luaL_error(L, "out of memory");
    memcpy(out, data, data_len);

    AES_CFB_encrypt(key, iv, out, data_len, out);
    push_data(L, out, data_len);
    free(out);
    return 1;
}

static int l_cfb_decrypt(lua_State *L) {
    check_key(L, 1);
    size_t iv_len;
    const uint8_t *iv = get_data(L, 2, &iv_len);
    check_iv_length(L, iv_len, BLOCKSIZE);

    size_t data_len;
    const uint8_t *data = get_data(L, 3, &data_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t *out = (uint8_t *) malloc(data_len);
    if (!out) return luaL_error(L, "out of memory");
    memcpy(out, data, data_len);

    AES_CFB_decrypt(key, iv, out, data_len, out);
    push_data(L, out, data_len);
    free(out);
    return 1;
}

#endif /* CFB */

/* -------------------- OFB Mode -------------------- */
#if OFB

static int l_ofb_encrypt(lua_State *L) {
    check_key(L, 1);
    size_t iv_len;
    const uint8_t *iv = get_data(L, 2, &iv_len);
    check_iv_length(L, iv_len, BLOCKSIZE);

    size_t data_len;
    const uint8_t *data = get_data(L, 3, &data_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t *out = (uint8_t *) malloc(data_len);
    if (!out) return luaL_error(L, "out of memory");
    memcpy(out, data, data_len);

    AES_OFB_encrypt(key, iv, out, data_len, out);
    push_data(L, out, data_len);
    free(out);
    return 1;
}

static int l_ofb_decrypt(lua_State *L) {
    /* OFB encryption and decryption are identical */
    return l_ofb_encrypt(L);
}

#endif /* OFB */

/* -------------------- CTR Mode -------------------- */
#if CTR_NA

static int l_ctr_crypt(lua_State *L) {
    check_key(L, 1);
    size_t iv_len;
    const uint8_t *iv = get_data(L, 2, &iv_len);
    check_iv_length(L, iv_len, CTR_IV_LENGTH);

    size_t data_len;
    const uint8_t *data = get_data(L, 3, &data_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t *out = (uint8_t *) malloc(data_len);
    if (!out) return luaL_error(L, "out of memory");
    memcpy(out, data, data_len);

    AES_CTR_encrypt(key, iv, out, data_len, out);
    push_data(L, out, data_len);
    free(out);
    return 1;
}

#endif /* CTR_NA */

/* -------------------- XTS Mode -------------------- */
#if XTS

static int l_xts_encrypt(lua_State *L) {
    /* keys: two keys concatenated (2 * AES_KEYLENGTH) */
    size_t keys_len;
    const uint8_t *keys = get_data(L, 1, &keys_len);
    if (keys_len != 2 * AES_KEYLENGTH)
        luaL_error(L, "XTS key pair must be exactly %d bytes", 2 * AES_KEYLENGTH);

    size_t tweak_len;
    const uint8_t *tweak = get_data(L, 2, &tweak_len);
    if (tweak_len != BLOCKSIZE && tweak_len != 0)
        luaL_error(L, "XTS tweak must be exactly %d bytes or empty", BLOCKSIZE);

    size_t ptext_len;
    const uint8_t *ptext = get_data(L, 3, &ptext_len);
    if (ptext_len < BLOCKSIZE)
        luaL_error(L, "XTS plaintext length must be at least %d bytes", BLOCKSIZE);

    uint8_t *crtxt = (uint8_t *) malloc(ptext_len);
    if (!crtxt) return luaL_error(L, "out of memory");
    memcpy(crtxt, ptext, ptext_len);

    char ret = AES_XTS_encrypt(keys, tweak_len ? tweak : NULL, crtxt, ptext_len, crtxt);
    check_result(L, ret);
    push_data(L, crtxt, ptext_len);
    free(crtxt);
    return 1;
}

static int l_xts_decrypt(lua_State *L) {
    size_t keys_len;
    const uint8_t *keys = get_data(L, 1, &keys_len);
    if (keys_len != 2 * AES_KEYLENGTH)
        luaL_error(L, "XTS key pair must be exactly %d bytes", 2 * AES_KEYLENGTH);

    size_t tweak_len;
    const uint8_t *tweak = get_data(L, 2, &tweak_len);
    if (tweak_len != BLOCKSIZE && tweak_len != 0)
        luaL_error(L, "XTS tweak must be exactly %d bytes or empty", BLOCKSIZE);

    size_t crtxt_len;
    const uint8_t *crtxt = get_data(L, 3, &crtxt_len);
    if (crtxt_len < BLOCKSIZE)
        luaL_error(L, "XTS ciphertext length must be at least %d bytes", BLOCKSIZE);

    uint8_t *ptext = (uint8_t *) malloc(crtxt_len);
    if (!ptext) return luaL_error(L, "out of memory");
    memcpy(ptext, crtxt, crtxt_len);

    char ret = AES_XTS_decrypt(keys, tweak_len ? tweak : NULL, ptext, crtxt_len, ptext);
    check_result(L, ret);
    push_data(L, ptext, crtxt_len);
    free(ptext);
    return 1;
}

#endif /* XTS */

/* -------------------- CMAC -------------------- */
#if CMAC

static int l_cmac(lua_State *L) {
    check_key(L, 1);
    size_t data_len;
    const uint8_t *data = get_data(L, 2, &data_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t mac[BLOCKSIZE];
    AES_CMAC(key, data, data_len, mac);
    push_data(L, mac, BLOCKSIZE);
    return 1;
}

#endif /* CMAC */

/* -------------------- GCM -------------------- */
#if GCM

static int l_gcm_encrypt(lua_State *L) {
    check_key(L, 1);
    size_t nonce_len;
    const uint8_t *nonce = get_data(L, 2, &nonce_len);
    check_iv_length(L, nonce_len, GCM_NONCE_LEN);

    size_t aad_len;
    const uint8_t *aad = get_data(L, 3, &aad_len);
    size_t ptext_len;
    const uint8_t *ptext = get_data(L, 4, &ptext_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    size_t crtxt_len = ptext_len + GCM_TAG_LEN;
    uint8_t *crtxt = (uint8_t *) malloc(crtxt_len);
    if (!crtxt) return luaL_error(L, "out of memory");
    memcpy(crtxt, ptext, ptext_len);

    AES_GCM_encrypt(key, nonce, aad, aad_len, crtxt, ptext_len, crtxt);
    push_data(L, crtxt, crtxt_len);
    free(crtxt);
    return 1;
}

static int l_gcm_decrypt(lua_State *L) {
    check_key(L, 1);
    size_t nonce_len;
    const uint8_t *nonce = get_data(L, 2, &nonce_len);
    check_iv_length(L, nonce_len, GCM_NONCE_LEN);

    size_t aad_len;
    const uint8_t *aad = get_data(L, 3, &aad_len);
    size_t crtxt_len;
    const uint8_t *crtxt = get_data(L, 4, &crtxt_len);
    if (crtxt_len < GCM_TAG_LEN)
        luaL_error(L, "GCM ciphertext too short (missing tag)");

    size_t ptext_len = crtxt_len - GCM_TAG_LEN;
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t *ptext = (uint8_t *) malloc(ptext_len);
    if (!ptext) return luaL_error(L, "out of memory");

    char ret = AES_GCM_decrypt(key, nonce, aad, aad_len, crtxt, ptext_len, ptext);
    if (ret != M_RESULT_SUCCESS) {
        free(ptext);
        luaL_error(L, "GCM decryption/authentication failed");
    }
    push_data(L, ptext, ptext_len);
    free(ptext);
    return 1;
}

#endif /* GCM */

/* -------------------- CCM -------------------- */
#if CCM

static int l_ccm_encrypt(lua_State *L) {
    check_key(L, 1);
    size_t nonce_len;
    const uint8_t *nonce = get_data(L, 2, &nonce_len);
    check_iv_length(L, nonce_len, CCM_NONCE_LEN);

    size_t aad_len;
    const uint8_t *aad = get_data(L, 3, &aad_len);
    size_t ptext_len;
    const uint8_t *ptext = get_data(L, 4, &ptext_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    size_t crtxt_len = ptext_len + CCM_TAG_LEN;
    uint8_t *crtxt = (uint8_t *) malloc(crtxt_len);
    if (!crtxt) return luaL_error(L, "out of memory");
    memcpy(crtxt, ptext, ptext_len);

    AES_CCM_encrypt(key, nonce, aad, aad_len, crtxt, ptext_len, crtxt);
    push_data(L, crtxt, crtxt_len);
    free(crtxt);
    return 1;
}

static int l_ccm_decrypt(lua_State *L) {
    check_key(L, 1);
    size_t nonce_len;
    const uint8_t *nonce = get_data(L, 2, &nonce_len);
    check_iv_length(L, nonce_len, CCM_NONCE_LEN);

    size_t aad_len;
    const uint8_t *aad = get_data(L, 3, &aad_len);
    size_t crtxt_len;
    const uint8_t *crtxt = get_data(L, 4, &crtxt_len);
    if (crtxt_len < CCM_TAG_LEN)
        luaL_error(L, "CCM ciphertext too short (missing tag)");

    size_t ptext_len = crtxt_len - CCM_TAG_LEN;
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t *ptext = (uint8_t *) malloc(ptext_len);
    if (!ptext) return luaL_error(L, "out of memory");

    char ret = AES_CCM_decrypt(key, nonce, aad, aad_len, crtxt, ptext_len, ptext);
    if (ret != M_RESULT_SUCCESS) {
        free(ptext);
        luaL_error(L, "CCM decryption/authentication failed");
    }
    push_data(L, ptext, ptext_len);
    free(ptext);
    return 1;
}

#endif /* CCM */

/* -------------------- OCB -------------------- */
#if OCB

static int l_ocb_encrypt(lua_State *L) {
    check_key(L, 1);
    size_t nonce_len;
    const uint8_t *nonce = get_data(L, 2, &nonce_len);
    check_iv_length(L, nonce_len, OCB_NONCE_LEN);

    size_t aad_len;
    const uint8_t *aad = get_data(L, 3, &aad_len);
    size_t ptext_len;
    const uint8_t *ptext = get_data(L, 4, &ptext_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    size_t crtxt_len = ptext_len + OCB_TAG_LEN;
    uint8_t *crtxt = (uint8_t *) malloc(crtxt_len);
    if (!crtxt) return luaL_error(L, "out of memory");
    memcpy(crtxt, ptext, ptext_len);

    AES_OCB_encrypt(key, nonce, aad, aad_len, crtxt, ptext_len, crtxt);
    push_data(L, crtxt, crtxt_len);
    free(crtxt);
    return 1;
}

static int l_ocb_decrypt(lua_State *L) {
    check_key(L, 1);
    size_t nonce_len;
    const uint8_t *nonce = get_data(L, 2, &nonce_len);
    check_iv_length(L, nonce_len, OCB_NONCE_LEN);

    size_t aad_len;
    const uint8_t *aad = get_data(L, 3, &aad_len);
    size_t crtxt_len;
    const uint8_t *crtxt = get_data(L, 4, &crtxt_len);
    if (crtxt_len < OCB_TAG_LEN)
        luaL_error(L, "OCB ciphertext too short (missing tag)");

    size_t ptext_len = crtxt_len - OCB_TAG_LEN;
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t *ptext = (uint8_t *) malloc(ptext_len);
    if (!ptext) return luaL_error(L, "out of memory");

    char ret = AES_OCB_decrypt(key, nonce, aad, aad_len, crtxt, ptext_len, ptext);
    if (ret != M_RESULT_SUCCESS) {
        free(ptext);
        luaL_error(L, "OCB decryption/authentication failed");
    }
    push_data(L, ptext, ptext_len);
    free(ptext);
    return 1;
}

#endif /* OCB */

/* -------------------- EAX -------------------- */
#if EAX

/* EAX has two variants: EAX (with AAD) and EAX' (EAXP, with nonce only) */
static int l_eax_encrypt(lua_State *L) {
    check_key(L, 1);
#if EAXP
    /* EAX' signature: key, nonce, nonce_len, plaintext */
    size_t nonce_len;
    const uint8_t* nonce = get_data(L, 2, &nonce_len);
    size_t ptext_len;
    const uint8_t* ptext = get_data(L, 3, &ptext_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    size_t crtxt_len = ptext_len + 4; /* EAX' tag is 4 bytes */
    uint8_t* crtxt = (uint8_t*)malloc(crtxt_len);
    if (!crtxt) return luaL_error(L, "out of memory");
    memcpy(crtxt, ptext, ptext_len);

    AES_EAX_encrypt(key, nonce, nonce_len, NULL, 0, crtxt, ptext_len, crtxt);
    push_data(L, crtxt, crtxt_len);
    free(crtxt);
#else
    /* EAX signature: key, nonce, aad, aad_len, plaintext */
    size_t nonce_len;
    const uint8_t *nonce = get_data(L, 2, &nonce_len);
    /* 检查 nonce 长度是否为 EAX_NONCE_LEN（16） */
    if (nonce_len != EAX_NONCE_LEN) {
        return luaL_error(L, "nonce must be exactly %d bytes for EAX mode", EAX_NONCE_LEN);
    }
    size_t aad_len;
    const uint8_t *aad = get_data(L, 3, &aad_len);
    size_t ptext_len;
    const uint8_t *ptext = get_data(L, 4, &ptext_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    size_t crtxt_len = ptext_len + EAX_TAG_LEN;
    uint8_t *crtxt = (uint8_t *) malloc(crtxt_len);
    if (!crtxt) return luaL_error(L, "out of memory");

    /* 正确调用：参数为 key, nonce, aad, aad_len, ptext, ptext_len, crtxt */
    AES_EAX_encrypt(key, nonce, aad, aad_len, ptext, ptext_len, crtxt);
    push_data(L, crtxt, crtxt_len);
    free(crtxt);
#endif
    return 1;
}

static int l_eax_decrypt(lua_State *L) {
    check_key(L, 1);
#if EAXP
    size_t nonce_len;
    const uint8_t* nonce = get_data(L, 2, &nonce_len);
    size_t crtxt_len;
    const uint8_t* crtxt = get_data(L, 3, &crtxt_len);
    if (crtxt_len < 4) luaL_error(L, "EAX' ciphertext too short");
    size_t ptext_len = crtxt_len - 4;
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t* ptext = (uint8_t*)malloc(ptext_len);
    if (!ptext) return luaL_error(L, "out of memory");

    char ret = AES_EAX_decrypt(key, nonce, nonce_len, NULL, 0, crtxt, ptext_len, ptext);
    if (ret != M_RESULT_SUCCESS) {
        free(ptext);
        luaL_error(L, "EAX' decryption/authentication failed");
    }
    push_data(L, ptext, ptext_len);
    free(ptext);
#else
    size_t nonce_len;
    const uint8_t *nonce = get_data(L, 2, &nonce_len);
    /* 检查 nonce 长度 */
    if (nonce_len != EAX_NONCE_LEN) {
        return luaL_error(L, "nonce must be exactly %d bytes for EAX mode", EAX_NONCE_LEN);
    }
    size_t aad_len;
    const uint8_t *aad = get_data(L, 3, &aad_len);
    size_t crtxt_len;
    const uint8_t *crtxt = get_data(L, 4, &crtxt_len);
    if (crtxt_len < EAX_TAG_LEN) luaL_error(L, "EAX ciphertext too short");
    size_t ptext_len = crtxt_len - EAX_TAG_LEN;
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t *ptext = (uint8_t *) malloc(ptext_len);
    if (!ptext) return luaL_error(L, "out of memory");

    /* 正确调用：参数为 key, nonce, aad, aad_len, crtxt, ptext_len, ptext */
    char ret = AES_EAX_decrypt(key, nonce, aad, aad_len, crtxt, ptext_len, ptext);
    if (ret != M_RESULT_SUCCESS) {
        free(ptext);
        luaL_error(L, "EAX decryption/authentication failed");
    }
    push_data(L, ptext, ptext_len);
    free(ptext);
#endif
    return 1;
}

#endif /* EAX */

/* -------------------- SIV -------------------- */
#if SIV

static int l_siv_encrypt(lua_State *L) {
    size_t keys_len;
    const uint8_t *keys = get_data(L, 1, &keys_len);
    if (keys_len != 2 * AES_KEYLENGTH)
        luaL_error(L, "SIV key pair must be exactly %d bytes", 2 * AES_KEYLENGTH);

    size_t aad_len;
    const uint8_t *aad = get_data(L, 2, &aad_len);
    size_t ptext_len;
    const uint8_t *ptext = get_data(L, 3, &ptext_len);

    size_t crtxt_len = ptext_len + BLOCKSIZE; /* SIV prepends IV */
    uint8_t *crtxt = (uint8_t *) malloc(crtxt_len);
    if (!crtxt) return luaL_error(L, "out of memory");

    /* crtxt will contain IV followed by ciphertext */
    AES_SIV_encrypt(keys, aad, aad_len, ptext, ptext_len, crtxt, crtxt + BLOCKSIZE);
    push_data(L, crtxt, crtxt_len);
    free(crtxt);
    return 1;
}

static int l_siv_decrypt(lua_State *L) {
    size_t keys_len;
    const uint8_t *keys = get_data(L, 1, &keys_len);
    if (keys_len != 2 * AES_KEYLENGTH)
        luaL_error(L, "SIV key pair must be exactly %d bytes", 2 * AES_KEYLENGTH);

    size_t aad_len;
    const uint8_t *aad = get_data(L, 2, &aad_len);
    size_t in_len;
    const uint8_t *in = get_data(L, 3, &in_len);
    if (in_len < BLOCKSIZE)
        luaL_error(L, "SIV input too short (missing IV)");

    size_t crtxt_len = in_len - BLOCKSIZE;
    const uint8_t *iv = in;
    const uint8_t *crtxt = in + BLOCKSIZE;

    uint8_t *ptext = (uint8_t *) malloc(crtxt_len);
    if (!ptext) return luaL_error(L, "out of memory");

    char ret = AES_SIV_decrypt(keys, iv, aad, aad_len, crtxt, crtxt_len, ptext);
    if (ret != M_RESULT_SUCCESS) {
        free(ptext);
        luaL_error(L, "SIV decryption/authentication failed");
    }
    push_data(L, ptext, crtxt_len);
    free(ptext);
    return 1;
}

#endif /* SIV */

/* -------------------- GCM-SIV -------------------- */
#if GCM_SIV

static int l_gcm_siv_encrypt(lua_State *L) {
    check_key(L, 1);
    size_t nonce_len;
    const uint8_t *nonce = get_data(L, 2, &nonce_len);
    check_iv_length(L, nonce_len, SIVGCM_NONCE_LEN);

    size_t aad_len;
    const uint8_t *aad = get_data(L, 3, &aad_len);
    size_t ptext_len;
    const uint8_t *ptext = get_data(L, 4, &ptext_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    size_t crtxt_len = ptext_len + SIVGCM_TAG_LEN;
    uint8_t *crtxt = (uint8_t *) malloc(crtxt_len);
    if (!crtxt) return luaL_error(L, "out of memory");
    memcpy(crtxt, ptext, ptext_len);

    GCM_SIV_encrypt(key, nonce, aad, aad_len, crtxt, ptext_len, crtxt);
    push_data(L, crtxt, crtxt_len);
    free(crtxt);
    return 1;
}

static int l_gcm_siv_decrypt(lua_State *L) {
    check_key(L, 1);
    size_t nonce_len;
    const uint8_t *nonce = get_data(L, 2, &nonce_len);
    check_iv_length(L, nonce_len, SIVGCM_NONCE_LEN);

    size_t aad_len;
    const uint8_t *aad = get_data(L, 3, &aad_len);
    size_t crtxt_len;
    const uint8_t *crtxt = get_data(L, 4, &crtxt_len);
    if (crtxt_len < SIVGCM_TAG_LEN)
        luaL_error(L, "GCM-SIV ciphertext too short (missing tag)");

    size_t ptext_len = crtxt_len - SIVGCM_TAG_LEN;
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t *ptext = (uint8_t *) malloc(ptext_len);
    if (!ptext) return luaL_error(L, "out of memory");

    char ret = GCM_SIV_decrypt(key, nonce, aad, aad_len, crtxt, ptext_len, ptext);
    if (ret != M_RESULT_SUCCESS) {
        free(ptext);
        luaL_error(L, "GCM-SIV decryption/authentication failed");
    }
    push_data(L, ptext, ptext_len);
    free(ptext);
    return 1;
}

#endif /* GCM_SIV */

/* -------------------- Key Wrap (KW) -------------------- */
#if KWA

static int l_key_wrap(lua_State *L) {
    size_t kek_len;
    const uint8_t *kek = get_data(L, 1, &kek_len);
    if (kek_len != AES_KEYLENGTH)
        luaL_error(L, "KEK must be exactly %d bytes", AES_KEYLENGTH);

    size_t secret_len;
    const uint8_t *secret = get_data(L, 2, &secret_len);
    if (secret_len % (BLOCKSIZE / 2) != 0 || secret_len < BLOCKSIZE)
        luaL_error(L, "secret length must be multiple of %d and at least %d", BLOCKSIZE / 2,
                   BLOCKSIZE);

    size_t wrap_len = secret_len + BLOCKSIZE / 2;
    uint8_t *wrapped = (uint8_t *) malloc(wrap_len);
    if (!wrapped) return luaL_error(L, "out of memory");

    char ret = AES_KEY_wrap(kek, secret, secret_len, wrapped);
    check_result(L, ret);
    push_data(L, wrapped, wrap_len);
    free(wrapped);
    return 1;
}

static int l_key_unwrap(lua_State *L) {
    size_t kek_len;
    const uint8_t *kek = get_data(L, 1, &kek_len);
    if (kek_len != AES_KEYLENGTH)
        luaL_error(L, "KEK must be exactly %d bytes", AES_KEYLENGTH);

    size_t wrap_len;
    const uint8_t *wrapped = get_data(L, 2, &wrap_len);
    if (wrap_len % (BLOCKSIZE / 2) != 0 || wrap_len < BLOCKSIZE + BLOCKSIZE / 2)
        luaL_error(L, "wrapped length must be multiple of %d and at least %d", BLOCKSIZE / 2,
                   BLOCKSIZE + BLOCKSIZE / 2);

    size_t secret_len = wrap_len - BLOCKSIZE / 2;
    uint8_t *secret = (uint8_t *) malloc(secret_len);
    if (!secret) return luaL_error(L, "out of memory");

    char ret = AES_KEY_unwrap(kek, wrapped, wrap_len, secret);
    if (ret != M_RESULT_SUCCESS) {
        free(secret);
        luaL_error(L, "key unwrap authentication failed");
    }
    push_data(L, secret, secret_len);
    free(secret);
    return 1;
}

#endif /* KWA */

/* -------------------- FPE -------------------- */
#if FPE

/* FPE works on strings of characters from a given alphabet.
   The Lua bindings assume the default alphabet (digits) unless CUSTOM_ALPHABET is defined.
   Input/output are Lua strings. The tweak is a binary string.
   For FF1, tweak length is variable; for FF3-1, tweak must be exactly 7 bytes.
*/
static int l_fpe_encrypt(lua_State *L) {
    check_key(L, 1);
#if FF_X != 3
    size_t tweak_len;
    const uint8_t *tweak = get_data(L, 2, &tweak_len);
#else
    size_t tweak_len = 7;
    const uint8_t* tweak = get_data(L, 2, &tweak_len);
    if (tweak_len != 7)
        luaL_error(L, "FF3-1 tweak must be exactly 7 bytes");
#endif

    size_t text_len;
    const uint8_t *text = get_data(L, 3, &text_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    /* Output buffer same length as input */
    uint8_t *out = (uint8_t *) malloc(text_len + 1); /* +1 for safety */
    if (!out) return luaL_error(L, "out of memory");
    memcpy(out, text, text_len);

    char ret;
#if FF_X == 3
    ret = AES_FPE_encrypt(key, tweak, out, text_len, out);
#else
    ret = AES_FPE_encrypt(key, tweak, tweak_len, out, text_len, out);
#endif
    if (ret != M_RESULT_SUCCESS) {
        free(out);
        luaL_error(L, "FPE encryption failed");
    }
    push_data(L, out, text_len);
    free(out);
    return 1;
}

static int l_fpe_decrypt(lua_State *L) {
    check_key(L, 1);
#if FF_X != 3
    size_t tweak_len;
    const uint8_t *tweak = get_data(L, 2, &tweak_len);
#else
    size_t tweak_len = 7;
    const uint8_t* tweak = get_data(L, 2, &tweak_len);
    if (tweak_len != 7)
        luaL_error(L, "FF3-1 tweak must be exactly 7 bytes");
#endif

    size_t text_len;
    const uint8_t *text = get_data(L, 3, &text_len);
    uint8_t key[AES_KEYLENGTH];
    memcpy(key, lua_tostring(L, 1), AES_KEYLENGTH);

    uint8_t *out = (uint8_t *) malloc(text_len + 1);
    if (!out) return luaL_error(L, "out of memory");
    memcpy(out, text, text_len);

    char ret;
#if FF_X == 3
    ret = AES_FPE_decrypt(key, tweak, out, text_len, out);
#else
    ret = AES_FPE_decrypt(key, tweak, tweak_len, out, text_len, out);
#endif
    if (ret != M_RESULT_SUCCESS) {
        free(out);
        luaL_error(L, "FPE decryption failed");
    }
    push_data(L, out, text_len);
    free(out);
    return 1;
}

#endif /* FPE */

/* -------------------- Poly1305 -------------------- */
#if POLY1305

static int l_poly1305(lua_State *L) {
    size_t keys_len;
    const uint8_t *keys = get_data(L, 1, &keys_len);
    if (keys_len != AES_KEYLENGTH + 16)
        luaL_error(L, "Poly1305 key pair must be exactly %d bytes (AES key + 16-byte r)",
                   AES_KEYLENGTH + 16);

    size_t nonce_len;
    const uint8_t *nonce = get_data(L, 2, &nonce_len);
    if (nonce_len != 16)
        luaL_error(L, "Poly1305 nonce must be exactly 16 bytes");

    size_t data_len;
    const uint8_t *data = get_data(L, 3, &data_len);

    uint8_t mac[16];
    AES_Poly1305(keys, nonce, data, data_len, mac);
    push_data(L, mac, 16);
    return 1;
}

#endif /* POLY1305 */

/* -------------------- Module table -------------------- */
static const luaL_Reg micro_aes_lib[] = {
#if ECB
        {"ecb_encrypt", l_ecb_encrypt},
        {"ecb_decrypt", l_ecb_decrypt},
#endif
#if CBC
        {"cbc_encrypt", l_cbc_encrypt},
        {"cbc_decrypt", l_cbc_decrypt},
#endif
#if CFB
        {"cfb_encrypt", l_cfb_encrypt},
        {"cfb_decrypt", l_cfb_decrypt},
#endif
#if OFB
        {"ofb_encrypt", l_ofb_encrypt},
        {"ofb_decrypt", l_ofb_decrypt},
#endif
#if CTR_NA
        {"ctr_crypt", l_ctr_crypt},   /* same for encrypt/decrypt */
#endif
#if XTS
        {"xts_encrypt", l_xts_encrypt},
        {"xts_decrypt", l_xts_decrypt},
#endif
#if CMAC
        {"cmac", l_cmac},
#endif
#if GCM
        {"gcm_encrypt", l_gcm_encrypt},
        {"gcm_decrypt", l_gcm_decrypt},
#endif
#if CCM
        {"ccm_encrypt", l_ccm_encrypt},
        {"ccm_decrypt", l_ccm_decrypt},
#endif
#if OCB
        {"ocb_encrypt", l_ocb_encrypt},
        {"ocb_decrypt", l_ocb_decrypt},
#endif
#if EAX
        {"eax_encrypt", l_eax_encrypt},
        {"eax_decrypt", l_eax_decrypt},
#endif
#if SIV
        {"siv_encrypt", l_siv_encrypt},
        {"siv_decrypt", l_siv_decrypt},
#endif
#if GCM_SIV
        {"gcm_siv_encrypt", l_gcm_siv_encrypt},
        {"gcm_siv_decrypt", l_gcm_siv_decrypt},
#endif
#if KWA
        {"key_wrap", l_key_wrap},
        {"key_unwrap", l_key_unwrap},
#endif
#if FPE
        {"fpe_encrypt", l_fpe_encrypt},
        {"fpe_decrypt", l_fpe_decrypt},
#endif
#if POLY1305
        {"poly1305", l_poly1305},
#endif
        {NULL, NULL}
};

/* -------------------- Module open function -------------------- */
LUAMOD_API int luaopen_aes(lua_State *L) {
    luaL_newlib(L, micro_aes_lib);

    /* Provide useful constants */
    lua_pushinteger(L, BLOCKSIZE);
    lua_setfield(L, -2, "BLOCKSIZE");

    lua_pushinteger(L, AES_KEYLENGTH);
    lua_setfield(L, -2, "KEYLENGTH");

#if GCM
    lua_pushinteger(L, GCM_TAG_LEN);
    lua_setfield(L, -2, "GCM_TAG_LEN");
#endif
#if CCM
    lua_pushinteger(L, CCM_TAG_LEN);
    lua_setfield(L, -2, "CCM_TAG_LEN");
#endif
#if OCB
    lua_pushinteger(L, OCB_TAG_LEN);
    lua_setfield(L, -2, "OCB_TAG_LEN");
#endif
#if EAX && !EAXP
    lua_pushinteger(L, EAX_TAG_LEN);
    lua_setfield(L, -2, "EAX_TAG_LEN");
#endif
#if SIV
    /* SIV tag/IV is always 16 bytes */
    lua_pushinteger(L, 16);
    lua_setfield(L, -2, "SIV_TAG_LEN");
#endif
#if GCM_SIV
    lua_pushinteger(L, SIVGCM_TAG_LEN);
    lua_setfield(L, -2, "GCM_SIV_TAG_LEN");
#endif
#if POLY1305
    lua_pushinteger(L, 16);
    lua_setfield(L, -2, "POLY1305_TAG_LEN");
#endif
#if CTR_NA
    lua_pushinteger(L, CTR_IV_LENGTH);
    lua_setfield(L, -2, "CTR_IV_LENGTH");
#endif
#if GCM
    lua_pushinteger(L, GCM_NONCE_LEN);
    lua_setfield(L, -2, "GCM_NONCE_LEN");
#endif
#if CCM
    lua_pushinteger(L, CCM_NONCE_LEN);
    lua_setfield(L, -2, "CCM_NONCE_LEN");
#endif
#if OCB
    lua_pushinteger(L, OCB_NONCE_LEN);
    lua_setfield(L, -2, "OCB_NONCE_LEN");
#endif
#if GCM_SIV
    lua_pushinteger(L, SIVGCM_NONCE_LEN);
    lua_setfield(L, -2, "GCM_SIV_NONCE_LEN");
#endif
#if EAX && !EAXP
    lua_pushinteger(L, EAX_NONCE_LEN);
    lua_setfield(L, -2, "EAX_NONCE_LEN");
#endif

    /* Add version/identification */
    lua_pushliteral(L, "μAES Lua binding");
    lua_setfield(L, -2, "_VERSION");

    return 1;
}