# LuaFabric Studio

# 更新日志

## 26.08.18（后续）
- 新增 `libmmkv.so`（MMKV v2.4.1 高性能键值存储）Lua 绑定，支持类型化读写、过期时间、多实例
- 新增 `libzstd.so`（Zstandard 1.6.0 压缩）Lua 绑定，支持压缩/解压/流式接口
- 新增 README「Lua 库接口文档」章节，涵盖 mmkv / zstd / aes / sodium / cjson / ffi

## 26.08.18
- 软件更名：LuaForge Studio → **LuaFabric Studio**
- 包名迁移：
  - 应用包名 `com.luaforge.studio` → `com.luafabric.studio.falling`
  - 工具库包名 `com.luaforge.studio.utils.*` → `muling.views.tool.utils.*`
  - 组件包名 → `com.luafabric.studio.falling.core.widget.textfield`
- 存储路径变更：`/storage/emulated/0/LuaForge-Studio/` → `/storage/emulated/0/LuaFabric-Studio/`
  - 注意：**不提供自动迁移**，旧项目需手动移动到新路径
- 修复 RecyclerView 适配器只能添加 8 项的问题（Lua 表通过 `push()` 传递）
- 修复全新安装时无存储权限导致的日志写入报错（日志文件改为应用私有目录）
- 修复 `import` 模块缺失问题（Splash 启动时幂等解包 lua 运行库）
- 版本号切换为年份命名：`versionName 26.08.18` / `versionCode 260818`

## 1.6.0
- 新增 `libdecrypt.so`，支持 Lua 加密脚本自动解密加载
  - 提供 `decrypt.loadfile()` / `decrypt.dofile()` / `decrypt.load()` 接口
  - 兼容 `.luae` 加密格式及普通 `.lua` / `.luac` 文件
  - 复用 `ldump.c` / `lundump.c` 保护配置，加解密算法一致

## 1.5.0
- 支持 Maven 依赖自动下载与打包（JAR 类型）

## 1.4.0
- 优化补全数据加载机制：检测到版本变更时自动重建补全数据缓存，确保代码补全与新版本一致
- 优化应用启动流程

## 1.3.0
- 增加 `com.luafabric.studio.falling.core.widget.textfield.MaterialTextField` 组件

## 1.2.1
- 增加 `onActivityReenter` 回调函数

# Lua 扩展语法

## 1. 概述

本文档基于 Lua 5.5 的深度魔改版本，全面支持现代编程语言的语法特性。主要特性包括：

- 增强型语法结构：支持 try-catch-finally 异常处理、switch-case 多分支、defer 延迟执行、when 条件分支、continue 跳转。
- 现代化操作符：三元运算符、复合赋值、可选链、空值合并、管道运算符。
- 函数式编程增强：Lambda 表达式（匿名函数）简洁语法。
- 完整的特性组合：所有扩展语法可以无缝嵌套使用。

---

## 2. 词法扩展

### 2.1 新增操作符

| 符号 | 等价于/描述 | 说明 |
|------|------------|------|
| `? :` | if-else 三元 | 条件运算符 `(cond) ? true_expr : false_expr` |
| `?.` | 安全访问 | 可选链操作符，避免 nil 报错 |
| `??` | 空值合并 | 左侧为 nil 时返回右侧值 |
| `|>` | 管道操作符 | 将值传入函数 `value |> func` |
| `!` | `not` | 逻辑非 |
| `!=` | `~=` | 不等于 |
| `&&` | `and` | 逻辑与 |
| `||` | `or` | 逻辑或 |
| `$` | `local` | 局部变量声明缩写 |
| `@` | `::` | 标签声明（用于 goto） |

### 2.2 复合赋值

支持完整的 C 风格复合赋值操作符：

| 操作符 | 示例 | 等价于 |
|--------|------|--------|
| `+=` | `a += 5` | `a = a + 5` |
| `-=` | `a -= 3` | `a = a - 3` |
| `*=` | `a *= 2` | `a = a * 2` |
| `/=` | `a /= 4` | `a = a / 4` |
| `//=` | `a //= 2` | `a = a // 2` |
| `%=` | `a %= 3` | `a = a % 3` |
| `^=` | `a ^= 2` | `a = a ^ 2` |
| `..=` | `s ..= "x"` | `s = s .. "x"` |
| `&=` | `bits &= mask` | `bits = bits & mask` |
| `|=` | `flags |= 0x01` | `flags = flags | 0x01` |
| `<<=` | `a <<= 2` | `a = a << 2` |
| `>>=` | `a >>= 1` | `a = a >> 1` |

