package com.luafabric.studio.falling;

/**
 * 保活 androidx.viewpager2 类：Lua 项目经反射使用 ViewPager2，
 * Java 侧零调用会被打包流水线从 DEX 输入中剔除（同包 FragmentStateAdapter
 * 因有内部字节码使用而幸存）。此处用静态字段类型引用建立硬字节码依赖，
 * 强制 widget 包进入 DEX，类本身无任何逻辑。
 */
public final class Vp2Holder {
    private static final androidx.viewpager2.widget.ViewPager2 KEEP_VIEWPAGER = null;
    private static final androidx.viewpager2.adapter.FragmentStateAdapter KEEP_ADAPTER = null;

    private Vp2Holder() {
    }
}