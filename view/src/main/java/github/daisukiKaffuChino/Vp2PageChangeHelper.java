package github.daisukiKaffuChino;

import androidx.annotation.Keep;
import androidx.viewpager2.widget.ViewPager2;

import com.luajava.LuaObject;
import com.luajava.LuaTable;

/**
 * ViewPager2 页面回调辅助。
 * ViewPager2.registerOnPageChangeCallback 要求 {@link ViewPager2.OnPageChangeCallback}（抽象类），
 * luajava 的参数转换只支持接口代理，Lua 表无法自动转为该抽象类，故在此以 Java 侧注册，
 * 由 Lua 表提供 onPageSelected 等方法实现（与 ViewPager1 的 OnPageChangeListener 语义对齐）。
 */
@Keep
public class Vp2PageChangeHelper {
    private Vp2PageChangeHelper() {
    }

    public static void register(ViewPager2 pager, final LuaTable callback) {
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                try {
                    LuaObject fn = callback.getField("onPageSelected");
                    if (fn != null && fn.isFunction()) {
                        fn.call(new Object[]{ position });
                    }
                } catch (Throwable ignore) {
                }
            }
        });
    }
}