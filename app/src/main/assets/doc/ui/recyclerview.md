# RecyclerView 列表控件

`RecyclerView` 是 Android 高效列表容器。本工程运行时内置了 `RecyclerAdapterUtil` 适配器封装，自动完成数据与视图绑定，支持单类型/多类型布局、数据插入/删除/更新/清空/整批替换，以及条目点击、长按、子控件事件，增删改操作自带默认动画，无需手动调用 notify 方法。

## 基本用法（单类型列表）

```lua
require "import"
import "androidx.recyclerview.widget.RecyclerView"
import "android.widget.LinearLayout"
import "android.widget.TextView"
import "android.widget.LinearLayoutManager"
import "muling.views.tool.utils.RecyclerAdapterUtil"

-- 条目布局（带 id 的控件会注入到 holder.itemView）
local itemLayout = {
  LinearLayout,
  layout_width = "fill",
  layout_height = "50dp",
  orientation = "vertical",
  gravity = "center",
  {
    TextView,
    id = "name",
    textSize = 16,
  },
}

local layout = {
  LinearLayout,
  layout_width = "fill",
  layout_height = "fill",
  {
    RecyclerView,
    id = "list",
    layout_width = "fill",
    layout_height = "fill",
  },
}

activity.setContentView(loadlayout(layout))
list.setLayoutManager(LinearLayoutManager(activity))

local data = { "条目一", "条目二", "条目三" }

local adapter = RecyclerAdapterUtil.createAdapter(activity, data, itemLayout, {
  onBindViewHolder = function(holder, position)
    holder.itemView.name.text = data[position + 1]
  end,
})

list.setAdapter(adapter)
```

要点：

- `createAdapter(context, data, listItem, method)`：`context` 传 `activity`；`data` 为 Lua 表/Java List/数组；`listItem` 为条目布局表（或布局文件路径、资源 ID）；`method` 表必填 `onBindViewHolder`。
- `onBindViewHolder` 形参为 `(holder, position)`，`position` 从 0 开始；`holder.itemView` 下可直接取用条目布局中带 `id` 的控件。

## 实战：多类型列表

按整数类型码映射不同布局，`method` 中提供 `getItemType(position, item)` 决定每条用哪个布局：

```lua
-- 类型码 0 -> 普通文字条目，类型码 1 -> 醒目大标题条目
local typeMap = {
  [0] = { LinearLayout, layout_width = "fill", layout_height = "50dp",
          { TextView, id = "txt", textSize = 16 } },
  [1] = { LinearLayout, layout_width = "fill", layout_height = "100dp",
          { TextView, id = "txt", textSize = 26 } },
}

local data = {
  { type = 0, text = "普通文字条目" },
  { type = 1, text = "醒目大标题条目" },
}

local adapter = RecyclerAdapterUtil.createMultiTypeAdapter(activity, data, typeMap, {
  getItemType = function(position, item)
    return item.type
  end,
  onBindViewHolder = function(holder, position)
    holder.itemView.txt.text = data[position + 1].text
  end,
})

list.setAdapter(adapter)
```

要点：

- `typeMap` 的键必须为**整数类型码**（`[0]`、`[1]` …），值是对应类型的布局表。
- `getItemType(position, item)` 返回整数类型码；不提供时也会尝试读数据项的 `type` 数字字段，或按位置对布局数取模兜底。

## 实战：增删改与整批替换

返回的适配器自带实例方法，直接调用即触发刷新动画：

```lua
-- 尾部追加
adapter.addItem("新条目")

-- 插入到指定位置（position 从 0 起）
adapter.insertItem(1, "插在第二项")

-- 删除指定位置
adapter.removeItem(0)

-- 更新指定位置数据
adapter.updateItem(2, "改过的内容")

-- 清空
adapter.clearData()

-- 整批替换
adapter.updateData({ "新列表一", "新列表二" })

-- 读取
local dataLen = #adapter.getData()
```

> 以上均为适配器实例方法，内部自带动画。也可调用静态版本：`RecyclerAdapterUtil.insertItem(adapter, pos, item)`、`updateItem(adapter, pos, item)`、`removeItem(adapter, pos)`、`addItem(adapter, item)`、`clearData(adapter)`、`updateAdapterData(adapter, newData, activity)` 等。

## 条目事件绑定

```lua
import "android.view.View"

local adapter = RecyclerAdapterUtil.createAdapter(activity, data, itemLayout, {
  onBindViewHolder = function(holder, position)
    holder.itemView.name.text = data[position + 1]
    -- 整行点击
    holder.itemView.setOnClickListener(View.OnClickListener(function()
      print("点击了第 " .. position .. " 行")
    end))
    -- 子控件点击
    holder.itemView.name.setOnClickListener(View.OnClickListener(function()
      print("点击了名字控件")
    end))
  end,
})
```

## 说明

- 未配置 `settings.json` 的 `global_utils` 时，直接 `import "muling.views.tool.utils.RecyclerAdapterUtil"` 调用静态方法即可，两种方式等价；启用后还可用全局函数 `createRecyclerAdapter(...)`、`notifyDataSetChanged(...)`。
- `RecyclerView` 布局管理器：`LinearLayoutManager(activity)` 为纵向列表；横向列表用 `LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)`。
- 设置条目间距可对 `list` 调用 `addItemDecoration` 自定义，或直接给条目布局设置上下 `margin`。