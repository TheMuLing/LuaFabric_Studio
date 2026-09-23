package muling.views.tool.utils

import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject

@Keep
object JsonUtil {

    @JvmStatic
    fun parseObject(jsonString: String): Map<String, Any?> {
        val json = JSONObject(jsonString)
        return jsonToMap(json)
    }

    @JvmStatic
    fun parseArray(jsonString: String): List<Any?> {
        val json = JSONArray(jsonString)
        return jsonToList(json)
    }

    @JvmStatic
    fun toFormattedString(map: Map<String, Any?>, indentSpaces: Int = 4): String {
        return JSONObject(map).toString(indentSpaces)
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            map[key] = convertJsonValue(value)
        }
        return map
    }

    private fun jsonToList(json: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until json.length()) {
            val value = json.get(i)
            list.add(convertJsonValue(value))
        }
        return list
    }

    private fun convertJsonValue(value: Any): Any? {
        return when (value) {
            is JSONObject -> jsonToMap(value)
            is JSONArray -> jsonToList(value)
            JSONObject.NULL -> null
            else -> value
        }
    }
}