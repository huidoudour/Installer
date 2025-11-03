package io.github.huidoudour.Installer.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * Shell 终端执行助手
 * 
 * 使用 Shizuku 提供的原生能力执行高权限命令
 * 支持普通命令和 Root 命令两种模式
 * 支持持久化Shell会话，保持工作目录状态
 */
public class ShellExecutor {

    private static final String TAG = "ShellExecutor";
    
    // 持久化Shell会话
    private static Process persistentShellProcess = null;
    private static BufferedWriter persistentShellWriter = null;
    private static BufferedReader persistentShellStdout = null;
    private static BufferedReader persistentShellStderr = null;
    private static boolean isShizukuSession = false;
    private static String currentWorkingDirectory = "/";
    
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
            Process process = null;
            BufferedReader stdoutReader = null;
            BufferedReader stderrReader = null;
            
            try {
                // 尝试使用反射调用 Shizuku 的 newProcess 方法
                try {
                    Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
                    java.lang.reflect.Method newProcessMethod = shizukuClass.getDeclaredMethod(
                        "newProcess", 
                        String[].class, 
                        String[].class, 
                        String.class
                    );
                    newProcessMethod.setAccessible(true);
                    
                    process = (Process) newProcessMethod.invoke(
                        null,
                        new String[]{"sh", "-c", command},
                        null,
                        null
                    );
                } catch (NoSuchMethodException e) {
                    // 如果没有找到该方法，尝试使用其他重载
                    try {
                        Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
                        // 查找所有 newProcess 方法
                        for (java.lang.reflect.Method method : shizukuClass.getDeclaredMethods()) {
                            if (method.getName().equals("newProcess")) {
                                method.setAccessible(true);
                                // 尝试调用第一个找到的 newProcess 方法
                                process = (Process) method.invoke(
                                    null,
                                    new String[]{"sh", "-c", command},
                                    null,
                                    null
                                );
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        throw new Exception("Failed to invoke Shizuku newProcess: " + ex.getMessage());
                    }
                }
                
                if (process == null) {
                    throw new Exception("Failed to create process via Shizuku");
                }
                
                // 读取标准输出
                stdoutReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                );
                
                // 读取错误输出
                stderrReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream())
                );
                
                final BufferedReader finalStdoutReader = stdoutReader;
                final BufferedReader finalStderrReader = stderrReader;
                
