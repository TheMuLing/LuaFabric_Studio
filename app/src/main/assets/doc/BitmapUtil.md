# BitmapUtil - 文档

## 一、概述

- **功能**：位图处理工具类，提供图片加载、Drawable创建、颜色解析等核心功能，集成LuaBitmap优化性能，适配Lua环境。
- **适用场景**：Android开发中需手动处理位图、自定义Drawable的场景，如图标着色、尺寸适配、Canvas绘制等。
- **优势**：支持本地/网络图片加载，提供颜色格式转换，优先使用LuaBitmapDrawable提升性能。

## 二、启用方式

在项目 `settings.json` 文件中添加工具类到全局工具列表：

```json
{
  "application": {
    "label": "My App",
    "debugmode": true
  },
  "global_utils": [
    "BitmapUtil"
  ]
}
```

## 三、核心函数

### 1. getIconDrawable（获取图标Drawable）

- **Lua签名**：`getIconDrawable(input, ...)`
- **功能**：从指定路径加载图片并创建Drawable对象，支持颜色滤镜、尺寸调整等高级选项。
- **参数**：
    - `input` (string)：图片路径（支持本地文件、Lua项目目录相对路径，必填）
    - `color` (number|string, 可选)：颜色值（十六进制数字或"#RRGGBB"字符串）
    - `size` (number, 可选)：缩放目标宽度（像素），按"目标宽度/原图宽度"的比例等比缩放
    - `unit` (number, 可选)：尺寸单位（当前版本不生效，仅保留参数位）
- **返回值**：(userdata) Drawable对象，可直接设置为ImageView的src
- **示例**：

```lua
-- 基础加载图标
local drawable = getIconDrawable("icon.png")
imageView.setImageDrawable(drawable)
-- 加载红色图标
local redIcon = getIconDrawable("icon.png", "#FF0000")
-- 加载宽度为32像素的图标
local sizedIcon = getIconDrawable("logo.png", nil, 32)
```

### 2. getBitmap（获取位图对象）

- **Lua签名**：`getBitmap(input)`
- **功能**：从指定路径加载图片并创建Bitmap对象，支持本地文件和网络图片。
- **参数**：`input` (string)：图片路径（支持本地文件、http/https协议，必填）
- **返回值**：(userdata) Bitmap对象，可用于Canvas绘制或手动设置到ImageView
- **示例**：

```lua
-- 加载本地图片
local localBitmap = getBitmap("bg.png")
imageView.setImageBitmap(localBitmap)
-- 加载网络图片
local netBitmap = getBitmap("https://example.com/logo.png")
if netBitmap then
    imageView.setImageBitmap(netBitmap)
end
```

### 3. parseColor（解析颜色值）

- **Lua签名**：`parseColor(color)`
- **功能**：将多种格式的颜色值转换为整数型颜色，支持多种输入格式。
- **参数**：`color` (number|string)：颜色值（支持0xAARRGGBB、0xRRGGBB、"#RRGGBB"等格式，必填）
- **返回值**：(number) 解析后的整数颜色值，解析失败返回nil
- **示例**：

```lua
-- 解析十六进制颜色
local blue = parseColor("#0000FF")
local green = parseColor(0xFF4CAF50)
-- 设置TextView文字颜色
textView.setTextColor(parseColor("#FF5722"))
```

## 四、使用示例

### 示例1：动态创建带图标的按钮

```lua
-- 创建ImageView
local iconView = ImageView(this)
iconView.layoutParams = LinearLayout.LayoutParams(dp2px(24), dp2px(24))
-- 加载灰色设置图标
local icon = getIconDrawable("ic_setting.png", 0xFF757575)
iconView.setImageDrawable(icon)
-- 组装按钮布局
local button = Button(this)
button.text = "设置"
local linearLayout = LinearLayout(this)
linearLayout.orientation = LinearLayout.HORIZONTAL
linearLayout.addView(iconView)
linearLayout.addView(button)
```

### 示例2：列表项图标差异化处理

```lua
function getView(position, convertView, parent)
    local view = convertView or LayoutInflater.from(context).inflate(layoutId, parent, false)
    local item = data[position]
    -- 根据激活状态设置不同颜色
    local iconColor = item.isActive and 0xFF4CAF50 or 0xFF9E9E9E
    local iconDrawable = getIconDrawable(item.iconPath, iconColor)
    -- 设置图标
    local ivIcon = view.findViewById(id_iv_icon)
    ivIcon.setImageDrawable(iconDrawable)
    return view
end
```

## 五、最佳实践

1. **缓存常用Bitmap**：避免重复加载，提升性能

```lua
local iconCache = {}
function getCachedIcon(path, color)
    local cacheKey = path .. (color or "")
    if not iconCache[cacheKey] then
        iconCache[cacheKey] = getIconDrawable(path, color)
    end
    return iconCache[cacheKey]
end
```

2. **屏幕密度适配**：使用dp2px确保尺寸统一

```lua
local targetSize = dp2px(24)
local bitmap = getBitmap("logo.png")
if bitmap then
    local scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
    imageView.setImageBitmap(scaledBitmap)
end
```

3. **及时回收大Bitmap**：避免内存泄漏

```lua
local largeBitmap = getBitmap("big_image.png")
-- 使用完毕后回收资源
largeBitmap.recycle()
largeBitmap = nil
```