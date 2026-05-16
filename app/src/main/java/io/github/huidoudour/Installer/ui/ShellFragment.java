package io.github.huidoudour.Installer.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.databinding.FragmentShellBinding;
import io.github.huidoudour.Installer.util.CommandAutocomplete;
import io.github.huidoudour.Installer.util.CommandBookmarks;
import io.github.huidoudour.Installer.util.ShellExecutor;

public class ShellFragment extends Fragment {

    private static final String PREFS_NAME = "ShellPrefs";
    private static final String KEY_WELCOME_SHOWN = "welcome_shown";

    private FragmentShellBinding binding;
    private TextView tvTerminalOutput;
    private EditText etCommandInput;
    private NestedScrollView scrollViewOutput;
    
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private boolean isExecuting = false;
    private int commandCount = 0;
    private int historyIndex = -1;
    
    // Material You 颜色
    private int colorPrimary;
    private int colorOnSurface;
    private int colorError;
    private int colorTertiary;
    private int colorOnSurfaceVariant;
    
    // 搜索相关
    private String fullOutputText = ""; // 保存完整的输出文本用于搜索
    private boolean isSearchMode = false;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentShellBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 初始化视图
        tvTerminalOutput = binding.tvTerminalOutput;
        etCommandInput = binding.etCommandInput;
        scrollViewOutput = binding.scrollViewOutput;
        
        // 获取Material You颜色
        initMaterialColors();

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
        
        // 顶部工具栏按钮
        binding.btnHistory.setOnClickListener(v -> showHistoryDialog());
        binding.btnBookmarks.setOnClickListener(v -> showBookmarksDialog());
        binding.btnSearchOutput.setOnClickListener(v -> toggleSearchMode());
        binding.btnSaveOutput.setOnClickListener(v -> saveOutputToFile());
        
        // 快速滚动按钮
        binding.btnScrollTop.setOnClickListener(v -> scrollToTop());
        binding.btnScrollBottom.setOnClickListener(v -> scrollToBottom());
        
