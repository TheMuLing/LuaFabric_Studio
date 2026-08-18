package muling.views.tool.utils

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.recyclerview.widget.RecyclerView
import com.androlua.LuaActivity
import com.luajava.LuaException
import com.luajava.LuaObject
import com.luajava.LuaState
import com.luajava.LuaTable

/**
 * RecyclerView适配器工具类
 * 提供创建和管理RecyclerView适配器的功能
 *
 * @version 1.0.0
 */
@Keep
object RecyclerAdapterUtil {

    private const val TAG = "RecyclerAdapterUtil"

    /**
     * 创建自定义RecyclerView适配器
     * @param context Lua上下文，需要是LuaActivity类型
     * @param data 数据列表，可以是 List<Any> 或 LuaTable
     * @param listItem 列表项布局资源ID或布局视图对象
     * @param method Lua方法表对象，包含setViews和onBindViewHolder方法
     * @return LuaRecyclerAdapter
     */
    @JvmStatic
    fun createAdapter(
        context: Context,
        data: Any,
        listItem: Any,
        method: LuaObject
    ): LuaRecyclerAdapter {
        val luaActivity = context as LuaActivity

        // 将数据转换为 List<Any>
        val dataList = convertToJavaList(data, luaActivity)

        Log.d(TAG, "创建适配器，数据大小: ${dataList.size}")

        // 创建支持数据管理的AdapterCreator实现
        val adapterCreator = DataAdapterCreatorImpl(
            context = luaActivity,
            initialData = dataList,
            listItem = listItem,
            method = method
        )

        return LuaRecyclerAdapter(adapterCreator)
    }

    /**
     * 创建带有多种视图类型的适配器
     */
    @JvmStatic
    fun createMultiTypeAdapter(
        context: Context,
        data: Any,
        typeMap: Map<Int, Any>, // viewType -> layout
        method: LuaObject
    ): LuaRecyclerAdapter {
        val luaActivity = context as LuaActivity
        val dataList = convertToJavaList(data, luaActivity)

        // 创建支持多种视图类型的AdapterCreator实现
        val adapterCreator = MultiTypeAdapterCreatorImpl(
            context = luaActivity,
            initialData = dataList,
            typeMap = typeMap,
            method = method
        )

        return LuaRecyclerAdapter(adapterCreator)
    }

    /**
     * 获取适配器的数据列表
     */
    @JvmStatic
    fun getAdapterData(adapter: RecyclerView.Adapter<*>): List<Any> {
        return if (adapter is LuaRecyclerAdapter) {
            adapter.getData()
        } else {
            emptyList()
        }
    }

    /**
     * 更新适配器数据
     */
    @JvmStatic
    fun updateAdapterData(adapter: RecyclerView.Adapter<*>, newData: Any, context: Context) {
        if (adapter is LuaRecyclerAdapter) {
            val luaActivity = context as LuaActivity
            val dataList = convertToJavaList(newData, luaActivity)
            adapter.updateData(dataList)
            adapter.notifyDataSetChanged()
        }
    }

    /**
     * 在指定位置插入数据
     */
    @JvmStatic
    fun insertItem(adapter: RecyclerView.Adapter<*>, position: Int, item: Any) {
        if (adapter is LuaRecyclerAdapter) {
            adapter.insertItem(position, item)
            adapter.notifyItemInserted(position)
        }
    }

    /**
     * 移除指定位置的数据
     */
    @JvmStatic
    fun removeItem(adapter: RecyclerView.Adapter<*>, position: Int) {
        if (adapter is LuaRecyclerAdapter) {
            adapter.removeItem(position)
            adapter.notifyItemRemoved(position)
        }
    }

    /**
     * 更新指定位置的数据
     */
    @JvmStatic
    fun updateItem(adapter: RecyclerView.Adapter<*>, position: Int, item: Any) {
        if (adapter is LuaRecyclerAdapter) {
            adapter.updateItem(position, item)
            adapter.notifyItemChanged(position)
        }
    }

    /**
     * 添加数据项到末尾
     */
    @JvmStatic
    fun addItem(adapter: RecyclerView.Adapter<*>, item: Any) {
        if (adapter is LuaRecyclerAdapter) {
            val position = adapter.itemCount
            adapter.insertItem(position, item)
            adapter.notifyItemInserted(position)
        }
    }

    /**
     * 清除所有数据
     */
    @JvmStatic
    fun clearData(adapter: RecyclerView.Adapter<*>) {
        if (adapter is LuaRecyclerAdapter) {
            val count = adapter.itemCount
            adapter.clearData()
            adapter.notifyItemRangeRemoved(0, count)
        }
    }

