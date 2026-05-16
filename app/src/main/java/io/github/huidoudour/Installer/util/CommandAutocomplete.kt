package io.github.huidoudour.Installer.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令自动补全工具类
 * 参考aShell项目的Commands.java实现
 */
public class CommandAutocomplete {
    
    // 常用命令列表
    private static final List<String> COMMON_COMMANDS = new ArrayList<>();
    
    static {
        // 包管理器命令
        COMMON_COMMANDS.add("pm list packages");
        COMMON_COMMANDS.add("pm list packages -3");
        COMMON_COMMANDS.add("pm list packages -s");
        COMMON_COMMANDS.add("pm install <path>");
        COMMON_COMMANDS.add("pm uninstall <package>");
        COMMON_COMMANDS.add("pm clear <package>");
        COMMON_COMMANDS.add("pm enable <component>");
        COMMON_COMMANDS.add("pm disable <component>");
        COMMON_COMMANDS.add("pm grant <package> <permission>");
        COMMON_COMMANDS.add("pm revoke <package> <permission>");
        
        // Activity管理器命令
        COMMON_COMMANDS.add("am start -n <package>/<activity>");
        COMMON_COMMANDS.add("am force-stop <package>");
        COMMON_COMMANDS.add("am kill <package>");
        COMMON_COMMANDS.add("am kill-all");
        
        // AppOps命令
        COMMON_COMMANDS.add("appops get <package>");
        COMMON_COMMANDS.add("appops set <package> <op> <mode>");
        COMMON_COMMANDS.add("cmd appops get <package>");
        COMMON_COMMANDS.add("cmd appops set <package> <op> <mode>");
        
        // 文件系统命令
        COMMON_COMMANDS.add("ls");
        COMMON_COMMANDS.add("ls -la");
        COMMON_COMMANDS.add("ls -R");
        COMMON_COMMANDS.add("cd <path>");
        COMMON_COMMANDS.add("pwd");
        COMMON_COMMANDS.add("cat <file>");
        COMMON_COMMANDS.add("cp <src> <dst>");
        COMMON_COMMANDS.add("mv <src> <dst>");
        COMMON_COMMANDS.add("rm <file>");
        COMMON_COMMANDS.add("rm -rf <path>");
        COMMON_COMMANDS.add("mkdir <dir>");
        COMMON_COMMANDS.add("touch <file>");
        COMMON_COMMANDS.add("chmod <permissions> <file>");
        COMMON_COMMANDS.add("chown <owner> <file>");
        
        // 系统信息命令
        COMMON_COMMANDS.add("whoami");
        COMMON_COMMANDS.add("uname -a");
        COMMON_COMMANDS.add("df -h");
        COMMON_COMMANDS.add("free -h");
        COMMON_COMMANDS.add("ps aux");
        COMMON_COMMANDS.add("top");
        COMMON_COMMANDS.add("top -n1");
        COMMON_COMMANDS.add("netstat");
        COMMON_COMMANDS.add("ping <host>");
        COMMON_COMMANDS.add("getprop");
        COMMON_COMMANDS.add("setprop <name> <value>");
        
        // 日志命令
        COMMON_COMMANDS.add("logcat");
        COMMON_COMMANDS.add("logcat -d");
        COMMON_COMMANDS.add("logcat -c");
        COMMON_COMMANDS.add("dmesg");
        
        // Dumpsys命令
        COMMON_COMMANDS.add("dumpsys battery");
        COMMON_COMMANDS.add("dumpsys display");
        COMMON_COMMANDS.add("dumpsys activity");
        COMMON_COMMANDS.add("dumpsys package");
        COMMON_COMMANDS.add("dumpsys meminfo");
        
        // 其他命令
        COMMON_COMMANDS.add("echo <message>");
        COMMON_COMMANDS.add("sleep <seconds>");
        COMMON_COMMANDS.add("date");
        COMMON_COMMANDS.add("reboot");
        COMMON_COMMANDS.add("settings get secure default_input_method");
        COMMON_COMMANDS.add("wm size");
        COMMON_COMMANDS.add("wm density");
        COMMON_COMMANDS.add("service list");
        COMMON_COMMANDS.add("grep <pattern> <file>");
        COMMON_COMMANDS.add("find <path> -name <name>");
        COMMON_COMMANDS.add("clear");
        COMMON_COMMANDS.add("exit");
    }
    
    /**
     * 根据输入获取匹配的命令列表
     */
    public static List<String> getMatchingCommands(String input) {
        List<String> matches = new ArrayList<>();
        if (input == null || input.trim().isEmpty()) {
            return matches;
        }
        
        String lowerInput = input.toLowerCase();
        for (String command : COMMON_COMMANDS) {
            if (command.toLowerCase().startsWith(lowerInput)) {
                matches.add(command);
            }
        }
        
        return matches;
    }
    
    /**
     * 获取所有常用命令
     */
    public static List<String> getAllCommands() {
        return new ArrayList<>(COMMON_COMMANDS);
    }
}
