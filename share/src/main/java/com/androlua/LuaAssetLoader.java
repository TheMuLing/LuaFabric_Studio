package com.androlua;

import android.content.Context;
import android.content.res.AssetManager;

import com.luajava.JavaFunction;
import com.luajava.LuaException;
import com.luajava.LuaState;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class LuaAssetLoader extends JavaFunction {

    private final LuaState L;

    private final Context mContext;

    public LuaAssetLoader(LuaContext luaContext, LuaState L) {
        super(L);
        this.L = L;
        mContext = luaContext.getContext();
    }

    @Override
    public int execute() throws LuaException {
        String name = L.toString(-1);
        name = name.replace('.', '/') + ".lua";
        try {
            loadAsset(name);
            return 1;
        } catch (IOException e) {
            // 回退：优先 assets 根目录，其次 assets/lua/ 子目录
            try {
                loadAsset("lua/" + name);
                return 1;
            } catch (IOException e2) {
                L.pushString("\n\tno file '/assets/" + name + "'");
                return 1;
            }
        }
    }

    private void loadAsset(String name) throws IOException, LuaException {
        byte[] bytes = readAsset(name);
        int ok = L.LloadBuffer(bytes, name);
        if (ok != 0)
            L.pushString("\n\t" + L.toString(-1));
    }

    public byte[] readAsset(String name) throws IOException {
        AssetManager am = mContext.getAssets();
        InputStream is = am.open(name);
        byte[] ret = readAll(is);
        is.close();
        //am.close();
        return ret;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
        byte[] buffer = new byte[4096];
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
        byte[] ret = output.toByteArray();
        output.close();
        return ret;
    }
}

