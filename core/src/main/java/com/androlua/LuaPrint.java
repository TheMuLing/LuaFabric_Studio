package com.androlua;

import com.luajava.JavaFunction;
import com.luajava.LuaException;
import com.luajava.LuaState;
import com.luafabric.studio.falling.core.console.DebugConsoleBridge;
import com.luafabric.studio.falling.core.console.DebugConsoleRegistry;

public class LuaPrint extends JavaFunction {

    private final LuaState L;
    private final LuaContext mLuaContext;
    private final StringBuilder output = new StringBuilder();

    public LuaPrint(LuaContext luaContext, LuaState L) {
        super(L);
        this.L = L;
        mLuaContext = luaContext;
    }

    @Override
    public int execute() throws LuaException {
        int top = L.getTop();
        if (top < 2) {
            mLuaContext.sendMsg("");
            return 0;
        }
        DebugConsoleBridge bridge = DebugConsoleRegistry.get();
        int[] luaTypes = null;
        Object[] rawArgs = null;
        if (bridge != null) {
            luaTypes = new int[top - 1];
            rawArgs = new Object[top - 1];
            for (int i = 2; i <= top; i++) {
                luaTypes[i - 2] = L.type(i);
                try {
                    rawArgs[i - 2] = L.toJavaObject(i);
                } catch (Exception e) {
                    rawArgs[i - 2] = null;
                }
            }
        }
        for (int i = 2; i <= top; i++) {
            int type = L.type(i);
            String val = null;
            String stype = L.typeName(type);
            if (stype.equals("userdata")) {
                Object obj = L.toJavaObject(i);
                if (obj != null)
                    val = obj.toString();
            } else if (stype.equals("boolean")) {
                val = L.toBoolean(i) ? "true" : "false";
            } else {
                val = L.LtoString(i);
            }
            if (val == null)
                val = stype;
            output.append("\t");
            output.append(val);
            output.append("\t");
        }
        String text = output.toString().substring(1, output.length() - 1);
        mLuaContext.sendMsg(text);
        if (bridge != null) {
            try {
                bridge.onPrint(text, luaTypes, rawArgs);
            } catch (Exception ignored) {
            }
        }
        output.setLength(0);
        return 0;
    }


}