        // 搜索框监听
        binding.etSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || 
                (event != null && event.getAction() == KeyEvent.ACTION_DOWN && 
                 event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                performSearch();
                return true;
            }
            return false;
        });
        
        binding.etSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().isEmpty()) {
                    // 清空搜索，恢复原始输出
                    restoreFullOutput();
                } else {
                    performSearch();
                }
            }
        });

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

        // 首次打开 Shell 时自动显示欢迎信息（仅一次，除非 app 被完全关闭再打开）
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_WELCOME_SHOWN, false)) {
            showWelcomeMessage();
            prefs.edit().putBoolean(KEY_WELCOME_SHOWN, true).apply();
        }

        return root;
    }



    /**
     * 初始化Material You颜色
     */
    private void initMaterialColors() {
        // 获取主题颜色，如果无法获取则使用默认值
        try {
            TypedArray ta = requireContext().obtainStyledAttributes(new int[]{
                android.R.attr.colorPrimary,
                android.R.attr.colorError
            });
            
            colorPrimary = ta.getColor(0, 0xFF6750A4);
            colorError = ta.getColor(1, 0xFFB3261E);
            ta.recycle();
        } catch (Exception e) {
            colorPrimary = 0xFF6750A4;
            colorError = 0xFFB3261E;
        }
        
        // 使用Material Design预设颜色
        colorOnSurface = 0xFF1C1B1F;  // 深色模式下会自动反转
        colorTertiary = 0xFF7D5260;
        colorOnSurfaceVariant = 0xFF49454F;
    }
    
    /**
     * 显示欢迎信息
     */
    private void showWelcomeMessage() {
        appendOutput(getString(R.string.type_help_for_command_list), colorOnSurfaceVariant, false);
    }

    /**
     * 清除屏幕
     */
    private void clearScreen() {
        tvTerminalOutput.setText("");
        fullOutputText = "";
        binding.scrollButtonsContainer.setVisibility(View.GONE);
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
            getString(R.string.quick_path_root),
            getString(R.string.quick_path_sdcard),
            getString(R.string.quick_path_download),
            getString(R.string.quick_path_tmp),
            getString(R.string.quick_path_app_data),
            getString(R.string.quick_path_system),
            getString(R.string.quick_path_system_bin),
            getString(R.string.quick_path_home)
        };
        
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.quick_path_title))
            .setItems(pathNames, (dialog1, which) -> {
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
            .setNegativeButton(getString(R.string.cancel), null)
            .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }
    
    /**
     * 取消当前命令 (Ctrl+C)
     */
    private void cancelCommand() {
        if (isExecuting) {
            // 重置Shell会话
            ShellExecutor.resetSession();
            appendOutput(getString(R.string.ctrl_c), colorError, true);
            appendOutput(getString(R.string.command_cancelled_session_reset), colorTertiary, false);
            appendOutput("", colorOnSurface, false);
            
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
            Toast.makeText(requireContext(), getString(R.string.command_running), Toast.LENGTH_SHORT).show();
            return;
        }

        // 添加到历史
        ShellExecutor.CommandHistory.addCommand(command);
        historyIndex = -1;  // 重置历史索引
        commandCount++;

        // 显示命令提示符和命令
        String prompt = ShellExecutor.isShizukuAvailable() ? getString(R.string.root_prompt) : getString(R.string.user_prompt);
        appendOutput(prompt + " " + command, colorPrimary, true);

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
        ShellExecutor.executeCommand(requireContext(), command, new ShellExecutor.ExecuteCallback() {
            @Override
            public void onOutput(String line) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        appendOutput(line, colorOnSurface, false);
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        appendOutput(error, colorError, false);
                    });
                }
            }

            @Override
            public void onComplete(int exitCode) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (exitCode != 0) {
                            appendOutput(getString(R.string.process_completed_with_exit_code, exitCode), colorTertiary, false);
                        }
                        appendOutput("", colorOnSurface, false);
                        
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
                appendOutput("👋 Please use app navigation to switch pages", colorOnSurfaceVariant, false);
                return true;
            default:
                return false;
        }
    }
    
    
    
    

    /**
     * 显示帮助信息
     */
    private void showHelpMessage() {
        appendOutput("", colorOnSurface, false);
        appendOutput("Built-in commands:", colorPrimary, true);
        appendOutput("  help     - Show this help", colorOnSurface, false);
        appendOutput("  clear    - Clear screen", colorOnSurface, false);
        appendOutput("  history  - Show command history", colorOnSurface, false);
        appendOutput("  exit     - Exit tip", colorOnSurface, false);
        appendOutput("", colorOnSurface, false);
        appendOutput("Shortcuts:", colorPrimary, true);
        appendOutput("  C   - Clear screen", colorOnSurface, false);
        appendOutput("  📋  - Copy output", colorOnSurface, false);
        appendOutput("  ⚡  - Quick commands", colorOnSurface, false);
        appendOutput("", colorOnSurface, false);
        appendOutput("All Linux shell commands supported", colorOnSurfaceVariant, false);
        appendOutput("", colorOnSurface, false);
    }

    /**
     * 显示命令历史
     */
    private void showHistory() {
        var history = ShellExecutor.CommandHistory.getAll();
        appendOutput("", colorOnSurface, false);
        appendOutput("Command History:", colorPrimary, true);
        appendOutput("", colorOnSurface, false);
        
        if (history.isEmpty()) {
            appendOutput("No history yet", colorOnSurfaceVariant, false);
        } else {
            for (int i = 0; i < history.size(); i++) {
                appendOutput("  " + (i + 1) + ". " + history.get(i), colorOnSurface, false);
            }
        }
        appendOutput("", colorOnSurface, false);
    }

    /**
     * 显示快捷命令对话框
     */
    private void showQuickCommands() {
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚡ Quick Commands")
            .setItems(ShellExecutor.QuickCommands.COMMAND_NAMES, (dialog1, which) -> {
                String command = ShellExecutor.QuickCommands.COMMANDS[which];
                
                // 处理Native命令
                if (command.startsWith("native:")) {
                    etCommandInput.setText(command);
                    executeCommand();
                } else {
                    etCommandInput.setText(command);
                    executeCommand();
                }
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    /**
     * 复制输出
     */
    private void copyOutput() {
        String output = tvTerminalOutput.getText().toString();
        if (ShellExecutor.copyToClipboard(requireContext(), output)) {
            Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), R.string.copy_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 追加输出（使用Material You颜色）
     */
    private void appendOutput(String text, int color, boolean bold) {
        if (getActivity() == null) return;
        
        requireActivity().runOnUiThread(() -> {
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
            
            // 更新完整输出文本（用于搜索功能）
            fullOutputText = tvTerminalOutput.getText().toString();
            
            // 强制滚动到底部
            tvTerminalOutput.post(() -> {
                scrollViewOutput.fullScroll(View.FOCUS_DOWN);
                scrollViewOutput.post(() -> {
                    scrollViewOutput.scrollTo(0, tvTerminalOutput.getBottom());
                    
                    // 如果输出行数超过25行，显示快速滚动按钮
                    String[] lines = tvTerminalOutput.getText().toString().split("\n");
                    if (lines.length > 25) {
                        binding.scrollButtonsContainer.setVisibility(View.VISIBLE);
                    }
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
                // 先请求焦点
                etCommandInput.requestFocus();
                // 强制显示键盘，使用 SHOW_FORCED 确保持续显示
                InputMethodManager imm = (InputMethodManager) 
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etCommandInput, InputMethodManager.SHOW_FORCED);
                }
            }
        }, 50); // 减少延迟时间，避免闪烁
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
     * 滚动到顶部
     */
    private void scrollToTop() {
        if (getActivity() == null) return;
        
        requireActivity().runOnUiThread(() -> {
            scrollViewOutput.smoothScrollTo(0, 0);
        });
    }
    
    /**
     * 显示历史命令对话框
     */
    private void showHistoryDialog() {
        var history = ShellExecutor.CommandHistory.getAll();
        if (history.isEmpty()) {
            Toast.makeText(requireContext(), "No command history yet", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] historyArray = new String[history.size()];
        for (int i = 0; i < history.size(); i++) {
            historyArray[i] = (i + 1) + ". " + history.get(i);
        }
        
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Command History")
            .setItems(historyArray, (dialog, which) -> {
                String command = history.get(which);
                etCommandInput.setText(command);
                etCommandInput.setSelection(etCommandInput.getText().length());
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }
    
    /**
     * 显示书签对话框
     */
    private void showBookmarksDialog() {
        List<String> bookmarks = CommandBookmarks.getBookmarks(requireContext());
        if (bookmarks.isEmpty()) {
            Toast.makeText(requireContext(), "No bookmarks yet. Use ⭐ to bookmark commands.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] bookmarksArray = bookmarks.toArray(new String[0]);
        
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bookmarked Commands")
            .setItems(bookmarksArray, (dialog, which) -> {
                String command = bookmarks.get(which);
                etCommandInput.setText(command);
                etCommandInput.setSelection(etCommandInput.getText().length());
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }
    
    /**
     * 切换搜索模式
     */
    private void toggleSearchMode() {
        isSearchMode = !isSearchMode;
        
        if (isSearchMode) {
            binding.searchCard.setVisibility(View.VISIBLE);
            binding.etSearchInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) 
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(binding.etSearchInput, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            binding.searchCard.setVisibility(View.GONE);
            binding.etSearchInput.setText("");
            restoreFullOutput();
        }
    }
    
    /**
     * 执行搜索
     */
    private void performSearch() {
        String searchText = binding.etSearchInput.getText().toString().trim();
        if (searchText.isEmpty()) {
            restoreFullOutput();
            return;
        }
        
        // 保存完整输出（如果还没有保存）
        if (fullOutputText.isEmpty()) {
            fullOutputText = tvTerminalOutput.getText().toString();
        }
        
        String[] lines = fullOutputText.split("\n");
        StringBuilder filteredOutput = new StringBuilder();
        int matchCount = 0;
        
        for (String line : lines) {
            if (line.toLowerCase().contains(searchText.toLowerCase())) {
                filteredOutput.append(line).append("\n");
                matchCount++;
            }
        }
        
        if (matchCount > 0) {
            tvTerminalOutput.setText(filteredOutput.toString());
            Toast.makeText(requireContext(), "Found " + matchCount + " matches", Toast.LENGTH_SHORT).show();
        } else {
            tvTerminalOutput.setText("No matches found for: " + searchText);
        }
    }
    
    /**
     * 恢复完整输出
     */
    private void restoreFullOutput() {
        if (!fullOutputText.isEmpty()) {
            tvTerminalOutput.setText(fullOutputText);
        }
    }
    
    /**
     * 保存输出到文件
     */
    private void saveOutputToFile() {
        String output = tvTerminalOutput.getText().toString();
        if (output.trim().isEmpty()) {
            Toast.makeText(requireContext(), "No output to save", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 生成文件名
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new java.util.Date());
        String fileName = "shell_output_" + timestamp + ".txt";
        
        // 保存到Download目录
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File outputFile = new File(downloadDir, fileName);
        
        try {
            FileWriter writer = new FileWriter(outputFile);
            writer.write(output);
            writer.close();
            
            Toast.makeText(requireContext(), "Saved to: " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