**示例代码：**
```lua
$ counter = 10
counter += 5      -- 15
counter *= 2      -- 30

$ message = "Hello"
message ..= " World"  -- "Hello World"

$ flags = 0b1100
flags &= 0b1010   -- 0b1000
```

---

## 3. 语法扩展

### 3.1 三元运算符

简洁的条件表达式，支持嵌套使用。

**语法格式：**
```lua
(condition) ? true_expression : false_expression
```

**示例代码：**
```lua
$ age = 18
$ status = (age >= 18) ? "adult" : "minor"
print(status)  -- adult

-- 嵌套使用
$ score = 85
$ grade = score >= 90 ? "A" : score >= 80 ? "B" : "C"
print(grade)  -- B
```

---

### 3.2 可选链

安全访问嵌套对象属性，避免因中间值为 nil 而抛出错误。

**语法格式：**
```lua
obj?.field          -- 安全访问字段
obj?.[index]        -- 安全访问数组元素
obj?.method?()      -- 安全调用方法
```

**示例代码：**
```lua
$ user = {
  profile = {
    name = "Alice",
    settings = { theme = "dark" }
  }
}

print(user?.profile?.name)          -- Alice
print(user?.profile?.address?.city) -- nil（不会报错）
print(user?.nonexist?.field)        -- nil

-- 与空值合并结合使用
$ city = user?.profile?.address?.city ?? "unknown"
print(city)  -- unknown
```

---

### 3.3 空值合并

当左侧值为 nil 时返回右侧值，否则返回左侧值。

**语法格式：**
```lua
value ?? default_value
```

**示例代码：**
```lua
$ name = nil
$ display = name ?? "Anonymous"  -- "Anonymous"

$ count = 0
$ result = count ?? 100          -- 0（0 不是 nil）

$ a = nil; $ b = nil; $ c = 42
$ value = a ?? b ?? c ?? 0       -- 42
```

---

### 3.4 Lambda 表达式

简洁的匿名函数定义语法，支持多种写法。

**语法格式：**
```lua
\参数列表 -> 表达式                 -- 单表达式自动返回
\参数列表 => 语句块                 -- 多语句需显式 return
lambda 参数列表 -> 表达式           -- 完整写法
```

**示例代码：**
```lua
-- 基础用法
$ add = \x, y -> x + y
print(add(3, 5))  -- 8

$ square = \x -> x * x
print(square(4))  -- 16

-- 多语句块
$ complex = \x, y -> do
  $ temp = x + y
  return temp * 2
end

-- 闭包
$ factor = 3
$ multiplier = \x -> x * factor
print(multiplier(5))  -- 15

-- 高阶函数
$ make_adder = \n -> \x -> x + n
$ add5 = make_adder(5)
print(add5(10))  -- 15

-- 与管道结合
$ numbers = {1, 2, 3, 4, 5}
$ doubled = map(numbers, \x -> x * 2)
```

---

### 3.5 管道运算符

将值从左到右传递通过一系列函数，提高代码可读性。

**语法格式：**
```lua
value |> function1 |> function2 |> function3
```

**示例代码：**
```lua
$ double = \x -> x * 2
$ add1 = \x -> x + 1
$ square = \x -> x * x

$ result = 5 |> double |> add1 |> square
print(result)  -- ((5*2)+1)^2 = 121

-- 与三元结合
$ value = (x > 0) ? x : 0 |> double |> add1

-- 与可选链结合
$ user = { score = 80 }
$ level = user?.score |> \s -> s >= 60 ? "pass" : "fail"
```

---

### 3.6 Try-Catch-Finally

完整的异常处理机制，支持错误捕获和资源清理。

**语法格式：**
```lua
try
  -- 可能抛出错误的代码
  error("something wrong")
catch (error_variable)
  -- 错误处理代码
finally
  -- 无论是否出错都会执行的清理代码
end
```