                // 实时输出标准输出
                Thread stdoutThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = finalStdoutReader.readLine()) != null) {
                            callback.onOutput(line);
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                });
                
                // 实时输出错误输出
                Thread stderrThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = finalStderrReader.readLine()) != null) {
                            callback.onError(line);
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                });
                
                stdoutThread.start();
                stderrThread.start();
                
                int exitCode = process.waitFor();
                
                // 等待输出线程结束
                stdoutThread.join(1000);
                stderrThread.join(1000);
                
                callback.onComplete(exitCode);
                
            } catch (Exception e) {
                callback.onError("Shizuku error: " + e.getMessage());
                callback.onError("Falling back to normal mode...");
                // 降级到普通模式
                executeNormalCommand(command, callback);
                return;
            } finally {
                try {
                    if (stdoutReader != null) stdoutReader.close();
                    if (stderrReader != null) stderrReader.close();
                    if (process != null) process.destroy();
                } catch (Exception e) {
                    // 忽略
                }
            }
        }).start();
    }

    /**
     * 使用普通权限执行命令（异步）
     */
    public static void executeNormalCommand(String command, ExecuteCallback callback) {
        new Thread(() -> {
            Process process = null;
            BufferedReader stdoutReader = null;
            BufferedReader stderrReader = null;
            
            try {
                process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
                
                stdoutReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                );
                
                stderrReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream())
                );
                
                final BufferedReader finalStdoutReader = stdoutReader;
                final BufferedReader finalStderrReader = stderrReader;
                
                Thread stdoutThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = finalStdoutReader.readLine()) != null) {
                            callback.onOutput(line);
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                });
                
                Thread stderrThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = finalStderrReader.readLine()) != null) {
                            callback.onError(line);
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                });
                
                stdoutThread.start();
                stderrThread.start();
                
                int exitCode = process.waitFor();
                
                // 等待输出线程结束
                stdoutThread.join(1000);
                stderrThread.join(1000);
                
                callback.onComplete(exitCode);
                
            } catch (Exception e) {
                callback.onError("Command failed: " + e.getMessage());
                callback.onComplete(-1);
            } finally {
                try {
                    if (stdoutReader != null) stdoutReader.close();
                    if (stderrReader != null) stderrReader.close();
                    if (process != null) process.destroy();
                } catch (Exception e) {
                    // 忽略
                }
            }
        }).start();
    }

    /**
     * 智能执行命令（使用持久化会话）
     */
    public static void executeCommand(String command, ExecuteCallback callback) {
        executePersistentCommand(command, callback);
    }
    
    /**
     * 使用持久化Shell会话执行命令（保持工作目录）
     */
    private static void executePersistentCommand(String command, ExecuteCallback callback) {
        new Thread(() -> {
            try {
                // 检查是否需要创建新会话
                boolean needNewSession = persistentShellProcess == null || !persistentShellProcess.isAlive();
                boolean shizukuAvailable = isShizukuAvailable();
                
                // 如果权限状态变化，需要重新创建会话
                if (!needNewSession && isShizukuSession != shizukuAvailable) {
                    destroyPersistentSession();
                    needNewSession = true;
                }
                
                if (needNewSession) {
                    createPersistentSession(shizukuAvailable);
                }
                
                // 执行命令
                if (persistentShellWriter != null && persistentShellProcess != null) {
                    // 使用唯一标记来识别命令结束
                    String endMarker = "__CMD_END_" + System.currentTimeMillis() + "__";
                    String exitCodeMarker = "__EXIT_CODE_" + System.currentTimeMillis() + "__";
                    
                    // 发送命令
                    persistentShellWriter.write(command + "\n");
                    persistentShellWriter.write("echo " + exitCodeMarker + "$?\n");
                    persistentShellWriter.write("echo " + endMarker + "\n");
                    persistentShellWriter.flush();
                    
                    // 读取输出
                    final int[] exitCode = {0};
                    boolean[] commandEnded = {false};
                    
                    Thread stdoutThread = new Thread(() -> {
                        try {
                            String line;
                            while ((line = persistentShellStdout.readLine()) != null && !commandEnded[0]) {
                                if (line.equals(endMarker)) {
                                    commandEnded[0] = true;
                                    break;
                                } else if (line.startsWith(exitCodeMarker)) {
                                    try {
                                        exitCode[0] = Integer.parseInt(line.substring(exitCodeMarker.length()));
                                    } catch (Exception e) {
                                        exitCode[0] = 0;
                                    }
                                } else {
                                    callback.onOutput(line);
                                }
                            }
                        } catch (Exception e) {
                            // 会话可能已断开
                        }
                    });
                    
                    Thread stderrThread = new Thread(() -> {
                        try {
                            String line;
                            // 使用非阻塞读取
                            while (!commandEnded[0]) {
                                if (persistentShellStderr.ready()) {
                                    line = persistentShellStderr.readLine();
                                    if (line != null) {
                                        callback.onError(line);
                                    }
                                } else {
                                    Thread.sleep(50);
                                }
                            }
                        } catch (Exception e) {
                            // 忽略
                        }
                    });
                    
                    stdoutThread.start();
                    stderrThread.start();
                    
                    // 等待命令完成（最多10秒）
                    long startTime = System.currentTimeMillis();
                    while (!commandEnded[0] && System.currentTimeMillis() - startTime < 10000) {
                        Thread.sleep(100);
                    }
                    
                    // 更新工作目录（执行pwd获取）
                    updateWorkingDirectory();
                    
                    callback.onComplete(exitCode[0]);
                    
                } else {
                    throw new Exception("Failed to create persistent shell session");
                }
                
            } catch (Exception e) {
                callback.onError("Session error: " + e.getMessage());
                callback.onError("Trying to recreate session...");
                destroyPersistentSession();
                // 重试一次
                executeFallbackCommand(command, callback);
            }
        }).start();
    }
    
    /**
     * 创建持久化Shell会话
     */
    private static void createPersistentSession(boolean useShizuku) throws Exception {
        if (useShizuku) {
            // 使用Shizuku创建会话
            try {
                Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
                for (java.lang.reflect.Method method : shizukuClass.getDeclaredMethods()) {
                    if (method.getName().equals("newProcess")) {
                        method.setAccessible(true);
                        // 使用非交互式shell，避免TTY错误
                        persistentShellProcess = (Process) method.invoke(
                            null,
                            new String[]{"sh"},  // 非交互式模式
                            null,
                            null
                        );
                        isShizukuSession = true;
                        break;
                    }
                }
            } catch (Exception e) {
                throw new Exception("Shizuku session creation failed: " + e.getMessage());
            }
        } else {
            // 普通模式 - 使用非交互式shell
            persistentShellProcess = Runtime.getRuntime().exec(new String[]{"sh"});
            isShizukuSession = false;
        }
        
        if (persistentShellProcess != null) {
            persistentShellWriter = new BufferedWriter(
                new OutputStreamWriter(persistentShellProcess.getOutputStream())
            );
            persistentShellStdout = new BufferedReader(
                new InputStreamReader(persistentShellProcess.getInputStream())
            );
            persistentShellStderr = new BufferedReader(
                new InputStreamReader(persistentShellProcess.getErrorStream())
            );
            
            // 初始化环境 - 清除提示符并禁用作业控制
            persistentShellWriter.write("export PS1=''\n");
            persistentShellWriter.write("export PS2=''\n");
            persistentShellWriter.write("set +m\n");  // 禁用作业控制，避免TTY错误
            
            // 切换到用户可访问的目录（/sdcard 或 /data/local/tmp）
            if (useShizuku) {
                // Shizuku模式下可以访问任何目录，使用/data/local/tmp
                persistentShellWriter.write("cd /data/local/tmp 2>/dev/null || cd /sdcard\n");
            } else {
                // 普通模式使用/sdcard
                persistentShellWriter.write("cd /sdcard 2>/dev/null || cd /data/local/tmp\n");
            }
            
            persistentShellWriter.flush();
            
            // 等待初始化完成
            Thread.sleep(200);
            
            // 清空初始输出
            while (persistentShellStdout.ready()) {
                persistentShellStdout.readLine();
            }
            while (persistentShellStderr.ready()) {
                persistentShellStderr.readLine();
            }
        }
    }
    
    /**
     * 销毁持久化会话
     */
    private static void destroyPersistentSession() {
        try {
            if (persistentShellWriter != null) {
                persistentShellWriter.write("exit\n");
                persistentShellWriter.flush();
                persistentShellWriter.close();
            }
            if (persistentShellStdout != null) persistentShellStdout.close();
            if (persistentShellStderr != null) persistentShellStderr.close();
            if (persistentShellProcess != null) persistentShellProcess.destroy();
        } catch (Exception e) {
            // 忽略
        } finally {
            persistentShellProcess = null;
            persistentShellWriter = null;
            persistentShellStdout = null;
            persistentShellStderr = null;
        }
    }
    
    /**
     * 更新当前工作目录
     */
    private static void updateWorkingDirectory() {
        // 这个方法会在后台异步更新，不影响主流程
        // 实际使用中可以通过解析命令来更新，或者定期执行pwd
    }
    
    /**
     * 降级执行（单次命令）
     */
    private static void executeFallbackCommand(String command, ExecuteCallback callback) {
        if (isShizukuAvailable()) {
            executeShizukuCommand(command, callback);
        } else {
            executeNormalCommand(command, callback);
        }
    }

    /**
     * 获取当前工作目录
     */
    public static String getCurrentWorkingDirectory() {
        return currentWorkingDirectory;
    }
    
    /**
     * 重置Shell会话
     */
    public static void resetSession() {
        destroyPersistentSession();
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
            "logcat -d -v time",
            "native:info",
            "native:test"
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
            "系统日志",
            "🔧 Native库信息",
            "🚀 性能测试"
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