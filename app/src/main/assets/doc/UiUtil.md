# UiUtil - 文档

## 一、概述

- **功能**：屏幕显示相关工具类，封装屏幕尺寸获取、单位转换（DP/SP/像素）、方向判断等核心功能，适配多设备屏幕。
- **适用场景**：Android开发中UI布局适配、屏幕参数获取、响应式界面设计等场景。
- **优势**：自动适配屏幕密度与字体缩放设置，简化多设备适配流程。

## 二、启用方式

在项目 `settings.json` 文件中添加工具类到全局工具列表：

```json
{
  "application": {
    "label": "My App",
    "debugmode": true
  },
  "global_utils": [
    "UiUtil"
  ]
}
```

## 三、核心函数

### 1. dp2px（DP转像素）

- **Lua签名**：`dp2px(dp)`
- **功能**：将DP单位转换为像素值，自动适配当前设备屏幕密度。
- **参数**：`dp` (number)：需要转换的DP值（必填）
- **返回值**：(integer) 转换后的像素整数值
- **示例**：

```lua
-- 16dp转换为像素（320dpi设备上返回48px）
local px = dp2px(16)
print("16dp = " .. px .. "px")
```

### 2. px2dp（像素转DP）

- **Lua签名**：`px2dp(px)`
- **功能**：将像素值转换为DP单位。
- **参数**：`px` (number)：需要转换的像素值（必填）
- **返回值**：(number) 转换后的DP浮点值
- **示例**：

```lua
-- 1080像素转换为DP
local dp = px2dp(1080)
print("1080px = " .. dp .. "dp")
```

### 3. sp2px（SP转像素）

- **Lua签名**：`sp2px(sp)`
- **功能**：将SP（缩放独立像素）转换为像素值，适配系统字体缩放设置。
- **参数**：`sp` (number)：需要转换的SP值（必填）
- **返回值**：(number) 转换后的像素浮点值
- **示例**：

```lua
-- 14sp转换为像素
local px = sp2px(14)
print("14sp = " .. px .. "px")
```

### 4. px2sp（像素转SP）

- **Lua签名**：`px2sp(px)`
- **功能**：将像素值转换为SP单位。
- **参数**：`px` (number)：需要转换的像素值（必填）
- **返回值**：(integer) 转换后的SP整数值
- **示例**：

```lua
-- 56像素转换为SP
local sp = px2sp(56)
print("56px = " .. sp .. "sp")
```

### 5. width（获取屏幕宽度）

- **Lua签名**：`width()`
- **功能**：获取当前屏幕宽度（像素值）。
- **参数**：无
- **返回值**：(integer) 屏幕宽度像素值
- **示例**：

```lua
local screenWidth = width()
print("屏幕宽度: " .. screenWidth .. "px") -- 输出示例：1080px
```

### 6. height（获取屏幕高度）

- **Lua签名**：`height()`
- **功能**：获取当前屏幕高度（像素值）。
- **参数**：无
- **返回值**：(integer) 屏幕高度像素值
- **示例**：

```lua
local screenHeight = height()
print("屏幕高度: " .. screenHeight .. "px") -- 输出示例：1920px
```

### 7. statusBarHeight（获取状态栏高度）

- **Lua签名**：`statusBarHeight()`
- **功能**：获取系统状态栏高度（像素值）。
- **参数**：无
- **返回值**：(integer) 状态栏高度像素值，获取失败返回0
- **示例**：

```lua
local sbHeight = statusBarHeight()
print("状态栏高度: " .. sbHeight .. "px") -- 输出示例：60px
```

### 8. isLandscape（判断是否横屏）

- **Lua签名**：`isLandscape()`
- **功能**：判断当前设备是否为横屏模式。
- **参数**：无
- **返回值**：(boolean) 横屏返回`true`，竖屏返回`false`
- **示例**：

```lua
if isLandscape() then
    print("当前为横屏模式")
else
    print("当前为竖屏模式")
end
```

### 9. isPortrait（判断是否竖屏）

- **Lua签名**：`isPortrait()`
- **功能**：判断当前设备是否为竖屏模式。
- **参数**：无
- **返回值**：(boolean) 竖屏返回`true`，横屏返回`false`
- **示例**：

```lua
if isPortrait() then
    print("当前为竖屏模式")
    -- 调整竖屏UI布局
end
```

### 10. widthPercent（按宽度百分比计算像素）

- **Lua签名**：`widthPercent(percent)`
- **功能**：根据屏幕宽度百分比计算像素值，适配响应式布局。
- **参数**：`percent` (number)：百分比值（0-100，必填）
- **返回值**：(integer) 计算后的像素值
- **示例**：

