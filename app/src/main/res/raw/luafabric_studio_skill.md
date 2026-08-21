---
name: luafabric-studio
description: LuaFabric Studio、AndroLua、LuaFabric 项目开发专用 skill。仅当用户要求编写、修改、审查或排查 LuaFabric Studio 项目中的 Lua/Android 代码时使用。
---

# LuaFabric Studio Development

本文件自包含。执行任务不要求读取项目 `README.md` 或附件；项目源码、构建配置和实机结果仅用于核验当前版本事实。不得将这些规则泛化到普通 Lua、LuaJIT、其他 AndroLua 分支或 Android 项目。

## 资料与决策

1. 事实来源优先级：当前项目源码/构建配置与实测 > 用户明确指令 > 本文件 > 通用经验。
2. API、版本、兼容性、性能、安全和运行时行为，必须有官方文档、当前源码或可复现实验支撑。没有证据时明确标为未知，不猜测、不编造、不伪造测试结果。
3. 遇到环境缺失、表意不清、重大方案分歧或输入缺少上下文，先询问用户。不得擅自替用户做关键决定。
4. 先检查现有源码和局部模式，再编辑；只做最小正确改动；保留无关用户改动。
5. 发现本文件、源码、构建配置或用户要求冲突时，指出冲突和影响，并询问采用哪一项；用户已明确指定时按用户指定执行。

## LuaFabric Runtime

- LuaFabric 使用 Lua 5.5 深度魔改运行时，不是标准 Lua 5.3 或 LuaJIT。
- 支持 `try/catch/finally`、`switch/case`、`defer`、`when`、`continue`、三元运算符、可选链、空值合并、管道、lambda、复合赋值、`$` 局部声明、`@label@` 标签。
- 原生模块经 `require` 加载：`mmkv`、`zstd`、`aes`、`sodium`、`cjson`、`ffi`；Lua 模块包括 `memory`、`lanes`。模块是否随当前构建和设备提供，仍须核验。
- assets Lua 模块可从 `assets/` 与 `assets/lua/` 解析；不要假定任意路径或第三方模块存在。
- `lanes` 中每个 lane 为独立 Lua 状态和 GC；跨线程用 linda/mmkv，不依赖共享 Lua 状态。`linda:receive()` 返回 `(key, value)`，超时参数必须置首。
- `memory` 是 Handler + 协程调度，不占真实线程；页面销毁时按项目 API 调用 `memory.on_destroy()`。

### 扩展语法速查

```lua
local x = condition ? value1 : value2
local name = user?.profile?.name ?? "anonymous"
local result = value |> transform |> validate
local add = \a, b -> a + b
count += 1
try body() catch (err) handle(err) finally cleanup() end
switch value do case 1 then print("one") default print("other") end
defer file:close() end
when score >= 60 then pass() case score >= 0 then review() else reject() end
```

扩展语法必须以当前 LuaFabric 解释器实测为准；示例只描述语法形态，不证明任意项目版本均启用。

### `...` 可变参数（vararg）

`...` 只能在声明为可变参数的函数内部使用（即 `function foo(...) ... end` 或 `function(...) ... end`）。在非 vararg 函数中使用会报错：

```
cannot use '...' outside a vararg function near '...'
```

嵌套函数可以捕获外层函数的 `...`，但中间不能有赋值。如需在中间插入局部变量后再使用 `...`，用 `local arg = {...}` 包装。

## AndroLua 与 luajava

### 点号语法（强制）

Java 对象方法调用**必须使用点号**，**禁止使用冒号**。这是 AndroLua 核心约束，适用于所有 Java 对象，包括链式调用。

**错误（冒号语法）：**
```lua
card:setRadius(24)
card:setCardElevation(8)
builder:setTitle("标题"):setView(view):show()
```

**正确（点号语法）：**
```lua
card.setRadius(24)
card.setCardElevation(8)
builder.setTitle("标题").setView(view).show()
```

