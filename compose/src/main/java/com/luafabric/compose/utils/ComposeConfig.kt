package com.luafabric.compose.utils

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Compose 项目二进制配置编解码（与 compose_demo/gen_conf.py 同源实现；compose 模块内副本，
 * 包名 com.luafabric.compose.utils，与 view 模块 muling.views.tool.utils.ComposeConfig 逐字节同源）。
 *
 * 单文件 build.gradle.b85 = base85( header(4B) + sha256(32B) + BSON ) :
 *   header     "LC" + schemaVer 0x01 + flags（bit0=debugmode，预启动直读）
 *   sha256     覆盖 [0:4]+BSON 全字节（防「误以为改成功」，不防故意重算）
 *   BSON       自描述、二进制 JSON 语义（int32 LE 长度前缀 + 类型字节）
 *
 * 判定无效即重生：magic/schemaVer/SHA-256/BSON 长度任一失败 → 整份回模板。
 * 本实现是 Python 参考实现（gen_conf.py）的逐字节镜像，encode/decode/verify 同源。
 */
object ComposeConfig {

    /** magic */
    private const val MAGIC_B0 = 'L'.code.toByte()
    private const val MAGIC_B1 = 'C'.code.toByte()

    /** schema 版本：升/降 → 判未知 → 重生 */
    const val SCHEMA_VER: Int = 0x01

    /** flags bit0 = debugmode */
    const val FLAG_DEBUG: Int = 0x01

    /** 配置文件标准名（项目根判定 + 落盘文件名） */
    const val FILE_NAME = "build.gradle.b85"

    /** RFC 1924 Base85 字母表（与 Python base64.b85encode 一致） */
    private val B85_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#\$%&()*+-;<=>?@^_`{|}~"

    // ---------- BSON 类型字节 ----------
    private const val T_DOUBLE = 0x01
    private const val T_STRING = 0x02
    private const val T_DOC = 0x03
    private const val T_ARRAY = 0x04
    private const val T_BOOL = 0x08
    private const val T_NULL = 0x0A
    private const val T_INT32 = 0x10
    private const val T_INT64 = 0x12

    // ---------- header ----------
    private fun header(flags: Int): ByteArray =
        byteArrayOf(MAGIC_B0, MAGIC_B1, SCHEMA_VER.toByte(), flags.toByte())

    private fun d80(digits: IntArray): CharArray = CharArray(5) { B85_ALPHABET[digits[it]] }

