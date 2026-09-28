package com.androlua;

import android.util.Log;
import com.luajava.ConsoleBridgeRef;
import com.luajava.JavaFunction;
import com.luajava.LuaException;
import com.luajava.LuaState;

public class LuaPrint extends JavaFunction {

    private static final String TAG = "ConsoleTrace";

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
        Log.d(TAG, "print.execute enter: top=" + top);
        if (top < 2) {
            mLuaContext.sendMsg("");
            return 0;
        }
        boolean bridgeActive = ConsoleBridgeRef.isBridgeActive();
        Log.d(TAG, "print.execute: bridgeActive=" + bridgeActive);
        int[] luaTypes = null;
        Object[] rawArgs = null;
        if (bridgeActive) {
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
        Log.d(TAG, "print.execute: build ok, text.length=" + text.length());
        mLuaContext.sendMsg(text);
        if (bridgeActive) {
            try {
                ConsoleBridgeRef.onPrint(text, luaTypes, rawArgs);
            } catch (Exception ignored) {
                Log.e(TAG, "print.execute: ConsoleBridgeRef.onPrint threw", ignored);
            }
        }
        output.setLength(0);
        return 0;
    }


}

