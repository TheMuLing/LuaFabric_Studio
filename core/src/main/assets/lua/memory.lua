--by：YuYuanJin Apache-2
--[[
初次使用请先：
local a = require "memory"
print(a.help())

For first-time use, please:
local a = require "memory"
print(a.help())
]]
-- Lua 5.5 全局变量显式声明
global print, warn, error, pcall, tostring, tonumber, type, math, table, string, os, collectgarbage, debug, io, luajava, activity, require, ipairs, pairs, coroutine

-- ============================================================
-- 库模块定义
-- ============================================================
local M = {}

-- ============================================================
-- 局部化常用函数（性能优先，避免全局哈希查找）
-- ============================================================
local math_max = math.max
local math_min = math.min
local math_floor = math.floor
local math_abs = math.abs
local math_tanh = math.tanh
local table_create = table.create
local table_insert = table.insert
local table_remove = table.remove
local os_time = os.time
local os_clock = os.clock
local os_date = os.date
local collectgarbage_func = collectgarbage
local tonumber_func = tonumber
local tostring_func = tostring
local type_func = type
local pcall_func = pcall
local ipairs_func = ipairs
local pairs_func = pairs
local coroutine_create = coroutine.create
local coroutine_resume = coroutine.resume
local coroutine_yield = coroutine.yield
local coroutine_status = coroutine.status
local coroutine_close = coroutine.close
local warn_func = warn

-- ============================================================
-- 局部化 Java 类（知识库1.1节：避免循环中重复bindClass）
-- ============================================================
local bindClass = luajava.bindClass
local Runtime = bindClass("java.lang.Runtime")
local Handler = bindClass("android.os.Handler")
local Looper = bindClass("android.os.Looper")
local Runnable = bindClass("java.lang.Runnable")

-- 缓存 Runtime 实例（只获取一次，避免重复JNI跨越）
local runtime = Runtime.getRuntime()

-- ============================================================
-- 内部状态（模块级局部变量）
-- ============================================================
local _config = {}
local _state = {}
local _running = false
local _mode = "idle" -- "idle" / "auto" / "manual"
local _co = nil
local _handler = nil
local _runnable = nil

-- 内存监测历史
local _monitor_history = table_create(10, 0)
local _monitor_pointer = 1
local _leak_warning = false

-- ============================================================
-- 内部工具函数
-- ============================================================

-- 精度控制：保留两位小数（比 string.format 快 5~10 倍）
local function round2(n)
    return math_floor(n * 100) / 100
end

-- 获取当前 Lua 内存占用（KB）
local function get_lua_mem_kb()
    return round2(collectgarbage_func("count"))
end

-- 获取 Java 堆内存使用（MB）
local function get_java_mem_mb()
    local used = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
    return round2(used)
end

-- 获取总内存（Lua + Java，单位MB）
local function get_total_mem_mb()
    local lua_mb = collectgarbage_func("count") / 1024
    local java_mb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
    return round2(lua_mb + java_mb)
end

-- ============================================================
-- GC 执行函数（Lua GC + Java GC + JNI清理）
-- ============================================================

-- 完整GC：Lua全量回收 + JNI清理 + Java GC
local function do_full_gc()
    collectgarbage_func("collect")
    luajava.clear()
    if _config.java_gc then
        runtime.gc()
    end
end

-- 分步GC：Lua步进回收 + JNI清理
local function do_step_gc(step_size)
    collectgarbage_func("step", step_size)
    luajava.clear()
end

-- 切换分代GC模式
local function enable_generational_gc()
    local ok, err = pcall_func(function()
        collectgarbage_func("generational")
    end)
    if not ok then
        warn_func("[Memory] 分代GC启用失败 Generation GC enabling failed: " .. tostring_func(err))
    end
end

-- 切换增量GC模式
local function enable_incremental_gc(pause_val, stepmul_val)
    collectgarbage_func("incremental")
    collectgarbage_func("setpause", math_floor(pause_val))
    collectgarbage_func("setstepmul", math_floor(stepmul_val))