    /** Base85 编码：每 4 字节大端 uint32 → 5 个 85 进制数字；末尾不足 4 字节补 0 后裁尾（Python pad=False 语义） */
    fun b85Encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val padding = (-data.size).mod(4)
        val padded = if (padding != 0) data + ByteArray(padding) else data
        val sb = StringBuilder(padded.size / 4 * 5)
        var i = 0
        while (i < padded.size) {
            val word = ((padded[i].toLong() and 0xFF) shl 24) or
                ((padded[i + 1].toLong() and 0xFF) shl 16) or
                ((padded[i + 2].toLong() and 0xFF) shl 8) or
                (padded[i + 3].toLong() and 0xFF)
            sb.append(d80(intArrayOf(
                ((word / 52200625) % 85).toInt(), // 85^4
                ((word / 614125) % 85).toInt(),   // 85^3
                ((word / 7225) % 85).toInt(),     // 85^2
                ((word / 85) % 85).toInt(),
                (word % 85).toInt()
            )))
            i += 4
        }
        if (padding != 0) sb.setLength(sb.length - padding)
        return sb.toString()
    }

    fun b85Decode(text: String): ByteArray {
        if (text.isEmpty()) return ByteArray(0)
        val padding = (-text.length).mod(5)
        val padded = text + "~".repeat(padding)
        val out = ByteArrayOutputStream((padded.length / 5) * 4)
        var i = 0
        while (i < padded.length) {
            var acc = 0L
            for (j in 0 until 5) {
                val idx = B85_ALPHABET.indexOf(padded[i + j])
                require(idx >= 0) { "bad base85 char at ${i + j}" }
                acc = acc * 85 + idx
            }
            require(acc < (1L shl 32)) { "base85 overflow" }
            out.write(((acc shr 24).toInt() and 0xFF))
            out.write(((acc shr 16).toInt() and 0xFF))
            out.write(((acc shr 8).toInt() and 0xFF))
            out.write((acc.toInt() and 0xFF))
            i += 5
        }
        val bytes = out.toByteArray()
        return if (padding != 0) bytes.copyOf(bytes.size - padding) else bytes
    }

    // ---------- BSON writer（镜像 gen_conf.py） ----------

    private fun cstr(s: String): ByteArray {
        val raw = s.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(raw.size + 1).array() + raw + byteArrayOf(0)
    }

    private fun bson(value: Any?): ByteArray {
        return when (value) {
            null -> byteArrayOf(T_NULL.toByte(), 0)
            is Boolean -> byteArrayOf(T_BOOL.toByte(), if (value) 1 else 0)
            is Int -> {
                if (value >= Int.MIN_VALUE) {
                    byteArrayOf(T_INT32.toByte()) +
                        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
                } else {
                    byteArrayOf(T_INT64.toByte()) +
                        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value.toLong()).array()
                }
            }
            is Long -> {
                if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    byteArrayOf(T_INT32.toByte()) +
                        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array()
                } else {
                    byteArrayOf(T_INT64.toByte()) +
                        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
                }
            }
            is Double -> byteArrayOf(T_DOUBLE.toByte()) +
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array()
            is String -> byteArrayOf(T_STRING.toByte()) + cstr(value)
            is Map<*, *> -> {
                // Python: b"\x03" + _cstr("")[1:] + int32(len(body)+4) + body
                //        body = join(bson(k) + bson(v)) + b"\x00"（嵌套 key 带类型字节）
                val body = ByteArrayOutputStream()
                value.forEach { (k, v) ->
                    body.write(bson(k.toString()))   // 嵌套 dict 的 key 也走 bson → 带 0x02 前缀
                    body.write(bson(v))
                }
                body.write(0)
                byteArrayOf(T_DOC.toByte()) +
                    ByteArray(4) + // Python 的 _cstr("")[1:] —— 空 key 占位 4 字节
                    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size() + 4).array() +
                    body.toByteArray()
            }
            is List<*> -> {
                val body = ByteArrayOutputStream()
                value.forEachIndexed { idx, v ->
                    body.write(cstr(idx.toString()))
                    body.write(bson(v))
                }
                body.write(0)
                byteArrayOf(T_ARRAY.toByte()) +
                    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size() + 4).array() +
                    body.toByteArray()
            }
            else -> throw IllegalArgumentException("unsupported bson type: ${value?.javaClass}")
        }
    }

    fun bsonDocument(doc: Map<String, Any?>): ByteArray {
        val body = ByteArrayOutputStream()
        doc.forEach { (k, v) ->
            body.write(cstr(k))
            body.write(bson(v))
        }
        body.write(0)
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(body.size() + 4).array() + body.toByteArray()
    }

    // ---------- pack / verify / decode ----------

    fun pack(doc: Map<String, Any?>, flags: Int = 0): ByteArray {
        val bsonBytes = bsonDocument(doc)
        val h = header(flags)
        val digest = MessageDigest.getInstance("SHA-256").digest(h + bsonBytes)
        return h + digest + bsonBytes
    }

    /** 判定有效性，返回 "valid" 或 "invalid: <原因>"（与 gen_conf.py verify 同语义） */
    fun verify(raw: ByteArray): String {
        if (raw.size < 40 || raw[0] != MAGIC_B0 || raw[1] != MAGIC_B1) return "invalid: magic"
        if (raw[2].toInt() != SCHEMA_VER) return "invalid: schema ver"
        val expected = raw.copyOfRange(4, 36)
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(raw.copyOfRange(0, 4) + raw.copyOfRange(36, raw.size))
        if (!MessageDigest.isEqual(expected, actual)) return "invalid: sha256"
        val bsonLen = leInt(raw, 36)
        if (bsonLen != raw.size - 36) return "invalid: bson length"
        return "valid"
    }

    /** b85 明文全文 → 原始字节（容忍尾随换行） */
    fun decodeB85Text(text: String): ByteArray = b85Decode(text.trim())

    /** 便捷入口：b85 全文 → 解码 Map（任一步失败返回 null） */
    fun load(text: String): Map<String, Any?>? {
        val raw = try { decodeB85Text(text) } catch (e: Exception) { return null }
        return decodeDocument(raw)
    }

    /** 解码 BSON 文档：校验通过则解析，否则 null */
    fun decodeDocument(raw: ByteArray): Map<String, Any?>? {
        if (verify(raw) != "valid") return null
        return try {
            parseDocument(raw, 36).value as Map<String, Any?>
        } catch (e: Exception) {
            null
        }
    }

    /** 预启动直读 debugmode：仅取 header 前 4 字节 flags bit0，不解析 BSON；无效返回 null */
    fun debugFlag(raw: ByteArray): Boolean? {
        if (raw.size < 4 || raw[0] != MAGIC_B0 || raw[1] != MAGIC_B1) return null
        if (raw[2].toInt() != SCHEMA_VER) return null
        return (raw[3].toInt() and FLAG_DEBUG) != 0
    }

    // ---------- BSON reader ----------

    private fun leInt(buf: ByteArray, off: Int): Int =
        ByteBuffer.wrap(buf, off, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private data class Parsed(val value: Any?, val next: Int)

    /**
     * 解析 BSON 文档 body（不带头部的 int32 总长）。
     * @param keyed Python 嵌套 dict 的 key 走 bson(k)（带 0x02 类型字节）；
     *              顶层 doc 与 array 的 key 走裸 cstr（无类型字节）。
     */
    private fun parseBody(buf: ByteArray, start: Int, total: Int, keyed: Boolean): Parsed {
        val map = linkedMapOf<String, Any?>()
        var p = start
        while (p < start + total && buf[p] != 0.toByte()) {
            if (keyed) {
                // 嵌套 dict key：bson(k) = 0x02 + cstr(k)
                val type = buf[p].toInt() and 0xFF
                check(type == T_STRING) { "nested doc key must be string, got 0x${type.toString(16)}" }
                p += 1
            }
            val keyLen = leInt(buf, p)
            val key = String(buf, p + 4, keyLen - 1, Charsets.UTF_8)
            p += 4 + keyLen
            val parsed = parseValue(buf, p)
            map[key] = parsed.value
            p = parsed.next
        }
        return Parsed(map, start + if (total > 0) total else p - start)
    }

    private fun parseDocument(buf: ByteArray, start: Int): Parsed {
        val total = leInt(buf, start)
        return parseBody(buf, start + 4, total - 4, keyed = false)
    }

    private fun parseValue(buf: ByteArray, p0: Int): Parsed {
        val type = buf[p0].toInt() and 0xFF
        var p = p0 + 1
        return when (type) {
            T_NULL -> Parsed(null, p)
            T_BOOL -> {
                val v = buf[p] != 0.toByte()
                Parsed(v, p + 1)
            }
            T_INT32 -> {
                val v = leInt(buf, p)
                Parsed(v, p + 4)
            }
            T_INT64 -> {
                val v = ByteBuffer.wrap(buf, p, 8).order(ByteOrder.LITTLE_ENDIAN).long
                Parsed(v, p + 8)
            }
            T_DOUBLE -> {
                val v = ByteBuffer.wrap(buf, p, 8).order(ByteOrder.LITTLE_ENDIAN).double
                Parsed(v, p + 8)
            }
            T_STRING -> {
                val len = leInt(buf, p)
                val v = String(buf, p + 4, len - 1, Charsets.UTF_8)
                Parsed(v, p + 4 + len)
            }
            T_DOC -> {
                // Python: \x03 + _cstr("")[1:]（4 零字节占位）+ int32(len(body)+4) + body
                p += 4 // 跳过空 key 占位
                val total = leInt(buf, p)
                val inner = parseBody(buf, p + 4, total - 4, keyed = true)
                Parsed(inner.value, p + total)
            }
            T_ARRAY -> {
                val total = leInt(buf, p)
                val list = mutableListOf<Any?>()
                var q = p + 4
                val end = p + total
                while (q < end && buf[q] != 0.toByte()) {
                    val keyLen = leInt(buf, q)
                    q += 4 + keyLen
                    val parsed = parseValue(buf, q)
                    list.add(parsed.value)
                    q = parsed.next
                }
                Parsed(list, end)
            }
            else -> throw IllegalArgumentException("unknown bson type $type")
        }
    }
}