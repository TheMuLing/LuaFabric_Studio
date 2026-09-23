package com.androlua;

import android.content.Intent;
import android.os.Bundle;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class Main extends LuaActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // TODO: Implement this method
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null && getIntent().getData() != null)
            runFunc("onNewIntent", getIntent());
        if (getIntent().getBooleanExtra("isVersionChanged", false) && (savedInstanceState == null)) {
            onVersionChanged(getIntent().getStringExtra("newVersionName"), getIntent().getStringExtra("oldVersionName"));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // TODO: Implement this method
        runFunc("onNewIntent", intent);
        super.onNewIntent(intent);
    }

    @Override
    public String getLuaDir() {
        // TODO: Implement this method
        return getLocalDir();
    }

    @Override
    public String getLuaPath() {
        // TODO: Implement this method
        initMain();
        return getLocalDir() + "/" + resolveEntryName();
    }

    // 读取打包期写入的 .entry 引导确定入口文件（默认 main.lua，支持子目录相对路径）
    private String resolveEntryName() {
        String entry = "main.lua";
        try {
            File f = new File(getLocalDir(), ".entry");
            if (f.exists() && f.isFile()) {
                String s = new String(new FileInputStream(f).readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!s.isEmpty() && !s.startsWith("/") && !s.contains("../")) {
                    entry = s;
                }
            }
        } catch (Exception ignored) {
        }
        return entry;
    }

    private void onVersionChanged(String newVersionName, String oldVersionName) {
        // TODO: Implement this method
        runFunc("onVersionChanged", newVersionName, oldVersionName);

    }


}