end

-- ============================================================
-- 参数验证
-- ============================================================
local function validate_param(value, default_val, min_val, max_val, name)
    if value == nil then
        return default_val
    end
    local num = tonumber_func(value)
    if num == nil or num ~= num then
        warn_func("[Memory] 参数 " .. name .. " 非法，使用默认值 : " .. tostring_func(default_val))
        return default_val
    end
    return math_max(min_val, math_min(num, max_val))
end

-- ============================================================
-- 动态GC步长计算
-- ============================================================
local function calc_adaptive_stepmul(current_mem, max_mem)
    local ratio = current_mem / max_mem
    local base = _config.stepmul or 180
    local adaptive = math_floor(base * (1 + ratio * 2))
    return math_max(100, math_min(adaptive, 500))
end

local function calc_adaptive_pause(current_mem, max_mem)
    local ratio = current_mem / max_mem
    local base = _config.pause or 80
    local adaptive = math_floor(base * (1 - ratio * 0.5))
    return math_max(50, math_min(adaptive, 200))
end

-- ============================================================
-- 内存检查核心逻辑（每次tick执行一次）
-- ============================================================
local function memory_check()
    if not _running then
        return
    end

    local current_mem_kb = get_lua_mem_kb()
    local java_mem_mb = get_java_mem_mb()
    local start_time = os_clock()

    -- 更新状态
    _state.current_mem = current_mem_kb
    _state.last_check_time = os_time()

    -- 动态GC参数（如果开启了自适应模式）
    local cur_stepmul = _config.stepmul
    local cur_pause = _config.pause

    if _config.dynamic_stepmul then
        cur_stepmul = calc_adaptive_stepmul(current_mem_kb, _config.peak_memory / 1024)
        collectgarbage_func("setstepmul", cur_stepmul)
    end

    if _config.dynamic_pause then
        cur_pause = calc_adaptive_pause(current_mem_kb, _config.peak_memory / 1024)
        collectgarbage_func("setpause", cur_pause)
    end

    -- 手动处理状态表
    local manual = {
        full_gc = false,
        cooling = false,
        step_gc = false,
        leak_detected = false,
        pressure_warning = false,
        delay_time = _config.interval or 1000,
        delta = current_mem_kb - (_state.stored_mem or current_mem_kb),
    }

    -- 压力判断
    local mem_ratio = current_mem_kb / (_config.peak_memory / 1024)

    if mem_ratio >= _config.pressure_threshold then
        manual.full_gc = true
        manual.pressure_warning = true
        manual.delay_time = 500
        do_full_gc()
        enable_incremental_gc(cur_pause, cur_stepmul)
    elseif current_mem_kb >= _config.alert then
        manual.full_gc = true
        manual.delay_time = 800
        do_full_gc()
    else
        _monitor_history[_monitor_pointer] = current_mem_kb
        _monitor_pointer = (_monitor_pointer % 10) + 1

        local hist_count = 0
        for i = 1, 10 do
            if _monitor_history[i] and _monitor_history[i] > 0 then
                hist_count = hist_count + 1
            end
        end

        if hist_count >= 5 then
            local sum = 0
            local valid_count = 0
            for i = 2, 10 do
                if _monitor_history[i] and _monitor_history[i] > 0 and
                    _monitor_history[i - 1] and _monitor_history[i - 1] > 0 then
                    sum = sum + (_monitor_history[i] - _monitor_history[i - 1])
                    valid_count = valid_count + 1
                end
            end

            if valid_count > 0 then
                sum = sum / valid_count
            end

            if sum > 50 then
                manual.leak_detected = true
                manual.full_gc = true
                manual.delay_time = 500
                do_full_gc()
                if not _leak_warning then
                    _leak_warning = true
                    warn_func("[Memory] 检测到内存持续增长，可能存在泄漏! Continuous memory growth detected, possible leak!")
                end
            elseif sum < -20 then
                _leak_warning = false
                if current_mem_kb < _config.alert and
                    os_time() - (_state.last_gc_time or 0) > _config.cooling_time then
                    manual.cooling = true
                    manual.delay_time = (_config.interval or 1000) + 500
                elseif current_mem_kb <= (_state.stored_mem or current_mem_kb) then
                    manual.step_gc = true
                    local step_size = math_floor(current_mem_kb * math_max(1, mem_ratio))
                    do_step_gc(step_size)
                    manual.delay_time = (_config.interval or 1000) + 300
                end
            end
        end
    end

    luajava.clear()

    if _config.debug then
        local elapsed = round2((os_clock() - start_time) * 1000)
        print("──────")
        print("[调试] Lua内存 Lua memory: " .. current_mem_kb .. "KB")
        print("[调试] Java内存 Java memory: " .. java_mem_mb .. "MB")
        print("[调试] 趋势 trend: " .. (manual.delta > 0 and "↑" or manual.delta < 0 and "↓" or "=") .. math_abs(manual.delta) .. "KB")
        print("[调试] 耗时 Time consuming: " .. elapsed .. "ms")
        print("[调试] 全量GC Full GC: " .. tostring_func(manual.full_gc))
        print("[调试] 分步GC Step-by-step GC: " .. tostring_func(manual.step_gc))
        print("[调试] 泄漏检测 Leak Detection: " .. tostring_func(manual.leak_detected))
        print("[调试] 压力警告 Pressure Warning: " .. tostring_func(manual.pressure_warning))
        print("[调试] 下次间隔 Next Interval: " .. manual.delay_time .. "ms")
    end

    _state.stored_mem = current_mem_kb
    _state.last_gc_time = os_time()

    return manual.delay_time