**示例代码：**
```lua
-- 基础用法
try
  $ file = io.open("data.txt", "r")
  $ content = file:read("*a")
  print(content)
catch (err)
  print("Error reading file:", err)
finally
  if file then file:close() end
end

-- 嵌套使用
try
  print("Outer try")
  try
    error("inner error")
  catch (e)
    print("Inner catch:", e)
    error("rethrown")
  finally
    print("Inner finally")
  end
catch (e)
  print("Outer catch:", e)
finally
  print("Outer finally")
end

-- 与 return 结合
function test()
  try
    return "from try"
  catch (e)
    return "from catch"
  finally
    print("finally runs before return")
  end
end
```

---

### 3.7 Switch-Case 语句

多分支选择结构，支持多值匹配。

**语法格式：**
```lua
switch expression do
  case value1 then
    -- 代码块
  case value2, value3 then
    -- 多值匹配
  default
    -- 默认分支
end
```

**示例代码：**
```lua
$ command = "start"

switch command do
  case "start", "run" then
    print("Starting...")
    -- 执行启动逻辑
  case "stop", "halt" then
    print("Stopping...")
  case "restart" then
    print("Restarting...")
  default
    print("Unknown command:", command)
end

-- 数值匹配
$ score = 85
switch math.floor(score / 10) do
  case 9, 10 then
    print("Grade A")
  case 8 then
    print("Grade B")
  case 7 then
    print("Grade C")
  default
    print("Grade D")
end
```

---

### 3.8 Defer 语句

延迟执行，在作用域结束时自动运行，常用于资源释放。

**语法格式：**
```lua
defer statement end
```

**示例代码：**
```lua
function processFile(filename)
  $ file = io.open(filename, "r")
  defer file:close() end
  
  -- 无论发生什么，file:close() 都会在函数退出前执行
  $ data = file:read("*a")
  if #data == 0 then
    return nil  -- defer 仍会执行
  end
  return process(data)
end

-- 多个 defer 按后进先出顺序执行
function test()
  defer print("first") end
  defer print("second") end
  defer print("third") end
  print("body")
end
-- 输出顺序: body, third, second, first

-- 在块中使用
do
  $ resource = acquire()
  defer release(resource) end
  -- 使用资源
end  -- 退出块时自动释放
```

---

### 3.9 When 语句

简洁的条件分支，类似多路 if-elseif。

**语法格式：**
```lua
when condition1 then
  -- 代码块
case condition2 then
  -- 代码块
else
  -- 默认分支
end
```

**示例代码：**
```lua
$ temperature = 25

when temperature > 30 then
  print("Hot")
case temperature > 20 then
  print("Warm")
case temperature > 10 then
  print("Cool")
else
  print("Cold")
end

-- 与逻辑操作符结合
$ age = 25
$ hasLicense = true

when age >= 18 && hasLicense then
  print("Can drive")
case age >= 18 && !hasLicense then
  print("Need license")
else
  print("Too young")
end
```

---

### 3.10 Continue 语句

跳过当前循环迭代，进入下一次循环。

**语法格式：**
```lua
continue  -- 在 for, while, repeat 循环中使用
```

**示例代码：**
```lua
-- for 循环
for i = 1, 10 do
  if i % 2 == 0 then
    continue  -- 跳过偶数
  end
  print("odd:", i)  -- 输出 1,3,5,7,9
end

-- while 循环
$ i = 0
while i < 10 do
  i = i + 1
  if i == 5 then
    continue  -- 跳过 5
  end
  print(i)  -- 输出 1,2,3,4,6,7,8,9,10
end

-- repeat 循环
$ j = 0
repeat
  j = j + 1
  if j == 3 then
    continue
  end
  print(j)  -- 输出 1,2,4,5
until j >= 5

-- 嵌套循环
for i = 1, 3 do
  for j = 1, 3 do
    if j == 2 then
      continue  -- 只跳过内层循环的当前迭代
    end
    print(i, j)
  end
end
```

---

### 3.11 局部声明缩写

使用 `$` 快速声明局部变量。

