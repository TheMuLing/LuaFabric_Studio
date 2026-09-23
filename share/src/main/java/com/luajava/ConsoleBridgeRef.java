package com.luajava;

import android.app.Activity;

/**
 * 调试控制台桥的反射门面（compose 产物瘦身用）。
 *
 * <p>console 契约类（com.luafabric.studio.falling.core.console.*）位于 share，但仅 IDE 宿主
 * （app 注册的 DebugConsoleBridge 实现）需要；compose 打包产物禁止包含控制台。share 内的
 * LuaJavaAPI / LuaPrint / compose LuaActivity 若编译期 import console 类，R8 shrink 无法剃除。
 * 本门面以反射访问 console 类，编译期零依赖：产物无 console 类时各方法优雅短路（等同桥未注册）。
 *
 * <p>行为与原 DebugConsoleBridge 短路语义一致：桥未注册 / 反射失败 → 全部放行、无副作用。
 */
public final class ConsoleBridgeRef {

    private static final String REGISTRY_CLS =
            "com.luafabric.studio.falling.core.console.DebugConsoleRegistry";
    private static final String BRIDGE_CLS =
            "com.luafabric.studio.falling.core.console.DebugConsoleBridge";
    private static final String SESSION_CLS =
            "com.luafabric.studio.falling.core.console.SessionInfo";
    private static final String RESULT_CLS =
            "com.luafabric.studio.falling.core.console.MethodCallResult";

    private ConsoleBridgeRef() {}

    /** 方法调用拦截结果（console 的 MethodCallResult 反射映射）。 */
    public static final class InterceptResult {
        public final int status; // 0=ALLOW 1=VETO 2=REPLACE
        public final Object replaceValue;

        InterceptResult(int status, Object replaceValue) {
            this.status = status;
            this.replaceValue = replaceValue;
        }

        public boolean isVeto() { return status == 1; }
        public boolean isReplace() { return status == 2; }
    }

    private static final InterceptResult ALLOW = new InterceptResult(0, null);

    /** 放行结果（桥未注册/反射失败/不感兴趣场景统一返回）。 */
    public static InterceptResult allow() {
        return ALLOW;
    }

    // ---- 反射句柄缓存（首次成功访问后固化为 null-safe 快速路径） ----
    private static Class<?> sRegistry;
    private static volatile boolean sInit;

    private static Class<?> bridgeInterface() {
        return tryClass(BRIDGE_CLS);
    }

    private static Class<?> sessionClass() {
        return tryClass(SESSION_CLS);
    }

    private static Class<?> registry() {
        Class<?> c = sRegistry;
        if (c != null) return c;
        if (sInit) return null;
        synchronized (ConsoleBridgeRef.class) {
            if (sRegistry == null && !sInit) {
                sRegistry = tryClass(REGISTRY_CLS);
                sInit = true;
            }
            return sRegistry;
        }
    }