end

-- ============================================================
-- 自动模式：Handler + 协程（无线程）
-- ============================================================
local function auto_loop()
    while _running do
        local delay = memory_check()
        if not _running then
            break
        end
        coroutine_yield(delay or (_config.interval or 1000))
    end
end

-- ============================================================
-- 公开API
-- ============================================================

function M.start(cfg)
    if _running then
        warn_func("[Memory] 模块已在运行中，请先调用 stop() Module is already running, call stop () first")
        return false
    end

    cfg = cfg or {}

    _config.alert = validate_param(cfg.alert, 2000, 100, 9007199254740991, "溢出警告KB Overflow Warning KB")
    _config.dynamic_pause = (type_func(cfg.pause) == "boolean" and cfg.pause)
    _config.dynamic_stepmul = (type_func(cfg.stepmul) == "boolean" and cfg.stepmul)
    _config.pause = _config.dynamic_pause and 80 or validate_param(cfg.pause, 80, 50, 200, "GC触发阈值 GC Trigger Threshold")
    _config.stepmul = _config.dynamic_stepmul and 180 or validate_param(cfg.stepmul, 180, 100, 500, "GC步长速度 GC step speed")
    _config.cooling_time = validate_param(cfg.cooling_time, 2, 0, 1000, "冷却时间 Cooling time")
    _config.peak_memory = validate_param(cfg.peak_memory, 2936210, 100, 9007199254740991, "Lua峰值内存 Lua Peak Memory")
    _config.pressure_threshold = validate_param(cfg.pressure_threshold, 0.8, 0, 1, "压力阀值 Pressure threshold")
    _config.debug = cfg.debug and true or false
    _config.interval = validate_param(cfg.interval, 1000, 200, 60000, "检查间隔 Inspection interval")
    _config.java_gc = cfg.java_gc ~= false

    _state = {
        current_mem = get_lua_mem_kb(),
        stored_mem = get_lua_mem_kb(),
        last_gc_time = os_time(),
        last_check_time = os_time(),
    }

    _running = true
    _mode = "auto"

    enable_generational_gc()

    if not _config.dynamic_pause then
        collectgarbage_func("setpause", math_floor(_config.pause))
    end
    if not _config.dynamic_stepmul then
        collectgarbage_func("setstepmul", math_floor(_config.stepmul))
    end

    _co = coroutine_create(function()
        local ok, err = pcall_func(auto_loop)
        if not ok then
            warn_func("[Memory] 协程异常 Coroutine exception: " .. tostring_func(err))
        end
    end)

    _handler = Handler(Looper.getMainLooper())

    _runnable = Runnable {
        run = function()
            if not _running then
                return
            end

            local status, delay = coroutine_resume(_co)

            if status and coroutine_status(_co) ~= "dead" then
                _handler.postDelayed(_runnable, delay or _config.interval)
            else
                if not status then
                    warn_func("[Memory] 协程执行出错 Coroutine execution error: " .. tostring_func(delay))
                end
                M.stop()
            end
        end
    }

    _handler.post(_runnable)

    if _config.debug then
        print("[Memory] 已启动 Started")
        print("[Memory] 检查间隔 Inspection interval: " .. _config.interval .. "ms")
        print("[Memory] Java GC: " .. tostring_func(_config.java_gc))
    end

    return true
