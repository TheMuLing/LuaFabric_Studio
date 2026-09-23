package com.luajava;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * LuaJavaAPI 与 view 侧 cglib 代理（LuaEnhancer / com.android.cglib.proxy.*）的解耦接缝。
 *
 * <p>share 编译期不得引用 view 的任何类型，因此在运行时按类名反射查找：
 * <ul>
 *   <li>classic 产物（view-apk）：dex 内含 LuaEnhancer 与 cglib → 走真实代理；</li>
 *   <li>compose 产物（compose-apk）：无 cglib → {@link #create} 抛 {@link LuaException}（代理不可用）。</li>
 * </ul>
 */
public final class LuaProxyFactory {

    /** 等价于 cglib 的 MethodFilter，但本类自持接口，避免编译期依赖 view。 */
    public interface LuaProxyFilter {
        boolean filter(Method method, String name);
    }

    private static final String ENHANCER_CLS = "com.androlua.LuaEnhancer";
    private static final String METHOD_FILTER_CLS = "com.android.cglib.proxy.MethodFilter";
    private static final String SET_INTERCEPTOR_METHOD = "setMethodInterceptor_Enhancer";

    private LuaProxyFactory() {
    }

    /** 创建代理类。当前运行时无 cglib 支持时抛 {@link LuaException}。 */
    public static Class<?> create(Class<?> clazz, LuaProxyFilter filter) throws LuaException {
        try {
            Class<?> enhancer = Class.forName(ENHANCER_CLS);
            Object enhancerInstance = enhancer.getConstructor(Class.class).newInstance(clazz);
            Object cglibFilter = toCglibFilter(filter);
            Method create = enhancer.getMethod("create", Class.forName(METHOD_FILTER_CLS));
            Object result = create.invoke(enhancerInstance, cglibFilter);
            if (!(result instanceof Class)) {
                throw new LuaException("proxy create returned non-class: " + result);
            }
            return (Class<?>) result;
        } catch (ClassNotFoundException e) {
            throw new LuaException(
                    "proxy is not supported in this runtime (com.androlua.LuaEnhancer missing): " + e);
        } catch (LuaException e) {
            throw e;
        } catch (Exception e) {
            throw new LuaException("create proxy failed: " + e);
        }
    }

    /**
     * 给代理实例装配方法拦截器（cglib 的 EnhancerInterface.setMethodInterceptor_Enhancer）。
     * interceptor 实例的运行时类型即声明参数类型（LuaMethodInterceptor / LuaAbstractMethodInterceptor），
     * 按可赋值匹配查找方法避免反射精确类型失配。
     */
    public static void setInterceptor(Object target, Object interceptor) throws LuaException {
        try {
            for (Method m : target.getClass().getMethods()) {
                if (!SET_INTERCEPTOR_METHOD.equals(m.getName())) {
                    continue;
                }
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 1 && pts[0].isInstance(interceptor)) {
                    m.invoke(target, interceptor);
                    return;
                }
            }
            throw new NoSuchMethodException(SET_INTERCEPTOR_METHOD);
        } catch (Exception e) {
            throw new LuaException("set proxy interceptor failed: " + e);
        }
    }

    /** 把本包 LuaProxyFilter 桥到 cglib MethodFilter（JDK 动态代理，免编译期依赖）。 */
    private static Object toCglibFilter(final LuaProxyFilter filter) throws Exception {
        Class<?> iface = Class.forName(METHOD_FILTER_CLS);
        return Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("filter".equals(method.getName()) && args != null && args.length == 2
                                && args[0] instanceof Method) {
                            return filter.filter((Method) args[0], (String) args[1]);
                        }
                        if ("toString".equals(method.getName())) {
                            return "LuaProxyFilter{cglib}";
                        }
                        return null;
                    }
                });
    }
}