# RecyclerAdapterUtil - 文档

## 一、概述

- **功能**
  ：为 Lua 层提供 RecyclerView 适配器封装：自动完成数据与视图绑定；支持单类型/多类型布局、数据插入/删除/更新/清空/整批替换，以及条目点击、长按、子控件事件，增删改操作同步调用且自带默认动画，无需手动调用 notify 相关方法。
- **适用场景**：Android 开发中各类 RecyclerView 实现场景，如聊天列表、商品列表、瀑布流、文件管理器、设置页面等。
- **两种使用方式**：
    1. **静态工具类**（推荐）：`import "muling.views.tool.utils.RecyclerAdapterUtil"`，调用 `RecyclerAdapterUtil.xxx(...)` 静态方法；
    2. **全局函数**（需在 settings.json 的 `global_utils` 中启用）：启用后可直接调用 `createRecyclerAdapter(...)`、`notifyDataSetChanged(...)`。

## 二、启用方式（全局函数）

在项目 `settings.json` 文件的 `global_utils` 列表中加入 `"RecyclerAdapterUtil"`：

```json
{
  "application": {
    "label": "My App",
    "debugmode": true
  },
  "global_utils": [
    "RecyclerAdapterUtil"
  ]
}
```

启用后全局注册两个函数：

- `createRecyclerAdapter(data, listItem, method)` — 创建单类型适配器（注意：全局函数名为 `createRecyclerAdapter`，不是 `createAdapter`）
- `notifyDataSetChanged(adapter)` — 手动刷新列表

> 未配置 `global_utils` 时，也可以直接 `import "muling.views.tool.utils.RecyclerAdapterUtil"` 后使用静态方法，两种方式等价。

## 三、核心函数

### 1. createAdapter（创建单类型适配器，静态方法）

- **Lua签名**：`RecyclerAdapterUtil.createAdapter(context, data, listItem, method)`
- **功能**：快速创建单布局类型的 RecyclerView 适配器，完成数据与视图绑定
- **参数**：
    - `context`(activity)：LuaActivity 上下文，脚本内直接传 `activity`
    - `data`(table/List)：数据源（Lua 表、Java List 或数组）
    - `listItem`(table/string/int)：Item 布局（loadlayout 布局表、布局文件路径或资源 ID，必填）
    - `method`(table)：回调方法表，核心含 `onBindViewHolder`（必填）
- **返回值**：(userdata) 适配器对象，可直接绑定到 RecyclerView

### 2. createMultiTypeAdapter（创建多类型适配器，静态方法）

- **Lua签名**：`RecyclerAdapterUtil.createMultiTypeAdapter(context, data, typeMap, method)`
- **功能**：创建支持多布局类型的 RecyclerView 适配器，适配不同样式条目场景
- **参数**：
    - `context`(activity)：LuaActivity 上下文
    - `data`(table)：数据源（Lua 表）
    - `typeMap`(table)：类型与布局映射表（键为类型标识，值为布局路径/表）
    - `method`(table)：回调方法表，含 `getItemType`、`onBindViewHolder`（必填）
- **返回值**：(userdata) 适配器对象

### 3. insertItem（插入单条数据，静态方法/实例方法）

- **Lua签名**：`RecyclerAdapterUtil.insertItem(adapter, pos, item)` 或 `adapter.insertItem(pos, item)`
- **功能**：在指定位置插入单条数据，自带默认动画，无需手动刷新
- **参数**：
    - `adapter`(userdata)：目标适配器对象（必填）
    - `pos`(number)：插入位置索引（必填）
    - `item`(table)：待插入数据条目（必填）

### 4. removeItem（删除单条数据，静态方法/实例方法）

- **Lua签名**：`RecyclerAdapterUtil.removeItem(adapter, pos)` 或 `adapter.removeItem(pos)`
- **功能**：删除指定位置的数据条目，自带默认动画

### 5. updateItem（更新单条数据，静态方法/实例方法）

- **Lua签名**：`RecyclerAdapterUtil.updateItem(adapter, pos, item)` 或 `adapter.updateItem(pos, item)`
- **功能**：替换指定位置的数据条目，自动刷新视图

### 6. addItem（尾部追加数据，仅静态方法）

- **Lua签名**：`RecyclerAdapterUtil.addItem(adapter, item)`
- **功能**：在数据源尾部追加单条数据，自带默认动画
- **注意**：适配器实例上**没有** `addItem` 方法，请调用静态方法；等价替代为 `adapter.insertItem(adapter.getItemCount(), item)`。

### 7. clearData（清空数据源，静态方法/实例方法）

