local bindClass = luajava.bindClass
local layout_main

luaproject = luajava.luaextdir

require "classes"
require "layoutData"
method = require "method"

local intent = activity.getIntent()
local layoutContent = intent.getStringExtra("layout_content")
local luapath = intent.getStringExtra("luapath")

luadir = luapath:gsub("/[^/]+$", "")

local ArrayExpandableListAdapter = bindClass "android.widget.ArrayExpandableListAdapter"
local MaterialAlertDialogBuilder = bindClass "com.google.android.material.dialog.MaterialAlertDialogBuilder"
local View = bindClass "android.view.View"
local File = bindClass "java.io.File"
local MyBottomSheetDialog = require "MyBottomSheetDialog"
local loadlayout = require "loadlayout"
local loadlayout2 = require "loadlayout2"

function Error(str)
  local logPath = "/storage/emulated/0/LuaFabric-Studio/LuaFabric.log"
  local file = io.open(logPath, "a+")
  if file then
    file:write(string.format("[%s] [ERROR] [Layouthelper] %s\n", os.date("%Y-%m-%d %H:%M:%S"), tostring(str)))
    file:close()
  end
end

function onError(title, message)
  MaterialAlertDialogBuilder(this)
  .setTitle(tostring(title))
  .setMessage(tostring(message))
  .setPositiveButton("确定", nil)
  .show()
end

try
  result = assert(loadstring("return " .. layoutContent))()
  layout_main = result
 catch(e)
  print("布局字符串解析失败: " .. tostring(e))
  Error(e)
  activity.finish()
end

activity
.setContentView(loadlayout("layouthelper"))
.setSupportActionBar(toolbar)
.getSupportActionBar()
.setDisplayHomeAsUpEnabled(true)

activity.decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)

try
  root.addView(loadlayout2(layout_main, {}))
 catch(e)
  print("不支持编辑此布局." .. e)
  Error(e)
  activity.finish()
end

--属性列表对话框
fd_dlg = MyBottomSheetDialog(activity).setView("dialog_item")
fd_list, fd_title = mDialogListView, mDialogTitle

--属性选择列表
checks = {}
checks.layout_width = { "match_parent", "wrap_content", "Fixed size..." }
checks.layout_height = { "match_parent", "wrap_content", "Fixed size..." }
checks.ellipsize = { "start", "end", "middle", "marquee" }
checks.singleLine = { "true", "false" }
checks.fitsSystemWindows = { "true", "false" }
checks.orientation = { "vertical", "horizontal" }
checks.gravity = { "left", "top", "right", "bottom", "start", "center", "end", "bottom|end", "end|center", "left|center", "top|center", "bottom|center" }
checks.layout_gravity = { "left", "top", "right", "bottom", "start", "center", "end", "bottom|end", "end|center", "left|center", "top|center", "bottom|center" }
checks.scaleType = {
  "matrix",
  "fitXY",
  "fitStart",
  "fitCenter",
  "fitEnd",
  "center",
  "centerCrop",
  "centerInside"
}

function addDir(out, dir, f)
  local ls = f.listFiles()
  for n = 0, #ls - 1 do
    local name = ls[n].getName()
    if ls[n].isDirectory() then
      addDir(out, dir .. name .. "/", ls[n])
     elseif name:find("%.j?pn?g$") then
      table.insert(out, dir .. name)
    end
  end
end

checks.src = function()
  local src = {}
  addDir(src, "", File(luadir))
  return src
end

fd_list.onItemClick = function(l, v, p, i)
  fd_dlg.dismiss()
  local fd = tostring(v.Text)
  if string.find(fd, " = ") then
    fd = fd:gsub("% = .*", "")
  end
  if checks[fd] then
    if type(checks[fd]) == "table" then
      check_title.setText(fd)
      method.adapter(check_list, checks[fd])
      check_dlg.show()
     else
      check_title.setText(fd)
      method.adapter(check_list, checks[fd](fd))
      check_dlg.show()
    end
   else
    func[fd]()
  end
end

--子视图列表对话框
cd_dlg = MyBottomSheetDialog(activity).setView("dialog_item")
cd_list, cd_title = mDialogListView, mDialogTitle
cd_list.onItemClick = function(l, v, p, i)
  getCurr(chids[p])
  cd_dlg.dismiss()
end