end

function M.start_manual(cfg)
    if _running then
        warn_func("[Memory] 模块已在运行中，请先调用 stop() Module is already running, call stop () first")
        return false
    end

    cfg = cfg or {}

    _config.alert = validate_param(cfg.alert, 2000, 100, 9007199254740991, "溢出警告KB Overflow Warning KB")
    _config.dynamic_pause = (type_func(cfg.pause) == "boolean" and cfg.pause)
    _config.dynamic_stepmul = (type_func(cfg.stepmul) == "boolean" and cfg.stepmul)
    _config.pause = _config.dynamic_pause and 80 or validate_param(cfg.pause, 80, 50, 200, "GC触发阈值 GC Trigger Threshold")
    _config.stepmul = _config.dynamic_stepmul and 180 or validate_param(cfg.stepmul, 180, 100, 500, "GC步长速度 GC step speed")
    _config.cooling_time = validate_param(cfg.cooling_time, 2, 0, 1000, "冷却时间 Cooling time")
    _config.peak_memory = validate_param(cfg.peak_memory, 2936210, 100, 9007199254740991, "Lua峰值内存 Lua Peak Memory")
    _config.pressure_threshold = validate_param(cfg.pressure_threshold, 0.8, 0, 1, "压力阀值 Pressure threshold")
    _config.debug = cfg.debug and true or false
    _config.interval = validate_param(cfg.interval, 1000, 200, 60000, "检查间隔 Inspection interval")
    _config.java_gc = cfg.java_gc ~= false

    _state = {
        current_mem = get_lua_mem_kb(),
        stored_mem = get_lua_mem_kb(),
        last_gc_time = os_time(),
        last_check_time = os_time(),
    }

    _running = true
    _mode = "manual"

    enable_generational_gc()

    if not _config.dynamic_pause then
        collectgarbage_func("setpause", math_floor(_config.pause))
    end
    if not _config.dynamic_stepmul then
        collectgarbage_func("setstepmul", math_floor(_config.stepmul))
    end

    if _config.debug then
        print("[Memory] 已启动（手动模式，请定时调用 tick()）Started (manual mode, please call tick () regularly)")
    end

    return true
end

function M.tick()
    if not _running or _mode ~= "manual" then
        return
    end
    memory_check()
end

function M.stop()
    _running = false

    if _handler and _runnable then
        _handler.removeCallbacks(_runnable)
    end

    if _co and coroutine_status(_co) ~= "dead" then
        coroutine_close(_co)
    end
    _co = nil

    if _runnable then
        _runnable = nil
    end
    if _handler then
        _handler = nil
    end
    luajava.clear()

    _mode = "idle"

    if _config.debug then
        print("[Memory] 模块已停止 Module stopped")
    end
end

