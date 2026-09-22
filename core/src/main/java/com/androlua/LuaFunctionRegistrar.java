package com.androlua;

import android.content.Context;
import android.util.Log;

import androidx.recyclerview.widget.RecyclerView;

import muling.views.tool.utils.LuaRecyclerAdapter;
import com.luajava.JavaFunction;
import com.luajava.LuaException;
import com.luajava.LuaObject;
import com.luajava.LuaState;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LuaFunctionRegistrar {

    private static final String TAG = "LuaFunctionRegistrar";

    // 工具类映射表，用于快速查找
    private static final Map<String, String> UTIL_CLASS_MAP =
            new HashMap<String, String>() {
                {
                    put("UiUtil", "muling.views.tool.utils.UiUtil");
                    put("RecyclerAdapterUtil", "muling.views.tool.utils.RecyclerAdapterUtil");
                }
            };

    private final LuaState L;
    private final Context context;
    private final String luaDir;

    public LuaFunctionRegistrar(LuaState L, Context context, String luaDir) {
        this.L = L;
        this.context = context;
        this.luaDir = luaDir;
    }

    /**
     * 根据提供的工具类名称列表选择性注册函数
     */
    public void registerSelectedFunctions(List<String> selectedUtils) {

        // 将列表转换为集合以便快速查找
        Set<String> selectedSet = new HashSet<>(selectedUtils);

        try {
            // 注册选中的工具类
            for (String utilName : selectedSet) {
                String className = UTIL_CLASS_MAP.get(utilName);
                if (className != null) {
                    registerUtilClass(className);
                } else {
                    Log.w(TAG, "未找到工具类: " + utilName);
                }
            }

            if (selectedSet.contains("RecyclerAdapterUtil")) {
                registerRecyclerAdapterFunctions();
            }

            // 注册通用函数（无论选择什么工具类都需要的）
            registerCommonFunctions();

        } catch (Exception e) {
            Log.e(TAG, "选择性注册工具类函数时出错", e);
            e.printStackTrace();
        }
    }

    /**
     * 注册单个工具类
     */
    private void registerUtilClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Method[] methods = clazz.getMethods();

            for (final Method method : methods) {
                // 只处理公共静态方法
                if (!Modifier.isStatic(method.getModifiers())
                        || !Modifier.isPublic(method.getModifiers())) {
                    continue;
                }

                // 跳过Kotlin自动生成的方法
                if (method.getName().contains("$")) {
                    continue;
                }

                final String methodName = method.getName();

                // 检查是否需要Context参数
                final Class<?>[] paramTypes = method.getParameterTypes();
                final boolean needsContext =
                        paramTypes.length > 0 && Context.class.isAssignableFrom(paramTypes[0]);

                // 检查是否是可变参数方法
                final boolean isVarArgs = method.isVarArgs();

                // 为这个方法创建Lua函数
                L.pushJavaFunction(
                        new JavaFunction(L) {
                            @Override
                            public int execute() throws LuaException {
                                try {
                                    // 计算实际需要的Lua参数数量
                                    int paramCount = paramTypes.length;
                                    int luaArgCount = L.getTop() - 1;

                                    // 准备参数数组
                                    Object[] args = new Object[paramCount];
                                    int luaIndex = 2; // Lua参数从索引2开始

                                    // 如果需要Context，自动注入当前context
                                    if (needsContext) {
                                        args[0] = context;
                                    }

                                    // 处理固定参数（可变参数前的参数）
                                    int fixedParamCount = isVarArgs ? paramCount - 1 : paramCount;
                                    for (int i = needsContext ? 1 : 0; i < fixedParamCount; i++) {
                                        if (luaIndex <= L.getTop()) {
                                            args[i] = L.toJavaObject(luaIndex);
                                            luaIndex++;
                                        } else {
                                            // 缺少参数，设为null
                                            args[i] = null;
                                        }
                                    }

                                    // 处理可变参数（如果有）
                                    if (isVarArgs) {
                                        // 计算可变参数数量
                                        int varArgCount = luaArgCount - (fixedParamCount - (needsContext ? 1 : 0));

                                        if (varArgCount > 0) {
                                            // 创建数组来保存可变参数
                                            Class<?> varArgType = paramTypes[paramCount - 1].getComponentType();
                                            Object[] varArgs =
                                                    (Object[]) java.lang.reflect.Array.newInstance(varArgType, varArgCount);

                                            // 从Lua栈中获取可变参数
                                            for (int i = 0; i < varArgCount; i++) {
                                                varArgs[i] = L.toJavaObject(luaIndex);
                                                luaIndex++;
                                            }

                                            args[paramCount - 1] = varArgs;
                                        } else {
                                            // 如果没有可变参数，传入空数组
                                            Class<?> varArgType = paramTypes[paramCount - 1].getComponentType();
                                            args[paramCount - 1] = java.lang.reflect.Array.newInstance(varArgType, 0);
                                        }
                                    }

                                    // 调用方法
                                    Object result = method.invoke(null, args);

                                    // 处理返回值
                                    if (method.getReturnType() == void.class
                                            || method.getReturnType() == Void.class) {
                                        return 0; // 没有返回值
                                    } else {
                                        L.pushObjectValue(result);
                                        return 1; // 有返回值
                                    }

                                } catch (Exception e) {
                                    Log.e(TAG, "调用方法 " + methodName + " 失败: " + e.getMessage());
                                    e.printStackTrace();
                                    throw new LuaException("Failed to call " + methodName + ": " + e.getMessage());
                                }
                            }
                        });

                // 注册到Lua全局变量
                L.setGlobal(methodName);
            }

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void registerRecyclerAdapterFunctions() {
        try {

            // 注册createAdapter方法
            JavaFunction createAdapterFunc = new JavaFunction(L) {
                @Override
                public int execute() throws LuaException {
                    try {
                        // 获取参数：data, list_item, method
                        int top = L.getTop();
                        if (top < 3) {
                            throw new LuaException("参数不足，需要data, list_item, method三个参数");
                        }

                        // 获取data参数
                        List<Object> dataList = new ArrayList<>();

                        // 参数1是data表
                        if (L.isTable(1)) {
                            // 使用LuaTable的asArray()方法将Lua表转换为Java数组
                            LuaObject luaData = L.getLuaObject(1);

                            // 检查是否为数组表
                            if (luaData.isTable()) {
                                try {
                                    // 使用asArray()方法将Lua表转换为Object数组
                                    Object[] array = luaData.asArray();
                                    if (array != null) {
                                        // 将数组转换为List
                                        dataList = Arrays.asList(array);
                                    } else {
                                        // 如果asArray()返回null，尝试手动遍历
                                        int len = L.rawLen(1);
                                        for (int i = 1; i <= len; i++) {
                                            L.pushInteger(i);
                                            L.getTable(1);
                                            try {
                                                dataList.add(L.toJavaObject(-1));
                                            } catch (Exception e) {
                                                // 忽略转换错误
                                            }
                                            L.pop(1);
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "转换Lua表为数组失败: " + e.getMessage(), e);
                                    // 备用方法：手动遍历
                                    int len = L.rawLen(1);
                                    for (int i = 1; i <= len; i++) {
                                        L.pushInteger(i);
                                        L.getTable(1);
                                        try {
                                            dataList.add(L.toJavaObject(-1));
                                        } catch (Exception e2) {
                                            // 忽略转换错误
                                        }
                                        L.pop(1);
                                    }
                                }
                            }
                        } else if (L.isUserdata(1)) {
                            Object obj = L.toJavaObject(1);
                            if (obj instanceof List) {
                                dataList = (List<Object>) obj;
                            }
                        }

                        // 获取list_item参数
                        Object listItem;
                        int type = L.type(2);

                        switch (type) {
                            case LuaState.LUA_TSTRING:
                                listItem = L.toString(2);
                                break;
                            case LuaState.LUA_TNUMBER:
                                if (L.isInteger(2)) {
                                    listItem = (int) L.toInteger(2);
                                } else {
                                    listItem = L.toNumber(2);
                                }
                                break;
                            case LuaState.LUA_TTABLE:
                                listItem = L.getLuaObject(2);
                                break;
                            default:
                                listItem = L.toJavaObject(2);
                                break;
                        }

                        // 获取method参数
                        LuaObject method = L.getLuaObject(3);

                        // 调用RecyclerAdapterUtil.createAdapter
                        LuaRecyclerAdapter adapter =
                                muling.views.tool.utils.RecyclerAdapterUtil.createAdapter(
                                        context,
                                        dataList,
                                        listItem,
                                        method
                                );

                        L.pushJavaObject(adapter);
                        return 1;

                    } catch (Exception e) {
                        Log.e(TAG, "创建RecyclerAdapter失败: " + e.getMessage(), e);
                        throw new LuaException("创建RecyclerAdapter失败: " + e.getMessage());
                    }
                }
            };
            createAdapterFunc.register("createRecyclerAdapter");

            // 注册notifyDataSetChanged方法
            JavaFunction notifyDataSetChangedFunc = new JavaFunction(L) {
                @Override
                public int execute() throws LuaException {
                    try {
                        if (L.getTop() < 1) {
                            throw new LuaException("参数不足，需要adapter参数");
                        }

                        LuaObject adapterObj = L.getLuaObject(1);
                        if (adapterObj.isUserdata()) {
                            Object obj = adapterObj.getObject();
                            if (obj instanceof LuaRecyclerAdapter) {
                                ((LuaRecyclerAdapter) obj).notifyDataSetChanged();
                            } else if (obj instanceof RecyclerView.Adapter) {
                                ((RecyclerView.Adapter) obj).notifyDataSetChanged();
                            }
                        }
                        return 0;
                    } catch (Exception e) {
                        throw new LuaException(e);
                    }
                }
            };
            notifyDataSetChangedFunc.register("notifyDataSetChanged");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册一些通用的Lua函数
     */
    private void registerCommonFunctions() {
        try {
            // 注册获取Lua目录的函数
            JavaFunction getLuaDirectory =
                    new JavaFunction(L) {
                        @Override
                        public int execute() throws LuaException {
                            L.pushString(luaDir);
                            return 1;
                        }
                    };
            getLuaDirectory.register("getLuaDir");
        } catch (Exception e) {
        }
    }
}