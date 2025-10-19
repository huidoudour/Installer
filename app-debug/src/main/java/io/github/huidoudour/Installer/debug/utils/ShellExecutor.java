package io.github.huidoudour.Installer.debug.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * Shell 终端执行助手
 * 
 * 使用 Shizuku 提供的原生能力执行高权限命令
 * 支持普通命令和 Root 命令两种模式
 */
public class ShellExecutor {

    private static final String TAG = "ShellExecutor";
    
    /**
     * 命令执行回调接口
     */
    public interface ExecuteCallback {
        void onOutput(String line);
        void onError(String error);
        void onComplete(int exitCode);
    }

    /**
     * 检查 Shizuku 是否可用且已授权
     */
    public static boolean isShizukuAvailable() {
        try {
            return Shizuku.pingBinder() && 
                   Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED &&
                   !Shizuku.isPreV11() &&
                   Shizuku.getVersion() >= 11;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 使用 Shizuku 执行高权限命令（异步）
     * 
     * @param command 要执行的命令
     * @param callback 回调接口
     */
    public static void executeShizukuCommand(String command, ExecuteCallback callback) {
        new Thread(() -> {
            try {
                // 使用反射调用 Shizuku 的私有 API
                Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
                java.lang.reflect.Method newProcessMethod = shizukuClass.getDeclaredMethod(
                    "newProcess", 
                    String[].class, 
                    String[].class, 
                    String.class
                );
                newProcessMethod.setAccessible(true);
                
                Process process = (Process) newProcessMethod.invoke(
                    null,
                    new String[]{"sh", "-c", command},
                    null,
                    null
                );
                
                // 读取标准输出
                BufferedReader stdoutReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                );
                
                // 读取错误输出
                BufferedReader stderrReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream())
                );
                
                // 实时输出标准输出
                Thread stdoutThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = stdoutReader.readLine()) != null) {
                            callback.onOutput(line);
                        }
                    } catch (Exception e) {
                        callback.onError("读取输出失败: " + e.getMessage());
                    }
                });
                
                // 实时输出错误输出
                Thread stderrThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = stderrReader.readLine()) != null) {
                            callback.onError(line);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                });
                
                stdoutThread.start();
                stderrThread.start();
                
                int exitCode = process.waitFor();
                
                stdoutThread.join();
                stderrThread.join();
                
                stdoutReader.close();
                stderrReader.close();
                
                callback.onComplete(exitCode);
                
            } catch (Exception e) {
                callback.onError("命令执行失败: " + e.getMessage());
                callback.onComplete(-1);
            }
        }).start();
    }

    /**
     * 使用普通权限执行命令（异步）
     */
    public static void executeNormalCommand(String command, ExecuteCallback callback) {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
                
                BufferedReader stdoutReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                );
                
                BufferedReader stderrReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream())
                );
                
                Thread stdoutThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = stdoutReader.readLine()) != null) {
                            callback.onOutput(line);
                        }
                    } catch (Exception e) {
                        callback.onError("读取输出失败: " + e.getMessage());
                    }
                });
                
                Thread stderrThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = stderrReader.readLine()) != null) {
                            callback.onError(line);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                });
                
                stdoutThread.start();
                stderrThread.start();
                
                int exitCode = process.waitFor();
                
                stdoutThread.join();
                stderrThread.join();
                
                stdoutReader.close();
                stderrReader.close();
                
                callback.onComplete(exitCode);
                
            } catch (Exception e) {
                callback.onError("命令执行失败: " + e.getMessage());
                callback.onComplete(-1);
            }
        }).start();
    }

    /**
     * 智能执行命令（自动选择 Shizuku 或普通模式）
     */
    public static void executeCommand(String command, ExecuteCallback callback) {
        if (isShizukuAvailable()) {
            executeShizukuCommand(command, callback);
        } else {
            executeNormalCommand(command, callback);
        }
    }

    /**
     * 快捷命令列表
     */
    public static class QuickCommands {
        public static final String[] COMMANDS = {
            "ls -la",
            "pwd",
            "whoami",
            "uname -a",
            "df -h",
            "free -h",
            "ps aux",
            "pm list packages",
            "getprop",
            "logcat -d -v time"
        };
        
        public static final String[] COMMAND_NAMES = {
            "列出文件",
            "当前目录",
            "当前用户",
            "系统信息",
            "磁盘空间",
            "内存信息",
            "进程列表",
            "已安装应用",
            "系统属性",
            "系统日志"
        };
    }

    /**
     * 复制文本到剪贴板
     */
    public static boolean copyToClipboard(Context context, String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) 
                context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Terminal Output", text);
            clipboard.setPrimaryClip(clip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 命令历史记录管理
     */
    public static class CommandHistory {
        private static final int MAX_HISTORY = 100;
        private static final List<String> history = new ArrayList<>();
        private static int currentIndex = -1;

        public static void addCommand(String command) {
            if (command == null || command.trim().isEmpty()) return;
            
            // 避免连续重复
            if (!history.isEmpty() && history.get(history.size() - 1).equals(command)) {
                return;
            }
            
            history.add(command);
            if (history.size() > MAX_HISTORY) {
                history.remove(0);
            }
            currentIndex = history.size();
        }

        public static String getPrevious() {
            if (history.isEmpty()) return "";
            currentIndex = Math.max(0, currentIndex - 1);
            return currentIndex < history.size() ? history.get(currentIndex) : "";
        }

        public static String getNext() {
            if (history.isEmpty()) return "";
            currentIndex = Math.min(history.size(), currentIndex + 1);
            return currentIndex < history.size() ? history.get(currentIndex) : "";
        }

        public static List<String> getAll() {
            return new ArrayList<>(history);
        }

        public static void clear() {
            history.clear();
            currentIndex = -1;
        }
    }
}
