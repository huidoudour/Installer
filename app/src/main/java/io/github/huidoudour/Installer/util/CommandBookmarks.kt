package io.github.huidoudour.Installer.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 命令书签管理类
 */
object CommandBookmarks {

    private const val PREFS_NAME = "command_bookmarks"
    private const val KEY_BOOKMARKS = "bookmarks"
    private const val SEPARATOR = "|||"

    /**
     * 获取所有书签
     */
    fun getBookmarks(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bookmarksString = prefs.getString(KEY_BOOKMARKS, "") ?: ""
        return if (bookmarksString.isEmpty()) {
            emptyList()
        } else {
            bookmarksString.split(SEPARATOR).filter { it.isNotBlank() }
        }
    }

    /**
     * 添加书签
     */
    fun addBookmark(context: Context, command: String) {
        val bookmarks = getBookmarks(context).toMutableList()
        if (!bookmarks.contains(command)) {
            bookmarks.add(command)
            saveBookmarks(context, bookmarks)
        }
    }

    /**
     * 删除书签
     */
    fun removeBookmark(context: Context, command: String) {
        val bookmarks = getBookmarks(context).toMutableList()
        bookmarks.remove(command)
        saveBookmarks(context, bookmarks)
    }

    /**
     * 清空所有书签
     */
    fun clearBookmarks(context: Context) {
        saveBookmarks(context, emptyList())
    }

    /**
     * 检查是否为书签
     */
    fun isBookmark(context: Context, command: String): Boolean {
        return getBookmarks(context).contains(command)
    }

    private fun saveBookmarks(context: Context, bookmarks: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BOOKMARKS, bookmarks.joinToString(SEPARATOR)).apply()
    }

    /**
     * 获取推荐的书签
     */
    fun getRecommendedBookmarks(): List<String> {
        return listOf(
            "ls -la /sdcard/Download",
            "pm list packages -3",
            "dumpsys battery",
            "getprop | grep product",
            "df -h",
            "free -h"
        )
    }
}