- **Lua签名**：`RecyclerAdapterUtil.clearData(adapter)` 或 `adapter.clearData()`
- **功能**：清空适配器所有数据源，视图同步刷新

### 8. updateAdapterData（整批替换数据，静态方法）

- **Lua签名**：`RecyclerAdapterUtil.updateAdapterData(adapter, newData, context)`
- **功能**：用新数据源整批替换原有数据，一次性刷新视图
- **注意**：实例方法名为 `updateData`：`adapter.updateData(newData)`。

### 9. getItem（读取单条数据，静态方法/实例方法）

- **Lua签名**：`RecyclerAdapterUtil.getItem(adapter, pos)` 或 `adapter.getItem(pos)`
- **功能**：获取适配器指定位置的数据条目

### 10. getAdapterData（读取完整数据源，仅静态方法）

- **Lua签名**：`RecyclerAdapterUtil.getAdapterData(adapter)`
- **功能**：获取适配器数据源的完整副本
- **注意**：实例方法名为 `getData`：`adapter.getData()`。

### 11. findItemPosition（条件查找数据位置，静态方法/实例方法）

- **Lua签名**：`RecyclerAdapterUtil.findItemPosition(adapter, predicate)` 或 `adapter.findItemPosition(predicate)`
- **功能**：根据自定义条件查找数据条目对应的位置索引
- **参数**：`predicate`(function)：查找条件函数（返回 true 时匹配成功）
- **返回值**：(number) 匹配条目位置索引，无匹配返回 -1

## 四、回调方法表（method）

`createAdapter` / `createRecyclerAdapter` 的 `method` 表支持以下回调（全部可选）：

| 回调名 | 调用时机 | 参数 |
|--------|----------|------|
| `onBindViewHolder` | 每个条目绑定时 | `(holder, pos, views, item)` |
| `setViews` | 创建条目视图时 | `(holder, viewType)` |
| `onCreateViewHolder` | 创建条目视图时 | `(holder, view, holder, viewType)` |
| `getItemViewType` | 单类型适配器查询条目类型 | `(pos)`，返回 number |

`createMultiTypeAdapter` / 多类型场景的 `method` 表：

| 回调名 | 调用时机 | 参数 |
|--------|----------|------|
| `onBindViewHolder` | 每个条目绑定时 | `(holder, pos, views, item)` |
| `getItemType` | 多类型适配器查询条目类型 | `(pos, item)`，返回 number（须在 typeMap 中定义） |
| `setViews` | 创建条目视图时 | `(holder, viewType)` |

## 五、单类型快速上手

30 行精简代码即可实现基础列表功能：

```lua
require "import"
import "androidx.recyclerview.widget.RecyclerView"
import "androidx.recyclerview.widget.LinearLayoutManager"
import "muling.views.tool.utils.RecyclerAdapterUtil"

activity.setContentView(loadlayout("layout")) -- layout 中包含 id 为 recyclerView 的 RecyclerView

-- 1. 准备数据源（普通 Lua 表）
local data = {
  {title = "Apple",  content = "Sweet"},
  {title = "Banana", content = "Soft"},
}

-- 2. 定义 Item 布局（inline 写法，也可引用独立布局文件）
local itemView = {
  LinearLayout,
  orientation = "vertical",
  padding = "16dp",
  {
    TextView, id = "tv_title", textSize = "18sp",
  },
  {
    TextView, id = "tv_content", textSize = "14sp",
  },
}

-- 3. 创建单类型适配器，绑定数据与视图
local adapter = RecyclerAdapterUtil.createAdapter(
  activity,
  data,
  itemView,
  {
    onBindViewHolder = function(holder, pos, views, item)
      views.tv_title.text   = item.title
      views.tv_content.text = item.content
    end
  }
)

-- 4. 绑定适配器到 RecyclerView 并设置布局管理器
recyclerView.setAdapter(adapter)
recyclerView.setLayoutManager(LinearLayoutManager(activity))
```

运行效果：列表立即展示 2 条数据，后续调用 `addItem`、`insertItem` 等方法，均会自带默认动画同步刷新视图。

### 全局函数写法

启用 `global_utils` 后，同一示例可简写为：

```lua
local adapter = createRecyclerAdapter(data, itemView, {
  onBindViewHolder = function(holder, pos, views, item)
    views.tv_title.text   = item.title
    views.tv_content.text = item.content
  end
})
```

## 六、多类型适配（聊天场景示例）

适配不同样式条目场景，以聊天列表为例：