冒号 `:` 仅用于 Lua 标准库对象风格调用或项目明确支持处，例如 `file:read()`、`"text":find()`。默认选显式形式 `string.find(value, pattern)`，避免歧义。凡是 Java 对象（控件、Builder、Canvas 等），一律用点号。
- `require "import"` 导入 AndroLua 标准功能，如 `loadlayout`、`dump`、`tostring`。
- Lua string 不等于 Java `String`，不能调用 `getBytes()`。传入 Java 数组或 API 前，按目标类型显式转换；Java `byte[]` 转 Lua string 可用 `java.lang.String(bytes, StandardCharsets.UTF_8).toString()`。
- Java 数组优先用 `luajava.bindClass` 与 `java.lang.reflect.Array.newInstance` 创建，并使用 0 起始下标写入。
- 第三方 dex 放项目根目录 `libs/`；按需用 `import` 或 `require` 导入。无法由 `require` 加载时可改用 `import`，不得假定 dex 已安装。
- `luajava` 没有 `proxy`；单方法 Java 接口或抽象类可用 `ClassName{ method = function(...) ... end }`。
- 线程作用域独立，默认无 AndroLua 标准库；传入 table 在线程内为 Java Lua Table，遍历前用 `luajava.astable()`。

### 类型转换

Lua string 没有 Java `getBytes()`。Lua string 转 Java `byte[]`：

```lua
local bindClass = luajava.bindClass
local Byte = bindClass "java.lang.Byte"
local Array = bindClass "java.lang.reflect.Array"
local value = "test"
local bytes = Array.newInstance(Byte.TYPE, #value)
for i = 1, #value do bytes[i - 1] = string.byte(value, i) end
```

Java `byte[]` 转 UTF-8 Lua string：

```lua
local String = luajava.bindClass "java.lang.String"
local StandardCharsets = luajava.bindClass "java.nio.charset.StandardCharsets"
local value = String(bytes, StandardCharsets.UTF_8).toString()
```

Java 类通常局部绑定：`local String = luajava.bindClass "java.lang.String"`。Java 数组使用 `luajava.newArray` 或 `Array.newInstance`，Java 数组下标从 0 开始。

### import 与模块

- `require "import"` 提供 `loadlayout`、`dump`、`tostring` 等 AndroLua 标准功能。
- 第三方 dex 放项目根目录 `libs/`；全局导入用 `import "com.example.T"` 或 `import "com.example.*"`，局部导入用 `require "com.example.T"`。
- Lua 模块通常返回 table：`local module = require "dir.module"`。assets 模块按 `assets/`、`assets/lua/` 回退查找。

## UI 与布局

### 布局表 + loadlayout（强制）

**必须使用布局表 + `loadlayout` 构建 UI**，禁止手动 `new` 控件 + 逐个 `setLayoutParams`/`setPadding`/`setRadius` 的命令式写法。仅在 `loadlayout` 无法满足的极端场景（如完全动态运行时拼接未知结构）才考虑替代方案，且须先说明原因。

**错误（命令式构建）：**
```lua
local card = CardView(activity)
card.setLayoutParams(params(MATCH_PARENT, WRAP_CONTENT))
card.setRadius(24)
card.setCardElevation(8)
card.setPadding(32, 32, 32, 32)
```

**正确（布局表）：**
```lua
local layout = {
  CardView,
  layout_width = "match_parent",
  layout_height = "wrap_content",
  CardElevation = "8dp",
  Radius = "24dp",
  Padding = "32dp",
  {
    TextView,
    layout_width = "wrap_content",
    layout_height = "wrap_content",
    text = "Hello"
  }
}
activity.setContentView(loadlayout(layout))
```

`.aly` 文件经 `require` 得到 Lua table，可直接作为 `loadlayout` 参数。布局属性映射 Java setter。尺寸优先带单位字符串（`"16dp"`）；支持 `%h`、`%w` 时按需使用。

### 标准用法：id 注入 _G

AndroLua 标准写法直接将布局表内所有设有 `id` 的控件注入 `_G` 全局环境，无需第二参数：

