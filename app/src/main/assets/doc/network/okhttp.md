# OkHttp 基础用法

OkHttp 是 Square 出品的工业级 HTTP 客户端，具备连接池复用、透明 GZIP 压缩、响应缓存与自动恢复连接等能力。LuaFabric 运行时已内置 okhttp（当前版本 `5.3.2`），无需再手动引入依赖。

## 基本认知

- `OkHttpClient` 内部持有连接池与线程池，**整个应用应只创建一个实例**复用。
- 响应体 `body` 只能读取一次；读完后应关闭 `Response` 释放连接。
- 网络请求不能跑在 Android 主线程（会抛 `NetworkOnMainThreadException`）。

## 创建客户端（单例）

```lua
require "import"
import "okhttp3.OkHttpClient"
import "okhttp3.Request"
import "okhttp3.RequestBody"
import "okhttp3.MediaType"
import "java.util.concurrent.TimeUnit"

-- 单例：全程复用同一客户端
local client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
```

## 同步 GET

```lua
local request = Request.Builder()
    .url("https://api.github.com/users/octocat")
    .build()

local response = client.newCall(request).execute()
if response.isSuccessful() then
    local body = response.body().string()
    print(body)
end
response.close()
```

## 异步 GET

`enqueue` 在 OkHttp 内部线程池执行，回调不阻塞调用线程：

```lua
local request = Request.Builder()
    .url("https://api.github.com/users/octocat")
    .build()

client.newCall(request).enqueue({
    onFailure = function(call, e)
        print("请求失败：" .. tostring(e.getMessage()))
    end,
    onResponse = function(call, response)
        response.close()
        if response.isSuccessful() then
            local body = response.body().string()
            print(body)
        end
    end
})
```

## POST JSON

```lua
local json = '{"name":"luafabric","lang":"lua"}'
local reqBody = RequestBody.create(
    MediaType.parse("application/json; charset=utf-8"),
    json
)

local request = Request.Builder()
    .url("https://httpbin.org/post")
    .post(reqBody)
    .build()

local response = client.newCall(request).execute()
print("code = " .. response.code())
print(response.body().string())
response.close()
```

## 注意事项

- `response.body().string()` 只能调用一次，再次读取会抛异常。
- 每次读完响应后调用 `response.close()`，避免连接泄漏；或改用异步 `enqueue`。
- 请求失败（断网、超时）时 `execute` 会抛出异常，可用 `pcall` 包裹处理。
- `OkHttpClient.Builder` 可继续扩展：`addInterceptor`、`cache`、`cookieJar` 等，按需配置。
