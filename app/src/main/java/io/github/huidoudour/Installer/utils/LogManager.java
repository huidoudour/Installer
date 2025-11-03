package io.github.huidoudour.Installer.utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 全局日志管理器
 * 用于在不同Fragment之间共享日志数据
 */
public class LogManager {
    
    private static LogManager instance;
    private final List<String> logs;
    private final List<LogListener> listeners;
    private final SimpleDateFormat dateFormat;
    
    public interface LogListener {
        void onLogAdded(String log);
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
    
    public void addLog(String message) {
        String timestamp = dateFormat.format(new Date());
        String logMessage = timestamp + ": " + message;
        logs.add(logMessage);
        
        // 通知所有监听器
        for (LogListener listener : listeners) {
            listener.onLogAdded(logMessage);
        }
        
        System.out.println("ShizukuInstallerApp: " + logMessage);
    }
    
    public void clearLogs() {
        logs.clear();
        
        // 通知所有监听器
        for (LogListener listener : listeners) {
            listener.onLogCleared();
        }
    }
    
    public String getAllLogs() {
        if (logs.isEmpty()) {
            return "等待操作...";
        }
        StringBuilder sb = new StringBuilder();
        for (String log : logs) {
            sb.append(log).append("\n");
        }
        return sb.toString();
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