```lua
-- 计算屏幕50%宽度的像素值
local halfWidth = widthPercent(50)
print("屏幕一半宽度: " .. halfWidth .. "px")
```

### 11. heightPercent（按高度百分比计算像素）

- **Lua签名**：`heightPercent(percent)`
- **功能**：根据屏幕高度百分比计算像素值。
- **参数**：`percent` (number)：百分比值（0-100，必填）
- **返回值**：(integer) 计算后的像素值
- **示例**：

```lua
-- 计算屏幕30%高度的像素值
local viewHeight = heightPercent(30)
```

### 12. minPercent（按最小边百分比计算像素）

- **Lua签名**：`minPercent(percent)`
- **功能**：根据屏幕宽高中较小边的百分比计算像素值，适用于正方形元素。
- **参数**：`percent` (number)：百分比值（0-100，必填）
- **返回值**：(integer) 计算后的像素值
- **示例**：

```lua
-- 创建占屏幕最小边50%的正方形按钮
local btnSize = minPercent(50)
button.layoutParams = {width = btnSize, height = btnSize}
```

### 13. applyEdgeToEdgePreference（设置Edge-to-Edge模式）

- **Lua签名**：`applyEdgeToEdgePreference(window, edgeToEdgeEnabled)`
- **功能**：设置窗口是否启用Edge-to-Edge（内容延伸到状态栏/导航栏之后），需配合Insets处理避免内容被遮挡。
- **参数**：
    - `window` (userdata)：Window对象，脚本内传 `activity.window`
    - `edgeToEdgeEnabled` (boolean)：是否启用（必填）
- **返回值**：无
- **示例**：

```lua
applyEdgeToEdgePreference(activity.window, true)
setupWindowInsets() -- 配合设置Insets避让系统栏
```

### 14. actionBarSize（获取ActionBar高度）

- **Lua签名**：`actionBarSize()`
- **功能**：获取当前主题下ActionBar的高度（像素值），获取失败返回0。
- **参数**：无
- **返回值**：(integer) ActionBar高度像素值，失败返回0
- **示例**：

```lua
local barHeight = actionBarSize()
print("ActionBar高度: " .. barHeight .. "px")
```

## 四、使用示例

### 示例1：响应式布局创建

```lua
-- 计算容器尺寸（占屏幕80%宽、60%高）
local containerWidth = widthPercent(80)
local containerHeight = heightPercent(60)
-- 计算居中边距
local screenWidth = width()
local screenHeight = height()
local layoutParams = {
    width = containerWidth,
    height = containerHeight,
    leftMargin = (screenWidth - containerWidth) / 2,
    topMargin = (screenHeight - containerHeight) / 2,
    padding = dp2px(16) -- 16dp内边距
}
```

### 示例2：屏幕方向适配

```lua
-- 根据屏幕方向调整分栏布局
if isLandscape() then
    -- 横屏：左右分栏（4:6比例）
    leftPanel.width = widthPercent(40)
    rightPanel.width = widthPercent(60)
else
    -- 竖屏：上下分栏（3:7比例）
    topPanel.height = heightPercent(30)
    bottomPanel.height = heightPercent(70)
end
-- 大屏幕字体适配
local baseFontSize = 16 -- 基准16sp
if width() > 1080 then
    textView.textSize = baseFontSize * 1.2 -- 大屏放大1.2倍
end
```

### 示例3：状态栏避让

```lua
-- 获取状态栏高度，避免UI遮挡
local sbHeight = statusBarHeight()
-- 顶部控件边距 = 状态栏高度 + 8dp
topView.topMargin = sbHeight + dp2px(8)
print("状态栏避让后，顶部边距: " .. topView.topMargin .. "px")
```

## 五、最佳实践

1. **缓存常用参数**：应用启动时缓存屏幕尺寸等常量，避免重复计算

```lua
local displayConfig = {
    screenWidth = width(),
    screenHeight = height(),
    statusBarHeight = statusBarHeight(),
    dp1 = dp2px(1), -- 1dp对应的像素值
    isTablet = width() > dp2px(600) -- 判断平板设备
}
```

2. **统一布局适配**：封装适配函数，简化UI开发

```lua
-- 快速创建全屏宽度按钮（左右留16dp边距）
function createFullWidthButton()
    local btn = Button(this)
    btn.layoutParams = {
        width = displayConfig.screenWidth - dp2px(32),
        height = dp2px(48)
    }
    return btn
end
```

3. **字体适配技巧**：结合SP单位与屏幕尺寸动态调整字体

```lua
-- 根据屏幕宽度动态计算字体大小
function getAdaptedFontSize(baseSp)
    local scale = displayConfig.screenWidth / 1080 -- 以1080px为基准
    return math.max(baseSp * scale, baseSp * 0.8) -- 限制最小缩放比例
end
```