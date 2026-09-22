-- main.lua — LuaFabric Compose 空项目模板（compose 运行时标记见 build.gradle.b85）
--
-- 定稿约定：
--   ui.* 为 Compose 宿主全局（数据树 + 签名缓存，无逐参反射）
--   state 写即重组；回调由桥层统一抛回主线程
--   build.gradle.b85 判无效即重生，不手改（B 类篡改不在承诺内）

local ui = require "ui"
local cfg = ui.project()                -- 读 build.gradle.b85

local state = ui.state { count = 0 }

local screen = ui.Scaffold {
  topBar = ui.TopAppBar { title = ui.Text { text = cfg.name or "AppName" } },
  content = ui.Column {
    padding = 16,
    spacing = 12,
    children = {
      ui.Text { text = "你好，Lua Compose", style = "headlineMedium" },
      ui.Button {
        onClick = function() state.count = state.count + 1 end,
        children = ui.Text { text = "点击计数：" .. state.count },
      },
    },
  },
}

ui.render(screen)