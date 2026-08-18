package muling.views.tool.utils

import androidx.annotation.Keep
import com.luajava.LuaException
import com.luajava.LuaState
import java.io.File

@Keep
object ConsoleUtil {
    /**
     * 编译脚本文件（Lua 或 ALY）
     * @param state Lua 状态
     * @param path 文件路径
     * @return Lua table 对象 {path, error}
     */
    @JvmStatic
    fun build(state: LuaState, path: String): Any {
        synchronized(state) {
            try {
                // 检查文件是否存在
                val file = File(path)
                if (!file.exists()) {
                    throw LuaException("File does not exist: $path")
                }

                // 获取文件扩展名
                val extension = getFileExtension(file)

                // 根据扩展名判断类型
                return when (extension.lowercase()) {
                    "lua" -> compileLuaFile(state, path)
                    "aly" -> compileALYFile(state, path)
                    else -> throw LuaException("Unsupported file type: .$extension")
                }
            } catch (e: LuaException) {
                // 将 Lua 异常信息返回给 Lua 层
                return createResultTable(state, null, e.message)
            } catch (e: Exception) {
                // 将其他异常信息返回给 Lua 层
                return createResultTable(state, null, "Internal error: ${e.message}")
            }
        }
    }

    /**
     * 获取文件扩展名
     */
    private fun getFileExtension(file: File): String {
        val name = file.name
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0 && dotIndex < name.length - 1) {
            name.substring(dotIndex + 1)
        } else {
            ""
        }
    }

    /**
     * 创建结果 table
     */
    private fun createResultTable(state: LuaState, path: String?, error: String?): Any {
        // 保存当前栈顶位置
        val topBefore = state.top

        // 创建一个新的 Lua table
        state.newTable()

        // 设置 path 字段
        if (path != null) {
            state.pushString(path)
            state.setField(-2, "path")
        } else {
            state.pushNil()
            state.setField(-2, "path")
        }

        // 设置 error 字段
        if (error != null) {
            state.pushString(error)
            state.setField(-2, "error")
        } else {
            state.pushNil()
            state.setField(-2, "error")
        }

        // 现在栈顶是创建的 table，确保只有一个元素
        val newTop = state.top
        if (newTop != topBefore + 1) {
            // 栈状态异常，清理并返回空 table
            state.top = topBefore
            state.newTable()
        }

        // 获取 table 对象
        try {
            val table = state.toJavaObject(-1)
            // 弹出 table
            state.pop(1)
            return table ?: "null"
        } catch (e: Exception) {
            // 如果无法获取 table 对象，返回一个简单的 Map
            state.pop(state.top - topBefore)  // 清理栈
            val resultMap = HashMap<String, Any?>()
            resultMap["path"] = path
            resultMap["error"] = error
            return resultMap
        }
    }

    /**
     * 编译 Lua 文件
     */
    private fun compileLuaFile(state: LuaState, path: String): Any {
        val luaCode = """
            function build(path)
                if path then
                    local str, st = loadfile(path)
                    if st then
                        return nil, st
                    end
                    local path = path .. 'c'
                    
                    local st, str = pcall(string.dump, str, true)
                    if st then
                        local f = io.open(path, 'wb')
                        f:write(str)
                        f:close()
                        return path, nil
                    else
                        os.remove(path)
                        return nil, str
                    end
                end
            end
            
            return build('$path')
        """.trimIndent()

        val result = executeLuaCode(state, luaCode)
        return createResultTable(state, result.first, result.second)
    }

    /**
     * 编译 ALY 文件
     */
    private fun compileALYFile(state: LuaState, path: String): Any {
        val luaCode = """
            function build_aly(path2)
                if path2 then
                    local f, st = io.open(path2)
                    if st then
                        return nil, st
                    end
                    local str = f:read("*a")
                    f:close()
                    str = string.format("local layout=%s\nreturn layout", str)
                    local path = path2 .. 'c'
                    str, st = loadstring(str, path2:match("[^/]+/[^/]+$"), "bt")
                    if st then
                        return nil, st:gsub("%b[]", path2, 1)
                    end

                    local st, str = pcall(string.dump, str, true)
                    if st then
                        f = io.open(path, 'wb')
                        f:write(str)
                        f:close()
                        return path, nil
                    else
                        os.remove(path)
                        return nil, str
                    end
                end
            end
            
            return build_aly('$path')
        """.trimIndent()

        val result = executeLuaCode(state, luaCode)
        return createResultTable(state, result.first, result.second)
    }

    /**
     * 执行 Lua 代码
     */
    private fun executeLuaCode(state: LuaState, luaCode: String): Pair<String?, String?> {
        try {
            // 保存当前栈顶位置
            val topBefore = state.top

            // 加载 Lua 代码
            val ok = state.LloadString(luaCode)
            if (ok != 0) {
                return Pair(null, "Failed to load Lua code: ${state.toString(-1)}")
            }

            // 设置错误处理函数
            state.getGlobal("debug")
            state.getField(-1, "traceback")
            state.remove(-2)
            state.insert(-2)

            // 执行 Lua 代码，期望 2 个返回值
            val result = state.pcall(0, 2, -2)
            if (result != 0) {
                return Pair(null, "Failed to execute Lua code ($result): ${state.toString(-1)}")
            }

            // 获取两个返回值
            val return2 = state.toJavaObject(-1)  // 第二个返回值
            val return1 = state.toJavaObject(-2)  // 第一个返回值

            // 清理栈，恢复到原始状态
            state.top = topBefore

            // 根据 Lua 的约定：成功时返回 (路径, nil)，失败时返回 (nil, 错误信息)
            val pathResult = if (return1 != null) return1.toString() else null
            val errorResult = if (return2 != null) return2.toString() else null

            return Pair(pathResult, errorResult)

        } catch (e: LuaException) {
            throw LuaException("Lua error: ${e.message}")
        } catch (e: Exception) {
            throw LuaException("Error executing Lua code: ${e.message}")
        } finally {
            // 确保栈被清理
            state.top = 0
        }
    }
}