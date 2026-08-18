# OkHttpUtil - 文档

## 一、概述

- **功能**：网络请求工具类，基于OkHttp库封装，提供HTTP GET/POST请求、文件上传下载等功能，所有请求异步执行，通过回调返回结果。
- **适用场景**：Android开发中各类网络操作场景，如API数据请求、用户登录、文件上传下载等。
- **优势**：支持自定义请求头、Cookie管理、断点续传，异步执行不阻塞主线程，适配Lua环境。

## 二、启用方式

在项目 `settings.json` 文件中添加工具类到全局工具列表：

```json
{
  "application": {
    "label": "My App",
    "debugmode": true
  },
  "global_utils": [
    "OkHttpUtil"
  ]
}
```

## 三、核心函数

### 1. get（发送GET请求）

- **Lua签名**：`get(url, cookie, charset, header, callback)`
- **功能**：发送异步GET请求，支持自定义Cookie、字符集和请求头。
- **参数**：
    - `url` (string)：请求URL地址（必填）
    - `cookie` (string, 可选)：Cookie字符串
    - `charset` (string, 可选)：响应内容字符集（默认UTF-8）
    - `header` (table, 可选)：请求头表（键值对格式）
    - `callback` (function)：回调函数，接收参数：code（响应码）、content（响应内容）、cookie（返回Cookie）、headers（响应头）（必填）
- **返回值**：无（异步执行，结果通过回调返回）
- **示例**：

```lua
-- 简单GET请求
get("https://api.example.com/data", function(code, content, cookie, headers)
    if code == 200 then
        textView.text = content
    else
        toast("请求失败: " .. code)
    end
end)
-- 带自定义头和Cookie的GET请求
local headers = {["Authorization"] = "Bearer token123", ["User-Agent"] = "LuaFabric/1.0"}
get("https://api.example.com/user", "session=abc123", "UTF-8", headers, function(code, content)
    if code == 200 then
        local userData = json.decode(content)
        print(userData.username)
    end
end)
```

### 2. post（发送POST表单请求）

- **Lua签名**：`post(url, formData, cookie, charset, header, callback)`
- **功能**：发送异步POST请求，支持表单数据提交。
- **参数**：
    - `url` (string)：请求URL地址（必填）
    - `formData` (table)：表单数据表（键值对格式，必填）
    - `cookie` (string, 可选)：Cookie字符串
    - `charset` (string, 可选)：响应内容字符集
    - `header` (table, 可选)：请求头表
    - `callback` (function)：回调函数，参数同get（必填）
- **返回值**：无（异步执行）
- **示例**：

```lua
-- 登录请求
local loginForm = {username = "admin", password = "123456", remember = "true"}
post("https://api.example.com/login", loginForm, function(code, content, cookie)
    if code == 200 then
        setSharedData("session", cookie) -- 保存Cookie
        toast("登录成功")
    else
        toast("登录失败")
    end
end)
-- 带自定义Content-Type的POST请求
local headers = {["Content-Type"] = "application/x-www-form-urlencoded"}
post("https://api.example.com/update", {status = "online"}, nil, nil, headers, callback)
```

### 3. download（下载文件）

- **Lua签名**：`download(url, savePath, cookie, header, callback)`
- **功能**：异步下载文件到指定路径，支持断点续传。
- **参数**：
    - `url` (string)：文件URL地址（必填）
    - `savePath` (string)：本地保存路径（相对路径基于项目目录，必填）
    - `cookie` (string, 可选)：Cookie字符串
    - `header` (table, 可选)：请求头表
    - `callback` (function)：回调函数，参数同get（content为下载状态信息，必填）
- **返回值**：无（异步执行）
- **示例**：

```lua
-- 下载图片并显示
download("https://example.com/wallpaper.jpg", "images/wallpaper.jpg", function(code, content)
    if code == 200 then
        toast("下载成功")
        local bitmap = getBitmap("images/wallpaper.jpg")
        imageView.setImageBitmap(bitmap)
    else
        toast("下载失败: " .. code)
    end
end)
-- 带进度提示的大文件下载
progressBar.visibility = View.VISIBLE
download("https://example.com/bigfile.zip", "downloads/file.zip", nil, nil, function(code)
    progressBar.visibility = View.GONE
    if code == 200 then
        toast("文件下载完成")
    end
end)
```

### 4. upload（上传文件）