```lua
-- 布局表中为控件设置 id
local layout = {
  CardView,
  id = "card",
  layout_width = "match_parent",
  layout_height = "wrap_content",
  {
    TextView,
    id = "title",
    text = "Hello"
  }
}
activity.setContentView(loadlayout(layout))
-- 直接使用 id 作为全局变量
title.Text = "New Title"
card.setRadius(16)
```

**禁止**在标准场景下使用 `loadlayout` 第二参数收集控件。布局表中设 `id` + 全局访问是 AndroLua 标准模式。

### loadlayout 第二参数（仅特殊场景）

`loadlayout` 支持第二个参数：传入一个 lua table，加载完成后**仅**布局表内设有 `id` 的控件会以 **id 字符串为键、控件实例为值** 存入该 table。未设置 `id` 的控件不会被收集。

```lua
local views = {}
activity.setContentView(loadlayout(layout, views))
-- views["card"] = CardView 实例
-- views["title"] = TextView 实例
-- 注意：id 重复则后者覆盖前者
```

仅在需要批量引用且不希望污染 `_G` 时才使用第二参数；日常开发仍以标准 `_G` 注入为主。

### Material Design 3 组件（强制）

使用组件时遵循 **Material > AppCompat > 原生** 的优先级。能用 Material 组件就不用 AppCompat，能用 AppCompat 就不用原生 `android.widget.*`。

| 用途 | 禁止使用 | 优先级 1（Material） | 优先级 2（AppCompat） |
|------|---------|---------------------|----------------------|
| 卡片 | `androidx.cardview.widget.CardView` | `MaterialCardView` | — |
| 按钮 | `android.widget.Button` | `MaterialButton` | `AppCompatButton` |
| 文本显示 | `android.widget.TextView` | `MaterialTextView` | `AppCompatTextView` |
| 文本输入 | `android.widget.EditText` | `TextInputEditText`（配合 `TextInputLayout`） | `AppCompatEditText` |
| 顶部栏 | `android.widget.Toolbar` | `MaterialToolbar` / `TopAppBar` | `Toolbar` |
| 底部导航 | `android.widget.TabLayout` | `BottomNavigationView` / `NavigationRailView` | — |
| 浮动按钮 | — | `FloatingActionButton` / `ExtendedFloatingActionButton` | — |
| 开关 | `android.widget.Switch` | `MaterialSwitch` | `SwitchCompat` |
| 进度条 | `android.widget.ProgressBar` | `LinearProgressIndicator` / `CircularProgressIndicator` | — |
| 对话框 | `android.app.AlertDialog` | `MaterialAlertDialogBuilder` | — |
| 线性布局 | — | — | `LinearLayoutCompat`（原生 `LinearLayout` 可用但不推荐） |
| 帧布局 | — | — | `ContentFrameLayout`（原生 `FrameLayout` 可用但不推荐） |

具体 Material 类名以当前项目源码和 `com.google.android.material` 包实测为准；上表为常见映射参考。所有组件一律使用点号语法，禁止冒号。

### 其他 UI 规则

- 控件事件可写 `button.onClick = function() ... end`；匿名事件函数不依赖事件参数，使用控件 id。复杂监听仍用 `setOnClickListener`。
- `background` 仅用图片路径或 `Drawable`；纯色用 `backgroundColor`。圆角仅在卡片类控件可靠使用 `Radius`。
- 避免 `style`：解释器差异大，且布局外 `setStyle` 会报错。
- 多个 Android `R` 类并存时，分别 `require` 到不同局部变量；不可直接 `import` 覆盖。

## Implementation Rules

