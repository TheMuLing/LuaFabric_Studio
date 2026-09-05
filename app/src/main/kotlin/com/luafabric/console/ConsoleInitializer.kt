package com.luafabric.console

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.luafabric.studio.falling.core.console.DebugConsoleRegistry

/**
 * 应用启动期注册调试控制台桥实现（ContentProvider 早于 Application.onCreate 初始化）。
 * 用户应用（core-apk 打包）不含本 Provider，注册表保持 null，core 钩点全部短路。
 */
class ConsoleInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        val appContext = context ?: return true
        DebugConsoleRegistry.register(ConsoleBridgeImpl(appContext))
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