- **Lua签名**：`upload(url, filePath, cookie, header, callback)`
- **功能**：异步上传文件到服务器，支持多部分表单上传。
- **参数**：
    - `url` (string)：上传URL地址（必填）
    - `filePath` (string)：本地文件路径（相对路径基于项目目录，必填）
    - `cookie` (string, 可选)：Cookie字符串
    - `header` (table, 可选)：请求头表
    - `callback` (function)：回调函数，参数同get（必填）
- **返回值**：无（异步执行）
- **示例**：

```lua
-- 上传头像
local avatarPath = "images/avatar.jpg"
upload("https://api.example.com/upload/avatar", avatarPath, function(code, content)
    if code == 200 then
        local result = json.decode(content)
        if result.success then
            toast("头像上传成功")
        end
    else
        toast("上传失败: " .. code)
    end
end)
-- 带认证头的文件上传
local token = getSharedData("token")
local headers = {["Authorization"] = "Bearer " .. token}
upload("https://api.example.com/file", "documents/report.pdf", nil, headers, callback)
```

## 四、使用示例

### 示例1：完整用户信息请求流程

```lua
-- 获取用户详情（含Token过期处理）
function fetchUserProfile(userId)
    local url = "https://api.example.com/users/" .. userId
    local sessionCookie = getSharedData("session")
    
    get(url, sessionCookie, "UTF-8", nil, function(code, content)
        if code == 200 then
            local userData = json.decode(content)
            -- 更新UI
            nameText.text = userData.name
            emailText.text = userData.email
            -- 加载用户头像
            loadImage(avatarView, userData.avatar_url)
        elseif code == 401 then
            toast("登录已过期，请重新登录")
            newActivity("login") -- 跳转到登录页
        else
            toast("获取用户信息失败: " .. code)
        end
    end)
end

-- 调用
fetchUserProfile("user123456")
```

### 示例2：文件下载管理器（避免重复下载）

```lua
local downloadTasks = {} -- 存储正在下载的任务

function startDownload(fileUrl, fileName)
    local savePath = "downloads/" .. fileName
    -- 检查是否已在下载
    if downloadTasks[fileName] then
        toast("该文件正在下载中")
        return
    end
    
    downloadTasks[fileName] = true
    toast("开始下载: " .. fileName)
    
    download(fileUrl, savePath, nil, nil, function(code)
        downloadTasks[fileName] = nil -- 移除任务标记
        if code == 200 then
            toast("下载完成: " .. fileName)
            addToDownloadList(fileName, savePath) -- 添加到下载列表
        else
            toast("下载失败: " .. code)
        end
    end)
end

-- 调用
startDownload("https://example.com/app.apk", "MyApp.apk")
```

## 五、最佳实践

1. **统一错误处理与重试**：封装请求函数，处理服务器错误重试

```lua
function requestWithRetry(method, url, data, callback, retries)
    retries = retries or 3
    local function onResult(code, content, cookie, headers)
        if code >= 200 and code < 300 then
            callback(true, content, cookie, headers)
        elseif retries > 0 and code >= 500 then
            toast("请求失败，重试中(" .. retries .. ")")
            requestWithRetry(method, url, data, callback, retries - 1)
        else
            callback(false, content, cookie, headers)
        end
    end
    
    if method == "GET" then
        get(url, getSharedData("cookie"), "UTF-8", nil, onResult)
    elseif method == "POST" then
        post(url, data, getSharedData("cookie"), "UTF-8", nil, onResult)
    end
end
```

2. **Cookie管理**：统一保存和读取Cookie，维持登录状态

```lua
-- 保存响应中的Cookie
function saveCookie(cookieStr)
    if cookieStr and #cookieStr > 0 then
        setSharedData("cookie", cookieStr)
    end
end

-- 登录回调中保存Cookie
post("https://api.example.com/login", loginForm, function(code, content, cookie)
    if code == 200 then
        saveCookie(cookie)
    end
end)
```

3. **文件路径规范**：使用统一的目录结构管理下载/上传文件，避免路径混乱

```lua
-- 定义统一路径常量
local DOWNLOAD_DIR = "downloads/"
local UPLOAD_DIR = "uploads/"
local IMAGE_DIR = "images/"

-- 下载文件时使用统一目录
download(fileUrl, DOWNLOAD_DIR .. fileName, callback)
```

4. **避免内存泄漏**：回调中避免直接引用Activity，必要时使用弱引用；文件操作后及时释放资源。