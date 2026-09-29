# UiUtil 屏幕尺寸工具

屏幕尺寸与单位转换工具类：封装 dp/sp/像素 互相转换、屏幕宽高、状态栏高度、横竖屏判断与百分比尺寸计算等函数，自动适配屏幕密度与字体缩放，简化多设备 UI 适配。

## 一、启用方式

在项目 `settings.json` 的 `global_utils` 列表中加入 `"UiUtil"`：

```json
{
  "application": {
    "label": "My App",
    "debugmode": true
  },
  "global_utils": ["UiUtil"]
}
```

启用后即可直接在 Lua 中调用下列全局函数。

## 二、单位转换

### dp2px

将 DP 转像素，自动适配当前屏幕密度。

**签名**：`dp2px(dp)`

| 参数 | 类型 | 说明 |
|------|------|------|
| `dp` | number | 要转换的 DP 值（必填） |

**返回值**：integer，转换后的像素值。

```lua
local px = dp2px(16) -- 320dpi 设备上返回 48
print("16dp = " .. px .. "px")
```

### px2dp

将像素转 DP。

**签名**：`px2dp(px)`

| 参数 | 类型 | 说明 |
|------|------|------|
| `px` | number | 像素值（必填） |

**返回值**：number，转换后的 DP 浮点值。

```lua
local dp = px2dp(1080)
print("1080px = " .. dp .. "dp")
```

### sp2px

将 SP（缩放独立像素）转像素，适配系统字体缩放设置。

**签名**：`sp2px(sp)`

### px2sp

将像素转 SP。

**签名**：`px2sp(px)`

```lua
local px = sp2px(14)
local sp = px2sp(px)
```

## 三、屏幕信息

### width / height

**签名**：`width()` / `height()`

获取屏幕宽 / 高（像素）。返回 integer。

```lua
print("屏幕尺寸: " .. width() .. "x" .. height())
```

### statusBarHeight

**签名**：`statusBarHeight()`

获取系统状态栏高度（像素），获取失败返回 0。

```lua
activity.setPadding(0, statusBarHeight(), 0, 0)
```

### isLandscape / isPortrait

**签名**：`isLandscape()` / `isPortrait()`

判断当前横竖屏模式，返回 boolean。

```lua
if isLandscape() then
  print("横屏")
else
  print("竖屏")
end
```

### actionBarSize

**签名**：`actionBarSize()`

获取当前主题 ActionBar 高度（像素），失败返回 0。

## 四、百分比尺寸

| 函数 | 返回值说明 |
|------|-----------|
| `widthPercent(percent)` | 屏幕宽度百分比，返回 integer 像素 |
| `heightPercent(percent)` | 屏幕高度百分比，返回 integer 像素 |
| `minPercent(percent)` | 屏幕较小边百分比（适合绘制正方形），返回 integer 像素 |

```lua
-- 占屏幕最小边 50% 的正方形按钮
button.width  = minPercent(50)
button.height = minPercent(50)
```

## 五、Edge-to-Edge

**签名**：`applyEdgeToEdgePreference(window, edgeToEdgeEnabled)`

设置窗口内容是否延伸进系统栏，需配合 Insets 避让。

| 参数 | 类型 | 说明 |
|------|------|------|
| `window` | userdata | Window 对象，传 `activity.window` |
| `edgeToEdgeEnabled` | boolean | 是否启用（必填） |

```lua
applyEdgeToEdgePreference(activity.window, false)
```

## 六、综合示例

### 响应式布局

```lua
-- 计算容器尺寸（占屏幕 80% 宽、60% 高）并居中
local containerWidth  = widthPercent(80)
local containerHeight = heightPercent(60)
local layoutParams = {
  width = containerWidth,
  height = containerHeight,
  leftMargin = (width() - containerWidth) / 2,
  topMargin = (height() - containerHeight) / 2,
  padding = dp2px(16)
}
```

### 启动缓存常用参数

```lua
local displayConfig = {
  screenWidth = width(),
  screenHeight = height(),
  statusBarHeight = statusBarHeight(),
  dp1 = dp2px(1), -- 1dp 对应的像素
  isTablet = width() > dp2px(600)
}
```

### 字体大小适配

```lua
function getAdaptedFontSize(baseSp)
  local scale = width() / 1080 -- 以 1080px 为基准
  return math.max(baseSp * scale, baseSp * 0.8) -- 限制最小缩放
end
```

> 常用尺寸建议在启动时缓存一次，避免重复调用。