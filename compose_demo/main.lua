-- main.lua — LuaFabric Compose 项目「落地后」模拟实例（预览/展示用）
--
-- 对应已确认方案：
--   配置：build.gradle.b85（引导段 4B + SHA-256 + BSON，base85 文本化）；反序列化失败 → 判定无效、IDE 重生
--   A1 ：「数据树 + 签名缓存」— Lua 只产数据树，单一 ui 宿主装配，热路径零反射
--   状态：显式事件驱动 v1，state.* 写入即 invalidate 对应 scope

local ui = require "ui"   -- Compose 宿主绑定（buildComponents + 签名缓存，无逐参反射）

-- 本项目允许的全局仅有 ui.*（与全局辅助库白名单同源），无 get/post 等网络全局
local cfg = ui.project()  -- 读 project.conf.b91 → { name="协作清单", packageId="cn.lf.demo", uiMode="compose", ... }

--------------------------------------------------------------------------------
-- 状态：写即重组（v1 显式事件驱动，不经 snapshot 双向绑定）
--------------------------------------------------------------------------------
local state = ui.state {
  count = 0,
  busy  = false,
  todos = { "数据树入库", "签名缓存热路径", "二进制配置引导段" },
}

local function onAdd()
  state.busy = true
  ui.later(16, function()               -- 主线程回调闸门：桥层统一 post 到 main looper
    table.insert(state.todos, "新条目 #" .. (#state.todos + 1))
    state.busy = false
  end)
end

--------------------------------------------------------------------------------
-- 界面 = 纯数据树；叶子属性在 Kotlin 宿主统一解析（number/string → Dp/Sp/Color）
--------------------------------------------------------------------------------
local screen = ui.Scaffold {
  topBar = ui.TopAppBar {
    title = ui.Text { text = cfg.name .. " · Compose 预览" },
  },
  content = ui.LazyColumn {
    spacing = 8,
    contentPadding = 16,
    children = {
      ui.Text {
        text  = "你好，Lua Compose",
        style = "headlineMedium",
        color = ui.color.primary,
      },
      ui.Spacer { height = 8 },

      -- 状态展示：Button 回调 → JavaFunction → () -> Unit
      ui.Button {
        onClick = function() state.count = state.count + 1 end,
        children = ui.Text { text = "点击计数：" .. state.count },
      },
      ui.Row {
        spacing = 8,
        children = {
          ui.OutlinedButton { text = "添加任务", onClick = onAdd },
          ui.Text { text = state.busy and "处理中…" or "就绪", style = "labelMedium" },
        },
      },

      -- 图片：数据树节点 → Coil 加载（依赖保留，非全局函数）
      ui.AsyncImage { src = "res/cat.png", radius = 12, height = 120, weight = 1 },

      ui.Divider {},
      ui.Text { text = "任务列表", style = "titleMedium" },

      -- 列表：ForEach 由宿主按 key diff 重组
      ui.ForEach {
        of     = state.todos,
        key    = function(t) return t end,
        render = function(t)
          return ui.Card {
            children = ui.Row {
              spacing = 8,
              children = {
                ui.Icon { name = "check_circle", tint = "tertiary" },
                ui.Text { text = t, weight = 1 },
                ui.IconButton {
                  icon = "delete",
                  onClick = function()
                    for i, v in ipairs(state.todos) do
                      if v == t then table.remove(state.todos, i); break end
                    end
                  end,
                },
              },
            },
          }
        end,
      },
    },
  },
}

-- 单次装配进宿主 Recomposer；后续 diff 由签名缓存承接
ui.render(screen)