- Java 类优先局部绑定：`local Class = luajava.bindClass "package.Class"`。方法调用一律点号，不得用冒号。
- UI 构建一律布局表 + `loadlayout`；禁止命令式逐个 new + set 的方式构建界面。
- 使用 Material* 组件（`com.google.android.material.*`），不退回 `androidx.*` 原生控件。
- Lua 模块按 `require "path.module"` 导入，模块返回 table。单字符串参数调用可省略括号。
- 控件文本语法糖如 `edit.Text` 返回 Lua string；Java 对象转 Lua 文本优先 `tostring(value)`，不可假设每个对象均可直接调用 `toString()`。
- 颜色优先数字 `0xAARRGGBB`。需要字符串时使用明确转换，不丢失 alpha 通道。
- 不为未经项目证实的 AndroidX、Material、R 类、解释器行为或语法糖添加代码。先查依赖、源码或实测。
- 改动后执行可用的最小相关验证；若无 Android 环境、设备或任务，明确说明未验证项与原因，不以推测代替结果。

## 内置模块接口

以下接口是 skill 内置参考，不替代当前源码和实测。参数括号表示可选参数。

### `mmkv`

`require "mmkv"`。先 `mmkv.initialize(rootDir)`，再读写。

- `version()` -> version string
- `initialize(rootDir)` -> `true` 或 `false, errmsg`
- `set(id, key, value[, expireSeconds])`：支持 string/boolean/integer/number；`nil` 删除 key
- `get(id, key)`、`contains(id, key)`、`remove(id, key)`、`clear(id)`
- `count(id)`、`totalSize(id)`、`allKeys(id)`

`id` 隔离存储实例。类型推断存在边界歧义，读写应保持类型一致。

### `zstd`

`require "zstd"`，Zstandard 1.6.0 绑定。

- `version()`、`minCLevel()`、`maxCLevel()`、`defaultCLevel()`
- `compress(data[, level])`、`decompress(packed)`
- `compressBound(len)`、`decompressedSize(packed)`
- `compressStream(data[, level])`、`decompressStream(packed)`

失败通常返回 `nil, errmsg`；不要把错误字符串当成功数据。

### `aes`

`require "aes"`，基于 μAES 库（micro_aes.c）。AES-256：密钥固定 32 字节，块大小 16 字节。输入输出均为 Lua string（二进制安全）。

#### 常量

`BLOCKSIZE`=16、`KEYLENGTH`=32，以及各模式：`GCM_NONCE_LEN`=12、`GCM_TAG_LEN`=16、`CCM_NONCE_LEN`=11、`CCM_TAG_LEN`=16、`OCB_NONCE_LEN`=12、`OCB_TAG_LEN`=16、`EAX_NONCE_LEN`=16、`EAX_TAG_LEN`=16、`SIV_TAG_LEN`=16、`GCM_SIV_NONCE_LEN`=12、`GCM_SIV_TAG_LEN`=16、`POLY1305_TAG_LEN`=16、`CTR_IV_LENGTH`=12。

#### 分组模式

加密自动 PKCS#7 补齐到块边界；解密时密文长度必须是 16 的整数倍。

| Lua 函数 | 参数顺序 | 说明 |
|----------|---------|------|
| `aes.ecb_encrypt(key, data)` | key, plaintext | ECB，key 32 字节 |
| `aes.ecb_decrypt(key, data)` | key, ciphertext | ECB 解密 |
| `aes.cbc_encrypt(key, iv, data)` | key, iv(16B), plaintext | CBC |
| `aes.cbc_decrypt(key, iv, data)` | key, iv(16B), ciphertext | CBC 解密 |
| `aes.cfb_encrypt(key, iv, data)` | key, iv(16B), plaintext | CFB |
| `aes.cfb_decrypt(key, iv, data)` | key, iv(16B), ciphertext | CFB 解密 |
| `aes.ofb_encrypt(key, iv, data)` | key, iv(16B), plaintext | OFB（加解密相同） |
| `aes.ofb_decrypt(key, iv, data)` | key, iv(16B), ciphertext | OFB 解密（调用 encrypt） |
| `aes.ctr_crypt(key, iv, data)` | key, iv(12B), data | CTR，加解密共用 |

#### XTS