    /**
     * 获取数据项
     */
    @JvmStatic
    fun getItem(adapter: RecyclerView.Adapter<*>, position: Int): Any? {
        return if (adapter is LuaRecyclerAdapter) {
            adapter.getItem(position)
        } else {
            null
        }
    }

    /**
     * 查找数据项的位置
     */
    @JvmStatic
    fun findItemPosition(adapter: RecyclerView.Adapter<*>, predicate: (Any) -> Boolean): Int {
        if (adapter is LuaRecyclerAdapter) {
            return adapter.findItemPosition(predicate)
        }
        return -1
    }

    /**
     * 将任意数据转换为 Java List
     */
    internal fun convertToJavaList(data: Any, luaActivity: LuaActivity): List<Any> {
        return when (data) {
            is List<*> -> {
                // 已经是 List，直接使用
                data.filterNotNull() as List<Any>
            }

            is Array<*> -> {
                // 数组，转换为 List
                data.filterNotNull().toList() as List<Any>
            }

            is LuaTable<*, *> -> {
                // LuaTable，转换为 List
                convertLuaTableToList(data, luaActivity)
            }

            else -> {
                // 其他类型，包装为单元素列表
                listOf(data)
            }
        }
    }

    /**
     * 将LuaTable转换为Java List
     */
    private fun convertLuaTableToList(table: LuaTable<*, *>, luaActivity: LuaActivity): List<Any> {
        val list = ArrayList<Any>()
        try {
            val L = luaActivity.luaState
            // 必须 push 真实的 Lua 表，而不是 pushJavaObject（userdata 的 objLen 返回字节长度 sizeof(java_object)=8）
            table.push()

            // 先尝试获取长度（假设是数组表）
            var len: Int = 0
            try {
                len = L.objLen(-1)  // objLen 返回的是 int
            } catch (e: Exception) {
                // 如果不是数组表，尝试其他方式
            }

            if (len > 0) {
                // 作为数组处理
                for (i in 1..len) {
                    // pushInteger 需要 long 类型，将 Int 转换为 Long
                    L.pushInteger(i.toLong())
                    L.getTable(-2)
                    try {
                        val value = L.toJavaObject(-1)
                        if (value != null) {
                            list.add(value)
                        }
                    } catch (e: Exception) {
                        // 忽略转换错误
                    }
                    L.pop(1)
                }
            } else {
                // 尝试遍历所有键值对
                L.pushNil()
                while (L.next(-2) != 0) {
                    try {
                        // 检查键是否为数字（数组索引）
                        if (L.type(-2) == LuaState.LUA_TNUMBER) {
                            val value = L.toJavaObject(-1)
                            if (value != null) {
                                list.add(value)
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略错误
                    }
                    L.pop(1)
                }
            }
            L.pop(1) // 弹出table
        } catch (e: Exception) {
            Log.e(TAG, "转换LuaTable为List失败: ${e.message}")
            e.printStackTrace()
        }
        return list
    }

    /**
     * 收集视图并设置到holder表中
     */
    internal fun collectViewsAndSetToHolder(view: View, holder: LuaObject) {
        val views = HashMap<String, View>()
        collectViewsWithId(view, views)

        // 将视图设置到holder表中
        for ((key, value) in views) {
            try {
                holder.setField(key, value)
            } catch (e: Exception) {
                Log.e(TAG, "设置视图到holder失败: $key")
                e.printStackTrace()
            }
        }
    }

    /**
     * 递归收集视图树中所有有id的视图
     */
    private fun collectViewsWithId(view: View, views: HashMap<String, View>) {
        val id = view.id
        if (id != View.NO_ID) {
            try {
                val resourceName = view.resources.getResourceEntryName(id)
                views[resourceName] = view
            } catch (e: Exception) {
                views["id_${id}"] = view
            }
        }

        if (view is ViewGroup) {
            val childCount = view.childCount
            for (i in 0 until childCount) {
                collectViewsWithId(view.getChildAt(i), views)
            }
        }
    }
}

/**
 * 适配器创建器接口 - 支持数据管理
 */
interface AdapterCreator {
    fun getItemCount(): Int
    fun getItemViewType(position: Int): Int
    fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder
    fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int)

    // 数据管理方法
    fun getData(): List<Any>
    fun getItem(position: Int): Any?
    fun updateData(newData: List<Any>)
    fun insertItem(position: Int, item: Any)
    fun removeItem(position: Int)
    fun updateItem(position: Int, item: Any)
    fun clearData()
    fun findItemPosition(predicate: (Any) -> Boolean): Int
}

/**
 * 基础的适配器创建器实现，支持数据管理
 */
abstract class BaseAdapterCreator(
    protected val context: LuaActivity,
    protected val method: LuaObject
) : AdapterCreator {

    protected val dataList = mutableListOf<Any>()

    override fun getItemCount(): Int = dataList.size

    override fun getItem(position: Int): Any? {
        return if (position in 0 until dataList.size) {
            dataList[position]
        } else {
            null
        }
    }

    override fun getData(): List<Any> = dataList.toList()

    override fun updateData(newData: List<Any>) {
        dataList.clear()
        dataList.addAll(newData)
    }

    override fun insertItem(position: Int, item: Any) {
        if (position in 0..dataList.size) {
            dataList.add(position, item)
        }
    }

    override fun removeItem(position: Int) {
        if (position in 0 until dataList.size) {
            dataList.removeAt(position)
        }
    }

    override fun updateItem(position: Int, item: Any) {
        if (position in 0 until dataList.size) {
            dataList[position] = item
        }
    }

    override fun clearData() {
        dataList.clear()
    }

    override fun findItemPosition(predicate: (Any) -> Boolean): Int {
        return dataList.indexOfFirst { predicate(it) }
    }

    override fun getItemViewType(position: Int): Int {
        return try {
            if (method.isTable) {
                val getItemViewTypeFunc = method.getField("getItemViewType")
                if (getItemViewTypeFunc.isFunction) {
                    val result = getItemViewTypeFunc.call(position)
                    if (result is Number) {
                        result.toInt()
                    } else {
                        0
                    }
                } else {
                    0
                }
            } else {
                0
            }
        } catch (e: Exception) {
            // 将错误发送到Lua层
            context.sendError("getItemViewType", e)
            0
        }
    }
}

/**
 * 单视图类型的适配器创建器实现
 */
class DataAdapterCreatorImpl(
    context: LuaActivity,
    initialData: List<Any>,
    private val listItem: Any,
    method: LuaObject
) : BaseAdapterCreator(context, method) {

    init {
        dataList.addAll(initialData)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        try {
            val L = context.luaState

            // 获取loadlayout函数
            val loadlayout = L.getLuaObject("loadlayout")
            if (loadlayout.isNil) {
                throw LuaException("loadlayout function not found")
            }

            // 创建holder表
            L.newTable()
            val holder = L.getLuaObject(-1)
            L.pop(1)

            val view: View = when (listItem) {
                is String -> {
                    // 字符串形式的布局
                    loadlayout.call(listItem, holder) as View
                }

                is Int -> {
                    // 资源ID
                    val v = View.inflate(parent.context, listItem, null)
                    // 收集视图并设置到holder中
                    RecyclerAdapterUtil.collectViewsAndSetToHolder(v, holder)
                    v
                }

                is LuaObject -> {
                    // Lua表形式的布局
                    loadlayout.call(listItem, holder) as View
                }

                else -> {
                    // 其他类型
                    View.inflate(parent.context, android.R.layout.simple_list_item_1, null)
                }
            }

            // 创建ViewHolder
            val holderObj = LuaRecyclerHolder(view)
            holderObj.luaHolder = holder

            // 如果method中有setViews函数，调用它
            if (method.isTable) {
                val setViews = method.getField("setViews")
                if (setViews.isFunction) {
                    try {
                        setViews.call(holder, viewType)
                    } catch (e: Exception) {
                        // 将错误发送到Lua层
                        context.sendError("setViews", e)
                        Log.e("DataAdapterCreatorImpl", "setViews调用失败: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            // 如果method中有onCreateViewHolder函数，调用它
            if (method.isTable) {
                val onCreateViewHolderFunc = method.getField("onCreateViewHolder")
                if (onCreateViewHolderFunc.isFunction) {
                    try {
                        onCreateViewHolderFunc.call(holder, view, holder, viewType)
                    } catch (e: Exception) {
                        // 将错误发送到Lua层
                        context.sendError("onCreateViewHolder", e)
                        Log.e("DataAdapterCreatorImpl", "onCreateViewHolder调用失败: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            return holderObj

        } catch (e: Exception) {
            // 将错误发送到Lua层
            context.sendError("onCreateViewHolder", e)
            Log.e("DataAdapterCreatorImpl", "onCreateViewHolder失败: ${e.message}")
            e.printStackTrace()
            // 创建空视图作为备选
            val emptyView = View(context)
            return LuaRecyclerHolder(emptyView)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (method.isTable) {
            val onBindViewHolder = method.getField("onBindViewHolder")
            if (onBindViewHolder.isFunction) {
                try {
                    val luaHolder = (holder as LuaRecyclerHolder).luaHolder
                    val itemData = if (position < dataList.size) dataList[position] else null

                    // 调用Lua的onBindViewHolder函数
                    onBindViewHolder.call(holder, position, luaHolder, itemData)
                } catch (e: Exception) {
                    // 将错误发送到Lua层
                    context.sendError("onBindViewHolder", e)
                    Log.e("DataAdapterCreatorImpl", "onBindViewHolder调用失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
}

/**
 * 多视图类型的适配器创建器实现
 */
class MultiTypeAdapterCreatorImpl(
    context: LuaActivity,
    initialData: List<Any>,
    private val typeMap: Map<Int, Any>,
    method: LuaObject
) : BaseAdapterCreator(context, method) {

    init {
        dataList.addAll(initialData)
    }

    override fun getItemViewType(position: Int): Int {
        return try {
            if (method.isTable) {
                val getItemTypeFunc = method.getField("getItemType")
                if (getItemTypeFunc.isFunction) {
                    val result = getItemTypeFunc.call(position, dataList[position])
                    if (result is Number) {
                        result.toInt()
                    } else {
                        0
                    }
                } else {
                    // 默认逻辑：根据数据类型或位置决定viewType
                    when (val item = dataList[position]) {
                        is Map<*, *> -> (item["type"] as? Number)?.toInt() ?: 0
                        else -> position % typeMap.size
                    }
                }
            } else {
                0
            }
        } catch (e: Exception) {
            // 将错误发送到Lua层
            context.sendError("getItemViewType", e)
            0
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = typeMap[viewType] ?: typeMap[0] // 默认使用第一个布局

        try {
            val L = context.luaState
            val loadlayout = L.getLuaObject("loadlayout")

            L.newTable()
            val holder = L.getLuaObject(-1)
            L.pop(1)

            val view: View = when (layout) {
                is String -> loadlayout.call(layout, holder) as View
                is Int -> {
                    val v = View.inflate(parent.context, layout, null)
                    RecyclerAdapterUtil.collectViewsAndSetToHolder(v, holder)
                    v
                }

                is LuaObject -> loadlayout.call(layout, holder) as View
                else -> View.inflate(parent.context, android.R.layout.simple_list_item_1, null)
            }

            val holderObj = LuaRecyclerHolder(view)
            holderObj.luaHolder = holder

            // 调用setViews
            if (method.isTable) {
                val setViews = method.getField("setViews")
                if (setViews.isFunction) {
                    try {
                        setViews.call(holder, viewType)
                    } catch (e: Exception) {
                        // 将错误发送到Lua层
                        context.sendError("setViews", e)
                    }
                }
            }

            return holderObj
        } catch (e: Exception) {
            // 将错误发送到Lua层
            context.sendError("onCreateViewHolder", e)
            Log.e("MultiTypeAdapterCreatorImpl", "创建ViewHolder失败: ${e.message}")
            return LuaRecyclerHolder(View(context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (method.isTable) {
            val onBindViewHolder = method.getField("onBindViewHolder")
            if (onBindViewHolder.isFunction) {
                try {
                    val luaHolder = (holder as LuaRecyclerHolder).luaHolder
                    val itemData = if (position < dataList.size) dataList[position] else null
                    onBindViewHolder.call(holder, position, luaHolder, itemData)
                } catch (e: Exception) {
                    // 将错误发送到Lua层
                    context.sendError("onBindViewHolder", e)
                    e.printStackTrace()
                }
            }
        }
    }
}

/**
 * 自定义RecyclerView适配器
 */
class LuaRecyclerAdapter(
    private val adapterCreator: AdapterCreator
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount(): Int {
        return adapterCreator.getItemCount()
    }

    override fun getItemViewType(position: Int): Int {
        return adapterCreator.getItemViewType(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return adapterCreator.onCreateViewHolder(parent, viewType)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        adapterCreator.onBindViewHolder(holder, position)
    }

    /**
     * 获取适配器数据
     */
    fun getData(): List<Any> {
        return adapterCreator.getData()
    }

    /**
     * 获取指定位置的数据项
     */
    fun getItem(position: Int): Any? {
        return adapterCreator.getItem(position)
    }

    /**
     * 更新数据
     */
    fun updateData(newData: List<Any>) {
        adapterCreator.updateData(newData)
    }

    /**
     * 插入数据
     */
    fun insertItem(position: Int, item: Any) {
        adapterCreator.insertItem(position, item)
    }

    /**
     * 移除数据
     */
    fun removeItem(position: Int) {
        adapterCreator.removeItem(position)
    }

    /**
     * 更新数据项
     */
    fun updateItem(position: Int, item: Any) {
        adapterCreator.updateItem(position, item)
    }

    /**
     * 清除所有数据
     */
    fun clearData() {
        adapterCreator.clearData()
    }

    /**
     * 查找数据项的位置
     */
    fun findItemPosition(predicate: (Any) -> Boolean): Int {
        return adapterCreator.findItemPosition(predicate)
    }
}

/**
 * 自定义RecyclerView ViewHolder
 */
class LuaRecyclerHolder(view: View) : RecyclerView.ViewHolder(view) {
    var luaHolder: LuaObject? = null
    val view: View = view
}