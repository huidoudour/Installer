package io.github.huidoudour.Installer.ui.logs;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.huidoudour.Installer.R;
import io.github.huidoudour.Installer.databinding.FragmentLogsBinding;
import io.github.huidoudour.Installer.utils.LogManager;

public class LogsFragment extends Fragment implements LogManager.LogListener {

    private FragmentLogsBinding binding;
    private TextView tvFullLog;
    private Button btnClearLog;
    private Button btnExportLog;
    private LogManager logManager;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLogsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 初始化视图
        tvFullLog = binding.tvFullLog;
        btnClearLog = binding.btnClearLog;
        btnExportLog = binding.btnExportLog;

        tvFullLog.setMovementMethod(new ScrollingMovementMethod());

        // 获取日志管理器
        logManager = LogManager.getInstance();
        logManager.addListener(this);

        // 加载现有日志
        updateLogDisplay();

        // 设置按钮点击事件
        btnClearLog.setOnClickListener(v -> {
            logManager.clearLogs();
            Toast.makeText(requireContext(), R.string.log_cleared, Toast.LENGTH_SHORT).show();
        });

        btnExportLog.setOnClickListener(v -> exportLogs());

        return root;
    }

    private void updateLogDisplay() {
        if (getActivity() == null) return;
        
        getActivity().runOnUiThread(() -> {
            tvFullLog.setText(logManager.getAllLogs());
            
            // 滚动到底部
            scrollToBottom();
        });
    }

    private void scrollToBottom() {
        try {
            int scrollAmount = tvFullLog.getLineCount() * tvFullLog.getLineHeight() - tvFullLog.getHeight();
            if (scrollAmount > 0) {
                tvFullLog.scrollTo(0, scrollAmount);
            }
        } catch (Exception ignored) {
        }
    }

    private void exportLogs() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "installer_log_" + timestamp + ".txt";
            File logFile = new File(requireContext().getExternalCacheDir(), fileName);

            FileWriter writer = new FileWriter(logFile);
            writer.write(getString(R.string.log_export_header) + "\n");
            writer.write(getString(R.string.export_time, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())) + "\n");
            writer.write(getString(R.string.total_logs, logManager.getLogCount()) + "\n");
            writer.write("\n" + getString(R.string.log_content_header) + "\n");
            writer.write(logManager.getAllLogs());
            writer.close();

            // 分享文件
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_file_subject));
            shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.log_file_generated, fileName));
            
            // 如果支持FileProvider，可以共享文件
            shareIntent.putExtra(Intent.EXTRA_STREAM, 
                FileProvider.getUriForFile(requireContext(), 
                    requireContext().getPackageName() + ".provider", logFile));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_log)));
            
            Toast.makeText(requireContext(), getString(R.string.log_exported, fileName), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(requireContext(), getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            // FileProvider 可能不可用，简单提示
            Toast.makeText(requireContext(), R.string.log_saved_to_cache, Toast.LENGTH_SHORT).show();
        }
    }

    // LogListener 接口实现
    @Override
    public void onLogAdded(String log) {
        updateLogDisplay();
    }

    @Override
    public void onLogCleared() {
        updateLogDisplay();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (logManager != null) {
            logManager.removeListener(this);
        }
        binding = null;
    }
}
