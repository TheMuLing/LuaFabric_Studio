package com.luafabric.compose;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.RequiresApi;

import com.androlua.LuaApplication;
import com.androlua.LuaAssetLoader;
import com.androlua.LuaContext;
import com.androlua.LuaDexLoader;
import com.androlua.LuaGcable;
import com.androlua.LuaPrint;
import com.androlua.LuaResources;
import com.androlua.LuaUtil;
import com.luafabric.compose.ui.ComposeUiHost;
import com.luafabric.compose.utils.ComposeConfig;
import com.luajava.ConsoleBridgeRef;
import com.luajava.JavaFunction;
import com.luajava.LuaException;
import com.luajava.LuaObject;
import com.luajava.LuaState;
import com.luajava.LuaStateFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import dalvik.system.DexClassLoader;

/**
 * Compose 运行时精简 Activity（compose 打包基底 + IDE 双重身份宿主）。
 *
 * 与 view 版 LuaActivity 的差异（删除项均有 compose 宿主替代，见 build.gradle.b85 配套 ui.* 宿主）：
 *   - 继承 ComponentActivity（非 AppCompatActivity），无 appcompat
 *   - 删除 status/list/layout 兜底 UI、setContentView(String/LuaObject) 重载
 *     （布局由 ui.render 数据树接管；无数据树的组装失败走 Toast + 控制台错误）
 *   - 删除传统 view 脚本 API：thread/task/timer 族（LuaThread/LuaAsyncTask/LuaTimer/Ticker）、
 *     startService/bindService/stopService（LuaService）、setFragment、registerReceiver 广播族、
 *     LuaAccessibilityService、onNightModeChanged、showLogs
 *     （异步替代：ui.later(delayMs, fn) 主线程回调；多界面：activity.newActivity 保留）
 *   - initLua 全局仅 activity/this/R/android；material/androidx/appcompat.R 资源全局不再注入
 *   - initENV 仅保留 b85 分支（build.gradle.b85 header flags bit0 预启动直读），settings.json 分支删除
 */
public class LuaActivity extends ComponentActivity implements LuaContext {

  private static final String ARG = "arg";
  private static final String DATA = "data";
  private static final String NAME = "name";
  private static final ArrayList<String> prjCache = new ArrayList<String>();
  private String luaDir;
  private Handler handler;
  private String luaCpath;
  private LuaDexLoader mLuaDexLoader;
  private int mWidth;
  private int mHeight;
  private LuaState L;
  private String luaPath;
  private final StringBuilder toastbuilder = new StringBuilder();
  private Boolean isCreate = false;
  private Toast toast;
  private boolean isSetViewed;
  private long lastShow;
  // Compose 运行时宿主（ui.*，所有项目常驻注册；打包产物无 b85，只能无条件注册）
  private ComposeUiHost composeHost;
  private Menu optionsMenu;
  private LuaObject mOnKeyDown;
  private LuaObject mOnKeyUp;
  private LuaObject mOnKeyLongPress;
  private LuaObject mOnTouchEvent;
  private LuaObject mOnActivityReenter;
  private String localDir;

  private Object consoleSession;

  private String odexDir;

  private String libDir;

  private String luaExtDir;

  private String luaLpath;

  private String luaMdDir;

  private boolean isUpdata;

  private boolean mDebug = true;
  private LuaResources mResources;
  private final ArrayList<LuaGcable> gclist = new ArrayList<LuaGcable>();
  private String pageName = "main";
  private static final HashMap<String, LuaActivity> sLuaActivityMap =
      new HashMap<String, LuaActivity>();
  private LuaObject mOnKeyShortcut;

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

  private static byte[] readFileBytes(File f) throws IOException {
    FileInputStream in = new FileInputStream(f);
    try {
      return readAll(in);
    } finally {
      in.close();
    }
  }

  @Override
  public ArrayList<ClassLoader> getClassLoaders() {
    return mLuaDexLoader.getClassLoaders();
  }

