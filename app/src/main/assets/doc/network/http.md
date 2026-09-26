# com.androlua.Http 网络请求

`http` 模块封装了 `com.androlua.Http` 的 HTTP 请求能力，支持同步 / 异步请求、表单提交、文件上传与下载。底层基于 `HttpURLConnection`，默认忽略 HTTPS 证书校验（便于调试，生产环境请自行评估）。

## 导入

```lua
require "import"
import "http"
```

## 函数签名

```lua
-- ① GET 请求
body, cookie, code, headers = http.get(url [,cookie, ua, header])

-- ② POST 请求（postdata 为字符串或字符串数据组表）
body, cookie, code, headers = http.post(url, postdata [,cookie, ua, header])

-- ③ 下载
code, headers = http.download(url [,cookie, ua, ref, header])

-- ④ 上传（datas 为字符串数据组表，files 为文件名数据表）
body, cookie, code, headers = http.upload(url, datas, files [,cookie, ua, header])
```

### 参数说明

| 参数 | 说明 |
|------|------|
| `url` | 请求网址 |
| `postdata` | POST 的字符串（如 JSON）或字符串数据组表（表单键值） |
| `datas` | upload 的字符串数据组表 |
| `files` | upload 的文件名数据表（字段名 → 文件路径） |
| `cookie` | 网页要求的 cookie |
| `ua` | 浏览器识别串（User-Agent） |
| `ref` | 来源页网址（Referer） |
| `header` | HTTP 请求头（键值表） |

## GET 请求

```lua
require "import"
import "http"

-- 同步 GET：阻塞直到返回
local body, cookie, code, headers = http.get("https://httpbin.org/get")

if code == 200 then
    print("响应内容：" .. body)
else
    print("请求失败，code = " .. tostring(code))
end
```

带自定义请求头与 UA：

```lua
local header = {["Accept"] = "application/json"}
local body, cookie, code = http.get(
    "https://httpbin.org/get",
    nil,
    "Mozilla/5.0 (Linux; Android 13)",
    header
)
```

## POST 请求

提交 JSON 字符串：

```lua
local body, cookie, code = http.post(
    "https://httpbin.org/post",
    '{"username":"admin","password":"123456"}'
)
```

提交表单键值表：

```lua
local body, cookie, code = http.post(
    "https://httpbin.org/post",
    {username = "admin", password = "123456"}
)
```

## 文件上传

```lua
local body, cookie, code = http.upload(
    "https://httpbin.org/post",
    {remark = "测试上传"},
    {file = "/sdcard/Download/test.png"}
)
```

`files` 表内每个键为表单字段名，值为本地文件路径；也可传入多个字段。

## 下载

```lua
local code, headers = http.download("https://example.com/app.apk")
if code == 200 then
    print("下载成功")
end
```

> 说明：下载目标路径由运行时 `http` 模块实现决定，返回值以当前运行时的实机行为为准。

## 全局请求头

除单次请求的 `header` 参数外，还可通过 `com.androlua.Http` 设置全局默认头，作用于之后所有请求：

```lua
require "import"
import "http"
import "com.androlua.Http"

Http.setUserAgent("Mozilla/5.0 (Linux; Android 13)")
Http.setReferer("https://example.com/")
Http.setCookie("token=abc123")
Http.setHeader({["Accept-Language"] = "zh-CN,zh;q=0.9"})
```

## 注意事项

- 请求异常时 `code` 为 `-1`，此时 `body` 为错误信息（底层 `Http.java` 行为）。
- `body` 为字符串；如需二次请求携带会话，将上一次返回的 `cookie` 传给下一次调用。
- 同步调用会阻塞当前线程，不要在 UI 线程执行耗时请求。
- 默认连接超时 30 秒，自动跟随重定向。
