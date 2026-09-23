# LuaFabric 调试控制台 — 实施计划

## Context

为 Lua 脚本侧构建一款全新调试控制台（完全推翻传统 AndLua+ 悬浮窗 + logcat 轮询架构）。经完整需求 grilling 已锁定架构与 F1\~F7 全部功能规格。控制台原生 UI 承载，仅服务 debugmode 项目，禁随用户软件打包，零 androlua 依赖。

核心设计：core 定义 `DebugConsoleBridge` 接口 + 静态注册表，Lua 宿主/VM 在既有关键闸点转发事件；app 模块 `com.luafabric.console` 实现全部 UI/逻辑。未注册实现（用户包）→ 单次 volatile 判空即 no-op，零行为变化。

## 已验证钩点（承重断言全部读源确认）

| 钩点            | 位置                                                                                                                                                                   | 用途                                                                                           |
| ------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Lua→Java 调用总闸 | `core/.../com/luajava/LuaJavaAPI.java` `callMethod(long, int, String)` L121-285（`obj=L.getJavaObject(idx)` L124，`top=L.getTop()` L128）                               | F2 setContentView / F3 newActivity / F6 runFunc / F1 Toast·Snackbar 观察；VETO→pushNil return 1 |
| bindClass     | `LuaJavaAPI.java` `javaBindClass` L627-631                                                                                                                           | F4 Java 库跟踪                                                                                  |
| 会话开始          | `core/.../com/androlua/LuaActivity.java` `onCreate` L220 `initLua()` 与 L225 `doFile(luaPath,arg)` 之间                                                                 | onSessionStart（须在 doFile 前注入 debugParams）                                                    |
| 会话结束          | `LuaActivity.java` `onDestroy` L611 首部                                                                                                                               | onSessionEnd（Logcat 停止/缓冲归档/浮球移除）                                                            |
| 音量键           | `LuaActivity.java` `onKeyDown` L691-702 前                                                                                                                            | 仅 CLOSED 态消费音量下恢复浮球                                                                          |
| print         | `LuaActivity.java` `initLua` L1193-1194 `new LuaPrint(this,L).register("print")`；实现 `core/.../com/androlua/LuaPrint.java` `execute` L20-47（共享 LuaState，多线程 print 全收） | F1 输出捕获                                                                                      |
| 错误            | `LuaActivity.java` `sendError` L1486-1491                                                                                                                            | F1 error 条目                                                                                  |
| runFunc 触发    | `LuaActivity.java` `runFunc` L1380-1411（`L.isFunction(-1)` 通过后）                                                                                                      | F6（ConsoleCallMarker 标记 Lua 侧显式调用）                                                           |
| require       | `initLua` 内（仿现有 `LuaAssetLoader`/`set`/`call` 注册模式）                                                                                                                  | F4 按文件 require 跟踪                                                                            |
| debugmode     | `LuaActivity.java` `initENV` L1250-1267 `settings.json` `application.debugmode` → `mDebug`（缺 settings.json 时 mDebug 默认 true）                                         | 会话门控                                                                                         |

## 桥（core 新增，非 androlua 命名空间）

`core/src/main/java/com/luafabric/studio/falling/core/console/`

- `DebugConsoleBridge.java` — 接口，全默认空实现：`onSessionStart/onSessionEnd(SessionInfo)`、`onPrint(String text, int[] luaTypes, Object[] rawArgs)`、`onToast(String)`、`onSnackbar(String)`、`onError(String title, String msg)`、`onMethodCall(Object receiver, String methodName, Object[] args, long luaState)→MethodCallResult`、`onKeyDown(int, KeyEvent)→boolean`、`onEvent(String func, Object[] args)`、`onRequire(String)`、`onBindClass(String, Class<?>)`

- `DebugConsoleRegistry.java` — `volatile static bridge` + register/unregister/get；所有 core 钩点 `get()==null` 短路

- `SessionInfo.java` — Activity 持为 `android.app.Activity`（绝不 androlua 类型）+ `LuaState` + `luaPath/luaDir/luaExtDir` + `debugMode` + 时间

- `MethodCallResult.java` — `ALLOW / VETO / REPLACE` + replaceValue

- `ConsoleCallMarker.java` — ThreadLocal 一次性标记：callMethod 拦到 Lua 侧 `runFunc` 时置位，runFunc 触发后消费 → F6「显式调用且实际触发」精确判定

## 控制台模块（app，包 `com.luafabric.console`）

