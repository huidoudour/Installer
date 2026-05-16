package io.github.huidoudour.Installer.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令书签管理工具类
 * 用于保存和管理用户收藏的常用命令
 */
public class CommandBookmarks {
    
    private static final String PREFS_NAME = "shell_bookmarks";
    private static final String KEY_BOOKMARKS = "bookmarks_list";
    private static final String SEPARATOR = "|||";
    
    /**
     * 添加书签
     */
    public static void addBookmark(Context context, String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        
        List<String> bookmarks = getBookmarks(context);
        if (!bookmarks.contains(command)) {
            bookmarks.add(0, command); // 添加到列表开头
            saveBookmarks(context, bookmarks);
        }
    }
    
    /**
     * 删除书签
     */
    public static void removeBookmark(Context context, String command) {
        List<String> bookmarks = getBookmarks(context);
        bookmarks.remove(command);
        saveBookmarks(context, bookmarks);
    }
    
    /**
     * 检查是否已收藏
     */
    public static boolean isBookmarked(Context context, String command) {
        return getBookmarks(context).contains(command);
    }
    
    /**
     * 获取所有书签
     */
    public static List<String> getBookmarks(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String bookmarksStr = prefs.getString(KEY_BOOKMARKS, "");
        
        List<String> bookmarks = new ArrayList<>();
        if (!bookmarksStr.isEmpty()) {
            String[] items = bookmarksStr.split(SEPARATOR);
            for (String item : items) {
                if (!item.isEmpty()) {
                    bookmarks.add(item);
                }
            }
        }
        
        return bookmarks;
    }
    
    /**
     * 保存书签列表
     */
    private static void saveBookmarks(Context context, List<String> bookmarks) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < bookmarks.size(); i++) {
            if (i > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(bookmarks.get(i));
        }
        
        prefs.edit().putString(KEY_BOOKMARKS, sb.toString()).apply();
    }
    
    /**
     * 清空所有书签
     */
    public static void clearAllBookmarks(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_BOOKMARKS).apply();
    }
}
