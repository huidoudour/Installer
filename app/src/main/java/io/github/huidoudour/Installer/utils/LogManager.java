package io.github.huidoudour.Installer.utils;

import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogManager {
    private static LogManager instance;
    private final List<String> logs = new ArrayList<>();
    private final List<LogListener> listeners = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public interface LogListener {
        void onLogAdded(String log);
        void onLogCleared();
    }

    private LogManager() {
    }

    public static synchronized LogManager getInstance() {
        if (instance == null) {
            instance = new LogManager();
        }
        return instance;
    }

    public void addLog(String message) {
        String timestamp = timeFormat.format(new Date());
        String logMessage = timestamp + ": " + message;
        logs.add(logMessage);
        
        // 通知所有监听器
        for (LogListener listener : listeners) {
            listener.onLogAdded(logMessage);
        }
    }

    public String getAllLogs() {
        return TextUtils.join("\n", logs);
    }

    public int getLogCount() {
        return logs.size();
    }

    public void clearLogs() {
        logs.clear();
        
        // 通知所有监听器
        for (LogListener listener : listeners) {
            listener.onLogCleared();
        }
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