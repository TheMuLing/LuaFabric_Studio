-- LuaFabric Studio 防火墙运行时（仅 IDE 会话注入；打包产物不含本文件）
-- 入口覆盖：io.open(w/a/+)/io.output | os.remove/os.rename/os.execute | io.popen | lfs.remove/rmdir/rename
-- 执行 shell：os.execute/io.popen 双入口已全覆盖；判定引擎 FirewallGate（Java，共享于 Java 侧 File/LuaUtil 拦截）
local FGOK, FG = pcall(function() return luajava.bindClass("com.androlua.FirewallGate") end)
if not FGOK or FG == nil then
  return -- 类缺失（理论上仅产物，此处防御）→ 全放行
end

-- 同一 LuaState 防重复注入（页 join 等场景导致 onSessionStart 重入时避免二次包装）
if rawget(_G, "__luafabric_fw_injected") then return true end
_G.__luafabric_fw_injected = true

-- 规范化路径：展开 ./ 与 ../（不拼 cwd，相对路径交由 Java File 解析）
local function norm(p)
  p = tostring(p or "")
  if p == "" then return p end
  local segs = {}
  for seg in p:gmatch("[^/]+") do
    if seg == "." then
      -- 跳过
    elseif seg == ".." then
      if #segs > 0 and segs[#segs] ~= ".." and segs[#segs] ~= "" then
        segs[#segs] = nil
      else
        segs[#segs + 1] = ".."
      end
    else
      segs[#segs + 1] = seg
    end
  end
  local out = table.concat(segs, "/")
  if p:sub(1, 1) == "/" then out = "/" .. out end
  if out == "" then return p end
  return out
end

-- 写判定（越级写入 + 自我守护合一，Java 引擎内部分发并上报计数）
local function checkWrite(p)
  if FG == nil then return nil end
  local ok, r = pcall(function() return FG.checkWrite(norm(p)) end)
  if not ok then return nil end
  return r ~= nil and r or nil
end

-- 写模式：mode 含 w/a/+ 任一即视为写（r 单独为读，r+ 为读写）
local function isWriteMode(mode)
  if mode == nil then return false end
  return tostring(mode):find("[wa+]") ~= nil
end

-- ---------- io ----------
local orig_open = io.open
io.open = function(path, mode)
  if isWriteMode(mode) then
    local r = checkWrite(path)
    if r then return nil, r end
  end
  return orig_open(path, mode)
end

local orig_output = io.output
io.output = function(f)
  if type(f) == "string" then
    local r = checkWrite(f)
    if r then return nil, r end
  end
  return orig_output(f)
end

-- ---------- os ----------
local orig_os_remove = os.remove
os.remove = function(path)
  local r = checkWrite(path)
  if r then return nil, r end
  return orig_os_remove(path)
end

local orig_os_rename = os.rename
os.rename = function(old, new)
  -- 双侧判定：自我保护要拦 LuaFabric-Studio/ 及 project/ 根目录被重命名
  local ok, r = pcall(function() return FG.checkRename(norm(old), norm(new)) end)
  if ok and r then return nil, r end
  return orig_os_rename(old, new)
end

-- ---------- shell 词法（os.execute / io.popen 共用） ----------
local DANGER = {
  rm = true, mv = true, cp = true, mkdir = true, rmdir = true,
  touch = true, mkfs = true, truncate = true, install = true,
  dd = true, shred = true, tee = true, unlink = true, ln = true,
}

-- 从一条命令串提取候选目标路径交给 Java 判定；解析不出确切路径 → 放行（用户已定）
local function checkShell(cmd)
  if type(cmd) ~= "string" or cmd == "" then return nil end
  local tokens = {}
  for t in cmd:gmatch("%S+") do tokens[#tokens + 1] = t end
  local i = 1
  while i <= #tokens do
    local tok = tokens[i]
    local name = tok:match("^([%w_+.-]+)")
    local used = 1
    if name and DANGER[name] then
      -- 跳过本命令 flag（-x / --x=a / a=b 形式；dd 的 of= 特殊处理）
      local j = i + 1
      while j <= #tokens and (tokens[j]:match("^%-") or tokens[j]:match("^$(%w+)=\"") or tokens[j]:match("^%w+=")) do
        j = j + 1
      end
      -- 位置参数即候选路径；遇分隔符(;/&&/|/ || )或新命令停止
      while j <= #tokens do
        local t = tokens[j]
        if t == ";" or t == "&&" or t == "|" or t == "||" or t == "$(" or t == ">" or t == ">>" then
          if t == ">" or t == ">>" then j = j + 2 else j = j + 1 end
          break
        end
        if t:match("^%-") or t:match("^%w+=") then break end
        local red = t:match("^%d?>")
        if red then break end
        local r = checkWrite(t)
        if r then return r end
        j = j + 1
      end
      -- dd 的 of= 输出目标
      if name == "dd" then
        for k = i + 1, #tokens do
          local of = tokens[k]:match("^of=(.*)$")
          if of and of ~= "" then
            local r = checkWrite(of)
            if r then return r end
          end
        end
      end
      i = j
    else
      i = i + 1
    end
  end
  return nil
end

local orig_exec = os.execute
os.execute = function(cmd)
  if type(cmd) == "string" then
    local r = checkShell(cmd)
    if r then return nil, r end
  end
  return orig_exec(cmd)
end

local orig_popen = io.popen
io.popen = function(cmd, mode)
  if type(cmd) == "string" then
    local r = checkShell(cmd)
    if r then return nil, r end
  end
  return orig_popen(cmd, mode)
end

-- ---------- lfs（若加载） ----------
if type(lfs) == "table" then
  local orig_lfs_remove = lfs.remove
  lfs.remove = function(path)
    local r = checkWrite(path)
    if r then return nil, r end
    return orig_lfs_remove(path)
  end
  local orig_lfs_rmdir = lfs.rmdir
  lfs.rmdir = function(path)
    local r = checkWrite(path)
    if r then return nil, r end
    return orig_lfs_rmdir(path)
  end
  local orig_lfs_rename = lfs.rename
  lfs.rename = function(old, new)
    local r = checkWrite(new)
    if r then return nil, r end
    return orig_lfs_rename(old, new)
  end
end

return true