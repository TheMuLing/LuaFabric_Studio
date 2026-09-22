package muling.views.tool.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ComposeConfig 与 compose_demo/gen_conf.py 参考实现对拍测试。
 * 向量由 Python 生成（gen_conf.py build 运行产物），见 test/resources/。
 */
class ComposeConfigTest {

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun bytesToHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    private fun resource(name: String): String =
        File("src/test/resources/$name").readText().trim()

    // ---- b85 编码/解码对拍（Python base64.b85encode 向量） ----

    @Test
    fun b85EncodeMatchesPythonVectors() {
        assertEquals("", ComposeConfig.b85Encode(hexToBytes("")))
        assertEquals("K>", ComposeConfig.b85Encode(byteArrayOf(0x41)))
        assertEquals("VPaz", ComposeConfig.b85Encode(hexToBytes("616263")))
        assertEquals("K|(`B", ComposeConfig.b85Encode(hexToBytes("41424344")))
        assertEquals("K|(`BMF", ComposeConfig.b85Encode(hexToBytes("4142434445")))
        assertEquals("F)}kWH8wW", ComposeConfig.b85Encode(hexToBytes("31323334353637")))
        assertEquals("F)}kWH8wXm", ComposeConfig.b85Encode(hexToBytes("3132333435363738")))
    }

    @Test
    fun b85DecodeMatchesPythonVectors() {
        assertEquals("", bytesToHex(ComposeConfig.b85Decode("")))
        assertEquals("41", bytesToHex(ComposeConfig.b85Decode("K>")))
        assertEquals("616263", bytesToHex(ComposeConfig.b85Decode("VPaz")))
        assertEquals("41424344", bytesToHex(ComposeConfig.b85Decode("K|(`B")))
        assertEquals("4142434445", bytesToHex(ComposeConfig.b85Decode("K|(`BMF")))
    }

    // ---- 真实 CONFIG（gen_conf.py 的 CONFIG，flags=0x01，由 Python 生成向量） ----

    private val configVec = mapOf(
        "name" to "协作清单",
        "packageId" to "cn.lf.demo",
        "versionCode" to 1L,
        "versionName" to "1.0.0",
        "minSdk" to 29L,
        "targetSdk" to 36L,
        "uiMode" to "compose",
        "entry" to "main.lua",
        "icon" to "res/icon.png",
        "theme" to mapOf(
            "dark" to false,
            "dynamic" to true,
            "seed" to 0x3A6CC8L
        ),
        "deps" to listOf("coil", "material3"),
        "global_utils" to emptyList<Any>()
    )

    @Test
    fun packMatchesPythonVectorAndVerify() {
        val raw = ComposeConfig.pack(configVec, flags = 0x01)
        // 头部断言
        assertEquals(0x4C, raw[0].toInt() and 0xFF)
        assertEquals(0x43, raw[1].toInt() and 0xFF)
        assertEquals(0x01, raw[2].toInt() and 0xFF)

        // 与 Python pack 的 raw 字节逐字节一致
        val rawVec = hexToBytes(resource("raw_hex_vec.txt"))
        println("DIFF python=${bytesToHex(rawVec)}")
        println("DIFF kotlin=${bytesToHex(raw)}")
        val m = bytesToHex(rawVec).zip(bytesToHex(raw)).indexOfFirst { it.first != it.second }
        println("DIFF first-diff-index=$m")
        assertEquals(rawVec.size, raw.size)
        assertEquals(bytesToHex(rawVec), bytesToHex(raw))

        // verify 通过
        assertEquals("valid", ComposeConfig.verify(raw))
    }

    @Test
    fun loadFromPythonB85ProducesSameConfig() {
        val map = ComposeConfig.load(resource("config_b85_vec.txt"))
        assertEquals("协作清单", map?.get("name"))
        assertEquals("cn.lf.demo", map?.get("packageId"))
        assertEquals(1, (map?.get("versionCode") as Number).toInt())
        assertEquals("1.0.0", map?.get("versionName"))
        assertEquals(29, (map?.get("minSdk") as Number).toInt())
        assertEquals(36, (map?.get("targetSdk") as Number).toInt())
        assertEquals("compose", map?.get("uiMode"))
        assertEquals("main.lua", map?.get("entry"))
        assertEquals("res/icon.png", map?.get("icon"))
        assertEquals(false, (map?.get("theme") as Map<*, *>)["dark"])
        assertEquals(true, (map["theme"] as Map<*, *>)["dynamic"])
        assertEquals(listOf("coil", "material3"), map?.get("deps"))
        assertEquals(emptyList<Any>(), map?.get("global_utils"))
    }

    @Test
    fun tamperFailsSha256() {
        val raw = ComposeConfig.pack(configVec, flags = 0x01)
        val tampered = raw.copyOf()
        tampered[40] = (tampered[40].toInt() xor 0x01).toByte()
        assertTrue(ComposeConfig.verify(tampered).startsWith("invalid: sha256"))
    }

    @Test
    fun magicAndSchemaRejected() {
        val raw = ComposeConfig.pack(configVec, flags = 0x01)
        val badMagic = raw.copyOf().also { it[0] = 'X'.code.toByte() }
        assertTrue(ComposeConfig.verify(badMagic).startsWith("invalid: magic"))

        val badSchema = raw.copyOf().also { it[2] = 0x02 }
        assertTrue(ComposeConfig.verify(badSchema).startsWith("invalid: schema ver"))
    }

    @Test
    fun debugFlagReadsHeaderOnly() {
        val raw = ComposeConfig.pack(configVec, flags = 0x01)
        assertEquals(true, ComposeConfig.debugFlag(raw))
        val raw0 = ComposeConfig.pack(configVec, flags = 0x00)
        assertEquals(false, ComposeConfig.debugFlag(raw0))
        // 无效文件 → null
        assertNull(ComposeConfig.debugFlag(byteArrayOf(0x4C, 0x4F, 0x01, 0x01)))
    }

    @Test
    fun roundTripViaB85TextWithNewline() {
        val raw = ComposeConfig.pack(configVec, flags = 1)
        val text = ComposeConfig.b85Encode(raw) + "\n"
        val map = ComposeConfig.load(text)
        assertEquals("协作清单", map?.get("name"))
        assertEquals(1, (map?.get("versionCode") as Number).toInt())
        assertEquals(listOf("coil", "material3"), map?.get("deps"))
    }
}