| Lua 函数 | 参数顺序 | 说明 |
|----------|---------|------|
| `aes.xs_encrypt(keys, tweak, data)` | keys(64B), tweak(16B 或空), plaintext | 数据至少 16 字节 |
| `aes.xs_decrypt(keys, tweak, data)` | keys(64B), tweak(16B 或空), ciphertext | 同上 |

`keys` = key1(32B) .. key2(32B) 拼接。

#### AEAD 认证加密

输出为 `ciphertext + tag`（tag 追加在密文尾部）。解密或认证失败时抛错。

| Lua 函数 | 参数顺序 | 说明 |
|----------|---------|------|
| `aes.gcm_encrypt(key, nonce, aad, data)` | key(32B), nonce(12B), aad, plaintext | GCM |
| `aes.gcm_decrypt(key, nonce, aad, data)` | key(32B), nonce(12B), aad, ciphertext+tag | GCM 解密 |
| `aes.ccm_encrypt(key, nonce, aad, data)` | key(32B), nonce(11B), aad, plaintext | CCM |
| `aes.ccm_decrypt(key, nonce, aad, data)` | key(32B), nonce(11B), aad, ciphertext+tag | CCM 解密 |
| `aes.ocb_encrypt(key, nonce, aad, data)` | key(32B), nonce(12B), aad, plaintext | OCB |
| `aes.ocb_decrypt(key, nonce, aad, data)` | key(32B), nonce(12B), aad, ciphertext+tag | OCB 解密 |
| `aes.eax_encrypt(key, nonce, aad, data)` | key(32B), nonce(16B), aad, plaintext | EAX |
| `aes.eax_decrypt(key, nonce, aad, data)` | key(32B), nonce(16B), aad, ciphertext+tag | EAX 解密 |
| `aes.gcm_siv_encrypt(key, nonce, aad, data)` | key(32B), nonce(12B), aad, plaintext | GCM-SIV |
| `aes.gcm_siv_decrypt(key, nonce, aad, data)` | key(32B), nonce(12B), aad, ciphertext+tag | GCM-SIV 解密 |

**关键：GCM/CCM/OCB/EAX/GCM-SIV 的参数顺序均为 `(key, nonce, aad, data)`，aad 在 data 之前。**

#### SIV（RFC 5297）

SIV 无独立 nonce，IV 由合成算法生成并内嵌于输出前 16 字节。

| Lua 函数 | 参数顺序 | 说明 |
|----------|---------|------|
| `aes.siv_encrypt(keys, aad, data)` | keys(64B), aad, plaintext | 输出前 16 字节为合成 IV |
| `aes.siv_decrypt(keys, aad, data)` | keys(64B), aad, IV(16B)+ciphertext | data 前 16 字节为 IV |

#### 认证与其它

| Lua 函数 | 参数顺序 | 说明 |
|----------|---------|------|
| `aes.cmac(key, data)` | key(32B), data | 返回 16 字节 CMAC |
| `aes.poly1305(keys, nonce, data)` | keys(48B: AES密钥+16B r), nonce(16B), data | 返回 16 字节 tag |
| `aes.key_wrap(kek, secret)` | kek(32B), secret | secret 为 8 字节倍数，至少 16 字节 |
| `aes.key_unwrap(kek, wrapped)` | kek(32B), wrapped | wrapped 为 8 字节倍数，至少 24 字节 |
| `aes.fpe_encrypt(key, tweak, data)` | key(32B), tweak, plaintext | FF1 保形加密，默认数字字母表 |
| `aes.fpe_decrypt(key, tweak, data)` | key(32B), tweak, ciphertext | 输出长度与输入相同 |

```lua
local aes = require "aes"
-- CBC 示例
local key = ("k"):rep(32)
local iv  = ("i"):rep(16)
local ct  = aes.cbc_encrypt(key, iv, "hello")  -- 自动补齐到 16 字节
local pt  = aes.cbc_decrypt(key, iv, ct)

-- GCM 示例
local nonce = ("\0"):rep(12)
local aad = "header"
local ct2 = aes.gcm_encrypt(key, nonce, aad, "secret")
local pt2 = aes.gcm_decrypt(key, nonce, aad, ct2)  -- 认证失败则抛错
```

