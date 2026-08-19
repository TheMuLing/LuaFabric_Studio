# ThemeUtil - 文档

## 一、概述

- **功能**：主题工具类，提供系统深色模式检测、状态栏/导航栏样式设置及WindowInsets处理功能，适配Android系统主题。
- **适用场景**：Android开发中需要适配系统主题、调整状态栏导航栏图标颜色、处理刘海屏/状态栏遮挡等场景。
- **优势**：自动适配系统深色/浅色模式，统一处理状态栏导航栏样式，避免内容被系统栏遮挡。

## 二、启用方式

在项目 `settings.json` 文件中添加工具类到全局工具列表：

```json
{
  "application": {
    "label": "My App",
    "debugmode": true
  },
  "global_utils": [
    "ThemeUtil"
  ]
}
```

## 三、核心函数

### 1. isSystemDarkTheme（检测系统深色模式）

- **Lua签名**：`isSystemDarkTheme()`
- **功能**：检测当前系统是否处于深色模式。
- **参数**：无
- **返回值**：(boolean) 系统处于深色模式返回`true`，浅色模式返回`false`
- **示例**：

```lua
if isSystemDarkTheme() then
    print("当前为系统深色模式")
    -- 加载深色主题资源
else
    print("当前为系统浅色模式")
end
```

### 2. applySystemThemeSettings（自动应用系统主题设置）

- **Lua签名**：`applySystemThemeSettings()`
- **功能**：根据系统主题自动设置状态栏/导航栏图标颜色，并处理WindowInsets避免内容被遮挡。
- **参数**：无
- **返回值**：无
- **示例**：

```lua
activity.setContentView(loadlayout("layout"))
applySystemThemeSettings() -- 自动适配系统主题
```

### 3. setupWindowInsets（处理WindowInsets）

- **Lua签名**：`setupWindowInsets()`
- **功能**：为内容视图设置适当的Padding，避免被状态栏、导航栏等系统栏遮挡。
- **参数**：无
- **返回值**：无
- **示例**：

```lua
-- 手动设置Insets，避免顶部内容被状态栏遮挡
setupWindowInsets()
```

### 4. setLightBars（手动设置状态栏导航栏样式）

- **Lua签名**：`setLightBars(lightStatusBars, lightNavigationBars)`
- **功能**：手动设置状态栏和导航栏的浅色外观模式（即图标颜色）。
- **参数**：
    - `lightStatusBars` (boolean)：是否为浅色状态栏（true=图标深色，false=图标浅色）
    - `lightNavigationBars` (boolean, 可选)：是否为浅色导航栏，默认与lightStatusBars相同
- **返回值**：无
- **示例**：

```lua
-- 设置状态栏图标为深色（浅色背景时用）
setLightBars(true)
-- 设置状态栏图标深色，导航栏图标浅色
setLightBars(true, false)
```

## 四、使用示例

### 示例1：根据系统主题动态切换界面主题

```lua
activity.setContentView(loadlayout("layout"))

-- 自动适配系统主题（状态栏/导航栏图标颜色 + Insets避让）
applySystemThemeSettings()

-- 根据系统主题动态选择界面配色
if isSystemDarkTheme() then
  -- 深色模式：使用深色配色
  mainLayout.backgroundColor = 0xFF121212
  titleText.textColor = 0xFFFFFFFF
else
  -- 浅色模式：使用浅色配色
  mainLayout.backgroundColor = 0xFFFFFFFF
  titleText.textColor = 0xFF000000
end
```

### 示例2：状态栏与导航栏单独控制

```lua
activity.setContentView(loadlayout("layout"))

-- 应用系统主题设置
applySystemThemeSettings()

-- 根据系统主题设置背景色
if isSystemDarkTheme() then
  -- 深色模式：设置深色背景
  mainLayout.backgroundColor = 0xFF121212
  titleText.textColor = 0xFFFFFFFF
 else
  -- 浅色模式：设置浅色背景
  mainLayout.backgroundColor = 0xFFFFFFFF
  titleText.textColor = 0xFF000000
end
```

### 示例3：避免内容被刘海屏/状态栏遮挡

```lua
activity.setContentView(loadlayout("layout"))

-- 设置WindowInsets，自动避让系统栏
setupWindowInsets()

-- 如果需要手动微调，可以获取状态栏高度
local sbHeight = statusBarHeight()
-- 给顶部控件增加额外边距
topView.topPadding = sbHeight + dp2px(8)
```

## 五、最佳实践

1. **生命周期正确调用**：建议在`onCreate`中调用`applySystemThemeSettings()`，确保主题在界面初始化时正确应用

```lua
activity.setContentView(loadlayout("layout"))
applySystemThemeSettings() -- 自动适配系统主题
```

2. **动态资源加载**：结合系统主题检测，为深色/浅色模式加载不同的颜色、图片资源

```lua
local function getThemeColor()
    return isSystemDarkTheme() and 0xFF4CAF50 or 0xFF2196F3
end

local function getThemeBackground()
    return isSystemDarkTheme() and 0xFF121212 or 0xFFFFFFFF
end
```

3. **状态栏图标颜色匹配**：根据背景色设置状态栏图标样式，确保图标可见性

```lua
-- 当背景为浅色时，图标应为深色
mainLayout.backgroundColor = 0xFFFFFFFF
setLightBars(true) -- 深色图标
```

4. **全面屏适配必做**：在全面屏或刘海屏设备上，务必调用`setupWindowInsets()`避免内容被系统栏遮挡

```lua
-- 在onCreate中调用
setupWindowInsets()
```

## 六、Q & A

- **问题**：调用`applySystemThemeSettings()`后状态栏图标颜色没有改变
  **原因/解决**：检查是否在`setContentView`之后调用，且Activity是否已正确初始化；部分Android版本需要在
  `onWindowFocusChanged`中重新调用。

- **问题**：`isSystemDarkTheme()`返回值与预期不符
  **原因/解决**：检查应用是否强制设置了主题（如`android:forceDarkAllowed`），或系统是否处于电池省电模式自动切换深色主题。

- **问题**：调用`setupWindowInsets()`后布局出现额外空白
  **原因/解决**：确保没有在XML布局中重复设置`fitsSystemWindows="true"`，两者可能冲突，建议使用代码统一控制。