  public HashMap<String, String> getLibrarys() {
    return mLuaDexLoader.getLibrarys();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {

    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
    StrictMode.setThreadPolicy(policy);

    super.onCreate(null);

    WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    DisplayMetrics outMetrics = new DisplayMetrics();
    wm.getDefaultDisplay().getMetrics(outMetrics);
    mWidth = outMetrics.widthPixels;
    mHeight = outMetrics.heightPixels;

    // 定义文件夹
    LuaApplication app = (LuaApplication) getApplication();
    if (app.getClass() != LuaApplication.class) {
      while (true) {
        if (app.getClass() == LuaApplication.class) break;
      }
    }
    localDir = app.getLocalDir();
    odexDir = app.getOdexDir();
    libDir = app.getLibDir();
    luaMdDir = app.getMdDir();
    luaCpath = app.getLuaCpath();
    luaDir = localDir;
    luaLpath = app.getLuaLpath();
    luaExtDir = app.getLuaExtDir();

    handler = new MainHandler();

    try {
      Intent intent = getIntent();
      Object[] arg = (Object[]) intent.getSerializableExtra(ARG);
      if (arg == null) arg = new Object[0];

      luaPath = getLuaPath();
      pageName = new File(luaPath).getName();
      int idx = pageName.lastIndexOf(".");
      if (idx > 0) pageName = pageName.substring(0, idx);

      // 传统 view 隔离：IDE 调试宿主（app）运行时会经 SplashWelcome 把 view 版 import.lua
      // （含 loadlayout/loadbitmap/loadmenu 注入）解包进私有 luaMdDir。若命中该污染源，
      // 剔除 luaMdDir 查找段，使 compose 调试项目 require "import" 无法加载传统 view 工具。
      // compose-apk 产物自带精简版 import.lua（不含 loadlayout）→ 不受影响。
      String luaMdPath = luaMdDir + "/?.lua;" + luaMdDir + "/lua/?.lua;" + luaMdDir + "/?/settings.json;";
      File viewImport = new File(luaMdDir, "import.lua");
      if (viewImport.exists()) {
        try {
          if (new String(readFileBytes(viewImport), "UTF-8").contains("loadlayout")) {
            luaMdPath = "";
          }
        } catch (IOException ignored) {
        }
      }
      luaLpath =
          (luaDir + "/?.lua;" + luaDir + "/lua/?.lua;" + luaDir + "/?/settings.json;") + luaMdPath;
      initLua();

      // 调试控制台：会话开始（须在 doFile 前，保证 debugParams 可注入 LuaState）
      // 工具型启动（布局助手等）经 console_disable 抑制，不进调试会话
      Object bridge = ConsoleBridgeRef.getBridge();
      if (bridge != null && !getIntent().getBooleanExtra("console_disable", false)) {
        try {
          consoleSession =
              ConsoleBridgeRef.newSessionInfo(
                  this,
                  L,
                  luaPath,
                  luaDir,
                  luaExtDir,
                  System.currentTimeMillis(),
                  mDebug,
                  getIntent().getStringExtra("debugParams"));
          ConsoleBridgeRef.onSessionStart(consoleSession);
        } catch (Exception ignored) {
        }
      }

      mLuaDexLoader = new LuaDexLoader(this);
      mLuaDexLoader.loadLibs();
      sLuaActivityMap.put(pageName, this);
      if (composeHost != null) {
        // Compose 主块执行前复位 state 序号（同调用点重跑复用同一代理，写即重组不丢值）
        composeHost.beginRun();
      }
      doFile(luaPath, arg);
      isCreate = true;
      if (!pageName.equals("main")) runFunc("main", arg);
      runFunc(pageName, arg);
      runFunc("onCreate", savedInstanceState);
    } catch (Exception e) {
      sendMsg(e.getMessage());
      return;
    }

    mOnKeyShortcut = L.getLuaObject("onKeyShortcut");
    if (mOnKeyShortcut.isNil()) mOnKeyShortcut = null;
    mOnKeyDown = L.getLuaObject("onKeyDown");
    if (mOnKeyDown.isNil()) mOnKeyDown = null;
    mOnKeyUp = L.getLuaObject("onKeyUp");
    if (mOnKeyUp.isNil()) mOnKeyUp = null;
    mOnKeyLongPress = L.getLuaObject("onKeyLongPress");
    if (mOnKeyLongPress.isNil()) mOnKeyLongPress = null;
    mOnTouchEvent = L.getLuaObject("onTouchEvent");
    if (mOnTouchEvent.isNil()) mOnTouchEvent = null;
    LuaObject onActivityReenter = L.getLuaObject("onActivityReenter");
    if (onActivityReenter.isFunction()) {
        mOnActivityReenter = onActivityReenter;
      } else {
        mOnActivityReenter = null;
    }

    // 注册新的返回手势回调（Android 13+ 推荐）
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                Object ret = runFunc("onBackPressed");
                if (ret != null && ret.getClass() == Boolean.class && (Boolean) ret) {
                  // Lua 返回 true，拦截返回事件
                  return;
                }
                // 否则执行默认返回行为
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
              }
            });
  }

  @Override
  public boolean onKeyShortcut(int keyCode, KeyEvent event) {
    if (mOnKeyShortcut != null) {
      try {
        Object ret = mOnKeyShortcut.call(keyCode, event);
        if (ret != null && ret.getClass() == Boolean.class && (Boolean) ret) return true;
      } catch (LuaException e) {
        sendError("onKeyShortcut", e);
      }
    }
    return super.onKeyShortcut(keyCode, event);
  }

  @Override
  public void regGc(LuaGcable obj) {
    gclist.add(obj);
  }

  public void initMain() {
    prjCache.add(getLocalDir());
  }

  public String getLuaPath() {
    Intent intent = getIntent();
    Uri uri = intent.getData();
    String path = null;
    if (uri == null) return null;

    path = uri.getPath();
    if (!new File(path).exists() && new File(getLuaPath(path)).exists()) path = getLuaPath(path);

    luaPath = path;
    File f = new File(path);

    luaDir = new File(luaPath).getParent();
    if (f.getName().equals("main.lua") && new File(luaDir, "settings.json").exists()) {
      if (!prjCache.contains(luaDir)) prjCache.add(luaDir);
    } else {
      String parent = luaDir;
      while (parent != null) {
        if (prjCache.contains(parent)) {
          luaDir = parent;
          break;
        } else {
          // 项目根标志：settings.json（IDE 项目）或 .entry（打包产物，产物不含 settings.json）
          if (new File(parent, "settings.json").exists() || new File(parent, ".entry").exists()) {
            luaDir = parent;
            if (!prjCache.contains(luaDir)) prjCache.add(luaDir);
            break;
          }
        }
        parent = new File(parent).getParent();
      }
    }
    return path;
  }

  public String getQuery(String name) {
    Uri uri = getIntent().getData();
    if (uri == null) return null;
    return uri.getQueryParameter(name);
  }

  public Object getArg(int idx) {
    Object[] arg = (Object[]) getIntent().getSerializableExtra(ARG);
    if (arg == null || arg.length >= idx) return null;
    return arg[idx];
  }

  @Override
  public String getLuaPath(String path) {
    return new File(getLuaDir(), path).getAbsolutePath();
  }

  @Override
  public String getLuaPath(String dir, String name) {
    return new File(getLuaDir(dir), name).getAbsolutePath();
  }

  @Override
  public String getLuaExtPath(String path) {
    return new File(getLuaExtDir(), path).getAbsolutePath();
  }

  @Override
  public String getLuaExtPath(String dir, String name) {
    return new File(getLuaExtDir(dir), name).getAbsolutePath();
  }

  @Override
  public String getLuaLpath() {
    return luaLpath;
  }

  @Override
  public String getLuaCpath() {
    return luaCpath;
  }

  @Override
  public Context getContext() {
    return this;
  }

  @Override
  public LuaState getLuaState() {
    return L;
  }

  public View getDecorView() {
    return getWindow().getDecorView();
  }

  public String getLocalDir() {
    return localDir;
  }

  @Override
  public String getLuaExtDir() {
    return luaExtDir;
  }

  @Override
  public void setLuaExtDir(String dir) {
    if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
      String sdDir = Environment.getExternalStorageDirectory().getAbsolutePath();
      luaExtDir = new File(sdDir, dir).getAbsolutePath();
    } else {
      File[] fs = new File("/storage").listFiles();
      for (File f : fs) {
        String[] ls = f.list();
        if (ls == null) continue;
        if (ls.length > 5) luaExtDir = new File(f, dir).getAbsolutePath();
      }
      if (luaExtDir == null) luaExtDir = getDir(dir, Context.MODE_PRIVATE).getAbsolutePath();
    }
    File d = new File(luaExtDir);
    if (!d.exists()) d.mkdirs();
  }

  @Override
  public String getLuaExtDir(String name) {
    File dir = new File(getLuaExtDir(), name);
    if (!dir.exists()) if (!dir.mkdirs()) return null;
    return dir.getAbsolutePath();
  }

  @Override
  public String getLuaDir() {
    return luaDir;
  }

  public void setLuaDir(String dir) {
    luaDir = dir;
  }

  @Override
  public String getLuaDir(String name) {
    File dir = new File(luaDir + "/" + name);
    if (!dir.exists()) if (!dir.mkdirs()) return null;
    return dir.getAbsolutePath();
  }

  public DexClassLoader loadApp(String path) throws LuaException {
    return mLuaDexLoader.loadApp(path);
  }

  public DexClassLoader loadDex(String path) throws LuaException {
    return mLuaDexLoader.loadDex(path);
  }

  public void loadResources(String path) {
    mLuaDexLoader.loadResources(path);
  }

  @Override
  public AssetManager getAssets() {
    if (mLuaDexLoader != null && mLuaDexLoader.getAssets() != null)
      return mLuaDexLoader.getAssets();
    return super.getAssets();
  }

  public LuaResources getLuaResources() {
    Resources superRes = super.getResources();
    if (mLuaDexLoader != null && mLuaDexLoader.getResources() != null)
      superRes = mLuaDexLoader.getResources();
    mResources =
        new LuaResources(getAssets(), superRes.getDisplayMetrics(), superRes.getConfiguration());
    mResources.setSuperResources(superRes);
    return mResources;
  }

  public Resources getSuperResources() {
    return super.getResources();
  }

  @Override
  public Resources getResources() {
    if (mLuaDexLoader != null && mLuaDexLoader.getResources() != null)
      return mLuaDexLoader.getResources();
    if (mResources != null) return mResources;
    return super.getResources();
  }

  public Object loadLib(String name) throws LuaException {
    int i = name.indexOf(".");
    String fn = name;
    if (i > 0) fn = name.substring(0, i);
    File f = new File(libDir + "/lib" + fn + ".so");
    if (!f.exists()) {
      f = new File(luaDir + "/lib" + fn + ".so");
      if (!f.exists()) throw new LuaException("can not find lib " + name);
      LuaUtil.copyFile(luaDir + "/lib" + fn + ".so", libDir + "/lib" + fn + ".so");
    }
    LuaObject require = L.getLuaObject("require");
    return require.call(name);
  }

  @Override
  public void onContentChanged() {
    super.onContentChanged();
    isSetViewed = true;
  }

  @Override
  protected void onStart() {
    super.onStart();
    runFunc("onStart");
  }

  @Override
  protected void onResume() {
    super.onResume();
    runFunc("onResume");
  }

  @Override
  protected void onPause() {
    super.onPause();
    runFunc("onPause");
  }

  @Override
  protected void onStop() {
    super.onStop();
    runFunc("onStop");
  }

  public static LuaActivity getActivity(String name) {
    return sLuaActivityMap.get(name);
  }

  @Override
  protected void onDestroy() {
    // 调试控制台：会话结束（归档旧会话 / 停止 logcat / 移除浮球）
    if (consoleSession != null) {
      try {
        ConsoleBridgeRef.onSessionEnd(consoleSession);
      } catch (Exception ignored) {
      }
      consoleSession = null;
    }

    for (LuaGcable obj : gclist) {
      obj.gc();
    }
    sLuaActivityMap.remove(pageName);
    runFunc("onDestroy");

    if (mLuaDexLoader != null) {
      try {
        mLuaDexLoader.cleanupOldFiles();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    super.onDestroy();
    System.gc();
    L.gc(LuaState.LUA_GCCOLLECT, 1);
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    runFunc("onRestart");
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    runFunc("onSaveInstanceState", outState);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    runFunc("onRestoreInstanceState", savedInstanceState);
  }

  @Override
  protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    runFunc("onUserLeaveHint");
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (data != null) {
      String name = data.getStringExtra(NAME);
      if (name != null) {
        Object[] res = (Object[]) data.getSerializableExtra(DATA);
        if (res == null) {
          runFunc("onResult", name);
        } else {
          Object[] arg = new Object[res.length + 1];
          arg[0] = name;
          System.arraycopy(res, 0, arg, 1, res.length);
          Object ret = runFunc("onResult", arg);
          if (ret != null && ret.getClass() == Boolean.class && (Boolean) ret) return;
        }
      }
    }
    runFunc("onActivityResult", requestCode, resultCode, data);
    super.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    runFunc("onRequestPermissionsResult", requestCode, permissions, grantResults);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    try {
      if (ConsoleBridgeRef.onKeyDown(keyCode, event)) return true;
    } catch (Exception ignored) {
    }
    if (mOnKeyDown != null) {
      try {
        Object ret = mOnKeyDown.call(keyCode, event);
        if (ret != null && ret.getClass() == Boolean.class && (Boolean) ret) return true;
      } catch (LuaException e) {
        sendError("onKeyDown", e);
      }
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (mOnKeyUp != null) {
      try {
        Object ret = mOnKeyUp.call(keyCode, event);
        if (ret != null && ret.getClass() == Boolean.class && (Boolean) ret) return true;
      } catch (LuaException e) {
        sendError("onKeyUp", e);
      }
    }
    return super.onKeyUp(keyCode, event);
  }

  @Override
  public boolean onKeyLongPress(int keyCode, KeyEvent event) {
    if (mOnKeyLongPress != null) {
      try {
        Object ret = mOnKeyLongPress.call(keyCode, event);
        if (ret != null && ret.getClass() == Boolean.class && (Boolean) ret) return true;
      } catch (LuaException e) {
        sendError("onKeyLongPress", e);
      }
    }
    return super.onKeyLongPress(keyCode, event);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (mOnTouchEvent != null) {
      try {
        Object ret = mOnTouchEvent.call(event);
        if (ret != null && ret.getClass() == Boolean.class && (Boolean) ret) return true;
      } catch (LuaException e) {
        sendError("onTouchEvent", e);
      }
    }
    return super.onTouchEvent(event);
  }

  @Override
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public void onActivityReenter(int resultCode, Intent data) {
      super.onActivityReenter(resultCode, data);
      if (mOnActivityReenter != null) {
          try {
              mOnActivityReenter.call(resultCode, data);
          } catch (LuaException e) {
              sendError("onActivityReenter", e);
          }
      }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    optionsMenu = menu;
    runFunc("onCreateOptionsMenu", menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Object ret = null;
    if (!item.hasSubMenu()) ret = runFunc("onOptionsItemSelected", item);
    if (ret != null && ret.getClass() == Boolean.class && (Boolean) ret) return true;
    return super.onOptionsItemSelected(item);
  }

  public Menu getOptionsMenu() {
    return optionsMenu;
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    runFunc("onCreateContextMenu", menu, v, menuInfo);
    super.onCreateContextMenu(menu, v, menuInfo);
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    runFunc("onContextItemSelected", item);
    return super.onContextItemSelected(item);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    DisplayMetrics outMetrics = new DisplayMetrics();
    wm.getDefaultDisplay().getMetrics(outMetrics);
    mWidth = outMetrics.widthPixels;
    mHeight = outMetrics.heightPixels;
    runFunc("onConfigurationChanged", newConfig);
  }

  public int getWidth() {
    return mWidth;
  }

  public int getHeight() {
    return mHeight;
  }

  @Override
  public Map getGlobalData() {
    return ((LuaApplication) getApplication()).getGlobalData();
  }

  @Override
  public Object getSharedData() {
    return LuaApplication.getInstance().getSharedData();
  }

  @Override
  public Object getSharedData(String key) {
    return LuaApplication.getInstance().getSharedData(key);
  }

  @Override
  public Object getSharedData(String key, Object def) {
    return LuaApplication.getInstance().getSharedData(key, def);
  }

  @Override
  public boolean setSharedData(String key, Object value) {
    return LuaApplication.getInstance().setSharedData(key, value);
  }

  public void result(Object[] data) {
    Intent res = new Intent();
    res.putExtra(NAME, getIntent().getStringExtra(NAME));
    res.putExtra(DATA, data);
    setResult(0, res);
    finish();
  }

  public void newActivity(String path, boolean newDocument) throws FileNotFoundException {
    newActivity(1, path, null, newDocument);
  }

  public void newActivity(String path, Object[] arg, boolean newDocument)
      throws FileNotFoundException {
    newActivity(1, path, arg, newDocument);
  }

  public void newActivity(int req, String path, boolean newDocument) throws FileNotFoundException {
    newActivity(req, path, null, newDocument);
  }

  public void newActivity(String path) throws FileNotFoundException {
    newActivity(1, path, new Object[0]);
  }

  public void newActivity(String path, Object[] arg) throws FileNotFoundException {
    newActivity(1, path, arg);
  }

  public void newActivity(int req, String path) throws FileNotFoundException {
    newActivity(req, path, new Object[0]);
  }

  public void newActivity(int req, String path, Object[] arg) throws FileNotFoundException {
    newActivity(req, path, arg, false);
  }

  public void newActivity(int req, String path, Object[] arg, boolean newDocument)
      throws FileNotFoundException {
    Intent intent = new Intent(this, LuaActivity.class);
    intent.putExtra(NAME, path);
    if (path.charAt(0) != '/') path = luaDir + "/" + path;
    File f = new File(path);
    if (f.isDirectory() && new File(path + "/main.lua").exists()) path += "/main.lua";
    else if ((f.isDirectory() || !f.exists()) && !path.endsWith(".lua")) path += ".lua";
    if (!new File(path).exists()) throw new FileNotFoundException(path);

    if (newDocument) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
      }
    }

    intent.setData(Uri.parse("file://" + path));

    if (arg != null) intent.putExtra(ARG, arg);
    if (newDocument) startActivity(intent);
    else startActivityForResult(intent, req);
  }

  public void newActivity(String path, int in, int out, boolean newDocument)
      throws FileNotFoundException {
    newActivity(1, path, in, out, null, newDocument);
  }

  public void newActivity(String path, int in, int out, Object[] arg, boolean newDocument)
      throws FileNotFoundException {
    newActivity(1, path, in, out, arg, newDocument);
  }

  public void newActivity(int req, String path, int in, int out, boolean newDocument)
      throws FileNotFoundException {
    newActivity(req, path, in, out, null, newDocument);
  }

  public void newActivity(String path, int in, int out) throws FileNotFoundException {
    newActivity(1, path, in, out, new Object[0]);
  }

  public void newActivity(String path, int in, int out, Object[] arg)
      throws FileNotFoundException {
    newActivity(1, path, in, out, arg);
  }

  public void newActivity(int req, String path, int in, int out) throws FileNotFoundException {
    newActivity(req, path, in, out, new Object[0]);
  }

  public void newActivity(int req, String path, int in, int out, Object[] arg)
      throws FileNotFoundException {
    newActivity(req, path, in, out, arg, false);
  }

  public void newActivity(int req, String path, int in, int out, Object[] arg, boolean newDocument)
      throws FileNotFoundException {
    Intent intent = new Intent(this, LuaActivity.class);
    intent.putExtra(NAME, path);
    if (path.charAt(0) != '/') path = luaDir + "/" + path;
    File f = new File(path);
    if (f.isDirectory() && new File(path + "/main.lua").exists()) path += "/main.lua";
    else if ((f.isDirectory() || !f.exists()) && !path.endsWith(".lua")) path += ".lua";
    if (!new File(path).exists()) throw new FileNotFoundException(path);
    intent.setData(Uri.parse("file://" + path));

    if (newDocument) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
      }
    }

    if (arg != null) intent.putExtra(ARG, arg);
    if (newDocument) startActivity(intent);
    else startActivityForResult(intent, req);
    overridePendingTransition(in, out);
  }

  public void finish(boolean finishTask) {
    if (!finishTask) {
      super.finish();
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      Intent intent = getIntent();
      if (intent != null && (intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0)
        finishAndRemoveTask();
      else super.finish();
    } else {
      super.finish();
    }
  }

  public void setDebug(boolean isDebug) {
    mDebug = isDebug;
  }

  private void initLua() throws Exception {
    L = LuaStateFactory.newLuaState();
    L.openLibs();
    L.pushJavaObject(this);
    L.setGlobal("activity");
    L.getGlobal("activity");
    L.setGlobal("this");

    L.pushJavaObject(com.luafabric.compose.R.class);
    L.setGlobal("R");

    L.newTable();
    L.pushJavaObject(android.R.class);
    L.setField(-2, "R");
    L.setGlobal("android");

    L.pushContext(this);
    L.getGlobal("luajava");
    L.pushString(luaExtDir);
    L.setField(-2, "luaextdir");
    L.pushString(luaDir);
    L.setField(-2, "luadir");
    L.pushString(luaPath);
    L.setField(-2, "luapath");
    L.pop(1);
    initENV();

    JavaFunction print = new LuaPrint(this, L);
    print.register("print");

    // 调试控制台：按文件跟踪 require（仅控制台桥已注册时挂载）
    if (ConsoleBridgeRef.isBridgeActive()) {
      final LuaObject originalRequire = L.getLuaObject("require");
      JavaFunction tracedRequire =
          new JavaFunction(L) {
            @Override
            public int execute() throws LuaException {
              String name = (L.type(2) == LuaState.LUA_TSTRING) ? L.toString(2) : null;
              if (originalRequire != null && !originalRequire.isNil()) {
                L.pushObjectValue(originalRequire);
                L.pushValue(2);
                int ok = L.pcall(1, 1, 0);
                if (ok == 0) {
                  // 调试控制台：模块加载后枚举 C/Lua 库函数名与 debug.getinfo 参数个数
                  if (name != null) {
                    try {
                      ConsoleBridgeRef.onRequire(name, probeRequireFunctions(L), probeIsNative(name, L));
                    } catch (Exception ignored) {
                    }
                  }
                  return 1;
                }
                throw new LuaException("require failed: " + L.toString(-1));
              }
              return 0;
            }
          };
      tracedRequire.register("require");
    }

    L.getGlobal("package");
    L.pushString(luaLpath);
    L.setField(-2, "path");
    L.pushString(luaCpath);
    L.setField(-2, "cpath");
    L.pop(1);

    // 注册 assets searcher：require("xxx") 可从 apk 内 assets 或 assets/lua/ 加载
    JavaFunction assetLoader = new LuaAssetLoader(this, L);
    L.getGlobal("package");
    L.getField(-1, "searchers");
    int searchersLen = L.rawLen(-1);
    L.pushInteger(searchersLen + 1);
    L.pushJavaFunction(assetLoader);
    L.setTable(-3);
    L.pop(1);
  }

  private void initENV() throws LuaException {
    // ui.* 宿主：无条件注册。IDE 运行靠 b85 识别 compose，但打包产物按打包策略丢弃 b85，
    // 无法在运行时区分 → 只能常驻注册；非 compose 项目从不调用 ui.*，行为零影响。
    try {
      composeHost = new ComposeUiHost(this, L, luaPath, luaDir);
      composeHost.register();
    } catch (Exception e) {
      Log.e("LuaActivity", "compose ui host register failed", e);
      composeHost = null;
    }

    // Compose 项目（build.gradle.b85）：debugmode 预启动直读 header flags bit0
    File b85File = new File(luaDir + "/" + ComposeConfig.FILE_NAME);
    if (b85File.exists()) {
      Boolean debug = null;
      try {
        debug = ComposeConfig.INSTANCE.debugFlag(readFileBytes(b85File));
      } catch (IOException e) {
        debug = null;
      }
      // 无效 b85（magic/schema 不过）→ 与打包产物同待遇：显式关闭调试
      mDebug = debug != null && debug.booleanValue();
      if (composeHost != null) {
        String name = composeHost.getProjectName();
        if (name != null && !name.isEmpty()) setTitle(name);
      }
      return;
    }

    // 无 b85（打包产物按打包策略丢弃）：显式关闭调试，避免 mDebug 默认 true 误开控制台/调试链路。
    mDebug = false;
  }

  @Override
  public void setTitle(CharSequence title) {
    super.setTitle(title);
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      ActivityManager.TaskDescription tDesc = new ActivityManager.TaskDescription(title.toString());
      setTaskDescription(tDesc);
    }
  }

  public Object doFile(String filePath) {
    return doFile(filePath, new Object[0]);
  }

  public Object doFile(String filePath, Object[] args) {
    int ok = 0;
    try {
      if (filePath.charAt(0) != '/') filePath = luaDir + "/" + filePath;

      L.setTop(0);
      ok = L.LloadFile(filePath);

      if (ok == 0) {
        L.getGlobal("debug");
        L.getField(-1, "traceback");
        L.remove(-2);
        L.insert(-2);
        int l = args.length;
        for (int i = 0; i < l; i++) {
          L.pushObjectValue(args[i]);
        }
        ok = L.pcall(l, 1, -2 - l);
        if (ok == 0) {
          return L.toJavaObject(-1);
        }
      }
      Intent res = new Intent();
      res.putExtra(DATA, L.toString(-1));
      setResult(ok, res);
      throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
    } catch (LuaException e) {
      setTitle(errorReason(ok));
      sendMsg(e.getMessage());
      reportConsoleError(errorReason(ok), e.getMessage());
      String s = e.getMessage();
      String p = "android.permission.";
      int i = s.indexOf(p);
      if (i > 0) {
        i = i + p.length();
        int n = s.indexOf(".", i);
        if (n > i) {
          String m = s.substring(i, n);
          L.getGlobal("require");
          L.pushString("permission");
          L.pcall(1, 0, 0);
          L.getGlobal("permission_info");
          L.getField(-1, m);
          if (L.isString(-1)) m = m + " (" + L.toString(-1) + ")";
          sendMsg("权限错误: " + m);
          return null;
        }
      }
      if (isUpdata) {}
    }

    return null;
  }

  public Object doAsset(String name, Object[] args) {
    int ok = 0;
    try {
      byte[] bytes = readAsset(name);
      L.setTop(0);
      ok = L.LloadBuffer(bytes, name);

      if (ok == 0) {
        L.getGlobal("debug");
        L.getField(-1, "traceback");
        L.remove(-2);
        L.insert(-2);
        int l = args.length;
        for (int i = 0; i < l; i++) {
          L.pushObjectValue(args[i]);
        }
        ok = L.pcall(l, 0, -2 - l);
        if (ok == 0) {
          return L.toJavaObject(-1);
        }
      }
      throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
    } catch (Exception e) {
      setTitle(errorReason(ok));
      sendMsg(e.getMessage());
      reportConsoleError(errorReason(ok), e.getMessage());
    }

    return null;
  }

  public Object runFunc(String funcName, Object... args) {
    if (L != null) {
      synchronized (L) {
        try {
          L.setTop(0);
          L.pushGlobalTable();
          L.pushString(funcName);
          L.rawGet(-2);
          if (L.isFunction(-1)) {
            // 事件捕获：Lua 文件显式定义且实际即将被调用的函数（含 Java 生命周期触发，
            // 如 onPause/onResume），一律记录；函数未定义则不记录。
            try {
              ConsoleBridgeRef.onEvent(funcName, args);
            } catch (Exception ignored) {
            }
            L.getGlobal("debug");
            L.getField(-1, "traceback");
            L.remove(-2);
            L.insert(-2);

            int l = args.length;
            for (int i = 0; i < l; i++) {
              L.pushObjectValue(args[i]);
            }

            int ok = L.pcall(l, 1, -2 - l);
            if (ok == 0) {
              return L.toJavaObject(-1);
            }
            throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
          }
        } catch (LuaException e) {
          sendError(funcName, e);
        }
      }
    }
    return null;
  }

  public Object doString(String funcSrc, Object... args) {
    try {
      L.setTop(0);
      int ok = L.LloadString(funcSrc);

      if (ok == 0) {
        L.getGlobal("debug");
        L.getField(-1, "traceback");
        L.remove(-2);
        L.insert(-2);

        int l = args.length;
        for (int i = 0; i < l; i++) {
          L.pushObjectValue(args[i]);
        }

        ok = L.pcall(l, 1, -2 - l);
        if (ok == 0) {
          return L.toJavaObject(-1);
        }
      }
      throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
    } catch (LuaException e) {
      sendMsg(e.getMessage());
    }
    return null;
  }

  private String errorReason(int error) {
    switch (error) {
      case 6:
        return "error error";
      case 5:
        return "GC error";
      case 4:
        return "Out of memory";
      case 3:
        return "Syntax error";
      case 2:
        return "Runtime error";
      case 1:
        return "Yield error";
    }
    return "Unknown error " + error;
  }

  public byte[] readAsset(String name) throws IOException {
    AssetManager am = getAssets();
    InputStream is = am.open(name);
    byte[] ret = readAll(is);
    is.close();
    return ret;
  }

  // ---- Compose 宿主调用点 ----

  /** Compose 宿主：标记内容视图已被宿主接管（数据树 ui.render 已 setContentView） */
  public void markComposeViewSet() {
    isSetViewed = true;
  }

  public void sendMsg(String msg) {
    Message message = new Message();
    Bundle bundle = new Bundle();
    bundle.putString(DATA, msg);
    message.setData(bundle);
    message.what = 0;
    handler.sendMessage(message);
    Log.i("lua", msg);
  }

  /**
   * 调试控制台：枚举 require 返回模块表的函数名与 debug.getinfo 参数个数。
   * 纯只读探针，结束时恢复栈顶，异常时返回空表。
   */
  private static Map<String, Integer> probeRequireFunctions(LuaState L) {
    Map<String, Integer> out = new HashMap<>();
    if (L == null || L.getTop() < 1) return out;
    int base = L.getTop(); // 模块表位于 base（绝对索引）
    if (L.type(base) != LuaState.LUA_TTABLE) return out; // 模块非 table（string/function 等）直接跳过，防止 lua_next 原生崩溃
    try {
      L.getGlobal("debug"); // base+1
      if (L.type(base + 1) == LuaState.LUA_TTABLE) {
        L.getField(base + 1, "getinfo"); // base+2
        if (L.type(base + 2) == LuaState.LUA_TFUNCTION) {
          L.pushNil(); // base+3 遍历 key
          while (L.next(base) != 0) { // 模块表 base，key base+3，value base+4
            if (L.type(base + 3) == LuaState.LUA_TSTRING
                && L.type(base + 4) == LuaState.LUA_TFUNCTION) {
              String fname = L.toString(base + 3);
              L.pushValue(base + 2); // getinfo 函数 base+5
              L.pushValue(base + 4); // 目标函数 base+6
              L.pushString("u"); // base+7
              int ok = L.pcall(2, 1, 0);
              if (ok == 0 && L.type(base + 5) == LuaState.LUA_TTABLE) {
                L.getField(base + 5, "nparams"); // base+6
                int np =
                    (L.type(base + 6) == LuaState.LUA_TNUMBER)
                        ? (int) L.toInteger(base + 6)
                        : -1;
                out.put(fname, np);
                L.setTop(base + 5);
              }
            }
            L.setTop(base + 4);
            L.pop(1); // 仅弹 value，保留 key 供下一轮 lua_next
          }
        }
      }
    } catch (Exception ignored) {
      out.clear();
    }
    L.setTop(base);
    return out;
  }

  /**
   * 模块来源探测：遍历模块表，按 debug.getinfo 的 what 字段统计 C/Lua 函数。
   * 全部函数为 C（what=="C"）视为原生库；含任一 Lua 函数、无函数（纯数据表）或非表返回视为 lua 模块。
   * 纯只读，异常保守视为 lua 模块（内置库另行由 LUA_BUILTIN 过滤）。结束时恢复栈顶。
   */
  private static boolean probeIsNative(String moduleName, LuaState L) {
    if (L == null || L.getTop() < 1) return false;
    int base = L.getTop();
    if (L.type(base) != LuaState.LUA_TTABLE) return false;
    int cFuncs = 0;
    int luaFuncs = 0;
    try {
      L.getGlobal("debug"); // base+1
      if (L.type(base + 1) != LuaState.LUA_TTABLE) return false;
      L.getField(base + 1, "getinfo"); // base+2
      if (L.type(base + 2) != LuaState.LUA_TFUNCTION) return false;
      L.pushNil(); // base+3 遍历 key
      int checked = 0;
      while (L.next(base) != 0 && checked < 64) { // key base+3, value base+4
        if (L.type(base + 3) == LuaState.LUA_TSTRING && L.type(base + 4) == LuaState.LUA_TFUNCTION) {
          L.pushValue(base + 2); // getinfo base+5
          L.pushValue(base + 4); // 目标函数 base+6
          L.pushString("S"); // base+7
          int ok = L.pcall(2, 1, 0);
          if (ok == 0 && L.type(base + 5) == LuaState.LUA_TTABLE) {
            L.getField(base + 5, "what"); // base+6
            if (L.type(base + 6) == LuaState.LUA_TSTRING) {
              String what = L.toString(base + 6);
              if (what != null) {
                if (what.equals("C")) cFuncs++;
                else if (what.equals("Lua") || what.equals("main") || what.equals("tail")) luaFuncs++;
              }
            }
            L.setTop(base + 5);
          }
          checked++;
        }
        L.setTop(base + 4);
        L.pop(1); // 仅弹 value，保留 key 供下一轮 lua_next
      }
      if (cFuncs > 0 || luaFuncs > 0) {
        Log.i("lua", "probeIsNative(" + moduleName + "): c=" + cFuncs + " lua=" + luaFuncs);
      }
      return cFuncs > 0 && luaFuncs == 0;
    } catch (Exception ignored) {
      return false;
    } finally {
      L.setTop(base);
    }
  }

  @Override
  public void sendError(String title, Exception msg) {
    Object ret = runFunc("onError", title, msg);
    // 报错 Toast 由控制台设置项门控（默认关），避免高版本受限 toast 遮 UI / 卡点击；
    // 无论开关与否，错误都会经 reportConsoleError 入控制台 F1 缓冲（角标可见）。
    if (ret != null && ret.getClass() == Boolean.class && (Boolean) ret) {
    } else if (ConsoleBridgeRef.isErrorToastEnabled()) sendMsg(title + ": " + msg.getMessage());
    reportConsoleError(title, msg != null ? msg.getMessage() : String.valueOf(msg));
  }

  /**
   * 调试控制台：Lua 运行时错误上报。桥未注册/非调试会话（桥实现内部按 active 门控）时零开销。
   */
  private void reportConsoleError(String title, String message) {
    try {
      ConsoleBridgeRef.onError(title, message);
    } catch (Exception ignored) {
    }
  }

  @SuppressLint("ShowToast")
  public void showToast(String text) {
    long now = System.currentTimeMillis();
    if (toast == null || now - lastShow > 1000) {
      toastbuilder.setLength(0);
      toastbuilder.append(text);
      toast = Toast.makeText(this, toastbuilder.toString(), Toast.LENGTH_LONG);
      toast.show();
    } else {
      toastbuilder.append("\n").append(text);
      toast.setText(toastbuilder.toString());
      toast.show();
    }
    lastShow = now;
  }

  private void setField(String key, Object value) {
    synchronized (L) {
      try {
        L.pushObjectValue(value);
        L.setGlobal(key);
      } catch (LuaException e) {
        sendError("setField", e);
      }
    }
  }

  public void call(String func) {
    push(2, func);
  }

  public void call(String func, Object[] args) {
    if (args.length == 0) push(2, func);
    else push(3, func, args);
  }

  public void set(String key, Object value) {
    push(1, key, new Object[] {value});
  }

  public Object get(String key) throws LuaException {
    synchronized (L) {
      L.getGlobal(key);
      return L.toJavaObject(-1);
    }
  }

  public void push(int what, String s) {
    Message message = new Message();
    Bundle bundle = new Bundle();
    bundle.putString(DATA, s);
    message.setData(bundle);
    message.what = what;

    handler.sendMessage(message);
  }

  public void push(int what, String s, Object[] args) {
    Message message = new Message();
    Bundle bundle = new Bundle();
    bundle.putString(DATA, s);
    bundle.putSerializable("args", args);
    message.setData(bundle);
    message.what = what;

    handler.sendMessage(message);
  }

  public class MainHandler extends Handler {
    @Override
    public void handleMessage(Message msg) {
      super.handleMessage(msg);
      switch (msg.what) {
        case 0:
          {
            String data = msg.getData().getString(DATA);
            // 报错/print toast 统一由控制台「使用 Toast 输出 Lua 侧错误」开关门控（默认关）；
            // 非控制台应用（bridge 未注册）registry 恒 true，保持旧行为。
            if (mDebug && ConsoleBridgeRef.isErrorToastEnabled()) showToast(data);
          }
          break;
        case 1:
          {
            Bundle data = msg.getData();
            setField(data.getString(DATA), ((Object[]) data.getSerializable("args"))[0]);
          }
          break;
        case 2:
          {
            String src = msg.getData().getString(DATA);
            runFunc(src);
          }
          break;
        case 3:
          {
            String src = msg.getData().getString(DATA);
            Serializable args = msg.getData().getSerializable("args");
            runFunc(src, (Object[]) args);
          }
          break;
      }
    }
  }
}