package com.androlua;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Environment;

import com.luajava.LuaException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

import dalvik.system.DexClassLoader;

public class LuaDexLoader {
    private static final HashMap<String, LuaDexClassLoader> dexCache = new HashMap<String, LuaDexClassLoader>();
    private final ArrayList<ClassLoader> dexList = new ArrayList<ClassLoader>();
    private final HashMap<String, String> libCache = new HashMap<String, String>();

    private final LuaContext mContext;

    private final String luaDir;

    private AssetManager mAssetManager;

    private LuaResources mResources;
    private Resources.Theme mTheme;
    private final String odexDir;
    private final String privateLibsDir;

    public LuaDexLoader(LuaContext context) {
        mContext = context;
        luaDir = context.getLuaDir();
        LuaApplication app = LuaApplication.getInstance();
        odexDir = app.getOdexDir();

        // 初始化私有libs目录
        Context ctx = mContext.getContext();
        privateLibsDir = new File(ctx.getFilesDir(), "private_libs").getAbsolutePath();
        File dir = new File(privateLibsDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public Resources.Theme getTheme() {
        return mTheme;
    }

    public ArrayList<ClassLoader> getClassLoaders() {
        return dexList;
    }

    public LuaDexClassLoader loadApp(String pkg) {
        try {
            LuaDexClassLoader dex = dexCache.get(pkg);
            if (dex == null) {
                PackageManager manager = mContext.getContext().getPackageManager();
                ApplicationInfo info = manager.getPackageInfo(pkg, 0).applicationInfo;
                dex = new LuaDexClassLoader(info.publicSourceDir,
                        LuaApplication.getInstance().getOdexDir(),
                        info.nativeLibraryDir,
                        mContext.getContext().getClassLoader());
                dexCache.put(pkg, dex);
            }
            if (!dexList.contains(dex)) {
                dexList.add(dex);
            }
            return dex;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void loadLibs() throws LuaException {
        File[] libs = new File(mContext.getLuaDir() + "/libs").listFiles();
        if (libs == null)
            return;
        for (File f : libs) {
            if (f.isDirectory())
                continue;
            if (f.getAbsolutePath().endsWith(".so"))
                loadLib(f.getName());
            else
                loadDex(f.getAbsolutePath());
        }
    }

    public void loadLib(String name) throws LuaException {
        String fn = name;
        int i = name.indexOf(".");
        if (i > 0)
            fn = name.substring(0, i);
        if (fn.startsWith("lib"))
            fn = fn.substring(3);
        String libDir = mContext.getContext().getDir(fn, Context.MODE_PRIVATE).getAbsolutePath();
        String libPath = libDir + "/lib" + fn + ".so";
        File f = new File(libPath);
        if (!f.exists()) {
            f = new File(luaDir + "/libs/lib" + fn + ".so");
            if (!f.exists())
                throw new LuaException("can not find lib " + name);
            LuaUtil.copyFile(luaDir + "/libs/lib" + fn + ".so", libPath);
        }
        libCache.put(fn, libPath);
    }

    public HashMap<String, String> getLibrarys() {
        return libCache;
    }

    public DexClassLoader loadDex(String path) throws LuaException {
        LuaDexClassLoader dex = dexCache.get(path);
        if (dex == null)
            dex = loadApp(path);
        if (dex == null) {
            String name = path;
            if (path.charAt(0) != '/')
                path = luaDir + "/" + path;

            // 检查文件是否存在
            File srcFile = new File(path);
            if (!srcFile.exists()) {
                if (new File(path + ".dex").exists())
                    path += ".dex";
                else if (new File(path + ".jar").exists())
                    path += ".jar";
                else
                    throw new LuaException(path + " not found");
                srcFile = new File(path);
            }

            // Android 14+ 需要特殊处理
            String finalPath = path;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
                // Android 14+：文件必须是只读的
                finalPath = prepareFileForAndroid14(srcFile);
            }

            String id = LuaUtil.getFileMD5(finalPath);
            if (id != null && id.equals("0"))
                id = name;
            dex = dexCache.get(id);

            if (dex == null) {
                dex = new LuaDexClassLoader(finalPath, odexDir,
                        LuaApplication.getInstance().getApplicationInfo().nativeLibraryDir,
                        mContext.getContext().getClassLoader());
                dexCache.put(id, dex);
            }
        }

        if (!dexList.contains(dex)) {
            dexList.add(dex);
            String dexPath = dex.getDexPath();
            if (dexPath.endsWith(".jar"))
                loadResources(dexPath);
        }
        return dex;
    }

    /**
     * Android 14+ 特殊处理：确保文件是只读的
     */
    private String prepareFileForAndroid14(File srcFile) throws LuaException {
        try {
            // 检查文件是否在外部存储中
            if (isInExternalStorage(srcFile)) {
                // 复制到私有目录
                return copyToPrivateDirWithReadOnly(srcFile);
            } else {
                // 文件已经在私有目录，确保是只读的
                if (srcFile.exists() && srcFile.canWrite()) {
                    srcFile.setReadOnly();
                }
                return srcFile.getAbsolutePath();
            }
        } catch (Exception e) {
            throw new LuaException("Failed to prepare file for Android 14: " + e.getMessage());
        }
    }

    /**
     * 检查文件是否在外部存储（可写的公共目录）中
     */
    private boolean isInExternalStorage(File file) {
        try {
            if (Environment.isExternalStorageRemovable()) {
                return false;
            }

            String filePath = file.getAbsolutePath();
            String[] externalPaths = {
                    Environment.getExternalStorageDirectory().getAbsolutePath(),
                    "/sdcard",
                    "/storage/emulated",
                    "/mnt/sdcard"
            };

            for (String externalPath : externalPaths) {
                if (filePath.startsWith(externalPath)) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 将文件复制到应用私有目录并设置为只读（Android 14+ 安全模式）
     */
    private String copyToPrivateDirWithReadOnly(File srcFile) throws LuaException, IOException {
        // 创建私有目录（如果不存在）
        File dir = new File(privateLibsDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new LuaException("Failed to create directory: " + privateLibsDir);
            }
        }

        // 创建目标文件路径（保持文件名）
        String fileName = srcFile.getName();
        File destFile = new File(dir, fileName);

        // 如果文件已存在，检查是否需要更新
        if (destFile.exists()) {
            // 检查文件大小和MD5是否相同
            long srcSize = srcFile.length();
            long destSize = destFile.length();

            if (srcSize == destSize) {
                // 文件大小相同，检查MD5
                String srcMd5 = LuaUtil.getFileMD5(srcFile.getAbsolutePath());
                String destMd5 = LuaUtil.getFileMD5(destFile.getAbsolutePath());

                if (srcMd5 != null && srcMd5.equals(destMd5)) {
                    // 文件相同，确保文件是只读的
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        destFile.setReadOnly();
                    }
                    return destFile.getAbsolutePath();
                }
            }

            // 文件不同或无法获取MD5，删除旧文件
            if (!destFile.delete()) {
                // 如果删除失败，可以尝试重命名旧文件
                File oldFile = new File(dir, fileName + ".old." + System.currentTimeMillis());
                destFile.renameTo(oldFile);
            }
        }

        // 根据文档要求，在写入前将文件设置为只读（避免竞态条件）
        // 先创建空文件并设置为只读
        destFile.createNewFile();
        destFile.setReadOnly();

        // 但是，我们需要写入文件内容，所以需要重新打开为可写
        // 创建一个临时文件写入，然后重命名
        File tempFile = new File(dir, fileName + ".tmp." + System.currentTimeMillis());

        try {
            // 复制文件到临时文件
            LuaUtil.copyFile(srcFile.getAbsolutePath(), tempFile.getAbsolutePath());

            // 验证复制是否成功
            if (!tempFile.exists()) {
                throw new LuaException("Failed to create temporary file");
            }

            long srcSize = srcFile.length();
            long tempSize = tempFile.length();
            if (srcSize != tempSize) {
                throw new LuaException("File size mismatch (source: " + srcSize + ", temp: " + tempSize + ")");
            }

            // 删除只读的空文件
            destFile.delete();

            // 将临时文件重命名为目标文件
            if (!tempFile.renameTo(destFile)) {
                throw new LuaException("Failed to rename temporary file to destination");
            }

            // 设置目标文件为只读
            destFile.setReadOnly();

            // 验证最终文件
            if (!destFile.exists()) {
                throw new LuaException("Destination file does not exist after rename");
            }

            return destFile.getAbsolutePath();

        } finally {
            // 清理临时文件
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public void loadResources(String path) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            int ok = (int) addAssetPath.invoke(assetManager, path);
            if (ok == 0)
                return;
            mAssetManager = assetManager;
            Resources superRes = mContext.getContext().getResources();
            mResources = new LuaResources(mAssetManager, superRes.getDisplayMetrics(),
                    superRes.getConfiguration());
            mResources.setSuperResources(superRes);
            mTheme = mResources.newTheme();
            mTheme.setTo(mContext.getContext().getTheme());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public AssetManager getAssets() {
        return mAssetManager;
    }

    public Resources getResources() {
        return mResources;
    }

    /**
     * 清理私有目录中的旧文件
     */
    public void cleanupOldFiles() {
        try {
            File dir = new File(privateLibsDir);
            if (!dir.exists() || !dir.isDirectory()) {
                return;
            }

            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }

            // 清理超过30天的文件
            long cutoff = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L);

            for (File file : files) {
                // 不清理正在使用的文件
                if (file.lastModified() < cutoff) {
                    // 检查是否是.tmp文件或者.old文件
                    if (file.getName().contains(".tmp.") || file.getName().contains(".old.")) {
                        file.delete();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}