--可选属性对话框
check_dlg = MyBottomSheetDialog(activity).setView("dialog_item")
check_list, check_title = mDialogListView, mDialogTitle
check_list.onItemClick = function(l, v, p, i)
  local v = tostring(v.text)
  if #v == 0 or v == "none" then
    v = nil
   elseif v == "Fixed size..." then
    check_dlg.dismiss()
    func[check_title.Text]()
    return
  end
  local fld = check_title.Text
  local old = curr[tostring(fld)]
  curr[tostring(fld)] = v
  check_dlg.dismiss()
  local s, l = pcall(loadlayout2, layout_main, {})
  if s then
    method.showlayout(l)
   else
    curr[tostring(fld)] = old
    print(l)
    Error(l)
  end
end

func = {}
func["添加"] = function()
  add_title.setText(tostring(currView.Class.getSimpleName()))
  for n = 0, #ns - 1 do
    if n ~= i then
      el.collapseGroup(n)
    end
  end
  add_dlg.show()
end

func["删除"] = function()
  local gp = currView.Parent.Tag
  if gp == nil then
    print("顶部控件可能不会被删除")
    return
  end
  for k, v in ipairs(gp) do
    if v == curr then
      table.remove(gp, k)
      break
    end
  end
  method.showlayout(loadlayout2(layout_main, {}))
end

func["父控件"] = function()
  local p = currView.Parent
  if p.Tag == nil then
    print("已经是顶部控件")
   else
    getCurr(p)
  end
end

chids = {}
func["子控件"] = function()
  chids = {}
  local arr = {}
  for n = 0, currView.ChildCount - 1 do
    local chid = currView.getChildAt(n)
    chids[n] = chid
    table.insert(arr, chid.Class.getSimpleName())
  end
  cd_title.setText(tostring(currView.Class.getSimpleName()))
  method.adapter(cd_list, arr)
  cd_dlg.show()
end

--添加视图对话框
add_dlg = MyBottomSheetDialog(activity).setView("dialog_expandablelist")
el, add_title = mDialogListView, mDialogTitle

local mAdapter = ArrayExpandableListAdapter(activity)

for k, v in ipairs(ns) do
  for i = 1, #wds2[k] do
    wds2[k][i] = wds[k][i] .. (" - " .. wds2[k][i] or "")
  end

  ns[k] = ns2[k] or ""
  mAdapter.add(ns[k], wds2[k])
end

el.setAdapter(mAdapter)

el.onChildClick = function(l, v, g, c)
  local w = { _G[wds[g + 1][c + 1]] }
  table.insert(curr, w)
  local s, l = pcall(loadlayout2, layout_main, {})
  if s then
    method.showlayout(l)
   else
    table.remove(curr)
    print(l)
    Error(l)
  end
  add_dlg.dismiss()
end

local function createEditDialog(k)
  local ids = {}
  local dialog = MaterialAlertDialogBuilder(activity)
  dialog.setView(loadlayout("dialog_fileinput", ids))

  ids.content.setText(curr[k] or "")

  dialog.setPositiveButton("确定", function()
    local v = tostring(ids.content.Text)
    if #v == 0 then
      v = nil
    end
    local old = curr[tostring(k)]
    curr[tostring(k)] = v
    local s, l = pcall(loadlayout2, layout_main, {})
    if s then
      method.showlayout(l)
     else
      curr[tostring(k)] = old
      print(l)
      Error(l)
    end
  end)

  dialog.setNegativeButton("取消", nil)

  dialog.setNeutralButton("无", function()
    local old = curr[tostring(k)]
    curr[tostring(k)] = nil
    local s, l = pcall(loadlayout2, layout_main, {})
    if s then
      method.showlayout(l)
     else
      curr[tostring(k)] = old
      print(l)
      Error(l)
    end
  end)

  return dialog, ids
end

setmetatable(func, {
  __index = function(t, k)
    return function()
      local dialog, ids = createEditDialog(k)
      dialog.setTitle(k)
      dialog.show()
    end
  end
})

local function save()
  local newLayoutStr = method.dumplayout(layout_main)
  local resultIntent = luajava.newInstance("android.content.Intent")
  resultIntent.putExtra("layout_result", newLayoutStr)
  activity.setResult(activity.RESULT_OK, resultIntent)
  activity.finish()
end

function onCreateOptionsMenu(menu)
  menu.add("保存")
  .setShowAsAction(2)
  .setIcon(bindClass "com.luafabric.studio.falling.R".drawable.ic_content_save_outline)
  .onMenuItemClick = function()
    save()
  end
end

function onOptionsItemSelected(item)
  local id = item.getItemId()
  if id == android.R.id.home then
    save()
  end
end

function onKeyDown(e)
  if e == 4 then
    activity.setResult(activity.RESULT_CANCELED)
    activity.finish()
    return true
  end
end