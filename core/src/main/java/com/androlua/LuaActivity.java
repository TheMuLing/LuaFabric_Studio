package com.androlua;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
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
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.widget.ArrayListAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.luajava.JavaFunction;
import com.luajava.LuaException;
import com.luajava.LuaObject;
import com.luajava.LuaState;
import com.luajava.LuaStateFactory;

import com.luafabric.studio.falling.core.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dalvik.system.DexClassLoader;

public class LuaActivity extends AppCompatActivity
    implements LuaBroadcastReceiver.OnReceiveListener, LuaContext {

  private static final String ARG = "arg";
  private static final String DATA = "data";
  private static final String NAME = "name";
  private static final ArrayList<String> prjCache = new ArrayList<String>();
  private String luaDir;
  private Handler handler;
  private TextView status;
  private String luaCpath;
  private LuaDexLoader mLuaDexLoader;
  private int mWidth;
  private int mHeight;
  private ListView list;
  private ArrayListAdapter<String> adapter;
  private LuaState L;
  private String luaPath;
  private final StringBuilder toastbuilder = new StringBuilder();
  private Boolean isCreate = false;
  private Toast toast;
  private LinearLayout layout;
  private boolean isSetViewed;
  private long lastShow;
  private Menu optionsMenu;
  private LuaObject mOnKeyDown;
  private LuaObject mOnKeyUp;
  private LuaObject mOnKeyLongPress;
  private LuaObject mOnTouchEvent;
  private LuaObject mOnActivityReenter;
  private String localDir;

  private String odexDir;

  private String libDir;

  private String luaExtDir;

  private LuaBroadcastReceiver mReceiver;

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

    layout = new LinearLayout(this);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    status = new TextView(this);

    status.setTextColor(Color.BLACK);
    scroll.addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    status.setText("");
    status.setTextIsSelectable(true);
    list = new ListView(this);
    list.setFastScrollEnabled(true);
    adapter =
        new ArrayListAdapter<String>(this, android.R.layout.simple_list_item_1) {
          @Override
          public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            if (convertView == null) view.setTextIsSelectable(true);
            return view;
          }
        };
    list.setAdapter(adapter);
    layout.addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

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
      status.setText("");
      adapter.clear();
      Intent intent = getIntent();
      Object[] arg = (Object[]) intent.getSerializableExtra(ARG);
      if (arg == null) arg = new Object[0];

      luaPath = getLuaPath();
      pageName = new File(luaPath).getName();
      int idx = pageName.lastIndexOf(".");
      if (idx > 0) pageName = pageName.substring(0, idx);

      luaLpath =
          (luaDir + "/?.lua;" + luaDir + "/lua/?.lua;" + luaDir + "/?/settings.json;") + luaLpath;
      initLua();

      mLuaDexLoader = new LuaDexLoader(this);
      mLuaDexLoader.loadLibs();
      sLuaActivityMap.put(pageName, this);
      doFile(luaPath, arg);
      isCreate = true;
      if (!pageName.equals("main")) runFunc("main", arg);
      runFunc(pageName, arg);
      runFunc("onCreate", savedInstanceState);
      if (!isSetViewed) {
        TypedArray array =
            getTheme()
                .obtainStyledAttributes(
                    new int[] {
                      android.R.attr.colorBackground,
                      android.R.attr.textColorPrimary,
                      android.R.attr.textColorHighlightInverse,
                    });
        int backgroundColor = array.getColor(0, 0xFF00FF);
        int textColor = array.getColor(1, 0xFF00FF);
        array.recycle();
        status.setTextColor(textColor);
        layout.setBackgroundColor(backgroundColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
          WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

          boolean isLightBg = ColorUtils.calculateLuminance(backgroundColor) > 0.5;
          WindowInsetsControllerCompat controller =
              WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

          if (controller != null) {
            controller.setAppearanceLightStatusBars(isLightBg);
            controller.setAppearanceLightNavigationBars(isLightBg);
          }

          ViewCompat.setOnApplyWindowInsetsListener(
              layout,
              (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
              });
        }

        setContentView(layout);
      }
    } catch (Exception e) {
      sendMsg(e.getMessage());
      setContentView(layout);
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
            new androidx.activity.OnBackPressedCallback(true) {
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

    LuaObject onAccessibilityEvent = L.getLuaObject("onAccessibilityEvent");
    if (onAccessibilityEvent.isFunction())
      LuaAccessibilityService.onAccessibilityEvent = onAccessibilityEvent.getFunction();
  }

  public void setFragment(Fragment fragment) {
    isSetViewed = true;
    setContentView(new View(this));
    getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
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

  public long test(String src, int n) {
    long t = System.currentTimeMillis();
    for (int i = 0; i < n; i++) {
      L.LdoString(src);
    }
    return System.currentTimeMillis() - t;
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
          if (new File(parent, "main.lua").exists() && new File(parent, "settings.json").exists()) {
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

  public Intent registerReceiver(LuaBroadcastReceiver receiver, IntentFilter filter) {
    return super.registerReceiver(receiver, filter);
  }

  public Intent registerReceiver(LuaBroadcastReceiver.OnReceiveListener ltr, IntentFilter filter) {
    LuaBroadcastReceiver receiver = new LuaBroadcastReceiver(ltr);
    return super.registerReceiver(receiver, filter);
  }

  public Intent registerReceiver(IntentFilter filter) {
    if (mReceiver != null) unregisterReceiver(mReceiver);
    mReceiver = new LuaBroadcastReceiver(this);
    return super.registerReceiver(mReceiver, filter);
  }

  @Override
  public void unregisterReceiver(BroadcastReceiver receiver) {
    try {
      super.unregisterReceiver(receiver);
    } catch (Exception e) {
      Log.i("lua", "unregisterReceiver: " + receiver);
      e.printStackTrace();
    }
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    runFunc("onReceive", context, intent);
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
    if (mReceiver != null) unregisterReceiver(mReceiver);

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
  public void onNightModeChanged(int mode) {
    super.onNightModeChanged(mode);
    runFunc("onNightModeChanged", mode);
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

  public boolean bindService(int flag) {
    ServiceConnection conn =
        new ServiceConnection() {
          @Override
          public void onServiceConnected(ComponentName comp, IBinder binder) {
            runFunc("onServiceConnected", comp, ((LuaService.LuaBinder) binder).getService());
          }

          @Override
          public void onServiceDisconnected(ComponentName comp) {
            runFunc("onServiceDisconnected", comp);
          }
        };
    return bindService(conn, flag);
  }

  public boolean bindService(ServiceConnection conn, int flag) {
    Intent service = new Intent(this, LuaService.class);
    service.putExtra("luaDir", luaDir);
    service.putExtra("luaPath", luaPath);
    return super.bindService(service, conn, flag);
  }

  public boolean stopService() {
    return stopService(new Intent(this, LuaService.class));
  }

  public ComponentName startService() {
    return startService(null, null);
  }

  public ComponentName startService(Object[] arg) {
    return startService(null, arg);
  }

  public ComponentName startService(String path) {
    return startService(path, null);
  }

  public ComponentName startService(String path, Object[] arg) {
    Intent intent = new Intent(this, LuaService.class);
    intent.putExtra("luaDir", luaDir);
    intent.putExtra("luaPath", luaPath);
    if (path != null) {
      if (path.charAt(0) != '/')
        intent.setData(Uri.parse("file://" + luaDir + "/" + path + ".lua"));
      else intent.setData(Uri.parse("file://" + path));
    }

    if (arg != null) intent.putExtra(ARG, arg);

    return super.startService(intent);
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
    if (newDocument) intent = new Intent(this, LuaActivityX.class);

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

  public void newActivity(String path, int in, int out, Object[] arg) throws FileNotFoundException {
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
    if (newDocument) intent = new Intent(this, LuaActivityX.class);
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

  public LuaAsyncTask newTask(LuaObject func) throws LuaException {
    return newTask(func, null, null);
  }

  public LuaAsyncTask newTask(LuaObject func, LuaObject callback) throws LuaException {
    return newTask(func, null, callback);
  }

  public LuaAsyncTask newTask(LuaObject func, LuaObject update, LuaObject callback)
      throws LuaException {
    return new LuaAsyncTask(this, func, update, callback);
  }

  public LuaThread newThread(LuaObject func) throws LuaException {
    return newThread(func, new Object[0]);
  }

  public LuaThread newThread(LuaObject func, Object[] arg) throws LuaException {
    LuaThread thread = new LuaThread(this, func, true, arg);
    return thread;
  }

  public LuaTimer newTimer(LuaObject func) throws LuaException {
    return newTimer(func, new Object[0]);
  }

  public LuaTimer newTimer(LuaObject func, Object[] arg) throws LuaException {
    return new LuaTimer(this, func, arg);
  }

  public LuaAsyncTask task(long delay, LuaObject func) throws LuaException {
    return task(delay, null, null);
  }

  public LuaAsyncTask task(long delay, Object[] arg, LuaObject func) throws LuaException {
    LuaAsyncTask task = new LuaAsyncTask(this, delay, func);
    task.execute(arg);
    return task;
  }

  public LuaAsyncTask task(LuaObject func) throws LuaException {
    return task(func, null, null, null);
  }

  public LuaAsyncTask task(LuaObject func, Object[] arg) throws LuaException {
    return task(func, arg, null, null);
  }

  public LuaAsyncTask task(LuaObject func, Object[] arg, LuaObject callback) throws LuaException {
    return task(func, null, null, callback);
  }

  public LuaAsyncTask task(LuaObject func, LuaObject update, LuaObject callback)
      throws LuaException {
    return task(func, null, update, callback);
  }

  public LuaAsyncTask task(LuaObject func, Object[] arg, LuaObject update, LuaObject callback)
      throws LuaException {
    LuaAsyncTask task = new LuaAsyncTask(this, func, update, callback);
    task.execute(arg);
    return task;
  }

  public LuaThread thread(LuaObject func) throws LuaException {
    LuaThread thread = newThread(func, new Object[0]);
    thread.start();
    return thread;
  }

  public LuaThread thread(LuaObject func, Object[] arg) throws LuaException {
    LuaThread thread = new LuaThread(this, func, true, arg);
    thread.start();
    return thread;
  }

  public LuaTimer timer(LuaObject func, long period) throws LuaException {
    return timer(func, 0, period, new Object[0]);
  }

  public LuaTimer timer(LuaObject func, long period, Object[] arg) throws LuaException {
    return timer(func, 0, period, arg);
  }

  public LuaTimer timer(LuaObject func, long delay, long period) throws LuaException {
    return timer(func, delay, period, new Object[0]);
  }

  public LuaTimer timer(LuaObject func, long delay, long period, Object[] arg) throws LuaException {
    LuaTimer timer = new LuaTimer(this, func, arg);
    timer.start(delay, period);
    return timer;
  }

  public Ticker ticker(final LuaObject func, long period) throws LuaException {
    Ticker timer = new Ticker();
    timer.setOnTickListener(
        new Ticker.OnTickListener() {
          @Override
          public void onTick() {
            try {
              func.call();
            } catch (LuaException e) {
              e.printStackTrace();
              sendError("onTick", e);
            }
          }
        });
    timer.start();
    timer.setPeriod(period);
    return timer;
  }

  public void setContentView(String layout) throws LuaException {
    setContentView(layout, null);
  }

  public void setContentView(String layout, LuaObject env) throws LuaException {
    LuaObject loadlayout = L.getLuaObject("loadlayout");
    View view = (View) loadlayout.call(layout, env);
    super.setContentView(view);
  }

  public void setContentView(LuaObject layout) throws LuaException {
    setContentView(layout, null);
  }

  public void setContentView(LuaObject layout, LuaObject env) throws LuaException {
    LuaObject loadlayout = L.getLuaObject("loadlayout");
    View view = null;
    if (layout.isString()) view = (View) loadlayout.call(layout.getString(), env);
    else if (layout.isTable()) view = (View) loadlayout.call(layout, env);
    else throw new LuaException("layout may be table or string.");
    super.setContentView(view);
  }

  public void result(Object[] data) {
    Intent res = new Intent();
    res.putExtra(NAME, getIntent().getStringExtra(NAME));
    res.putExtra(DATA, data);
    setResult(0, res);
    finish();
  }

  private void initLua() throws Exception {
    L = LuaStateFactory.newLuaState();
    L.openLibs();
    L.pushJavaObject(this);
    L.setGlobal("activity");
    L.getGlobal("activity");
    L.setGlobal("this");

    L.pushJavaObject(com.luafabric.studio.falling.core.R.class);
    L.setGlobal("R");

    L.newTable();
    L.pushJavaObject(com.google.android.material.R.class);
    L.setField(-2, "R");
    L.setGlobal("material");

    L.newTable();
    L.pushJavaObject(androidx.appcompat.R.class);
    L.setField(-2, "R");
    L.setGlobal("androidx");

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

    L.getGlobal("package");
    L.pushString(luaLpath);
    L.setField(-2, "path");
    L.pushString(luaCpath);
    L.setField(-2, "cpath");
    L.pop(1);

    // 注册 assets searcher：require("xxx") 可从 apk 内 assets 或 assets/lua/ 加载（如 memory、lanes）
    JavaFunction assetLoader = new LuaAssetLoader(this, L);
    L.getGlobal("package");
    L.getField(-1, "searchers");
    int searchersLen = L.rawLen(-1);
    L.pushInteger(searchersLen + 1);
    L.pushJavaFunction(assetLoader);
    L.setTable(-3);
    L.pop(1);

    JavaFunction set =
        new JavaFunction(L) {
          @Override
          public int execute() throws LuaException {
            LuaThread thread = (LuaThread) L.toJavaObject(2);
            thread.set(L.toString(3), L.toJavaObject(4));
            return 0;
          }
        };
    set.register("set");

    JavaFunction call =
        new JavaFunction(L) {
          @Override
          public int execute() throws LuaException {
            LuaThread thread = (LuaThread) L.toJavaObject(2);
            int top = L.getTop();
            if (top > 3) {
              Object[] args = new Object[top - 3];
              for (int i = 4; i <= top; i++) {
                args[i - 4] = L.toJavaObject(i);
              }
              thread.call(L.toString(3), args);
            } else if (top == 3) {
              thread.call(L.toString(3));
            }
            return 0;
          }
        };
    call.register("call");
  }

  public void setDebug(boolean isDebug) {
    mDebug = isDebug;
  }

  private void initENV() throws LuaException {
    if (!new File(luaDir + "/settings.json").exists()) return;

    try {
      L.newTable();
      LuaObject env = L.getLuaObject(-1);
      L.setUpValue(-2, 1);

      Map<String, Object> jsonMap =
          muling.views.tool.utils.JsonUtil.parseObject(
              com.androlua.util.FileUtil.read(luaDir + "/settings.json"));

      Map<String, Object> application = (Map<String, Object>) jsonMap.get("application");

      String label = (String) application.get("label");
      Boolean debug = (Boolean) application.get("debugmode");

      setTitle(label);
      mDebug = debug;

      @SuppressWarnings("unchecked")
      List<String> globalUtils = (List<String>) jsonMap.get("global_utils");

      if (globalUtils != null && !globalUtils.isEmpty()) {
        LuaFunctionRegistrar registrar = new LuaFunctionRegistrar(L, this, luaDir);
        registrar.registerSelectedFunctions(globalUtils);
      }

    } catch (Exception e) {
      sendMsg(e.getMessage());
    }
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
      setContentView(layout);
      sendMsg(e.getMessage());
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
      setContentView(layout);
      sendMsg(e.getMessage());
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

  public void showLogs() {
    new AlertDialog.Builder(this)
        .setTitle("Logs")
        .setAdapter(adapter, null)
        .setPositiveButton(android.R.string.ok, null)
        .create()
        .show();
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

  @Override
  public void sendError(String title, Exception msg) {
    Object ret = runFunc("onError", title, msg);
    if (ret != null && ret.getClass() == Boolean.class && (Boolean) ret) {
    } else sendMsg(title + ": " + msg.getMessage());
  }

  /*
  @SuppressLint("ShowToast")
  public void showToast(String text) {
      long now = System.currentTimeMillis();
      status.append(text + "\n");
      adapter.add(text);
      if (toast == null || now - lastShow > 1000) {
          toastbuilder.setLength(0);
          toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
          toastbuilder.append(text);
          toast.show();
      } else {
          toastbuilder.append("\n");
          toastbuilder.append(text);
          toast.setText(toastbuilder.toString());
          toast.setDuration(Toast.LENGTH_LONG);
      }
      lastShow = now;
  }
  */

  @SuppressLint("ShowToast")
  public void showToast(String text) {
    long now = System.currentTimeMillis();
    if (toast == null || now - lastShow > 1000) {
      toastbuilder.setLength(0);
      toastbuilder.append(text);

      LayoutInflater inflater = LayoutInflater.from(this);
      View layout = inflater.inflate(R.layout.toast, null);
      TextView tv = layout.findViewById(R.id.toast_text);
      tv.setText(toastbuilder.toString());

      toast = new Toast(getApplicationContext());
      toast.setView(layout);
      toast.setDuration(Toast.LENGTH_LONG);
      toast.show();
    } else {
      toastbuilder.append("\n").append(text);
      View view = toast.getView();
      if (view != null) {
        TextView tv = view.findViewById(R.id.toast_text);
        if (tv != null) {
          tv.setText(toastbuilder.toString());
          tv.scrollTo(0, tv.getLineHeight() * tv.getLineCount());
        }
      }
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
            if (mDebug) showToast(data);
            status.append(data + "\n");
            adapter.add(data);
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
      }
    }
  }
}
