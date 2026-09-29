# ViewPager2 页面滑动容器

`ViewPager2` 是基于 `RecyclerView` 的新一代翻页容器，用于左右滑动切换多个页面。本工程运行时内置了与它配套的 `LuaPagerAdapter`，在 `loadlayout` 中只要给 `ViewPager2` 提供 `pages` 属性即可自动装载多个子页面，无需手工绑定适配器。

## 基本用法（loadlayout + pages）

```lua
require "import"
import "androidx.viewpager2.widget.ViewPager2"
import "android.widget.TextView"

-- 三个子页面布局
local page1 = {
  LinearLayout,
  layout_width = "fill",
  layout_height = "fill",
  orientation = "vertical",
  gravity = "center",
  {
    TextView,
    text = "第一页",
    textSize = 24,
  },
}

local page2 = {
  LinearLayout,
  layout_width = "fill",
  layout_height = "fill",
  orientation = "vertical",
  gravity = "center",
  {
    TextView,
    text = "第二页",
    textSize = 24,
  },
}

local page3 = {
  LinearLayout,
  layout_width = "fill",
  layout_height = "fill",
  orientation = "vertical",
  gravity = "center",
  {
    TextView,
    text = "第三页",
    textSize = 24,
  },
}

-- 主布局：pages 属性传入子页面数组，运行时逐个 loadlayout 后经 LuaPagerAdapter 装载
local layout = {
  LinearLayout,
  layout_width = "fill",
  layout_height = "fill",
  {
    ViewPager2,
    id = "pager",
    layout_width = "fill",
    layout_height = "fill",
    pages = { page1, page2, page3 },
  },
}

activity.setContentView(loadlayout(layout))
```

几点说明：

- `pages` 的每个元素是一张**布局表**，由运行时统一 `loadlayout` 成 View。
- **不要**对 `ViewPager2` 手工调用 `addView`，页面一律通过 `pages` 提供。
- 指定 `id` 后布局注入 `_G`，可直接以 `页面变量`（如 `pager`）访问。

## 页面切换监听

`ViewPager2` 只提供抽象类回调 `OnPageChangeCallback`，luajava 无法直接把 Lua 表转为抽象类参数，须经内置辅助类 `Vp2PageChangeHelper` 注册：

```lua
import "github.daisukiKaffuChino.Vp2PageChangeHelper"

Vp2PageChangeHelper.register(pager, {
  onPageSelected = function(position)
    print("滑到第 " .. position .. " 页")
  end,
})
```

回调表内可提供 `onPageSelected`（页面切换完成）、`onPageScrollStateChanged`（滚动状态变化）等方法。

## 实战：配合底部导航联动

"顶部 ViewPager2 滑动 + 底部导航条选中态"是最常见组合。核心是两步互锁：**底部点击驱动翻页**、**滑动翻页回写底部选中态**，中间用同步锁防止死循环。

```lua
require "import"
import "androidx.viewpager2.widget.ViewPager2"
import "github.daisukiKaffuChino.Vp2PageChangeHelper"
import "com.google.android.material.bottomnavigation.BottomNavigationView"

local syncing = false

-- 底部导航点击 -> 翻页
bottomNav.setOnItemSelectedListener(ItemSelectedListener{
  onNavigationItemSelected = function(item)
    if syncing then return true end
    syncing = true
    pager.setCurrentItem(item.getItemId() - 1, true)
    syncing = false
    return true
  end,
})

-- 滑动翻页 -> 回写底部选中态
Vp2PageChangeHelper.register(pager, {
  onPageSelected = function(position)
    if syncing then return end
    syncing = true
    bottomNav.setSelectedItemId(position + 1)
    syncing = false
  end,
})
```

要点：

- `setCurrentItem(position, true)` 第二参为 `true` 时平滑滚动至对应页。
- `onPageSelected` 与底部选中回调都可能互相触发，用 `syncing` 锁避免重复联动。
- 用 `setSelectedItemId` 同步底部导航，选中图标/文字自动高亮。

## 其他常用方法

| 方法 | 说明 |
|------|------|
| `setCurrentItem(position)` | 直接切到指定页 |
| `setCurrentItem(position, true)` | 平滑滚动到指定页 |
| `getCurrentItem()` | 获取当前页索引 |
| `setOffscreenPageLimit(n)` | 预加载页数，默认预加载相邻页 |
| `setAdapter(pagerAdapter)` | 手动设置适配器（本工程一般用 `pages` 属性自动装载） |