### `sodium`

`require "sodium"`，libsodium 绑定。

- `version()`、`randombytes(n)`
- `bin2hex/hex2bin`、`bin2base64/base642bin`
- `generichash(msg[, out_len[, key]])`
- `secretbox_easy`、`secretbox_open_easy`
- `sign_keypair()`、`sign_detached`、`sign_verify_detached`
- `scalarmult_base(scalar)`、`pwhash(pw, salt[, out_len[, ops[, mem]]])`

常量包括 `KEYBYTES=32`、`NONCEBYTES=24`、Ed25519 公私钥长度和 `PWHASH_SALTBYTES=16`。密钥、nonce、salt 长度须按 API 常量传递。

### `cjson`

`require "cjson"` 或 `require "cjson.safe"`。

- `encode(value)`、`decode(json)`、`new()`
- 配置：`encode_empty_table_as_object`、`decode_array_with_array_mt`、`decode_allow_comment`、`encode_sparse_array`、`encode_max_depth`、`decode_max_depth`、`encode_number_precision`、`encode_keep_buffer`、`encode_invalid_numbers`、`decode_invalid_numbers`、`encode_escape_forward_slash`、`encode_skip_unsupported_value_types`、`encode_indent`
- 特殊值：`null`、`empty_array`、`array_mt`、`empty_array_mt`

`cjson.safe` 用于需要把解码错误转为返回值的场景；具体返回形式须实测。

### `memory`

`require "memory"`。纯 Lua 动态内存管理模块，使用 Handler + 协程调度，不等于真实线程。

- `start(cfg)`：自动模式；cfg 支持 `alert`、`pause`、`stepmul`、`cooling_time`、`peak_memory`、`pressure_threshold`、`interval`、`debug`、`java_gc`
- `start_manual(cfg)`、`tick()`、`stop()`
- `monitoring()` -> `lua_kb, leak_warning, java_mb`
- `force_gc()`、`get_status()`、`on_destroy()`、`help()`

页面销毁时调用 `on_destroy()`；手动模式建议至少间隔 500 ms 调用 `tick()`。

### `lanes`

`require "lanes"`。每个 lane 是独立 Lua 状态，通过 linda 传递可序列化值。

- `lanes.gen([libs[, opts,] lane_func])` 创建可复用生成器；`libs`：`nil` 无库、`""` 仅 base、`"math,os"` 指定库、`"*"` 全部标准库
- `lanes.linda()` 创建通道
- linda：`send(key, value)`、`receive([timeout,] key...)`、`get([timeout,] key...)`、`set(key, value)`、`count()`、`broadcast(key, value[, limit])`
- lane：`join([timeout])`、`status()`、`cancel()`、`get_peer()`

`receive` 成功返回 `(key, value)`；超时通常返回 `(nil, "timeout")`。闭包 upvalue 按值传递，不共享主线程状态。

### `ffi`

`require "ffi"` 提供 C 接口调用能力。先用 `ffi.cdef` 声明，再 `ffi.load` 加载库。

- `cdef`、`load`、`new`、`cast`、`metatype`、`typeof`
- `addressof`、`gc`、`sizeof`、`alignof`、`offsetof`、`istype`、`errno`
- `string`、`copy`、`fill`、`toretval`、`eval`、`type`
- `getLuaState()`、`luatopointer()`

`ffi.os`、`ffi.arch`、`ffi.abi`、`ffi.nullptr`、`ffi.tonumber`、`ffi.L`、`ffi.INFO` 为环境相关值；不可凭名称推断平台支持。

## Response

- 引用证据来源：文件路径、官方链接或实测命令/设备结果；不要求 README 存在。
- 发现文档与源码不一致，先报告冲突并询问；除非用户明确指定，不自行选一边。
- 不输出不存在的命令、依赖、日志、测试、构建产物或设备结果。
