# LuaFabric Compose 打包基底壳 R8 规则。
#
# 目标：产物禁止包含调试控制台（com.luafabric.studio.falling.core.console.*）。
# 控制台类已被 share 侧 LuaJavaAPI/LuaPrint 与 compose LuaActivity 改为 ConsoleBridgeRef
# 反射访问（编译期零 import）→ R8 无强引用链 → 本文件不 keep 该包 → shrink 直接剔除。

# ---- JNI/native 绑定：luajava 全保留（native 方法签名不可混淆） ----
-keep class com.luajava.** { *; }

# ---- androlua 运行时（LuaApplication / LuaDexLoader / LuaAssetLoader / LuaPrint / LuaResources ...） ----
-keep class com.androlua.** { *; }

# ---- compose 宿主（LuaActivity / SplashWelcome / ComposeUiHost / ComposeConfig） ----
-keep,includedescriptorclasses class com.luafabric.compose.** { *; }

# ---- Compose 库：宿主 Astile 依赖组成，整体保留（含 material3 反射/枚举） ----
-keep class androidx.compose.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.annotation.** { *; }
-keep class androidx.collection.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.savedstate.** { *; }
-keep class androidx.startup.** { *; }
-keep class androidx.arch.core.** { *; }

# ---- Kotlin 运行时（compose 硬依赖） ----
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# ---- 支持库 lambda/内联 ----
-dontwarn kotlin.**
-dontwarn androidx.**
-dontwarn org.jetbrains.**