```lua
-- 1. 定义类型与布局的映射表（类型标识对应布局路径）
local typeMap = {
  [0] = "item_send.lua",   -- 自己发送的文字消息布局
  [1] = "item_recv.lua",   -- 接收的文字消息布局
  [2] = "item_pic.lua",    -- 图片消息布局
}

-- 2. 准备多类型数据源，每条数据带 type 字段标识类型
local chatData = {
  {type = 0, text = "Hello"},
  {type = 1, text = "Hi"},
  {type = 2, img = "http://a.com/a.jpg"},
}

-- 3. 创建多类型适配器
local adapter = RecyclerAdapterUtil.createMultiTypeAdapter(
  activity,
  chatData,
  typeMap,
  {
    getItemType = function(pos, item) return item.type end,
    onBindViewHolder = function(holder, pos, views, item)
      if item.text then
        views.tv_text.text = item.text
      else
        loadImage(views.iv_img, item.img)
      end
    end
  }
)

-- 4. 绑定到 RecyclerView
recyclerView.setAdapter(adapter)
recyclerView.setLayoutManager(LinearLayoutManager(activity))
```

## 七、事件绑定（点击/长按/子控件事件）

在 `onBindViewHolder` 回调中直接绑定各类事件：

```lua
onBindViewHolder = function(holder, pos, views, item)
  -- 1. 条目整体点击事件
  holder.itemView.onClick = function()
    toast("点击条目，位置：" .. pos)
  end

  -- 2. 条目长按事件
  holder.itemView.onLongClick = function()
    RecyclerAdapterUtil.removeItem(adapter, pos)
    return true
  end

  -- 3. 子控件事件（示例：删除按钮）
  views.btn_delete.onClick = function()
    RecyclerAdapterUtil.removeItem(adapter, pos)
  end
end
```

> 注意：`holder` 是 Java 层 ViewHolder 对象，`views` 是 Lua 层控件表，控件已按布局 id 自动注入，可直接调用。

## 八、动态增删改实战示例

```lua
-- 1. 顶部插入新条目
btnAdd.onClick = function()
  local newItem = {title = "New Item", content = os.date()}
  adapter.insertItem(0, newItem)   -- 实例方法
  recyclerView.smoothScrollToPosition(0) -- 滚动到顶部显示新条目
end

-- 2. 批量更新所有条目内容
btnUpdate.onClick = function()
  local allData = adapter.getData() -- 获取数据源副本（实例方法）
  for i, item in ipairs(allData) do
    item.title = "Updated " .. i -- 批量修改数据
  end
  adapter.updateData(allData) -- 整批替换刷新（实例方法）
end

-- 3. 清空列表所有数据
btnClear.onClick = function()
  adapter.clearData() -- 清空数据源，视图同步刷新
end
```

## 九、布局写法小贴士

1. Item 布局根节点宽高无需手动写死，最终由 RecyclerView 的 LayoutManager 决定适配规则；
2. 布局中需设置背景、圆角、阴影时，直接通过 `background` 属性引用 drawable 资源即可；
3. 多类型布局场景下，不同样式 Item 的控件 id 可以重复，Lua 层会按当前布局实际控件注入 views，不影响使用。

## 十、最佳实践

1. 图片加载优化：Item 内图片加载直接使用 `loadImage(views.iv_img, url)`，底层 Glide 会自动完成图片回收，避免内存泄漏；
2. 大数据适配：面对大量数据时采用分页加载策略，每次追加 50 条以内数据，避免一次性替换超大数据源，提升流畅度；
3. 内存回收：在 Activity 的 `onDestroy` 生命周期方法中，调用 `recyclerView.setAdapter(nil)`，解除适配器与 RecyclerView 绑定，帮助系统及时 GC，避免内存泄漏。

## 十一、Q & A

- **问题**：修改数据源后视图未刷新
  原因/解决：直接修改原始 Lua 数据源表不会同步到视图。需使用 `insertItem`、`removeItem`、`updateItem`、`updateData` 等标准接口，接口会自动触发视图刷新；
- **问题**：多类型列表出现条目样式错位
  原因/解决：`getItemType` 回调返回的类型标识，必须在 `typeMap` 中预先定义对应布局，未定义的类型会导致样式匹配失败；
- **问题**：数据表有 N 条数据，但列表只显示 8 条
  原因/解决：历史版本在将 Lua 表转换为 Java 列表时误用了 `pushJavaObject`，导致长度被错误计算为 userdata 字节长度（8）。该问题已在代码中修复，请确保使用包含修复的版本；
- **问题**：Item 内图片加载出现闪烁
  原因/解决：给 `loadImage` 方法添加签名或指定磁盘缓存策略为 `"RESOURCE"`，提升图片加载复用率，解决闪烁问题。
