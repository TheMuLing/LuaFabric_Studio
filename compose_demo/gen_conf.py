#!/usr/bin/env python3
# gen_conf.py — 生成 LuaFabric Compose 项目的二进制配置产物
#
# 定稿布局（单文件，无 settings.json）：
#   [0..1]  magic   "LC"
#   [2]     ver     0x01（schema 版本：升/降 → 判未知 → 重生）
#   [3]     flags   bit0=debugmode（预启动直读，无需启 Lua）
#   [4..35] SHA-256 校验段（覆盖 [0:4]+BSON 全字节；故意重算拦不住，也不需拦——
#           攻击面 = 用户自身设备/自身运行时，防篡改是伪需求，防「误以为改成功」才是）
#   [36..]  BSON 文档（自描述、二进制 JSON 语义；内部 int32 长度须 == 文件长-36）
#
# 判定无效即重生（不兜底修）：magic/ver/SHA-256/BSON 长度任一项失败 → 整份回模板。
#   - B 类威胁（解码→改→重算→回写）：不承诺拦截，由 schema 版本 + 用户知情兜底
#   - 可选：重生前把坏文件闪存为 build.gradle.bak（闪存 ≠ 擦屁股）
#
# 定稿形态：单文件 build.gradle.b85 = base85( header(4) + sha256(32) + BSON )
#   —— base85 文本化便于贴码/版本库 diff/资产带出；.bson 纯二进制仅为调试留（不复用作规范）

import struct, hashlib, hmac

# ---------- 文本化编码 ----------
# 注：base91/base122 无官方参考实现，手写易错（实测规范算法边界越表）；
# 落库先用标准库 base85（RFC 1924，4B→5 字符，13.3% 明文化），往返由 stdlib 保证。
# 若要严格 base91，直接引 pybase91 / 等成熟实现，不在本模拟阶段手写。
import base64

def b91_encode(data: bytes) -> str:
    return base64.b85encode(data).decode("ascii")

def b91_decode(text: str) -> bytes:
    return base64.b85decode(text.encode("ascii"))

# ---------- 极简 BSON 写入器（够用即可：string/int32/int64/bool/null/doc/array） ----------
def _cstr(s: str) -> bytes:
    raw = s.encode("utf-8")
    return struct.pack("<i", len(raw) + 1) + raw + b"\x00"

def bson(value):
    if value is None:
        return b"\x0A" + b"\x00"
    if isinstance(value, bool):
        return b"\x08" + (b"\x01" if value else b"\x00")
    if isinstance(value, int):
        if -2 ** 31 <= value < 2 ** 31:
            return b"\x10" + struct.pack("<i", value)
        return b"\x12" + struct.pack("<q", value)
    if isinstance(value, float):
        return b"\x01" + struct.pack("<d", value)
    if isinstance(value, str):
        return b"\x02" + _cstr(value)
    if isinstance(value, dict):
        body = b"".join(bson(k) + bson(v) for k, v in value.items()) + b"\x00"
        return b"\x03" + _cstr("")[1:] + struct.pack("<i", len(body) + 4) + body
    if isinstance(value, list):
        body = b"".join(_cstr(str(i)) + bson(v) for i, v in enumerate(value)) + b"\x00"
        return b"\x04" + struct.pack("<i", len(body) + 4) + body
    raise TypeError(type(value))

def bson_document(value: dict) -> bytes:
    body = b"".join(_cstr(k) + bson(v) for k, v in value.items()) + b"\x00"
    return struct.pack("<i", len(body) + 4) + body

# ---------- 配置内容（Compile 侧 schema v1） ----------
CONFIG = {
    "name": "协作清单",              # 项目名（UTF-8）
    "packageId": "cn.lf.demo",       # 包名
    "versionCode": 1,
    "versionName": "1.0.0",
    "minSdk": 29,
    "targetSdk": 36,
    "uiMode": "compose",             # 运行时标记：compose 不再走 loadlayout
    "entry": "main.lua",
    "icon": "res/icon.png",
    "theme": {"dark": False, "dynamic": True, "seed": 0x3A6CC8},
    "deps": ["coil", "material3"],   # 构建期解析（build.gradle.lua 职责之外的元数据）
    "global_utils": [],              # 白名单辅助库：compose 项目不再挂旧全局
}

# ---------- 定稿组装： header(4) + sha256(32) + bson ----------
def pack(doc: dict, flags: int = 0x01) -> bytes:
    bson_bytes = bson_document(doc)
    header = b"LC" + bytes([0x01, flags])
    digest = hashlib.sha256(header + bson_bytes).digest()
    return header + digest + bson_bytes

def verify(raw: bytes) -> str:
    if len(raw) < 40 or raw[0:2] != b"LC":
        return "invalid: magic"
    if raw[2] != 0x01:
        return "invalid: schema ver"
    digest_expected = raw[4:36]
    digest_actual = hashlib.sha256(raw[0:4] + raw[36:]).digest()
    if not hmac.compare_digest(digest_expected, digest_actual):
        return "invalid: sha256"
    bson_len = struct.unpack("<i", raw[36:40])[0]        # BSON int32 含自身 4B 头
    if bson_len != len(raw) - 36:
        return "invalid: bson length"
    return "valid"

def build() -> None:
    raw = pack(CONFIG, flags=0x01)                     # debug=1
    b85 = b91_encode(raw)

    with open("build.gradle.b85", "w", encoding="ascii") as f:
        f.write(b85)
        f.write("\n")

    # 往返自检 + 篡改演示（改 1 字节 → 判定 invalid，即「手改即重生」的成立前提）
    assert b91_decode(b85) == raw, "codec round-trip failed"
    tampered = bytearray(raw)
    tampered[40] ^= 1
    assert verify(raw) == "valid"
    assert verify(bytes(tampered)) == "invalid: sha256", "tamper must be rejected"
    print(f"OK  raw={len(raw)}B (bson={len(raw)-36}B)  b85={len(b85)} chars  {verify(raw)}")
    print(f"TAMPER {verify(bytes(tampered))}  <- 单字节翻转即判无效重生")

if __name__ == "__main__":
    build()