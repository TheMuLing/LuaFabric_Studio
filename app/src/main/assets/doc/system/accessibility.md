# 无障碍库 LuaAccessibilityService

无障碍服务允许应用读取屏幕上其他应用的界面节点并执行点击、滑动、输入等操作，是自动化脚本的核心能力。LuaFabric 通过 `com.androlua.LuaAccessibilityService` 将无障碍能力暴露给 Lua。

## 前置条件

无障碍服务是系统级服务，必须满足两点才可用：

1. 项目的 `AndroidManifest.xml` 声明了该服务（打包壳已内置）：
   ```xml
   <service
       android:name="com.androlua.LuaAccessibilityService"
       android:label="@string/app_name"
       android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
       <intent-filter>
           <action android:name="android.accessibilityservice.AccessibilityService" />
       </intent-filter>
       <meta-data
           android:name="android.accessibilityservice"
           android:resource="@xml/accessibility_service_config" />
   </service>
   ```
2. 用户在系统「设置 → 无障碍」中手动开启该服务（无障碍服务无法由应用自行激活）。

服务连接成功后，`LuaAccessibilityService.getInstance()` 才返回实例。

## Lua 接线

### 方式一：静态事件钩子

```lua
require "import"
import "com.androlua.LuaAccessibilityService"

-- 每次无障碍事件触发
LuaAccessibilityService.onAccessibilityEvent = function(event)
    local cls = event.getClassName()
    local text = event.getText()
    -- 处理事件...
end
```

### 方式二：全局数据表回调

服务在 `onCreate` / `onServiceConnected` / `onAccessibilityEvent` 时从全局数据 `LuaAccessibilityService` 表读取同名 Lua 函数并回调（`onError` 捕获异常）。全局数据表的具体桥接以当前项目运行时实测为准。

## 常用方法

| 方法 | 说明 |
|------|------|
| `getInstance()` | 获取服务实例，未连接时返回 nil |
| `click(x, y)` | 坐标点击 |
| `longClick(x, y)` | 坐标长按 |
| `press(x, y, delay)` | 坐标按压（可指定时长） |
| `swipe(x1, y1, x2, y2, delay)` | 坐标滑动 |
| `click({...})` | 按描述查找节点并点击（见下文） |
| `toClick(node)` / `toLongClick(node)` | 对节点执行点击 / 长按 |
| `scrollForward(node)` / `scrollBackward(node)` | 节点滚动 |
| `toBack()` / `toHome()` / `toRecents()` / `toNotifications()` | 全局返回 / 桌面 / 最近任务 / 通知栏 |
| `startApp(appName)` | 按应用名启动应用 |
| `getText(node)` / `copy()` / `paste()` / `setText(text)` | 文本读取、复制、粘贴、设置 |
| `findAccessibilityNodeInfoByText(关键字)` | 按文本查找节点列表 |
| `findAccessibilityNodeInfo(描述)` | 按复合描述定位单个节点 |
| `getScreenshot()` | 屏幕截图 |

## 节点查找

`findAccessibilityNodeInfo(描述)` 的复合描述语法（源码支持）：

| 前缀 | 含义 | 示例 |
|------|------|------|
| 无 | 文本匹配 | `"登录"` |
| `*` | 通配（`*文本`=结尾、`文本*`=开头、`*文本*`=包含） | `"*登录*"` |
| `|` | 多关键字或 | `"登录|注册"` |
| `#` | 第 N 个匹配（负数从尾部） | `"确定#1"` |
| `@` | 限定当前应用 | `"确定@抖音"` |
| `$` | 按子节点索引定位 | `"$1-2-0"` |
| `>` | 启动应用 | `">设置"` |
| `%` | 执行内置命令 | `"%向下翻页"` |

## 综合示例

```lua
require "import"
import "com.androlua.LuaAccessibilityService"

LuaAccessibilityService.onAccessibilityEvent = function(event)
    -- 事件驱动：每次界面变化触发
end

-- 服务连接后自动执行（全局数据表回调示例）
-- LuaAccessibilityService = {
--     onServiceConnected = function(service)
--         -- 找到"登录"按钮并点击
--         local node = service.findAccessibilityNodeInfo("登录")
--         if node ~= nil then
--             service.toClick(node)
--         end
--     end,
--     onError = function(err)
--         print("无障碍异常：" .. tostring(err))
--     end
-- }

local service = LuaAccessibilityService.getInstance()
if service ~= nil then
    service.click(540, 1200)          -- 坐标点击
    service.swipe(540, 1500, 540, 600, 500)  -- 上滑
    service.toBack()                  -- 返回键
end
```

## 注意事项

- 服务未开启时 `getInstance()` 返回 nil，调用前务必判空。
- 无障碍节点是系统的临时快照，界面刷新后旧节点可能失效，需重新查找。
- 坐标点击依赖屏幕分辨率，不同设备请按实际屏幕适配。
- 自动化操作请遵守应用与平台的使用规范，避免滥用。
