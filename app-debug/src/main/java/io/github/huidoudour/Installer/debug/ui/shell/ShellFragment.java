package io.github.huidoudour.Installer.debug.ui.shell;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.huidoudour.Installer.debug.R;
import io.github.huidoudour.Installer.debug.databinding.FragmentShellBinding;
import io.github.huidoudour.Installer.debug.utils.ShellExecutor;
import rikka.shizuku.Shizuku;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ShellFragment extends Fragment {

    private FragmentShellBinding binding;
    private TextView tvTerminalOutput;
    private EditText etCommandInput;
    private TextView tvPrompt;
    private ScrollView scrollViewOutput;
    private View shizukuIndicator;
    private TextView tvShizukuStatus;
    
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private boolean isExecuting = false;
    private int commandCount = 0;
    private int historyIndex = -1;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentShellBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 初始化视图
        tvTerminalOutput = binding.tvTerminalOutput;
        etCommandInput = binding.etCommandInput;
        tvPrompt = binding.tvPrompt;
        scrollViewOutput = binding.scrollViewOutput;
        shizukuIndicator = binding.shizukuIndicator;
        tvShizukuStatus = binding.tvShizukuStatus;

        // 设置点击事件
        binding.btnClearScreen.setOnClickListener(v -> clearScreen());
        binding.btnCopyOutput.setOnClickListener(v -> copyOutput());
        binding.btnQuickCommands.setOnClickListener(v -> showQuickCommands());
        
        // 功能键监听
        binding.btnHistoryUp.setOnClickListener(v -> navigateHistoryUp());
        binding.btnHistoryDown.setOnClickListener(v -> navigateHistoryDown());
        binding.btnTab.setOnClickListener(v -> showPathCompletion());
        binding.btnCtrlC.setOnClickListener(v -> cancelCommand());
        binding.btnEsc.setOnClickListener(v -> etCommandInput.setText(""));

        // 输入框监听 - 回车键执行命令
        etCommandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || 
                (event != null && event.getAction() == KeyEvent.ACTION_DOWN && 
                 event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                executeCommand();
                return true;
            }
            return false;
        });
        
        // 只在用户点击输入框时打开键盘
        etCommandInput.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) 
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etCommandInput, InputMethodManager.SHOW_FORCED);
            }
        });
        
        // 移除自动重新获取焦点的逻辑

        // 更新 Shizuku 状态
        updateShizukuStatus();
        
        // 显示欢迎信息
        showWelcomeMessage();
        
        // 不要自动打开键盘，等待用户点击输入框
        
        // 显示欢迎信息
        showWelcomeMessage();

        return root;
    }

    /**
     * 显示欢迎信息
     */
    private void showWelcomeMessage() {
        appendOutput("Welcome to Termux Shell Emulator", "#00FF00", false);
        appendOutput("Android Shell Environment v1.0", "#808080", false);
        appendOutput("", "#00FF00", false);
        
        if (ShellExecutor.isShizukuAvailable()) {
            appendOutput("[*] Root mode enabled via Shizuku", "#00FF00", false);
        } else {
            appendOutput("[!] User mode (grant Shizuku for root)", "#FFA500", false);
        }
        
        appendOutput("", "#00FF00", false);
        appendOutput("Type 'help' for command list", "#808080", false);
        appendOutput("", "#00FF00", false);
    }

    /**
     * 导航到历史上一条命令
     */
    private void navigateHistoryUp() {
        var history = ShellExecutor.CommandHistory.getAll();
        if (history.isEmpty()) return;
        
        if (historyIndex == -1) {
            historyIndex = history.size() - 1;
        } else if (historyIndex > 0) {
            historyIndex--;
        }
        
        if (historyIndex >= 0 && historyIndex < history.size()) {
            etCommandInput.setText(history.get(historyIndex));
            etCommandInput.setSelection(etCommandInput.getText().length());
        }
    }
    
    /**
     * 导航到历史下一条命令
     */
    private void navigateHistoryDown() {
        var history = ShellExecutor.CommandHistory.getAll();
        if (historyIndex == -1) return;
        
        if (historyIndex < history.size() - 1) {
            historyIndex++;
            etCommandInput.setText(history.get(historyIndex));
            etCommandInput.setSelection(etCommandInput.getText().length());
        } else {
            historyIndex = -1;
            etCommandInput.setText("");
        }
    }
    
    /**
     * 显示路径补全菜单 (简化版Tab功能)
     */
    private void showPathCompletion() {
        String[] commonPaths = {
            "/",
            "/sdcard/",
            "/sdcard/Download/",
            "/data/local/tmp/",
            "/data/data/",
            "/system/",
            "/system/bin/",
            "~/"
        };
        
        String[] pathNames = {
            "Root (/)",
            "SD卡 (/sdcard/)",
            "Download (/sdcard/Download/)",
            "Tmp (/data/local/tmp/)",
            "App Data (/data/data/)",
            "System (/system/)",
            "System Bin (/system/bin/)",
            "Home (~/) "
        };
        
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("📁 Quick Path")
            .setItems(pathNames, (dialog, which) -> {
                String currentText = etCommandInput.getText().toString();
                String path = commonPaths[which];
                
                // 如果已经有cd命令，只替换路径
                if (currentText.startsWith("cd ")) {
                    etCommandInput.setText("cd " + path);
                } else {
                    etCommandInput.setText("cd " + path);
                }
                etCommandInput.setSelection(etCommandInput.getText().length());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * 取消当前命令 (Ctrl+C)
     */
    private void cancelCommand() {
        if (isExecuting) {
            // 重置Shell会话
            ShellExecutor.resetSession();
            appendOutput("^C", "#FF4444", true);
            appendOutput("[Command cancelled, session reset]", "#FFA500", false);
            appendOutput("", "#00FF00", false);
            
            etCommandInput.setEnabled(true);
            etCommandInput.requestFocus();
            isExecuting = false;
        } else {
            // 清空输入
            etCommandInput.setText("");
        }
    }
    
    /**
     * 执行命令
     */
    private void executeCommand() {
        String command = etCommandInput.getText().toString().trim();
        if (command.isEmpty()) return;

        if (isExecuting) {
            Toast.makeText(requireContext(), "Command running...", Toast.LENGTH_SHORT).show();
            return;
        }

        // 添加到历史
        ShellExecutor.CommandHistory.addCommand(command);
        historyIndex = -1;  // 重置历史索引
        commandCount++;

        // 显示命令提示符和命令
        String prompt = ShellExecutor.isShizukuAvailable() ? "root@termux:~#" : "user@termux:~$";
        appendOutput(prompt + " " + command, "#00FFFF", true);

        // 内置命令
        if (handleBuiltinCommand(command)) {
            etCommandInput.setText("");
            return;
        }

        // 清空输入框
        etCommandInput.setText("");
        etCommandInput.setEnabled(false);
        isExecuting = true;

        // 执行命令
        ShellExecutor.executeCommand(command, new ShellExecutor.ExecuteCallback() {
            @Override
            public void onOutput(String line) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        appendOutput(line, "#FFFFFF", false);
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        appendOutput(error, "#FF4444", false);
                    });
                }
            }

            @Override
            public void onComplete(int exitCode) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (exitCode != 0) {
                            appendOutput("[Process completed with exit code " + exitCode + "]", "#FFA500", false);
                        }
                        appendOutput("", "#00FF00", false);
                        
                        etCommandInput.setEnabled(true);
                        isExecuting = false;
                        
                        // 保持键盘打开
                        keepKeyboardOpen();
                    });
                }
            }
        });
    }

    /**
     * 处理内置命令
     */
    private boolean handleBuiltinCommand(String command) {
        switch (command.toLowerCase()) {
            case "help":
                showHelpMessage();
                return true;
            case "clear":
            case "cls":
                clearScreen();
                return true;
            case "history":
                showHistory();
                return true;
            case "exit":
            case "quit":
                appendOutput("👋 Please use app navigation to switch pages", "#808080", false);
                return true;
            default:
                return false;
        }
    }

    /**
     * 显示帮助信息
     */
    private void showHelpMessage() {
        appendOutput("", "#00FF00", false);
        appendOutput("Built-in commands:", "#00FF00", true);
        appendOutput("  help     - Show this help", "#FFFFFF", false);
        appendOutput("  clear    - Clear screen", "#FFFFFF", false);
        appendOutput("  history  - Show command history", "#FFFFFF", false);
        appendOutput("  exit     - Exit tip", "#FFFFFF", false);
        appendOutput("", "#00FF00", false);
        appendOutput("Shortcuts:", "#00FF00", true);
        appendOutput("  C   - Clear screen", "#FFFFFF", false);
        appendOutput("  📋  - Copy output", "#FFFFFF", false);
        appendOutput("  ⚡  - Quick commands", "#FFFFFF", false);
        appendOutput("", "#00FF00", false);
        appendOutput("All Linux shell commands supported", "#808080", false);
        appendOutput("", "#00FF00", false);
    }

    /**
     * 显示命令历史
     */
    private void showHistory() {
        var history = ShellExecutor.CommandHistory.getAll();
        appendOutput("", "#00FF00", false);
        appendOutput("Command History:", "#00FF00", true);
        appendOutput("", "#00FF00", false);
        
        if (history.isEmpty()) {
            appendOutput("No history yet", "#808080", false);
        } else {
            for (int i = 0; i < history.size(); i++) {
                appendOutput("  " + (i + 1) + ". " + history.get(i), "#FFFFFF", false);
            }
        }
        appendOutput("", "#00FF00", false);
    }

    /**
     * 显示快捷命令对话框
     */
    private void showQuickCommands() {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚡ Quick Commands")
            .setItems(ShellExecutor.QuickCommands.COMMAND_NAMES, (dialog, which) -> {
                String command = ShellExecutor.QuickCommands.COMMANDS[which];
                etCommandInput.setText(command);
                executeCommand();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * 清除屏幕
     */
    private void clearScreen() {
        tvTerminalOutput.setText("");
        showWelcomeMessage();
    }

    /**
     * 复制输出
     */
    private void copyOutput() {
        String output = tvTerminalOutput.getText().toString();
        if (ShellExecutor.copyToClipboard(requireContext(), output)) {
            Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "Copy failed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 追加输出（使用颜色字符串）
     */
    private void appendOutput(String text, String colorHex, boolean bold) {
        if (getActivity() == null) return;
        
        requireActivity().runOnUiThread(() -> {
            SpannableStringBuilder builder = new SpannableStringBuilder(tvTerminalOutput.getText());
            
            int start = builder.length();
            builder.append(text).append("\n");
            int end = builder.length();
            
            try {
                int color = Color.parseColor(colorHex);
                builder.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (bold) {
                    builder.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 
                                   start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } catch (Exception e) {
                // 默认绿色
                builder.setSpan(new ForegroundColorSpan(0xFF00FF00), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            
            tvTerminalOutput.setText(builder);
            
            // 强制滚动到底部
            tvTerminalOutput.post(() -> {
                scrollViewOutput.fullScroll(View.FOCUS_DOWN);
                scrollViewOutput.post(() -> {
                    scrollViewOutput.scrollTo(0, tvTerminalOutput.getBottom());
                });
            });
        });
    }

    /**
     * 保持软键盘打开
     */
    private void keepKeyboardOpen() {
        etCommandInput.postDelayed(() -> {
            if (getContext() != null && etCommandInput != null) {
                etCommandInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) 
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etCommandInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 100);
    }
    
    /**
     * 强制滚动到底部
     */
    private void scrollToBottom() {
        if (getActivity() == null) return;
        
        requireActivity().runOnUiThread(() -> {
            tvTerminalOutput.post(() -> {
                scrollViewOutput.fullScroll(View.FOCUS_DOWN);
                scrollViewOutput.post(() -> {
                    scrollViewOutput.scrollTo(0, tvTerminalOutput.getBottom());
                });
            });
        });
    }
    
    /**
     * 更新 Shizuku 状态
     */
    private void updateShizukuStatus() {
        if (ShellExecutor.isShizukuAvailable()) {
            shizukuIndicator.setBackgroundColor(Color.parseColor("#00FF00"));
            tvShizukuStatus.setText("root");
            tvShizukuStatus.setTextColor(Color.parseColor("#00FF00"));
            tvPrompt.setText("#");
        } else {
            shizukuIndicator.setBackgroundColor(Color.parseColor("#FFA500"));
            tvShizukuStatus.setText("user");
            tvShizukuStatus.setTextColor(Color.parseColor("#FFA500"));
            tvPrompt.setText("$");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateShizukuStatus();
        // 不要自动打开键盘
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