function M.monitoring()
    local current = get_lua_mem_kb()

    _monitor_history[_monitor_pointer] = current
    _monitor_pointer = (_monitor_pointer % 10) + 1

    local hist_count = 0
    for i = 1, 10 do
        if _monitor_history[i] and _monitor_history[i] > 0 then
            hist_count = hist_count + 1
        end
    end

    if hist_count >= 5 then
        local sum = 0
        local valid_count = 0
        for i = 2, 10 do
            if _monitor_history[i] and _monitor_history[i] > 0 and
                _monitor_history[i - 1] and _monitor_history[i - 1] > 0 then
                sum = sum + (_monitor_history[i] - _monitor_history[i - 1])
                valid_count = valid_count + 1
            end
        end

        if valid_count > 0 then
            sum = sum / valid_count
        end

        if sum > 50 and not _leak_warning then
            _leak_warning = true
            warn_func("[Memory] 检测到内存持续增长，可能存在泄漏!Continuous memory growth detected, possible leak!")
        elseif sum < -20 then
            _leak_warning = false
        end
    end

    return current, _leak_warning, get_java_mem_mb()
end

function M.force_gc()
    do_full_gc()
end

function M.get_status()
    return {
        lua_kb = get_lua_mem_kb(),
        java_mb = get_java_mem_mb(),
        total_mb = get_total_mem_mb(),
        running = _running,
        mode = _mode,
        leak_warning = _leak_warning,
    }
end

function M.on_destroy()
    M.stop()
    luajava.clear()
    collectgarbage_func("collect")
    runtime.gc()
end

