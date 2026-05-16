package io.github.huidoudour.Installer.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.huidoudour.Installer.R;

/**
 * 全局日志管理器
 * 用于在不同Fragment之间共享日志数据
 * 支持无限日志输出，通过 RecyclerView 保证流畅渲染
 * 日志持久化到 SharedPreferences，App 重启后不丢失
 */
public class LogManager {

    private static final String PREFS_NAME = "log_manager_prefs";
    private static final String KEY_LOGS_JSON = "logs_json";

    private static LogManager instance;
    private final List<String> logs;
    private final List<LogListener> listeners;
    private final SimpleDateFormat dateFormat;
    private Context context;
    private SharedPreferences prefs;

    public interface LogListener {
        /** 新增一条日志，index 为在当前列表中的位置 */
        void onLogAdded(String log, int index);
        /** 日志被清空 */
        void onLogCleared();
    }

    private LogManager() {
        logs = new ArrayList<>();
        listeners = new ArrayList<>();
        dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    }

    public static synchronized LogManager getInstance() {
        if (instance == null) {
            instance = new LogManager();
        }
        return instance;
    }

    public void setContext(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // 启动时加载已有日志
        loadLogs();
    }

    // ── 持久化 ─────────────────────────────────────────────────────────────────

    /** 从 SharedPreferences 加载日志 */
    private void loadLogs() {
        if (prefs == null) return;
        String json = prefs.getString(KEY_LOGS_JSON, null);
        if (json == null || json.isEmpty()) return;
        try {
            logs.clear();
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.beginArray();
            while (reader.hasNext()) {
                logs.add(reader.nextString());
            }
            reader.endArray();
            reader.close();
        } catch (Exception e) {
            // 加载失败，从头开始
            logs.clear();
        }
    }

    /** 异步保存日志到 SharedPreferences */
    private void saveLogsAsync() {
        if (prefs == null) return;
        // 在后台线程序列化并写入
        new Thread(() -> {
            try {
                StringWriter stringWriter = new StringWriter();
                JsonWriter writer = new JsonWriter(stringWriter);
                writer.beginArray();
                for (String log : logs) {
                    writer.value(log);
                }
                writer.endArray();
                writer.close();
                prefs.edit().putString(KEY_LOGS_JSON, stringWriter.toString()).apply();
            } catch (Exception e) {
                // 忽略写入错误
            }
        }).start();
    }

    // ── 日志操作 ────────────────────────────────────────────────────────────────

    public void addLog(String message) {
        String timestamp = dateFormat.format(new Date());
        String logMessage = timestamp + ": " + message;

        logs.add(logMessage);
        int insertedIndex = logs.size() - 1;

        // 通知所有监听器
        for (LogListener listener : new ArrayList<>(listeners)) {
            listener.onLogAdded(logMessage, insertedIndex);
        }

        // 持久化保存
        saveLogsAsync();

        // 同时输出到 System.out，方便 logcat 查看
        String logPrefix = context != null ?
            context.getString(R.string.app_name) : "Installer";
        System.out.println(logPrefix + ": " + logMessage);
    }

    public void clearLogs() {
        logs.clear();
        for (LogListener listener : new ArrayList<>(listeners)) {
            listener.onLogCleared();
        }
        saveLogsAsync();
    }

    /** 返回当前全量日志文本（用于导出） */
    public String getAllLogs() {
        if (logs.isEmpty()) {
            if (context != null) {
                return context.getString(R.string.log_manager_waiting);
            }
            return "等待操作...";
        }
        StringBuilder sb = new StringBuilder();
        for (String log : logs) {
            sb.append(log).append("\n");
        }
        return sb.toString();
    }

    /** 返回不可变的日志列表快照（供 RecyclerView Adapter 初始化使用） */
    public List<String> getLogsSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(logs));
    }

    public int getLogCount() {
        return logs.size();
    }

    public String getLastUpdateTime() {
        if (logs.isEmpty()) {
            return "--:--:--";
        }
        return dateFormat.format(new Date());
    }

    public void addListener(LogListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }
}
