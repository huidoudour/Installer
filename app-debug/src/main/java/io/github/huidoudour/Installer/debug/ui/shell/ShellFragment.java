package io.github.huidoudour.Installer.debug.ui.shell;

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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

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
    private TextInputEditText etCommandInput;
    private ScrollView scrollViewOutput;
    private View shizukuIndicator;
    private TextView tvShizukuStatus;
    
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private boolean isExecuting = false;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentShellBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 初始化视图
        tvTerminalOutput = binding.tvTerminalOutput;
        etCommandInput = binding.etCommandInput;
        scrollViewOutput = binding.scrollViewOutput;
        shizukuIndicator = binding.shizukuIndicator;
        tvShizukuStatus = binding.tvShizukuStatus;

        // 设置点击事件
        binding.btnExecuteCommand.setOnClickListener(v -> executeCommand());
        binding.btnClearScreen.setOnClickListener(v -> clearScreen());
        binding.btnCopyOutput.setOnClickListener(v -> copyOutput());
        binding.btnQuickCommands.setOnClickListener(v -> showQuickCommands());

        // 输入框监听
        etCommandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                executeCommand();
                return true;
            }
            return false;
        });

        // 更新 Shizuku 状态
        updateShizukuStatus();
        
        // 显示欢迎信息
        showWelcomeMessage();

        return root;
    }

    /**
     * 显示欢迎信息
     */
    private void showWelcomeMessage() {
        appendOutput("💻 Shell 终端模拟器", Color.parseColor("#4CAF50"), true);
        appendOutput("版本: 1.0.0 | Powered by Shizuku", Color.GRAY, false);
        appendOutput("", Color.WHITE, false);
        
        if (ShellExecutor.isShizukuAvailable()) {
            appendOutput("✅ Root 模式已启用 (通过 Shizuku)", Color.parseColor("#4CAF50"), false);
        } else {
            appendOutput("⚠️ 普通模式 (请授予 Shizuku 权限以启用 Root 模式)", Color.parseColor("#FF9800"), false);
        }
        
        appendOutput("", Color.WHITE, false);
        appendOutput("输入 'help' 查看帮助信息", Color.GRAY, false);
        appendOutput("", Color.WHITE, false);
    }

    /**
     * 执行命令
     */
    private void executeCommand() {
        String command = etCommandInput.getText().toString().trim();
        if (command.isEmpty()) return;

        if (isExecuting) {
            Toast.makeText(requireContext(), "命令正在执行中...", Toast.LENGTH_SHORT).show();
            return;
        }

        // 添加到历史
        ShellExecutor.CommandHistory.addCommand(command);

        // 显示命令
        String timestamp = timeFormat.format(new Date());
        appendOutput("", Color.WHITE, false);
        appendOutput("$ " + command, Color.parseColor("#2196F3"), true);

        // 内置命令
        if (handleBuiltinCommand(command)) {
            etCommandInput.setText("");
            return;
        }

        // 清空输入框
        etCommandInput.setText("");
        etCommandInput.setEnabled(false);
        binding.btnExecuteCommand.setEnabled(false);
        isExecuting = true;

        // 执行命令
        ShellExecutor.executeCommand(command, new ShellExecutor.ExecuteCallback() {
            @Override
            public void onOutput(String line) {
                requireActivity().runOnUiThread(() -> {
                    appendOutput(line, Color.WHITE, false);
                });
            }

            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() -> {
                    appendOutput(error, Color.parseColor("#F44336"), false);
                });
            }

            @Override
            public void onComplete(int exitCode) {
                requireActivity().runOnUiThread(() -> {
                    String status = exitCode == 0 ? 
                        "✅ 命令执行完成" : 
                        "❌ 命令执行失败 (退出码: " + exitCode + ")";
                    int color = exitCode == 0 ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336");
                    appendOutput(status, color, false);
                    
                    etCommandInput.setEnabled(true);
                    binding.btnExecuteCommand.setEnabled(true);
                    isExecuting = false;
                });
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
                appendOutput("👋 请使用应用导航移到其他页面", Color.GRAY, false);
                return true;
            default:
                return false;
        }
    }

    /**
     * 显示帮助信息
     */
    private void showHelpMessage() {
        appendOutput("", Color.WHITE, false);
        appendOutput("📚 帮助信息", Color.parseColor("#4CAF50"), true);
        appendOutput("", Color.WHITE, false);
        appendOutput("内置命令:", Color.parseColor("#2196F3"), true);
        appendOutput("  help     - 显示此帮助信息", Color.WHITE, false);
        appendOutput("  clear    - 清除屏幕输出", Color.WHITE, false);
        appendOutput("  history  - 显示命令历史", Color.WHITE, false);
        appendOutput("  exit     - 退出提示", Color.WHITE, false);
        appendOutput("", Color.WHITE, false);
        appendOutput("快捷按钮:", Color.parseColor("#2196F3"), true);
        appendOutput("  清屏   - 清除所有输出", Color.WHITE, false);
        appendOutput("  复制   - 复制终端输出到剪贴板", Color.WHITE, false);
        appendOutput("  快捷   - 显示快捷命令列表", Color.WHITE, false);
        appendOutput("", Color.WHITE, false);
        appendOutput("💡 提示: 所有 Linux Shell 命令都可以使用", Color.GRAY, false);
        appendOutput("", Color.WHITE, false);
    }

    /**
     * 显示命令历史
     */
    private void showHistory() {
        var history = ShellExecutor.CommandHistory.getAll();
        appendOutput("", Color.WHITE, false);
        appendOutput("📜 命令历史", Color.parseColor("#4CAF50"), true);
        appendOutput("", Color.WHITE, false);
        
        if (history.isEmpty()) {
            appendOutput("暂无历史记录", Color.GRAY, false);
        } else {
            for (int i = 0; i < history.size(); i++) {
                appendOutput((i + 1) + ". " + history.get(i), Color.WHITE, false);
            }
        }
        appendOutput("", Color.WHITE, false);
    }

    /**
     * 显示快捷命令对话框
     */
    private void showQuickCommands() {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("🚀 快捷命令")
            .setItems(ShellExecutor.QuickCommands.COMMAND_NAMES, (dialog, which) -> {
                String command = ShellExecutor.QuickCommands.COMMANDS[which];
                etCommandInput.setText(command);
                executeCommand();
            })
            .setNegativeButton("取消", null)
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
            Toast.makeText(requireContext(), "✅ 已复制到剪贴板", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "❌ 复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 追加输出
     */
    private void appendOutput(String text, int color, boolean bold) {
        SpannableStringBuilder builder = new SpannableStringBuilder(tvTerminalOutput.getText());
        
        int start = builder.length();
        builder.append(text).append("\n");
        int end = builder.length();
        
        builder.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) {
            builder.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 
                           start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        
        tvTerminalOutput.setText(builder);
        
        // 自动滚动到底部
        scrollViewOutput.post(() -> scrollViewOutput.fullScroll(View.FOCUS_DOWN));
    }

    /**
     * 更新 Shizuku 状态
     */
    private void updateShizukuStatus() {
        if (ShellExecutor.isShizukuAvailable()) {
            shizukuIndicator.setBackgroundColor(Color.parseColor("#4CAF50"));
            tvShizukuStatus.setText("Root");
            tvShizukuStatus.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            shizukuIndicator.setBackgroundColor(Color.parseColor("#FF9800"));
            tvShizukuStatus.setText("User");
            tvShizukuStatus.setTextColor(Color.parseColor("#FF9800"));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateShizukuStatus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
