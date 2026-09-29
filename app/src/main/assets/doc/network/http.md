# com.androlua.Http 网络请求

Lua 侧的 HTTP 请求分两种方式，注意区分，**名字相同但同步异步完全不同**：

- **同步**：`import "http"` 后的 `http.get/post/download/upload` —— 阻塞当前线程，直接返回结果。
- **异步**：`import "com.androlua.Http"` 后的 `Http.get/post/download` 与 `Http.HttpTask` —— 不阻塞线程，结果通过回调函数返回。

底层均基于 `HttpURLConnection`，默认忽略 HTTPS 证书校验（便于调试，生产环境请自行评估）。

## 一、同步方式（import "http"）

### 导入

```lua
require "import"
import "http"
```

### 函数签名

```lua
-- ① GET 请求
body, cookie, code, headers = http.get(url [,cookie, ua, header])

-- ② POST 请求（postdata 为字符串或字符串数据组表）
body, cookie, code, headers = http.post(url, postdata [,cookie, ua, header])

-- ③ 下载（返回码与响应头）
code, headers = http.download(url [,cookie, ua, ref, header])

-- ④ 上传（datas 为字符串数据组表，files 为文件名数据表）
body, cookie, code, headers = http.upload(url, datas, files [,cookie, ua, header])
```

### 参数说明

| 参数 | 说明 |
|------|------|
| `url` | 网址 |
| `postdata` | post 的字符串或字符串数据组表 |
| `datas` | upload 的字符串数据组表 |
| `files` | upload 的文件名数据表 |
| `cookie` | 网页要求的 cookie |
| `ua` | 浏览器识别 |
| `ref` | 来源页网址 |
| `header` | http 请求头 |

### 示例

```lua
require "import"
import "http"

-- get 函数以 get 请求获取网页，参数为请求的网址与 cookie
local body, cookie, code, headers = http.get("https://httpbin.org/get")

-- post 函数以 post 请求获取网页，通常用于提交表单，参数为请求的网址、要发送的内容与 cookie
local body, cookie, code, headers = http.post(
    "https://httpbin.org/post",
    "name=用户名&pass=密码&ki=1"
)

-- download 函数和 get 函数类似，用于下载文件，参数为请求的网址、保存文件的路径与 cookie
http.download("https://httpbin.org/image/png", "/sdcard/a.png")

-- upload 用于上传文件：参数为请求的网址、请求内容字符串部分（key=value 形式的表）、
-- 请求文件部分（key=文件路径的表）、最后一个参数为 cookie
local body, cookie, code, headers = http.upload(
    "https://httpbin.org/post",
    { title = "标题", msg = "内容" },
    { file1 = "/sdcard/1.txt", file2 = "/sdcard/2.txt" }
)
```

> 同步调用会阻塞当前线程，不要在 UI 线程执行耗时请求。

## 二、异步方式（com.androlua.Http）

### 获取内容 get

```lua
Http.get(url, cookie, charset, header, callback)
```

- `url` 网络请求的链接网址
- `cookie` 使用的 cookie，也就是服务器的身份识别信息
- `charset` 内容编码
- `header` 请求头
- `callback` 请求完成后执行的函数

```lua
require "import"
import "com.androlua.Http"

Http.get("https://httpbin.org/get", nil, "utf-8", nil, function(code, content, cookie, header)
    -- code 响应代码，2xx 表示成功，4xx 表示请求错误，5xx 表示服务器错误，-1 表示出错
    -- content 内容，如果 code 是 -1，则为出错信息
    -- cookie 服务器返回的用户身份识别信息
    -- header 服务器返回的头信息
    if code == 200 then
        print("响应内容：" .. content)
    else
        print("请求出错：" .. content)
    end
end)
```

> 除了 `url` 和 `callback`，其他参数都不是必须的。

### 向服务器发送数据 post

```lua
Http.post(url, data, cookie, charset, header, callback)
```

除了增加了一个 `data` 外，其他参数和 `get` 完全相同。`data` 为向服务器发送的数据。

### 下载文件 download

```lua
Http.download(url, path, cookie, header, callback)
```

参数中没有编码参数，其他同 `get`。`path` 为文件保存路径。

### 并发上限

需要特别注意一点：**只支持同时有 127 个网络请求**，超出会出错。

### 通用封装 HttpTask

`Http` 其实是对 `Http.HttpTask` 的封装，`Http.HttpTask` 使用更加通用和灵活的形式：

```lua
Http.HttpTask(url, method, cookie, charset, header, callback)
```

所有参数都是必选，没有则传入 `nil`。

- `url` 请求的网址
- `method` 请求方法，可以是 get、post、put、delete 等
- `cookie` 身份验证信息
- `charset` 内容编码
- `header` 请求头
- `callback` 回调函数

该函数返回的是一个 `HttpTask` 对象，需要调用 `execute` 方法才可以执行：

```lua
local t = Http.HttpTask(url, "POST", nil, "utf-8", nil, callback)
t.execute{ data }
```

注意调用 `execute` 的括号是**花括号**，内容可以是字符串或者 byte 数组。使用这个形式可以自己封装异步上传函数。

### 全局请求头

可设置作用于**之后所有异步请求**的默认头：

```lua
Http.setUserAgent("Mozilla/5.0 (Linux; Android 13)")
Http.setReferer("https://example.com/")
Http.setCookie("token=abc123")
Http.setHeader({["Accept-Language"] = "zh-CN,zh;q=0.9"})
```

## 注意事项

- 同步 `http.*` 出错时 `code` 为 `-1`，此时 `body` 为错误信息；异步 `Http.get` 出错时回调收到 `code = -1`，`content` 为错误信息。
- 需要携带会话时，把上一次返回的 `cookie` 传给下一次调用。
- 异步回调运行在子线程，更新 UI 请自行切换到主线程处理。
- 默认连接超时 30 秒，自动跟随重定向。