**语法格式：**
```lua
$ variable = value
$ var1, var2 = value1, value2
```

**示例代码：**
```lua
$ name = "Lua"
$ x, y = 10, 20
$ result = x + y

-- 与三元结合
$ max = (x > y) ? x : y

-- 在块中使用
do
  $ temp = calculate()
  print(temp)
end
-- temp 在这里不可访问
```

---

### 3.12 标签与 Goto

使用 `@` 声明标签，支持 goto 跳转。

**语法格式：**
```lua
@label@  -- 标签声明
goto label
```

**示例代码：**
```lua
-- 循环模拟
$ count = 1
@start@
print("count:", count)
count = count + 1
if count <= 3 then
  goto start
end

-- 错误处理
$ success, err = pcall(function()
  if some_condition then
    goto error_handler
  end
  -- 正常逻辑
  return
  @error_handler@
  print("Error occurred")
end)
```

---

## 4. 特性组合使用

所有扩展语法可以无缝组合，实现简洁而强大的代码。

### 4.1 综合示例

```lua
-- 复杂数据处理管道
$ processUserData = \data -> do
  try
    $ result = data?.users
      ?.[0]
      ?.profile
      ?.name ?? "anonymous"
      |> upper
      |> \name -> name .. " (" .. (data?.version ?? 1) .. ")"
    
    $ counter = 0
    counter += (result != "anonymous") ? 10 : 0
    
    when counter > 5 then
      print("High priority user")
    case counter > 0 then
      print("Normal user")
    else
      print("Anonymous user")
    end
    
    return result
  catch (e)
    return "error: " .. e
  finally
    print("Processing completed")
  end
end

-- 资源管理
function safeFileOperation(filename, operation)
  $ file = io.open(filename, "r")
  if !file then
    return nil, "Cannot open file"
  end
  defer file:close() end
  
  $ content = file:read("*a")
  $ result = content 
    |> operation 
    |> \r -> r ?? "no result"
  
  return result
end

-- 状态机
$ state = "initial"
while true do
  when state == "initial" then
    print("Initializing...")
    state = "running"
  case state == "running" then
    print("Running...")
    state = (counter++ > 10) ? "finished" : "running"
  case state == "finished" then
    print("Finished")
    break
  else
    print("Unknown state:", state)
    break
  end
end

-- 错误处理与清理
try
  $ conn = createConnection()
  defer conn:close() end
  
  $ data = conn:query("SELECT * FROM users")
  $ processed = data 
    |> filter(\u -> u.age > 18)
    |> map(\u -> {
      name = u.name |> upper,
      adult = true
    })
  
  switch #processed do
    case 0 then
      print("No adult users")
    case 1 then
      print("One adult user:", processed[1].name)
    default
      print("Multiple adult users:", #processed)
  end
catch (err)
  print("Database error:", err)
finally
  print("Database operation completed")
end
```

---

## 5. 完整特性列表

| 特性类别 | 具体特性 | 语法示例 |
|----------|----------|----------|
| 条件表达式 | 三元运算符 | `(a>b) ? a : b` |
| | 空值合并 | `value ?? default` |
| | 可选链 | `obj?.field?.[index]` |
| 赋值操作 | 复合赋值 | `+= -= *= /= //= %= ^= ..= &= |= <<= >>=` |
| 逻辑操作 | 逻辑非 | `!condition` |
| | 不等于 | `a != b` |
| | 逻辑与 | `a && b` |
| | 逻辑或 | `a || b` |
| 函数式编程 | Lambda | `\x,y -> x+y` |
| | 管道 | `value |> func1 |> func2` |
| 异常处理 | Try-Catch-Finally | `try ... catch(e) ... finally ... end` |
| 控制流 | Switch-Case | `switch x do case v: ... default ... end` |
| | When | `when c1 then ... case c2 then ... else ... end` |
| | Continue | `continue` |
| 资源管理 | Defer | `defer cleanup() end` |
| 语法糖 | 局部声明 | `$ var = value` |
| | 标签 | `@label@` |

---

# Lua 库接口文档

所有库均为编译进 APK 的原生模块，通过 `require` 直接加载。

## 1. mmkv —— 高性能键值存储

