# RecyclerAdapterUtil 列表适配器

RecyclerView 适配器封装工具类：自动完成数据与视图绑定，支持单类型 / 多类型布局、数据增删改查与条目点击/长按/子控件事件。数据变更推荐走静态方法，自带局部刷新动画。

## 一、启用方式

在项目 `settings.json` 的 `global_utils` 列表中加入 `"RecyclerAdapterUtil"`：

```json
{
  "global_utils": ["RecyclerAdapterUtil"]
}
```

启用后可直接调用 `createRecyclerAdapter(...)` 等全局函数；未启用时也可以 `import "muling.views.tool.utils.RecyclerAdapterUtil"` 后调用静态方法，两者等价。

## 二、创建适配器

### 单类型 createAdapter

**签名**：`RecyclerAdapterUtil.createAdapter(context, data, listItem, method)`

| 参数 | 类型 | 说明 |
|------|------|------|
| `context` | activity | Lua 上下文，直接传 `activity` |
| `data` | table | 数据源（Lua 表 / Java List / 数组） |
| `listItem` | table/string/int | Item 布局：loadlayout 布局表、布局文件路径或资源 ID |
| `method` | table | 回调方法表，核心为 `onBindViewHolder` |

**返回值**：userdata，适配器对象。

```lua
require "import"
import "androidx.recyclerview.widget.RecyclerView"
import "androidx.recyclerview.widget.LinearLayoutManager"

local data = { {title = "Apple"}, {title = "Banana"} }
local itemView = {
  LinearLayout, orientation = "vertical", padding = "16dp",
  { TextView, id = "tv_title", textSize = "18sp" }
}

local adapter = RecyclerAdapterUtil.createAdapter(activity, data, itemView, {
  onBindViewHolder = function(holder, pos, views, item)
    views.tv_title.text = item.title
  end
})

recyclerView.setAdapter(adapter)
recyclerView.setLayoutManager(LinearLayoutManager(activity))
```

### 多类型 createMultiTypeAdapter

**签名**：`RecyclerAdapterUtil.createMultiTypeAdapter(context, data, typeMap, method)`

`typeMap` 为 **类型标识（整数）→ 布局** 的映射表，key 必须是数字。

```lua
local typeMap = {
  [0] = "item_send.lua", -- 自己发送的文字消息
  [1] = "item_recv.lua", -- 接收的文字消息
  [2] = "item_pic.lua",  -- 图片消息
}

local adapter = RecyclerAdapterUtil.createMultiTypeAdapter(activity, data, typeMap, {
  getItemType = function(pos, item) return item.type end, -- 返回整数类型标识
  onBindViewHolder = function(holder, pos, views, item)
    if item.text then
      views.tv_text.text = item.text
    end
  end
})
```

## 三、回调方法表

单类型适配器 method 表：

| 回调名 | 调用时机 | 参数 |
|--------|----------|------|
| `onBindViewHolder` | 每个条目绑定时 | `(holder, pos, views, item)`；`views` 已按布局 id 注入控件 |
| `setViews` | 创建条目视图时 | `(holder, viewType)` |
| `onCreateViewHolder` | 创建条目视图时 | `(holder, view, holder, viewType)` |
| `getItemViewType` | 查询条目类型时 | `(pos)`，返回 number（单类型可不写） |

多类型适配器 method 表：

| 回调名 | 调用时机 | 参数 |
|--------|----------|------|
| `onBindViewHolder` | 每个条目绑定时 | `(holder, pos, views, item)` |
| `getItemType` | 查询条目类型时 | `(pos, item)`，返回 number（必须在 typeMap 中定义） |
| `setViews` | 创建条目视图时 | `(holder, viewType)` |

## 四、数据增删改查

### 静态方法（推荐，自带刷新动画）

| 静态方法 | 功能 |
|----------|------|
| `addItem(adapter, item)` | 末尾追加一条 |
| `insertItem(adapter, pos, item)` | 指定位置插入一条 |
| `removeItem(adapter, pos)` | 删除指定位置 |
| `updateItem(adapter, pos, item)` | 替换指定位置 |
| `clearData(adapter)` | 清空所有数据 |
| `updateAdapterData(adapter, newData, context)` | 整批替换数据并整体刷新 |
| `getItem(adapter, pos)` | 读取单条数据 |
| `getAdapterData(adapter)` | 获取数据源副本 |
| `findItemPosition(adapter, predicate)` | 按条件查找位置，无匹配返回 -1 |

> 静态方法会同步调用对应的 notify 实现局部动画，无需手动刷新。

### 实例方法（仅改数据，需手动刷新）

实例本身也暴露同名方法：`adapter.addItem(item)`、`adapter.insertItem(pos, item)`、`adapter.removeItem(pos)`、`adapter.updateItem(pos, item)`、`adapter.clearData()`、`adapter.updateData(newData)`、`adapter.getData()`、`adapter.getItem(pos)`、`adapter.findItemPosition(predicate)`。

实例方法只修改数据源，**不会自动通知 RecyclerView 刷新**，改完需手动 `recyclerView.adapter.notifyDataSetChanged()`，或直接用静态方法。

## 五、事件绑定

在 `onBindViewHolder` 回调中为条目 / 子控件绑定事件：

```lua
onBindViewHolder = function(holder, pos, views, item)
  -- 条目点击
  holder.itemView.onClick = function()
    toast("点击条目：" .. pos)
  end
  -- 子控件事件（示例：删除按钮）
  views.btn_delete.onClick = function()
    RecyclerAdapterUtil.removeItem(adapter, pos)
  end
end
```

## 六、注意事项

- 直接修改原始 Lua 数据源表不会同步到视图，需走 addItem / insertItem / removeItem / updateItem / updateData 等标准接口；
- 多类型列表的 `getItemType` 返回类型标识必须预先在 typeMap 中定义对应布局；
- Item 布局根节点宽高由 LayoutManager 决定，无需手动写死；
- 图片加载建议使用 `loadImage(views.iv_img, url)`，底层 Glide 自动复用。