    private static Class<?> tryClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object callStatic(Class<?> owner, String method, Object... args) {
        if (owner == null) return null;
        try {
            Class<?>[] argTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) argTypes[i] = args[i].getClass();
            return owner.getMethod(method, argTypes).invoke(null, args);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object call(Object receiver, String method, Class<?>[] argTypes, Object... args) {
        if (receiver == null || argTypes == null) return null;
        try {
            return receiver.getClass().getMethod(method, argTypes).invoke(receiver, args);
        } catch (Throwable t) {
            return null;
        }
    }

    /** DebugConsoleRegistry.get()；无 console 类时 null。 */
    public static Object getBridge() {
        return callStatic(registry(), "get");
    }

    /** 桥是否已注册（调 onPrint 等回调前判定，避免反射探活开销）。 */
    public static boolean isBridgeActive() {
        return getBridge() != null;
    }

    /** onMethodCall 拦截：bridge.onMethodCall(receiver, method, args, luaState) → InterceptResult。 */
    public static InterceptResult interceptMethodCall(Object bridge, Object receiver, String method, Object[] args, long luaState) {
        if (bridge == null) return ALLOW;
        try {
            Object result = bridge.getClass()
                    .getMethod("onMethodCall", Object.class, String.class, Object[].class, long.class)
                    .invoke(bridge, receiver, method, args, luaState);
            if (result == null) return ALLOW;
            Class<?> resultCls = tryClass(RESULT_CLS);
            if (resultCls == null || !resultCls.isInstance(result)) return ALLOW;
            Object statusObj = result.getClass().getField("status").get(result);
            Object replaceValue = result.getClass().getField("replaceValue").get(result);
            String statusName = statusObj == null ? "ALLOW" : statusObj.toString();
            if ("VETO".equals(statusName)) return new InterceptResult(1, replaceValue);
            if ("REPLACE".equals(statusName)) return new InterceptResult(2, replaceValue);
            return ALLOW;
        } catch (Throwable t) {
            return ALLOW;
        }
    }

    /** 便捷重载：自行取桥后拦截（调用方已判空时可省一次探活）。 */
    public static InterceptResult interceptMethodCall(Object receiver, String method, Object[] args, long luaState) {
        return interceptMethodCall(getBridge(), receiver, method, args, luaState);
    }

    /** onPopupCaptured(instance, text, snackbar) —— Toast/Snackbar.make 工厂登记。 */
    public static void onPopupCaptured(Object instance, String text, boolean snackbar) {
        Object bridge = getBridge();
        if (bridge == null) return;
        call(bridge, "onPopupCaptured",
                new Class<?>[]{Object.class, String.class, boolean.class},
                instance, text, snackbar);
    }

    /** onPopupShown(instance) —— 实例 show() 配对。 */
    public static void onPopupShown(Object instance) {
        Object bridge = getBridge();
        if (bridge == null) return;
        call(bridge, "onPopupShown", new Class<?>[]{Object.class}, instance);
    }

    /** onBindClass(className, clazz)。 */
    public static void onBindClass(String className, Class<?> clazz) {
        Object bridge = getBridge();
        if (bridge == null) return;
        call(bridge, "onBindClass",
                new Class<?>[]{String.class, Class.class}, className, clazz);
    }

    /** onPrint(text, luaTypes, rawArgs)。 */
    public static void onPrint(String text, int[] luaTypes, Object[] rawArgs) {
        Object bridge = getBridge();
        if (bridge == null) return;
        call(bridge, "onPrint",
                new Class<?>[]{String.class, int[].class, Object[].class}, text, luaTypes, rawArgs);
    }

    /** 构造 SessionInfo（反射 new）；无 console 类时 null。 */
    public static Object newSessionInfo(
            Activity activity, LuaState L, String luaPath, String luaDir, String luaExtDir,
            long startTimeMs, boolean debugMode, String debugParamsJson) {
        Class<?> cls = sessionClass();
        if (cls == null) return null;
        try {
            return cls.getConstructor(
                            Activity.class, LuaState.class, String.class, String.class, String.class,
                            long.class, boolean.class, String.class)
                    .newInstance(activity, L, luaPath, luaDir, luaExtDir, startTimeMs, debugMode, debugParamsJson);
        } catch (Throwable t) {
            return null;
        }
    }

    /** onSessionStart(info)。 */
    public static void onSessionStart(Object session) {
        Object bridge = getBridge();
        if (bridge == null || session == null) return;
        Class<?> sessionCls = tryClass(SESSION_CLS);
        if (sessionCls == null) return;
        call(bridge, "onSessionStart", new Class<?>[]{sessionCls}, session);
    }

    /** onSessionEnd(info)。 */
    public static void onSessionEnd(Object session) {
        Object bridge = getBridge();
        if (bridge == null || session == null) return;
        Class<?> sessionCls = tryClass(SESSION_CLS);
        if (sessionCls == null) return;
        call(bridge, "onSessionEnd", new Class<?>[]{sessionCls}, session);
    }

    /** DebugConsoleRegistry.isErrorToastEnabled()。 */
    public static boolean isErrorToastEnabled() {
        Class<?> cls = registry();
        if (cls == null) return false;
        try {
            Object ret = cls.getMethod("isErrorToastEnabled").invoke(null);
            return Boolean.TRUE.equals(ret);
        } catch (Throwable t) {
            return false;
        }
    }

    /** onError(title, message)。 */
    public static void onError(String title, String message) {
        Object bridge = getBridge();
        if (bridge == null) return;
        call(bridge, "onError",
                new Class<?>[]{String.class, String.class}, title, message);
    }

    /** onKeyDown(keyCode, event) —— 返回 true 表示消费按键。 */
    public static boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        Object bridge = getBridge();
        if (bridge == null) return false;
        try {
            Object ret = bridge.getClass()
                    .getMethod("onKeyDown", int.class, android.view.KeyEvent.class)
                    .invoke(bridge, keyCode, event);
            return Boolean.TRUE.equals(ret);
        } catch (Throwable t) {
            return false;
        }
    }

    /** onRequire(moduleName, funcParams, nativeModule) —— require 后枚举库函数签名。 */
    public static void onRequire(String moduleName, java.util.Map<String, Integer> funcParams, boolean nativeModule) {
        Object bridge = getBridge();
        if (bridge == null) return;
        call(bridge, "onRequire",
                new Class<?>[]{String.class, java.util.Map.class, boolean.class},
                moduleName, funcParams, nativeModule);
    }

    /** onEvent(funcName, args) —— 显式定义的 Lua 函数实际被调用。 */
    public static void onEvent(String funcName, Object[] args) {
        Object bridge = getBridge();
        if (bridge == null) return;
        call(bridge, "onEvent",
                new Class<?>[]{String.class, Object[].class}, funcName, args);
    }
}