`require "mmkv"`，基于 [MMKV](https://github.com/Tencent/MMKV) v2.4.1。

| 函数 | 说明 |
|------|------|
| `mmkv.version()` | 返回版本号字符串，如 `"2.4.1"` |
| `mmkv.initialize(rootDir)` | 初始化存储根目录，成功返回 `true`；失败返回 `false, errmsg`。首次读写前必须调用 |
| `mmkv.set(id, key, value[, expireSeconds])` | 写入值。`value` 支持 string / boolean / integer / number；传 `nil` 等价于删除该 key；`expireSeconds` 为过期秒数（可选，大于 0 时启用，到期自动删除） |
| `mmkv.get(id, key)` | 读取值，key 不存在返回 `nil`。根据底层编码自动推断类型 |
| `mmkv.contains(id, key)` | 返回 `boolean`，是否包含该 key |
| `mmkv.remove(id, key)` | 删除该 key，返回 `boolean` |
| `mmkv.clear(id)` | 清空该实例全部数据 |
| `mmkv.count(id)` | 返回该实例的 key 数量 |
| `mmkv.totalSize(id)` | 返回该实例存储文件大小（字节） |
| `mmkv.allKeys(id)` | 返回全部 key 组成的表（数组） |

`id` 为存储实例名（如 `"default"`），不同 `id` 之间数据互相隔离。

```lua
local mmkv = require "mmkv"
mmkv.initialize("/data/data/com.luafabric.studio.falling/files/mmkv")
mmkv.set("default", "name", "Alice", 3600)  -- 1 小时后过期
print(mmkv.get("default", "name"))          -- Alice
```

**类型推断说明**：`get` 依据底层编码推断类型，少数边界情形可能产生歧义：

- boolean 优先于整数 `0 / 1`；
- 大于 2^49 的整数可能被当作 number 读取；
- 恰好 4 字节的整数可能被当作 float 读取；
- 极端 float 字节序列可能被当作 string 读取。

建议 `set` / `get` 使用一致的类型读写。

## 2. zstd —— Zstandard 压缩

`require "zstd"`，基于 [Zstandard](https://github.com/facebook/zstd) v1.6.0。

| 函数 | 说明 |
|------|------|
| `zstd.version()` | 返回版本号字符串，如 `"1.6.0"` |
| `zstd.compress(data[, level])` | 压缩，`level` 默认 3。成功返回压缩后字符串；失败返回 `nil, errmsg` |
| `zstd.decompress(packed)` | 解压。失败返回 `nil, errmsg`（如 `"not a valid zstd frame"`） |
| `zstd.compressBound(len)` | 返回长度为 `len` 的数据压缩后的最大尺寸 |
| `zstd.decompressedSize(packed)` | 返回解压后的原始尺寸；无法确定时返回 -1 |
| `zstd.minCLevel()` / `zstd.maxCLevel()` | 压缩级别范围（实测 -131072 ~ 22） |
| `zstd.defaultCLevel()` | 默认压缩级别（3） |
| `zstd.compressStream(data[, level])` | 流式压缩（结果与 compress 等价） |
| `zstd.decompressStream(packed)` | 流式解压 |

```lua
local zstd = require "zstd"
local packed = zstd.compress(("A"):rep(5600), 3)  -- 5600 字节 → 46 字节
local original = zstd.decompress(packed)          -- 还原 5600 字节
```

## 3. aes —— AES-256 加密

`require "aes"`，内置 micro AES 实现（AES-256：密钥 32 字节，块大小 16 字节）。

**常量**：`BLOCKSIZE`=16、`KEYLENGTH`=32，及各模式长度常量 `GCM_NONCE_LEN`=12、`GCM_TAG_LEN`=16、`CCM_NONCE_LEN`=11、`CCM_TAG_LEN`=16、`OCB_NONCE_LEN`=12、`OCB_TAG_LEN`=16、`EAX_NONCE_LEN`=16、`EAX_TAG_LEN`=16、`SIV_TAG_LEN`=16、`GCM_SIV_NONCE_LEN`=12、`GCM_SIV_TAG_LEN`=16、`POLY1305_TAG_LEN`=16、`CTR_IV_LENGTH`=12。

### 3.1 分组模式

加密自动补齐到块边界；解密时密文长度必须是 16 的整数倍。

| 函数 | 说明 |
|------|------|
| `aes.ecb_encrypt(key, data)` / `aes.ecb_decrypt(key, data)` | ECB |
| `aes.cbc_encrypt(key, iv, data)` / `aes.cbc_decrypt(key, iv, data)` | CBC，iv 16 字节 |
| `aes.cfb_encrypt(key, iv, data)` / `aes.cfb_decrypt(key, iv, data)` | CFB，iv 16 字节 |
| `aes.ofb_encrypt(key, iv, data)` / `aes.ofb_decrypt(key, iv, data)` | OFB，iv 16 字节（加解密相同） |
| `aes.ctr_crypt(key, iv, data)` | CTR，加解密共用，iv 12 字节 |
| `aes.xts_encrypt(keys, tweak, data)` / `aes.xts_decrypt(keys, tweak, data)` | XTS，`keys` 为两个 32 字节密钥拼接（64 字节），`tweak` 16 字节或空，数据至少 16 字节 |

### 3.2 AEAD 认证加密

输出为 `密文 + 16 字节 tag`；解密或认证失败时抛错。

| 函数 | 说明 |
|------|------|
| `aes.gcm_encrypt(key, nonce, aad, data)` / `aes.gcm_decrypt(...)` | GCM，nonce 12 字节 |
| `aes.ccm_encrypt(...)` / `aes.ccm_decrypt(...)` | CCM，nonce 11 字节 |
| `aes.ocb_encrypt(...)` / `aes.ocb_decrypt(...)` | OCB，nonce 12 字节 |
| `aes.eax_encrypt(...)` / `aes.eax_decrypt(...)` | EAX，nonce 16 字节 |
| `aes.gcm_siv_encrypt(...)` / `aes.gcm_siv_decrypt(...)` | GCM-SIV，nonce 12 字节 |
| `aes.siv_encrypt(keys, aad, data)` / `aes.siv_decrypt(keys, aad, data)` | SIV（RFC 5297），`keys` 64 字节，无独立 nonce（IV 内嵌于输出前 16 字节） |

### 3.3 认证与其它

| 函数 | 说明 |
|------|------|
| `aes.cmac(key, data)` | CMAC 消息认证码，返回 16 字节 |
| `aes.poly1305(keys, data)` | Poly1305-AES，`keys` 48 字节（32 字节 AES 密钥 + 16 字节 r） |
| `aes.key_wrap(kek, secret)` / `aes.key_unwrap(kek, wrapped)` | NIST SP 800-38F 密钥封装；`kek` 32 字节，`secret` 为 16 字节倍数 |
| `aes.fpe_encrypt(key, tweak, text)` / `aes.fpe_decrypt(key, tweak, text)` | FF1 保形加密（NIST SP 800-38G），默认字母表为数字 0-9，输出长度不变 |

```lua
local aes = require "aes"
local key = ("k"):rep(32)                    -- 32 字节密钥
local iv  = ("i"):rep(16)
local ct  = aes.cbc_encrypt(key, iv, "hello")  -- 自动补齐到 16 字节
local pt  = aes.cbc_decrypt(key, iv, ct)
```

## 4. sodium —— libsodium 加密库

`require "sodium"`，封装 libsodium 核心接口。

**常量**：`KEYBYTES`=32（secretbox 密钥）、`NONCEBYTES`=24、`SIGN_PUBLICKEYBYTES`=32、`SIGN_SECRETKEYBYTES`=64、`PWHASH_SALTBYTES`=16、`B64_ORIGINAL`、`B64_URLSAFE`（base64 变体参数）。

| 函数 | 说明 |
|------|------|
| `sodium.version()` | libsodium 版本号 |
| `sodium.randombytes(n)` | 生成 n 字节安全随机数 |
| `sodium.bin2hex(bin)` / `sodium.hex2bin(hex[, ignore])` | 二进制 ↔ 十六进制（`ignore` 为需跳过的字符） |
| `sodium.bin2base64(bin[, variant])` / `sodium.base642bin(b64[, variant[, ignore]])` | 二进制 ↔ Base64 |
| `sodium.generichash(msg[, out_len[, key]])` | BLAKE2b 通用哈希，默认输出 32 字节 |
| `sodium.secretbox_easy(msg, nonce, key)` | XSalsa20-Poly1305 对称加密，输出含 16 字节 MAC |
| `sodium.secretbox_open_easy(ct, nonce, key)` | 解密；密钥/nonce 错误或数据被篡改时报错 |
| `sodium.sign_keypair()` | 生成 Ed25519 签名密钥对，返回 `pubkey, secretkey` |
| `sodium.sign_detached(msg, sk)` | 签名，返回 64 字节签名 |
| `sodium.sign_verify_detached(sig, msg, pk)` | 验签，返回 `boolean` |
| `sodium.scalarmult_base(scalar)` | Curve25519 标量乘基点，返回 32 字节公钥 |
| `sodium.pwhash(pw, salt[, out_len[, ops[, mem]]])` | Argon2i 密码哈希；`salt` 16 字节，默认 ops/mem 为交互式参数 |

## 5. cjson —— JSON 编解码

`require "cjson"`，标准 lua-cjson 2.1.0.11（另有 `cjson.safe` 变体，错误时不抛异常）。

| 函数 | 说明 |
|------|------|
| `cjson.encode(value)` | Lua 值 → JSON 字符串 |
| `cjson.decode(json)` | JSON 字符串 → Lua 值 |
| `cjson.new()` | 新建独立配置的 cjson 模块表 |

**配置函数**（标准 lua-cjson）：`encode_empty_table_as_object`、`decode_array_with_array_mt`、`decode_allow_comment`、`encode_sparse_array`、`encode_max_depth`、`decode_max_depth`、`encode_number_precision`、`encode_keep_buffer`、`encode_invalid_numbers`、`decode_invalid_numbers`、`encode_escape_forward_slash`、`encode_skip_unsupported_value_types`、`encode_indent`。

**特殊值**：`cjson.null`、`cjson.empty_array`、`cjson.array_mt`、`cjson.empty_array_mt`，以及 `cjson._NAME` / `cjson._VERSION`。

## 6. ffi —— C 接口调用

`require "ffi"`，LuaJIT 风格 FFI（cffi 移植），可在 Lua 中声明并调用任意 C 函数与结构体。

```lua
local ffi = require "ffi"
ffi.cdef[[
  int strlen(const char *s);
]]
local lib = ffi.load("libc.so")
print(lib.strlen("hello"))   -- 5
```

| 函数 | 说明 |
|------|------|
| `ffi.cdef(decl)` | 声明 C 类型与函数原型 |
| `ffi.load(name)` | 加载动态库，返回库句柄 |
| `ffi.new(ct, ...)` | 创建 cdata 对象（可带初值） |
| `ffi.cast(ct, value)` | 类型转换 |
| `ffi.metatype(ct, mt)` | 为 cdata 类型绑定元表 |
| `ffi.typeof(ct)` | 获取类型对象 |
| `ffi.addressof(cd)` | 取 cdata 地址 |
| `ffi.gc(cd, finalizer)` | 设置析构回调 |
| `ffi.sizeof(ct)` / `ffi.alignof(ct)` / `ffi.offsetof(ct, field)` | 尺寸 / 对齐 / 字段偏移 |
| `ffi.istype(ct, value)` | 判断类型 |
| `ffi.errno([err])` | 读取 / 设置 errno |
| `ffi.string(cdata[, len])` | cdata → Lua 字符串 |
| `ffi.copy(dst, src, len)` | 内存拷贝 |
| `ffi.fill(dst, len[, c])` | 内存填充 |
| `ffi.toretval(...)` / `ffi.eval(...)` / `ffi.type(...)` | 进阶工具 |
| `ffi.getLuaState()` | 返回当前 `lua_State` 指针 |
| `ffi.luatopointer(v)` | 任意 Lua 值 → 指针 |

**环境常量**：`ffi.os`（如 `"Linux"`）、`ffi.arch`（如 `"arm64"`）、`ffi.abi`（ABI 特性表）、`ffi.nullptr`、`ffi.tonumber`、`ffi.L`、`ffi.INFO`。