`app/src/main/kotlin/com/luafabric/console/`：`ConsoleInitializer.kt`(ContentProvider 注册桥) · `ConsoleBridgeImpl.kt` · `core/`(ConsoleSession, SessionManager, StateMachine\[IDLE→BALL→PANEL→CLOSED], ConsoleSettings) · `output/`(OutputEntry\[一级内容+标签+MM-DD HH:mm:ss/二级毫秒时间+线程+类型解析], OutputBuffer, BufferPool, OutputManager\[任意线程入队→主线程批量差量刷新], TypeResolver\[一级 lua 类型/二级 java 类+预览+上限+循环防护], OutputExporter\[空行分隔/多行原样/单次最全时间], ClipboardHelper) · `intercept/`(MethodCallRouter, NewActivityInterceptor\[阻塞确认+同文件同参丢弃+异参列表单选+长参 pop]) · `ui/`(ConsoleBallView\[圆角矩形「控制台」], OverlayController\[TYPE\_APPLICATION\_OVERLAY + 权限兜底 addContentView], ConsoleSheet\[Modal BottomSheet + TabLayout 输出/文件/事件/环境/Logcat/调试/设置], tabs/*, adapters/*) · `logcat/`(LogcatCapture\[会话起止], LogcatEntry, LogcatFileStore\[RandomAccessFile 行索引懒加载]) · `env/`(ModuleTracker, LuaEnvironment\[\_VERSION/jit 检测]) · `debug/`(ProjectTreeBuilder, FileLauncher\[debugParams JSON 注入/重启项目/重建当前文件/最后界面二次确认]) · `persist/`(ConsolePaths, CrashCapture\[链 core CrashHandler → crash/ + 浮球变红])

持久化根：`getExternalFilesDir(null)/console/` → `outputs/ logcat/ crash/ sessions/`。

## 实施顺序（9 提交，每提交可编译，仅本地 git）

1. `feat(core): add DebugConsoleBridge registry + gated hooks` — core 桥包 + callMethod 拦截(VETO/REPLACE) + javaBindClass + LuaActivity 会话起止/onKeyDown/sendError/runFunc 标记 + LuaPrint 元数据 + app: ContentProvider 注册 + ConsoleBridgeImpl 空壳。**附带处理未提交的** **`app/src/main/assets/core.apk`（preBuild 产物）**
2. `feat(console): F1 output model + persistence` — 输出模型/缓冲池/类型解析/导出/设置/路径；桥接 onPrint/onToast/onSnackbar/onError
3. `feat(console): ball + sheet + output tab + state machine` — 浮球/面板/输出页/状态机/完全关闭/首次 Toast/音量键恢复
4. `feat(console): F2 file tab` — 当前文件相对路径+复制；布局三态（aly 相对路径|内联布局|无布局）
5. `feat(console): F3 newActivity confirm` — 阻塞确认/取消阻断/同参丢弃/异参列表单选/长参 pop
6. `feat(console): F4 environment tab` — 版本/JIT/按文件 require·bindClass 跟踪/签名（Java 反射、C·Lua getinfo）
7. `feat(console): F5 logcat + crash + ball-red` — 会话起止后台记录 `logcat_<项目名>_<毫秒>.log`/懒加载渲染/仅看错误/崩溃捕获
8. `feat(console): F6 events tab` — runFunc 显式调用+实际触发条目（相对路径+毫秒时间）
9. `feat(console): F7 debug tree + restart/rebuild + archive` — 文件树调起+参数表单（类型下拉+table 树编）/重启项目(归档 sessions/)/重建当前文件/最后界面二次确认

## 验证

- 每提交 `.\gradlew.bat :app:assembleDebug`（JDK21 已钉）；`.\gradlew.bat :core:assembleDebug` + `:core-apk:assembleRelease` 证明用户包无控制台代码

- 实机 debugmode 项目：浮球出现→面板各页→打印含一级标签/二级展开→toast/snackbar 条目→完全关闭+首次 Toast+音量下恢复（其余状态不消费）→布局三态→newActivity 确认/阻断/列表→环境签名→logcat 文件+仅看错误→错误/崩溃条目+浮球变红→事件页→文件树调起+参数注入→重启/重建/最后界面二次确认→多选复制/导出（空行分隔、单次最全时间）

- 反例：debugmode=false 无浮球；core-apk 打包应用桥为空 → 钩子全 no-op

## 风险

- F3 阻塞确认：主线程 re-entrant Looper 泵；子线程 CountDownLatch；弹窗过程不触碰 Lua 栈

- RecyclerView 虚拟化：F1 内存缓冲行数上限；F5 文件 RandomAccessFile 行索引按可见块渲染

- 悬浮权限缺失 → `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` + `addContentView` 兜底

- Toast/Snackbar 静态调用在 callMethod 呈 `obj instanceof Class`+`makeText/make`；Snackbar 仅拦 `make` 防重复；参数转换 try/catch 只读不弹栈

- settings.json 缺失 → mDebug 默认 true（现状即调试态，文档化）

- 参数深比较：IdentityHashMap 环防护；复用 `muling.views.tool.utils.JsonUtil` 序列化展示

- debugParams 注入须在 onSessionStart（doFile 前）经 `LuaState.newTable/setField` 建真 Lua table，跨 Intent 用 JSON extra

- R8：`com.luafabric.console.**` keep 规则（ContentProvider 靠清单引用）

- logcat 自读 `--pid=<own>` 尽力而为，失败不影响（崩溃兜底已覆盖）

