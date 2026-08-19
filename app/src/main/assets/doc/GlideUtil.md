# GlideUtil - 文档

## 一、概述

- **功能**：基于Glide库的图片加载工具类，提供异步加载、缓存管理、图片变换（圆形/圆角）等功能，支持多格式图片与丰富配置。
- **适用场景**：Android开发中各类图片展示需求，如头像、列表图片、 banner图等。
- **优势**：复用原生高性能加载内核，兼容性强、稳定性高，无需从零开发基础加载能力。

## 二、启用方式

在项目 `settings.json` 文件中添加工具类到全局工具列表：

```json
{
  "application": {
    "label": "My App",
    "debugmode": true
  },
  "global_utils": [
    "GlideUtil"
  ]
}
```

## 三、核心函数

### 1. loadImage（基础图片加载）

- **Lua签名**：`loadImage(imgView, url, ...)`
- **功能**：异步加载图片到指定ImageView，支持占位符、尺寸调整、缓存控制等。
- **参数**：
    - `imgView` (userdata)：目标ImageView对象（必填）
    - `url` (string)：图片路径（支持本地文件、http/https，必填；资源ID仅可用于 placeholder/error 参数）
    - `placeholder` (number|string, 可选)：加载中占位图（资源ID或路径）
    - `error` (number|string, 可选)：加载失败显示图
    - `width` (number, 可选)：目标宽度（像素）
    - `height` (number, 可选)：目标高度（像素）
    - `centerCrop` (boolean, 可选)：是否启用centerCrop裁剪
    - `fitCenter` (boolean, 可选)：是否启用fitCenter缩放
    - `skipMemoryCache` (boolean, 可选)：是否跳过内存缓存
    - `diskCacheStrategy` (string, 可选)：磁盘缓存策略（"NONE"/"DATA"/"RESOURCE"/"AUTOMATIC"/"ALL"）
    - `signature` (any, 可选)：缓存签名（用于强制刷新）
    - `crossFade` (boolean, 可选)：是否启用淡入淡出动画
    - `crossFadeDuration` (number, 可选)：动画时长（毫秒）
- **返回值**：无
- **示例**：

```lua
-- 基础加载
loadImage(imageView, "https://example.com/avatar.jpg")
-- 带占位符、错误图与动画
loadImage(imageView, "banner.jpg", "placeholder.png", "error.png", nil, dp2px(200), true, false, false, "ALL", nil, true, 500)
```

### 2. loadCircleImage（圆形图片加载）

- **Lua签名**：`loadCircleImage(imgView, url, placeholder, error)`
- **功能**：加载圆形裁剪图片，适用于头像场景。
- **参数**：
    - `imgView` (userdata)：目标ImageView（必填）
    - `url` (string)：图片路径（必填）
    - `placeholder` (number|string, 可选)：占位图
    - `error` (number|string, 可选)：加载失败图
- **返回值**：无
- **示例**：

```lua
local avatarView = ImageView(this)
avatarView.layoutParams = LinearLayout.LayoutParams(dp2px(48), dp2px(48))
loadCircleImage(avatarView, "user_avatar.jpg", R.drawable.ic_default_avatar)
```

### 3. loadRoundedImage（圆角图片加载）

- **Lua签名**：`loadRoundedImage(imgView, url, radius, placeholder, error)`
- **功能**：加载指定圆角半径的图片。
- **参数**：
    - `imgView` (userdata)：目标ImageView（必填）
    - `url` (string)：图片路径（必填）
    - `radius` (number)：圆角半径（像素，必填）
    - `placeholder` (number|string, 可选)：占位图
    - `error` (number|string, 可选)：加载失败图
- **返回值**：无
- **示例**：

```lua
local cardImg = ImageView(this)
local radius = dp2px(8) -- 转换为像素适配屏幕
loadRoundedImage(cardImg, "card_bg.jpg", radius, "card_placeholder.png")
```

### 4. preloadImage（图片预加载）

- **Lua签名**：`preloadImage(url)`
- **功能**：预加载图片到缓存，提升后续显示速度。
- **参数**：`url` (string)：图片路径（必填）
- **返回值**：无
- **示例**：

```lua
-- 应用启动时预加载常用图片
function onCreate()
    preloadImage("main_bg.jpg")
    preloadImage("default_avatar.png")
end
```

### 5. clearGlideCache（缓存清理）

- **Lua签名**：`clearGlideCache()`
- **功能**：清理Glide内存缓存与磁盘缓存。
- **参数**：无
- **返回值**：无
- **示例**：

```lua
-- 清理缓存按钮点击事件
local function onClearCacheClick()
    clearGlideCache()
    toast("缓存已清理")
end
```

### 6. pauseRequests（暂停加载请求）

- **Lua签名**：`pauseRequests()`
- **功能**：暂停所有进行中的图片加载，适用于列表快速滚动场景。
- **参数**：无
- **返回值**：无
- **示例**：

```lua
-- ListView滚动时暂停加载
listView.setOnScrollListener({
    onScrollStateChanged = function(view, scrollState)
        if scrollState ~= SCROLL_STATE_IDLE then
            pauseRequests()
        end
    end
})
```

### 7. resumeRequests（恢复加载请求）

- **Lua签名**：`resumeRequests()`
- **功能**：恢复暂停的图片加载请求。
- **参数**：无
- **返回值**：无
- **示例**：

```lua
-- 列表滚动停止时恢复加载
listView.setOnScrollListener({
    onScrollStateChanged = function(view, scrollState)
        if scrollState == SCROLL_STATE_IDLE then
            resumeRequests()
        end
    end
})
```

## 四、使用示例

### 示例1：网格布局图片列表

```lua
local images = {"img1.jpg", "img2.jpg", "img3.jpg", "img4.jpg"}
local gridLayout = GridLayout(this)
gridLayout.columnCount = 2

for i, url in ipairs(images) do
    local imgView = ImageView(this)
    local params = GridLayout.LayoutParams()
    params.width = widthPercent(50)
    params.height = dp2px(150)
    imgView.layoutParams = params
    -- 加载圆角图片
    loadRoundedImage(imgView, url, dp2px(8), "placeholder.jpg", "error.jpg")
    gridLayout.addView(imgView)
end
```

### 示例2：带缓存刷新的头像加载

```lua
-- 加载用户头像，支持更换后实时刷新
function loadUserAvatar(imgView, userId, avatarUrl)
    local signature = userId .. "_" .. os.time() -- 唯一签名确保刷新
    loadCircleImage(imgView, avatarUrl, R.drawable.ic_default_avatar, R.drawable.ic_load_fail)
end

-- 调用
local avatarView = ImageView(this)
loadUserAvatar(avatarView, "user8975", "https://api.example.com/avatar/8975.jpg")
```

## 五、最佳实践

1. **统一配置封装**：封装通用加载函数，减少重复代码

```lua
local function loadCardImage(view, url)
    loadImage(
        view, url,
        "card_placeholder.png", "card_error.png",
        widthPercent(100), dp2px(180),
        true, false, false, "RESOURCE", nil, true, 300
    )
end
```

2. **内存优化**：Activity销毁时清理资源

```lua
function onDestroy()
    pauseRequests()
    clearGlideCache()
end
```

3. **缓存策略适配**：频繁变化图片用`diskCacheStrategy="NONE"`，静态图片用`"ALL"`；需更新的图片通过
   `signature`强制刷新。
4. **尺寸适配**：使用`dp2px`转换尺寸，确保不同屏幕密度下显示一致。