function M.help()
    local help = {}
    table.insert(help, [[>>插件说明
本模块提供了动态内存处理(详细包括自动GC处理、手动式智能处理，分步智能处理，Java GC清理，luajava.clear()清理，分代GC等)，采用Handler+协程替代真实Java Thread，避免线程资源耗尽。进行配置操作后，会自动重启一次内存处理模块，拥有智能的配置操作，如参数为空则进行默认的处理方式。在内存处理中会记录下上一次处理时的内存与本次处理内存对比，如上一次处理与本次处理相同，会进行无延迟的处理内存。当然，正常处理时我们也会考虑到CPU的开销，尽可能减少CPU开销，如使用table储存相关参数，进行手动式智能处理延迟等。
插件名称[动态内存模块-Memory]
模块作者[屿缘尽]
模块状态[完善-未停止更新]
模块版本[2.0.0]
适配版本[Lua 5.5]
交流群聊[785474606]
联系作者[light.partridge.tgof@mask.me(常用)/xmabyss@gmail.com]
★ 开源说明：
本模块已在GitHub开源
仓库地址: https://github.com/YuYuanJinn/LuaMemory
我的博客：YuYuanJinn.github.io
开源协议: Apache License 2.0
欢迎Star、Fork、提交Issue和Pull Request
★ 使用方法
如：
local Abyss = require "memory"
★ 函数名[start]
Abyss.start({自定义溢出警告KB,GC触发阈值,GC步长速度,冷却时间,Lua峰值内存,压力阀值,调试模式,检查间隔,Java GC开关})
使用提示
参数以table形式传入，其中GC触发阈值、GC步长速度可以调整为动态自适应模式，调试模式和Java GC开关为布尔值类型
"alert"意思为内存上限，当达到这个数值后就会进行"手动式智能处理"
如果参数都不填，则使用本模块提供的默认参数
参数提示
alert，尽量不要调太小，会增加手动式智能处理和CPU开销，但也不能太高，尽量在应用极度卡顿或OOM内存溢出情况的大小(不可预)
pause，调整GC的触发阈值，越低越快自动处理内存(会增加GC频率和CPU开销)，越高越慢(积累到一定程度)自动处理内存(CPU开销减少)，模块给予动态自适应生成触发阀值，如想打开此模式填true布尔值(非字符串)
stepmul，调整GC的步长速度，越大越快处理内存，越小越平滑处理(无法及时回收内存)，模块给予动态自适应生成步长速度，如想打开此模式填true布尔值(非字符串)
cooling_time，调整冷却时间(秒)，内存低于阈值且不在冷却期时不触发GC
peak_memory，Lua峰值内存，则为最大内存，大到Lua出现OOM内存溢出或内存不足前一个最大内存(默认为2936210KB)(需填KB单位)
pressure_threshold，压力阀值，则为内存占用总量是否到达最大内存的0.X
debug，调试模式，可以查看内存处理是否正常，如想打开此模式填true布尔值(非字符串)
interval，检查间隔(毫秒)，每次内存检查的时间间隔，最小200，最大60000，默认1000
java_gc，Java GC开关，是否触发Java侧垃圾回收，默认true开启
正确使用示例
Abyss.start({alert=2000,pause=90,stepmul=200,cooling_time=5,peak_memory=2936210,pressure_threshold=0.8,debug=false,interval=1500,java_gc=true})
解释:自定义溢出警告KB为2000，GC的触发阈值为90，GC步长速度为200，冷却时间为5，Lua峰值内存为2936210，压力阀值为0.8，调试模式不开启，检查间隔1500毫秒，Java GC开启
Abyss.start({alert=2000,pause=true,stepmul=true,cooling_time=5,peak_memory=2936210,pressure_threshold=0.8,debug=false})
解释:自定义溢出警告KB为2000，GC的触发阈值为动态自适应，GC步长速度为动态自适应，冷却时间为5，Lua峰值内存为2936210，压力阀值为0.8，调试模式不开启
Abyss.start({alert=2000,pause=90,stepmul=200})
解释:自定义溢出警告KB为2000，GC的触发阈值为90，GC步长速度为200，其余参数使用默认值
Abyss.start()
解释:默认模式，调试模式不开启
注意事项
默认模式为: alert=2000,pause=80,stepmul=180,cooling_time=2,peak_memory=2936210,pressure_threshold=0.8,debug=false,interval=1000,java_gc=true (偏性能模式)
★ 函数名[start_manual]
Abyss.start_manual({自定义溢出警告KB,GC触发阈值,GC步长速度,冷却时间,Lua峰值内存,压力阀值,调试模式,检查间隔,Java GC开关})
使用提示
此函数为手动模式启动，参数与start相同，启动后需要用户自行在定时器中调用tick()
正确使用示例
Abyss.start_manual({alert=2000,pause=90,stepmul=200})
解释:手动模式启动，参数同上
注意事项
启动后必须在定时器或循环中手动调用Abyss.tick()才会执行内存检查
★ 函数名[tick]
Abyss.tick()
使用提示
此函数为手动模式下的单次内存检查，仅在手动模式(start_manual)启动后可用
正确使用示例
Abyss.tick()
解释:执行一次内存检查
注意事项
严禁在帧循环或高频定时器中调用，建议放在500ms以上的定时器中
★ 函数名[stop]
Abyss.stop()
使用提示
此函数为停止内存管理模块，会清理所有资源(协程、Handler、JNI引用)
正确使用示例
Abyss.stop()
解释:停止模块并清理资源
★ 函数名[monitoring]
Abyss.monitoring()
使用提示
此函数为监测内存占用，以及监测内存泄露，独立于主模块可随时调用
正确使用示例
local mem, leak, java_mb = Abyss.monitoring()
print("内存: "..mem.."KB, Java: "..java_mb.."MB, 泄露: "..tostring(leak))
解释:返回当前Lua内存(KB)、是否泄漏、Java内存(MB)
注意事项
可以使用三个变量来储存返回的数据
★ 函数名[force_gc]
Abyss.force_gc()
使用提示
此函数为手动触发完整GC(Lua全量回收 + luajava.clear() + Java GC)
正确使用示例
Abyss.force_gc()
解释:立即执行一次完整的垃圾回收
★ 函数名[get_status]
Abyss.get_status()
使用提示
此函数为获取当前内存状态信息
正确使用示例
local status = Abyss.get_status()
print("Lua内存: "..status.lua_kb.."KB")
print("Java内存: "..status.java_mb.."MB")
print("总内存: "..status.total_mb.."MB")
print("运行状态: "..tostring(status.running))
print("模式: "..status.mode)
★ 函数名[on_destroy]
Abyss.on_destroy()
使用提示
此函数为页面销毁时的标准清理，应在全局onDestroy函数中调用
正确使用示例
function onDestroy()
    Abyss.on_destroy()
end
解释:停止模块并执行完整清理
★ 函数名[help]
Abyss.help()
使用提示
此函数是提供帮助用的
正确使用示例
print(Abyss.help())
解释:打印出帮助内容
English：
>>Plugin Description
This module provides dynamic memory processing (including automatic GC processing, manual intelligent processing, step-by-step intelligent processing, Java GC cleaning, lua. clear() cleaning, generational GC, etc.), using Handler+coroutine instead of real Java Thread to avoid thread resource depletion. After performing configuration operations, the memory processing module will automatically restart once, with intelligent configuration operations. If the parameter is empty, the default processing method will be used. In memory processing, the comparison between the memory from the previous processing and the current processing will be recorded. If the previous processing is the same as the current processing, the memory will be processed without delay. Of course, during normal processing, we also take into account the CPU overhead and try to minimize it as much as possible, such as using tables to store relevant parameters and performing manual intelligent processing delays.
Plugin Name [Dynamic Memory Module Memory]
Module author [End of the Island]
Module status [complete - not stopped updating]
Module version [2.0.0]
Adaptation version [Lua 5.5]
Communication group chat [785474606]
Contact the author[ light.partridge.tgof@mask.me (Commonly used)/ xmabyss@gmail.com ]
★ Open source explanation:
This module has been open sourced on GitHub
Warehouse address: https://github.com/YuYuanJinn/LuaMemory
My Blog: YuYuanJinn.github.io
Open source license: Apache License 2.0
Welcome Star, Fork, Submit Issue and Pull Request
★ Instructions for use
For example:
local Abyss = require "memory"
★ Function name [start]
Abyss.start ({Custom overflow warning KB, GC trigger threshold, GC step speed, cooldown time, Lua peak memory, pressure threshold, debug mode, check interval, Java GC switch})
Usage tips
The parameters are passed in the form of a table, where the GC trigger threshold and GC step speed can be adjusted to dynamic adaptive mode, debugging mode, and Java GC switch are Boolean values
Alert "means the maximum memory limit, and when this value is reached," manual intelligent processing "will be performed
If no parameters are filled in, use the default parameters provided by this module
Parameter Prompt
alert， Try not to adjust it too small, as it will increase manual intelligent processing and CPU overhead, but also not too high. Try to adjust it to a size that is extremely laggy or OOM memory overflow in the application (unpredictable)
pause， Adjust the trigger threshold of GC, the lower the threshold, the faster it will automatically process memory (which will increase GC frequency and CPU overhead), and the higher the threshold, the slower it will be (accumulated to a certain extent). The module will automatically process memory (reduce CPU overhead), and dynamically adaptively generate trigger thresholds. If you want to turn on this mode, fill in the true cloth value (non string)
stepmul， Adjust the step speed of GC, the larger it is, the faster it processes memory, and the smaller it is, the smoother it processes (memory cannot be reclaimed in a timely manner). The module provides dynamic adaptive generation of step speed. If you want to turn on this mode, fill in the true Boolean value (non string)
cooling_time， Adjust cooldown time (seconds), do not trigger GC when memory is below threshold and not in cooldown period
Peak_memory, the peak memory of Lua, is the maximum memory, which is the maximum memory before Lua experiences OOM memory overflow or insufficient memory (default is 2936210KB) (in KB units)
pressure_threshold， The pressure threshold is whether the total memory usage has reached 0. X of the maximum memory
debug， Debugging mode, you can check whether the memory processing is normal. If you want to open this mode, fill in the true boolean value (non string)
interval， Check interval (milliseconds), time interval for each memory check, minimum 200, maximum 60000, default 1000
Java_gc, Java GC switch, whether to trigger Java side garbage collection, default to true enabled
Correct use of examples
Abyss.start({alert=2000,pause=90,stepmul=200,cooling_time=5,peak_memory=2936210,pressure_threshold=0.8,debug=false,interval=1500,java_gc=true})
Explanation: Custom overflow warning KB is 2000, GC trigger threshold is 90, GC step speed is 200, cooldown time is 5, Lua peak memory is 2936210, pressure threshold is 0.8, debug mode is not turned on, check interval is 1500 milliseconds, Java GC is turned on
Abyss.start({alert=2000,pause=true,stepmul=true,cooling_time=5,peak_memory=2936210,pressure_threshold=0.8,debug=false})
Explanation: Custom overflow warning KB is 2000, GC trigger threshold is dynamically adaptive, GC step speed is dynamically adaptive, cooling time is 5, Lua peak memory is 2936210, pressure threshold is 0.8, debugging mode is not enabled
Abyss.start({alert=2000,pause=90,stepmul=200})
Explanation: Custom overflow warning KB is 2000, GC trigger threshold is 90, GC step speed is 200, and other parameters use default values
Abyss.start()
Explanation: Default mode, debug mode is not enabled
Precautions
The default mode is: alert=2000,pause=80,stepmul=180,cooling_time=2,peak_memory=2936210,pressure_threshold=0.8,debug=false,interval=1000,java_gc=true ( Performance oriented mode)
★ Function name [start_manual]
Abyss.start_manual ({Custom overflow warning KB, GC trigger threshold, GC step speed, cooldown time, Lua peak memory, pressure threshold, debug mode, check interval, Java GC switch})
Usage tips
This function is started in manual mode with the same parameters as start. After starting, the user needs to call ticke() in the timer
Correct use of examples
Abyss.start_manual({alert=2000,pause=90,stepmul=200})
Explanation: Manual mode starts, with the same parameters as above
Precautions
After startup, Abyss. tick() must be manually called in a timer or loop to perform memory checks
★ Function name [tick]
Abyss.tick()
Usage tips
This function is a single memory check in manual mode and is only available after starting manual mode (start_manual)
Correct use of examples
Abyss.tick()
Explanation: Perform a memory check once
Precautions
It is strictly prohibited to call it in frame cycle or high frequency timer, and it is recommended to put it in the timer above 500ms
★ Function name [stop]
Abyss.stop()
Usage tips
This function stops the memory management module and clears all resources (coroutines, Handler, JNI references)
Correct use of examples
Abyss.stop()
Explanation: Stop the module and clean up resources
★ Function name [monitoring]
Abyss.monitoring()
Usage tips
This function is used to monitor memory usage and memory leaks, and can be called at any time independently of the main module
Correct use of examples
local mem, leak, java_mb = Abyss.monitoring()
Print ("Memory:".. Mem.. "KB, Java:".. Java_mb. "MB, Leakage:".. String (leak))
Explanation: Returns the current Lua memory (KB), whether there is a leak, and Java memory (MB)
Precautions
Three variables can be used to store the returned data
★ Function name [force_gc]
Abyss.force_gc()
Usage tips
This function manually triggers the complete GC (Lua full recycle+Lua. clear()+Java GC)
Correct use of examples
Abyss.force_gc()
Explanation: Perform a complete garbage collection immediately
★ Function Name [get_stus]
Abyss.get_status()
Usage tips
This function is used to obtain the current memory status information
Correct use of examples
local status = Abyss.get_status()
Print ("Lua Memory:".. Status. lua_kb.. "KB")
Print ("Java Memory:".. Status. java. mb. "MB")
Print ("Total Memory:".. Status. total. mb. "MB")
Print ("Running Status:".. Staging (status. running))
Print ("Mode:".. Status. mode)
★ Function name [o'destroy]
Abyss.on_destroy()
Usage tips
This function is the standard cleanup when the page is destroyed. It should be called in the global onDestroy function
Correct use of examples
function onDestroy()
Abyss.on_destroy()
end
Explanation: Stop the module and perform a complete cleanup
★ Function Name [help]
Abyss.help()
Usage tips
This function is designed to provide assistance
Correct use of examples
print(Abyss.help())
Explanation: Print out the help content
]])
    return table.concat(help)
end

return M